"""
Annunci e mercatino (F02), richieste di aiuto (F04), segnalazioni di quartiere (F06).
Stati: bozza → pubblicato → prenotato → chiuso (le segnalazioni passano a "risolto").

© Roberto Di Flumeri
"""

from datetime import timedelta

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func, select, update
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app import geo
from app.config import get_settings
from app.database import get_db
from app.models import Area, Conversation, Group, Photo, Post, User, adesso
from app.schemas import PostIn, PostUpdate, StatoPostIn
from app.security import audit, controlla_rate, utente_corrente
from app.services import controlli, foto
from app.services.catalogo import STATO_FINALE_PER_TIPO, TIPI_MERCATINO, TRANSIZIONI, categorie_per_tipo
from app.services.notifiche import diffondi, notifica
from app.services.vicinato import (
    archivia_segnalazioni, area_di, membro_di, non_trovato, posizione_pubblica, traccia, utente_pubblico, utenti_bloccati,
    visibile_da,
)

router = APIRouter(prefix="/v1", tags=["Annunci, aiuti e segnalazioni"])


def post_dict(p: Post, autore: User | None, lettore: User, area: Area | None) -> dict:
    mio = p.autore_id == lettore.id
    out = {
        "id": p.id, "genere": "post", "tipo": p.tipo, "categoria": p.categoria, "titolo": p.titolo,
        "descrizione": p.descrizione, "stato": p.stato, "scadenza": p.scadenza, "disponibilita": p.disponibilita,
        "prezzo": p.prezzo, "posizione": posizione_pubblica(p.lat, p.lon, area), "autore": utente_pubblico(autore),
        "foto": [{"id": f.id, "url": foto.url_firmato(f.id), "larghezza": f.larghezza, "altezza": f.altezza} for f in p.foto],
        "gruppo_id": p.gruppo_id, "created_at": p.created_at, "updated_at": p.updated_at, "mio": mio,
        "scaduto": bool(p.scadenza and p.scadenza < adesso()),
    }
    if mio or p.prenotato_da == lettore.id:
        out["prenotato_da"] = p.prenotato_da
    if mio:
        out["raggio_m"] = p.raggio_m
    return out


async def carica_post(db: AsyncSession, post_id: str) -> Post:
    p = (await db.execute(select(Post).options(selectinload(Post.foto)).where(Post.id == post_id)
                          .execution_options(populate_existing=True))).scalar_one_or_none()
    if p is None:
        raise non_trovato("Contenuto")
    return p


async def verifica_visibilita_post(db: AsyncSession, p: Post, user: User) -> Area | None:
    """Il contenuto è visibile all'autore, ai membri del gruppo o ai vicini nel raggio scelto dall'autore."""
    area = (await db.execute(select(Area).where(Area.user_id == user.id))).scalar_one_or_none()
    if p.autore_id == user.id or user.ruolo == "moderatore":
        return area
    if p.stato in ("bozza", "rimosso") or p.autore_id in await utenti_bloccati(db, user.id):
        raise non_trovato("Contenuto")
    if p.gruppo_id:
        if not await membro_di(db, p.gruppo_id, user.id):
            raise non_trovato("Contenuto")
        return area
    if area is None or visibile_da(area, p.lat, p.lon, p.raggio_m, get_settings().raggio_max_m) is None:
        raise non_trovato("Contenuto")
    return area


async def _collega_foto(db: AsyncSession, user: User, p: Post, foto_ids: list[str]) -> None:
    if len(foto_ids) > get_settings().foto_per_post:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, f"Massimo {get_settings().foto_per_post} foto")
    for i, fid in enumerate(foto_ids):
        f = await db.get(Photo, fid)
        if f is None or f.proprietario_id != user.id or (f.post_id and f.post_id != p.id):
            raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Foto non valida")
        f.post_id, f.ordine = p.id, i
    # foto tolte dal contenuto: si scollegano (verranno eliminate dalla pulizia periodica)
    await db.execute(update(Photo).where(Photo.post_id == p.id, Photo.id.not_in(foto_ids or [""])).values(post_id=None))


def _controlla_testo(*testi: str | None) -> None:
    for t in testi:
        if t:
            trovati = controlli.dati_personali(t)
            if trovati:
                raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY,
                                    f"Il testo contiene {', '.join(trovati)}: per proteggere la tua privacy "
                                    "condividili solo in chat con chi ti contatta.")


