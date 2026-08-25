import uuid

import httpx
import pytest

from app.config import settings
from app.services.ai_assistant import AIAssistantService


@pytest.fixture
def ollama_non_raggiungibile(monkeypatch):
    """
    Forza un ConnectError per qualunque chiamata httpx diretta a Ollama, lasciando
    intatte le richieste del client di test verso l'app FastAPI (ASGITransport).
    Necessario perché su questa macchina Ollama può risultare effettivamente in
    esecuzione: vogliamo comunque un test deterministico del percorso "non disponibile".
    """
    original_get = httpx.AsyncClient.get
    original_post = httpx.AsyncClient.post

    async def fake_get(self, url, *args, **kwargs):
        if str(url).startswith(settings.ollama_base_url):
            raise httpx.ConnectError("Connessione rifiutata (mock)")
        return await original_get(self, url, *args, **kwargs)

    async def fake_post(self, url, *args, **kwargs):
        if str(url).startswith(settings.ollama_base_url):
            raise httpx.ConnectError("Connessione rifiutata (mock)")
        return await original_post(self, url, *args, **kwargs)

    monkeypatch.setattr(httpx.AsyncClient, "get", fake_get)
    monkeypatch.setattr(httpx.AsyncClient, "post", fake_post)


# ── Status (Ollama non raggiungibile) ──────────────────────────────────────────

async def test_status_ollama_non_raggiungibile(client, ollama_non_raggiungibile):
    """Con Ollama non raggiungibile, l'endpoint deve degradare senza errori (non 500)."""
    r = await client.get("/api/v1/ai/status")
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["ollama_disponibile"] is False
    assert data["modello"] == "llama3"
    assert "non raggiungibile" in data["messaggio"].lower()


# ── Chat: percorso reale con Ollama non raggiungibile ──────────────────────────

async def test_chat_senza_ollama_risposta_fallback_persistita(client, ollama_non_raggiungibile):
    """
    Senza mockare _chiedi_ollama: il codice reale tenta la chiamata HTTP (che qui
    fallisce sempre con ConnectError) e deve restituire un messaggio di fallback
    leggibile, non un 500, persistendo comunque lo scambio in cronologia.
    """
    r = await client.post("/api/v1/ai/chat", json={"messaggio": "Ciao, come va il fatturato?"})
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["sessione_id"]
    assert "ollama" in data["risposta"].lower()
    assert "non è raggiungibile" in data["risposta"].lower() or "non raggiungibile" in data["risposta"].lower()

    r = await client.get("/api/v1/ai/cronologia", params={"sessione_id": data["sessione_id"]})
    assert r.status_code == 200
    messaggi = r.json()
    assert len(messaggi) == 2
    assert messaggi[0]["ruolo"] == "utente"
    assert messaggi[0]["contenuto"] == "Ciao, come va il fatturato?"
    assert messaggi[1]["ruolo"] == "assistente"


# ── Chat: percorso mockato (nessuna chiamata di rete) ──────────────────────────

async def test_chat_con_ollama_mockato(client, monkeypatch):
    async def fake_chiedi_ollama(self, prompt):
        return "Il fatturato è in crescita del 10%."

    monkeypatch.setattr(AIAssistantService, "_chiedi_ollama", fake_chiedi_ollama)

    r = await client.post("/api/v1/ai/chat", json={"messaggio": "Come va il fatturato?"})
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["risposta"] == "Il fatturato è in crescita del 10%."
    sessione_id = data["sessione_id"]

    r = await client.get("/api/v1/ai/cronologia", params={"sessione_id": sessione_id})
    messaggi = r.json()
    assert len(messaggi) == 2
    assert messaggi[1]["contenuto"] == "Il fatturato è in crescita del 10%."


async def test_chat_riusa_sessione_id_fornita(client, monkeypatch):
    async def fake_chiedi_ollama(self, prompt):
        return "OK"

    monkeypatch.setattr(AIAssistantService, "_chiedi_ollama", fake_chiedi_ollama)

    sessione_id = str(uuid.uuid4())
    r = await client.post("/api/v1/ai/chat", json={"messaggio": "Prima domanda", "sessione_id": sessione_id})
    assert r.json()["sessione_id"] == sessione_id

    r = await client.post("/api/v1/ai/chat", json={"messaggio": "Seconda domanda", "sessione_id": sessione_id})
    assert r.json()["sessione_id"] == sessione_id

    r = await client.get("/api/v1/ai/cronologia", params={"sessione_id": sessione_id})
    assert len(r.json()) == 4


async def test_build_context_include_kpi_e_movimenti(client, monkeypatch):
    """Verifica indirettamente _build_context: il prompt inviato a Ollama deve
    contenere i KPI finanziari e i movimenti creati, quando la domanda li riguarda."""
    await client.post("/api/v1/movimenti/", json={
        "data": "2026-01-15", "tipo": "entrata", "importo": "5000.00",
        "descrizione": "Vendita Gennaio", "categoria": "Vendite",
    })

    captured = {}

    async def fake_chiedi_ollama(self, prompt):
        captured["prompt"] = prompt
        return "risposta finta"

    monkeypatch.setattr(AIAssistantService, "_chiedi_ollama", fake_chiedi_ollama)

    r = await client.post("/api/v1/ai/chat", json={"messaggio": "Quali sono le mie entrate recenti?"})
    assert r.status_code == 200, r.text

    prompt = captured["prompt"]
    assert "KPI FINANZIARI" in prompt
    assert "5,000.00" in prompt or "5000.00" in prompt
    assert "Vendita Gennaio" in prompt


# ── Cronologia ─────────────────────────────────────────────────────────────────

async def test_cronologia_sessione_inesistente_vuota(client):
    r = await client.get("/api/v1/ai/cronologia", params={"sessione_id": "sessione-mai-esistita"})
    assert r.status_code == 200
    assert r.json() == []


async def test_cronologia_richiede_sessione_id(client):
    r = await client.get("/api/v1/ai/cronologia")
    assert r.status_code == 422


async def test_cancella_cronologia(client, monkeypatch):
    async def fake_chiedi_ollama(self, prompt):
        return "OK"

    monkeypatch.setattr(AIAssistantService, "_chiedi_ollama", fake_chiedi_ollama)

    sessione_id = str(uuid.uuid4())
    await client.post("/api/v1/ai/chat", json={"messaggio": "Msg 1", "sessione_id": sessione_id})
    await client.post("/api/v1/ai/chat", json={"messaggio": "Msg 2", "sessione_id": sessione_id})

    r = await client.delete("/api/v1/ai/cronologia", params={"sessione_id": sessione_id})
    assert r.status_code == 200, r.text
    assert r.json()["cancellati"] == 4

    r = await client.get("/api/v1/ai/cronologia", params={"sessione_id": sessione_id})
    assert r.json() == []


async def test_cancella_cronologia_sessione_vuota_zero_cancellati(client):
    r = await client.delete("/api/v1/ai/cronologia", params={"sessione_id": "mai-esistita"})
    assert r.status_code == 200
    assert r.json()["cancellati"] == 0


# ── Validazione request ─────────────────────────────────────────────────────────

async def test_chat_messaggio_vuoto_422(client):
    r = await client.post("/api/v1/ai/chat", json={"messaggio": ""})
    assert r.status_code == 422


async def test_chat_messaggio_troppo_lungo_422(client):
    r = await client.post("/api/v1/ai/chat", json={"messaggio": "x" * 4001})
    assert r.status_code == 422
