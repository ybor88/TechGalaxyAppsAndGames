"""
Autenticazione (access token breve + refresh token ruotato), autorizzazione,
rate limiting e audit.

© Roberto Di Flumeri
"""

import secrets
import time
from collections import defaultdict, deque
from datetime import datetime, timedelta, timezone

import bcrypt
import jwt
from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import CONSENT_VERSION, get_settings
from app.crypto import hash_token
from app.database import get_db
from app.models import AuditLog, Consent, RefreshToken, User, adesso

# ── Password ──────────────────────────────────────────────────────────────────


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8")[:72], bcrypt.gensalt(rounds=12)).decode()


def verifica_password(password: str, hashato: str) -> bool:
    try:
        return bcrypt.checkpw(password.encode("utf-8")[:72], hashato.encode())
    except ValueError:
        return False


# ── Token ─────────────────────────────────────────────────────────────────────


def crea_access_token(user: User) -> str:
    s = get_settings()
    ora = datetime.now(timezone.utc)
    payload = {"sub": user.id, "ruolo": user.ruolo, "iat": ora, "exp": ora + timedelta(minutes=s.access_token_minuti)}
    return jwt.encode(payload, s.jwt_secret, algorithm="HS256")


async def emetti_refresh_token(db: AsyncSession, user_id: str, famiglia: str | None = None) -> str:
    token = secrets.token_urlsafe(48)
    db.add(RefreshToken(
        user_id=user_id,
        token_hash=hash_token(token),
        famiglia=famiglia or secrets.token_hex(16),
        scadenza=adesso() + timedelta(days=get_settings().refresh_token_giorni),
    ))
    return token


async def ruota_refresh_token(db: AsyncSession, token: str) -> tuple[User, str]:
    """
    Rotazione: ogni refresh token si usa una sola volta. Il riuso di un token già
    ruotato indica un furto: l'intera famiglia di token viene revocata.
    """
    rec = (await db.execute(select(RefreshToken).where(RefreshToken.token_hash == hash_token(token)))).scalar_one_or_none()
    if rec is None or rec.scadenza < adesso():
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Sessione scaduta, accedi di nuovo")
    if rec.revocato_at is not None:
        await db.execute(
            update(RefreshToken).where(RefreshToken.famiglia == rec.famiglia).values(revocato_at=adesso())
        )
        await db.commit()
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Sessione non valida, accedi di nuovo")
    user = await db.get(User, rec.user_id)
    if user is None or user.stato != "attivo":
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Account non attivo")
    rec.revocato_at = adesso()
    nuovo = await emetti_refresh_token(db, user.id, rec.famiglia)
    await db.commit()
    return user, nuovo


async def revoca_tutti_refresh(db: AsyncSession, user_id: str) -> None:
    await db.execute(
        update(RefreshToken)
        .where(RefreshToken.user_id == user_id, RefreshToken.revocato_at.is_(None))
        .values(revocato_at=adesso())
    )


# ── Dipendenze FastAPI ───────────────────────────────────────────────────────

_bearer = HTTPBearer(auto_error=False)


async def utente_corrente(
    cred: HTTPAuthorizationCredentials | None = Depends(_bearer),
    db: AsyncSession = Depends(get_db),
) -> User:
    if cred is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Autenticazione richiesta")
    try:
        payload = jwt.decode(cred.credentials, get_settings().jwt_secret, algorithms=["HS256"])
    except jwt.PyJWTError:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Token non valido o scaduto")
    user = await db.get(User, payload.get("sub"))
    if user is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Utente non trovato")
    if user.stato != "attivo":
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Account bloccato")
    return user


async def solo_moderatore(user: User = Depends(utente_corrente)) -> User:
    if user.ruolo != "moderatore":
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Riservato ai moderatori")
    return user


# ── Consensi ──────────────────────────────────────────────────────────────────

FINALITA = {
    "trattamento_profilo": "Conservare le dichiarazioni e i contenuti che inserisci (necessario per usare l'app).",
    "elaborazione_ai": "Generare risposte e simulazioni dell'alter ego a partire dai dati che autorizzi.",
    "condivisione": "Creare link condivisi e test di compatibilità con altri utenti.",
    "analytics": "Raccogliere statistiche d'uso anonime per migliorare l'app (facoltativo).",
}


async def consenso_attivo(db: AsyncSession, user_id: str, finalita: str) -> bool:
    rec = (await db.execute(
        select(Consent)
        .where(Consent.user_id == user_id, Consent.finalita == finalita)
        .order_by(Consent.timestamp.desc())
        .limit(1)
    )).scalar_one_or_none()
    return bool(rec and rec.concesso and rec.revocato_at is None)


async def registra_consenso(db: AsyncSession, user_id: str, finalita: str, concesso: bool) -> Consent:
    if finalita not in FINALITA:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, f"Finalità sconosciuta: {finalita}")
    # il consenso precedente (se attivo) viene chiuso: il registro resta completo
    await db.execute(
        update(Consent)
        .where(Consent.user_id == user_id, Consent.finalita == finalita, Consent.revocato_at.is_(None))
        .values(revocato_at=adesso())
    )
    rec = Consent(user_id=user_id, finalita=finalita, versione=CONSENT_VERSION, concesso=concesso)
    db.add(rec)
    return rec


def richiede_consenso(finalita: str):
    async def _dip(user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)) -> User:
        if not await consenso_attivo(db, user.id, finalita):
            raise HTTPException(
                status.HTTP_403_FORBIDDEN,
                f"Serve il consenso '{finalita}': puoi attivarlo in Privacy e dati.",
            )
        return user

    return _dip


# ── Rate limiting (in memoria, finestra scorrevole di 60 s) ───────────────────

_finestre: dict[str, deque] = defaultdict(deque)


def controlla_rate(chiave: str, limite_minuto: int) -> None:
    ora = time.monotonic()
    coda = _finestre[chiave]
    while coda and ora - coda[0] > 60:
        coda.popleft()
    if len(coda) >= limite_minuto:
        raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS, "Troppe richieste: riprova tra un minuto")
    coda.append(ora)


def azzera_rate_limit() -> None:
    _finestre.clear()


def ip_client(request: Request) -> str:
    return request.client.host if request.client else "sconosciuto"


# ── Audit ─────────────────────────────────────────────────────────────────────


def audit(db: AsyncSession, attore_id: str | None, azione: str, oggetto: str | None = None, dettagli: str | None = None):
    db.add(AuditLog(attore_id=attore_id, azione=azione, oggetto=oggetto, dettagli=dettagli))
