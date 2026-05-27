import { PaymentStatus as PaymentStatusType } from '@/types/dashboard';

interface PaymentStatusProps {
  data: PaymentStatusType;
}

export default function PaymentStatus({ data }: PaymentStatusProps) {
  const { pagato, inAttesa, inMora, totaleCondomini, percentualePagato } = data;

  return (
    <div
      className="rounded-xl p-5 shadow-sm"
      style={{ backgroundColor: 'var(--card-bg)', border: '1px solid var(--border)' }}
    >
      <h2 className="text-sm font-semibold mb-4" style={{ color: 'var(--foreground)' }}>
        Stato Pagamenti
      </h2>

      {/* Progress bar */}
      <div className="mb-4">
        <div className="flex justify-between text-xs mb-1" style={{ color: 'var(--text-muted)' }}>
          <span>{percentualePagato}% pagato</span>
          <span>{totaleCondomini} totale condomini</span>
        </div>
        <div className="w-full rounded-full overflow-hidden" style={{ height: '10px', backgroundColor: '#fee2e2' }}>
          <div
            className="h-full rounded-full transition-all"
            style={{ width: `${percentualePagato}%`, backgroundColor: 'var(--primary)' }}
          />
        </div>
      </div>

      {/* Legend */}
      <div className="grid grid-cols-3 gap-3">
        <div className="text-center p-3 rounded-lg" style={{ backgroundColor: '#f0fdf4' }}>
          <p className="text-xl font-bold" style={{ color: '#16a34a' }}>{pagato}</p>
          <p className="text-xs mt-0.5" style={{ color: '#16a34a' }}>Pagati</p>
        </div>
        <div className="text-center p-3 rounded-lg" style={{ backgroundColor: '#fefce8' }}>
          <p className="text-xl font-bold" style={{ color: '#ca8a04' }}>{inAttesa}</p>
          <p className="text-xs mt-0.5" style={{ color: '#ca8a04' }}>In attesa</p>
        </div>
        <div className="text-center p-3 rounded-lg" style={{ backgroundColor: '#fef2f2' }}>
          <p className="text-xl font-bold" style={{ color: 'var(--primary)' }}>{inMora}</p>
          <p className="text-xs mt-0.5" style={{ color: 'var(--primary)' }}>In mora</p>
        </div>
      </div>
    </div>
  );
}
