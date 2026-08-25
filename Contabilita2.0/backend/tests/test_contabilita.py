from decimal import Decimal


async def _crea_conto(client, codice, descrizione, tipo, saldo="0.00"):
    payload = {"codice": codice, "descrizione": descrizione, "tipo": tipo, "saldo": saldo}
    r = await client.post("/api/v1/conti/", json=payload)
    assert r.status_code == 201, r.text
    return r.json()


async def _crea_registrazione(client, righe, data="2026-01-15", causale="Test", tipo_causale="manuale"):
    payload = {"data": data, "causale": causale, "tipo_causale": tipo_causale, "righe": righe}
    return await client.post("/api/v1/contabilita/registrazioni", json=payload)


async def _saldo_conto(client, conto_id):
    r = await client.get("/api/v1/conti/")
    for c in r.json():
        if c["id"] == conto_id:
            return Decimal(c["saldo"])
    raise AssertionError(f"Conto {conto_id} non trovato")


# ── Piano dei conti standard ──────────────────────────────────────────────────

async def test_init_piano_conti_crea_conti(client):
    r = await client.post("/api/v1/contabilita/init-piano-conti")
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["conti_creati"] > 0
    assert data["conti_esistenti"] == 0

    r = await client.get("/api/v1/conti/")
    assert len(r.json()) == data["conti_creati"]


async def test_init_piano_conti_idempotente(client):
    r1 = await client.post("/api/v1/contabilita/init-piano-conti")
    creati_1 = r1.json()["conti_creati"]

    r2 = await client.post("/api/v1/contabilita/init-piano-conti")
    data2 = r2.json()
    assert data2["conti_creati"] == 0
    assert data2["conti_esistenti"] == creati_1


# ── Creazione registrazioni: validazione partita doppia ──────────────────────

async def test_registrazione_non_bilanciata_422(client):
    cassa = await _crea_conto(client, "10.01", "Cassa", "attivo")
    ricavi = await _crea_conto(client, "50.01", "Ricavi", "ricavo")
    righe = [
        {"conto_id": cassa["id"], "dare": "100.00", "avere": "0.00"},
        {"conto_id": ricavi["id"], "dare": "0.00", "avere": "90.00"},
    ]
    r = await _crea_registrazione(client, righe)
    assert r.status_code == 422


async def test_registrazione_importo_zero_422(client):
    cassa = await _crea_conto(client, "10.01", "Cassa", "attivo")
    ricavi = await _crea_conto(client, "50.01", "Ricavi", "ricavo")
    righe = [
        {"conto_id": cassa["id"], "dare": "0.00", "avere": "0.00"},
        {"conto_id": ricavi["id"], "dare": "0.00", "avere": "0.00"},
    ]
    r = await _crea_registrazione(client, righe)
    assert r.status_code == 422


async def test_registrazione_meno_di_due_righe_422(client):
    cassa = await _crea_conto(client, "10.01", "Cassa", "attivo")
    righe = [{"conto_id": cassa["id"], "dare": "100.00", "avere": "0.00"}]
    r = await _crea_registrazione(client, righe)
    assert r.status_code == 422


async def test_registrazione_conto_inesistente_422(client):
    cassa = await _crea_conto(client, "10.01", "Cassa", "attivo")
    righe = [
        {"conto_id": cassa["id"], "dare": "100.00", "avere": "0.00"},
        {"conto_id": 9999, "dare": "0.00", "avere": "100.00"},
    ]
    r = await _crea_registrazione(client, righe)
    assert r.status_code == 422


# ── Creazione registrazioni: contabilità corretta ─────────────────────────────

