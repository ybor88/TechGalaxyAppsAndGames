'use client';

import { useEffect, useState } from 'react';
import { Wrench, Trash2, Plus, Edit2, Mail, Phone, MapPin } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { fetchFornitori, createFornitore, deleteFornitore, updateFornitore, FornitoreItem } from '@/lib/api';

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center" style={{ backgroundColor: 'rgba(0,0,0,0.45)' }} onClick={onClose}>
      <div className="w-full rounded-2xl p-6 shadow-2xl" style={{ maxWidth: 640, backgroundColor: '#fff' }} onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-bold" style={{ color: '#1a1a1a' }}>{title}</h3>
          <button onClick={onClose} style={{ color: '#aaa' }}>✕</button>
        </div>
        {children}
      </div>
    </div>
  );
}

export default function FornitoriPage() {
  const { token } = useAuth();
  const [fornitori, setFornitori] = useState<FornitoreItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<FornitoreItem | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    setLoading(true);
    setError(null);
    fetchFornitori(token)
      .then((d) => setFornitori(d))
      .catch((e) => setError((e as Error).message || 'Errore caricamento fornitori'))
      .finally(() => setLoading(false));
  }, [token]);

  const filtered = fornitori.filter((f) => f.nome.toLowerCase().includes(query.toLowerCase()) || (f.tipo ?? '').toLowerCase().includes(query.toLowerCase()));

  const handleSave = async (payload: Partial<FornitoreItem>) => {
    if (!token) return;
    setSaving(true);
    try {
      if (editing) {
        const updated = await updateFornitore(token, editing.id, payload as any);
        setFornitori((p) => p.map((x) => x.id === updated.id ? updated : x));
      } else {
        const created = await createFornitore(token, payload as any);
        setFornitori((p) => [created, ...p]);
      }
      setShowModal(false);
      setEditing(null);
    } catch (e) {
      alert((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!token) return;
    if (!confirm('Eliminare fornitore?')) return;
    await deleteFornitore(token, id);
    setFornitori((p) => p.filter((x) => x.id !== id));
  };

  return (
    <div className="flex flex-col flex-1 overflow-hidden" style={{ backgroundColor: 'var(--background)' }}>
      {/* Header */}
      <header className="flex items-center justify-between px-6 py-3 border-b flex-shrink-0" style={{ backgroundColor: '#fff', borderColor: '#f0f0f0' }}>
        <div>
          <h1 className="text-sm font-bold" style={{ color: '#1a1a1a' }}>Fornitori</h1>
          <p className="text-xs" style={{ color: '#888' }}>Gestione fornitori e interventi</p>
        </div>
        <div className="flex items-center gap-3">
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Cerca per nome o tipo" className="px-3 py-2 rounded-lg text-sm outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }} />
          <button onClick={() => { setShowModal(true); setEditing(null); }} className="flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-semibold text-white" style={{ backgroundColor: 'var(--primary)' }}>
            <Plus size={14} /> Aggiungi
          </button>
        </div>
      </header>

      <main className="flex-1 p-6 overflow-auto">
        {loading ? (
          <p>Caricamento fornitori...</p>
        ) : error ? (
          <div className="rounded-xl p-6 text-center" style={{ backgroundColor: '#fef2f2', border: '1px solid #fecaca' }}>
            <p className="font-semibold" style={{ color: '#dc2626' }}>Impossibile caricare i fornitori</p>
            <p className="text-sm mt-1" style={{ color: '#6b7280' }}>{error}</p>
          </div>
        ) : filtered.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-64 rounded-xl" style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0' }}>
            <Wrench size={36} style={{ color: 'var(--primary)' }} />
            <p className="text-sm font-semibold mt-3">Nessun fornitore</p>
            <p className="text-xs text-gray-500">Aggiungi il primo fornitore per iniziare a gestire gli interventi</p>
            <div className="mt-4">
              <button onClick={() => { setShowModal(true); setEditing(null); }} className="px-4 py-2 rounded-lg text-sm font-semibold text-white" style={{ backgroundColor: 'var(--primary)' }}>Aggiungi fornitore</button>
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {filtered.map((f) => (
              <div key={f.id} className="p-4 rounded-2xl transition-shadow hover:shadow-sm" style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0' }}>
                <div className="flex items-start gap-3">
                  <div className="flex-shrink-0 w-10 h-10 rounded-lg flex items-center justify-center" style={{ backgroundColor: '#f3f4f6' }}>
                    <Wrench size={18} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-2">
                      <div className="text-sm font-semibold truncate" style={{ color: '#1a1a1a' }}>{f.nome}</div>
                      <div className="flex gap-2">
                        <button onClick={() => { setEditing(f); setShowModal(true); }} className="p-1.5 rounded-lg" style={{ border: '1px solid #e5e7eb', color: '#888' }}>
                          <Edit2 size={12} />
                        </button>
                        <button onClick={() => handleDelete(f.id)} className="p-1.5 rounded-lg" style={{ backgroundColor: '#fef2f2', color: '#dc2626' }}>
                          <Trash2 size={12} />
                        </button>
                      </div>
                    </div>
                    <div className="text-xs mt-1 flex flex-col gap-1" style={{ color: '#666' }}>
                      {f.tipo && <div className="flex items-center gap-2"><Wrench size={12} /> <span className="text-xs">{f.tipo}</span></div>}
                      {f.email && <div className="flex items-center gap-2"><Mail size={12} /> <span className="text-xs">{f.email}</span></div>}
                      {f.telefono && <div className="flex items-center gap-2"><Phone size={12} /> <span className="text-xs">{f.telefono}</span></div>}
                      {f.indirizzo && <div className="flex items-center gap-2"><MapPin size={12} /> <span className="text-xs">{f.indirizzo}</span></div>}
                      {f.note && <div className="text-xs mt-1" style={{ color: '#999' }}>{f.note}</div>}
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {showModal && (
          <Modal title={editing ? 'Modifica fornitore' : 'Nuovo fornitore'} onClose={() => { setShowModal(false); setEditing(null); }}>
            <FornitoreForm initial={editing ?? undefined} onCancel={() => { setShowModal(false); setEditing(null); }} onSave={handleSave} saving={saving} />
          </Modal>
        )}
      </main>
    </div>
  );
}

function FornitoreForm({ initial, onCancel, onSave, saving }: { initial?: FornitoreItem; onCancel: () => void; onSave: (data: Partial<FornitoreItem>) => void; saving: boolean }) {
  const [nome, setNome] = useState(initial?.nome ?? '');
  const [tipo, setTipo] = useState(initial?.tipo ?? '');
  const [email, setEmail] = useState(initial?.email ?? '');
  const [telefono, setTelefono] = useState(initial?.telefono ?? '');
  const [indirizzo, setIndirizzo] = useState(initial?.indirizzo ?? '');
  const [note, setNote] = useState(initial?.note ?? '');

  return (
    <div className="space-y-4">
      <div>
        <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Nome *</label>
        <input value={nome} onChange={(e) => setNome(e.target.value)} className="w-full px-3 py-2 rounded-lg text-sm outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }} />
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Tipo</label>
          <input value={tipo} onChange={(e) => setTipo(e.target.value)} className="w-full px-3 py-2 rounded-lg text-sm outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }} />
        </div>
        <div>
          <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Email</label>
          <input value={email} onChange={(e) => setEmail(e.target.value)} className="w-full px-3 py-2 rounded-lg text-sm outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }} />
        </div>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Telefono</label>
          <input value={telefono} onChange={(e) => setTelefono(e.target.value)} className="w-full px-3 py-2 rounded-lg text-sm outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }} />
        </div>
        <div>
          <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Indirizzo</label>
          <input value={indirizzo} onChange={(e) => setIndirizzo(e.target.value)} className="w-full px-3 py-2 rounded-lg text-sm outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }} />
        </div>
      </div>
      <div>
        <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Note</label>
        <textarea value={note} onChange={(e) => setNote(e.target.value)} className="w-full px-3 py-2 rounded-lg text-sm outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }} rows={3} />
      </div>

      <div className="flex justify-end gap-2 pt-2">
        <button onClick={onCancel} className="px-4 py-2 rounded-lg text-sm" style={{ border: '1px solid #e5e7eb', color: '#666' }}>Annulla</button>
        <button onClick={() => onSave({ nome, tipo, email, telefono, indirizzo, note })} disabled={saving || !nome.trim()} className="px-4 py-2 rounded-lg text-sm font-semibold text-white" style={{ backgroundColor: 'var(--primary)', opacity: saving ? 0.7 : 1 }}>{saving ? 'Salvataggio...' : (initial ? 'Salva' : 'Crea')}</button>
      </div>
    </div>
  );
}
