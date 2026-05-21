"use client";

import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from "recharts";
import { SimulazioneScenariResponse } from "@/lib/api";

const fmt = (v: number) =>
  new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(v);

interface Props {
  data: SimulazioneScenariResponse;
}

export default function ScenariChart({ data }: Props) {
  const chartData = data.scenari.map((s) => ({
    name: s.scenario.charAt(0).toUpperCase() + s.scenario.slice(1),
    Entrate: s.entrate_previste,
    Uscite: s.uscite_previste,
    Cashflow: s.cashflow,
  }));

  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={chartData} margin={{ top: 4, right: 16, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
        <XAxis dataKey="name" tick={{ fontSize: 12 }} />
        <YAxis tick={{ fontSize: 11 }} tickFormatter={(v) => `€${(v / 1000).toFixed(0)}k`} />
        <Tooltip formatter={(v: number) => fmt(v)} />
        <Legend />
        <Bar dataKey="Entrate" fill="#4ade80" radius={[4, 4, 0, 0]} />
        <Bar dataKey="Uscite" fill="#f87171" radius={[4, 4, 0, 0]} />
        <Bar dataKey="Cashflow" fill="#60a5fa" radius={[4, 4, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
}
