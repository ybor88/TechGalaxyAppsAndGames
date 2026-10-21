"""Eventi (capienza, lista d'attesa, idempotenza) e chat privata contestuale. © Roberto Di Flumeri"""

import asyncio
from datetime import datetime, timedelta

from sqlalchemy import text

from tests.conftest import id_utente, pubblica, vicino


def evento(**campi):
    base = {"titolo": "Partita di calcetto", "descrizione": "Partita amatoriale, portate scarpe da calcetto",
            "categoria": "sport", "inizio": (datetime.utcnow() + timedelta(days=2)).isoformat(),
            "luogo": "Campetto del parco", "capienza": 2}
    base.update(campi)
    return base


async def crea_evento(client, h, **campi):
    r = await client.post("/v1/events", headers=h, json=evento(**campi))
    assert r.status_code == 201, r.text
    return r.json()


async def test_capienza_e_lista_attesa_con_promozione(client, anna, bruno):
    dino = await vicino(client, "dino@example.com", "Dino")
    e = await crea_evento(client, anna)
    assert (await client.post(f"/v1/events/{e['id']}/join", headers=bruno)).json()["mia_partecipazione"] == "confermato"
    carla_vicina = await vicino(client, "carla2@example.com", "Carla")
    assert (await client.post(f"/v1/events/{e['id']}/join", headers=carla_vicina)).json()["mia_partecipazione"] == "confermato"
    r = (await client.post(f"/v1/events/{e['id']}/join", headers=dino)).json()
    assert r["mia_partecipazione"] == "lista_attesa" and r["posti_liberi"] == 0
    # Bruno rinuncia: Dino viene promosso
    await client.delete(f"/v1/events/{e['id']}/join", headers=bruno)
    d = (await client.get(f"/v1/events/{e['id']}", headers=dino)).json()
    assert d["mia_partecipazione"] == "confermato" and d["confermati"] == 2
    notif = (await client.get("/v1/notifications", headers=dino)).json()["notifiche"]
    assert any("liberato un posto" in n["titolo"] for n in notif)


async def test_partecipazione_idempotente(client, anna, bruno):
    e = await crea_evento(client, anna, capienza=None)
    risposte = await asyncio.gather(*[client.post(f"/v1/events/{e['id']}/join", headers=bruno) for _ in range(3)])
    assert all(r.status_code == 200 for r in risposte)
    assert (await client.get(f"/v1/events/{e['id']}", headers=anna)).json()["confermati"] == 1


async def test_organizzatore_vede_partecipanti_e_approva(client, anna, bruno):
    e = await crea_evento(client, anna, modalita="su_approvazione", capienza=5)
    assert (await client.post(f"/v1/events/{e['id']}/join", headers=bruno)).json()["mia_partecipazione"] == "in_attesa"
    dettaglio = (await client.get(f"/v1/events/{e['id']}", headers=anna)).json()
    assert dettaglio["partecipanti"][0]["alias"] == "Bruno"
    assert "partecipanti" not in (await client.get(f"/v1/events/{e['id']}", headers=bruno)).json()
    bid = await id_utente(client, bruno)
    r = await client.post(f"/v1/events/{e['id']}/partecipanti/{bid}", headers=anna, json={"stato": "confermato"})
    assert r.json()["confermati"] == 1
    assert (await client.post(f"/v1/events/{e['id']}/partecipanti/{bid}", headers=bruno,
                              json={"stato": "confermato"})).status_code == 404


async def test_rifiutato_non_rientra(client, anna, bruno):
    e = await crea_evento(client, anna, modalita="su_approvazione")
    await client.post(f"/v1/events/{e['id']}/join", headers=bruno)
    await client.post(f"/v1/events/{e['id']}/partecipanti/{await id_utente(client, bruno)}", headers=anna, json={"stato": "rifiutato"})
    assert (await client.post(f"/v1/events/{e['id']}/join", headers=bruno)).status_code == 409


