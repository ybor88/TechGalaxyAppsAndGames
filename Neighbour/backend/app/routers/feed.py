"""
Feed locale e mappa di quartiere (F01, §6.1 GET /v1/feed).

- Prefiltro server-side con i prefissi geohash della cella del lettore e delle 8 vicine,
  poi distanza tra i centri delle celle: valgono il raggio scelto da chi pubblica e
  quello scelto da chi legge.
- Esclusi gli utenti bloccati (in entrambe le direzioni), i contenuti scaduti e quelli dei gruppi.
- Cursor pagination stabile su (data di creazione, id).

© Roberto Di Flumeri
"""

import base64
import json
from datetime import datetime, timedelta
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import and_, or_, select, true
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app import geo
from app.config import get_settings
from app.database import get_db
from app.models import Event, Group, GroupMember, Post, Report, Service, User, adesso
from app.routers.eventi import concluso, evento_dict
from app.routers.posts import post_dict
from app.security import consenso_attivo, utente_corrente
from app.services.catalogo import TIPI_MERCATINO
from app.services.notifiche import preferenze
from app.services.vicinato import area_di, posizione_pubblica, utente_pubblico, utenti_bloccati

router = APIRouter(prefix="/v1", tags=["Feed e mappa"])
LOTTO = 200
PERIODI = {"oggi": 1, "settimana": 7, "mese": 30}


def _cursore(c: str | None) -> tuple[datetime, str] | None:
    if not c:
        return None
    try:
        d = json.loads(base64.urlsafe_b64decode(c.encode()).decode())
        return datetime.fromisoformat(d["t"]), d["i"]
    except (ValueError, KeyError, json.JSONDecodeError):
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Cursore non valido")


async def segnalati_da_me(db: AsyncSession, user_id: str) -> set[str]:
    """Ciò che ho segnalato sparisce subito dalla mia vista, in attesa della decisione della moderazione."""
    return set((await db.execute(select(Report.oggetto_id).where(
        Report.segnalante_id == user_id, Report.stato == "aperta", Report.oggetto_tipo.in_(("post", "evento", "servizio"))
    ))).scalars())


def _nuovo_cursore(t: datetime, i: str) -> str:
    return base64.urlsafe_b64encode(json.dumps({"t": t.isoformat(), "i": i}).encode()).decode()


def _prima_di(modello, cur):
    if cur is None:
        return true()
    t, i = cur
    return or_(modello.created_at < t, and_(modello.created_at == t, modello.id < i))


async def _interessi(db: AsyncSession, user: User) -> set[str]:
    """Categorie seguite (notifiche) e categorie dei propri gruppi: base del filtro "Per te"."""
    p = await preferenze(db, user.id)
    cat = set(p.categorie or [])
    righe = (await db.execute(select(Group.categoria).join(GroupMember, GroupMember.gruppo_id == Group.id)
                              .where(GroupMember.user_id == user.id))).scalars()
    return cat | set(righe)


@router.get("/feed")
async def feed(
    tipo: Literal["tutti", "mercatino", "offerta", "richiesta", "regalo", "scambio", "aiuto", "segnalazione", "evento"] = "tutti",
    categoria: str | None = None,
    raggio_m: int | None = Query(None, ge=300, le=5000),
    periodo: Literal["oggi", "settimana", "mese"] | None = None,
    q: str | None = Query(None, max_length=60),
    per_te: bool = False,
    cursore: str | None = None,
    limite: int = Query(20, ge=1, le=50),
    user: User = Depends(utente_corrente),
    db: AsyncSession = Depends(get_db),
):
    area = await area_di(db, user)
    raggio = raggio_m or area.raggio_m
    prefissi = geo.prefissi_ricerca(area.lat, area.lon)
    bloccati = await utenti_bloccati(db, user.id)
    nascosti = await segnalati_da_me(db, user.id)
    cur = _cursore(cursore)
    ora = adesso()
    interessi = None
    if per_te:
        if not await consenso_attivo(db, user.id, "profilazione"):
            raise HTTPException(status.HTTP_403_FORBIDDEN, "Il feed «Per te» richiede il consenso alla profilazione")
        interessi = await _interessi(db, user)
    testo = q.lower().strip() if q else None

    def adatto(o, autore_id, cat, titolo, descr) -> bool:
        if autore_id in bloccati or o.id in nascosti:
            return False
        if categoria and cat != categoria:
            return False
        if interessi is not None and cat not in interessi:
            return False
        if testo and testo not in f"{titolo} {descr}".lower():
            return False
        d = geo.distanza_m(area.lat, area.lon, o.lat, o.lon)
        return d <= min(raggio, o.raggio_m) or autore_id == user.id

    vuoi_post = tipo != "evento"
    vuoi_eventi = tipo in ("tutti", "evento")
    raccolti: list[tuple[datetime, str, object]] = []

    async def raccogli(modello, condizioni, filtro):
        c = cur
        while True:
            qy = select(modello).where(or_(*[modello.geohash.like(f"{p}%") for p in prefissi]), _prima_di(modello, c),
                                       *condizioni).order_by(modello.created_at.desc(), modello.id.desc()).limit(LOTTO)
            if modello is Post:
                qy = qy.options(selectinload(Post.foto))
            righe = (await db.execute(qy)).scalars().all()
            for r in righe:
                if filtro(r):
                    raccolti.append((r.created_at, r.id, r))
            if len(righe) < LOTTO or sum(1 for x in raccolti if isinstance(x[2], modello)) > limite:
                return
            c = (righe[-1].created_at, righe[-1].id)

    if vuoi_post:
        cond = [Post.stato.in_(("pubblicato", "prenotato", "risolto")), Post.gruppo_id.is_(None), Post.tipo != "gruppo",
                or_(Post.scadenza.is_(None), Post.scadenza >= ora)]
        if tipo == "mercatino":
            cond.append(Post.tipo.in_(TIPI_MERCATINO))
        elif tipo not in ("tutti", "evento"):
            cond.append(Post.tipo == tipo)
        if periodo:
            cond.append(Post.created_at >= ora - timedelta(days=PERIODI[periodo]))
        # le segnalazioni risolte restano visibili per 3 giorni, poi escono dal feed
        cond.append(or_(Post.stato != "risolto", Post.updated_at >= ora - timedelta(days=3)))
        await raccogli(Post, cond, lambda p: adatto(p, p.autore_id, p.categoria, p.titolo, p.descrizione))
    if vuoi_eventi:
        cond = [Event.stato == "pubblicato", Event.gruppo_id.is_(None)]
        if periodo:
            cond.append(Event.inizio <= ora + timedelta(days=PERIODI[periodo]))
        await raccogli(Event, cond, lambda e: not concluso(e) and adatto(e, e.creatore_id, e.categoria, e.titolo, e.descrizione))

    raccolti.sort(key=lambda x: (x[0], x[1]), reverse=True)
    pagina = raccolti[:limite]
    autori: dict[str, User | None] = {}
    elementi = []
    for _, _, o in pagina:
        if isinstance(o, Post):
            if o.autore_id not in autori:
                autori[o.autore_id] = await db.get(User, o.autore_id)
            elementi.append(post_dict(o, autori[o.autore_id], user, area))
        else:
            elementi.append(await evento_dict(db, o, user, area))
    prossimo = _nuovo_cursore(pagina[-1][0], pagina[-1][1]) if len(raccolti) > limite and pagina else None
    return {"elementi": elementi, "cursore": prossimo, "area": {"nome": area.nome, "raggio_m": raggio}}


