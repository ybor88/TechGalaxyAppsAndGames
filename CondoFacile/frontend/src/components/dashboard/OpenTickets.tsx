import { OpenTicket } from '@/types/dashboard';
import { ArrowRight } from 'lucide-react';

interface OpenTicketsProps {
  tickets: OpenTicket[];
}

const priorityConfig = {
  alta: { label: 'Alta', bg: '#fef2f2', color: '#dc2626' },
  media: { label: 'Media', bg: '#fefce8', color: '#ca8a04' },
  bassa: { label: 'Bassa', bg: '#f0fdf4', color: '#16a34a' },
};

const statoConfig: Record<string, { bg: string; color: string }> = {
  Nuova: { bg: '#eff6ff', color: '#2563eb' },
  'In lavorazione': { bg: '#fff7ed', color: '#ea580c' },
  Assegnata: { bg: '#faf5ff', color: '#7c3aed' },
};

export default function OpenTickets({ tickets }: OpenTicketsProps) {
  return (
    <div
      className="rounded-xl p-5 shadow-sm"
      style={{ backgroundColor: 'var(--card-bg)', border: '1px solid var(--border)' }}
    >
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-sm font-semibold" style={{ color: 'var(--foreground)' }}>
          Segnalazioni Aperte
        </h2>
        <span
          className="text-xs font-medium px-2 py-0.5 rounded-full"
          style={{ backgroundColor: '#fef2f2', color: 'var(--primary)' }}
        >
          {tickets.length}
        </span>
      </div>

      <ul className="space-y-3">
        {tickets.map((t) => {
          const p = priorityConfig[t.priorita];
          const s = statoConfig[t.stato] ?? { bg: '#f3f4f6', color: '#6b7280' };
          return (
            <li
              key={t.id}
              className="flex items-start justify-between gap-3 pb-3"
              style={{ borderBottom: '1px solid var(--border)' }}
            >
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium truncate" style={{ color: 'var(--foreground)' }}>
                  {t.titolo}
                </p>
                <div className="flex items-center gap-2 mt-1">
                  <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
                    {t.categoria}
                  </span>
                  <span
                    className="text-xs px-1.5 py-0.5 rounded"
                    style={{ backgroundColor: p.bg, color: p.color }}
                  >
                    {p.label}
                  </span>
                </div>
              </div>
              <span
                className="text-xs px-2 py-1 rounded-full whitespace-nowrap flex-shrink-0"
                style={{ backgroundColor: s.bg, color: s.color }}
              >
                {t.stato}
              </span>
            </li>
          );
        })}
      </ul>

      <button
        className="mt-3 flex items-center gap-1 text-xs font-medium"
        style={{ color: 'var(--primary)' }}
      >
        Vedi tutte <ArrowRight size={12} />
      </button>
    </div>
  );
}
