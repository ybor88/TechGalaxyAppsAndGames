import { UpcomingDeadline } from '@/types/dashboard';
import { Calendar, ArrowRight } from 'lucide-react';

interface UpcomingDeadlinesProps {
  deadlines: UpcomingDeadline[];
}

const tipoConfig: Record<string, { bg: string; color: string }> = {
  Pagamento: { bg: '#fef2f2', color: '#dc2626' },
  Contratto: { bg: '#fff7ed', color: '#ea580c' },
  Manutenzione: { bg: '#eff6ff', color: '#2563eb' },
  Assemblea: { bg: '#faf5ff', color: '#7c3aed' },
};

function urgencyColor(giorni: number): string {
  if (giorni <= 7) return '#dc2626';
  if (giorni <= 14) return '#f59e0b';
  return '#6b7280';
}

export default function UpcomingDeadlines({ deadlines }: UpcomingDeadlinesProps) {
  return (
    <div
      className="rounded-xl p-5 shadow-sm"
      style={{ backgroundColor: 'var(--card-bg)', border: '1px solid var(--border)' }}
    >
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <h2 className="text-sm font-semibold" style={{ color: 'var(--foreground)' }}>
            Scadenze Imminenti
          </h2>
          <span
            className="text-xs px-2 py-0.5 rounded-full font-semibold"
            style={{ backgroundColor: '#fef2f2', color: 'var(--primary)' }}
          >
            Prossimi 30 giorni
          </span>
        </div>
        <Calendar size={16} style={{ color: 'var(--text-muted)' }} />
      </div>

      {deadlines.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-6" style={{ color: '#ccc' }}>
          <Calendar size={28} style={{ marginBottom: 6 }} />
          <p className="text-xs" style={{ color: '#aaa' }}>Nessuna scadenza nei prossimi 30 giorni</p>
        </div>
      ) : (
      <ul className="space-y-3">
        {deadlines.map((d) => {
          const t = tipoConfig[d.tipo] ?? { bg: '#f3f4f6', color: '#6b7280' };
          return (
            <li
              key={d.id}
              className="flex items-center gap-3 pb-3"
              style={{ borderBottom: '1px solid var(--border)' }}
            >
              <div
                className="w-10 h-10 rounded-lg flex flex-col items-center justify-center flex-shrink-0 text-center"
                style={{ backgroundColor: t.bg }}
              >
                <span className="text-xs font-bold leading-none" style={{ color: t.color }}>
                  {d.giorniMancanti}
                </span>
                <span className="text-xs leading-none" style={{ color: t.color }}>
                  gg
                </span>
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium truncate" style={{ color: 'var(--foreground)' }}>
                  {d.descrizione}
                </p>
                <div className="flex items-center gap-2 mt-0.5">
                  <span
                    className="text-xs px-1.5 py-0.5 rounded"
                    style={{ backgroundColor: t.bg, color: t.color }}
                  >
                    {d.tipo}
                  </span>
                  <span className="text-xs" style={{ color: urgencyColor(d.giorniMancanti) }}>
                    {new Date(d.data).toLocaleDateString('it-IT')}
                  </span>
                </div>
              </div>
            </li>
          );
        })}
      </ul>
      )}

      <button
        className="mt-3 flex items-center gap-1 text-xs font-medium"
        style={{ color: 'var(--primary)' }}
      >
        Vedi scadenziario <ArrowRight size={12} />
      </button>
    </div>
  );
}
