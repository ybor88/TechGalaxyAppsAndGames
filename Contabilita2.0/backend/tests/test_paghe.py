from decimal import Decimal

from app.services.paghe import (
    calcola_irpef_lorda_annua,
    calcola_detrazione_lavoro_dipendente_annua,
)


# ── Unit test: motore di calcolo IRPEF a scaglioni (numeri esatti) ──────────────

def test_irpef_scaglione_23_percento():
    # Fino a 28.000: aliquota unica 23%.
    assert calcola_irpef_lorda_annua(Decimal("28000")) == Decimal("6440.00")


def test_irpef_scaglione_35_percento():
    # 6440 (primo scaglione) + 35% su (50000-28000) = 6440 + 7700 = 14140.
    assert calcola_irpef_lorda_annua(Decimal("50000")) == Decimal("14140.00")


def test_irpef_scaglione_43_percento():
    # 14140 + 43% su (60000-50000) = 14140 + 4300 = 18440.
    assert calcola_irpef_lorda_annua(Decimal("60000")) == Decimal("18440.00")


def test_irpef_reddito_zero():
    assert calcola_irpef_lorda_annua(Decimal("0")) == Decimal("0.00")


# ── Unit test: detrazione lavoro dipendente (numeri esatti) ─────────────────────

def test_detrazione_fino_15000_flat():
    assert calcola_detrazione_lavoro_dipendente_annua(Decimal("15000")) == Decimal("1955.00")


def test_detrazione_a_28000_e_1910():
    # (28000-28000)/13000 = 0 -> detrazione = 1910 + 0.
    assert calcola_detrazione_lavoro_dipendente_annua(Decimal("28000")) == Decimal("1910.00")


def test_detrazione_punto_medio_fascia_15000_28000():
    # 1910 + 1190 * (6500/13000) = 1910 + 595 = 2505.
    assert calcola_detrazione_lavoro_dipendente_annua(Decimal("21500")) == Decimal("2505.00")


def test_detrazione_punto_medio_fascia_28000_50000():
    # 1190 * (11000/22000) = 595.
    assert calcola_detrazione_lavoro_dipendente_annua(Decimal("39000")) == Decimal("595.00")


def test_detrazione_a_50000_e_zero():
    assert calcola_detrazione_lavoro_dipendente_annua(Decimal("50000")) == Decimal("0.00")


def test_detrazione_oltre_50000_e_zero():
    assert calcola_detrazione_lavoro_dipendente_annua(Decimal("60000")) == Decimal("0.00")


# ── Integration test: API dipendenti + cedolini ─────────────────────────────────

async def _crea_dipendente(client, **overrides):
    payload = {
        "nome": "Mario",
        "cognome": "Rossi",
        "data_assunzione": "2023-01-01",
        "retribuzione_lorda_mensile": "2000.00",
        "numero_mensilita": 13,
    }
    payload.update(overrides)
    r = await client.post("/api/v1/paghe/dipendenti", json=payload)
    assert r.status_code == 201, r.text
    return r.json()


async def test_crea_dipendente_default_aliquote(client):
    dip = await _crea_dipendente(client)
    assert Decimal(dip["aliquota_inps_dipendente"]) == Decimal("9.19")
    assert Decimal(dip["aliquota_inps_azienda"]) == Decimal("30.00")


async def test_anteprima_cedolino_coerenza_netto(client):
    dip = await _crea_dipendente(client, retribuzione_lorda_mensile="2000.00")
    r = await client.post(
        f"/api/v1/paghe/dipendenti/{dip['id']}/cedolini/anteprima",
        json={"anno": 2024, "mese": 1},
    )
    assert r.status_code == 200, r.text
    ced = r.json()

    lordo = Decimal(ced["retribuzione_lorda"])
    contributi_dip = Decimal(ced["contributi_inps_dipendente"])
    irpef_netta = Decimal(ced["irpef_netta"])
    netto = Decimal(ced["netto_busta"])

    assert contributi_dip == (lordo * Decimal("9.19") / 100).quantize(Decimal("0.01"))
    assert netto == lordo - contributi_dip - irpef_netta
    assert netto < lordo  # il netto deve sempre essere inferiore al lordo
    assert Decimal(ced["irpef_lorda"]) >= irpef_netta  # le detrazioni non aumentano l'imposta

    # TFR = lordo / 13.5
    assert Decimal(ced["quota_tfr"]) == (lordo / Decimal("13.5")).quantize(Decimal("0.01"))
    # Contributi azienda = lordo * 30%
    assert Decimal(ced["contributi_inps_azienda"]) == (lordo * Decimal("30") / 100).quantize(Decimal("0.01"))
    # Costo azienda = lordo + contributi azienda + TFR
    assert Decimal(ced["costo_azienda"]) == lordo + Decimal(ced["contributi_inps_azienda"]) + Decimal(ced["quota_tfr"])


