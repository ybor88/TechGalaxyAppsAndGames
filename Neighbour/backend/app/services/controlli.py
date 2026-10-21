"""
Controlli automatici sui contenuti (§8, §11). Sono regole trasparenti e deterministiche,
non profilazione: proteggono la community e ricordano i canali di emergenza.

© Roberto Di Flumeri
"""

import hashlib
import re
import unicodedata

AVVISO_EMERGENZA = (
    "Neighbour non sostituisce i canali di emergenza. Se c'è un pericolo immediato chiama il 112 "
    "(Numero Unico di Emergenza)."
)

# frasi che indicano un'emergenza in corso: il contenuto viene comunque accettato
# (è una segnalazione utile ai vicini), ma l'app mostra subito l'avviso del 112
_EMERGENZA = re.compile(
    r"\b(incendio|fiamme|fumo dalla|fuga di gas|odore di gas|incidente grave|ferit[oiae]|svenut[oa]|"
    r"aggression[ei]|rapina|furto in corso|sta male|non respira|infarto|allagamento|crollo|annegando)\b",
    re.IGNORECASE,
)

# dati che non devono comparire in un contenuto pubblico: li si condivide solo in chat
_TELEFONO = re.compile(r"(?<!\d)(?:\+39[\s.-]?)?3\d{2}[\s.-]?\d{6,7}(?!\d)")
_EMAIL = re.compile(r"[\w.+-]+@[\w-]+\.[\w.]+")
_INDIRIZZO = re.compile(
    r"\b(via|viale|piazza|piazzale|corso|largo|vicolo)\s+[A-ZÀ-Üa-zà-ü' ]{2,40}\s*,?\s*\d{1,4}\b", re.IGNORECASE
)


def emergenza(testo: str) -> bool:
    return bool(_EMERGENZA.search(testo))


def dati_personali(testo: str) -> list[str]:
    """Elenco dei dati personali trovati in un testo destinato a essere pubblico."""
    trovati = []
    if _TELEFONO.search(testo):
        trovati.append("un numero di telefono")
    if _EMAIL.search(testo):
        trovati.append("un indirizzo email")
    if _INDIRIZZO.search(testo):
        trovati.append("un indirizzo con numero civico")
    return trovati


def normalizza(testo: str) -> str:
    t = unicodedata.normalize("NFKD", testo.lower())
    t = "".join(c for c in t if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]+", " ", t).strip()


def impronta(titolo: str, descrizione: str) -> str:
    """Impronta del contenuto per riconoscere pubblicazioni ripetute dello stesso autore."""
    return hashlib.sha256(f"{normalizza(titolo)}|{normalizza(descrizione)}".encode()).hexdigest()
