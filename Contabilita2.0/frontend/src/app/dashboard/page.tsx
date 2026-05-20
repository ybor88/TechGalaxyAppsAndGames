"use client";

import { useEffect, useState } from "react";
import dynamic from "next/dynamic";
import { dashboardApi, DashboardData } from "@/lib/api";
import KPICard from "@/components/dashboard/KPICard";
import LiquiditaIndicator from "@/components/dashboard/LiquiditaIndicator";
import { TrendingUp, TrendingDown, Wallet, Activity } from "lucide-react";

// Importazione dinamica per evitare errori SSR con Recharts
const EntrateUsciteChart = dynamic(
  () => import("@/components/dashboard/EntrateUsciteChart"),
  { ssr: false, loading: () => <div className="bg-white rounded-2xl border border-gray-100 h-[348px] animate-pulse" /> }
);
const CashflowChart = dynamic(
  () => import("@/components/dashboard/CashflowChart"),
  { ssr: false, loading: () => <div className="bg-white rounded-2xl border border-gray-100 h-[348px] animate-pulse" /> }
);

const fmt = (n: number | string) =>
  new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(Number(n));

export default function DashboardPage() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [errore, setErrore] = useState<string | null>(null);

  useEffect(() => {
    dashboardApi
      .getDashboard()
      .then((res) => setData(res.data))
      .catch(() => setErrore("Impossibile caricare i dati della dashboard."))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen text-gray-400">
        Caricamento dashboard...
      </div>
    );
  }

  if (errore || !data) {
    return (
      <div className="flex items-center justify-center h-screen text-red-500">
        {errore ?? "Dati non disponibili."}
      </div>
    );
  }

  const { kpi, andamento_mensile, cashflow_settimanale, aggiornato_al } = data;

  return (
    <main className="p-6 max-w-7xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Dashboard Finanziaria</h1>
          <p className="text-sm text-gray-400">Aggiornato al {aggiornato_al}</p>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <KPICard
          titolo="Saldo Operativo"
          valore={fmt(kpi.saldo_operativo)}
          tendenza={Number(kpi.saldo_operativo) >= 0 ? "positivo" : "negativo"}
          icona={<Wallet size={18} />}
        />
        <KPICard
          titolo="Totale Entrate"
          valore={fmt(kpi.totale_entrate)}
          tendenza="positivo"
          icona={<TrendingUp size={18} />}
        />
        <KPICard
          titolo="Totale Uscite"
          valore={fmt(kpi.totale_uscite)}
          tendenza="negativo"
          icona={<TrendingDown size={18} />}
        />
        <KPICard
          titolo="Cashflow Netto"
          valore={fmt(kpi.cashflow_netto)}
          tendenza={Number(kpi.cashflow_netto) >= 0 ? "positivo" : "negativo"}
          icona={<Activity size={18} />}
        />
      </div>

      {/* Grafici */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <EntrateUsciteChart dati={andamento_mensile} />
        <CashflowChart dati={cashflow_settimanale} />
      </div>

      {/* Liquidità */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <LiquiditaIndicator indice={kpi.indice_liquidita} />
      </div>
    </main>
  );
}
