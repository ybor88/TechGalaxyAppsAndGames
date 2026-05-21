from contextlib import asynccontextmanager
import asyncio
import logging

from fastapi import FastAPI

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%H:%M:%S",
)
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.database import init_db
from app.routers import dashboard, movimenti, conti, fatturazione, ocr, contabilita, crm, workflow, forecasting, ai_assistant
import app.models.crm  # noqa: F401 — registers CRM tables with SQLAlchemy metadata
import app.models.workflow  # noqa: F401 — registers Workflow tables with SQLAlchemy metadata
import app.models.ai_assistant  # noqa: F401 — registers AI assistant tables with SQLAlchemy metadata


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
)

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


@app.get("/health")
async def health_check():
    return {"status": "ok", "version": settings.app_version}
