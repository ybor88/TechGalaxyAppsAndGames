"""
Pannello di moderazione (§8, Persona "Moderatore"): segnalazioni, ricorsi, utenti recidivi,
audit delle azioni amministrative e dashboard qualità (Fase 2).
Il moderatore vede i contenuti pubblici segnalati e, per i messaggi privati, solo il messaggio
segnalato (copia cifrata salvata come evidenza), mai le altre conversazioni.

© Roberto Di Flumeri
"""

from datetime import timedelta

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.metriche import metriche
from app.models import (
    AnalyticsEvent, AuditLog, Block, Event, Group, Message, Post, Report, Service, User, adesso,
)
from app.routers.segnalazioni import report_dict
from app.schemas import DecisioneModerazioneIn, EsitoRicorsoIn
from app.security import audit, revoca_tutti_refresh, solo_moderatore
from app.services.catalogo import DECISIONI_MODERAZIONE, MOTIVI_SEGNALAZIONE
from app.services.notifiche import notifica
from app.services.vicinato import utente_pubblico

router = APIRouter(prefix="/v1/admin", tags=["Moderazione"])
TESTO_RIMOSSO = "[messaggio rimosso dalla moderazione]"
SANZIONI = ("rimuovi_contenuto", "avviso", "sospendi_7_giorni", "blocca_utente")


async def _violazioni(db: AsyncSession, user_id: str | None) -> int:
    if not user_id:
        return 0
    return (await db.execute(select(func.count()).select_from(Report).where(
        Report.utente_segnalato_id == user_id, Report.stato == "risolta", Report.decisione.in_(SANZIONI),
        Report.ricorso_stato.is_distinct_from("accolto")))).scalar_one()


async def _rimuovi(db: AsyncSession, r: Report) -> None:
    tipo, oid = r.oggetto_tipo, r.oggetto_id
    if tipo == "post" and (o := await db.get(Post, oid)):
        o.stato = "rimosso"
    elif tipo == "evento" and (o := await db.get(Event, oid)):
        o.stato = "rimosso"
    elif tipo == "servizio" and (o := await db.get(Service, oid)):
        o.rimosso = True
    elif tipo == "gruppo" and (o := await db.get(Group, oid)):
        o.rimosso = True
    elif tipo == "messaggio" and (o := await db.get(Message, oid)):
        o.testo = TESTO_RIMOSSO
    elif tipo == "utente" and (o := await db.get(User, oid)):
        o.bio = None


async def _ripristina(db: AsyncSession, r: Report) -> None:
    ev = r.evidenze or {}
    tipo, oid = r.oggetto_tipo, r.oggetto_id
    if tipo == "post" and (o := await db.get(Post, oid)) and o.stato == "rimosso":
        o.stato = ev.get("stato_precedente", "pubblicato")
    elif tipo == "evento" and (o := await db.get(Event, oid)) and o.stato == "rimosso":
        o.stato = ev.get("stato_precedente", "pubblicato")
    elif tipo == "servizio" and (o := await db.get(Service, oid)):
        o.rimosso = False
    elif tipo == "gruppo" and (o := await db.get(Group, oid)):
        o.rimosso = False
    elif tipo == "messaggio" and (o := await db.get(Message, oid)) and o.testo == TESTO_RIMOSSO:
        o.testo = ev.get("testo", "")
    elif tipo == "utente" and (o := await db.get(User, oid)):
        o.bio = ev.get("testo") or None


@router.get("/reports")
async def segnalazioni(stato: str | None = "aperta", mod: User = Depends(solo_moderatore), db: AsyncSession = Depends(get_db)):
    qy = select(Report).order_by(Report.created_at.desc())
    if stato:
        qy = qy.where(Report.stato == stato)
    righe = (await db.execute(qy.limit(500))).scalars().all()
    # una scheda per contenuto: la decisione vale per tutte le segnalazioni gemelle
    gruppi: dict[tuple[str, str], list[Report]] = {}
    for r in righe:
        gruppi.setdefault((r.oggetto_tipo, r.oggetto_id) if stato == "aperta" else (r.id, ""), []).append(r)
    out = []
    for gemelle in list(gruppi.values())[:200]:
        r = min(gemelle, key=lambda x: x.created_at)
        segnalato = await db.get(User, r.utente_segnalato_id) if r.utente_segnalato_id else None
        out.append({**report_dict(r, per_moderatore=True), "segnalato": utente_pubblico(segnalato) if segnalato else None,
                    "stato_account": segnalato.stato if segnalato else None,
                    "violazioni_precedenti": await _violazioni(db, r.utente_segnalato_id),
                    "segnalazioni_stesso_contenuto": len(gemelle),
                    "motivi": sorted({MOTIVI_SEGNALAZIONE.get(x.motivo, x.motivo) for x in gemelle}),
                    "commenti": [x.dettagli for x in sorted(gemelle, key=lambda x: x.created_at) if x.dettagli]})
    return out


