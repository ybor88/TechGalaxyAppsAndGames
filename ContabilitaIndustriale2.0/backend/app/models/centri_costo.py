from __future__ import annotations

from decimal import Decimal
from datetime import datetime
from sqlalchemy import Numeric, String, DateTime, ForeignKey, Text, Boolean, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class CentroCosto(Base):
    """Centro di costo/ricavo: unità organizzativa su cui si aggregano i costi interni."""

    __tablename__ = "centri_costo"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    codice: Mapped[str] = mapped_column(String(20), unique=True, index=True)
    descrizione: Mapped[str] = mapped_column(String(200))
    tipo: Mapped[str] = mapped_column(String(20))  # produttivo | ausiliario | comune
    centro_padre_id: Mapped[int | None] = mapped_column(ForeignKey("centri_costo.id"), nullable=True)
    attivo: Mapped[bool] = mapped_column(Boolean, default=True)
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    centro_padre: Mapped["CentroCosto"] = relationship("CentroCosto", remote_side=[id])


class BaseRiparto(Base):
    """Base di riparto: driver usato per ribaltare un costo indiretto sui centri (es. ore macchina, mq, kWh)."""

    __tablename__ = "basi_riparto"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    descrizione: Mapped[str] = mapped_column(String(200))
    unita_misura: Mapped[str] = mapped_column(String(30))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class ValoreBaseRiparto(Base):
    """Quantità del driver consumata da un centro di costo in un dato periodo (es. ore macchina di Gennaio 2026)."""

    __tablename__ = "valori_base_riparto"
    __table_args__ = (
        UniqueConstraint("base_riparto_id", "centro_costo_id", "periodo", name="uq_valore_base_riparto"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    base_riparto_id: Mapped[int] = mapped_column(ForeignKey("basi_riparto.id"), index=True)
    centro_costo_id: Mapped[int] = mapped_column(ForeignKey("centri_costo.id"), index=True)
    periodo: Mapped[str] = mapped_column(String(7), index=True)  # "YYYY-MM"
    quantita: Mapped[Decimal] = mapped_column(Numeric(15, 3))

    base_riparto: Mapped["BaseRiparto"] = relationship("BaseRiparto")
    centro_costo: Mapped["CentroCosto"] = relationship("CentroCosto")


class RipartoCostoIndiretto(Base):
    """Costo indiretto sostenuto da un centro (tipicamente ausiliario/comune) da ribaltare su altri centri
    in proporzione al consumo della base di riparto scelta (metodo delle basi di riparto multiple)."""

    __tablename__ = "riparti_costo_indiretto"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    descrizione: Mapped[str] = mapped_column(String(300))
    centro_costo_origine_id: Mapped[int] = mapped_column(ForeignKey("centri_costo.id"), index=True)
    importo: Mapped[Decimal] = mapped_column(Numeric(15, 2))
    base_riparto_id: Mapped[int] = mapped_column(ForeignKey("basi_riparto.id"), index=True)
    periodo: Mapped[str] = mapped_column(String(7), index=True)  # "YYYY-MM"
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    centro_costo_origine: Mapped["CentroCosto"] = relationship("CentroCosto")
    base_riparto: Mapped["BaseRiparto"] = relationship("BaseRiparto")
