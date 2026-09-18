from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.schemas.prodotti import (
    ProdottoCreate, ProdottoUpdate, ProdottoOut,
    DistintaBaseRigaCreate, DistintaBaseResponse,
    MovimentoMagazzinoCreate, MovimentoMagazzinoOut, GiacenzaMagazzino,
)
from app.services.prodotti import ProdottiService

router = APIRouter()


@router.get("/", response_model=list[ProdottoOut])
async def list_prodotti(
    tipo: str | None = Query(None),
    solo_attivi: bool = Query(False),
    db: AsyncSession = Depends(get_db),
):
    return await ProdottiService(db).list_prodotti(tipo=tipo, solo_attivi=solo_attivi)


@router.post("/", response_model=ProdottoOut, status_code=status.HTTP_201_CREATED)
async def crea_prodotto(payload: ProdottoCreate, db: AsyncSession = Depends(get_db)):
    return await ProdottiService(db).crea_prodotto(payload)


@router.get("/{prodotto_id}", response_model=ProdottoOut)
async def get_prodotto(prodotto_id: int, db: AsyncSession = Depends(get_db)):
    prodotto = await ProdottiService(db).get_prodotto(prodotto_id)
    if prodotto is None:
        raise HTTPException(status_code=404, detail="Prodotto non trovato")
    return prodotto


@router.put("/{prodotto_id}", response_model=ProdottoOut)
async def aggiorna_prodotto(prodotto_id: int, payload: ProdottoUpdate, db: AsyncSession = Depends(get_db)):
    prodotto = await ProdottiService(db).aggiorna_prodotto(prodotto_id, payload)
    if prodotto is None:
        raise HTTPException(status_code=404, detail="Prodotto non trovato")
    return prodotto


@router.delete("/{prodotto_id}", status_code=status.HTTP_204_NO_CONTENT)
async def elimina_prodotto(prodotto_id: int, db: AsyncSession = Depends(get_db)):
    ok = await ProdottiService(db).elimina_prodotto(prodotto_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Prodotto non trovato")


@router.get("/{prodotto_id}/distinta-base", response_model=DistintaBaseResponse)
async def get_distinta_base(prodotto_id: int, db: AsyncSession = Depends(get_db)):
    distinta = await ProdottiService(db).get_distinta_base(prodotto_id)
    if distinta is None:
        raise HTTPException(status_code=404, detail="Prodotto non trovato")
    return distinta


@router.post("/{prodotto_id}/distinta-base", status_code=status.HTTP_201_CREATED)
async def aggiungi_componente(prodotto_id: int, payload: DistintaBaseRigaCreate, db: AsyncSession = Depends(get_db)):
    try:
        return await ProdottiService(db).aggiungi_componente(prodotto_id, payload)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))


@router.delete("/distinta-base/{riga_id}", status_code=status.HTTP_204_NO_CONTENT)
async def rimuovi_componente(riga_id: int, db: AsyncSession = Depends(get_db)):
    ok = await ProdottiService(db).rimuovi_componente(riga_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Riga non trovata")


@router.get("/magazzino/movimenti", response_model=list[MovimentoMagazzinoOut])
async def list_movimenti(
    prodotto_id: int | None = Query(None),
    skip: int = Query(0),
    limit: int = Query(200),
    db: AsyncSession = Depends(get_db),
):
    return await ProdottiService(db).list_movimenti(prodotto_id, skip, limit)


@router.post("/magazzino/movimenti", response_model=MovimentoMagazzinoOut, status_code=status.HTTP_201_CREATED)
async def registra_movimento(payload: MovimentoMagazzinoCreate, db: AsyncSession = Depends(get_db)):
    try:
        return await ProdottiService(db).registra_movimento(payload)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))


@router.get("/magazzino/giacenze", response_model=list[GiacenzaMagazzino])
async def get_giacenze(solo_sotto_scorta: bool = Query(False), db: AsyncSession = Depends(get_db)):
    return await ProdottiService(db).get_giacenze(solo_sotto_scorta)
