"""
Quartiere dimostrativo (Isola, Milano): vicini, annunci con foto, aiuti, segnalazioni, eventi,
gruppi, servizi, chat, feedback, segnalazioni alla moderazione e un ricorso.

Tutto passa dalle API vere (stesse validazioni dell'app). I profili sono dichiarati come
dimostrativi nella presentazione e le foto sono illustrazioni di esempio: contenuti "seed"
trasparenti (§11).

    cd backend
    .venv\\Scripts\\python scripts\\demo.py            # crea il quartiere se manca
    .venv\\Scripts\\python scripts\\demo.py --reset    # lo ricrea da zero

© Roberto Di Flumeri
"""

import asyncio
import io
import sys
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from httpx import ASGITransport, AsyncClient  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402
from sqlalchemy import select, update  # noqa: E402

from app.crypto import hash_email  # noqa: E402
from app.database import SessionLocal, init_db  # noqa: E402
from app.main import app  # noqa: E402
from app.models import Conversation, Event, Message, Notification, Post, Report, Reputation, User, adesso  # noqa: E402
from app.security import azzera_rate_limit  # noqa: E402

PASSWORD = "VicinoDemo2026"
NOTA = "Profilo dimostrativo di Neighbour."
TUTTI = {"trattamento_dati": True, "geolocalizzazione": True, "notifiche": True, "profilazione": True, "analytics": True}
CENTRO = (45.4869, 9.1897)

# chi: (email, alias, scostamento dell'area dal centro dell'Isola, presentazione, attività professionale)
UTENTI = {
    "giulia": ("giulia@neighbour.it", "Giulia", (0.0, 0.0), "Mamma di Pietro, nel quartiere da 6 anni. Amo le piante e il riuso.", None),
    "marco": ("marco@neighbour.it", "Marco", (0.0026, -0.0027), "Organizzo il calcetto del giovedì. Ciclista urbano.", None),
    "elena": ("elena@neighbour.it", "Elena", (-0.0019, 0.0028), "Pensionata, ex maestra. Coltivo pomodori sul balcone.", None),
    "forno": ("forno@neighbour.it", "Panificio Isola", (0.0011, 0.0008), "Pane a lievitazione naturale dal 1987.", "Panificio Isola"),
    "luca": ("luca@neighbour.it", "Luca", (-0.0029, -0.0037), "Studente di ingegneria, disponibile per piccoli aiuti.", None),
    "sara": ("sara@neighbour.it", "Sara", (0.0036, 0.0038), "Due bimbi piccoli e un cane di nome Brioche.", None),
    "chiara": ("chiara@neighbour.it", "Chiara", (-0.0041, 0.0009), "Studio architettura e restauro mobili vecchi.", None),
    "giorgio": ("giorgio@neighbour.it", "Nonno Giorgio", (0.0019, 0.0051), "Ex falegname, nonno di quattro nipoti. Aggiusto un po' di tutto.", None),
    "fatima": ("fatima@neighbour.it", "Fatima", (-0.0052, -0.0014), "Nuova nel quartiere: mi sono trasferita da Torino a settembre.", None),
    "dario": ("dario@neighbour.it", "Dario", (0.0047, -0.0049), "Papà runner, corro al parco quasi ogni mattina.", None),
    "vet": ("veterinaria@neighbour.it", "Dott.ssa Bianchi", (0.0008, -0.0044), "Medico veterinario, ambulatorio in zona.", "Ambulatorio Veterinario Isola"),
    "promo": ("promo@neighbour.it", "OfferteTop", (0.0006, 0.0012), "Offerte e guadagni.", None),
    "moderatore": ("moderatore@neighbour.it", "Moderazione Neighbour", (0.0, 0.0), "Team di moderazione.", None),
    "moderatrice": ("moderatrice@neighbour.it", "Moderazione (turno 2)", (0.0, 0.0), "Team di moderazione.", None),
}
MODERATORI = ("moderatore", "moderatrice")

# scostamenti (gradi) attorno al centro: zone entro circa 1 km, come se l'autore le avesse indicate sulla mappa
ZONE = [(0.0012, -0.0020), (-0.0025, 0.0030), (0.0040, 0.0015), (-0.0008, -0.0045), (0.0030, -0.0060),
        (-0.0045, -0.0010), (0.0055, 0.0045), (0.0005, 0.0060), (-0.0030, 0.0075), (0.0070, -0.0025),
        (-0.0060, 0.0040), (0.0020, 0.0095), (-0.0015, -0.0080), (0.0080, 0.0070), (-0.0070, -0.0055),
        (0.0045, -0.0095), (-0.0090, 0.0010), (0.0062, 0.0021), (-0.0036, -0.0066), (0.0028, 0.0034),
        (-0.0054, 0.0061), (0.0091, -0.0008), (-0.0011, 0.0041), (0.0016, -0.0069)]

FONT_EMOJI = "C:/Windows/Fonts/seguiemj.ttf"
SFONDI = [((232, 245, 233), (200, 230, 201)), ((255, 243, 224), (255, 224, 178)), ((227, 242, 253), (187, 222, 251)),
          ((243, 229, 245), (225, 190, 231)), ((255, 248, 225), (255, 236, 179)), ((224, 247, 250), (178, 235, 242))]


def tra(**kw) -> str:
    return (adesso() + timedelta(**kw)).isoformat()


