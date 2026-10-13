"""
Errori applicativi comuni, tradotti in risposte HTTP da main.py.

SQLite non applica i vincoli di chiave esterna: l'integrità dei riferimenti
(cliente di una fattura, conto di una registrazione, ...) è verificata dai
servizi con gli helper qui sotto.

© Roberto Di Flumeri
"""

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession


class Conflitto(ValueError):
    """Operazione non consentita dallo stato dei dati (HTTP 409)."""


class RiferimentoNonValido(ValueError):
    """Il payload fa riferimento a un record inesistente (HTTP 422)."""


async def verifica_esiste(db: AsyncSession, modello, record_id: int | None, etichetta: str) -> None:
    """Solleva RiferimentoNonValido se record_id è valorizzato ma il record non esiste."""
    if record_id is None:
        return
    if await db.get(modello, record_id) is None:
        raise RiferimentoNonValido(f"{etichetta} {record_id} non trovato/a")


async def conta_riferimenti(db: AsyncSession, colonna, record_id: int) -> int:
    """Numero di righe in cui `colonna` punta a record_id."""
    return (await db.execute(select(func.count()).where(colonna == record_id))).scalar_one()


async def blocca_se_in_uso(db: AsyncSession, record_id: int, cosa: str, riferimenti: list[tuple]) -> None:
    """
    riferimenti: [(colonna, descrizione_plurale), ...]
    Solleva Conflitto elencando i dati collegati che impediscono l'eliminazione.
    """
    in_uso = []
    for colonna, descrizione in riferimenti:
        n = await conta_riferimenti(db, colonna, record_id)
        if n:
            in_uso.append(f"{n} {descrizione}")
    if in_uso:
        raise Conflitto(f"Impossibile eliminare {cosa}: è collegato/a a {', '.join(in_uso)}.")
