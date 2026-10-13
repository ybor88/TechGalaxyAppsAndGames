"""Funzioni condivise dai router. © Roberto Di Flumeri"""

import hashlib
from datetime import date

from fastapi import HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import AnalyticsEvent, KnowledgeItem, TraitChange, TraitStatement, User
from app.security import consenso_attivo
from app.services.catalogo import QUESTIONARIO
from app.services.testo import Documento

# testo della domanda del questionario: dà contesto alla risposta nella ricerca
DOMANDE = {q["chiave"]: q["domanda"] for q in QUESTIONARIO}


async def risorsa_utente(db: AsyncSession, modello, risorsa_id: str, user: User, campo: str = "user_id"):
    """Restituisce la risorsa solo se appartiene all'utente (altrimenti 404, senza rivelarne l'esistenza)."""
    obj = await db.get(modello, risorsa_id)
    if obj is None or getattr(obj, campo) != user.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Elemento non trovato")
    return obj


def dichiarazione_dict(s: TraitStatement) -> dict:
    return {"id": s.id, "testo": s.testo, "categoria": s.categoria, "peso": s.peso, "fonte": s.fonte,
            "chiave_domanda": s.chiave_domanda, "visibilita": s.visibilita}


def valida_oggi(s: TraitStatement) -> bool:
    oggi = date.today()
    return (s.valido_da is None or s.valido_da <= oggi) and (s.valido_a is None or s.valido_a >= oggi)


async def documenti_autorizzati(
    db: AsyncSession,
    user_id: str,
    categorie: list[str] | None = None,
    solo_condivisibili: bool = False,
) -> list[Documento]:
    """Solo i dati che l'utente ha autorizzato per l'AI (e, se richiesto, marcati come condivisibili)."""
    q = select(TraitStatement).where(TraitStatement.user_id == user_id, TraitStatement.usa_in_ai.is_(True))
    if solo_condivisibili:
        q = q.where(TraitStatement.visibilita == "condivisibile")
    if categorie:
        q = q.where(TraitStatement.categoria.in_(categorie))
    docs = [
        Documento(id=s.id, tipo="dichiarazione", testo=s.testo, categoria=s.categoria, peso=s.peso,
                  titolo=DOMANDE.get(s.chiave_domanda or ""))
        for s in (await db.execute(q)).scalars() if valida_oggi(s)
    ]
    qk = select(KnowledgeItem).where(KnowledgeItem.user_id == user_id, KnowledgeItem.usa_in_ai.is_(True))
    if solo_condivisibili:
        qk = qk.where(KnowledgeItem.visibilita == "condivisibile")
    if categorie:
        qk = qk.where(KnowledgeItem.categoria.in_(categorie))
    for k in (await db.execute(qk)).scalars():
        docs.append(Documento(id=k.id, tipo="conoscenza", testo=k.contenuto, categoria=k.categoria, titolo=k.titolo))
    return docs


def registra_modifica(db: AsyncSession, user_id: str, s: TraitStatement, azione: str, prima: dict | None) -> None:
    db.add(TraitChange(
        user_id=user_id, statement_id=s.id, azione=azione, categoria=s.categoria,
        prima=prima, dopo=None if azione == "eliminata" else dichiarazione_dict(s),
    ))


async def traccia(db: AsyncSession, user: User, evento: str) -> None:
    """Statistica d'uso pseudonima, registrata solo se l'utente ha dato il consenso analytics."""
    if await consenso_attivo(db, user.id, "analytics"):
        pseudo = hashlib.sha256(f"ghostme:{user.id}".encode()).hexdigest()
        db.add(AnalyticsEvent(utente_pseudo=pseudo, evento=evento))
