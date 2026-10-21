"""
Segnalazioni degli utenti e ricorsi (§6.1 POST /v1/reports, §8).
Motivi codificati, copia cifrata del contenuto come evidenza, blocco immediato opzionale.

© Roberto Di Flumeri
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models import Block, Conversation, Event, Group, Message, Post, Report, Service, User
from app.schemas import ReportIn, RicorsoIn
from app.security import audit, controlla_rate, utente_corrente
from app.services.catalogo import DECISIONI_MODERAZIONE, MOTIVI_SEGNALAZIONE
from app.services.vicinato import non_trovato

router = APIRouter(prefix="/v1/reports", tags=["Segnalazioni"])
RICORRIBILI = {"rimuovi_contenuto", "avviso", "sospendi_7_giorni"}


async def oggetto_segnalato(db: AsyncSession, tipo: str, oid: str, user: User) -> tuple[str | None, dict]:
    """(utente segnalato, evidenza) — l'evidenza è la copia del contenuto al momento della segnalazione."""
    if tipo == "post":
        o = await db.get(Post, oid)
        if o is None:
            raise non_trovato("Contenuto")
        return o.autore_id, {"titolo": o.titolo, "testo": o.descrizione, "tipo": o.tipo, "stato_precedente": o.stato}
    if tipo == "evento":
        o = await db.get(Event, oid)
        if o is None:
            raise non_trovato("Evento")
        return o.creatore_id, {"titolo": o.titolo, "testo": o.descrizione, "luogo": o.luogo, "stato_precedente": o.stato}
    if tipo == "servizio":
        o = await db.get(Service, oid)
        if o is None:
            raise non_trovato("Servizio")
        return o.autore_id, {"titolo": o.nome, "testo": o.descrizione, "contatto": o.contatto}
    if tipo == "gruppo":
        o = await db.get(Group, oid)
        if o is None:
            raise non_trovato("Gruppo")
        return o.creatore_id, {"titolo": o.nome, "testo": o.descrizione}
    if tipo == "messaggio":
        m = await db.get(Message, oid)
        c = await db.get(Conversation, m.conversazione_id) if m else None
        # si segnala solo un messaggio ricevuto in una propria conversazione
        if m is None or c is None or user.id not in (c.richiedente_id, c.destinatario_id) or m.autore_id == user.id:
            raise non_trovato("Messaggio")
        return m.autore_id, {"titolo": f"Messaggio in chat su «{c.contesto_titolo}»", "testo": m.testo}
    u = await db.get(User, oid)
    if u is None:
        raise non_trovato("Utente")
    return u.id, {"titolo": f"Profilo di {u.alias}", "testo": u.bio or ""}


def report_dict(r: Report, per_moderatore: bool = False) -> dict:
    out = {"id": r.id, "oggetto_tipo": r.oggetto_tipo, "oggetto_id": r.oggetto_id, "motivo": r.motivo,
           "motivo_testo": MOTIVI_SEGNALAZIONE.get(r.motivo, r.motivo), "stato": r.stato, "decisione": r.decisione,
           "decisione_testo": DECISIONI_MODERAZIONE.get(r.decisione or "", None), "nota_decisione": r.nota_decisione,
           "ricorso": r.ricorso, "ricorso_stato": r.ricorso_stato, "created_at": r.created_at, "risolta_at": r.risolta_at,
           "titolo": (r.evidenze or {}).get("titolo")}
    if per_moderatore:
        out.update({"dettagli": r.dettagli, "evidenze": r.evidenze, "utente_segnalato_id": r.utente_segnalato_id,
                    "segnalante_id": r.segnalante_id})
    return out


@router.post("", status_code=status.HTTP_201_CREATED)
async def segnala(dati: ReportIn, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    controlla_rate(f"segnalazioni:{user.id}", 10, 3600, "Hai inviato molte segnalazioni nell'ultima ora: riprova più tardi")
    segnalato_id, evidenze = await oggetto_segnalato(db, dati.oggetto_tipo, dati.oggetto_id, user)
    if segnalato_id == user.id:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Non puoi segnalare un tuo contenuto")
    esistente = (await db.execute(select(Report).where(
        Report.segnalante_id == user.id, Report.oggetto_tipo == dati.oggetto_tipo, Report.oggetto_id == dati.oggetto_id,
        Report.stato == "aperta"))).scalar_one_or_none()
    if esistente is None:  # una sola segnalazione aperta per persona e contenuto
        esistente = Report(segnalante_id=user.id, oggetto_tipo=dati.oggetto_tipo, oggetto_id=dati.oggetto_id,
                           utente_segnalato_id=segnalato_id, motivo=dati.motivo, dettagli=dati.dettagli, evidenze=evidenze)
        db.add(esistente)
        audit(db, user.id, "segnalazione_inviata", dati.oggetto_id, f"{dati.oggetto_tipo}:{dati.motivo}")
    if dati.blocca_utente and segnalato_id:
        gia = (await db.execute(select(Block).where(Block.bloccante_id == user.id, Block.bloccato_id == segnalato_id))).first()
        if not gia:
            db.add(Block(bloccante_id=user.id, bloccato_id=segnalato_id))
            audit(db, user.id, "utente_bloccato", segnalato_id)
    await db.commit()
    return {**report_dict(esistente), "bloccato": dati.blocca_utente,
            "messaggio": "Grazie: la moderazione esaminerà la segnalazione. Riceverai l'esito tra le notifiche."}


@router.get("/me")
async def mie(user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    inviate = (await db.execute(select(Report).where(Report.segnalante_id == user.id)
                                .order_by(Report.created_at.desc()))).scalars().all()
    # decisioni che mi riguardano: le vedo sempre, con la possibilità di ricorso
    ricevute = (await db.execute(select(Report).where(
        Report.utente_segnalato_id == user.id, Report.stato == "risolta",
        Report.decisione.in_(tuple(RICORRIBILI) + ("blocca_utente",))).order_by(Report.risolta_at.desc()))).scalars().all()
    return {"inviate": [report_dict(r) for r in inviate],
            "ricevute": [{**report_dict(r), "ricorribile": r.decisione in RICORRIBILI and r.ricorso is None} for r in ricevute]}


@router.post("/{report_id}/ricorso")
async def ricorso(report_id: str, dati: RicorsoIn, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    r = await db.get(Report, report_id)
    if r is None or r.utente_segnalato_id != user.id or r.stato != "risolta":
        raise non_trovato("Decisione")
    if r.decisione not in RICORRIBILI:
        raise HTTPException(status.HTTP_409_CONFLICT, "Questa decisione non prevede ricorso")
    if r.ricorso is not None:
        raise HTTPException(status.HTTP_409_CONFLICT, "Hai già presentato ricorso per questa decisione")
    r.ricorso, r.ricorso_stato = dati.testo, "aperto"
    audit(db, user.id, "ricorso_presentato", r.id)
    await db.commit()
    return report_dict(r)
