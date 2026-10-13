"""
Generatore di fatture di test per l'upload OCR (sezione "OCR Fatture").

Crea una cartella con fatture fittizie in tutti i formati accettati
(PDF digitale, PDF scansionato, PNG, JPG, TIFF, BMP, WEBP) più alcuni casi
negativi (PDF corrotto, file vuoto, formato non supportato, documento senza
dati fiscali). Per ogni file scrive in `attesi.json` l'esito atteso
dell'upload: codice HTTP, stato OCR e campi estratti.

Uso:
    cd backend
    .venv\\Scripts\\python scripts\\genera_fatture_test.py            # -> ..\\fatture_di_test
    .venv\\Scripts\\python scripts\\genera_fatture_test.py C:\\percorso  # cartella a scelta
    .venv\\Scripts\\python scripts\\genera_fatture_test.py --grande     # include file da 21 MB (413)

I file si caricano poi da http://localhost:3000/ocr oppure con
POST /api/v1/ocr/elabora.

© Roberto Di Flumeri
"""

from __future__ import annotations

import io
import json
import sys
from dataclasses import dataclass, field
from decimal import Decimal
from pathlib import Path

# ── Dati delle fatture ────────────────────────────────────────────────────────


def _it(n: Decimal) -> str:
    """1234.5 -> '1.234,50'"""
    return f"{n:,.2f}".replace(",", "X").replace(".", ",").replace("X", ".")


def _en(n: Decimal) -> str:
    """1234.5 -> '1,234.50'"""
    return f"{n:,.2f}"


@dataclass
class Fattura:
    file: str
    fornitore: str
    piva: str
    numero: str
    data: str  # gg/mm/aaaa
    righe: list[tuple[str, int, Decimal]]  # descrizione, quantità, prezzo
    aliquota: int = 22
    cf: str | None = None
    inglese: bool = False
    totale_con_eur: bool = False
    subtotale_label: str = "Imponibile"
    note: list[str] = field(default_factory=list)

    @property
    def imponibile(self) -> Decimal:
        return sum((q * p for _, q, p in self.righe), Decimal("0")).quantize(Decimal("0.01"))

    @property
    def iva(self) -> Decimal:
        return (self.imponibile * self.aliquota / 100).quantize(Decimal("0.01"))

    @property
    def totale(self) -> Decimal:
        return self.imponibile + self.iva

    def righe_testo(self) -> list[str]:
        fmt = _en if self.inglese else _it
        if self.inglese:
            testa = [self.fornitore, f"VAT number {self.piva}", f"Invoice No. {self.numero}",
                     f"Date: {self.data}", ""]
            corpo = [f"{d}  {q} x {fmt(p)}" for d, q, p in self.righe]
            piede = ["", f"Subtotal: {fmt(self.imponibile)}", f"VAT {self.aliquota}%",
                     f"Total VAT: {fmt(self.iva)}", f"Total: {fmt(self.totale)}"]
        else:
            testa = [self.fornitore, f"P.IVA {self.piva}"]
            if self.cf:
                testa.append(f"C.F. {self.cf}")
            testa += [f"Fattura n. {self.numero}", f"Data: {self.data}", ""]
            corpo = [f"{d}  {q} x {fmt(p)}" for d, q, p in self.righe]
            totale = f"Totale documento EUR {fmt(self.totale)}" if self.totale_con_eur \
                else f"Totale: {fmt(self.totale)}"
            piede = ["", f"{self.subtotale_label}: {fmt(self.imponibile)}",
                     f"Aliquota IVA {self.aliquota}%", f"Totale IVA: {fmt(self.iva)}", totale]
        return testa + corpo + piede + self.note

    def attesi(self) -> dict:
        return {
            "status_code": 201,
            "stato": "elaborato",
            "piva": self.piva,
            "cf": self.cf,
            "numero_documento": self.numero,
            "data_documento": "-".join(reversed(self.data.split("/"))),
            "importo_netto": str(self.imponibile),
            "importo_iva": str(self.iva),
            "importo_totale": str(self.totale),
            "aliquota_iva": self.aliquota,
        }


