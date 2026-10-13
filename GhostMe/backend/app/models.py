"""
Modello dati (specifica §5). I contenuti personali sono cifrati a riposo.

© Roberto Di Flumeri
"""

import uuid
from datetime import date, datetime, timezone

from sqlalchemy import Boolean, Date, DateTime, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.crypto import Cifrato, CifratoJSON
from app.database import Base


def adesso() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def nuovo_id() -> str:
    return uuid.uuid4().hex


class User(Base):
    __tablename__ = "users"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    email_hash: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(100))
    nome_visualizzato: Mapped[str | None] = mapped_column(Cifrato, nullable=True)
    stato: Mapped[str] = mapped_column(String(20), default="attivo")  # attivo | bloccato
    ruolo: Mapped[str] = mapped_column(String(20), default="utente")  # utente | moderatore
    locale: Mapped[str] = mapped_column(String(10), default="it")
    motore_ai: Mapped[str] = mapped_column(String(10), default="locale")  # locale | ollama | auto
    conservazione_chat_giorni: Mapped[int | None] = mapped_column(Integer, nullable=True)  # None = illimitata
    onboarding_completato: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)


class RefreshToken(Base):
    __tablename__ = "refresh_tokens"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    famiglia: Mapped[str] = mapped_column(String(32), index=True)  # catena di rotazione
    scadenza: Mapped[datetime] = mapped_column(DateTime)
    revocato_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)


class Consent(Base):
    __tablename__ = "consents"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    finalita: Mapped[str] = mapped_column(String(40))
    versione: Mapped[str] = mapped_column(String(30))
    concesso: Mapped[bool] = mapped_column(Boolean)
    timestamp: Mapped[datetime] = mapped_column(DateTime, default=adesso)
    revocato_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)


class TraitStatement(Base):
    """Dichiarazione personale: non è una diagnosi, è sempre modificabile."""

    __tablename__ = "trait_statements"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    testo: Mapped[str] = mapped_column(Cifrato)
    categoria: Mapped[str] = mapped_column(String(30), index=True)
    peso: Mapped[int] = mapped_column(Integer, default=3)  # importanza dichiarata 1-5
    fonte: Mapped[str] = mapped_column(String(20), default="manuale")
    chiave_domanda: Mapped[str | None] = mapped_column(String(60), nullable=True)
    valido_da: Mapped[date | None] = mapped_column(Date, nullable=True)
    valido_a: Mapped[date | None] = mapped_column(Date, nullable=True)
    visibilita: Mapped[str] = mapped_column(String(20), default="privata")  # privata | condivisibile
    usa_in_ai: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)


class TraitChange(Base):
    """Storico delle modifiche alle dichiarazioni (diario di evoluzione e timeline)."""

    __tablename__ = "trait_changes"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    statement_id: Mapped[str] = mapped_column(String(32), index=True)
    azione: Mapped[str] = mapped_column(String(20))  # creata | modificata | eliminata
    categoria: Mapped[str] = mapped_column(String(30))
    prima: Mapped[dict | None] = mapped_column(CifratoJSON, nullable=True)
    dopo: Mapped[dict | None] = mapped_column(CifratoJSON, nullable=True)
    timestamp: Mapped[datetime] = mapped_column(DateTime, default=adesso, index=True)


class KnowledgeItem(Base):
    __tablename__ = "knowledge_items"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    titolo: Mapped[str] = mapped_column(Cifrato)
    contenuto: Mapped[str] = mapped_column(Cifrato)
    tipo: Mapped[str] = mapped_column(String(20), default="nota")  # nota | testo | ricordo | link
    categoria: Mapped[str] = mapped_column(String(30), default="altro")
    usa_in_ai: Mapped[bool] = mapped_column(Boolean, default=True)
    visibilita: Mapped[str] = mapped_column(String(20), default="privata")
    # riferimento all'indice di ricerca (indice lessicale locale; predisposto per embedding futuri)
    indice_ref: Mapped[str | None] = mapped_column(String(64), nullable=True)
    sospetta_injection: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)


class ProfileSnapshot(Base):
    """Versione immutabile del profilo (reale o simulazione etichettata)."""

    __tablename__ = "profile_snapshots"
    __table_args__ = (UniqueConstraint("user_id", "versione"),)

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    versione: Mapped[int] = mapped_column(Integer)
    tipo: Mapped[str] = mapped_column(String(30), default="reale")  # reale | simulazione_futura
    etichetta: Mapped[str] = mapped_column(Cifrato)
    data_riferimento: Mapped[date] = mapped_column(Date)
    riepilogo: Mapped[str] = mapped_column(Cifrato)
    dati: Mapped[list] = mapped_column(CifratoJSON)
    impronta: Mapped[str] = mapped_column(String(64))  # SHA-256 dei dati: verifica integrità
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)


class Conversation(Base):
    __tablename__ = "conversations"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    titolo: Mapped[str] = mapped_column(Cifrato)
    snapshot_id: Mapped[str | None] = mapped_column(
        ForeignKey("profile_snapshots.id", ondelete="SET NULL"), nullable=True
    )
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=adesso, index=True)

    messaggi: Mapped[list["Message"]] = relationship(
        back_populates="conversazione", cascade="all, delete-orphan", order_by="Message.created_at"
    )


