from decimal import Decimal
from datetime import date, datetime
from typing import Literal
from pydantic import BaseModel, field_validator


TIPI_TASK = Literal["task", "approvazione", "reminder", "acquisto"]
STATI_TASK = Literal["aperto", "in_corso", "completato", "annullato", "approvato", "rifiutato"]
PRIORITA_TASK = Literal["bassa", "media", "alta", "urgente"]
STATI_PASSO = Literal["in_attesa", "approvato", "rifiutato"]


# ── Passo approvazione ────────────────────────────────────────────────────────

class PassoApprovazioneCreate(BaseModel):
    approvatore: str
    ordine: int = 1


class PassoApprovazioneResponse(BaseModel):
    id: int
    task_id: int
    ordine: int
    approvatore: str
    stato: str
    commento: str | None
    aggiornato_at: datetime

    class Config:
        from_attributes = True


class ApprovaPasso(BaseModel):
    stato: STATI_PASSO
    commento: str | None = None


# ── Task ──────────────────────────────────────────────────────────────────────

class TaskCreate(BaseModel):
    titolo: str
    descrizione: str | None = None
    tipo: TIPI_TASK = "task"
    priorita: PRIORITA_TASK = "media"
    assegnato_a: str | None = None
    creato_da: str | None = None
    data_scadenza: date | None = None
    anagrafica_id: int | None = None
    importo_stimato: Decimal | None = None
    note: str | None = None
    passi: list[PassoApprovazioneCreate] = []


class TaskUpdate(BaseModel):
    titolo: str | None = None
    descrizione: str | None = None
    stato: STATI_TASK | None = None
    priorita: PRIORITA_TASK | None = None
    assegnato_a: str | None = None
    data_scadenza: date | None = None
    data_completamento: date | None = None
    importo_stimato: Decimal | None = None
    importo_approvato: Decimal | None = None
    note: str | None = None


class TaskResponse(BaseModel):
    id: int
    titolo: str
    descrizione: str | None
    tipo: str
    stato: str
    priorita: str
    assegnato_a: str | None
    creato_da: str | None
    data_scadenza: date | None
    data_completamento: date | None
    anagrafica_id: int | None
    anagrafica_nome: str | None = None
    importo_stimato: Decimal | None
    importo_approvato: Decimal | None
    reminder_inviato: bool
    note: str | None
    created_at: datetime
    updated_at: datetime
    passi: list[PassoApprovazioneResponse] = []
    giorni_alla_scadenza: int | None = None
    passo_corrente: int | None = None

    class Config:
        from_attributes = True


# ── Summary ───────────────────────────────────────────────────────────────────

class WorkflowSummary(BaseModel):
    task_aperti: int
    task_in_corso: int
    approvazioni_in_attesa: int
    reminder_in_scadenza: int
    acquisti_aperti: int
    task_scaduti: int