D = Decimal
FATTURE_PDF = [
    Fattura("01_fattura_standard_22.pdf", "ROSSI FORNITURE SRL", "01234567890", "FT-2026/001",
            "15/01/2026", [("Carta A4 risme", 40, D("4.50")), ("Toner stampante", 3, D("89.90"))]),
    Fattura("02_fattura_aliquota_10.pdf", "BIANCHI EDILIZIA SPA", "02345678901", "2026-118",
            "03/02/2026", [("Ristrutturazione bagno", 1, D("8500.00"))], aliquota=10),
    Fattura("03_fattura_aliquota_4_eur.pdf", "VERDI ALIMENTARI SNC", "03456789012", "A-77",
            "28/02/2026", [("Pane e farine", 120, D("2.35")), ("Latte fresco", 60, D("1.10"))],
            aliquota=4, totale_con_eur=True),
    Fattura("04_invoice_inglese.pdf", "ACME EUROPE LTD", "04567890123", "INV-55012",
            "10/03/2026", [("Cloud hosting annual plan", 1, D("2400.00")),
                           ("Support hours", 12, D("75.00"))], inglese=True),
    Fattura("05_subtotale_prima_del_totale.pdf", "NERI CONSULTING SRL", "05678901234", "FT-2026/042",
            "21/03/2026", [("Consulenza fiscale", 10, D("100.00"))], subtotale_label="Subtotale"),
    Fattura("06_professionista_con_cf.pdf", "STUDIO LEGALE MARIO ROSSI", "06789012345", "12/2026",
            "05/04/2026", [("Parere legale", 1, D("1500.00")), ("Diritti di segreteria", 1, D("50.00"))],
            cf="RSSMRA80A01H501U"),
    Fattura("07_multipagina_80_righe.pdf", "GROSSISTA RICAMBI SPA", "07890123456", "FT-2026/900",
            "30/04/2026", [(f"Ricambio codice R{i:04d}", (i % 5) + 1, D("3.40") + i) for i in range(80)]),
]

FATTURE_IMMAGINE = [
    ("08_scansione.pdf", Fattura("", "GALLI TRASPORTI SRL", "08901234567", "T-310", "12/05/2026",
                                  [("Trasporto bancali", 8, D("125.00"))])),
    ("09_foto_fattura.png", Fattura("", "FERRI METALLI SRL", "09012345678", "FT-2026/077", "18/05/2026",
                                     [("Lamiera inox", 5, D("240.00"))])),
    ("10_foto_fattura.jpg", Fattura("", "COSTA SERVIZI SRL", "10123456789", "FT-2026/120", "02/06/2026",
                                     [("Pulizia uffici", 1, D("650.00"))])),
    ("11_foto_fattura.tiff", Fattura("", "MARINO ENERGIA SPA", "11234567890", "E-4410", "15/06/2026",
                                      [("Fornitura energia", 1, D("1830.00"))])),
    ("12_foto_fattura.bmp", Fattura("", "GRECO INFORMATICA SRL", "12345678901", "FT-2026/301", "01/07/2026",
                                     [("Notebook", 2, D("899.00"))])),
    ("13_foto_fattura.webp", Fattura("", "BRUNO ARREDI SNC", "13456789012", "AR-19", "20/07/2026",
                                      [("Scrivania", 3, D("310.00"))])),
]

# ── Rendering ─────────────────────────────────────────────────────────────────


def _pdf_testuale(righe: list[str]) -> bytes:
    from reportlab.lib.pagesizes import A4
    from reportlab.pdfgen import canvas

    buf = io.BytesIO()
    c = canvas.Canvas(buf, pagesize=A4)
    y = 800
    for riga in righe:
        if y < 60:
            c.showPage()
            y = 800
        c.setFont("Helvetica", 10)
        c.drawString(60, y, riga)
        y -= 16
    c.save()
    return buf.getvalue()


