"use client";

import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from "recharts";
import { PrevisioneVenditeResponse } from "@/lib/api";

const fmt = (v: number) =>
  new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(v);

interface Props {
  data: PrevisioneVenditeResponse;
}

export default function VenditeChart({ data }: Props) {
  const chartData = [
    ...data.storico.map((p) => ({
      periodo: p.periodo.slice(5),
      storico: p.valore,
      previsione: null as number | null,
    })),
    ...data.previsione.map((p) => ({
      periodo: p.periodo.slice(5),
      storico: null as number | null,
      previsione: p.valore,
    })),
  ];

  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart data={chartData} margin={{ top: 4, right: 16, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
        <XAxis dataKey="periodo" tick={{ fontSize: 11 }} />
        <YAxis tick={{ fontSize: 11 }} tickFormatter={(v) => `€${(v / 1000).toFixed(0)}k`} />
        <Tooltip formatter={(v: number) => fmt(v)} />
        <Legend />
        <Line
          type="monotone"
          dataKey="storico"
          name="Storico"
          stroke="#16a34a"
          strokeWidth={2}
          dot={false}
          connectNulls={false}
        />
        <Line
          type="monotone"
          dataKey="previsione"
          name="Previsione"
          stroke="#4ade80"
          strokeWidth={2}
          strokeDasharray="6 3"
          dot={{ r: 4 }}
          connectNulls={false}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
