import math
from datetime import date


def _shift_month(base: date, offset: int) -> date:
    """Restituisce una data nel mese `base` + offset mesi (giorno fisso a 10 per evitare edge case)."""
    total = base.year * 12 + (base.month - 1) + offset
    anno, mese = divmod(total, 12)
    return date(anno, mese + 1, 10)


def _linreg(xs: list[float], ys: list[float]) -> tuple[float, float]:
    """Stessa formula OLS usata dal servizio, per calcolare i valori attesi nei test."""
    n = len(xs)
    if n < 2:
        return 0.0, (ys[0] if ys else 0.0)
    mx = sum(xs) / n
    my = sum(ys) / n
    num = sum((xs[i] - mx) * (ys[i] - my) for i in range(n))
    den = sum((xs[i] - mx) ** 2 for i in range(n))
    slope = num / den if den != 0 else 0.0
    return slope, my - slope * mx


async def _crea_movimento(client, tipo, importo, data):
    payload = {
        "data": data.isoformat(),
        "tipo": tipo,
        "importo": str(importo),
        "descrizione": "Forecast test",
        "categoria": "Varie",
    }
    r = await client.post("/api/v1/movimenti/", json=payload)
    assert r.status_code == 201, r.text
    return r.json()


# ── Previsione vendite: dati insufficienti (degrado senza crash) ─────────────

async def test_previsione_vendite_senza_dati(client):
    r = await client.get("/api/v1/forecasting/previsione-vendite", params={"mesi": 3})
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["storico"] == []
    assert len(data["previsione"]) == 3
    assert data["trend"] == "stabile"
    assert all(p["valore"] == 0.0 for p in data["previsione"])


async def test_previsione_liquidita_senza_dati(client):
    r = await client.get("/api/v1/forecasting/previsione-liquidita", params={"giorni": 15})
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["saldo_attuale"] == 0.0
    assert len(data["previsione_giorni"]) == 15


async def test_simulazione_scenari_senza_dati(client):
    r = await client.get("/api/v1/forecasting/simulazione-scenari")
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["media_storica_entrate"] == 0.0
    assert data["media_storica_uscite"] == 0.0
    assert len(data["scenari"]) == 3
    for s in data["scenari"]:
        assert s["cashflow"] == 0.0


async def test_rischio_insolvenza_senza_dati(client):
    r = await client.get("/api/v1/forecasting/rischio-insolvenza")
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["punteggio"] == 0.0
    assert data["livello"] == "basso"
    assert data["fattori"] == []
    assert data["raccomandazioni"] == ["Nessun dato storico disponibile."]


# ── Previsione vendite: con dati storici sufficienti ──────────────────────────

async def test_previsione_vendite_con_trend_crescente(client):
    oggi = date.today()
    importi = [1000, 1100, 1200, 1300]  # trend lineare crescente, slope=100/mese
    for offset, importo in zip([-3, -2, -1, 0], importi):
        await _crea_movimento(client, "entrata", str(importo), _shift_month(oggi, offset))

    r = await client.get("/api/v1/forecasting/previsione-vendite", params={"mesi": 2})
    assert r.status_code == 200, r.text
    data = r.json()

    assert len(data["storico"]) == 4
    assert len(data["previsione"]) == 2
    assert data["trend"] == "crescente"
    assert data["variazione_percentuale"] > 0

    # Verifica numerica: la previsione deve essere coerente con la regressione OLS attesa
    xs = list(range(4))
    slope, intercept = _linreg(xs, [float(v) for v in importi])
    atteso_primo = max(slope * 4 + intercept, 0)
    assert abs(data["previsione"][0]["valore"] - atteso_primo) < 1.0


async def test_previsione_vendite_trend_decrescente(client):
    oggi = date.today()
    importi = [2000, 1500, 1000, 500]
    for offset, importo in zip([-3, -2, -1, 0], importi):
        await _crea_movimento(client, "entrata", str(importo), _shift_month(oggi, offset))

    r = await client.get("/api/v1/forecasting/previsione-vendite", params={"mesi": 1})
    assert r.status_code == 200
    data = r.json()
    assert data["trend"] == "decrescente"
    assert data["variazione_percentuale"] < 0


