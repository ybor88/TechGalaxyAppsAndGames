from __future__ import annotations

from decimal import Decimal
from datetime import date, datetime
from sqlalchemy import Numeric, String, Date, DateTime, ForeignKey, Text, Boolean, Integer, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class Dipendente(Base):
    """Anagrafica dipendente con i parametri usati per il calcolo del cedolino."""

    __tablename__ = "dipendenti"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    nome: Mapped[str] = mapped_column(String(100))
    cognome: Mapped[str] = mapped_column(String(100))
    codice_fiscale: Mapped[str | None] = mapped_column(String(16), nullable=True)
    qualifica: Mapped[str | None] = mapped_column(String(100), nullable=True)
    data_assunzione: Mapped[date] = mapped_column(Date)
    data_cessazione: Mapped[date | None] = mapped_column(Date, nullable=True)
    retribuzione_lorda_mensile: Mapped[Decimal] = mapped_column(Numeric(15, 2))
    numero_mensilita: Mapped[int] = mapped_column(Integer, default=13)  # 13 o 14
    aliquota_inps_dipendente: Mapped[Decimal] = mapped_column(Numeric(5, 2), default=Decimal("9.19"))
    aliquota_inps_azienda: Mapped[Decimal] = mapped_column(Numeric(5, 2), default=Decimal("30.00"))
    detrazioni_attive: Mapped[bool] = mapped_column(Boolean, default=True)
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    cedolini: Mapped[list["CedolinoPaga"]] = relationship(
        back_populates="dipendente",
        cascade="all, delete-orphan",
        order_by="CedolinoPaga.anno, CedolinoPaga.mese",
    )


class CedolinoPaga(Base):
    """Cedolino paga mensile calcolato per un dipendente."""

    __tablename__ = "cedolini_paga"
    __table_args__ = (
        UniqueConstraint("dipendente_id", "anno", "mese", name="uq_cedolino_dipendente_periodo"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    dipendente_id: Mapped[int] = mapped_column(ForeignKey("dipendenti.id"), index=True)
    anno: Mapped[int] = mapped_column(Integer, index=True)
    mese: Mapped[int] = mapped_column(Integer, index=True)  # 1-12

    retribuzione_lorda: Mapped[Decimal] = mapped_column(Numeric(15, 2))
    contributi_inps_dipendente: Mapped[Decimal] = mapped_column(Numeric(15, 2))
    imponibile_fiscale: Mapped[Decimal] = mapped_column(Numeric(15, 2))
    irpef_lorda: Mapped[Decimal] = mapped_column(Numeric(15, 2))
    detrazioni_irpef: Mapped[Decimal] = mapped_column(Numeric(15, 2))
    irpef_netta: Mapped[Decimal] = mapped_column(Numeric(15, 2))
    netto_busta: Mapped[Decimal] = mapped_column(Numeric(15, 2))
    contributi_inps_azienda: Mapped[Decimal] = mapped_column(Numeric(15, 2))
    quota_tfr: Mapped[Decimal] = mapped_column(Numeric(15, 2))
    costo_azienda: Mapped[Decimal] = mapped_column(Numeric(15, 2))

    registrazione_id: Mapped[int | None] = mapped_column(
        ForeignKey("registrazioni_contabili.id"), nullable=True
    )
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    dipendente: Mapped["Dipendente"] = relationship(back_populates="cedolini")
