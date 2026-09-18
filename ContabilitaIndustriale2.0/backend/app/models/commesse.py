from __future__ import annotations

from decimal import Decimal
from datetime import date, datetime
from sqlalchemy import Numeric, String, Date, DateTime, ForeignKey, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class Commessa(Base):
    """Commessa/ordine di produzione: unità su cui si accumulano i costi diretti e indiretti (job order costing)."""

    __tablename__ = "commesse"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    codice: Mapped[str] = mapped_column(String(30), unique=True, index=True)
    descrizione: Mapped[str] = mapped_column(String(300))
    prodotto_id: Mapped[int] = mapped_column(ForeignKey("prodotti.id"), index=True)
    centro_costo_id: Mapped[int] = mapped_column(ForeignKey("centri_costo.id"), index=True)
    anagrafica_id: Mapped[int | None] = mapped_column(ForeignKey("anagrafiche.id"), nullable=True)
    quantita_pianificata: Mapped[Decimal] = mapped_column(Numeric(15, 3))
    quantita_prodotta: Mapped[Decimal | None] = mapped_column(Numeric(15, 3), nullable=True)
    data_apertura: Mapped[date] = mapped_column(Date, index=True)
    data_chiusura: Mapped[date | None] = mapped_column(Date, nullable=True)
    stato: Mapped[str] = mapped_column(String(15), default="aperta")  # aperta | chiusa
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    prodotto: Mapped["Prodotto"] = relationship("Prodotto")  # type: ignore[name-defined]
    centro_costo: Mapped["CentroCosto"] = relationship("CentroCosto")  # type: ignore[name-defined]
    anagrafica: Mapped["Anagrafica"] = relationship("Anagrafica")  # type: ignore[name-defined]
    righe_costo: Mapped[list["RigaCostoCommessa"]] = relationship(
        back_populates="commessa", cascade="all, delete-orphan", order_by="RigaCostoCommessa.data",
    )


class RigaCostoCommessa(Base):
    """Costo diretto o indiretto imputato a una commessa: materiale, manodopera diretta o quota di indiretti."""

    __tablename__ = "righe_costo_commessa"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    commessa_id: Mapped[int] = mapped_column(ForeignKey("commesse.id"), index=True)
    tipo: Mapped[str] = mapped_column(String(15), index=True)  # materiale | manodopera | indiretto
    descrizione: Mapped[str] = mapped_column(String(300))
    data: Mapped[date] = mapped_column(Date, index=True)
    # Materiale: prodotto_id + quantita (qtà consumata) + costo_unitario (prezzo/ora pagato).
    # Manodopera: quantita = ore lavorate, costo_unitario = costo orario effettivo.
    # Indiretto: solo importo (quota di costo indiretto imputata, es. da riparto).
    prodotto_id: Mapped[int | None] = mapped_column(ForeignKey("prodotti.id"), nullable=True)
    centro_costo_id: Mapped[int | None] = mapped_column(ForeignKey("centri_costo.id"), nullable=True)
    quantita: Mapped[Decimal | None] = mapped_column(Numeric(15, 3), nullable=True)
    costo_unitario: Mapped[Decimal | None] = mapped_column(Numeric(15, 4), nullable=True)
    importo: Mapped[Decimal] = mapped_column(Numeric(15, 2))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    commessa: Mapped["Commessa"] = relationship(back_populates="righe_costo")
    prodotto: Mapped["Prodotto"] = relationship("Prodotto")  # type: ignore[name-defined]
    centro_costo: Mapped["CentroCosto"] = relationship("CentroCosto")  # type: ignore[name-defined]
