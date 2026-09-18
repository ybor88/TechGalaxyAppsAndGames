from decimal import Decimal

from pydantic import BaseModel, Field


class ClassificazioneCostoSet(BaseModel):
    conto_id: int
    classificazione: str = Field(pattern="^(fisso|variabile)$")


class ClassificazioneCostoOut(BaseModel):
    conto_id: int
    conto_codice: str
    conto_descrizione: str
    tipo_conto: str
    classificazione: str | None  # None = non ancora classificato

    class Config:
        from_attributes = True


class BreakEvenAziendale(BaseModel):
    totale_ricavi: Decimal
    totale_costi_fissi: Decimal
    totale_costi_variabili: Decimal
    margine_di_contribuzione: Decimal
    percentuale_margine_di_contribuzione: Decimal  # 0-100
    punto_di_pareggio_valore: Decimal | None  # None se margine <= 0
    conti_non_classificati: int


class BreakEvenProdotto(BaseModel):
    prodotto_id: int
    codice: str
    descrizione: str
    prezzo_vendita: Decimal
    costo_variabile_unitario: Decimal
    margine_di_contribuzione_unitario: Decimal
    percentuale_margine_di_contribuzione: Decimal
    quota_costi_fissi_aziendali: Decimal
    punto_di_pareggio_quantita: Decimal | None


class KPIIndustriale(BaseModel):
    numero_commesse_aperte: int
    valore_commesse_aperte: Decimal
    numero_commesse_chiuse_periodo: int
    scostamento_totale_periodo: Decimal
    valore_magazzino: Decimal
    numero_prodotti_sotto_scorta: int
    margine_di_contribuzione_percentuale: Decimal | None
