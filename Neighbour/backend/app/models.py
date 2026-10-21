"""
Modello dati (specifica §5). Messaggi privati e note sono cifrati a riposo;
le posizioni sono solo celle geohash approssimate (mai indirizzi).

© Roberto Di Flumeri
"""

import uuid
from datetime import datetime, timezone

from sqlalchemy import JSON, Boolean, DateTime, Float, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.crypto import Cifrato, CifratoJSON
from app.database import Base


def adesso() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def nuovo_id() -> str:
    return uuid.uuid4().hex


def _fk_utente(nullable: bool = False, ondelete: str = "CASCADE"):
    return mapped_column(ForeignKey("users.id", ondelete=ondelete), index=True, nullable=nullable)


# ── Utenti, sessioni, consensi ───────────────────────────────────────────────


class User(Base):
    __tablename__ = "users"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    email_hash: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(100))
    alias: Mapped[str] = mapped_column(String(40))  # nome visibile ai vicini
    bio: Mapped[str | None] = mapped_column(String(300), nullable=True)
    tipo_profilo: Mapped[str] = mapped_column(String(20), default="privato")  # privato | professionale
    attivita: Mapped[str | None] = mapped_column(String(80), nullable=True)  # nome dell'attività (profilo professionale)
    stato: Mapped[str] = mapped_column(String(20), default="attivo")  # attivo | sospeso | bloccato
    sospeso_fino: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    ruolo: Mapped[str] = mapped_column(String(20), default="utente")  # utente | moderatore
    accetta_messaggi: Mapped[str] = mapped_column(String(20), default="contesto")  # contesto | nessuno
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)

    area: Mapped["Area | None"] = relationship(back_populates="utente", uselist=False, cascade="all, delete-orphan",
                                               passive_deletes=True)


class RefreshToken(Base):
    __tablename__ = "refresh_tokens"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    user_id: Mapped[str] = _fk_utente()
    token_hash: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    famiglia: Mapped[str] = mapped_column(String(32), index=True)
    scadenza: Mapped[datetime] = mapped_column(DateTime)
    revocato_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)


class Consent(Base):
    __tablename__ = "consents"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    user_id: Mapped[str] = _fk_utente()
    finalita: Mapped[str] = mapped_column(String(40))
    versione: Mapped[str] = mapped_column(String(30))
    concesso: Mapped[bool] = mapped_column(Boolean)
    timestamp: Mapped[datetime] = mapped_column(DateTime, default=adesso)
    revocato_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)


class Area(Base):
    """Quartiere dell'utente: solo il centro approssimato della cella (precisione 6) e il raggio scelto."""

    __tablename__ = "aree"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), unique=True)
    nome: Mapped[str] = mapped_column(String(80))  # es. "Isola, Milano"
    geohash: Mapped[str] = mapped_column(String(12), index=True)
    lat: Mapped[float] = mapped_column(Float)
    lon: Mapped[float] = mapped_column(Float)
    raggio_m: Mapped[int] = mapped_column(Integer, default=1500)  # visibilità dei propri contenuti e area di interesse
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=adesso, onupdate=adesso)

    utente: Mapped[User] = relationship(back_populates="area")


class Block(Base):
    __tablename__ = "blocchi"
    __table_args__ = (UniqueConstraint("bloccante_id", "bloccato_id"),)

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    bloccante_id: Mapped[str] = _fk_utente()
    bloccato_id: Mapped[str] = _fk_utente()
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)


# ── Contenuti di quartiere ───────────────────────────────────────────────────


class Group(Base):
    """Gruppo di interesse locale (F08)."""

    __tablename__ = "gruppi"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    creatore_id: Mapped[str | None] = _fk_utente(nullable=True, ondelete="SET NULL")
    nome: Mapped[str] = mapped_column(String(80))
    descrizione: Mapped[str] = mapped_column(Text)
    categoria: Mapped[str] = mapped_column(String(30))
    regole: Mapped[str | None] = mapped_column(Text, nullable=True)
    geohash: Mapped[str] = mapped_column(String(12), index=True)
    lat: Mapped[float] = mapped_column(Float)
    lon: Mapped[float] = mapped_column(Float)
    rimosso: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)


