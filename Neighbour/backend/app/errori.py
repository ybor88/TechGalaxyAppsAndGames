"""
Messaggi di validazione in italiano: sostituiscono i testi inglesi generati da Pydantic
mantenendo la stessa struttura della risposta 422 (lista "detail").

© Roberto Di Flumeri
"""

from fastapi import Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

# Nomi dei campi come li vede l'utente
CAMPI = {
    "email": "Email", "password": "Password", "nuova_password": "Nuova password",
    "password_attuale": "Password attuale", "alias": "Nome visibile", "bio": "Presentazione",
    "titolo": "Titolo", "descrizione": "Descrizione", "testo": "Testo", "categoria": "Categoria",
    "tipo": "Tipo", "scadenza": "Scadenza", "disponibilita": "Disponibilità", "inizio": "Inizio",
    "fine": "Fine", "capienza": "Capienza", "luogo": "Luogo", "nome": "Nome", "motivo": "Motivo",
    "dettagli": "Dettagli", "raggio_m": "Raggio", "lat": "Posizione", "lon": "Posizione",
    "nome_area": "Quartiere", "commento": "Commento", "esito": "Esito", "messaggio": "Messaggio",
    "quiet_inizio": "Inizio silenzio", "quiet_fine": "Fine silenzio", "contatto": "Contatto",
}


def _campo(loc) -> str:
    for parte in reversed(list(loc or [])):
        if isinstance(parte, str) and parte not in ("body", "query", "path"):
            return CAMPI.get(parte, parte.replace("_", " ").capitalize())
    return ""


def messaggio_italiano(err: dict) -> str:
    tipo = err.get("type", "")
    ctx = err.get("ctx") or {}
    campo = _campo(err.get("loc"))
    nel = f"{campo}: " if campo else ""

    if tipo == "missing":
        return f"{campo or 'Un campo obbligatorio'} è obbligatorio" if campo else "Manca un campo obbligatorio"
    if tipo == "string_too_short":
        n = ctx.get("min_length", 1)
        return f"{nel}inserisci almeno {n} caratteri" if n > 1 else f"{campo or 'Il campo'} non può essere vuoto"
    if tipo == "string_too_long":
        return f"{nel}massimo {ctx.get('max_length')} caratteri"
    if tipo == "too_short":
        return f"{nel}servono almeno {ctx.get('min_length')} elementi"
    if tipo == "too_long":
        return f"{nel}massimo {ctx.get('max_length')} elementi"
    if tipo in ("greater_than_equal", "greater_than"):
        return f"{nel}il valore minimo è {ctx.get('ge', ctx.get('gt'))}"
    if tipo in ("less_than_equal", "less_than"):
        return f"{nel}il valore massimo è {ctx.get('le', ctx.get('lt'))}"
    if tipo in ("int_parsing", "int_type", "float_parsing", "int_from_float"):
        return f"{nel}inserisci un numero valido"
    if tipo in ("bool_parsing", "bool_type"):
        return f"{nel}valore non valido"
    if tipo in ("string_type",):
        return f"{nel}inserisci un testo"
    if tipo in ("literal_error", "enum"):
        return f"{nel}valore non ammesso"
    if tipo == "value_error":
        msg = str(err.get("msg", "")).removeprefix("Value error, ")
        if "email" in msg.lower() and ("valid" in msg.lower() or "@" in msg):
            return "Inserisci un indirizzo email valido"
        return msg
    if tipo in ("json_invalid", "model_type", "model_attributes_type", "dict_type", "list_type"):
        return "Dati inviati non validi"
    return f"{nel}valore non valido" if campo else "Dati non validi"


async def gestore_validazione(_request: Request, exc: RequestValidationError) -> JSONResponse:
    dettagli = [{"loc": list(e.get("loc", [])), "type": e.get("type"), "msg": messaggio_italiano(e)} for e in exc.errors()]
    return JSONResponse(status_code=422, content={"detail": dettagli})
