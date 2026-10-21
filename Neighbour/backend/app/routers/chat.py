"""
Chat privata contestuale (F05, §6.1 POST /v1/conversations).

- Si apre solo a partire da un annuncio, un evento o un servizio visibile.
- Consenso: chi ha scelto "nessun messaggio" non può essere contattato; i blocchi valgono
  in entrambe le direzioni e fermano subito la conversazione.
- Anti-spam: quota oraria di nuove chat e limite di messaggi finché l'altra persona non risponde.
- I messaggi sono cifrati a riposo e leggibili solo dai partecipanti.

© Roberto Di Flumeri
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func, or_, select, update
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.config import get_settings
from app.database import get_db
from app.models import Conversation, Message, Service, User, adesso
from app.routers.eventi import carica_visibile
from app.routers.posts import carica_post, verifica_visibilita_post
from app.schemas import ConversazioneIn, MessaggioIn
from app.security import audit, controlla_rate, utente_corrente
from app.services.notifiche import notifica
from app.services.vicinato import bloccati_tra, non_trovato, traccia, utente_pubblico

router = APIRouter(prefix="/v1/conversations", tags=["Chat"])


async def _contesto(db: AsyncSession, dati: ConversazioneIn, user: User) -> tuple[str, str]:
    """(id del destinatario, titolo del contesto) dopo aver verificato che il contesto sia visibile e aperto."""
    if dati.contesto_tipo == "post":
        p = await carica_post(db, dati.contesto_id)
        await verifica_visibilita_post(db, p, user)
        if p.stato not in ("pubblicato", "prenotato"):
            raise HTTPException(status.HTTP_409_CONFLICT, "Questo contenuto non accetta più contatti")
        return p.autore_id, p.titolo
    if dati.contesto_tipo == "evento":
        e, _ = await carica_visibile(db, dati.contesto_id, user)
        if e.stato != "pubblicato":
            raise HTTPException(status.HTTP_409_CONFLICT, "Questo evento non accetta più contatti")
        return e.creatore_id, e.titolo
    sv = await db.get(Service, dati.contesto_id)
    if sv is None or sv.rimosso:
        raise non_trovato("Servizio")
    return sv.autore_id, sv.nome


def _altro(c: Conversation, user_id: str) -> str | None:
    return c.destinatario_id if c.richiedente_id == user_id else c.richiedente_id


async def conversazione_dict(db: AsyncSession, c: Conversation, user: User, messaggi: bool = False) -> dict:
    altro_id = _altro(c, user.id)
    altro = await db.get(User, altro_id) if altro_id else None
    ultimo = (await db.execute(select(Message).where(Message.conversazione_id == c.id)
                               .order_by(Message.created_at.desc()).limit(1))).scalar_one_or_none()
    non_lette = (await db.execute(select(func.count()).select_from(Message).where(
        Message.conversazione_id == c.id, Message.autore_id != user.id, Message.letto.is_(False)))).scalar_one()
    bloccata = bool(altro_id) and await bloccati_tra(db, user.id, altro_id)
    out = {
        "id": c.id, "contesto_tipo": c.contesto_tipo, "contesto_id": c.contesto_id, "contesto_titolo": c.contesto_titolo,
        "con": utente_pubblico(altro), "stato": "bloccata" if bloccata else c.stato, "updated_at": c.updated_at,
        "ultimo": {"testo": ultimo.testo[:120], "mio": ultimo.autore_id == user.id, "created_at": ultimo.created_at} if ultimo else None,
        "non_lette": non_lette, "sono_richiedente": c.richiedente_id == user.id,
    }
    if messaggi:
        out["messaggi"] = [{"id": m.id, "testo": m.testo, "mio": m.autore_id == user.id, "letto": m.letto,
                            "created_at": m.created_at} for m in c.messaggi]
    return out


async def _partecipante(db: AsyncSession, conv_id: str, user: User) -> Conversation:
    c = (await db.execute(select(Conversation).options(selectinload(Conversation.messaggi))
                          .where(Conversation.id == conv_id)
                          .execution_options(populate_existing=True))).scalar_one_or_none()
    if c is None or user.id not in (c.richiedente_id, c.destinatario_id):
        raise non_trovato("Conversazione")
    return c


async def _scrivi(db: AsyncSession, c: Conversation, user: User, testo: str) -> None:
    s = get_settings()
    altro_id = _altro(c, user.id)
    if altro_id is None:
        raise HTTPException(status.HTTP_409_CONFLICT, "L'altra persona ha eliminato l'account")
    if await bloccati_tra(db, user.id, altro_id):
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Non puoi scrivere a questa persona")
    if c.stato != "attiva":
        raise HTTPException(status.HTTP_409_CONFLICT, "La conversazione è chiusa")
    controlla_rate(f"messaggi:{user.id}", 30)
    if c.richiedente_id == user.id:
        risposte = (await db.execute(select(func.count()).select_from(Message).where(
            Message.conversazione_id == c.id, Message.autore_id == altro_id))).scalar_one()
        miei = (await db.execute(select(func.count()).select_from(Message).where(
            Message.conversazione_id == c.id, Message.autore_id == user.id))).scalar_one()
        if risposte == 0 and miei >= s.messaggi_senza_risposta:
            raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS,
                                f"Hai già inviato {s.messaggi_senza_risposta} messaggi: aspetta una risposta prima di scrivere ancora")
    gia_avvisato = (await db.execute(select(func.count()).select_from(Message).where(
        Message.conversazione_id == c.id, Message.autore_id == user.id, Message.letto.is_(False)))).scalar_one()
    db.add(Message(conversazione_id=c.id, autore_id=user.id, testo=testo))
    c.updated_at = adesso()
    if not gia_avvisato:  # una sola notifica finché i messaggi precedenti non vengono letti
        await notifica(db, altro_id, "messaggio", f"Nuovo messaggio da {user.alias}",
                       f"Riguardo a «{c.contesto_titolo}»", f"/app/chat/{c.id}")


@router.post("", status_code=status.HTTP_201_CREATED)
async def apri(dati: ConversazioneIn, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    destinatario_id, titolo = await _contesto(db, dati, user)
    if destinatario_id == user.id:
        raise HTTPException(status.HTTP_409_CONFLICT, "È un tuo contenuto")
    destinatario = await db.get(User, destinatario_id)
    if destinatario is None or await bloccati_tra(db, user.id, destinatario_id):
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Non puoi contattare questa persona")
    esistente = (await db.execute(select(Conversation).options(selectinload(Conversation.messaggi)).where(
        Conversation.contesto_tipo == dati.contesto_tipo, Conversation.contesto_id == dati.contesto_id,
        Conversation.richiedente_id == user.id))).scalar_one_or_none()
    if esistente is None:
        if destinatario.accetta_messaggi == "nessuno" or destinatario.stato != "attivo":
            raise HTTPException(status.HTTP_403_FORBIDDEN, "Questa persona al momento non riceve messaggi")
        controlla_rate(f"nuove_chat:{user.id}", get_settings().nuove_chat_ora, 3600,
                       "Hai aperto troppe conversazioni nell'ultima ora: riprova più tardi")
        esistente = Conversation(contesto_tipo=dati.contesto_tipo, contesto_id=dati.contesto_id, contesto_titolo=titolo,
                                 richiedente_id=user.id, destinatario_id=destinatario_id)
        db.add(esistente)
        await db.flush()
        await traccia(db, user, f"chat_{dati.contesto_tipo}")
    await _scrivi(db, esistente, user, dati.messaggio)
    await db.commit()
    return await conversazione_dict(db, await _partecipante(db, esistente.id, user), user, messaggi=True)


@router.get("")
async def elenco(user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    righe = (await db.execute(select(Conversation).where(
        or_(Conversation.richiedente_id == user.id, Conversation.destinatario_id == user.id))
        .order_by(Conversation.updated_at.desc()))).scalars().all()
    return [await conversazione_dict(db, c, user) for c in righe]


@router.get("/non-lette")
async def non_lette(user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    mie = select(Conversation.id).where(or_(Conversation.richiedente_id == user.id, Conversation.destinatario_id == user.id))
    n = (await db.execute(select(func.count()).select_from(Message).where(
        Message.conversazione_id.in_(mie), Message.autore_id != user.id, Message.letto.is_(False)))).scalar_one()
    return {"non_lette": n}


@router.get("/{conv_id}")
async def leggi(conv_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    c = await _partecipante(db, conv_id, user)
    await db.execute(update(Message).where(Message.conversazione_id == c.id, Message.autore_id != user.id,
                                           Message.letto.is_(False)).values(letto=True))
    await db.commit()
    return await conversazione_dict(db, await _partecipante(db, conv_id, user), user, messaggi=True)


@router.post("/{conv_id}/messages", status_code=status.HTTP_201_CREATED)
async def invia(conv_id: str, dati: MessaggioIn, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    c = await _partecipante(db, conv_id, user)
    await _scrivi(db, c, user, dati.testo)
    await db.commit()
    return await conversazione_dict(db, await _partecipante(db, conv_id, user), user, messaggi=True)


@router.post("/{conv_id}/chiudi")
async def chiudi(conv_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    c = await _partecipante(db, conv_id, user)
    c.stato = "chiusa"
    audit(db, user.id, "chat_chiusa", c.id)
    await db.commit()
    return await conversazione_dict(db, c, user)
