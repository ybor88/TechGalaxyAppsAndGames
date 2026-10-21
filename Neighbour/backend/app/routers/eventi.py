"""
Eventi locali (F03): luogo approssimativo, capienza, modalità di partecipazione,
lista d'attesa con promozione automatica e aggiornamenti ai partecipanti.
La partecipazione è idempotente (vincolo unico evento/utente).

© Roberto Di Flumeri
"""

from datetime import timedelta

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app import geo
from app.config import get_settings
from app.database import get_db
from app.models import Area, Event, EventParticipation, EventUpdate, Group, User, adesso
from app.schemas import AggiornamentoIn, DecisionePartecipanteIn, EventoIn, EventoUpdate
from app.security import audit, controlla_rate, utente_corrente
from app.services import controlli
from app.services.notifiche import diffondi, notifica
from app.services.vicinato import (
    area_di, membro_di, non_trovato, posizione_pubblica, traccia, utente_pubblico, utenti_bloccati, visibile_da,
)

router = APIRouter(prefix="/v1/events", tags=["Eventi"])
ATTIVI = ("confermato", "in_attesa", "lista_attesa")


async def conteggi(db: AsyncSession, evento_id: str) -> dict[str, int]:
    righe = (await db.execute(select(EventParticipation.stato, func.count()).where(
        EventParticipation.evento_id == evento_id).group_by(EventParticipation.stato))).all()
    return {s: n for s, n in righe}


def concluso(e: Event) -> bool:
    return (e.fine or e.inizio + timedelta(hours=3)) < adesso()


async def evento_dict(db: AsyncSession, e: Event, lettore: User, area: Area | None, dettaglio: bool = False) -> dict:
    c = await conteggi(db, e.id)
    mia = (await db.execute(select(EventParticipation.stato).where(
        EventParticipation.evento_id == e.id, EventParticipation.user_id == lettore.id))).scalar_one_or_none()
    organizzatore = e.creatore_id == lettore.id
    out = {
        "id": e.id, "genere": "evento", "tipo": "evento", "titolo": e.titolo, "descrizione": e.descrizione,
        "categoria": e.categoria, "inizio": e.inizio, "fine": e.fine, "luogo": e.luogo,
        "posizione": posizione_pubblica(e.lat, e.lon, area), "capienza": e.capienza, "modalita": e.modalita,
        "stato": e.stato, "concluso": concluso(e), "gruppo_id": e.gruppo_id, "created_at": e.created_at,
        "organizzatore": utente_pubblico(await db.get(User, e.creatore_id)), "mio": organizzatore,
        "confermati": c.get("confermato", 0), "in_lista_attesa": c.get("lista_attesa", 0),
        "posti_liberi": None if e.capienza is None else max(0, e.capienza - c.get("confermato", 0)),
        "mia_partecipazione": mia,
    }
    if dettaglio:
        out["aggiornamenti"] = [{"testo": u.testo, "created_at": u.created_at} for u in (await db.execute(
            select(EventUpdate).where(EventUpdate.evento_id == e.id).order_by(EventUpdate.created_at.desc()))).scalars()]
        if organizzatore:
            part = (await db.execute(select(EventParticipation).where(EventParticipation.evento_id == e.id)
                                     .order_by(EventParticipation.created_at))).scalars().all()
            out["partecipanti"] = [{**utente_pubblico(await db.get(User, p.user_id)), "stato": p.stato,
                                    "dal": p.created_at} for p in part]
        elif mia == "confermato":
            # chi partecipa vede gli altri confermati (solo alias), utile per organizzarsi
            conf = (await db.execute(select(EventParticipation.user_id).where(
                EventParticipation.evento_id == e.id, EventParticipation.stato == "confermato"))).scalars().all()
            out["partecipanti"] = [{**utente_pubblico(await db.get(User, uid)), "stato": "confermato"} for uid in conf]
    return out


