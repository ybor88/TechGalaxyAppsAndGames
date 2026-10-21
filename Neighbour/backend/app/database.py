"""Motore database asincrono (SQLite di default, PostgreSQL via NEIGHBOUR_DATABASE_URL). © Roberto Di Flumeri"""

from sqlalchemy import event
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase

from app.config import get_settings


class Base(DeclarativeBase):
    pass


def crea_engine(url: str):
    sqlite = url.startswith("sqlite")
    engine = create_async_engine(url, connect_args={"timeout": 30} if sqlite else {})
    if sqlite:
        @event.listens_for(engine.sync_engine, "connect")
        def _pragma(dbapi_conn, _record):
            cur = dbapi_conn.cursor()
            cur.execute("PRAGMA journal_mode=WAL")
            cur.execute("PRAGMA foreign_keys=ON")  # integrità referenziale applicata dal DB
            cur.execute("PRAGMA busy_timeout=30000")
            cur.close()
    return engine


engine = crea_engine(get_settings().database_url)
SessionLocal = async_sessionmaker(engine, expire_on_commit=False)


async def get_db():
    async with SessionLocal() as session:
        yield session


async def init_db(eng=None) -> None:
    import app.models  # noqa: F401 — registra i modelli

    async with (eng or engine).begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
