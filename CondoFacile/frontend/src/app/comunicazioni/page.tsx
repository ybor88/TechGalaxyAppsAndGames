'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  Bell, Plus, X, ChevronRight, Search, Trash2, Eye, EyeOff,
  Building2, Check, Users, Megaphone, Calendar,
  AlertTriangle, Wrench, FileText, RefreshCw,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import {
  fetchCondominii, fetchComunicazioni, fetchMieComunicazioni,
  createComunicazione, updateComunicazione, deleteComunicazione,
  presoVisioneComunicazione, fetchLettureComunicazione,
  CondominioListItem, ComunicazioneItem, ComunicazioneLettura,
} from '@/lib/api';

// ─── Costanti ─────────────────────────────────────────────────────────────────

const TIPI = ['avviso', 'assemblea', 'manutenzione', 'emergenza', 'circolare'];

const TIPO_CFG: Record<string, { label: string; bg: string; color: string; icon: React.ReactNode }> = {
  avviso:       { label: 'Avviso',       bg: '#eff6ff', color: '#2563eb', icon: <Bell size={12} /> },
  assemblea:    { label: 'Assemblea',    bg: '#faf5ff', color: '#7c3aed', icon: <Users size={12} /> },
  manutenzione: { label: 'Manutenzione', bg: '#fff7ed', color: '#ea580c', icon: <Wrench size={12} /> },
  emergenza:    { label: 'Emergenza',    bg: '#fef2f2', color: '#dc2626', icon: <AlertTriangle size={12} /> },
  circolare:    { label: 'Circolare',    bg: '#f0fdf4', color: '#16a34a', icon: <FileText size={12} /> },
};

function fmtDate(s: string) {
  return new Date(s).toLocaleDateString('it-IT', { day: '2-digit', month: 'short', year: 'numeric' });
}

// ─── Componenti riutilizzabili ────────────────────────────────────────────────

function Modal({
  title, onClose, children, wide,
}: { title: string; onClose: () => void; children: React.ReactNode; wide?: boolean }) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center"
      style={{ backgroundColor: 'rgba(0,0,0,0.45)' }}
      onClick={onClose}
    >
      <div
        className="w-full rounded-2xl p-6 shadow-2xl"
        style={{ maxWidth: wide ? 680 : 500, backgroundColor: '#fff', maxHeight: '92vh', overflowY: 'auto' }}
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

function TipoBadge({ tipo }: { tipo: string }) {
  const cfg = TIPO_CFG[tipo] ?? { label: tipo, bg: '#f5f5f5', color: '#888', icon: null };
  return (
    <span
      className="flex items-center gap-1 text-xs px-2 py-0.5 rounded-full font-semibold"
      style={{ backgroundColor: cfg.bg, color: cfg.color }}
    >
      {cfg.icon} {cfg.label}
    </span>
  );
}

// ─── Modale nuova/modifica comunicazione ─────────────────────────────────────

