from decimal import Decimal
from datetime import date, datetime

from pydantic import BaseModel, Field, field_validator


# ── Input schemas ─────────────────────────────────────────────────────────────

class DipendenteCreate(BaseModel):
    nome: str
    cognome: str
    codice_fiscale: str | None = None
    qualifica: str | None = None
    data_assunzione: date
    data_cessazione: date | None = None
    retribuzione_lorda_mensile: Decimal = Field(gt=0)
    numero_mensilita: int = Field(default=13, ge=12, le=14)
    aliquota_inps_dipendente: Decimal = Field(default=Decimal("9.19"), ge=0, le=100)
    aliquota_inps_azienda: Decimal = Field(default=Decimal("30.00"), ge=0, le=100)
    detrazioni_attive: bool = True
    note: str | None = None


class DipendenteUpdate(BaseModel):
    nome: str | None = None
    cognome: str | None = None
    codice_fiscale: str | None = None
    qualifica: str | None = None
    data_assunzione: date | None = None
    data_cessazione: date | None = None
    retribuzione_lorda_mensile: Decimal | None = Field(default=None, gt=0)
    numero_mensilita: int | None = Field(default=None, ge=12, le=14)
    aliquota_inps_dipendente: Decimal | None = Field(default=None, ge=0, le=100)
    aliquota_inps_azienda: Decimal | None = Field(default=None, ge=0, le=100)
    detrazioni_attive: bool | None = None
    note: str | None = None


class CalcolaCedolinoRequest(BaseModel):
    anno: int
    mese: int = Field(ge=1, le=12)

    @field_validator("anno")
    @classmethod
    def anno_valido(cls, v: int) -> int:
        if v < 2000 or v > 2100:
            raise ValueError("Anno non valido")
        return v


# ── Output schemas ────────────────────────────────────────────────────────────

class DipendenteOut(BaseModel):
    id: int
    nome: str
    cognome: str
    codice_fiscale: str | None
    qualifica: str | None
    data_assunzione: date
    data_cessazione: date | None
    retribuzione_lorda_mensile: Decimal
    numero_mensilita: int
    aliquota_inps_dipendente: Decimal
    aliquota_inps_azienda: Decimal
    detrazioni_attive: bool
    note: str | None
    created_at: datetime

    class Config:
        from_attributes = True


class CedolinoOut(BaseModel):
    id: int | None = None
    dipendente_id: int
    anno: int
    mese: int
    retribuzione_lorda: Decimal
    contributi_inps_dipendente: Decimal
    imponibile_fiscale: Decimal
    irpef_lorda: Decimal
    detrazioni_irpef: Decimal
    irpef_netta: Decimal
    netto_busta: Decimal
    contributi_inps_azienda: Decimal
    quota_tfr: Decimal
    costo_azienda: Decimal
    registrazione_id: int | None = None

    class Config:
        from_attributes = True


class ContabilizzaCedolinoResponse(BaseModel):
    cedolino_id: int
    registrazione_id: int
