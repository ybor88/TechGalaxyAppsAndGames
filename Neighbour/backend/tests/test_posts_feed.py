"""Annunci, richieste di aiuto, segnalazioni, feed locale e mappa. © Roberto Di Flumeri"""

from datetime import datetime, timedelta

from app import geo
from tests.conftest import ISOLA, ROMA, id_utente, pubblica, vicino


def tra(giorni=3):
    return (datetime.utcnow() + timedelta(days=giorni)).isoformat()


async def test_pubblica_e_vede_il_vicino(client, anna, bruno):
    p = await pubblica(client, anna)
    assert p["stato"] == "pubblicato" and p["autore"]["alias"] == "Anna"
    feed = (await client.get("/v1/feed", headers=bruno)).json()
    assert [e["id"] for e in feed["elementi"]] == [p["id"]]
    assert feed["elementi"][0]["posizione"]["distanza"].startswith("circa")


async def test_posizione_contenuto_approssimata(client, anna):
    p = await pubblica(client, anna, lat=45.487123, lon=9.190456)
    gh, lat, lon = geo.approssima(45.487123, 9.190456, geo.PRECISIONE_LUOGO)
    assert (p["posizione"]["lat"], p["posizione"]["lon"]) == (lat, lon)


async def test_raggio_di_chi_pubblica_e_di_chi_legge(client, anna, bruno, carla):
    # Anna pubblica con raggio 500 m: Bruno (900 m) non lo vede
    piccolo = await pubblica(client, anna, titolo="Libri per bambini", raggio_m=500)
    assert piccolo["id"] not in [e["id"] for e in (await client.get("/v1/feed", headers=bruno)).json()["elementi"]]
    # raggio 5 km: lo vede Carla solo se anche lei legge abbastanza lontano
    grande = await pubblica(client, anna, titolo="Bicicletta da bambino", raggio_m=5000)
    vista_carla = (await client.get("/v1/feed", headers=carla)).json()["elementi"]
    assert grande["id"] not in [e["id"] for e in vista_carla]  # Carla legge entro 1,5 km
    vista_carla = (await client.get("/v1/feed?raggio_m=4000", headers=carla)).json()["elementi"]
    assert grande["id"] in [e["id"] for e in vista_carla]


async def test_dettaglio_fuori_zona_non_visibile(client, anna):
    p = await pubblica(client, anna)
    lontano = await vicino(client, "roma@example.com", "Romano", ROMA)
    assert (await client.get(f"/v1/posts/{p['id']}", headers=lontano)).status_code == 404


async def test_bloccati_esclusi_in_entrambe_le_direzioni(client, anna, bruno):
    p_anna = await pubblica(client, anna)
    p_bruno = await pubblica(client, bruno, titolo="Cerco trapano", tipo="richiesta", categoria="casa",
                             descrizione="Mi serve un trapano per un pomeriggio.")
    await client.post(f"/v1/blocks/{await id_utente(client, bruno)}", headers=anna)
    assert p_bruno["id"] not in [e["id"] for e in (await client.get("/v1/feed", headers=anna)).json()["elementi"]]
    assert p_anna["id"] not in [e["id"] for e in (await client.get("/v1/feed", headers=bruno)).json()["elementi"]]
    assert (await client.get(f"/v1/posts/{p_anna['id']}", headers=bruno)).status_code == 404


async def test_cursor_pagination_senza_duplicati(client, anna, bruno):
    ids = [(await pubblica(client, anna, titolo=f"Oggetto numero {i}", descrizione=f"Descrizione {i} dell'oggetto"))["id"]
           for i in range(7)]
    visti, cursore = [], None
    while True:
        url = "/v1/feed?limite=3" + (f"&cursore={cursore}" if cursore else "")
        r = (await client.get(url, headers=bruno)).json()
        visti += [e["id"] for e in r["elementi"]]
        cursore = r["cursore"]
        if not cursore:
            break
    assert visti == list(reversed(ids))
    assert (await client.get("/v1/feed?cursore=rotto", headers=bruno)).status_code == 422