@router.post("/reports/{report_id}/decidi")
async def decidi(report_id: str, dati: DecisioneModerazioneIn, mod: User = Depends(solo_moderatore),
                 db: AsyncSession = Depends(get_db)):
    r = await db.get(Report, report_id)
    if r is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Segnalazione non trovata")
    if r.stato != "aperta":
        raise HTTPException(status.HTTP_409_CONFLICT, "Segnalazione già decisa")
    segnalato = await db.get(User, r.utente_segnalato_id) if r.utente_segnalato_id else None
    if segnalato and segnalato.ruolo == "moderatore" and dati.decisione in ("sospendi_7_giorni", "blocca_utente"):
        raise HTTPException(status.HTTP_409_CONFLICT, "Un moderatore non può essere sospeso da questo pannello")
    if dati.decisione == "rimuovi_contenuto":
        await _rimuovi(db, r)
    elif dati.decisione == "sospendi_7_giorni" and segnalato:
        segnalato.stato, segnalato.sospeso_fino = "sospeso", adesso() + timedelta(days=7)
    elif dati.decisione == "blocca_utente" and segnalato:
        segnalato.stato = "bloccato"
        await revoca_tutti_refresh(db, segnalato.id)
    # la stessa decisione chiude tutte le segnalazioni aperte sullo stesso contenuto
    gemelle = (await db.execute(select(Report).where(
        Report.oggetto_tipo == r.oggetto_tipo, Report.oggetto_id == r.oggetto_id, Report.stato == "aperta"))).scalars().all()
    for g in gemelle:
        g.stato = "archiviata" if dati.decisione == "nessuna_violazione" else "risolta"
        g.decisione, g.nota_decisione, g.moderatore_id, g.risolta_at = dati.decisione, dati.nota, mod.id, adesso()
        if g.segnalante_id:
            await notifica(db, g.segnalante_id, "moderazione", "Esito della tua segnalazione",
                           f"{DECISIONI_MODERAZIONE[dati.decisione]}. Grazie per aver aiutato il quartiere.", "/app/privacy#segnalazioni")
    if segnalato and dati.decisione != "nessuna_violazione":
        testo = DECISIONI_MODERAZIONE[dati.decisione] + (f". Motivo: {dati.nota}" if dati.nota else "")
        if dati.decisione in ("rimuovi_contenuto", "avviso", "sospendi_7_giorni"):
            testo += ". Puoi presentare ricorso da Privacy e dati."
        await notifica(db, segnalato.id, "moderazione", "Decisione della moderazione", testo, "/app/privacy#segnalazioni")
    audit(db, mod.id, f"moderazione_{dati.decisione}", r.id, dati.nota)
    await db.commit()
    return report_dict(r, per_moderatore=True)


@router.get("/ricorsi")
async def ricorsi(mod: User = Depends(solo_moderatore), db: AsyncSession = Depends(get_db)):
    righe = (await db.execute(select(Report).where(Report.ricorso_stato == "aperto").order_by(Report.risolta_at))).scalars()
    out = []
    for r in righe:
        segnalato = await db.get(User, r.utente_segnalato_id) if r.utente_segnalato_id else None
        out.append({**report_dict(r, per_moderatore=True), "segnalato": utente_pubblico(segnalato) if segnalato else None})
    return out


@router.post("/reports/{report_id}/ricorso")
async def decidi_ricorso(report_id: str, dati: EsitoRicorsoIn, mod: User = Depends(solo_moderatore),
                         db: AsyncSession = Depends(get_db)):
    r = await db.get(Report, report_id)
    if r is None or r.ricorso_stato != "aperto":
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Ricorso non trovato")
    if r.moderatore_id == mod.id:
        # principio dei quattro occhi: il ricorso lo decide un moderatore diverso, se disponibile
        altri = (await db.execute(select(func.count()).select_from(User).where(
            User.ruolo == "moderatore", User.id != mod.id, User.stato == "attivo"))).scalar_one()
        if altri:
            raise HTTPException(status.HTTP_409_CONFLICT, "Il ricorso deve essere esaminato da un altro moderatore")
    r.ricorso_stato = dati.esito
    segnalato = await db.get(User, r.utente_segnalato_id) if r.utente_segnalato_id else None
    if dati.esito == "accolto":
        if r.decisione == "rimuovi_contenuto":
            await _ripristina(db, r)
        if r.decisione == "sospendi_7_giorni" and segnalato and segnalato.stato == "sospeso":
            segnalato.stato, segnalato.sospeso_fino = "attivo", None
    if segnalato:
        await notifica(db, segnalato.id, "moderazione", f"Ricorso {dati.esito}",
                       "La decisione è stata annullata." if dati.esito == "accolto"
                       else "La decisione resta valida." + (f" {dati.nota}" if dati.nota else ""), "/app/privacy#segnalazioni")
    audit(db, mod.id, f"ricorso_{dati.esito}", r.id, dati.nota)
    await db.commit()
    return report_dict(r, per_moderatore=True)


