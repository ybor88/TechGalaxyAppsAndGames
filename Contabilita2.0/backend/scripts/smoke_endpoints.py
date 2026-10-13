"""
Smoke test di tutti gli endpoint GET sul database reale (contabilita20.db).

Chiama ogni endpoint GET esposto dall'OpenAPI, sostituendo i parametri di
percorso con il primo id disponibile, e segnala status != 2xx e tempi lenti.

    cd backend
    .venv\\Scripts\\python scripts\\smoke_endpoints.py

© Roberto Di Flumeri
"""

import asyncio
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

from httpx import ASGITransport, AsyncClient  # noqa: E402

from app.main import app  # noqa: E402

SOGLIA_LENTO_S = 2.0

# Endpoint che dipendono da servizi esterni (Ollama) o da parametri obbligatori
ESCLUSI: set[str] = set()
QUERY_EXTRA = {
    "/api/v1/contabilita/iva": {"data_da": "2025-01-01", "data_a": "2025-12-31"},
    "/api/v1/ai/cronologia": {"sessione_id": "smoke-test"},
}

# Per ogni parametro di percorso, l'endpoint lista da cui prendere un id valido
SORGENTI_ID = {
    "documento_id": "/api/v1/documenti/",
    "anagrafica_id": "/api/v1/anagrafiche/",
    "movimento_id": "/api/v1/movimenti/",
    "conto_id": "/api/v1/conti/",
    "risultato_id": "/api/v1/ocr/risultati",
    "reg_id": "/api/v1/contabilita/registrazioni",
    "registrazione_id": "/api/v1/contabilita/registrazioni",
    "task_id": "/api/v1/workflow/tasks",
    "cespite_id": "/api/v1/ammortamenti/cespiti",
    "dipendente_id": "/api/v1/paghe/dipendenti",
}


async def main() -> int:
    errori = 0
    async with app.router.lifespan_context(app):
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://smoke", timeout=120) as c:
            spec = (await c.get("/openapi.json")).json()
            cache_id: dict[str, object] = {}

            async def primo_id(param: str):
                if param not in cache_id:
                    src = SORGENTI_ID.get(param)
                    val = None
                    if src:
                        r = await c.get(src)
                        if r.status_code == 200 and isinstance(r.json(), list) and r.json():
                            val = r.json()[0].get("id")
                    cache_id[param] = val
                return cache_id[param]

            for path, ops in sorted(spec["paths"].items()):
                if "get" not in ops or path in ESCLUSI:
                    continue
                url = path
                mancante = False
                for param in re.findall(r"{(\w+)}", path):
                    val = await primo_id(param)
                    if val is None:
                        mancante = True
                        break
                    url = url.replace(f"{{{param}}}", str(val))
                if mancante:
                    print(f"  SKIP  {path} (nessun id disponibile)")
                    continue

                t0 = time.perf_counter()
                r = await c.get(url, params=QUERY_EXTRA.get(path))
                dt = time.perf_counter() - t0
                ok = 200 <= r.status_code < 300
                lento = dt > SOGLIA_LENTO_S
                flag = "OK  " if ok and not lento else ("LENTO" if ok else "ERR ")
                print(f"  {flag} {r.status_code} {dt:6.2f}s  {url}")
                if not ok:
                    errori += 1
                    print(f"         {r.text[:300]}")
                elif lento:
                    errori += 1
    print(f"\nEndpoint con problemi: {errori}")
    return 1 if errori else 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
