"""Schemi di input con validazione (messaggi in italiano via app.errori). © Roberto Di Flumeri"""

from datetime import datetime, timezone
from typing import Literal

from pydantic import BaseModel, EmailStr, Field, field_validator, model_validator

from app.services.catalogo import (
    CATEGORIE_EVENTO, CATEGORIE_GRUPPO, CATEGORIE_SERVIZIO, DECISIONI_MODERAZIONE, MOTIVI_SEGNALAZIONE,
    TIPI_NOTIFICA_QUARTIERE, categorie_per_tipo,
)

Testo = lambda mn, mx: Field(min_length=mn, max_length=mx)  # noqa: E731


def _password_robusta(v: str) -> str:
    if not any(c.isalpha() for c in v) or not any(c.isdigit() for c in v):
        raise ValueError("La password deve contenere lettere e numeri")
    return v


def _pulisci(v: str | None) -> str | None:
    return v.strip() if isinstance(v, str) else v


def _utc(v: datetime | None) -> datetime | None:
    """Le date arrivano dal browser con il fuso: il database lavora in UTC senza fuso."""
    if isinstance(v, datetime) and v.tzinfo is not None:
        return v.astimezone(timezone.utc).replace(tzinfo=None)
    return v


# ── Account ──────────────────────────────────────────────────────────────────


class ConsensiIn(BaseModel):
    trattamento_dati: bool
    geolocalizzazione: bool = False
    notifiche: bool = False
    profilazione: bool = False
    analytics: bool = False


class RegistrazioneIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=10, max_length=128)
    alias: str = Testo(2, 40)
    accetto_termini: bool
    consensi: ConsensiIn
    tipo_profilo: Literal["privato", "professionale"] = "privato"
    attivita: str | None = Field(default=None, max_length=80)

    _pwd = field_validator("password")(_password_robusta)
    _alias = field_validator("alias", "attivita")(_pulisci)

    @model_validator(mode="after")
    def _obbligatori(self):
        if not self.accetto_termini:
            raise ValueError("Per registrarti devi accettare termini d'uso e linee guida")
        if not self.consensi.trattamento_dati:
            raise ValueError("Il consenso al trattamento dei dati è necessario per usare l'app")
        if self.tipo_profilo == "professionale" and not self.attivita:
            raise ValueError("Per un profilo professionale indica il nome dell'attività")
        return self


class LoginIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=1, max_length=128)


class CambioPasswordIn(BaseModel):
    password_attuale: str = Field(min_length=1, max_length=128)
    nuova_password: str = Field(min_length=10, max_length=128)
    _pwd = field_validator("nuova_password")(_password_robusta)


class CancellazioneIn(BaseModel):
    password: str = Field(min_length=1, max_length=128)
    conferma: str

    @field_validator("conferma")
    @classmethod
    def _conferma(cls, v: str) -> str:
        if v != "ELIMINA":
            raise ValueError('Per confermare scrivi ELIMINA')
        return v


class ProfiloUpdate(BaseModel):
    alias: str | None = Field(default=None, min_length=2, max_length=40)
    bio: str | None = Field(default=None, max_length=300)
    tipo_profilo: Literal["privato", "professionale"] | None = None
    attivita: str | None = Field(default=None, max_length=80)
    _p = field_validator("alias", "bio", "attivita")(_pulisci)


class AreaIn(BaseModel):
    nome: str = Testo(2, 80)
    lat: float = Field(ge=-90, le=90)
    lon: float = Field(ge=-180, le=180)
    raggio_m: int = Field(default=1500, ge=300, le=5000)
    da_gps: bool = False  # posizione presa dal dispositivo: richiede il consenso alla geolocalizzazione
    _p = field_validator("nome")(_pulisci)


class PreferenzeNotificheIn(BaseModel):
    tipi: list[str] | None = None
    categorie: list[str] | None = None
    raggio_m: int | None = Field(default=None, ge=300, le=5000)
    frequenza: Literal["immediata", "giornaliera", "disattivata"] | None = None
    quiet_inizio: int | None = Field(default=None, ge=0, le=23)
    quiet_fine: int | None = Field(default=None, ge=0, le=23)
    senza_silenzio: bool = False

    @field_validator("tipi")
    @classmethod
    def _tipi(cls, v):
        if v is not None and any(t not in TIPI_NOTIFICA_QUARTIERE for t in v):
            raise ValueError("Tipo di contenuto non valido")
        return v


