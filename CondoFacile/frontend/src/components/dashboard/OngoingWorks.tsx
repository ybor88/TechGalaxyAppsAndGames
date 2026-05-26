import { OngoingWork } from '@/types/dashboard';
import { HardHat } from 'lucide-react';

interface OngoingWorksProps {
  works: OngoingWork[];
}

export default function OngoingWorks({ works }: OngoingWorksProps) {
  return (
    <div
      className="rounded-xl p-5 shadow-sm"
      style={{ backgroundColor: 'var(--card-bg)', border: '1px solid var(--border)' }}
    >
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-sm font-semibold" style={{ color: 'var(--foreground)' }}>
          Lavori in Corso
        </h2>
        <HardHat size={16} style={{ color: 'var(--text-muted)' }} />
      </div>

      <ul className="space-y-4">
        {works.map((w) => (
          <li
            key={w.id}
            className="pb-4"
            style={{ borderBottom: '1px solid var(--border)' }}
          >
            <div className="flex items-start justify-between gap-2 mb-2">
              <div>
                <p className="text-sm font-medium" style={{ color: 'var(--foreground)' }}>
                  {w.descrizione}
                </p>
                <p className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>
                  {w.fornitore} · dal {new Date(w.dataInizio).toLocaleDateString('it-IT')} al{' '}
                  {new Date(w.dataFine).toLocaleDateString('it-IT')}
                </p>
              </div>
              <span
                className="text-xs px-2 py-1 rounded-full whitespace-nowrap flex-shrink-0"
                style={{ backgroundColor: '#fff7ed', color: '#ea580c' }}
              >
                {w.stato}
              </span>
            </div>

            {/* Progress bar */}
            <div>
              <div className="flex justify-between text-xs mb-1" style={{ color: 'var(--text-muted)' }}>
                <span>Avanzamento</span>
                <span className="font-medium" style={{ color: 'var(--foreground)' }}>
                  {w.progressione}%
                </span>
              </div>
              <div
                className="w-full rounded-full overflow-hidden"
                style={{ height: '6px', backgroundColor: '#f3f4f6' }}
              >
                <div
                  className="h-full rounded-full transition-all"
                  style={{
                    width: `${w.progressione}%`,
                    backgroundColor: w.progressione >= 75 ? '#10b981' : 'var(--primary)',
                  }}
                />
              </div>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
