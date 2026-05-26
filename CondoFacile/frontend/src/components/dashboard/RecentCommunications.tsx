import { RecentCommunication } from '@/types/dashboard';
import { Bell, ArrowRight } from 'lucide-react';

interface RecentCommunicationsProps {
  communications: RecentCommunication[];
}

const tipoConfig: Record<string, { bg: string; color: string }> = {
  'Avviso urgente': { bg: '#fef2f2', color: '#dc2626' },
  Assemblea: { bg: '#faf5ff', color: '#7c3aed' },
  Comunicazione: { bg: '#eff6ff', color: '#2563eb' },
};

export default function RecentCommunications({ communications }: RecentCommunicationsProps) {
  return (
    <div
      className="rounded-xl p-5 shadow-sm"
      style={{ backgroundColor: 'var(--card-bg)', border: '1px solid var(--border)' }}
    >
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-sm font-semibold" style={{ color: 'var(--foreground)' }}>
          Comunicazioni Recenti
        </h2>
        <Bell size={16} style={{ color: 'var(--text-muted)' }} />
      </div>

      <ul className="space-y-3">
        {communications.map((c) => {
          const t = tipoConfig[c.tipo] ?? { bg: '#f3f4f6', color: '#6b7280' };
          return (
            <li
              key={c.id}
              className="flex items-start gap-3 pb-3"
              style={{ borderBottom: '1px solid var(--border)' }}
            >
              <div
                className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5"
                style={{ backgroundColor: t.bg, color: t.color }}
              >
                <Bell size={14} />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium" style={{ color: 'var(--foreground)' }}>
                  {c.titolo}
                </p>
                <div className="flex items-center gap-2 mt-1">
                  <span
                    className="text-xs px-1.5 py-0.5 rounded"
                    style={{ backgroundColor: t.bg, color: t.color }}
                  >
                    {c.tipo}
                  </span>
                  <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
                    {new Date(c.data).toLocaleDateString('it-IT')} · {c.destinatari} destinatari
                  </span>
                </div>
              </div>
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
