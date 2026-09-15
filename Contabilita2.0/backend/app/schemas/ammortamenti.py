from decimal import Decimal
from datetime import date, datetime

from pydantic import BaseModel, Field


# ── Tabella ministeriale ─────────────────────────────────────────────────────

class CategoriaMinisteriale(BaseModel):
    categoria: str
    label: str
    aliquota_fiscale: Decimal


# ── Input schemas ─────────────────────────────────────────────────────────────

class CespiteCreate(BaseModel):
    descrizione: str
    categoria: str
    data_acquisto: date
    costo_storico: Decimal = Field(gt=0)
    # Se omesse, vengono prese dalla tabella ministeriale in base alla categoria.
    aliquota_civilistica: Decimal | None = Field(default=None, gt=0, le=100)
    aliquota_fiscale: Decimal | None = Field(default=None, gt=0, le=100)
    conto_costo_id: int
    conto_fondo_id: int
    note: str | None = None


class CespiteUpdate(BaseModel):
    descrizione: str | None = None
    categoria: str | None = None
    data_acquisto: date | None = None
    costo_storico: Decimal | None = Field(default=None, gt=0)
    aliquota_civilistica: Decimal | None = Field(default=None, gt=0, le=100)
    aliquota_fiscale: Decimal | None = Field(default=None, gt=0, le=100)
    conto_costo_id: int | None = None
    conto_fondo_id: int | None = None
    note: str | None = None


class DismettiCespiteRequest(BaseModel):
    data_dismissione: date


# ── Output schemas ────────────────────────────────────────────────────────────

class CespiteOut(BaseModel):
    id: int
    descrizione: str
    categoria: str
    categoria_label: str
    data_acquisto: date
    costo_storico: Decimal
    aliquota_civilistica: Decimal
    aliquota_fiscale: Decimal
    conto_costo_id: int
    conto_costo_descrizione: str
    conto_fondo_id: int
    conto_fondo_descrizione: str
    note: str | None
    dismesso: bool
    data_dismissione: date | None
    created_at: datetime

    class Config:
        from_attributes = True


class QuotaPiano(BaseModel):
    anno: int
    quota_civilistica: Decimal
    fondo_civilistico: Decimal
    valore_residuo_civilistico: Decimal
    quota_fiscale: Decimal
    fondo_fiscale: Decimal
    valore_residuo_fiscale: Decimal
    contabilizzato: bool = False


class PianoAmmortamentoResponse(BaseModel):
    cespite_id: int
    descrizione: str
    costo_storico: Decimal
    quote: list[QuotaPiano]


class RiepilogoAmmortamenti(BaseModel):
    anno: int
    numero_cespiti: int
    totale_costo_storico: Decimal
    totale_fondo_civilistico: Decimal
    totale_quota_anno: Decimal
    totale_valore_residuo: Decimal


class ContabilizzaQuotaRequest(BaseModel):
    anno: int


class ContabilizzaQuotaResponse(BaseModel):
    cespite_id: int
    anno: int
    importo: Decimal
    registrazione_id: int
