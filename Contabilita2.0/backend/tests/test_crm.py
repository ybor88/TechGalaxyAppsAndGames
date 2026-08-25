from datetime import date, timedelta
from decimal import Decimal


async def _crea_anagrafica(client, nome="Cliente Test", tipo="cliente"):
    payload = {"nome": nome, "tipo": tipo}
    r = await client.post("/api/v1/anagrafiche/", json=payload)
    assert r.status_code == 201, r.text
    return r.json()


async def _crea_storico(client, anagrafica_id, data_pagamento="2026-01-15",
                         importo="100.00", giorni_ritardo=0):
    payload = {
        "anagrafica_id": anagrafica_id,
        "data_pagamento": data_pagamento,
        "importo": importo,
        "giorni_ritardo": giorni_ritardo,
    }
    r = await client.post("/api/v1/crm/storico-pagamenti", json=payload)
    assert r.status_code == 201, r.text
    return r.json()


async def _crea_scadenza(client, titolo="Scadenza Test", data_scadenza="2026-12-31",
                          anagrafica_id=None, tipo="incasso", stato="aperta"):
    payload = {
        "titolo": titolo,
        "data_scadenza": data_scadenza,
        "anagrafica_id": anagrafica_id,
        "tipo": tipo,
        "stato": stato,
    }
    r = await client.post("/api/v1/crm/scadenze", json=payload)
    assert r.status_code == 201, r.text
    return r.json()


async def _crea_opportunita(client, titolo="Opportunita Test", fase="prospecting",
                             probabilita=50, anagrafica_id=None):
    payload = {
        "titolo": titolo,
        "fase": fase,
        "probabilita": probabilita,
        "anagrafica_id": anagrafica_id,
    }
    r = await client.post("/api/v1/crm/pipeline", json=payload)
    assert r.status_code == 201, r.text
    return r.json()


# ── Clienti / Fornitori ────────────────────────────────────────────────────────

async def test_list_clienti_vuoto(client):
    r = await client.get("/api/v1/crm/clienti")
    assert r.status_code == 200
    assert r.json() == []


async def test_list_clienti_include_tipo_entrambi_senza_duplicati(client):
    await _crea_anagrafica(client, nome="Solo Cliente", tipo="cliente")
    await _crea_anagrafica(client, nome="Solo Fornitore", tipo="fornitore")
    await _crea_anagrafica(client, nome="Cliente e Fornitore", tipo="entrambi")

    r = await client.get("/api/v1/crm/clienti")
    assert r.status_code == 200
    nomi = [a["nome"] for a in r.json()]
    assert nomi == ["Cliente e Fornitore", "Solo Cliente"]

    r = await client.get("/api/v1/crm/fornitori")
    nomi = [a["nome"] for a in r.json()]
    assert nomi == ["Cliente e Fornitore", "Solo Fornitore"]


# ── Storico pagamenti ──────────────────────────────────────────────────────────

async def test_create_storico_pagamento(client):
    ana = await _crea_anagrafica(client)
    rec = await _crea_storico(client, ana["id"], importo="250.00", giorni_ritardo=5)
    assert Decimal(rec["importo"]) == Decimal("250.00")
    assert rec["giorni_ritardo"] == 5
    assert rec["anagrafica_nome"] == ana["nome"]
    assert rec["metodo_pagamento"] == "bonifico"


async def test_create_storico_importo_non_positivo_422(client):
    ana = await _crea_anagrafica(client)
    payload = {"anagrafica_id": ana["id"], "data_pagamento": "2026-01-15", "importo": "0.00"}
    r = await client.post("/api/v1/crm/storico-pagamenti", json=payload)
    assert r.status_code == 422


async def test_list_storico_filtro_anagrafica(client):
    ana1 = await _crea_anagrafica(client, nome="Ana 1")
    ana2 = await _crea_anagrafica(client, nome="Ana 2")
    await _crea_storico(client, ana1["id"])
    await _crea_storico(client, ana2["id"])

    r = await client.get("/api/v1/crm/storico-pagamenti", params={"anagrafica_id": ana1["id"]})
    assert r.status_code == 200
    data = r.json()
    assert len(data) == 1
    assert data[0]["anagrafica_id"] == ana1["id"]


async def test_delete_storico(client):
    ana = await _crea_anagrafica(client)
    rec = await _crea_storico(client, ana["id"])
    r = await client.delete(f"/api/v1/crm/storico-pagamenti/{rec['id']}")
    assert r.status_code == 204

    r = await client.get("/api/v1/crm/storico-pagamenti")
    assert r.json() == []