async def test_registrazione_bilanciata_aggiorna_saldi_secondo_tipo_conto(client):
    cassa = await _crea_conto(client, "10.01", "Cassa", "attivo")
    ricavi = await _crea_conto(client, "50.01", "Ricavi", "ricavo")

    righe = [
        {"conto_id": cassa["id"], "descrizione": "Incasso", "dare": "100.00", "avere": "0.00"},
        {"conto_id": ricavi["id"], "descrizione": "Vendita", "dare": "0.00", "avere": "100.00"},
    ]
    r = await _crea_registrazione(client, righe)
    assert r.status_code == 201, r.text
    reg = r.json()
    assert reg["numero"] == 1
    assert reg["chiusa"] is False
    assert Decimal(reg["totale_dare"]) == Decimal("100.00")
    assert Decimal(reg["totale_avere"]) == Decimal("100.00")
    assert len(reg["righe"]) == 2

    # Attivo: saldo aumenta con dare
    assert await _saldo_conto(client, cassa["id"]) == Decimal("100.00")
    # Ricavo: saldo aumenta con avere
    assert await _saldo_conto(client, ricavi["id"]) == Decimal("100.00")


async def test_registrazione_passivo_e_costo_direzione_saldo_opposta(client):
    fornitori = await _crea_conto(client, "21.01", "Debiti v/fornitori", "passivo")
    acquisti = await _crea_conto(client, "40.01", "Acquisti", "costo")

    righe = [
        {"conto_id": acquisti["id"], "dare": "50.00", "avere": "0.00"},
        {"conto_id": fornitori["id"], "dare": "0.00", "avere": "50.00"},
    ]
    r = await _crea_registrazione(client, righe)
    assert r.status_code == 201, r.text

    # Costo: saldo aumenta con dare
    assert await _saldo_conto(client, acquisti["id"]) == Decimal("50.00")
    # Passivo: saldo aumenta con avere
    assert await _saldo_conto(client, fornitori["id"]) == Decimal("50.00")


async def test_numerazione_progressiva(client):
    cassa = await _crea_conto(client, "10.01", "Cassa", "attivo")
    ricavi = await _crea_conto(client, "50.01", "Ricavi", "ricavo")
    righe = [
        {"conto_id": cassa["id"], "dare": "10.00", "avere": "0.00"},
        {"conto_id": ricavi["id"], "dare": "0.00", "avere": "10.00"},
    ]
    r1 = await _crea_registrazione(client, righe)
    r2 = await _crea_registrazione(client, righe)
    assert r1.json()["numero"] == 1
    assert r2.json()["numero"] == 2


# ── Lettura / lista registrazioni ─────────────────────────────────────────────

async def test_get_registrazione(client):
    cassa = await _crea_conto(client, "10.01", "Cassa", "attivo")
    ricavi = await _crea_conto(client, "50.01", "Ricavi", "ricavo")
    righe = [
        {"conto_id": cassa["id"], "dare": "10.00", "avere": "0.00"},
        {"conto_id": ricavi["id"], "dare": "0.00", "avere": "10.00"},
    ]
    reg = (await _crea_registrazione(client, righe)).json()
    r = await client.get(f"/api/v1/contabilita/registrazioni/{reg['id']}")
    assert r.status_code == 200
    assert r.json()["righe"][0]["conto_codice"] == "10.01"


async def test_get_registrazione_inesistente(client):
    r = await client.get("/api/v1/contabilita/registrazioni/9999")
    assert r.status_code == 404


async def test_list_registrazioni_filtro_date(client):
    cassa = await _crea_conto(client, "10.01", "Cassa", "attivo")
    ricavi = await _crea_conto(client, "50.01", "Ricavi", "ricavo")
    righe = [
        {"conto_id": cassa["id"], "dare": "10.00", "avere": "0.00"},
        {"conto_id": ricavi["id"], "dare": "0.00", "avere": "10.00"},
    ]
    await _crea_registrazione(client, righe, data="2026-01-01")
    await _crea_registrazione(client, righe, data="2026-06-01")
    await _crea_registrazione(client, righe, data="2026-12-01")

    r = await client.get(
        "/api/v1/contabilita/registrazioni",
        params={"data_da": "2026-02-01", "data_a": "2026-07-01"},
    )
    assert r.status_code == 200
    data = r.json()
    assert len(data) == 1
    assert data[0]["data"] == "2026-06-01"
    assert Decimal(data[0]["totale_dare"]) == Decimal("10.00")


# ── Chiusura / eliminazione ────────────────────────────────────────────────────