@router.post("/posts", status_code=status.HTTP_201_CREATED)
async def crea(dati: PostIn, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    s = get_settings()
    controlla_rate(f"scrittura:{user.id}", s.rate_limit_scrittura_min)
    area = await area_di(db, user)
    gruppo = None
    if dati.gruppo_id:
        gruppo = await db.get(Group, dati.gruppo_id)
        if gruppo is None or gruppo.rimosso or not await membro_di(db, gruppo.id, user.id):
            raise HTTPException(status.HTTP_403_FORBIDDEN, "Puoi scrivere solo nei gruppi di cui fai parte")
    if dati.tipo == "gruppo" and gruppo is None:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Un messaggio di gruppo richiede il gruppo")
    if dati.scadenza and dati.scadenza <= adesso():
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "La scadenza deve essere nel futuro")
    _controlla_testo(dati.titolo, dati.descrizione, dati.disponibilita)

    # quota pubblicazioni e contenuti ripetuti (§11 "Spam commerciale")
    giorno = adesso() - timedelta(days=1)
    pubblicati = (await db.execute(select(func.count()).select_from(Post).where(
        Post.autore_id == user.id, Post.created_at >= giorno, Post.tipo != "gruppo"))).scalar_one()
    if dati.tipo != "gruppo" and pubblicati >= s.post_al_giorno:
        raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS,
                            f"Hai raggiunto il limite di {s.post_al_giorno} pubblicazioni in 24 ore")
    impronta = controlli.impronta(dati.titolo, dati.descrizione)
    ripetuto = (await db.execute(select(Post.id).where(
        Post.autore_id == user.id, Post.impronta == impronta, Post.created_at >= adesso() - timedelta(days=7),
        Post.stato != "rimosso"))).first()
    if ripetuto:
        raise HTTPException(status.HTTP_409_CONFLICT, "Hai già pubblicato questo contenuto negli ultimi 7 giorni: modificalo invece di ripubblicarlo")

    lat, lon = (dati.lat, dati.lon) if dati.lat is not None else (area.lat, area.lon)
    gh, lat, lon = geo.approssima(lat, lon, geo.PRECISIONE_LUOGO)
    p = Post(autore_id=user.id, tipo=dati.tipo, categoria=dati.categoria, titolo=dati.titolo, descrizione=dati.descrizione,
             stato="bozza" if dati.bozza else "pubblicato", scadenza=dati.scadenza, disponibilita=dati.disponibilita,
             prezzo=dati.prezzo if dati.tipo in TIPI_MERCATINO else None, geohash=gh, lat=lat, lon=lon,
             raggio_m=dati.raggio_m or area.raggio_m, gruppo_id=dati.gruppo_id, impronta=impronta)
    db.add(p)
    await db.flush()
    await _collega_foto(db, user, p, dati.foto_ids)
    avvisati = 0
    if p.stato == "pubblicato" and not p.gruppo_id:
        avvisati = await diffondi(db, autore_id=user.id, tipo=p.tipo, categoria=p.categoria, lat=p.lat, lon=p.lon,
                                  raggio_m=p.raggio_m, titolo=p.titolo, link=f"/app/annunci/{p.id}")
    await traccia(db, user, f"post_{p.tipo}")
    await db.commit()
    p = await carica_post(db, p.id)
    out = post_dict(p, user, user, area)
    out["vicini_avvisati"] = avvisati
    if controlli.emergenza(f"{p.titolo} {p.descrizione}"):
        out["avviso_emergenza"] = controlli.AVVISO_EMERGENZA
    return out