class PreferenzeIn(BaseModel):
    """PATCH /v1/preferences: notifiche e privacy in un'unica operazione, con versionamento del consenso."""

    notifiche: PreferenzeNotificheIn | None = None
    consensi: dict[str, bool] | None = None
    accetta_messaggi: Literal["contesto", "nessuno"] | None = None


# ── Contenuti ────────────────────────────────────────────────────────────────


class PostIn(BaseModel):
    tipo: Literal["offerta", "richiesta", "regalo", "scambio", "aiuto", "segnalazione", "gruppo"]
    categoria: str = Field(min_length=2, max_length=30)
    titolo: str = Testo(3, 120)
    descrizione: str = Testo(3, 2000)
    scadenza: datetime | None = None
    disponibilita: str | None = Field(default=None, max_length=200)
    prezzo: str | None = Field(default=None, max_length=40)
    lat: float | None = Field(default=None, ge=-90, le=90)
    lon: float | None = Field(default=None, ge=-180, le=180)
    raggio_m: int | None = Field(default=None, ge=300, le=5000)
    bozza: bool = False
    foto_ids: list[str] = Field(default_factory=list, max_length=4)
    gruppo_id: str | None = None

    _p = field_validator("titolo", "descrizione", "disponibilita", "prezzo")(_pulisci)
    _d = field_validator("scadenza")(_utc)

    @model_validator(mode="after")
    def _coerenza(self):
        if self.tipo == "gruppo":
            if not self.gruppo_id:
                raise ValueError("Un messaggio di gruppo richiede il gruppo")
            if self.categoria not in CATEGORIE_GRUPPO:
                self.categoria = "altro"
        elif self.categoria not in categorie_per_tipo(self.tipo):
            raise ValueError("Categoria non valida per questo tipo di contenuto")
        if self.tipo == "aiuto":
            if not self.scadenza:
                raise ValueError("Per una richiesta di aiuto indica entro quando ti serve")
            if not self.disponibilita:
                raise ValueError("Per una richiesta di aiuto indica quando sei disponibile")
        if (self.lat is None) != (self.lon is None):
            raise ValueError("Posizione incompleta")
        return self


class PostUpdate(BaseModel):
    titolo: str | None = Field(default=None, min_length=3, max_length=120)
    descrizione: str | None = Field(default=None, min_length=3, max_length=2000)
    categoria: str | None = Field(default=None, min_length=2, max_length=30)
    scadenza: datetime | None = None
    disponibilita: str | None = Field(default=None, max_length=200)
    prezzo: str | None = Field(default=None, max_length=40)
    foto_ids: list[str] | None = Field(default=None, max_length=4)
    _p = field_validator("titolo", "descrizione", "disponibilita", "prezzo")(_pulisci)
    _d = field_validator("scadenza")(_utc)


class StatoPostIn(BaseModel):
    stato: Literal["pubblicato", "prenotato", "chiuso", "risolto"]
    prenotato_da: str | None = None  # id dell'utente con cui si è concordato (dalla chat)


class EventoIn(BaseModel):
    titolo: str = Testo(3, 120)
    descrizione: str = Testo(3, 2000)
    categoria: str
    inizio: datetime
    fine: datetime | None = None
    luogo: str = Testo(3, 120)
    lat: float | None = Field(default=None, ge=-90, le=90)
    lon: float | None = Field(default=None, ge=-180, le=180)
    raggio_m: int | None = Field(default=None, ge=300, le=5000)
    capienza: int | None = Field(default=None, ge=1, le=500)
    modalita: Literal["libera", "su_approvazione"] = "libera"
    gruppo_id: str | None = None
    _p = field_validator("titolo", "descrizione", "luogo")(_pulisci)
    _d = field_validator("inizio", "fine")(_utc)

    @model_validator(mode="after")
    def _coerenza(self):
        if self.categoria not in CATEGORIE_EVENTO:
            raise ValueError("Categoria non valida per un evento")
        if self.fine and self.fine <= self.inizio:
            raise ValueError("La fine dell'evento deve essere successiva all'inizio")
        if (self.lat is None) != (self.lon is None):
            raise ValueError("Posizione incompleta")
        return self


