from datetime import date, datetime
from decimal import Decimal

from pydantic import BaseModel


class OcrRisultatoResponse(BaseModel):
    id: int
    filename: str
    content_type: str
    testo_estratto: str | None
    fornitore: str | None
    piva: str | None
    cf: str | None
    numero_documento: str | None
    data_documento: date | None
    importo_netto: Decimal | None
    importo_iva: Decimal | None
    importo_totale: Decimal | None
    aliquota_iva: Decimal | None
    stato: str
    errore: str | None
    documento_id: int | None
    created_at: datetime

    model_config = {"from_attributes": True}


class OcrElaboraResponse(BaseModel):
    risultato: OcrRisultatoResponse
    avvisi: list[str]
