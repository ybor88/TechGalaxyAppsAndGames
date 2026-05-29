'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import {
  Wrench, Plus, X, ChevronRight, AlertTriangle, Clock, CheckCircle,
  Zap, Search, Filter, Trash2, MessageSquare, Send, User,
  Image as ImageIcon, Building2,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import {
  fetchCondominii, fetchTickets, fetchMieiTickets, createTicket,
  updateTicket, deleteTicket, addTicketNota,
  CondominioListItem, TicketItem,
} from '@/lib/api';

// ─── Costanti ─────────────────────────────────────────────────────────────────

const CATEGORIE = ['idraulica', 'elettrica', 'pulizie', 'sicurezza', 'strutturale', 'altro'];

const STATI = ['Nuova', 'In lavorazione', 'Assegnata', 'Risolta', 'Chiusa'];

const PRIORITA_CFG: Record<string, { label: string; bg: string; color: string; dot: string }> = {
  bassa:   { label: 'Bassa',   bg: '#f5f5f5', color: '#888',    dot: '#aaa' },
  media:   { label: 'Media',   bg: '#eff6ff', color: '#2563eb', dot: '#2563eb' },
  alta:    { label: 'Alta',    bg: '#fffbeb', color: '#d97706', dot: '#d97706' },
  urgente: { label: 'Urgente', bg: '#fef2f2', color: '#dc2626', dot: '#dc2626' },
};

const STATO_CFG: Record<string, { label: string; color: string; bg: string; icon: React.ReactNode }> = {
  'Nuova':          { label: 'Nuova',          color: '#2563eb', bg: '#eff6ff', icon: <Plus size={11} /> },
  'In lavorazione': { label: 'In lavorazione', color: '#d97706', bg: '#fffbeb', icon: <Clock size={11} /> },
  'Assegnata':      { label: 'Assegnata',      color: '#7c3aed', bg: '#faf5ff', icon: <User size={11} /> },
  'Risolta':        { label: 'Risolta',        color: '#16a34a', bg: '#f0fdf4', icon: <CheckCircle size={11} /> },
  'Chiusa':         { label: 'Chiusa',         color: '#888',    bg: '#f5f5f5', icon: <CheckCircle size={11} /> },
};

const CAT_ICON: Record<string, string> = {
  idraulica: '💧', elettrica: '⚡', pulizie: '🧹',
  sicurezza: '🔒', strutturale: '🏗️', altro: '📌',
};

function fmtDate(s: string) {
  return new Date(s).toLocaleDateString('it-IT', { day: '2-digit', month: 'short', year: 'numeric' });
}

// ─── Componenti riutilizzabili ────────────────────────────────────────────────

function Modal({ title, onClose, children, wide }: { title: string; onClose: () => void; children: React.ReactNode; wide?: boolean }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center" style={{ backgroundColor: 'rgba(0,0,0,0.45)' }} onClick={onClose}>
      <div
        className="w-full rounded-2xl p-6 shadow-2xl"
        style={{ maxWidth: wide ? 720 : 500, backgroundColor: '#fff', maxHeight: '92vh', overflowY: 'auto' }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between mb-5">
          <h2 className="text-sm font-bold" style={{ color: '#1a1a1a' }}>{title}</h2>
          <button onClick={onClose} style={{ color: '#aaa' }}><X size={18} /></button>
        </div>
        {children}
      </div>
    </div>
  );
}

function PrioritaBadge({ priorita }: { priorita: string }) {
  const cfg = PRIORITA_CFG[priorita] ?? PRIORITA_CFG['media'];
  return (
    <span className="flex items-center gap-1 text-xs px-2 py-0.5 rounded-full font-semibold" style={{ backgroundColor: cfg.bg, color: cfg.color }}>
      <span className="w-1.5 h-1.5 rounded-full inline-block" style={{ backgroundColor: cfg.dot }} />
      {cfg.label}
    </span>
  );
}

function StatoBadge({ stato }: { stato: string }) {
  const cfg = STATO_CFG[stato] ?? { label: stato, color: '#888', bg: '#f5f5f5', icon: null };
  return (
    <span className="flex items-center gap-1 text-xs px-2 py-0.5 rounded-full font-semibold" style={{ backgroundColor: cfg.bg, color: cfg.color }}>
      {cfg.icon}{cfg.label}
    </span>
  );
}

