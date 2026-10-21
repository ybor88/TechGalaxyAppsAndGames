"""Segnalazioni, decisioni di moderazione, sospensione in sola lettura e ricorsi. © Roberto Di Flumeri"""

from sqlalchemy import update

from app.models import User
from tests.conftest import id_utente, pubblica, vicino


async def moderatore(client, fabbrica, email="mod@example.com", alias="Moderatore"):
    h = await vicino(client, email, alias)
    uid = await id_utente(client, h)
    async with fabbrica() as db:
        await db.execute(update(User).where(User.id == uid).values(ruolo="moderatore"))
        await db.commit()
    return h


async def segnala(client, h, oggetto_tipo, oggetto_id, motivo="spam", **kw):
    r = await client.post("/v1/reports", headers=h, json={"oggetto_tipo": oggetto_tipo, "oggetto_id": oggetto_id,
                                                          "motivo": motivo, **kw})
    assert r.status_code == 201, r.text
    return r.json()


async def test_segnalazione_e_rimozione(client, fabbrica, anna, bruno):
    mod = await moderatore(client, fabbrica)
    p = await pubblica(client, anna, titolo="Offerta imperdibile", descrizione="Compra i miei corsi online")
    r1 = await segnala(client, bruno, "post", p["id"], dettagli="Pubblicità ripetuta")
    # segnalazione doppia della stessa persona: idempotente
    assert (await segnala(client, bruno, "post", p["id"]))["id"] == r1["id"]
    assert (await client.get("/v1/admin/reports", headers=bruno)).status_code == 403
    coda = (await client.get("/v1/admin/reports", headers=mod)).json()
    assert len(coda) == 1 and coda[0]["evidenze"]["titolo"] == "Offerta imperdibile"
    assert coda[0]["motivi"] == ["Spam o pubblicità ripetitiva"] and coda[0]["commenti"] == ["Pubblicità ripetuta"]
    r = await client.post(f"/v1/admin/reports/{r1['id']}/decidi", headers=mod,
                          json={"decisione": "rimuovi_contenuto", "nota": "Pubblicità non consentita"})
    assert r.status_code == 200
    assert (await client.get(f"/v1/posts/{p['id']}", headers=bruno)).status_code == 404
    assert (await client.get("/v1/feed", headers=bruno)).json()["elementi"] == []
    assert (await client.post(f"/v1/admin/reports/{r1['id']}/decidi", headers=mod,
                              json={"decisione": "avviso"})).status_code == 409
    # esiti notificati a chi segnala e a chi è segnalato
    assert any(n["tipo"] == "moderazione" for n in (await client.get("/v1/notifications", headers=bruno)).json()["notifiche"])
    ricevute = (await client.get("/v1/reports/me", headers=anna)).json()["ricevute"]
    assert ricevute[0]["decisione"] == "rimuovi_contenuto" and ricevute[0]["ricorribile"] is True


async def test_non_si_segnala_se_stessi_e_motivo_codificato(client, anna, bruno):
    p = await pubblica(client, anna)
    r = await client.post("/v1/reports", headers=anna, json={"oggetto_tipo": "post", "oggetto_id": p["id"], "motivo": "spam"})
    assert r.status_code == 422
    r = await client.post("/v1/reports", headers=bruno, json={"oggetto_tipo": "post", "oggetto_id": p["id"], "motivo": "antipatia"})
    assert r.status_code == 422


async def test_segnalazione_messaggio_con_blocco_immediato(client, fabbrica, anna, bruno):
    mod = await moderatore(client, fabbrica)
    p = await pubblica(client, anna)
    conv = (await client.post("/v1/conversations", headers=bruno, json={"contesto_tipo": "post", "contesto_id": p["id"],
                                                                       "messaggio": "Sei proprio una stupida"})).json()
    mid = conv["messaggi"][0]["id"]
    # il messaggio si può segnalare solo da chi l'ha ricevuto
    assert (await client.post("/v1/reports", headers=bruno, json={"oggetto_tipo": "messaggio", "oggetto_id": mid,
                                                                   "motivo": "molestie"})).status_code == 404
    rep = await segnala(client, anna, "messaggio", mid, motivo="molestie", blocca_utente=True)
    assert rep["bloccato"] is True
    assert (await client.post(f"/v1/conversations/{conv['id']}/messages", headers=bruno, json={"testo": "?"})).status_code == 403
    coda = (await client.get("/v1/admin/reports", headers=mod)).json()
    assert coda[0]["evidenze"]["testo"] == "Sei proprio una stupida"
    await client.post(f"/v1/admin/reports/{rep['id']}/decidi", headers=mod, json={"decisione": "rimuovi_contenuto"})
    testi = [m["testo"] for m in (await client.get(f"/v1/conversations/{conv['id']}", headers=anna)).json()["messaggi"]]
    assert testi == ["[messaggio rimosso dalla moderazione]"]


