"""
Servizio Forecasting Aziendale (F7).

Previsioni calcolate su dati storici dei movimenti.
Usa scikit-learn (LinearRegression) se disponibile;
altrimenti cade in un fallback con regressione lineare pura Python.
"""
from __future__ import annotations

import math
from collections import defaultdict
from datetime import date, timedelta
from decimal import Decimal

from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.financial import Movimento
from app.schemas.forecasting import (
    FattoreRischio,
    PrevisioneLiquiditaResponse,
    PrevisioneVenditeResponse,
    PuntoPrevisione,
    RischioInsolvenzaResponse,
    ScenarioItem,
    SimulazioneScenariResponse,
)

# ── dipendenza opzionale ─────────────────────────────────────────────────────
try:
    from sklearn.linear_model import LinearRegression  # type: ignore
    import numpy as np  # type: ignore
    _SKLEARN = True
except ImportError:  # pragma: no cover
    _SKLEARN = False


# ── helper regressione lineare pura Python (fallback) ───────────────────────

def _linreg_fallback(xs: list[float], ys: list[float]) -> tuple[float, float]:
    """Restituisce (slope, intercept) con regressione OLS."""
    n = len(xs)
    if n < 2:
        return 0.0, (ys[0] if ys else 0.0)
    mx = sum(xs) / n
    my = sum(ys) / n
    num = sum((xs[i] - mx) * (ys[i] - my) for i in range(n))
    den = sum((xs[i] - mx) ** 2 for i in range(n))
    slope = num / den if den != 0 else 0.0
    return slope, my - slope * mx


def _predict(slope: float, intercept: float, x: float) -> float:
    return slope * x + intercept


# ── helper sklearn ───────────────────────────────────────────────────────────

def _fit_predict_sklearn(
    xs: list[float], ys: list[float], future_xs: list[float]
) -> list[float]:
    X = np.array(xs).reshape(-1, 1)
    y = np.array(ys)
    model = LinearRegression().fit(X, y)
    return model.predict(np.array(future_xs).reshape(-1, 1)).tolist()


