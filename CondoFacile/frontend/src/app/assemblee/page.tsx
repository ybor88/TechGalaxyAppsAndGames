'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  CalendarDays, Plus, X, ChevronRight, Search, Trash2,
  Building2, Users, CheckCircle2, XCircle, Clock,
  FileText, RefreshCw, Edit2, Check, AlertTriangle,
  MapPin, Info, ListOrdered,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import {
  fetchCondominii, CondominioListItem,
  fetchAssemblee, fetchMieAssemblee,
  createAssemblea, updateAssemblea, deleteAssemblea,
  upsertPresenza, fetchQuorum,
  createPuntoOdG, updatePuntoOdG, deletePuntoOdG,
  inviaDelega,
  AssembleaItem, AssembleaPuntoOdG, QuorumInfo,
} from '@/lib/api';

// ─── Costanti ─────────────────────────────────────────────────────────────────

const STATI = ['convocata', 'in_corso', 'conclusa', 'annullata'];
const TIPI_ASS = ['ordinaria', 'straordinaria'];

const STATO_CFG: Record<string, { label: string; bg: string; color: string; icon: React.ReactNode }> = {
  convocata:  { label: 'Convocata',  bg: '#eff6ff', color: '#2563eb', icon: <Clock size={12} /> },
  in_corso:   { label: 'In corso',   bg: '#fef9c3', color: '#a16207', icon: <AlertTriangle size={12} /> },
  conclusa:   { label: 'Conclusa',   bg: '#f0fdf4', color: '#16a34a', icon: <CheckCircle2 size={12} /> },
  annullata:  { label: 'Annullata',  bg: '#fef2f2', color: '#dc2626', icon: <XCircle size={12} /> },
};

const ESITO_CFG: Record<string, { label: string; color: string }> = {
  approvato:  { label: 'Approvato',  color: '#16a34a' },
  respinto:   { label: 'Respinto',   color: '#dc2626' },
  rimandato:  { label: 'Rimandato',  color: '#d97706' },
};

