"""Esportazione dati e cancellazione account self-service (§8). © Roberto Di Flumeri"""

import hashlib
import json
import secrets
from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import and_, delete, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models import (
    AnalyticsEvent, Area, Block, Consent, Conversation, DeletionReceipt, Event, EventParticipation, Group, GroupMember, Message,
    Notification, NotificationPrefs, Photo, Post, Report, Reputation, Service, ServiceRecommendation, User, adesso, nuovo_id,
)
from app.schemas import CancellazioneIn
from app.security import audit, utente_corrente, verifica_password
from app.services import foto
from app.services.catalogo import TIPI_POST
from app.services.vicinato import archivia_segnalazioni, pseudonimo

router = APIRouter(prefix="/v1/account", tags=["Account"])


async def _tutti(db: AsyncSession, modello, *condizioni):
    return (await db.execute(select(modello).where(*condizioni))).scalars().all()


def _iso(d):
    return d.isoformat() if d else None


async def raccogli_dati(db: AsyncSession, user: User) -> dict:
    area = (await db.execute(select(Area).where(Area.user_id == user.id))).scalar_one_or_none()
    prefs = await db.get(NotificationPrefs, user.id)
    conversazioni = await _tutti(db, Conversation, or_(Conversation.richiedente_id == user.id,
                                                       Conversation.destinatario_id == user.id))
    chat = []
    for c in conversazioni:
        messaggi = await _tutti(db, Message, Message.conversazione_id == c.id)
        chat.append({"contesto": c.contesto_titolo, "creata_il": _iso(c.created_at), "messaggi": [
            {"io": m.autore_id == user.id, "testo": m.testo, "data": _iso(m.created_at)}
            for m in sorted(messaggi, key=lambda m: m.created_at)]})
    return {
        "formato": "neighbour-export/1",
        "esportato_il": datetime.now().isoformat(timespec="seconds"),
        "account": {"alias": user.alias, "bio": user.bio, "tipo_profilo": user.tipo_profilo, "attivita": user.attivita,
                    "creato_il": _iso(user.created_at), "accetta_messaggi": user.accetta_messaggi},
        "area": {"nome": area.nome, "centro_approssimato": [area.lat, area.lon], "raggio_m": area.raggio_m} if area else None,
        "preferenze_notifiche": {"tipi": prefs.tipi, "categorie": prefs.categorie, "raggio_m": prefs.raggio_m,
                                 "frequenza": prefs.frequenza, "silenzio": [prefs.quiet_inizio, prefs.quiet_fine]} if prefs else None,
        "consensi": [{"finalita": c.finalita, "concesso": c.concesso, "versione": c.versione, "timestamp": _iso(c.timestamp),
                      "revocato_il": _iso(c.revocato_at)} for c in await _tutti(db, Consent, Consent.user_id == user.id)],
        "contenuti": [{"tipo": p.tipo, "categoria": p.categoria, "titolo": p.titolo, "descrizione": p.descrizione,
                       "stato": p.stato, "creato_il": _iso(p.created_at)} for p in await _tutti(db, Post, Post.autore_id == user.id)],
        "eventi_organizzati": [{"titolo": e.titolo, "inizio": _iso(e.inizio), "luogo": e.luogo, "stato": e.stato}
                               for e in await _tutti(db, Event, Event.creatore_id == user.id)],
        "partecipazioni": [{"evento_id": p.evento_id, "stato": p.stato}
                           for p in await _tutti(db, EventParticipation, EventParticipation.user_id == user.id)],
        "gruppi": [{"gruppo_id": m.gruppo_id, "ruolo": m.ruolo} for m in await _tutti(db, GroupMember, GroupMember.user_id == user.id)],
        "servizi": [{"nome": s.nome, "tipo_scheda": s.tipo_scheda, "descrizione": s.descrizione}
                    for s in await _tutti(db, Service, Service.autore_id == user.id)],
        "conversazioni": chat,
        "feedback_ricevuti": [{"esito": r.esito, "commento": r.commento, "contesto": r.contesto_titolo}
                              for r in await _tutti(db, Reputation, Reputation.destinatario_id == user.id)],
        "feedback_lasciati": [{"esito": r.esito, "commento": r.commento, "contesto": r.contesto_titolo}
                              for r in await _tutti(db, Reputation, Reputation.autore_id == user.id)],
        "segnalazioni_inviate": [{"motivo": r.motivo, "stato": r.stato, "decisione": r.decisione, "data": _iso(r.created_at)}
                                 for r in await _tutti(db, Report, Report.segnalante_id == user.id)],
        "utenti_bloccati": len(await _tutti(db, Block, Block.bloccante_id == user.id)),
    }


