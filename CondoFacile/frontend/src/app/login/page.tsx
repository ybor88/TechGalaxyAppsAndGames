'use client';

import { useState, FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import Image from 'next/image';
import { useAuth } from '@/context/AuthContext';

export default function LoginPage() {
  const { login } = useAuth();
  const router = useRouter();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await login(username, password);
      router.replace('/dashboard');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Errore di accesso');
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
        {/* Logo */}
        <div className="flex justify-center mb-6">
          <Image src="/logo.jpeg" alt="CondoFacile" width={150} height={56} className="object-contain" priority />
        </div>

        <h1 className="text-xl font-bold text-center mb-1" style={{ color: 'var(--foreground)' }}>
          Accedi a CondoFacile
        </h1>
        <p className="text-sm text-center mb-6" style={{ color: 'var(--text-muted)' }}>
          Inserisci le tue credenziali per continuare
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
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
              style={{
                border: '1px solid var(--border)',
                backgroundColor: '#fafafa',
                color: 'var(--foreground)',
              }}
              placeholder="es. admin"
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
              autoComplete="current-password"
              className="w-full px-3 py-2 rounded-lg text-sm outline-none transition"
              style={{
                border: '1px solid var(--border)',
                backgroundColor: '#fafafa',
                color: 'var(--foreground)',
              }}
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
            {loading ? 'Accesso in corso…' : 'Accedi'}
          </button>
        </form>

        {/* Credenziali demo */}
        <div
          className="mt-6 p-3 rounded-lg text-xs space-y-1"
          style={{ backgroundColor: '#f0fdf4', border: '1px solid #bbf7d0', color: '#166534' }}
        >
          <p className="font-semibold">Credenziali di accesso:</p>
          <p>🔑 Amministratore: <strong>admin</strong> / <strong>admin123</strong></p>
          <p>🏠 Condomino demo: <strong>mario.rossi</strong> / <strong>condo123</strong></p>
        </div>

        <p className="text-center text-xs mt-6" style={{ color: '#ccc' }}>
          © 2026 Roberto Di Flumeri
        </p>
      </div>
    </div>
  );
}
