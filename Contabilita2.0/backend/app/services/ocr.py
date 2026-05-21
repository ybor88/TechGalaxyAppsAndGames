"""
Servizio OCR Contabile — Funzionalità 3.

Flusso:
  1. Riceve i byte del file caricato (immagine o PDF).
  2. Estrae il testo:
       - Immagini (PNG/JPG/JPEG/TIFF/BMP/WEBP): Pillow + Tesseract.
       - PDF digitale: pypdf (estrazione testo nativa, zero dipendenze di sistema).
       - PDF scansionato: ogni pagina resa come immagine tramite pypdf+Pillow se
         il testo nativo è assente, poi Tesseract per l'OCR.
  3. Analizza il testo con regex ottimizzate per fatture italiane:
       - Partita IVA (11 cifre), Codice Fiscale (16 caratteri alfanumerici)
       - Numero documento, Data documento
       - Importo netto, IVA, Totale, Aliquota IVA
       - Ragione sociale del fornitore
  4. Persiste il risultato in tabella ocr_risultati.
"""

from __future__ import annotations

import io
import re
from datetime import date
from decimal import Decimal, InvalidOperation

from sqlalchemy import select, delete
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.ocr import OcrRisultato
from app.schemas.ocr import OcrRisultatoResponse

# ---------------------------------------------------------------------------
# Costanti / pattern regex
# ---------------------------------------------------------------------------

_RE_PIVA = re.compile(
    r"(?:P\.?\s*IVA|Partita\s+IVA|VAT\s*(?:n\.?|number)?|PI)[\s:.\-]*([0-9]{11})",
    re.IGNORECASE,
)

_RE_CF = re.compile(
    r"(?:C\.?\s*F\.?|Codice\s+Fiscale|CF)[\s:.\-]*"
    r"([A-Z]{6}[0-9LMNPQRSTUV]{2}[ABCDEHLMPRST][0-9LMNPQRSTUV]{2}"
    r"[A-Z][0-9LMNPQRSTUV]{3}[A-Z])\b",
    re.IGNORECASE,
)

_RE_NUM_DOC = re.compile(
    r"(?:Fattura\s+(?:n\.?°?|N\.?°?)|N\.?\s*°?\s*Fattura|Numero\s+Fattura"
    r"|Invoice\s+(?:n\.?|No\.?|Number)?)[\s:.\-]*([A-Z0-9][A-Z0-9/\-_]{0,30})",
    re.IGNORECASE,
)

# Formati data: DD/MM/YYYY, DD-MM-YYYY, YYYY-MM-DD, DD MM YYYY
_RE_DATA = re.compile(
    r"\b(\d{2})[/\-.](\d{2})[/\-.](\d{4})\b"
    r"|\b(\d{4})[/\-.](\d{2})[/\-.](\d{2})\b"
)

# Importo in formato italiano (1.234,56) o internazionale (1,234.56)
_NUM_IT = r"(?:[0-9]{1,3}(?:\.[0-9]{3})*,[0-9]{2}|[0-9]+,[0-9]{2})"
_NUM_EN = r"(?:[0-9]{1,3}(?:,[0-9]{3})*\.[0-9]{2}|[0-9]+\.[0-9]{2})"
_NUM = rf"(?:{_NUM_IT}|{_NUM_EN})"

_RE_TOTALE = re.compile(
    rf"(?:Totale\s+(?:fattura|documento|complessivo|a\s+pagare|dovuto)?|TOTALE|Total)[\s:€]*({_NUM})",
    re.IGNORECASE,
)
_RE_SUBTOTALE = re.compile(
    rf"(?:Imponibile|Subtotale|Totale\s+(?:imponibile|netto)|Sub[\s\-]?Total)[\s:€]*({_NUM})",
    re.IGNORECASE,
)
_RE_IVA_IMPORTO = re.compile(
    rf"(?:Totale\s+IVA|Importo\s+IVA|IVA\s+(?:totale)?)[\s:€]*({_NUM})",
    re.IGNORECASE,
)
_RE_ALIQUOTA = re.compile(
    r"(?:Aliquota\s+IVA|IVA)[\s:]*([0-9]{1,2})%"
    r"|([0-9]{1,2})%\s*IVA",
    re.IGNORECASE,
)