def in_markdown(d: dict) -> str:
    righe = [f"# Esportazione Neighbour — {d['account']['alias']}", "",
             f"Esportato il {d['esportato_il']}. Il file JSON contiene gli stessi dati in formato portabile.", ""]
    if d["area"]:
        righe += ["## Quartiere", "", f"- {d['area']['nome']} (raggio {d['area']['raggio_m']} m, posizione approssimata)", ""]
    if d["contenuti"]:
        righe += ["## I miei contenuti", ""] + [
            f"- **{TIPI_POST.get(p['tipo'], p['tipo'])}** · {p['titolo']} — {p['stato']}" for p in d["contenuti"]] + [""]
    if d["eventi_organizzati"]:
        righe += ["## Eventi organizzati", ""] + [f"- {e['titolo']} ({e['inizio'][:10]}, {e['luogo']})"
                                                for e in d["eventi_organizzati"]] + [""]
    if d["conversazioni"]:
        righe += ["## Conversazioni", ""]
        for c in d["conversazioni"]:
            righe.append(f"### {c['contesto']}")
            righe += [f"- **{'Io' if m['io'] else 'Vicino'}**: {m['testo']}" for m in c["messaggi"]]
            righe.append("")
    if d["feedback_ricevuti"]:
        righe += ["## Feedback ricevuti", ""] + [f"- {r['esito']} su «{r['contesto']}»" + (f": {r['commento']}" if r["commento"] else "")
                                               for r in d["feedback_ricevuti"]] + [""]
    righe += ["---", "© Roberto Di Flumeri — Neighbour"]
    return "\n".join(righe)


@router.get("/export")
async def esporta(formato: str = Query("json", pattern="^(json|markdown)$"), user: User = Depends(utente_corrente),
                  db: AsyncSession = Depends(get_db)):
    dati = await raccogli_dati(db, user)
    audit(db, user.id, "export_dati", formato)
    await db.commit()
    data = datetime.now().strftime("%Y%m%d")
    if formato == "markdown":
        return Response(in_markdown(dati), media_type="text/markdown; charset=utf-8",
                        headers={"Content-Disposition": f'attachment; filename="neighbour-{data}.md"'})
    return Response(json.dumps(dati, ensure_ascii=False, indent=2, default=str), media_type="application/json",
                    headers={"Content-Disposition": f'attachment; filename="neighbour-{data}.json"'})


@router.delete("")
async def cancella_account(dati: CancellazioneIn, response: Response, user: User = Depends(utente_corrente),
                           db: AsyncSession = Depends(get_db)):
    if not verifica_password(dati.password, user.password_hash):  # ri-autenticazione obbligatoria
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Password non corretta")
    uid = user.id
    # foto su disco
    for f in await _tutti(db, Photo, Photo.proprietario_id == uid):
        (foto.cartella() / f.file).unlink(missing_ok=True)
    # i messaggi scritti dall'utente vengono cancellati; le conversazioni rimaste senza partecipanti spariscono
    await db.execute(delete(Message).where(Message.autore_id == uid))
    await db.execute(delete(Conversation).where(or_(
        and_(Conversation.richiedente_id == uid, Conversation.destinatario_id.is_(None)),
        and_(Conversation.destinatario_id == uid, Conversation.richiedente_id.is_(None)))))
    await db.execute(delete(ServiceRecommendation).where(ServiceRecommendation.user_id == uid))
    await db.execute(delete(AnalyticsEvent).where(AnalyticsEvent.utente_pseudo == pseudonimo(uid)))
    await db.execute(delete(Notification).where(Notification.user_id == uid))
    await archivia_segnalazioni(db, "Account eliminato dall'utente", utente_id=uid)
    # gruppi: se era l'unico amministratore subentra il membro più anziano
    for m in await _tutti(db, GroupMember, GroupMember.user_id == uid, GroupMember.ruolo == "amministratore"):
        altri_admin = (await db.execute(select(GroupMember.id).where(
            GroupMember.gruppo_id == m.gruppo_id, GroupMember.ruolo == "amministratore", GroupMember.user_id != uid))).first()
        if not altri_admin:
            erede = (await db.execute(select(GroupMember).where(GroupMember.gruppo_id == m.gruppo_id, GroupMember.user_id != uid)
                                      .order_by(GroupMember.created_at).limit(1))).scalar_one_or_none()
            if erede:
                erede.ruolo = "amministratore"
    await db.delete(user)  # il resto è eliminato a cascata dal database (vincoli ON DELETE)
    await db.flush()
    # i gruppi rimasti senza nessun membro non hanno più senso di esistere
    await db.execute(delete(Group).where(Group.id.not_in(select(GroupMember.gruppo_id))))
    sale = secrets.token_hex(8)
    ricevuta = DeletionReceipt(id=nuovo_id(), timestamp=adesso(), impronta=hashlib.sha256(f"{uid}:{sale}".encode()).hexdigest())
    db.add(ricevuta)
    audit(db, None, "account_eliminato", ricevuta.id)
    await db.commit()
    response.delete_cookie("nb_refresh", path="/v1/auth")
    return {"ricevuta": ricevuta.id, "eliminato_il": ricevuta.timestamp,
            "messaggio": "Account e dati eliminati definitivamente. Conserva il numero di ricevuta."}
