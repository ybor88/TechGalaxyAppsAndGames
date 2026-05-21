from decimal import Decimal
from datetime import datetime
from pydantic import BaseModel


class ContoCreate(BaseModel):
    codice: str
    descrizione: str
    tipo: str  # "attivo" | "passivo" | "costo" | "ricavo"
    saldo: Decimal = Decimal("0.00")


class ContoUpdate(BaseModel):
    codice: str | None = None
    descrizione: str | None = None
    tipo: str | None = None
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