async def test_delete_storico_inesistente(client):
    r = await client.delete("/api/v1/crm/storico-pagamenti/9999")
    assert r.status_code == 404


# ── Affidabilità ────────────────────────────────────────────────────────────────

async def test_affidabilita_anagrafica_inesistente(client):
    r = await client.get("/api/v1/crm/affidabilita/9999")
    assert r.status_code == 404


async def test_affidabilita_nessun_pagamento(client):
    ana = await _crea_anagrafica(client)
    r = await client.get(f"/api/v1/crm/affidabilita/{ana['id']}")
    assert r.status_code == 200
    data = r.json()
    assert data["totale_pagamenti"] == 0
    assert data["pagamenti_puntuali"] == 0
    assert data["pagamenti_in_ritardo"] == 0
    assert data["media_giorni_ritardo"] == 0.0
    assert data["score"] == 100
    assert data["livello"] == "ottimo"


async def test_affidabilita_con_ritardi_calcola_score(client):
    ana = await _crea_anagrafica(client)
    # 2 puntuali, 1 in ritardo di 10 giorni
    await _crea_storico(client, ana["id"], data_pagamento="2026-01-01", giorni_ritardo=0)
    await _crea_storico(client, ana["id"], data_pagamento="2026-01-02", giorni_ritardo=-2)
    await _crea_storico(client, ana["id"], data_pagamento="2026-01-03", giorni_ritardo=10)

    r = await client.get(f"/api/v1/crm/affidabilita/{ana['id']}")
    assert r.status_code == 200
    data = r.json()
    assert data["totale_pagamenti"] == 3
    assert data["pagamenti_puntuali"] == 2
    assert data["pagamenti_in_ritardo"] == 1
    assert data["media_giorni_ritardo"] == 10.0
    # score = 100 - (1*5) - min(30, 10) = 85
    assert data["score"] == 85
    assert data["livello"] == "ottimo"


async def test_affidabilita_ritardo_grave_livello_basso(client):
    ana = await _crea_anagrafica(client)
    for _ in range(5):
        await _crea_storico(client, ana["id"], giorni_ritardo=60)

    r = await client.get(f"/api/v1/crm/affidabilita/{ana['id']}")
    data = r.json()
    assert data["pagamenti_in_ritardo"] == 5
    # score = 100 - (5*5) - min(30, 60) = 100 - 25 - 30 = 45
    assert data["score"] == 45
    assert data["livello"] == "sufficiente"


# ── Scadenze ────────────────────────────────────────────────────────────────────

async def test_create_scadenza(client):
    ana = await _crea_anagrafica(client)
    scad = await _crea_scadenza(client, anagrafica_id=ana["id"], data_scadenza="2026-12-31")
    assert scad["stato"] == "aperta"
    assert scad["anagrafica_nome"] == ana["nome"]
    assert scad["giorni_alla_scadenza"] is not None


async def test_scadenza_giorni_alla_scadenza_calcolati(client):
    domani = (date.today() + timedelta(days=5)).isoformat()
    scad = await _crea_scadenza(client, data_scadenza=domani)
    assert scad["giorni_alla_scadenza"] == 5


async def test_list_scadenze_filtro_stato_tipo(client):
    await _crea_scadenza(client, titolo="A", stato="aperta", tipo="incasso")
    await _crea_scadenza(client, titolo="B", stato="pagata", tipo="pagamento")

    r = await client.get("/api/v1/crm/scadenze", params={"stato": "aperta"})
    data = r.json()
    assert len(data) == 1
    assert data[0]["titolo"] == "A"

    r = await client.get("/api/v1/crm/scadenze", params={"tipo": "pagamento"})
    data = r.json()
    assert len(data) == 1
    assert data[0]["titolo"] == "B"


async def test_update_scadenza(client):
    scad = await _crea_scadenza(client)
    r = await client.put(f"/api/v1/crm/scadenze/{scad['id']}", json={"stato": "pagata"})
    assert r.status_code == 200, r.text
    assert r.json()["stato"] == "pagata"


async def test_update_scadenza_inesistente(client):
    r = await client.put("/api/v1/crm/scadenze/9999", json={"stato": "pagata"})
    assert r.status_code == 404


