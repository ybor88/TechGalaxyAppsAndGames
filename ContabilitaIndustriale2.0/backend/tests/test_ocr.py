import io
from datetime import date
from decimal import Decimal

import pytest

from app.services.ocr import _parse_number, _parse_date, _parse_dati_fattura
import app.services.ocr as ocr_service_module


def _crea_pdf(righe: list[str]) -> bytes:
    """Costruisce un PDF minimale con testo nativo (nessun bisogno di Tesseract)."""
    from reportlab.pdfgen import canvas
    from reportlab.lib.pagesizes import A4

    buf = io.BytesIO()
    c = canvas.Canvas(buf, pagesize=A4)
    y = 800
    for riga in righe:
        c.drawString(80, y, riga)
        y -= 20
    c.save()
    return buf.getvalue()


# ── Funzioni pure di parsing ──────────────────────────────────────────────────

class TestParseNumber:
    def test_formato_italiano_con_migliaia(self):
        assert _parse_number("1.234,56") == Decimal("1234.56")

    def test_formato_italiano_senza_migliaia(self):
        assert _parse_number("22,00") == Decimal("22.00")

    def test_formato_inglese_con_migliaia(self):
        assert _parse_number("1,234.56") == Decimal("1234.56")

    def test_formato_inglese_senza_migliaia(self):
        assert _parse_number("100.50") == Decimal("100.50")

    def test_stringa_vuota(self):
        assert _parse_number("") is None

    def test_none_implicito(self):
        assert _parse_number(None) is None

    def test_stringa_non_numerica(self):
        assert _parse_number("abc") is None


class TestParseDate:
    def test_formato_gg_mm_aaaa_slash(self):
        assert _parse_date("Data: 15/03/2024") == date(2024, 3, 15)

    def test_formato_gg_mm_aaaa_trattino(self):
        assert _parse_date("Data: 15-03-2024") == date(2024, 3, 15)

    def test_formato_aaaa_mm_gg(self):
        assert _parse_date("Data: 2024-03-15") == date(2024, 3, 15)

    def test_nessuna_data(self):
        assert _parse_date("Nessuna data qui") is None

    def test_data_non_valida_ignorata(self):
        # 32/13/2024 non è una data valida: nessun altro match nel testo -> None
        assert _parse_date("32/13/2024") is None


class TestParseDatiFattura:
    def test_estrazione_completa_fattura_italiana(self):
        testo = (
            "ACME SRL\n"
            "Fattura n. 2024/001\n"
            "Data: 15/03/2024\n"
            "P.IVA 12345678901\n"
            "Imponibile € 1.000,00\n"
            "IVA 22%\n"
            "Totale € 1.220,00\n"
            "Fornitore: ACME SRL\n"
        )
        dati = _parse_dati_fattura(testo)
        assert dati["piva"] == "12345678901"
        assert dati["numero_documento"] == "2024/001"
        assert dati["data_documento"] == date(2024, 3, 15)
        assert dati["importo_netto"] == Decimal("1000.00")
        assert dati["importo_totale"] == Decimal("1220.00")
        assert dati["aliquota_iva"] == Decimal("22")
        assert dati["fornitore"] == "ACME SRL"

    def test_calcolo_incrociato_iva_da_totale_e_netto(self):
        testo = "Totale € 1.220,00\nImponibile € 1.000,00\n"
        dati = _parse_dati_fattura(testo)
        assert dati["importo_iva"] == Decimal("220.00")

    def test_calcolo_incrociato_netto_da_totale_e_iva(self):
        testo = "Totale € 1.220,00\nImporto IVA € 220,00\n"
        dati = _parse_dati_fattura(testo)
        assert dati["importo_netto"] == Decimal("1000.00")

    def test_calcolo_incrociato_totale_da_netto_e_iva(self):
        testo = "Imponibile € 1.000,00\nImporto IVA € 220,00\n"
        dati = _parse_dati_fattura(testo)
        assert dati["importo_totale"] == Decimal("1220.00")

    def test_codice_fiscale_diverso_da_piva(self):
        testo = "P.IVA 12345678901\nCodice Fiscale RSSMRA80A01H501U\n"
        dati = _parse_dati_fattura(testo)
        assert dati["piva"] == "12345678901"
        assert dati["cf"] == "RSSMRA80A01H501U"

    def test_testo_vuoto_nessun_campo(self):
        dati = _parse_dati_fattura("")
        assert all(v is None for v in dati.values())

    def test_fornitore_fallback_prima_riga(self):
        testo = "Trattoria Da Mario\nVia Roma 1\nTotale € 50,00\n"
        dati = _parse_dati_fattura(testo)
        assert dati["fornitore"] == "Trattoria Da Mario"


