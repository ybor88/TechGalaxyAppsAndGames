"use client";

import { useEffect, useState } from "react";
import dynamic from "next/dynamic";
import Link from "next/link";
import {
  forecastingApi,
  PrevisioneVenditeResponse,
  PrevisioneLiquiditaResponse,
  SimulazioneScenariResponse,
  RischioInsolvenzaResponse,
} from "@/lib/api";
import {
  ChevronLeft,
  TrendingUp,
  Droplets,
  BarChart2,
  ShieldAlert,
  AlertTriangle,
  CheckCircle2,
  XCircle,
  Info,
} from "lucide-react";

// Importazione dinamica (SSR-safe) dei componenti chart
const VenditeChart = dynamic(
  () => import("@/components/forecasting/VenditeChart"),
  { ssr: false, loading: () => <div className="h-72 animate-pulse bg-gray-50 rounded-xl" /> }
);
const LiquiditaChart = dynamic(
  () => import("@/components/forecasting/LiquiditaChart"),
  { ssr: false, loading: () => <div className="h-72 animate-pulse bg-gray-50 rounded-xl" /> }
);
const ScenariChart = dynamic(
  () => import("@/components/forecasting/ScenariChart"),
  { ssr: false, loading: () => <div className="h-64 animate-pulse bg-gray-50 rounded-xl" /> }
);

// ── Helpers ───────────────────────────────────────────────────────────────────

const fmt = (n: number) =>
  new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(n);

const fmtPct = (n: number) => `${n >= 0 ? "+" : ""}${n.toFixed(1)}%`;

type Tab = "vendite" | "liquidita" | "scenari" | "rischio";

const TABS: { id: Tab; label: string; icon: React.ElementType }[] = [
  { id: "vendite", label: "Previsione Vendite", icon: TrendingUp },
  { id: "liquidita", label: "Liquidità", icon: Droplets },
  { id: "scenari", label: "Simulazione Scenari", icon: BarChart2 },
  { id: "rischio", label: "Rischio Insolvenza", icon: ShieldAlert },
];

const RISCHIO_COLOR: Record<string, string> = {
  basso: "text-green-600",
  medio: "text-amber-500",
  alto: "text-orange-600",
  critico: "text-red-600",
};

const RISCHIO_BG: Record<string, string> = {
  basso: "bg-green-50 border-green-200",
  medio: "bg-amber-50 border-amber-200",
  alto: "bg-orange-50 border-orange-200",
  critico: "bg-red-50 border-red-200",
};

const IMPATTO_COLOR: Record<string, string> = {
  alto: "bg-red-100 text-red-700",
  medio: "bg-amber-100 text-amber-700",
  basso: "bg-green-100 text-green-700",
};

// ── Componente principale ─────────────────────────────────────────────────────

