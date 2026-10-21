"""
Reputazione contestuale (F09): un feedback è possibile solo dopo un'interazione verificata
e conclusa (scambio chiuso tra autore e chi l'ha prenotato, oppure evento concluso tra
organizzatore e partecipante confermato). Nessun punteggio globale: si mostrano i singoli
feedback con il loro contesto.

© Roberto Di Flumeri
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models import Event, EventParticipation, Post, Reputation, User
from app.routers.eventi import concluso
from app.schemas import FeedbackIn
from app.security import utente_corrente
from app.services import controlli
from app.services.notifiche import notifica
from app.services.vicinato import utente_pubblico

router = APIRouter(prefix="/v1/reputation", tags=["Reputazione"])

SPIEGAZIONE = ("Non esiste un punteggio complessivo: ogni feedback è lasciato da un vicino dopo uno scambio "
               "o un evento realmente concluso insieme, e mostra il suo contesto.")


async def interazione_verificata(db: AsyncSession, autore_id: str, destinatario_id: str, tipo: str, cid: str) -> str | None:
    """Titolo del contesto se l'interazione tra i due utenti è verificata e conclusa, altrimenti None."""
    if tipo == "post":
        p = await db.get(Post, cid)
        if p and p.stato == "chiuso" and p.prenotato_da and {autore_id, destinatario_id} == {p.autore_id, p.prenotato_da}:
            return p.titolo
        return None
    e = await db.get(Event, cid)
    if e is None or e.stato != "pubblicato" or not concluso(e):
        return None
    if e.creatore_id not in (autore_id, destinatario_id):
        return None
    altro = destinatario_id if e.creatore_id == autore_id else autore_id
    conf = (await db.execute(select(EventParticipation).where(
        EventParticipation.evento_id == e.id, EventParticipation.user_id == altro,
        EventParticipation.stato == "confermato"))).scalar_one_or_none()
    return e.titolo if conf else None


async def riepilogo_reputazione(db: AsyncSession, user_id: str) -> dict:
    righe = (await db.execute(select(Reputation).where(Reputation.destinatario_id == user_id)
                              .order_by(Reputation.created_at.desc()))).scalars().all()
    conteggi = {"positivo": 0, "neutro": 0, "negativo": 0}
    for r in righe:
        conteggi[r.esito] += 1
    return {
        "spiegazione": SPIEGAZIONE, "conteggi": conteggi, "totale": len(righe),
        "feedback": [{"esito": r.esito, "commento": r.commento, "contesto_tipo": r.contesto_tipo,
                      "contesto": r.contesto_titolo, "da": utente_pubblico(await db.get(User, r.autore_id)) if r.autore_id else None,
                      "created_at": r.created_at} for r in righe[:30]],
    }


@router.post("", status_code=status.HTTP_201_CREATED)
async def lascia(dati: FeedbackIn, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    if dati.destinatario_id == user.id:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Non puoi lasciare un feedback a te stesso")
    titolo = await interazione_verificata(db, user.id, dati.destinatario_id, dati.contesto_tipo, dati.contesto_id)
    if titolo is None:
        raise HTTPException(status.HTTP_403_FORBIDDEN,
                            "Puoi lasciare un feedback solo dopo uno scambio o un evento concluso con questa persona")
    if dati.commento and controlli.dati_personali(dati.commento):
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Il commento non deve contenere recapiti o indirizzi")
    esiste = (await db.execute(select(Reputation.id).where(
        Reputation.autore_id == user.id, Reputation.destinatario_id == dati.destinatario_id,
        Reputation.contesto_tipo == dati.contesto_tipo, Reputation.contesto_id == dati.contesto_id))).first()
    if esiste:
        raise HTTPException(status.HTTP_409_CONFLICT, "Hai già lasciato un feedback per questa interazione")
    db.add(Reputation(autore_id=user.id, destinatario_id=dati.destinatario_id, contesto_tipo=dati.contesto_tipo,
                      contesto_id=dati.contesto_id, contesto_titolo=titolo, esito=dati.esito, commento=dati.commento or None))
    await notifica(db, dati.destinatario_id, "reputazione", "Hai ricevuto un feedback",
                   f"{user.alias} ha lasciato un feedback su «{titolo}».", "/app/profilo")
    await db.commit()
    return await riepilogo_reputazione(db, dati.destinatario_id)


@router.get("/da-lasciare")
async def da_lasciare(user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    """Interazioni concluse per cui posso ancora lasciare un feedback."""
    gia = {(r.destinatario_id, r.contesto_tipo, r.contesto_id) for r in (await db.execute(
        select(Reputation).where(Reputation.autore_id == user.id))).scalars()}
    out = []
    scambi = (await db.execute(select(Post).where(
        Post.stato == "chiuso", Post.prenotato_da.is_not(None),
        (Post.autore_id == user.id) | (Post.prenotato_da == user.id)))).scalars().all()
    for p in scambi:
        altro = p.prenotato_da if p.autore_id == user.id else p.autore_id
        if altro and (altro, "post", p.id) not in gia:
            out.append({"contesto_tipo": "post", "contesto_id": p.id, "contesto": p.titolo,
                        "con": utente_pubblico(await db.get(User, altro))})
    miei_eventi = (await db.execute(select(Event).where(Event.creatore_id == user.id, Event.stato == "pubblicato"))).scalars().all()
    for e in miei_eventi:
        if not concluso(e):
            continue
        conf = (await db.execute(select(EventParticipation.user_id).where(
            EventParticipation.evento_id == e.id, EventParticipation.stato == "confermato"))).scalars().all()
        for uid in conf:
            if (uid, "evento", e.id) not in gia:
                out.append({"contesto_tipo": "evento", "contesto_id": e.id, "contesto": e.titolo,
                            "con": utente_pubblico(await db.get(User, uid))})
    partecipati = (await db.execute(select(Event).join(EventParticipation, EventParticipation.evento_id == Event.id).where(
        EventParticipation.user_id == user.id, EventParticipation.stato == "confermato", Event.stato == "pubblicato"))).scalars().all()
    for e in partecipati:
        if concluso(e) and (e.creatore_id, "evento", e.id) not in gia:
            out.append({"contesto_tipo": "evento", "contesto_id": e.id, "contesto": e.titolo,
                        "con": utente_pubblico(await db.get(User, e.creatore_id))})
    return out


@router.get("/me")
async def mia(user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    return await riepilogo_reputazione(db, user.id)