# ── Endpoint /elabora: rifiuto formati non supportati ─────────────────────────

async def test_elabora_formato_non_supportato(client):
    files = {"file": ("nota.txt", b"contenuto qualsiasi", "text/plain")}
    r = await client.post("/api/v1/ocr/elabora", files=files)
    assert r.status_code == 415


async def test_elabora_file_vuoto(client):
    files = {"file": ("fattura.pdf", b"", "application/pdf")}
    r = await client.post("/api/v1/ocr/elabora", files=files)
    assert r.status_code == 400


async def test_elabora_file_troppo_grande(client):
    contenuto = b"0" * (21 * 1024 * 1024)
    files = {"file": ("fattura.pdf", contenuto, "application/pdf")}
    r = await client.post("/api/v1/ocr/elabora", files=files)
    assert r.status_code == 413


# ── Endpoint /elabora: PDF con testo nativo (nessun bisogno di Tesseract) ─────

async def test_elabora_pdf_testo_nativo(client):
    pdf_bytes = _crea_pdf([
        "ACME SRL",
        "Fattura n. 2024/001",
        "Data: 15/03/2024",
        "P.IVA 12345678901",
        "Imponibile 1.000,00",
        "IVA 22%",
        "Totale 1.220,00",
        "Fornitore: ACME SRL",
        "Testo aggiuntivo per superare la soglia minima di 50 caratteri nativi.",
    ])
    files = {"file": ("fattura.pdf", pdf_bytes, "application/pdf")}
    r = await client.post("/api/v1/ocr/elabora", files=files)
    assert r.status_code == 201, r.text
    data = r.json()
    risultato = data["risultato"]
    assert risultato["stato"] == "elaborato"
    assert risultato["piva"] == "12345678901"
    assert risultato["importo_totale"] == "1220.00"
    assert risultato["testo_estratto"] is not None


async def test_elabora_pdf_senza_dati_riconoscibili_va_in_revisione(client):
    pdf_bytes = _crea_pdf([
        "Questo documento non contiene alcun dato fiscale riconoscibile,",
        "ma ha comunque abbastanza testo nativo da superare la soglia minima.",
    ])
    files = {"file": ("documento.pdf", pdf_bytes, "application/pdf")}
    r = await client.post("/api/v1/ocr/elabora", files=files)
    assert r.status_code == 201, r.text
    risultato = r.json()["risultato"]
    assert risultato["stato"] == "revisione"
    assert risultato["piva"] is None
    assert risultato["cf"] is None


# ── Endpoint /elabora: immagine senza Tesseract installato ────────────────────

async def test_elabora_immagine_senza_tesseract_installato_gestito_senza_crash(client):
    """
    Il binario Tesseract non è installato su questa macchina: pytesseract solleverà
    un errore a runtime. Il servizio deve gestirlo con stato 'errore', non con un 500.
    """
    from PIL import Image

    buf = io.BytesIO()
    Image.new("RGB", (100, 50), color="white").save(buf, format="PNG")
    files = {"file": ("scontrino.png", buf.getvalue(), "image/png")}
    r = await client.post("/api/v1/ocr/elabora", files=files)
    assert r.status_code == 201, r.text
    risultato = r.json()["risultato"]
    assert risultato["stato"] == "errore"
    assert risultato["errore"] is not None


