from datetime import date, timedelta
from decimal import Decimal


async def _crea_task(client, titolo="Task Test", tipo="task", priorita="media",
                      data_scadenza=None, passi=None, **extra):
    payload = {
        "titolo": titolo,
        "tipo": tipo,
        "priorita": priorita,
        "data_scadenza": data_scadenza,
        "passi": passi or [],
        **extra,
    }
    r = await client.post("/api/v1/workflow/tasks", json=payload)
    assert r.status_code == 201, r.text
    return r.json()


# ── CRUD Task ──────────────────────────────────────────────────────────────────

async def test_list_tasks_vuoto(client):
    r = await client.get("/api/v1/workflow/tasks")
    assert r.status_code == 200
    assert r.json() == []


async def test_create_task_semplice(client):
    task = await _crea_task(client, titolo="Chiamare cliente")
    assert task["stato"] == "aperto"
    assert task["tipo"] == "task"
    assert task["priorita"] == "media"
    assert task["passi"] == []
    assert task["passo_corrente"] is None
    assert task["reminder_inviato"] is False


async def test_get_task(client):
    task = await _crea_task(client)
    r = await client.get(f"/api/v1/workflow/tasks/{task['id']}")
    assert r.status_code == 200
    assert r.json()["id"] == task["id"]


async def test_get_task_inesistente(client):
    r = await client.get("/api/v1/workflow/tasks/9999")
    assert r.status_code == 404


async def test_task_giorni_alla_scadenza(client):
    scadenza = (date.today() + timedelta(days=3)).isoformat()
    task = await _crea_task(client, data_scadenza=scadenza)
    assert task["giorni_alla_scadenza"] == 3


async def test_task_senza_scadenza_giorni_none(client):
    task = await _crea_task(client)
    assert task["giorni_alla_scadenza"] is None


async def test_list_tasks_filtri(client):
    await _crea_task(client, titolo="A", tipo="task", stato="aperto")
    await _crea_task(client, titolo="B", tipo="acquisto", assegnato_a="Mario")

    r = await client.get("/api/v1/workflow/tasks", params={"tipo": "acquisto"})
    data = r.json()
    assert len(data) == 1
    assert data[0]["titolo"] == "B"

    r = await client.get("/api/v1/workflow/tasks", params={"assegnato_a": "Mario"})
    data = r.json()
    assert len(data) == 1
    assert data[0]["titolo"] == "B"


async def test_update_task(client):
    task = await _crea_task(client)
    r = await client.put(f"/api/v1/workflow/tasks/{task['id']}", json={"stato": "in_corso"})
    assert r.status_code == 200, r.text
    assert r.json()["stato"] == "in_corso"
    assert r.json()["data_completamento"] is None


async def test_update_task_completato_imposta_data_completamento(client):
    task = await _crea_task(client)
    r = await client.put(f"/api/v1/workflow/tasks/{task['id']}", json={"stato": "completato"})
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["stato"] == "completato"
    assert data["data_completamento"] == date.today().isoformat()


async def test_update_task_inesistente(client):
    r = await client.put("/api/v1/workflow/tasks/9999", json={"stato": "in_corso"})
    assert r.status_code == 404


async def test_delete_task(client):
    task = await _crea_task(client)
    r = await client.delete(f"/api/v1/workflow/tasks/{task['id']}")
    assert r.status_code == 204

    r = await client.get("/api/v1/workflow/tasks")
    assert r.json() == []


async def test_delete_task_inesistente(client):
    r = await client.delete("/api/v1/workflow/tasks/9999")
    assert r.status_code == 404


# ── Flusso di approvazione multi-livello ──────────────────────────────────────

async def test_task_approvazione_con_passi_passo_corrente(client):
    passi = [
        {"approvatore": "Direttore", "ordine": 1},
        {"approvatore": "CFO", "ordine": 2},
    ]
    task = await _crea_task(client, tipo="approvazione", passi=passi)
    assert len(task["passi"]) == 2
    assert task["passo_corrente"] == 1
    assert all(p["stato"] == "in_attesa" for p in task["passi"])


async def test_approva_primo_passo_non_completa_task(client):
    passi = [
        {"approvatore": "Direttore", "ordine": 1},
        {"approvatore": "CFO", "ordine": 2},
    ]
    task = await _crea_task(client, tipo="approvazione", passi=passi)
    passo1_id = task["passi"][0]["id"]

    r = await client.post(
        f"/api/v1/workflow/tasks/{task['id']}/passi/{passo1_id}/approva",
        json={"stato": "approvato", "commento": "OK"},
    )
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["stato"] == "aperto"  # non tutti i passi sono approvati
    assert data["passo_corrente"] == 2
    assert data["data_completamento"] is None


