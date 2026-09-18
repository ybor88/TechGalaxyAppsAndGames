from decimal import Decimal
from datetime import datetime

from pydantic import BaseModel, Field


# ── Centri di costo ────────────────────────────────────────────────────────────

class CentroCostoCreate(BaseModel):
    codice: str
    descrizione: str
    tipo: str = Field(pattern="^(produttivo|ausiliario|comune)$")
    centro_padre_id: int | None = None
    note: str | None = None


class CentroCostoUpdate(BaseModel):
    codice: str | None = None
    descrizione: str | None = None
    tipo: str | None = Field(default=None, pattern="^(produttivo|ausiliario|comune)$")
    centro_padre_id: int | None = None
    attivo: bool | None = None
    note: str | None = None


class CentroCostoOut(BaseModel):
    id: int
    codice: str
    descrizione: str
    tipo: str
    centro_padre_id: int | None
    centro_padre_descrizione: str | None = None
    attivo: bool
    note: str | None
    created_at: datetime

    class Config:
        from_attributes = True


# ── Basi di riparto ─────────────────────────────────────────────────────────────

class BaseRipartoCreate(BaseModel):
    descrizione: str
    unita_misura: str


class BaseRipartoOut(BaseModel):
    id: int
    descrizione: str
    unita_misura: str
    created_at: datetime

    class Config:
        from_attributes = True


class ValoreBaseRipartoCreate(BaseModel):
    base_riparto_id: int
    centro_costo_id: int
    periodo: str = Field(pattern=r"^\d{4}-\d{2}$")
    quantita: Decimal = Field(gt=0)


class ValoreBaseRipartoOut(BaseModel):
    id: int
    base_riparto_id: int
    centro_costo_id: int
    centro_costo_descrizione: str
    periodo: str
    quantita: Decimal

    class Config:
        from_attributes = True


# ── Riparto costi indiretti ──────────────────────────────────────────────────────

class RipartoCostoIndirettoCreate(BaseModel):
    descrizione: str
    centro_costo_origine_id: int
    importo: Decimal = Field(gt=0)
    base_riparto_id: int
    periodo: str = Field(pattern=r"^\d{4}-\d{2}$")
    note: str | None = None


class RipartoCostoIndirettoOut(BaseModel):
    id: int
    descrizione: str
    centro_costo_origine_id: int
    centro_costo_origine_descrizione: str
    importo: Decimal
    base_riparto_id: int
    base_riparto_descrizione: str
    periodo: str
    note: str | None
    created_at: datetime

    class Config:
        from_attributes = True


class AllocazioneRiga(BaseModel):
    centro_costo_id: int
    centro_costo_descrizione: str
    quantita_base: Decimal
    percentuale: Decimal
    importo_allocato: Decimal


class RipartoCalcolato(BaseModel):
    riparto_id: int
    descrizione: str
    importo_totale: Decimal
    base_riparto_descrizione: str
    periodo: str
    allocazioni: list[AllocazioneRiga]