export default function ForecastingPage() {
  const [tab, setTab] = useState<Tab>("vendite");

  const [vendite, setVendite] = useState<PrevisioneVenditeResponse | null>(null);
  const [liquidita, setLiquidita] = useState<PrevisioneLiquiditaResponse | null>(null);
  const [scenari, setScenari] = useState<SimulazioneScenariResponse | null>(null);
  const [rischio, setRischio] = useState<RischioInsolvenzaResponse | null>(null);

  const [loading, setLoading] = useState<Record<Tab, boolean>>({
    vendite: false, liquidita: false, scenari: false, rischio: false,
  });
  const [errori, setErrori] = useState<Record<Tab, string | null>>({
    vendite: null, liquidita: null, scenari: null, rischio: null,
  });

  const setLoad = (t: Tab, v: boolean) =>
    setLoading((p) => ({ ...p, [t]: v }));
  const setErr = (t: Tab, v: string | null) =>
    setErrori((p) => ({ ...p, [t]: v }));

  // Carica il tab corrente al cambio
  useEffect(() => {
    if (tab === "vendite" && !vendite) {
      setLoad("vendite", true);
      forecastingApi
        .previsioneVendite()
        .then((r) => setVendite(r.data))
        .catch(() => setErr("vendite", "Impossibile caricare le previsioni vendite."))
        .finally(() => setLoad("vendite", false));
    }
    if (tab === "liquidita" && !liquidita) {
      setLoad("liquidita", true);
      forecastingApi
        .previsioneLiquidita()
        .then((r) => setLiquidita(r.data))
        .catch(() => setErr("liquidita", "Impossibile caricare la previsione liquidità."))
        .finally(() => setLoad("liquidita", false));
    }
    if (tab === "scenari" && !scenari) {
      setLoad("scenari", true);
      forecastingApi
        .simulazioneScenari()
        .then((r) => setScenari(r.data))
        .catch(() => setErr("scenari", "Impossibile caricare la simulazione scenari."))
        .finally(() => setLoad("scenari", false));
    }
    if (tab === "rischio" && !rischio) {
      setLoad("rischio", true);
      forecastingApi
        .rischioInsolvenza()
        .then((r) => setRischio(r.data))
        .catch(() => setErr("rischio", "Impossibile caricare il rischio insolvenza."))
        .finally(() => setLoad("rischio", false));
    }
  }, [tab]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="min-h-screen bg-[#f8fafc] flex flex-col">
      {/* ── Header ─────────────────────────────────────────── */}
      <header className="bg-white border-b border-gray-100 shadow-sm">
        <div className="max-w-7xl mx-auto px-6 py-3 flex items-center gap-4">
          <Link href="/" className="text-gray-400 hover:text-gray-700 transition-colors">
            <ChevronLeft className="w-5 h-5" />
          </Link>
          <TrendingUp className="w-6 h-6 text-green-600" />
          <h1 className="text-lg font-semibold text-gray-800">Forecasting Aziendale</h1>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-6 py-8 flex-1 w-full">
        {/* ── Tabs ──────────────────────────────────────────── */}
        <div className="flex gap-2 mb-8 flex-wrap">
          {TABS.map(({ id, label, icon: Icon }) => (
            <button
              key={id}
              onClick={() => setTab(id)}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium transition-all border ${
                tab === id
                  ? "bg-green-600 text-white border-green-600 shadow"
                  : "bg-white text-gray-600 border-gray-200 hover:border-green-400"
              }`}
            >
              <Icon className="w-4 h-4" />
              {label}
            </button>
          ))}
        </div>

        {/* ── Previsione Vendite ─────────────────────────────── */}
        {tab === "vendite" && (
          <TabPanel loading={loading.vendite} errore={errori.vendite}>
            {vendite && <VenditePanel data={vendite} />}
          </TabPanel>
        )}

        {/* ── Liquidità ─────────────────────────────────────── */}
        {tab === "liquidita" && (
          <TabPanel loading={loading.liquidita} errore={errori.liquidita}>
            {liquidita && <LiquiditaPanel data={liquidita} />}
          </TabPanel>
        )}

        {/* ── Scenari ───────────────────────────────────────── */}
        {tab === "scenari" && (
          <TabPanel loading={loading.scenari} errore={errori.scenari}>
            {scenari && <ScenariPanel data={scenari} />}
          </TabPanel>
        )}

        {/* ── Rischio Insolvenza ─────────────────────────────── */}
        {tab === "rischio" && (
          <TabPanel loading={loading.rischio} errore={errori.rischio}>
            {rischio && <RischioPanel data={rischio} />}
          </TabPanel>
        )}
      </main>
    </div>
  );
}

// ── Tab wrapper ────────────────────────────────────────────────────────────────

function TabPanel({
  loading,
  errore,
  children,
}: {
  loading: boolean;
  errore: string | null;
  children: React.ReactNode;
}) {
  if (loading)
    return (
      <div className="flex items-center justify-center h-64 text-gray-400 text-sm">
        Caricamento in corso...
      </div>
    );
  if (errore)
    return (
      <div className="flex items-center justify-center h-64 text-red-500 text-sm gap-2">
        <XCircle className="w-5 h-5" /> {errore}
      </div>
    );
  return <>{children}</>;
}

// ── Previsione Vendite panel ──────────────────────────────────────────────────

function VenditePanel({ data }: { data: PrevisioneVenditeResponse }) {
  const trendColor =
    data.trend === "crescente"
      ? "text-green-600"
      : data.trend === "decrescente"
      ? "text-red-500"
      : "text-amber-500";

  return (
    <div className="space-y-6">
      {/* KPI row */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <KpiBox label="Trend" value={data.trend.toUpperCase()} extra={trendColor} />
        <KpiBox label="Variazione mensile" value={fmtPct(data.variazione_percentuale)} />
        <KpiBox
          label="Previsione prossimo mese"
          value={data.previsione[0] ? fmt(data.previsione[0].valore) : "—"}
        />
      </div>

      {/* Grafico */}
      <div className="bg-white rounded-2xl border border-gray-100 p-6">
        <h2 className="text-sm font-semibold text-gray-600 mb-4">Andamento entrate — storico + previsione</h2>
        <div className="h-72">
          <VenditeChart data={data} />
        </div>
      </div>

      {/* Tabella previsioni */}
      <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-500 uppercase text-xs">
            <tr>
              <th className="px-4 py-3 text-left">Mese</th>
              <th className="px-4 py-3 text-right">Valore previsto</th>
              <th className="px-4 py-3 text-right">Intervallo 90%</th>
            </tr>
          </thead>
          <tbody>
            {data.previsione.map((p) => (
              <tr key={p.periodo} className="border-t border-gray-50 hover:bg-gray-50 transition-colors">
                <td className="px-4 py-3 font-medium text-gray-700">{p.periodo}</td>
                <td className="px-4 py-3 text-right text-green-700 font-semibold">{fmt(p.valore)}</td>
                <td className="px-4 py-3 text-right text-gray-400 text-xs">
                  {fmt(p.confidenza_min)} — {fmt(p.confidenza_max)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// ── Liquidità panel ───────────────────────────────────────────────────────────

function LiquiditaPanel({ data }: { data: PrevisioneLiquiditaResponse }) {
  return (
    <div className="space-y-6">
      {/* KPI row */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <KpiBox label="Saldo attuale" value={fmt(data.saldo_attuale)} />
        <KpiBox
          label="Copertura stimata"
          value={
            data.giorni_copertura === data.previsione_giorni.length
              ? `>${data.previsione_giorni.length} giorni`
              : `${data.giorni_copertura} giorni`
          }
        />
        <KpiBox
          label="Allerta liquidità"
          value={data.allerta ? "⚠ ATTIVA" : "OK"}
          extra={data.allerta ? "text-red-600" : "text-green-600"}
        />
      </div>

      {data.allerta && (
        <div className="bg-red-50 border border-red-200 rounded-xl px-4 py-3 flex items-center gap-2 text-red-700 text-sm">
          <AlertTriangle className="w-4 h-4 flex-shrink-0" />
          Attenzione: il saldo potrebbe diventare negativo entro{" "}
          <strong>{data.giorni_copertura} giorni</strong>.
        </div>
      )}

      {/* Grafico */}
      <div className="bg-white rounded-2xl border border-gray-100 p-6">
        <h2 className="text-sm font-semibold text-gray-600 mb-4">Proiezione saldo liquidità (30 giorni)</h2>
        <div className="h-72">
          <LiquiditaChart data={data} />
        </div>
      </div>
    </div>
  );
}

// ── Scenari panel ─────────────────────────────────────────────────────────────

function ScenariPanel({ data }: { data: SimulazioneScenariResponse }) {
  const SCENARIO_COLOR: Record<string, string> = {
    ottimistico: "bg-green-50 border-green-200 text-green-700",
    base: "bg-blue-50 border-blue-200 text-blue-700",
    pessimistico: "bg-red-50 border-red-200 text-red-700",
  };

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <KpiBox label="Mese di riferimento" value={data.mese_riferimento} />
        <KpiBox
          label="Media storica cashflow"
          value={fmt(data.media_storica_entrate - data.media_storica_uscite)}
        />
      </div>

      {/* Cards scenari */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {data.scenari.map((s) => (
          <div
            key={s.scenario}
            className={`rounded-2xl border p-5 ${SCENARIO_COLOR[s.scenario] ?? "bg-gray-50 border-gray-200"}`}
          >
            <p className="text-xs font-semibold uppercase tracking-wide mb-3 opacity-70">
              {s.scenario}
            </p>
            <div className="space-y-1 text-sm">
              <Row label="Entrate" value={fmt(s.entrate_previste)} />
              <Row label="Uscite" value={fmt(s.uscite_previste)} />
              <Row label="Cashflow" value={fmt(s.cashflow)} bold />
              <Row label="Variazione" value={fmtPct(s.variazione_percentuale)} />
            </div>
          </div>
        ))}
      </div>

      {/* Grafico comparativo */}
      <div className="bg-white rounded-2xl border border-gray-100 p-6">
        <h2 className="text-sm font-semibold text-gray-600 mb-4">Confronto scenari</h2>
        <div className="h-64">
          <ScenariChart data={data} />
        </div>
      </div>
    </div>
  );
}

// ── Rischio panel ─────────────────────────────────────────────────────────────

function RischioPanel({ data }: { data: RischioInsolvenzaResponse }) {
  const barWidth = Math.min(data.punteggio, 100);
  const barColor =
    data.livello === "basso"
      ? "bg-green-500"
      : data.livello === "medio"
      ? "bg-amber-400"
      : data.livello === "alto"
      ? "bg-orange-500"
      : "bg-red-600";

  return (
    <div className="space-y-6">
      {/* Punteggio principale */}
      <div
        className={`rounded-2xl border p-6 ${RISCHIO_BG[data.livello] ?? "bg-gray-50 border-gray-200"}`}
      >
        <div className="flex items-center justify-between mb-4">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 mb-1">
              Punteggio rischio
            </p>
            <p className={`text-5xl font-bold ${RISCHIO_COLOR[data.livello]}`}>
              {data.punteggio.toFixed(0)}
              <span className="text-2xl font-medium opacity-60">/100</span>
            </p>
          </div>
          <div
            className={`text-lg font-bold uppercase px-4 py-2 rounded-xl ${RISCHIO_COLOR[data.livello]} ${RISCHIO_BG[data.livello]}`}
          >
            {data.livello}
          </div>
        </div>
        {/* Barra progresso */}
        <div className="h-3 bg-white/60 rounded-full overflow-hidden">
          <div
            className={`h-full rounded-full transition-all ${barColor}`}
            style={{ width: `${barWidth}%` }}
          />
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
        {/* Fattori di rischio */}
        <div className="bg-white rounded-2xl border border-gray-100 p-5">
          <h2 className="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-orange-500" /> Fattori di rischio
          </h2>
          {data.fattori.length === 0 ? (
            <p className="text-sm text-gray-400">Nessun fattore critico rilevato.</p>
          ) : (
            <ul className="space-y-2">
              {data.fattori.map((f, i) => (
                <li key={i} className="flex items-start gap-2 text-sm">
                  <span
                    className={`text-xs font-semibold px-2 py-0.5 rounded-full mt-0.5 flex-shrink-0 ${
                      IMPATTO_COLOR[f.impatto] ?? "bg-gray-100 text-gray-600"
                    }`}
                  >
                    {f.impatto}
                  </span>
                  <span className="text-gray-600">{f.fattore}</span>
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* Raccomandazioni */}
        <div className="bg-white rounded-2xl border border-gray-100 p-5">
          <h2 className="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-green-500" /> Raccomandazioni
          </h2>
          <ul className="space-y-2">
            {data.raccomandazioni.map((r, i) => (
              <li key={i} className="flex items-start gap-2 text-sm text-gray-600">
                <Info className="w-4 h-4 text-blue-400 flex-shrink-0 mt-0.5" />
                {r}
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}

// ── Micro-componenti ──────────────────────────────────────────────────────────

function KpiBox({
  label,
  value,
  extra = "text-gray-800",
}: {
  label: string;
  value: string;
  extra?: string;
}) {
  return (
    <div className="bg-white rounded-2xl border border-gray-100 px-5 py-4">
      <p className="text-xs text-gray-400 uppercase tracking-wide mb-1">{label}</p>
      <p className={`text-xl font-bold ${extra}`}>{value}</p>
    </div>
  );
}

function Row({
  label,
  value,
  bold,
}: {
  label: string;
  value: string;
  bold?: boolean;
}) {
  return (
    <div className="flex justify-between">
      <span className="opacity-70">{label}</span>
      <span className={bold ? "font-bold" : ""}>{value}</span>
    </div>
  );
}