async def test_aggiornamenti_e_annullamento_avvisano(client, anna, bruno):
    e = await crea_evento(client, anna)
    await client.post(f"/v1/events/{e['id']}/join", headers=bruno)
    await client.post(f"/v1/events/{e['id']}/aggiornamenti", headers=anna, json={"testo": "Si gioca alle 19 invece che alle 18"})
    assert (await client.post(f"/v1/events/{e['id']}/aggiornamenti", headers=bruno, json={"testo": "Ciao"})).status_code == 404
    await client.post(f"/v1/events/{e['id']}/annulla", headers=anna)
    titoli = [n["titolo"] for n in (await client.get("/v1/notifications", headers=bruno)).json()["notifiche"]]
    assert any("Novità" in t for t in titoli) and any("annullato" in t for t in titoli)
    assert (await client.post(f"/v1/events/{e['id']}/join", headers=bruno)).status_code == 409


async def test_capienza_non_sotto_i_confermati(client, anna, bruno):
    e = await crea_evento(client, anna, capienza=3)
    await client.post(f"/v1/events/{e['id']}/join", headers=bruno)
    assert (await client.patch(f"/v1/events/{e['id']}", headers=anna, json={"capienza": 1})).status_code == 200
    altro = await vicino(client, "e@example.com", "Elia")
    await client.post(f"/v1/events/{e['id']}/join", headers=altro)
    assert (await client.patch(f"/v1/events/{e['id']}", headers=anna, json={"capienza": 1})).status_code == 200  # invariata
    r = await client.patch(f"/v1/events/{e['id']}", headers=anna, json={"capienza": 2})
    assert r.json()["confermati"] == 2  # aumentando la capienza Elia viene promosso


async def test_evento_nel_passato_e_luogo_preciso_rifiutati(client, anna):
    r = await client.post("/v1/events", headers=anna, json=evento(inizio=(datetime.utcnow() - timedelta(hours=1)).isoformat()))
    assert r.status_code == 422
    r = await client.post("/v1/events", headers=anna, json=evento(luogo="via Borsieri 12"))
    assert r.status_code == 422 and "approssimativo" in r.text


async def test_evento_nel_feed(client, anna, bruno):
    e = await crea_evento(client, anna)
    feed = (await client.get("/v1/feed?tipo=evento", headers=bruno)).json()["elementi"]
    assert [x["id"] for x in feed] == [e["id"]] and feed[0]["genere"] == "evento"


# ── Chat ─────────────────────────────────────────────────────────────────────


async def test_chat_contestuale_e_privata(client, anna, bruno, carla, fabbrica):
    p = await pubblica(client, anna)
    r = await client.post("/v1/conversations", headers=bruno, json={"contesto_tipo": "post", "contesto_id": p["id"],
                                                                    "messaggio": "Ciao, è ancora disponibile?"})
    assert r.status_code == 201
    conv = r.json()
    assert conv["con"]["alias"] == "Anna" and conv["messaggi"][0]["mio"] is True
    # una seconda apertura riusa la stessa conversazione
    r2 = await client.post("/v1/conversations", headers=bruno, json={"contesto_tipo": "post", "contesto_id": p["id"],
                                                                     "messaggio": "Posso passare domani"})
    assert r2.json()["id"] == conv["id"] and len(r2.json()["messaggi"]) == 2
    # chi non partecipa non legge
    assert (await client.get(f"/v1/conversations/{conv['id']}", headers=carla)).status_code == 404
    # Anna vede il non letto, poi legge
    assert (await client.get("/v1/conversations/non-lette", headers=anna)).json()["non_lette"] == 2
    letta = (await client.get(f"/v1/conversations/{conv['id']}", headers=anna)).json()
    assert [m["mio"] for m in letta["messaggi"]] == [False, False]
    assert (await client.get("/v1/conversations/non-lette", headers=anna)).json()["non_lette"] == 0
    # cifratura a riposo
    async with fabbrica() as db:
        grezzo = (await db.execute(text("SELECT testo FROM messaggi"))).scalars().first()
        assert "disponibile" not in grezzo and grezzo.startswith("gAAAA")  # token Fernet


