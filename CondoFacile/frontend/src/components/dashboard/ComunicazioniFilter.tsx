'use client';

import { useState, useMemo } from 'react';
import { RecentCommunication } from '@/types/dashboard';
import { Megaphone, ChevronDown } from 'lucide-react';

const TIPI = ['Tutti', 'avviso', 'assemblea', 'manutenzione', 'emergenza', 'circolare'];

const tipoComConfig: Record<string, { bg: string; color: string }> = {
  avviso:       { bg: '#fef9c3', color: '#ca8a04' },
  assemblea:    { bg: '#faf5ff', color: '#7c3aed' },
  manutenzione: { bg: '#eff6ff', color: '#2563eb' },
  emergenza:    { bg: '#fef2f2', color: '#dc2626' },
  circolare:    { bg: '#f0fdf4', color: '#16a34a' },
};

/** Genera etichette mese/anno dagli ultimi N mesi a partire da oggi */
function lastNMonths(n: number): { label: string; value: string }[] {
  const result: { label: string; value: string }[] = [];
  const mesi = ['Gen','Feb','Mar','Apr','Mag','Giu','Lug','Ago','Set','Ott','Nov','Dic'];
  const now = new Date();
  for (let i = 0; i < n; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    result.push({ label: `${mesi[d.getMonth()]} ${d.getFullYear()}`, value: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}` });
  }
  return result;
}

interface Props {
  comunicazioni: RecentCommunication[];
}

export default function ComunicazioniFilter({ comunicazioni }: Props) {
  const [filtroTipo, setFiltroTipo] = useState('Tutti');
  const [filtroMese, setFiltroMese] = useState('Tutti');
  const mesi = [{ label: 'Tutti i mesi', value: 'Tutti' }, ...lastNMonths(12)];

  const filtered = useMemo(() => {
    return comunicazioni.filter((c) => {
      const tipoOk = filtroTipo === 'Tutti' || c.tipo === filtroTipo;
      const meseOk = filtroMese === 'Tutti' || c.data.startsWith(filtroMese);
      return tipoOk && meseOk;
    });
  }, [comunicazioni, filtroTipo, filtroMese]);

  return (
    <div
      className="rounded-xl p-5 shadow-sm col-span-2"
      style={{ backgroundColor: 'var(--card-bg)', border: '1px solid var(--border)' }}
    >
      {/* Intestazione + filtri */}
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div className="flex items-center gap-2">
          <Megaphone size={15} style={{ color: 'var(--text-muted)' }} />
          <h3 className="text-sm font-semibold" style={{ color: 'var(--foreground)' }}>
            Storico Comunicazioni
          </h3>
          <span
            className="text-xs px-2 py-0.5 rounded-full"
            style={{ backgroundColor: '#f3f4f6', color: '#6b7280' }}
          >
            {filtered.length} / {comunicazioni.length}
          </span>
        </div>

        <div className="flex items-center gap-2">
          {/* Filtro tipo */}
          <div className="relative">
            <select
              value={filtroTipo}
              onChange={(e) => setFiltroTipo(e.target.value)}
              className="appearance-none text-xs pl-2.5 pr-7 py-1.5 rounded-lg cursor-pointer"
              style={{
                backgroundColor: '#f9fafb',
                border: '1px solid var(--border)',
                color: 'var(--foreground)',
                outline: 'none',
              }}
            >
              {TIPI.map((t) => (
                <option key={t} value={t}>{t === 'Tutti' ? 'Tutti i tipi' : t.charAt(0).toUpperCase() + t.slice(1)}</option>
              ))}
            </select>
            <ChevronDown size={12} className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2" style={{ color: '#9ca3af' }} />
          </div>

          {/* Filtro mese */}
          <div className="relative">
            <select
              value={filtroMese}
              onChange={(e) => setFiltroMese(e.target.value)}
              className="appearance-none text-xs pl-2.5 pr-7 py-1.5 rounded-lg cursor-pointer"
              style={{
                backgroundColor: '#f9fafb',
                border: '1px solid var(--border)',
                color: 'var(--foreground)',
                outline: 'none',
              }}
            >
              {mesi.map((m) => (
                <option key={m.value} value={m.value}>{m.label}</option>
              ))}
            </select>
            <ChevronDown size={12} className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2" style={{ color: '#9ca3af' }} />
          </div>
        </div>
      </div>

      {/* Lista */}
      {filtered.length === 0 ? (
        <div className="text-center py-8">
          <Megaphone size={28} style={{ color: '#e5e7eb', margin: '0 auto 6px' }} />
          <p className="text-xs" style={{ color: '#aaa' }}>Nessuna comunicazione per i filtri selezionati</p>
        </div>
      ) : (
        <ul className="divide-y" style={{ borderColor: 'var(--border)' }}>
          {filtered.map((c) => {
            const cfg = tipoComConfig[c.tipo] ?? { bg: '#f3f4f6', color: '#6b7280' };
            return (
              <li key={c.id} className="flex items-start gap-3 py-3 first:pt-0 last:pb-0">
                <span
                  className="text-xs px-1.5 py-0.5 rounded font-semibold flex-shrink-0 mt-0.5"
                  style={{ backgroundColor: cfg.bg, color: cfg.color }}
                >
                  {c.tipo}
                </span>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium" style={{ color: '#1a1a1a' }}>{c.titolo}</p>
                </div>
                <span className="text-xs flex-shrink-0" style={{ color: '#aaa' }}>
                  {new Date(c.data).toLocaleDateString('it-IT')}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
