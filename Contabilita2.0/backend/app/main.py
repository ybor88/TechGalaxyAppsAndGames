from contextlib import asynccontextmanager
import asyncio
import logging

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%H:%M:%S",
)
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.errors import Conflitto, RiferimentoNonValido
from app.database import init_db
from app.seed import run_seed
from app.routers import dashboard, movimenti, conti, fatturazione, ocr, contabilita, crm, workflow, forecasting, ai_assistant, ammortamenti, paghe
import app.models.crm  # noqa: F401 — registers CRM tables with SQLAlchemy metadata
import app.models.workflow  # noqa: F401 — registers Workflow tables with SQLAlchemy metadata
import app.models.ai_assistant  # noqa: F401 — registers AI assistant tables with SQLAlchemy metadata
import app.models.ammortamenti  # noqa: F401 — registers Ammortamenti tables with SQLAlchemy metadata
import app.models.paghe  # noqa: F401 — registers Paghe tables with SQLAlchemy metadata


async def _pull_ollama_model() -> None:
    """Avvia Ollama se non in esecuzione, poi scarica il modello se mancante."""
    import httpx
    import os
    import shutil
    import subprocess

    await asyncio.sleep(3)

    # Controlla se Ollama è già raggiungibile
    ollama_attivo = False
    try:
        async with httpx.AsyncClient(timeout=4.0) as client:
            r = await client.get(f"{settings.ollama_base_url}/api/tags")
        ollama_attivo = r.status_code == 200
    except Exception:
        pass

    # Se non attivo, prova ad avviarlo (standalone Windows)
    if not ollama_attivo:
        percorsi = [
            shutil.which("ollama"),
            os.path.expandvars(r"%LOCALAPPDATA%\Programs\Ollama\ollama.exe"),
            os.path.expandvars(r"%USERPROFILE%\AppData\Local\Programs\Ollama\ollama.exe"),
        ]
        for path in percorsi:
            if path and os.path.isfile(path):
                try:
                    subprocess.Popen(
                        [path, "serve"],
                        stdout=subprocess.DEVNULL,
                        stderr=subprocess.DEVNULL,
                    )
                    await asyncio.sleep(5)
                    async with httpx.AsyncClient(timeout=4.0) as client:
                        r = await client.get(f"{settings.ollama_base_url}/api/tags")
                    ollama_attivo = r.status_code == 200
                except Exception:
                    pass
                break

    if not ollama_attivo:
        return  # Ollama non disponibile — l'utente vedrà il badge Offline nella UI

    # Scarica il modello se non già presente
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            r = await client.get(f"{settings.ollama_base_url}/api/tags")
        modelli = [m.get("name", "") for m in r.json().get("models", [])]
        if not any(settings.ollama_model in n for n in modelli):
            async with httpx.AsyncClient(timeout=600.0) as client:
                await client.post(
                    f"{settings.ollama_base_url}/api/pull",
                    json={"name": settings.ollama_model, "stream": False},
                )
    except Exception:
        pass


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    await run_seed()
    asyncio.create_task(_pull_ollama_model())
    yield


app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    description="ERP contabile aziendale 100% open source",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
    expose_headers=["X-Total-Count"],
)

def _routes():
    return app.router.routes


class _BarraFinaleFlessibile:
    """
    Accetta sia /api/v1/documenti sia /api/v1/documenti/ senza redirect.
    Il redirect standard di Starlette punta a un URL assoluto (http://localhost:8000/...):
    dietro il proxy di Next.js il browser uscirebbe dal proxy e, aprendo l'app da un
    altro PC della rete, "localhost" non sarebbe più il server.
    """

    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] == "http":
            from starlette.routing import Match

            percorso = scope["path"]
            if percorso != "/" and not any(
                r.matches(scope)[0] != Match.NONE for r in _routes()
            ):
                alternativo = percorso[:-1] if percorso.endswith("/") else percorso + "/"
                scope_alt = {**scope, "path": alternativo, "raw_path": alternativo.encode()}
                if any(r.matches(scope_alt)[0] != Match.NONE for r in _routes()):
                    scope = scope_alt
        await self.app(scope, receive, send)


app.add_middleware(_BarraFinaleFlessibile)


@app.exception_handler(Conflitto)
async def _conflitto_handler(_: Request, exc: Conflitto):
    return JSONResponse(status_code=409, content={"detail": str(exc)})


@app.exception_handler(RiferimentoNonValido)
async def _riferimento_handler(_: Request, exc: RiferimentoNonValido):
    return JSONResponse(status_code=422, content={"detail": str(exc)})


# Routers
app.include_router(dashboard.router, prefix="/api/v1/dashboard", tags=["Dashboard Finanziaria"])
app.include_router(movimenti.router, prefix="/api/v1/movimenti", tags=["Movimenti"])
app.include_router(conti.router, prefix="/api/v1/conti", tags=["Conti"])
app.include_router(fatturazione.router, prefix="/api/v1/documenti", tags=["Documenti"])
app.include_router(fatturazione.anagrafiche_router, prefix="/api/v1/anagrafiche", tags=["Anagrafiche"])
app.include_router(ocr.router, prefix="/api/v1/ocr", tags=["OCR Contabile"])
app.include_router(contabilita.router, prefix="/api/v1/contabilita", tags=["Contabilità Generale"])
app.include_router(crm.router, prefix="/api/v1/crm", tags=["CRM Economico"])
app.include_router(workflow.router, prefix="/api/v1/workflow", tags=["Workflow Aziendale"])
app.include_router(forecasting.router, prefix="/api/v1/forecasting", tags=["Forecasting Aziendale"])
app.include_router(ai_assistant.router, prefix="/api/v1/ai", tags=["AI Assistant Locale"])
app.include_router(ammortamenti.router, prefix="/api/v1/ammortamenti", tags=["Ammortamenti"])
app.include_router(paghe.router, prefix="/api/v1/paghe", tags=["Gestione Paga Dipendenti"])


@app.get("/health")
async def health_check():
    return {"status": "ok", "version": settings.app_version}
