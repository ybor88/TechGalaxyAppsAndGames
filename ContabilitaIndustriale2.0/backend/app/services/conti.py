from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.financial import Conto
from app.schemas.conti import ContoCreate, ContoUpdate


class ContiService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def list(self, skip: int = 0, limit: int = 200) -> list[Conto]:
        result = await self.db.execute(
            select(Conto).order_by(Conto.codice).offset(skip).limit(limit)
        )
        return list(result.scalars().all())

    async def get(self, conto_id: int) -> Conto | None:
        result = await self.db.execute(
            select(Conto).where(Conto.id == conto_id)
        )
        return result.scalar_one_or_none()

    async def create(self, payload: ContoCreate) -> Conto:
        conto = Conto(**payload.model_dump())
        self.db.add(conto)
        await self.db.commit()
        await self.db.refresh(conto)
        return conto

    async def update(self, conto_id: int, payload: ContoUpdate) -> Conto | None:
        conto = await self.get(conto_id)
        if not conto:
            return None
        for campo, valore in payload.model_dump(exclude_none=True).items():
            setattr(conto, campo, valore)
        await self.db.commit()
        await self.db.refresh(conto)
        return conto

    async def delete(self, conto_id: int) -> bool:
        conto = await self.get(conto_id)
        if not conto:
            return False
        await self.db.delete(conto)
        await self.db.commit()
        return True
