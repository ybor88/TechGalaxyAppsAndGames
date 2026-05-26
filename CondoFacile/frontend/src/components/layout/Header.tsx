import { RefreshCw, User } from 'lucide-react';

interface HeaderProps {
  nomeCondominio: string;
  aggiornato: string;
}

export default function Header({ nomeCondominio, aggiornato }: HeaderProps) {
  const oraAggiornamento = new Date(aggiornato).toLocaleTimeString('it-IT', {
    hour: '2-digit',
    minute: '2-digit',
  });

  return (
    <header
      className="flex items-center justify-between px-6 py-3 border-b"
      style={{ backgroundColor: '#fff', borderColor: 'var(--border)' }}
    >
      <div className="flex items-center gap-4">
        <div>
          <h1 className="text-sm font-semibold leading-tight" style={{ color: 'var(--foreground)' }}>
            Dashboard
          </h1>
          <p className="text-xs" style={{ color: 'var(--text-muted)' }}>
            {nomeCondominio}
          </p>
        </div>
      </div>

      <div className="flex items-center gap-4">
        <div className="flex items-center gap-1.5 text-xs" style={{ color: 'var(--text-muted)' }}>
          <RefreshCw size={12} />
          <span>Aggiornato alle {oraAggiornamento}</span>
        </div>

        <div
          className="w-9 h-9 rounded-full flex items-center justify-center"
          style={{ backgroundColor: 'var(--primary)', color: '#fff' }}
        >
          <User size={16} />
        </div>
      </div>
    </header>
  );
}
