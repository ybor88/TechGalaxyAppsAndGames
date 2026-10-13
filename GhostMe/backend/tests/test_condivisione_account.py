"""Condivisione (F08), compatibilità (F05), moderazione, export e cancellazione account. © Roberto Di Flumeri"""

import json
from datetime import timedelta

from sqlalchemy import select, text, update

from app.models import ShareGrant, User, adesso
from tests.conftest import PASSWORD, dichiara, registra


async def _condividi(client, h, **extra):
    corpo = {"nome": "Per gli amici", "categorie": ["gusti", "stile_comunicativo"], "max_domande": 3, "durata_ore": 24}
    corpo.update(extra)
    r = await client.post("/v1/shares", headers=h, json=corpo)
    assert r.status_code == 201, r.text
    return r.json()


async def _domanda(client, token, testo="Cosa ti piace fare?"):
    return await client.post(f"/v1/public/shares/{token}/chat", json={"domanda": testo})


async def test_link_condiviso_con_watermark_e_scope(client, profilo):
    c = await _condividi(client, profilo)
    token = c["token"]
    info = (await client.get(f"/v1/public/shares/{token}")).json()
    assert info["attiva"] and info["domande_rimaste"] == 3 and "generata" in info["watermark"].lower()
    r = await _domanda(client, token, "Cosa ti piace fare? viaggiare?")
    assert r.status_code == 200
    corpo = r.json()
    assert "viaggiare" in corpo["risposta"].lower() and corpo["domande_rimaste"] == 2
    # i valori non sono nello scope del link: la famiglia non deve emergere
    r = await _domanda(client, token, "Quanto conta la famiglia rispetto al lavoro?")
    assert "famiglia viene prima" not in r.json()["risposta"].lower()


async def test_dati_privati_mai_esposti_nel_link(client, profilo):
    await dichiara(client, profilo, "Il mio stipendio segreto è alto", "gusti", 5, visibilita="privata")
    c = await _condividi(client, profilo, max_domande=10)
    r = await _domanda(client, c["token"], "Qual è il tuo stipendio segreto?")
    assert "stipendio" not in r.json()["risposta"].lower()
    assert "id" not in json.dumps(r.json()["fonti"])  # all'invitato non arrivano identificativi interni


async def test_limite_domande_revoca_e_scadenza(client, profilo, sessione):
    c = await _condividi(client, profilo, max_domande=1)
    assert (await _domanda(client, c["token"])).status_code == 200
    r = await _domanda(client, c["token"])
    assert r.status_code == 410 and "numero massimo" in r.json()["detail"]

    c2 = await _condividi(client, profilo)
    assert (await client.delete(f"/v1/shares/{c2['condivisione']['id']}", headers=profilo)).json()["attiva"] is False
    assert "revocato" in (await _domanda(client, c2["token"])).json()["detail"]

    c3 = await _condividi(client, profilo)
    await sessione.execute(update(ShareGrant).where(ShareGrant.id == c3["condivisione"]["id"])
                           .values(scadenza=adesso() - timedelta(minutes=1)))
    await sessione.commit()
    assert "scaduto" in (await _domanda(client, c3["token"])).json()["detail"]
    assert (await client.get("/v1/public/shares/token-inventato")).status_code == 404


async def test_token_salvato_solo_come_hash_e_accessi_registrati(client, profilo, sessione):
    c = await _condividi(client, profilo)
    await _domanda(client, c["token"])
    righe = (await sessione.execute(text("select token_hash from share_grants"))).scalars().all()
    assert c["token"] not in righe
    accessi = (await client.get(f"/v1/shares/{c['condivisione']['id']}/accessi", headers=profilo)).json()
    assert [a["evento"] for a in accessi] == ["domanda"]


async def test_revoca_consenso_condivisione_disattiva_i_link(client, profilo):
    c = await _condividi(client, profilo)
    await client.put("/v1/consents/condivisione", headers=profilo, json={"concesso": False})
    assert (await _domanda(client, c["token"])).status_code == 410


async def test_segnalazione_e_moderazione(client, profilo, sessione):
    c = await _condividi(client, profilo, max_domande=10)
    r = await client.post(f"/v1/public/shares/{c['token']}/report", json={"motivo": "imitazione_terzi", "dettagli": "Finge di essere un'altra persona"})
    assert r.status_code == 201

    utente_normale = await registra(client, "u@example.com")
    assert (await client.get("/v1/admin/reports", headers=utente_normale)).status_code == 403

    mod = await registra(client, "mod@example.com", nome="Moderatore")
    mod_user = [u for u in (await sessione.execute(select(User))).scalars() if u.nome_visualizzato == "Moderatore"][0]
    mod_user.ruolo = "moderatore"
    await sessione.commit()

    segnalazioni = (await client.get("/v1/admin/reports", headers=mod)).json()
    assert len(segnalazioni) == 1 and segnalazioni[0]["motivo"] == "imitazione_terzi"
    assert "testo" not in segnalazioni[0]  # il moderatore non vede i contenuti del profilo
    r = await client.post(f"/v1/admin/reports/{segnalazioni[0]['id']}/risolvi", headers=mod,
                          json={"azione": "revoca_condivisione", "nota": "verificato"})
    assert r.json()["stato"] == "risolta"
    assert "moderazione" in (await _domanda(client, c["token"])).json()["detail"]

    r = await client.post(f"/v1/admin/reports/{segnalazioni[0]['id']}/risolvi", headers=mod, json={"azione": "blocca_utente"})
    assert (await client.get("/v1/auth/me", headers=profilo)).status_code == 403
    audit = (await client.get("/v1/admin/audit", headers=mod)).json()
    assert any(a["azione"] == "moderazione_blocca_utente" for a in audit)
    metriche = (await client.get("/v1/admin/metriche", headers=mod)).json()
    assert metriche["utenti"]["bloccato"] == 1 and "latenza_p95_ms" in metriche["api"]


