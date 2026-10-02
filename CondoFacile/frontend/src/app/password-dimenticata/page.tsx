'use client';

import { useState, FormEvent } from 'react';
import Image from 'next/image';
import { requestPasswordReset } from '@/lib/auth';

export default function PasswordDimenticataPage() {
  const [username, setUsername] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const result = await requestPasswordReset(username.trim());
      setSuccess(result.message);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Errore durante la richiesta');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div
      className="min-h-screen flex items-center justify-center"
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
          Password dimenticata
        </h1>
        <p className="text-sm text-center mb-6" style={{ color: 'var(--text-muted)' }}>
          Inserisci il tuo username o la tua email: invieremo una richiesta all&apos;amministratore. Dopo la sua approvazione riceverai un&apos;email con il tuo username e il link per scegliere una nuova password.
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
            <div>
              <label className="block text-sm font-medium mb-1" style={{ color: 'var(--foreground)' }}>
                Username o email
              </label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                autoComplete="username"
                className="w-full px-3 py-2 rounded-lg text-sm outline-none transition"
                style={{
                  border: '1px solid var(--border)',
                  backgroundColor: '#fafafa',
                  color: 'var(--foreground)',
                }}
                placeholder="es. mario.rossi o mario@email.it"
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
              {loading ? 'Invio richiesta…' : 'Invia richiesta'}
            </button>

            <p className="text-sm text-center" style={{ color: 'var(--text-muted)' }}>
              Ricordi la password?{' '}
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
