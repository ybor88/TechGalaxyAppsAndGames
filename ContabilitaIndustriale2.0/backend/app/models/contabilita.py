from __future__ import annotations

from decimal import Decimal
from datetime import date, datetime
from sqlalchemy import Numeric, String, Date, DateTime, ForeignKey, Text, Boolean, Integer
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class RegistrazioneContabile(Base):
    """Registrazione contabile in partita doppia (prima nota)."""

    __tablename__ = "registrazioni_contabili"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    numero: Mapped[int] = mapped_column(Integer, index=True)  # numero progressivo
    data: Mapped[date] = mapped_column(Date, index=True)
    causale: Mapped[str] = mapped_column(String(500))
    tipo_causale: Mapped[str] = mapped_column(String(30), default="manuale")
    # manuale | fattura_attiva | fattura_passiva | pagamento | incasso | altro
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    chiusa: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    righe: Mapped[list[RigaRegistrazione]] = relationship(
        back_populates="registrazione",
        cascade="all, delete-orphan",
        order_by="RigaRegistrazione.id",
    )


class RigaRegistrazione(Base):
    """Singola riga dare/avere di una registrazione contabile (partita doppia)."""

    __tablename__ = "righe_registrazione"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    registrazione_id: Mapped[int] = mapped_column(
        ForeignKey("registrazioni_contabili.id"), index=True
    )
    conto_id: Mapped[int] = mapped_column(ForeignKey("conti.id"), index=True)
    descrizione: Mapped[str | None] = mapped_column(String(500), nullable=True)
    dare: Mapped[Decimal] = mapped_column(Numeric(15, 2), default=Decimal("0.00"))
    avere: Mapped[Decimal] = mapped_column(Numeric(15, 2), default=Decimal("0.00"))
    # Tracciamento IVA
    aliquota_iva: Mapped[Decimal | None] = mapped_column(Numeric(5, 2), nullable=True)
    tipo_iva: Mapped[str | None] = mapped_column(String(20), nullable=True)
    # tipo_iva: "imponibile" | "iva" | "esente" | None

    registrazione: Mapped[RegistrazioneContabile] = relationship(back_populates="righe")
    conto: Mapped["Conto"] = relationship("Conto")  # type: ignore[name-defined]
