import { CondominoDashboardData } from '@/types/dashboard';
import { CheckCircle2, Clock, AlertCircle, Wrench, Calendar } from 'lucide-react';
import ComunicazioniFilter from './ComunicazioniFilter';

const mesiNomi = ['Gennaio','Febbraio','Marzo','Aprile','Maggio','Giugno',
  'Luglio','Agosto','Settembre','Ottobre','Novembre','Dicembre'];

const prioritaConfig = {
  alta:  { bg: '#fef2f2', color: '#dc2626', label: 'Alta' },
  media: { bg: '#fff7ed', color: '#ea580c', label: 'Media' },
  bassa: { bg: '#f0fdf4', color: '#16a34a', label: 'Bassa' },
};

export default function CondominoDashboard({ data }: { data: CondominoDashboardData }) {
  const { quotaCorrente, comunicazioniRecenti, segnalazioniAperte, scadenzeImminenti } = data;

  // ── Quota corrente ─────────────────────────────────────────────────────────
  const quotaConfig = {
    pagata:    { bg: '#f0fdf4', border: '#86efac', color: '#16a34a', icon: <CheckCircle2 size={28} />, label: 'Pagata' },
    in_attesa: { bg: '#fff7ed', border: '#fed7aa', color: '#ea580c', icon: <Clock size={28} />,        label: 'In attesa' },
    in_mora:   { bg: '#fef2f2', border: '#fca5a5', color: '#dc2626', icon: <AlertCircle size={28} />, label: 'In mora' },
  };
  const qc = quotaCorrente ? quotaConfig[quotaCorrente.stato] : null;

  return (
    <div className="flex-1 p-6 overflow-auto" style={{ backgroundColor: 'var(--background)' }}>

      {/* ── Benvenuto ──────────────────────────────────────────────────────── */}
      <div className="mb-5">
        <h2 className="text-lg font-bold" style={{ color: '#1a1a1a' }}>
          Benvenuto, {data.nomeCondomino} 👋
        </h2>
        <p className="text-xs mt-0.5" style={{ color: '#888' }}>
          Unità {data.unita} · {data.nomeCondominio}
        </p>
      </div>

      {/* ── Grid principale ─────────────────────────────────────────────────── */}
      <div className="grid grid-cols-2 gap-4">

        {/* QUOTA CORRENTE */}
        <div
          className="rounded-xl p-5 shadow-sm"
          style={{ backgroundColor: 'var(--card-bg)', border: '1px solid var(--border)' }}
        >
          <h3 className="text-sm font-semibold mb-4" style={{ color: 'var(--foreground)' }}>
            Quota Corrente
          </h3>

          {quotaCorrente && qc ? (
            <div
              className="flex items-center gap-4 rounded-xl p-4"
              style={{ backgroundColor: qc.bg, border: `1.5px solid ${qc.border}` }}
            >
              <div style={{ color: qc.color }}>{qc.icon}</div>
              <div>
                <p className="text-base font-bold" style={{ color: qc.color }}>
                  {qc.label}
                </p>
                <p className="text-sm font-semibold mt-0.5" style={{ color: '#1a1a1a' }}>
                  € {quotaCorrente.importo.toFixed(2)}
                </p>
                <p className="text-xs mt-0.5" style={{ color: '#888' }}>
                  {mesiNomi[quotaCorrente.mese - 1]} {quotaCorrente.anno}
                  {quotaCorrente.dataPagamento && (
                    <> · pagata il {new Date(quotaCorrente.dataPagamento).toLocaleDateString('it-IT')}</>
                  )}
                </p>
              </div>
            </div>
          ) : (
            <div className="text-center py-6">
              <p className="text-sm" style={{ color: '#aaa' }}>Nessuna quota registrata</p>
            </div>
          )}
        </div>

        {/* SCADENZE – PROSSIMI 30 GIORNI */}
        <div
          className="rounded-xl p-5 shadow-sm"
          style={{ backgroundColor: 'var(--card-bg)', border: '1px solid var(--border)' }}
        >
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-semibold" style={{ color: 'var(--foreground)' }}>
              Scadenze
            </h3>
            <span
              className="text-xs px-2 py-0.5 rounded-full font-semibold"
              style={{ backgroundColor: '#fef2f2', color: 'var(--primary)' }}
            >
              Prossimi 30 giorni
            </span>
          </div>

          {scadenzeImminenti.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-6" style={{ color: '#ccc' }}>
              <Calendar size={28} style={{ marginBottom: 6 }} />
              <p className="text-xs" style={{ color: '#aaa' }}>Nessuna scadenza nei prossimi 30 giorni</p>
            </div>
          ) : (
            <ul className="space-y-2">
              {scadenzeImminenti.map((s) => (
                <li key={s.id} className="flex items-center gap-3">
                  <div
                    className="w-9 h-9 rounded-lg flex flex-col items-center justify-center flex-shrink-0"
                    style={{ backgroundColor: s.giorniMancanti <= 7 ? '#fef2f2' : '#f5f5f5' }}
                  >
                    <span className="text-xs font-bold leading-none"
                      style={{ color: s.giorniMancanti <= 7 ? '#dc2626' : '#888' }}>
                      {s.giorniMancanti}
                    </span>
                    <span className="text-xs leading-none"
                      style={{ color: s.giorniMancanti <= 7 ? '#dc2626' : '#aaa' }}>gg</span>
                  </div>
                  <div className="min-w-0">
                    <p className="text-sm font-medium truncate" style={{ color: '#1a1a1a' }}>{s.descrizione}</p>
                    <p className="text-xs" style={{ color: '#aaa' }}>
                      {new Date(s.data).toLocaleDateString('it-IT')} · {s.tipo}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* COMUNICAZIONI – storico filtrabile, larghezza piena */}
        <ComunicazioniFilter comunicazioni={comunicazioniRecenti} />

        {/* SEGNALAZIONI APERTE */}
        <div
          className="rounded-xl p-5 shadow-sm"
          style={{ backgroundColor: 'var(--card-bg)', border: '1px solid var(--border)' }}
        >
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-semibold" style={{ color: 'var(--foreground)' }}>
              Segnalazioni Aperte
            </h3>
            <Wrench size={15} style={{ color: 'var(--text-muted)' }} />
          </div>

          {segnalazioniAperte.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-6" style={{ color: '#ccc' }}>
              <CheckCircle2 size={28} style={{ marginBottom: 6, color: '#86efac' }} />
              <p className="text-xs" style={{ color: '#aaa' }}>Nessuna segnalazione aperta</p>
            </div>
          ) : (
            <ul className="space-y-2.5">
              {segnalazioniAperte.map((t) => {
                const pc = prioritaConfig[t.priorita] ?? prioritaConfig.media;
                return (
                  <li key={t.id} className="flex items-start gap-3">
                    <span
                      className="text-xs px-1.5 py-0.5 rounded font-semibold flex-shrink-0 mt-0.5"
                      style={{ backgroundColor: pc.bg, color: pc.color }}
                    >
                      {pc.label}
                    </span>
                    <div className="min-w-0">
                      <p className="text-sm font-medium truncate" style={{ color: '#1a1a1a' }}>{t.titolo}</p>
                      <p className="text-xs" style={{ color: '#aaa' }}>{t.stato} · {t.categoria}</p>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

      </div>
    </div>
  );
}
