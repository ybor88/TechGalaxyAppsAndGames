import os
import sys
from pathlib import Path

os.environ.setdefault("DATABASE_URL", "sqlite+aiosqlite:///./tests_unused.db")

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from app.database import Base, get_db

# Registra tutti i modelli ORM nel metadata di Base prima del create_all.
import app.models.financial  # noqa: F401
import app.models.fatturazione  # noqa: F401
import app.models.ocr  # noqa: F401
import app.models.contabilita  # noqa: F401
import app.models.crm  # noqa: F401
import app.models.workflow  # noqa: F401
import app.models.ai_assistant  # noqa: F401

from app.main import app as fastapi_app


@pytest_asyncio.fixture
async def db_engine(tmp_path):
    """Motore SQLite isolato (file temporaneo) per ogni test."""
    db_path = tmp_path / "test.db"
    engine = create_async_engine(
        f"sqlite+aiosqlite:///{db_path}",
        connect_args={"check_same_thread": False},
    )
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield engine
    await engine.dispose()


@pytest_asyncio.fixture
async def client(db_engine):
    """AsyncClient HTTP contro l'app FastAPI con DB di test isolato."""
    session_factory = async_sessionmaker(db_engine, expire_on_commit=False)

    async def override_get_db():
        async with session_factory() as session:
            yield session

    fastapi_app.dependency_overrides[get_db] = override_get_db
    transport = ASGITransport(app=fastapi_app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac
    fastapi_app.dependency_overrides.clear()


@pytest_asyncio.fixture
async def db_session(db_engine):
    """Sessione DB diretta, utile per popolare fixture dati nei test."""
    session_factory = async_sessionmaker(db_engine, expire_on_commit=False)
    async with session_factory() as session:
        yield session
