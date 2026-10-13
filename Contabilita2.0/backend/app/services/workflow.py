from datetime import date, datetime

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.models.workflow import PassoApprovazione, Task
from app.models.fatturazione import Anagrafica
from app.schemas.workflow import (
    ApprovaPasso,
    TaskCreate,
    TaskResponse,
    TaskUpdate,
    WorkflowSummary,
)


class WorkflowService:
    def __init__(self, db: AsyncSession):
        self.db = db

    # ── Query helpers ────────────────────────────────────────────────────────

    def _with_passi(self):
        return select(Task).options(selectinload(Task.passi))

    def _enrich(self, task: Task, ana_map: dict) -> TaskResponse:
        today = date.today()
        giorni = (
            (task.data_scadenza - today).days if task.data_scadenza else None
        )
        # passo corrente: primo passo in_attesa nell'ordine
        passo_corrente = None
        if task.passi:
            for p in sorted(task.passi, key=lambda x: x.ordine):
                if p.stato == "in_attesa":
                    passo_corrente = p.ordine
                    break

        resp = TaskResponse.model_validate(task)
        resp.anagrafica_nome = ana_map.get(task.anagrafica_id) if task.anagrafica_id else None
        resp.giorni_alla_scadenza = giorni
        resp.passo_corrente = passo_corrente
        return resp

    async def _ana_map(self) -> dict:
        result = await self.db.execute(select(Anagrafica))
        return {a.id: a.nome for a in result.scalars().all()}

    # ── CRUD Task ─────────────────────────────────────────────────────────────

    async def list_tasks(
        self,
        tipo: str | None = None,
        stato: str | None = None,
        assegnato_a: str | None = None,
        priorita: str | None = None,
        skip: int = 0,
        limit: int = 500,
        cerca: str | None = None,
    ) -> list[TaskResponse]:
        q = self._with_passi().order_by(Task.data_scadenza.asc().nulls_last(), Task.created_at.desc())
        for cond in self._filtri(tipo, stato, assegnato_a, priorita, cerca):
            q = q.where(cond)
        q = q.offset(skip).limit(limit)
        result = await self.db.execute(q)
        tasks = list(result.scalars().all())
        ana_map = await self._ana_map()
        return [self._enrich(t, ana_map) for t in tasks]

    @staticmethod
    def _filtri(tipo, stato, assegnato_a, priorita, cerca) -> list:
        cond = []
        if tipo:
            cond.append(Task.tipo == tipo)
        if stato:
            cond.append(Task.stato == stato)
        if assegnato_a:
            cond.append(Task.assegnato_a == assegnato_a)
        if priorita:
            cond.append(Task.priorita == priorita)
        if cerca and cerca.strip():
            testo = f"%{cerca.strip()}%"
            cond.append(Task.titolo.ilike(testo) | Task.descrizione.ilike(testo))
        return cond

    async def count_tasks(self, tipo=None, stato=None, assegnato_a=None, priorita=None, cerca=None) -> int:
        q = select(func.count(Task.id))
        for cond in self._filtri(tipo, stato, assegnato_a, priorita, cerca):
            q = q.where(cond)
        return (await self.db.execute(q)).scalar_one()

    async def get_task(self, task_id: int) -> TaskResponse | None:
        result = await self.db.execute(
            self._with_passi().where(Task.id == task_id)
        )
        task = result.scalar_one_or_none()
        if not task:
            return None
        ana_map = await self._ana_map()
        return self._enrich(task, ana_map)

    async def _get_raw(self, task_id: int) -> Task | None:
        result = await self.db.execute(
            self._with_passi().where(Task.id == task_id)
        )
        return result.scalar_one_or_none()

    async def create_task(self, payload: TaskCreate) -> TaskResponse:
        from app.errors import verifica_esiste

        await verifica_esiste(self.db, Anagrafica, payload.anagrafica_id, "Anagrafica")
        passi_data = payload.passi
        task_data = payload.model_dump(exclude={"passi"})
        task = Task(**task_data)
        self.db.add(task)
        await self.db.flush()  # ottieni task.id prima di aggiungere i passi

        for passo_in in passi_data:
            passo = PassoApprovazione(
                task_id=task.id,
                ordine=passo_in.ordine,
                approvatore=passo_in.approvatore,
            )
            self.db.add(passo)

        await self.db.commit()
        return await self.get_task(task.id)  # type: ignore[return-value]

    async def update_task(self, task_id: int, payload: TaskUpdate) -> TaskResponse | None:
        task = await self._get_raw(task_id)
        if not task:
            return None
        for campo, valore in payload.model_dump(exclude_none=True).items():
            setattr(task, campo, valore)
        task.updated_at = datetime.utcnow()
        # Se completato/approvato/rifiutato/annullato senza data_completamento, la imposta;
        # se il task viene riaperto la data di completamento non ha più senso.
        if payload.stato in ("completato", "approvato", "rifiutato", "annullato"):
            if not task.data_completamento:
                task.data_completamento = date.today()
        elif payload.stato in ("aperto", "in_corso"):
            task.data_completamento = None
        await self.db.commit()
        return await self.get_task(task_id)

    async def delete_task(self, task_id: int) -> bool:
        task = await self._get_raw(task_id)
        if not task:
            return False
        await self.db.delete(task)
        await self.db.commit()
        return True

    # ── Passi approvazione ───────────────────────────────────────────────────

    async def approva_passo(
        self, task_id: int, passo_id: int, payload: ApprovaPasso
    ) -> TaskResponse | None:
        task = await self._get_raw(task_id)
        if not task:
            return None

        passo = next((p for p in task.passi if p.id == passo_id), None)
        if not passo:
            return None
        from app.errors import Conflitto

        if task.stato not in ("aperto", "in_corso"):
            raise Conflitto(f"Il task è {task.stato}: non è più possibile approvare o rifiutare passi.")
        if passo.stato != "in_attesa":
            raise Conflitto(f"Il passo {passo.ordine} è già stato {passo.stato}.")
        precedenti = [p for p in task.passi if p.ordine < passo.ordine and p.stato != "approvato"]
        if precedenti:
            raise Conflitto(
                f"Il passo {passo.ordine} non può essere gestito prima dell'approvazione "
                f"del passo {min(p.ordine for p in precedenti)}."
            )
        passo.stato = payload.stato
        passo.commento = payload.commento
        passo.aggiornato_at = datetime.utcnow()

        # Aggiorna lo stato del task in base ai passi
        passi_ordinati = sorted(task.passi, key=lambda x: x.ordine)
        if payload.stato == "rifiutato":
            task.stato = "rifiutato"
            task.data_completamento = date.today()
        else:
            # Controlla se tutti i passi sono approvati
            tutti_approvati = all(p.stato == "approvato" for p in passi_ordinati)
            if tutti_approvati:
                task.stato = "approvato"
                task.data_completamento = date.today()

        task.updated_at = datetime.utcnow()
        await self.db.commit()
        return await self.get_task(task_id)

    # ── Summary ──────────────────────────────────────────────────────────────

    async def get_summary(self) -> WorkflowSummary:
        today = date.today()

        aperti = await self.db.execute(
            select(func.count(Task.id)).where(Task.stato == "aperto")
        )
        in_corso = await self.db.execute(
            select(func.count(Task.id)).where(Task.stato == "in_corso")
        )
        # approvazioni con almeno un passo in_attesa
        appr_attesa = await self.db.execute(
            select(func.count(Task.id.distinct())).where(
                Task.tipo == "approvazione",
                Task.stato.in_(["aperto", "in_corso"]),
            )
        )
        # reminder che scadono entro 7 giorni
        from datetime import timedelta
        reminder_scad = await self.db.execute(
            select(func.count(Task.id)).where(
                Task.tipo == "reminder",
                Task.stato.in_(["aperto", "in_corso"]),
                Task.data_scadenza <= today + timedelta(days=7),
                Task.data_scadenza >= today,
            )
        )
        acquisti = await self.db.execute(
            select(func.count(Task.id)).where(
                Task.tipo == "acquisto",
                Task.stato.in_(["aperto", "in_corso"]),
            )
        )
        scaduti = await self.db.execute(
            select(func.count(Task.id)).where(
                Task.stato.in_(["aperto", "in_corso"]),
                Task.data_scadenza < today,
            )
        )

        return WorkflowSummary(
            task_aperti=aperti.scalar_one() or 0,
            task_in_corso=in_corso.scalar_one() or 0,
            approvazioni_in_attesa=appr_attesa.scalar_one() or 0,
            reminder_in_scadenza=reminder_scad.scalar_one() or 0,
            acquisti_aperti=acquisti.scalar_one() or 0,
            task_scaduti=scaduti.scalar_one() or 0,
        )