async def test_sospensione_in_sola_lettura_e_ricorso(client, fabbrica, anna, bruno):
    mod = await moderatore(client, fabbrica)
    mod2 = await moderatore(client, fabbrica, "mod2@example.com", "Seconda moderatrice")
    p = await pubblica(client, anna)
    rep = await segnala(client, bruno, "post", p["id"], motivo="truffa")
    await client.post(f"/v1/admin/reports/{rep['id']}/decidi", headers=mod, json={"decisione": "sospendi_7_giorni"})
    # sospesa: legge ma non pubblica
    assert (await client.get("/v1/feed", headers=anna)).status_code == 200
    r = await client.post("/v1/posts", headers=anna, json={"tipo": "regalo", "categoria": "casa", "titolo": "Sedia",
                                                            "descrizione": "Sedia in legno"})
    assert r.status_code == 403 and "sospeso" in r.text
    # ricorso: una sola volta, deciso da un altro moderatore
    r = await client.post(f"/v1/reports/{rep['id']}/ricorso", headers=anna, json={"testo": "Era un vero regalo, nessuna truffa"})
    assert r.status_code == 200 and r.json()["ricorso_stato"] == "aperto"
    assert (await client.post(f"/v1/reports/{rep['id']}/ricorso", headers=anna,
                              json={"testo": "Secondo tentativo di ricorso"})).status_code == 409
    assert (await client.post(f"/v1/admin/reports/{rep['id']}/ricorso", headers=mod, json={"esito": "accolto"})).status_code == 409
    assert len((await client.get("/v1/admin/ricorsi", headers=mod2)).json()) == 1
    assert (await client.post(f"/v1/admin/reports/{rep['id']}/ricorso", headers=mod2, json={"esito": "accolto"})).status_code == 200
    # sospensione annullata
    await pubblica(client, anna, titolo="Tavolo", descrizione="Tavolo da cucina")


async def test_blocco_account_e_recidivi(client, fabbrica, anna, bruno):
    mod = await moderatore(client, fabbrica)
    for i in range(2):
        p = await pubblica(client, anna, titolo=f"Annuncio {i}", descrizione=f"Testo {i}")
        rep = await segnala(client, bruno, "post", p["id"])
        await client.post(f"/v1/admin/reports/{rep['id']}/decidi", headers=mod, json={"decisione": "avviso"})
    recidivi = (await client.get("/v1/admin/utenti-recidivi", headers=mod)).json()
    assert recidivi[0]["alias"] == "Anna" and recidivi[0]["violazioni"] == 2
    p = await pubblica(client, anna, titolo="Terzo", descrizione="Terzo annuncio")
    rep = await segnala(client, bruno, "post", p["id"])
    assert (await client.get("/v1/admin/reports", headers=mod)).json()[0]["violazioni_precedenti"] == 2
    await client.post(f"/v1/admin/reports/{rep['id']}/decidi", headers=mod, json={"decisione": "blocca_utente"})
    assert (await client.get("/v1/feed", headers=anna)).status_code == 403
    assert (await client.post("/v1/auth/login", json={"email": "anna@example.com", "password": "Password123vicini"})).status_code == 403
    await client.post(f"/v1/admin/utenti/{recidivi[0]['id']}/sblocca", headers=mod)
    assert (await client.post("/v1/auth/login", json={"email": "anna@example.com", "password": "Password123vicini"})).status_code == 200


