"""Fixture comuni: database SQLite isolato per ogni test e utenti di prova. © Roberto Di Flumeri"""

import os
import sys
from pathlib import Path

from cryptography.fernet import Fernet

# segreti di test (nessun file .secrets toccato) — vanno impostati prima di importare l'app
os.environ.setdefault("GHOSTME_JWT_SECRET", "segreto-di-test-" + "x" * 40)
os.environ.setdefault("GHOSTME_DATA_KEY", Fernet.generate_key().decode())
os.environ.setdefault("GHOSTME_EMAIL_PEPPER", "pepe-di-test")
os.environ.setdefault("GHOSTME_OLLAMA_URL", "http://127.0.0.1:9")  # Ollama "spento" nei test
os.environ.setdefault("GHOSTME_DATABASE_URL", "sqlite+aiosqlite:///:memory:")

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import pytest  # noqa: E402
import pytest_asyncio  # noqa: E402
from httpx import ASGITransport, AsyncClient  # noqa: E402
from sqlalchemy.ext.asyncio import async_sessionmaker  # noqa: E402

from app import database  # noqa: E402
from app.database import crea_engine, get_db, init_db  # noqa: E402
from app.main import app  # noqa: E402
from app.security import azzera_rate_limit  # noqa: E402

PASSWORD = "Password123segreta"
TUTTI_I_CONSENSI = {"trattamento_profilo": True, "elaborazione_ai": True, "condivisione": True, "analytics": True}


@pytest_asyncio.fixture
async def engine(tmp_path):
    eng = crea_engine(f"sqlite+aiosqlite:///{(tmp_path / 'test.db').as_posix()}")
    await init_db(eng)
    yield eng
    await eng.dispose()


@pytest_asyncio.fixture
async def sessione(engine):
    fabbrica = async_sessionmaker(engine, expire_on_commit=False)
    async with fabbrica() as s:
        yield s


@pytest_asyncio.fixture
async def client(engine, monkeypatch):
    fabbrica = async_sessionmaker(engine, expire_on_commit=False)

    async def _db():
        async with fabbrica() as s:
            yield s

    app.dependency_overrides[get_db] = _db
    monkeypatch.setattr(database, "SessionLocal", fabbrica)
    azzera_rate_limit()
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as c:
        yield c
    app.dependency_overrides.clear()


async def registra(client, email="mario@example.com", consensi=None, nome="Mario"):
    r = await client.post("/v1/auth/register", json={
        "email": email, "password": PASSWORD, "nome": nome, "accetto_termini": True,
        "consensi": consensi or TUTTI_I_CONSENSI,
    })
    assert r.status_code == 201, r.text
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


async def dichiara(client, h, testo, categoria="valori", peso=3, visibilita="privata", usa_in_ai=True):
    r = await client.post("/v1/profile/statements", headers=h, json={
        "testo": testo, "categoria": categoria, "peso": peso, "visibilita": visibilita, "usa_in_ai": usa_in_ai,
    })
    assert r.status_code == 201, r.text
    return r.json()


@pytest_asyncio.fixture
async def utente(client):
    return await registra(client)


@pytest_asyncio.fixture
async def profilo(client, utente):
    """Utente con alcune dichiarazioni di esempio."""
    h = utente
    await dichiara(client, h, "Amo viaggiare in posti poco turistici e fare trekking in montagna", "gusti", 5, "condivisibile")
    await dichiara(client, h, "La famiglia viene prima del lavoro", "valori", 5, "condivisibile")
    await dichiara(client, h, "Voglio cambiare lavoro e diventare designer entro un anno", "obiettivi", 4)
    await dichiara(client, h, "Lavoro meglio la mattina presto", "abitudini", 3)
    await dichiara(client, h, "Sono diretto ma gentile, uso spesso l'ironia", "stile_comunicativo", 3, "condivisibile")
    await dichiara(client, h, "Il mio diario segreto: non voglio che l'AI lo usi", "altro", 2, usa_in_ai=False)
    return h


@pytest.fixture
def password():
    return PASSWORD
