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
import { SaldoMensile } from "@/lib/api";

interface EntrateUsciteProps {
  dati: SaldoMensile[];
}

const formatEuro = (v: number | string) =>
  new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(Number(v));

export default function EntrateUsciteChart({ dati }: EntrateUsciteProps) {
  const datiNumerici = dati.map((d) => ({
    ...d,
    entrate: Number(d.entrate),
    uscite: Number(d.uscite),
    saldo: Number(d.saldo),
  }));
  return (
    <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6">
      <h2 className="text-base font-semibold text-gray-700 mb-4">
        Entrate / Uscite mensili
      </h2>
      <ResponsiveContainer width="100%" height={300}>
        <BarChart data={datiNumerici} margin={{ top: 4, right: 16, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
          <XAxis dataKey="mese" tick={{ fontSize: 12 }} />
          <YAxis tickFormatter={(v) => `€${(v / 1000).toFixed(0)}k`} tick={{ fontSize: 12 }} />
          <Tooltip formatter={(value: number) => formatEuro(value)} />
          <Legend />
          <Bar dataKey="entrate" name="Entrate" fill="#22c55e" radius={[4, 4, 0, 0]} />
          <Bar dataKey="uscite" name="Uscite" fill="#ef4444" radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