async def test_approva_tutti_i_passi_completa_task(client):
    passi = [
        {"approvatore": "Direttore", "ordine": 1},
        {"approvatore": "CFO", "ordine": 2},
    ]
    task = await _crea_task(client, tipo="approvazione", passi=passi)
    passo1_id = task["passi"][0]["id"]
    passo2_id = task["passi"][1]["id"]

    await client.post(
        f"/api/v1/workflow/tasks/{task['id']}/passi/{passo1_id}/approva",
        json={"stato": "approvato"},
    )
    r = await client.post(
        f"/api/v1/workflow/tasks/{task['id']}/passi/{passo2_id}/approva",
        json={"stato": "approvato"},
    )
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["stato"] == "approvato"
    assert data["data_completamento"] == date.today().isoformat()
    assert data["passo_corrente"] is None


async def test_rifiuta_passo_rifiuta_subito_il_task(client):
    passi = [
        {"approvatore": "Direttore", "ordine": 1},
        {"approvatore": "CFO", "ordine": 2},
    ]
    task = await _crea_task(client, tipo="approvazione", passi=passi)
    passo1_id = task["passi"][0]["id"]

    r = await client.post(
        f"/api/v1/workflow/tasks/{task['id']}/passi/{passo1_id}/approva",
        json={"stato": "rifiutato", "commento": "Budget insufficiente"},
    )
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["stato"] == "rifiutato"
    assert data["data_completamento"] == date.today().isoformat()
    assert data["passi"][0]["commento"] == "Budget insufficiente"


async def test_approva_passo_task_inesistente(client):
    r = await client.post(
        "/api/v1/workflow/tasks/9999/passi/1/approva",
        json={"stato": "approvato"},
    )
    assert r.status_code == 404


async def test_approva_passo_id_inesistente(client):
    task = await _crea_task(client, tipo="approvazione", passi=[{"approvatore": "X", "ordine": 1}])
    r = await client.post(
        f"/api/v1/workflow/tasks/{task['id']}/passi/9999/approva",
        json={"stato": "approvato"},
    )
    assert r.status_code == 404


# ── Summary ──────────────────────────────────────────────────────────────────

async def test_summary_vuoto(client):
    r = await client.get("/api/v1/workflow/summary")
    assert r.status_code == 200
    data = r.json()
    assert data["task_aperti"] == 0
    assert data["task_in_corso"] == 0
    assert data["approvazioni_in_attesa"] == 0
    assert data["reminder_in_scadenza"] == 0
    assert data["acquisti_aperti"] == 0
    assert data["task_scaduti"] == 0


async def test_summary_conta_correttamente(client):
    # NB: TaskCreate non espone il campo `stato` (nasce sempre "aperto"): per i task
    # che devono avere uno stato diverso lo si imposta con un PUT successivo.
    oggi = date.today()
    scaduta = (oggi - timedelta(days=2)).isoformat()
    fra_3_giorni = (oggi + timedelta(days=3)).isoformat()
    fra_20_giorni = (oggi + timedelta(days=20)).isoformat()

    await _crea_task(client, titolo="Aperto normale", tipo="task")

    in_corso = await _crea_task(client, titolo="In corso", tipo="task")
    await client.put(f"/api/v1/workflow/tasks/{in_corso['id']}", json={"stato": "in_corso"})

    await _crea_task(client, titolo="Approvazione pendente", tipo="approvazione")
    await _crea_task(client, titolo="Reminder vicino", tipo="reminder", data_scadenza=fra_3_giorni)
    await _crea_task(client, titolo="Reminder lontano", tipo="reminder", data_scadenza=fra_20_giorni)
    await _crea_task(client, titolo="Acquisto aperto", tipo="acquisto")
    await _crea_task(client, titolo="Task scaduto", tipo="task", data_scadenza=scaduta)

    completato = await _crea_task(client, titolo="Task completato", tipo="task")
    await client.put(f"/api/v1/workflow/tasks/{completato['id']}", json={"stato": "completato"})

    r = await client.get("/api/v1/workflow/summary")
    assert r.status_code == 200
    data = r.json()
    # aperto: normale, approvazione, reminder vicino, reminder lontano, acquisto, scaduto = 6
    assert data["task_aperti"] == 6
    assert data["task_in_corso"] == 1
    assert data["approvazioni_in_attesa"] == 1
    assert data["reminder_in_scadenza"] == 1
    assert data["acquisti_aperti"] == 1
    assert data["task_scaduti"] == 1
