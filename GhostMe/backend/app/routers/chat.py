"""Chat con l'alter ego (F02), anche con versioni passate o simulate del profilo (F04). © Roberto Di Flumeri"""

import time

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.concurrency import run_in_threadpool
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.config import get_settings
from app.database import get_db
from app.models import Conversation, Message, ProfileSnapshot, User, adesso
from app.routers.comune import DOMANDE, documenti_autorizzati, risorsa_utente, traccia
from app.schemas import ChatIn, ChatOut, ConversazioneDettaglio, ConversazioneOut, MessaggioOut
from app.security import controlla_rate, richiede_consenso, utente_corrente
from app.services.motore import ETICHETTA, Contesto, genera_risposta, suggerisci_titolo
from app.services.testo import Documento

router = APIRouter(prefix="/v1", tags=["Chat"])
ai_ok = richiede_consenso("elaborazione_ai")


def descrivi_versione(s: ProfileSnapshot) -> str:
    if s.tipo == "simulazione_futura":
        return f"Simulazione: possibile me del {s.data_riferimento:%d/%m/%Y}, ipotetica"
    return f"Versione passata del {s.data_riferimento:%d/%m/%Y}"


def documenti_da_versione(s: ProfileSnapshot) -> list[Documento]:
    return [Documento(id=d["id"], tipo="dichiarazione", testo=d["testo"], categoria=d["categoria"], peso=d.get("peso", 3),
                      titolo=DOMANDE.get(d.get("chiave_domanda") or ""))
            for d in s.dati if d.get("usa_in_ai", True)]


@router.get("/conversations", response_model=list[ConversazioneOut])
async def elenco(user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    return (await db.execute(
        select(Conversation).where(Conversation.user_id == user.id).order_by(Conversation.updated_at.desc())
    )).scalars().all()


@router.get("/conversations/{conv_id}", response_model=ConversazioneDettaglio)
async def dettaglio(conv_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    conv = (await db.execute(
        select(Conversation).options(selectinload(Conversation.messaggi)).where(Conversation.id == conv_id)
    )).scalar_one_or_none()
    if conv is None or conv.user_id != user.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Conversazione non trovata")
    return conv


@router.delete("/conversations/{conv_id}", status_code=status.HTTP_204_NO_CONTENT)
async def elimina(conv_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    conv = await risorsa_utente(db, Conversation, conv_id, user)
    await db.delete(conv)
    await db.commit()


@router.post("/chat/completions", response_model=ChatOut)
async def chat(dati: ChatIn, user: User = Depends(ai_ok), db: AsyncSession = Depends(get_db)):
    controlla_rate(f"chat:{user.id}", get_settings().rate_limit_chat_min)
    t0 = time.monotonic()

    if dati.conversation_id:
        conv = (await db.execute(
            select(Conversation).options(selectinload(Conversation.messaggi)).where(Conversation.id == dati.conversation_id)
        )).scalar_one_or_none()
        if conv is None or conv.user_id != user.id:
            raise HTTPException(status.HTTP_404_NOT_FOUND, "Conversazione non trovata")
    else:
        if dati.snapshot_id:
            await risorsa_utente(db, ProfileSnapshot, dati.snapshot_id, user)
        conv = Conversation(user_id=user.id, titolo=suggerisci_titolo(dati.messaggio), snapshot_id=dati.snapshot_id)
        conv.messaggi = []
        db.add(conv)
        await db.flush()

    descrizione = None
    if conv.snapshot_id:
        snap = await db.get(ProfileSnapshot, conv.snapshot_id)
        documenti = documenti_da_versione(snap) if snap else []
        descrizione = descrivi_versione(snap) if snap else None
    else:
        documenti = await documenti_autorizzati(db, user.id)

    ctx = Contesto(
        documenti=documenti, nome=user.nome_visualizzato, modalita="versione" if descrizione else "privata",
        descrizione_versione=descrizione,
        cronologia=[(m.ruolo, m.contenuto) for m in conv.messaggi[-6:]],
    )
    # il motore può essere lento (modello locale): si esegue fuori dal loop asincrono
    risposta = await run_in_threadpool(genera_risposta, dati.messaggio, ctx, user.motore_ai)

    domanda = Message(conversation_id=conv.id, ruolo="utente", contenuto=dati.messaggio)
    db.add(domanda)
    await db.flush()
    msg = Message(
        conversation_id=conv.id, ruolo="alter_ego", contenuto=risposta.testo,
        fonti=[f.__dict__ for f in risposta.fonti], confidenza=risposta.confidenza,
        motore=risposta.motore, modello=risposta.modello,
        prompt_version=risposta.prompt_version, policy_version=risposta.policy_version,
    )
    db.add(msg)
    conv.updated_at = adesso()
    await traccia(db, user, "chat_messaggio")
    await db.commit()
    return ChatOut(
        conversation_id=conv.id,
        domanda=MessaggioOut.model_validate(domanda),
        risposta=MessaggioOut.model_validate(msg),
        avvisi=risposta.avvisi,
        etichetta=ETICHETTA,
        durata_ms=int((time.monotonic() - t0) * 1000),
    )
