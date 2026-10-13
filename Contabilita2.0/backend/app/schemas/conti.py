from decimal import Decimal
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field

TipoConto = Literal["attivo", "passivo", "costo", "ricavo"]


class ContoCreate(BaseModel):
    codice: str = Field(min_length=1, max_length=20)
    descrizione: str = Field(min_length=1, max_length=200)
    tipo: TipoConto
    saldo: Decimal = Decimal("0.00")


class ContoUpdate(BaseModel):
    codice: str | None = Field(None, min_length=1, max_length=20)
    descrizione: str | None = Field(None, min_length=1, max_length=200)
    tipo: TipoConto | None = None
    saldo: Decimal | None = None


class ContoResponse(BaseModel):
    id: int
    codice: str
    descrizione: str
    tipo: str
    saldo: Decimal
    created_at: datetime

    class Config:
        from_attributes = True
