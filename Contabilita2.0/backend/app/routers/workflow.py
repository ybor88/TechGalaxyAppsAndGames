from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.schemas.workflow import (
    ApprovaPasso,
    TaskCreate,
    TaskResponse,
    TaskUpdate,
    WorkflowSummary,
)
from app.services.workflow import WorkflowService

router = APIRouter()


# ── Summary ──────────────────────────────────────────────────────────────────

@router.get("/summary", response_model=WorkflowSummary)
async def get_summary(db: AsyncSession = Depends(get_db)):
    service = WorkflowService(db)
    return await service.get_summary()


# ── Task CRUD ─────────────────────────────────────────────────────────────────

@router.get("/tasks", response_model=list[TaskResponse])
async def list_tasks(
    tipo: str | None = Query(None),
    stato: str | None = Query(None),
    assegnato_a: str | None = Query(None),
    priorita: str | None = Query(None),
    db: AsyncSession = Depends(get_db),
):
    service = WorkflowService(db)
    return await service.list_tasks(tipo=tipo, stato=stato, assegnato_a=assegnato_a, priorita=priorita)


@router.post("/tasks", response_model=TaskResponse, status_code=status.HTTP_201_CREATED)
async def create_task(
    payload: TaskCreate,
    db: AsyncSession = Depends(get_db),
):
    service = WorkflowService(db)
    return await service.create_task(payload)


@router.get("/tasks/{task_id}", response_model=TaskResponse)
async def get_task(task_id: int, db: AsyncSession = Depends(get_db)):
    service = WorkflowService(db)
    task = await service.get_task(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Task non trovato")
    return task


@router.put("/tasks/{task_id}", response_model=TaskResponse)
async def update_task(
    task_id: int,
    payload: TaskUpdate,
    db: AsyncSession = Depends(get_db),
):
    service = WorkflowService(db)
    task = await service.update_task(task_id, payload)
    if not task:
        raise HTTPException(status_code=404, detail="Task non trovato")
    return task


@router.delete("/tasks/{task_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_task(task_id: int, db: AsyncSession = Depends(get_db)):
    service = WorkflowService(db)
    ok = await service.delete_task(task_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Task non trovato")


# ── Passi approvazione ────────────────────────────────────────────────────────

@router.post(
    "/tasks/{task_id}/passi/{passo_id}/approva",
    response_model=TaskResponse,
)
async def approva_passo(
    task_id: int,
    passo_id: int,
    payload: ApprovaPasso,
    db: AsyncSession = Depends(get_db),
):
    service = WorkflowService(db)
    task = await service.approva_passo(task_id, passo_id, payload)
    if not task:
        raise HTTPException(status_code=404, detail="Task o passo non trovato")
    return task
