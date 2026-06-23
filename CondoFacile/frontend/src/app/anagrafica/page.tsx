'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Building2, Users, Plus, X, Eye, EyeOff, ChevronRight,
  Mail, Phone, Home, Search, Pencil, Power,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import {
  fetchCondominii,
  createCondominio,
  updateCondominio,
  fetchCondominioDetail,
  addCondomino,
  updateCondomino,
  toggleCondominoStato,
  fetchUsersNonAssociati,
  UnassociatedUser,
  CondominioListItem,
  CondominioDetail,
  CondominoItem,
  AddCondominoPayload,
} from '@/lib/api';

// ─── Modale generica ──────────────────────────────────────────────────────────

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

// ─── Input helpers ────────────────────────────────────────────────────────────

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

function SelectField({
  label, value, onChange, options, required,
}: {
  label: string; value: string; onChange: (v: string) => void;
  options: { value: string; label: string }[]; required?: boolean;
}) {
  return (
    <div>
      <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>
        {label}{required && <span style={{ color: 'var(--primary)' }}> *</span>}
      </label>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full px-3 py-2 rounded-lg text-sm outline-none"
        style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>{o.label}</option>
        ))}
      </select>
    </div>
  );
}

const TIPO_OPTIONS = [
  { value: 'proprietario', label: 'Proprietario' },
  { value: 'inquilino', label: 'Inquilino' },
  { value: 'delegato', label: 'Delegato' },
];

const TIPO_COLORS: Record<string, { bg: string; color: string }> = {
  proprietario: { bg: '#eff6ff', color: '#2563eb' },
  inquilino: { bg: '#f0fdf4', color: '#16a34a' },
  delegato: { bg: '#fefce8', color: '#ca8a04' },
};

// ─── Form condòmino ───────────────────────────────────────────────────────────

interface CondominoFormState {
  nome: string; cognome: string; email: string; telefono: string;
  unita: string; millesimi: string; tipo: string;
  username: string; password: string;
}

const emptyForm = (): CondominoFormState => ({
  nome: '', cognome: '', email: '', telefono: '',
  unita: '', millesimi: '', tipo: 'proprietario',
  username: '', password: '',
});

