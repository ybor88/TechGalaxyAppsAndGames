from decimal import Decimal


# ── Anagrafiche ──────────────────────────────────────────────────────────────

async def _crea_anagrafica(client, nome="Acme Srl", tipo="cliente", **extra):
    payload = {"nome": nome, "tipo": tipo, **extra}
    r = await client.post("/api/v1/anagrafiche/", json=payload)
    assert r.status_code == 201, r.text
    return r.json()


async def test_list_anagrafiche_vuoto(client):
    r = await client.get("/api/v1/anagrafiche/")
    assert r.status_code == 200
    assert r.json() == []


async def test_create_anagrafica(client):
    ana = await _crea_anagrafica(client, nome="Acme Srl", tipo="cliente", piva="12345678901")
    assert ana["nome"] == "Acme Srl"
    assert ana["tipo"] == "cliente"
    assert ana["paese"] == "Italia"
    assert "id" in ana


async def test_create_anagrafica_tipo_invalido(client):
    r = await client.post("/api/v1/anagrafiche/", json={"nome": "X", "tipo": "boh"})
    assert r.status_code == 422


async def test_get_anagrafica(client):
    ana = await _crea_anagrafica(client)
    r = await client.get(f"/api/v1/anagrafiche/{ana['id']}")
    assert r.status_code == 200
    assert r.json()["nome"] == ana["nome"]


async def test_get_anagrafica_inesistente(client):
    r = await client.get("/api/v1/anagrafiche/9999")
    assert r.status_code == 404


async def test_list_anagrafiche_filtro_tipo(client):
    await _crea_anagrafica(client, nome="Cliente A", tipo="cliente")
    await _crea_anagrafica(client, nome="Fornitore B", tipo="fornitore")

    r = await client.get("/api/v1/anagrafiche/", params={"tipo": "fornitore"})
    assert r.status_code == 200
    data = r.json()
    assert len(data) == 1
    assert data[0]["nome"] == "Fornitore B"


async def test_update_anagrafica(client):
    ana = await _crea_anagrafica(client)
    r = await client.put(f"/api/v1/anagrafiche/{ana['id']}", json={"citta": "Milano"})
    assert r.status_code == 200, r.text
    assert r.json()["citta"] == "Milano"
    assert r.json()["nome"] == ana["nome"]


async def test_update_anagrafica_inesistente(client):
    r = await client.put("/api/v1/anagrafiche/9999", json={"citta": "Milano"})
    assert r.status_code == 404


async def test_delete_anagrafica(client):
    ana = await _crea_anagrafica(client)
    r = await client.delete(f"/api/v1/anagrafiche/{ana['id']}")
    assert r.status_code == 204
    r = await client.get(f"/api/v1/anagrafiche/{ana['id']}")
    assert r.status_code == 404


async def test_delete_anagrafica_inesistente(client):
    r = await client.delete("/api/v1/anagrafiche/9999")
    assert r.status_code == 404


# ── Documenti ────────────────────────────────────────────────────────────────

async def _crea_documento(client, tipo="fattura_attiva", data="2026-01-15", righe=None,
                           anagrafica_id=None, oggetto="Test"):
    payload = {
        "tipo": tipo,
        "data": data,
        "anagrafica_id": anagrafica_id,
        "oggetto": oggetto,
        "righe": righe if righe is not None else [],
    }
    r = await client.post("/api/v1/documenti/", json=payload)
    assert r.status_code == 201, r.text
    return r.json()


async def test_list_documenti_vuoto(client):
    r = await client.get("/api/v1/documenti/")
    assert r.status_code == 200
    assert r.json() == []


async def test_create_documento_senza_righe(client):
    doc = await _crea_documento(client)
    assert doc["stato"] == "bozza"
    assert doc["numero"] == "FAT-2026-0001"
    assert Decimal(doc["subtotale"]) == Decimal("0.00")
    assert Decimal(doc["totale"]) == Decimal("0.00")
    assert doc["righe"] == []


async def test_create_documento_con_righe_calcola_totali(client):
    righe = [
        {"descrizione": "Servizio A", "quantita": "2", "prezzo_unitario": "100.00", "iva_percentuale": "22.00"},
        {"descrizione": "Servizio B", "quantita": "1", "prezzo_unitario": "50.00", "iva_percentuale": "10.00"},
    ]
    doc = await _crea_documento(client, righe=righe)
    # imponibile: 200.00 + 50.00 = 250.00
    # iva: 44.00 + 5.00 = 49.00
    assert Decimal(doc["subtotale"]) == Decimal("250.00")
    assert Decimal(doc["totale_iva"]) == Decimal("49.00")
    assert Decimal(doc["totale"]) == Decimal("299.00")
    assert len(doc["righe"]) == 2


async def test_create_documento_riga_quantita_zero(client):
    righe = [{"descrizione": "X", "quantita": "0", "prezzo_unitario": "10.00"}]
    r = await client.post(
        "/api/v1/documenti/",
        json={"tipo": "preventivo", "data": "2026-01-15", "righe": righe},
    )
    assert r.status_code == 422


async def test_create_documento_riga_prezzo_negativo(client):
    righe = [{"descrizione": "X", "quantita": "1", "prezzo_unitario": "-5.00"}]
    r = await client.post(
        "/api/v1/documenti/",
        json={"tipo": "preventivo", "data": "2026-01-15", "righe": righe},
    )
    assert r.status_code == 422


async def test_create_documento_riga_iva_fuori_range(client):
    righe = [{"descrizione": "X", "quantita": "1", "prezzo_unitario": "10.00", "iva_percentuale": "150"}]
    r = await client.post(
        "/api/v1/documenti/",
        json={"tipo": "preventivo", "data": "2026-01-15", "righe": righe},
    )
    assert r.status_code == 422