# ════════════════════════════════════════════════════════════════════════════
class ForecastingService:

    def __init__(self, db: AsyncSession):
        self.db = db

    # ── lettura dati storici ────────────────────────────────────────────────

    async def _storico_mensile(self, mesi: int = 24) -> dict[str, dict[str, float]]:
        """Raggruppa entrate/uscite per mese degli ultimi N mesi."""
        data_inizio = _mesi_fa(mesi)
        result = await self.db.execute(
            select(
                func.strftime("%Y-%m", Movimento.data).label("mese"),
                Movimento.tipo,
                func.sum(Movimento.importo).label("totale"),
            )
            .where(Movimento.data >= data_inizio)
            .group_by("mese", Movimento.tipo)
            .order_by("mese")
        )
        data: dict[str, dict[str, float]] = defaultdict(lambda: {"entrata": 0.0, "uscita": 0.0})
        for row in result.all():
            data[row.mese][row.tipo] = float(row.totale or 0)
        return dict(sorted(data.items()))

    async def _storico_giornaliero(self, giorni: int = 90) -> dict[str, dict[str, float]]:
        data_inizio = date.today() - timedelta(days=giorni)
        result = await self.db.execute(
            select(
                func.strftime("%Y-%m-%d", Movimento.data).label("giorno"),
                Movimento.tipo,
                func.sum(Movimento.importo).label("totale"),
            )
            .where(Movimento.data >= data_inizio)
            .group_by("giorno", Movimento.tipo)
            .order_by("giorno")
        )
        data: dict[str, dict[str, float]] = defaultdict(lambda: {"entrata": 0.0, "uscita": 0.0})
        for row in result.all():
            data[row.giorno][row.tipo] = float(row.totale or 0)
        return dict(sorted(data.items()))

    # ── previsione vendite ──────────────────────────────────────────────────

    async def get_previsione_vendite(self, mesi_futuri: int = 3) -> PrevisioneVenditeResponse:
        storico = await self._storico_mensile(mesi=24)
        mesi_list = sorted(storico.keys())

        entrate = [storico[m]["entrata"] for m in mesi_list]
        xs = list(range(len(entrate)))

        if _SKLEARN and len(xs) >= 3:
            future_xs = [len(xs) + i for i in range(mesi_futuri)]
            valori_previsti = _fit_predict_sklearn(xs, entrate, future_xs)
        else:
            slope, intercept = _linreg_fallback(xs, entrate)
            valori_previsti = [
                _predict(slope, intercept, len(xs) + i) for i in range(mesi_futuri)
            ]

        # Deviazione standard come proxy dell'intervallo di confidenza
        std = _std(entrate)
        z = 1.645  # 90% CI

        storico_schema = [
            PuntoPrevisione(
                periodo=mese,
                valore=round(storico[mese]["entrata"], 2),
                confidenza_min=round(storico[mese]["entrata"], 2),
                confidenza_max=round(storico[mese]["entrata"], 2),
            )
            for mese in mesi_list
        ]

        previsione_schema = [
            PuntoPrevisione(
                periodo=_mese_successivo(mesi_list[-1] if mesi_list else _mese_corrente(), i + 1),
                valore=round(max(valori_previsti[i], 0), 2),
                confidenza_min=round(max(valori_previsti[i] - z * std, 0), 2),
                confidenza_max=round(max(valori_previsti[i] + z * std, 0), 2),
            )
            for i in range(mesi_futuri)
        ]

        slope_tot, _ = _linreg_fallback(xs, entrate) if entrate else (0.0, 0.0)
        media = sum(entrate) / len(entrate) if entrate else 1.0
        variazione = (slope_tot / media * 100) if media != 0 else 0.0

        if abs(variazione) < 3:
            trend = "stabile"
        elif variazione > 0:
            trend = "crescente"
        else:
            trend = "decrescente"

        return PrevisioneVenditeResponse(
            storico=storico_schema,
            previsione=previsione_schema,
            trend=trend,
            variazione_percentuale=round(variazione, 2),
        )

    # ── previsione liquidità ────────────────────────────────────────────────

    async def get_previsione_liquidita(self, giorni: int = 30) -> PrevisioneLiquiditaResponse:
        storico = await self._storico_giornaliero(giorni=90)

        # Cashflow giornaliero netto
        cashflow_per_giorno: list[float] = [
            v["entrata"] - v["uscita"] for v in storico.values()
        ]
        media_giornaliera = (
            sum(cashflow_per_giorno) / len(cashflow_per_giorno)
            if cashflow_per_giorno else 0.0
        )

        # Saldo attuale = somma cashflow storico
        saldo_attuale = sum(cashflow_per_giorno)
        std = _std(cashflow_per_giorno)
        z = 1.282  # 80% CI

        previsione: list[PuntoPrevisione] = []
        saldo_cumulativo = saldo_attuale
        giorni_copertura = giorni
        allerta = False

        for i in range(1, giorni + 1):
            giorno_str = (date.today() + timedelta(days=i)).strftime("%Y-%m-%d")
            saldo_cumulativo += media_giornaliera
            previsione.append(
                PuntoPrevisione(
                    periodo=giorno_str,
                    valore=round(saldo_cumulativo, 2),
                    confidenza_min=round(saldo_cumulativo - z * std * math.sqrt(i), 2),
                    confidenza_max=round(saldo_cumulativo + z * std * math.sqrt(i), 2),
                )
            )
            if saldo_cumulativo <= 0 and giorni_copertura == giorni:
                giorni_copertura = i
                allerta = True

        return PrevisioneLiquiditaResponse(
            saldo_attuale=round(saldo_attuale, 2),
            previsione_giorni=previsione,
            giorni_copertura=giorni_copertura,
            allerta=allerta,
        )

    # ── simulazione scenari ─────────────────────────────────────────────────

    async def get_simulazione_scenari(self) -> SimulazioneScenariResponse:
        storico = await self._storico_mensile(mesi=12)
        mesi_list = sorted(storico.keys())

        if not mesi_list:
            media_e = media_u = 0.0
        else:
            media_e = sum(storico[m]["entrata"] for m in mesi_list) / len(mesi_list)
            media_u = sum(storico[m]["uscita"] for m in mesi_list) / len(mesi_list)

        mese_prossimo = _mese_successivo(_mese_corrente(), 1)

        scenari_cfg = [
            ("ottimistico",  1.20, 0.90),
            ("base",         1.00, 1.00),
            ("pessimistico", 0.75, 1.15),
        ]
        scenari: list[ScenarioItem] = []
        for nome, fe, fu in scenari_cfg:
            entrate = media_e * fe
            uscite = media_u * fu
            cashflow = entrate - uscite
            variazione = ((cashflow - (media_e - media_u)) / (media_e - media_u) * 100
                          if (media_e - media_u) != 0 else 0.0)
            scenari.append(
                ScenarioItem(
                    scenario=nome,
                    entrate_previste=round(entrate, 2),
                    uscite_previste=round(uscite, 2),
                    cashflow=round(cashflow, 2),
                    variazione_percentuale=round(variazione, 2),
                )
            )

        return SimulazioneScenariResponse(
            mese_riferimento=mese_prossimo,
            media_storica_entrate=round(media_e, 2),
            media_storica_uscite=round(media_u, 2),
            scenari=scenari,
        )

    # ── rischio insolvenza ──────────────────────────────────────────────────

    async def get_rischio_insolvenza(self) -> RischioInsolvenzaResponse:
        storico = await self._storico_mensile(mesi=12)
        mesi_list = sorted(storico.keys())

        fattori: list[FattoreRischio] = []
        punteggio = 0.0

        if not mesi_list:
            return RischioInsolvenzaResponse(
                punteggio=0.0,
                livello="basso",
                fattori=[],
                raccomandazioni=["Nessun dato storico disponibile."],
            )

        entrate_list = [storico[m]["entrata"] for m in mesi_list]
        uscite_list = [storico[m]["uscita"] for m in mesi_list]
        cashflow_list = [e - u for e, u in zip(entrate_list, uscite_list)]

        media_cf = sum(cashflow_list) / len(cashflow_list)
        mesi_negativi = sum(1 for c in cashflow_list if c < 0)

        # 1. Cashflow medio negativo
        if media_cf < 0:
            punteggio += 40
            fattori.append(FattoreRischio(fattore="Cashflow medio negativo negli ultimi 12 mesi", impatto="alto"))
        elif media_cf < 500:
            punteggio += 20
            fattori.append(FattoreRischio(fattore="Cashflow medio molto basso", impatto="medio"))

        # 2. Mesi con cashflow negativo
        if mesi_negativi >= 6:
            punteggio += 30
            fattori.append(FattoreRischio(
                fattore=f"Cashflow negativo in {mesi_negativi} mesi su {len(mesi_list)}",
                impatto="alto"))
        elif mesi_negativi >= 3:
            punteggio += 15
            fattori.append(FattoreRischio(
                fattore=f"Cashflow negativo in {mesi_negativi} mesi su {len(mesi_list)}",
                impatto="medio"))

        # 3. Trend entrate decrescente
        xs = list(range(len(entrate_list)))
        slope_e, _ = _linreg_fallback(xs, entrate_list)
        if slope_e < -100:
            punteggio += 20
            fattori.append(FattoreRischio(fattore="Trend entrate in forte calo", impatto="alto"))
        elif slope_e < 0:
            punteggio += 10
            fattori.append(FattoreRischio(fattore="Trend entrate in lieve calo", impatto="basso"))

        # 4. Indice di liquidità
        media_e = sum(entrate_list) / len(entrate_list)
        media_u = sum(uscite_list) / len(uscite_list)
        indice = media_e / media_u if media_u > 0 else 999
        if indice < 0.8:
            punteggio += 20
            fattori.append(FattoreRischio(fattore=f"Indice liquidità critico ({indice:.2f})", impatto="alto"))
        elif indice < 1.0:
            punteggio += 10
            fattori.append(FattoreRischio(fattore=f"Indice liquidità sotto soglia ({indice:.2f})", impatto="medio"))

        punteggio = min(punteggio, 100.0)

        if punteggio < 25:
            livello = "basso"
        elif punteggio < 50:
            livello = "medio"
        elif punteggio < 75:
            livello = "alto"
        else:
            livello = "critico"

        raccomandazioni = _raccomandazioni(livello, media_cf, slope_e)

        return RischioInsolvenzaResponse(
            punteggio=round(punteggio, 1),
            livello=livello,
            fattori=fattori,
            raccomandazioni=raccomandazioni,
        )


