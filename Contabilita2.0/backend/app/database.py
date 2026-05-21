from sqlalchemy import event
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine, async_sessionmaker
from sqlalchemy.orm import DeclarativeBase

from app.config import settings

# connect_args necessario per SQLite (consente uso in thread multipli + busy timeout)
if settings.database_url.startswith("sqlite"):
    connect_args = {
        "check_same_thread": False,
        "timeout": 30,  # secondi di attesa prima di restituire "database is locked"
    }
else:
    connect_args = {}

engine = create_async_engine(
    settings.database_url,
    echo=False,
    connect_args=connect_args,
)

# Abilita WAL e busy_timeout a livello PRAGMA appena si apre una connessione SQLite.
# WAL permette letture concorrenti durante una scrittura ed elimina quasi tutti i lock.
if settings.database_url.startswith("sqlite"):
    @event.listens_for(engine.sync_engine, "connect")
    def _set_sqlite_pragmas(dbapi_conn, _connection_record):
        cursor = dbapi_conn.cursor()
        cursor.execute("PRAGMA journal_mode=WAL")
        cursor.execute("PRAGMA busy_timeout=30000")   # 30 secondi in millisecondi
        cursor.execute("PRAGMA synchronous=NORMAL")   # buon compromesso velocità/sicurezza con WAL
        cursor.close()

AsyncSessionLocal = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)


class Base(DeclarativeBase):
    pass


async def init_db() -> None:
    """Crea tutte le tabelle se non esistono (usato all'avvio)."""
    from app.models import financial  # noqa: F401 — registra i modelli
    from app.models import fatturazione  # noqa: F401 — registra modelli F2
    from app.models import ocr  # noqa: F401 — registra modelli F3
    from app.models import contabilita  # noqa: F401 — registra modelli F4
    from app.models import crm  # noqa: F401 — registra modelli F5
    from app.models import workflow  # noqa: F401 — registra modelli F6
    from app.models import ai_assistant  # noqa: F401 — registra modelli F8
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)


async def get_db() -> AsyncSession:
    async with AsyncSessionLocal() as session:
        try:
            yield session
        finally:
            await session.close()
