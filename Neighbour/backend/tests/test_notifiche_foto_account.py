"""Notifiche geolocalizzate, foto senza metadati, esportazione e cancellazione. © Roberto Di Flumeri"""

import io
import time
from datetime import datetime, timedelta, timezone

from PIL import Image
from sqlalchemy import select

from app.models import Conversation, Message, Notification, NotificationPrefs, Photo, Post, User
from app.services import foto
from app.services.notifiche import FUSO, genera_riepiloghi, in_silenzio
from tests.conftest import PASSWORD, TUTTI, VICINO_900M, id_utente, pubblica, vicino


async def notifiche(client, h):
    return (await client.get("/v1/notifications", headers=h)).json()


async def test_avviso_ai_vicini_interessati(client, anna, bruno, carla):
    await client.patch("/v1/preferences", headers=bruno, json={"notifiche": {"tipi": ["regalo"], "raggio_m": 2000}})
    p = await pubblica(client, anna)
    assert p["vicini_avvisati"] == 1  # Bruno sì, Carla no (fuori raggio)
    n = (await notifiche(client, bruno))["notifiche"]
    assert n[0]["link"] == f"/app/annunci/{p['id']}" and "circa" in n[0]["testo"]
    assert (await notifiche(client, carla))["notifiche"] == []
    # tipo non seguito: nessun avviso
    await pubblica(client, anna, tipo="segnalazione", categoria="viabilita", titolo="Semaforo rotto",
                   descrizione="Il semaforo dell'incrocio lampeggia")
    assert len((await notifiche(client, bruno))["notifiche"]) == 1


async def test_niente_avvisi_senza_consenso(client, anna):
    senza = await vicino(client, "nc@example.com", "Nora", VICINO_900M, consensi={**TUTTI, "notifiche": False})
    await pubblica(client, anna)
    assert (await notifiche(client, senza))["notifiche"] == []


async def test_bloccati_non_ricevono_avvisi(client, anna, bruno):
    await client.post(f"/v1/blocks/{await id_utente(client, anna)}", headers=bruno)
    assert (await pubblica(client, anna))["vicini_avvisati"] == 0


async def test_ore_di_silenzio(client, anna, bruno, fabbrica):
    ora_locale = datetime.now(timezone.utc).astimezone(FUSO).hour
    # silenzio che copre l'ora attuale e finisce tra due ore
    await client.patch("/v1/preferences", headers=bruno, json={"notifiche": {
        "quiet_inizio": (ora_locale - 1) % 24, "quiet_fine": (ora_locale + 2) % 24}})
    await pubblica(client, anna)
    stato = await notifiche(client, bruno)
    assert stato["notifiche"] == [] and stato["in_attesa"] == 1
    async with fabbrica() as db:
        n = (await db.execute(select(Notification))).scalar_one()
        assert n.stato == "in_attesa" and n.visibile_da > datetime.utcnow() + timedelta(minutes=50)


def test_calcolo_silenzio_a_cavallo_della_mezzanotte():
    p = NotificationPrefs(quiet_inizio=22, quiet_fine=8)
    locale = lambda h: datetime(2026, 1, 15, h, 30, tzinfo=FUSO).astimezone(timezone.utc).replace(tzinfo=None)  # noqa: E731
    assert in_silenzio(p, locale(23)) and in_silenzio(p, locale(3)) and not in_silenzio(p, locale(12))
    assert not in_silenzio(NotificationPrefs(quiet_inizio=None, quiet_fine=None), locale(3))


