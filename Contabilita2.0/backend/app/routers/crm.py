from datetime import date

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.schemas.crm import (
    AffidabilitaCliente,
    CrmSummary,
    OpportunitaCreate,
    OpportunitaResponse,
    OpportunitaUpdate,
    ScadenzaCreate,
    ScadenzaResponse,
    ScadenzaUpdate,
    StoricoPagamentoCreate,
    StoricoPagamentoResponse,
)
from app.schemas.fatturazione import AnagraficaResponse
from app.services.crm import CrmService
from app.services.fatturazione import AnagraficheService

router = APIRouter()


# ── Summary ──────────────────────────────────────────────────────────────────

@router.get("/summary", response_model=CrmSummary)
async def get_summary(db: AsyncSession = Depends(get_db)):
    service = CrmService(db)
    return await service.get_summary()


# ── Anagrafiche CRM (clienti / fornitori) ────────────────────────────────────

@router.get("/clienti", response_model=list[AnagraficaResponse])
async def list_clienti(db: AsyncSession = Depends(get_db)):
    service = AnagraficheService(db)
    result = await service.list(tipo="cliente")
    # include anche tipo=entrambi
    result_entrambi = await service.list(tipo="entrambi")
    seen = {a.id for a in result}
    for a in result_entrambi:
        if a.id not in seen:
            result.append(a)
            seen.add(a.id)
    return sorted(result, key=lambda a: a.nome)


@router.get("/fornitori", response_model=list[AnagraficaResponse])
async def list_fornitori(db: AsyncSession = Depends(get_db)):
    service = AnagraficheService(db)
    result = await service.list(tipo="fornitore")
    result_entrambi = await service.list(tipo="entrambi")
    seen = {a.id for a in result}
    for a in result_entrambi:
        if a.id not in seen:
            result.append(a)
            seen.add(a.id)
    return sorted(result, key=lambda a: a.nome)


# ── Affidabilità ──────────────────────────────────────────────────────────────

@router.get("/affidabilita/{anagrafica_id}", response_model=AffidabilitaCliente)
async def get_affidabilita(
    anagrafica_id: int,
    db: AsyncSession = Depends(get_db),
):
    service = CrmService(db)
    result = await service.get_affidabilita(anagrafica_id)
    if not result:
        raise HTTPException(status_code=404, detail="Anagrafica non trovata")
    return result


# ── Storico Pagamenti ─────────────────────────────────────────────────────────

def _enrich_storico(record, ana_map: dict) -> StoricoPagamentoResponse:
    data = StoricoPagamentoResponse.model_validate(record)
    data.anagrafica_nome = ana_map.get(record.anagrafica_id)
    return data


@router.get("/storico-pagamenti", response_model=list[StoricoPagamentoResponse])
async def list_storico(
    anagrafica_id: int | None = Query(None),
    skip: int = Query(0, ge=0),
    limit: int = Query(200, ge=1, le=1000),
    db: AsyncSession = Depends(get_db),
):
    crm = CrmService(db)
    ana_svc = AnagraficheService(db)
    records = await crm.list_storico(anagrafica_id=anagrafica_id, skip=skip, limit=limit)
    anagrafiche = await ana_svc.list()
    ana_map = {a.id: a.nome for a in anagrafiche}
    return [_enrich_storico(r, ana_map) for r in records]


@router.post(
    "/storico-pagamenti",
    response_model=StoricoPagamentoResponse,
    status_code=status.HTTP_201_CREATED,
)
async def create_storico(
    payload: StoricoPagamentoCreate,
    db: AsyncSession = Depends(get_db),
):
    crm = CrmService(db)
    ana_svc = AnagraficheService(db)
    record = await crm.create_storico(payload)
    ana = await ana_svc.get(record.anagrafica_id)
    resp = StoricoPagamentoResponse.model_validate(record)
    resp.anagrafica_nome = ana.nome if ana else None
    return resp