# ── Compatibilità ────────────────────────────────────────────────────────────


async def test_compatibilita_consensuale(client, profilo):
    inv = (await client.post("/v1/compatibility/inviti", headers=profilo, json={"categorie": ["gusti", "valori"]})).json()
    assert (await client.post("/v1/compatibility/accetta", headers=profilo, json={"codice": inv["codice"]})).status_code == 422

    giulia = await registra(client, "giulia@example.com", nome="Giulia")
    await dichiara(client, giulia, "Adoro fare trekking in montagna e viaggiare", "gusti", 5, "condivisibile")
    await dichiara(client, giulia, "Il lavoro viene prima di tutto", "valori", 4, "condivisibile")
    await dichiara(client, giulia, "Segreto privato di Giulia", "gusti", 3, "privata")
    r = await client.post("/v1/compatibility/accetta", headers=giulia, json={"codice": inv["codice"].lower()})
    assert r.status_code == 200
    ris = r.json()["risultato"]
    gusti = next(c for c in ris["categorie"] if c["categoria"] == "gusti")
    assert gusti["punti_in_comune"] and gusti["descrizione"] != "prospettive diverse"
    assert "non è un giudizio" in ris["criteri"].lower()
    assert "Segreto privato" not in json.dumps(ris)

    # entrambi vedono il risultato; il codice non è riutilizzabile
    elenco = (await client.get("/v1/compatibility", headers=profilo)).json()
    assert elenco[0]["accettato"] and elenco[0]["con"] == "Giulia"
    terzo = await registra(client, "t@example.com")
    assert (await client.post("/v1/compatibility/accetta", headers=terzo, json={"codice": inv["codice"]})).status_code == 409
    assert (await client.delete(f"/v1/compatibility/{inv['id']}", headers=terzo)).status_code == 404
    assert (await client.delete(f"/v1/compatibility/{inv['id']}", headers=giulia)).status_code == 204


# ── Account ───────────────────────────────────────────────────────────────────


async def test_export_json_e_markdown(client, profilo):
    await client.post("/v1/chat/completions", headers=profilo, json={"messaggio": "cosa mi piace?"})
    r = await client.get("/v1/account/export", headers=profilo)
    dati = r.json()
    assert dati["formato"] == "ghostme-export/1" and len(dati["dichiarazioni"]) == 6
    assert dati["conversazioni"][0]["messaggi"] and dati["consensi"]
    md = await client.get("/v1/account/export?formato=markdown", headers=profilo)
    assert md.headers["content-type"].startswith("text/markdown") and "## Dichiarazioni" in md.text
    assert (await client.get("/v1/account/export?formato=pdf", headers=profilo)).status_code == 422


async def test_cancellazione_definitiva(client, profilo, sessione):
    await _condividi(client, profilo)
    await client.post("/v1/snapshots", headers=profilo, json={})
    r = await client.request("DELETE", "/v1/account", headers=profilo, json={"password": "sbagliata1", "conferma": "ELIMINA"})
    assert r.status_code == 403
    r = await client.request("DELETE", "/v1/account", headers=profilo, json={"password": PASSWORD, "conferma": "SI"})
    assert r.status_code == 422
    r = await client.request("DELETE", "/v1/account", headers=profilo, json={"password": PASSWORD, "conferma": "ELIMINA"})
    assert r.status_code == 200 and r.json()["ricevuta"]
    for tabella in ("users", "trait_statements", "trait_changes", "share_grants", "profile_snapshots", "consents",
                    "conversations", "messages", "refresh_tokens"):
        n = (await sessione.execute(text(f"select count(*) from {tabella}"))).scalar()
        assert n == 0, tabella
    assert (await sessione.execute(text("select count(*) from deletion_receipts"))).scalar() == 1
    r = await client.post("/v1/auth/login", json={"email": "mario@example.com", "password": PASSWORD})
    assert r.status_code == 401


async def test_conservazione_conversazioni(client, profilo, sessione):
    from app.main import applica_conservazione
    from app.models import Conversation

    await client.post("/v1/chat/completions", headers=profilo, json={"messaggio": "ciao"})
    await client.patch("/v1/auth/me", headers=profilo, json={"conservazione_chat_giorni": 7})
    await sessione.execute(update(Conversation).values(updated_at=adesso() - timedelta(days=8)))
    await sessione.commit()
    assert await applica_conservazione() == 1
    assert (await client.get("/v1/conversations", headers=profilo)).json() == []