class GroupMember(Base):
    __tablename__ = "membri_gruppo"
    __table_args__ = (UniqueConstraint("gruppo_id", "user_id"),)

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    gruppo_id: Mapped[str] = mapped_column(ForeignKey("gruppi.id", ondelete="CASCADE"), index=True)
    user_id: Mapped[str] = _fk_utente()
    ruolo: Mapped[str] = mapped_column(String(20), default="membro")  # amministratore | membro
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)


class Post(Base):
    """Contenitore comune per annunci, richieste di aiuto, segnalazioni e messaggi nei gruppi."""

    __tablename__ = "posts"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    autore_id: Mapped[str] = _fk_utente()
    # offerta | richiesta | regalo | scambio | aiuto | segnalazione | gruppo
    tipo: Mapped[str] = mapped_column(String(20), index=True)
    categoria: Mapped[str] = mapped_column(String(30), index=True)
    titolo: Mapped[str] = mapped_column(String(120))
    descrizione: Mapped[str] = mapped_column(Text)
    # bozza | pubblicato | prenotato | chiuso | risolto | rimosso
    stato: Mapped[str] = mapped_column(String(20), default="pubblicato", index=True)
    scadenza: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    disponibilita: Mapped[str | None] = mapped_column(String(200), nullable=True)  # richieste di aiuto
    prezzo: Mapped[str | None] = mapped_column(String(40), nullable=True)  # testo libero: "gratis", "10 €", "da scambiare"
    geohash: Mapped[str] = mapped_column(String(12), index=True)
    lat: Mapped[float] = mapped_column(Float)
    lon: Mapped[float] = mapped_column(Float)
    raggio_m: Mapped[int] = mapped_column(Integer, default=1500)  # visibilità scelta dall'autore
    gruppo_id: Mapped[str | None] = mapped_column(ForeignKey("gruppi.id", ondelete="CASCADE"), nullable=True, index=True)
    prenotato_da: Mapped[str | None] = _fk_utente(nullable=True, ondelete="SET NULL")
    impronta: Mapped[str] = mapped_column(String(64), index=True)  # per i contenuti ripetuti
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso, index=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=adesso, onupdate=adesso)

    foto: Mapped[list["Photo"]] = relationship(order_by="Photo.ordine", cascade="all, delete-orphan", passive_deletes=True)


class Photo(Base):
    __tablename__ = "foto"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    proprietario_id: Mapped[str] = _fk_utente()
    post_id: Mapped[str | None] = mapped_column(ForeignKey("posts.id", ondelete="CASCADE"), nullable=True, index=True)
    file: Mapped[str] = mapped_column(String(80))
    larghezza: Mapped[int] = mapped_column(Integer)
    altezza: Mapped[int] = mapped_column(Integer)
    ordine: Mapped[int] = mapped_column(Integer, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)


class Event(Base):
    __tablename__ = "eventi"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    creatore_id: Mapped[str] = _fk_utente()
    titolo: Mapped[str] = mapped_column(String(120))
    descrizione: Mapped[str] = mapped_column(Text)
    categoria: Mapped[str] = mapped_column(String(30))
    inizio: Mapped[datetime] = mapped_column(DateTime, index=True)
    fine: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    luogo: Mapped[str] = mapped_column(String(120))  # descrizione approssimativa ("giardini di via Pepe")
    geohash: Mapped[str] = mapped_column(String(12), index=True)
    lat: Mapped[float] = mapped_column(Float)
    lon: Mapped[float] = mapped_column(Float)
    raggio_m: Mapped[int] = mapped_column(Integer, default=1500)
    capienza: Mapped[int | None] = mapped_column(Integer, nullable=True)  # None = senza limite
    modalita: Mapped[str] = mapped_column(String(20), default="libera")  # libera | su_approvazione
    stato: Mapped[str] = mapped_column(String(20), default="pubblicato")  # pubblicato | annullato | rimosso
    gruppo_id: Mapped[str | None] = mapped_column(ForeignKey("gruppi.id", ondelete="CASCADE"), nullable=True, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso, index=True)


