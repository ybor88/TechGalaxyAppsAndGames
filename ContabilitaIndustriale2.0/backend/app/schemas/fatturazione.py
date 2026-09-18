from decimal import Decimal
from datetime import date, datetime
from typing import Literal
from pydantic import BaseModel, field_validator


# ── Anagrafica ──────────────────────────────────────────────────────────────

class AnagraficaBase(BaseModel):
    nome: str
    tipo: Literal["cliente", "fornitore", "entrambi"]
    piva: str | None = None
    cf: str | None = None
    indirizzo: str | None = None
    cap: str | None = None
    citta: str | None = None
    provincia: str | None = None
    paese: str = "Italia"
    email: str | None = None
    telefono: str | None = None
    note: str | None = None


class AnagraficaCreate(AnagraficaBase):
    pass


class AnagraficaUpdate(BaseModel):
    nome: str | None = None
    tipo: Literal["cliente", "fornitore", "entrambi"] | None = None
    piva: str | None = None
    cf: str | None = None
    indirizzo: str | None = None
    cap: str | None = None
    citta: str | None = None
    provincia: str | None = None
    paese: str | None = None
    email: str | None = None
    telefono: str | None = None
    note: str | None = None


class AnagraficaResponse(AnagraficaBase):
    id: int
    created_at: datetime

    class Config:
        from_attributes = True


class AnagraficaBreve(BaseModel):
    """Versione compatta per embedding nei documenti."""

    id: int
    nome: str
    tipo: str

    class Config:
        from_attributes = True


# ── Riga documento ──────────────────────────────────────────────────────────

class RigaDocumentoCreate(BaseModel):
    descrizione: str
    quantita: Decimal = Decimal("1.0000")
    prezzo_unitario: Decimal
    iva_percentuale: Decimal = Decimal("22.00")

    @field_validator("quantita")
    @classmethod
    def quantita_positiva(cls, v: Decimal) -> Decimal:
        if v <= 0:
            raise ValueError("quantita deve essere maggiore di zero")
        return v

    @field_validator("prezzo_unitario")
    @classmethod
    def prezzo_non_negativo(cls, v: Decimal) -> Decimal:
        if v < 0:
            raise ValueError("prezzo_unitario non può essere negativo")
        return v

    @field_validator("iva_percentuale")
    @classmethod
    def iva_valida(cls, v: Decimal) -> Decimal:
        if v < 0 or v > 100:
            raise ValueError("iva_percentuale deve essere tra 0 e 100")
        return v


class RigaDocumentoResponse(BaseModel):
    id: int
    documento_id: int
    descrizione: str
    quantita: Decimal
    prezzo_unitario: Decimal
    iva_percentuale: Decimal
    importo: Decimal

    class Config:
        from_attributes = True


# ── Documento ───────────────────────────────────────────────────────────────

TIPI_DOCUMENTO = Literal[
    "preventivo", "ordine", "fattura_attiva", "fattura_passiva", "nota_credito"
]
STATI_DOCUMENTO = Literal["bozza", "emesso", "pagato", "annullato"]


class DocumentoCreate(BaseModel):
    tipo: TIPI_DOCUMENTO
    data: date
    data_scadenza: date | None = None
    anagrafica_id: int | None = None
    oggetto: str | None = None
    note: str | None = None
    righe: list[RigaDocumentoCreate] = []


class DocumentoUpdate(BaseModel):
    data: date | None = None
    data_scadenza: date | None = None
    anagrafica_id: int | None = None
    stato: STATI_DOCUMENTO | None = None
    oggetto: str | None = None
    note: str | None = None
    righe: list[RigaDocumentoCreate] | None = None


class DocumentoResponse(BaseModel):
    id: int
    tipo: str
    numero: str
    data: date
    data_scadenza: date | None
    anagrafica_id: int | None
    stato: str
    oggetto: str | None
    subtotale: Decimal
    totale_iva: Decimal
    totale: Decimal
    note: str | None
    created_at: datetime
    updated_at: datetime
    anagrafica: AnagraficaBreve | None
    righe: list[RigaDocumentoResponse]

    class Config:
        from_attributes = True