async def carica_visibile(db: AsyncSession, evento_id: str, user: User) -> tuple[Event, Area | None]:
    e = await db.get(Event, evento_id)
    if e is None:
        raise non_trovato("Evento")
    area = (await db.execute(select(Area).where(Area.user_id == user.id))).scalar_one_or_none()
    if e.creatore_id == user.id or user.ruolo == "moderatore":
        return e, area
    if e.stato == "rimosso" or e.creatore_id in await utenti_bloccati(db, user.id):
        raise non_trovato("Evento")
    if e.gruppo_id:
        if not await membro_di(db, e.gruppo_id, user.id):
            raise non_trovato("Evento")
    elif area is None or visibile_da(area, e.lat, e.lon, e.raggio_m, get_settings().raggio_max_m) is None:
        raise non_trovato("Evento")
    return e, area


async def _promuovi_lista_attesa(db: AsyncSession, e: Event) -> None:
    """Libera posti: i primi in lista d'attesa vengono confermati in ordine di arrivo."""
    while True:
        c = await conteggi(db, e.id)
        if e.capienza is not None and c.get("confermato", 0) >= e.capienza:
            return
        primo = (await db.execute(select(EventParticipation).where(
            EventParticipation.evento_id == e.id, EventParticipation.stato == "lista_attesa")
            .order_by(EventParticipation.created_at).limit(1))).scalar_one_or_none()
        if primo is None:
            return
        primo.stato = "confermato"
        await db.flush()
        await notifica(db, primo.user_id, "evento", f"Si è liberato un posto: {e.titolo}",
                       "Sei passato dalla lista d'attesa ai partecipanti confermati.", f"/app/eventi/{e.id}")


async def _avvisa_partecipanti(db: AsyncSession, e: Event, titolo: str, testo: str) -> None:
    ids = (await db.execute(select(EventParticipation.user_id).where(
        EventParticipation.evento_id == e.id, EventParticipation.stato.in_(ATTIVI)))).scalars().all()
    for uid in ids:
        await notifica(db, uid, "evento", titolo, testo, f"/app/eventi/{e.id}")


@router.post("", status_code=status.HTTP_201_CREATED)
async def crea(dati: EventoIn, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    controlla_rate(f"scrittura:{user.id}", get_settings().rate_limit_scrittura_min)
    area = await area_di(db, user)
    if dati.inizio <= adesso():
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "L'evento deve iniziare nel futuro")
    if dati.gruppo_id:
        g = await db.get(Group, dati.gruppo_id)
        if g is None or g.rimosso or not await membro_di(db, g.id, user.id):
            raise HTTPException(status.HTTP_403_FORBIDDEN, "Puoi creare eventi solo nei gruppi di cui fai parte")
    for t in (dati.titolo, dati.descrizione, dati.luogo):
        trovati = controlli.dati_personali(t)
        if trovati:
            raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY,
                                f"Il testo contiene {', '.join(trovati)}: indica un luogo approssimativo e "
                                "condividi i dettagli in chat con i partecipanti.")
    lat, lon = (dati.lat, dati.lon) if dati.lat is not None else (area.lat, area.lon)
    gh, lat, lon = geo.approssima(lat, lon, geo.PRECISIONE_LUOGO)
    e = Event(creatore_id=user.id, titolo=dati.titolo, descrizione=dati.descrizione, categoria=dati.categoria,
              inizio=dati.inizio, fine=dati.fine, luogo=dati.luogo, geohash=gh, lat=lat, lon=lon,
              raggio_m=dati.raggio_m or area.raggio_m, capienza=dati.capienza, modalita=dati.modalita,
              gruppo_id=dati.gruppo_id)
    db.add(e)
    await db.flush()
    if not e.gruppo_id:
        await diffondi(db, autore_id=user.id, tipo="evento", categoria=e.categoria, lat=e.lat, lon=e.lon,
                       raggio_m=e.raggio_m, titolo=e.titolo, link=f"/app/eventi/{e.id}")
    await traccia(db, user, "evento_creato")
    await db.commit()
    return await evento_dict(db, e, user, area, dettaglio=True)


