"""Schemi di validazione delle API (Pydantic). © Roberto Di Flumeri"""

from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator, model_validator

Categoria = Literal["valori", "abitudini", "gusti", "obiettivi", "stile_comunicativo", "altro"]
Visibilita = Literal["privata", "condivisibile"]
Motore = Literal["locale", "ollama", "auto"]


class Base(BaseModel):
    model_config = ConfigDict(from_attributes=True)


# ── Autenticazione e account ─────────────────────────────────────────────────

class ConsensiIniziali(BaseModel):
    trattamento_profilo: bool
    elaborazione_ai: bool = False
    condivisione: bool = False
    analytics: bool = False


class RegistrazioneIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=10, max_length=128)
    nome: str = Field(min_length=1, max_length=60)
    accetto_termini: bool
    consensi: ConsensiIniziali

    @model_validator(mode="after")
    def _obbligatori(self):
        if not self.accetto_termini:
            raise ValueError("Per registrarti devi accettare i termini d'uso")
        if not self.consensi.trattamento_profilo:
            raise ValueError("Il consenso al trattamento del profilo è necessario per usare l'app")
        return self

    @field_validator("password")
    @classmethod
    def _robusta(cls, v: str) -> str:
        return password_robusta(v)


def password_robusta(v: str) -> str:
    if not (any(c.isalpha() for c in v) and any(c.isdigit() for c in v)):
        raise ValueError("La password deve contenere lettere e numeri (almeno 10 caratteri)")
    return v


class LoginIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=1, max_length=128)


class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
    scade_in_secondi: int


class UtenteOut(Base):
    id: str
    nome_visualizzato: str | None
    ruolo: str
    locale: str
    motore_ai: str
    conservazione_chat_giorni: int | None
    onboarding_completato: bool
    created_at: datetime


class UtenteUpdate(BaseModel):
    nome_visualizzato: str | None = Field(None, min_length=1, max_length=60)
    motore_ai: Motore | None = None
    conservazione_chat_giorni: int | None = Field(None, ge=1, le=3650)
    conservazione_illimitata: bool | None = None
    locale: Literal["it", "en"] | None = None


class CambioPasswordIn(BaseModel):
    password_attuale: str
    nuova_password: str = Field(min_length=10, max_length=128)

    @field_validator("nuova_password")
    @classmethod
    def _robusta(cls, v: str) -> str:
        return password_robusta(v)


class ConsensoIn(BaseModel):
    concesso: bool


class CancellazioneIn(BaseModel):
    password: str
    conferma: Literal["ELIMINA"]


# ── Profilo ───────────────────────────────────────────────────────────────────

class DichiarazioneIn(BaseModel):
    testo: str = Field(min_length=2, max_length=1000)
    categoria: Categoria
    peso: int = Field(3, ge=1, le=5)
    visibilita: Visibilita = "privata"
    usa_in_ai: bool = True
    valido_da: date | None = None
    valido_a: date | None = None

    @model_validator(mode="after")
    def _date(self):
        if self.valido_da and self.valido_a and self.valido_a < self.valido_da:
            raise ValueError("La data di fine validità precede quella di inizio")
        return self


class DichiarazioneUpdate(BaseModel):
    testo: str | None = Field(None, min_length=2, max_length=1000)
    categoria: Categoria | None = None
    peso: int | None = Field(None, ge=1, le=5)
    visibilita: Visibilita | None = None
    usa_in_ai: bool | None = None
    valido_da: date | None = None
    valido_a: date | None = None


class DichiarazioneOut(Base):
    id: str
    testo: str
    categoria: str
    peso: int
    fonte: str
    chiave_domanda: str | None
    valido_da: date | None
    valido_a: date | None
    visibilita: str
    usa_in_ai: bool
    created_at: datetime
    updated_at: datetime


class RispostaQuestionario(BaseModel):
    chiave: str
    testo: str = Field(min_length=2, max_length=1000)
    peso: int = Field(3, ge=1, le=5)
    visibilita: Visibilita = "privata"


class QuestionarioIn(BaseModel):
    risposte: list[RispostaQuestionario] = Field(min_length=1, max_length=50)


# ── Conoscenze ────────────────────────────────────────────────────────────────

class ConoscenzaIn(BaseModel):
    titolo: str = Field(min_length=1, max_length=120)
    contenuto: str = Field(min_length=1, max_length=8000)
    tipo: Literal["nota", "testo", "ricordo", "link"] = "nota"
    categoria: Categoria = "altro"
    usa_in_ai: bool = True
    visibilita: Visibilita = "privata"


class ConoscenzaUpdate(BaseModel):
    titolo: str | None = Field(None, min_length=1, max_length=120)
    contenuto: str | None = Field(None, min_length=1, max_length=8000)
    categoria: Categoria | None = None
    usa_in_ai: bool | None = None
    visibilita: Visibilita | None = None


class ConoscenzaOut(Base):
    id: str
    titolo: str
    contenuto: str
    tipo: str
    categoria: str
    usa_in_ai: bool
    visibilita: str
    sospetta_injection: bool
    created_at: datetime


# ── Chat ──────────────────────────────────────────────────────────────────────

class FonteOut(BaseModel):
    id: str
    tipo: str
    categoria: str
    estratto: str


class MessaggioOut(Base):
    id: str
    ruolo: str
    contenuto: str
    fonti: list[FonteOut] | None = None
    confidenza: str | None = None
    motore: str | None = None
    modello: str | None = None
    prompt_version: str | None = None
    policy_version: str | None = None
    created_at: datetime


class ChatIn(BaseModel):
    messaggio: str = Field(min_length=1, max_length=2000)
    conversation_id: str | None = None
    snapshot_id: str | None = None