async def test_filtri_tipo_categoria_testo(client, anna, bruno):
    await pubblica(client, anna)
    await pubblica(client, anna, tipo="aiuto", categoria="spesa", titolo="Spesa per mia nonna",
                   descrizione="Qualcuno può portare la spesa giovedì?", scadenza=tra(), disponibilita="giovedì mattina")
    await pubblica(client, anna, tipo="segnalazione", categoria="viabilita", titolo="Buca in strada",
                   descrizione="Grossa buca vicino alla fermata del tram.")

    async def tipi_di(q):
        return [e["tipo"] for e in (await client.get(f"/v1/feed?{q}", headers=bruno)).json()["elementi"]]

    assert await tipi_di("tipo=aiuto") == ["aiuto"]
    assert await tipi_di("tipo=mercatino") == ["regalo"]
    assert await tipi_di("categoria=viabilita") == ["segnalazione"]
    assert await tipi_di("q=nonna") == ["aiuto"]
    assert sorted(await tipi_di("tipo=tutti")) == ["aiuto", "regalo", "segnalazione"]


async def test_aiuto_richiede_scadenza_e_disponibilita(client, anna):
    corpo = {"tipo": "aiuto", "categoria": "spesa", "titolo": "Spesa", "descrizione": "Mi serve aiuto con la spesa"}
    r = await client.post("/v1/posts", headers=anna, json=corpo)
    assert r.status_code == 422 and "entro quando" in r.text
    r = await client.post("/v1/posts", headers=anna, json={**corpo, "scadenza": tra()})
    assert r.status_code == 422 and "disponibile" in r.text
    r = await client.post("/v1/posts", headers=anna, json={**corpo, "scadenza": (datetime.utcnow() - timedelta(days=1)).isoformat(),
                                                             "disponibilita": "sabato"})
    assert r.status_code == 422 and "futuro" in r.text


async def test_categoria_coerente_con_tipo(client, anna):
    r = await client.post("/v1/posts", headers=anna, json={"tipo": "regalo", "categoria": "viabilita",
                                                            "titolo": "Regalo", "descrizione": "Un regalo per voi"})
    assert r.status_code == 422


async def test_dati_personali_bloccati_nel_testo_pubblico(client, anna):
    for testo in ("Chiamami al 333 1234567", "scrivimi a anna.rossi@gmail.com", "ritiro in via Borsieri 12"):
        r = await client.post("/v1/posts", headers=anna, json={"tipo": "regalo", "categoria": "casa",
                                                                "titolo": "Sedia", "descrizione": testo})
        assert r.status_code == 422 and "chat" in r.text, testo


async def test_avviso_emergenza(client, anna):
    p = await pubblica(client, anna, tipo="segnalazione", categoria="sicurezza", titolo="Fuga di gas in cortile",
                       descrizione="Si sente odore di gas vicino ai box")
    assert "112" in p["avviso_emergenza"]


async def test_contenuto_ripetuto_e_quota(client, anna):
    await pubblica(client, anna)
    r = await client.post("/v1/posts", headers=anna, json={"tipo": "regalo", "categoria": "bambini",
                                                            "titolo": "PASSEGGINO in regalo!",
                                                            "descrizione": "Passeggino leggero in buono stato, ritiro in zona"})
    assert r.status_code == 409
    for i in range(9):
        await pubblica(client, anna, titolo=f"Oggetto {i}", descrizione=f"Descrizione numero {i}")
    r = await client.post("/v1/posts", headers=anna, json={"tipo": "regalo", "categoria": "casa",
                                                            "titolo": "Undicesimo", "descrizione": "Oltre la quota"})
    assert r.status_code == 429


async def test_bozza_invisibile_poi_pubblicata(client, anna, bruno):
    p = await pubblica(client, anna, bozza=True)
    assert p["stato"] == "bozza"
    assert (await client.get(f"/v1/posts/{p['id']}", headers=bruno)).status_code == 404
    assert (await client.get("/v1/feed", headers=bruno)).json()["elementi"] == []
    r = await client.post(f"/v1/posts/{p['id']}/stato", headers=anna, json={"stato": "pubblicato"})
    assert r.status_code == 200
    assert len((await client.get("/v1/feed", headers=bruno)).json()["elementi"]) == 1