async def test_elabora_immagine_con_ocr_mockato(client, monkeypatch):
    """Monkeypatcha l'estrazione testo immagine per testare il parsing senza Tesseract reale."""
    testo_finto = (
        "FORNITORE TEST SRL\n"
        "P.IVA 98765432109\n"
        "Data: 01/06/2024\n"
        "Totale € 500,00\n"
        "Imponibile € 409,84\n"
    )
    monkeypatch.setattr(ocr_service_module, "_estrai_testo_immagine", lambda data: testo_finto)

    files = {"file": ("scontrino.png", b"fake-image-bytes", "image/png")}
    r = await client.post("/api/v1/ocr/elabora", files=files)
    assert r.status_code == 201, r.text
    risultato = r.json()["risultato"]
    assert risultato["stato"] == "elaborato"
    assert risultato["piva"] == "98765432109"
    assert risultato["importo_totale"] == "500.00"
    assert risultato["testo_estratto"] == testo_finto


# ── CRUD risultati ──────────────────────────────────────────────────────────

async def test_list_risultati_vuoto(client):
    r = await client.get("/api/v1/ocr/risultati")
    assert r.status_code == 200
    assert r.json() == []


async def test_get_risultato_inesistente(client):
    r = await client.get("/api/v1/ocr/risultati/9999")
    assert r.status_code == 404


async def test_get_risultato(client):
    pdf_bytes = _crea_pdf(["Totale 100,00", "testo di riempimento per superare i 50 caratteri minimi richiesti."])
    files = {"file": ("f.pdf", pdf_bytes, "application/pdf")}
    r = await client.post("/api/v1/ocr/elabora", files=files)
    risultato_id = r.json()["risultato"]["id"]

    r = await client.get(f"/api/v1/ocr/risultati/{risultato_id}")
    assert r.status_code == 200
    assert r.json()["id"] == risultato_id


async def test_delete_risultato(client):
    pdf_bytes = _crea_pdf(["Totale 100,00", "testo di riempimento per superare i 50 caratteri minimi richiesti."])
    files = {"file": ("f.pdf", pdf_bytes, "application/pdf")}
    r = await client.post("/api/v1/ocr/elabora", files=files)
    risultato_id = r.json()["risultato"]["id"]

    r = await client.delete(f"/api/v1/ocr/risultati/{risultato_id}")
    assert r.status_code == 204

    r = await client.get(f"/api/v1/ocr/risultati/{risultato_id}")
    assert r.status_code == 404


async def test_delete_risultato_inesistente(client):
    r = await client.delete("/api/v1/ocr/risultati/9999")
    assert r.status_code == 404


async def test_collega_documento(client):
    # Crea un documento reale a cui collegare il risultato OCR
    doc_payload = {"tipo": "fattura_passiva", "data": "2026-01-15", "righe": []}
    r = await client.post("/api/v1/documenti/", json=doc_payload)
    documento_id = r.json()["id"]

    pdf_bytes = _crea_pdf(["Totale 100,00", "testo di riempimento per superare i 50 caratteri minimi richiesti."])
    files = {"file": ("f.pdf", pdf_bytes, "application/pdf")}
    r = await client.post("/api/v1/ocr/elabora", files=files)
    risultato_id = r.json()["risultato"]["id"]

    r = await client.patch(f"/api/v1/ocr/risultati/{risultato_id}/collega-documento/{documento_id}")
    assert r.status_code == 200, r.text
    assert r.json()["documento_id"] == documento_id


async def test_collega_documento_risultato_inesistente(client):
    r = await client.patch("/api/v1/ocr/risultati/9999/collega-documento/1")
    assert r.status_code == 404
