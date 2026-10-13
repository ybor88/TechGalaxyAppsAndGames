"""
Condivisione avatar con link temporaneo (F08), area pubblica per l'utente invitato,
test di compatibilità consensuale (F05) e segnalazioni.

© Roberto Di Flumeri
"""

import hashlib
import secrets
import time
from datetime import timedelta

from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.concurrency import run_in_threadpool
from sqlalchemy import or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import get_settings
from app.crypto import hash_token
from app.database import get_db
from app.models import CompatibilityInvite, Report, ShareAccess, ShareGrant, TraitStatement, User, adesso
from app.routers.comune import dichiarazione_dict, documenti_autorizzati, risorsa_utente, traccia, valida_oggi
from app.schemas import (
    AccettaInvitoIn, CondivisioneIn, CondivisioneOut, DomandaPubblicaIn, InvitoCompatibilitaIn, SegnalazioneIn,
)
from app.security import audit, consenso_attivo, controlla_rate, ip_client, richiede_consenso
from app.services.catalogo import CATEGORIE
from app.services.dominio import calcola_compatibilita
from app.services.motore import ETICHETTA, Contesto, genera_risposta

router = APIRouter(prefix="/v1", tags=["Condivisione"])
condivisione_ok = richiede_consenso("condivisione")

WATERMARK = "Risposta generata · Avatar digitale condiviso tramite Ghost Me · non rappresenta la persona reale"


def _attiva(g: ShareGrant) -> bool:
    return (g.revocato_at is None and not g.bloccato_moderazione and g.scadenza > adesso()
            and g.domande_usate < g.max_domande)


def _out(g: ShareGrant) -> CondivisioneOut:
    o = CondivisioneOut.model_validate(g)
    o.attiva = _attiva(g)
    return o


def _ip_hash(request: Request) -> str:
    return hashlib.sha256(f"ip:{ip_client(request)}".encode()).hexdigest()


# ── Link condivisi (proprietario) ────────────────────────────────────────────

@router.post("/shares", status_code=status.HTTP_201_CREATED)
async def crea(dati: CondivisioneIn, user: User = Depends(condivisione_ok), db: AsyncSession = Depends(get_db)):
    if not await consenso_attivo(db, user.id, "elaborazione_ai"):
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Per condividere l'avatar serve anche il consenso 'elaborazione_ai'")
    token = secrets.token_urlsafe(24)
    g = ShareGrant(user_id=user.id, token_hash=hash_token(token), nome=dati.nome, categorie=list(dict.fromkeys(dati.categorie)),
                   max_domande=min(dati.max_domande, get_settings().share_max_domande),
                   scadenza=adesso() + timedelta(hours=dati.durata_ore))
    db.add(g)
    await db.flush()
    audit(db, user.id, "condivisione_creata", g.id)
    await traccia(db, user, "condivisione_creata")
    await db.commit()
    # il token è mostrato una sola volta: nel database ne resta solo l'hash
    return {"condivisione": _out(g), "token": token, "percorso": f"/s/{token}"}


@router.get("/shares", response_model=list[CondivisioneOut])
async def elenco(user: User = Depends(condivisione_ok), db: AsyncSession = Depends(get_db)):
    rows = (await db.execute(
        select(ShareGrant).where(ShareGrant.user_id == user.id).order_by(ShareGrant.created_at.desc())
    )).scalars().all()
    return [_out(g) for g in rows]


@router.delete("/shares/{share_id}", response_model=CondivisioneOut)
async def revoca(share_id: str, user: User = Depends(condivisione_ok), db: AsyncSession = Depends(get_db)):
    g = await risorsa_utente(db, ShareGrant, share_id, user)
    if g.revocato_at is None:
        g.revocato_at = adesso()
        audit(db, user.id, "condivisione_revocata", g.id)
        await db.commit()
    return _out(g)


@router.get("/shares/{share_id}/accessi")
async def accessi(share_id: str, user: User = Depends(condivisione_ok), db: AsyncSession = Depends(get_db)):
    g = await risorsa_utente(db, ShareGrant, share_id, user)
    rows = (await db.execute(
        select(ShareAccess).where(ShareAccess.share_id == g.id).order_by(ShareAccess.timestamp.desc()).limit(200)
    )).scalars()
    return [{"evento": a.evento, "timestamp": a.timestamp} for a in rows]


