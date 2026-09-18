from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.schemas.centri_costo import (
    CentroCostoCreate, CentroCostoUpdate, CentroCostoOut,
    BaseRipartoCreate, BaseRipartoOut,
    ValoreBaseRipartoCreate, ValoreBaseRipartoOut,
    RipartoCostoIndirettoCreate, RipartoCostoIndirettoOut,
    RipartoCalcolato,
)
from app.services.centri_costo import CentriCostoService

router = APIRouter()


@router.get("/centri", response_model=list[CentroCostoOut])
async def list_centri(solo_attivi: bool = Query(False), db: AsyncSession = Depends(get_db)):
    return await CentriCostoService(db).list_centri(solo_attivi=solo_attivi)


@router.post("/centri", response_model=CentroCostoOut, status_code=status.HTTP_201_CREATED)
async def crea_centro(payload: CentroCostoCreate, db: AsyncSession = Depends(get_db)):
    try:
        return await CentriCostoService(db).crea_centro(payload)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))


@router.put("/centri/{centro_id}", response_model=CentroCostoOut)
async def aggiorna_centro(centro_id: int, payload: CentroCostoUpdate, db: AsyncSession = Depends(get_db)):
    centro = await CentriCostoService(db).aggiorna_centro(centro_id, payload)
    if centro is None:
        raise HTTPException(status_code=404, detail="Centro di costo non trovato")
    return centro


@router.delete("/centri/{centro_id}", status_code=status.HTTP_204_NO_CONTENT)
async def elimina_centro(centro_id: int, db: AsyncSession = Depends(get_db)):
    ok = await CentriCostoService(db).elimina_centro(centro_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Centro di costo non trovato")


@router.get("/basi-riparto", response_model=list[BaseRipartoOut])
async def list_basi_riparto(db: AsyncSession = Depends(get_db)):
    return await CentriCostoService(db).list_basi_riparto()


@router.post("/basi-riparto", response_model=BaseRipartoOut, status_code=status.HTTP_201_CREATED)
async def crea_base_riparto(payload: BaseRipartoCreate, db: AsyncSession = Depends(get_db)):
    return await CentriCostoService(db).crea_base_riparto(payload)


@router.get("/valori-base", response_model=list[ValoreBaseRipartoOut])
async def list_valori_base(
    base_riparto_id: int | None = Query(None),
    periodo: str | None = Query(None),
    db: AsyncSession = Depends(get_db),
):
    return await CentriCostoService(db).list_valori_base(base_riparto_id, periodo)


@router.post("/valori-base", response_model=ValoreBaseRipartoOut, status_code=status.HTTP_201_CREATED)
async def crea_valore_base(payload: ValoreBaseRipartoCreate, db: AsyncSession = Depends(get_db)):
    try:
        return await CentriCostoService(db).crea_valore_base(payload)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))


@router.delete("/valori-base/{valore_id}", status_code=status.HTTP_204_NO_CONTENT)
async def elimina_valore_base(valore_id: int, db: AsyncSession = Depends(get_db)):
    ok = await CentriCostoService(db).elimina_valore_base(valore_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Valore non trovato")


@router.get("/riparti", response_model=list[RipartoCostoIndirettoOut])
async def list_riparti(periodo: str | None = Query(None), db: AsyncSession = Depends(get_db)):
    return await CentriCostoService(db).list_riparti(periodo)


@router.post("/riparti", response_model=RipartoCostoIndirettoOut, status_code=status.HTTP_201_CREATED)
async def crea_riparto(payload: RipartoCostoIndirettoCreate, db: AsyncSession = Depends(get_db)):
    try:
        return await CentriCostoService(db).crea_riparto(payload)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))


@router.delete("/riparti/{riparto_id}", status_code=status.HTTP_204_NO_CONTENT)
async def elimina_riparto(riparto_id: int, db: AsyncSession = Depends(get_db)):
    ok = await CentriCostoService(db).elimina_riparto(riparto_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Riparto non trovato")


@router.get("/riparti/{riparto_id}/calcolo", response_model=RipartoCalcolato)
async def calcola_riparto(riparto_id: int, db: AsyncSession = Depends(get_db)):
    try:
        return await CentriCostoService(db).calcola_riparto(riparto_id)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))