# ── utility ──────────────────────────────────────────────────────────────────

def _std(values: list[float]) -> float:
    if len(values) < 2:
        return 0.0
    media = sum(values) / len(values)
    variance = sum((v - media) ** 2 for v in values) / (len(values) - 1)
    return math.sqrt(variance)


def _mesi_fa(n: int) -> date:
    oggi = date.today()
    mese = oggi.month - n
    anno = oggi.year
    while mese <= 0:
        mese += 12
        anno -= 1
    return date(anno, mese, 1)


def _mese_corrente() -> str:
    return date.today().strftime("%Y-%m")


def _mese_successivo(base: str, n: int) -> str:
    anno, mese = int(base[:4]), int(base[5:7])
    mese += n
    while mese > 12:
        mese -= 12
        anno += 1
    return f"{anno}-{mese:02d}"


def _raccomandazioni(livello: str, media_cf: float, slope_e: float) -> list[str]:
    base = []
    if livello in ("alto", "critico"):
        base += [
            "Ridurre i costi fissi non essenziali nel breve periodo.",
            "Valutare linee di credito o fidi bancari a copertura.",
            "Accelerare la riscossione dei crediti in scadenza.",
        ]
    if livello == "critico":
        base.append("Consultare un advisor finanziario per un piano di risanamento.")
    if slope_e < 0:
        base.append("Avviare azioni commerciali per incrementare le entrate.")
    if livello in ("basso", "medio"):
        base.append("Mantenere un fondo di riserva pari ad almeno 3 mesi di uscite.")
    if not base:
        base.append("Situazione finanziaria nella norma. Monitorare periodicamente.")
    return base