# ── Area pubblica (utente invitato) ──────────────────────────────────────────

async def _grant_da_token(db: AsyncSession, token: str) -> ShareGrant:
    g = (await db.execute(select(ShareGrant).where(ShareGrant.token_hash == hash_token(token)))).scalar_one_or_none()
    if g is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Link non valido")
    return g


def _motivo_inattivo(g: ShareGrant) -> str | None:
    if g.revocato_at is not None:
        return "Il proprietario ha revocato questo link."
    if g.bloccato_moderazione:
        return "Questo link è stato sospeso dalla moderazione."
    if g.scadenza <= adesso():
        return "Il link è scaduto."
    if g.domande_usate >= g.max_domande:
        return "È stato raggiunto il numero massimo di domande per questo link."
    return None


@router.get("/public/shares/{token}")
async def info_pubblica(token: str, request: Request, db: AsyncSession = Depends(get_db)):
    controlla_rate(f"public:{ip_client(request)}", get_settings().rate_limit_public_min)
    g = await _grant_da_token(db, token)
    proprietario = await db.get(User, g.user_id)
    db.add(ShareAccess(share_id=g.id, evento="apertura", ip_hash=_ip_hash(request)))
    await db.commit()
    return {
        "nome": g.nome,
        "proprietario": proprietario.nome_visualizzato if proprietario else None,
        "categorie": [CATEGORIE.get(c, c) for c in g.categorie],
        "domande_rimaste": max(0, g.max_domande - g.domande_usate),
        "scadenza": g.scadenza,
        "attiva": _attiva(g),
        "motivo": _motivo_inattivo(g),
        "watermark": WATERMARK,
    }


@router.post("/public/shares/{token}/chat")
async def chat_pubblica(token: str, dati: DomandaPubblicaIn, request: Request, db: AsyncSession = Depends(get_db)):
    controlla_rate(f"public:{ip_client(request)}", get_settings().rate_limit_public_min)
    g = await _grant_da_token(db, token)
    motivo = _motivo_inattivo(g)
    proprietario = await db.get(User, g.user_id)
    if motivo is None and (proprietario is None or proprietario.stato != "attivo"
                           or not await consenso_attivo(db, g.user_id, "condivisione")):
        motivo = "Questo avatar non è più disponibile."
    if motivo:
        db.add(ShareAccess(share_id=g.id, evento="rifiutata", ip_hash=_ip_hash(request)))
        await db.commit()
        raise HTTPException(status.HTTP_410_GONE, motivo)
    t0 = time.monotonic()
    # accesso minimo: solo dichiarazioni condivisibili, autorizzate per l'AI, nelle categorie concesse
    documenti = await documenti_autorizzati(db, g.user_id, categorie=g.categorie, solo_condivisibili=True)
    ctx = Contesto(documenti=documenti, nome=proprietario.nome_visualizzato, modalita="condivisa")
    risposta = await run_in_threadpool(genera_risposta, dati.domanda, ctx, proprietario.motore_ai)
    g.domande_usate += 1
    db.add(ShareAccess(share_id=g.id, evento="domanda", ip_hash=_ip_hash(request)))
    await db.commit()
    return {
        "risposta": risposta.testo,
        "confidenza": risposta.confidenza,
        "fonti": [{"categoria": CATEGORIE.get(f.categoria, f.categoria)} for f in risposta.fonti],
        "avvisi": risposta.avvisi,
        "watermark": WATERMARK,
        "etichetta": ETICHETTA,
        "domande_rimaste": max(0, g.max_domande - g.domande_usate),
        "durata_ms": int((time.monotonic() - t0) * 1000),
    }


@router.post("/public/shares/{token}/report", status_code=status.HTTP_201_CREATED)
async def segnala(token: str, dati: SegnalazioneIn, request: Request, db: AsyncSession = Depends(get_db)):
    controlla_rate(f"report:{ip_client(request)}", 5)
    g = await _grant_da_token(db, token)
    db.add(Report(share_id=g.id, utente_segnalato_id=g.user_id, motivo=dati.motivo, dettagli=dati.dettagli))
    audit(db, None, "segnalazione_ricevuta", g.id, dati.motivo)
    await db.commit()
    return {"ricevuta": True}


# ── Compatibilità consensuale ────────────────────────────────────────────────

