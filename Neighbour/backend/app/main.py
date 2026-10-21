"""
Neighbour — API REST (OpenAPI 3 su /docs).
Monolite modulare: i router sono separabili in servizi quando il carico lo richiede (§6).

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

from app import database
from app.config import POLICY_VERSION, get_settings
from app.database import init_db
from app.errori import gestore_validazione
from app.metriche import MiddlewareOsservabilita
from app.models import Photo, RefreshToken, adesso
from app.routers import (
    account, admin, auth, chat, eventi, feed, files, gruppi, notifiche, posts, preferenze, reputazione, segnalazioni, servizi,
)
from app.services import catalogo, foto
from app.services.controlli import AVVISO_EMERGENZA
from app.services.notifiche import genera_riepiloghi

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
log = logging.getLogger("neighbour")


async def manutenzione() -> dict:
    """Riepiloghi delle notifiche, foto mai collegate a un contenuto, sessioni scadute."""
    async with database.SessionLocal() as db:
        riepiloghi = await genera_riepiloghi(db)
        orfane = (await db.execute(select(Photo).where(Photo.post_id.is_(None),
                                                       Photo.created_at < adesso() - timedelta(hours=24)))).scalars().all()
        for f in orfane:
            (foto.cartella() / f.file).unlink(missing_ok=True)
            await db.delete(f)
        await db.execute(delete(RefreshToken).where(RefreshToken.scadenza < adesso()))
        await db.commit()
    return {"riepiloghi": riepiloghi, "foto_rimosse": len(orfane)}


async def _ciclo():
    while True:
        try:
            esito = await manutenzione()
            if any(esito.values()):
                log.info("manutenzione: %s", esito)
        except Exception:  # noqa: BLE001
            log.exception("errore nella manutenzione periodica")
        await asyncio.sleep(15 * 60)


@asynccontextmanager
async def lifespan(_app: FastAPI):
    await init_db()
    attivita = asyncio.create_task(_ciclo())
    yield
    attivita.cancel()


s = get_settings()
app = FastAPI(title="Neighbour API", version=s.app_version, lifespan=lifespan, redirect_slashes=False,
              description="Persone vere. Più vicine. — API dell'app iperlocale Neighbour. © Roberto Di Flumeri")
app.add_exception_handler(RequestValidationError, gestore_validazione)
app.add_middleware(MiddlewareOsservabilita)
app.add_middleware(CORSMiddleware, allow_origins=[s.frontend_origin], allow_credentials=True,
                   allow_methods=["*"], allow_headers=["*"])

for r in (auth, preferenze, files, posts, eventi, feed, chat, gruppi, servizi, reputazione, segnalazioni,
          notifiche, account, admin):
    app.include_router(r.router)


@app.get("/v1/health", tags=["Sistema"])
async def health():
    return {"stato": "ok", "versione": s.app_version, "linee_guida": POLICY_VERSION}


@app.get("/v1/catalogo", tags=["Sistema"])
async def vocabolari():
    """Tipi, categorie e motivi: un'unica fonte per app e backend."""
    return {
        "tipi_post": catalogo.TIPI_POST,
        "categorie": {"mercatino": catalogo.CATEGORIE_MERCATINO, "aiuto": catalogo.CATEGORIE_AIUTO,
                      "segnalazione": catalogo.CATEGORIE_SEGNALAZIONE, "evento": catalogo.CATEGORIE_EVENTO,
                      "gruppo": catalogo.CATEGORIE_GRUPPO, "servizio": catalogo.CATEGORIE_SERVIZIO},
        "motivi_segnalazione": catalogo.MOTIVI_SEGNALAZIONE,
        "decisioni_moderazione": catalogo.DECISIONI_MODERAZIONE,
        "raggio": {"min": s.raggio_min_m, "max": s.raggio_max_m},
        "avviso_emergenza": AVVISO_EMERGENZA,
    }
