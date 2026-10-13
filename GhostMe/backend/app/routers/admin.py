"""
Moderazione e supporto (§8): segnalazioni, blocco utenti, audit e metriche aggregate.
Il moderatore non vede contenuti personali (dichiarazioni, conversazioni, diario).

© Roberto Di Flumeri
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.metriche import metriche
from app.models import AnalyticsEvent, AuditLog, Report, ShareGrant, User, adesso
from app.schemas import RisoluzioneIn
from app.security import audit, revoca_tutti_refresh, solo_moderatore

router = APIRouter(prefix="/v1/admin", tags=["Moderazione"])


@router.get("/reports")
async def segnalazioni(stato: str | None = None, mod: User = Depends(solo_moderatore), db: AsyncSession = Depends(get_db)):
    q = select(Report).order_by(Report.created_at.desc())
    if stato:
        q = q.where(Report.stato == stato)
    rows = (await db.execute(q.limit(200))).scalars().all()
    # dettagli della segnalazione visibili (scritti da chi segnala), non i contenuti del profilo
    return [{"id": r.id, "share_id": r.share_id, "utente_segnalato_id": r.utente_segnalato_id, "motivo": r.motivo,
             "dettagli": r.dettagli, "stato": r.stato, "azione": r.azione, "created_at": r.created_at,
             "risolta_at": r.risolta_at} for r in rows]


@router.post("/reports/{report_id}/risolvi")
async def risolvi(report_id: str, dati: RisoluzioneIn, mod: User = Depends(solo_moderatore), db: AsyncSession = Depends(get_db)):
    r = await db.get(Report, report_id)
    if r is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Segnalazione non trovata")
    if dati.azione == "revoca_condivisione" and r.share_id:
        g = await db.get(ShareGrant, r.share_id)
        if g:
            g.bloccato_moderazione = True
    if dati.azione == "blocca_utente" and r.utente_segnalato_id:
        u = await db.get(User, r.utente_segnalato_id)
        if u and u.ruolo != "moderatore":
            u.stato = "bloccato"
            await revoca_tutti_refresh(db, u.id)
    r.stato, r.azione, r.risolta_at = "risolta", dati.azione, adesso()
    audit(db, mod.id, f"moderazione_{dati.azione}", r.id, dati.nota)
    await db.commit()
    return {"id": r.id, "stato": r.stato, "azione": r.azione}


@router.post("/utenti/{user_id}/sblocca")
async def sblocca(user_id: str, mod: User = Depends(solo_moderatore), db: AsyncSession = Depends(get_db)):
    u = await db.get(User, user_id)
    if u is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Utente non trovato")
    u.stato = "attivo"
    audit(db, mod.id, "moderazione_sblocco", user_id)
    await db.commit()
    return {"id": u.id, "stato": u.stato}


@router.get("/audit")
async def registro(mod: User = Depends(solo_moderatore), db: AsyncSession = Depends(get_db)):
    rows = (await db.execute(select(AuditLog).order_by(AuditLog.timestamp.desc()).limit(300))).scalars()
    return [{"attore_id": a.attore_id, "azione": a.azione, "oggetto": a.oggetto, "dettagli": a.dettagli,
             "timestamp": a.timestamp} for a in rows]


@router.get("/metriche")
async def statistiche(mod: User = Depends(solo_moderatore), db: AsyncSession = Depends(get_db)):
    utenti = (await db.execute(select(User.stato, func.count()).group_by(User.stato))).all()
    eventi = (await db.execute(select(AnalyticsEvent.evento, func.count()).group_by(AnalyticsEvent.evento))).all()
    segn = (await db.execute(select(Report.stato, func.count()).group_by(Report.stato))).all()
    return {
        "utenti": {s: n for s, n in utenti},
        "uso_funzionalita_con_consenso": {e: n for e, n in eventi},
        "segnalazioni": {s: n for s, n in segn},
        "api": metriche.riepilogo(),
    }
