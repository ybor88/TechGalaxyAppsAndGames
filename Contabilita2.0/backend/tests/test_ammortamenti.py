from decimal import Decimal


async def _crea_conto(client, codice, descrizione, tipo):
    r = await client.post("/api/v1/conti/", json={
        "codice": codice, "descrizione": descrizione, "tipo": tipo, "saldo": "0.00",
    })
    assert r.status_code == 201, r.text
    return r.json()


async def _crea_cespite(client, conto_costo_id, conto_fondo_id, **overrides):
    payload = {
        "descrizione": "Furgone aziendale",
        "categoria": "autocarri",
        "data_acquisto": "2024-01-01",
        "costo_storico": "20000.00",
        "conto_costo_id": conto_costo_id,
        "conto_fondo_id": conto_fondo_id,
    }
    payload.update(overrides)
    r = await client.post("/api/v1/ammortamenti/cespiti", json=payload)
    assert r.status_code == 201, r.text
    return r.json()


# ── Tabella ministeriale ───────────────────────────────────────────────────────

async def test_list_categorie_include_autocarri(client):
    r = await client.get("/api/v1/ammortamenti/categorie")
    assert r.status_code == 200
    categorie = {c["categoria"] for c in r.json()}
    assert "autocarri" in categorie
    assert "software" in categorie


# ── Creazione cespite ────────────────────────────────────────────────────────

async def test_crea_cespite_usa_aliquota_ministeriale_di_default(client):
    costo = await _crea_conto(client, "42100", "Amm.to materiali", "costo")
    fondo = await _crea_conto(client, "14500", "Fondo amm.to", "attivo")
    cespite = await _crea_cespite(client, costo["id"], fondo["id"])
    # "autocarri" in tabella ministeriale = 20.00%
    assert Decimal(cespite["aliquota_fiscale"]) == Decimal("20.00")
    assert Decimal(cespite["aliquota_civilistica"]) == Decimal("20.00")
    assert cespite["categoria_label"]


async def test_crea_cespite_conto_inesistente_422(client):
    r = await client.post("/api/v1/ammortamenti/cespiti", json={
        "descrizione": "Test",
        "categoria": "autocarri",
        "data_acquisto": "2024-01-01",
        "costo_storico": "1000.00",
        "conto_costo_id": 9999,
        "conto_fondo_id": 9998,
    })
    assert r.status_code == 422


# ── Piano di ammortamento: quote costanti ───────────────────────────────────────

async def test_piano_ammortamento_quote_costanti_dopo_primo_anno(client):
    costo = await _crea_conto(client, "42100", "Amm.to materiali", "costo")
    fondo = await _crea_conto(client, "14500", "Fondo amm.to", "attivo")
    # Acquisto a gennaio: 12 mesi pieni nel primo anno -> nessuna riduzione civilistica.
    cespite = await _crea_cespite(
        client, costo["id"], fondo["id"],
        costo_storico="10000.00", categoria="mobili_arredi_ufficio",  # 12%
        data_acquisto="2024-01-01",
    )
    r = await client.get(f"/api/v1/ammortamenti/cespiti/{cespite['id']}/piano")
    assert r.status_code == 200
    quote = r.json()["quote"]

    # Fiscale: primo anno sempre dimezzato (art. 102 TUIR) = 1200/2 = 600.
    assert Decimal(quote[0]["quota_fiscale"]) == Decimal("600.00")
    # Civilistico: acquisto a gennaio -> 12/12 mesi -> quota piena 1200.
    assert Decimal(quote[0]["quota_civilistica"]) == Decimal("1200.00")
    # Anno successivo: quota fiscale piena.
    assert Decimal(quote[1]["quota_fiscale"]) == Decimal("1200.00")
    assert Decimal(quote[1]["quota_civilistica"]) == Decimal("1200.00")


async def test_piano_ammortamento_prorata_mese_acquisto_civilistico(client):
    costo = await _crea_conto(client, "42100", "Amm.to materiali", "costo")
    fondo = await _crea_conto(client, "14500", "Fondo amm.to", "attivo")
    # Acquisto a luglio (mese 7): mesi residui = 13 - 7 = 6 -> metà quota civilistica.
    cespite = await _crea_cespite(
        client, costo["id"], fondo["id"],
        costo_storico="10000.00", categoria="mobili_arredi_ufficio",  # 12%
        data_acquisto="2024-07-01",
    )
    r = await client.get(f"/api/v1/ammortamenti/cespiti/{cespite['id']}/piano")
    quote = r.json()["quote"]
    assert Decimal(quote[0]["quota_civilistica"]) == Decimal("600.00")  # 1200 * 6/12


async def test_piano_ammortamento_fondo_non_supera_costo_storico(client):
    costo = await _crea_conto(client, "42100", "Amm.to materiali", "costo")
    fondo = await _crea_conto(client, "14500", "Fondo amm.to", "attivo")
    cespite = await _crea_cespite(
        client, costo["id"], fondo["id"],
        costo_storico="1000.00", categoria="autovetture",  # 25%
        data_acquisto="2024-01-01",
    )
    r = await client.get(f"/api/v1/ammortamenti/cespiti/{cespite['id']}/piano")
    quote = r.json()["quote"]
    for q in quote:
        assert Decimal(q["fondo_civilistico"]) <= Decimal("1000.00")
        assert Decimal(q["fondo_fiscale"]) <= Decimal("1000.00")
    # L'ultimo anno il fondo deve coincidere esattamente col costo storico (nessun residuo perso).
    assert Decimal(quote[-1]["fondo_civilistico"]) == Decimal("1000.00")
    assert Decimal(quote[-1]["fondo_fiscale"]) == Decimal("1000.00")
    assert Decimal(quote[-1]["valore_residuo_civilistico"]) == Decimal("0.00")