def alle(giorni: int, ora: int, minuti: int = 0) -> str:
    """Tra `giorni` giorni all'ora locale indicata (Europe/Rome), in UTC per il server."""
    locale = datetime.now(ZoneInfo("Europe/Rome")).replace(hour=ora, minute=minuti, second=0, microsecond=0) + timedelta(days=giorni)
    return locale.astimezone(timezone.utc).replace(tzinfo=None).isoformat()


def illustrazione(emoji: str, indice: int) -> bytes:
    """Illustrazione di esempio (emoji a colori su fondo sfumato) usata come foto dimostrativa."""
    w, h = 1200, 900
    a, b = SFONDI[indice % len(SFONDI)]
    img = Image.new("RGB", (w, h), a)
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / h
        d.line([(0, y), (w, y)], fill=tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)))
    d.ellipse((w / 2 - 330, h / 2 - 300, w / 2 + 330, h / 2 + 330), fill=tuple(min(255, c + 18) for c in a))
    try:
        font = ImageFont.truetype(FONT_EMOJI, 420)
        d.text((w / 2, h / 2 + 20), emoji, font=font, embedded_color=True, anchor="mm")
    except OSError:  # font non disponibile: resta l'illustrazione astratta
        pass
    out = io.BytesIO()
    img.save(out, "JPEG", quality=88)
    return out.getvalue()


class Quartiere:
    def __init__(self, c: AsyncClient):
        self.c, self.h, self.id = c, {}, {}
        self.n_post = self.n_foto = 0

    async def ok(self, metodo, url, chi, **kw):
        azzera_rate_limit()
        r = await self.c.request(metodo, url, headers=self.h[chi], **kw)
        if r.status_code >= 400:
            raise RuntimeError(f"{metodo} {url} ({chi}): {r.status_code} {r.text}")
        return r.json() if r.content else None

    async def utenti(self):
        for chi, (email, alias, (dlat, dlon), bio, attivita) in UTENTI.items():
            azzera_rate_limit()
            r = await self.c.post("/v1/auth/register", json={
                "email": email, "password": PASSWORD, "alias": alias, "accetto_termini": True, "consensi": TUTTI,
                "tipo_profilo": "professionale" if attivita else "privato", "attivita": attivita})
            r.raise_for_status()
            self.h[chi] = {"Authorization": f"Bearer {r.json()['access_token']}"}
            await self.ok("PATCH", "/v1/auth/me", chi, json={"bio": f"{bio} {NOTA}"})
            await self.ok("PUT", "/v1/area", chi, json={"nome": "Isola, Milano", "lat": CENTRO[0] + dlat,
                                                        "lon": CENTRO[1] + dlon, "raggio_m": 1500})
            await self.ok("PATCH", "/v1/preferences", chi, json={"notifiche": {"quiet_inizio": 23, "quiet_fine": 7}})
            self.id[chi] = (await self.ok("GET", "/v1/auth/me", chi))["id"]
        async with SessionLocal() as db:
            await db.execute(update(User).where(User.id.in_([self.id[m] for m in MODERATORI])).values(ruolo="moderatore"))
            await db.commit()

    async def foto(self, chi, *emoji) -> list[str]:
        ids = []
        for e in emoji:
            self.n_foto += 1
            azzera_rate_limit()
            r = await self.c.post("/v1/files", headers=self.h[chi],
                                  files={"file": ("foto.jpg", illustrazione(e, self.n_foto), "image/jpeg")})
            r.raise_for_status()
            ids.append(r.json()["id"])
        return ids

    async def post(self, chi, foto=(), **campi):
        if campi.get("tipo") != "gruppo" and "lat" not in campi:
            self.n_post += 1
            dlat, dlon = ZONE[self.n_post % len(ZONE)]
            campi["lat"], campi["lon"] = CENTRO[0] + dlat, CENTRO[1] + dlon
        if foto:
            campi["foto_ids"] = await self.foto(chi, *foto)
        return await self.ok("POST", "/v1/posts", chi, json=campi)

    async def chat(self, chi, tipo, cid, *messaggi):
        """Il primo messaggio è di chi apre; poi si alternano le due persone."""
        conv = await self.ok("POST", "/v1/conversations", chi, json={"contesto_tipo": tipo, "contesto_id": cid, "messaggio": messaggi[0]})
        altro = next(k for k, v in self.id.items() if v == conv["con"]["id"])
        for i, testo in enumerate(messaggi[1:]):
            await self.ok("POST", f"/v1/conversations/{conv['id']}/messages", altro if i % 2 == 0 else chi, json={"testo": testo})
        return conv

    async def evento(self, chi, partecipanti=(), **campi):
        e = await self.ok("POST", "/v1/events", chi, json=campi)
        for p in partecipanti:
            await self.ok("POST", f"/v1/events/{e['id']}/join", p)
        return e

    async def scambio(self, autore, altro, post, *messaggi, chiudi=True):
        await self.chat(altro, "post", post["id"], *messaggi)
        await self.ok("POST", f"/v1/posts/{post['id']}/stato", autore, json={"stato": "prenotato", "prenotato_da": self.id[altro]})
        if chiudi:
            await self.ok("POST", f"/v1/posts/{post['id']}/stato", autore, json={"stato": "chiuso"})

    async def feedback(self, da, a, tipo, cid, commento, esito="positivo"):
        await self.ok("POST", "/v1/reputation", da, json={"destinatario_id": self.id[a], "contesto_tipo": tipo,
                                                           "contesto_id": cid, "esito": esito, "commento": commento})