# Ragione sociale: prima riga non vuota del documento, spesso il nome del fornitore
_RE_FORNITORE = re.compile(
    r"(?:Fornitore|Emittente|Ditta|Società|Azienda|Ragione\s+Sociale)[\s:.\-]*(.+)",
    re.IGNORECASE,
)

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _parse_number(s: str) -> Decimal | None:
    """Converte stringa numerica italiana (1.234,56) o inglese (1,234.56) in Decimal."""
    if not s:
        return None
    s = s.strip()
    # Formato italiano: ha virgola come decimale
    if "," in s and ("." not in s or s.rfind(",") > s.rfind(".")):
        s = s.replace(".", "").replace(",", ".")
    else:
        s = s.replace(",", "")
    try:
        return Decimal(s)
    except InvalidOperation:
        return None


def _parse_date(testo: str) -> date | None:
    """Estrae la prima data valida dal testo."""
    for m in _RE_DATA.finditer(testo):
        g1, m1, a1, a2, m2, g2 = m.groups()
        try:
            if g1:
                return date(int(a1), int(m1), int(g1))
            else:
                return date(int(a2), int(m2), int(g2))
        except ValueError:
            continue
    return None


def _first_group(pattern: re.Pattern, testo: str) -> str | None:
    m = pattern.search(testo)
    if not m:
        return None
    for g in m.groups():
        if g:
            return g.strip()
    return None


# ---------------------------------------------------------------------------
# Estrazione testo
# ---------------------------------------------------------------------------

def _estrai_testo_immagine(data: bytes) -> str:
    """Usa Pillow + Tesseract per estrarre testo da un'immagine."""
    try:
        import pytesseract
        from PIL import Image

        img = Image.open(io.BytesIO(data))
        testo = pytesseract.image_to_string(img, lang="ita+eng")
        return testo
    except ImportError:
        raise RuntimeError(
            "pytesseract/Pillow non installati. Installa le dipendenze F3."
        )
    except Exception as exc:
        raise RuntimeError(f"Errore OCR immagine: {exc}") from exc


def _estrai_testo_pdf(data: bytes) -> str:
    """
    Estrae testo da un PDF.
    - Prima prova estrazione nativa (PDF digitale) con pypdf.
    - Se il testo è scarso (< 50 caratteri), tenta OCR pagina per pagina
      rasterizzando con Pillow (richiede pypdf >= 4.x con supporto rendering).
    """
    try:
        import pypdf
    except ImportError:
        raise RuntimeError("pypdf non installato. Installa le dipendenze F3.")

    reader = pypdf.PdfReader(io.BytesIO(data))
    testo_nativo = ""
    for page in reader.pages:
        testo_nativo += (page.extract_text() or "") + "\n"

    if len(testo_nativo.strip()) >= 50:
        return testo_nativo

    # PDF scansionato: prova OCR con pytesseract se disponibile
    try:
        import pytesseract
        from PIL import Image

        testo_ocr = ""
        for page in reader.pages:
            # pypdf >= 4.x consente di estrarre le immagini incorporate
            for img_obj in page.images:
                img = Image.open(io.BytesIO(img_obj.data))
                testo_ocr += pytesseract.image_to_string(img, lang="ita+eng") + "\n"
        if testo_ocr.strip():
            return testo_ocr
    except Exception:
        pass  # Tesseract non disponibile o nessuna immagine nel PDF

    return testo_nativo  # Restituisce quello che c'è (potrebbe essere vuoto)


# ---------------------------------------------------------------------------
# Parser italiano
# ---------------------------------------------------------------------------

def _parse_dati_fattura(testo: str) -> dict:
    """Estrae i campi contabili dal testo OCR."""
    result: dict = {
        "fornitore": None,
        "piva": None,
        "cf": None,
        "numero_documento": None,
        "data_documento": None,
        "importo_netto": None,
        "importo_iva": None,
        "importo_totale": None,
        "aliquota_iva": None,
    }

    # Partita IVA
    m_piva = _RE_PIVA.search(testo)
    if m_piva:
        result["piva"] = m_piva.group(1).strip()

    # Codice fiscale (solo se diverso dalla P.IVA già trovata)
    m_cf = _RE_CF.search(testo)
    if m_cf:
        cf_val = m_cf.group(1).strip().upper()
        if cf_val != result.get("piva"):
            result["cf"] = cf_val

    # Numero documento
    result["numero_documento"] = _first_group(_RE_NUM_DOC, testo)

    # Data documento
    result["data_documento"] = _parse_date(testo)

    # Importi
    totale_raw = _first_group(_RE_TOTALE, testo)
    subtotale_raw = _first_group(_RE_SUBTOTALE, testo)
    iva_raw = _first_group(_RE_IVA_IMPORTO, testo)

    result["importo_totale"] = _parse_number(totale_raw)
    result["importo_netto"] = _parse_number(subtotale_raw)
    result["importo_iva"] = _parse_number(iva_raw)

    # Aliquota IVA
    m_al = _RE_ALIQUOTA.search(testo)
    if m_al:
        val = m_al.group(1) or m_al.group(2)
        if val:
            result["aliquota_iva"] = Decimal(val)

    # Calcolo incrociato: se mancano alcuni valori, derivali dagli altri
    t = result["importo_totale"]
    n = result["importo_netto"]
    i = result["importo_iva"]
    if t and n and not i:
        result["importo_iva"] = t - n
    elif t and i and not n:
        result["importo_netto"] = t - i
    elif n and i and not t:
        result["importo_totale"] = n + i

    # Fornitore: cerca etichetta esplicita, poi fallback prima riga non vuota
    m_forn = _RE_FORNITORE.search(testo)
    if m_forn:
        result["fornitore"] = m_forn.group(1).strip()[:200]
    else:
        for line in testo.splitlines():
            line = line.strip()
            if len(line) > 3 and not line.startswith("http") and not re.match(r"^\d", line):
                result["fornitore"] = line[:200]
                break

    return result


