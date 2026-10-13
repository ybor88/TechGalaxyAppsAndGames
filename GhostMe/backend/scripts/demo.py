"""
Crea un utente dimostrativo con profilo, conoscenze, diario, versioni e una decisione.
Si esegue tramite API (stesse regole e validazioni dell'app).

    cd backend
    .venv\\Scripts\\python scripts\\demo.py

Credenziali: demo@ghostme.it / DemoGhost2026

© Roberto Di Flumeri
"""

import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

from httpx import ASGITransport, AsyncClient  # noqa: E402

from app.database import init_db  # noqa: E402
from app.main import app  # noqa: E402

EMAIL, PASSWORD = "demo@ghostme.it", "DemoGhost2026"

RISPOSTE = {
    "valori_importanti": ("La famiglia, la libertà di gestire il mio tempo e imparare cose nuove", 5, True),
    "valori_non_negoziabili": ("Non rinuncio all'onestà, anche quando è scomoda", 5, True),
    "valori_successo": ("Fare un lavoro creativo che mi appassiona e avere tempo per le persone care", 4, False),
    "abitudini_giornata": ("Mi sveglio presto, lavoro meglio la mattina e la sera leggo", 3, False),
    "abitudini_energia": ("Camminare all'aria aperta e cucinare con calma mi ricaricano", 4, True),
    "abitudini_organizzazione": ("Uso liste e calendario, ma lascio spazio all'improvvisazione nel weekend", 3, False),
    "gusti_tempo_libero": ("Amo viaggiare in posti poco turistici e fare trekking in montagna", 5, True),
    "gusti_cultura": ("Fantascienza, jazz e documentari sulla natura", 3, True),
    "gusti_cibo": ("Cucina mediterranea e locali tranquilli e luminosi", 3, True),
    "obiettivi_anno": ("Cambiare lavoro verso il design e correre una mezza maratona", 5, False),
    "obiettivi_lungo": ("Vivere vicino al mare e avere un piccolo studio creativo mio", 4, False),
    "obiettivi_crescita": ("Parlare in pubblico con più sicurezza", 3, False),
    "stile_tono": ("Sono diretto ma gentile e uso spesso l'ironia", 3, True),
    "stile_conflitti": ("Preferisco affrontare i disaccordi subito, a voce, senza lasciar correre", 3, True),
    "stile_decisioni": ("Raccolgo informazioni, faccio pro e contro e poi seguo l'istinto", 4, True),
}


async def main() -> None:
    await init_db()
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://demo") as c:
        r = await c.post("/v1/auth/login", json={"email": EMAIL, "password": PASSWORD})
        if r.status_code == 200:
            print("Utente demo già presente:", EMAIL)
            return
        r = await c.post("/v1/auth/register", json={
            "email": EMAIL, "password": PASSWORD, "nome": "Alex", "accetto_termini": True,
            "consensi": {"trattamento_profilo": True, "elaborazione_ai": True, "condivisione": True, "analytics": False},
        })
        r.raise_for_status()
        h = {"Authorization": f"Bearer {r.json()['access_token']}"}
        await c.post("/v1/profile/questionnaire", headers=h, json={"risposte": [
            {"chiave": k, "testo": t, "peso": p, "visibilita": "condivisibile" if cond else "privata"}
            for k, (t, p, cond) in RISPOSTE.items()
        ]})
        await c.post("/v1/snapshots", headers=h, json={"etichetta": "Il mio punto di partenza"})
        for titolo, contenuto, cat in [
            ("Viaggio in Islanda", "Il viaggio più bello: trekking tra i ghiacciai, poca gente, tanto silenzio.", "gusti"),
            ("Corso di UX design", "Ho iniziato un corso serale di UX design: mi piace progettare cose utili.", "obiettivi"),
        ]:
            await c.post("/v1/knowledge", headers=h, json={"titolo": titolo, "contenuto": contenuto, "tipo": "ricordo", "categoria": cat})
        await c.post("/v1/profile/statements", headers=h, json={
            "testo": "Voglio dedicare più tempo alla fotografia di paesaggio", "categoria": "obiettivi", "peso": 3})
        await c.post("/v1/journal", headers=h, json={"tipo": "check_in", "testo": "Settimana intensa ma il corso di design mi dà energia.", "energia": 4})
        await c.post("/v1/decisions", headers=h, json={
            "domanda": "Accetto l'offerta dello studio di design?",
            "opzioni": [{"nome": "Studio di design", "descrizione": "Lavoro creativo da designer, orari flessibili, stipendio più basso"},
                        {"nome": "Restare in azienda", "descrizione": "Stipendio sicuro, lavoro ripetitivo, molte ore in ufficio"}],
            "criteri": [{"nome": "Lavoro creativo", "peso": 5}, {"nome": "Tempo per la famiglia", "peso": 4}, {"nome": "Sicurezza economica", "peso": 3}],
            "valutazioni": {"0": {"0": 5, "1": 4, "2": 2}, "1": {"0": 1, "1": 2, "2": 5}},
        })
        print(f"Utente demo creato: {EMAIL} / {PASSWORD}")


if __name__ == "__main__":
    asyncio.run(main())
