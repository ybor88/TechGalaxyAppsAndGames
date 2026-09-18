from decimal import Decimal
from datetime import date, datetime

from pydantic import BaseModel, Field


# ── Commesse ─────────────────────────────────────────────────────────────────

class CommessaCreate(BaseModel):
    codice: str
    descrizione: str
    prodotto_id: int
    centro_costo_id: int
    anagrafica_id: int | None = None
    quantita_pianificata: Decimal = Field(gt=0)
    data_apertura: date
    note: str | None = None


class CommessaUpdate(BaseModel):
    descrizione: str | None = None
    centro_costo_id: int | None = None
    anagrafica_id: int | None = None
    quantita_pianificata: Decimal | None = None
    note: str | None = None


class ChiudiCommessaRequest(BaseModel):
    data_chiusura: date
    quantita_prodotta: Decimal = Field(gt=0)


class CommessaOut(BaseModel):
    id: int
    codice: str
    descrizione: str
    prodotto_id: int
    prodotto_codice: str
    prodotto_descrizione: str
    centro_costo_id: int
    centro_costo_descrizione: str
    anagrafica_id: int | None
    anagrafica_nome: str | None
    quantita_pianificata: Decimal
    quantita_prodotta: Decimal | None
    data_apertura: date
    data_chiusura: date | None
    stato: str
    costo_totale_consuntivo: Decimal
    note: str | None
    created_at: datetime

    class Config:
        from_attributes = True


# ── Righe di costo ───────────────────────────────────────────────────────────

class RigaCostoCommessaCreate(BaseModel):
    tipo: str = Field(pattern="^(materiale|manodopera|indiretto)$")
    descrizione: str
    data: date
    prodotto_id: int | None = None  # obbligatorio per tipo=materiale (scarica anche il magazzino)
    centro_costo_id: int | None = None
    quantita: Decimal | None = None  # qtà materiale o ore manodopera
    costo_unitario: Decimal | None = None  # prezzo materiale o costo orario manodopera
    importo: Decimal | None = None  # obbligatorio per tipo=indiretto, altrimenti calcolato


class RigaCostoCommessaOut(BaseModel):
    id: int
    commessa_id: int
    tipo: str
    descrizione: str
    data: date
    prodotto_id: int | None
    prodotto_descrizione: str | None
    centro_costo_id: int | None
    centro_costo_descrizione: str | None
    quantita: Decimal | None
    costo_unitario: Decimal | None
    importo: Decimal
    created_at: datetime

    class Config:
        from_attributes = True


class RiepilogoCostiCommessa(BaseModel):
    commessa_id: int
    totale_materiale: Decimal
    totale_manodopera: Decimal
    totale_indiretti: Decimal
    totale: Decimal
    costo_unitario: Decimal | None  # totale / quantita di riferimento (prodotta se chiusa, altrimenti pianificata)


# ── Analisi degli scostamenti ────────────────────────────────────────────────

class ScostamentoVoce(BaseModel):
    categoria: str  # materiale | manodopera | indiretti
    standard: Decimal
    consuntivo: Decimal
    scostamento: Decimal  # consuntivo - standard (positivo = sfavorevole)
    # Scomposizione dello scostamento (stessa convenzione: positivo = sfavorevole).
    # La somma di prezzo + quantità coincide con `scostamento`.
    scostamento_prezzo: Decimal | None = None
    scostamento_quantita: Decimal | None = None


class ScostamentiCommessaResponse(BaseModel):
    commessa_id: int
    codice: str
    quantita_riferimento: Decimal
    quantita_riferimento_tipo: str  # "prodotta" | "pianificata"
    voci: list[ScostamentoVoce]
    scostamento_totale: Decimal