async def test_detrazioni_disattivate_aumentano_irpef_netta(client):
    dip_con = await _crea_dipendente(client, retribuzione_lorda_mensile="2500.00", detrazioni_attive=True)
    dip_senza = await _crea_dipendente(client, retribuzione_lorda_mensile="2500.00", detrazioni_attive=False)

    r1 = await client.post(f"/api/v1/paghe/dipendenti/{dip_con['id']}/cedolini/anteprima", json={"anno": 2024, "mese": 1})
    r2 = await client.post(f"/api/v1/paghe/dipendenti/{dip_senza['id']}/cedolini/anteprima", json={"anno": 2024, "mese": 1})

    irpef_con_detrazioni = Decimal(r1.json()["irpef_netta"])
    irpef_senza_detrazioni = Decimal(r2.json()["irpef_netta"])
    assert irpef_senza_detrazioni > irpef_con_detrazioni
    assert Decimal(r1.json()["detrazioni_irpef"]) > Decimal("0.00")
    assert Decimal(r2.json()["detrazioni_irpef"]) == Decimal("0.00")


async def test_salva_cedolino_e_blocca_duplicato(client):
    dip = await _crea_dipendente(client)
    r1 = await client.post(f"/api/v1/paghe/dipendenti/{dip['id']}/cedolini", json={"anno": 2024, "mese": 3})
    assert r1.status_code == 201, r1.text

    r2 = await client.post(f"/api/v1/paghe/dipendenti/{dip['id']}/cedolini", json={"anno": 2024, "mese": 3})
    assert r2.status_code == 422

    lista = await client.get(f"/api/v1/paghe/dipendenti/{dip['id']}/cedolini")
    assert len(lista.json()) == 1


async def test_contabilizza_cedolino_registrazione_bilanciata(client):
    # Il piano dei conti standard fornisce tutti i conti richiesti dalla contabilizzazione.
    await client.post("/api/v1/contabilita/init-piano-conti")

    dip = await _crea_dipendente(client, retribuzione_lorda_mensile="2000.00")
    ced_r = await client.post(f"/api/v1/paghe/dipendenti/{dip['id']}/cedolini", json={"anno": 2024, "mese": 5})
    ced = ced_r.json()

    r = await client.post(f"/api/v1/paghe/cedolini/{ced['id']}/contabilizza")
    assert r.status_code == 200, r.text
    reg_id = r.json()["registrazione_id"]

    dettaglio = (await client.get(f"/api/v1/contabilita/registrazioni/{reg_id}")).json()
    assert Decimal(dettaglio["totale_dare"]) == Decimal(dettaglio["totale_avere"])

    lordo = Decimal(ced["retribuzione_lorda"])
    contributi_azienda = Decimal(ced["contributi_inps_azienda"])
    quota_tfr = Decimal(ced["quota_tfr"])
    assert Decimal(dettaglio["totale_dare"]) == lordo + contributi_azienda + quota_tfr

    # Non deve essere possibile contabilizzare due volte lo stesso cedolino.
    r2 = await client.post(f"/api/v1/paghe/cedolini/{ced['id']}/contabilizza")
    assert r2.status_code == 422


async def test_contabilizza_cedolino_senza_piano_conti_errore_chiaro(client):
    dip = await _crea_dipendente(client)
    ced_r = await client.post(f"/api/v1/paghe/dipendenti/{dip['id']}/cedolini", json={"anno": 2024, "mese": 6})
    ced = ced_r.json()

    r = await client.post(f"/api/v1/paghe/cedolini/{ced['id']}/contabilizza")
    assert r.status_code == 422
    assert "piano dei conti" in r.json()["detail"].lower()


async def test_elimina_cedolino_contabilizzato_409(client):
    await client.post("/api/v1/contabilita/init-piano-conti")
    dip = await _crea_dipendente(client)
    ced = (await client.post(f"/api/v1/paghe/dipendenti/{dip['id']}/cedolini", json={"anno": 2024, "mese": 7})).json()
    await client.post(f"/api/v1/paghe/cedolini/{ced['id']}/contabilizza")

    r = await client.delete(f"/api/v1/paghe/cedolini/{ced['id']}")
    assert r.status_code == 409
