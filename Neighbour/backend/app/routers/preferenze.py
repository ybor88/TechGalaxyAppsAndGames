"""
Area di quartiere, preferenze di notifica e privacy, consensi versionati e blocchi.
PATCH /v1/preferences aggiorna notifiche e privacy insieme (specifica §6.1).

© Roberto Di Flumeri
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app import geo
from app.config import CONSENT_VERSION
from app.database import get_db
from app.models import Area, Block, Consent, Notification, User
from app.schemas import AreaIn, PreferenzeIn
from app.security import FINALITA, OBBLIGATORI, audit, consenso_attivo, registra_consenso, utente_corrente
from app.services.notifiche import preferenze
from app.services.vicinato import non_trovato, utente_pubblico

router = APIRouter(prefix="/v1", tags=["Area e preferenze"])


def area_dict(a: Area) -> dict:
    return {"nome": a.nome, "lat": a.lat, "lon": a.lon, "raggio_m": a.raggio_m, "geohash": a.geohash}


@router.get("/area")
async def leggi_area(user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    a = (await db.execute(select(Area).where(Area.user_id == user.id))).scalar_one_or_none()
    return area_dict(a) if a else None


@router.put("/area")
async def imposta_area(dati: AreaIn, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    if dati.da_gps and not await consenso_attivo(db, user.id, "geolocalizzazione"):
        raise HTTPException(status.HTTP_403_FORBIDDEN,
                            "Per usare la posizione del dispositivo attiva il consenso alla geolocalizzazione")
    gh, lat, lon = geo.approssima(dati.lat, dati.lon, geo.PRECISIONE_AREA)  # solo il centro della cella
    a = (await db.execute(select(Area).where(Area.user_id == user.id))).scalar_one_or_none()
    if a is None:
        a = Area(user_id=user.id, nome=dati.nome, geohash=gh, lat=lat, lon=lon, raggio_m=dati.raggio_m)
        db.add(a)
    else:
        a.nome, a.geohash, a.lat, a.lon, a.raggio_m = dati.nome, gh, lat, lon, dati.raggio_m
    audit(db, user.id, "area_impostata", dettagli=f"raggio {dati.raggio_m} m")
    await db.commit()
    return area_dict(a)


@router.get("/preferences")
async def leggi_preferenze(user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    p = await preferenze(db, user.id)
    await db.commit()
    return {
        "notifiche": {"tipi": p.tipi, "categorie": p.categorie, "raggio_m": p.raggio_m, "frequenza": p.frequenza,
                      "quiet_inizio": p.quiet_inizio, "quiet_fine": p.quiet_fine},
        "accetta_messaggi": user.accetta_messaggi,
        "consensi": {f: await consenso_attivo(db, user.id, f) for f in FINALITA},
    }


@router.patch("/preferences")
async def aggiorna_preferenze(dati: PreferenzeIn, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    if dati.consensi:
        for finalita, concesso in dati.consensi.items():
            if finalita not in FINALITA:
                raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, f"Finalità sconosciuta: {finalita}")
            if finalita in OBBLIGATORI and not concesso:
                raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY,
                                    "Il trattamento dei dati è necessario: per revocarlo cancella l'account")
            if await consenso_attivo(db, user.id, finalita) != concesso:
                await registra_consenso(db, user.id, finalita, concesso)
                audit(db, user.id, "consenso", finalita, f"{'concesso' if concesso else 'revocato'} ({CONSENT_VERSION})")
                if finalita == "notifiche" and not concesso:
                    # revoca: gli avvisi non ancora mostrati non verranno più consegnati
                    await db.execute(delete(Notification).where(
                        Notification.user_id == user.id, Notification.stato.in_(("in_attesa", "raggruppata"))))
    if dati.notifiche:
        p = await preferenze(db, user.id)
        n = dati.notifiche
        if n.tipi is not None:
            p.tipi = n.tipi
        if n.categorie is not None:
            p.categorie = n.categorie
        if n.raggio_m is not None:
            p.raggio_m = n.raggio_m
        if n.frequenza is not None:
            p.frequenza = n.frequenza
        if n.senza_silenzio:
            p.quiet_inizio = p.quiet_fine = None
        else:
            if n.quiet_inizio is not None:
                p.quiet_inizio = n.quiet_inizio
            if n.quiet_fine is not None:
                p.quiet_fine = n.quiet_fine
        p.versione_consenso = CONSENT_VERSION
    if dati.accetta_messaggi is not None:
        user.accetta_messaggi = dati.accetta_messaggi
    await db.commit()
    return await leggi_preferenze(user, db)


@router.get("/consents")
async def consensi(user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    registro = (await db.execute(
        select(Consent).where(Consent.user_id == user.id).order_by(Consent.timestamp.desc())
    )).scalars().all()
    return {
        "stato": [{"finalita": f, "descrizione": d, "obbligatorio": f in OBBLIGATORI,
                   "concesso": await consenso_attivo(db, user.id, f)} for f, d in FINALITA.items()],
        "registro": [{"finalita": c.finalita, "concesso": c.concesso, "versione": c.versione, "timestamp": c.timestamp,
                      "revocato_at": c.revocato_at} for c in registro],
    }


# ── Blocchi ──────────────────────────────────────────────────────────────────


@router.get("/blocks")
async def elenco_blocchi(user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    righe = (await db.execute(select(Block).where(Block.bloccante_id == user.id).order_by(Block.created_at.desc()))).scalars()
    out = []
    for b in righe:
        out.append({**utente_pubblico(await db.get(User, b.bloccato_id)), "bloccato_il": b.created_at})
    return out


@router.post("/blocks/{user_id}", status_code=status.HTTP_201_CREATED)
async def blocca(user_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    if user_id == user.id:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Non puoi bloccare te stesso")
    if await db.get(User, user_id) is None:
        raise non_trovato("Utente")
    esiste = (await db.execute(select(Block).where(Block.bloccante_id == user.id, Block.bloccato_id == user_id))).scalar_one_or_none()
    if esiste is None:  # idempotente
        db.add(Block(bloccante_id=user.id, bloccato_id=user_id))
        audit(db, user.id, "utente_bloccato", user_id)
        await db.commit()
    return {"bloccato": True}


@router.delete("/blocks/{user_id}", status_code=status.HTTP_204_NO_CONTENT)
async def sblocca(user_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    await db.execute(delete(Block).where(Block.bloccante_id == user.id, Block.bloccato_id == user_id))
    audit(db, user.id, "utente_sbloccato", user_id)
    await db.commit()
