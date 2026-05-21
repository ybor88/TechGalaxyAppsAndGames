from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.schemas.ai_assistant import (
    CancellaResponse,
    ChatRequest,
    ChatResponse,
    MessaggioResponse,
    StatusResponse,
)
from app.services.ai_assistant import AIAssistantService

router = APIRouter()


@router.get("/status", response_model=StatusResponse)
async def get_status(db: AsyncSession = Depends(get_db)):
    """Verifica la disponibilità di Ollama e del modello configurato."""
    service = AIAssistantService(db)
    return await service.check_status()


@router.post("/chat", response_model=ChatResponse)
async def chat(payload: ChatRequest, db: AsyncSession = Depends(get_db)):
    """Invia un messaggio all'AI assistant e ricevi la risposta."""
    service = AIAssistantService(db)
    return await service.chat(payload.messaggio, payload.sessione_id)


@router.get("/cronologia", response_model=list[MessaggioResponse])
async def get_cronologia(
    sessione_id: str = Query(..., description="ID sessione di cui recuperare la cronologia"),
    db: AsyncSession = Depends(get_db),
):
    """Recupera la cronologia messaggi di una sessione."""
    service = AIAssistantService(db)
    return await service.get_cronologia(sessione_id)


@router.delete("/cronologia", response_model=CancellaResponse)
async def cancella_cronologia(
    sessione_id: str = Query(..., description="ID sessione da cancellare"),
    db: AsyncSession = Depends(get_db),
):
    """Cancella la cronologia messaggi di una sessione."""
    service = AIAssistantService(db)
    return await service.cancella_cronologia(sessione_id)
