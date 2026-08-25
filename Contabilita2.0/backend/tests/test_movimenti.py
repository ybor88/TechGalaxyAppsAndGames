from decimal import Decimal


async def _crea_movimento(client, tipo="entrata", importo="100.00", data="2026-01-15",
                           descrizione="Test", categoria="Varie"):
    payload = {
        "data": data,
        "tipo": tipo,
        "importo": str(importo),
        "descrizione": descrizione,
        "categoria": categoria,
    }
    r = await client.post("/api/v1/movimenti/", json=payload)
    assert r.status_code == 201, r.text
    return r.json()


async def test_list_vuoto(client):
    r = await client.get("/api/v1/movimenti/")
    assert r.status_code == 200
    assert r.json() == []


async def test_create_movimento_entrata(client):
    mov = await _crea_movimento(client, tipo="entrata", importo="1000.00")
    assert mov["tipo"] == "entrata"
    assert Decimal(mov["importo"]) == Decimal("1000.00")
    assert mov["categoria"] == "Varie"
    assert mov["conto_id"] is None
    assert "id" in mov
    assert "created_at" in mov


async def test_create_movimento_uscita(client):
    mov = await _crea_movimento(client, tipo="uscita", importo="50.00")
    assert mov["tipo"] == "uscita"


async def test_create_movimento_tipo_invalido(client):
    payload = {
        "data": "2026-01-15",
        "tipo": "trasferimento",
        "importo": "10.00",
        "descrizione": "X",
    }
    r = await client.post("/api/v1/movimenti/", json=payload)
    assert r.status_code == 422


async def test_create_movimento_importo_zero(client):
    payload = {
        "data": "2026-01-15",
        "tipo": "entrata",
        "importo": "0.00",
        "descrizione": "X",
    }
    r = await client.post("/api/v1/movimenti/", json=payload)
    assert r.status_code == 422


async def test_create_movimento_importo_negativo(client):
    payload = {
        "data": "2026-01-15",
        "tipo": "entrata",
        "importo": "-10.00",
        "descrizione": "X",
    }
    r = await client.post("/api/v1/movimenti/", json=payload)
    assert r.status_code == 422


async def test_create_movimento_campi_mancanti(client):
    r = await client.post("/api/v1/movimenti/", json={"tipo": "entrata"})
    assert r.status_code == 422


async def test_list_movimenti_ordinati_per_data_desc(client):
    await _crea_movimento(client, data="2026-01-01", descrizione="Vecchio")
    await _crea_movimento(client, data="2026-03-01", descrizione="Recente")
    await _crea_movimento(client, data="2026-02-01", descrizione="Medio")

    r = await client.get("/api/v1/movimenti/")
    assert r.status_code == 200
    date = [m["data"] for m in r.json()]
    assert date == ["2026-03-01", "2026-02-01", "2026-01-01"]


async def test_list_movimenti_paginazione(client):
    for i in range(5):
        await _crea_movimento(client, data=f"2026-01-{i + 1:02d}", descrizione=f"Mov {i}")

    r = await client.get("/api/v1/movimenti/", params={"skip": 1, "limit": 2})
    assert r.status_code == 200
    assert len(r.json()) == 2


async def test_update_movimento(client):
    mov = await _crea_movimento(client, importo="100.00")
    r = await client.put(
        f"/api/v1/movimenti/{mov['id']}",
        json={"importo": "200.00", "descrizione": "Aggiornato"},
    )
    assert r.status_code == 200, r.text
    data = r.json()
    assert Decimal(data["importo"]) == Decimal("200.00")
    assert data["descrizione"] == "Aggiornato"
    assert data["tipo"] == mov["tipo"]


async def test_update_movimento_tipo_invalido(client):
    mov = await _crea_movimento(client)
    r = await client.put(f"/api/v1/movimenti/{mov['id']}", json={"tipo": "boh"})
    assert r.status_code == 422


async def test_update_movimento_inesistente(client):
    r = await client.put("/api/v1/movimenti/9999", json={"descrizione": "X"})
    assert r.status_code == 404


async def test_delete_movimento(client):
    mov = await _crea_movimento(client)
    r = await client.delete(f"/api/v1/movimenti/{mov['id']}")
    assert r.status_code == 204

    r = await client.get("/api/v1/movimenti/")
    assert r.json() == []


async def test_delete_movimento_inesistente(client):
    r = await client.delete("/api/v1/movimenti/9999")
    assert r.status_code == 404
