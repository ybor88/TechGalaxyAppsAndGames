from datetime import date

from fastapi import APIRouter, Depends, HTTPException, status, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.schemas.contabilita import (
    RegistrazioneCreate,
    RegistrazioneSummary,
    RegistrazioneDetail,
    BilancioResponse,
    LiquidazioneIVAResponse,
    InizializzaPianoContiResponse,
)
from app.services.contabilita import ContabilitaService

router = APIRouter()


# ── Prima nota / registrazioni ────────────────────────────────────────────────

@router.get("/registrazioni", response_model=list[RegistrazioneSummary])
async def list_registrazioni(
    skip: int = Query(0, ge=0),
    limit: int = Query(200, ge=1, le=1000),
    data_da: date | None = Query(None),
    data_a: date | None = Query(None),
    db: AsyncSession = Depends(get_db),
):
    return await ContabilitaService(db).list_registrazioni(
        skip=skip, limit=limit, data_da=data_da, data_a=data_a
    )


@router.post(
    "/registrazioni",
    response_model=RegistrazioneDetail,
    status_code=status.HTTP_201_CREATED,
)
async def crea_registrazione(
    payload: RegistrazioneCreate,
    db: AsyncSession = Depends(get_db),
):
    try:
        return await ContabilitaService(db).crea_registrazione(payload)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))


@router.get("/registrazioni/{reg_id}", response_model=RegistrazioneDetail)
async def get_registrazione(
    reg_id: int,
    db: AsyncSession = Depends(get_db),
):
    reg = await ContabilitaService(db).get_registrazione(reg_id)
    if reg is None:
        raise HTTPException(status_code=404, detail="Registrazione non trovata")
    return reg


@router.delete("/registrazioni/{reg_id}", status_code=status.HTTP_204_NO_CONTENT)
async def elimina_registrazione(
    reg_id: int,
    db: AsyncSession = Depends(get_db),
):
    try:
        ok = await ContabilitaService(db).elimina_registrazione(reg_id)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(exc))
    if not ok:
        raise HTTPException(status_code=404, detail="Registrazione non trovata")


@router.post("/registrazioni/{reg_id}/chiudi", status_code=status.HTTP_200_OK)
async def chiudi_registrazione(
    reg_id: int,
    db: AsyncSession = Depends(get_db),
):
    ok = await ContabilitaService(db).chiudi_registrazione(reg_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Registrazione non trovata")
    return {"message": "Registrazione chiusa"}


# ── Bilancio di verifica ──────────────────────────────────────────────────────

@router.get("/bilancio", response_model=BilancioResponse)
async def get_bilancio(db: AsyncSession = Depends(get_db)):
    return await ContabilitaService(db).get_bilancio()


# ── Liquidazione IVA ──────────────────────────────────────────────────────────

@router.get("/iva", response_model=LiquidazioneIVAResponse)
async def get_liquidazione_iva(
    data_da: date = Query(...),
    data_a: date = Query(...),
    db: AsyncSession = Depends(get_db),
):
    if data_da > data_a:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="data_da deve essere anteriore a data_a",
        )
    return await ContabilitaService(db).get_liquidazione_iva(data_da, data_a)


# ── Piano dei conti standard ──────────────────────────────────────────────────

@router.post("/init-piano-conti", response_model=InizializzaPianoContiResponse)
async def inizializza_piano_conti(db: AsyncSession = Depends(get_db)):
    return await ContabilitaService(db).inizializza_piano_conti()
