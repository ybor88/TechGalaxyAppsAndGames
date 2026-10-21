"""
Servizi locali (F07): schede di professionisti e attività, con distinzione chiara tra
"suggerito da un vicino" e "scheda gestita dal professionista".

© Roberto Di Flumeri
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app import geo
from app.database import get_db
from app.models import Area, Service, ServiceRecommendation, User
from app.schemas import ConsiglioIn, ServizioIn
from app.security import audit, controlla_rate, utente_corrente
from app.services import controlli
from app.services.vicinato import archivia_segnalazioni, area_di, non_trovato, posizione_pubblica, traccia, utente_pubblico, utenti_bloccati

router = APIRouter(prefix="/v1/services", tags=["Servizi locali"])
ETICHETTE = {"suggerito": "Suggerito da un vicino", "professionale": "Scheda del professionista"}


async def servizio_dict(db: AsyncSession, sv: Service, user: User, area: Area | None) -> dict:
    consigli = (await db.execute(select(ServiceRecommendation).where(ServiceRecommendation.servizio_id == sv.id)
                                 .order_by(ServiceRecommendation.created_at.desc()))).scalars().all()
    return {
        "id": sv.id, "nome": sv.nome, "categoria": sv.categoria, "descrizione": sv.descrizione,
        "tipo_scheda": sv.tipo_scheda, "etichetta_scheda": ETICHETTE[sv.tipo_scheda], "contatto": sv.contatto,
        "zona": sv.zona, "posizione": posizione_pubblica(sv.lat, sv.lon, area),
        "autore": utente_pubblico(await db.get(User, sv.autore_id)), "mio": sv.autore_id == user.id,
        "consigli": len(consigli), "consigliato_da_me": any(c.user_id == user.id for c in consigli),
        "commenti": [{"testo": c.commento, "autore": utente_pubblico(await db.get(User, c.user_id)), "created_at": c.created_at}
                     for c in consigli if c.commento][:10],
        "created_at": sv.created_at,
    }


@router.get("")
async def elenco(categoria: str | None = None, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    area = await area_di(db, user)
    prefissi = geo.prefissi_ricerca(area.lat, area.lon)
    qy = select(Service).where(or_(*[Service.geohash.like(f"{p}%") for p in prefissi]), Service.rimosso.is_(False))
    if categoria:
        qy = qy.where(Service.categoria == categoria)
    bloccati = await utenti_bloccati(db, user.id)
    raggio = max(area.raggio_m, 3000)
    out = []
    for sv in (await db.execute(qy)).scalars():
        if sv.autore_id not in bloccati and geo.distanza_m(area.lat, area.lon, sv.lat, sv.lon) <= raggio:
            out.append(await servizio_dict(db, sv, user, area))
    return sorted(out, key=lambda s: (-s["consigli"], s["nome"].lower()))


@router.post("", status_code=status.HTTP_201_CREATED)
async def crea(dati: ServizioIn, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    controlla_rate(f"servizi:{user.id}", 5, 86400, "Puoi aggiungere al massimo 5 servizi al giorno")
    area = await area_di(db, user)
    if dati.tipo_scheda == "professionale" and user.tipo_profilo != "professionale":
        raise HTTPException(status.HTTP_403_FORBIDDEN,
                            "Solo un profilo professionale può creare la scheda della propria attività: "
                            "puoi suggerire il servizio come vicino")
    # il campo «Contatto» è il recapito pubblico dell'attività (telefono, sito, email del negozio) ed è ammesso;
    # nella descrizione invece non devono comparire recapiti personali
    trovati = controlli.dati_personali(dati.descrizione)
    if trovati:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY,
                            f"La descrizione contiene {', '.join(trovati)}: usa il campo «Contatto» per i recapiti dell'attività")
    simile = (await db.execute(select(Service).where(func.lower(Service.nome) == dati.nome.lower(),
                                                     Service.rimosso.is_(False)))).scalars().all()
    for s in simile:
        if geo.distanza_m(area.lat, area.lon, s.lat, s.lon) < 1500:
            raise HTTPException(status.HTTP_409_CONFLICT, "Questo servizio è già presente in zona: consiglialo invece di aggiungerlo")
    lat, lon = (dati.lat, dati.lon) if dati.lat is not None else (area.lat, area.lon)
    gh, lat, lon = geo.approssima(lat, lon, geo.PRECISIONE_LUOGO)
    sv = Service(autore_id=user.id, nome=dati.nome, categoria=dati.categoria, descrizione=dati.descrizione,
                 tipo_scheda=dati.tipo_scheda, contatto=dati.contatto, zona=dati.zona, geohash=gh, lat=lat, lon=lon)
    db.add(sv)
    await traccia(db, user, f"servizio_{dati.tipo_scheda}")
    await db.commit()
    return await servizio_dict(db, sv, user, area)


@router.get("/{servizio_id}")
async def dettaglio(servizio_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    sv = await db.get(Service, servizio_id)
    if sv is None or (sv.rimosso and user.ruolo != "moderatore") or sv.autore_id in await utenti_bloccati(db, user.id):
        raise non_trovato("Servizio")
    area = (await db.execute(select(Area).where(Area.user_id == user.id))).scalar_one_or_none()
    return await servizio_dict(db, sv, user, area)


@router.post("/{servizio_id}/consigli")
async def consiglia(servizio_id: str, dati: ConsiglioIn, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    sv = await db.get(Service, servizio_id)
    if sv is None or sv.rimosso:
        raise non_trovato("Servizio")
    if sv.autore_id == user.id and sv.tipo_scheda == "professionale":
        raise HTTPException(status.HTTP_409_CONFLICT, "Non puoi consigliare la tua attività")
    if dati.commento and controlli.dati_personali(dati.commento):
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Il commento non deve contenere recapiti personali")
    c = (await db.execute(select(ServiceRecommendation).where(
        ServiceRecommendation.servizio_id == sv.id, ServiceRecommendation.user_id == user.id))).scalar_one_or_none()
    if c is None:
        db.add(ServiceRecommendation(servizio_id=sv.id, user_id=user.id, commento=dati.commento))
    else:
        c.commento = dati.commento
    await db.commit()
    return await servizio_dict(db, sv, user, await area_di(db, user))


@router.delete("/{servizio_id}/consigli")
async def ritira_consiglio(servizio_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    c = (await db.execute(select(ServiceRecommendation).where(
        ServiceRecommendation.servizio_id == servizio_id, ServiceRecommendation.user_id == user.id))).scalar_one_or_none()
    if c:
        await db.delete(c)
        await db.commit()
    sv = await db.get(Service, servizio_id)
    if sv is None:
        raise non_trovato("Servizio")
    return await servizio_dict(db, sv, user, await area_di(db, user))


@router.delete("/{servizio_id}", status_code=status.HTTP_204_NO_CONTENT)
async def elimina(servizio_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    sv = await db.get(Service, servizio_id)
    if sv is None or sv.autore_id != user.id:
        raise non_trovato("Servizio")
    await db.delete(sv)
    await archivia_segnalazioni(db, "Servizio eliminato dall'autore", oggetto_id=servizio_id)
    audit(db, user.id, "servizio_eliminato", servizio_id)
    await db.commit()