async def test_create_documento_tipo_invalido(client):
    r = await client.post(
        "/api/v1/documenti/",
        json={"tipo": "scontrino", "data": "2026-01-15", "righe": []},
    )
    assert r.status_code == 422


async def test_numerazione_progressiva_per_tipo_e_anno(client):
    doc1 = await _crea_documento(client, tipo="fattura_attiva", data="2026-01-15")
    doc2 = await _crea_documento(client, tipo="fattura_attiva", data="2026-06-15")
    doc3 = await _crea_documento(client, tipo="preventivo", data="2026-01-15")
    doc4 = await _crea_documento(client, tipo="fattura_attiva", data="2025-01-15")

    assert doc1["numero"] == "FAT-2026-0001"
    assert doc2["numero"] == "FAT-2026-0002"
    assert doc3["numero"] == "PRE-2026-0001"
    assert doc4["numero"] == "FAT-2025-0001"


async def test_documento_con_anagrafica(client):
    ana = await _crea_anagrafica(client, nome="Cliente Test")
    doc = await _crea_documento(client, anagrafica_id=ana["id"])
    assert doc["anagrafica"]["nome"] == "Cliente Test"
    assert doc["anagrafica_id"] == ana["id"]


async def test_get_documento(client):
    doc = await _crea_documento(client)
    r = await client.get(f"/api/v1/documenti/{doc['id']}")
    assert r.status_code == 200
    assert r.json()["id"] == doc["id"]


async def test_get_documento_inesistente(client):
    r = await client.get("/api/v1/documenti/9999")
    assert r.status_code == 404


async def test_list_documenti_filtro_tipo_stato(client):
    await _crea_documento(client, tipo="fattura_attiva")
    await _crea_documento(client, tipo="preventivo")

    r = await client.get("/api/v1/documenti/", params={"tipo": "preventivo"})
    assert r.status_code == 200
    data = r.json()
    assert len(data) == 1
    assert data[0]["tipo"] == "preventivo"

    r = await client.get("/api/v1/documenti/", params={"stato": "bozza"})
    assert len(r.json()) == 2

    r = await client.get("/api/v1/documenti/", params={"stato": "pagato"})
    assert len(r.json()) == 0


async def test_update_documento_stato(client):
    doc = await _crea_documento(client)
    r = await client.put(f"/api/v1/documenti/{doc['id']}", json={"stato": "emesso"})
    assert r.status_code == 200, r.text
    assert r.json()["stato"] == "emesso"


async def test_update_documento_stato_invalido(client):
    doc = await _crea_documento(client)
    r = await client.put(f"/api/v1/documenti/{doc['id']}", json={"stato": "boh"})
    assert r.status_code == 422


async def test_update_documento_sostituisce_righe_e_ricalcola_totali(client):
    doc = await _crea_documento(
        client,
        righe=[{"descrizione": "A", "quantita": "1", "prezzo_unitario": "100.00", "iva_percentuale": "22.00"}],
    )
    assert Decimal(doc["totale"]) == Decimal("122.00")

    nuove_righe = [
        {"descrizione": "B", "quantita": "3", "prezzo_unitario": "10.00", "iva_percentuale": "0.00"},
    ]
    r = await client.put(f"/api/v1/documenti/{doc['id']}", json={"righe": nuove_righe})
    assert r.status_code == 200, r.text
    data = r.json()
    assert len(data["righe"]) == 1
    assert data["righe"][0]["descrizione"] == "B"
    assert Decimal(data["subtotale"]) == Decimal("30.00")
    assert Decimal(data["totale_iva"]) == Decimal("0.00")
    assert Decimal(data["totale"]) == Decimal("30.00")


async def test_update_documento_inesistente(client):
    r = await client.put("/api/v1/documenti/9999", json={"stato": "emesso"})
    assert r.status_code == 404


async def test_delete_documento(client):
    doc = await _crea_documento(client)
    r = await client.delete(f"/api/v1/documenti/{doc['id']}")
    assert r.status_code == 204
    r = await client.get(f"/api/v1/documenti/{doc['id']}")
    assert r.status_code == 404


async def test_delete_documento_inesistente(client):
    r = await client.delete("/api/v1/documenti/9999")
    assert r.status_code == 404


# ── PDF ──────────────────────────────────────────────────────────────────────

async def test_download_pdf(client):
    ana = await _crea_anagrafica(client, nome="Cliente PDF", piva="01234567890")
    righe = [{"descrizione": "Consulenza", "quantita": "1", "prezzo_unitario": "500.00", "iva_percentuale": "22.00"}]
    doc = await _crea_documento(client, righe=righe, anagrafica_id=ana["id"])

    r = await client.get(f"/api/v1/documenti/{doc['id']}/pdf")
    assert r.status_code == 200
    assert r.headers["content-type"] == "application/pdf"
    assert "attachment" in r.headers["content-disposition"]
    assert doc["numero"] in r.headers["content-disposition"]
    # Header PDF valido
    assert r.content[:5] == b"%PDF-"
    assert len(r.content) > 100


async def test_download_pdf_documento_inesistente(client):
    """Verifica che /9999/pdf ritorni 404 e non venga catturato dalla rotta /{documento_id}."""
    r = await client.get("/api/v1/documenti/9999/pdf")
    assert r.status_code == 404


async def test_download_pdf_senza_righe_e_senza_anagrafica(client):
    doc = await _crea_documento(client, anagrafica_id=None, righe=[])
    r = await client.get(f"/api/v1/documenti/{doc['id']}/pdf")
    assert r.status_code == 200
    assert r.content[:5] == b"%PDF-"
