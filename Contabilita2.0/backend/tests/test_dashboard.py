from decimal import Decimal


async def _crea_movimento(client, tipo, importo, data="2026-01-15", categoria="Vendite"):
    payload = {
        "data": data,
        "tipo": tipo,
        "importo": str(importo),
        "descrizione": f"Movimento {tipo}",
        "categoria": categoria,
    }
    r = await client.post("/api/v1/movimenti/", json=payload)
    assert r.status_code == 201, r.text
    return r.json()


async def test_kpi_vuoto(client):
    r = await client.get("/api/v1/dashboard/kpi")
    assert r.status_code == 200
    data = r.json()
    assert Decimal(data["totale_entrate"]) == 0
    assert Decimal(data["totale_uscite"]) == 0
    assert Decimal(data["indice_liquidita"]) == 0


async def test_kpi_con_movimenti(client):
    await _crea_movimento(client, "entrata", "1000.00")
    await _crea_movimento(client, "uscita", "400.00")

    r = await client.get("/api/v1/dashboard/kpi")
    assert r.status_code == 200
    data = r.json()
    assert Decimal(data["totale_entrate"]) == Decimal("1000.00")
    assert Decimal(data["totale_uscite"]) == Decimal("400.00")
    assert Decimal(data["saldo_operativo"]) == Decimal("600.00")
    assert Decimal(data["cashflow_netto"]) == Decimal("600.00")
    assert Decimal(data["indice_liquidita"]) == Decimal("2.5")


async def test_andamento_mensile(client):
    await _crea_movimento(client, "entrata", "500.00")
    r = await client.get("/api/v1/dashboard/andamento-mensile", params={"mesi": 12})
    assert r.status_code == 200
    data = r.json()
    assert len(data) == 1
    assert Decimal(data[0]["entrate"]) == Decimal("500.00")


async def test_cashflow_settimanale_non_va_in_errore(client):
    """Regressione: l'endpoint passava settimane= a un metodo che accetta solo mesi=."""
    await _crea_movimento(client, "entrata", "500.00")
    r = await client.get("/api/v1/dashboard/cashflow-settimanale", params={"settimane": 8})
    assert r.status_code == 200
    data = r.json()
    assert len(data) == 1
    assert Decimal(data[0]["entrate"]) == Decimal("500.00")
    assert Decimal(data[0]["cashflow"]) == Decimal("500.00")


async def test_dashboard_completa(client):
    await _crea_movimento(client, "entrata", "500.00")
    await _crea_movimento(client, "uscita", "200.00")
    r = await client.get("/api/v1/dashboard/")
    assert r.status_code == 200
    data = r.json()
    assert "kpi" in data
    assert "andamento_mensile" in data
    assert "cashflow_settimanale" in data
    assert "aggiornato_al" in data
