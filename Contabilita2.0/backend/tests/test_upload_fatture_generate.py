# © Roberto Di Flumeri
"""
Upload end-to-end delle fatture generate da scripts/genera_fatture_test.py.

Ogni file viene caricato su POST /api/v1/ocr/elabora e confrontato con
l'esito atteso dichiarato dal generatore (attesi.json).
"""

import sys
from decimal import Decimal
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "scripts"))

import genera_fatture_test as gen  # noqa: E402
import app.services.ocr as ocr_service  # noqa: E402

_MIME = {
    "pdf": "application/pdf", "png": "image/png", "jpg": "image/jpeg",
    "tiff": "image/tiff", "bmp": "image/bmp", "webp": "image/webp", "txt": "text/plain",
}


def _tesseract_ok() -> bool:
    try:
        return ocr_service._configura_tesseract() is not None
    except Exception:
        return False


@pytest.fixture(scope="module")
def fatture(tmp_path_factory):
    cartella = tmp_path_factory.mktemp("fatture")
    return cartella, gen.genera(cartella, grande=True)


def _nomi():
    # elenco statico (senza generare file) per parametrizzare i test
    nomi = [f.file for f in gen.FATTURE_PDF] + [n for n, _ in gen.FATTURE_IMMAGINE]
    nomi += ["20_senza_dati_fiscali.pdf", "21_pdf_corrotto.pdf", "22_file_vuoto.pdf",
             "23_formato_non_supportato.txt", "24_troppo_grande.pdf"]
    return nomi


@pytest.mark.parametrize("nome", _nomi())
async def test_upload_fattura_generata(client, fatture, nome):
    cartella, attesi = fatture
    att = attesi[nome]
    if att.get("richiede_tesseract") and not _tesseract_ok():
        pytest.skip("Tesseract non installato")

    contenuto = (cartella / nome).read_bytes()
    mime = _MIME[nome.rsplit(".", 1)[-1]]
    r = await client.post("/api/v1/ocr/elabora", files={"file": (nome, contenuto, mime)})
    assert r.status_code == att["status_code"], r.text
    if r.status_code != 201:
        return

    ris = r.json()["risultato"]
    assert ris["stato"] == att["stato"], ris
    if att["stato"] != "elaborato":
        return

    assert ris["piva"] == att["piva"]
    assert Decimal(ris["importo_totale"]) == Decimal(att["importo_totale"])
    assert Decimal(ris["importo_netto"]) == Decimal(att["importo_netto"])
    assert Decimal(ris["importo_iva"]) == Decimal(att["importo_iva"])
    if not att.get("richiede_tesseract"):
        # sui PDF digitali il testo è esatto: verifichiamo tutti i campi
        assert ris["numero_documento"] == att["numero_documento"]
        assert ris["data_documento"] == att["data_documento"]
        assert ris["cf"] == att["cf"]
        assert Decimal(ris["aliquota_iva"]) == att["aliquota_iva"]


async def test_upload_fattura_e_collega_documento(client, fatture):
    """Flusso completo: OCR -> creazione fattura passiva -> collegamento."""
    cartella, attesi = fatture
    nome = "01_fattura_standard_22.pdf"
    r = await client.post(
        "/api/v1/ocr/elabora",
        files={"file": (nome, (cartella / nome).read_bytes(), "application/pdf")},
    )
    ris = r.json()["risultato"]

    r = await client.post("/api/v1/anagrafiche/", json={
        "nome": ris["fornitore"], "tipo": "fornitore", "piva": ris["piva"]})
    assert r.status_code == 201
    ana_id = r.json()["id"]

    r = await client.post("/api/v1/documenti/", json={
        "tipo": "fattura_passiva",
        "data": ris["data_documento"],
        "anagrafica_id": ana_id,
        "righe": [{"descrizione": "Da OCR", "quantita": "1",
                   "prezzo_unitario": ris["importo_netto"],
                   "iva_percentuale": ris["aliquota_iva"]}],
    })
    assert r.status_code == 201, r.text
    doc = r.json()
    assert Decimal(doc["totale"]) == Decimal(attesi[nome]["importo_totale"])

    r = await client.patch(f"/api/v1/ocr/risultati/{ris['id']}/collega-documento/{doc['id']}")
    assert r.status_code == 200
    assert r.json()["documento_id"] == doc["id"]


def test_importi_inglesi_sopra_mille():
    testo = ("ACME EUROPE LTD\nVAT number 04567890123\nSubtotal: 3,300.00\n"
             "VAT 22%\nTotal VAT: 726.00\nTotal: 4,026.00")
    r = ocr_service._parse_dati_fattura(testo)
    assert r["importo_totale"] == Decimal("4026.00")
    assert r["importo_netto"] == Decimal("3300.00")
    assert r["importo_iva"] == Decimal("726.00")
    assert r["aliquota_iva"] == Decimal("22")


@pytest.mark.parametrize("testo,atteso", [
    ("Invoice No. INV-55012", "INV-55012"),
    ("Invoice Number: 2026/7", "2026/7"),
    ("Fattura numero 12", "12"),
    ("Fattura nr. A-77", "A-77"),
    ("Fattura n. FT-2026/001", "FT-2026/001"),
    ("Fattura N° 45", "45"),
])
def test_numero_documento_varianti(testo, atteso):
    assert ocr_service._parse_dati_fattura(testo)["numero_documento"] == atteso