async def test_archiviazione_senza_violazione(client, fabbrica, anna, bruno):
    mod = await moderatore(client, fabbrica)
    p = await pubblica(client, anna)
    rep = await segnala(client, bruno, "post", p["id"], motivo="altro")
    await client.post(f"/v1/admin/reports/{rep['id']}/decidi", headers=mod, json={"decisione": "nessuna_violazione"})
    assert (await client.get(f"/v1/posts/{p['id']}", headers=bruno)).status_code == 200
    assert (await client.get("/v1/reports/me", headers=bruno)).json()["inviate"][0]["stato"] == "archiviata"
    assert (await client.get("/v1/reports/me", headers=anna)).json()["ricevute"] == []


async def test_audit_e_dashboard(client, fabbrica, anna, bruno):
    mod = await moderatore(client, fabbrica)
    p = await pubblica(client, anna)
    rep = await segnala(client, bruno, "post", p["id"])
    await client.post(f"/v1/admin/reports/{rep['id']}/decidi", headers=mod, json={"decisione": "avviso", "nota": "Primo avviso"})
    audit = (await client.get("/v1/admin/audit", headers=mod)).json()
    assert any(a["azione"] == "moderazione_avviso" and a["dettagli"] == "Primo avviso" for a in audit)
    m = (await client.get("/v1/admin/metriche", headers=mod)).json()
    assert m["fiducia"]["tasso_risoluzione"] == 1.0 and "latenza_p95_ms" in m["api"]


async def test_contenuto_segnalato_sparisce_per_chi_segnala(client, anna, bruno):
    p = await pubblica(client, anna)
    await segnala(client, bruno, "post", p["id"])
    assert p["id"] not in [e["id"] for e in (await client.get("/v1/feed", headers=bruno)).json()["elementi"]]
    assert (await client.get("/v1/map", headers=bruno)).json()["punti"] == []
    # per gli altri resta visibile finché la moderazione non decide
    altro = await vicino(client, "d@example.com", "Dora")
    assert p["id"] in [e["id"] for e in (await client.get("/v1/feed", headers=altro)).json()["elementi"]]


async def test_nessuna_segnalazione_orfana_dopo_cancellazioni(client, fabbrica, anna, bruno):
    mod = await moderatore(client, fabbrica)
    p1 = await pubblica(client, anna, titolo="Primo", descrizione="Primo annuncio")
    p2 = await pubblica(client, anna, titolo="Secondo", descrizione="Secondo annuncio")
    await segnala(client, bruno, "post", p1["id"])
    rep2 = await segnala(client, bruno, "post", p2["id"])
    await client.post(f"/v1/admin/reports/{rep2['id']}/decidi", headers=mod, json={"decisione": "avviso"})
    await client.post(f"/v1/reports/{rep2['id']}/ricorso", headers=anna, json={"testo": "Non era una violazione"})
    await client.delete(f"/v1/posts/{p1['id']}", headers=anna)
    assert (await client.get("/v1/admin/reports", headers=mod)).json() == []
    assert (await client.get("/v1/reports/me", headers=bruno)).json()["inviate"][1]["stato"] == "archiviata"
    from tests.conftest import PASSWORD
    await client.request("DELETE", "/v1/account", headers=anna, json={"password": PASSWORD, "conferma": "ELIMINA"})
    assert (await client.get("/v1/admin/ricorsi", headers=mod)).json() == []


async def test_coda_raggruppa_le_segnalazioni_dello_stesso_contenuto(client, fabbrica, anna, bruno):
    mod = await moderatore(client, fabbrica)
    altro = await vicino(client, "e@example.com", "Ezio")
    p = await pubblica(client, anna)
    await segnala(client, bruno, "post", p["id"], motivo="spam", dettagli="Pubblicità")
    await segnala(client, altro, "post", p["id"], motivo="truffa", dettagli="Sembra una truffa")
    coda = (await client.get("/v1/admin/reports", headers=mod)).json()
    assert len(coda) == 1 and coda[0]["segnalazioni_stesso_contenuto"] == 2
    assert coda[0]["commenti"] == ["Pubblicità", "Sembra una truffa"] and len(coda[0]["motivi"]) == 2
