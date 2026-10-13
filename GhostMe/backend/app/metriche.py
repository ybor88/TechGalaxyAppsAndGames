"""
Osservabilità (§6): log strutturati JSON e metriche in memoria (latenza p95, tasso errori)
senza registrare contenuti personali: si salvano solo metodo, rotta, stato e durata.

© Roberto Di Flumeri
"""

import json
import logging
import time
from collections import defaultdict, deque

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request

log = logging.getLogger("ghostme.http")


class Metriche:
    def __init__(self):
        self.durate: dict[str, deque] = defaultdict(lambda: deque(maxlen=500))
        self.richieste = 0
        self.errori_5xx = 0
        self.errori_4xx = 0

    def registra(self, rotta: str, stato: int, ms: float) -> None:
        self.richieste += 1
        self.durate[rotta].append(ms)
        if stato >= 500:
            self.errori_5xx += 1
        elif stato >= 400:
            self.errori_4xx += 1

    @staticmethod
    def _p95(valori) -> float:
        v = sorted(valori)
        return round(v[min(len(v) - 1, int(len(v) * 0.95))], 1) if v else 0.0

    def riepilogo(self) -> dict:
        return {
            "richieste": self.richieste,
            "tasso_errori_5xx": round(self.errori_5xx / self.richieste, 4) if self.richieste else 0,
            "errori_4xx": self.errori_4xx,
            "latenza_p95_ms": {r: self._p95(d) for r, d in sorted(self.durate.items())},
        }


metriche = Metriche()


class MiddlewareOsservabilita(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        t0 = time.monotonic()
        stato = 500
        try:
            risposta = await call_next(request)
            stato = risposta.status_code
            return risposta
        finally:
            ms = (time.monotonic() - t0) * 1000
            route = request.scope.get("route")
            # rotta "modello" (es. /v1/shares/{share_id}): niente token o identificativi nei log
            rotta = f"{request.method} {getattr(route, 'path', 'non_trovata')}"
            metriche.registra(rotta, stato, ms)
            log.info(json.dumps({"evento": "http", "rotta": rotta, "stato": stato, "ms": round(ms, 1)}))