async def test_delete_scadenza(client):
    scad = await _crea_scadenza(client)
    r = await client.delete(f"/api/v1/crm/scadenze/{scad['id']}")
    assert r.status_code == 204

    r = await client.get("/api/v1/crm/scadenze")
    assert r.json() == []


async def test_delete_scadenza_inesistente(client):
    r = await client.delete("/api/v1/crm/scadenze/9999")
    assert r.status_code == 404


# ── Pipeline commerciale ─────────────────────────────────────────────────────────

async def test_create_opportunita(client):
    op = await _crea_opportunita(client, titolo="Nuovo affare", probabilita=70)
    assert op["fase"] == "prospecting"
    assert op["probabilita"] == 70


async def test_create_opportunita_probabilita_fuori_range_422(client):
    payload = {"titolo": "X", "probabilita": 150}
    r = await client.post("/api/v1/crm/pipeline", json=payload)
    assert r.status_code == 422


async def test_list_pipeline_filtro_fase(client):
    await _crea_opportunita(client, titolo="A", fase="prospecting")
    await _crea_opportunita(client, titolo="B", fase="chiusa_vinta")

    r = await client.get("/api/v1/crm/pipeline", params={"fase": "chiusa_vinta"})
    data = r.json()
    assert len(data) == 1
    assert data[0]["titolo"] == "B"


async def test_update_opportunita(client):
    op = await _crea_opportunita(client)
    r = await client.put(f"/api/v1/crm/pipeline/{op['id']}", json={"fase": "trattativa", "probabilita": 80})
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["fase"] == "trattativa"
    assert data["probabilita"] == 80


async def test_update_opportunita_probabilita_invalida_422(client):
    op = await _crea_opportunita(client)
    r = await client.put(f"/api/v1/crm/pipeline/{op['id']}", json={"probabilita": -5})
    assert r.status_code == 422


async def test_update_opportunita_inesistente(client):
    r = await client.put("/api/v1/crm/pipeline/9999", json={"fase": "trattativa"})
    assert r.status_code == 404


async def test_delete_opportunita(client):
    op = await _crea_opportunita(client)
    r = await client.delete(f"/api/v1/crm/pipeline/{op['id']}")
    assert r.status_code == 204

    r = await client.get("/api/v1/crm/pipeline")
    assert r.json() == []


async def test_delete_opportunita_inesistente(client):
    r = await client.delete("/api/v1/crm/pipeline/9999")
    assert r.status_code == 404


# ── Summary ──────────────────────────────────────────────────────────────────────

async def test_summary_vuoto(client):
    r = await client.get("/api/v1/crm/summary")
    assert r.status_code == 200
    data = r.json()
    assert data["totale_clienti"] == 0
    assert data["totale_fornitori"] == 0
    assert data["scadenze_aperte"] == 0
    assert data["scadenze_scadute"] == 0
    assert data["opportunita_aperte"] == 0
    assert Decimal(data["valore_pipeline_attivo"]) == Decimal("0")


async def test_summary_conta_correttamente(client):
    await _crea_anagrafica(client, nome="C1", tipo="cliente")
    await _crea_anagrafica(client, nome="F1", tipo="fornitore")
    await _crea_anagrafica(client, nome="E1", tipo="entrambi")

    ieri = (date.today() - timedelta(days=1)).isoformat()
    domani = (date.today() + timedelta(days=1)).isoformat()
    await _crea_scadenza(client, titolo="Futura", data_scadenza=domani, stato="aperta")
    await _crea_scadenza(client, titolo="Scaduta", data_scadenza=ieri, stato="aperta")
    await _crea_scadenza(client, titolo="Pagata", data_scadenza=ieri, stato="pagata")

    await _crea_opportunita(client, titolo="Aperta 1", fase="prospecting")
    op2 = await client.post("/api/v1/crm/pipeline", json={
        "titolo": "Aperta 2", "fase": "trattativa", "valore_stimato": "1000.00",
    })
    assert op2.status_code == 201
    await _crea_opportunita(client, titolo="Chiusa", fase="chiusa_vinta")

    r = await client.get("/api/v1/crm/summary")
    assert r.status_code == 200
    data = r.json()
    # clienti = cliente + entrambi = 2; fornitori = fornitore + entrambi = 2
    assert data["totale_clienti"] == 2
    assert data["totale_fornitori"] == 2
    assert data["scadenze_aperte"] == 1
    assert data["scadenze_scadute"] == 1
    assert data["opportunita_aperte"] == 2
    assert Decimal(data["valore_pipeline_attivo"]) == Decimal("1000.00")