class ChatOut(BaseModel):
    conversation_id: str
    domanda: MessaggioOut
    risposta: MessaggioOut
    avvisi: list[str]
    etichetta: str
    durata_ms: int


class ConversazioneOut(Base):
    id: str
    titolo: str
    snapshot_id: str | None
    created_at: datetime
    updated_at: datetime


class ConversazioneDettaglio(ConversazioneOut):
    messaggi: list[MessaggioOut]


# ── Decisioni ─────────────────────────────────────────────────────────────────

class OpzioneIn(BaseModel):
    nome: str = Field(min_length=1, max_length=120)
    descrizione: str | None = Field(None, max_length=500)


class CriterioIn(BaseModel):
    nome: str = Field(min_length=1, max_length=120)
    peso: int = Field(3, ge=1, le=5)
    statement_id: str | None = None


class DecisioneIn(BaseModel):
    domanda: str = Field(min_length=3, max_length=300)
    opzioni: list[OpzioneIn] = Field(min_length=2, max_length=6)
    criteri: list[CriterioIn] = Field(min_length=1, max_length=10)
    valutazioni: dict[str, dict[str, int]] = Field(default_factory=dict)

    @field_validator("valutazioni")
    @classmethod
    def _range(cls, v):
        for riga in v.values():
            for punteggio in riga.values():
                if not 1 <= punteggio <= 5:
                    raise ValueError("Le valutazioni vanno da 1 a 5")
        return v


class ValutazioniIn(BaseModel):
    valutazioni: dict[str, dict[str, int]]

    @field_validator("valutazioni")
    @classmethod
    def _range(cls, v):
        return DecisioneIn._range(v)


class EsitoIn(BaseModel):
    scelta_finale: str = Field(min_length=1, max_length=120)
    esito: Literal["positivo", "neutro", "negativo"] | None = None
    apprendimento: str | None = Field(None, max_length=1000)
    salva_come_dichiarazione: bool = False


class DecisioneOut(Base):
    id: str
    domanda: str
    opzioni: list
    criteri: list
    valutazioni: dict
    risultato: dict
    stato: str
    scelta_finale: str | None
    esito: str | None
    apprendimento: str | None
    created_at: datetime
    updated_at: datetime


# ── Versioni / timeline ──────────────────────────────────────────────────────

class SnapshotIn(BaseModel):
    etichetta: str | None = Field(None, max_length=80)


class IpotesiIn(BaseModel):
    azione: Literal["aggiungi", "modifica", "rimuovi"]
    id: str | None = None
    testo: str | None = Field(None, max_length=1000)
    categoria: Categoria | None = None
    peso: int | None = Field(None, ge=1, le=5)

    @model_validator(mode="after")
    def _coerente(self):
        if self.azione == "aggiungi" and not self.testo:
            raise ValueError("Per aggiungere serve il testo")
        if self.azione in ("modifica", "rimuovi") and not self.id:
            raise ValueError("Indica la dichiarazione da modificare o rimuovere")
        return self


class SimulazioneIn(BaseModel):
    mesi: int = Field(ge=1, le=120)
    ipotesi: list[IpotesiIn] = Field(min_length=1, max_length=30)
    etichetta: str | None = Field(None, max_length=80)


class SnapshotOut(Base):
    id: str
    versione: int
    tipo: str
    etichetta: str
    data_riferimento: date
    riepilogo: str
    impronta: str
    created_at: datetime


class SnapshotDettaglio(SnapshotOut):
    dati: list
    integro: bool = True


# ── Diario e sfide ────────────────────────────────────────────────────────────

class DiarioIn(BaseModel):
    tipo: Literal["nota", "check_in"] = "nota"
    testo: str = Field(min_length=1, max_length=4000)
    energia: int | None = Field(None, ge=1, le=5)
    etichette: list[str] | None = Field(None, max_length=10)


class DiarioOut(Base):
    id: str
    tipo: str
    testo: str
    energia: int | None
    etichette: list | None
    created_at: datetime


class RispostaSfidaIn(BaseModel):
    risposta: str = Field(min_length=2, max_length=2000)
    salva_come_dichiarazione: bool = False
    categoria: Categoria | None = None


# ── Condivisione e compatibilità ─────────────────────────────────────────────

class CondivisioneIn(BaseModel):
    nome: str = Field(min_length=1, max_length=60)
    categorie: list[Categoria] = Field(min_length=1)
    max_domande: int = Field(10, ge=1, le=50)
    durata_ore: int = Field(72, ge=1, le=24 * 30)


class CondivisioneOut(Base):
    id: str
    nome: str
    categorie: list
    max_domande: int
    domande_usate: int
    scadenza: datetime
    revocato_at: datetime | None
    bloccato_moderazione: bool
    created_at: datetime
    attiva: bool = False


class DomandaPubblicaIn(BaseModel):
    domanda: str = Field(min_length=1, max_length=500)


class SegnalazioneIn(BaseModel):
    motivo: Literal["contenuto_offensivo", "imitazione_terzi", "dati_altrui", "spam", "altro"]
    dettagli: str | None = Field(None, max_length=1000)


class InvitoCompatibilitaIn(BaseModel):
    categorie: list[Categoria] = Field(min_length=1)
    durata_giorni: int = Field(7, ge=1, le=30)


class AccettaInvitoIn(BaseModel):
    codice: str = Field(min_length=6, max_length=40)


class RisoluzioneIn(BaseModel):
    azione: Literal["nessuna", "revoca_condivisione", "blocca_utente"]
    nota: str | None = Field(None, max_length=500)