class EventoUpdate(BaseModel):
    titolo: str | None = Field(default=None, min_length=3, max_length=120)
    descrizione: str | None = Field(default=None, min_length=3, max_length=2000)
    inizio: datetime | None = None
    fine: datetime | None = None
    luogo: str | None = Field(default=None, min_length=3, max_length=120)
    capienza: int | None = Field(default=None, ge=1, le=500)
    senza_limite: bool = False
    _d = field_validator("inizio", "fine")(_utc)


class AggiornamentoIn(BaseModel):
    testo: str = Testo(2, 500)


class DecisionePartecipanteIn(BaseModel):
    stato: Literal["confermato", "rifiutato"]


class ConversazioneIn(BaseModel):
    contesto_tipo: Literal["post", "evento", "servizio"]
    contesto_id: str = Field(min_length=1, max_length=32)
    messaggio: str = Testo(1, 2000)
    _p = field_validator("messaggio")(_pulisci)


class MessaggioIn(BaseModel):
    testo: str = Testo(1, 2000)
    _p = field_validator("testo")(_pulisci)


class GruppoIn(BaseModel):
    nome: str = Testo(3, 80)
    descrizione: str = Testo(10, 1000)
    categoria: str
    regole: str | None = Field(default=None, max_length=1000)
    _p = field_validator("nome", "descrizione", "regole")(_pulisci)

    @field_validator("categoria")
    @classmethod
    def _cat(cls, v):
        if v not in CATEGORIE_GRUPPO:
            raise ValueError("Categoria non valida per un gruppo")
        return v


class ServizioIn(BaseModel):
    nome: str = Testo(2, 80)
    categoria: str
    descrizione: str = Testo(10, 1000)
    tipo_scheda: Literal["suggerito", "professionale"]
    contatto: str | None = Field(default=None, max_length=120)
    zona: str = Testo(2, 80)
    lat: float | None = Field(default=None, ge=-90, le=90)
    lon: float | None = Field(default=None, ge=-180, le=180)
    _p = field_validator("nome", "descrizione", "contatto", "zona")(_pulisci)

    @field_validator("categoria")
    @classmethod
    def _cat(cls, v):
        if v not in CATEGORIE_SERVIZIO:
            raise ValueError("Categoria non valida per un servizio")
        return v


class ConsiglioIn(BaseModel):
    commento: str | None = Field(default=None, max_length=300)


class FeedbackIn(BaseModel):
    destinatario_id: str
    contesto_tipo: Literal["post", "evento"]
    contesto_id: str
    esito: Literal["positivo", "neutro", "negativo"]
    commento: str | None = Field(default=None, max_length=300)
    _p = field_validator("commento")(_pulisci)


class ReportIn(BaseModel):
    oggetto_tipo: Literal["post", "evento", "servizio", "gruppo", "messaggio", "utente"]
    oggetto_id: str = Field(min_length=1, max_length=32)
    motivo: str
    dettagli: str | None = Field(default=None, max_length=1000)
    blocca_utente: bool = False

    @field_validator("motivo")
    @classmethod
    def _motivo(cls, v):
        if v not in MOTIVI_SEGNALAZIONE:
            raise ValueError("Motivo della segnalazione non valido")
        return v


class RicorsoIn(BaseModel):
    testo: str = Testo(10, 1000)


class DecisioneModerazioneIn(BaseModel):
    decisione: str
    nota: str | None = Field(default=None, max_length=500)

    @field_validator("decisione")
    @classmethod
    def _dec(cls, v):
        if v not in DECISIONI_MODERAZIONE:
            raise ValueError("Decisione non valida")
        return v


class EsitoRicorsoIn(BaseModel):
    esito: Literal["accolto", "respinto"]
    nota: str | None = Field(default=None, max_length=500)