async def test_transizioni_di_stato(client, anna, bruno):
    p = await pubblica(client, anna)
    pid = p["id"]
    # prenotato solo per chi ha scritto in chat
    r = await client.post(f"/v1/posts/{pid}/stato", headers=anna, json={"stato": "prenotato", "prenotato_da": "nessuno"})
    assert r.status_code == 422
    await client.post("/v1/conversations", headers=bruno, json={"contesto_tipo": "post", "contesto_id": pid,
                                                               "messaggio": "Ciao, mi interessa!"})
    bid = await id_utente(client, bruno)
    r = await client.post(f"/v1/posts/{pid}/stato", headers=anna, json={"stato": "prenotato", "prenotato_da": bid})
    assert r.status_code == 200 and r.json()["stato"] == "prenotato"
    # idempotente
    assert (await client.post(f"/v1/posts/{pid}/stato", headers=anna, json={"stato": "prenotato", "prenotato_da": bid})).status_code == 200
    # un annuncio non diventa "risolto": quello è lo stato finale delle segnalazioni
    assert (await client.post(f"/v1/posts/{pid}/stato", headers=anna, json={"stato": "risolto"})).status_code == 409
    assert (await client.post(f"/v1/posts/{pid}/stato", headers=anna, json={"stato": "chiuso"})).status_code == 200
    assert (await client.post(f"/v1/posts/{pid}/stato", headers=anna, json={"stato": "pubblicato"})).status_code == 409
    # chi non è l'autore non può cambiare stato
    assert (await client.post(f"/v1/posts/{pid}/stato", headers=bruno, json={"stato": "pubblicato"})).status_code == 404
    # un contenuto chiuso non si modifica
    assert (await client.patch(f"/v1/posts/{pid}", headers=anna, json={"titolo": "Nuovo titolo"})).status_code == 409


async def test_segnalazione_risolta(client, anna):
    s = await pubblica(client, anna, tipo="segnalazione", categoria="smarrito", titolo="Trovate chiavi",
                       descrizione="Trovato un mazzo di chiavi alla fermata")
    assert (await client.post(f"/v1/posts/{s['id']}/stato", headers=anna, json={"stato": "chiuso"})).status_code == 409
    r = await client.post(f"/v1/posts/{s['id']}/stato", headers=anna, json={"stato": "risolto"})
    assert r.json()["stato"] == "risolto"


async def test_modifica_ed_eliminazione_solo_autore(client, anna, bruno):
    p = await pubblica(client, anna)
    assert (await client.patch(f"/v1/posts/{p['id']}", headers=bruno, json={"titolo": "Rubato"})).status_code == 404
    r = await client.patch(f"/v1/posts/{p['id']}", headers=anna, json={"titolo": "Passeggino e seggiolino"})
    assert r.json()["titolo"] == "Passeggino e seggiolino"
    assert (await client.delete(f"/v1/posts/{p['id']}", headers=bruno)).status_code == 404
    assert (await client.delete(f"/v1/posts/{p['id']}", headers=anna)).status_code == 204
    assert (await client.get(f"/v1/posts/{p['id']}", headers=anna)).status_code == 404


async def test_mappa_senza_posizioni_delle_persone(client, anna, bruno):
    await pubblica(client, anna)
    m = (await client.get("/v1/map", headers=bruno)).json()
    assert len(m["punti"]) == 1
    punto = m["punti"][0]
    # il punto è il centro della cella del contenuto, non la posizione di Anna
    gh = geo.codifica(punto["lat"], punto["lon"], geo.PRECISIONE_LUOGO)
    assert geo.centro(gh) == (punto["lat"], punto["lon"])
    assert "indirizzo" not in str(punto).lower()
    assert set(m["centro"]) == {"lat", "lon", "nome"}


async def test_per_te_richiede_consenso(client, anna):
    from tests.conftest import TUTTI
    senza = await vicino(client, "np@example.com", "Nico", ISOLA, consensi={**TUTTI, "profilazione": False})
    assert (await client.get("/v1/feed?per_te=true", headers=senza)).status_code == 403
    assert (await client.get("/v1/feed?per_te=true", headers=anna)).status_code == 200


async def test_scaduti_esclusi(client, anna, bruno, fabbrica):
    from sqlalchemy import update

    from app.models import Post
    p = await pubblica(client, anna, scadenza=tra(1))
    async with fabbrica() as db:
        await db.execute(update(Post).where(Post.id == p["id"]).values(scadenza=datetime.utcnow() - timedelta(hours=1)))
        await db.commit()
    assert (await client.get("/v1/feed", headers=bruno)).json()["elementi"] == []
