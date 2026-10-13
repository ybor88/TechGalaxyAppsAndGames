# © Roberto Di Flumeri
"""
Integrità dei riferimenti tra i moduli (SQLite non applica le chiavi esterne)
e regole di business su workflow e paghe.
"""

from datetime import date


async def _conto(client, codice, tipo="attivo"):
    r = await client.post("/api/v1/conti/", json={"codice": codice, "descrizione": f"Conto {codice}", "tipo": tipo})
    assert r.status_code == 201, r.text
    return r.json()


async def _anagrafica(client, nome="Cliente Test", tipo="cliente"):
    r = await client.post("/api/v1/anagrafiche/", json={"nome": nome, "tipo": tipo})
    assert r.status_code == 201, r.text
    return r.json()


async def _documento(client, anagrafica_id=None):
    r = await client.post("/api/v1/documenti/", json={
        "tipo": "fattura_attiva", "data": "2026-05-10", "anagrafica_id": anagrafica_id,
        "righe": [{"descrizione": "Servizio", "quantita": "1", "prezzo_unitario": "100.00"}],
    })
    assert r.status_code == 201, r.text
    return r.json()


async def _registrazione(client, dare_id, avere_id, importo="100.00"):
    r = await client.post("/api/v1/contabilita/registrazioni", json={
        "data": "2026-05-10", "causale": "Test", "righe": [
            {"conto_id": dare_id, "dare": importo, "avere": "0"},
            {"conto_id": avere_id, "dare": "0", "avere": importo},
        ],
    })
    assert r.status_code == 201, r.text
    return r.json()


# ── Conti ─────────────────────────────────────────────────────────────────────

async def test_conto_tipo_non_valido_rifiutato(client):
    r = await client.post("/api/v1/conti/", json={"codice": "X1", "descrizione": "x", "tipo": "attiv"})
    assert r.status_code == 422


async def test_conto_codice_duplicato_409(client):
    await _conto(client, "D100")
    r = await client.post("/api/v1/conti/", json={"codice": "D100", "descrizione": "dup", "tipo": "attivo"})
    assert r.status_code == 409
    altro = await _conto(client, "D200")
    r = await client.put(f"/api/v1/conti/{altro['id']}", json={"codice": "D100"})
    assert r.status_code == 409


async def test_conto_movimentato_non_eliminabile_e_bilancio_resta_leggibile(client):
    cassa = await _conto(client, "C100", "attivo")
    ricavi = await _conto(client, "R400", "ricavo")
    reg = await _registrazione(client, cassa["id"], ricavi["id"])

    r = await client.delete(f"/api/v1/conti/{cassa['id']}")
    assert r.status_code == 409
    assert "prima nota" in r.json()["detail"]

    assert (await client.get(f"/api/v1/contabilita/registrazioni/{reg['id']}")).status_code == 200
    bil = (await client.get("/api/v1/contabilita/bilancio")).json()
    assert bil["totale_dare"] == bil["totale_avere"]


async def test_conto_inutilizzato_eliminabile(client):
    c = await _conto(client, "L100")
    assert (await client.delete(f"/api/v1/conti/{c['id']}")).status_code == 204


# ── Anagrafiche e documenti ──────────────────────────────────────────────────

async def test_anagrafica_con_documenti_non_eliminabile(client):
    ana = await _anagrafica(client)
    await _documento(client, ana["id"])
    r = await client.delete(f"/api/v1/anagrafiche/{ana['id']}")
    assert r.status_code == 409
    assert "documenti" in r.json()["detail"]


async def test_anagrafica_senza_collegamenti_eliminabile(client):
    ana = await _anagrafica(client, "Da eliminare")
    assert (await client.delete(f"/api/v1/anagrafiche/{ana['id']}")).status_code == 204


async def test_documento_con_pagamenti_non_eliminabile(client):
    ana = await _anagrafica(client)
    doc = await _documento(client, ana["id"])
    r = await client.post("/api/v1/crm/storico-pagamenti", json={
        "anagrafica_id": ana["id"], "documento_id": doc["id"], "data_pagamento": "2026-06-01", "importo": "122.00"})
    assert r.status_code == 201, r.text
    assert (await client.delete(f"/api/v1/documenti/{doc['id']}")).status_code == 409


