from __future__ import annotations

from datetime import datetime

from sqlalchemy import DateTime, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class MessaggioAI(Base):
    """Messaggio della cronologia conversazione con l'AI Assistant."""

    __tablename__ = "ai_messaggi"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    sessione_id: Mapped[str] = mapped_column(String(36), index=True)
    ruolo: Mapped[str] = mapped_column(String(20))  # utente | assistente
    contenuto: Mapped[str] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
