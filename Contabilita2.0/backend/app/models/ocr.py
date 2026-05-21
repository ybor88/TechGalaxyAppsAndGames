from decimal import Decimal
from datetime import date, datetime

from sqlalchemy import Numeric, String, Date, DateTime, ForeignKey, Text, Integer
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class OcrRisultato(Base):
    """Risultato di un'elaborazione OCR su fattura caricata."""

    __tablename__ = "ocr_risultati"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    filename: Mapped[str] = mapped_column(String(300))
    content_type: Mapped[str] = mapped_column(String(100))

    # Testo grezzo estratto
    testo_estratto: Mapped[str | None] = mapped_column(Text, nullable=True)

    # Dati estratti dal parser
    fornitore: Mapped[str | None] = mapped_column(String(300), nullable=True)
    piva: Mapped[str | None] = mapped_column(String(20), nullable=True)
    cf: Mapped[str | None] = mapped_column(String(20), nullable=True)
    numero_documento: Mapped[str | None] = mapped_column(String(100), nullable=True)
    data_documento: Mapped[date | None] = mapped_column(Date, nullable=True)
    importo_netto: Mapped[Decimal | None] = mapped_column(Numeric(15, 2), nullable=True)
    importo_iva: Mapped[Decimal | None] = mapped_column(Numeric(15, 2), nullable=True)
    importo_totale: Mapped[Decimal | None] = mapped_column(Numeric(15, 2), nullable=True)
    aliquota_iva: Mapped[Decimal | None] = mapped_column(Numeric(5, 2), nullable=True)

    # Stato elaborazione: elaborato | errore | revisione
    stato: Mapped[str] = mapped_column(String(20), default="elaborato")
    errore: Mapped[str | None] = mapped_column(Text, nullable=True)

    # Collegamento opzionale a un documento creato da questo OCR
    documento_id: Mapped[int | None] = mapped_column(
        Integer, ForeignKey("documenti.id"), nullable=True
    )

    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