async def test_piano_ammortamento_si_interrompe_a_dismissione(client):
    costo = await _crea_conto(client, "42100", "Amm.to materiali", "costo")
    fondo = await _crea_conto(client, "14500", "Fondo amm.to", "attivo")
    cespite = await _crea_cespite(
        client, costo["id"], fondo["id"],
        costo_storico="10000.00", categoria="mobili_arredi_ufficio",
        data_acquisto="2020-01-01",
    )
    r = await client.post(
        f"/api/v1/ammortamenti/cespiti/{cespite['id']}/dismetti",
        json={"data_dismissione": "2022-06-30"},
    )
    assert r.status_code == 200
    piano = await client.get(f"/api/v1/ammortamenti/cespiti/{cespite['id']}/piano")
    anni = [q["anno"] for q in piano.json()["quote"]]
    assert anni == [2020, 2021, 2022]


# ── Contabilizzazione in prima nota ──────────────────────────────────────────

async def test_contabilizza_quota_crea_registrazione_bilanciata(client):
    costo = await _crea_conto(client, "42100", "Amm.to materiali", "costo")
    fondo = await _crea_conto(client, "14500", "Fondo amm.to", "attivo")
    cespite = await _crea_cespite(
        client, costo["id"], fondo["id"],
        costo_storico="10000.00", categoria="mobili_arredi_ufficio",
        data_acquisto="2024-01-01",
    )
    r = await client.post(f"/api/v1/ammortamenti/cespiti/{cespite['id']}/contabilizza", json={"anno": 2024})
    assert r.status_code == 200, r.text
    reg_id = r.json()["registrazione_id"]

    dettaglio = (await client.get(f"/api/v1/contabilita/registrazioni/{reg_id}")).json()
    assert Decimal(dettaglio["totale_dare"]) == Decimal(dettaglio["totale_avere"])
    assert Decimal(dettaglio["totale_dare"]) == Decimal("1200.00")

    # Il fondo ammortamento è un conto rettificativo dell'attivo (tipo "attivo" ma
    # movimentato in avere): il suo saldo, calcolato come dare-avere, diventa
    # negativo e va a ridurre il totale attivo nel bilancio di verifica.
    conti = (await client.get("/api/v1/conti/")).json()
    fondo_aggiornato = next(c for c in conti if c["id"] == fondo["id"])
    assert Decimal(fondo_aggiornato["saldo"]) == Decimal("-1200.00")


async def test_contabilizza_quota_due_volte_stesso_anno_422(client):
    costo = await _crea_conto(client, "42100", "Amm.to materiali", "costo")
    fondo = await _crea_conto(client, "14500", "Fondo amm.to", "attivo")
    cespite = await _crea_cespite(
        client, costo["id"], fondo["id"],
        costo_storico="10000.00", categoria="mobili_arredi_ufficio",
        data_acquisto="2024-01-01",
    )
    r1 = await client.post(f"/api/v1/ammortamenti/cespiti/{cespite['id']}/contabilizza", json={"anno": 2024})
    assert r1.status_code == 200
    r2 = await client.post(f"/api/v1/ammortamenti/cespiti/{cespite['id']}/contabilizza", json={"anno": 2024})
    assert r2.status_code == 422


async def test_elimina_cespite_con_quote_contabilizzate_409(client):
    costo = await _crea_conto(client, "42100", "Amm.to materiali", "costo")
    fondo = await _crea_conto(client, "14500", "Fondo amm.to", "attivo")
    cespite = await _crea_cespite(
        client, costo["id"], fondo["id"],
        costo_storico="10000.00", categoria="mobili_arredi_ufficio",
        data_acquisto="2024-01-01",
    )
    await client.post(f"/api/v1/ammortamenti/cespiti/{cespite['id']}/contabilizza", json={"anno": 2024})
    r = await client.delete(f"/api/v1/ammortamenti/cespiti/{cespite['id']}")
    assert r.status_code == 409


# ── Riepilogo ────────────────────────────────────────────────────────────────

async def test_riepilogo_totali(client):
    costo = await _crea_conto(client, "42100", "Amm.to materiali", "costo")
    fondo = await _crea_conto(client, "14500", "Fondo amm.to", "attivo")
    await _crea_cespite(
        client, costo["id"], fondo["id"],
        costo_storico="10000.00", categoria="mobili_arredi_ufficio",
        data_acquisto="2024-01-01",
    )
    r = await client.get("/api/v1/ammortamenti/riepilogo?anno=2024")
    assert r.status_code == 200
    data = r.json()
    assert data["numero_cespiti"] == 1
    assert Decimal(data["totale_costo_storico"]) == Decimal("10000.00")
    assert Decimal(data["totale_quota_anno"]) == Decimal("1200.00")
