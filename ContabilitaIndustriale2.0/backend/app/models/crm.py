from __future__ import annotations

from decimal import Decimal
from datetime import date, datetime
from sqlalchemy import Numeric, String, Date, DateTime, ForeignKey, Text, Integer
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class StoricoPagamento(Base):
    """Registro storico dei pagamenti ricevuti/effettuati per un'anagrafica."""

    __tablename__ = "storico_pagamenti"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    anagrafica_id: Mapped[int] = mapped_column(ForeignKey("anagrafiche.id"), index=True)
    documento_id: Mapped[int | None] = mapped_column(
        ForeignKey("documenti.id"), nullable=True, index=True
    )
    data_pagamento: Mapped[date] = mapped_column(Date, index=True)
    importo: Mapped[Decimal] = mapped_column(Numeric(15, 2))
    metodo_pagamento: Mapped[str] = mapped_column(String(50), default="bonifico")
    # bonifico | contanti | assegno | carta | rid | altro
    giorni_ritardo: Mapped[int] = mapped_column(Integer, default=0)
    # 0 = puntuale, >0 = ritardo in giorni, <0 = anticipato
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    anagrafica: Mapped["Anagrafica"] = relationship("Anagrafica")  # type: ignore[name-defined]


class Scadenza(Base):
    """Scadenza finanziaria (incasso atteso, pagamento dovuto, altro)."""

    __tablename__ = "scadenze_crm"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    anagrafica_id: Mapped[int | None] = mapped_column(
        ForeignKey("anagrafiche.id"), nullable=True, index=True
    )
    documento_id: Mapped[int | None] = mapped_column(
        ForeignKey("documenti.id"), nullable=True, index=True
    )
    titolo: Mapped[str] = mapped_column(String(300))
    descrizione: Mapped[str | None] = mapped_column(Text, nullable=True)
    data_scadenza: Mapped[date] = mapped_column(Date, index=True)
    importo: Mapped[Decimal | None] = mapped_column(Numeric(15, 2), nullable=True)
    tipo: Mapped[str] = mapped_column(String(20), default="incasso")
    # incasso | pagamento | altro
    stato: Mapped[str] = mapped_column(String(20), default="aperta")
    # aperta | pagata | scaduta | annullata
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    anagrafica: Mapped["Anagrafica | None"] = relationship("Anagrafica")  # type: ignore[name-defined]


class OpportunitaPipeline(Base):
    """Opportunità commerciale nel pipeline vendite."""

    __tablename__ = "pipeline_commerciale"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    anagrafica_id: Mapped[int | None] = mapped_column(
        ForeignKey("anagrafiche.id"), nullable=True, index=True
    )
    titolo: Mapped[str] = mapped_column(String(300))
    valore_stimato: Mapped[Decimal | None] = mapped_column(Numeric(15, 2), nullable=True)
    fase: Mapped[str] = mapped_column(String(30), default="prospecting", index=True)
    # prospecting | qualifica | proposta | trattativa | chiusa_vinta | chiusa_persa
    probabilita: Mapped[int] = mapped_column(Integer, default=50)
    # percentuale 0-100
    data_chiusura_prevista: Mapped[date | None] = mapped_column(Date, nullable=True)
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    anagrafica: Mapped["Anagrafica | None"] = relationship("Anagrafica")  # type: ignore[name-defined]
