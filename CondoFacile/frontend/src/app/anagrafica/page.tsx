'use client';

import { useState, useEffect, useCallback } from 'react';
import { Building2, Users, Plus, X, Eye, EyeOff, ChevronRight, Mail, Phone, Home } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import {
  fetchCondominii,
  createCondominio,
  fetchCondominioDetail,
  addCondomino,
  CondominioListItem,
  CondominioDetail,
  AddCondominoPayload,
} from '@/lib/api';

// ─── Modale generica ─────────────────────────────────────────────────────────

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center"
      style={{ backgroundColor: 'rgba(0,0,0,0.4)' }}
      onClick={onClose}
    >
      <div
        className="w-full max-w-lg rounded-2xl p-6 shadow-xl"
        style={{ backgroundColor: '#fff', maxHeight: '90vh', overflowY: 'auto' }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between mb-5">
          <h2 className="text-base font-bold" style={{ color: '#1a1a1a' }}>{title}</h2>
          <button onClick={onClose} style={{ color: '#888' }}><X size={18} /></button>
        </div>
        {children}
      </div>
    </div>
  );
}

// ─── Input helper ─────────────────────────────────────────────────────────────

function Field({
  label, value, onChange, placeholder, required, type = 'text',
}: {
  label: string; value: string; onChange: (v: string) => void;
  placeholder?: string; required?: boolean; type?: string;
}) {
  return (
    <div>
      <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>
        {label}{required && <span style={{ color: 'var(--primary)' }}> *</span>}
      </label>
      <input
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="w-full px-3 py-2 rounded-lg text-sm outline-none"
        style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
      />
    </div>
  );
}

// ─── Pagina principale ────────────────────────────────────────────────────────