async def test_riepilogo_giornaliero(client, anna, bruno, fabbrica):
    await client.patch("/v1/preferences", headers=bruno, json={"notifiche": {"frequenza": "giornaliera"}})
    for i in range(3):
        await pubblica(client, anna, titolo=f"Oggetto {i}", descrizione=f"Descrizione {i}")
    assert (await notifiche(client, bruno))["notifiche"] == []
    domani = datetime.now(FUSO).replace(minute=0, second=0, microsecond=0) + timedelta(days=1)
    utc = lambda d: d.astimezone(timezone.utc).replace(tzinfo=None)  # noqa: E731
    async with fabbrica() as db:
        assert await genera_riepiloghi(db, utc(domani.replace(hour=7))) == 0  # prima delle 8: niente
        assert await genera_riepiloghi(db, utc(domani.replace(hour=9))) == 1
        assert await genera_riepiloghi(db, utc(domani.replace(hour=11))) == 0  # uno solo al giorno
        r = (await db.execute(select(Notification).where(Notification.tipo == "riepilogo"))).scalar_one()
        assert "3 novità" in r.titolo and r.testo.count(";") == 2


async def test_frequenza_controllata(client, anna, bruno):
    for i in range(8):
        await pubblica(client, anna, titolo=f"Oggetto {i}", descrizione=f"Descrizione {i}")
    stato = await notifiche(client, bruno)
    assert len(stato["notifiche"]) == 6 and stato["in_attesa"] == 2  # oltre la soglia oraria: nel riepilogo


async def test_revoca_consenso_cancella_avvisi_in_attesa(client, anna, bruno):
    await client.patch("/v1/preferences", headers=bruno, json={"notifiche": {"frequenza": "giornaliera"}})
    await pubblica(client, anna)
    await client.patch("/v1/preferences", headers=bruno, json={"consensi": {"notifiche": False}})
    assert (await notifiche(client, bruno))["in_attesa"] == 0


async def test_lettura_notifiche(client, anna, bruno):
    await pubblica(client, anna)
    assert (await client.get("/v1/notifications/conteggio", headers=bruno)).json()["non_lette"] == 1
    await client.post("/v1/notifications/lette", headers=bruno)
    assert (await client.get("/v1/notifications/conteggio", headers=bruno)).json()["non_lette"] == 0


# ── Foto ─────────────────────────────────────────────────────────────────────


def jpeg_con_gps() -> bytes:
    img = Image.new("RGB", (2400, 1200), (40, 140, 80))
    exif = Image.Exif()
    exif[0x8825] = {1: "N", 2: (45.0, 29.0, 12.0), 3: "E", 4: (9.0, 11.0, 23.0)}  # GPSInfo
    exif[0x010F] = "FotocameraDiProva"
    out = io.BytesIO()
    img.save(out, "JPEG", exif=exif)
    return out.getvalue()


async def test_foto_senza_gps_e_ridimensionata(client, anna, bruno):
    originale = jpeg_con_gps()
    assert Image.open(io.BytesIO(originale)).getexif().get(0x8825)
    r = await client.post("/v1/files", headers=anna, files={"file": ("foto.jpg", originale, "image/jpeg")})
    assert r.status_code == 201
    f = r.json()
    assert f["larghezza"] == 1600
    p = await pubblica(client, anna, foto_ids=[f["id"]])
    url = (await client.get(f"/v1/posts/{p['id']}", headers=bruno)).json()["foto"][0]["url"]
    img = await client.get(url)  # URL firmato: niente header di autenticazione
    assert img.status_code == 200 and img.headers["content-type"] == "image/jpeg"
    salvata = Image.open(io.BytesIO(img.content))
    assert not salvata.getexif()  # nessun metadato, nessuna posizione GPS


async def test_file_non_immagine_rifiutato(client, anna):
    r = await client.post("/v1/files", headers=anna, files={"file": ("virus.jpg", b"MZ\x90\x00finto eseguibile", "image/jpeg")})
    assert r.status_code == 415
    r = await client.post("/v1/files", headers=anna, files={"file": ("pagina.png", b"<svg onload=alert(1)>", "image/png")})
    assert r.status_code == 415


