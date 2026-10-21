"""Gruppi, servizi locali e reputazione contestuale. © Roberto Di Flumeri"""

from datetime import datetime, timedelta

from sqlalchemy import update

from app.models import Event
from tests.conftest import ROMA, id_utente, pubblica, vicino


async def crea_gruppo(client, h, **campi):
    corpo = {"nome": "Cani dell'Isola", "descrizione": "Passeggiate e consigli per chi ha un cane in zona",
             "categoria": "animali"}
    corpo.update(campi)
    r = await client.post("/v1/groups", headers=h, json=corpo)
    assert r.status_code == 201, r.text
    return r.json()


async def test_gruppo_bacheca_solo_membri(client, anna, bruno, carla):
    g = await crea_gruppo(client, anna)
    assert g["mio_ruolo"] == "amministratore" and g["membri"] == 1
    assert [x["id"] for x in (await client.get("/v1/groups", headers=bruno)).json()] == [g["id"]]
    # non membro: niente bacheca e niente scrittura
    assert "bacheca" not in (await client.get(f"/v1/groups/{g['id']}", headers=bruno)).json()
    r = await client.post("/v1/posts", headers=bruno, json={"tipo": "gruppo", "categoria": "animali", "gruppo_id": g["id"],
                                                             "titolo": "Ciao", "descrizione": "Primo messaggio"})
    assert r.status_code == 403
    await client.post(f"/v1/groups/{g['id']}/join", headers=bruno)
    await client.post(f"/v1/groups/{g['id']}/join", headers=bruno)  # idempotente
    msg = await pubblica(client, bruno, tipo="gruppo", categoria="animali", gruppo_id=g["id"], titolo="Passeggiata sabato",
                         descrizione="Chi viene al parco sabato mattina con i cani?")
    d = (await client.get(f"/v1/groups/{g['id']}", headers=anna)).json()
    assert d["membri"] == 2 and [p["id"] for p in d["bacheca"]] == [msg["id"]]
    # i messaggi di gruppo non finiscono nel feed pubblico
    assert msg["id"] not in [e["id"] for e in (await client.get("/v1/feed", headers=carla)).json()["elementi"]]
    # elenco membri solo per i membri
    assert (await client.get(f"/v1/groups/{g['id']}/members", headers=carla)).status_code == 403
    assert len((await client.get(f"/v1/groups/{g['id']}/members", headers=bruno)).json()) == 2


async def test_admin_lascia_e_subentra_membro(client, anna, bruno):
    g = await crea_gruppo(client, anna)
    await client.post(f"/v1/groups/{g['id']}/join", headers=bruno)
    await client.delete(f"/v1/groups/{g['id']}/join", headers=anna)
    assert (await client.get(f"/v1/groups/{g['id']}", headers=bruno)).json()["mio_ruolo"] == "amministratore"


async def test_gruppo_lontano_non_accessibile(client, anna):
    g = await crea_gruppo(client, anna)
    lontano = await vicino(client, "r@example.com", "Romano", ROMA)
    assert (await client.get("/v1/groups", headers=lontano)).json() == []
    assert (await client.post(f"/v1/groups/{g['id']}/join", headers=lontano)).status_code == 403


async def test_admin_rimuove_messaggio_di_gruppo(client, anna, bruno):
    g = await crea_gruppo(client, anna)
    await client.post(f"/v1/groups/{g['id']}/join", headers=bruno)
    m = await pubblica(client, bruno, tipo="gruppo", categoria="animali", gruppo_id=g["id"], titolo="Spam", descrizione="Vendo cose")
    assert (await client.post(f"/v1/groups/{g['id']}/posts/{m['id']}/rimuovi", headers=anna)).status_code == 204
    assert (await client.get(f"/v1/groups/{g['id']}", headers=anna)).json()["bacheca"] == []


# ── Servizi ──────────────────────────────────────────────────────────────────


async def test_scheda_professionale_solo_per_profili_professionali(client, anna):
    corpo = {"nome": "Ferramenta Rossi", "categoria": "negozi", "descrizione": "Ferramenta storica con copia chiavi",
             "tipo_scheda": "professionale", "contatto": "02 1234567", "zona": "Isola"}
    assert (await client.post("/v1/services", headers=anna, json=corpo)).status_code == 403
    r = await client.post("/v1/services", headers=anna, json={**corpo, "tipo_scheda": "suggerito"})
    assert r.status_code == 201 and r.json()["etichetta_scheda"] == "Suggerito da un vicino"
    pro = await vicino(client, "pro@example.com", "Marta", tipo_profilo="professionale", attivita="Sartoria Marta")
    r = await client.post("/v1/services", headers=pro, json={**corpo, "nome": "Sartoria Marta", "categoria": "artigiani",
                                                              "descrizione": "Riparazioni e orli in giornata"})
    assert r.status_code == 201 and r.json()["etichetta_scheda"] == "Scheda del professionista"


async def test_servizio_duplicato_e_consigli(client, anna, bruno):
    corpo = {"nome": "Idraulico Gino", "categoria": "artigiani", "descrizione": "Interventi rapidi e prezzi onesti",
             "tipo_scheda": "suggerito", "zona": "Isola"}
    s = (await client.post("/v1/services", headers=anna, json=corpo)).json()
    assert (await client.post("/v1/services", headers=bruno, json={**corpo, "nome": "idraulico gino"})).status_code == 409
    r = await client.post(f"/v1/services/{s['id']}/consigli", headers=bruno, json={"commento": "Bravissimo e puntuale"})
    assert r.json()["consigli"] == 1 and r.json()["commenti"][0]["testo"] == "Bravissimo e puntuale"
    await client.post(f"/v1/services/{s['id']}/consigli", headers=bruno, json={"commento": "Confermo"})
    assert (await client.get(f"/v1/services/{s['id']}", headers=anna)).json()["consigli"] == 1  # un consiglio a testa
    lista = (await client.get("/v1/services", headers=bruno)).json()
    assert lista[0]["consigliato_da_me"] is True


