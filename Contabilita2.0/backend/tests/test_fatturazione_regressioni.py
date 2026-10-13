# © Roberto Di Flumeri
"""Test di regressione per bug trovati in fatturazione."""

from decimal import Decimal


def _riga(descr="Servizio", qta="1", prezzo="100.00", iva="22"):
    return {"descrizione": descr, "quantita": qta, "prezzo_unitario": prezzo, "iva_percentuale": iva}


async def _crea_doc(client, tipo="fattura_attiva", data="2026-03-10", **extra):
    payload = {"tipo": tipo, "data": data, "righe": [_riga()], **extra}
    r = await client.post("/api/v1/documenti/", json=payload)
    assert r.status_code == 201, r.text
    return r.json()


async def test_numerazione_non_duplica_dopo_cancellazione(client):
    """Cancellando un documento intermedio il numero successivo non deve collidere."""
    d1 = await _crea_doc(client)
    d2 = await _crea_doc(client)
    assert d1["numero"] == "FAT-2026-0001"
    assert d2["numero"] == "FAT-2026-0002"

    r = await client.delete(f"/api/v1/documenti/{d1['id']}")
    assert r.status_code == 204

    d3 = await _crea_doc(client)
    assert d3["numero"] == "FAT-2026-0003"

    r = await client.get("/api/v1/documenti/", params={"tipo": "fattura_attiva"})
    numeri = [d["numero"] for d in r.json()]
    assert len(numeri) == len(set(numeri))


async def test_numerazione_separata_per_anno(client):
    a = await _crea_doc(client, data="2025-12-31")
    b = await _crea_doc(client, data="2026-01-01")
    assert a["numero"] == "FAT-2025-0001"
    assert b["numero"] == "FAT-2026-0001"


async def test_pdf_con_caratteri_speciali_nei_testi(client):
    """Nome, oggetto e note con & < > non devono far fallire la generazione PDF."""
    r = await client.post(
        "/api/v1/anagrafiche/",
        json={
            "nome": "Rossi & Figli <S.r.l.>",
            "tipo": "cliente",
            "indirizzo": "Via <Roma> 1 & 2",
        },
    )
    assert r.status_code == 201, r.text
    ana_id = r.json()["id"]
    doc = await _crea_doc(
        client,
        anagrafica_id=ana_id,
        oggetto="Fornitura <urgente> & installazione",
        note="Pagamento a 30gg <bonifico> & ritenuta",
    )
    r = await client.get(f"/api/v1/documenti/{doc['id']}/pdf")
    assert r.status_code == 200, r.text
    assert r.content[:4] == b"%PDF"


async def test_pdf_con_molte_righe_multipagina(client):
    righe = [_riga(descr=f"Articolo di prova numero {i} con descrizione lunga", prezzo="12.34") for i in range(150)]
    r = await client.post(
        "/api/v1/documenti/",
        json={"tipo": "fattura_attiva", "data": "2026-04-01", "righe": righe},
    )
    assert r.status_code == 201, r.text
    doc = r.json()
    assert Decimal(doc["subtotale"]) == Decimal("1851.00")
    assert Decimal(doc["totale_iva"]) == Decimal("150") * Decimal("2.71")
    r = await client.get(f"/api/v1/documenti/{doc['id']}/pdf")
    assert r.status_code == 200
    assert r.content[:4] == b"%PDF"


async def test_documento_con_anagrafica_inesistente_rifiutato(client):
    r = await client.post(
        "/api/v1/documenti/",
        json={"tipo": "fattura_attiva", "data": "2026-04-01", "anagrafica_id": 99999, "righe": [_riga()]},
    )
    assert r.status_code == 422, r.text


async def test_arrotondamento_iva_per_riga(client):
    # 3 x 0.33 = 0.99 imponibile ; IVA 22% = 0.2178 -> 0.22
    r = await client.post(
        "/api/v1/documenti/",
        json={"tipo": "fattura_attiva", "data": "2026-04-01",
              "righe": [_riga(qta="3", prezzo="0.33", iva="22")]},
    )
    assert r.status_code == 201
    doc = r.json()
    assert Decimal(doc["subtotale"]) == Decimal("0.99")
    assert Decimal(doc["totale_iva"]) == Decimal("0.22")
    assert Decimal(doc["totale"]) == Decimal("1.21")