async def popola(c: AsyncClient) -> None:
    q = Quartiere(c)
    await q.utenti()
    await q.ok("PATCH", "/v1/preferences", "giulia", json={"notifiche": {"tipi": ["regalo", "scambio", "aiuto", "segnalazione", "evento"]}})

    # ── Mercatino ────────────────────────────────────────────────────────
    seggiolone = await q.post("giulia", ("🪑",), tipo="regalo", categoria="bambini", titolo="Seggiolone pieghevole",
                              descrizione="Seggiolone usato poco, pieghevole, con vassoio lavabile. Pietro è cresciuto!")
    bici = await q.post("marco", ("🚲",), tipo="offerta", categoria="sport", titolo="Bici da città con cestino", prezzo="60 €",
                        descrizione="Bici da donna 28 pollici, cambio a 3 marce, luci a dinamo. Freni appena sistemati.")
    scrivania = await q.post("luca", tipo="richiesta", categoria="casa", titolo="Cerco scrivania usata", prezzo="fino a 30 €",
                             descrizione="Mi serve una scrivania di almeno 100 cm per lo studio. Posso passare a ritirarla in bici con il carrello.")
    await q.post("sara", ("📚", "🧩"), tipo="scambio", categoria="bambini", titolo="Libri 0-3 anni in cambio di puzzle",
                 descrizione="Una decina di libri cartonati in ottimo stato. Li scambio volentieri con puzzle da 24-48 pezzi.")
    basilico = await q.post("giulia", ("🌿", "🪴"), tipo="regalo", categoria="giardino", titolo="Piantine di basilico e menta",
                            descrizione="Ho fatto troppe talee: vasetti di basilico genovese e menta piperita per chi li vuole.")
    await q.post("forno", ("🥖",), tipo="offerta", categoria="cibo", titolo="Pane del giorno prima a metà prezzo", prezzo="metà prezzo",
                 descrizione="Ogni sera dalle 18:30 il pane rimasto lo diamo a metà prezzo, così non si spreca nulla.")
    calcolatrice = await q.post("luca", ("🧮",), tipo="offerta", categoria="elettronica", titolo="Calcolatrice grafica", prezzo="25 €",
                                descrizione="Calcolatrice grafica usata per l'esame di stato, perfettamente funzionante, con cover.")
    gialli = await q.post("elena", ("📖",), tipo="regalo", categoria="libri", titolo="Romanzi gialli da regalare",
                          descrizione="Una scatola di gialli italiani e stranieri. Li ho letti tutti, meritano una seconda vita.")
    await q.post("chiara", ("🪑", "🎨"), tipo="offerta", categoria="casa", titolo="Sedia anni '60 restaurata", prezzo="45 €",
                 descrizione="Sedia in legno di faggio restaurata a mano, seduta in paglia di Vienna nuova.")
    await q.post("giorgio", ("🧰",), tipo="regalo", categoria="casa", titolo="Cassetta degli attrezzi completa",
                 descrizione="Martelli, cacciaviti, pinze e una sega a mano: per chi inizia a fare da sé.")
    passeggino = await q.post("dario", ("👶",), tipo="offerta", categoria="bambini", titolo="Passeggino leggero da viaggio", prezzo="40 €",
                              descrizione="Si chiude con una mano e sta nel bagagliaio. Usato per un anno, sempre in garage.")
    await q.post("fatima", tipo="richiesta", categoria="casa", titolo="Cerco tende per finestre grandi",
                 descrizione="Mi sono appena trasferita: cerco tende chiare per due finestre da 160 cm.")
    await q.post("sara", ("🧸",), tipo="regalo", categoria="bambini", titolo="Peluche e giochi in legno",
                 descrizione="Una borsa di peluche lavati e qualche gioco in legno: per bimbi da 1 a 3 anni.")
    await q.post("dario", ("👟",), tipo="regalo", categoria="sport", titolo="Scarpe da corsa n. 43 quasi nuove",
                 descrizione="Comprate della misura sbagliata e usate due volte. Le regalo a chi corre!")
    await q.post("chiara", ("🖼️",), tipo="scambio", categoria="casa", titolo="Cornici vintage in cambio di piante",
                 descrizione="Ho cinque cornici in legno di varie misure: le scambio con piante da interno.")
    await q.post("marco", ("⚽",), tipo="regalo", categoria="sport", titolo="Palloni da calcetto usati",
                 descrizione="Tre palloni ancora buoni per allenarsi. Li lascio a chi gioca al parco.")
    await q.post("giorgio", ("📻",), tipo="offerta", categoria="elettronica", titolo="Radio d'epoca funzionante", prezzo="35 €",
                 descrizione="Radio a valvole degli anni '50 che ho rimesso in funzione io: riceve ancora bene in FM.")
    await q.post("fatima", ("🍪",), tipo="regalo", categoria="cibo", titolo="Biscotti fatti in casa per i nuovi vicini",
                 descrizione="Ho preparato troppi biscotti alle mandorle: passate a prenderne una scatola, per conoscerci!")

    # ── Richieste di aiuto ───────────────────────────────────────────────
    spesa = await q.post("elena", tipo="aiuto", categoria="spesa", titolo="Spesa del giovedì",
                         descrizione="Mi è difficile portare le borse pesanti: qualcuno può accompagnarmi al mercato di piazzale Lagosta?",
                         scadenza=tra(days=6), disponibilita="giovedì mattina, dalle 9 alle 11")
    telefono = await q.post("elena", tipo="aiuto", categoria="tecnologia", titolo="Configurare il nuovo telefono",
                            descrizione="Mi hanno regalato uno smartphone: vorrei imparare a fare le videochiamate con i nipoti.",
                            scadenza=tra(days=10), disponibilita="pomeriggi dopo le 15")
    await q.post("sara", tipo="aiuto", categoria="animali", titolo="Qualcuno porta fuori Brioche sabato?",
                 descrizione="Sabato sono fuori città tutto il giorno: cerco chi porti a spasso la mia cagnolina due volte.",
                 scadenza=tra(days=4), disponibilita="sabato, mattina e sera")
    await q.post("fatima", tipo="aiuto", categoria="piccoli_lavori", titolo="Montare una libreria",
                 descrizione="Ho una libreria da montare e mi manca il trapano: chi mi presta una mano per un'oretta?",
                 scadenza=tra(days=8), disponibilita="domenica pomeriggio")
    await q.post("giorgio", tipo="aiuto", categoria="trasporto", titolo="Passaggio per la visita in ospedale",
                 descrizione="Martedì ho un controllo al Niguarda alle 10: cerco un passaggio all'andata.",
                 scadenza=tra(days=5), disponibilita="martedì mattina")
    await q.post("chiara", tipo="aiuto", categoria="studio", titolo="Cerco chi mi aiuti con l'inglese",
                 descrizione="Ho un esame tra un mese: scambierei conversazione in inglese con lezioni di disegno.",
                 scadenza=tra(days=20), disponibilita="sere infrasettimanali")

    # ── Segnalazioni di quartiere ────────────────────────────────────────
    await q.post("giulia", ("🚧",), tipo="segnalazione", categoria="viabilita", titolo="Buca pericolosa vicino alla fermata del tram",
                 descrizione="Buca profonda sul lato della pista ciclabile: di sera non si vede. Segnalata anche al Comune.")
    chiavi = await q.post("marco", ("🔑",), tipo="segnalazione", categoria="smarrito", titolo="Trovato mazzo di chiavi al parco",
                          descrizione="Mazzo con portachiavi a forma di gatto, trovato vicino alle altalene. Scrivetemi per descriverlo.")
    await q.post("sara", tipo="segnalazione", categoria="avviso", titolo="Mercoledì niente acqua dalle 9 alle 12",
                 descrizione="Avviso affisso nei portoni della zona: lavori alla rete idrica. Meglio fare scorta la sera prima.",
                 scadenza=tra(days=3))
    await q.post("dario", ("🐈",), tipo="segnalazione", categoria="smarrito", titolo="Gatto rosso smarrito",
                 descrizione="Si chiama Biscotto, è rosso tigrato con il collare blu. Non esce mai: è scappato dal balcone ieri sera.")
    await q.post("elena", ("🌳",), tipo="segnalazione", categoria="verde", titolo="Panchine nuove ai giardini",
                 descrizione="Il Municipio ha messo quattro panchine nuove vicino al laghetto: finalmente si sta comodi!")
    await q.post("luca", tipo="segnalazione", categoria="lavori", titolo="Lavori in strada fino a venerdì",
                 descrizione="Corsia chiusa per il rifacimento dell'asfalto: le bici passano sul marciapiede a piedi.",
                 scadenza=tra(days=4))

    # ── Spam (per la coda di moderazione) ────────────────────────────────
    spam = await q.post("promo", tipo="offerta", categoria="altro", titolo="Guadagna 500 euro al giorno da casa!!!",
                        descrizione="Metodo segreto garantito, scrivimi in privato per sapere come fare. Posti limitati!")
    spam2 = await q.post("promo", tipo="offerta", categoria="elettronica", titolo="iPhone a 99 euro, ultimi pezzi",
                         descrizione="Telefoni originali a prezzo stracciato, pagamento anticipato con ricarica.")

    # ── Gruppi ───────────────────────────────────────────────────────────
    cani = await q.ok("POST", "/v1/groups", "giulia", json={
        "nome": "Cani dell'Isola", "categoria": "animali",
        "descrizione": "Passeggiate di gruppo, consigli sui veterinari e scambio di favori tra chi ha un cane in zona.",
        "regole": "Rispetto, niente vendita di animali, raccogliamo sempre i bisogni."})
    genitori = await q.ok("POST", "/v1/groups", "sara", json={
        "nome": "Genitori Isola", "categoria": "genitori",
        "descrizione": "Scambio di vestiti e giochi, consigli su nidi e scuole, pomeriggi al parco insieme."})
    sport = await q.ok("POST", "/v1/groups", "marco", json={
        "nome": "Calcetto e corsa", "categoria": "sport",
        "descrizione": "Partite amatoriali e uscite di corsa al parco: tutti i livelli sono benvenuti."})
    orti = await q.ok("POST", "/v1/groups", "elena", json={
        "nome": "Orti sul balcone", "categoria": "hobby",
        "descrizione": "Semi, talee e consigli per coltivare ortaggi e aromatiche anche in pochi metri quadri."})
    benvenuto = await q.ok("POST", "/v1/groups", "chiara", json={
        "nome": "Nuovi arrivati in zona", "categoria": "iniziative",
        "descrizione": "Per chi si è appena trasferito: consigli pratici, uffici, scuole e qualcuno con cui prendere un caffè."})
    await q.ok("POST", "/v1/groups", "giorgio", json={
        "nome": "Riparatori di quartiere", "categoria": "iniziative",
        "descrizione": "Una volta al mese ci troviamo per aggiustare insieme piccoli elettrodomestici e biciclette invece di buttarli."})
    for chi, g in (("marco", cani), ("elena", cani), ("sara", cani), ("dario", cani), ("vet", cani),
                   ("giulia", genitori), ("dario", genitori), ("fatima", genitori),
                   ("luca", sport), ("giulia", sport), ("dario", sport), ("chiara", sport),
                   ("giulia", orti), ("giorgio", orti), ("fatima", orti),
                   ("fatima", benvenuto), ("giulia", benvenuto), ("luca", benvenuto)):
        await q.ok("POST", f"/v1/groups/{g['id']}/join", chi)
    for chi, g, titolo, testo, emoji in (
        ("sara", cani, "Passeggiata domenica mattina", "Ci troviamo domenica alle 10 all'ingresso del parco per un giro lungo con i cani?", ()),
        ("vet", cani, "Attenzione alle zecche", "Con questo caldo fuori stagione tornano le zecche: controllate i cani dopo le passeggiate nell'erba alta.", ()),
        ("elena", cani, "Veterinario di zona consigliato", "Mi sono trovata benissimo con l'ambulatorio vicino al mercato: gentili e prezzi onesti.", ()),
        ("dario", cani, "Area cani chiusa per lavori", "L'area cani dei giardini resta chiusa fino a lunedì: andiamo in quella di via Pastrengo.", ()),
        ("giulia", genitori, "Vestiti 12-18 mesi", "Ho un sacco di vestiti invernali taglia 12-18 mesi: chi li vuole li porto al parco giovedì.", ("👕",)),
        ("fatima", genitori, "Asilo nido: consigli?", "Mi sono appena trasferita: quali nidi della zona mi consigliate per una bimba di un anno?", ()),
        ("luca", sport, "Corsa al parco sabato alle 8", "5 km tranquilli, ritmo chiacchierata. Chi viene?", ()),
        ("giorgio", orti, "Semi di pomodoro cuore di bue", "Ho conservato i semi dei miei pomodori: ne regalo una bustina a chi passa.", ("🍅",)),
        ("chiara", benvenuto, "Benvenuta Fatima!", "Ciao Fatima, se vuoi sabato ti porto a fare un giro dei negozi storici del quartiere.", ()),
    ):
        await q.post(chi, emoji, tipo="gruppo", categoria="altro", gruppo_id=g["id"], titolo=titolo, descrizione=testo)

    # ── Eventi ───────────────────────────────────────────────────────────
    ora = adesso()
    giovedi = (ora + timedelta(days=(3 - ora.weekday()) % 7 or 7)).replace(hour=17, minute=0, second=0, microsecond=0)
    calcetto = await q.evento("marco", ("luca", "giulia", "sara", "dario", "chiara"),
        titolo="Calcetto del giovedì", categoria="sport", capienza=10, descrizione=(
            "Partita amatoriale 5 contro 5. Portate una maglia chiara e una scura. Si gioca anche se pioviggina."),
        inizio=giovedi.isoformat(), fine=(giovedi + timedelta(hours=1, minutes=30)).isoformat(),
        luogo="Campetto del parco, vicino alla Stecca", lat=CENTRO[0] + 0.0035, lon=CENTRO[1] - 0.0012)
    await q.ok("POST", f"/v1/events/{calcetto['id']}/aggiornamenti", "marco",
               json={"testo": "Ho prenotato il campo anche per la settimana dopo: passate parola!"})
    laboratorio = await q.evento("forno", ("sara", "giulia", "elena", "fatima"),
        titolo="Laboratorio di pane per bambini", categoria="bambini", capienza=3, modalita="su_approvazione",
        descrizione="Un'ora a impastare con il nostro fornaio: ogni bambino porta a casa il suo panino. Gratuito.",
        inizio=alle(5, 16), luogo="Retro del panificio, ingresso dal cortile")
    for chi in ("sara", "giulia"):
        await q.ok("POST", f"/v1/events/{laboratorio['id']}/partecipanti/{q.id[chi]}", "forno", json={"stato": "confermato"})
    await q.evento("sara", ("elena", "luca", "giorgio", "fatima", "chiara"),
        titolo="Pulizia dei giardini", categoria="volontariato",
        descrizione="Guanti e sacchi li porta il Municipio. Portate solo voglia di fare e una borraccia.",
        inizio=alle(9, 10), luogo="Giardini pubblici, lato giochi", lat=CENTRO[0] - 0.0022, lon=CENTRO[1] + 0.0041)
    passeggiata = await q.evento("giulia", ("sara", "elena", "dario", "marco", "luca", "fatima"),
        titolo="Passeggiata con i cani al parco", categoria="altro", capienza=5,
        descrizione="Giro lungo di un'ora con i nostri cani, poi caffè tutti insieme. Guinzaglio obbligatorio.",
        inizio=alle(3, 9, 30), fine=alle(3, 11), luogo="Ingresso principale del parco", gruppo_id=None,
        lat=CENTRO[0] + 0.0041, lon=CENTRO[1] + 0.0018)
    await q.evento("giorgio", ("chiara", "luca", "marco"),
        titolo="Repair café: aggiustiamo insieme", categoria="volontariato",
        descrizione="Portate un piccolo elettrodomestico, una lampada o una bici da sistemare: si ripara insieme, gratis.",
        inizio=alle(12, 15), fine=alle(12, 18), luogo="Sala comune della biblioteca di quartiere", lat=CENTRO[0] - 0.0031, lon=CENTRO[1] - 0.0021)
    await q.evento("chiara", ("fatima", "giulia", "elena"),
        titolo="Mercatino del baratto", categoria="mercatino",
        descrizione="Portate libri, vestiti e oggetti in buono stato: si scambia senza soldi. Tavoli per tutti.",
        inizio=alle(16, 10), fine=alle(16, 13), luogo="Piazzetta pedonale vicino al mercato", lat=CENTRO[0] + 0.0012, lon=CENTRO[1] + 0.0063)
    await q.evento("dario", ("luca", "marco"),
        titolo="Corsa all'alba", categoria="sport", gruppo_id=sport["id"],
        descrizione="10 km con partenza alle 7. Ritmo 5:30/km, nessuno resta indietro.",
        inizio=alle(2, 7), luogo="Fontana del parco")
    aperitivo = await q.evento("giulia", ("marco", "elena", "luca", "chiara"),
        titolo="Aperitivo di benvenuto per i nuovi residenti", categoria="cibo", capienza=20,
        descrizione="Ognuno porta qualcosa: un modo semplice per conoscersi tra vicini.",
        inizio=alle(1, 19), luogo="Cortile condiviso della zona")
    cinema = await q.evento("chiara", ("giulia", "fatima", "giorgio"),
        titolo="Cinema all'aperto in cortile", categoria="cultura",
        descrizione="Proiezione di un classico italiano su un lenzuolo bianco. Portate una sedia!",
        inizio=alle(1, 21), luogo="Cortile della casa di ringhiera")

    # ── Servizi locali ───────────────────────────────────────────────────
    panificio = await q.ok("POST", "/v1/services", "forno", json={
        "nome": "Panificio Isola", "categoria": "negozi", "tipo_scheda": "professionale", "zona": "Isola",
        "descrizione": "Pane a lievitazione naturale, focacce e dolci della tradizione. Aperti dal martedì alla domenica.",
        "contatto": "panificio-isola.example"})
    ambulatorio = await q.ok("POST", "/v1/services", "vet", json={
        "nome": "Ambulatorio Veterinario Isola", "categoria": "animali", "tipo_scheda": "professionale", "zona": "Isola, vicino al mercato",
        "descrizione": "Visite, vaccinazioni e piccola chirurgia per cani e gatti. Su appuntamento anche il sabato.",
        "contatto": "ambulatorio-vet-isola.example"})
    calzolaio = await q.ok("POST", "/v1/services", "elena", json={
        "nome": "Calzolaio Gianni", "categoria": "artigiani", "tipo_scheda": "suggerito", "zona": "Isola, vicino al mercato",
        "descrizione": "Ripara scarpe e borse in giornata, fa anche le chiavi. Onesto e velocissimo."})
    ciclofficina = await q.ok("POST", "/v1/services", "marco", json={
        "nome": "Ciclofficina popolare", "categoria": "artigiani", "tipo_scheda": "suggerito", "zona": "Isola",
        "descrizione": "Ti insegnano a sistemare la bici da solo, con gli attrezzi a disposizione. Offerta libera."})
    sarta = await q.ok("POST", "/v1/services", "sara", json={
        "nome": "Sartoria di via Borsieri", "categoria": "artigiani", "tipo_scheda": "suggerito", "zona": "Isola",
        "descrizione": "Orli, cerniere e piccole modifiche in un paio di giorni. La signora è bravissima con i vestiti da cerimonia."})
    libreria = await q.ok("POST", "/v1/services", "chiara", json={
        "nome": "Libreria indipendente del quartiere", "categoria": "negozi", "tipo_scheda": "suggerito", "zona": "Isola",
        "descrizione": "Ordinano qualsiasi libro in due giorni e organizzano letture per bambini il sabato."})
    lezioni = await q.ok("POST", "/v1/services", "luca", json={
        "nome": "Ripetizioni di matematica (studenti del Politecnico)", "categoria": "lezioni", "tipo_scheda": "suggerito",
        "zona": "Isola", "descrizione": "Un gruppo di studenti che fa ripetizioni a medie e superiori a prezzi bassi."})
    bar = await q.ok("POST", "/v1/services", "giorgio", json={
        "nome": "Bar della piazzetta", "categoria": "ristorazione", "tipo_scheda": "suggerito", "zona": "Isola",
        "descrizione": "Il caffè migliore della zona e il barista sa sempre tutto di tutti. Tengono anche le chiavi per i vicini."})
    pulizie = await q.ok("POST", "/v1/services", "fatima", json={
        "nome": "Cooperativa pulizie Il Fiore", "categoria": "casa", "tipo_scheda": "suggerito", "zona": "Isola e Garibaldi",
        "descrizione": "Mi hanno aiutato con il trasloco e le pulizie di fine lavori: precisi e puntuali."})
    for chi, sv, testo in (
        ("giulia", panificio, "La focaccia del sabato è imperdibile"), ("marco", panificio, None), ("elena", panificio, "Gentilissimi"),
        ("chiara", panificio, None), ("dario", panificio, "Brioche ottime"), ("sara", calzolaio, "Mi ha salvato gli stivali in un'ora"),
        ("giulia", calzolaio, None), ("giorgio", calzolaio, "Lavora come una volta"), ("luca", ciclofficina, "Ho imparato a cambiare la camera d'aria"),
        ("dario", ciclofficina, None), ("giulia", ambulatorio, "Dottoressa paziente e chiarissima"), ("sara", ambulatorio, "Brioche la adora"),
        ("elena", sarta, None), ("giulia", sarta, "Ha sistemato il mio abito da sposa"), ("fatima", libreria, "Accoglienti e bravissimi con i bambini"),
        ("sara", libreria, None), ("elena", lezioni, "Mio nipote è passato dal 5 al 7"), ("marco", bar, "Caffè e chiacchiere"),
        ("luca", bar, None), ("chiara", pulizie, None),
    ):
        await q.ok("POST", f"/v1/services/{sv['id']}/consigli", chi, json={"commento": testo})

    # ── Chat e scambi ────────────────────────────────────────────────────
    await q.scambio("giulia", "sara", seggiolone, "Ciao Giulia! Il seggiolone è ancora disponibile? Sarebbe perfetto per la piccola.",
                    "Ciao Sara, sì! Quando ti farebbe comodo passare?", "Domani verso le 18 va bene?",
                    "Perfetto, ci vediamo davanti al portone verde all'angolo con la piazzetta.", "Grazie mille, a domani!")
    await q.feedback("sara", "giulia", "post", seggiolone["id"], "Puntualissima e il seggiolone è come nuovo. Grazie!")
    await q.feedback("giulia", "sara", "post", seggiolone["id"], "Gentilissima, scambio semplicissimo.")
    await q.scambio("marco", "luca", bici, "Ciao Marco, la bici è ancora disponibile? La userei per andare al Politecnico.",
                    "Ciao Luca! Sì, se vuoi puoi provarla sabato mattina.", "Ottimo, sabato alle 10 ci sono.",
                    "Te la tengo da parte allora.", chiudi=False)
    await q.scambio("elena", "fatima", gialli, "Buongiorno Elena, adoro i gialli! Posso passare a prenderli?",
                    "Certo cara, sono contenta che finiscano in buone mani. Passi pure nel pomeriggio.",
                    "Arrivo alle 16, grazie!")
    await q.feedback("fatima", "elena", "post", gialli["id"], "Dolcissima, mi ha anche offerto un tè!")
    await q.feedback("elena", "fatima", "post", gialli["id"], "Ragazza educata e simpatica, benvenuta nel quartiere.")
    await q.scambio("giulia", "giorgio", basilico, "Ciao Giulia, prenderei volentieri due vasetti di basilico per il mio balcone.",
                    "Ciao Giorgio! Te li lascio al bar della piazzetta domattina, va bene?", "Benissimo, grazie!")
    await q.feedback("giorgio", "giulia", "post", basilico["id"], "Piantine bellissime e già belle grandi.")
    await q.scambio("dario", "sara", passeggino, "Ciao Dario, il passeggino si apre anche con una mano?",
                    "Sì, è comodissimo. Se vuoi lo provi.", "Lo prendo! Ci vediamo stasera al parco?", "Perfetto, alle 19.")
    await q.feedback("sara", "dario", "post", passeggino["id"], "Passeggino perfetto, consigliatissimo.")
    await q.chat("luca", "post", spesa["id"], "Buongiorno Elena, giovedì mattina sono libero: posso accompagnarla io al mercato.",
                 "Che gentile, grazie Luca! Alle 9:30 va bene?", "Benissimo, la aspetto sotto casa sua.")
    await q.chat("chiara", "post", telefono["id"], "Ciao Elena, posso aiutarla io con il telefono: sono brava con le videochiamate!",
                 "Grazie Chiara! Va bene mercoledì pomeriggio?")
    await q.chat("giulia", "servizio", panificio["id"], "Buongiorno, fate anche torte su ordinazione per un compleanno?",
                 "Buongiorno! Certo, ci serve un preavviso di due giorni. Passi in negozio per scegliere il gusto.",
                 "Perfetto, passo sabato mattina.")
    await q.chat("giulia", "post", calcolatrice["id"], "Ciao Luca, la calcolatrice serve a mio nipote: la tieni per me fino a lunedì?")
    await q.chat("dario", "post", chiavi["id"], "Ciao Marco! Quelle chiavi forse sono di mia moglie: il portachiavi è un gatto nero?")
    await q.chat("fatima", "evento", passeggiata["id"], "Ciao Giulia, posso venire anche se non ho un cane? Vorrei conoscere un po' di gente!",
                 "Ma certo Fatima, vieni pure! Ci sarà anche il caffè.")
    await q.chat("chiara", "post", scrivania["id"], "Ciao Luca, ho una scrivania da 120 cm che devo restaurare: te la lascio a 20 €.",
                 "Fantastico! Posso vederla domani?")

    # ── Blocchi, segnalazioni e moderazione ──────────────────────────────
    for chi, oggetto, motivo, dettagli in (
        ("giulia", spam, "spam", "Sembra una truffa: promette guadagni facili."),
        ("marco", spam, "truffa", "Chiede di scrivergli in privato per 'il metodo'."),
        ("elena", spam, "spam", None),
        ("luca", spam2, "truffa", "Prezzo impossibile e pagamento anticipato: è una truffa."),
    ):
        await q.ok("POST", "/v1/reports", chi, json={"oggetto_tipo": "post", "oggetto_id": oggetto["id"], "motivo": motivo,
                                                      "dettagli": dettagli, "blocca_utente": chi == "giulia"})
    # una decisione già presa, con ricorso in attesa che deve esaminare l'altro moderatore
    litigio = await q.post("dario", tipo="segnalazione", categoria="altro", titolo="Basta con le auto in doppia fila!",
                           descrizione="Ogni mattina qualcuno blocca il passo carraio. Siete degli incivili, vergognatevi!")
    rep = await q.ok("POST", "/v1/reports", "sara", json={"oggetto_tipo": "post", "oggetto_id": litigio["id"], "motivo": "molestie",
                                                          "dettagli": "Toni aggressivi verso i vicini."})
    await q.ok("POST", f"/v1/admin/reports/{rep['id']}/decidi", "moderatrice",
               json={"decisione": "rimuovi_contenuto", "nota": "Linguaggio offensivo verso altri residenti."})
    await q.ok("POST", f"/v1/reports/{rep['id']}/ricorso", "dario",
               json={"testo": "Non volevo offendere nessuno: segnalavo un problema reale. Posso riformulare il testo con toni più calmi."})
    # segnalazione archiviata senza violazione
    rep2 = await q.ok("POST", "/v1/reports", "fatima", json={"oggetto_tipo": "post", "oggetto_id": chiavi["id"], "motivo": "falso"})
    await q.ok("POST", f"/v1/admin/reports/{rep2['id']}/decidi", "moderatore",
               json={"decisione": "nessuna_violazione", "nota": "Contenuto utile e veritiero."})

    # ── Ritocchi temporali: un quartiere che esiste da qualche settimana ──
    async with SessionLocal() as db:
        posts = (await db.execute(select(Post).where(Post.tipo != "gruppo").order_by(Post.created_at))).scalars().all()
        ora = adesso()
        for i, p in enumerate(posts):
            p.created_at = p.updated_at = ora - timedelta(hours=9 * (len(posts) - i) + 1)
        gruppi = (await db.execute(select(Post).where(Post.tipo == "gruppo").order_by(Post.created_at))).scalars().all()
        for i, p in enumerate(gruppi):
            p.created_at = p.updated_at = ora - timedelta(hours=7 * (len(gruppi) - i) + 2)
        # gli eventi sono stati proposti nei giorni scorsi, mescolati agli altri contenuti
        for i, e in enumerate((await db.execute(select(Event).order_by(Event.created_at))).scalars().all()):
            e.created_at = ora - timedelta(hours=20 * (i + 1) + 5)
        # eventi già avvenuti: si possono lasciare i feedback
        for e, giorni in ((aperitivo, 4), (cinema, 9)):
            await db.execute(update(Event).where(Event.id == e["id"]).values(
                inizio=ora - timedelta(days=giorni), fine=ora - timedelta(days=giorni) + timedelta(hours=2),
                created_at=ora - timedelta(days=giorni + 8)))
        await db.execute(update(Report).values(created_at=ora - timedelta(hours=5)))
        await db.commit()
    await q.feedback("marco", "giulia", "evento", aperitivo["id"], "Bellissima idea, organizzata benissimo.")
    await q.feedback("elena", "giulia", "evento", aperitivo["id"], "Finalmente ho conosciuto i vicini del mio palazzo.")
    await q.feedback("chiara", "giulia", "evento", aperitivo["id"], "Atmosfera bellissima, da rifare!")
    await q.feedback("giulia", "marco", "evento", aperitivo["id"], "Ha portato una focaccia buonissima!")
    await q.feedback("giulia", "chiara", "evento", cinema["id"], "Serata magica, grazie per l'organizzazione.")
    await q.feedback("fatima", "chiara", "evento", cinema["id"], "Mi sono sentita subito a casa.")
    await q.feedback("giorgio", "chiara", "evento", cinema["id"], "Film scelto benissimo.", esito="positivo")

    async with SessionLocal() as db:
        # notifiche e messaggi dimostrativi: consegnati e distribuiti nel tempo, alcuni ancora da leggere
        # (creati in pochi secondi, gli avvisi supererebbero la soglia oraria: si distribuiscono nei giorni scorsi)
        notifiche = (await db.execute(select(Notification).order_by(Notification.created_at))).scalars().all()
        for i, n in enumerate(reversed(notifiche)):
            n.stato = "consegnata"
            n.created_at = n.visibile_da = adesso() - timedelta(minutes=25 + 95 * i)
            n.letta = i >= 6
        conv = (await db.execute(select(Conversation))).scalars().all()
        for i, c in enumerate(conv):
            messaggi = (await db.execute(select(Message).where(Message.conversazione_id == c.id).order_by(Message.created_at))).scalars().all()
            base = adesso() - timedelta(hours=3 + 11 * i)
            for j, m in enumerate(messaggi):
                m.created_at = base + timedelta(minutes=7 * j)
                m.letto = j < len(messaggi) - 1 or i % 3 == 0
            if messaggi:
                c.updated_at = messaggi[-1].created_at
        await db.execute(update(Reputation).values(created_at=adesso() - timedelta(days=2)))
        await db.commit()


async def esiste() -> bool:
    async with SessionLocal() as db:
        return (await db.execute(select(User.id).where(User.email_hash == hash_email(UTENTI["giulia"][0])))).first() is not None


async def elimina() -> None:
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://demo") as c:
        for email, *_ in UTENTI.values():
            azzera_rate_limit()
            r = await c.post("/v1/auth/login", json={"email": email, "password": PASSWORD})
            if r.status_code == 200:
                h = {"Authorization": f"Bearer {r.json()['access_token']}"}
                await c.request("DELETE", "/v1/account", headers=h, json={"password": PASSWORD, "conferma": "ELIMINA"})


async def main() -> None:
    await init_db()
    if "--reset" in sys.argv:
        await elimina()
    elif await esiste():
        print(f"Quartiere dimostrativo già presente (giulia@neighbour.it / {PASSWORD}): usa --reset per ricrearlo")
        return
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://demo") as c:
        await popola(c)
    print("Quartiere dimostrativo creato (Isola, Milano).")
    for chi, (email, alias, *_) in UTENTI.items():
        if chi != "promo":
            print(f"  {alias:<30} {email:<28} {PASSWORD}")


if __name__ == "__main__":
    asyncio.run(main())
