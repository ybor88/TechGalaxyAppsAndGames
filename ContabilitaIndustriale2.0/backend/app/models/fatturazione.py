from decimal import Decimal
from datetime import date, datetime
from sqlalchemy import Numeric, String, Date, DateTime, ForeignKey, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class Anagrafica(Base):
    """Clienti e fornitori."""

    __tablename__ = "anagrafiche"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    nome: Mapped[str] = mapped_column(String(200), index=True)
    tipo: Mapped[str] = mapped_column(String(20))  # cliente | fornitore | entrambi
    piva: Mapped[str | None] = mapped_column(String(20), nullable=True)
    cf: Mapped[str | None] = mapped_column(String(20), nullable=True)
    indirizzo: Mapped[str | None] = mapped_column(String(300), nullable=True)
    cap: Mapped[str | None] = mapped_column(String(10), nullable=True)
    citta: Mapped[str | None] = mapped_column(String(100), nullable=True)
    provincia: Mapped[str | None] = mapped_column(String(5), nullable=True)
    paese: Mapped[str] = mapped_column(String(50), default="Italia")
    email: Mapped[str | None] = mapped_column(String(200), nullable=True)
    telefono: Mapped[str | None] = mapped_column(String(50), nullable=True)
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    documenti: Mapped[list["Documento"]] = relationship(back_populates="anagrafica")


class Documento(Base):
    """Documento amministrativo: preventivo, ordine, fattura attiva/passiva, nota credito."""

    __tablename__ = "documenti"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    tipo: Mapped[str] = mapped_column(String(30), index=True)
    # preventivo | ordine | fattura_attiva | fattura_passiva | nota_credito
    numero: Mapped[str] = mapped_column(String(50), index=True)
    data: Mapped[date] = mapped_column(Date, index=True)
    data_scadenza: Mapped[date | None] = mapped_column(Date, nullable=True)
    anagrafica_id: Mapped[int | None] = mapped_column(
        ForeignKey("anagrafiche.id"), nullable=True
    )
    stato: Mapped[str] = mapped_column(String(20), default="bozza")
    # bozza | emesso | pagato | annullato
    oggetto: Mapped[str | None] = mapped_column(String(500), nullable=True)
    subtotale: Mapped[Decimal] = mapped_column(Numeric(15, 2), default=Decimal("0.00"))
    totale_iva: Mapped[Decimal] = mapped_column(Numeric(15, 2), default=Decimal("0.00"))
    totale: Mapped[Decimal] = mapped_column(Numeric(15, 2), default=Decimal("0.00"))
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    anagrafica: Mapped["Anagrafica | None"] = relationship(back_populates="documenti")
    righe: Mapped[list["RigaDocumento"]] = relationship(
        back_populates="documento",
        cascade="all, delete-orphan",
        order_by="RigaDocumento.id",
    )


class RigaDocumento(Base):
    """Singola riga di dettaglio di un documento."""

    __tablename__ = "righe_documento"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    documento_id: Mapped[int] = mapped_column(ForeignKey("documenti.id"), index=True)
    descrizione: Mapped[str] = mapped_column(String(500))
    quantita: Mapped[Decimal] = mapped_column(Numeric(15, 4), default=Decimal("1.0000"))
    prezzo_unitario: Mapped[Decimal] = mapped_column(
        Numeric(15, 2), default=Decimal("0.00")
    )
    iva_percentuale: Mapped[Decimal] = mapped_column(
        Numeric(5, 2), default=Decimal("22.00")
    )
    importo: Mapped[Decimal] = mapped_column(Numeric(15, 2), default=Decimal("0.00"))
    # importo = quantita * prezzo_unitario (al netto IVA)

    documento: Mapped["Documento"] = relationship(back_populates="righe")
