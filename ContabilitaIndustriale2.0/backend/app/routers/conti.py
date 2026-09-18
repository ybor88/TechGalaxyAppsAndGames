from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.schemas.conti import ContoCreate, ContoUpdate, ContoResponse
from app.services.conti import ContiService

router = APIRouter()


@router.get("/", response_model=list[ContoResponse])
async def list_conti(
    skip: int = 0,
    limit: int = 200,
    db: AsyncSession = Depends(get_db),
):
    service = ContiService(db)
    return await service.list(skip=skip, limit=limit)


@router.post("/", response_model=ContoResponse, status_code=status.HTTP_201_CREATED)
async def create_conto(
    payload: ContoCreate,
    db: AsyncSession = Depends(get_db),
):
    service = ContiService(db)
    return await service.create(payload)


@router.put("/{conto_id}", response_model=ContoResponse)
async def update_conto(
    conto_id: int,
    payload: ContoUpdate,
    db: AsyncSession = Depends(get_db),
):
    service = ContiService(db)
    conto = await service.update(conto_id, payload)
    if not conto:
        raise HTTPException(status_code=404, detail="Conto non trovato")
    return conto


@router.delete("/{conto_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_conto(
    conto_id: int,
    db: AsyncSession = Depends(get_db),
):
    service = ContiService(db)
    ok = await service.delete(conto_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Conto non trovato")