# ── Previsione liquidità: con dati storici ────────────────────────────────────

async def test_previsione_liquidita_con_dati_cashflow_positivo(client):
    oggi = date.today()
    # Alcuni giorni recenti con cashflow netto positivo
    await _crea_movimento(client, "entrata", "1000.00", oggi)
    await _crea_movimento(client, "uscita", "200.00", oggi)

    r = await client.get("/api/v1/forecasting/previsione-liquidita", params={"giorni": 10})
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["saldo_attuale"] == 800.0
    assert len(data["previsione_giorni"]) == 10
    # Con saldo iniziale positivo e cashflow medio positivo, non deve scattare l'allerta
    assert data["allerta"] is False
    assert data["giorni_copertura"] == 10
    # Il saldo previsto deve crescere nel tempo (cashflow medio giornaliero positivo)
    assert data["previsione_giorni"][-1]["valore"] > data["previsione_giorni"][0]["valore"]


async def test_previsione_liquidita_cashflow_negativo_genera_allerta(client):
    oggi = date.today()
    await _crea_movimento(client, "entrata", "100.00", oggi)
    await _crea_movimento(client, "uscita", "5000.00", oggi)

    r = await client.get("/api/v1/forecasting/previsione-liquidita", params={"giorni": 30})
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["saldo_attuale"] < 0
    assert data["allerta"] is True
    assert data["giorni_copertura"] < 30


# ── Simulazione scenari: con dati ─────────────────────────────────────────────

async def test_simulazione_scenari_con_dati_ordina_scenari_coerentemente(client):
    oggi = date.today()
    for offset in [-2, -1, 0]:
        await _crea_movimento(client, "entrata", "1000.00", _shift_month(oggi, offset))
        await _crea_movimento(client, "uscita", "600.00", _shift_month(oggi, offset))

    r = await client.get("/api/v1/forecasting/simulazione-scenari")
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["media_storica_entrate"] == 1000.0
    assert data["media_storica_uscite"] == 600.0

    scenari = {s["scenario"]: s for s in data["scenari"]}
    assert set(scenari.keys()) == {"ottimistico", "base", "pessimistico"}
    # Cashflow atteso: ottimistico > base > pessimistico
    assert scenari["ottimistico"]["cashflow"] > scenari["base"]["cashflow"] > scenari["pessimistico"]["cashflow"]
    assert scenari["base"]["cashflow"] == 400.0  # 1000 - 600
    assert scenari["base"]["variazione_percentuale"] == 0.0


# ── Rischio insolvenza: con dati ad alto rischio ──────────────────────────────

async def test_rischio_insolvenza_alto_con_cashflow_costantemente_negativo(client):
    oggi = date.today()
    for offset in range(-11, 1):  # 12 mesi
        await _crea_movimento(client, "entrata", "500.00", _shift_month(oggi, offset))
        await _crea_movimento(client, "uscita", "2000.00", _shift_month(oggi, offset))

    r = await client.get("/api/v1/forecasting/rischio-insolvenza")
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["punteggio"] > 50
    assert data["livello"] in ("alto", "critico")
    assert len(data["fattori"]) > 0
    assert any("Cashflow medio negativo" in f["fattore"] for f in data["fattori"])
    assert len(data["raccomandazioni"]) > 0


async def test_rischio_insolvenza_basso_con_finanze_sane(client):
    oggi = date.today()
    for offset in range(-11, 1):
        await _crea_movimento(client, "entrata", "5000.00", _shift_month(oggi, offset))
        await _crea_movimento(client, "uscita", "1000.00", _shift_month(oggi, offset))

    r = await client.get("/api/v1/forecasting/rischio-insolvenza")
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["livello"] == "basso"
    assert data["punteggio"] < 25