async def _condivisibili(db: AsyncSession, user_id: str, categorie: list[str]) -> list[dict]:
    rows = (await db.execute(select(TraitStatement).where(
        TraitStatement.user_id == user_id, TraitStatement.visibilita == "condivisibile",
        TraitStatement.categoria.in_(categorie),
    ))).scalars()
    return [dichiarazione_dict(s) for s in rows if valida_oggi(s)]


def _invito_out(inv: CompatibilityInvite, user: User, nomi: dict[str, str | None]) -> dict:
    return {
        "id": inv.id, "ruolo": "proprietario" if inv.proprietario_id == user.id else "invitato",
        "categorie": inv.categorie, "scadenza": inv.scadenza, "accettato": inv.accettato_da is not None,
        "con": nomi.get(inv.accettato_da if inv.proprietario_id == user.id else inv.proprietario_id),
        "risultato": inv.risultato, "created_at": inv.created_at,
    }


@router.post("/compatibility/inviti", status_code=status.HTTP_201_CREATED)
async def crea_invito(dati: InvitoCompatibilitaIn, user: User = Depends(condivisione_ok), db: AsyncSession = Depends(get_db)):
    codice = secrets.token_hex(5).upper()
    inv = CompatibilityInvite(proprietario_id=user.id, codice_hash=hash_token(codice), categorie=list(dict.fromkeys(dati.categorie)),
                              scadenza=adesso() + timedelta(days=dati.durata_giorni))
    db.add(inv)
    await db.flush()
    audit(db, user.id, "compatibilita_invito", inv.id)
    await db.commit()
    return {"id": inv.id, "codice": codice, "scadenza": inv.scadenza}


@router.post("/compatibility/accetta")
async def accetta_invito(dati: AccettaInvitoIn, user: User = Depends(condivisione_ok), db: AsyncSession = Depends(get_db)):
    inv = (await db.execute(select(CompatibilityInvite).where(
        CompatibilityInvite.codice_hash == hash_token(dati.codice.strip().upper())
    ))).scalar_one_or_none()
    if inv is None or inv.scadenza <= adesso():
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Codice non valido o scaduto")
    if inv.proprietario_id == user.id:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Non puoi accettare un tuo invito")
    if inv.accettato_da is not None:
        raise HTTPException(status.HTTP_409_CONFLICT, "Questo invito è già stato usato")
    if not await consenso_attivo(db, inv.proprietario_id, "condivisione"):
        raise HTTPException(status.HTTP_410_GONE, "L'invito non è più valido")
    a = await _condivisibili(db, inv.proprietario_id, inv.categorie)
    b = await _condivisibili(db, user.id, inv.categorie)
    inv.accettato_da = user.id
    inv.risultato = calcola_compatibilita(inv.categorie, a, b)
    audit(db, user.id, "compatibilita_accettata", inv.id)
    await db.commit()
    proprietario = await db.get(User, inv.proprietario_id)
    return _invito_out(inv, user, {inv.proprietario_id: proprietario.nome_visualizzato if proprietario else None})


@router.get("/compatibility")
async def elenco_compatibilita(user: User = Depends(condivisione_ok), db: AsyncSession = Depends(get_db)):
    inviti = (await db.execute(select(CompatibilityInvite).where(
        or_(CompatibilityInvite.proprietario_id == user.id, CompatibilityInvite.accettato_da == user.id)
    ).order_by(CompatibilityInvite.created_at.desc()))).scalars().all()
    ids = {i.proprietario_id for i in inviti} | {i.accettato_da for i in inviti if i.accettato_da}
    nomi = {u.id: u.nome_visualizzato for u in (await db.execute(select(User).where(User.id.in_(ids)))).scalars()}
    return [_invito_out(i, user, nomi) for i in inviti]


@router.delete("/compatibility/{invito_id}", status_code=status.HTTP_204_NO_CONTENT)
async def elimina_invito(invito_id: str, user: User = Depends(condivisione_ok), db: AsyncSession = Depends(get_db)):
    inv = await db.get(CompatibilityInvite, invito_id)
    # entrambi i partecipanti possono eliminare il confronto in qualsiasi momento
    if inv is None or user.id not in (inv.proprietario_id, inv.accettato_da):
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Elemento non trovato")
    await db.delete(inv)
    await db.commit()
