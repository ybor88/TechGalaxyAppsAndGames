import { Users, AlertCircle, Euro, HardHat } from 'lucide-react';
import { DashboardData } from '@/types/dashboard';

interface StatsCardsProps {
  data: DashboardData;
}

interface StatCardProps {
  label: string;
  value: string | number;
  sublabel: string;
  icon: React.ReactNode;
  accentColor: string;
}

function StatCard({ label, value, sublabel, icon, accentColor }: StatCardProps) {
  return (
    <div
      className="rounded-xl p-5 flex items-start justify-between shadow-sm"
      style={{ backgroundColor: 'var(--card-bg)', border: '1px solid var(--border)' }}
    >
      <div>
        <p className="text-sm font-medium" style={{ color: 'var(--text-muted)' }}>
          {label}
        </p>
        <p className="text-3xl font-bold mt-1" style={{ color: 'var(--foreground)' }}>
          {value}
        </p>
        <p className="text-xs mt-1" style={{ color: 'var(--text-muted)' }}>
          {sublabel}
        </p>
      </div>
      <div
        className="w-11 h-11 rounded-xl flex items-center justify-center"
        style={{ backgroundColor: `${accentColor}18`, color: accentColor }}
      >
        {icon}
      </div>
    </div>
  );
}

export default function StatsCards({ data }: StatsCardsProps) {
  const speseCorrente = data.speseUltimiMesi[data.speseUltimiMesi.length - 1];

  return (
    <div className="grid grid-cols-4 gap-4">
      <StatCard
        label="Condòmini paganti"
        value={`${data.statoPagementi.pagato}/${data.statoPagementi.totaleCondomini}`}
        sublabel={`${data.statoPagementi.inMora} in mora`}
        icon={<Users size={20} />}
        accentColor="var(--primary)"
      />
      <StatCard
        label="Segnalazioni aperte"
        value={data.segnalazioniAperte.length}
        sublabel={`${data.segnalazioniAperte.filter((t) => t.priorita === 'alta').length} ad alta priorità`}
        icon={<AlertCircle size={20} />}
        accentColor="#f59e0b"
      />
      <StatCard
        label="Spese mese corrente"
        value={`€${speseCorrente?.importo.toLocaleString('it-IT') ?? 0}`}
        sublabel={speseCorrente?.mese ?? ''}
        icon={<Euro size={20} />}
        accentColor="#10b981"
      />
      <StatCard
        label="Lavori in corso"
        value={data.lavoriInCorso.length}
        sublabel="Interventi attivi"
        icon={<HardHat size={20} />}
        accentColor="#6366f1"
      />
    </div>
  );
}
