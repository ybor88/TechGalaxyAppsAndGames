from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.database import init_db
from app.routers import dashboard, movimenti, conti, fatturazione, ocr, contabilita, crm, workflow
import app.models.crm  # noqa: F401 — registers CRM tables with SQLAlchemy metadata
import app.models.workflow  # noqa: F401 — registers Workflow tables with SQLAlchemy metadata


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Avvio: crea solo le tabelle
    await init_db()
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


@app.get("/health")
async def health_check():
    return {"status": "ok", "version": settings.app_version}