// Barra di avanzamento stato
function StatoProgress({ stato }: { stato: string }) {
  const idx = STATI.indexOf(stato);
  return (
    <div className="flex items-center gap-0 mt-2">
      {STATI.map((s, i) => {
        const done = i <= idx;
        const active = i === idx;
        return (
          <div key={s} className="flex items-center flex-1">
            <div
              className="w-3 h-3 rounded-full flex-shrink-0 border-2"
              style={{
                backgroundColor: done ? 'var(--primary)' : '#e5e7eb',
                borderColor: done ? 'var(--primary)' : '#e5e7eb',
                transform: active ? 'scale(1.3)' : 'scale(1)',
              }}
            />
            {i < STATI.length - 1 && (
              <div className="h-0.5 flex-1" style={{ backgroundColor: i < idx ? 'var(--primary)' : '#e5e7eb' }} />
            )}
          </div>
        );
      })}
    </div>
  );
}

// ─── Vista Amministratore ─────────────────────────────────────────────────────

function AdminView({ token }: { token: string }) {
  const [condominii, setCondominii] = useState<CondominioListItem[]>([]);
  const [selectedCondo, setSelectedCondo] = useState<CondominioListItem | null>(null);
  const [tickets, setTickets] = useState<TicketItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<TicketItem | null>(null);

  // Filtri
  const [search, setSearch] = useState('');
  const [fStato, setFStato] = useState('tutti');
  const [fPriorita, setFPriorita] = useState('tutti');
  const [fCategoria, setFCategoria] = useState('tutti');

  // Edit panel
  const [editStato, setEditStato] = useState('');
  const [editPriorita, setEditPriorita] = useState('');
  const [editAssegnatoa, setEditAssegnatoa] = useState('');
  const [saving, setSaving] = useState(false);

  // Nota
  const [notaTesto, setNotaTesto] = useState('');
  const [addingNota, setAddingNota] = useState(false);

  // Nuovo ticket modal
  const [showNew, setShowNew] = useState(false);

  useEffect(() => {
    fetchCondominii(token).then((data) => {
      setCondominii(data);
      if (data.length === 1) selectCondo(data[0]);
    });
  }, [token]); // eslint-disable-line react-hooks/exhaustive-deps

  const loadTickets = useCallback(async (condoId: number) => {
    setLoading(true);
    try {
      const data = await fetchTickets(token, condoId, {
        stato: fStato !== 'tutti' ? fStato : undefined,
        priorita: fPriorita !== 'tutti' ? fPriorita : undefined,
        categoria: fCategoria !== 'tutti' ? fCategoria : undefined,
      });
      setTickets(data);
      setSelected((prev) => prev ? data.find((t) => t.id === prev.id) ?? null : null);
    } finally {
      setLoading(false);
    }
  }, [token, fStato, fPriorita, fCategoria]);

  const selectCondo = useCallback((c: CondominioListItem) => {
    setSelectedCondo(c);
    setSelected(null);
    loadTickets(c.id);
  }, [loadTickets]);

  useEffect(() => {
    if (selectedCondo) loadTickets(selectedCondo.id);
  }, [fStato, fPriorita, fCategoria]); // eslint-disable-line react-hooks/exhaustive-deps

  const openTicket = (t: TicketItem) => {
    setSelected(t);
    setEditStato(t.stato);
    setEditPriorita(t.priorita);
    setEditAssegnatoa(t.assegnatoa ?? '');
    setNotaTesto('');
  };

  const handleSave = async () => {
    if (!selected) return;
    setSaving(true);
    try {
      const updated = await updateTicket(token, selected.id, {
        stato: editStato,
        priorita: editPriorita,
        assegnatoa: editAssegnatoa.trim() || null,
      });
      setSelected(updated);
      setTickets((prev) => prev.map((t) => t.id === updated.id ? updated : t));
    } finally {
      setSaving(false);
    }
  };

  const handleAddNota = async () => {
    if (!selected || !notaTesto.trim()) return;
    setAddingNota(true);
    try {
      const nota = await addTicketNota(token, selected.id, notaTesto);
      setSelected((prev) => prev ? { ...prev, note: [...prev.note, nota], _count: { note: prev._count.note + 1 } } : null);
      setNotaTesto('');
    } finally {
      setAddingNota(false);
    }
  };

  const handleDelete = async (t: TicketItem) => {
    if (!confirm(`Eliminare il ticket "${t.titolo}"?`)) return;
    await deleteTicket(token, t.id);
    setTickets((prev) => prev.filter((x) => x.id !== t.id));
    if (selected?.id === t.id) setSelected(null);
  };

  // Filtra lato client per search
  const displayed = tickets.filter((t) => {
    if (!search) return true;
    const q = search.toLowerCase();
    return t.titolo.toLowerCase().includes(q)
      || t.categoria.toLowerCase().includes(q)
      || (t.apertoCondomino && `${t.apertoCondomino.nome} ${t.apertoCondomino.cognome}`.toLowerCase().includes(q));
  });

  return (
    <div className="flex flex-col flex-1 overflow-hidden">
      {/* Header */}
      <header className="flex items-center justify-between px-6 py-3 border-b flex-shrink-0" style={{ backgroundColor: '#fff', borderColor: '#f0f0f0' }}>
        <div>
          <h1 className="text-sm font-bold" style={{ color: '#1a1a1a' }}>Segnalazioni & Ticket</h1>
          <p className="text-xs" style={{ color: '#888' }}>Gestione interventi di manutenzione</p>
        </div>
        <div className="flex items-center gap-2">
          {/* Selector condominio */}
          <select
            value={selectedCondo?.id ?? ''}
            onChange={(e) => { const c = condominii.find((x) => x.id === Number(e.target.value)); if (c) selectCondo(c); }}
            className="px-3 py-2 rounded-lg text-xs outline-none"
            style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
          >
            <option value="">Seleziona condominio…</option>
            {condominii.map((c) => <option key={c.id} value={c.id}>{c.nome}</option>)}
          </select>
          {selectedCondo && (
            <button
              onClick={() => setShowNew(true)}
              className="flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-semibold text-white"
              style={{ backgroundColor: 'var(--primary)' }}
            >
              <Plus size={14} /> Nuova
            </button>
          )}
        </div>
      </header>

      {/* Barra filtri */}
      {selectedCondo && (
        <div className="flex items-center gap-2 px-6 py-2.5 flex-shrink-0 flex-wrap" style={{ backgroundColor: '#fafafa', borderBottom: '1px solid #f0f0f0' }}>
          <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg" style={{ backgroundColor: '#fff', border: '1px solid #e5e7eb', flex: '1 1 180px' }}>
            <Search size={13} style={{ color: '#aaa' }} />
            <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Cerca titolo, condomino…" className="text-xs outline-none w-full bg-transparent" style={{ color: '#333' }} />
          </div>
          {/* Filtro stato */}
          <select value={fStato} onChange={(e) => setFStato(e.target.value)} className="px-2 py-1.5 rounded-lg text-xs outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fff' }}>
            <option value="tutti">Tutti gli stati</option>
            {STATI.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
          {/* Filtro priorità */}
          <select value={fPriorita} onChange={(e) => setFPriorita(e.target.value)} className="px-2 py-1.5 rounded-lg text-xs outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fff' }}>
            <option value="tutti">Tutte le priorità</option>
            {Object.entries(PRIORITA_CFG).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
          </select>
          {/* Filtro categoria */}
          <select value={fCategoria} onChange={(e) => setFCategoria(e.target.value)} className="px-2 py-1.5 rounded-lg text-xs outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fff' }}>
            <option value="tutti">Tutte le categorie</option>
            {CATEGORIE.map((c) => <option key={c} value={c}>{CAT_ICON[c]} {c.charAt(0).toUpperCase() + c.slice(1)}</option>)}
          </select>
          <span className="text-xs ml-1" style={{ color: '#aaa' }}>{displayed.length} ticket</span>
        </div>
      )}

      {!selectedCondo ? (
        <div className="flex flex-1 items-center justify-center flex-col gap-2">
          <Building2 size={40} style={{ color: '#ddd' }} />
          <p className="text-sm" style={{ color: '#aaa' }}>Seleziona un condominio</p>
        </div>
      ) : (
        <div className="flex flex-1 overflow-hidden">
          {/* Lista ticket */}
          <div className="flex flex-col flex-shrink-0 overflow-y-auto" style={{ width: 360, borderRight: '1px solid #f0f0f0' }}>
            {loading && <p className="text-xs text-center mt-8" style={{ color: '#aaa' }}>Caricamento...</p>}
            {!loading && displayed.length === 0 && (
              <div className="flex flex-col items-center justify-center py-20 gap-2">
                <Wrench size={32} style={{ color: '#ddd' }} />
                <p className="text-sm" style={{ color: '#aaa' }}>Nessuna segnalazione</p>
              </div>
            )}
            {displayed.map((t) => {
              const isActive = selected?.id === t.id;
              return (
                <button
                  key={t.id}
                  onClick={() => openTicket(t)}
                  className="w-full text-left p-4 transition-all"
                  style={{
                    backgroundColor: isActive ? '#fef2f2' : '#fff',
                    borderBottom: '1px solid #f5f5f5',
                    borderLeft: isActive ? '3px solid var(--primary)' : '3px solid transparent',
                  }}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-1.5 mb-1">
                        <span className="text-sm">{CAT_ICON[t.categoria]}</span>
                        <span className="text-xs font-bold truncate" style={{ color: '#1a1a1a' }}>{t.titolo}</span>
                      </div>
                      <div className="flex items-center gap-2 flex-wrap">
                        <PrioritaBadge priorita={t.priorita} />
                        <StatoBadge stato={t.stato} />
                      </div>
                      {t.apertoCondomino && (
                        <p className="text-xs mt-1" style={{ color: '#aaa' }}>
                          {t.apertoCondomino.nome} {t.apertoCondomino.cognome} · Unità {t.apertoCondomino.unita}
                        </p>
                      )}
                    </div>
                    <div className="flex flex-col items-end gap-1 flex-shrink-0">
                      <span className="text-xs" style={{ color: '#ccc' }}>{fmtDate(t.dataApertura)}</span>
                      {t._count.note > 0 && (
                        <span className="flex items-center gap-0.5 text-xs" style={{ color: '#aaa' }}>
                          <MessageSquare size={10} />{t._count.note}
                        </span>
                      )}
                    </div>
                  </div>
                </button>
              );
            })}
          </div>

          {/* Pannello dettaglio / edit */}
          {!selected ? (
            <div className="flex flex-1 items-center justify-center flex-col gap-2">
              <ChevronRight size={36} style={{ color: '#ddd' }} />
              <p className="text-sm" style={{ color: '#aaa' }}>Seleziona un ticket per gestirlo</p>
            </div>
          ) : (
            <div className="flex flex-col flex-1 overflow-y-auto p-6" style={{ backgroundColor: '#fafafa' }}>
              <div className="rounded-2xl p-5 mb-4" style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0', maxWidth: 680 }}>
                {/* Intestazione */}
                <div className="flex items-start justify-between gap-3 mb-3">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-lg">{CAT_ICON[selected.categoria]}</span>
                      <h2 className="text-base font-bold" style={{ color: '#1a1a1a' }}>{selected.titolo}</h2>
                    </div>
                    <div className="flex items-center gap-2 flex-wrap">
                      <PrioritaBadge priorita={selected.priorita} />
                      <StatoBadge stato={selected.stato} />
                      <span className="text-xs" style={{ color: '#aaa' }}>#TK-{String(selected.id).padStart(4, '0')}</span>
                    </div>
                  </div>
                  <button onClick={() => handleDelete(selected)} className="p-1.5 rounded-lg" style={{ backgroundColor: '#fef2f2', color: '#dc2626' }}>
                    <Trash2 size={14} />
                  </button>
                </div>

                {/* Avanzamento stato */}
                <StatoProgress stato={selected.stato} />

                {/* Info */}
                <div className="mt-4 space-y-1.5">
                  {selected.descrizione && <p className="text-sm" style={{ color: '#444' }}>{selected.descrizione}</p>}
                  {selected.apertoCondomino && (
                    <p className="text-xs" style={{ color: '#aaa' }}>
                      Aperto da: <strong style={{ color: '#555' }}>{selected.apertoCondomino.nome} {selected.apertoCondomino.cognome}</strong> (Unità {selected.apertoCondomino.unita})
                    </p>
                  )}
                  <p className="text-xs" style={{ color: '#aaa' }}>Aperto il: <strong style={{ color: '#555' }}>{fmtDate(selected.dataApertura)}</strong></p>
                  {selected.assegnatoa && <p className="text-xs" style={{ color: '#aaa' }}>Assegnato a: <strong style={{ color: '#555' }}>{selected.assegnatoa}</strong></p>}
                  {selected.dataChiusura && <p className="text-xs" style={{ color: '#aaa' }}>Chiuso il: <strong style={{ color: '#555' }}>{fmtDate(selected.dataChiusura)}</strong></p>}
                </div>

                {/* Foto allegata */}
                {selected.foto && (
                  <div className="mt-3">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src={selected.foto} alt="allegato" className="rounded-xl max-h-48 object-cover" style={{ border: '1px solid #f0f0f0' }} />
                  </div>
                )}
              </div>

              {/* Gestione ticket */}
              <div className="rounded-2xl p-5 mb-4" style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0', maxWidth: 680 }}>
                <h3 className="text-xs font-bold uppercase tracking-widest mb-3" style={{ color: '#bbb' }}>Gestione</h3>
                <div className="grid grid-cols-2 gap-3 mb-3">
                  <div>
                    <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Stato</label>
                    <select value={editStato} onChange={(e) => setEditStato(e.target.value)} className="w-full px-3 py-2 rounded-lg text-sm outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}>
                      {STATI.map((s) => <option key={s} value={s}>{s}</option>)}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Priorità</label>
                    <select value={editPriorita} onChange={(e) => setEditPriorita(e.target.value)} className="w-full px-3 py-2 rounded-lg text-sm outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}>
                      {Object.entries(PRIORITA_CFG).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
                    </select>
                  </div>
                </div>
                <div className="mb-3">
                  <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Assegnato a (fornitore / tecnico)</label>
                  <input value={editAssegnatoa} onChange={(e) => setEditAssegnatoa(e.target.value)} placeholder="Nome fornitore o tecnico…" className="w-full px-3 py-2 rounded-lg text-sm outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }} />
                </div>
                <button onClick={handleSave} disabled={saving} className="w-full py-2 rounded-lg text-sm font-semibold text-white" style={{ backgroundColor: 'var(--primary)', opacity: saving ? 0.7 : 1 }}>
                  {saving ? 'Salvataggio...' : 'Salva modifiche'}
                </button>
              </div>

              {/* Note interne */}
              <div className="rounded-2xl p-5" style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0', maxWidth: 680 }}>
                <h3 className="text-xs font-bold uppercase tracking-widest mb-3" style={{ color: '#bbb' }}>Note interne ({selected.note.length})</h3>
                <div className="flex flex-col gap-2 mb-3 max-h-48 overflow-y-auto">
                  {selected.note.length === 0 && <p className="text-xs" style={{ color: '#ccc' }}>Nessuna nota aggiunta</p>}
                  {selected.note.map((n) => (
                    <div key={n.id} className="rounded-xl p-3" style={{ backgroundColor: '#fafafa', border: '1px solid #f0f0f0' }}>
                      <div className="flex justify-between items-center mb-1">
                        <span className="text-xs font-semibold" style={{ color: '#555' }}>{n.autore}</span>
                        <span className="text-xs" style={{ color: '#ccc' }}>{fmtDate(n.createdAt)}</span>
                      </div>
                      <p className="text-xs" style={{ color: '#333' }}>{n.testo}</p>
                    </div>
                  ))}
                </div>
                <div className="flex gap-2">
                  <input
                    value={notaTesto}
                    onChange={(e) => setNotaTesto(e.target.value)}
                    onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleAddNota(); } }}
                    placeholder="Aggiungi nota interna…"
                    className="flex-1 px-3 py-2 rounded-lg text-sm outline-none"
                    style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
                  />
                  <button onClick={handleAddNota} disabled={addingNota || !notaTesto.trim()} className="px-3 py-2 rounded-lg" style={{ backgroundColor: 'var(--primary)', color: '#fff', opacity: (!notaTesto.trim() || addingNota) ? 0.5 : 1 }}>
                    <Send size={14} />
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Modal nuova segnalazione (admin) */}
      {showNew && selectedCondo && (
        <NewTicketModal
          token={token}
          condominioId={selectedCondo.id}
          onClose={() => setShowNew(false)}
          onCreated={(t) => { setTickets((prev) => [t, ...prev]); setShowNew(false); openTicket(t); }}
        />
      )}
    </div>
  );
}

// ─── Vista Condomino ──────────────────────────────────────────────────────────

function CondominoView({ token }: { token: string }) {
  const [tickets, setTickets] = useState<TicketItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [showNew, setShowNew] = useState(false);
  const [selected, setSelected] = useState<TicketItem | null>(null);
  const [filterStato, setFilterStato] = useState('tutti');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchMieiTickets(token);
      setTickets(data);
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => { load(); }, [load]);

  const displayed = filterStato === 'tutti' ? tickets : tickets.filter((t) => t.stato === filterStato);

  const statoCount = (s: string) => (s === 'tutti' ? tickets.length : tickets.filter((t) => t.stato === s).length);

  return (
    <div className="flex flex-col flex-1 overflow-hidden">
      {/* Header */}
      <header className="flex items-center justify-between px-6 py-3 border-b flex-shrink-0" style={{ backgroundColor: '#fff', borderColor: '#f0f0f0' }}>
        <div>
          <h1 className="text-sm font-bold" style={{ color: '#1a1a1a' }}>Le mie Segnalazioni</h1>
          <p className="text-xs" style={{ color: '#888' }}>Monitora lo stato delle tue richieste</p>
        </div>
        <button onClick={() => setShowNew(true)} className="flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-semibold text-white" style={{ backgroundColor: 'var(--primary)' }}>
          <Plus size={14} /> Nuova Segnalazione
        </button>
      </header>

      {/* Filtri stato */}
      <div className="flex items-center gap-2 px-6 py-2.5 flex-shrink-0" style={{ backgroundColor: '#fafafa', borderBottom: '1px solid #f0f0f0' }}>
        {['tutti', ...STATI].map((s) => (
          <button
            key={s}
            onClick={() => setFilterStato(s)}
            className="px-3 py-1 rounded-lg text-xs font-semibold"
            style={{ backgroundColor: filterStato === s ? 'var(--primary)' : '#f0f0f0', color: filterStato === s ? '#fff' : '#666' }}
          >
            {s === 'tutti' ? 'Tutte' : s} ({statoCount(s)})
          </button>
        ))}
      </div>

      <div className="flex flex-1 overflow-hidden">
        {/* Lista */}
        <div className="flex-1 overflow-y-auto p-5">
          {loading && <p className="text-sm text-center mt-8" style={{ color: '#aaa' }}>Caricamento...</p>}
          {!loading && displayed.length === 0 && (
            <div className="flex flex-col items-center justify-center py-20 gap-3">
              <Wrench size={40} style={{ color: '#ddd' }} />
              <p className="text-sm font-medium" style={{ color: '#aaa' }}>Nessuna segnalazione</p>
              <button onClick={() => setShowNew(true)} className="flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-semibold text-white" style={{ backgroundColor: 'var(--primary)' }}>
                <Plus size={13} /> Apri segnalazione
              </button>
            </div>
          )}
          <div className="grid grid-cols-1 gap-3" style={{ maxWidth: 720 }}>
            {displayed.map((t) => (
              <button
                key={t.id}
                onClick={() => setSelected(t)}
                className="w-full text-left rounded-2xl p-4 transition-all hover:shadow-sm"
                style={{ backgroundColor: '#fff', border: `1.5px solid ${selected?.id === t.id ? 'var(--primary)' : '#f0f0f0'}` }}
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-2">
                      <span className="text-lg">{CAT_ICON[t.categoria]}</span>
                      <span className="text-sm font-bold" style={{ color: '#1a1a1a' }}>{t.titolo}</span>
                    </div>
                    <div className="flex items-center gap-2 flex-wrap mb-2">
                      <PrioritaBadge priorita={t.priorita} />
                      <StatoBadge stato={t.stato} />
                    </div>
                    <StatoProgress stato={t.stato} />
                    {t.descrizione && <p className="text-xs mt-2 line-clamp-2" style={{ color: '#888' }}>{t.descrizione}</p>}
                  </div>
                  <div className="flex-shrink-0 text-right">
                    <p className="text-xs" style={{ color: '#ccc' }}>{fmtDate(t.dataApertura)}</p>
                    {t._count.note > 0 && (
                      <div className="flex items-center gap-0.5 mt-1 justify-end" style={{ color: '#aaa' }}>
                        <MessageSquare size={10} />
                        <span className="text-xs">{t._count.note}</span>
                      </div>
                    )}
                  </div>
                </div>
              </button>
            ))}
          </div>
        </div>

        {/* Pannello dettaglio (condomino) */}
        {selected && (
          <div className="flex-shrink-0 overflow-y-auto p-5" style={{ width: 320, borderLeft: '1px solid #f0f0f0', backgroundColor: '#fafafa' }}>
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-bold" style={{ color: '#1a1a1a' }}>Dettaglio</h3>
              <button onClick={() => setSelected(null)} style={{ color: '#aaa' }}><X size={16} /></button>
            </div>
            <div className="rounded-xl p-4 mb-3" style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0' }}>
              <div className="flex items-center gap-2 mb-2">
                <span className="text-xl">{CAT_ICON[selected.categoria]}</span>
                <span className="text-sm font-bold" style={{ color: '#1a1a1a' }}>{selected.titolo}</span>
              </div>
              <StatoProgress stato={selected.stato} />
              <div className="mt-3 flex flex-col gap-1">
                <div className="flex justify-between"><span className="text-xs" style={{ color: '#aaa' }}>Stato</span><StatoBadge stato={selected.stato} /></div>
                <div className="flex justify-between"><span className="text-xs" style={{ color: '#aaa' }}>Priorità</span><PrioritaBadge priorita={selected.priorita} /></div>
                <div className="flex justify-between"><span className="text-xs" style={{ color: '#aaa' }}>Aperto il</span><span className="text-xs font-semibold" style={{ color: '#555' }}>{fmtDate(selected.dataApertura)}</span></div>
                {selected.assegnatoa && <div className="flex justify-between"><span className="text-xs" style={{ color: '#aaa' }}>Assegnato a</span><span className="text-xs font-semibold" style={{ color: '#555' }}>{selected.assegnatoa}</span></div>}
                {selected.dataChiusura && <div className="flex justify-between"><span className="text-xs" style={{ color: '#aaa' }}>Chiuso il</span><span className="text-xs font-semibold" style={{ color: '#555' }}>{fmtDate(selected.dataChiusura)}</span></div>}
              </div>
              {selected.descrizione && <p className="text-xs mt-3 p-2 rounded-lg" style={{ color: '#555', backgroundColor: '#fafafa' }}>{selected.descrizione}</p>}
              {selected.foto && (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={selected.foto} alt="allegato" className="rounded-xl mt-3 w-full object-cover max-h-32" />
              )}
            </div>
            {/* Note */}
            {selected.note.length > 0 && (
              <div className="rounded-xl p-4" style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0' }}>
                <h4 className="text-xs font-bold uppercase tracking-widest mb-2" style={{ color: '#bbb' }}>Note dall'amministratore</h4>
                <div className="flex flex-col gap-2 max-h-48 overflow-y-auto">
                  {selected.note.map((n) => (
                    <div key={n.id} className="rounded-lg p-2" style={{ backgroundColor: '#fafafa' }}>
                      <p className="text-xs font-semibold mb-0.5" style={{ color: '#888' }}>{n.autore} · {fmtDate(n.createdAt)}</p>
                      <p className="text-xs" style={{ color: '#333' }}>{n.testo}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {showNew && (
        <NewTicketModal
          token={token}
          onClose={() => setShowNew(false)}
          onCreated={(t) => { setTickets((prev) => [t, ...prev]); setShowNew(false); setSelected(t); }}
        />
      )}
    </div>
  );
}

// ─── Modal nuova segnalazione ──────────────────────────────────────────────────

function NewTicketModal({
  token, condominioId, onClose, onCreated,
}: {
  token: string;
  condominioId?: number;
  onClose: () => void;
  onCreated: (t: TicketItem) => void;
}) {
  const [titolo, setTitolo] = useState('');
  const [descrizione, setDescrizione] = useState('');
  const [categoria, setCategoria] = useState('altro');
  const [priorita, setPriorita] = useState('media');
  const [foto, setFoto] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const handleFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (file.size > 2_097_152) { setError('Immagine troppo grande (max 2MB)'); return; }
    const reader = new FileReader();
    reader.onload = () => setFoto(reader.result as string);
    reader.readAsDataURL(file);
  };

  const handleSubmit = async () => {
    if (!titolo.trim()) { setError('Il titolo è obbligatorio'); return; }
    setSaving(true);
    setError(null);
    try {
      const ticket = await createTicket(token, { titolo, descrizione, categoria, priorita, foto, condominioId });
      onCreated(ticket);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title="Nuova Segnalazione" onClose={onClose}>
      <div className="flex flex-col gap-4">
        <div>
          <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Titolo <span style={{ color: 'var(--primary)' }}>*</span></label>
          <input value={titolo} onChange={(e) => setTitolo(e.target.value)} placeholder="Descrivi brevemente il problema…" className="w-full px-3 py-2 rounded-lg text-sm outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }} />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Categoria</label>
            <select value={categoria} onChange={(e) => setCategoria(e.target.value)} className="w-full px-3 py-2 rounded-lg text-sm outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}>
              {CATEGORIE.map((c) => <option key={c} value={c}>{CAT_ICON[c]} {c.charAt(0).toUpperCase() + c.slice(1)}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Priorità</label>
            <select value={priorita} onChange={(e) => setPriorita(e.target.value)} className="w-full px-3 py-2 rounded-lg text-sm outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}>
              {Object.entries(PRIORITA_CFG).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
            </select>
          </div>
        </div>
        <div>
          <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Descrizione</label>
          <textarea value={descrizione} onChange={(e) => setDescrizione(e.target.value)} rows={3} placeholder="Descrivi il problema in dettaglio…" className="w-full px-3 py-2 rounded-lg text-sm outline-none resize-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }} />
        </div>
        {/* Foto allegata */}
        <div>
          <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Foto (opzionale)</label>
          {foto ? (
            <div className="relative">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={foto} alt="preview" className="rounded-xl w-full max-h-36 object-cover" />
              <button onClick={() => { setFoto(null); if (fileRef.current) fileRef.current.value = ''; }} className="absolute top-2 right-2 p-1 rounded-full" style={{ backgroundColor: '#fef2f2', color: '#dc2626' }}><X size={12} /></button>
            </div>
          ) : (
            <button onClick={() => fileRef.current?.click()} className="w-full py-8 rounded-xl flex flex-col items-center gap-1.5" style={{ border: '2px dashed #e5e7eb', backgroundColor: '#fafafa', color: '#aaa' }}>
              <ImageIcon size={20} />
              <span className="text-xs">Clicca per allegare una foto</span>
            </button>
          )}
          <input ref={fileRef} type="file" accept="image/*" onChange={handleFile} className="hidden" />
        </div>
        {error && <p className="text-xs px-3 py-2 rounded-lg" style={{ backgroundColor: '#fef2f2', color: '#dc2626' }}>{error}</p>}
        <div className="flex gap-2 pt-1">
          <button onClick={onClose} className="flex-1 py-2 rounded-lg text-sm font-semibold" style={{ backgroundColor: '#f5f5f5', color: '#555' }}>Annulla</button>
          <button onClick={handleSubmit} disabled={saving} className="flex-1 py-2 rounded-lg text-sm font-semibold text-white" style={{ backgroundColor: 'var(--primary)', opacity: saving ? 0.7 : 1 }}>
            {saving ? 'Invio...' : 'Invia Segnalazione'}
          </button>
        </div>
      </div>
    </Modal>
  );
}

// ─── Pagina principale (switch ruolo) ─────────────────────────────────────────

export default function TicketPage() {
  const { user, token } = useAuth();

  if (!token) return null;

  if (user?.role === 'AMMINISTRATORE') return <AdminView token={token} />;
  return <CondominoView token={token} />;
}
