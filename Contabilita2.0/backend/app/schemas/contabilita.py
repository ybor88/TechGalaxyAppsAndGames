from decimal import Decimal
from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, Field, model_validator


# ── Input schemas ─────────────────────────────────────────────────────────────

class RigaRegistrazioneIn(BaseModel):
    conto_id: int
    descrizione: str | None = None
    dare: Decimal = Decimal("0.00")
    avere: Decimal = Decimal("0.00")
    aliquota_iva: Decimal | None = None  # es. 22.00, 10.00, 4.00, 0.00
    tipo_iva: Literal["imponibile", "iva", "esente"] | None = None


class RegistrazioneCreate(BaseModel):
    data: date
    causale: str
    tipo_causale: Literal[
        "manuale", "fattura_attiva", "fattura_passiva",
        "pagamento", "incasso", "altro"
    ] = "manuale"
    note: str | None = None
    righe: list[RigaRegistrazioneIn] = Field(min_length=2)

    @model_validator(mode="after")
    def check_balanced(self) -> "RegistrazioneCreate":
        total_dare = sum(r.dare for r in self.righe)
        total_avere = sum(r.avere for r in self.righe)
        if total_dare != total_avere:
            raise ValueError(
                f"Registrazione non bilanciata: dare={total_dare:.2f}, avere={total_avere:.2f}"
            )
        if total_dare == Decimal("0.00"):
            raise ValueError("La registrazione deve avere importi maggiori di zero")
        return self


# ── Output schemas ────────────────────────────────────────────────────────────

class RigaRegistrazioneOut(BaseModel):
    id: int
    registrazione_id: int
    conto_id: int
    conto_codice: str
    conto_descrizione: str
    descrizione: str | None
    dare: Decimal
    avere: Decimal
    aliquota_iva: Decimal | None
    tipo_iva: str | None

    class Config:
        from_attributes = True


class RegistrazioneSummary(BaseModel):
    id: int
    numero: int
    data: date
    causale: str
    tipo_causale: str
    chiusa: bool
    totale_dare: Decimal
    totale_avere: Decimal
    created_at: datetime

    class Config:
        from_attributes = True


class RegistrazioneDetail(RegistrazioneSummary):
    note: str | None
    righe: list[RigaRegistrazioneOut]


# ── Bilancio ──────────────────────────────────────────────────────────────────

class VoceBilancio(BaseModel):
    conto_id: int
    codice: str
    descrizione: str
    tipo: str
    totale_dare: Decimal
    totale_avere: Decimal
    saldo: Decimal  # positivo = saldo naturale del conto


class BilancioResponse(BaseModel):
    conti: list[VoceBilancio]
    totale_dare: Decimal
    totale_avere: Decimal
    totale_attivo: Decimal
    totale_passivo: Decimal
    totale_costi: Decimal
    totale_ricavi: Decimal
    utile_perdita: Decimal  # positivo = utile, negativo = perdita


# ── Liquidazione IVA ──────────────────────────────────────────────────────────

class RigaIVA(BaseModel):
    aliquota_iva: Decimal
    imponibile_acquisti: Decimal
    iva_a_credito: Decimal
    imponibile_vendite: Decimal
    iva_a_debito: Decimal


class LiquidazioneIVAResponse(BaseModel):
    data_da: date
    data_a: date
    iva_a_credito: Decimal    # IVA detraibile su acquisti
    iva_a_debito: Decimal     # IVA su vendite
    saldo_iva: Decimal        # debito - credito (>0 = da versare, <0 = credito)
    dettaglio: list[RigaIVA]


# ── Piano dei conti standard ──────────────────────────────────────────────────

class InizializzaPianoContiResponse(BaseModel):
    conti_creati: int
    conti_esistenti: int
    message: str