async def test_url_firmato_scaduto_o_alterato(client, anna):
    fid = (await client.post("/v1/files", headers=anna, files={"file": ("f.jpg", jpeg_con_gps(), "image/jpeg")})).json()["id"]
    scaduto = int(time.time()) - 10
    assert (await client.get(f"/v1/files/{fid}?exp={scaduto}&sig={foto._firma(fid, scaduto)}")).status_code == 403
    futuro = int(time.time()) + 600
    assert (await client.get(f"/v1/files/{fid}?exp={futuro}&sig=00000000000000000000000000000000")).status_code == 403


async def test_foto_di_altri_non_collegabile(client, anna, bruno):
    fid = (await client.post("/v1/files", headers=anna, files={"file": ("f.jpg", jpeg_con_gps(), "image/jpeg")})).json()["id"]
    r = await client.post("/v1/posts", headers=bruno, json={"tipo": "regalo", "categoria": "casa", "titolo": "Sedia",
                                                             "descrizione": "Sedia in legno", "foto_ids": [fid]})
    assert r.status_code == 422


# ── Account ──────────────────────────────────────────────────────────────────


async def test_esportazione_completa(client, anna, bruno):
    p = await pubblica(client, anna)
    await client.post("/v1/conversations", headers=bruno, json={"contesto_tipo": "post", "contesto_id": p["id"],
                                                               "messaggio": "Mi interessa molto"})
    r = await client.get("/v1/account/export?formato=json", headers=bruno)
    dati = r.json()
    assert dati["account"]["alias"] == "Bruno" and dati["conversazioni"][0]["messaggi"][0]["testo"] == "Mi interessa molto"
    assert dati["area"]["raggio_m"] == 1500 and len(dati["consensi"]) == 5
    md = await client.get("/v1/account/export?formato=markdown", headers=anna)
    assert "Passeggino in regalo" in md.text and "Roberto Di Flumeri" in md.text


async def test_cancellazione_account(client, anna, bruno, fabbrica):
    fid = (await client.post("/v1/files", headers=anna, files={"file": ("f.jpg", jpeg_con_gps(), "image/jpeg")})).json()["id"]
    p = await pubblica(client, anna, foto_ids=[fid])
    await client.post("/v1/conversations", headers=bruno, json={"contesto_tipo": "post", "contesto_id": p["id"], "messaggio": "Ciao"})
    cid = (await client.get("/v1/conversations", headers=bruno)).json()[0]["id"]
    await client.post(f"/v1/conversations/{cid}/messages", headers=anna, json={"testo": "Messaggio di Anna"})
    assert (await client.request("DELETE", "/v1/account", headers=anna,
                                 json={"password": "Sbagliata123", "conferma": "ELIMINA"})).status_code == 403
    assert (await client.request("DELETE", "/v1/account", headers=anna,
                                 json={"password": PASSWORD, "conferma": "elimina"})).status_code == 422
    r = await client.request("DELETE", "/v1/account", headers=anna, json={"password": PASSWORD, "conferma": "ELIMINA"})
    assert r.status_code == 200 and r.json()["ricevuta"]
    assert (await client.post("/v1/auth/login", json={"email": "anna@example.com", "password": PASSWORD})).status_code == 401
    async with fabbrica() as db:
        assert (await db.execute(select(User).where(User.alias == "Anna"))).first() is None
        assert (await db.execute(select(Post))).first() is None
        assert (await db.execute(select(Photo))).first() is None
        testi = [m.testo for m in (await db.execute(select(Message))).scalars()]
        assert testi == ["Ciao"]  # i messaggi di Anna sono stati cancellati, quelli di Bruno restano a lui
        conv = (await db.execute(select(Conversation))).scalar_one()
        assert conv.destinatario_id is None
    assert not (foto.cartella() / f"{fid}.jpg").exists()
    # Bruno vede ancora la conversazione, con un utente eliminato
    assert (await client.get("/v1/conversations", headers=bruno)).json()[0]["con"]["alias"] == "Utente eliminato"
