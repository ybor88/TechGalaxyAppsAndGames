from decimal import Decimal
from datetime import date
from pydantic import BaseModel


class KPIFinanziario(BaseModel):
    saldo_operativo: Decimal
    totale_entrate: Decimal
    totale_uscite: Decimal
    cashflow_netto: Decimal
    indice_liquidita: Decimal  # entrate / uscite


class PuntoGrafico(BaseModel):
    data: date
    entrate: Decimal
    uscite: Decimal
    cashflow: Decimal


class SaldoMensile(BaseModel):
    mese: str  # formato "YYYY-MM"
    entrate: Decimal
    uscite: Decimal
    saldo: Decimal


class DashboardResponse(BaseModel):
    kpi: KPIFinanziario
    andamento_mensile: list[SaldoMensile]
    cashflow_settimanale: list[PuntoGrafico]
    aggiornato_al: date


class MovimentoCreate(BaseModel):
    data: date
    tipo: str  # "entrata" | "uscita"
    importo: Decimal
    descrizione: str
    categoria: str | None = None
    conto_id: int | None = None
    note: str | None = None


class MovimentoResponse(MovimentoCreate):
    id: int

    class Config:
        from_attributes = True
