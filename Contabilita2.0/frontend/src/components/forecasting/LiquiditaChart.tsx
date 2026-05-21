"use client";

import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ReferenceLine,
  ResponsiveContainer,
} from "recharts";
import { PrevisioneLiquiditaResponse } from "@/lib/api";

const fmt = (v: number) =>
  new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(v);

interface Props {
  data: PrevisioneLiquiditaResponse;
}

export default function LiquiditaChart({ data }: Props) {
  const chartData = data.previsione_giorni
    .filter((_, i) => i % 5 === 0 || i === data.previsione_giorni.length - 1)
    .map((p) => ({
      giorno: p.periodo.slice(5),
      saldo: p.valore,
      ci_min: p.confidenza_min,
      ci_max: p.confidenza_max,
    }));

  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart data={chartData} margin={{ top: 4, right: 16, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
        <XAxis dataKey="giorno" tick={{ fontSize: 10 }} />
        <YAxis tick={{ fontSize: 11 }} tickFormatter={(v) => `€${(v / 1000).toFixed(0)}k`} />
        <Tooltip formatter={(v: number) => fmt(v)} />
        <Legend />
        <ReferenceLine y={0} stroke="#ef4444" strokeDasharray="4 2" />
        <Line
          type="monotone"
          dataKey="saldo"
          name="Saldo previsto"
          stroke="#0ea5e9"
          strokeWidth={2}
          dot={false}
        />
        <Line
          type="monotone"
          dataKey="ci_min"
          name="Limite inferiore"
          stroke="#bae6fd"
          strokeWidth={1}
          strokeDasharray="3 3"
          dot={false}
        />
        <Line
          type="monotone"
          dataKey="ci_max"
          name="Limite superiore"
          stroke="#7dd3fc"
          strokeWidth={1}
          strokeDasharray="3 3"
          dot={false}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
