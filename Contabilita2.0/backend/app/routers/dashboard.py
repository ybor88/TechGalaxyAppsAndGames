from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.schemas.dashboard import DashboardResponse, MovimentoCreate, MovimentoResponse
from app.services.dashboard import DashboardService

router = APIRouter()


@router.get("/", response_model=DashboardResponse)
async def get_dashboard(db: AsyncSession = Depends(get_db)):
    """Restituisce tutti i dati per la Dashboard Finanziaria."""
    service = DashboardService(db)
    return await service.get_dashboard()


@router.get("/kpi")
async def get_kpi(db: AsyncSession = Depends(get_db)):
    """KPI: saldo operativo, entrate/uscite, cashflow, indice liquidità."""
    service = DashboardService(db)
    return await service.get_kpi()


@router.get("/andamento-mensile")
async def get_andamento_mensile(mesi: int = 12, db: AsyncSession = Depends(get_db)):
    """Andamento mensile entrate/uscite/saldo."""
    service = DashboardService(db)
    return await service.get_andamento_mensile(mesi=mesi)


@router.get("/cashflow-settimanale")
async def get_cashflow_settimanale(settimane: int = 8, db: AsyncSession = Depends(get_db)):
    """Cashflow cumulativo settimanale."""
    service = DashboardService(db)
    return await service.get_cashflow_settimanale(settimane=settimane)
