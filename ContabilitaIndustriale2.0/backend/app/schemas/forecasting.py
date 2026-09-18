from pydantic import BaseModel


class PuntoPrevisione(BaseModel):
    periodo: str        # YYYY-MM (mesi) o YYYY-MM-DD (giorni)
    valore: float
    confidenza_min: float
    confidenza_max: float


class PrevisioneVenditeResponse(BaseModel):
    storico: list[PuntoPrevisione]
    previsione: list[PuntoPrevisione]
    trend: str                  # "crescente" | "stabile" | "decrescente"
    variazione_percentuale: float


class PrevisioneLiquiditaResponse(BaseModel):
    saldo_attuale: float
    previsione_giorni: list[PuntoPrevisione]
    giorni_copertura: int       # giorni stimati prima di un eventuale deficit
    allerta: bool


class ScenarioItem(BaseModel):
    scenario: str               # "ottimistico" | "base" | "pessimistico"
    entrate_previste: float
    uscite_previste: float
    cashflow: float
    variazione_percentuale: float   # vs media storica


class SimulazioneScenariResponse(BaseModel):
    mese_riferimento: str
    media_storica_entrate: float
    media_storica_uscite: float
    scenari: list[ScenarioItem]


class FattoreRischio(BaseModel):
    fattore: str
    impatto: str                # "alto" | "medio" | "basso"


class RischioInsolvenzaResponse(BaseModel):
    punteggio: float            # 0-100
    livello: str                # "basso" | "medio" | "alto" | "critico"
    fattori: list[FattoreRischio]
    raccomandazioni: list[str]