function ComunicazioneModal({
  condominioId,
  token,
  existing,
  onClose,
  onSaved,
}: {
  condominioId: number;
  token: string;
  existing?: ComunicazioneItem;
  onClose: () => void;
  onSaved: (c: ComunicazioneItem) => void;
}) {
  const [titolo, setTitolo] = useState(existing?.titolo ?? '');
  const [corpo, setCorpo] = useState(existing?.corpo ?? '');
  const [tipo, setTipo] = useState(existing?.tipo ?? 'avviso');
  const [destinatariTipo, setDestinatariTipo] = useState(existing?.destinatariTipo ?? 'tutti');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const handleSave = async () => {
    if (!titolo.trim()) { setError('Titolo obbligatorio'); return; }
    setSaving(true);
    setError('');
    try {
      let saved: ComunicazioneItem;
      if (existing) {
        saved = await updateComunicazione(token, existing.id, { titolo, corpo, tipo, destinatariTipo });
      } else {
        saved = await createComunicazione(token, { titolo, corpo, tipo, destinatariTipo, condominioId });
      }
      onSaved(saved);
      onClose();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title={existing ? 'Modifica comunicazione' : 'Nuova comunicazione'} onClose={onClose} wide>
      <div className="space-y-4">
        <div>
          <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Titolo *</label>
          <input
            value={titolo}
            onChange={(e) => setTitolo(e.target.value)}
            placeholder="Titolo della comunicazione"
            className="w-full px-3 py-2 rounded-lg text-sm outline-none"
            style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
          />
        </div>
        <div>
          <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Categoria</label>
          <select
            value={tipo}
            onChange={(e) => setTipo(e.target.value)}
            className="w-full px-3 py-2 rounded-lg text-sm outline-none"
            style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
          >
            {TIPI.map((t) => (
              <option key={t} value={t}>{TIPO_CFG[t]?.label ?? t}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Destinatari</label>
          <select
            value={destinatariTipo}
            onChange={(e) => setDestinatariTipo(e.target.value)}
            className="w-full px-3 py-2 rounded-lg text-sm outline-none"
            style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
          >
            <option value="tutti">Tutti i condòmini</option>
          </select>
        </div>
        <div>
          <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Testo</label>
          <textarea
            value={corpo}
            onChange={(e) => setCorpo(e.target.value)}
            rows={6}
            placeholder="Scrivi il testo della comunicazione..."
            className="w-full px-3 py-2 rounded-lg text-sm outline-none resize-none"
            style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
          />
        </div>
        {error && <p className="text-xs" style={{ color: '#dc2626' }}>{error}</p>}
        <div className="flex justify-end gap-2 pt-2">
          <button onClick={onClose} className="px-4 py-2 rounded-lg text-sm" style={{ border: '1px solid #e5e7eb', color: '#666' }}>
            Annulla
          </button>
          <button
            onClick={handleSave}
            disabled={saving}
            className="px-4 py-2 rounded-lg text-sm font-semibold text-white"
            style={{ backgroundColor: 'var(--primary)', opacity: saving ? 0.7 : 1 }}
          >
            {saving ? 'Salvataggio...' : existing ? 'Salva modifiche' : 'Pubblica'}
          </button>
        </div>
      </div>
    </Modal>
  );
}

// ─── Modale letture ───────────────────────────────────────────────────────────

function LettureModal({
  com, token, onClose,
}: { com: ComunicazioneItem; token: string; onClose: () => void }) {
  const [letture, setLetture] = useState<ComunicazioneLettura[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchLettureComunicazione(token, com.id)
      .then(setLetture)
      .finally(() => setLoading(false));
  }, [token, com.id]);

  return (
    <Modal title={`Letture: ${com.titolo}`} onClose={onClose}>
      {loading && <p className="text-xs text-center py-4" style={{ color: '#aaa' }}>Caricamento...</p>}
      {!loading && letture.length === 0 && (
        <p className="text-xs text-center py-6" style={{ color: '#aaa' }}>
          Nessuna presa visione ancora registrata
        </p>
      )}
      {letture.map((l, i) => (
        <div key={i} className="flex items-center justify-between py-2.5 border-b" style={{ borderColor: '#f5f5f5' }}>
          <div>
            <p className="text-sm font-semibold" style={{ color: '#1a1a1a' }}>
              {l.condomino.nome} {l.condomino.cognome}
            </p>
            <p className="text-xs" style={{ color: '#aaa' }}>Unità {l.condomino.unita}</p>
          </div>
          <div className="text-right">
            <p className="text-xs" style={{ color: '#16a34a' }}>✓ Confermata</p>
            <p className="text-xs" style={{ color: '#aaa' }}>{fmtDate(l.dataLettura)}</p>
          </div>
        </div>
      ))}
      <div className="mt-3 pt-2" style={{ borderTop: '1px solid #f5f5f5' }}>
        <p className="text-xs text-right" style={{ color: '#aaa' }}>
          {letture.length} / {com.destinatari} confermato{letture.length !== 1 ? 'i' : ''}
        </p>
      </div>
    </Modal>
  );
}

// ─── Vista Amministratore ─────────────────────────────────────────────────────

function AdminView({ token }: { token: string }) {
  const [condominii, setCondominii] = useState<CondominioListItem[]>([]);
  const [selectedCondo, setSelectedCondo] = useState<CondominioListItem | null>(null);
  const [comunicazioni, setComunicazioni] = useState<ComunicazioneItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<ComunicazioneItem | null>(null);

  // Filtri
  const [search, setSearch] = useState('');
  const [fTipo, setFTipo] = useState('tutti');

  // Modal
  const [showNew, setShowNew] = useState(false);
  const [editing, setEditing] = useState<ComunicazioneItem | null>(null);
  const [showLetture, setShowLetture] = useState<ComunicazioneItem | null>(null);

  useEffect(() => {
    fetchCondominii(token).then((data) => {
      setCondominii(data);
      if (data.length === 1) selectCondo(data[0]);
    });
  }, [token]); // eslint-disable-line react-hooks/exhaustive-deps

  const loadComunicazioni = useCallback(async (condoId: number) => {
    setLoading(true);
    try {
      const data = await fetchComunicazioni(token, condoId, fTipo !== 'tutti' ? fTipo : undefined);
      setComunicazioni(data);
      setSelected((prev) => prev ? data.find((c) => c.id === prev.id) ?? null : null);
    } finally {
      setLoading(false);
    }
  }, [token, fTipo]);

  const selectCondo = useCallback((c: CondominioListItem) => {
    setSelectedCondo(c);
    setSelected(null);
    loadComunicazioni(c.id);
  }, [loadComunicazioni]);

  useEffect(() => {
    if (selectedCondo) loadComunicazioni(selectedCondo.id);
  }, [fTipo]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleDelete = async (c: ComunicazioneItem) => {
    if (!confirm(`Eliminare la comunicazione "${c.titolo}"?`)) return;
    await deleteComunicazione(token, c.id);
    setComunicazioni((prev) => prev.filter((x) => x.id !== c.id));
    if (selected?.id === c.id) setSelected(null);
  };

  const handleSaved = (saved: ComunicazioneItem) => {
    setComunicazioni((prev) => {
      const idx = prev.findIndex((c) => c.id === saved.id);
      if (idx >= 0) {
        const next = [...prev];
        next[idx] = saved;
        return next;
      }
      return [saved, ...prev];
    });
    setSelected(saved);
  };

  const displayed = comunicazioni.filter((c) => {
    if (!search) return true;
    const q = search.toLowerCase();
    return c.titolo.toLowerCase().includes(q) || c.corpo.toLowerCase().includes(q);
  });

  return (
    <div className="flex flex-col flex-1 overflow-hidden">
      {/* Header */}
      <header className="flex items-center justify-between px-6 py-3 border-b flex-shrink-0" style={{ backgroundColor: '#fff', borderColor: '#f0f0f0' }}>
        <div>
          <h1 className="text-sm font-bold" style={{ color: '#1a1a1a' }}>Comunicazioni</h1>
          <p className="text-xs" style={{ color: '#888' }}>Avvisi, circolari e comunicazioni condominiali</p>
        </div>
        <div className="flex items-center gap-2">
          <select
            value={selectedCondo?.id ?? ''}
            onChange={(e) => {
              const c = condominii.find((x) => x.id === Number(e.target.value));
              if (c) selectCondo(c);
            }}
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

      {/* Filtri */}
      {selectedCondo && (
        <div className="flex items-center gap-2 px-6 py-2.5 flex-shrink-0 flex-wrap" style={{ backgroundColor: '#fafafa', borderBottom: '1px solid #f0f0f0' }}>
          <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg" style={{ backgroundColor: '#fff', border: '1px solid #e5e7eb', flex: '1 1 200px' }}>
            <Search size={13} style={{ color: '#aaa' }} />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Cerca comunicazioni…"
              className="text-xs outline-none w-full bg-transparent"
              style={{ color: '#333' }}
            />
          </div>
          <select
            value={fTipo}
            onChange={(e) => setFTipo(e.target.value)}
            className="px-2 py-1.5 rounded-lg text-xs outline-none"
            style={{ border: '1px solid #e5e7eb', backgroundColor: '#fff' }}
          >
            <option value="tutti">Tutte le categorie</option>
            {TIPI.map((t) => <option key={t} value={t}>{TIPO_CFG[t]?.label ?? t}</option>)}
          </select>
          <span className="text-xs ml-1" style={{ color: '#aaa' }}>{displayed.length} comunicazioni</span>
        </div>
      )}

      {!selectedCondo ? (
        <div className="flex flex-1 items-center justify-center flex-col gap-2">
          <Building2 size={40} style={{ color: '#ddd' }} />
          <p className="text-sm" style={{ color: '#aaa' }}>Seleziona un condominio</p>
        </div>
      ) : (
        <div className="flex flex-1 overflow-hidden">
          {/* Lista */}
          <div className="flex flex-col flex-shrink-0 overflow-y-auto" style={{ width: 360, borderRight: '1px solid #f0f0f0' }}>
            {loading && <p className="text-xs text-center mt-8" style={{ color: '#aaa' }}>Caricamento...</p>}
            {!loading && displayed.length === 0 && (
              <div className="flex flex-col items-center justify-center py-20 gap-2">
                <Bell size={32} style={{ color: '#ddd' }} />
                <p className="text-sm" style={{ color: '#aaa' }}>Nessuna comunicazione</p>
              </div>
            )}
            {displayed.map((c) => {
              const isActive = selected?.id === c.id;
              return (
                <button
                  key={c.id}
                  onClick={() => setSelected(c)}
                  className="w-full text-left p-4 transition-all"
                  style={{
                    backgroundColor: isActive ? '#eff6ff' : '#fff',
                    borderBottom: '1px solid #f5f5f5',
                    borderLeft: isActive ? '3px solid var(--primary)' : '3px solid transparent',
                  }}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1.5">
                        <TipoBadge tipo={c.tipo} />
                      </div>
                      <p className="text-xs font-bold truncate mb-1" style={{ color: '#1a1a1a' }}>{c.titolo}</p>
                      {c.corpo && (
                        <p className="text-xs line-clamp-2" style={{ color: '#888' }}>{c.corpo}</p>
                      )}
                    </div>
                    <div className="flex flex-col items-end gap-1 flex-shrink-0">
                      <span className="text-xs" style={{ color: '#ccc' }}>{fmtDate(c.data)}</span>
                      <span className="flex items-center gap-0.5 text-xs" style={{ color: '#aaa' }}>
                        <Eye size={10} /> {c._count.letture}/{c.destinatari}
                      </span>
                    </div>
                  </div>
                </button>
              );
            })}
          </div>

          {/* Dettaglio */}
          {!selected ? (
            <div className="flex flex-1 items-center justify-center flex-col gap-2">
              <ChevronRight size={36} style={{ color: '#ddd' }} />
              <p className="text-sm" style={{ color: '#aaa' }}>Seleziona una comunicazione</p>
            </div>
          ) : (
            <div className="flex flex-col flex-1 overflow-y-auto p-6" style={{ backgroundColor: '#fafafa' }}>
              <div className="rounded-2xl p-5 mb-4" style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0', maxWidth: 680 }}>
                {/* Intestazione */}
                <div className="flex items-start justify-between gap-3 mb-4">
                  <div className="flex-1">
                    <TipoBadge tipo={selected.tipo} />
                    <h2 className="text-base font-bold mt-2 mb-1" style={{ color: '#1a1a1a' }}>{selected.titolo}</h2>
                    <p className="text-xs" style={{ color: '#aaa' }}>
                      Pubblicata il {fmtDate(selected.data)} · {selected.destinatariTipo === 'tutti' ? 'Tutti i condòmini' : selected.destinatariTipo}
                    </p>
                  </div>
                  <div className="flex gap-2">
                    <button
                      onClick={() => setEditing(selected)}
                      className="px-3 py-1.5 rounded-lg text-xs font-semibold"
                      style={{ border: '1px solid #e5e7eb', color: '#555' }}
                    >
                      Modifica
                    </button>
                    <button
                      onClick={() => handleDelete(selected)}
                      className="p-1.5 rounded-lg"
                      style={{ backgroundColor: '#fef2f2', color: '#dc2626' }}
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                </div>

                {/* Corpo */}
                {selected.corpo ? (
                  <div className="rounded-xl p-4 text-sm whitespace-pre-wrap" style={{ backgroundColor: '#fafafa', color: '#333', border: '1px solid #f0f0f0' }}>
                    {selected.corpo}
                  </div>
                ) : (
                  <p className="text-sm italic" style={{ color: '#bbb' }}>Nessun testo</p>
                )}

                {/* Letture */}
                <div className="mt-4 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg" style={{ backgroundColor: '#f0fdf4', border: '1px solid #bbf7d0' }}>
                      <Check size={12} style={{ color: '#16a34a' }} />
                      <span className="text-xs font-semibold" style={{ color: '#16a34a' }}>
                        {selected._count.letture} / {selected.destinatari} prese visione
                      </span>
                    </div>
                    {selected.destinatari > 0 && (
                      <div
                        className="h-2 rounded-full overflow-hidden"
                        style={{ width: 80, backgroundColor: '#e5e7eb' }}
                      >
                        <div
                          className="h-full rounded-full"
                          style={{
                            width: `${Math.round((selected._count.letture / selected.destinatari) * 100)}%`,
                            backgroundColor: '#16a34a',
                          }}
                        />
                      </div>
                    )}
                  </div>
                  <button
                    onClick={() => setShowLetture(selected)}
                    className="flex items-center gap-1.5 text-xs font-semibold px-3 py-1.5 rounded-lg"
                    style={{ border: '1px solid #e5e7eb', color: '#2563eb' }}
                  >
                    <Users size={12} /> Chi ha letto
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Modali */}
      {showNew && selectedCondo && (
        <ComunicazioneModal
          condominioId={selectedCondo.id}
          token={token}
          onClose={() => setShowNew(false)}
          onSaved={handleSaved}
        />
      )}
      {editing && selectedCondo && (
        <ComunicazioneModal
          condominioId={selectedCondo.id}
          token={token}
          existing={editing}
          onClose={() => setEditing(null)}
          onSaved={handleSaved}
        />
      )}
      {showLetture && (
        <LettureModal com={showLetture} token={token} onClose={() => setShowLetture(null)} />
      )}
    </div>
  );
}

// ─── Vista Condomino (bacheca) ────────────────────────────────────────────────

function CondominoView({ token }: { token: string }) {
  const [comunicazioni, setComunicazioni] = useState<ComunicazioneItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [selected, setSelected] = useState<ComunicazioneItem | null>(null);

  // Filtri
  const [fTipo, setFTipo] = useState('tutti');

  const load = useCallback(async () => {
    try {
      const data = await fetchMieComunicazioni(token);
      setComunicazioni(data);
      setSelected((prev) => prev ? data.find((c) => c.id === prev.id) ?? null : null);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [token]);

  useEffect(() => { load(); }, [load]);

  const handlePresoVisione = async (c: ComunicazioneItem) => {
    if (c.presoVisione) return;
    await presoVisioneComunicazione(token, c.id);
    setComunicazioni((prev) =>
      prev.map((x) => x.id === c.id ? { ...x, presoVisione: true, dataLettura: new Date().toISOString() } : x),
    );
    setSelected((prev) => prev?.id === c.id ? { ...prev, presoVisione: true, dataLettura: new Date().toISOString() } : prev);
  };

  const displayed = comunicazioni.filter((c) => fTipo === 'tutti' || c.tipo === fTipo);
  const nonLette = comunicazioni.filter((c) => !c.presoVisione).length;

  return (
    <div className="flex flex-col flex-1 overflow-hidden">
      {/* Header */}
      <header className="flex items-center justify-between px-6 py-3 border-b flex-shrink-0" style={{ backgroundColor: '#fff', borderColor: '#f0f0f0' }}>
        <div className="flex items-center gap-3">
          <div>
            <h1 className="text-sm font-bold" style={{ color: '#1a1a1a' }}>Bacheca Comunicazioni</h1>
            <p className="text-xs" style={{ color: '#888' }}>Avvisi e comunicazioni dal tuo amministratore</p>
          </div>
          {nonLette > 0 && (
            <span className="flex items-center justify-center w-5 h-5 rounded-full text-xs font-bold text-white" style={{ backgroundColor: 'var(--primary)' }}>
              {nonLette}
            </span>
          )}
        </div>
        <button
          onClick={() => { setRefreshing(true); load(); }}
          disabled={refreshing}
          className="p-2 rounded-lg"
          style={{ border: '1px solid #e5e7eb', color: '#666' }}
        >
          <RefreshCw size={14} className={refreshing ? 'animate-spin' : ''} />
        </button>
      </header>

      {/* Filtri */}
      <div className="flex items-center gap-2 px-6 py-2.5 flex-shrink-0 flex-wrap" style={{ backgroundColor: '#fafafa', borderBottom: '1px solid #f0f0f0' }}>
        <select
          value={fTipo}
          onChange={(e) => setFTipo(e.target.value)}
          className="px-2 py-1.5 rounded-lg text-xs outline-none"
          style={{ border: '1px solid #e5e7eb', backgroundColor: '#fff' }}
        >
          <option value="tutti">Tutte le categorie</option>
          {TIPI.map((t) => <option key={t} value={t}>{TIPO_CFG[t]?.label ?? t}</option>)}
        </select>
        <span className="text-xs" style={{ color: '#aaa' }}>{displayed.length} comunicazioni</span>
      </div>

      <div className="flex flex-1 overflow-hidden">
        {/* Lista */}
        <div className="flex flex-col flex-shrink-0 overflow-y-auto" style={{ width: 360, borderRight: '1px solid #f0f0f0' }}>
          {loading && <p className="text-xs text-center mt-8" style={{ color: '#aaa' }}>Caricamento...</p>}
          {!loading && displayed.length === 0 && (
            <div className="flex flex-col items-center justify-center py-20 gap-2">
              <Megaphone size={32} style={{ color: '#ddd' }} />
              <p className="text-sm" style={{ color: '#aaa' }}>Nessuna comunicazione</p>
            </div>
          )}
          {displayed.map((c) => {
            const isActive = selected?.id === c.id;
            return (
              <button
                key={c.id}
                onClick={() => setSelected(c)}
                className="w-full text-left p-4 transition-all"
                style={{
                  backgroundColor: isActive ? '#eff6ff' : c.presoVisione ? '#fff' : '#fefce8',
                  borderBottom: '1px solid #f5f5f5',
                  borderLeft: isActive ? '3px solid var(--primary)' : c.presoVisione ? '3px solid transparent' : '3px solid #eab308',
                }}
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1.5">
                      <TipoBadge tipo={c.tipo} />
                      {!c.presoVisione && (
                        <span className="text-xs font-bold px-1.5 py-0.5 rounded" style={{ backgroundColor: '#fef9c3', color: '#a16207' }}>
                          Nuova
                        </span>
                      )}
                    </div>
                    <p className="text-xs font-bold truncate mb-0.5" style={{ color: '#1a1a1a', fontWeight: c.presoVisione ? 500 : 700 }}>
                      {c.titolo}
                    </p>
                    <p className="text-xs" style={{ color: '#aaa' }}>{fmtDate(c.data)}</p>
                  </div>
                  <div className="flex-shrink-0">
                    {c.presoVisione
                      ? <Eye size={14} style={{ color: '#16a34a' }} />
                      : <EyeOff size={14} style={{ color: '#d97706' }} />
                    }
                  </div>
                </div>
              </button>
            );
          })}
        </div>

        {/* Dettaglio */}
        {!selected ? (
          <div className="flex flex-1 items-center justify-center flex-col gap-2">
            <Bell size={36} style={{ color: '#ddd' }} />
            <p className="text-sm" style={{ color: '#aaa' }}>Seleziona una comunicazione</p>
          </div>
        ) : (
          <div className="flex flex-col flex-1 overflow-y-auto p-6" style={{ backgroundColor: '#fafafa' }}>
            <div className="rounded-2xl p-5 mb-4" style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0', maxWidth: 680 }}>
              {/* Intestazione */}
              <div className="mb-4">
                <TipoBadge tipo={selected.tipo} />
                <h2 className="text-base font-bold mt-2 mb-1" style={{ color: '#1a1a1a' }}>{selected.titolo}</h2>
                <div className="flex items-center gap-3 text-xs" style={{ color: '#aaa' }}>
                  <span className="flex items-center gap-1"><Calendar size={12} /> {fmtDate(selected.data)}</span>
                </div>
              </div>

              {/* Corpo */}
              {selected.corpo ? (
                <div className="rounded-xl p-4 text-sm whitespace-pre-wrap mb-5" style={{ backgroundColor: '#fafafa', color: '#333', border: '1px solid #f0f0f0', lineHeight: 1.7 }}>
                  {selected.corpo}
                </div>
              ) : (
                <p className="text-sm italic mb-5" style={{ color: '#bbb' }}>Nessun testo</p>
              )}

              {/* Presa visione */}
              {selected.presoVisione ? (
                <div className="flex items-center gap-2 px-4 py-3 rounded-xl" style={{ backgroundColor: '#f0fdf4', border: '1px solid #bbf7d0' }}>
                  <Check size={16} style={{ color: '#16a34a' }} />
                  <div>
                    <p className="text-sm font-semibold" style={{ color: '#16a34a' }}>Presa visione confermata</p>
                    {selected.dataLettura && (
                      <p className="text-xs" style={{ color: '#86efac' }}>il {fmtDate(selected.dataLettura)}</p>
                    )}
                  </div>
                </div>
              ) : (
                <button
                  onClick={() => handlePresoVisione(selected)}
                  className="w-full flex items-center justify-center gap-2 py-3 rounded-xl text-sm font-semibold text-white transition-opacity hover:opacity-90"
                  style={{ backgroundColor: 'var(--primary)' }}
                >
                  <Check size={16} /> Conferma presa visione
                </button>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

// ─── Pagina principale ────────────────────────────────────────────────────────

export default function ComunicazioniPage() {
  const { token, user } = useAuth();

  if (!token || !user) return null;

  return user.role === 'AMMINISTRATORE'
    ? <AdminView token={token} />
    : <CondominoView token={token} />;
}
