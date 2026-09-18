from decimal import Decimal
from datetime import date, datetime

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.crm import OpportunitaPipeline, Scadenza, StoricoPagamento
from app.models.fatturazione import Anagrafica
from app.schemas.crm import (
    AffidabilitaCliente,
    CrmSummary,
    OpportunitaCreate,
    OpportunitaUpdate,
    ScadenzaCreate,
    ScadenzaUpdate,
    StoricoPagamentoCreate,
)


class CrmService:
    def __init__(self, db: AsyncSession):
        self.db = db

    # ── Storico Pagamenti ────────────────────────────────────────────────────

    async def list_storico(
        self, anagrafica_id: int | None = None, skip: int = 0, limit: int = 200
    ) -> list[StoricoPagamento]:
        q = (
            select(StoricoPagamento)
            .order_by(StoricoPagamento.data_pagamento.desc())
            .offset(skip)
            .limit(limit)
        )
        if anagrafica_id:
            q = q.where(StoricoPagamento.anagrafica_id == anagrafica_id)
        result = await self.db.execute(q)
        return list(result.scalars().all())

    async def get_storico(self, storico_id: int) -> StoricoPagamento | None:
        result = await self.db.execute(
            select(StoricoPagamento).where(StoricoPagamento.id == storico_id)
        )
        return result.scalar_one_or_none()

    async def create_storico(self, payload: StoricoPagamentoCreate) -> StoricoPagamento:
        record = StoricoPagamento(**payload.model_dump())
        self.db.add(record)
        await self.db.commit()
        await self.db.refresh(record)
        return record

    async def delete_storico(self, storico_id: int) -> bool:
        record = await self.get_storico(storico_id)
        if not record:
            return False
        await self.db.delete(record)
        await self.db.commit()
        return True

    # ── Affidabilità ─────────────────────────────────────────────────────────

    async def get_affidabilita(self, anagrafica_id: int) -> AffidabilitaCliente | None:
        ana_result = await self.db.execute(
            select(Anagrafica).where(Anagrafica.id == anagrafica_id)
        )
        ana = ana_result.scalar_one_or_none()
        if not ana:
            return None

        pagamenti = await self.list_storico(anagrafica_id=anagrafica_id, limit=1000)
        totale = len(pagamenti)
        puntuali = sum(1 for p in pagamenti if p.giorni_ritardo <= 0)
        in_ritardo = totale - puntuali
        media_ritardo = (
            sum(p.giorni_ritardo for p in pagamenti if p.giorni_ritardo > 0) / in_ritardo
            if in_ritardo > 0
            else 0.0
        )

        # Score: parte da 100, -5 per ogni pagamento in ritardo (min 0)
        # -1 per ogni giorno medio di ritardo (max malus 30)
        score = max(
            0,
            100
            - (in_ritardo * 5)
            - min(30, int(media_ritardo)),
        )

        if score >= 85:
            livello = "ottimo"
        elif score >= 65:
            livello = "buono"
        elif score >= 45:
            livello = "sufficiente"
        else:
            livello = "scarso"

        return AffidabilitaCliente(
            anagrafica_id=anagrafica_id,
            nome=ana.nome,
            totale_pagamenti=totale,
            pagamenti_puntuali=puntuali,
            pagamenti_in_ritardo=in_ritardo,
            media_giorni_ritardo=round(media_ritardo, 1),
            score=score,
            livello=livello,
        )

    # ── Scadenze ─────────────────────────────────────────────────────────────

    async def list_scadenze(
        self,
        stato: str | None = None,
        tipo: str | None = None,
        anagrafica_id: int | None = None,
    ) -> list[Scadenza]:
        q = select(Scadenza).order_by(Scadenza.data_scadenza)
        if stato:
            q = q.where(Scadenza.stato == stato)
        if tipo:
            q = q.where(Scadenza.tipo == tipo)
        if anagrafica_id:
            q = q.where(Scadenza.anagrafica_id == anagrafica_id)
        result = await self.db.execute(q)
        return list(result.scalars().all())

    async def get_scadenza(self, scadenza_id: int) -> Scadenza | None:
        result = await self.db.execute(
            select(Scadenza).where(Scadenza.id == scadenza_id)
        )
        return result.scalar_one_or_none()

    async def create_scadenza(self, payload: ScadenzaCreate) -> Scadenza:
        scadenza = Scadenza(**payload.model_dump())
        self.db.add(scadenza)
        await self.db.commit()
        await self.db.refresh(scadenza)
        return scadenza

    async def update_scadenza(
        self, scadenza_id: int, payload: ScadenzaUpdate
    ) -> Scadenza | None:
        scadenza = await self.get_scadenza(scadenza_id)
        if not scadenza:
            return None
        for campo, valore in payload.model_dump(exclude_none=True).items():
            setattr(scadenza, campo, valore)
        await self.db.commit()
        await self.db.refresh(scadenza)
        return scadenza

    async def delete_scadenza(self, scadenza_id: int) -> bool:
        scadenza = await self.get_scadenza(scadenza_id)
        if not scadenza:
            return False
        await self.db.delete(scadenza)
        await self.db.commit()
        return True

    # ── Pipeline Commerciale ─────────────────────────────────────────────────

    async def list_pipeline(self, fase: str | None = None) -> list[OpportunitaPipeline]:
        q = select(OpportunitaPipeline).order_by(
            OpportunitaPipeline.updated_at.desc()
        )
        if fase:
            q = q.where(OpportunitaPipeline.fase == fase)
        result = await self.db.execute(q)
        return list(result.scalars().all())

    async def get_opportunita(self, op_id: int) -> OpportunitaPipeline | None:
        result = await self.db.execute(
            select(OpportunitaPipeline).where(OpportunitaPipeline.id == op_id)
        )
        return result.scalar_one_or_none()

    async def create_opportunita(self, payload: OpportunitaCreate) -> OpportunitaPipeline:
        op = OpportunitaPipeline(**payload.model_dump())
        self.db.add(op)
        await self.db.commit()
        await self.db.refresh(op)
        return op

    async def update_opportunita(
        self, op_id: int, payload: OpportunitaUpdate
    ) -> OpportunitaPipeline | None:
        op = await self.get_opportunita(op_id)
        if not op:
            return None
        for campo, valore in payload.model_dump(exclude_none=True).items():
            setattr(op, campo, valore)
        op.updated_at = datetime.utcnow()
        await self.db.commit()
        await self.db.refresh(op)
        return op

    async def delete_opportunita(self, op_id: int) -> bool:
        op = await self.get_opportunita(op_id)
        if not op:
            return False
        await self.db.delete(op)
        await self.db.commit()
        return True

    # ── Summary ──────────────────────────────────────────────────────────────

    async def get_summary(self) -> CrmSummary:
        today = date.today()

        clienti_r = await self.db.execute(
            select(func.count(Anagrafica.id)).where(
                Anagrafica.tipo.in_(["cliente", "entrambi"])
            )
        )
        fornitori_r = await self.db.execute(
            select(func.count(Anagrafica.id)).where(
                Anagrafica.tipo.in_(["fornitore", "entrambi"])
            )
        )
        scad_aperte_r = await self.db.execute(
            select(func.count(Scadenza.id)).where(
                Scadenza.stato == "aperta",
                Scadenza.data_scadenza >= today,
            )
        )
        scad_scadute_r = await self.db.execute(
            select(func.count(Scadenza.id)).where(
                Scadenza.stato == "aperta",
                Scadenza.data_scadenza < today,
            )
        )

        fasi_aperte = [
            "prospecting", "qualifica", "proposta", "trattativa"
        ]
        pipeline_r = await self.db.execute(
            select(
                func.count(OpportunitaPipeline.id),
                func.coalesce(
                    func.sum(OpportunitaPipeline.valore_stimato), 0
                ),
            ).where(OpportunitaPipeline.fase.in_(fasi_aperte))
        )
        pipeline_row = pipeline_r.one()

        return CrmSummary(
            totale_clienti=clienti_r.scalar_one() or 0,
            totale_fornitori=fornitori_r.scalar_one() or 0,
            scadenze_aperte=scad_aperte_r.scalar_one() or 0,
            scadenze_scadute=scad_scadute_r.scalar_one() or 0,
            opportunita_aperte=pipeline_row[0] or 0,
            valore_pipeline_attivo=Decimal(str(pipeline_row[1] or "0")),
        )
