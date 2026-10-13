from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.errors import Conflitto, blocca_se_in_uso
from app.models.ammortamenti import Cespite
from app.models.contabilita import RigaRegistrazione
from app.models.financial import Conto, Movimento
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

    async def _verifica_codice_libero(self, codice: str, escludi_id: int | None = None) -> None:
        q = select(Conto.id).where(Conto.codice == codice)
        if escludi_id is not None:
            q = q.where(Conto.id != escludi_id)
        if (await self.db.execute(q)).first() is not None:
            raise Conflitto(f"Esiste già un conto con codice {codice}")

    async def create(self, payload: ContoCreate) -> Conto:
        await self._verifica_codice_libero(payload.codice)
        conto = Conto(**payload.model_dump())
        self.db.add(conto)
        await self.db.commit()
        await self.db.refresh(conto)
        return conto

    async def update(self, conto_id: int, payload: ContoUpdate) -> Conto | None:
        conto = await self.get(conto_id)
        if not conto:
            return None
        if payload.codice is not None:
            await self._verifica_codice_libero(payload.codice, escludi_id=conto_id)
        for campo, valore in payload.model_dump(exclude_none=True).items():
            setattr(conto, campo, valore)
        await self.db.commit()
        await self.db.refresh(conto)
        return conto

    async def delete(self, conto_id: int) -> bool:
        conto = await self.get(conto_id)
        if not conto:
            return False
        # Un conto movimentato non si elimina: le registrazioni diventerebbero
        # illeggibili e il bilancio di verifica non quadrerebbe più.
        await blocca_se_in_uso(self.db, conto_id, f"il conto {conto.codice}", [
            (RigaRegistrazione.conto_id, "righe di prima nota"),
            (Movimento.conto_id, "movimenti"),
            (Cespite.conto_costo_id, "cespiti (conto costo)"),
            (Cespite.conto_fondo_id, "cespiti (conto fondo)"),
        ])
        await self.db.delete(conto)
        await self.db.commit()
        return True
