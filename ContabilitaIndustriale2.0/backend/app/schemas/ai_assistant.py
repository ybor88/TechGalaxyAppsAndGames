from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    messaggio: str = Field(..., min_length=1, max_length=4000)
    sessione_id: str | None = None


class ChatResponse(BaseModel):
    risposta: str
    sessione_id: str
    model: str | None = None


class MessaggioResponse(BaseModel):
    id: int
    sessione_id: str
    ruolo: str
    contenuto: str
    created_at: datetime

    model_config = {"from_attributes": True}


class StatusResponse(BaseModel):
    ollama_disponibile: bool
    modello: str
    messaggio: str


class CancellaResponse(BaseModel):
    cancellati: int
