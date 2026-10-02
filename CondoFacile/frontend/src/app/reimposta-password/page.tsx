'use client';

// © Roberto Di Flumeri
import { useState, FormEvent, Suspense } from 'react';
import Image from 'next/image';
import { useSearchParams } from 'next/navigation';
import { confirmPasswordReset } from '@/lib/auth';

const inputStyle = {
  border: '1px solid var(--border)',
  backgroundColor: '#fafafa',
  color: 'var(--foreground)',
};

function ReimpostaPasswordForm() {
  const token = useSearchParams().get('token') ?? '';
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState<{ message: string; username: string } | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
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
      setSuccess(await confirmPasswordReset(token, password));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Errore durante il reset della password');
    } finally {
      setLoading(false);
    }
  }

  if (!token) {
    return (
      <div className="flex flex-col gap-4">
        <div
          className="text-sm px-3 py-3 rounded-lg"
          style={{ backgroundColor: '#fef2f2', color: 'var(--primary)', border: '1px solid #fca5a5' }}
        >
          Link non valido. Apri il link ricevuto via email oppure richiedi di nuovo il recupero credenziali.
        </div>
        <a
          href="/password-dimenticata"
          className="w-full text-center py-2.5 rounded-lg text-sm font-semibold"
          style={{ backgroundColor: 'var(--primary)', color: '#fff' }}
        >
          Recupera credenziali
        </a>
      </div>
    );
  }

  if (success) {
    return (
      <div className="flex flex-col gap-4">
        <div
          className="text-sm px-3 py-3 rounded-lg"
          style={{ backgroundColor: '#f0fdf4', color: '#16a34a', border: '1px solid #86efac' }}
        >
          {success.message}
          <br />
          Il tuo username è <strong>{success.username}</strong>.
        </div>
        <a
          href="/login"
          className="w-full text-center py-2.5 rounded-lg text-sm font-semibold"
          style={{ backgroundColor: 'var(--primary)', color: '#fff' }}
        >
          Vai al login
        </a>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label className="block text-sm font-medium mb-1" style={{ color: 'var(--foreground)' }}>
          Nuova password
        </label>
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          autoComplete="new-password"
          className="w-full px-3 py-2 rounded-lg text-sm outline-none transition"
          style={inputStyle}
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
          style={inputStyle}
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
        {loading ? 'Salvataggio…' : 'Salva nuova password'}
      </button>
    </form>
  );
}

export default function ReimpostaPasswordPage() {
  return (
    <div className="min-h-screen flex items-center justify-center" style={{ backgroundColor: 'var(--background)' }}>
      <div
        className="w-full max-w-sm rounded-2xl shadow-lg p-8"
        style={{ backgroundColor: 'var(--card-bg)', border: '1px solid var(--border)' }}
      >
        <div className="flex justify-center mb-6">
          <Image src="/logo.jpeg" alt="CondoFacile" width={150} height={56} className="object-contain" priority />
        </div>

        <h1 className="text-xl font-bold text-center mb-1" style={{ color: 'var(--foreground)' }}>
          Imposta una nuova password
        </h1>
        <p className="text-sm text-center mb-6" style={{ color: 'var(--text-muted)' }}>
          Scegli la nuova password per accedere a CondoFacile.
        </p>

        <Suspense fallback={<p className="text-sm text-center" style={{ color: '#aaa' }}>Caricamento…</p>}>
          <ReimpostaPasswordForm />
        </Suspense>

        <p className="text-center text-xs mt-6" style={{ color: '#ccc' }}>
          © 2026 Roberto Di Flumeri
        </p>
      </div>
    </div>
  );
}
