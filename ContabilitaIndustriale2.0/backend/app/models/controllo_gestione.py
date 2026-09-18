from __future__ import annotations

from datetime import datetime
from sqlalchemy import String, DateTime, ForeignKey
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class ClassificazioneCosto(Base):
    """Classifica un conto di costo/ricavo come fisso o variabile, base per direct costing e break-even."""

    __tablename__ = "classificazioni_costo"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    conto_id: Mapped[int] = mapped_column(ForeignKey("conti.id"), unique=True, index=True)
    classificazione: Mapped[str] = mapped_column(String(10))  # fisso | variabile
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    conto: Mapped["Conto"] = relationship("Conto")  # type: ignore[name-defined]