# ---------------------------------------------------------------------------
# Servizio
# ---------------------------------------------------------------------------

class OcrService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def elabora(
        self,
        file_bytes: bytes,
        filename: str,
        content_type: str,
    ) -> tuple[OcrRisultato, list[str]]:
        """
        Elabora il file caricato ed esegue OCR + parsing.
        Restituisce il record OcrRisultato salvato e una lista di avvisi.
        """
        avvisi: list[str] = []
        testo = ""
        stato = "elaborato"
        errore = None

        ext = filename.rsplit(".", 1)[-1].lower() if "." in filename else ""
        is_pdf = content_type == "application/pdf" or ext == "pdf"
        is_img = content_type.startswith("image/") or ext in {"png", "jpg", "jpeg", "tiff", "tif", "bmp", "webp"}

        try:
            if is_pdf:
                testo = _estrai_testo_pdf(file_bytes)
            elif is_img:
                testo = _estrai_testo_immagine(file_bytes)
            else:
                stato = "errore"
                errore = f"Formato non supportato: {content_type or ext}. Usa PDF, PNG, JPG, TIFF."

            if stato != "errore" and len(testo.strip()) < 30:
                avvisi.append(
                    "Testo estratto molto breve. Verifica che il file sia leggibile "
                    "e che Tesseract OCR sia installato sul sistema."
                )

        except RuntimeError as exc:
            stato = "errore"
            errore = str(exc)

        dati = _parse_dati_fattura(testo) if testo else {
            "fornitore": None, "piva": None, "cf": None,
            "numero_documento": None, "data_documento": None,
            "importo_netto": None, "importo_iva": None,
            "importo_totale": None, "aliquota_iva": None,
        }

        if stato == "elaborato":
            campi_mancanti = [k for k, v in dati.items() if v is None]
            if campi_mancanti:
                avvisi.append(
                    f"Campi non rilevati automaticamente: {', '.join(campi_mancanti)}. "
                    "Compila manualmente prima di creare il documento."
                )
            if dati["piva"] is None and dati["cf"] is None:
                stato = "revisione"

        record = OcrRisultato(
            filename=filename,
            content_type=content_type,
            testo_estratto=testo or None,
            stato=stato,
            errore=errore,
            **dati,
        )
        self.db.add(record)
        await self.db.commit()
        await self.db.refresh(record)
        return record, avvisi

    async def list(self) -> list[OcrRisultato]:
        stmt = select(OcrRisultato).order_by(OcrRisultato.created_at.desc())
        result = await self.db.execute(stmt)
        return list(result.scalars().all())

    async def get(self, risultato_id: int) -> OcrRisultato | None:
        stmt = select(OcrRisultato).where(OcrRisultato.id == risultato_id)
        result = await self.db.execute(stmt)
        return result.scalar_one_or_none()

    async def delete(self, risultato_id: int) -> bool:
        record = await self.get(risultato_id)
        if not record:
            return False
        await self.db.execute(delete(OcrRisultato).where(OcrRisultato.id == risultato_id))
        await self.db.commit()
        return True

    async def collega_documento(self, risultato_id: int, documento_id: int) -> OcrRisultato | None:
        """Collega il risultato OCR a un documento già creato."""
        record = await self.get(risultato_id)
        if not record:
            return None
        record.documento_id = documento_id
        await self.db.commit()
        await self.db.refresh(record)
        return record
