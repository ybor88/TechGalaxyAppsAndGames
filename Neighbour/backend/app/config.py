"""
Configurazione da variabili d'ambiente / file .env (prefisso NEIGHBOUR_).
I segreti non sono nel codice: se mancano vengono generati al primo avvio e
salvati in backend/.secrets (file escluso da git).

© Roberto Di Flumeri
"""

import json
import secrets
from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

BASE_DIR = Path(__file__).resolve().parent.parent
FILE_SEGRETI = BASE_DIR / ".secrets"

CONSENT_VERSION = "consensi/1.0"
POLICY_VERSION = "linee-guida/1.0"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=BASE_DIR / ".env", env_prefix="NEIGHBOUR_", extra="ignore")

    app_name: str = "Neighbour"
    app_version: str = "1.0.0"
    # SQLite in locale; per la produzione: postgresql+asyncpg://utente:pwd@host/neighbour
    database_url: str = f"sqlite+aiosqlite:///{(BASE_DIR / 'neighbour.db').as_posix()}"
    frontend_origin: str = "http://localhost:3200"
    cartella_file: str = str(BASE_DIR / "uploads")

    # Segreti (se vuoti vengono letti/generati in .secrets)
    jwt_secret: str = ""
    data_key: str = ""  # chiave Fernet per la cifratura a riposo
    email_pepper: str = ""  # per l'hash delle email
    file_secret: str = ""  # firma degli URL temporanei delle foto

    access_token_minuti: int = 15
    refresh_token_giorni: int = 30
    cookie_secure: bool = False  # True in produzione dietro HTTPS
    url_file_minuti: int = 30  # validità degli URL firmati

    # Visibilità geografica (metri)
    raggio_min_m: int = 300
    raggio_max_m: int = 5000

    # Limiti anti-abuso
    rate_limit_login_min: int = 10
    rate_limit_scrittura_min: int = 30
    post_al_giorno: int = 10  # quota pubblicazioni per utente
    nuove_chat_ora: int = 10
    messaggi_senza_risposta: int = 3  # limite contatto finché l'altra persona non risponde
    foto_max_mb: int = 5
    foto_per_post: int = 4
    notifiche_immediate_ora: int = 6  # oltre questa soglia finiscono nel riepilogo


def _segreti() -> dict[str, str]:
    dati: dict[str, str] = {}
    if FILE_SEGRETI.exists():
        dati = json.loads(FILE_SEGRETI.read_text(encoding="utf-8"))
    mancanti = False
    from cryptography.fernet import Fernet

    generatori = {
        "jwt_secret": lambda: secrets.token_urlsafe(48),
        "data_key": lambda: Fernet.generate_key().decode(),
        "email_pepper": lambda: secrets.token_urlsafe(32),
        "file_secret": lambda: secrets.token_urlsafe(32),
    }
    for chiave, genera in generatori.items():
        if not dati.get(chiave):
            dati[chiave] = genera()
            mancanti = True
    if mancanti:
        FILE_SEGRETI.write_text(json.dumps(dati, indent=2), encoding="utf-8")
    return dati


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    s = Settings()
    if not (s.jwt_secret and s.data_key and s.email_pepper and s.file_secret):
        segreti = _segreti()
        s.jwt_secret = s.jwt_secret or segreti["jwt_secret"]
        s.data_key = s.data_key or segreti["data_key"]
        s.email_pepper = s.email_pepper or segreti["email_pepper"]
        s.file_secret = s.file_secret or segreti["file_secret"]
    return s
