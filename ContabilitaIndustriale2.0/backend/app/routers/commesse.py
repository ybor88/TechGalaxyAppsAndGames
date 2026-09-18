from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.schemas.commesse import (
    CommessaCreate, CommessaUpdate, CommessaOut, ChiudiCommessaRequest,
    RigaCostoCommessaCreate, RigaCostoCommessaOut,
    RiepilogoCostiCommessa, ScostamentiCommessaResponse,
)
from app.services.commesse import CommesseService

router = APIRouter()


@router.get("/", response_model=list[CommessaOut])
async def list_commesse(stato: str | None = Query(None), db: AsyncSession = Depends(get_db)):
    return await CommesseService(db).list_commesse(stato)


@router.post("/", response_model=CommessaOut, status_code=status.HTTP_201_CREATED)
async def crea_commessa(payload: CommessaCreate, db: AsyncSession = Depends(get_db)):
    try:
        return await CommesseService(db).crea_commessa(payload)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))


@router.get("/{commessa_id}", response_model=CommessaOut)
async def get_commessa(commessa_id: int, db: AsyncSession = Depends(get_db)):
    commessa = await CommesseService(db).get_commessa(commessa_id)
    if commessa is None:
        raise HTTPException(status_code=404, detail="Commessa non trovata")
    return commessa


@router.put("/{commessa_id}", response_model=CommessaOut)
async def aggiorna_commessa(commessa_id: int, payload: CommessaUpdate, db: AsyncSession = Depends(get_db)):
    commessa = await CommesseService(db).aggiorna_commessa(commessa_id, payload)
    if commessa is None:
        raise HTTPException(status_code=404, detail="Commessa non trovata")
    return commessa


@router.post("/{commessa_id}/chiudi", response_model=CommessaOut)
async def chiudi_commessa(commessa_id: int, payload: ChiudiCommessaRequest, db: AsyncSession = Depends(get_db)):
    try:
        commessa = await CommesseService(db).chiudi_commessa(commessa_id, payload)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))
    if commessa is None:
        raise HTTPException(status_code=404, detail="Commessa non trovata")
    return commessa


@router.delete("/{commessa_id}", status_code=status.HTTP_204_NO_CONTENT)
async def elimina_commessa(commessa_id: int, db: AsyncSession = Depends(get_db)):
    ok = await CommesseService(db).elimina_commessa(commessa_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Commessa non trovata")


@router.get("/{commessa_id}/costi", response_model=list[RigaCostoCommessaOut])
async def list_righe_costo(commessa_id: int, db: AsyncSession = Depends(get_db)):
    return await CommesseService(db).list_righe_costo(commessa_id)


@router.post("/{commessa_id}/costi", response_model=RigaCostoCommessaOut, status_code=status.HTTP_201_CREATED)
async def aggiungi_riga_costo(commessa_id: int, payload: RigaCostoCommessaCreate, db: AsyncSession = Depends(get_db)):
    try:
        return await CommesseService(db).aggiungi_riga_costo(commessa_id, payload)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))


@router.delete("/costi/{riga_id}", status_code=status.HTTP_204_NO_CONTENT)
async def elimina_riga_costo(riga_id: int, db: AsyncSession = Depends(get_db)):
    try:
        ok = await CommesseService(db).elimina_riga_costo(riga_id)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(exc))
    if not ok:
        raise HTTPException(status_code=404, detail="Riga di costo non trovata")


@router.get("/{commessa_id}/riepilogo", response_model=RiepilogoCostiCommessa)
async def riepilogo_costi(commessa_id: int, db: AsyncSession = Depends(get_db)):
    try:
        return await CommesseService(db).riepilogo_costi(commessa_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc))


@router.get("/{commessa_id}/scostamenti", response_model=ScostamentiCommessaResponse)
async def scostamenti(commessa_id: int, db: AsyncSession = Depends(get_db)):
    try:
        return await CommesseService(db).scostamenti(commessa_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc))
