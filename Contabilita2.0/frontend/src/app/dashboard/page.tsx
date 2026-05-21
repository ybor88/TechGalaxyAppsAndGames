"use client";

import { useEffect, useState } from "react";
import dynamic from "next/dynamic";
import Link from "next/link";
import Image from "next/image";
import { dashboardApi, DashboardData } from "@/lib/api";
import KPICard from "@/components/dashboard/KPICard";
import LiquiditaIndicator from "@/components/dashboard/LiquiditaIndicator";
import { TrendingUp, TrendingDown, Wallet, Activity, ChevronLeft } from "lucide-react";

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
    <div className="min-h-screen bg-[#f8fafc] flex flex-col">
      {/* ── Top navbar ─────────────────────────────────────── */}
      <header className="bg-white border-b border-gray-100 shadow-sm">
        <div className="max-w-7xl mx-auto px-6 py-3 flex items-center gap-4">
          <Link href="/" className="flex items-center gap-3 focus:outline-none group">
            <Image
              src="/logo.jpeg"
              alt="Contabilità 2.0"
              width={44}
              height={44}
              className="logo-glow rounded-xl object-contain"
              priority
            />
            <div>
              <span className="text-lg font-bold leading-none">
                <span className="text-[#1e3a8a]">Contabilità</span>{" "}
                <span className="text-[#f97316]">2</span>
                <span className="text-[#16a34a]">.</span>
                <span className="text-[#f59e0b]">0</span>
              </span>
              <p className="text-[10px] text-gray-400 mt-0.5">ERP aziendale 100% open source</p>
            </div>
          </Link>
          <nav className="ml-8 flex gap-4 text-sm">
            <Link href="/dashboard" className="text-[#1e3a8a] font-semibold border-b-2 border-[#1e3a8a] pb-0.5">Dashboard</Link>
            <Link href="/movimenti" className="text-gray-500 hover:text-[#1e3a8a] transition-colors">Movimenti</Link>
            <Link href="/conti" className="text-gray-500 hover:text-[#1e3a8a] transition-colors">Piano dei Conti</Link>
          </nav>
        </div>
      </header>

      <main className="flex-1 p-6 max-w-7xl mx-auto w-full space-y-6">
      {/* Header pagina */}
      <div className="flex items-center justify-between">
        <div>
          <Link
            href="/"
            className="inline-flex items-center gap-1 text-xs text-gray-400 hover:text-[#1e3a8a] transition-colors mb-1"
          >
            <ChevronLeft size={14} /> Home
          </Link>
          <h1 className="text-2xl font-bold text-[#1e3a8a]">Dashboard Finanziaria</h1>
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

      {/* Footer */}
      <footer className="border-t border-gray-100 py-5">
        <div className="flex items-center justify-center gap-2">
          <Image
            src="/logo.jpeg"
            alt="Contabilità 2.0"
            width={22}
            height={22}
            className="rounded opacity-60"
          />
          <p className="text-xs text-gray-300">
            Contabilità 2.0 — v1.0.0 — 100% open source, 0€
          </p>
        </div>
      </footer>
    </div>
  );
}