export default function AnagraficaPage() {
  const { token } = useAuth();

  const [condominii, setCondominii] = useState<CondominioListItem[]>([]);
  const [selected, setSelected] = useState<CondominioDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Modal condominio
  const [showCondoModal, setShowCondoModal] = useState(false);
  const [condoNome, setCondoNome] = useState('');
  const [condoIndirizzo, setCondoIndirizzo] = useState('');
  const [condoSaving, setCondoSaving] = useState(false);
  const [condoError, setCondoError] = useState<string | null>(null);

  // Modal condomino
  const [showPersonaModal, setShowPersonaModal] = useState(false);
  const [personaSaving, setPersonaSaving] = useState(false);
  const [personaError, setPersonaError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [persona, setPersona] = useState<AddCondominoPayload>({
    nome: '', cognome: '', email: '', telefono: '', unita: '', millesimi: undefined,
    username: '', password: '',
  });

  const loadCondominii = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError(null);
    try {
      const data = await fetchCondominii(token);
      setCondominii(data);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => { loadCondominii(); }, [loadCondominii]);

  const selectCondominio = async (id: number) => {
    if (!token) return;
    setDetailLoading(true);
    try {
      const detail = await fetchCondominioDetail(token, id);
      setSelected(detail);
    } catch {
      // ignore
    } finally {
      setDetailLoading(false);
    }
  };

  // ── Salva nuovo condominio ──────────────────────────────────────────────────
  const handleSaveCondominio = async () => {
    if (!token || !condoNome.trim() || !condoIndirizzo.trim()) {
      setCondoError('Nome e indirizzo sono obbligatori');
      return;
    }
    setCondoSaving(true);
    setCondoError(null);
    try {
      await createCondominio(token, condoNome.trim(), condoIndirizzo.trim());
      setShowCondoModal(false);
      setCondoNome('');
      setCondoIndirizzo('');
      await loadCondominii();
    } catch (e) {
      setCondoError((e as Error).message);
    } finally {
      setCondoSaving(false);
    }
  };

  // ── Salva nuovo condomino ────────────────────────────────────────────────────
  const handleSavePersona = async () => {
    if (!token || !selected) return;
    if (!persona.nome.trim() || !persona.cognome.trim() || !persona.unita.trim()) {
      setPersonaError('Nome, cognome e unità sono obbligatori');
      return;
    }
    if (persona.username && !persona.password) {
      setPersonaError('Inserisci una password per l\'account utente');
      return;
    }
    setPersonaSaving(true);
    setPersonaError(null);
    try {
      const payload: AddCondominoPayload = {
        nome: persona.nome.trim(),
        cognome: persona.cognome.trim(),
        unita: persona.unita.trim(),
        email: persona.email?.trim() || undefined,
        telefono: persona.telefono?.trim() || undefined,
        millesimi: persona.millesimi ? Number(persona.millesimi) : undefined,
        username: persona.username?.trim() || undefined,
        password: persona.password?.trim() || undefined,
      };
      await addCondomino(token, selected.id, payload);
      setShowPersonaModal(false);
      setPersona({ nome: '', cognome: '', email: '', telefono: '', unita: '', millesimi: undefined, username: '', password: '' });
      // Refresh dettaglio
      const detail = await fetchCondominioDetail(token, selected.id);
      setSelected(detail);
      // Aggiorna contatore nella lista
      setCondominii((prev) =>
        prev.map((c) => c.id === selected.id ? { ...c, _count: { condomini: c._count.condomini + 1 } } : c)
      );
    } catch (e) {
      setPersonaError((e as Error).message);
    } finally {
      setPersonaSaving(false);
    }
  };

  // ── Render ──────────────────────────────────────────────────────────────────

  return (
    <div className="flex flex-col flex-1 overflow-hidden" style={{ backgroundColor: 'var(--background)' }}>
      {/* Header */}
      <header
        className="flex items-center justify-between px-6 py-3 border-b flex-shrink-0"
        style={{ backgroundColor: '#fff', borderColor: '#f0f0f0' }}
      >
        <div>
          <h1 className="text-sm font-semibold" style={{ color: '#1a1a1a' }}>Anagrafica</h1>
          <p className="text-xs" style={{ color: '#888' }}>Gestione condominii e condòmini</p>
        </div>
        <button
          onClick={() => { setCondoError(null); setShowCondoModal(true); }}
          className="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold text-white transition-opacity hover:opacity-90"
          style={{ backgroundColor: 'var(--primary)' }}
        >
          <Plus size={15} />
          Nuovo Condominio
        </button>
      </header>

      {/* Corpo */}
      <div className="flex flex-1 overflow-hidden">

        {/* ── Lista condominii ─────────────────────────────────────────── */}
        <div
          className="flex flex-col flex-shrink-0 overflow-y-auto p-4 gap-3"
          style={{ width: 320, borderRight: '1px solid #f0f0f0', backgroundColor: '#fafafa' }}
        >
          <p className="text-xs font-semibold uppercase tracking-widest px-1" style={{ color: '#bbb' }}>
            Condominii ({condominii.length})
          </p>

          {loading && (
            <p className="text-sm text-center mt-6" style={{ color: '#aaa' }}>Caricamento...</p>
          )}
          {error && (
            <p className="text-xs text-center mt-4 px-2" style={{ color: 'var(--primary)' }}>{error}</p>
          )}

          {!loading && condominii.length === 0 && !error && (
            <div className="text-center mt-8">
              <Building2 size={32} style={{ color: '#ddd', margin: '0 auto 8px' }} />
              <p className="text-xs" style={{ color: '#aaa' }}>Nessun condominio</p>
              <p className="text-xs mt-1" style={{ color: '#ccc' }}>Clicca "Nuovo Condominio"</p>
            </div>
          )}

          {condominii.map((c) => {
            const isActive = selected?.id === c.id;
            return (
              <button
                key={c.id}
                onClick={() => selectCondominio(c.id)}
                className="w-full text-left rounded-xl p-3 transition-all"
                style={{
                  backgroundColor: isActive ? '#fef2f2' : '#fff',
                  border: isActive ? '1.5px solid var(--primary)' : '1.5px solid #f0f0f0',
                }}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2 min-w-0">
                    <Building2 size={15} style={{ color: isActive ? 'var(--primary)' : '#aaa', flexShrink: 0 }} />
                    <span className="text-sm font-semibold truncate" style={{ color: isActive ? 'var(--primary)' : '#222' }}>
                      {c.nome}
                    </span>
                  </div>
                  <ChevronRight size={14} style={{ color: '#ccc', flexShrink: 0 }} />
                </div>
                <p className="text-xs mt-0.5 truncate" style={{ color: '#888', paddingLeft: 23 }}>{c.indirizzo}</p>
                <p className="text-xs mt-1" style={{ color: '#aaa', paddingLeft: 23 }}>
                  {c._count.condomini} condòmin{c._count.condomini === 1 ? 'o' : 'i'}
                </p>
              </button>
            );
          })}
        </div>

        {/* ── Dettaglio condominio ─────────────────────────────────────── */}
        <div className="flex flex-col flex-1 overflow-hidden">
          {!selected ? (
            <div className="flex flex-col items-center justify-center h-full" style={{ color: '#ccc' }}>
              <Building2 size={48} style={{ marginBottom: 12 }} />
              <p className="text-sm font-medium" style={{ color: '#aaa' }}>Seleziona un condominio</p>
              <p className="text-xs mt-1" style={{ color: '#ccc' }}>per vedere i condòmini</p>
            </div>
          ) : (
            <div className="flex flex-col flex-1 overflow-hidden">
              {/* Intestazione condominio selezionato */}
              <div
                className="flex items-center justify-between px-6 py-4 flex-shrink-0"
                style={{ borderBottom: '1px solid #f0f0f0', backgroundColor: '#fff' }}
              >
                <div>
                  <h2 className="text-base font-bold" style={{ color: '#1a1a1a' }}>{selected.nome}</h2>
                  <p className="text-xs" style={{ color: '#888' }}>{selected.indirizzo}</p>
                </div>
                <button
                  onClick={() => { setPersonaError(null); setShowPersonaModal(true); }}
                  className="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold text-white hover:opacity-90 transition-opacity"
                  style={{ backgroundColor: 'var(--primary)' }}
                >
                  <Plus size={15} />
                  Aggiungi Condomino
                </button>
              </div>

              {/* Lista condòmini */}
              <div className="flex-1 overflow-y-auto p-6">
                {detailLoading && (
                  <p className="text-sm text-center mt-8" style={{ color: '#aaa' }}>Caricamento...</p>
                )}

                {!detailLoading && selected.condomini.length === 0 && (
                  <div className="text-center mt-12">
                    <Users size={36} style={{ color: '#ddd', margin: '0 auto 8px' }} />
                    <p className="text-sm font-medium" style={{ color: '#aaa' }}>Nessun condomino</p>
                    <p className="text-xs mt-1" style={{ color: '#ccc' }}>Clicca "Aggiungi Condomino" per iniziare</p>
                  </div>
                )}

                {!detailLoading && selected.condomini.length > 0 && (
                  <div className="grid grid-cols-1 gap-3" style={{ maxWidth: 700 }}>
                    {selected.condomini.map((p) => (
                      <div
                        key={p.id}
                        className="flex items-start gap-4 rounded-xl p-4"
                        style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0' }}
                      >
                        {/* Avatar iniziali */}
                        <div
                          className="flex-shrink-0 w-10 h-10 rounded-full flex items-center justify-center text-sm font-bold"
                          style={{ backgroundColor: '#fef2f2', color: 'var(--primary)' }}
                        >
                          {p.nome[0]}{p.cognome[0]}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="text-sm font-semibold" style={{ color: '#1a1a1a' }}>
                              {p.nome} {p.cognome}
                            </span>
                            <span
                              className="text-xs px-2 py-0.5 rounded-full"
                              style={{ backgroundColor: '#f0fdf4', color: '#16a34a', fontWeight: 600 }}
                            >
                              Unità {p.unita}
                            </span>
                            {p.user && (
                              <span
                                className="text-xs px-2 py-0.5 rounded-full"
                                style={{ backgroundColor: '#eff6ff', color: '#2563eb', fontWeight: 600 }}
                              >
                                @{p.user.username}
                              </span>
                            )}
                          </div>
                          <div className="flex items-center gap-4 mt-1.5 flex-wrap">
                            {p.email && (
                              <span className="flex items-center gap-1 text-xs" style={{ color: '#888' }}>
                                <Mail size={11} />{p.email}
                              </span>
                            )}
                            {p.telefono && (
                              <span className="flex items-center gap-1 text-xs" style={{ color: '#888' }}>
                                <Phone size={11} />{p.telefono}
                              </span>
                            )}
                            <span className="flex items-center gap-1 text-xs" style={{ color: '#aaa' }}>
                              <Home size={11} />{p.millesimi} millesimi
                            </span>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* ── Modal: Nuovo Condominio ─────────────────────────────────────────── */}
      {showCondoModal && (
        <Modal title="Nuovo Condominio" onClose={() => setShowCondoModal(false)}>
          <div className="flex flex-col gap-4">
            <Field label="Nome condominio" value={condoNome} onChange={setCondoNome} placeholder="es. Condominio Rossi" required />
            <Field label="Indirizzo" value={condoIndirizzo} onChange={setCondoIndirizzo} placeholder="es. Via Roma 12, Milano" required />

            {condoError && (
              <p className="text-xs px-3 py-2 rounded-lg" style={{ backgroundColor: '#fef2f2', color: 'var(--primary)' }}>
                {condoError}
              </p>
            )}

            <div className="flex gap-2 pt-2">
              <button
                onClick={() => setShowCondoModal(false)}
                className="flex-1 py-2 rounded-lg text-sm font-semibold transition"
                style={{ backgroundColor: '#f5f5f5', color: '#555' }}
              >
                Annulla
              </button>
              <button
                onClick={handleSaveCondominio}
                disabled={condoSaving}
                className="flex-1 py-2 rounded-lg text-sm font-semibold text-white transition hover:opacity-90"
                style={{ backgroundColor: 'var(--primary)', opacity: condoSaving ? 0.7 : 1 }}
              >
                {condoSaving ? 'Salvataggio...' : 'Crea Condominio'}
              </button>
            </div>
          </div>
        </Modal>
      )}

      {/* ── Modal: Aggiungi Condomino ────────────────────────────────────────── */}
      {showPersonaModal && (
        <Modal title="Aggiungi Condomino" onClose={() => setShowPersonaModal(false)}>
          <div className="flex flex-col gap-3">
            <div className="grid grid-cols-2 gap-3">
              <Field label="Nome" value={persona.nome} onChange={(v) => setPersona((p) => ({ ...p, nome: v }))} placeholder="Mario" required />
              <Field label="Cognome" value={persona.cognome} onChange={(v) => setPersona((p) => ({ ...p, cognome: v }))} placeholder="Rossi" required />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Unità abitativa" value={persona.unita} onChange={(v) => setPersona((p) => ({ ...p, unita: v }))} placeholder="es. A1, 3°piano" required />
              <Field label="Millesimi" value={String(persona.millesimi ?? '')} onChange={(v) => setPersona((p) => ({ ...p, millesimi: v ? Number(v) : undefined }))} placeholder="es. 150" type="number" />
            </div>
            <Field label="Email" value={persona.email ?? ''} onChange={(v) => setPersona((p) => ({ ...p, email: v }))} placeholder="mario.rossi@email.com" type="email" />
            <Field label="Telefono" value={persona.telefono ?? ''} onChange={(v) => setPersona((p) => ({ ...p, telefono: v }))} placeholder="+39 333 0000000" />

            {/* Separatore account */}
            <div className="flex items-center gap-2 mt-1">
              <div style={{ flex: 1, height: 1, backgroundColor: '#f0f0f0' }} />
              <span className="text-xs font-semibold uppercase tracking-wider" style={{ color: '#bbb' }}>Account accesso (opzionale)</span>
              <div style={{ flex: 1, height: 1, backgroundColor: '#f0f0f0' }} />
            </div>

            <Field label="Username" value={persona.username ?? ''} onChange={(v) => setPersona((p) => ({ ...p, username: v }))} placeholder="mario.rossi" />

            {/* Campo password con toggle visibilità */}
            <div>
              <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>
                Password {persona.username && <span style={{ color: 'var(--primary)' }}>*</span>}
              </label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={persona.password ?? ''}
                  onChange={(e) => setPersona((p) => ({ ...p, password: e.target.value }))}
                  placeholder="Almeno 6 caratteri"
                  className="w-full px-3 py-2 rounded-lg text-sm outline-none pr-10"
                  style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  className="absolute right-2 top-1/2 -translate-y-1/2"
                  style={{ color: '#aaa' }}
                >
                  {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
              </div>
            </div>

            {personaError && (
              <p className="text-xs px-3 py-2 rounded-lg" style={{ backgroundColor: '#fef2f2', color: 'var(--primary)' }}>
                {personaError}
              </p>
            )}

            <div className="flex gap-2 pt-2">
              <button
                onClick={() => setShowPersonaModal(false)}
                className="flex-1 py-2 rounded-lg text-sm font-semibold"
                style={{ backgroundColor: '#f5f5f5', color: '#555' }}
              >
                Annulla
              </button>
              <button
                onClick={handleSavePersona}
                disabled={personaSaving}
                className="flex-1 py-2 rounded-lg text-sm font-semibold text-white hover:opacity-90 transition-opacity"
                style={{ backgroundColor: 'var(--primary)', opacity: personaSaving ? 0.7 : 1 }}
              >
                {personaSaving ? 'Salvataggio...' : 'Aggiungi Condomino'}
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}
