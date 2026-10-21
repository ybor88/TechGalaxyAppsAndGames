"""
Regole di vicinato condivise dai router: area obbligatoria, visibilità geografica,
blocchi reciproci, controllo di proprietà e rappresentazione pubblica dei dati.

© Roberto Di Flumeri
"""

import hashlib

from fastapi import HTTPException, status
from sqlalchemy import or_, select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app import geo
from app.models import AnalyticsEvent, Area, Block, GroupMember, Report, User, adesso
from app.security import consenso_attivo


async def area_di(db: AsyncSession, user: User) -> Area:
    area = (await db.execute(select(Area).where(Area.user_id == user.id))).scalar_one_or_none()
    if area is None:
        raise HTTPException(status.HTTP_409_CONFLICT, "Scegli prima il tuo quartiere")
    return area


async def utenti_bloccati(db: AsyncSession, user_id: str) -> set[str]:
    """Utenti che ho bloccato o che mi hanno bloccato: i blocchi valgono in entrambe le direzioni."""
    righe = (await db.execute(
        select(Block.bloccante_id, Block.bloccato_id).where(or_(Block.bloccante_id == user_id, Block.bloccato_id == user_id))
    )).all()
    return {b if a == user_id else a for a, b in righe}


async def bloccati_tra(db: AsyncSession, a: str, b: str) -> bool:
    return b in await utenti_bloccati(db, a)


def visibile_da(area: Area, lat: float, lon: float, raggio_contenuto: int, raggio_lettore: int | None = None) -> float | None:
    """Distanza se il contenuto è visibile dall'area del lettore, altrimenti None.
    Valgono entrambi i limiti: il raggio scelto da chi pubblica e quello di chi legge."""
    d = geo.distanza_m(area.lat, area.lon, lat, lon)
    limite = min(raggio_contenuto, raggio_lettore or area.raggio_m)
    return d if d <= limite else None


async def membro_di(db: AsyncSession, gruppo_id: str, user_id: str) -> GroupMember | None:
    return (await db.execute(
        select(GroupMember).where(GroupMember.gruppo_id == gruppo_id, GroupMember.user_id == user_id)
    )).scalar_one_or_none()


def non_trovato(cosa: str = "Elemento") -> HTTPException:
    return HTTPException(status.HTTP_404_NOT_FOUND, f"{cosa} non trovato")


def utente_pubblico(u: User | None) -> dict | None:
    """Ciò che un vicino vede di un altro utente: alias e tipo di profilo, nessun dato di contatto."""
    if u is None:
        return {"id": None, "alias": "Utente eliminato", "tipo_profilo": "privato", "attivita": None}
    return {"id": u.id, "alias": u.alias, "tipo_profilo": u.tipo_profilo, "attivita": u.attivita}


def posizione_pubblica(lat: float, lon: float, area: Area | None) -> dict:
    """Centro della cella approssimata e distanza arrotondata dal quartiere di chi guarda."""
    out = {"lat": lat, "lon": lon}
    if area is not None:
        out["distanza"] = geo.distanza_leggibile(geo.distanza_m(area.lat, area.lon, lat, lon))
    return out


async def traccia(db: AsyncSession, user: User, evento: str) -> None:
    """Statistica d'uso pseudonima, registrata solo con il consenso analytics."""
    if await consenso_attivo(db, user.id, "analytics"):
        db.add(AnalyticsEvent(utente_pseudo=pseudonimo(user.id), evento=evento))


def pseudonimo(user_id: str) -> str:
    return hashlib.sha256(f"neighbour:{user_id}".encode()).hexdigest()


async def archivia_segnalazioni(db: AsyncSession, nota: str, *, oggetto_id: str | None = None,
                                utente_id: str | None = None) -> None:
    """Chi elimina un contenuto o l'account non lascia segnalazioni "orfane" in coda:
    vengono archiviate con una nota (l'evidenza resta) e gli eventuali ricorsi si chiudono."""
    cond = Report.oggetto_id == oggetto_id if oggetto_id else Report.utente_segnalato_id == utente_id
    await db.execute(update(Report).where(cond, Report.stato == "aperta")
                     .values(stato="archiviata", nota_decisione=nota, risolta_at=adesso()))
    await db.execute(update(Report).where(cond, Report.ricorso_stato == "aperto").values(ricorso_stato="ritirato"))
