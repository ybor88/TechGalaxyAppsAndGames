"""Fixture comuni: database SQLite isolato per ogni test e vicini di prova. © Roberto Di Flumeri"""

import os
import sys
import tempfile
from pathlib import Path

from cryptography.fernet import Fernet

# segreti di test (nessun file .secrets toccato): vanno impostati prima di importare l'app
os.environ.setdefault("NEIGHBOUR_JWT_SECRET", "segreto-di-test-" + "x" * 40)
os.environ.setdefault("NEIGHBOUR_DATA_KEY", Fernet.generate_key().decode())
os.environ.setdefault("NEIGHBOUR_EMAIL_PEPPER", "pepe-di-test")
os.environ.setdefault("NEIGHBOUR_FILE_SECRET", "firma-di-test")
os.environ.setdefault("NEIGHBOUR_DATABASE_URL", "sqlite+aiosqlite:///:memory:")
os.environ.setdefault("NEIGHBOUR_CARTELLA_FILE", tempfile.mkdtemp(prefix="neighbour-test-"))

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import pytest_asyncio  # noqa: E402
from httpx import ASGITransport, AsyncClient  # noqa: E402
from sqlalchemy.ext.asyncio import async_sessionmaker  # noqa: E402

from app import database  # noqa: E402
from app.database import crea_engine, get_db, init_db  # noqa: E402
from app.main import app  # noqa: E402
from app.security import azzera_rate_limit  # noqa: E402

PASSWORD = "Password123vicini"
TUTTI = {"trattamento_dati": True, "geolocalizzazione": True, "notifiche": True, "profilazione": True, "analytics": True}

# Milano, quartiere Isola e dintorni
ISOLA = (45.4869, 9.1897)
VICINO_900M = (45.4930, 9.1980)
DUOMO_3KM = (45.4642, 9.1900)
ROMA = (41.9028, 12.4964)


@pytest_asyncio.fixture
async def engine(tmp_path):
    eng = crea_engine(f"sqlite+aiosqlite:///{(tmp_path / 'test.db').as_posix()}")
    await init_db(eng)
    yield eng
    await eng.dispose()


@pytest_asyncio.fixture
async def fabbrica(engine):
    return async_sessionmaker(engine, expire_on_commit=False)


@pytest_asyncio.fixture
async def client(fabbrica, monkeypatch):
    async def _db():
        async with fabbrica() as s:
            yield s

    app.dependency_overrides[get_db] = _db
    monkeypatch.setattr(database, "SessionLocal", fabbrica)
    azzera_rate_limit()
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as c:
        yield c
    app.dependency_overrides.clear()


async def registra(client, email, alias="Vicino", consensi=None, tipo_profilo="privato", attivita=None):
    r = await client.post("/v1/auth/register", json={
        "email": email, "password": PASSWORD, "alias": alias, "accetto_termini": True,
        "consensi": consensi or TUTTI, "tipo_profilo": tipo_profilo, "attivita": attivita,
    })
    assert r.status_code == 201, r.text
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


async def imposta_area(client, h, pos=ISOLA, raggio=1500, nome="Isola, Milano"):
    r = await client.put("/v1/area", headers=h, json={"nome": nome, "lat": pos[0], "lon": pos[1], "raggio_m": raggio})
    assert r.status_code == 200, r.text
    return r.json()


async def vicino(client, email, alias, pos=ISOLA, raggio=1500, **kw):
    h = await registra(client, email, alias, **kw)
    await imposta_area(client, h, pos, raggio)
    # nei test le notifiche arrivano subito: niente ore di silenzio
    await client.patch("/v1/preferences", headers=h, json={"notifiche": {"senza_silenzio": True}})
    return h


async def pubblica(client, h, **campi):
    corpo = {"tipo": "regalo", "categoria": "bambini", "titolo": "Passeggino in regalo",
             "descrizione": "Passeggino leggero in buono stato, ritiro in zona."}
    corpo.update(campi)
    r = await client.post("/v1/posts", headers=h, json=corpo)
    assert r.status_code == 201, r.text
    return r.json()


async def id_utente(client, h) -> str:
    return (await client.get("/v1/auth/me", headers=h)).json()["id"]


@pytest_asyncio.fixture
async def anna(client):
    return await vicino(client, "anna@example.com", "Anna")


@pytest_asyncio.fixture
async def bruno(client):
    return await vicino(client, "bruno@example.com", "Bruno", VICINO_900M)


@pytest_asyncio.fixture
async def carla(client):
    """Abita più lontano (circa 2,5 km dall'Isola)."""
    return await vicino(client, "carla@example.com", "Carla", DUOMO_3KM)