async def test_descrizione_servizio_senza_recapiti_personali(client, anna):
    r = await client.post("/v1/services", headers=anna, json={
        "nome": "Baby sitter Laura", "categoria": "altro", "descrizione": "Bravissima, chiamatela al 347 1234567",
        "tipo_scheda": "suggerito", "zona": "Isola"})
    assert r.status_code == 422


# ── Reputazione ──────────────────────────────────────────────────────────────


async def _scambio_concluso(client, autore, altro):
    p = await pubblica(client, autore)
    await client.post("/v1/conversations", headers=altro, json={"contesto_tipo": "post", "contesto_id": p["id"],
                                                               "messaggio": "Lo prendo io!"})
    await client.post(f"/v1/posts/{p['id']}/stato", headers=autore,
                      json={"stato": "prenotato", "prenotato_da": await id_utente(client, altro)})
    return p


async def test_feedback_solo_dopo_scambio_concluso(client, anna, bruno, carla):
    p = await _scambio_concluso(client, anna, bruno)
    aid = await id_utente(client, anna)
    fb = {"destinatario_id": aid, "contesto_tipo": "post", "contesto_id": p["id"], "esito": "positivo",
          "commento": "Gentilissima e puntuale"}
    assert (await client.post("/v1/reputation", headers=bruno, json=fb)).status_code == 403  # ancora prenotato
    await client.post(f"/v1/posts/{p['id']}/stato", headers=anna, json={"stato": "chiuso"})
    assert any(x["contesto_id"] == p["id"] for x in (await client.get("/v1/reputation/da-lasciare", headers=bruno)).json())
    r = await client.post("/v1/reputation", headers=bruno, json=fb)
    assert r.status_code == 201 and r.json()["conteggi"]["positivo"] == 1
    assert (await client.post("/v1/reputation", headers=bruno, json=fb)).status_code == 409  # una volta sola
    assert (await client.post("/v1/reputation", headers=carla, json=fb)).status_code == 403  # estranea allo scambio
    profilo = (await client.get(f"/v1/users/{aid}", headers=carla)).json()
    assert profilo["reputazione"]["feedback"][0]["contesto"] == "Passeggino in regalo"
    assert "punteggio" in profilo["reputazione"]["spiegazione"]
    assert "score" not in str(profilo) and "media" not in profilo["reputazione"]


async def test_feedback_dopo_evento_concluso(client, anna, bruno, fabbrica):
    e = (await client.post("/v1/events", headers=anna, json={
        "titolo": "Pulizia del parco", "descrizione": "Raccogliamo insieme i rifiuti nel parco", "categoria": "volontariato",
        "inizio": (datetime.utcnow() + timedelta(hours=2)).isoformat(), "luogo": "Ingresso del parco"})).json()
    await client.post(f"/v1/events/{e['id']}/join", headers=bruno)
    fb = {"destinatario_id": await id_utente(client, anna), "contesto_tipo": "evento", "contesto_id": e["id"], "esito": "positivo"}
    assert (await client.post("/v1/reputation", headers=bruno, json=fb)).status_code == 403  # non ancora concluso
    async with fabbrica() as db:
        await db.execute(update(Event).where(Event.id == e["id"]).values(inizio=datetime.utcnow() - timedelta(hours=5)))
        await db.commit()
    assert (await client.post("/v1/reputation", headers=bruno, json=fb)).status_code == 201
    fb2 = {**fb, "destinatario_id": await id_utente(client, bruno)}
    assert (await client.post("/v1/reputation", headers=anna, json=fb2)).status_code == 201


async def test_feedback_a_se_stessi_e_con_recapiti(client, anna, bruno):
    p = await _scambio_concluso(client, anna, bruno)
    await client.post(f"/v1/posts/{p['id']}/stato", headers=anna, json={"stato": "chiuso"})
    fb = {"destinatario_id": await id_utente(client, bruno), "contesto_tipo": "post", "contesto_id": p["id"],
          "esito": "positivo", "commento": "Scrivetegli a bruno@gmail.com"}
    assert (await client.post("/v1/reputation", headers=anna, json=fb)).status_code == 422
    assert (await client.post("/v1/reputation", headers=anna, json={**fb, "destinatario_id": await id_utente(client, anna),
                                                                     "commento": None})).status_code == 422


async def test_profilo_pubblico_senza_dati_privati(client, anna, bruno):
    profilo = (await client.get(f"/v1/users/{await id_utente(client, anna)}", headers=bruno)).json()
    assert set(profilo) == {"id", "alias", "tipo_profilo", "attivita", "bio", "iscritto_dal", "reputazione"}


async def test_cancellazione_account_e_gruppi(client, anna, bruno):
    from tests.conftest import PASSWORD
    g = await crea_gruppo(client, anna)
    solo = await crea_gruppo(client, anna, nome="Gruppo di una persona sola")
    await client.post(f"/v1/groups/{g['id']}/join", headers=bruno)
    await client.request("DELETE", "/v1/account", headers=anna, json={"password": PASSWORD, "conferma": "ELIMINA"})
    elenco = {x["id"]: x for x in (await client.get("/v1/groups", headers=bruno)).json()}
    assert elenco[g["id"]]["mio_ruolo"] == "amministratore"  # Bruno eredita l'amministrazione
    assert solo["id"] not in elenco  # il gruppo rimasto senza membri viene eliminato
