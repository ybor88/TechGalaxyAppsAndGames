'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  Inbox, UserPlus, KeyRound, Building2, Mail, Phone, Clock, Eye, EyeOff, X, Check,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import {
  fetchUsersNonAssociati,
  fetchRichiesteResetPassword,
  resetPasswordCondomino,
  approvaResetPassword,
  approvaRegistrazione,
  notificaRichiesteAggiornate,
  rifiutaRegistrazione,
  fetchCondominii,
  CondominioListItem,
  UnassociatedUser,
  RichiestaResetPasswordItem,
} from '@/lib/api';

function formatData(iso: string | null): string {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString('it-IT', { day: '2-digit', month: 'short', year: 'numeric' });
}

export default function RichiestePage() {
  const { token } = useAuth();

  const [registrazioni, setRegistrazioni] = useState<UnassociatedUser[]>([]);
  const [resetRichieste, setResetRichieste] = useState<RichiestaResetPasswordItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Modal reimposta password
  const [resetTarget, setResetTarget] = useState<RichiestaResetPasswordItem | null>(null);
  const [newPassword, setNewPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [saving, setSaving] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);

  // Approvazione registrazione
  const [condominii, setCondominii] = useState<CondominioListItem[]>([]);
  const [regTarget, setRegTarget] = useState<UnassociatedUser | null>(null);
  const [regForm, setRegForm] = useState({ condominioId: '', unita: '', millesimi: '', tipo: 'proprietario' });
  const [regSaving, setRegSaving] = useState(false);
  const [regError, setRegError] = useState<string | null>(null);
  const [regMsg, setRegMsg] = useState<{ ok: boolean; text: string } | null>(null);

  // Approvazione con invio email di recupero
  const [approvingId, setApprovingId] = useState<number | null>(null);
  const [approveMsg, setApproveMsg] = useState<{ ok: boolean; text: string } | null>(null);

  const load = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError(null);
    try {
      const [reg, reset, condos] = await Promise.all([
        fetchUsersNonAssociati(token),
        fetchRichiesteResetPassword(token),
        fetchCondominii(token),
      ]);
      setRegistrazioni(reg);
      setResetRichieste(reset);
      setCondominii(condos);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => { load(); }, [load]);

  const openResetModal = (r: RichiestaResetPasswordItem) => {
    setResetTarget(r);
    setNewPassword('');
    setShowPassword(false);
    setModalError(null);
  };

  const handleResetPassword = async () => {
    if (!token || !resetTarget?.condomino) return;
    if (newPassword.length < 6) {
      setModalError('La password deve avere almeno 6 caratteri');
      return;
    }
    setSaving(true);
    setModalError(null);
    try {
      await resetPasswordCondomino(token, resetTarget.condomino.condominioId, resetTarget.condomino.id, newPassword);
      setResetTarget(null);
      setResetRichieste((prev) => prev.filter((r) => r.id !== resetTarget.id));
      notificaRichiesteAggiornate();
    } catch (e) {
      setModalError((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const openRegModal = (u: UnassociatedUser) => {
    setRegTarget(u);
    setRegForm({
      condominioId: condominii.length === 1 ? String(condominii[0].id) : '',
      unita: u.unitaRichiesta ?? '',
      millesimi: '',
      tipo: 'proprietario',
    });
    setRegError(null);
  };

  const handleApprovaRegistrazione = async () => {
    if (!token || !regTarget) return;
    if (!regForm.condominioId || !regForm.unita.trim()) {
      setRegError('Seleziona il condominio e indica l\'unità');
      return;
    }
    setRegSaving(true);
    setRegError(null);
    try {
      const res = await approvaRegistrazione(token, regTarget.id, {
        condominioId: Number(regForm.condominioId),
        unita: regForm.unita.trim(),
        millesimi: regForm.millesimi ? Number(regForm.millesimi) : undefined,
        tipo: regForm.tipo,
      });
      setRegMsg({ ok: true, text: res.message });
      setRegistrazioni((prev) => prev.filter((x) => x.id !== regTarget.id));
      notificaRichiesteAggiornate();
      setRegTarget(null);
    } catch (e) {
      setRegError((e as Error).message);
    } finally {
      setRegSaving(false);
    }
  };

  const handleRifiutaRegistrazione = async (u: UnassociatedUser) => {
    if (!token) return;
    if (!window.confirm(`Rifiutare la registrazione di @${u.username}? L'account verrà eliminato.`)) return;
    try {
      const res = await rifiutaRegistrazione(token, u.id);
      setRegMsg({ ok: true, text: res.message });
      setRegistrazioni((prev) => prev.filter((x) => x.id !== u.id));
      notificaRichiesteAggiornate();
    } catch (e) {
      setRegMsg({ ok: false, text: (e as Error).message });
    }
  };

  const handleApprova = async (r: RichiestaResetPasswordItem) => {
    if (!token) return;
    setApprovingId(r.id);
    setApproveMsg(null);
    try {
      const res = await approvaResetPassword(token, r.id);
      setApproveMsg({ ok: true, text: res.message });
      setResetRichieste((prev) => prev.filter((x) => x.id !== r.id));
      notificaRichiesteAggiornate();
    } catch (e) {
      setApproveMsg({ ok: false, text: (e as Error).message });
    } finally {
      setApprovingId(null);
    }
  };

  const totale = registrazioni.length + resetRichieste.length;

  return (
    <div className="flex flex-col flex-1 overflow-hidden" style={{ backgroundColor: 'var(--background)' }}>
      <header className="flex items-center justify-between px-6 py-3 border-b flex-shrink-0" style={{ backgroundColor: '#fff', borderColor: '#f0f0f0' }}>
        <div>
          <h1 className="text-sm font-semibold flex items-center gap-2" style={{ color: '#1a1a1a' }}>
            <Inbox size={16} /> Richieste
          </h1>
          <p className="text-xs" style={{ color: '#888' }}>
            Registrazioni e recuperi password in attesa · {totale} in sospeso
          </p>
        </div>
      </header>

      <div className="flex-1 overflow-y-auto p-6">
        {loading && <p className="text-sm text-center mt-8" style={{ color: '#aaa' }}>Caricamento...</p>}
        {error && <p className="text-xs text-center mt-4" style={{ color: 'var(--primary)' }}>{error}</p>}

        {!loading && !error && (
          <div className="flex flex-col gap-8" style={{ maxWidth: 820 }}>
            {/* Richieste di registrazione */}
            <section>
              <h2 className="text-sm font-bold mb-1 flex items-center gap-2" style={{ color: '#1a1a1a' }}>
                <UserPlus size={15} /> Richieste di registrazione ({registrazioni.length})
              </h2>
              <p className="text-xs mb-3" style={{ color: '#888' }}>
                Nuovi condòmini in attesa di approvazione. Approvando scegli condominio e unità: l&apos;account si attiva e il condòmino accede con username e password scelti in fase di registrazione.
              </p>
              {regMsg && (
                <p
                  className="text-xs mb-3 px-3 py-2 rounded-lg"
                  style={regMsg.ok
                    ? { backgroundColor: '#f0fdf4', color: '#16a34a', border: '1px solid #86efac' }
                    : { backgroundColor: '#fef2f2', color: 'var(--primary)', border: '1px solid #fca5a5' }}
                >
                  {regMsg.text}
                </p>
              )}
              {registrazioni.length === 0 ? (
                <p className="text-xs py-4 text-center rounded-xl" style={{ color: '#bbb', backgroundColor: '#fafafa', border: '1px solid #f0f0f0' }}>
                  Nessuna richiesta di registrazione in attesa
                </p>
              ) : (
                <div className="grid grid-cols-1 gap-3">
                  {registrazioni.map((u) => (
                    <div key={u.id} className="flex items-start gap-4 rounded-xl p-4" style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0' }}>
                      <div className="flex-shrink-0 w-10 h-10 rounded-full flex items-center justify-center text-sm font-bold" style={{ backgroundColor: '#eff6ff', color: '#2563eb' }}>
                        {(u.nome?.[0] ?? u.username[0]).toUpperCase()}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="text-sm font-semibold" style={{ color: '#1a1a1a' }}>
                            {u.nome && u.cognome ? `${u.nome} ${u.cognome}` : u.username}
                          </span>
                          <span className="text-xs px-2 py-0.5 rounded-full font-semibold" style={{ backgroundColor: '#eff6ff', color: '#2563eb' }}>@{u.username}</span>
                          {u.unitaRichiesta && (
                            <span className="text-xs px-2 py-0.5 rounded-full font-semibold" style={{ backgroundColor: '#f0fdf4', color: '#16a34a' }}>Unità richiesta: {u.unitaRichiesta}</span>
                          )}
                        </div>
                        <div className="flex items-center gap-4 mt-1.5 flex-wrap">
                          {u.email && <span className="flex items-center gap-1 text-xs" style={{ color: '#888' }}><Mail size={11} />{u.email}</span>}
                          {u.telefono && <span className="flex items-center gap-1 text-xs" style={{ color: '#888' }}><Phone size={11} />{u.telefono}</span>}
                        </div>
                      </div>
                      <div className="flex flex-col gap-1.5 flex-shrink-0">
                        <button
                          onClick={() => openRegModal(u)}
                          className="flex items-center justify-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold text-white hover:opacity-90"
                          style={{ backgroundColor: 'var(--primary)' }}
                        >
                          <Check size={12} />Approva
                        </button>
                        <button
                          onClick={() => handleRifiutaRegistrazione(u)}
                          className="px-3 py-1.5 rounded-lg text-xs font-semibold hover:bg-gray-100"
                          style={{ color: '#555', border: '1px solid #e5e5e5' }}
                        >
                          Rifiuta
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </section>

            {/* Richieste di reset password */}
            <section>
              <h2 className="text-sm font-bold mb-1 flex items-center gap-2" style={{ color: '#1a1a1a' }}>
                <KeyRound size={15} /> Richieste di reset password ({resetRichieste.length})
              </h2>
              <p className="text-xs mb-3" style={{ color: '#888' }}>
                Condòmini che hanno dimenticato le credenziali. Approvando, il condòmino riceve un&apos;email con il suo username e un link (valido 1 ora) per scegliere una nuova password. In alternativa puoi impostarla tu manualmente.
              </p>
              {approveMsg && (
                <p
                  className="text-xs mb-3 px-3 py-2 rounded-lg"
                  style={approveMsg.ok
                    ? { backgroundColor: '#f0fdf4', color: '#16a34a', border: '1px solid #86efac' }
                    : { backgroundColor: '#fef2f2', color: 'var(--primary)', border: '1px solid #fca5a5' }}
                >
                  {approveMsg.text}
                </p>
              )}
              {resetRichieste.length === 0 ? (
                <p className="text-xs py-4 text-center rounded-xl" style={{ color: '#bbb', backgroundColor: '#fafafa', border: '1px solid #f0f0f0' }}>
                  Nessuna richiesta di reset password in attesa
                </p>
              ) : (
                <div className="grid grid-cols-1 gap-3">
                  {resetRichieste.map((r) => (
                    <div key={r.id} className="flex items-start gap-4 rounded-xl p-4" style={{ backgroundColor: '#fff7ed', border: '1px solid #fed7aa' }}>
                      <div className="flex-shrink-0 w-10 h-10 rounded-full flex items-center justify-center text-sm font-bold" style={{ backgroundColor: '#ffedd5', color: '#ea580c' }}>
                        <KeyRound size={16} />
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="text-sm font-semibold" style={{ color: '#1a1a1a' }}>
                            {r.condomino ? `${r.condomino.nome} ${r.condomino.cognome}` : r.username}
                          </span>
                          <span className="text-xs px-2 py-0.5 rounded-full font-semibold" style={{ backgroundColor: '#eff6ff', color: '#2563eb' }}>@{r.username}</span>
                        </div>
                        <div className="flex items-center gap-4 mt-1.5 flex-wrap">
                          {r.condomino && (
                            <span className="flex items-center gap-1 text-xs" style={{ color: '#888' }}>
                              <Building2 size={11} />{r.condomino.condominio.nome} · Unità {r.condomino.unita}
                            </span>
                          )}
                          {(r.email ?? r.condomino?.email) && (
                            <span className="flex items-center gap-1 text-xs" style={{ color: '#888' }}><Mail size={11} />{r.email ?? r.condomino?.email}</span>
                          )}
                          {r.resetPasswordRichiestoAt && (
                            <span className="flex items-center gap-1 text-xs" style={{ color: '#888' }}><Clock size={11} />{formatData(r.resetPasswordRichiestoAt)}</span>
                          )}
                        </div>
                      </div>
                      <div className="flex flex-col gap-1.5 flex-shrink-0">
                        <button
                          onClick={() => handleApprova(r)}
                          disabled={approvingId === r.id || !(r.email ?? r.condomino?.email)}
                          title={(r.email ?? r.condomino?.email) ? undefined : 'Nessun indirizzo email: usa la reimpostazione manuale'}
                          className="flex items-center justify-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold text-white hover:opacity-90 disabled:opacity-50"
                          style={{ backgroundColor: 'var(--primary)' }}
                        >
                          <Mail size={12} />{approvingId === r.id ? 'Invio…' : 'Approva e invia email'}
                        </button>
                        {r.condomino && (
                          <button
                            onClick={() => openResetModal(r)}
                            className="px-3 py-1.5 rounded-lg text-xs font-semibold hover:bg-gray-100"
                            style={{ color: '#555', border: '1px solid #e5e5e5' }}
                          >
                            Reimposta manualmente
                          </button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </section>
          </div>
        )}
      </div>

      {/* Modal approva registrazione */}
      {regTarget && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center"
          style={{ backgroundColor: 'rgba(0,0,0,0.4)' }}
          onClick={() => setRegTarget(null)}
        >
          <div
            className="w-full max-w-sm rounded-2xl p-6 shadow-xl"
            style={{ backgroundColor: '#fff' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-base font-bold" style={{ color: '#1a1a1a' }}>
                Approva – {regTarget.nome && regTarget.cognome ? `${regTarget.nome} ${regTarget.cognome}` : regTarget.username}
              </h2>
              <button onClick={() => setRegTarget(null)} style={{ color: '#888' }}><X size={18} /></button>
            </div>
            <div className="flex flex-col gap-3">
              <p className="text-xs" style={{ color: '#888' }}>
                Account <strong>@{regTarget.username}</strong>{regTarget.email ? <> · {regTarget.email}</> : null}. Dopo l&apos;approvazione potrà accedere con la password scelta in registrazione.
              </p>
              <div>
                <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Condominio *</label>
                <select
                  value={regForm.condominioId}
                  onChange={(e) => setRegForm((f) => ({ ...f, condominioId: e.target.value }))}
                  className="w-full px-3 py-2 rounded-lg text-sm outline-none"
                  style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
                >
                  <option value="">Seleziona…</option>
                  {condominii.map((c) => (
                    <option key={c.id} value={c.id}>{c.nome}</option>
                  ))}
                </select>
              </div>
              <div className="flex gap-2">
                <div className="flex-1">
                  <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Unità *</label>
                  <input
                    value={regForm.unita}
                    onChange={(e) => setRegForm((f) => ({ ...f, unita: e.target.value }))}
                    placeholder="es. A1"
                    className="w-full px-3 py-2 rounded-lg text-sm outline-none"
                    style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
                  />
                </div>
                <div className="flex-1">
                  <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Millesimi</label>
                  <input
                    type="number"
                    min="0"
                    step="0.01"
                    value={regForm.millesimi}
                    onChange={(e) => setRegForm((f) => ({ ...f, millesimi: e.target.value }))}
                    placeholder="0"
                    className="w-full px-3 py-2 rounded-lg text-sm outline-none"
                    style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
                  />
                </div>
              </div>
              <div>
                <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Tipo</label>
                <select
                  value={regForm.tipo}
                  onChange={(e) => setRegForm((f) => ({ ...f, tipo: e.target.value }))}
                  className="w-full px-3 py-2 rounded-lg text-sm outline-none"
                  style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
                >
                  <option value="proprietario">Proprietario</option>
                  <option value="inquilino">Inquilino</option>
                </select>
              </div>
              {regError && <p className="text-xs px-3 py-2 rounded-lg" style={{ backgroundColor: '#fef2f2', color: 'var(--primary)' }}>{regError}</p>}
              <div className="flex gap-2 pt-2">
                <button onClick={() => setRegTarget(null)} className="flex-1 py-2 rounded-lg text-sm font-semibold" style={{ backgroundColor: '#f5f5f5', color: '#555' }}>Annulla</button>
                <button onClick={handleApprovaRegistrazione} disabled={regSaving} className="flex-1 py-2 rounded-lg text-sm font-semibold text-white hover:opacity-90" style={{ backgroundColor: 'var(--primary)', opacity: regSaving ? 0.7 : 1 }}>
                  {regSaving ? 'Approvazione...' : 'Approva'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Modal reimposta password */}
      {resetTarget && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center"
          style={{ backgroundColor: 'rgba(0,0,0,0.4)' }}
          onClick={() => setResetTarget(null)}
        >
          <div
            className="w-full max-w-sm rounded-2xl p-6 shadow-xl"
            style={{ backgroundColor: '#fff' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-base font-bold" style={{ color: '#1a1a1a' }}>
                Reimposta password – {resetTarget.condomino ? `${resetTarget.condomino.nome} ${resetTarget.condomino.cognome}` : resetTarget.username}
              </h2>
              <button onClick={() => setResetTarget(null)} style={{ color: '#888' }}><X size={18} /></button>
            </div>
            <div className="flex flex-col gap-3">
              <p className="text-xs" style={{ color: '#888' }}>
                Account: <strong>@{resetTarget.username}</strong>. Imposta una nuova password e comunicala al condòmino.
              </p>
              <div>
                <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Nuova password</label>
                <div className="relative">
                  <input
                    type={showPassword ? 'text' : 'password'}
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    placeholder="Almeno 6 caratteri"
                    className="w-full px-3 py-2 rounded-lg text-sm outline-none pr-10"
                    style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
                  />
                  <button type="button" onClick={() => setShowPassword((v) => !v)} className="absolute right-2 top-1/2 -translate-y-1/2" style={{ color: '#aaa' }}>
                    {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
                  </button>
                </div>
              </div>
              {modalError && <p className="text-xs px-3 py-2 rounded-lg" style={{ backgroundColor: '#fef2f2', color: 'var(--primary)' }}>{modalError}</p>}
              <div className="flex gap-2 pt-2">
                <button onClick={() => setResetTarget(null)} className="flex-1 py-2 rounded-lg text-sm font-semibold" style={{ backgroundColor: '#f5f5f5', color: '#555' }}>Annulla</button>
                <button onClick={handleResetPassword} disabled={saving} className="flex-1 py-2 rounded-lg text-sm font-semibold text-white hover:opacity-90" style={{ backgroundColor: 'var(--primary)', opacity: saving ? 0.7 : 1 }}>
                  {saving ? 'Salvataggio...' : 'Reimposta password'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
