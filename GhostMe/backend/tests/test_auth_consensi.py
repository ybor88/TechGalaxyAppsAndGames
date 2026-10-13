"""Autenticazione, sessioni, consensi e analytics. © Roberto Di Flumeri"""

from sqlalchemy import select

from app.models import AnalyticsEvent, User
from tests.conftest import PASSWORD, TUTTI_I_CONSENSI, registra


def _reg(email="a@example.com", **extra):
    base = {"email": email, "password": PASSWORD, "nome": "Anna", "accetto_termini": True,
            "consensi": {"trattamento_profilo": True}}
    base.update(extra)
    return base


async def test_registrazione_e_profilo(client):
    h = await registra(client)
    r = await client.get("/v1/auth/me", headers=h)
    assert r.status_code == 200
    assert r.json()["nome_visualizzato"] == "Mario"
    assert r.json()["ruolo"] == "utente"


async def test_email_salvata_solo_come_hash(client, sessione):
    await registra(client, "Segreta@Example.com")
    u = (await sessione.execute(select(User))).scalar_one()
    assert "segreta" not in u.email_hash.lower() and len(u.email_hash) == 64


async def test_registrazione_rifiutata_senza_consenso_o_termini(client):
    r = await client.post("/v1/auth/register", json=_reg(consensi={"trattamento_profilo": False}))
    assert r.status_code == 422
    r = await client.post("/v1/auth/register", json=_reg(accetto_termini=False))
    assert r.status_code == 422


async def test_password_debole_rifiutata(client):
    for pwd in ("corta1", "solamentelettere", "12345678901"):
        r = await client.post("/v1/auth/register", json=_reg(password=pwd))
        assert r.status_code == 422, pwd


async def test_messaggi_di_validazione_in_italiano(client):
    casi = {
        "corta1": "Password: inserisci almeno 10 caratteri",
        "solamentelettere": "La password deve contenere lettere e numeri (almeno 10 caratteri)",
    }
    for pwd, atteso in casi.items():
        r = await client.post("/v1/auth/register", json=_reg(password=pwd))
        assert r.status_code == 422 and r.json()["detail"][0]["msg"] == atteso
    r = await client.post("/v1/auth/register", json=_reg(email="non-una-email"))
    assert r.json()["detail"][0]["msg"] == "Inserisci un indirizzo email valido"
    r = await client.post("/v1/auth/register", json=_reg(nome=""))
    assert r.json()["detail"][0]["msg"] == "Nome non può essere vuoto"
    r = await client.post("/v1/auth/register", json={"email": "x@example.com"})
    assert r.json()["detail"][0]["msg"].endswith("è obbligatorio")


async def test_email_duplicata(client):
    await registra(client, "dup@example.com")
    r = await client.post("/v1/auth/register", json=_reg("DUP@example.com"))
    assert r.status_code == 409


async def test_login_corretto_ed_errato(client):
    await registra(client, "l@example.com")
    r = await client.post("/v1/auth/login", json={"email": "l@example.com", "password": PASSWORD})
    assert r.status_code == 200 and r.json()["access_token"]
    r = await client.post("/v1/auth/login", json={"email": "l@example.com", "password": "sbagliata123"})
    assert r.status_code == 401
    r = await client.post("/v1/auth/login", json={"email": "nessuno@example.com", "password": PASSWORD})
    assert r.status_code == 401
    assert r.json()["detail"] == "Email o password non corretti"  # nessuna enumerazione degli account


async def test_accesso_senza_token_o_con_token_falso(client):
    assert (await client.get("/v1/auth/me")).status_code == 401
    assert (await client.get("/v1/auth/me", headers={"Authorization": "Bearer abc"})).status_code == 401


async def test_refresh_token_ruotato_e_riuso_rilevato(client):
    await registra(client, "r@example.com")
    vecchio = client.cookies.get("gm_refresh")
    r = await client.post("/v1/auth/refresh")
    assert r.status_code == 200
    nuovo = client.cookies.get("gm_refresh")
    assert nuovo and nuovo != vecchio
    # riuso del token già ruotato: tutta la famiglia viene revocata
    client.cookies.set("gm_refresh", vecchio, path="/v1/auth")
    assert (await client.post("/v1/auth/refresh")).status_code == 401
    client.cookies.set("gm_refresh", nuovo, path="/v1/auth")
    assert (await client.post("/v1/auth/refresh")).status_code == 401


