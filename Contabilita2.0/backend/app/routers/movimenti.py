from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.schemas.movimenti import MovimentoCreate, MovimentoUpdate, MovimentoResponse
from app.services.movimenti import MovimentiService

router = APIRouter()


@router.get("/", response_model=list[MovimentoResponse])
async def list_movimenti(
    skip: int = 0,
    limit: int = 500,
    db: AsyncSession = Depends(get_db),
):
    service = MovimentiService(db)
    return await service.list(skip=skip, limit=limit)


@router.post("/", response_model=MovimentoResponse, status_code=status.HTTP_201_CREATED)
async def create_movimento(
    payload: MovimentoCreate,
    db: AsyncSession = Depends(get_db),
):
    service = MovimentiService(db)
    return await service.create(payload)


@router.put("/{movimento_id}", response_model=MovimentoResponse)
async def update_movimento(
    movimento_id: int,
    payload: MovimentoUpdate,
    db: AsyncSession = Depends(get_db),
):
    service = MovimentiService(db)
    mov = await service.update(movimento_id, payload)
    if not mov:
        raise HTTPException(status_code=404, detail="Movimento non trovato")
    return mov


@router.delete("/{movimento_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_movimento(
    movimento_id: int,
    db: AsyncSession = Depends(get_db),
):
    service = MovimentiService(db)
    ok = await service.delete(movimento_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Movimento non trovato")
