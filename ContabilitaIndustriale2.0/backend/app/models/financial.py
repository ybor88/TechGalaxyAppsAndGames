from decimal import Decimal
from datetime import date, datetime
from sqlalchemy import Numeric, String, Date, DateTime, ForeignKey, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship
import enum

from app.database import Base


class TipoMovimento(str, enum.Enum):
    ENTRATA = "entrata"
    USCITA = "uscita"


class Conto(Base):
    """Piano dei conti."""
    __tablename__ = "conti"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    codice: Mapped[str] = mapped_column(String(20), unique=True, index=True)
    descrizione: Mapped[str] = mapped_column(String(200))
    tipo: Mapped[str] = mapped_column(String(50))  # attivo, passivo, costo, ricavo
    saldo: Mapped[Decimal] = mapped_column(Numeric(15, 2), default=Decimal("0.00"))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    movimenti: Mapped[list["Movimento"]] = relationship(back_populates="conto")


class Movimento(Base):
    """Singolo movimento finanziario (entrata o uscita)."""
    __tablename__ = "movimenti"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    data: Mapped[date] = mapped_column(Date, index=True)
    # Stored as String per compatibilità SQLite
    tipo: Mapped[str] = mapped_column(String(10), index=True)
    importo: Mapped[Decimal] = mapped_column(Numeric(15, 2))
    descrizione: Mapped[str] = mapped_column(String(500))
    categoria: Mapped[str] = mapped_column(String(100), nullable=True)
    conto_id: Mapped[int] = mapped_column(ForeignKey("conti.id"), nullable=True)
    note: Mapped[str] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    conto: Mapped["Conto"] = relationship(back_populates="movimenti")
