"""
Cifratura a riposo dei contenuti personali (Fernet = AES-128-CBC + HMAC-SHA256).
Le colonne di tipo Cifrato/CifratoJSON sono salvate cifrate nel database e
decifrate solo in memoria: chi ha accesso al file del DB non legge i messaggi
privati, le email o le note personali.

© Roberto Di Flumeri
"""

import hashlib
import hmac
import json

from cryptography.fernet import Fernet, InvalidToken
from sqlalchemy.types import Text, TypeDecorator

from app.config import get_settings

_fernet: Fernet | None = None


def fernet() -> Fernet:
    global _fernet
    if _fernet is None:
        _fernet = Fernet(get_settings().data_key.encode())
    return _fernet


def cifra(testo: str | None) -> str | None:
    if testo is None:
        return None
    return fernet().encrypt(testo.encode("utf-8")).decode("ascii")


def decifra(valore: str | None) -> str | None:
    if valore is None:
        return None
    try:
        return fernet().decrypt(valore.encode("ascii")).decode("utf-8")
    except (InvalidToken, ValueError):
        return "[contenuto non leggibile: chiave di cifratura diversa]"


class Cifrato(TypeDecorator):
    """Testo cifrato a riposo."""

    impl = Text
    cache_ok = True

    def process_bind_param(self, value, dialect):
        return cifra(value)

    def process_result_value(self, value, dialect):
        return decifra(value)


class CifratoJSON(TypeDecorator):
    """Struttura JSON cifrata a riposo."""

    impl = Text
    cache_ok = True

    def process_bind_param(self, value, dialect):
        return None if value is None else cifra(json.dumps(value, ensure_ascii=False, default=str))

    def process_result_value(self, value, dialect):
        if value is None:
            return None
        testo = decifra(value)
        try:
            return json.loads(testo)
        except (TypeError, json.JSONDecodeError):
            return None


def hash_email(email: str) -> str:
    """Hash con pepper: l'email non è salvata in chiaro ma resta utilizzabile per il login."""
    norm = email.strip().lower().encode("utf-8")
    return hmac.new(get_settings().email_pepper.encode(), norm, hashlib.sha256).hexdigest()


def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()
