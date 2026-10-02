'use client';

import { useState, FormEvent } from 'react';
import Image from 'next/image';
import { registerRequest } from '@/lib/auth';

export default function RegistratiPage() {
  const [nome, setNome] = useState('');
  const [cognome, setCognome] = useState('');
  const [email, setEmail] = useState('');
  const [telefono, setTelefono] = useState('');
  const [unitaRichiesta, setUnitaRichiesta] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');

    if (!/^[a-z0-9._%+-]+@gmail\.com$/i.test(email.trim())) {
      setError('Inserisci un indirizzo Gmail valido (es. mario.rossi@gmail.com)');
      return;
    }
    if (password.length < 6) {
      setError('La password deve avere almeno 6 caratteri');
      return;
    }
    if (password !== confirmPassword) {
      setError('Le password non coincidono');
      return;
    }

    setLoading(true);
    try {
      const result = await registerRequest({
        nome,
        cognome,
        email: email.trim(),
        telefono: telefono || undefined,
        unitaRichiesta,
        username,
        password,
      });
      setSuccess(result.message);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Errore durante la registrazione');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div
      className="min-h-screen flex items-center justify-center py-10"
      style={{ backgroundColor: 'var(--background)' }}
    >
      <div
        className="w-full max-w-sm rounded-2xl shadow-lg p-8"
        style={{ backgroundColor: 'var(--card-bg)', border: '1px solid var(--border)' }}
      >
        <div className="flex justify-center mb-6">
          <Image src="/logo.jpeg" alt="CondoFacile" width={150} height={56} className="object-contain" priority />
        </div>

        <h1 className="text-xl font-bold text-center mb-1" style={{ color: 'var(--foreground)' }}>
          Registrati a CondoFacile
        </h1>
        <p className="text-sm text-center mb-6" style={{ color: 'var(--text-muted)' }}>
          Richiedi l&apos;accesso come condòmino
        </p>

        {success ? (
          <div className="flex flex-col gap-4">
            <div
              className="text-sm px-3 py-3 rounded-lg"
              style={{ backgroundColor: '#f0fdf4', color: '#16a34a', border: '1px solid #86efac' }}
            >
              {success}
            </div>
            <a
              href="/login"
              className="w-full text-center py-2.5 rounded-lg text-sm font-semibold transition"
              style={{ backgroundColor: 'var(--primary)', color: '#fff' }}
            >
              Torna al login
            </a>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-sm font-medium mb-1" style={{ color: 'var(--foreground)' }}>
                  Nome
                </label>
                <input
                  type="text"
                  value={nome}
                  onChange={(e) => setNome(e.target.value)}
                  required
                  className="w-full px-3 py-2 rounded-lg text-sm outline-none transition"
                  style={{ border: '1px solid var(--border)', backgroundColor: '#fafafa', color: 'var(--foreground)' }}
                  placeholder="Mario"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1" style={{ color: 'var(--foreground)' }}>
                  Cognome
                </label>
                <input
                  type="text"
                  value={cognome}
                  onChange={(e) => setCognome(e.target.value)}
                  required
                  className="w-full px-3 py-2 rounded-lg text-sm outline-none transition"
                  style={{ border: '1px solid var(--border)', backgroundColor: '#fafafa', color: 'var(--foreground)' }}
                  placeholder="Rossi"
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium mb-1" style={{ color: 'var(--foreground)' }}>
                Unità abitativa
              </label>
              <input
                type="text"
                value={unitaRichiesta}
                onChange={(e) => setUnitaRichiesta(e.target.value)}
                required
                className="w-full px-3 py-2 rounded-lg text-sm outline-none transition"
                style={{ border: '1px solid var(--border)', backgroundColor: '#fafafa', color: 'var(--foreground)' }}
                placeholder="es. A1"
              />
            </div>

            <div>
              <label className="block text-sm font-medium mb-1" style={{ color: 'var(--foreground)' }}>
                Email Gmail *
              </label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                pattern="[A-Za-z0-9._%+\-]+@gmail\.com"
                title="Inserisci un indirizzo che termina con @gmail.com"
                className="w-full px-3 py-2 rounded-lg text-sm outline-none transition"
                style={{ border: '1px solid var(--border)', backgroundColor: '#fafafa', color: 'var(--foreground)' }}
                placeholder="mario.rossi@gmail.com"
              />
              <p className="text-xs mt-1" style={{ color: 'var(--text-muted)' }}>
                È richiesto un indirizzo Gmail: serve per ricevere il recupero credenziali.
              </p>
            </div>

            <div>
              <label className="block text-sm font-medium mb-1" style={{ color: 'var(--foreground)' }}>
                Telefono
              </label>
              <input
                type="text"
                value={telefono}
                onChange={(e) => setTelefono(e.target.value)}
                className="w-full px-3 py-2 rounded-lg text-sm outline-none transition"
                style={{ border: '1px solid var(--border)', backgroundColor: '#fafafa', color: 'var(--foreground)' }}
                placeholder="+39 333 0000000"
              />
            </div>

            <div className="flex items-center gap-2 mt-1">
              <div style={{ flex: 1, height: 1, backgroundColor: '#f0f0f0' }} />
              <span className="text-xs font-semibold uppercase tracking-wider" style={{ color: '#bbb' }}>Credenziali</span>
              <div style={{ flex: 1, height: 1, backgroundColor: '#f0f0f0' }} />
            </div>

            <div>
              <label className="block text-sm font-medium mb-1" style={{ color: 'var(--foreground)' }}>
                Username
              </label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                autoComplete="username"
                className="w-full px-3 py-2 rounded-lg text-sm outline-none transition"
                style={{ border: '1px solid var(--border)', backgroundColor: '#fafafa', color: 'var(--foreground)' }}
                placeholder="es. mario.rossi"
              />
            </div>

            <div>
              <label className="block text-sm font-medium mb-1" style={{ color: 'var(--foreground)' }}>
                Password
              </label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                autoComplete="new-password"
                className="w-full px-3 py-2 rounded-lg text-sm outline-none transition"
                style={{ border: '1px solid var(--border)', backgroundColor: '#fafafa', color: 'var(--foreground)' }}
                placeholder="Almeno 6 caratteri"
              />
            </div>

            <div>
              <label className="block text-sm font-medium mb-1" style={{ color: 'var(--foreground)' }}>
                Conferma password
              </label>
              <input
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                required
                autoComplete="new-password"
                className="w-full px-3 py-2 rounded-lg text-sm outline-none transition"
                style={{ border: '1px solid var(--border)', backgroundColor: '#fafafa', color: 'var(--foreground)' }}
                placeholder="••••••••"
              />
            </div>

            {error && (
              <div
                className="text-sm px-3 py-2 rounded-lg"
                style={{ backgroundColor: '#fef2f2', color: 'var(--primary)', border: '1px solid #fca5a5' }}
              >
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full py-2.5 rounded-lg text-sm font-semibold transition"
              style={{
                backgroundColor: loading ? '#e0a0a0' : 'var(--primary)',
                color: '#fff',
                cursor: loading ? 'not-allowed' : 'pointer',
              }}
            >
              {loading ? 'Invio richiesta…' : 'Invia richiesta di registrazione'}
            </button>

            <p className="text-sm text-center" style={{ color: 'var(--text-muted)' }}>
              Hai già un account?{' '}
              <a href="/login" className="font-semibold" style={{ color: 'var(--primary)' }}>
                Accedi
              </a>
            </p>
          </form>
        )}

        <p className="text-center text-xs mt-6" style={{ color: '#ccc' }}>
          © 2026 Roberto Di Flumeri
        </p>
      </div>
    </div>
  );
}