function CondominoForm({
  form, setForm, showPassword, togglePassword, isEdit,
}: {
  form: CondominoFormState;
  setForm: React.Dispatch<React.SetStateAction<CondominoFormState>>;
  showPassword: boolean; togglePassword: () => void; isEdit: boolean;
}) {
  const set = (k: keyof CondominoFormState) => (v: string) => setForm((p) => ({ ...p, [k]: v }));
  return (
    <>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Nome" value={form.nome} onChange={set('nome')} placeholder="Mario" required />
        <Field label="Cognome" value={form.cognome} onChange={set('cognome')} placeholder="Rossi" required />
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Unità abitativa" value={form.unita} onChange={set('unita')} placeholder="es. A1" required />
        <Field label="Millesimi" value={form.millesimi} onChange={set('millesimi')} placeholder="es. 150" type="number" />
      </div>
      <SelectField label="Tipo" value={form.tipo} onChange={set('tipo')} options={TIPO_OPTIONS} required />
      <Field label="Email" value={form.email} onChange={set('email')} placeholder="mario.rossi@email.com" type="email" />
      <Field label="Telefono" value={form.telefono} onChange={set('telefono')} placeholder="+39 333 0000000" />
      {!isEdit && (
        <>
          <div className="flex items-center gap-2 mt-1">
            <div style={{ flex: 1, height: 1, backgroundColor: '#f0f0f0' }} />
            <span className="text-xs font-semibold uppercase tracking-wider" style={{ color: '#bbb' }}>Account (opzionale)</span>
            <div style={{ flex: 1, height: 1, backgroundColor: '#f0f0f0' }} />
          </div>
          <Field label="Username" value={form.username} onChange={set('username')} placeholder="mario.rossi" />
          <div>
            <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>
              Password {form.username && <span style={{ color: 'var(--primary)' }}>*</span>}
            </label>
            <div className="relative">
              <input
                type={showPassword ? 'text' : 'password'}
                value={form.password}
                onChange={(e) => set('password')(e.target.value)}
                placeholder="Almeno 6 caratteri"
                className="w-full px-3 py-2 rounded-lg text-sm outline-none pr-10"
                style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
              />
              <button type="button" onClick={togglePassword} className="absolute right-2 top-1/2 -translate-y-1/2" style={{ color: '#aaa' }}>
                {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>
          </div>
        </>
      )}
    </>
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

  // Filtri
  const [search, setSearch] = useState('');
  const [filterStato, setFilterStato] = useState<'tutti' | 'attivo' | 'disattivo'>('tutti');
  const [filterTipo, setFilterTipo] = useState<string>('tutti');

  // Modal condominio – crea
  const [showCondoModal, setShowCondoModal] = useState(false);
  const [condoNome, setCondoNome] = useState('');
  const [condoIndirizzo, setCondoIndirizzo] = useState('');
  const [condoSaving, setCondoSaving] = useState(false);
  const [condoError, setCondoError] = useState<string | null>(null);

  // Modal condominio – modifica
  const [showEditCondoModal, setShowEditCondoModal] = useState(false);
  const [editCondoNome, setEditCondoNome] = useState('');
  const [editCondoIndirizzo, setEditCondoIndirizzo] = useState('');
  const [editCondoSaving, setEditCondoSaving] = useState(false);
  const [editCondoError, setEditCondoError] = useState<string | null>(null);

  // Modal condòmino – aggiungi
  const [showAddModal, setShowAddModal] = useState(false);
  const [addTab, setAddTab] = useState<'nuovo' | 'esistente'>('nuovo');
  const [addForm, setAddForm] = useState<CondominoFormState>(emptyForm());
  const [addSaving, setAddSaving] = useState(false);
  const [addError, setAddError] = useState<string | null>(null);
  const [showAddPassword, setShowAddPassword] = useState(false);
  // Tab "esistente"
  const [unassociatedUsers, setUnassociatedUsers] = useState<UnassociatedUser[]>([]);
  const [unassociatedLoading, setUnassociatedLoading] = useState(false);
  const [selectedExistingUser, setSelectedExistingUser] = useState<UnassociatedUser | null>(null);
  const [existingUserSearch, setExistingUserSearch] = useState('');

  // Modal condòmino – modifica
  const [showEditModal, setShowEditModal] = useState(false);
  const [editingCondomino, setEditingCondomino] = useState<CondominoItem | null>(null);
  const [editForm, setEditForm] = useState<CondominoFormState>(emptyForm());
  const [editSaving, setEditSaving] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

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

  const refreshSelected = useCallback(async (id: number) => {
    if (!token) return;
    const detail = await fetchCondominioDetail(token, id);
    setSelected(detail);
  }, [token]);

  const selectCondominio = async (id: number) => {
    if (!token) return;
    setDetailLoading(true);
    setSearch('');
    setFilterStato('tutti');
    setFilterTipo('tutti');
    try {
      const detail = await fetchCondominioDetail(token, id);
      setSelected(detail);
    } finally {
      setDetailLoading(false);
    }
  };

  const filteredCondomini = useMemo(() => {
    if (!selected) return [];
    return selected.condomini.filter((p) => {
      const matchSearch =
        !search ||
        `${p.nome} ${p.cognome}`.toLowerCase().includes(search.toLowerCase()) ||
        p.unita.toLowerCase().includes(search.toLowerCase());
      const matchStato = filterStato === 'tutti' || p.stato === filterStato;
      const matchTipo = filterTipo === 'tutti' || p.tipo === filterTipo;
      return matchSearch && matchStato && matchTipo;
    });
  }, [selected, search, filterStato, filterTipo]);

  // ── Crea condominio ──────────────────────────────────────────────────────────
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

  // ── Modifica condominio ──────────────────────────────────────────────────────
  const openEditCondominio = () => {
    if (!selected) return;
    setEditCondoNome(selected.nome);
    setEditCondoIndirizzo(selected.indirizzo);
    setEditCondoError(null);
    setShowEditCondoModal(true);
  };

  const handleUpdateCondominio = async () => {
    if (!token || !selected || !editCondoNome.trim() || !editCondoIndirizzo.trim()) {
      setEditCondoError('Nome e indirizzo sono obbligatori');
      return;
    }
    setEditCondoSaving(true);
    setEditCondoError(null);
    try {
      await updateCondominio(token, selected.id, editCondoNome.trim(), editCondoIndirizzo.trim());
      setShowEditCondoModal(false);
      await loadCondominii();
      await refreshSelected(selected.id);
    } catch (e) {
      setEditCondoError((e as Error).message);
    } finally {
      setEditCondoSaving(false);
    }
  };

  // ── Aggiungi condòmino ───────────────────────────────────────────────────────
  const openAddModal = async () => {
    setAddError(null);
    setAddForm(emptyForm());
    setAddTab('nuovo');
    setSelectedExistingUser(null);
    setExistingUserSearch('');
    setShowAddModal(true);
    if (token) {
      setUnassociatedLoading(true);
      try {
        const users = await fetchUsersNonAssociati(token);
        setUnassociatedUsers(users);
      } catch {
        setUnassociatedUsers([]);
      } finally {
        setUnassociatedLoading(false);
      }
    }
  };

  const handleSaveAdd = async () => {
    if (!token || !selected) return;

    // Validate
    if (!addForm.nome.trim() || !addForm.cognome.trim() || !addForm.unita.trim()) {
      setAddError('Nome, cognome e unità sono obbligatori');
      return;
    }
    if (addTab === 'esistente' && !selectedExistingUser) {
      setAddError('Seleziona un utente da associare');
      return;
    }
    if (addTab === 'nuovo' && addForm.username && !addForm.password) {
      setAddError("Inserisci una password per l'account utente");
      return;
    }
    const unitaNormalizzata = addForm.unita.trim().toLowerCase();
    const unitaGiaUsata = selected.condomini.some(
      (c) => c.unita.toLowerCase() === unitaNormalizzata
    );
    if (unitaGiaUsata) {
      setAddError(`L'unità "${addForm.unita.trim()}" è già assegnata ad un altro condòmino`);
      return;
    }

    setAddSaving(true);
    setAddError(null);
    try {
      const payload: AddCondominoPayload = {
        nome: addForm.nome.trim(), cognome: addForm.cognome.trim(),
        unita: addForm.unita.trim(), tipo: addForm.tipo,
        email: addForm.email.trim() || undefined,
        telefono: addForm.telefono.trim() || undefined,
        millesimi: addForm.millesimi ? Number(addForm.millesimi) : undefined,
        username: addTab === 'esistente' ? selectedExistingUser!.username : (addForm.username.trim() || undefined),
        password: addTab === 'esistente' ? undefined : (addForm.password.trim() || undefined),
      };
      await addCondomino(token, selected.id, payload);
      setShowAddModal(false);
      setAddForm(emptyForm());
      setSelectedExistingUser(null);
      setCondominii((prev) =>
        prev.map((c) => c.id === selected.id ? { ...c, _count: { condomini: c._count.condomini + 1 } } : c)
      );
      await refreshSelected(selected.id);
    } catch (e) {
      setAddError((e as Error).message);
    } finally {
      setAddSaving(false);
    }
  };

  // ── Modifica condòmino ───────────────────────────────────────────────────────
  const openEditCondomino = (p: CondominoItem) => {
    setEditingCondomino(p);
    setEditForm({
      nome: p.nome, cognome: p.cognome,
      email: p.email ?? '', telefono: p.telefono ?? '',
      unita: p.unita, millesimi: String(p.millesimi),
      tipo: p.tipo, username: '', password: '',
    });
    setEditError(null);
    setShowEditModal(true);
  };

  const handleSaveEdit = async () => {
    if (!token || !selected || !editingCondomino) return;
    if (!editForm.nome.trim() || !editForm.cognome.trim() || !editForm.unita.trim()) {
      setEditError('Nome, cognome e unità sono obbligatori');
      return;
    }
    const unitaNorm = editForm.unita.trim().toLowerCase();
    const unitaOccupata = selected.condomini.some(
      (c) => c.id !== editingCondomino.id && c.unita.toLowerCase() === unitaNorm
    );
    if (unitaOccupata) {
      setEditError(`L'unità "${editForm.unita.trim()}" è già assegnata ad un altro condòmino`);
      return;
    }
    setEditSaving(true);
    setEditError(null);
    try {
      await updateCondomino(token, selected.id, editingCondomino.id, {
        nome: editForm.nome.trim(), cognome: editForm.cognome.trim(),
        unita: editForm.unita.trim(), tipo: editForm.tipo,
        email: editForm.email.trim() || undefined,
        telefono: editForm.telefono.trim() || undefined,
        millesimi: editForm.millesimi ? Number(editForm.millesimi) : undefined,
      });
      setShowEditModal(false);
      setEditingCondomino(null);
      await refreshSelected(selected.id);
    } catch (e) {
      setEditError((e as Error).message);
    } finally {
      setEditSaving(false);
    }
  };

  // ── Toggle stato condòmino ───────────────────────────────────────────────────
  const handleToggleStato = async (p: CondominoItem) => {
    if (!token || !selected) return;
    try {
      const updated = await toggleCondominoStato(token, selected.id, p.id);
      setSelected((prev) =>
        prev
          ? { ...prev, condomini: prev.condomini.map((c) => (c.id === p.id ? { ...c, stato: updated.stato } : c)) }
          : prev
      );
    } catch (e) {
      console.error(e);
    }
  };

  // ── Render ──────────────────────────────────────────────────────────────────

  return (
    <div className="flex flex-col flex-1 overflow-hidden" style={{ backgroundColor: 'var(--background)' }}>
      {/* Header */}
      <header className="flex items-center justify-between px-6 py-3 border-b flex-shrink-0" style={{ backgroundColor: '#fff', borderColor: '#f0f0f0' }}>
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

      <div className="flex flex-1 overflow-hidden">
        {/* ── Lista condominii ─────────────────────────────────────────── */}
        <div className="flex flex-col flex-shrink-0 overflow-y-auto p-4 gap-3" style={{ width: 300, borderRight: '1px solid #f0f0f0', backgroundColor: '#fafafa' }}>
          <p className="text-xs font-semibold uppercase tracking-widest px-1" style={{ color: '#bbb' }}>Condominii ({condominii.length})</p>
          {loading && <p className="text-sm text-center mt-6" style={{ color: '#aaa' }}>Caricamento...</p>}
          {error && <p className="text-xs text-center mt-4 px-2" style={{ color: 'var(--primary)' }}>{error}</p>}
          {!loading && condominii.length === 0 && !error && (
            <div className="text-center mt-8">
              <Building2 size={32} style={{ color: '#ddd', margin: '0 auto 8px' }} />
              <p className="text-xs" style={{ color: '#aaa' }}>Nessun condominio</p>
            </div>
          )}
          {condominii.map((c) => {
            const isActive = selected?.id === c.id;
            return (
              <button
                key={c.id}
                onClick={() => selectCondominio(c.id)}
                className="w-full text-left rounded-xl p-3 transition-all"
                style={{ backgroundColor: isActive ? '#fef2f2' : '#fff', border: isActive ? '1.5px solid var(--primary)' : '1.5px solid #f0f0f0' }}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2 min-w-0">
                    <Building2 size={15} style={{ color: isActive ? 'var(--primary)' : '#aaa', flexShrink: 0 }} />
                    <span className="text-sm font-semibold truncate" style={{ color: isActive ? 'var(--primary)' : '#222' }}>{c.nome}</span>
                  </div>
                  <ChevronRight size={14} style={{ color: '#ccc', flexShrink: 0 }} />
                </div>
                <p className="text-xs mt-0.5 truncate" style={{ color: '#888', paddingLeft: 23 }}>{c.indirizzo}</p>
                <p className="text-xs mt-1" style={{ color: '#aaa', paddingLeft: 23 }}>{c._count.condomini} condòmin{c._count.condomini === 1 ? 'o' : 'i'}</p>
              </button>
            );
          })}
        </div>

        {/* ── Dettaglio condominio ─────────────────────────────────────── */}
        <div className="flex flex-col flex-1 overflow-hidden">
          {!selected ? (
            <div className="flex flex-col items-center justify-center h-full">
              <Building2 size={48} style={{ color: '#ddd', marginBottom: 12 }} />
              <p className="text-sm font-medium" style={{ color: '#aaa' }}>Seleziona un condominio</p>
              <p className="text-xs mt-1" style={{ color: '#ccc' }}>per vedere i condòmini</p>
            </div>
          ) : (
            <div className="flex flex-col flex-1 overflow-hidden">
              {/* Intestazione */}
              <div className="flex items-center justify-between px-6 py-4 flex-shrink-0" style={{ borderBottom: '1px solid #f0f0f0', backgroundColor: '#fff' }}>
                <div>
                  <h2 className="text-base font-bold" style={{ color: '#1a1a1a' }}>{selected.nome}</h2>
                  <p className="text-xs" style={{ color: '#888' }}>{selected.indirizzo}</p>
                </div>
                <div className="flex items-center gap-2">
                  <button onClick={openEditCondominio} className="flex items-center gap-1.5 px-3 py-2 rounded-lg text-sm font-semibold" style={{ backgroundColor: '#f5f5f5', color: '#555' }}>
                    <Pencil size={13} /> Modifica
                  </button>
                  <button onClick={openAddModal} className="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold text-white hover:opacity-90" style={{ backgroundColor: 'var(--primary)' }}>
                    <Plus size={15} /> Aggiungi Condòmino
                  </button>
                </div>
              </div>

              {/* Barra filtri */}
              <div className="flex items-center gap-3 px-6 py-3 flex-shrink-0 flex-wrap" style={{ borderBottom: '1px solid #f5f5f5', backgroundColor: '#fafafa' }}>
                <div className="relative flex-1" style={{ minWidth: 160, maxWidth: 260 }}>
                  <Search size={13} className="absolute left-3 top-1/2 -translate-y-1/2" style={{ color: '#bbb' }} />
                  <input
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    placeholder="Cerca nome o unità…"
                    className="w-full pl-8 pr-3 py-1.5 rounded-lg text-xs outline-none"
                    style={{ border: '1px solid #e5e7eb', backgroundColor: '#fff' }}
                  />
                </div>
                <select value={filterTipo} onChange={(e) => setFilterTipo(e.target.value)} className="px-3 py-1.5 rounded-lg text-xs outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fff', color: '#555' }}>
                  <option value="tutti">Tutti i tipi</option>
                  {TIPO_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                </select>
                <select value={filterStato} onChange={(e) => setFilterStato(e.target.value as typeof filterStato)} className="px-3 py-1.5 rounded-lg text-xs outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fff', color: '#555' }}>
                  <option value="tutti">Tutti gli stati</option>
                  <option value="attivo">Attivi</option>
                  <option value="disattivo">Disattivi</option>
                </select>
                <span className="text-xs ml-auto" style={{ color: '#aaa' }}>{filteredCondomini.length} / {selected.condomini.length}</span>
              </div>

              {/* Lista */}
              <div className="flex-1 overflow-y-auto p-6">
                {detailLoading && <p className="text-sm text-center mt-8" style={{ color: '#aaa' }}>Caricamento...</p>}
                {!detailLoading && selected.condomini.length === 0 && (
                  <div className="text-center mt-12">
                    <Users size={36} style={{ color: '#ddd', margin: '0 auto 8px' }} />
                    <p className="text-sm font-medium" style={{ color: '#aaa' }}>Nessun condòmino</p>
                    <p className="text-xs mt-1" style={{ color: '#ccc' }}>Clicca "Aggiungi Condòmino" per iniziare</p>
                  </div>
                )}
                {!detailLoading && selected.condomini.length > 0 && filteredCondomini.length === 0 && (
                  <div className="text-center mt-12">
                    <Search size={32} style={{ color: '#ddd', margin: '0 auto 8px' }} />
                    <p className="text-sm" style={{ color: '#aaa' }}>Nessun risultato per i filtri applicati</p>
                  </div>
                )}
                {!detailLoading && filteredCondomini.length > 0 && (
                  <div className="grid grid-cols-1 gap-3" style={{ maxWidth: 760 }}>
                    {filteredCondomini.map((p) => {
                      const tipoStyle = TIPO_COLORS[p.tipo] ?? { bg: '#f5f5f5', color: '#888' };
                      const isDisattivo = p.stato === 'disattivo';
                      return (
                        <div key={p.id} className="flex items-start gap-4 rounded-xl p-4" style={{ backgroundColor: isDisattivo ? '#fafafa' : '#fff', border: `1px solid ${isDisattivo ? '#e5e7eb' : '#f0f0f0'}`, opacity: isDisattivo ? 0.7 : 1 }}>
                          <div className="flex-shrink-0 w-10 h-10 rounded-full flex items-center justify-center text-sm font-bold" style={{ backgroundColor: isDisattivo ? '#f0f0f0' : '#fef2f2', color: isDisattivo ? '#aaa' : 'var(--primary)' }}>
                            {p.nome[0]}{p.cognome[0]}
                          </div>
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-2 flex-wrap">
                              <span className="text-sm font-semibold" style={{ color: isDisattivo ? '#aaa' : '#1a1a1a' }}>{p.nome} {p.cognome}</span>
                              <span className="text-xs px-2 py-0.5 rounded-full font-semibold" style={{ backgroundColor: tipoStyle.bg, color: tipoStyle.color }}>{p.tipo.charAt(0).toUpperCase() + p.tipo.slice(1)}</span>
                              <span className="text-xs px-2 py-0.5 rounded-full font-semibold" style={{ backgroundColor: '#f0fdf4', color: '#16a34a' }}>Unità {p.unita}</span>
                              {isDisattivo && <span className="text-xs px-2 py-0.5 rounded-full font-semibold" style={{ backgroundColor: '#f5f5f5', color: '#999' }}>Disattivo</span>}
                              {p.user && <span className="text-xs px-2 py-0.5 rounded-full font-semibold" style={{ backgroundColor: '#eff6ff', color: '#2563eb' }}>@{p.user.username}</span>}
                            </div>
                            <div className="flex items-center gap-4 mt-1.5 flex-wrap">
                              {p.email && <span className="flex items-center gap-1 text-xs" style={{ color: '#888' }}><Mail size={11} />{p.email}</span>}
                              {p.telefono && <span className="flex items-center gap-1 text-xs" style={{ color: '#888' }}><Phone size={11} />{p.telefono}</span>}
                              <span className="flex items-center gap-1 text-xs" style={{ color: '#aaa' }}><Home size={11} />{p.millesimi} millesimi</span>
                            </div>
                          </div>
                          <div className="flex items-center gap-1 flex-shrink-0">
                            <button onClick={() => openEditCondomino(p)} className="p-1.5 rounded-lg hover:bg-gray-100" title="Modifica" style={{ color: '#888' }}><Pencil size={14} /></button>
                            <button onClick={() => handleToggleStato(p)} className="p-1.5 rounded-lg hover:bg-gray-100" title={isDisattivo ? 'Riattiva' : 'Disattiva'} style={{ color: isDisattivo ? '#16a34a' : '#dc2626' }}><Power size={14} /></button>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Modal: Nuovo Condominio */}
      {showCondoModal && (
        <Modal title="Nuovo Condominio" onClose={() => setShowCondoModal(false)}>
          <div className="flex flex-col gap-4">
            <Field label="Nome condominio" value={condoNome} onChange={setCondoNome} placeholder="es. Condominio Rossi" required />
            <Field label="Indirizzo" value={condoIndirizzo} onChange={setCondoIndirizzo} placeholder="es. Via Roma 12, Milano" required />
            {condoError && <p className="text-xs px-3 py-2 rounded-lg" style={{ backgroundColor: '#fef2f2', color: 'var(--primary)' }}>{condoError}</p>}
            <div className="flex gap-2 pt-2">
              <button onClick={() => setShowCondoModal(false)} className="flex-1 py-2 rounded-lg text-sm font-semibold" style={{ backgroundColor: '#f5f5f5', color: '#555' }}>Annulla</button>
              <button onClick={handleSaveCondominio} disabled={condoSaving} className="flex-1 py-2 rounded-lg text-sm font-semibold text-white hover:opacity-90" style={{ backgroundColor: 'var(--primary)', opacity: condoSaving ? 0.7 : 1 }}>
                {condoSaving ? 'Salvataggio...' : 'Crea Condominio'}
              </button>
            </div>
          </div>
        </Modal>
      )}

      {/* Modal: Modifica Condominio */}
      {showEditCondoModal && (
        <Modal title="Modifica Condominio" onClose={() => setShowEditCondoModal(false)}>
          <div className="flex flex-col gap-4">
            <Field label="Nome condominio" value={editCondoNome} onChange={setEditCondoNome} placeholder="es. Condominio Rossi" required />
            <Field label="Indirizzo" value={editCondoIndirizzo} onChange={setEditCondoIndirizzo} placeholder="es. Via Roma 12, Milano" required />
            {editCondoError && <p className="text-xs px-3 py-2 rounded-lg" style={{ backgroundColor: '#fef2f2', color: 'var(--primary)' }}>{editCondoError}</p>}
            <div className="flex gap-2 pt-2">
              <button onClick={() => setShowEditCondoModal(false)} className="flex-1 py-2 rounded-lg text-sm font-semibold" style={{ backgroundColor: '#f5f5f5', color: '#555' }}>Annulla</button>
              <button onClick={handleUpdateCondominio} disabled={editCondoSaving} className="flex-1 py-2 rounded-lg text-sm font-semibold text-white hover:opacity-90" style={{ backgroundColor: 'var(--primary)', opacity: editCondoSaving ? 0.7 : 1 }}>
                {editCondoSaving ? 'Salvataggio...' : 'Salva modifiche'}
              </button>
            </div>
          </div>
        </Modal>
      )}

      {/* Modal: Aggiungi Condòmino */}
      {showAddModal && (
        <Modal title="Aggiungi Condòmino" onClose={() => setShowAddModal(false)}>
          <div className="flex flex-col gap-3">
            {/* Tab switcher */}
            <div className="flex rounded-lg overflow-hidden border" style={{ borderColor: '#e5e7eb' }}>
              <button
                onClick={() => { setAddTab('nuovo'); setSelectedExistingUser(null); setAddError(null); }}
                className="flex-1 py-2 text-xs font-semibold transition-colors"
                style={{ backgroundColor: addTab === 'nuovo' ? 'var(--primary)' : '#f5f5f5', color: addTab === 'nuovo' ? '#fff' : '#555' }}
              >
                Nuovo condòmino
              </button>
              <button
                onClick={() => { setAddTab('esistente'); setAddError(null); }}
                className="flex-1 py-2 text-xs font-semibold transition-colors"
                style={{ backgroundColor: addTab === 'esistente' ? 'var(--primary)' : '#f5f5f5', color: addTab === 'esistente' ? '#fff' : '#555' }}
              >
                Associa utente esistente
              </button>
            </div>

            {addTab === 'esistente' && (
              <div className="flex flex-col gap-2">
                <p className="text-xs" style={{ color: '#888' }}>Seleziona un account già registrato ma non ancora associato a nessun condominio.</p>
                <div className="relative">
                  <Search size={13} className="absolute left-3 top-1/2 -translate-y-1/2" style={{ color: '#bbb' }} />
                  <input
                    value={existingUserSearch}
                    onChange={(e) => setExistingUserSearch(e.target.value)}
                    placeholder="Cerca username…"
                    className="w-full pl-8 pr-3 py-2 rounded-lg text-sm outline-none"
                    style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
                  />
                </div>
                {unassociatedLoading && <p className="text-xs text-center py-2" style={{ color: '#aaa' }}>Caricamento…</p>}
                {!unassociatedLoading && unassociatedUsers.length === 0 && (
                  <p className="text-xs text-center py-2" style={{ color: '#aaa' }}>Nessun utente non associato trovato</p>
                )}
                {!unassociatedLoading && (
                  <div className="flex flex-col gap-1 max-h-36 overflow-y-auto rounded-lg" style={{ border: '1px solid #e5e7eb' }}>
                    {unassociatedUsers
                      .filter((u) => !existingUserSearch || u.username.toLowerCase().includes(existingUserSearch.toLowerCase()))
                      .map((u) => (
                        <button
                          key={u.id}
                          onClick={() => setSelectedExistingUser(selectedExistingUser?.id === u.id ? null : u)}
                          className="text-left px-3 py-2 text-sm hover:bg-gray-50 transition-colors"
                          style={{
                            backgroundColor: selectedExistingUser?.id === u.id ? '#fef2f2' : 'transparent',
                            color: selectedExistingUser?.id === u.id ? 'var(--primary)' : '#333',
                            fontWeight: selectedExistingUser?.id === u.id ? 600 : 400,
                          }}
                        >
                          @{u.username}
                        </button>
                      ))}
                  </div>
                )}
                {selectedExistingUser && (
                  <p className="text-xs px-2 py-1 rounded" style={{ backgroundColor: '#f0fdf4', color: '#16a34a' }}>
                    Verrà associato: <strong>@{selectedExistingUser.username}</strong>
                  </p>
                )}
                <div style={{ height: 1, backgroundColor: '#f0f0f0', margin: '4px 0' }} />
                <p className="text-xs font-semibold" style={{ color: '#555' }}>Completa l&apos;anagrafica</p>
              </div>
            )}

            <CondominoForm
              form={addForm}
              setForm={setAddForm}
              showPassword={showAddPassword}
              togglePassword={() => setShowAddPassword((v) => !v)}
              isEdit={addTab === 'esistente'}
            />

            {addError && <p className="text-xs px-3 py-2 rounded-lg" style={{ backgroundColor: '#fef2f2', color: 'var(--primary)' }}>{addError}</p>}
            <div className="flex gap-2 pt-2">
              <button onClick={() => setShowAddModal(false)} className="flex-1 py-2 rounded-lg text-sm font-semibold" style={{ backgroundColor: '#f5f5f5', color: '#555' }}>Annulla</button>
              <button onClick={handleSaveAdd} disabled={addSaving} className="flex-1 py-2 rounded-lg text-sm font-semibold text-white hover:opacity-90" style={{ backgroundColor: 'var(--primary)', opacity: addSaving ? 0.7 : 1 }}>
                {addSaving ? 'Salvataggio...' : (addTab === 'esistente' ? 'Associa e aggiungi' : 'Aggiungi')}
              </button>
            </div>
          </div>
        </Modal>
      )}

      {/* Modal: Modifica Condòmino */}
      {showEditModal && editingCondomino && (
        <Modal title={`Modifica – ${editingCondomino.nome} ${editingCondomino.cognome}`} onClose={() => setShowEditModal(false)}>
          <div className="flex flex-col gap-3">
            <CondominoForm form={editForm} setForm={setEditForm} showPassword={false} togglePassword={() => {}} isEdit={true} />
            {editError && <p className="text-xs px-3 py-2 rounded-lg" style={{ backgroundColor: '#fef2f2', color: 'var(--primary)' }}>{editError}</p>}
            <div className="flex gap-2 pt-2">
              <button onClick={() => setShowEditModal(false)} className="flex-1 py-2 rounded-lg text-sm font-semibold" style={{ backgroundColor: '#f5f5f5', color: '#555' }}>Annulla</button>
              <button onClick={handleSaveEdit} disabled={editSaving} className="flex-1 py-2 rounded-lg text-sm font-semibold text-white hover:opacity-90" style={{ backgroundColor: 'var(--primary)', opacity: editSaving ? 0.7 : 1 }}>
                {editSaving ? 'Salvataggio...' : 'Salva modifiche'}
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}
