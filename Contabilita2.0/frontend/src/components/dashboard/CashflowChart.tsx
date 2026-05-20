"use client";

import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from "recharts";
import { PuntoGrafico } from "@/lib/api";

interface CashflowChartProps {
  dati: PuntoGrafico[];
}

const formatEuro = (v: number | string) =>
  new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(Number(v));

export default function CashflowChart({ dati }: CashflowChartProps) {
  const datiNumerici = dati.map((d) => ({
    ...d,
    entrate: Number(d.entrate),
    uscite: Number(d.uscite),
    cashflow: Number(d.cashflow),
  }));
  return (
    <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6">
      <h2 className="text-base font-semibold text-gray-700 mb-4">
        Cashflow cumulativo
      </h2>
      <ResponsiveContainer width="100%" height={300}>
        <AreaChart data={datiNumerici} margin={{ top: 4, right: 16, left: 0, bottom: 0 }}>
          <defs>
            <linearGradient id="cashflowGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3} />
              <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
          <XAxis dataKey="data" tick={{ fontSize: 11 }} />
          <YAxis tickFormatter={(v) => `€${(v / 1000).toFixed(0)}k`} tick={{ fontSize: 12 }} />
          <Tooltip formatter={(value: number) => formatEuro(value)} />
          <Area
            type="monotone"
            dataKey="cashflow"
            name="Cashflow"
            stroke="#3b82f6"
            fill="url(#cashflowGrad)"
            strokeWidth={2}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