async def test_documento_eliminato_scollega_scadenze_e_ocr(client):
    doc = await _documento(client)
    r = await client.post("/api/v1/crm/scadenze", json={
        "titolo": "Incasso", "data_scadenza": "2026-07-01", "documento_id": doc["id"]})
    scad_id = r.json()["id"]
    assert (await client.delete(f"/api/v1/documenti/{doc['id']}")).status_code == 204
    scad = [s for s in (await client.get("/api/v1/crm/scadenze")).json() if s["id"] == scad_id][0]
    assert scad["documento_id"] is None


# ── Riferimenti inesistenti ──────────────────────────────────────────────────

async def test_crm_rifiuta_anagrafiche_e_documenti_inesistenti(client):
    r = await client.post("/api/v1/crm/storico-pagamenti", json={
        "anagrafica_id": 9999, "data_pagamento": "2026-06-01", "importo": "10.00"})
    assert r.status_code == 422
    r = await client.post("/api/v1/crm/scadenze", json={
        "titolo": "x", "data_scadenza": "2026-07-01", "documento_id": 9999})
    assert r.status_code == 422
    r = await client.post("/api/v1/crm/pipeline", json={"titolo": "x", "anagrafica_id": 9999})
    assert r.status_code == 422


async def test_crm_rifiuta_importi_negativi_e_titoli_vuoti(client):
    r = await client.post("/api/v1/crm/scadenze", json={"titolo": "x", "data_scadenza": "2026-07-01", "importo": "-5"})
    assert r.status_code == 422
    r = await client.post("/api/v1/crm/pipeline", json={"titolo": "", "valore_stimato": "100"})
    assert r.status_code == 422


async def test_movimento_con_conto_inesistente_rifiutato(client):
    r = await client.post("/api/v1/movimenti/", json={
        "data": "2026-05-01", "tipo": "entrata", "importo": "10.00", "descrizione": "x", "conto_id": 9999})
    assert r.status_code == 422


async def test_ocr_collega_documento_inesistente_rifiutato(client):
    from tests.test_ocr import _crea_pdf

    pdf = _crea_pdf(["ACME SRL", "P.IVA 12345678901", "Totale 1.220,00",
                     "Testo aggiuntivo per superare la soglia minima di caratteri."])
    ris = (await client.post("/api/v1/ocr/elabora", files={"file": ("f.pdf", pdf, "application/pdf")})).json()
    r = await client.patch(f"/api/v1/ocr/risultati/{ris['risultato']['id']}/collega-documento/9999")
    assert r.status_code == 422


async def test_workflow_rifiuta_anagrafica_inesistente(client):
    r = await client.post("/api/v1/workflow/tasks", json={"titolo": "x", "anagrafica_id": 9999})
    assert r.status_code == 422


# ── Workflow: approvazioni ───────────────────────────────────────────────────

async def _task_approvazione(client):
    r = await client.post("/api/v1/workflow/tasks", json={
        "titolo": "Acquisto PC", "tipo": "approvazione",
        "passi": [{"approvatore": "resp", "ordine": 1}, {"approvatore": "dir", "ordine": 2}],
    })
    assert r.status_code == 201, r.text
    t = r.json()
    passi = {p["ordine"]: p["id"] for p in t["passi"]}
    return t["id"], passi


async def test_passi_approvati_in_ordine(client):
    tid, passi = await _task_approvazione(client)
    r = await client.post(f"/api/v1/workflow/tasks/{tid}/passi/{passi[2]}/approva", json={"stato": "approvato"})
    assert r.status_code == 409
    assert (await client.post(f"/api/v1/workflow/tasks/{tid}/passi/{passi[1]}/approva", json={"stato": "approvato"})).status_code == 200
    r = await client.post(f"/api/v1/workflow/tasks/{tid}/passi/{passi[2]}/approva", json={"stato": "approvato"})
    assert r.status_code == 200
    assert r.json()["stato"] == "approvato"


async def test_nessuna_approvazione_su_task_rifiutato(client):
    tid, passi = await _task_approvazione(client)
    r = await client.post(f"/api/v1/workflow/tasks/{tid}/passi/{passi[1]}/approva", json={"stato": "rifiutato"})
    assert r.json()["stato"] == "rifiutato"
    r = await client.post(f"/api/v1/workflow/tasks/{tid}/passi/{passi[1]}/approva", json={"stato": "approvato"})
    assert r.status_code == 409


