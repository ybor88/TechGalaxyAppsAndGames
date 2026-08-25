from decimal import Decimal


async def _crea_conto(client, codice="20.01", descrizione="Cassa", tipo="attivo", saldo="0.00"):
    payload = {"codice": codice, "descrizione": descrizione, "tipo": tipo, "saldo": saldo}
    r = await client.post("/api/v1/conti/", json=payload)
    assert r.status_code == 201, r.text
    return r.json()


async def test_list_vuoto(client):
    r = await client.get("/api/v1/conti/")
    assert r.status_code == 200
    assert r.json() == []


async def test_create_conto(client):
    conto = await _crea_conto(client, codice="20.01", descrizione="Cassa", tipo="attivo", saldo="100.50")
    assert conto["codice"] == "20.01"
    assert conto["descrizione"] == "Cassa"
    assert conto["tipo"] == "attivo"
    assert Decimal(conto["saldo"]) == Decimal("100.50")
    assert "id" in conto
    assert "created_at" in conto


async def test_create_conto_saldo_default(client):
    payload = {"codice": "20.02", "descrizione": "Banca", "tipo": "attivo"}
    r = await client.post("/api/v1/conti/", json=payload)
    assert r.status_code == 201, r.text
    assert Decimal(r.json()["saldo"]) == Decimal("0.00")


async def test_create_conto_campi_mancanti(client):
    r = await client.post("/api/v1/conti/", json={"codice": "20.03"})
    assert r.status_code == 422


async def test_list_conti_ordinati_per_codice(client):
    await _crea_conto(client, codice="30.00", descrizione="Debiti")
    await _crea_conto(client, codice="10.00", descrizione="Cassa")
    await _crea_conto(client, codice="20.00", descrizione="Banca")

    r = await client.get("/api/v1/conti/")
    assert r.status_code == 200
    codici = [c["codice"] for c in r.json()]
    assert codici == ["10.00", "20.00", "30.00"]


async def test_list_conti_paginazione(client):
    for i in range(5):
        await _crea_conto(client, codice=f"{i:02d}.00", descrizione=f"Conto {i}")

    r = await client.get("/api/v1/conti/", params={"skip": 2, "limit": 2})
    assert r.status_code == 200
    data = r.json()
    assert len(data) == 2
    assert data[0]["codice"] == "02.00"
    assert data[1]["codice"] == "03.00"


async def test_update_conto(client):
    conto = await _crea_conto(client)
    r = await client.put(
        f"/api/v1/conti/{conto['id']}",
        json={"descrizione": "Cassa Contanti", "saldo": "250.00"},
    )
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["descrizione"] == "Cassa Contanti"
    assert Decimal(data["saldo"]) == Decimal("250.00")
    # Campi non passati restano invariati
    assert data["codice"] == conto["codice"]
    assert data["tipo"] == conto["tipo"]


async def test_update_conto_inesistente(client):
    r = await client.put("/api/v1/conti/9999", json={"descrizione": "X"})
    assert r.status_code == 404


async def test_delete_conto(client):
    conto = await _crea_conto(client)
    r = await client.delete(f"/api/v1/conti/{conto['id']}")
    assert r.status_code == 204

    r = await client.get("/api/v1/conti/")
    assert r.json() == []


async def test_delete_conto_inesistente(client):
    r = await client.delete("/api/v1/conti/9999")
    assert r.status_code == 404
