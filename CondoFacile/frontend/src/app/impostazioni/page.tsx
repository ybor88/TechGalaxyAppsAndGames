'use client';

import { useState } from 'react';
import { Settings, Lock, User, Bell, Shield, CheckCircle, AlertCircle } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:3001/api';

function SectionCard({ title, icon: Icon, children }: { title: string; icon: React.ElementType; children: React.ReactNode }) {
  return (
    <div className="rounded-2xl p-6 shadow-sm" style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0' }}>
      <div className="flex items-center gap-2 mb-5">
        <Icon size={18} style={{ color: '#6366f1' }} />
        <h2 className="text-sm font-semibold" style={{ color: '#1a1a1a' }}>{title}</h2>
      </div>
      {children}
    </div>
  );
}

function Field({
  label,
  type = 'text',
  value,
  onChange,
  placeholder,
}: {
  label: string;
  type?: string;
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
}) {
  return (
    <div className="mb-4">
      <label className="block text-xs font-medium mb-1" style={{ color: '#555' }}>{label}</label>
      <input
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="w-full rounded-lg px-3 py-2 text-sm outline-none"
        style={{
          border: '1px solid #e5e7eb',
          backgroundColor: '#fafafa',
          color: '#1a1a1a',
        }}
      />
    </div>
  );
}

function Toast({ message, type }: { message: string; type: 'success' | 'error' }) {
  return (
    <div
      className="fixed bottom-6 right-6 flex items-center gap-2 rounded-xl px-4 py-3 shadow-lg text-sm z-50"
      style={{
        backgroundColor: type === 'success' ? '#ecfdf5' : '#fef2f2',
        border: `1px solid ${type === 'success' ? '#6ee7b7' : '#fca5a5'}`,
        color: type === 'success' ? '#065f46' : '#991b1b',
      }}
    >
      {type === 'success' ? <CheckCircle size={16} /> : <AlertCircle size={16} />}
      {message}
    </div>
  );
}

export default function ImpostazioniPage() {
  const { user, token } = useAuth();

  // Cambio password
  const [currentPwd, setCurrentPwd] = useState('');
  const [newPwd, setNewPwd] = useState('');
  const [confirmPwd, setConfirmPwd] = useState('');
  const [pwdLoading, setPwdLoading] = useState(false);

  // Toast
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  function showToast(message: string, type: 'success' | 'error') {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3500);
  }

  async function handleChangePassword(e: React.FormEvent) {
    e.preventDefault();
    if (newPwd !== confirmPwd) {
      showToast('Le password non coincidono', 'error');
      return;
    }
    if (newPwd.length < 6) {
      showToast('La nuova password deve avere almeno 6 caratteri', 'error');
      return;
    }
    setPwdLoading(true);
    try {
      const res = await fetch(`${API_URL}/auth/change-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ currentPassword: currentPwd, newPassword: newPwd }),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error((err as { message?: string }).message ?? 'Errore');
      }
      setCurrentPwd('');
      setNewPwd('');
      setConfirmPwd('');
      showToast('Password aggiornata con successo', 'success');
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Errore durante il cambio password', 'error');
    } finally {
      setPwdLoading(false);
    }
  }

  return (
    <div className="flex flex-col flex-1 overflow-y-auto" style={{ backgroundColor: '#f9fafb' }}>
      {/* Header */}
      <div className="px-8 py-6" style={{ backgroundColor: '#fff', borderBottom: '1px solid #f0f0f0' }}>
        <div className="flex items-center gap-3">
          <Settings size={22} style={{ color: '#6366f1' }} />
          <div>
            <h1 className="text-base font-bold" style={{ color: '#1a1a1a' }}>Impostazioni</h1>
            <p className="text-xs" style={{ color: '#888' }}>Gestisci il tuo account e le preferenze</p>
          </div>
        </div>
      </div>

      <div className="p-8 grid gap-6 max-w-2xl">
        {/* Profilo */}
        <SectionCard title="Informazioni account" icon={User}>
          <div className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <span className="block text-xs font-medium mb-1" style={{ color: '#888' }}>Username</span>
              <span className="font-medium" style={{ color: '#1a1a1a' }}>{user?.username ?? '—'}</span>
            </div>
            <div>
              <span className="block text-xs font-medium mb-1" style={{ color: '#888' }}>Ruolo</span>
              <span
                className="inline-block rounded-full px-3 py-0.5 text-xs font-semibold"
                style={{
                  backgroundColor: user?.role === 'AMMINISTRATORE' ? '#ede9fe' : '#ecfdf5',
                  color: user?.role === 'AMMINISTRATORE' ? '#5b21b6' : '#065f46',
                }}
              >
                {user?.role === 'AMMINISTRATORE' ? 'Amministratore' : 'Condomino'}
              </span>
            </div>
          </div>
        </SectionCard>

        {/* Cambio password */}
        <SectionCard title="Cambia password" icon={Lock}>
          <form onSubmit={handleChangePassword}>
            <Field
              label="Password attuale"
              type="password"
              value={currentPwd}
              onChange={setCurrentPwd}
              placeholder="••••••••"
            />
            <Field
              label="Nuova password"
              type="password"
              value={newPwd}
              onChange={setNewPwd}
              placeholder="••••••••"
            />
            <Field
              label="Conferma nuova password"
              type="password"
              value={confirmPwd}
              onChange={setConfirmPwd}
              placeholder="••••••••"
            />
            <button
              type="submit"
              disabled={pwdLoading || !currentPwd || !newPwd || !confirmPwd}
              className="mt-2 rounded-lg px-5 py-2 text-sm font-semibold transition-opacity"
              style={{
                backgroundColor: '#6366f1',
                color: '#fff',
                opacity: pwdLoading || !currentPwd || !newPwd || !confirmPwd ? 0.6 : 1,
                cursor: pwdLoading || !currentPwd || !newPwd || !confirmPwd ? 'not-allowed' : 'pointer',
              }}
            >
              {pwdLoading ? 'Salvataggio…' : 'Aggiorna password'}
            </button>
          </form>
        </SectionCard>

        {/* Sicurezza info */}
        <SectionCard title="Sicurezza" icon={Shield}>
          <div className="text-sm" style={{ color: '#555' }}>
            <div className="flex items-start gap-2 mb-3">
              <CheckCircle size={15} style={{ color: '#10b981', flexShrink: 0, marginTop: 1 }} />
              <span>Le password sono cifrate con algoritmo bcrypt</span>
            </div>
            <div className="flex items-start gap-2 mb-3">
              <CheckCircle size={15} style={{ color: '#10b981', flexShrink: 0, marginTop: 1 }} />
              <span>Le sessioni scadono automaticamente dopo 8 ore</span>
            </div>
            <div className="flex items-start gap-2">
              <Bell size={15} style={{ color: '#6366f1', flexShrink: 0, marginTop: 1 }} />
              <span>In caso di accesso non autorizzato, effettua subito il logout e cambia la password</span>
            </div>
          </div>
        </SectionCard>
      </div>

      {toast && <Toast message={toast.message} type={toast.type} />}
    </div>
  );
}
