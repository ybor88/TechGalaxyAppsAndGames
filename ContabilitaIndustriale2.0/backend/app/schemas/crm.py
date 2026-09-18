from decimal import Decimal
from datetime import date, datetime
from typing import Literal
from pydantic import BaseModel, field_validator


METODI_PAGAMENTO = Literal["bonifico", "contanti", "assegno", "carta", "rid", "altro"]
TIPI_SCADENZA = Literal["incasso", "pagamento", "altro"]
STATI_SCADENZA = Literal["aperta", "pagata", "scaduta", "annullata"]
FASI_PIPELINE = Literal[
    "prospecting", "qualifica", "proposta", "trattativa", "chiusa_vinta", "chiusa_persa"
]


# ── Storico Pagamenti ────────────────────────────────────────────────────────

class StoricoPagamentoCreate(BaseModel):
    anagrafica_id: int
    documento_id: int | None = None
    data_pagamento: date
    importo: Decimal
    metodo_pagamento: METODI_PAGAMENTO = "bonifico"
    giorni_ritardo: int = 0
    note: str | None = None

    @field_validator("importo")
    @classmethod
    def importo_positivo(cls, v: Decimal) -> Decimal:
        if v <= 0:
            raise ValueError("importo deve essere maggiore di zero")
        return v


class StoricoPagamentoResponse(BaseModel):
    id: int
    anagrafica_id: int
    documento_id: int | None
    data_pagamento: date
    importo: Decimal
    metodo_pagamento: str
    giorni_ritardo: int
    note: str | None
    created_at: datetime
    anagrafica_nome: str | None = None

    class Config:
        from_attributes = True


# ── Affidabilità Cliente ─────────────────────────────────────────────────────

class AffidabilitaCliente(BaseModel):
    anagrafica_id: int
    nome: str
    totale_pagamenti: int
    pagamenti_puntuali: int
    pagamenti_in_ritardo: int
    media_giorni_ritardo: float
    score: int  # 0-100
    livello: str  # ottimo | buono | sufficiente | scarso


# ── Scadenze ─────────────────────────────────────────────────────────────────

class ScadenzaCreate(BaseModel):
    anagrafica_id: int | None = None
    documento_id: int | None = None
    titolo: str
    descrizione: str | None = None
    data_scadenza: date
    importo: Decimal | None = None
    tipo: TIPI_SCADENZA = "incasso"
    stato: STATI_SCADENZA = "aperta"
    note: str | None = None


class ScadenzaUpdate(BaseModel):
    titolo: str | None = None
    descrizione: str | None = None
    data_scadenza: date | None = None
    importo: Decimal | None = None
    tipo: TIPI_SCADENZA | None = None
    stato: STATI_SCADENZA | None = None
    note: str | None = None


class ScadenzaResponse(BaseModel):
    id: int
    anagrafica_id: int | None
    documento_id: int | None
    titolo: str
    descrizione: str | None
    data_scadenza: date
    importo: Decimal | None
    tipo: str
    stato: str
    note: str | None
    created_at: datetime
    anagrafica_nome: str | None = None
    giorni_alla_scadenza: int | None = None

    class Config:
        from_attributes = True


# ── Pipeline Commerciale ─────────────────────────────────────────────────────

class OpportunitaCreate(BaseModel):
    anagrafica_id: int | None = None
    titolo: str
    valore_stimato: Decimal | None = None
    fase: FASI_PIPELINE = "prospecting"
    probabilita: int = 50
    data_chiusura_prevista: date | None = None
    note: str | None = None

    @field_validator("probabilita")
    @classmethod
    def probabilita_valida(cls, v: int) -> int:
        if v < 0 or v > 100:
            raise ValueError("probabilita deve essere tra 0 e 100")
        return v


class OpportunitaUpdate(BaseModel):
    anagrafica_id: int | None = None
    titolo: str | None = None
    valore_stimato: Decimal | None = None
    fase: FASI_PIPELINE | None = None
    probabilita: int | None = None
    data_chiusura_prevista: date | None = None
    note: str | None = None

    @field_validator("probabilita")
    @classmethod
    def probabilita_valida(cls, v: int | None) -> int | None:
        if v is not None and (v < 0 or v > 100):
            raise ValueError("probabilita deve essere tra 0 e 100")
        return v


class OpportunitaResponse(BaseModel):
    id: int
    anagrafica_id: int | None
    titolo: str
    valore_stimato: Decimal | None
    fase: str
    probabilita: int
    data_chiusura_prevista: date | None
    note: str | None
    created_at: datetime
    updated_at: datetime
    anagrafica_nome: str | None = None

    class Config:
        from_attributes = True


# ── Riepilogo CRM ────────────────────────────────────────────────────────────

class CrmSummary(BaseModel):
    totale_clienti: int
    totale_fornitori: int
    scadenze_aperte: int
    scadenze_scadute: int
    valore_pipeline_attivo: Decimal
    opportunita_aperte: int