async def test_riapertura_task_azzera_data_completamento(client):
    t = (await client.post("/api/v1/workflow/tasks", json={"titolo": "x"})).json()
    r = await client.put(f"/api/v1/workflow/tasks/{t['id']}", json={"stato": "completato"})
    assert r.json()["data_completamento"] is not None
    r = await client.put(f"/api/v1/workflow/tasks/{t['id']}", json={"stato": "aperto"})
    assert r.json()["data_completamento"] is None


# ── Paghe e prima nota ───────────────────────────────────────────────────────

async def _dipendente(client, assunzione="2026-03-15", cessazione=None):
    r = await client.post("/api/v1/paghe/dipendenti", json={
        "nome": "Mario", "cognome": "Rossi", "data_assunzione": assunzione,
        "data_cessazione": cessazione, "retribuzione_lorda_mensile": "2000.00"})
    assert r.status_code == 201, r.text
    return r.json()


async def test_cedolino_fuori_dal_rapporto_di_lavoro_rifiutato(client):
    dip = await _dipendente(client, "2026-03-15", "2026-08-31")
    url = f"/api/v1/paghe/dipendenti/{dip['id']}/cedolini"
    assert (await client.post(url, json={"anno": 2026, "mese": 2})).status_code == 422
    assert (await client.post(url, json={"anno": 2026, "mese": 9})).status_code == 422
    assert (await client.post(url, json={"anno": 2026, "mese": 3})).status_code == 201
    assert (await client.post(url, json={"anno": 2026, "mese": 8})).status_code == 201


async def test_storno_registrazione_cedolino_lo_rende_ricontabilizzabile(client):
    await client.post("/api/v1/contabilita/init-piano-conti")
    dip = await _dipendente(client)
    ced = (await client.post(f"/api/v1/paghe/dipendenti/{dip['id']}/cedolini", json={"anno": 2026, "mese": 4})).json()
    r = await client.post(f"/api/v1/paghe/cedolini/{ced['id']}/contabilizza")
    assert r.status_code == 200, r.text
    reg_id = r.json()["registrazione_id"]

    # dipendente con cedolini contabilizzati: non eliminabile
    assert (await client.delete(f"/api/v1/paghe/dipendenti/{dip['id']}")).status_code == 409

    assert (await client.delete(f"/api/v1/contabilita/registrazioni/{reg_id}")).status_code == 204
    cedolini = (await client.get(f"/api/v1/paghe/dipendenti/{dip['id']}/cedolini")).json()
    assert cedolini[0]["registrazione_id"] is None
    assert (await client.post(f"/api/v1/paghe/cedolini/{ced['id']}/contabilizza")).status_code == 200


async def test_storno_registrazione_ammortamento_libera_la_quota(client):
    costo = await _conto(client, "A420", "costo")
    fondo = await _conto(client, "A145", "attivo")
    c = (await client.post("/api/v1/ammortamenti/cespiti", json={
        "descrizione": "PC", "categoria": "macchine_ufficio_elettroniche", "data_acquisto": "2025-01-10",
        "costo_storico": "1000.00", "conto_costo_id": costo["id"], "conto_fondo_id": fondo["id"]})).json()
    r = await client.post(f"/api/v1/ammortamenti/cespiti/{c['id']}/contabilizza", json={"anno": 2025})
    assert r.status_code == 200, r.text
    reg_id = r.json()["registrazione_id"]
    assert (await client.delete(f"/api/v1/contabilita/registrazioni/{reg_id}")).status_code == 204
    piano = (await client.get(f"/api/v1/ammortamenti/cespiti/{c['id']}/piano")).json()
    assert not piano["quote"][0]["contabilizzato"]
    assert (await client.post(f"/api/v1/ammortamenti/cespiti/{c['id']}/contabilizza", json={"anno": 2025})).status_code == 200
    assert date.today().year >= 2025


async def test_rotte_con_e_senza_barra_finale_senza_redirect(client):
    """Nessun redirect assoluto verso localhost:8000 (romperebbe l'uso dietro proxy o da altri PC)."""
    for url in ("/api/v1/documenti", "/api/v1/documenti/", "/api/v1/movimenti", "/api/v1/anagrafiche/"):
        r = await client.get(url, follow_redirects=False)
        assert r.status_code == 200, (url, r.status_code)
    r = await client.post("/api/v1/anagrafiche", json={"nome": "Senza barra", "tipo": "cliente"}, follow_redirects=False)
    assert r.status_code == 201
    r = await client.get("/api/v1/documenti", follow_redirects=False)
    assert "x-total-count" in r.headers
