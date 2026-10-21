"""Registrazione, sessione, consensi, area approssimata e blocchi. © Roberto Di Flumeri"""

from sqlalchemy import select

from app import geo
from app.models import Area, User
from tests.conftest import ISOLA, PASSWORD, TUTTI, id_utente, imposta_area, registra


async def test_registrazione_richiede_termini_e_consenso(client):
    base = {"email": "x@example.com", "password": PASSWORD, "alias": "Xeno", "consensi": TUTTI}
    r = await client.post("/v1/auth/register", json={**base, "accetto_termini": False})
    assert r.status_code == 422 and "termini" in r.text
    r = await client.post("/v1/auth/register", json={**base, "accetto_termini": True,
                                                       "consensi": {**TUTTI, "trattamento_dati": False}})
    assert r.status_code == 422 and "necessario" in r.text


async def test_password_debole_e_email_duplicata(client):
    base = {"email": "x@example.com", "alias": "Xeno", "accetto_termini": True, "consensi": TUTTI}
    assert (await client.post("/v1/auth/register", json={**base, "password": "solamentelettere"})).status_code == 422
    assert (await client.post("/v1/auth/register", json={**base, "password": "corta1"})).status_code == 422
    await registra(client, "x@example.com")
    r = await client.post("/v1/auth/register", json={**base, "password": PASSWORD})
    assert r.status_code == 409


async def test_profilo_professionale_richiede_attivita(client):
    r = await client.post("/v1/auth/register", json={"email": "p@example.com", "password": PASSWORD, "alias": "Pino",
                                                       "accetto_termini": True, "consensi": TUTTI, "tipo_profilo": "professionale"})
    assert r.status_code == 422 and "attività" in r.text
    h = await registra(client, "p2@example.com", "Pina", tipo_profilo="professionale", attivita="Ferramenta Pina")
    me = (await client.get("/v1/auth/me", headers=h)).json()
    assert me["tipo_profilo"] == "professionale" and me["attivita"] == "Ferramenta Pina"


async def test_email_non_salvata_in_chiaro(client, fabbrica):
    await registra(client, "segreta@example.com")
    async with fabbrica() as db:
        u = (await db.execute(select(User))).scalar_one()
        assert "segreta" not in u.email_hash and len(u.email_hash) == 64


async def test_login_errato_e_corretto(client):
    await registra(client, "l@example.com")
    assert (await client.post("/v1/auth/login", json={"email": "l@example.com", "password": "Sbagliata123"})).status_code == 401
    assert (await client.post("/v1/auth/login", json={"email": "nessuno@example.com", "password": PASSWORD})).status_code == 401
    r = await client.post("/v1/auth/login", json={"email": "L@Example.com", "password": PASSWORD})
    assert r.status_code == 200 and r.json()["access_token"]


async def test_refresh_ruotato_e_riuso_bloccato(client):
    await registra(client, "r@example.com")
    vecchio = client.cookies.get("nb_refresh")
    r = await client.post("/v1/auth/refresh")
    assert r.status_code == 200
    assert client.cookies.get("nb_refresh") != vecchio
    client.cookies.set("nb_refresh", vecchio, path="/v1/auth")
    assert (await client.post("/v1/auth/refresh")).status_code == 401  # token riusato: famiglia revocata


async def test_senza_token_e_token_falso(client):
    assert (await client.get("/v1/auth/me")).status_code == 401
    assert (await client.get("/v1/auth/me", headers={"Authorization": "Bearer falso"})).status_code == 401


async def test_area_salvata_approssimata(client, fabbrica):
    h = await registra(client, "a@example.com")
    out = await imposta_area(client, h, (45.486912, 9.189734))
    gh, lat, lon = geo.approssima(45.486912, 9.189734, geo.PRECISIONE_AREA)
    assert (out["lat"], out["lon"]) == (lat, lon)
    assert (out["lat"], out["lon"]) != (45.486912, 9.189734)
    async with fabbrica() as db:
        a = (await db.execute(select(Area))).scalar_one()
        assert a.geohash == gh and len(a.geohash) == 6


async def test_raggio_fuori_limiti(client):
    h = await registra(client, "a@example.com")
    for raggio in (100, 20000):
        r = await client.put("/v1/area", headers=h, json={"nome": "Isola", "lat": ISOLA[0], "lon": ISOLA[1], "raggio_m": raggio})
        assert r.status_code == 422


async def test_posizione_gps_richiede_consenso(client):
    h = await registra(client, "g@example.com", consensi={**TUTTI, "geolocalizzazione": False})
    corpo = {"nome": "Isola", "lat": ISOLA[0], "lon": ISOLA[1], "raggio_m": 1000}
    assert (await client.put("/v1/area", headers=h, json={**corpo, "da_gps": True})).status_code == 403
    assert (await client.put("/v1/area", headers=h, json=corpo)).status_code == 200  # scelta manuale: ammessa


async def test_consensi_versionati_e_obbligatorio_non_revocabile(client):
    h = await registra(client, "c@example.com")
    r = await client.patch("/v1/preferences", headers=h, json={"consensi": {"analytics": False}})
    assert r.status_code == 200 and r.json()["consensi"]["analytics"] is False
    await client.patch("/v1/preferences", headers=h, json={"consensi": {"analytics": True}})
    reg = (await client.get("/v1/consents", headers=h)).json()["registro"]
    analytics = [c for c in reg if c["finalita"] == "analytics"]
    assert len(analytics) == 3 and all(c["versione"] == "consensi/1.0" for c in analytics)
    assert sum(1 for c in analytics if c["revocato_at"] is None) == 1
    r = await client.patch("/v1/preferences", headers=h, json={"consensi": {"trattamento_dati": False}})
    assert r.status_code == 422
    r = await client.patch("/v1/preferences", headers=h, json={"consensi": {"inventata": True}})
    assert r.status_code == 422


async def test_blocco_idempotente_e_non_su_se_stessi(client):
    a = await registra(client, "a@example.com", "Anna")
    b = await registra(client, "b@example.com", "Bruno")
    bid = await id_utente(client, b)
    assert (await client.post(f"/v1/blocks/{bid}", headers=a)).status_code == 201
    assert (await client.post(f"/v1/blocks/{bid}", headers=a)).status_code == 201
    assert len((await client.get("/v1/blocks", headers=a)).json()) == 1
    assert (await client.post(f"/v1/blocks/{await id_utente(client, a)}", headers=a)).status_code == 422
    assert (await client.delete(f"/v1/blocks/{bid}", headers=a)).status_code == 204
    assert (await client.get("/v1/blocks", headers=a)).json() == []


async def test_cambio_password_chiude_sessioni(client):
    h = await registra(client, "p@example.com")
    r = await client.post("/v1/auth/password", headers=h, json={"password_attuale": "Sbagliata1234", "nuova_password": "NuovaPass2026"})
    assert r.status_code == 403
    r = await client.post("/v1/auth/password", headers=h, json={"password_attuale": PASSWORD, "nuova_password": "NuovaPass2026"})
    assert r.status_code == 204
    assert (await client.post("/v1/auth/login", json={"email": "p@example.com", "password": PASSWORD})).status_code == 401
    assert (await client.post("/v1/auth/login", json={"email": "p@example.com", "password": "NuovaPass2026"})).status_code == 200


async def test_senza_area_niente_feed(client):
    h = await registra(client, "n@example.com")
    r = await client.get("/v1/feed", headers=h)
    assert r.status_code == 409 and "quartiere" in r.text
