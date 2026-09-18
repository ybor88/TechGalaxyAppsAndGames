from __future__ import annotations

from decimal import Decimal
from datetime import date, datetime
from sqlalchemy import Numeric, String, Date, DateTime, ForeignKey, Text, Boolean, Integer, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class Cespite(Base):
    """Bene ammortizzabile (cespite): immobilizzazione materiale o immateriale."""

    __tablename__ = "cespiti"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    descrizione: Mapped[str] = mapped_column(String(300))
    categoria: Mapped[str] = mapped_column(String(50))  # chiave TABELLA_MINISTERIALE
    data_acquisto: Mapped[date] = mapped_column(Date, index=True)
    costo_storico: Mapped[Decimal] = mapped_column(Numeric(15, 2))
    aliquota_civilistica: Mapped[Decimal] = mapped_column(Numeric(5, 2))
    aliquota_fiscale: Mapped[Decimal] = mapped_column(Numeric(5, 2))
    conto_costo_id: Mapped[int] = mapped_column(ForeignKey("conti.id"), index=True)
    conto_fondo_id: Mapped[int] = mapped_column(ForeignKey("conti.id"), index=True)
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    dismesso: Mapped[bool] = mapped_column(Boolean, default=False)
    data_dismissione: Mapped[date | None] = mapped_column(Date, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    conto_costo: Mapped["Conto"] = relationship("Conto", foreign_keys=[conto_costo_id])  # type: ignore[name-defined]
    conto_fondo: Mapped["Conto"] = relationship("Conto", foreign_keys=[conto_fondo_id])  # type: ignore[name-defined]
    quote_contabilizzate: Mapped[list["QuotaAmmortamentoContabilizzata"]] = relationship(
        back_populates="cespite",
        cascade="all, delete-orphan",
        order_by="QuotaAmmortamentoContabilizzata.anno",
    )


class QuotaAmmortamentoContabilizzata(Base):
    """Traccia le quote annuali di ammortamento già registrate in prima nota (evita doppie registrazioni)."""

    __tablename__ = "quote_ammortamento_contabilizzate"
    __table_args__ = (UniqueConstraint("cespite_id", "anno", name="uq_quota_cespite_anno"),)

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    cespite_id: Mapped[int] = mapped_column(ForeignKey("cespiti.id"), index=True)
    anno: Mapped[int] = mapped_column(Integer, index=True)
    importo: Mapped[Decimal] = mapped_column(Numeric(15, 2))
    registrazione_id: Mapped[int] = mapped_column(ForeignKey("registrazioni_contabili.id"))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    cespite: Mapped["Cespite"] = relationship(back_populates="quote_contabilizzate")
