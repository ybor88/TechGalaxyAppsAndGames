from __future__ import annotations

from decimal import Decimal
from datetime import date, datetime
from sqlalchemy import Numeric, String, Date, DateTime, ForeignKey, Text, Integer, Boolean
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class Task(Base):
    """Task aziendale: generico, approvazione, reminder o richiesta acquisto."""

    __tablename__ = "workflow_tasks"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    titolo: Mapped[str] = mapped_column(String(300))
    descrizione: Mapped[str | None] = mapped_column(Text, nullable=True)
    tipo: Mapped[str] = mapped_column(String(20), default="task", index=True)
    # task | approvazione | reminder | acquisto
    stato: Mapped[str] = mapped_column(String(20), default="aperto", index=True)
    # aperto | in_corso | completato | annullato | approvato | rifiutato
    priorita: Mapped[str] = mapped_column(String(10), default="media")
    # bassa | media | alta | urgente
    assegnato_a: Mapped[str | None] = mapped_column(String(200), nullable=True, index=True)
    creato_da: Mapped[str | None] = mapped_column(String(200), nullable=True)
    data_scadenza: Mapped[date | None] = mapped_column(Date, nullable=True, index=True)
    data_completamento: Mapped[date | None] = mapped_column(Date, nullable=True)
    # Campi specifici per acquisti
    anagrafica_id: Mapped[int | None] = mapped_column(
        ForeignKey("anagrafiche.id"), nullable=True, index=True
    )
    importo_stimato: Mapped[Decimal | None] = mapped_column(Numeric(15, 2), nullable=True)
    importo_approvato: Mapped[Decimal | None] = mapped_column(Numeric(15, 2), nullable=True)
    # Flag reminder
    reminder_inviato: Mapped[bool] = mapped_column(Boolean, default=False)
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    passi: Mapped[list["PassoApprovazione"]] = relationship(
        back_populates="task",
        cascade="all, delete-orphan",
        order_by="PassoApprovazione.ordine",
    )
    anagrafica: Mapped["Anagrafica | None"] = relationship("Anagrafica")  # type: ignore[name-defined]


class PassoApprovazione(Base):
    """Singolo passo di un flusso di approvazione multi-livello."""

    __tablename__ = "workflow_passi_approvazione"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    task_id: Mapped[int] = mapped_column(ForeignKey("workflow_tasks.id"), index=True)
    ordine: Mapped[int] = mapped_column(Integer, default=1)
    approvatore: Mapped[str] = mapped_column(String(200))
    stato: Mapped[str] = mapped_column(String(20), default="in_attesa")
    # in_attesa | approvato | rifiutato
    commento: Mapped[str | None] = mapped_column(Text, nullable=True)
    aggiornato_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    task: Mapped["Task"] = relationship(back_populates="passi")