class EventParticipation(Base):
    __tablename__ = "partecipazioni"
    __table_args__ = (UniqueConstraint("evento_id", "user_id"),)  # idempotenza della partecipazione

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    evento_id: Mapped[str] = mapped_column(ForeignKey("eventi.id", ondelete="CASCADE"), index=True)
    user_id: Mapped[str] = _fk_utente()
    stato: Mapped[str] = mapped_column(String(20))  # confermato | in_attesa | lista_attesa | rifiutato
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)


class EventUpdate(Base):
    __tablename__ = "aggiornamenti_evento"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    evento_id: Mapped[str] = mapped_column(ForeignKey("eventi.id", ondelete="CASCADE"), index=True)
    testo: Mapped[str] = mapped_column(String(500))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)


class Service(Base):
    """Scheda di un servizio locale (F07): suggerita da un vicino o gestita dal professionista."""

    __tablename__ = "servizi"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    autore_id: Mapped[str] = _fk_utente()
    nome: Mapped[str] = mapped_column(String(80))
    categoria: Mapped[str] = mapped_column(String(30), index=True)
    descrizione: Mapped[str] = mapped_column(Text)
    tipo_scheda: Mapped[str] = mapped_column(String(20))  # suggerito | professionale
    contatto: Mapped[str | None] = mapped_column(String(120), nullable=True)  # contatto pubblico dell'attività
    zona: Mapped[str] = mapped_column(String(80))
    geohash: Mapped[str] = mapped_column(String(12), index=True)
    lat: Mapped[float] = mapped_column(Float)
    lon: Mapped[float] = mapped_column(Float)
    rimosso: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)


class ServiceRecommendation(Base):
    __tablename__ = "consigli_servizio"
    __table_args__ = (UniqueConstraint("servizio_id", "user_id"),)

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    servizio_id: Mapped[str] = mapped_column(ForeignKey("servizi.id", ondelete="CASCADE"), index=True)
    user_id: Mapped[str] = _fk_utente()
    commento: Mapped[str | None] = mapped_column(String(300), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)


# ── Chat privata ─────────────────────────────────────────────────────────────


class Conversation(Base):
    __tablename__ = "conversazioni"
    __table_args__ = (UniqueConstraint("contesto_tipo", "contesto_id", "richiedente_id", "destinatario_id"),)

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    contesto_tipo: Mapped[str] = mapped_column(String(20))  # post | evento | servizio
    contesto_id: Mapped[str] = mapped_column(String(32))
    contesto_titolo: Mapped[str] = mapped_column(String(120))
    richiedente_id: Mapped[str | None] = _fk_utente(nullable=True, ondelete="SET NULL")
    destinatario_id: Mapped[str | None] = _fk_utente(nullable=True, ondelete="SET NULL")
    stato: Mapped[str] = mapped_column(String(20), default="attiva")  # attiva | chiusa
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=adesso, index=True)

    messaggi: Mapped[list["Message"]] = relationship(order_by="Message.created_at", cascade="all, delete-orphan",
                                                     passive_deletes=True)


class Message(Base):
    __tablename__ = "messaggi"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    conversazione_id: Mapped[str] = mapped_column(ForeignKey("conversazioni.id", ondelete="CASCADE"), index=True)
    autore_id: Mapped[str | None] = _fk_utente(nullable=True, ondelete="SET NULL")
    testo: Mapped[str] = mapped_column(Cifrato)
    letto: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)


# ── Fiducia e moderazione ────────────────────────────────────────────────────


class Reputation(Base):
    """Feedback contestuale (F09): ammesso solo dopo un'interazione verificata e conclusa."""

    __tablename__ = "reputazione"
    __table_args__ = (UniqueConstraint("autore_id", "destinatario_id", "contesto_tipo", "contesto_id"),)

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    autore_id: Mapped[str | None] = _fk_utente(nullable=True, ondelete="SET NULL")
    destinatario_id: Mapped[str] = _fk_utente()
    contesto_tipo: Mapped[str] = mapped_column(String(20))  # post | evento
    contesto_id: Mapped[str] = mapped_column(String(32))
    contesto_titolo: Mapped[str] = mapped_column(String(120))
    esito: Mapped[str] = mapped_column(String(20))  # positivo | neutro | negativo
    commento: Mapped[str | None] = mapped_column(String(300), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)