async def test_chiudi_registrazione(client):
    cassa = await _crea_conto(client, "10.01", "Cassa", "attivo")
    ricavi = await _crea_conto(client, "50.01", "Ricavi", "ricavo")
    righe = [
        {"conto_id": cassa["id"], "dare": "10.00", "avere": "0.00"},
        {"conto_id": ricavi["id"], "dare": "0.00", "avere": "10.00"},
    ]
    reg = (await _crea_registrazione(client, righe)).json()

    r = await client.post(f"/api/v1/contabilita/registrazioni/{reg['id']}/chiudi")
    assert r.status_code == 200

    r = await client.get(f"/api/v1/contabilita/registrazioni/{reg['id']}")
    assert r.json()["chiusa"] is True


async def test_chiudi_registrazione_inesistente(client):
    r = await client.post("/api/v1/contabilita/registrazioni/9999/chiudi")
    assert r.status_code == 404


async def test_elimina_registrazione_ripristina_saldi(client):
    cassa = await _crea_conto(client, "10.01", "Cassa", "attivo")
    ricavi = await _crea_conto(client, "50.01", "Ricavi", "ricavo")
    righe = [
        {"conto_id": cassa["id"], "dare": "100.00", "avere": "0.00"},
        {"conto_id": ricavi["id"], "dare": "0.00", "avere": "100.00"},
    ]
    reg = (await _crea_registrazione(client, righe)).json()
    assert await _saldo_conto(client, cassa["id"]) == Decimal("100.00")

    r = await client.delete(f"/api/v1/contabilita/registrazioni/{reg['id']}")
    assert r.status_code == 204

    assert await _saldo_conto(client, cassa["id"]) == Decimal("0.00")
    assert await _saldo_conto(client, ricavi["id"]) == Decimal("0.00")

    r = await client.get(f"/api/v1/contabilita/registrazioni/{reg['id']}")
    assert r.status_code == 404


async def test_elimina_registrazione_inesistente(client):
    r = await client.delete("/api/v1/contabilita/registrazioni/9999")
    assert r.status_code == 404


async def test_elimina_registrazione_chiusa_409(client):
    cassa = await _crea_conto(client, "10.01", "Cassa", "attivo")
    ricavi = await _crea_conto(client, "50.01", "Ricavi", "ricavo")
    righe = [
        {"conto_id": cassa["id"], "dare": "10.00", "avere": "0.00"},
        {"conto_id": ricavi["id"], "dare": "0.00", "avere": "10.00"},
    ]
    reg = (await _crea_registrazione(client, righe)).json()
    await client.post(f"/api/v1/contabilita/registrazioni/{reg['id']}/chiudi")

    r = await client.delete(f"/api/v1/contabilita/registrazioni/{reg['id']}")
    assert r.status_code == 409

    # Il saldo non deve essere stato toccato
    assert await _saldo_conto(client, cassa["id"]) == Decimal("10.00")


# ── Bilancio di verifica ──────────────────────────────────────────────────────

async def test_bilancio_vuoto(client):
    r = await client.get("/api/v1/contabilita/bilancio")
    assert r.status_code == 200
    data = r.json()
    assert data["conti"] == []
    assert Decimal(data["totale_dare"]) == Decimal("0")
    assert Decimal(data["totale_avere"]) == Decimal("0")


async def test_bilancio_quadra_dare_avere_e_calcola_utile(client):
    cassa = await _crea_conto(client, "10.01", "Cassa", "attivo")
    ricavi = await _crea_conto(client, "50.01", "Ricavi", "ricavo")
    fornitori = await _crea_conto(client, "21.01", "Fornitori", "passivo")
    acquisti = await _crea_conto(client, "40.01", "Acquisti", "costo")

    # Vendita: incasso 100 in cassa, ricavo 100
    await _crea_registrazione(client, [
        {"conto_id": cassa["id"], "dare": "100.00", "avere": "0.00"},
        {"conto_id": ricavi["id"], "dare": "0.00", "avere": "100.00"},
    ])
    # Acquisto: costo 40 pagato a debito fornitori
    await _crea_registrazione(client, [
        {"conto_id": acquisti["id"], "dare": "40.00", "avere": "0.00"},
        {"conto_id": fornitori["id"], "dare": "0.00", "avere": "40.00"},
    ])

    r = await client.get("/api/v1/contabilita/bilancio")
    assert r.status_code == 200
    data = r.json()

    # Il bilancio di verifica deve sempre quadrare (dare == avere totali)
    assert Decimal(data["totale_dare"]) == Decimal(data["totale_avere"])
    assert Decimal(data["totale_dare"]) == Decimal("140.00")

    assert Decimal(data["totale_attivo"]) == Decimal("100.00")
    assert Decimal(data["totale_passivo"]) == Decimal("40.00")
    assert Decimal(data["totale_ricavi"]) == Decimal("100.00")
    assert Decimal(data["totale_costi"]) == Decimal("40.00")
    assert Decimal(data["utile_perdita"]) == Decimal("60.00")


