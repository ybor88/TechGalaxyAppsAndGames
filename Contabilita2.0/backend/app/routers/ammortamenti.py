from datetime import date

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.schemas.ammortamenti import (
    CategoriaMinisteriale,
    CespiteCreate,
    CespiteUpdate,
    CespiteOut,
    DismettiCespiteRequest,
    PianoAmmortamentoResponse,
    RiepilogoAmmortamenti,
    ContabilizzaQuotaRequest,
    ContabilizzaQuotaResponse,
)
from app.services.ammortamenti import AmmortamentiService, get_tabella_ministeriale

router = APIRouter()


@router.get("/categorie", response_model=list[CategoriaMinisteriale])
async def list_categorie():
    return get_tabella_ministeriale()


@router.get("/cespiti", response_model=list[CespiteOut])
async def list_cespiti(
    includi_dismessi: bool = Query(True),
    db: AsyncSession = Depends(get_db),
):
    return await AmmortamentiService(db).list_cespiti(includi_dismessi=includi_dismessi)


@router.post("/cespiti", response_model=CespiteOut, status_code=status.HTTP_201_CREATED)
async def crea_cespite(payload: CespiteCreate, db: AsyncSession = Depends(get_db)):
    try:
        return await AmmortamentiService(db).crea_cespite(payload)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))


@router.get("/cespiti/{cespite_id}", response_model=CespiteOut)
async def get_cespite(cespite_id: int, db: AsyncSession = Depends(get_db)):
    cespite = await AmmortamentiService(db).get_cespite(cespite_id)
    if cespite is None:
        raise HTTPException(status_code=404, detail="Cespite non trovato")
    return cespite


@router.put("/cespiti/{cespite_id}", response_model=CespiteOut)
async def aggiorna_cespite(cespite_id: int, payload: CespiteUpdate, db: AsyncSession = Depends(get_db)):
    try:
        cespite = await AmmortamentiService(db).aggiorna_cespite(cespite_id, payload)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(exc))
    if cespite is None:
        raise HTTPException(status_code=404, detail="Cespite non trovato")
    return cespite


@router.delete("/cespiti/{cespite_id}", status_code=status.HTTP_204_NO_CONTENT)
async def elimina_cespite(cespite_id: int, db: AsyncSession = Depends(get_db)):
    try:
        ok = await AmmortamentiService(db).elimina_cespite(cespite_id)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(exc))
    if not ok:
        raise HTTPException(status_code=404, detail="Cespite non trovato")


@router.post("/cespiti/{cespite_id}/dismetti", response_model=CespiteOut)
async def dismetti_cespite(cespite_id: int, payload: DismettiCespiteRequest, db: AsyncSession = Depends(get_db)):
    try:
        cespite = await AmmortamentiService(db).dismetti_cespite(cespite_id, payload.data_dismissione)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))
    if cespite is None:
        raise HTTPException(status_code=404, detail="Cespite non trovato")
    return cespite


@router.get("/cespiti/{cespite_id}/piano", response_model=PianoAmmortamentoResponse)
async def get_piano(cespite_id: int, db: AsyncSession = Depends(get_db)):
    piano = await AmmortamentiService(db).get_piano(cespite_id)
    if piano is None:
        raise HTTPException(status_code=404, detail="Cespite non trovato")
    return piano


@router.post("/cespiti/{cespite_id}/contabilizza", response_model=ContabilizzaQuotaResponse)
async def contabilizza_quota(cespite_id: int, payload: ContabilizzaQuotaRequest, db: AsyncSession = Depends(get_db)):
    try:
        return await AmmortamentiService(db).contabilizza_quota(cespite_id, payload.anno)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))


@router.get("/riepilogo", response_model=RiepilogoAmmortamenti)
async def get_riepilogo(
    anno: int = Query(default_factory=lambda: date.today().year),
    db: AsyncSession = Depends(get_db),
):
    return await AmmortamentiService(db).get_riepilogo(anno)
