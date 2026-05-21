from decimal import Decimal
from datetime import date, datetime
from pydantic import BaseModel, field_validator


class MovimentoCreate(BaseModel):
    data: date
    tipo: str  # "entrata" | "uscita"
    importo: Decimal
    descrizione: str
    categoria: str | None = None
    conto_id: int | None = None
    note: str | None = None

    @field_validator("tipo")
    @classmethod
    def tipo_valido(cls, v: str) -> str:
        if v not in ("entrata", "uscita"):
            raise ValueError('tipo deve essere "entrata" o "uscita"')
        return v

    @field_validator("importo")
    @classmethod
    def importo_positivo(cls, v: Decimal) -> Decimal:
        if v <= 0:
            raise ValueError("importo deve essere maggiore di zero")
        return v


class MovimentoUpdate(BaseModel):
    data: date | None = None
    tipo: str | None = None
    importo: Decimal | None = None
    descrizione: str | None = None
    categoria: str | None = None
    conto_id: int | None = None
    note: str | None = None

    @field_validator("tipo")
    @classmethod
    def tipo_valido(cls, v: str | None) -> str | None:
        if v is not None and v not in ("entrata", "uscita"):
            raise ValueError('tipo deve essere "entrata" o "uscita"')
        return v


class MovimentoResponse(BaseModel):
    id: int
    data: date
    tipo: str
    importo: Decimal
    descrizione: str
    categoria: str | None
    conto_id: int | None
    note: str | None
    created_at: datetime

    class Config:
        from_attributes = True
