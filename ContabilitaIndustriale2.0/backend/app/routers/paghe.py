from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.schemas.paghe import (
    DipendenteCreate,
    DipendenteUpdate,
    DipendenteOut,
    CalcolaCedolinoRequest,
    CedolinoOut,
    ContabilizzaCedolinoResponse,
)
from app.services.paghe import PagheService

router = APIRouter()


# ── Dipendenti ──────────────────────────────────────────────────────────────

@router.get("/dipendenti", response_model=list[DipendenteOut])
async def list_dipendenti(
    includi_cessati: bool = Query(True),
    db: AsyncSession = Depends(get_db),
):
    return await PagheService(db).list_dipendenti(includi_cessati=includi_cessati)


@router.post("/dipendenti", response_model=DipendenteOut, status_code=status.HTTP_201_CREATED)
async def crea_dipendente(payload: DipendenteCreate, db: AsyncSession = Depends(get_db)):
    return await PagheService(db).crea_dipendente(payload)


@router.get("/dipendenti/{dipendente_id}", response_model=DipendenteOut)
async def get_dipendente(dipendente_id: int, db: AsyncSession = Depends(get_db)):
    dip = await PagheService(db).get_dipendente(dipendente_id)
    if dip is None:
        raise HTTPException(status_code=404, detail="Dipendente non trovato")
    return dip


@router.put("/dipendenti/{dipendente_id}", response_model=DipendenteOut)
async def aggiorna_dipendente(dipendente_id: int, payload: DipendenteUpdate, db: AsyncSession = Depends(get_db)):
    dip = await PagheService(db).aggiorna_dipendente(dipendente_id, payload)
    if dip is None:
        raise HTTPException(status_code=404, detail="Dipendente non trovato")
    return dip


@router.delete("/dipendenti/{dipendente_id}", status_code=status.HTTP_204_NO_CONTENT)
async def elimina_dipendente(dipendente_id: int, db: AsyncSession = Depends(get_db)):
    ok = await PagheService(db).elimina_dipendente(dipendente_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Dipendente non trovato")


# ── Cedolini ────────────────────────────────────────────────────────────────

@router.post("/dipendenti/{dipendente_id}/cedolini/anteprima", response_model=CedolinoOut)
async def calcola_anteprima(dipendente_id: int, payload: CalcolaCedolinoRequest, db: AsyncSession = Depends(get_db)):
    ced = await PagheService(db).calcola_anteprima(dipendente_id, payload.anno, payload.mese)
    if ced is None:
        raise HTTPException(status_code=404, detail="Dipendente non trovato")
    return ced


@router.post(
    "/dipendenti/{dipendente_id}/cedolini",
    response_model=CedolinoOut,
    status_code=status.HTTP_201_CREATED,
)
async def salva_cedolino(dipendente_id: int, payload: CalcolaCedolinoRequest, db: AsyncSession = Depends(get_db)):
    try:
        return await PagheService(db).salva_cedolino(dipendente_id, payload.anno, payload.mese)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))


@router.get("/dipendenti/{dipendente_id}/cedolini", response_model=list[CedolinoOut])
async def list_cedolini(dipendente_id: int, db: AsyncSession = Depends(get_db)):
    return await PagheService(db).list_cedolini(dipendente_id)


@router.delete("/cedolini/{cedolino_id}", status_code=status.HTTP_204_NO_CONTENT)
async def elimina_cedolino(cedolino_id: int, db: AsyncSession = Depends(get_db)):
    try:
        ok = await PagheService(db).elimina_cedolino(cedolino_id)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(exc))
    if not ok:
        raise HTTPException(status_code=404, detail="Cedolino non trovato")


@router.post("/cedolini/{cedolino_id}/contabilizza", response_model=ContabilizzaCedolinoResponse)
async def contabilizza_cedolino(cedolino_id: int, db: AsyncSession = Depends(get_db)):
    try:
        cedolino_id, registrazione_id = await PagheService(db).contabilizza_cedolino(cedolino_id)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))
    return ContabilizzaCedolinoResponse(cedolino_id=cedolino_id, registrazione_id=registrazione_id)
