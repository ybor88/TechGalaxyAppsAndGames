"""Registrazione, accesso, sessione e impostazioni dell'utente. © Roberto Di Flumeri"""

from fastapi import APIRouter, Cookie, Depends, HTTPException, Request, Response, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import get_settings
from app.crypto import hash_email
from app.database import get_db
from app.models import User
from app.schemas import CambioPasswordIn, LoginIn, RegistrazioneIn, TokenOut, UtenteOut, UtenteUpdate
from app.security import (
    audit, controlla_rate, crea_access_token, emetti_refresh_token, hash_password, ip_client,
    registra_consenso, revoca_tutti_refresh, ruota_refresh_token, utente_corrente, verifica_password,
)

router = APIRouter(prefix="/v1/auth", tags=["Autenticazione"])
COOKIE = "gm_refresh"
# hash fittizio per uniformare i tempi di risposta quando l'email non esiste
_HASH_FITTIZIO = hash_password("ghostme-password-fittizia-1")


def _imposta_cookie(resp: Response, token: str) -> None:
    s = get_settings()
    resp.set_cookie(
        COOKIE, token, httponly=True, secure=s.cookie_secure, samesite="strict",
        max_age=s.refresh_token_giorni * 86400, path="/v1/auth",
    )


def _token_out(user: User) -> TokenOut:
    return TokenOut(access_token=crea_access_token(user), scade_in_secondi=get_settings().access_token_minuti * 60)


@router.post("/register", response_model=TokenOut, status_code=status.HTTP_201_CREATED)
async def registra(dati: RegistrazioneIn, request: Request, response: Response, db: AsyncSession = Depends(get_db)):
    controlla_rate(f"register:{ip_client(request)}", get_settings().rate_limit_login_min)
    eh = hash_email(dati.email)
    if (await db.execute(select(User.id).where(User.email_hash == eh))).first():
        raise HTTPException(status.HTTP_409_CONFLICT, "Esiste già un account con questa email")
    user = User(email_hash=eh, password_hash=hash_password(dati.password), nome_visualizzato=dati.nome.strip())
    db.add(user)
    await db.flush()
    for finalita, valore in dati.consensi.model_dump().items():
        await registra_consenso(db, user.id, finalita, valore)
    audit(db, user.id, "account_creato")
    token = await emetti_refresh_token(db, user.id)
    await db.commit()
    _imposta_cookie(response, token)
    return _token_out(user)


@router.post("/login", response_model=TokenOut)
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
    if user.stato != "attivo":
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Account bloccato: contatta il supporto")
    token = await emetti_refresh_token(db, user.id)
    audit(db, user.id, "login")
    await db.commit()
    _imposta_cookie(response, token)
    return _token_out(user)


@router.post("/refresh", response_model=TokenOut, responses={204: {"description": "Nessuna sessione da riprendere"}})
async def refresh(response: Response, gm_refresh: str | None = Cookie(default=None), db: AsyncSession = Depends(get_db)):
    if not gm_refresh:
        # visitatore senza sessione: non è un errore (evita 401 a ogni apertura dell'app)
        return Response(status_code=status.HTTP_204_NO_CONTENT)
    user, nuovo = await ruota_refresh_token(db, gm_refresh)
    _imposta_cookie(response, nuovo)
    return _token_out(user)


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT)
async def logout(response: Response, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    await revoca_tutti_refresh(db, user.id)
    audit(db, user.id, "logout")
    await db.commit()
    response.delete_cookie(COOKIE, path="/v1/auth")


@router.get("/me", response_model=UtenteOut)
async def me(user: User = Depends(utente_corrente)):
    return user


@router.patch("/me", response_model=UtenteOut)
async def aggiorna_me(dati: UtenteUpdate, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    if dati.nome_visualizzato is not None:
        user.nome_visualizzato = dati.nome_visualizzato.strip()
    if dati.motore_ai is not None:
        user.motore_ai = dati.motore_ai
    if dati.locale is not None:
        user.locale = dati.locale
    if dati.conservazione_illimitata:
        user.conservazione_chat_giorni = None
    elif dati.conservazione_chat_giorni is not None:
        user.conservazione_chat_giorni = dati.conservazione_chat_giorni
    await db.commit()
    return user


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
