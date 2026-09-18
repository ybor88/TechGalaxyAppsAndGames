from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.schemas.controllo_gestione import (
    ClassificazioneCostoSet, ClassificazioneCostoOut,
    BreakEvenAziendale, BreakEvenProdotto, KPIIndustriale,
)
from app.services.controllo_gestione import ControlloGestioneService

router = APIRouter()


@router.get("/classificazioni", response_model=list[ClassificazioneCostoOut])
async def list_classificazioni(db: AsyncSession = Depends(get_db)):
    return await ControlloGestioneService(db).list_classificazioni()


@router.post("/classificazioni", response_model=ClassificazioneCostoOut)
async def imposta_classificazione(payload: ClassificazioneCostoSet, db: AsyncSession = Depends(get_db)):
    try:
        return await ControlloGestioneService(db).imposta_classificazione(payload)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))


@router.get("/break-even", response_model=BreakEvenAziendale)
async def break_even_aziendale(db: AsyncSession = Depends(get_db)):
    return await ControlloGestioneService(db).break_even_aziendale()


@router.get("/break-even/prodotti", response_model=list[BreakEvenProdotto])
async def break_even_prodotti(db: AsyncSession = Depends(get_db)):
    return await ControlloGestioneService(db).break_even_prodotti()


@router.get("/kpi", response_model=KPIIndustriale)
async def kpi_industriale(
    anno: int | None = Query(default=None),
    db: AsyncSession = Depends(get_db),
):
    return await ControlloGestioneService(db).kpi_industriale(anno)