def _immagine(righe: list[str]):
    from PIL import Image, ImageDraw, ImageFont

    try:
        font = ImageFont.truetype("arial.ttf", 34)
    except OSError:
        font = ImageFont.load_default(size=34)
    img = Image.new("RGB", (1240, 80 + 56 * len(righe)), "white")
    d = ImageDraw.Draw(img)
    for i, r in enumerate(righe):
        d.text((50, 40 + 56 * i), r, fill="black", font=font)
    return img


def _salva_immagine(img, nome: str) -> bytes:
    ext = nome.rsplit(".", 1)[-1].lower()
    fmt = {"png": "PNG", "jpg": "JPEG", "tiff": "TIFF", "bmp": "BMP", "webp": "WEBP", "pdf": "PDF"}[ext]
    buf = io.BytesIO()
    opts = {"quality": 95} if fmt in ("JPEG", "WEBP") else {}
    if fmt == "PDF":
        opts = {"resolution": 150}
    img.save(buf, format=fmt, **opts)
    return buf.getvalue()


# ── Generazione ───────────────────────────────────────────────────────────────


def genera(cartella: Path, grande: bool = False) -> dict[str, dict]:
    """Scrive i file nella cartella e restituisce {nome_file: esito_atteso}."""
    cartella.mkdir(parents=True, exist_ok=True)
    attesi: dict[str, dict] = {}

    for f in FATTURE_PDF:
        (cartella / f.file).write_bytes(_pdf_testuale(f.righe_testo()))
        attesi[f.file] = f.attesi()

    for nome, f in FATTURE_IMMAGINE:
        (cartella / nome).write_bytes(_salva_immagine(_immagine(f.righe_testo()), nome))
        attesi[nome] = {**f.attesi(), "richiede_tesseract": True}

    # ── Casi negativi ─────────────────────────────────────────────────────────
    (cartella / "20_senza_dati_fiscali.pdf").write_bytes(_pdf_testuale([
        "Verbale riunione di reparto del 12 marzo.",
        "Il documento non contiene partita IVA ne' codice fiscale,",
        "quindi deve finire nello stato 'revisione' per controllo manuale.",
    ]))
    attesi["20_senza_dati_fiscali.pdf"] = {"status_code": 201, "stato": "revisione"}

    (cartella / "21_pdf_corrotto.pdf").write_bytes(b"%PDF-1.4\n%%questo file e' danneggiato\n")
    attesi["21_pdf_corrotto.pdf"] = {"status_code": 201, "stato": "errore"}

    (cartella / "22_file_vuoto.pdf").write_bytes(b"")
    attesi["22_file_vuoto.pdf"] = {"status_code": 400}

    (cartella / "23_formato_non_supportato.txt").write_text(
        "Fattura n. 1 - P.IVA 01234567890 - Totale 100,00", encoding="utf-8")
    attesi["23_formato_non_supportato.txt"] = {"status_code": 415}

    if grande:
        (cartella / "24_troppo_grande.pdf").write_bytes(b"%PDF-1.4\n" + b"0" * (21 * 1024 * 1024))
        attesi["24_troppo_grande.pdf"] = {"status_code": 413}

    (cartella / "attesi.json").write_text(json.dumps(attesi, indent=2, ensure_ascii=False), encoding="utf-8")
    return attesi


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dest = Path(args[0]) if args else Path(__file__).resolve().parents[2] / "fatture_di_test"
    risultato = genera(dest, grande="--grande" in sys.argv)
    print(f"Generati {len(risultato)} file in {dest}")
    for nome, att in risultato.items():
        print(f"  {nome:38s} -> HTTP {att['status_code']}  {att.get('stato', '')}")
