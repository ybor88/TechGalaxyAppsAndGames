"""
Foto dei contenuti (§6 "File"): scansione e ricodifica dei caricamenti, URL firmati temporanei.

- Il file viene aperto e decodificato davvero: ciò che non è un'immagine viene rifiutato.
- L'immagine è ricodificata in JPEG senza metadati: i dati EXIF, compresa la posizione GPS
  dello scatto, non vengono mai salvati.
- Gli URL sono firmati con HMAC e scadono: funzionano come gli URL firmati di un object storage S3.

© Roberto Di Flumeri
"""

import hashlib
import hmac
import io
import time
from pathlib import Path

from fastapi import HTTPException, status
from PIL import Image, ImageOps, UnidentifiedImageError

from app.config import get_settings

LATO_MAX = 1600
FORMATI = {"JPEG", "PNG", "WEBP", "GIF"}
Image.MAX_IMAGE_PIXELS = 40_000_000  # protezione dalle "decompression bomb"


def cartella() -> Path:
    p = Path(get_settings().cartella_file)
    p.mkdir(parents=True, exist_ok=True)
    return p


def elabora(dati: bytes) -> tuple[bytes, int, int]:
    """Valida e ricodifica l'immagine. Restituisce (jpeg, larghezza, altezza)."""
    if len(dati) > get_settings().foto_max_mb * 1024 * 1024:
        raise HTTPException(status.HTTP_413_REQUEST_ENTITY_TOO_LARGE, f"La foto supera {get_settings().foto_max_mb} MB")
    try:
        with Image.open(io.BytesIO(dati)) as prova:
            formato = prova.format
            prova.verify()
        if formato not in FORMATI:
            raise UnidentifiedImageError(formato)
        img = Image.open(io.BytesIO(dati))
        img = ImageOps.exif_transpose(img)  # mantiene l'orientamento prima di togliere i metadati
        img = img.convert("RGB")
    except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError):
        raise HTTPException(status.HTTP_415_UNSUPPORTED_MEDIA_TYPE, "Il file non è un'immagine valida (JPEG, PNG, WEBP o GIF)")
    img.thumbnail((LATO_MAX, LATO_MAX))
    out = io.BytesIO()
    img.save(out, "JPEG", quality=85, optimize=True)  # nessun exif=: i metadati non vengono copiati
    return out.getvalue(), img.width, img.height


def _firma(file_id: str, scadenza: int) -> str:
    return hmac.new(get_settings().file_secret.encode(), f"{file_id}:{scadenza}".encode(), hashlib.sha256).hexdigest()[:32]


def url_firmato(file_id: str) -> str:
    scadenza = int(time.time()) + get_settings().url_file_minuti * 60
    return f"/v1/files/{file_id}?exp={scadenza}&sig={_firma(file_id, scadenza)}"


def firma_valida(file_id: str, scadenza: int, firma: str) -> bool:
    return scadenza >= time.time() and hmac.compare_digest(_firma(file_id, scadenza), firma)