async def test_logout_revoca_la_sessione(client):
    h = await registra(client, "o@example.com")
    token = client.cookies.get("gm_refresh")
    assert (await client.post("/v1/auth/logout", headers=h)).status_code == 204
    client.cookies.set("gm_refresh", token, path="/v1/auth")
    assert (await client.post("/v1/auth/refresh")).status_code == 401


async def test_aggiorna_impostazioni_e_password(client):
    h = await registra(client, "p@example.com")
    r = await client.patch("/v1/auth/me", headers=h, json={"motore_ai": "auto", "conservazione_chat_giorni": 30})
    assert r.json()["motore_ai"] == "auto" and r.json()["conservazione_chat_giorni"] == 30
    r = await client.patch("/v1/auth/me", headers=h, json={"motore_ai": "gpt"})
    assert r.status_code == 422
    r = await client.post("/v1/auth/password", headers=h, json={"password_attuale": "x", "nuova_password": "NuovaPass123"})
    assert r.status_code == 403
    r = await client.post("/v1/auth/password", headers=h, json={"password_attuale": PASSWORD, "nuova_password": "NuovaPass123"})
    assert r.status_code == 204
    r = await client.post("/v1/auth/login", json={"email": "p@example.com", "password": "NuovaPass123"})
    assert r.status_code == 200


async def test_rate_limit_login(client):
    for _ in range(10):
        await client.post("/v1/auth/login", json={"email": "x@example.com", "password": "sbagliata123"})
    r = await client.post("/v1/auth/login", json={"email": "x@example.com", "password": "sbagliata123"})
    assert r.status_code == 429


async def test_stato_e_registro_consensi(client):
    h = await registra(client, "c@example.com", consensi={"trattamento_profilo": True})
    r = (await client.get("/v1/consents", headers=h)).json()
    stato = {c["finalita"]: c["concesso"] for c in r["stato"]}
    assert stato == {"trattamento_profilo": True, "elaborazione_ai": False, "condivisione": False, "analytics": False}
    await client.put("/v1/consents/elaborazione_ai", headers=h, json={"concesso": True})
    await client.put("/v1/consents/elaborazione_ai", headers=h, json={"concesso": False})
    registro = (await client.get("/v1/consents", headers=h)).json()["registro"]
    ai = [c for c in registro if c["finalita"] == "elaborazione_ai"]
    assert len(ai) == 3  # storico completo: iniziale, concesso, revocato
    assert sum(1 for c in ai if c["revocato_at"] is None) == 1


async def test_consenso_obbligatorio_non_revocabile(client):
    h = await registra(client, "o2@example.com")
    r = await client.put("/v1/consents/trattamento_profilo", headers=h, json={"concesso": False})
    assert r.status_code == 409
    assert (await client.put("/v1/consents/inventato", headers=h, json={"concesso": True})).status_code == 404


async def test_funzioni_ai_richiedono_consenso(client):
    h = await registra(client, "n@example.com", consensi={"trattamento_profilo": True})
    r = await client.post("/v1/chat/completions", headers=h, json={"messaggio": "ciao"})
    assert r.status_code == 403
    assert "elaborazione_ai" in r.json()["detail"]
    assert (await client.get("/v1/decisions", headers=h)).status_code == 403
    assert (await client.post("/v1/shares", headers=h, json={"nome": "x", "categorie": ["valori"]})).status_code == 403
    await client.put("/v1/consents/elaborazione_ai", headers=h, json={"concesso": True})
    assert (await client.post("/v1/chat/completions", headers=h, json={"messaggio": "ciao"})).status_code == 200


async def test_analytics_solo_con_consenso_e_cancellati_alla_revoca(client, sessione):
    senza = await registra(client, "s@example.com", consensi={"trattamento_profilo": True})
    await client.post("/v1/profile/statements", headers=senza, json={"testo": "Mi piace il mare", "categoria": "gusti"})
    assert (await sessione.execute(select(AnalyticsEvent))).scalars().all() == []

    con = await registra(client, "k@example.com", consensi=TUTTI_I_CONSENSI)
    await client.post("/v1/profile/statements", headers=con, json={"testo": "Mi piace il mare", "categoria": "gusti"})
    assert len((await sessione.execute(select(AnalyticsEvent))).scalars().all()) == 1
    await client.put("/v1/consents/analytics", headers=con, json={"concesso": False})
    sessione.expire_all()
    assert (await sessione.execute(select(AnalyticsEvent))).scalars().all() == []


async def test_refresh_senza_sessione_non_e_un_errore(client):
    r = await client.post("/v1/auth/refresh")
    assert r.status_code == 204
