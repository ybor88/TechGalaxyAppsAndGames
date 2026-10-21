"""Registrazione, accesso, sessione e profilo personale. © Roberto Di Flumeri"""

from fastapi import APIRouter, Cookie, Depends, HTTPException, Request, Response, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import get_settings
from app.crypto import hash_email
from app.database import get_db
from app.models import Area, User
from app.schemas import CambioPasswordIn, LoginIn, ProfiloUpdate, RegistrazioneIn
from app.security import (
    audit, controlla_rate, crea_access_token, emetti_refresh_token, hash_password, ip_client,
    registra_consenso, revoca_tutti_refresh, ruota_refresh_token, utente_corrente, verifica_password,
    verifica_stato_account,
)
from app.services.notifiche import preferenze

router = APIRouter(prefix="/v1/auth", tags=["Autenticazione"])
COOKIE = "nb_refresh"
# hash fittizio per uniformare i tempi di risposta quando l'email non esiste
_HASH_FITTIZIO = hash_password("neighbour-password-fittizia-1")


def _imposta_cookie(resp: Response, token: str) -> None:
    s = get_settings()
    resp.set_cookie(COOKIE, token, httponly=True, secure=s.cookie_secure, samesite="strict",
                    max_age=s.refresh_token_giorni * 86400, path="/v1/auth")


def _token(user: User) -> dict:
    return {"access_token": crea_access_token(user), "token_type": "bearer",
            "scade_in_secondi": get_settings().access_token_minuti * 60}


async def utente_dict(db: AsyncSession, user: User) -> dict:
    area = (await db.execute(select(Area).where(Area.user_id == user.id))).scalar_one_or_none()
    return {
        "id": user.id, "alias": user.alias, "bio": user.bio, "tipo_profilo": user.tipo_profilo, "attivita": user.attivita,
        "ruolo": user.ruolo, "accetta_messaggi": user.accetta_messaggi, "created_at": user.created_at,
        "area": {"nome": area.nome, "lat": area.lat, "lon": area.lon, "raggio_m": area.raggio_m} if area else None,
    }


@router.post("/register", status_code=status.HTTP_201_CREATED)
async def registra(dati: RegistrazioneIn, request: Request, response: Response, db: AsyncSession = Depends(get_db)):
    controlla_rate(f"register:{ip_client(request)}", get_settings().rate_limit_login_min)
    eh = hash_email(dati.email)
    if (await db.execute(select(User.id).where(User.email_hash == eh))).first():
        raise HTTPException(status.HTTP_409_CONFLICT, "Esiste già un account con questa email")
    user = User(email_hash=eh, password_hash=hash_password(dati.password), alias=dati.alias,
                tipo_profilo=dati.tipo_profilo, attivita=dati.attivita if dati.tipo_profilo == "professionale" else None)
    db.add(user)
    await db.flush()
    for finalita, valore in dati.consensi.model_dump().items():
        await registra_consenso(db, user.id, finalita, valore)
    await preferenze(db, user.id)
    audit(db, user.id, "account_creato", dettagli=user.tipo_profilo)
    token = await emetti_refresh_token(db, user.id)
    await db.commit()
    _imposta_cookie(response, token)
    return _token(user)


@router.post("/login")
async def login(dati: LoginIn, request: Request, response: Response, db: AsyncSession = Depends(get_db)):
    controlla_rate(f"login:{ip_client(request)}", get_settings().rate_limit_login_min)
    user = (await db.execute(select(User).where(User.email_hash == hash_email(dati.email)))).scalar_one_or_none()
    if user is None:
        verifica_password(dati.password, _HASH_FITTIZIO)
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Email o password non corretti")
    if not verifica_password(dati.password, user.password_hash):
        audit(db, user.id, "login_fallito")
        await db.commit()
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Email o password non corretti")
    await verifica_stato_account(db, user)
    token = await emetti_refresh_token(db, user.id)
    audit(db, user.id, "login")
    await db.commit()
    _imposta_cookie(response, token)
    return _token(user)


@router.post("/refresh", responses={204: {"description": "Nessuna sessione da riprendere"}})
async def refresh(response: Response, nb_refresh: str | None = Cookie(default=None), db: AsyncSession = Depends(get_db)):
    if not nb_refresh:
        # visitatore senza sessione: non è un errore (evita un 401 a ogni apertura dell'app)
        return Response(status_code=status.HTTP_204_NO_CONTENT)
    user, nuovo = await ruota_refresh_token(db, nb_refresh)
    _imposta_cookie(response, nuovo)
    return _token(user)


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT)
async def logout(response: Response, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    await revoca_tutti_refresh(db, user.id)
    audit(db, user.id, "logout")
    await db.commit()
    response.delete_cookie(COOKIE, path="/v1/auth")


@router.get("/me")
async def me(user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    return await utente_dict(db, user)


@router.patch("/me")
async def aggiorna_me(dati: ProfiloUpdate, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    if dati.alias is not None:
        user.alias = dati.alias
    if dati.bio is not None:
        user.bio = dati.bio or None
    if dati.tipo_profilo is not None:
        if dati.tipo_profilo == "professionale" and not (dati.attivita or user.attivita):
            raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Per un profilo professionale indica il nome dell'attività")
        user.tipo_profilo = dati.tipo_profilo
        audit(db, user.id, "tipo_profilo", dettagli=dati.tipo_profilo)
    if dati.attivita is not None:
        user.attivita = dati.attivita or None
    if user.tipo_profilo == "privato":
        user.attivita = None
    await db.commit()
    return await utente_dict(db, user)


@router.post("/password", status_code=status.HTTP_204_NO_CONTENT)
async def cambia_password(dati: CambioPasswordIn, response: Response, user: User = Depends(utente_corrente),
                          db: AsyncSession = Depends(get_db)):
    if not verifica_password(dati.password_attuale, user.password_hash):
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Password attuale non corretta")
    user.password_hash = hash_password(dati.nuova_password)
    await revoca_tutti_refresh(db, user.id)  # le altre sessioni vengono chiuse
    audit(db, user.id, "password_cambiata")
    await db.commit()
    response.delete_cookie(COOKIE, path="/v1/auth")
