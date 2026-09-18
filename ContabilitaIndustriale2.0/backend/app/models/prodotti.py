from __future__ import annotations

from decimal import Decimal
from datetime import date, datetime
from sqlalchemy import Numeric, String, Date, DateTime, ForeignKey, Text, Boolean, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class Prodotto(Base):
    """Anagrafica materiali/prodotti: materie prime, semilavorati e prodotti finiti."""

    __tablename__ = "prodotti"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    codice: Mapped[str] = mapped_column(String(30), unique=True, index=True)
    descrizione: Mapped[str] = mapped_column(String(300))
    tipo: Mapped[str] = mapped_column(String(20), index=True)  # materia_prima | semilavorato | prodotto_finito
    unita_misura: Mapped[str] = mapped_column(String(15), default="pz")

    # Valorizzazione a costo medio ponderato (magazzino reale) — aggiornata ad ogni movimento.
    giacenza_attuale: Mapped[Decimal] = mapped_column(Numeric(15, 3), default=Decimal("0.000"))
    costo_medio_ponderato: Mapped[Decimal] = mapped_column(Numeric(15, 4), default=Decimal("0.0000"))
    scorta_minima: Mapped[Decimal] = mapped_column(Numeric(15, 3), default=Decimal("0.000"))

    # Costo standard (usato per budget e analisi degli scostamenti), indipendente dal costo medio reale.
    prezzo_standard: Mapped[Decimal] = mapped_column(Numeric(15, 4), default=Decimal("0.0000"))
    # Solo per semilavorati/prodotti finiti: manodopera diretta standard per produrre 1 unità.
    ore_manodopera_standard: Mapped[Decimal] = mapped_column(Numeric(10, 3), default=Decimal("0.000"))
    costo_orario_manodopera_standard: Mapped[Decimal] = mapped_column(Numeric(10, 2), default=Decimal("0.00"))
    # Quota di costi indiretti standard allocata per unità (da riparto o stima manuale).
    costo_indiretto_standard_unitario: Mapped[Decimal] = mapped_column(Numeric(15, 4), default=Decimal("0.0000"))

    prezzo_vendita: Mapped[Decimal | None] = mapped_column(Numeric(15, 2), nullable=True)
    attivo: Mapped[bool] = mapped_column(Boolean, default=True)
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    componenti: Mapped[list["DistintaBaseRiga"]] = relationship(
        foreign_keys="DistintaBaseRiga.prodotto_padre_id",
        back_populates="prodotto_padre",
        cascade="all, delete-orphan",
    )


class DistintaBaseRiga(Base):
    """Riga di distinta base (BOM): quantità di un componente necessaria per produrre 1 unità del prodotto padre."""

    __tablename__ = "distinta_base_righe"
    __table_args__ = (
        UniqueConstraint("prodotto_padre_id", "componente_id", name="uq_distinta_base_componente"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    prodotto_padre_id: Mapped[int] = mapped_column(ForeignKey("prodotti.id"), index=True)
    componente_id: Mapped[int] = mapped_column(ForeignKey("prodotti.id"), index=True)
    quantita: Mapped[Decimal] = mapped_column(Numeric(15, 4))
    note: Mapped[str | None] = mapped_column(Text, nullable=True)

    prodotto_padre: Mapped["Prodotto"] = relationship(
        "Prodotto", foreign_keys=[prodotto_padre_id], back_populates="componenti"
    )
    componente: Mapped["Prodotto"] = relationship("Prodotto", foreign_keys=[componente_id])


class MovimentoMagazzino(Base):
    """Movimento di magazzino industriale (carico/scarico) con valorizzazione a costo medio ponderato."""

    __tablename__ = "movimenti_magazzino"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    prodotto_id: Mapped[int] = mapped_column(ForeignKey("prodotti.id"), index=True)
    data: Mapped[date] = mapped_column(Date, index=True)
    tipo: Mapped[str] = mapped_column(String(10), index=True)  # carico | scarico
    quantita: Mapped[Decimal] = mapped_column(Numeric(15, 3))
    # Per i carichi: costo unitario di acquisto/produzione. Per gli scarichi: valorizzato al costo medio corrente.
    costo_unitario: Mapped[Decimal] = mapped_column(Numeric(15, 4))
    causale: Mapped[str] = mapped_column(String(300))
    commessa_id: Mapped[int | None] = mapped_column(ForeignKey("commesse.id"), nullable=True, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    prodotto: Mapped["Prodotto"] = relationship("Prodotto")