class Message(Base):
    __tablename__ = "messages"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    conversation_id: Mapped[str] = mapped_column(ForeignKey("conversations.id", ondelete="CASCADE"), index=True)
    ruolo: Mapped[str] = mapped_column(String(20))  # utente | alter_ego
    contenuto: Mapped[str] = mapped_column(Cifrato)
    fonti: Mapped[list | None] = mapped_column(CifratoJSON, nullable=True)
    confidenza: Mapped[str | None] = mapped_column(String(10), nullable=True)  # bassa | media | alta
    motore: Mapped[str | None] = mapped_column(String(20), nullable=True)
    modello: Mapped[str | None] = mapped_column(String(60), nullable=True)
    prompt_version: Mapped[str | None] = mapped_column(String(40), nullable=True)
    policy_version: Mapped[str | None] = mapped_column(String(40), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)

    conversazione: Mapped[Conversation] = relationship(back_populates="messaggi")


class DecisionSession(Base):
    __tablename__ = "decision_sessions"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    domanda: Mapped[str] = mapped_column(Cifrato)
    opzioni: Mapped[list] = mapped_column(CifratoJSON)
    criteri: Mapped[list] = mapped_column(CifratoJSON)
    valutazioni: Mapped[dict] = mapped_column(CifratoJSON)
    risultato: Mapped[dict] = mapped_column(CifratoJSON)
    stato: Mapped[str] = mapped_column(String(20), default="analizzata")  # analizzata | decisa | valutata
    scelta_finale: Mapped[str | None] = mapped_column(Cifrato, nullable=True)
    esito: Mapped[str | None] = mapped_column(String(20), nullable=True)  # positivo | neutro | negativo
    apprendimento: Mapped[str | None] = mapped_column(Cifrato, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)


class JournalEntry(Base):
    __tablename__ = "journal_entries"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    tipo: Mapped[str] = mapped_column(String(20), default="nota")  # nota | check_in
    testo: Mapped[str] = mapped_column(Cifrato)
    energia: Mapped[int | None] = mapped_column(Integer, nullable=True)  # 1-5, facoltativo, non clinico
    etichette: Mapped[list | None] = mapped_column(CifratoJSON, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso, index=True)


class ChallengeResponse(Base):
    __tablename__ = "challenge_responses"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    sfida_id: Mapped[str] = mapped_column(String(40))
    risposta: Mapped[str] = mapped_column(Cifrato)
    statement_id: Mapped[str | None] = mapped_column(String(32), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)


class ShareGrant(Base):
    __tablename__ = "share_grants"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    nome: Mapped[str] = mapped_column(Cifrato)
    categorie: Mapped[list] = mapped_column(CifratoJSON)
    max_domande: Mapped[int] = mapped_column(Integer, default=10)
    domande_usate: Mapped[int] = mapped_column(Integer, default=0)
    scadenza: Mapped[datetime] = mapped_column(DateTime)
    revocato_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    bloccato_moderazione: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)


class ShareAccess(Base):
    """Registro accessi al link condiviso (senza contenuti, IP solo come hash)."""

    __tablename__ = "share_accesses"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    share_id: Mapped[str] = mapped_column(ForeignKey("share_grants.id", ondelete="CASCADE"), index=True)
    evento: Mapped[str] = mapped_column(String(20))  # apertura | domanda | rifiutata
    ip_hash: Mapped[str | None] = mapped_column(String(64), nullable=True)
    timestamp: Mapped[datetime] = mapped_column(DateTime, default=adesso)


class CompatibilityInvite(Base):
    __tablename__ = "compatibility_invites"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    proprietario_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    codice_hash: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    categorie: Mapped[list] = mapped_column(CifratoJSON)
    scadenza: Mapped[datetime] = mapped_column(DateTime)
    accettato_da: Mapped[str | None] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), nullable=True)
    risultato: Mapped[dict | None] = mapped_column(CifratoJSON, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)


class Report(Base):
    """Segnalazione di abuso (es. da un utente invitato su un avatar condiviso)."""

    __tablename__ = "reports"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    share_id: Mapped[str | None] = mapped_column(ForeignKey("share_grants.id", ondelete="SET NULL"), nullable=True)
    utente_segnalato_id: Mapped[str | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    motivo: Mapped[str] = mapped_column(String(40))
    dettagli: Mapped[str | None] = mapped_column(Cifrato, nullable=True)
    stato: Mapped[str] = mapped_column(String(20), default="aperta")  # aperta | risolta
    azione: Mapped[str | None] = mapped_column(String(30), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)
    risolta_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)


class AuditLog(Base):
    """Audit delle azioni rilevanti (senza contenuti personali)."""

    __tablename__ = "audit_log"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    attore_id: Mapped[str | None] = mapped_column(String(32), nullable=True, index=True)
    azione: Mapped[str] = mapped_column(String(60))
    oggetto: Mapped[str | None] = mapped_column(String(80), nullable=True)
    dettagli: Mapped[str | None] = mapped_column(Text, nullable=True)
    timestamp: Mapped[datetime] = mapped_column(DateTime, default=adesso, index=True)


class AnalyticsEvent(Base):
    """Eventi d'uso registrati solo con consenso analytics (nessun contenuto)."""

    __tablename__ = "analytics_events"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    utente_pseudo: Mapped[str] = mapped_column(String(64), index=True)
    evento: Mapped[str] = mapped_column(String(40), index=True)
    timestamp: Mapped[datetime] = mapped_column(DateTime, default=adesso, index=True)


class DeletionReceipt(Base):
    """Ricevuta di cancellazione account: conservata senza dati personali."""

    __tablename__ = "deletion_receipts"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    impronta: Mapped[str] = mapped_column(String(64))
    timestamp: Mapped[datetime] = mapped_column(DateTime, default=adesso)
