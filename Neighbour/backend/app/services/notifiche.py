"""
Notifiche geolocalizzate (F10) con frequenza controllata.

- Avvisi di quartiere: solo a chi ha dato il consenso "notifiche", con area, tipi e
  categorie che corrispondono e nessun blocco reciproco.
- Ore di silenzio: la notifica resta in attesa e compare alla fine del silenzio.
- Frequenza: oltre la soglia oraria, o in modalità "giornaliera", gli avvisi vengono
  raggruppati in un unico riepilogo.

© Roberto Di Flumeri
"""

from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo

from sqlalchemy import func, or_, select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app import geo
from app.config import get_settings
from app.models import Area, Notification, NotificationPrefs, adesso
from app.security import consenso_attivo
from app.services.vicinato import utenti_bloccati

FUSO = ZoneInfo("Europe/Rome")
TIPI_SEMPRE = {"moderazione"}  # comunicazioni di servizio: arrivano sempre e ignorano il silenzio


def ora_locale(utc_naive: datetime) -> datetime:
    return utc_naive.replace(tzinfo=timezone.utc).astimezone(FUSO)


def in_silenzio(p: NotificationPrefs | None, quando: datetime) -> bool:
    if p is None or p.quiet_inizio is None or p.quiet_fine is None or p.quiet_inizio == p.quiet_fine:
        return False
    h = ora_locale(quando).hour
    a, b = p.quiet_inizio, p.quiet_fine
    return a <= h < b if a < b else (h >= a or h < b)


def fine_silenzio(p: NotificationPrefs, quando: datetime) -> datetime:
    locale = ora_locale(quando).replace(minute=0, second=0, microsecond=0)
    while True:
        locale += timedelta(hours=1)
        if locale.hour == p.quiet_fine:
            return locale.astimezone(timezone.utc).replace(tzinfo=None)


async def preferenze(db: AsyncSession, user_id: str) -> NotificationPrefs:
    p = await db.get(NotificationPrefs, user_id)
    if p is None:
        p = NotificationPrefs(user_id=user_id, tipi=[], categorie=[], raggio_m=1500, frequenza="immediata",
                              quiet_inizio=22, quiet_fine=8)
        db.add(p)
        await db.flush()
    return p


async def notifica(db: AsyncSession, user_id: str, tipo: str, titolo: str, testo: str, link: str | None = None) -> None:
    """Notifica personale (messaggio, evento, reputazione, moderazione)."""
    if tipo not in TIPI_SEMPRE and not await consenso_attivo(db, user_id, "notifiche"):
        return
    p = await db.get(NotificationPrefs, user_id)
    ora = adesso()
    if tipo not in TIPI_SEMPRE and p is not None and p.frequenza == "disattivata":
        return
    attesa = tipo not in TIPI_SEMPRE and in_silenzio(p, ora)
    db.add(Notification(
        user_id=user_id, tipo=tipo, titolo=titolo[:160], testo=testo[:500], link=link,
        stato="in_attesa" if attesa else "consegnata", visibile_da=fine_silenzio(p, ora) if attesa else ora,
    ))


async def diffondi(db: AsyncSession, *, autore_id: str, tipo: str, categoria: str, lat: float, lon: float,
                   raggio_m: int, titolo: str, link: str) -> int:
    """Avvisa i vicini interessati a un nuovo contenuto. Restituisce quante persone sono state avvisate."""
    s = get_settings()
    ora = adesso()
    prefissi = geo.prefissi_ricerca(lat, lon)
    aree = (await db.execute(
        select(Area).where(or_(*[Area.geohash.like(f"{p}%") for p in prefissi]), Area.user_id != autore_id)
    )).scalars().all()
    bloccati = await utenti_bloccati(db, autore_id)
    avvisati = 0
    for area in aree:
        if area.user_id in bloccati or not await consenso_attivo(db, area.user_id, "notifiche"):
            continue
        p = await db.get(NotificationPrefs, area.user_id)
        if p is None or p.frequenza == "disattivata":
            continue
        if p.tipi and tipo not in p.tipi:
            continue
        if p.categorie and categoria not in p.categorie:
            continue
        if geo.distanza_m(area.lat, area.lon, lat, lon) > min(p.raggio_m, raggio_m):
            continue
        recenti = (await db.execute(
            select(func.count()).select_from(Notification).where(
                Notification.user_id == area.user_id, Notification.tipo == "vicinato",
                Notification.created_at >= ora - timedelta(hours=1),
                Notification.stato.in_(("consegnata", "in_attesa")))
        )).scalar_one()
        raggruppa = p.frequenza == "giornaliera" or recenti >= s.notifiche_immediate_ora
        attesa = not raggruppa and in_silenzio(p, ora)
        db.add(Notification(
            user_id=area.user_id, tipo="vicinato", titolo=f"Nel tuo quartiere: {titolo}"[:160],
            testo=f"Nuovo contenuto a {geo.distanza_leggibile(geo.distanza_m(area.lat, area.lon, lat, lon))} da te.",
            link=link, stato="raggruppata" if raggruppa else ("in_attesa" if attesa else "consegnata"),
            visibile_da=fine_silenzio(p, ora) if attesa else ora,
        ))
        avvisati += 1
    return avvisati


async def genera_riepiloghi(db: AsyncSession, ora: datetime | None = None) -> int:
    """Raccoglie gli avvisi raggruppati in un riepilogo (giornaliero alle 8, o orario per chi ha superato la soglia)."""
    ora = ora or adesso()
    utenti = (await db.execute(
        select(Notification.user_id, func.min(Notification.created_at), func.count())
        .where(Notification.stato == "raggruppata").group_by(Notification.user_id)
    )).all()
    creati = 0
    for user_id, piu_vecchia, quante in utenti:
        p = await preferenze(db, user_id)
        if in_silenzio(p, ora):
            continue
        if p.frequenza == "giornaliera":
            # un riepilogo al giorno dalle 8: contiene gli avvisi arrivati prima delle 8 di oggi
            oggi_alle_8 = ora_locale(ora).replace(hour=8, minute=0, second=0, microsecond=0)
            oggi_alle_8_utc = oggi_alle_8.astimezone(timezone.utc).replace(tzinfo=None)
            gia_fatto = p.ultimo_riepilogo and ora_locale(p.ultimo_riepilogo).date() == oggi_alle_8.date()
            if ora < oggi_alle_8_utc or gia_fatto or piu_vecchia >= oggi_alle_8_utc:
                continue
        elif ora - piu_vecchia < timedelta(hours=1):
            continue
        voci = (await db.execute(
            select(Notification).where(Notification.user_id == user_id, Notification.stato == "raggruppata")
            .order_by(Notification.created_at.desc()).limit(5)
        )).scalars().all()
        elenco = "; ".join(v.titolo.removeprefix("Nel tuo quartiere: ") for v in voci)
        db.add(Notification(user_id=user_id, tipo="riepilogo", titolo=f"Riepilogo del quartiere: {quante} novità",
                            testo=elenco + ("…" if quante > 5 else ""), link="/app/notifiche", visibile_da=ora))
        await db.execute(update(Notification).where(Notification.user_id == user_id, Notification.stato == "raggruppata")
                         .values(stato="riepilogata"))
        p.ultimo_riepilogo = ora
        creati += 1
    await db.commit()
    return creati