@router.get("/map")
async def mappa(
    tipo: Literal["tutti", "mercatino", "aiuto", "segnalazione", "evento", "servizio"] = "tutti",
    categoria: str | None = None,
    raggio_m: int | None = Query(None, ge=300, le=5000),
    user: User = Depends(utente_corrente),
    db: AsyncSession = Depends(get_db),
):
    """Punti sulla mappa: solo centri di cella (≈150 m) e mai la posizione delle persone."""
    area = await area_di(db, user)
    raggio = raggio_m or area.raggio_m
    prefissi = geo.prefissi_ricerca(area.lat, area.lon)
    bloccati = await utenti_bloccati(db, user.id)
    nascosti = await segnalati_da_me(db, user.id)
    ora = adesso()
    punti = []

    def entro(o, raggio_contenuto, autore_id):
        return autore_id not in bloccati and o.id not in nascosti and (categoria is None or o.categoria == categoria) and \
            geo.distanza_m(area.lat, area.lon, o.lat, o.lon) <= min(raggio, raggio_contenuto)

    vicino = lambda m: or_(*[m.geohash.like(f"{p}%") for p in prefissi])  # noqa: E731
    if tipo in ("tutti", "mercatino", "aiuto", "segnalazione"):
        qy = select(Post).where(vicino(Post), Post.stato.in_(("pubblicato", "prenotato")), Post.gruppo_id.is_(None),
                                Post.tipo != "gruppo", or_(Post.scadenza.is_(None), Post.scadenza >= ora))
        if tipo == "mercatino":
            qy = qy.where(Post.tipo.in_(TIPI_MERCATINO))
        elif tipo != "tutti":
            qy = qy.where(Post.tipo == tipo)
        for p in (await db.execute(qy.order_by(Post.created_at.desc()).limit(300))).scalars():
            if entro(p, p.raggio_m, p.autore_id):
                punti.append({"id": p.id, "genere": "post", "tipo": p.tipo, "categoria": p.categoria, "titolo": p.titolo,
                              "stato": p.stato, "link": f"/app/annunci/{p.id}", **posizione_pubblica(p.lat, p.lon, area)})
    if tipo in ("tutti", "evento"):
        qy = select(Event).where(vicino(Event), Event.stato == "pubblicato", Event.gruppo_id.is_(None))
        for e in (await db.execute(qy.order_by(Event.inizio).limit(200))).scalars():
            if not concluso(e) and entro(e, e.raggio_m, e.creatore_id):
                punti.append({"id": e.id, "genere": "evento", "tipo": "evento", "categoria": e.categoria, "titolo": e.titolo,
                              "inizio": e.inizio, "link": f"/app/eventi/{e.id}", **posizione_pubblica(e.lat, e.lon, area)})
    if tipo in ("tutti", "servizio"):
        massimo = get_settings().raggio_max_m
        qy = select(Service).where(vicino(Service), Service.rimosso.is_(False))
        for sv in (await db.execute(qy.limit(200))).scalars():
            if entro(sv, massimo, sv.autore_id):
                punti.append({"id": sv.id, "genere": "servizio", "tipo": "servizio", "categoria": sv.categoria,
                              "titolo": sv.nome, "link": f"/app/servizi?id={sv.id}", **posizione_pubblica(sv.lat, sv.lon, area)})
    return {"centro": {"lat": area.lat, "lon": area.lon, "nome": area.nome}, "raggio_m": raggio, "punti": punti,
            "nota": "Le posizioni sono approssimate: nessun indirizzo viene mostrato."}


@router.get("/users/{user_id}")
async def profilo_pubblico(user_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    from app.routers.reputazione import riepilogo_reputazione

    u = await db.get(User, user_id)
    if u is None or (user_id in await utenti_bloccati(db, user.id) and user.ruolo != "moderatore"):
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Utente non trovato")
    return {**utente_pubblico(u), "bio": u.bio, "iscritto_dal": u.created_at,
            "reputazione": await riepilogo_reputazione(db, u.id)}
