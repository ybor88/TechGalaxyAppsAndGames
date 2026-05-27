import { Building2, Plus } from 'lucide-react';
import Link from 'next/link';

export default function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center h-full min-h-[60vh] text-center px-6">
      <div
        className="w-20 h-20 rounded-2xl flex items-center justify-center mb-6"
        style={{ backgroundColor: '#fef2f2' }}
      >
        <Building2 size={36} style={{ color: 'var(--primary)' }} />
      </div>

      <h2 className="text-xl font-bold mb-2" style={{ color: 'var(--foreground)' }}>
        Nessun condominio configurato
      </h2>
      <p className="text-sm max-w-sm mb-8" style={{ color: 'var(--text-muted)' }}>
        Per iniziare ad usare CondoFacile, aggiungi il tuo primo condominio.
        I dati della dashboard appariranno automaticamente.
      </p>

      <Link
        href="/anagrafica"
        className="flex items-center gap-2 px-5 py-2.5 rounded-lg text-sm font-semibold text-white transition-opacity hover:opacity-90"
        style={{ backgroundColor: 'var(--primary)' }}
      >
        <Plus size={16} />
        Aggiungi condominio
      </Link>

      <p className="text-xs mt-10" style={{ color: 'rgba(0,0,0,0.25)' }}>
        © 2026 Roberto Di Flumeri · CondoFacile
      </p>
    </div>
  );
}