@router.get("/posts/{post_id}")
async def dettaglio(post_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    p = await carica_post(db, post_id)
    area = await verifica_visibilita_post(db, p, user)
    out = post_dict(p, await db.get(User, p.autore_id), user, area)
    if p.autore_id == user.id:
        # persone che hanno scritto all'autore su questo contenuto: tra queste si sceglie a chi è prenotato
        conv = (await db.execute(select(Conversation).where(
            Conversation.contesto_tipo == "post", Conversation.contesto_id == p.id))).scalars().all()
        out["interlocutori"] = [{**utente_pubblico(await db.get(User, c.richiedente_id)), "conversazione_id": c.id}
                                for c in conv if c.richiedente_id]
    else:
        c = (await db.execute(select(Conversation.id).where(
            Conversation.contesto_tipo == "post", Conversation.contesto_id == p.id,
            Conversation.richiedente_id == user.id))).scalar_one_or_none()
        out["mia_conversazione_id"] = c
    if controlli.emergenza(f"{p.titolo} {p.descrizione}"):
        out["avviso_emergenza"] = controlli.AVVISO_EMERGENZA
    return out


@router.patch("/posts/{post_id}")
async def modifica(post_id: str, dati: PostUpdate, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    p = await carica_post(db, post_id)
    if p.autore_id != user.id:
        raise non_trovato("Contenuto")
    if p.stato in ("chiuso", "risolto", "rimosso"):
        raise HTTPException(status.HTTP_409_CONFLICT, "Un contenuto chiuso non si può modificare")
    _controlla_testo(dati.titolo, dati.descrizione, dati.disponibilita)
    if dati.categoria is not None:
        if p.tipo != "gruppo" and dati.categoria not in categorie_per_tipo(p.tipo):
            raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Categoria non valida per questo tipo di contenuto")
        p.categoria = dati.categoria
    for campo in ("titolo", "descrizione", "disponibilita", "scadenza"):
        v = getattr(dati, campo)
        if v is not None:
            setattr(p, campo, v)
    if dati.prezzo is not None and p.tipo in TIPI_MERCATINO:
        p.prezzo = dati.prezzo or None
    p.impronta = controlli.impronta(p.titolo, p.descrizione)
    if dati.foto_ids is not None:
        await _collega_foto(db, user, p, dati.foto_ids)
    await db.commit()
    p = await carica_post(db, post_id)
    return post_dict(p, user, user, await area_di(db, user))


@router.post("/posts/{post_id}/stato")
async def cambia_stato(post_id: str, dati: StatoPostIn, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    p = await carica_post(db, post_id)
    if p.autore_id != user.id:
        raise non_trovato("Contenuto")
    if dati.stato == p.stato:
        return post_dict(p, user, user, await area_di(db, user))  # idempotente
    if dati.stato not in TRANSIZIONI.get(p.stato, set()):
        raise HTTPException(status.HTTP_409_CONFLICT, f"Passaggio non consentito: da {p.stato} a {dati.stato}")
    finale = STATO_FINALE_PER_TIPO.get(p.tipo, "chiuso")
    if dati.stato in ("chiuso", "risolto") and dati.stato != finale:
        raise HTTPException(status.HTTP_409_CONFLICT, f"Per questo contenuto lo stato finale è «{finale}»")
    if dati.stato == "prenotato":
        if p.tipo not in TIPI_MERCATINO and p.tipo != "aiuto":
            raise HTTPException(status.HTTP_409_CONFLICT, "Solo annunci e richieste di aiuto si possono prenotare")
        # si prenota solo per qualcuno con cui si è già parlato in chat su questo contenuto
        conv = (await db.execute(select(Conversation).where(
            Conversation.contesto_tipo == "post", Conversation.contesto_id == p.id,
            Conversation.richiedente_id == dati.prenotato_da))).scalar_one_or_none()
        if not dati.prenotato_da or conv is None:
            raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Scegli una persona che ti ha contattato in chat")
        p.prenotato_da = dati.prenotato_da
        await notifica(db, dati.prenotato_da, "messaggio", f"«{p.titolo}» è prenotato per te",
                       f"{user.alias} ha segnato il contenuto come prenotato per te.", f"/app/annunci/{p.id}")
    elif dati.stato == "pubblicato" and p.stato == "prenotato":
        p.prenotato_da = None
    if p.stato == "bozza" and dati.stato == "pubblicato" and not p.gruppo_id:
        await diffondi(db, autore_id=user.id, tipo=p.tipo, categoria=p.categoria, lat=p.lat, lon=p.lon,
                       raggio_m=p.raggio_m, titolo=p.titolo, link=f"/app/annunci/{p.id}")
    if dati.stato == "chiuso" and p.prenotato_da:
        await notifica(db, p.prenotato_da, "reputazione", "Com'è andata?",
                       f"Lo scambio «{p.titolo}» è concluso: puoi lasciare un feedback a {user.alias}.", f"/app/annunci/{p.id}")
    p.stato = dati.stato
    audit(db, user.id, f"post_{dati.stato}", p.id)
    await db.commit()
    return post_dict(await carica_post(db, post_id), user, user, await area_di(db, user))


@router.delete("/posts/{post_id}", status_code=status.HTTP_204_NO_CONTENT)
async def elimina(post_id: str, user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    p = await carica_post(db, post_id)
    if p.autore_id != user.id:
        raise non_trovato("Contenuto")
    for f in p.foto:
        (foto.cartella() / f.file).unlink(missing_ok=True)
    await db.delete(p)
    await archivia_segnalazioni(db, "Contenuto eliminato dall'autore", oggetto_id=post_id)
    audit(db, user.id, "post_eliminato", post_id)
    await db.commit()


@router.get("/me/posts")
async def miei(user: User = Depends(utente_corrente), db: AsyncSession = Depends(get_db)):
    area = (await db.execute(select(Area).where(Area.user_id == user.id))).scalar_one_or_none()
    righe = (await db.execute(select(Post).options(selectinload(Post.foto)).where(
        Post.autore_id == user.id, Post.tipo != "gruppo").order_by(Post.created_at.desc()))).scalars().all()
    return [post_dict(p, user, user, area) for p in righe]
