from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.financial import Movimento
from app.schemas.movimenti import MovimentoCreate, MovimentoUpdate


class MovimentiService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def list(self, skip: int = 0, limit: int = 500) -> list[Movimento]:
        result = await self.db.execute(
            select(Movimento).order_by(Movimento.data.desc()).offset(skip).limit(limit)
        )
        return list(result.scalars().all())

    async def get(self, movimento_id: int) -> Movimento | None:
        result = await self.db.execute(
            select(Movimento).where(Movimento.id == movimento_id)
        )
        return result.scalar_one_or_none()

    async def create(self, payload: MovimentoCreate) -> Movimento:
        mov = Movimento(**payload.model_dump())
        self.db.add(mov)
        await self.db.commit()
        await self.db.refresh(mov)
        return mov

    async def update(self, movimento_id: int, payload: MovimentoUpdate) -> Movimento | None:
        mov = await self.get(movimento_id)
        if not mov:
            return None
        for campo, valore in payload.model_dump(exclude_none=True).items():
            setattr(mov, campo, valore)
        await self.db.commit()
        await self.db.refresh(mov)
        return mov

    async def delete(self, movimento_id: int) -> bool:
        mov = await self.get(movimento_id)
        if not mov:
            return False
        await self.db.delete(mov)
        await self.db.commit()
        return True
