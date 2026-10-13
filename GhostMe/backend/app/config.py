"""
Configurazione da variabili d'ambiente / file .env.
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

# Versioni registrate su ogni risposta generata (riproducibilità e audit)
PROMPT_VERSION = "alter-ego-prompt/1.1"
POLICY_VERSION = "ghostme-policy/1.0"
CONSENT_VERSION = "consensi/1.0"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=BASE_DIR / ".env", env_prefix="GHOSTME_", extra="ignore")

    app_name: str = "Ghost Me"
    app_version: str = "1.0.0"
    # SQLite in locale; per la produzione: postgresql+asyncpg://utente:pwd@host/ghostme
    database_url: str = f"sqlite+aiosqlite:///{(BASE_DIR / 'ghostme.db').as_posix()}"
    frontend_origin: str = "http://localhost:3100"

    # Segreti (se vuoti vengono letti/generati in .secrets)
    jwt_secret: str = ""
    data_key: str = ""  # chiave Fernet per la cifratura a riposo
    email_pepper: str = ""  # per l'hash delle email

    access_token_minuti: int = 15
    refresh_token_giorni: int = 30
    cookie_secure: bool = False  # True in produzione dietro HTTPS

    # Motore AI
    ollama_url: str = "http://localhost:11434"
    ollama_model: str = "llama3"
    ollama_timeout_s: float = 240.0

    # Limiti
    share_max_domande: int = 50
    rate_limit_login_min: int = 10
    rate_limit_chat_min: int = 30
    rate_limit_public_min: int = 20


def _segreti() -> dict[str, str]:
    dati: dict[str, str] = {}
    if FILE_SEGRETI.exists():
        dati = json.loads(FILE_SEGRETI.read_text(encoding="utf-8"))
    mancanti = False
    from cryptography.fernet import Fernet

    if not dati.get("jwt_secret"):
        dati["jwt_secret"] = secrets.token_urlsafe(48)
        mancanti = True
    if not dati.get("data_key"):
        dati["data_key"] = Fernet.generate_key().decode()
        mancanti = True
    if not dati.get("email_pepper"):
        dati["email_pepper"] = secrets.token_urlsafe(32)
        mancanti = True
    if mancanti:
        FILE_SEGRETI.write_text(json.dumps(dati, indent=2), encoding="utf-8")
    return dati


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    s = Settings()
    if not (s.jwt_secret and s.data_key and s.email_pepper):
        segreti = _segreti()
        s.jwt_secret = s.jwt_secret or segreti["jwt_secret"]
        s.data_key = s.data_key or segreti["data_key"]
        s.email_pepper = s.email_pepper or segreti["email_pepper"]
    return s
