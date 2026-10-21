"""Gruppi di interesse locali (F08): sport, animali, genitori, hobby e iniziative. © Roberto Di Flumeri"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app import geo
from app.config import get_settings
from app.database import get_db
from app.models import Area, Event, Group, GroupMember, Post, User
from app.routers.eventi import evento_dict
from app.routers.posts import post_dict
from app.schemas import GruppoIn
from app.security import audit, controlla_rate, utente_corrente
from app.services.vicinato import area_di, membro_di, non_trovato, posizione_pubblica, traccia, utente_pubblico, utenti_bloccati

router = APIRouter(prefix="/v1/groups", tags=["Gruppi"])


async def gruppo_dict(db: AsyncSession, g: Group, user: User, area: Area | None) -> dict:
    membri = (await db.execute(select(func.count()).select_from(GroupMember).where(GroupMember.gruppo_id == g.id))).scalar_one()
    io = await membro_di(db, g.id, user.id)
    return {"id": g.id, "nome": g.nome, "descrizione": g.descrizione, "categoria": g.categoria, "regole": g.regole,
            "membri": membri, "mio_ruolo": io.ruolo if io else None, "posizione": posizione_pubblica(g.lat, g.lon, area),
            "creatore": utente_pubblico(await db.get(User, g.creatore_id)) if g.creatore_id else None,
            "created_at": g.created_at}


async def _carica(db: AsyncSession, gruppo_id: str, user: User) -> Group:
    g = await db.get(Group, gruppo_id)
    if g is None or (g.rimosso and user.ruolo != "moderatore"):
        raise non_trovato("Gruppo")
    return g


@router.get("")
async def vicini(categoria: str | None = None, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    area = await area_di(db, user)
    prefissi = geo.prefissi_ricerca(area.lat, area.lon)
    qy = select(Group).where(or_(*[Group.geohash.like(f"{p}%") for p in prefissi]), Group.rimosso.is_(False))
    if categoria:
        qy = qy.where(Group.categoria == categoria)
    raggio = max(area.raggio_m, 3000)  # i gruppi raccolgono il quartiere allargato
    bloccati = await utenti_bloccati(db, user.id)
    out = []
    for g in (await db.execute(qy.order_by(Group.created_at.desc()))).scalars():
        if g.creatore_id not in bloccati and geo.distanza_m(area.lat, area.lon, g.lat, g.lon) <= raggio:
            out.append(await gruppo_dict(db, g, user, area))
    return out


@router.post("", status_code=status.HTTP_201_CREATED)
async def crea(dati: GruppoIn, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    controlla_rate(f"gruppi:{user.id}", 3, 86400, "Puoi creare al massimo 3 gruppi al giorno")
    area = await area_di(db, user)
    g = Group(creatore_id=user.id, nome=dati.nome, descrizione=dati.descrizione, categoria=dati.categoria,
              regole=dati.regole, geohash=area.geohash, lat=area.lat, lon=area.lon)
    db.add(g)
    await db.flush()
    db.add(GroupMember(gruppo_id=g.id, user_id=user.id, ruolo="amministratore"))
    await traccia(db, user, "gruppo_creato")
    await db.commit()
    return await gruppo_dict(db, g, user, area)


@router.get("/{gruppo_id}")
async def dettaglio(gruppo_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    g = await _carica(db, gruppo_id, user)
    area = (await db.execute(select(Area).where(Area.user_id == user.id))).scalar_one_or_none()
    out = await gruppo_dict(db, g, user, area)
    if out["mio_ruolo"] or user.ruolo == "moderatore":
        bloccati = await utenti_bloccati(db, user.id)
        posts = (await db.execute(select(Post).options(selectinload(Post.foto)).where(
            Post.gruppo_id == g.id, Post.stato.in_(("pubblicato", "prenotato", "chiuso")))
            .order_by(Post.created_at.desc()).limit(100))).scalars().all()
        out["bacheca"] = [post_dict(p, await db.get(User, p.autore_id), user, area) for p in posts if p.autore_id not in bloccati]
        eventi = (await db.execute(select(Event).where(Event.gruppo_id == g.id, Event.stato == "pubblicato")
                                   .order_by(Event.inizio))).scalars().all()
        out["eventi"] = [await evento_dict(db, e, user, area) for e in eventi]
    return out


@router.post("/{gruppo_id}/join")
async def entra(gruppo_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    g = await _carica(db, gruppo_id, user)
    area = await area_di(db, user)
    if geo.distanza_m(area.lat, area.lon, g.lat, g.lon) > get_settings().raggio_max_m:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Il gruppo è fuori dalla tua zona")
    if g.creatore_id in await utenti_bloccati(db, user.id):
        raise non_trovato("Gruppo")
    if not await membro_di(db, g.id, user.id):  # idempotente
        db.add(GroupMember(gruppo_id=g.id, user_id=user.id))
        await traccia(db, user, "gruppo_iscrizione")
        await db.commit()
    return await gruppo_dict(db, g, user, area)


@router.delete("/{gruppo_id}/join")
async def esci(gruppo_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    g = await _carica(db, gruppo_id, user)
    io = await membro_di(db, g.id, user.id)
    if io:
        await db.delete(io)
        await db.flush()
        if io.ruolo == "amministratore":
            admin = (await db.execute(select(GroupMember).where(
                GroupMember.gruppo_id == g.id, GroupMember.ruolo == "amministratore"))).first()
            if admin is None:  # il gruppo non resta senza amministratore: subentra il membro più anziano
                piu_anziano = (await db.execute(select(GroupMember).where(GroupMember.gruppo_id == g.id)
                                                .order_by(GroupMember.created_at).limit(1))).scalar_one_or_none()
                if piu_anziano:
                    piu_anziano.ruolo = "amministratore"
        await db.commit()
    return await gruppo_dict(db, g, user, (await db.execute(select(Area).where(Area.user_id == user.id))).scalar_one_or_none())


@router.get("/{gruppo_id}/members")
async def membri(gruppo_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    g = await _carica(db, gruppo_id, user)
    if not await membro_di(db, g.id, user.id):
        raise HTTPException(status.HTTP_403_FORBIDDEN, "L'elenco dei membri è visibile solo a chi fa parte del gruppo")
    righe = (await db.execute(select(GroupMember).where(GroupMember.gruppo_id == g.id).order_by(GroupMember.created_at))).scalars()
    return [{**utente_pubblico(await db.get(User, m.user_id)), "ruolo": m.ruolo, "dal": m.created_at} for m in righe]


@router.post("/{gruppo_id}/posts/{post_id}/rimuovi", status_code=status.HTTP_204_NO_CONTENT)
async def rimuovi_messaggio(gruppo_id: str, post_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    """Gli amministratori del gruppo possono togliere un messaggio dalla bacheca."""
    io = await membro_di(db, gruppo_id, user.id)
    p = await db.get(Post, post_id)
    if p is None or p.gruppo_id != gruppo_id:
        raise non_trovato("Messaggio")
    if not io or (io.ruolo != "amministratore" and p.autore_id != user.id):
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Solo gli amministratori del gruppo possono rimuovere i messaggi")
    p.stato = "rimosso"
    audit(db, user.id, "gruppo_messaggio_rimosso", post_id, gruppo_id)
    await db.commit()