@router.get("/utenti-recidivi")
async def recidivi(mod: User = Depends(solo_moderatore), db: AsyncSession = Depends(get_db)):
    righe = (await db.execute(select(Report.utente_segnalato_id, func.count()).where(
        Report.stato == "risolta", Report.decisione.in_(SANZIONI), Report.ricorso_stato.is_distinct_from("accolto"),
        Report.utente_segnalato_id.is_not(None)).group_by(Report.utente_segnalato_id)
        .having(func.count() >= 2).order_by(func.count().desc()))).all()
    out = []
    for uid, n in righe:
        u = await db.get(User, uid)
        if u:
            out.append({**utente_pubblico(u), "violazioni": n, "stato": u.stato, "sospeso_fino": u.sospeso_fino})
    return out


@router.post("/utenti/{user_id}/sblocca")
async def sblocca(user_id: str, mod: User = Depends(solo_moderatore), db: AsyncSession = Depends(get_db)):
    u = await db.get(User, user_id)
    if u is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Utente non trovato")
    u.stato, u.sospeso_fino = "attivo", None
    audit(db, mod.id, "moderazione_sblocco", user_id)
    await notifica(db, u.id, "moderazione", "Account riattivato", "Il tuo account è di nuovo attivo.", "/app")
    await db.commit()
    return {"id": u.id, "stato": u.stato}


@router.get("/audit")
async def registro(mod: User = Depends(solo_moderatore), db: AsyncSession = Depends(get_db)):
    rows = (await db.execute(select(AuditLog).order_by(AuditLog.timestamp.desc()).limit(300))).scalars()
    return [{"attore_id": a.attore_id, "azione": a.azione, "oggetto": a.oggetto, "dettagli": a.dettagli,
             "timestamp": a.timestamp} for a in rows]


@router.get("/metriche")
async def dashboard(mod: User = Depends(solo_moderatore), db: AsyncSession = Depends(get_db)):
    """Dashboard qualità e metriche di prodotto (§12) in forma aggregata."""
    conta = lambda m, *c: select(func.count()).select_from(m).where(*c)  # noqa: E731
    ora = adesso()
    utenti = dict((await db.execute(select(User.stato, func.count()).group_by(User.stato))).all())
    segn = dict((await db.execute(select(Report.stato, func.count()).group_by(Report.stato))).all())
    decise = (await db.execute(select(Report.created_at, Report.risolta_at).where(Report.risolta_at.is_not(None)))).all()
    ore_medie = round(sum((b - a).total_seconds() for a, b in decise) / len(decise) / 3600, 1) if decise else None
    privacy = dict((await db.execute(select(AuditLog.azione, func.count()).where(
        AuditLog.azione.in_(("export_dati", "account_eliminato", "consenso"))).group_by(AuditLog.azione))).all())
    eventi = dict((await db.execute(select(AnalyticsEvent.evento, func.count()).group_by(AnalyticsEvent.evento))).all())
    totali = sum(segn.values())
    return {
        "utenti": utenti,
        "contenuti": {
            "pubblicati_7_giorni": (await db.execute(conta(Post, Post.created_at >= ora - timedelta(days=7),
                                                           Post.tipo != "gruppo"))).scalar_one(),
            "scambi_conclusi": (await db.execute(conta(Post, Post.stato == "chiuso", Post.prenotato_da.is_not(None)))).scalar_one(),
            "eventi_futuri": (await db.execute(conta(Event, Event.inizio >= ora, Event.stato == "pubblicato"))).scalar_one(),
            "gruppi": (await db.execute(conta(Group, Group.rimosso.is_(False)))).scalar_one(),
        },
        "fiducia": {
            "segnalazioni": segn,
            "tasso_risoluzione": round((segn.get("risolta", 0) + segn.get("archiviata", 0)) / totali, 3) if totali else None,
            "ore_medie_risoluzione": ore_medie,
            "blocchi_tra_utenti": (await db.execute(conta(Block))).scalar_one(),
            "ricorsi_aperti": (await db.execute(conta(Report, Report.ricorso_stato == "aperto"))).scalar_one(),
            "richieste_privacy": privacy,
        },
        "uso_funzionalita_con_consenso": eventi,
        "api": metriche.riepilogo(),
    }