@router.get("/{evento_id}")
async def dettaglio(evento_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    e, area = await carica_visibile(db, evento_id, user)
    return await evento_dict(db, e, user, area, dettaglio=True)


@router.patch("/{evento_id}")
async def modifica(evento_id: str, dati: EventoUpdate, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    e = await db.get(Event, evento_id)
    if e is None or e.creatore_id != user.id:
        raise non_trovato("Evento")
    if e.stato != "pubblicato":
        raise HTTPException(status.HTTP_409_CONFLICT, "L'evento non è modificabile")
    cambi = []
    for campo, nome in (("titolo", "titolo"), ("descrizione", "descrizione"), ("inizio", "orario"), ("fine", "orario"),
                        ("luogo", "luogo")):
        v = getattr(dati, campo)
        if v is not None and v != getattr(e, campo):
            setattr(e, campo, v)
            if nome not in cambi:
                cambi.append(nome)
    if e.fine and e.fine <= e.inizio:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "La fine dell'evento deve essere successiva all'inizio")
    if dati.senza_limite or (dati.capienza is not None and dati.capienza != e.capienza):
        nuova = None if dati.senza_limite else dati.capienza
        confermati = (await conteggi(db, e.id)).get("confermato", 0)
        if nuova is not None and nuova < confermati:
            raise HTTPException(status.HTTP_409_CONFLICT,
                                f"Ci sono già {confermati} partecipanti confermati: la capienza non può essere inferiore")
        e.capienza = nuova
        cambi.append("capienza")
        await _promuovi_lista_attesa(db, e)
    if cambi:
        await _avvisa_partecipanti(db, e, f"Evento modificato: {e.titolo}", f"Cambiati: {', '.join(cambi)}.")
    await db.commit()
    area = await area_di(db, user)
    return await evento_dict(db, e, user, area, dettaglio=True)


@router.post("/{evento_id}/join")
async def partecipa(evento_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    e, area = await carica_visibile(db, evento_id, user)
    if e.creatore_id == user.id:
        raise HTTPException(status.HTTP_409_CONFLICT, "Sei l'organizzatore di questo evento")
    if e.stato != "pubblicato" or concluso(e):
        raise HTTPException(status.HTTP_409_CONFLICT, "L'evento non accetta più adesioni")
    esistente = (await db.execute(select(EventParticipation).where(
        EventParticipation.evento_id == e.id, EventParticipation.user_id == user.id))).scalar_one_or_none()
    if esistente and esistente.stato != "rifiutato":
        return await evento_dict(db, e, user, area, dettaglio=True)  # idempotente: nessun duplicato
    if esistente and esistente.stato == "rifiutato":
        raise HTTPException(status.HTTP_409_CONFLICT, "L'organizzatore non ha accettato la tua richiesta")
    if e.modalita == "su_approvazione":
        stato = "in_attesa"
    else:
        confermati = (await conteggi(db, e.id)).get("confermato", 0)
        stato = "lista_attesa" if e.capienza is not None and confermati >= e.capienza else "confermato"
    user_id = user.id
    db.add(EventParticipation(evento_id=e.id, user_id=user_id, stato=stato))
    try:
        await db.flush()
    except IntegrityError:  # richiesta doppia arrivata in parallelo: vale la prima, nessun duplicato
        await db.rollback()
        # il rollback scade gli oggetti della sessione: si ricaricano con query asincrone
        user = await db.get(User, user_id, populate_existing=True)
        e, area = await carica_visibile(db, evento_id, user)
        await db.refresh(e)
        return await evento_dict(db, e, user, area, dettaglio=True)
    testo = {"confermato": f"{user.alias} parteciperà.", "lista_attesa": f"{user.alias} è in lista d'attesa.",
             "in_attesa": f"{user.alias} chiede di partecipare: approva o rifiuta."}[stato]
    await notifica(db, e.creatore_id, "evento", f"Adesione a {e.titolo}", testo, f"/app/eventi/{e.id}")
    await traccia(db, user, "evento_adesione")
    await db.commit()
    return await evento_dict(db, e, user, area, dettaglio=True)


@router.delete("/{evento_id}/join")
async def lascia(evento_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    e, area = await carica_visibile(db, evento_id, user)
    p = (await db.execute(select(EventParticipation).where(
        EventParticipation.evento_id == e.id, EventParticipation.user_id == user.id))).scalar_one_or_none()
    if p is not None and p.stato != "rifiutato":
        await db.delete(p)
        await db.flush()
        await _promuovi_lista_attesa(db, e)
        await notifica(db, e.creatore_id, "evento", f"Rinuncia a {e.titolo}", f"{user.alias} non parteciperà più.",
                       f"/app/eventi/{e.id}")
        await db.commit()
    return await evento_dict(db, e, user, area, dettaglio=True)


@router.post("/{evento_id}/partecipanti/{user_id}")
async def decidi(evento_id: str, user_id: str, dati: DecisionePartecipanteIn, user: User = Depends(utente_corrente),
                 db: AsyncSession = Depends(get_db)):
    e = await db.get(Event, evento_id)
    if e is None or e.creatore_id != user.id:
        raise non_trovato("Evento")
    p = (await db.execute(select(EventParticipation).where(
        EventParticipation.evento_id == e.id, EventParticipation.user_id == user_id))).scalar_one_or_none()
    if p is None:
        raise non_trovato("Partecipante")
    if dati.stato == "rifiutato":
        era_confermato = p.stato == "confermato"
        p.stato = "rifiutato"
        await db.flush()
        if era_confermato:
            await _promuovi_lista_attesa(db, e)
        await notifica(db, user_id, "evento", f"Richiesta non accettata: {e.titolo}",
                       "L'organizzatore non ha confermato la tua partecipazione.", f"/app/eventi/{e.id}")
    elif p.stato in ("in_attesa", "lista_attesa", "rifiutato"):
        confermati = (await conteggi(db, e.id)).get("confermato", 0)
        if e.capienza is not None and confermati >= e.capienza:
            p.stato = "lista_attesa"
            testo = "Sei in lista d'attesa: l'evento è al completo."
        else:
            p.stato = "confermato"
            testo = "La tua partecipazione è confermata."
        await notifica(db, user_id, "evento", f"Aggiornamento su {e.titolo}", testo, f"/app/eventi/{e.id}")
    await db.commit()
    return await evento_dict(db, e, user, await area_di(db, user), dettaglio=True)


@router.post("/{evento_id}/aggiornamenti", status_code=status.HTTP_201_CREATED)
async def aggiorna(evento_id: str, dati: AggiornamentoIn, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    e = await db.get(Event, evento_id)
    if e is None or e.creatore_id != user.id:
        raise non_trovato("Evento")
    db.add(EventUpdate(evento_id=e.id, testo=dati.testo))
    await _avvisa_partecipanti(db, e, f"Novità su {e.titolo}", dati.testo)
    await db.commit()
    return await evento_dict(db, e, user, await area_di(db, user), dettaglio=True)


@router.post("/{evento_id}/annulla")
async def annulla(evento_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    e = await db.get(Event, evento_id)
    if e is None or e.creatore_id != user.id:
        raise non_trovato("Evento")
    if e.stato == "pubblicato":
        e.stato = "annullato"
        await _avvisa_partecipanti(db, e, f"Evento annullato: {e.titolo}", "L'organizzatore ha annullato l'evento.")
        audit(db, user.id, "evento_annullato", e.id)
        await db.commit()
    return await evento_dict(db, e, user, await area_di(db, user), dettaglio=True)


@router.get("")
async def miei_e_partecipati(user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    """Eventi che organizzo o a cui partecipo (quelli del quartiere sono nel feed)."""
    area = (await db.execute(select(Area).where(Area.user_id == user.id))).scalar_one_or_none()
    ids_part = select(EventParticipation.evento_id).where(EventParticipation.user_id == user.id,
                                                          EventParticipation.stato.in_(ATTIVI))
    righe = (await db.execute(select(Event).where((Event.creatore_id == user.id) | Event.id.in_(ids_part))
                              .order_by(Event.inizio))).scalars().all()
    return [await evento_dict(db, e, user, area) for e in righe if e.stato != "rimosso" or e.creatore_id == user.id]
