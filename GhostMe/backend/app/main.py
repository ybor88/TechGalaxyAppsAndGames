"""
Ghost Me — API REST (OpenAPI 3 su /docs).
Monolite modulare: i router sono separabili in servizi quando il carico lo richiede.

© Roberto Di Flumeri
"""

import asyncio
import logging
from contextlib import asynccontextmanager
from datetime import timedelta

from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import delete, select

from app.config import POLICY_VERSION, PROMPT_VERSION, get_settings
from app import database
from app.database import init_db
from app.errori import gestore_validazione
from app.metriche import MiddlewareOsservabilita
from app.models import Conversation, RefreshToken, User, adesso
from app.routers import account, admin, auth, chat, condivisione, decisioni, evoluzione, profilo

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
log = logging.getLogger("ghostme")


async def applica_conservazione() -> int:
    """Elimina le conversazioni più vecchie del periodo di conservazione scelto da ogni utente."""
    eliminate = 0
    async with database.SessionLocal() as db:
        utenti = (await db.execute(select(User).where(User.conservazione_chat_giorni.is_not(None)))).scalars().all()
        for u in utenti:
            limite = adesso() - timedelta(days=u.conservazione_chat_giorni)
            res = await db.execute(delete(Conversation).where(Conversation.user_id == u.id, Conversation.updated_at < limite))
            eliminate += res.rowcount or 0
        await db.execute(delete(RefreshToken).where(RefreshToken.scadenza < adesso()))
        await db.commit()
    return eliminate


async def _ciclo_conservazione():
    while True:
        try:
            n = await applica_conservazione()
            if n:
                log.info("conservazione: eliminate %d conversazioni scadute", n)
        except Exception:  # noqa: BLE001
            log.exception("errore nella pulizia periodica")
        await asyncio.sleep(6 * 3600)


@asynccontextmanager
async def lifespan(_app: FastAPI):
    await init_db()
    attivita = asyncio.create_task(_ciclo_conservazione())
    yield
    attivita.cancel()


s = get_settings()
app = FastAPI(
    title="Ghost Me API",
    version=s.app_version,
    description="Una copia digitale di te, sotto il tuo controllo. © Roberto Di Flumeri",
    lifespan=lifespan,
    redirect_slashes=False,
)
app.add_exception_handler(RequestValidationError, gestore_validazione)
app.add_middleware(MiddlewareOsservabilita)
app.add_middleware(
    CORSMiddleware,
    allow_origins=[s.frontend_origin],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

for r in (auth, profilo, chat, decisioni, evoluzione, condivisione, account, admin):
    app.include_router(r.router)


@app.get("/v1/health", tags=["Sistema"])
async def health():
    return {"stato": "ok", "versione": s.app_version, "prompt": PROMPT_VERSION, "policy": POLICY_VERSION}


@app.get("/v1/ai/stato", tags=["Sistema"])
async def stato_ai():
    from fastapi.concurrency import run_in_threadpool

    from app.services.motore import MotoreOllama

    ollama = MotoreOllama()
    disponibile = await run_in_threadpool(ollama.disponibile)
    return {"ollama_disponibile": disponibile, "modello": ollama.modello,
            "nota": "Senza Ollama l'alter ego usa il motore locale integrato, sempre disponibile."}
