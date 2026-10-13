"""Simulatore decisioni (F03/F07), versioni (F04), timeline (F10), diario (F06), sfide (F09). © Roberto Di Flumeri"""

from sqlalchemy import update

from app.models import ProfileSnapshot
from tests.conftest import dichiara, registra


async def _decisione(client, h, **extra):
    corpo = {
        "domanda": "Quale lavoro accetto?",
        "opzioni": [{"nome": "Studio di design", "descrizione": "Lavoro da designer, orari flessibili"},
                    {"nome": "Banca", "descrizione": "Stipendio alto, molte ore in ufficio"}],
        "criteri": [{"nome": "Diventare designer", "peso": 5}, {"nome": "Tempo per la famiglia", "peso": 4}],
    }
    corpo.update(extra)
    return await client.post("/v1/decisions", headers=h, json=corpo)


async def test_criteri_suggeriti_dalle_dichiarazioni(client, profilo):
    criteri = (await client.get("/v1/decisions/criteri-suggeriti", headers=profilo)).json()
    assert criteri and all(c["statement_id"] for c in criteri)
    assert criteri[0]["peso"] == 5  # i più importanti per primi


async def test_simulazione_con_valutazioni_esplicite(client, profilo):
    r = await _decisione(client, profilo, valutazioni={"0": {"0": 5, "1": 4}, "1": {"0": 1, "1": 2}})
    assert r.status_code == 201
    ris = r.json()["risultato"]
    assert ris["classifica"][0]["opzione"] == "Studio di design"
    assert ris["classifica"][0]["coerenza_percentuale"] == 91  # (5*5+4*4)/(9*5)
    assert "Diventare designer" in ris["classifica"][0]["pro"]
    assert "Tempo per la famiglia" in ris["classifica"][1]["contro"]
    assert ris["celle_stimate"] == [] and "non decide al posto tuo" in ris["avviso"]


async def test_celle_mancanti_stimate_e_dichiarate(client, profilo):
    r = await _decisione(client, profilo)
    ris = r.json()["risultato"]
    assert len(ris["celle_stimate"]) == 4 and ris["nota_stime"]
    dec_id = r.json()["id"]
    r = await client.put(f"/v1/decisions/{dec_id}/valutazioni", headers=profilo, json={"valutazioni": {"1": {"0": 1}}})
    ris = r.json()["risultato"]
    assert {"opzione": 1, "criterio": 0} not in ris["celle_stimate"]
    assert r.json()["valutazioni"]["1"]["0"] == 1


async def test_validazione_decisioni(client, profilo):
    assert (await _decisione(client, profilo, opzioni=[{"nome": "Sola"}])).status_code == 422
    assert (await _decisione(client, profilo, valutazioni={"0": {"0": 9}})).status_code == 422
    assert (await _decisione(client, profilo, valutazioni={"5": {"0": 3}})).status_code == 422
    r = await _decisione(client, profilo, criteri=[{"nome": "x", "peso": 3, "statement_id": "inesistente"}])
    assert r.status_code == 422


async def test_esito_aggiorna_il_profilo(client, profilo):
    dec = (await _decisione(client, profilo)).json()
    r = await client.put(f"/v1/decisions/{dec['id']}/esito", headers=profilo, json={"scelta_finale": "Pizzeria"})
    assert r.status_code == 422
    r = await client.put(f"/v1/decisions/{dec['id']}/esito", headers=profilo, json={
        "scelta_finale": "Studio di design", "esito": "positivo",
        "apprendimento": "Seguire le passioni mi rende più sereno", "salva_come_dichiarazione": True})
    assert r.json()["stato"] == "valutata"
    nuove = [s for s in (await client.get("/v1/profile/statements", headers=profilo)).json() if s["fonte"] == "decisione"]
    assert len(nuove) == 1 and "passioni" in nuove[0]["testo"]


async def test_decisioni_private(client, profilo):
    dec = (await _decisione(client, profilo)).json()
    altro = await registra(client, "z@example.com")
    assert (await client.get(f"/v1/decisions/{dec['id']}", headers=altro)).status_code == 404
    assert (await client.delete(f"/v1/decisions/{dec['id']}", headers=altro)).status_code == 404


# ── Versioni ──────────────────────────────────────────────────────────────────