@router.delete("/storico-pagamenti/{storico_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_storico(storico_id: int, db: AsyncSession = Depends(get_db)):
    crm = CrmService(db)
    ok = await crm.delete_storico(storico_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Pagamento non trovato")


# ── Scadenze ──────────────────────────────────────────────────────────────────

def _enrich_scadenza(record, ana_map: dict) -> ScadenzaResponse:
    today = date.today()
    giorni = (record.data_scadenza - today).days
    data = ScadenzaResponse.model_validate(record)
    data.anagrafica_nome = ana_map.get(record.anagrafica_id)
    data.giorni_alla_scadenza = giorni
    return data


@router.get("/scadenze", response_model=list[ScadenzaResponse])
async def list_scadenze(
    stato: str | None = Query(None),
    tipo: str | None = Query(None),
    anagrafica_id: int | None = Query(None),
    db: AsyncSession = Depends(get_db),
):
    crm = CrmService(db)
    ana_svc = AnagraficheService(db)
    records = await crm.list_scadenze(stato=stato, tipo=tipo, anagrafica_id=anagrafica_id)
    anagrafiche = await ana_svc.list()
    ana_map = {a.id: a.nome for a in anagrafiche}
    return [_enrich_scadenza(r, ana_map) for r in records]


@router.post("/scadenze", response_model=ScadenzaResponse, status_code=status.HTTP_201_CREATED)
async def create_scadenza(
    payload: ScadenzaCreate,
    db: AsyncSession = Depends(get_db),
):
    crm = CrmService(db)
    ana_svc = AnagraficheService(db)
    record = await crm.create_scadenza(payload)
    anagrafiche = await ana_svc.list()
    ana_map = {a.id: a.nome for a in anagrafiche}
    return _enrich_scadenza(record, ana_map)


@router.put("/scadenze/{scadenza_id}", response_model=ScadenzaResponse)
async def update_scadenza(
    scadenza_id: int,
    payload: ScadenzaUpdate,
    db: AsyncSession = Depends(get_db),
):
    crm = CrmService(db)
    ana_svc = AnagraficheService(db)
    record = await crm.update_scadenza(scadenza_id, payload)
    if not record:
        raise HTTPException(status_code=404, detail="Scadenza non trovata")
    anagrafiche = await ana_svc.list()
    ana_map = {a.id: a.nome for a in anagrafiche}
    return _enrich_scadenza(record, ana_map)


@router.delete("/scadenze/{scadenza_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_scadenza(scadenza_id: int, db: AsyncSession = Depends(get_db)):
    crm = CrmService(db)
    ok = await crm.delete_scadenza(scadenza_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Scadenza non trovata")


# ── Pipeline Commerciale ──────────────────────────────────────────────────────

def _enrich_opportunita(record, ana_map: dict) -> OpportunitaResponse:
    data = OpportunitaResponse.model_validate(record)
    data.anagrafica_nome = ana_map.get(record.anagrafica_id)
    return data


@router.get("/pipeline", response_model=list[OpportunitaResponse])
async def list_pipeline(
    fase: str | None = Query(None),
    db: AsyncSession = Depends(get_db),
):
    crm = CrmService(db)
    ana_svc = AnagraficheService(db)
    records = await crm.list_pipeline(fase=fase)
    anagrafiche = await ana_svc.list()
    ana_map = {a.id: a.nome for a in anagrafiche}
    return [_enrich_opportunita(r, ana_map) for r in records]


@router.post("/pipeline", response_model=OpportunitaResponse, status_code=status.HTTP_201_CREATED)
async def create_opportunita(
    payload: OpportunitaCreate,
    db: AsyncSession = Depends(get_db),
):
    crm = CrmService(db)
    ana_svc = AnagraficheService(db)
    record = await crm.create_opportunita(payload)
    anagrafiche = await ana_svc.list()
    ana_map = {a.id: a.nome for a in anagrafiche}
    return _enrich_opportunita(record, ana_map)


@router.put("/pipeline/{op_id}", response_model=OpportunitaResponse)
async def update_opportunita(
    op_id: int,
    payload: OpportunitaUpdate,
    db: AsyncSession = Depends(get_db),
):
    crm = CrmService(db)
    ana_svc = AnagraficheService(db)
    record = await crm.update_opportunita(op_id, payload)
    if not record:
        raise HTTPException(status_code=404, detail="Opportunità non trovata")
    anagrafiche = await ana_svc.list()
    ana_map = {a.id: a.nome for a in anagrafiche}
    return _enrich_opportunita(record, ana_map)


@router.delete("/pipeline/{op_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_opportunita(op_id: int, db: AsyncSession = Depends(get_db)):
    crm = CrmService(db)
    ok = await crm.delete_opportunita(op_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Opportunità non trovata")