function fmtDate(s: string) {
  return new Date(s).toLocaleDateString('it-IT', { day: '2-digit', month: 'short', year: 'numeric' });
}
function fmtDateTime(s: string) {
  return new Date(s).toLocaleString('it-IT', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
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
        style={{ maxWidth: wide ? 700 : 520, backgroundColor: '#fff', maxHeight: '92vh', overflowY: 'auto' }}
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

function StatoBadge({ stato }: { stato: string }) {
  const cfg = STATO_CFG[stato] ?? { label: stato, bg: '#f5f5f5', color: '#888', icon: null };
  return (
    <span
      className="flex items-center gap-1 text-xs px-2 py-0.5 rounded-full font-semibold"
      style={{ backgroundColor: cfg.bg, color: cfg.color }}
    >
      {cfg.icon} {cfg.label}
    </span>
  );
}

// ─── Modale nuova/modifica assemblea ─────────────────────────────────────────

function AssembleaModal({
  condominioId, token, existing, onClose, onSaved,
}: {
  condominioId: number;
  token: string;
  existing?: AssembleaItem;
  onClose: () => void;
  onSaved: (a: AssembleaItem) => void;
}) {
  const [titolo, setTitolo] = useState(existing?.titolo ?? '');
  const [data, setData] = useState(
    existing ? new Date(existing.data).toISOString().slice(0, 16) : '',
  );
  const [luogo, setLuogo] = useState(existing?.luogo ?? '');
  const [tipo, setTipo] = useState(existing?.tipo ?? 'ordinaria');
  const [stato, setStato] = useState(existing?.stato ?? 'convocata');
  const [odg, setOdg] = useState(existing?.ordineDelGiorno ?? '');
  const [verbale, setVerbale] = useState(existing?.verbale ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const handleSave = async () => {
    if (!titolo.trim() || !data) { setError('Titolo e data obbligatori'); return; }
    setSaving(true);
    setError('');
    try {
      let saved: AssembleaItem;
      if (existing) {
        saved = await updateAssemblea(token, existing.id, { titolo, data, luogo, tipo, stato, ordineDelGiorno: odg, verbale: verbale || undefined });
      } else {
        saved = await createAssemblea(token, { titolo, data, luogo, tipo, ordineDelGiorno: odg, condominioId });
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
    <Modal title={existing ? 'Modifica assemblea' : 'Nuova assemblea'} onClose={onClose} wide>
      <div className="space-y-4">
        <div>
          <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Titolo *</label>
          <input
            value={titolo} onChange={(e) => setTitolo(e.target.value)}
            placeholder="es. Assemblea ordinaria 2026"
            className="w-full px-3 py-2 rounded-lg text-sm outline-none"
            style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Data e ora *</label>
            <input
              type="datetime-local" value={data} onChange={(e) => setData(e.target.value)}
              className="w-full px-3 py-2 rounded-lg text-sm outline-none"
              style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
            />
          </div>
          <div>
            <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Tipo</label>
            <select value={tipo} onChange={(e) => setTipo(e.target.value)}
              className="w-full px-3 py-2 rounded-lg text-sm outline-none"
              style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}>
              {TIPI_ASS.map((t) => <option key={t} value={t}>{t.charAt(0).toUpperCase() + t.slice(1)}</option>)}
            </select>
          </div>
        </div>
        <div>
          <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Luogo</label>
          <input
            value={luogo} onChange={(e) => setLuogo(e.target.value)}
            placeholder="es. Sala riunioni condominiale"
            className="w-full px-3 py-2 rounded-lg text-sm outline-none"
            style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
          />
        </div>
        {existing && (
          <div>
            <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Stato</label>
            <select value={stato} onChange={(e) => setStato(e.target.value)}
              className="w-full px-3 py-2 rounded-lg text-sm outline-none"
              style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}>
              {STATI.map((s) => <option key={s} value={s}>{STATO_CFG[s]?.label ?? s}</option>)}
            </select>
          </div>
        )}
        <div>
          <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Ordine del giorno</label>
          <textarea
            value={odg} onChange={(e) => setOdg(e.target.value)}
            rows={4} placeholder="Elenca i punti dell'ordine del giorno..."
            className="w-full px-3 py-2 rounded-lg text-sm outline-none resize-none"
            style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
          />
        </div>
        {existing && (
          <div>
            <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Verbale</label>
            <textarea
              value={verbale} onChange={(e) => setVerbale(e.target.value)}
              rows={5} placeholder="Testo del verbale dell'assemblea..."
              className="w-full px-3 py-2 rounded-lg text-sm outline-none resize-none"
              style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
            />
          </div>
        )}
        {error && <p className="text-xs" style={{ color: '#dc2626' }}>{error}</p>}
        <div className="flex justify-end gap-2 pt-2">
          <button onClick={onClose} className="px-4 py-2 rounded-lg text-sm" style={{ border: '1px solid #e5e7eb', color: '#666' }}>
            Annulla
          </button>
          <button onClick={handleSave} disabled={saving}
            className="px-4 py-2 rounded-lg text-sm font-semibold text-white"
            style={{ backgroundColor: 'var(--primary)', opacity: saving ? 0.7 : 1 }}>
            {saving ? 'Salvataggio...' : existing ? 'Salva modifiche' : 'Crea assemblea'}
          </button>
        </div>
      </div>
    </Modal>
  );
}

// ─── Pannello dettaglio assemblea (admin) ─────────────────────────────────────

function PuntoOdGRow({
  punto, token, onUpdated, onDeleted,
}: {
  punto: AssembleaPuntoOdG;
  token: string;
  onUpdated: (p: AssembleaPuntoOdG) => void;
  onDeleted: (id: number) => void;
}) {
  const [editMode, setEditMode] = useState(false);
  const [titolo, setTitolo] = useState(punto.titolo);
  const [desc, setDesc] = useState(punto.descrizione);
  const [esito, setEsito] = useState<string>(punto.esito ?? '');
  const [votiSi, setVotiSi] = useState(String(punto.votiSi));
  const [votiNo, setVotiNo] = useState(String(punto.votiNo));
  const [votiAst, setVotiAst] = useState(String(punto.votiAstenuti));
  const [saving, setSaving] = useState(false);

  const handleSave = async () => {
    setSaving(true);
    try {
      const updated = await updatePuntoOdG(token, punto.id, {
        titolo, descrizione: desc,
        esito: esito || null,
        votiSi: parseFloat(votiSi) || 0,
        votiNo: parseFloat(votiNo) || 0,
        votiAstenuti: parseFloat(votiAst) || 0,
      });
      onUpdated(updated);
      setEditMode(false);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!confirm('Eliminare questo punto?')) return;
    await deletePuntoOdG(token, punto.id);
    onDeleted(punto.id);
  };

  const esitoInfo = esito ? ESITO_CFG[esito] : null;

  if (editMode) {
    return (
      <div className="rounded-xl p-4 mb-3" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}>
        <input value={titolo} onChange={(e) => setTitolo(e.target.value)}
          className="w-full px-2 py-1 rounded text-sm mb-2 outline-none"
          style={{ border: '1px solid #e5e7eb' }} />
        <textarea value={desc} onChange={(e) => setDesc(e.target.value)}
          rows={2} className="w-full px-2 py-1 rounded text-xs mb-2 outline-none resize-none"
          style={{ border: '1px solid #e5e7eb' }} />
        <div className="grid grid-cols-4 gap-2 mb-2">
          <select value={esito} onChange={(e) => setEsito(e.target.value)}
            className="px-2 py-1 rounded text-xs outline-none col-span-1"
            style={{ border: '1px solid #e5e7eb' }}>
            <option value="">— Esito —</option>
            <option value="approvato">Approvato</option>
            <option value="respinto">Respinto</option>
            <option value="rimandato">Rimandato</option>
          </select>
          <input type="number" value={votiSi} onChange={(e) => setVotiSi(e.target.value)}
            placeholder="Sì (mill.)" className="px-2 py-1 rounded text-xs outline-none"
            style={{ border: '1px solid #e5e7eb' }} />
          <input type="number" value={votiNo} onChange={(e) => setVotiNo(e.target.value)}
            placeholder="No (mill.)" className="px-2 py-1 rounded text-xs outline-none"
            style={{ border: '1px solid #e5e7eb' }} />
          <input type="number" value={votiAst} onChange={(e) => setVotiAst(e.target.value)}
            placeholder="Ast." className="px-2 py-1 rounded text-xs outline-none"
            style={{ border: '1px solid #e5e7eb' }} />
        </div>
        <div className="flex gap-2">
          <button onClick={handleSave} disabled={saving}
            className="px-3 py-1 rounded text-xs font-semibold text-white"
            style={{ backgroundColor: 'var(--primary)' }}>
            {saving ? '...' : 'Salva'}
          </button>
          <button onClick={() => setEditMode(false)} className="px-3 py-1 rounded text-xs"
            style={{ border: '1px solid #e5e7eb', color: '#666' }}>
            Annulla
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-xl p-4 mb-3 flex items-start justify-between gap-3" style={{ border: '1px solid #f0f0f0', backgroundColor: '#fff' }}>
      <div className="flex-1">
        <div className="flex items-center gap-2 mb-1">
          <span className="text-xs font-bold px-1.5 py-0.5 rounded" style={{ backgroundColor: '#f0f0f0', color: '#666' }}>
            {punto.ordine}
          </span>
          <p className="text-sm font-semibold" style={{ color: '#1a1a1a' }}>{punto.titolo}</p>
          {esitoInfo && (
            <span className="text-xs font-bold" style={{ color: esitoInfo.color }}>
              • {esitoInfo.label}
            </span>
          )}
        </div>
        {punto.descrizione && (
          <p className="text-xs mb-2" style={{ color: '#888' }}>{punto.descrizione}</p>
        )}
        {(punto.votiSi > 0 || punto.votiNo > 0 || punto.votiAstenuti > 0) && (
          <div className="flex items-center gap-3 text-xs" style={{ color: '#aaa' }}>
            <span style={{ color: '#16a34a' }}>✓ {punto.votiSi} mill.</span>
            <span style={{ color: '#dc2626' }}>✗ {punto.votiNo} mill.</span>
            <span>~ {punto.votiAstenuti} mill.</span>
          </div>
        )}
      </div>
      <div className="flex gap-1 flex-shrink-0">
        <button onClick={() => setEditMode(true)} className="p-1.5 rounded" style={{ color: '#888', border: '1px solid #e5e7eb' }}>
          <Edit2 size={12} />
        </button>
        <button onClick={handleDelete} className="p-1.5 rounded" style={{ backgroundColor: '#fef2f2', color: '#dc2626' }}>
          <Trash2 size={12} />
        </button>
      </div>
    </div>
  );
}

// ─── Sezione presenze (admin) ─────────────────────────────────────────────────

function PresenzePanel({ assemblea, token, onUpdated }: {
  assemblea: AssembleaItem;
  token: string;
  onUpdated: (a: AssembleaItem) => void;
}) {
  const [quorum, setQuorum] = useState<QuorumInfo | null>(null);
  const [loadingQ, setLoadingQ] = useState(false);

  const loadQuorum = useCallback(async () => {
    setLoadingQ(true);
    try {
      const q = await fetchQuorum(token, assemblea.id);
      setQuorum(q);
    } finally {
      setLoadingQ(false);
    }
  }, [token, assemblea.id]);

  useEffect(() => { loadQuorum(); }, [loadQuorum]);

  const handleToggle = async (condominoId: number, presente: boolean) => {
    const updated = await upsertPresenza(token, assemblea.id, condominoId, { presente });
    onUpdated({
      ...assemblea,
      presenze: assemblea.presenze.map((p) =>
        p.condominoId === condominoId ? { ...p, presente: updated.presente, delegatoId: updated.delegatoId } : p,
      ),
    });
    await loadQuorum();
  };

  const presenti = assemblea.presenze.filter((p) => p.presente).length;
  const delegati = assemblea.presenze.filter((p) => p.delegatoId != null).length;

  return (
    <div>
      {/* Quorum */}
      {quorum && (
        <div className="rounded-xl p-4 mb-4" style={{ backgroundColor: '#f8fafc', border: '1px solid #e5e7eb' }}>
          <p className="text-xs font-bold mb-2" style={{ color: '#555' }}>Quorum</p>
          <div className="flex items-center gap-3 mb-2">
            <div className="flex-1 h-2.5 rounded-full overflow-hidden" style={{ backgroundColor: '#e5e7eb' }}>
              <div className="h-full rounded-full transition-all"
                style={{ width: `${Math.min(quorum.percPresenti, 100)}%`, backgroundColor: quorum.quorumOrdinario ? '#16a34a' : '#f59e0b' }} />
            </div>
            <span className="text-sm font-bold" style={{ color: '#1a1a1a' }}>{quorum.percPresenti}%</span>
          </div>
          <div className="flex gap-4 text-xs" style={{ color: '#888' }}>
            <span>{quorum.millPresenti.toFixed(0)} / {quorum.millTotali.toFixed(0)} millesimi</span>
            <span className="font-semibold" style={{ color: quorum.quorumOrdinario ? '#16a34a' : '#dc2626' }}>
              Ordinario: {quorum.quorumOrdinario ? '✓' : '✗'}
            </span>
            <span className="font-semibold" style={{ color: quorum.quorumStraordinario ? '#16a34a' : '#dc2626' }}>
              Straordinario: {quorum.quorumStraordinario ? '✓' : '✗'}
            </span>
          </div>
        </div>
      )}
      {loadingQ && <p className="text-xs mb-3" style={{ color: '#aaa' }}>Calcolo quorum...</p>}

      <div className="flex items-center gap-3 mb-3 text-xs" style={{ color: '#888' }}>
        <span>Presenti: <b style={{ color: '#1a1a1a' }}>{presenti}</b></span>
        <span>Con delega: <b style={{ color: '#1a1a1a' }}>{delegati}</b></span>
        <span>Totale: <b style={{ color: '#1a1a1a' }}>{assemblea.presenze.length}</b></span>
      </div>

      <div className="space-y-1.5 max-h-80 overflow-y-auto pr-1">
        {assemblea.presenze.map((p) => (
          <div key={p.condominoId}
            className="flex items-center justify-between px-3 py-2 rounded-xl"
            style={{ backgroundColor: p.presente ? '#f0fdf4' : '#fafafa', border: '1px solid', borderColor: p.presente ? '#bbf7d0' : '#f0f0f0' }}>
            <div>
              <p className="text-xs font-semibold" style={{ color: '#1a1a1a' }}>
                {p.condomino.nome} {p.condomino.cognome}
              </p>
              <p className="text-xs" style={{ color: '#aaa' }}>
                Unità {p.condomino.unita} · {p.condomino.millesimi} mill.
                {p.delegatoId && <span style={{ color: '#d97706' }}> · Delegato</span>}
              </p>
            </div>
            <button
              onClick={() => handleToggle(p.condominoId, !p.presente)}
              className="flex items-center gap-1 px-2.5 py-1 rounded-lg text-xs font-semibold transition-colors"
              style={{
                backgroundColor: p.presente ? '#16a34a' : '#e5e7eb',
                color: p.presente ? '#fff' : '#666',
              }}
            >
              {p.presente ? <><Check size={11} /> Presente</> : 'Assente'}
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}

// ─── Dettaglio assemblea admin ─────────────────────────────────────────────────

function DettaglioAdmin({ assemblea, token, onUpdated, onDeleted, onClose }: {
  assemblea: AssembleaItem;
  token: string;
  onUpdated: (a: AssembleaItem) => void;
  onDeleted: (id: number) => void;
  onClose: () => void;
}) {
  const [tab, setTab] = useState<'info' | 'presenze' | 'odg' | 'verbale'>('info');
  const [editing, setEditing] = useState(false);
  const [newPunto, setNewPunto] = useState('');
  const [addingPunto, setAddingPunto] = useState(false);

  const handleDelete = async () => {
    if (!confirm(`Eliminare l'assemblea "${assemblea.titolo}"?`)) return;
    await deleteAssemblea(token, assemblea.id);
    onDeleted(assemblea.id);
    onClose();
  };

  const handleAddPunto = async () => {
    if (!newPunto.trim()) return;
    setAddingPunto(true);
    try {
      const punto = await createPuntoOdG(token, assemblea.id, { titolo: newPunto });
      onUpdated({ ...assemblea, puntiOdG: [...assemblea.puntiOdG, punto] });
      setNewPunto('');
    } finally {
      setAddingPunto(false);
    }
  };

  const TABS = [
    { id: 'info', label: 'Info', icon: <Info size={13} /> },
    { id: 'presenze', label: 'Presenze', icon: <Users size={13} /> },
    { id: 'odg', label: 'Ordine del giorno', icon: <ListOrdered size={13} /> },
    { id: 'verbale', label: 'Verbale', icon: <FileText size={13} /> },
  ] as const;

  return (
    <div className="flex flex-col flex-1 overflow-y-auto p-6" style={{ backgroundColor: '#fafafa' }}>
      <div className="rounded-2xl overflow-hidden" style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0', maxWidth: 720 }}>
        {/* Header card */}
        <div className="p-5 border-b" style={{ borderColor: '#f5f5f5' }}>
          <div className="flex items-start justify-between gap-3">
            <div className="flex-1">
              <div className="flex items-center gap-2 mb-2">
                <StatoBadge stato={assemblea.stato} />
                <span className="text-xs px-2 py-0.5 rounded-full" style={{ backgroundColor: '#f5f5f5', color: '#666' }}>
                  {assemblea.tipo.charAt(0).toUpperCase() + assemblea.tipo.slice(1)}
                </span>
              </div>
              <h2 className="text-base font-bold mb-1" style={{ color: '#1a1a1a' }}>{assemblea.titolo}</h2>
              <div className="flex items-center gap-4 text-xs" style={{ color: '#aaa' }}>
                <span className="flex items-center gap-1"><CalendarDays size={12} /> {fmtDateTime(assemblea.data)}</span>
                {assemblea.luogo && <span className="flex items-center gap-1"><MapPin size={12} /> {assemblea.luogo}</span>}
              </div>
            </div>
            <div className="flex gap-2">
              <button onClick={() => setEditing(true)}
                className="px-3 py-1.5 rounded-lg text-xs font-semibold"
                style={{ border: '1px solid #e5e7eb', color: '#555' }}>
                Modifica
              </button>
              <button onClick={handleDelete} className="p-1.5 rounded-lg"
                style={{ backgroundColor: '#fef2f2', color: '#dc2626' }}>
                <Trash2 size={14} />
              </button>
            </div>
          </div>
        </div>

        {/* Tabs */}
        <div className="flex border-b" style={{ borderColor: '#f5f5f5' }}>
          {TABS.map((t) => (
            <button key={t.id} onClick={() => setTab(t.id)}
              className="flex items-center gap-1.5 px-4 py-3 text-xs font-semibold transition-colors"
              style={{
                color: tab === t.id ? 'var(--primary)' : '#888',
                borderBottom: tab === t.id ? '2px solid var(--primary)' : '2px solid transparent',
              }}>
              {t.icon} {t.label}
            </button>
          ))}
        </div>

        <div className="p-5">
          {/* Tab: Info */}
          {tab === 'info' && (
            <div>
              {assemblea.ordineDelGiorno ? (
                <div className="rounded-xl p-4 whitespace-pre-wrap text-sm"
                  style={{ backgroundColor: '#fafafa', color: '#333', border: '1px solid #f0f0f0', lineHeight: 1.7 }}>
                  {assemblea.ordineDelGiorno}
                </div>
              ) : (
                <p className="text-sm italic" style={{ color: '#bbb' }}>Nessun ordine del giorno inserito</p>
              )}
            </div>
          )}

          {/* Tab: Presenze */}
          {tab === 'presenze' && (
            <PresenzePanel assemblea={assemblea} token={token} onUpdated={onUpdated} />
          )}

          {/* Tab: Ordine del giorno strutturato */}
          {tab === 'odg' && (
            <div>
              {assemblea.puntiOdG.map((p) => (
                <PuntoOdGRow
                  key={p.id} punto={p} token={token}
                  onUpdated={(updated) => onUpdated({
                    ...assemblea,
                    puntiOdG: assemblea.puntiOdG.map((x) => x.id === updated.id ? updated : x),
                  })}
                  onDeleted={(id) => onUpdated({
                    ...assemblea,
                    puntiOdG: assemblea.puntiOdG.filter((x) => x.id !== id),
                  })}
                />
              ))}
              {assemblea.puntiOdG.length === 0 && (
                <p className="text-sm italic mb-4" style={{ color: '#bbb' }}>Nessun punto aggiunto</p>
              )}
              <div className="flex gap-2 mt-3">
                <input
                  value={newPunto} onChange={(e) => setNewPunto(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && handleAddPunto()}
                  placeholder="Aggiungi punto OdG..."
                  className="flex-1 px-3 py-2 rounded-lg text-sm outline-none"
                  style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
                />
                <button onClick={handleAddPunto} disabled={addingPunto || !newPunto.trim()}
                  className="px-3 py-2 rounded-lg text-sm font-semibold text-white"
                  style={{ backgroundColor: 'var(--primary)', opacity: addingPunto ? 0.7 : 1 }}>
                  <Plus size={16} />
                </button>
              </div>
            </div>
          )}

          {/* Tab: Verbale */}
          {tab === 'verbale' && (
            <div>
              {assemblea.verbale ? (
                <div className="rounded-xl p-4 whitespace-pre-wrap text-sm"
                  style={{ backgroundColor: '#fafafa', color: '#333', border: '1px solid #f0f0f0', lineHeight: 1.7 }}>
                  {assemblea.verbale}
                </div>
              ) : (
                <div className="text-center py-8">
                  <FileText size={36} style={{ color: '#ddd', margin: '0 auto 8px' }} />
                  <p className="text-sm" style={{ color: '#aaa' }}>Verbale non ancora redatto</p>
                  <p className="text-xs mt-1" style={{ color: '#ccc' }}>Modifica l'assemblea per aggiungere il verbale</p>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {editing && (
        <AssembleaModal
          condominioId={assemblea.condominioId}
          token={token}
          existing={assemblea}
          onClose={() => setEditing(false)}
          onSaved={(saved) => { onUpdated(saved); setEditing(false); }}
        />
      )}
    </div>
  );
}

// ─── Vista Amministratore ─────────────────────────────────────────────────────

function AdminView({ token }: { token: string }) {
  const [condominii, setCondominii] = useState<CondominioListItem[]>([]);
  const [selectedCondo, setSelectedCondo] = useState<CondominioListItem | null>(null);
  const [assemblee, setAssemblee] = useState<AssembleaItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<AssembleaItem | null>(null);

  const [search, setSearch] = useState('');
  const [fStato, setFStato] = useState('tutti');
  const [fTipo, setFTipo] = useState('tutti');
  const [showNew, setShowNew] = useState(false);

  useEffect(() => {
    fetchCondominii(token).then((data) => {
      setCondominii(data);
      if (data.length === 1) selectCondo(data[0]);
    });
  }, [token]); // eslint-disable-line react-hooks/exhaustive-deps

  const load = useCallback(async (condoId: number) => {
    setLoading(true);
    try {
      const data = await fetchAssemblee(token, condoId, {
        stato: fStato !== 'tutti' ? fStato : undefined,
        tipo: fTipo !== 'tutti' ? fTipo : undefined,
      });
      setAssemblee(data);
      setSelected((prev) => prev ? data.find((a) => a.id === prev.id) ?? null : null);
    } finally {
      setLoading(false);
    }
  }, [token, fStato, fTipo]);

  const selectCondo = useCallback((c: CondominioListItem) => {
    setSelectedCondo(c);
    setSelected(null);
    load(c.id);
  }, [load]);

  useEffect(() => {
    if (selectedCondo) load(selectedCondo.id);
  }, [fStato, fTipo]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleSaved = (saved: AssembleaItem) => {
    setAssemblee((prev) => {
      const idx = prev.findIndex((a) => a.id === saved.id);
      if (idx >= 0) { const n = [...prev]; n[idx] = saved; return n; }
      return [saved, ...prev];
    });
    setSelected(saved);
  };

  const displayed = assemblee.filter((a) => {
    if (!search) return true;
    return a.titolo.toLowerCase().includes(search.toLowerCase()) || a.luogo.toLowerCase().includes(search.toLowerCase());
  });

  return (
    <div className="flex flex-col flex-1 overflow-hidden">
      {/* Header */}
      <header className="flex items-center justify-between px-6 py-3 border-b flex-shrink-0"
        style={{ backgroundColor: '#fff', borderColor: '#f0f0f0' }}>
        <div>
          <h1 className="text-sm font-bold" style={{ color: '#1a1a1a' }}>Assemblee</h1>
          <p className="text-xs" style={{ color: '#888' }}>Convocazioni, presenze e verbali condominiali</p>
        </div>
        <div className="flex items-center gap-2">
          <select
            value={selectedCondo?.id ?? ''}
            onChange={(e) => { const c = condominii.find((x) => x.id === Number(e.target.value)); if (c) selectCondo(c); }}
            className="px-3 py-2 rounded-lg text-xs outline-none"
            style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}>
            <option value="">Seleziona condominio…</option>
            {condominii.map((c) => <option key={c.id} value={c.id}>{c.nome}</option>)}
          </select>
          {selectedCondo && (
            <button onClick={() => setShowNew(true)}
              className="flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-semibold text-white"
              style={{ backgroundColor: 'var(--primary)' }}>
              <Plus size={14} /> Nuova
            </button>
          )}
        </div>
      </header>

      {/* Filtri */}
      {selectedCondo && (
        <div className="flex items-center gap-2 px-6 py-2.5 flex-shrink-0 flex-wrap"
          style={{ backgroundColor: '#fafafa', borderBottom: '1px solid #f0f0f0' }}>
          <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg"
            style={{ backgroundColor: '#fff', border: '1px solid #e5e7eb', flex: '1 1 180px' }}>
            <Search size={13} style={{ color: '#aaa' }} />
            <input value={search} onChange={(e) => setSearch(e.target.value)}
              placeholder="Cerca assemblee…" className="text-xs outline-none w-full bg-transparent" />
          </div>
          <select value={fStato} onChange={(e) => setFStato(e.target.value)}
            className="px-2 py-1.5 rounded-lg text-xs outline-none"
            style={{ border: '1px solid #e5e7eb', backgroundColor: '#fff' }}>
            <option value="tutti">Tutti gli stati</option>
            {STATI.map((s) => <option key={s} value={s}>{STATO_CFG[s]?.label ?? s}</option>)}
          </select>
          <select value={fTipo} onChange={(e) => setFTipo(e.target.value)}
            className="px-2 py-1.5 rounded-lg text-xs outline-none"
            style={{ border: '1px solid #e5e7eb', backgroundColor: '#fff' }}>
            <option value="tutti">Tutti i tipi</option>
            {TIPI_ASS.map((t) => <option key={t} value={t}>{t.charAt(0).toUpperCase() + t.slice(1)}</option>)}
          </select>
          <span className="text-xs" style={{ color: '#aaa' }}>{displayed.length} assemblee</span>
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
          <div className="flex flex-col flex-shrink-0 overflow-y-auto" style={{ width: 320, borderRight: '1px solid #f0f0f0' }}>
            {loading && <p className="text-xs text-center mt-8" style={{ color: '#aaa' }}>Caricamento...</p>}
            {!loading && displayed.length === 0 && (
              <div className="flex flex-col items-center justify-center py-20 gap-2">
                <CalendarDays size={32} style={{ color: '#ddd' }} />
                <p className="text-sm" style={{ color: '#aaa' }}>Nessuna assemblea</p>
              </div>
            )}
            {displayed.map((a) => {
              const isActive = selected?.id === a.id;
              return (
                <button key={a.id} onClick={() => setSelected(a)}
                  className="w-full text-left p-4 transition-all"
                  style={{
                    backgroundColor: isActive ? '#eff6ff' : '#fff',
                    borderBottom: '1px solid #f5f5f5',
                    borderLeft: isActive ? '3px solid var(--primary)' : '3px solid transparent',
                  }}>
                  <div className="flex items-start justify-between gap-2 mb-1.5">
                    <StatoBadge stato={a.stato} />
                    <span className="text-xs" style={{ color: '#ccc' }}>{fmtDate(a.data)}</span>
                  </div>
                  <p className="text-xs font-bold truncate mb-0.5" style={{ color: '#1a1a1a' }}>{a.titolo}</p>
                  <div className="flex items-center gap-2 text-xs" style={{ color: '#aaa' }}>
                    {a.luogo && <span className="flex items-center gap-0.5"><MapPin size={10} /> {a.luogo}</span>}
                    <span className="flex items-center gap-0.5"><Users size={10} /> {a._count.presenze}</span>
                  </div>
                </button>
              );
            })}
          </div>

          {/* Dettaglio */}
          {!selected ? (
            <div className="flex flex-1 items-center justify-center flex-col gap-2">
              <ChevronRight size={36} style={{ color: '#ddd' }} />
              <p className="text-sm" style={{ color: '#aaa' }}>Seleziona un'assemblea</p>
            </div>
          ) : (
            <DettaglioAdmin
              key={selected.id}
              assemblea={selected}
              token={token}
              onUpdated={(saved) => { handleSaved(saved); setSelected(saved); }}
              onDeleted={(id) => { setAssemblee((prev) => prev.filter((a) => a.id !== id)); setSelected(null); }}
              onClose={() => setSelected(null)}
            />
          )}
        </div>
      )}

      {showNew && selectedCondo && (
        <AssembleaModal
          condominioId={selectedCondo.id}
          token={token}
          onClose={() => setShowNew(false)}
          onSaved={(saved) => { handleSaved(saved); setShowNew(false); }}
        />
      )}
    </div>
  );
}

// ─── Vista Condomino ──────────────────────────────────────────────────────────

function CondominoView({ token }: { token: string }) {
  const [assemblee, setAssemblee] = useState<AssembleaItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [selected, setSelected] = useState<AssembleaItem | null>(null);
  const [showDelega, setShowDelega] = useState(false);
  const [delegatoInput, setDelegaInput] = useState('');
  const [delegaSaving, setDelegaSaving] = useState(false);
  const [delegaError, setDelegaError] = useState('');

  const load = useCallback(async () => {
    try {
      const data = await fetchMieAssemblee(token);
      setAssemblee(data);
      setSelected((prev) => prev ? data.find((a) => a.id === prev.id) ?? null : null);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [token]);

  useEffect(() => { load(); }, [load]);

  const handleInviaDelega = async () => {
    const id = parseInt(delegatoInput);
    if (!id || !selected) { setDelegaError('Inserisci un ID valido'); return; }
    setDelegaSaving(true);
    setDelegaError('');
    try {
      await inviaDelega(token, selected.id, id);
      setShowDelega(false);
      await load();
    } catch (e) {
      setDelegaError((e as Error).message);
    } finally {
      setDelegaSaving(false);
    }
  };

  return (
    <div className="flex flex-col flex-1 overflow-hidden">
      {/* Header */}
      <header className="flex items-center justify-between px-6 py-3 border-b flex-shrink-0"
        style={{ backgroundColor: '#fff', borderColor: '#f0f0f0' }}>
        <div>
          <h1 className="text-sm font-bold" style={{ color: '#1a1a1a' }}>Assemblee</h1>
          <p className="text-xs" style={{ color: '#888' }}>Convocazioni e verbali del tuo condominio</p>
        </div>
        <button onClick={() => { setRefreshing(true); load(); }} disabled={refreshing}
          className="p-2 rounded-lg" style={{ border: '1px solid #e5e7eb', color: '#666' }}>
          <RefreshCw size={14} className={refreshing ? 'animate-spin' : ''} />
        </button>
      </header>

      <div className="flex flex-1 overflow-hidden">
        {/* Lista */}
        <div className="flex flex-col flex-shrink-0 overflow-y-auto" style={{ width: 320, borderRight: '1px solid #f0f0f0' }}>
          {loading && <p className="text-xs text-center mt-8" style={{ color: '#aaa' }}>Caricamento...</p>}
          {!loading && assemblee.length === 0 && (
            <div className="flex flex-col items-center justify-center py-20 gap-2">
              <CalendarDays size={32} style={{ color: '#ddd' }} />
              <p className="text-sm" style={{ color: '#aaa' }}>Nessuna assemblea</p>
            </div>
          )}
          {assemblee.map((a) => {
            const isActive = selected?.id === a.id;
            return (
              <button key={a.id} onClick={() => setSelected(a)}
                className="w-full text-left p-4 transition-all"
                style={{
                  backgroundColor: isActive ? '#eff6ff' : '#fff',
                  borderBottom: '1px solid #f5f5f5',
                  borderLeft: isActive ? '3px solid var(--primary)' : '3px solid transparent',
                }}>
                <div className="flex items-start justify-between gap-2 mb-1.5">
                  <StatoBadge stato={a.stato} />
                  <span className="text-xs" style={{ color: '#ccc' }}>{fmtDate(a.data)}</span>
                </div>
                <p className="text-xs font-bold truncate mb-0.5" style={{ color: '#1a1a1a' }}>{a.titolo}</p>
                <div className="flex items-center gap-2 text-xs" style={{ color: '#aaa' }}>
                  {a.luogo && <span className="flex items-center gap-0.5"><MapPin size={10} /> {a.luogo}</span>}
                  {a.miaPresenza?.presente && <span style={{ color: '#16a34a' }}>• Presente</span>}
                  {a.miaPresenza?.delegatoId && <span style={{ color: '#d97706' }}>• Delegato</span>}
                </div>
              </button>
            );
          })}
        </div>

        {/* Dettaglio */}
        {!selected ? (
          <div className="flex flex-1 items-center justify-center flex-col gap-2">
            <CalendarDays size={36} style={{ color: '#ddd' }} />
            <p className="text-sm" style={{ color: '#aaa' }}>Seleziona un'assemblea</p>
          </div>
        ) : (
          <div className="flex flex-col flex-1 overflow-y-auto p-6" style={{ backgroundColor: '#fafafa' }}>
            <div className="rounded-2xl overflow-hidden" style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0', maxWidth: 680 }}>
              {/* Intestazione */}
              <div className="p-5 border-b" style={{ borderColor: '#f5f5f5' }}>
                <div className="flex items-center gap-2 mb-2">
                  <StatoBadge stato={selected.stato} />
                  <span className="text-xs px-2 py-0.5 rounded-full" style={{ backgroundColor: '#f5f5f5', color: '#666' }}>
                    {selected.tipo.charAt(0).toUpperCase() + selected.tipo.slice(1)}
                  </span>
                </div>
                <h2 className="text-base font-bold mb-1" style={{ color: '#1a1a1a' }}>{selected.titolo}</h2>
                <div className="flex items-center gap-4 text-xs" style={{ color: '#aaa' }}>
                  <span className="flex items-center gap-1"><CalendarDays size={12} /> {fmtDateTime(selected.data)}</span>
                  {selected.luogo && <span className="flex items-center gap-1"><MapPin size={12} /> {selected.luogo}</span>}
                </div>
              </div>

              <div className="p-5 space-y-5">
                {/* Stato presenza */}
                {selected.miaPresenza ? (
                  <div className="flex items-center gap-3 px-4 py-3 rounded-xl"
                    style={{ backgroundColor: selected.miaPresenza.presente ? '#f0fdf4' : selected.miaPresenza.delegatoId ? '#fffbeb' : '#fafafa', border: '1px solid', borderColor: selected.miaPresenza.presente ? '#bbf7d0' : '#e5e7eb' }}>
                    {selected.miaPresenza.presente
                      ? <><CheckCircle2 size={16} style={{ color: '#16a34a' }} /><p className="text-sm font-semibold" style={{ color: '#16a34a' }}>Sei registrato come presente</p></>
                      : selected.miaPresenza.delegatoId
                        ? <><Users size={16} style={{ color: '#d97706' }} /><p className="text-sm font-semibold" style={{ color: '#d97706' }}>Hai inviato una delega</p></>
                        : <><Info size={16} style={{ color: '#aaa' }} /><p className="text-sm" style={{ color: '#aaa' }}>Non ancora registrato</p></>
                    }
                  </div>
                ) : null}

                {/* Azione delega */}
                {selected.stato === 'convocata' && (
                  <button onClick={() => setShowDelega(true)}
                    className="w-full flex items-center justify-center gap-2 py-3 rounded-xl text-sm font-semibold"
                    style={{ border: '2px solid var(--primary)', color: 'var(--primary)' }}>
                    <Users size={16} /> Invia delega
                  </button>
                )}

                {/* Ordine del giorno */}
                {selected.ordineDelGiorno && (
                  <div>
                    <p className="text-xs font-bold mb-2" style={{ color: '#555' }}>Ordine del giorno</p>
                    <div className="rounded-xl p-4 whitespace-pre-wrap text-sm"
                      style={{ backgroundColor: '#fafafa', color: '#333', border: '1px solid #f0f0f0', lineHeight: 1.7 }}>
                      {selected.ordineDelGiorno}
                    </div>
                  </div>
                )}

                {/* Punti OdG strutturati */}
                {selected.puntiOdG.length > 0 && (
                  <div>
                    <p className="text-xs font-bold mb-2" style={{ color: '#555' }}>Punti votati</p>
                    <div className="space-y-2">
                      {selected.puntiOdG.map((p) => (
                        <div key={p.id} className="flex items-start gap-3 px-4 py-3 rounded-xl"
                          style={{ backgroundColor: '#fafafa', border: '1px solid #f0f0f0' }}>
                          <span className="text-xs font-bold px-1.5 py-0.5 rounded flex-shrink-0"
                            style={{ backgroundColor: '#f0f0f0', color: '#666' }}>{p.ordine}</span>
                          <div className="flex-1">
                            <p className="text-sm font-semibold" style={{ color: '#1a1a1a' }}>{p.titolo}</p>
                            {p.descrizione && <p className="text-xs mt-0.5" style={{ color: '#888' }}>{p.descrizione}</p>}
                            {p.esito && (
                              <p className="text-xs font-bold mt-1" style={{ color: ESITO_CFG[p.esito]?.color ?? '#888' }}>
                                → {ESITO_CFG[p.esito]?.label ?? p.esito}
                              </p>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Verbale */}
                {selected.verbale && (
                  <div>
                    <p className="text-xs font-bold mb-2" style={{ color: '#555' }}>Verbale</p>
                    <div className="rounded-xl p-4 whitespace-pre-wrap text-sm"
                      style={{ backgroundColor: '#fafafa', color: '#333', border: '1px solid #f0f0f0', lineHeight: 1.7 }}>
                      {selected.verbale}
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Modale delega */}
      {showDelega && selected && (
        <Modal title="Invia delega" onClose={() => setShowDelega(false)}>
          <div className="space-y-4">
            <p className="text-sm" style={{ color: '#555' }}>
              Indica il numero ID del condòmino a cui vuoi delegare il tuo voto per <b>{selected.titolo}</b>.
            </p>
            <div>
              <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>ID del delegato</label>
              <input
                type="number" value={delegatoInput} onChange={(e) => setDelegaInput(e.target.value)}
                placeholder="es. 3"
                className="w-full px-3 py-2 rounded-lg text-sm outline-none"
                style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
              />
            </div>
            {delegaError && <p className="text-xs" style={{ color: '#dc2626' }}>{delegaError}</p>}
            <div className="flex justify-end gap-2 pt-2">
              <button onClick={() => setShowDelega(false)} className="px-4 py-2 rounded-lg text-sm"
                style={{ border: '1px solid #e5e7eb', color: '#666' }}>
                Annulla
              </button>
              <button onClick={handleInviaDelega} disabled={delegaSaving}
                className="px-4 py-2 rounded-lg text-sm font-semibold text-white"
                style={{ backgroundColor: 'var(--primary)', opacity: delegaSaving ? 0.7 : 1 }}>
                {delegaSaving ? 'Invio...' : 'Invia delega'}
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}

// ─── Pagina principale ────────────────────────────────────────────────────────

export default function AssembleePage() {
  const { token, user } = useAuth();
  if (!token || !user) return null;

  return user.role === 'AMMINISTRATORE'
    ? <AdminView token={token} />
    : <CondominoView token={token} />;
}
