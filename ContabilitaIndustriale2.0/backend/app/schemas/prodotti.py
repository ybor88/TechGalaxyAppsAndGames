from decimal import Decimal
from datetime import date, datetime

from pydantic import BaseModel, Field


# ── Prodotti ─────────────────────────────────────────────────────────────────

class ProdottoCreate(BaseModel):
    codice: str
    descrizione: str
    tipo: str = Field(pattern="^(materia_prima|semilavorato|prodotto_finito)$")
    unita_misura: str = "pz"
    scorta_minima: Decimal = Decimal("0.000")
    prezzo_standard: Decimal = Decimal("0.0000")
    ore_manodopera_standard: Decimal = Decimal("0.000")
    costo_orario_manodopera_standard: Decimal = Decimal("0.00")
    costo_indiretto_standard_unitario: Decimal = Decimal("0.0000")
    prezzo_vendita: Decimal | None = None
    note: str | None = None


class ProdottoUpdate(BaseModel):
    codice: str | None = None
    descrizione: str | None = None
    tipo: str | None = Field(default=None, pattern="^(materia_prima|semilavorato|prodotto_finito)$")
    unita_misura: str | None = None
    scorta_minima: Decimal | None = None
    prezzo_standard: Decimal | None = None
    ore_manodopera_standard: Decimal | None = None
    costo_orario_manodopera_standard: Decimal | None = None
    costo_indiretto_standard_unitario: Decimal | None = None
    prezzo_vendita: Decimal | None = None
    attivo: bool | None = None
    note: str | None = None


class ProdottoOut(BaseModel):
    id: int
    codice: str
    descrizione: str
    tipo: str
    unita_misura: str
    giacenza_attuale: Decimal
    costo_medio_ponderato: Decimal
    scorta_minima: Decimal
    prezzo_standard: Decimal
    ore_manodopera_standard: Decimal
    costo_orario_manodopera_standard: Decimal
    costo_indiretto_standard_unitario: Decimal
    costo_standard_materiale_unitario: Decimal
    costo_standard_unitario_totale: Decimal
    prezzo_vendita: Decimal | None
    sotto_scorta: bool
    attivo: bool
    note: str | None
    created_at: datetime

    class Config:
        from_attributes = True


# ── Distinta base (BOM) ──────────────────────────────────────────────────────

class DistintaBaseRigaCreate(BaseModel):
    componente_id: int
    quantita: Decimal = Field(gt=0)
    note: str | None = None


class DistintaBaseRigaOut(BaseModel):
    id: int
    prodotto_padre_id: int
    componente_id: int
    componente_codice: str
    componente_descrizione: str
    componente_tipo: str
    quantita: Decimal
    costo_standard_componente: Decimal
    note: str | None

    class Config:
        from_attributes = True


class DistintaBaseResponse(BaseModel):
    prodotto_id: int
    descrizione: str
    righe: list[DistintaBaseRigaOut]
    costo_materiale_unitario: Decimal


# ── Magazzino ────────────────────────────────────────────────────────────────

class MovimentoMagazzinoCreate(BaseModel):
    prodotto_id: int
    data: date
    tipo: str = Field(pattern="^(carico|scarico)$")
    quantita: Decimal = Field(gt=0)
    # Obbligatorio per i carichi (prezzo di acquisto/produzione). Ignorato per gli scarichi
    # (valorizzati automaticamente al costo medio ponderato corrente).
    costo_unitario: Decimal | None = None
    causale: str
    commessa_id: int | None = None


class MovimentoMagazzinoOut(BaseModel):
    id: int
    prodotto_id: int
    prodotto_codice: str
    prodotto_descrizione: str
    data: date
    tipo: str
    quantita: Decimal
    costo_unitario: Decimal
    importo: Decimal
    causale: str
    commessa_id: int | None
    created_at: datetime

    class Config:
        from_attributes = True


class GiacenzaMagazzino(BaseModel):
    prodotto_id: int
    codice: str
    descrizione: str
    tipo: str
    unita_misura: str
    giacenza_attuale: Decimal
    costo_medio_ponderato: Decimal
    valore_giacenza: Decimal
    scorta_minima: Decimal
    sotto_scorta: bool
