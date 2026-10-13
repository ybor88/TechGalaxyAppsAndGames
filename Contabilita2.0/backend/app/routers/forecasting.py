from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.schemas.forecasting import (
    PrevisioneVenditeResponse,
    PrevisioneLiquiditaResponse,
    SimulazioneScenariResponse,
    RischioInsolvenzaResponse,
)
from app.services.forecasting import ForecastingService

router = APIRouter()


@router.get("/previsione-vendite", response_model=PrevisioneVenditeResponse)
async def get_previsione_vendite(
    mesi: int = Query(3, ge=1, le=24),
    db: AsyncSession = Depends(get_db),
):
    """Previsione entrate/vendite per i prossimi N mesi (default 3)."""
    return await ForecastingService(db).get_previsione_vendite(mesi_futuri=mesi)


@router.get("/previsione-liquidita", response_model=PrevisioneLiquiditaResponse)
async def get_previsione_liquidita(
    giorni: int = Query(30, ge=1, le=365),
    db: AsyncSession = Depends(get_db),
):
    """Previsione saldo di liquidità per i prossimi N giorni (default 30)."""
    return await ForecastingService(db).get_previsione_liquidita(giorni=giorni)


@router.get("/simulazione-scenari", response_model=SimulazioneScenariResponse)
async def get_simulazione_scenari(db: AsyncSession = Depends(get_db)):
    """Simulazione scenari ottimistico / base / pessimistico per il mese successivo."""
    return await ForecastingService(db).get_simulazione_scenari()


@router.get("/rischio-insolvenza", response_model=RischioInsolvenzaResponse)
async def get_rischio_insolvenza(db: AsyncSession = Depends(get_db)):
    """Calcolo del punteggio di rischio insolvenza aziendale."""
    return await ForecastingService(db).get_rischio_insolvenza()