async def test_limite_contatto_senza_risposta(client, anna, bruno):
    p = await pubblica(client, anna)
    r = await client.post("/v1/conversations", headers=bruno, json={"contesto_tipo": "post", "contesto_id": p["id"], "messaggio": "1"})
    cid = r.json()["id"]
    for t in ("2", "3"):
        assert (await client.post(f"/v1/conversations/{cid}/messages", headers=bruno, json={"testo": t})).status_code == 201
    assert (await client.post(f"/v1/conversations/{cid}/messages", headers=bruno, json={"testo": "4"})).status_code == 429
    await client.post(f"/v1/conversations/{cid}/messages", headers=anna, json={"testo": "Eccomi!"})
    assert (await client.post(f"/v1/conversations/{cid}/messages", headers=bruno, json={"testo": "Grazie"})).status_code == 201


async def test_blocco_ferma_la_chat(client, anna, bruno):
    p = await pubblica(client, anna)
    cid = (await client.post("/v1/conversations", headers=bruno, json={"contesto_tipo": "post", "contesto_id": p["id"],
                                                                      "messaggio": "Ciao"})).json()["id"]
    await client.post(f"/v1/blocks/{await id_utente(client, bruno)}", headers=anna)
    assert (await client.post(f"/v1/conversations/{cid}/messages", headers=bruno, json={"testo": "Ci sei?"})).status_code == 403
    assert (await client.get("/v1/conversations", headers=bruno)).json()[0]["stato"] == "bloccata"


async def test_non_si_scrive_a_se_stessi_ne_a_chi_rifiuta(client, anna, bruno):
    p = await pubblica(client, anna)
    r = await client.post("/v1/conversations", headers=anna, json={"contesto_tipo": "post", "contesto_id": p["id"], "messaggio": "io"})
    assert r.status_code == 409
    await client.patch("/v1/preferences", headers=anna, json={"accetta_messaggi": "nessuno"})
    r = await client.post("/v1/conversations", headers=bruno, json={"contesto_tipo": "post", "contesto_id": p["id"], "messaggio": "Ciao"})
    assert r.status_code == 403


async def test_contenuto_chiuso_non_accetta_contatti(client, anna, bruno):
    p = await pubblica(client, anna)
    await client.post(f"/v1/posts/{p['id']}/stato", headers=anna, json={"stato": "chiuso"})
    r = await client.post("/v1/conversations", headers=bruno, json={"contesto_tipo": "post", "contesto_id": p["id"], "messaggio": "Ciao"})
    assert r.status_code == 409


async def test_chat_su_evento(client, anna, bruno):
    e = await crea_evento(client, anna)
    r = await client.post("/v1/conversations", headers=bruno, json={"contesto_tipo": "evento", "contesto_id": e["id"],
                                                                    "messaggio": "Posso portare un amico?"})
    assert r.status_code == 201 and r.json()["contesto_titolo"] == "Partita di calcetto"


async def test_quota_nuove_chat(client, anna, bruno):
    ids = [(await pubblica(client, anna, titolo=f"Oggetto {i}", descrizione=f"Descrizione {i}"))["id"] for i in range(10)]
    for pid in ids:
        r = await client.post("/v1/conversations", headers=bruno, json={"contesto_tipo": "post", "contesto_id": pid, "messaggio": "Ciao"})
        assert r.status_code == 201
    nuovo = await pubblica(client, await vicino(client, "f@example.com", "Fabio"), titolo="Lampada", descrizione="Lampada da tavolo")
    r = await client.post("/v1/conversations", headers=bruno, json={"contesto_tipo": "post", "contesto_id": nuovo["id"], "messaggio": "Ciao"})
    assert r.status_code == 429


async def test_date_con_fuso_orario_dal_browser(client, anna):
    inizio = (datetime.utcnow() + timedelta(days=1)).replace(microsecond=0).isoformat() + "Z"
    e = await crea_evento(client, anna, inizio=inizio)
    assert e["inizio"] == inizio.removesuffix("Z")
    p = await pubblica(client, anna, tipo="aiuto", categoria="spesa", titolo="Spesa", descrizione="Serve aiuto per la spesa",
                       scadenza=inizio.replace("Z", "+02:00"), disponibilita="sabato")
    assert p["scadenza"] < e["inizio"]  # +02:00 convertito in UTC
