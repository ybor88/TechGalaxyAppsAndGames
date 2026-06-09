'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { fetchFornitoriAnalytics, FornitoriAnalytics } from '@/lib/api';

export default function AnalyticsPage() {
  const { token } = useAuth();
  const [data, setData] = useState<FornitoriAnalytics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    setLoading(true);
    setError(null);
    fetchFornitoriAnalytics(token)
      .then((d) => setData(d))
      .catch((e) => setError((e as Error).message || 'Errore caricamento analytics'))
      .finally(() => setLoading(false));
  }, [token]);

  return (
    <div className="flex flex-col flex-1 overflow-hidden" style={{ backgroundColor: 'var(--background)' }}>
      <header className="flex items-center justify-between px-6 py-3 border-b flex-shrink-0" style={{ backgroundColor: '#fff', borderColor: '#f0f0f0' }}>
        <div>
          <h1 className="text-sm font-bold" style={{ color: '#1a1a1a' }}>Analytics Fornitori</h1>
          <p className="text-xs" style={{ color: '#888' }}>Panoramica interventi e costi per fornitore</p>
        </div>
      </header>

      <main className="flex-1 p-6 overflow-auto">
        {loading ? (
          <p>Caricamento analytics...</p>
        ) : error ? (
          <div className="rounded-xl p-6 text-center" style={{ backgroundColor: '#fef2f2', border: '1px solid #fecaca' }}>
            <p className="font-semibold" style={{ color: '#dc2626' }}>Impossibile caricare le statistiche</p>
            <p className="text-sm mt-1" style={{ color: '#6b7280' }}>{error}</p>
          </div>
        ) : data ? (
          <div className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="p-4 rounded-2xl" style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0' }}>
                <div className="text-xs text-gray-500">Fornitori totali</div>
                <div className="text-2xl font-bold mt-2">{data.totalFornitori}</div>
              </div>
              <div className="p-4 rounded-2xl" style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0' }}>
                <div className="text-xs text-gray-500">Interventi totali</div>
                <div className="text-2xl font-bold mt-2">{data.totalInterventi}</div>
              </div>
              <div className="p-4 rounded-2xl" style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0' }}>
                <div className="text-xs text-gray-500">Costo totale</div>
                <div className="text-2xl font-bold mt-2">€ {Number(data.totalCosto).toFixed(2)}</div>
              </div>
            </div>

            <div className="p-4 rounded-2xl" style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0' }}>
              <div className="text-sm font-semibold mb-3">Top fornitori (per numero interventi)</div>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-xs text-gray-500">
                      <th className="pb-2">Fornitore</th>
                      <th className="pb-2">Interventi</th>
                      <th className="pb-2">Costo totale</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.topFornitori.map((f) => (
                      <tr key={f.id} className="border-t">
                        <td className="py-3">{f.nome}</td>
                        <td className="py-3">{f.interventi}</td>
                        <td className="py-3">€ {Number(f.costo).toFixed(2)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        ) : (
          <p>Nessun dato disponibile</p>
        )}
      </main>
    </div>
  );
}