async def test_snapshot_immutabile_e_confronto(client, profilo):
    s1 = (await client.post("/v1/snapshots", headers=profilo, json={"etichetta": "Inizio"})).json()
    assert s1["versione"] == 1 and s1["tipo"] == "reale" and len(s1["impronta"]) == 64
    dichiarazioni = (await client.get("/v1/profile/statements", headers=profilo)).json()
    da_modificare = next(d for d in dichiarazioni if d["categoria"] == "abitudini")
    await client.patch(f"/v1/profile/statements/{da_modificare['id']}", headers=profilo, json={"testo": "Ora lavoro meglio la sera"})
    await dichiara(client, profilo, "Voglio imparare il giapponese", "obiettivi")
    s2 = (await client.post("/v1/snapshots", headers=profilo, json={})).json()
    assert s2["versione"] == 2

    conf = (await client.get(f"/v1/snapshots/confronto?da={s1['id']}&a={s2['id']}", headers=profilo)).json()
    assert [d["testo"] for d in conf["aggiunte"]] == ["Voglio imparare il giapponese"]
    assert conf["modificate"][0]["campi"] == ["testo"] and conf["rimosse"] == []
    assert "non è una valutazione psicologica" in conf["nota"]
    attuale = (await client.get(f"/v1/snapshots/confronto?da={s2['id']}", headers=profilo)).json()
    assert attuale["a"] == "Profilo attuale"

    det = (await client.get(f"/v1/snapshots/{s1['id']}", headers=profilo)).json()
    assert det["integro"] is True


async def test_integrita_rileva_manomissioni(client, profilo, sessione):
    s = (await client.post("/v1/snapshots", headers=profilo, json={})).json()
    await sessione.execute(update(ProfileSnapshot).where(ProfileSnapshot.id == s["id"]).values(impronta="0" * 64))
    await sessione.commit()
    assert (await client.get(f"/v1/snapshots/{s['id']}", headers=profilo)).json()["integro"] is False


async def test_snapshot_profilo_vuoto(client, utente):
    assert (await client.post("/v1/snapshots", headers=utente, json={})).status_code == 422


async def test_simulazione_futura_sempre_etichettata(client, profilo):
    dichiarazioni = (await client.get("/v1/profile/statements", headers=profilo)).json()
    obiettivo = next(d for d in dichiarazioni if d["categoria"] == "obiettivi")
    r = await client.post("/v1/snapshots/simulazione", headers=profilo, json={
        "mesi": 12, "ipotesi": [
            {"azione": "aggiungi", "testo": "Lavoro come designer freelance", "categoria": "abitudini"},
            {"azione": "rimuovi", "id": obiettivo["id"]},
        ]})
    assert r.status_code == 201
    sim = r.json()
    assert sim["tipo"] == "simulazione_futura" and sim["etichetta"].startswith("SIMULAZIONE")
    assert "non una previsione" in sim["riepilogo"]
    r = await client.post("/v1/snapshots/simulazione", headers=profilo, json={"mesi": 6, "ipotesi": [{"azione": "rimuovi", "id": "x"}]})
    assert r.status_code == 422
    chat = (await client.post("/v1/chat/completions", headers=profilo,
                              json={"messaggio": "che lavoro faccio?", "snapshot_id": sim["id"]})).json()
    assert chat["risposta"]["contenuto"].startswith("(Simulazione")


async def test_timeline_eventi_e_serie(client, profilo):
    await client.post("/v1/snapshots", headers=profilo, json={})
    await client.post("/v1/journal", headers=profilo, json={"tipo": "check_in", "testo": "Settimana intensa", "energia": 3})
    t = (await client.get("/v1/timeline", headers=profilo)).json()
    tipi = {e["tipo"] for e in t["eventi"]}
    assert {"dichiarazione", "versione", "diario"} <= tipi
    assert t["serie"][-1]["etichetta"] == "Oggi" and t["serie"][-1]["gusti"] == 1
    assert "non attribuisce diagnosi" in t["nota"]


# ── Diario e sfide ────────────────────────────────────────────────────────────


async def test_diario(client, utente):
    v = (await client.post("/v1/journal", headers=utente, json={"testo": "Oggi ho corso 5 km", "etichette": ["sport"]})).json()
    assert (await client.post("/v1/journal", headers=utente, json={"testo": "x", "energia": 9})).status_code == 422
    assert len((await client.get("/v1/journal", headers=utente)).json()) == 1
    altro = await registra(client, "j@example.com")
    assert (await client.delete(f"/v1/journal/{v['id']}", headers=altro)).status_code == 404
    assert (await client.delete(f"/v1/journal/{v['id']}", headers=utente)).status_code == 204


async def test_sfide_e_conversione_in_dichiarazione(client, utente):
    sfide = (await client.get("/v1/challenges", headers=utente)).json()
    assert len(sfide) >= 10
    r = await client.post(f"/v1/challenges/{sfide[0]['id']}/risposte", headers=utente,
                          json={"risposta": "Curioso, testardo, leale", "salva_come_dichiarazione": True})
    assert r.status_code == 201 and r.json()["statement_id"]
    s = (await client.get("/v1/profile/statements", headers=utente)).json()
    assert s[0]["fonte"] == "sfida"
    assert (await client.post("/v1/challenges/inesistente/risposte", headers=utente, json={"risposta": "ab"})).status_code == 404
