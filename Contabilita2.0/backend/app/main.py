from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.database import init_db
from app.routers import dashboard
from app.seed import run_seed


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Avvio: crea tabelle e inserisce dati demo se il DB è vuoto
    await init_db()
    await run_seed()
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


@app.get("/health")
async def health_check():
    return {"status": "ok", "version": settings.app_version}