class Report(Base):
    __tablename__ = "segnalazioni"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    segnalante_id: Mapped[str | None] = _fk_utente(nullable=True, ondelete="SET NULL")
    oggetto_tipo: Mapped[str] = mapped_column(String(20))  # post | evento | servizio | gruppo | messaggio | utente
    oggetto_id: Mapped[str] = mapped_column(String(32))
    utente_segnalato_id: Mapped[str | None] = _fk_utente(nullable=True, ondelete="SET NULL")
    motivo: Mapped[str] = mapped_column(String(30))
    dettagli: Mapped[str | None] = mapped_column(String(1000), nullable=True)
    evidenze: Mapped[dict | None] = mapped_column(CifratoJSON, nullable=True)  # copia cifrata del contenuto segnalato
    stato: Mapped[str] = mapped_column(String(20), default="aperta")  # aperta | risolta | archiviata
    decisione: Mapped[str | None] = mapped_column(String(30), nullable=True)
    nota_decisione: Mapped[str | None] = mapped_column(String(500), nullable=True)
    moderatore_id: Mapped[str | None] = _fk_utente(nullable=True, ondelete="SET NULL")
    ricorso: Mapped[str | None] = mapped_column(String(1000), nullable=True)
    ricorso_stato: Mapped[str | None] = mapped_column(String(20), nullable=True)  # aperto | accolto | respinto
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso, index=True)
    risolta_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)


# ── Notifiche ────────────────────────────────────────────────────────────────


class NotificationPrefs(Base):
    """Avvisi configurabili (F10): area, categorie, frequenza e ore di silenzio."""

    __tablename__ = "preferenze_notifiche"

    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    tipi: Mapped[list] = mapped_column(JSON, default=list)  # tipi di contenuto da seguire
    categorie: Mapped[list] = mapped_column(JSON, default=list)  # vuoto = tutte
    raggio_m: Mapped[int] = mapped_column(Integer, default=1500)  # come il raggio predefinito dell'area
    frequenza: Mapped[str] = mapped_column(String(20), default="immediata")  # immediata | giornaliera | disattivata
    quiet_inizio: Mapped[int | None] = mapped_column(Integer, nullable=True, default=22)  # ora 0-23
    quiet_fine: Mapped[int | None] = mapped_column(Integer, nullable=True, default=8)
    ultimo_riepilogo: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    versione_consenso: Mapped[str | None] = mapped_column(String(30), nullable=True)


class Notification(Base):
    __tablename__ = "notifiche"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    user_id: Mapped[str] = _fk_utente()
    tipo: Mapped[str] = mapped_column(String(30))  # vicinato | messaggio | evento | moderazione | reputazione | riepilogo
    titolo: Mapped[str] = mapped_column(String(160))
    testo: Mapped[str] = mapped_column(String(500))
    link: Mapped[str | None] = mapped_column(String(200), nullable=True)
    # le notifiche di quartiere in ore di silenzio o in modalità riepilogo restano in attesa
    stato: Mapped[str] = mapped_column(String(20), default="consegnata")  # consegnata | in_attesa | raggruppata
    visibile_da: Mapped[datetime] = mapped_column(DateTime, default=adesso, index=True)
    letta: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=adesso)


# ── Sistema ──────────────────────────────────────────────────────────────────


class AuditLog(Base):
    __tablename__ = "audit"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    attore_id: Mapped[str | None] = mapped_column(String(32), nullable=True, index=True)
    azione: Mapped[str] = mapped_column(String(60))
    oggetto: Mapped[str | None] = mapped_column(String(64), nullable=True)
    dettagli: Mapped[str | None] = mapped_column(String(500), nullable=True)
    timestamp: Mapped[datetime] = mapped_column(DateTime, default=adesso, index=True)


class AnalyticsEvent(Base):
    """Statistiche d'uso pseudonime, registrate solo con il consenso analytics."""

    __tablename__ = "analytics"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    utente_pseudo: Mapped[str] = mapped_column(String(64), index=True)
    evento: Mapped[str] = mapped_column(String(40))
    timestamp: Mapped[datetime] = mapped_column(DateTime, default=adesso)


class DeletionReceipt(Base):
    __tablename__ = "ricevute_cancellazione"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=nuovo_id)
    impronta: Mapped[str] = mapped_column(String(64))
    timestamp: Mapped[datetime] = mapped_column(DateTime, default=adesso)
