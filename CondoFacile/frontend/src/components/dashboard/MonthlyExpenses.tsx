'use client';

import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import { MonthlyExpense } from '@/types/dashboard';

interface MonthlyExpensesProps {
  data: MonthlyExpense[];
}

export default function MonthlyExpenses({ data }: MonthlyExpensesProps) {
  return (
    <div
      className="rounded-xl p-5 shadow-sm"
      style={{ backgroundColor: 'var(--card-bg)', border: '1px solid var(--border)' }}
    >
      <h2 className="text-sm font-semibold mb-4" style={{ color: 'var(--foreground)' }}>
        Spese Ultimi 6 Mesi
      </h2>
      <ResponsiveContainer width="100%" height={180}>
        <BarChart data={data} margin={{ top: 0, right: 0, left: -20, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" vertical={false} />
          <XAxis
            dataKey="mese"
            tick={{ fontSize: 11, fill: '#6b7280' }}
            axisLine={false}
            tickLine={false}
          />
          <YAxis
            tick={{ fontSize: 11, fill: '#6b7280' }}
            axisLine={false}
            tickLine={false}
            tickFormatter={(v) => `€${v}`}
          />
          <Tooltip
            formatter={(value) => [`€${Number(value ?? 0).toLocaleString('it-IT')}`, 'Spesa']}
            contentStyle={{
              borderRadius: '8px',
              border: '1px solid #e5e7eb',
              fontSize: '12px',
            }}
          />
          <Bar dataKey="importo" fill="var(--primary)" radius={[4, 4, 0, 0]} maxBarSize={40} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
