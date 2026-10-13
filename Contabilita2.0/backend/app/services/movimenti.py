from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.errors import verifica_esiste
from app.models.financial import Conto, Movimento
from app.schemas.movimenti import MovimentoCreate, MovimentoUpdate


class MovimentiService:
    def __init__(self, db: AsyncSession):
        self.db = db

    @staticmethod
    def _filtri(tipo: str | None, cerca: str | None) -> list:
        cond = []
        if tipo:
            cond.append(Movimento.tipo == tipo)
        if cerca and cerca.strip():
            testo = f"%{cerca.strip()}%"
            cond.append(or_(Movimento.descrizione.ilike(testo), Movimento.categoria.ilike(testo)))
        return cond

    async def list(
        self, skip: int = 0, limit: int = 500, tipo: str | None = None, cerca: str | None = None
    ) -> list[Movimento]:
        q = select(Movimento).where(*self._filtri(tipo, cerca))
        result = await self.db.execute(
            q.order_by(Movimento.data.desc(), Movimento.id.desc()).offset(skip).limit(limit)
        )
        return list(result.scalars().all())

    async def count(self, tipo: str | None = None, cerca: str | None = None) -> int:
        q = select(func.count(Movimento.id)).where(*self._filtri(tipo, cerca))
        return (await self.db.execute(q)).scalar_one()

    async def get(self, movimento_id: int) -> Movimento | None:
        result = await self.db.execute(
            select(Movimento).where(Movimento.id == movimento_id)
        )
        return result.scalar_one_or_none()

    async def create(self, payload: MovimentoCreate) -> Movimento:
        await verifica_esiste(self.db, Conto, payload.conto_id, "Conto")
        mov = Movimento(**payload.model_dump())
        self.db.add(mov)
        await self.db.commit()
        await self.db.refresh(mov)
        return mov

    async def update(self, movimento_id: int, payload: MovimentoUpdate) -> Movimento | None:
        mov = await self.get(movimento_id)
        if not mov:
            return None
        await verifica_esiste(self.db, Conto, payload.conto_id, "Conto")
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