# ── Liquidazione IVA ───────────────────────────────────────────────────────────

async def test_liquidazione_iva_data_da_dopo_data_a_422(client):
    r = await client.get(
        "/api/v1/contabilita/iva",
        params={"data_da": "2026-06-01", "data_a": "2026-01-01"},
    )
    assert r.status_code == 422


async def test_liquidazione_iva_calcola_dettaglio_per_aliquota(client):
    crediti = await _crea_conto(client, "11.01", "Crediti clienti", "attivo")
    ricavi = await _crea_conto(client, "50.01", "Ricavi vendite", "ricavo")
    iva_debito = await _crea_conto(client, "22.01", "IVA a debito", "passivo")

    # Vendita di 100.00 + IVA 22% = 122.00 incassabile
    righe = [
        {"conto_id": crediti["id"], "dare": "122.00", "avere": "0.00"},
        {
            "conto_id": ricavi["id"], "dare": "0.00", "avere": "100.00",
            "tipo_iva": "imponibile", "aliquota_iva": "22.00",
        },
        {
            "conto_id": iva_debito["id"], "dare": "0.00", "avere": "22.00",
            "tipo_iva": "iva", "aliquota_iva": "22.00",
        },
    ]
    r = await _crea_registrazione(client, righe, data="2026-03-10")
    assert r.status_code == 201, r.text

    r = await client.get(
        "/api/v1/contabilita/iva",
        params={"data_da": "2026-01-01", "data_a": "2026-12-31"},
    )
    assert r.status_code == 200, r.text
    data = r.json()
    assert Decimal(data["iva_a_debito"]) == Decimal("22.00")
    assert Decimal(data["iva_a_credito"]) == Decimal("0.00")
    assert Decimal(data["saldo_iva"]) == Decimal("22.00")
    assert len(data["dettaglio"]) == 1
    riga = data["dettaglio"][0]
    assert Decimal(riga["aliquota_iva"]) == Decimal("22.00")
    assert Decimal(riga["imponibile_vendite"]) == Decimal("100.00")
    assert Decimal(riga["iva_a_debito"]) == Decimal("22.00")


async def test_liquidazione_iva_fuori_periodo_esclusa(client):
    crediti = await _crea_conto(client, "11.01", "Crediti clienti", "attivo")
    ricavi = await _crea_conto(client, "50.01", "Ricavi vendite", "ricavo")
    iva_debito = await _crea_conto(client, "22.01", "IVA a debito", "passivo")

    righe = [
        {"conto_id": crediti["id"], "dare": "122.00", "avere": "0.00"},
        {
            "conto_id": ricavi["id"], "dare": "0.00", "avere": "100.00",
            "tipo_iva": "imponibile", "aliquota_iva": "22.00",
        },
        {
            "conto_id": iva_debito["id"], "dare": "0.00", "avere": "22.00",
            "tipo_iva": "iva", "aliquota_iva": "22.00",
        },
    ]
    await _crea_registrazione(client, righe, data="2025-01-01")

    r = await client.get(
        "/api/v1/contabilita/iva",
        params={"data_da": "2026-01-01", "data_a": "2026-12-31"},
    )
    assert r.status_code == 200
    data = r.json()
    assert Decimal(data["iva_a_debito"]) == Decimal("0.00")
    assert data["dettaglio"] == []
