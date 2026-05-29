'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  CreditCard, Plus, X, ChevronRight, Zap, Trash2,
  CheckCircle, Clock, AlertTriangle, CalendarDays, TrendingUp,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import {
  fetchCondominii,
  fetchQuote,
  createQuota,
  deleteQuota,
  generaPagamenti,
  fetchPagamenti,
  updatePagamento,
  fetchBilancio,
  CondominioListItem,
  QuotaMensileItem,
  PagamentoItem,
  BilancioData,
} from '@/lib/api';

// ─── Helpers ──────────────────────────────────────────────────────────────────

const MESI = ['Gen', 'Feb', 'Mar', 'Apr', 'Mag', 'Giu', 'Lug', 'Ago', 'Set', 'Ott', 'Nov', 'Dic'];

const STATO_CONFIG: Record<string, { label: string; bg: string; color: string; icon: React.ReactNode }> = {
  pagato: { label: 'Pagato', bg: '#f0fdf4', color: '#16a34a', icon: <CheckCircle size={12} /> },
  in_attesa: { label: 'In attesa', bg: '#fffbeb', color: '#d97706', icon: <Clock size={12} /> },
  in_mora: { label: 'In mora', bg: '#fef2f2', color: '#dc2626', icon: <AlertTriangle size={12} /> },
};

const METODI = ['bonifico', 'contanti', 'assegno', 'pos', 'altro'];

function fmt(n: number) {
  return n.toLocaleString('it-IT', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function quotaStats(quota: QuotaMensileItem) {
  const pagati = quota.pagamenti.filter((p) => p.stato === 'pagato').reduce((s, p) => s + p.importo, 0);
  const mora = quota.pagamenti.filter((p) => p.stato === 'in_mora').reduce((s, p) => s + p.importo, 0);
  const attesa = quota.pagamenti.filter((p) => p.stato === 'in_attesa').reduce((s, p) => s + p.importo, 0);
  return { pagati, mora, attesa };
}

// ─── Modal ────────────────────────────────────────────────────────────────────

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center" style={{ backgroundColor: 'rgba(0,0,0,0.4)' }} onClick={onClose}>
      <div className="w-full max-w-md rounded-2xl p-6 shadow-xl" style={{ backgroundColor: '#fff', maxHeight: '90vh', overflowY: 'auto' }} onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between mb-5">
          <h2 className="text-base font-bold" style={{ color: '#1a1a1a' }}>{title}</h2>
          <button onClick={onClose} style={{ color: '#888' }}><X size={18} /></button>
        </div>
        {children}
      </div>
    </div>
  );
}

// ─── Pagina ───────────────────────────────────────────────────────────────────

export default function PagamentiPage() {
  const { token } = useAuth();

  // Dati
  const [condominii, setCondominii] = useState<CondominioListItem[]>([]);
  const [selectedCondo, setSelectedCondo] = useState<CondominioListItem | null>(null);
  const [quote, setQuote] = useState<QuotaMensileItem[]>([]);
  const [selectedQuota, setSelectedQuota] = useState<QuotaMensileItem | null>(null);
  const [pagamenti, setPagamenti] = useState<PagamentoItem[]>([]);
  const [bilancio, setBilancio] = useState<BilancioData | null>(null);

  // Loading states
  const [loadingCondo, setLoadingCondo] = useState(true);
  const [loadingQuote, setLoadingQuote] = useState(false);
  const [loadingPagamenti, setLoadingPagamenti] = useState(false);
  const [generando, setGenerando] = useState(false);
  const [eliminando, setEliminando] = useState(false);

  // Filtro pagamenti
  const [filterStato, setFilterStato] = useState<string>('tutti');

  // Modal quota
  const [showQuotaModal, setShowQuotaModal] = useState(false);
  const [qMese, setQMese] = useState<string>('1');
  const [qAnno, setQAnno] = useState<string>(String(new Date().getFullYear()));
  const [qImporto, setQImporto] = useState<string>('');
  const [quotaSaving, setQuotaSaving] = useState(false);
  const [quotaError, setQuotaError] = useState<string | null>(null);

  // Modal registra pagamento
  const [editingPagamento, setEditingPagamento] = useState<PagamentoItem | null>(null);
  const [editStato, setEditStato] = useState<string>('pagato');
  const [editImporto, setEditImporto] = useState<string>('');
  const [editData, setEditData] = useState<string>('');
  const [editMetodo, setEditMetodo] = useState<string>('bonifico');
  const [editNote, setEditNote] = useState<string>('');
  const [editSaving, setEditSaving] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  // ── Load condominii ───────────────────────────────────────────────────────

  const loadCondominii = useCallback(async () => {
    if (!token) return;
    setLoadingCondo(true);
    try {
      const data = await fetchCondominii(token);
      setCondominii(data);
      if (data.length === 1) selectCondo(data[0]);
    } finally {
      setLoadingCondo(false);
    }
  }, [token]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { loadCondominii(); }, [loadCondominii]);

  // ── Select condominio ─────────────────────────────────────────────────────

  const loadQuoteAndBilancio = useCallback(async (condoId: number) => {
    if (!token) return;
    setLoadingQuote(true);
    try {
      const [q, b] = await Promise.all([fetchQuote(token, condoId), fetchBilancio(token, condoId)]);
      setQuote(q);
      setBilancio(b);
      setSelectedQuota(null);
      setPagamenti([]);
    } finally {
      setLoadingQuote(false);
    }
  }, [token]);

  const selectCondo = useCallback((c: CondominioListItem) => {
    setSelectedCondo(c);
    loadQuoteAndBilancio(c.id);
  }, [loadQuoteAndBilancio]);

  // ── Select quota ──────────────────────────────────────────────────────────

  const selectQuota = async (q: QuotaMensileItem) => {
    if (!token) return;
    setSelectedQuota(q);
    setLoadingPagamenti(true);
    setFilterStato('tutti');
    try {
      const data = await fetchPagamenti(token, q.id);
      setPagamenti(data);
    } finally {
      setLoadingPagamenti(false);
    }
  };

  // ── Crea quota ────────────────────────────────────────────────────────────

  const handleCreateQuota = async () => {
    if (!token || !selectedCondo) return;
    const importo = Number(qImporto);
    if (!qImporto || isNaN(importo) || importo <= 0) {
      setQuotaError("Inserisci un importo valido");
      return;
    }
    setQuotaSaving(true);
    setQuotaError(null);
    try {
      await createQuota(token, selectedCondo.id, Number(qMese), Number(qAnno), importo);
      setShowQuotaModal(false);
      setQImporto('');
      await loadQuoteAndBilancio(selectedCondo.id);
    } catch (e) {
      setQuotaError((e as Error).message);
    } finally {
      setQuotaSaving(false);
    }
  };

  // ── Genera pagamenti ──────────────────────────────────────────────────────

  const handleGenera = async () => {
    if (!token || !selectedQuota) return;
    setGenerando(true);
    try {
      const data = await generaPagamenti(token, selectedQuota.id);
      setPagamenti(data);
      await loadQuoteAndBilancio(selectedCondo!.id);
      // refresh quota list item
      setQuote((prev) => prev.map((q) => q.id === selectedQuota.id ? { ...q, _count: { pagamenti: data.length }, pagamenti: data.map((p) => ({ stato: p.stato, importo: p.importo })) } : q));
    } catch (e) {
      alert((e as Error).message);
    } finally {
      setGenerando(false);
    }
  };

  // ── Elimina quota ─────────────────────────────────────────────────────────

  const handleDeleteQuota = async () => {
    if (!token || !selectedQuota || !selectedCondo) return;
    if (!confirm(`Eliminare la quota ${MESI[selectedQuota.mese - 1]} ${selectedQuota.anno}?`)) return;
    setEliminando(true);
    try {
      await deleteQuota(token, selectedQuota.id);
      setSelectedQuota(null);
      setPagamenti([]);
      await loadQuoteAndBilancio(selectedCondo.id);
    } catch (e) {
      alert((e as Error).message);
    } finally {
      setEliminando(false);
    }
  };

  // ── Registra / modifica pagamento ─────────────────────────────────────────

  const openEdit = (p: PagamentoItem) => {
    setEditingPagamento(p);
    setEditStato(p.stato);
    setEditImporto(String(p.importo));
    setEditData(p.dataPagamento ? p.dataPagamento.slice(0, 10) : new Date().toISOString().slice(0, 10));
    setEditMetodo(p.metodoPagamento ?? 'bonifico');
    setEditNote(p.note ?? '');
    setEditError(null);
  };

  const handleSaveEdit = async () => {
    if (!token || !editingPagamento) return;
    setEditSaving(true);
    setEditError(null);
    try {
      const updated = await updatePagamento(token, editingPagamento.id, {
        stato: editStato,
        importo: Number(editImporto),
        dataPagamento: editStato === 'pagato' ? editData : null,
        metodoPagamento: editStato === 'pagato' ? editMetodo : null,
        note: editNote.trim() || null,
      });
      setPagamenti((prev) => prev.map((p) => p.id === updated.id ? { ...p, ...updated } : p));
      // refresh stats in quota list
      setQuote((prev) =>
        prev.map((q) => {
          if (q.id !== selectedQuota?.id) return q;
          const newPagamenti = q.pagamenti.map((pp, i) =>
            pagamenti[i]?.id === updated.id ? { stato: updated.stato, importo: updated.importo } : pp
          );
          return { ...q, pagamenti: newPagamenti };
        })
      );
      setEditingPagamento(null);
    } catch (e) {
      setEditError((e as Error).message);
    } finally {
      setEditSaving(false);
    }
  };

  // ── Filtered pagamenti ────────────────────────────────────────────────────

  const filteredPagamenti = filterStato === 'tutti' ? pagamenti : pagamenti.filter((p) => p.stato === filterStato);

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <div className="flex flex-col flex-1 overflow-hidden" style={{ backgroundColor: 'var(--background)' }}>
      {/* Header */}
      <header className="flex items-center justify-between px-6 py-3 border-b flex-shrink-0" style={{ backgroundColor: '#fff', borderColor: '#f0f0f0' }}>
        <div>
          <h1 className="text-sm font-semibold" style={{ color: '#1a1a1a' }}>Quote & Pagamenti</h1>
          <p className="text-xs" style={{ color: '#888' }}>Gestione quote mensili e monitoraggio pagamenti</p>
        </div>
        {selectedCondo && (
          <button
            onClick={() => { setQuotaError(null); setQMese(String(new Date().getMonth() + 1)); setShowQuotaModal(true); }}
            className="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold text-white hover:opacity-90"
            style={{ backgroundColor: 'var(--primary)' }}
          >
            <Plus size={15} />
            Nuova Quota
          </button>
        )}
      </header>

      <div className="flex flex-1 overflow-hidden">
        {/* ── Colonna sinistra: condominii + quote ─────────────────────── */}
        <div className="flex flex-col flex-shrink-0 overflow-y-auto" style={{ width: 280, borderRight: '1px solid #f0f0f0', backgroundColor: '#fafafa' }}>
          {/* Selector condominio */}
          <div className="p-4 border-b" style={{ borderColor: '#f0f0f0' }}>
            <p className="text-xs font-semibold uppercase tracking-widest mb-2" style={{ color: '#bbb' }}>Condominio</p>
            {loadingCondo ? (
              <p className="text-xs" style={{ color: '#aaa' }}>Caricamento...</p>
            ) : (
              <select
                value={selectedCondo?.id ?? ''}
                onChange={(e) => {
                  const c = condominii.find((x) => x.id === Number(e.target.value));
                  if (c) selectCondo(c);
                }}
                className="w-full px-3 py-2 rounded-lg text-sm outline-none"
                style={{ border: '1px solid #e5e7eb', backgroundColor: '#fff' }}
              >
                <option value="">Seleziona condominio…</option>
                {condominii.map((c) => <option key={c.id} value={c.id}>{c.nome}</option>)}
              </select>
            )}
          </div>

          {/* Lista quote */}
          <div className="flex flex-col flex-1 p-3 gap-2">
            <p className="text-xs font-semibold uppercase tracking-widest px-1" style={{ color: '#bbb' }}>Quote mensili</p>
            {loadingQuote && <p className="text-xs text-center mt-4" style={{ color: '#aaa' }}>Caricamento...</p>}
            {!loadingQuote && !selectedCondo && <p className="text-xs text-center mt-4" style={{ color: '#ccc' }}>Seleziona un condominio</p>}
            {!loadingQuote && selectedCondo && quote.length === 0 && (
              <div className="text-center mt-6">
                <CreditCard size={28} style={{ color: '#ddd', margin: '0 auto 6px' }} />
                <p className="text-xs" style={{ color: '#aaa' }}>Nessuna quota</p>
                <p className="text-xs" style={{ color: '#ccc' }}>Clicca "Nuova Quota"</p>
              </div>
            )}
            {quote.map((q) => {
              const isActive = selectedQuota?.id === q.id;
              const stats = quotaStats(q);
              const pct = q.importoTotale > 0 ? Math.round((stats.pagati / q.importoTotale) * 100) : 0;
              return (
                <button
                  key={q.id}
                  onClick={() => selectQuota(q)}
                  className="w-full text-left rounded-xl p-3 transition-all"
                  style={{ backgroundColor: isActive ? '#fef2f2' : '#fff', border: isActive ? '1.5px solid var(--primary)' : '1.5px solid #f0f0f0' }}
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-sm font-bold" style={{ color: isActive ? 'var(--primary)' : '#222' }}>
                      {MESI[q.mese - 1]} {q.anno}
                    </span>
                    <ChevronRight size={13} style={{ color: '#ccc' }} />
                  </div>
                  <p className="text-xs font-semibold mb-1.5" style={{ color: '#666' }}>€ {fmt(q.importoTotale)}</p>
                  {/* Progress bar */}
                  <div className="w-full rounded-full h-1.5" style={{ backgroundColor: '#f0f0f0' }}>
                    <div className="h-1.5 rounded-full" style={{ width: `${pct}%`, backgroundColor: '#16a34a' }} />
                  </div>
                  <div className="flex justify-between mt-1">
                    <span className="text-xs" style={{ color: '#16a34a' }}>{pct}% pagato</span>
                    {stats.mora > 0 && <span className="text-xs" style={{ color: '#dc2626' }}>€{fmt(stats.mora)} mora</span>}
                  </div>
                </button>
              );
            })}
          </div>
        </div>

        {/* ── Colonna destra: bilancio + pagamenti ─────────────────────── */}
        <div className="flex flex-col flex-1 overflow-hidden">
          {!selectedCondo ? (
            <div className="flex flex-col items-center justify-center h-full">
              <CreditCard size={48} style={{ color: '#ddd', marginBottom: 12 }} />
              <p className="text-sm font-medium" style={{ color: '#aaa' }}>Seleziona un condominio</p>
            </div>
          ) : !selectedQuota ? (
            <div className="flex flex-col h-full overflow-y-auto">
              {/* Bilancio globale */}
              {bilancio && (
                <div className="p-6">
                  <p className="text-xs font-semibold uppercase tracking-widest mb-4" style={{ color: '#bbb' }}>Riepilogo – {selectedCondo.nome}</p>
                  <div className="grid grid-cols-2 gap-3 mb-6" style={{ maxWidth: 600 }}>
                    {[
                      { label: 'Totale emesso', value: `€ ${fmt(bilancio.totaleEmesso)}`, color: '#1a1a1a', bg: '#fff' },
                      { label: 'Incassato', value: `€ ${fmt(bilancio.totalePagato)}`, color: '#16a34a', bg: '#f0fdf4' },
                      { label: 'In attesa', value: `€ ${fmt(bilancio.totaleAttesa)}`, color: '#d97706', bg: '#fffbeb' },
                      { label: 'In mora', value: `€ ${fmt(bilancio.totaleMora)}`, color: '#dc2626', bg: '#fef2f2' },
                    ].map((s) => (
                      <div key={s.label} className="rounded-xl p-4" style={{ backgroundColor: s.bg, border: '1px solid #f0f0f0' }}>
                        <p className="text-xs mb-1" style={{ color: '#888' }}>{s.label}</p>
                        <p className="text-lg font-bold" style={{ color: s.color }}>{s.value}</p>
                      </div>
                    ))}
                  </div>
                  {/* Barra incasso */}
                  <div style={{ maxWidth: 600 }}>
                    <div className="flex justify-between mb-1">
                      <span className="text-xs" style={{ color: '#888' }}>Tasso incasso</span>
                      <span className="text-xs font-bold" style={{ color: '#16a34a' }}>{bilancio.percentualePagata}%</span>
                    </div>
                    <div className="w-full rounded-full h-3" style={{ backgroundColor: '#f0f0f0' }}>
                      <div className="h-3 rounded-full" style={{ width: `${bilancio.percentualePagata}%`, backgroundColor: '#16a34a' }} />
                    </div>
                    <div className="flex gap-4 mt-3">
                      <span className="text-xs" style={{ color: '#888' }}>
                        <span className="font-semibold" style={{ color: '#16a34a' }}>{bilancio.numeroPagati}</span> pagati
                      </span>
                      <span className="text-xs" style={{ color: '#888' }}>
                        <span className="font-semibold" style={{ color: '#d97706' }}>{bilancio.numeroAttesa}</span> in attesa
                      </span>
                      <span className="text-xs" style={{ color: '#888' }}>
                        <span className="font-semibold" style={{ color: '#dc2626' }}>{bilancio.numeroMora}</span> in mora
                      </span>
                    </div>
                  </div>
                </div>
              )}
              <div className="flex flex-col items-center justify-center flex-1 pb-16">
                <TrendingUp size={36} style={{ color: '#ddd', marginBottom: 8 }} />
                <p className="text-sm" style={{ color: '#aaa' }}>Seleziona una quota per vedere i pagamenti</p>
              </div>
            </div>
          ) : (
            <div className="flex flex-col flex-1 overflow-hidden">
              {/* Intestazione quota */}
              <div className="flex items-center justify-between px-6 py-4 flex-shrink-0" style={{ borderBottom: '1px solid #f0f0f0', backgroundColor: '#fff' }}>
                <div>
                  <h2 className="text-base font-bold" style={{ color: '#1a1a1a' }}>
                    Quota {MESI[selectedQuota.mese - 1]} {selectedQuota.anno}
                  </h2>
                  <p className="text-xs" style={{ color: '#888' }}>
                    Importo totale: <strong>€ {fmt(selectedQuota.importoTotale)}</strong> · {selectedQuota._count.pagamenti} condòmini
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {selectedQuota._count.pagamenti === 0 && (
                    <button
                      onClick={handleGenera}
                      disabled={generando}
                      className="flex items-center gap-1.5 px-3 py-2 rounded-lg text-sm font-semibold text-white hover:opacity-90"
                      style={{ backgroundColor: '#2563eb', opacity: generando ? 0.7 : 1 }}
                    >
                      <Zap size={13} />
                      {generando ? 'Generando...' : 'Genera pagamenti'}
                    </button>
                  )}
                  <button
                    onClick={handleDeleteQuota}
                    disabled={eliminando}
                    className="flex items-center gap-1.5 px-3 py-2 rounded-lg text-sm font-semibold"
                    style={{ backgroundColor: '#fef2f2', color: '#dc2626' }}
                  >
                    <Trash2 size={13} />
                    Elimina
                  </button>
                </div>
              </div>

              {/* Filtri pagamenti */}
              <div className="flex items-center gap-3 px-6 py-2.5 flex-shrink-0" style={{ borderBottom: '1px solid #f5f5f5', backgroundColor: '#fafafa' }}>
                {['tutti', 'pagato', 'in_attesa', 'in_mora'].map((s) => (
                  <button
                    key={s}
                    onClick={() => setFilterStato(s)}
                    className="px-3 py-1 rounded-lg text-xs font-semibold transition"
                    style={{
                      backgroundColor: filterStato === s ? 'var(--primary)' : '#f0f0f0',
                      color: filterStato === s ? '#fff' : '#666',
                    }}
                  >
                    {s === 'tutti' ? 'Tutti' : STATO_CONFIG[s]?.label ?? s}
                    {' '}({s === 'tutti' ? pagamenti.length : pagamenti.filter((p) => p.stato === s).length})
                  </button>
                ))}
              </div>

              {/* Lista pagamenti */}
              <div className="flex-1 overflow-y-auto p-5">
                {loadingPagamenti && <p className="text-sm text-center mt-8" style={{ color: '#aaa' }}>Caricamento...</p>}

                {!loadingPagamenti && pagamenti.length === 0 && (
                  <div className="text-center mt-12">
                    <CalendarDays size={36} style={{ color: '#ddd', margin: '0 auto 8px' }} />
                    <p className="text-sm font-medium" style={{ color: '#aaa' }}>Nessun pagamento generato</p>
                    <p className="text-xs mt-1" style={{ color: '#ccc' }}>Usa "Genera pagamenti" per creare le rate automaticamente</p>
                  </div>
                )}

                {!loadingPagamenti && pagamenti.length > 0 && filteredPagamenti.length === 0 && (
                  <p className="text-sm text-center mt-8" style={{ color: '#aaa' }}>Nessun pagamento con questo filtro</p>
                )}

                {!loadingPagamenti && filteredPagamenti.length > 0 && (
                  <div className="grid grid-cols-1 gap-2" style={{ maxWidth: 720 }}>
                    {filteredPagamenti.map((p) => {
                      const cfg = STATO_CONFIG[p.stato] ?? { label: p.stato, bg: '#f5f5f5', color: '#888', icon: null };
                      return (
                        <div
                          key={p.id}
                          className="flex items-center gap-4 rounded-xl px-4 py-3"
                          style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0' }}
                        >
                          {/* Avatar */}
                          <div className="w-9 h-9 flex-shrink-0 rounded-full flex items-center justify-center text-xs font-bold" style={{ backgroundColor: '#fef2f2', color: 'var(--primary)' }}>
                            {p.condomino.nome[0]}{p.condomino.cognome[0]}
                          </div>
                          {/* Info */}
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-2">
                              <span className="text-sm font-semibold" style={{ color: '#1a1a1a' }}>{p.condomino.nome} {p.condomino.cognome}</span>
                              <span className="text-xs px-1.5 py-0.5 rounded-full font-medium" style={{ backgroundColor: '#f0fdf4', color: '#16a34a' }}>Unità {p.condomino.unita}</span>
                            </div>
                            <div className="flex items-center gap-3 mt-0.5">
                              <span className="text-xs font-bold" style={{ color: '#1a1a1a' }}>€ {fmt(p.importo)}</span>
                              {p.dataPagamento && (
                                <span className="text-xs" style={{ color: '#aaa' }}>
                                  {new Date(p.dataPagamento).toLocaleDateString('it-IT')}
                                </span>
                              )}
                              {p.metodoPagamento && (
                                <span className="text-xs" style={{ color: '#aaa' }}>· {p.metodoPagamento}</span>
                              )}
                              {p.note && <span className="text-xs truncate" style={{ color: '#bbb', maxWidth: 180 }}>"{p.note}"</span>}
                            </div>
                          </div>
                          {/* Stato */}
                          <div className="flex items-center gap-2">
                            <span className="flex items-center gap-1 text-xs px-2 py-1 rounded-full font-semibold" style={{ backgroundColor: cfg.bg, color: cfg.color }}>
                              {cfg.icon}{cfg.label}
                            </span>
                            <button
                              onClick={() => openEdit(p)}
                              className="px-2.5 py-1 rounded-lg text-xs font-semibold"
                              style={{ backgroundColor: '#f5f5f5', color: '#555' }}
                            >
                              Registra
                            </button>
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

      {/* Modal: Nuova Quota */}
      {showQuotaModal && (
        <Modal title="Nuova Quota Mensile" onClose={() => setShowQuotaModal(false)}>
          <div className="flex flex-col gap-4">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Mese <span style={{ color: 'var(--primary)' }}>*</span></label>
                <select value={qMese} onChange={(e) => setQMese(e.target.value)} className="w-full px-3 py-2 rounded-lg text-sm outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}>
                  {MESI.map((m, i) => <option key={i} value={i + 1}>{m}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Anno <span style={{ color: 'var(--primary)' }}>*</span></label>
                <input type="number" value={qAnno} onChange={(e) => setQAnno(e.target.value)} className="w-full px-3 py-2 rounded-lg text-sm outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }} />
              </div>
            </div>
            <div>
              <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Importo totale (€) <span style={{ color: 'var(--primary)' }}>*</span></label>
              <input type="number" step="0.01" value={qImporto} onChange={(e) => setQImporto(e.target.value)} placeholder="es. 1500.00" className="w-full px-3 py-2 rounded-lg text-sm outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }} />
              <p className="text-xs mt-1" style={{ color: '#aaa' }}>L'importo verrà ripartito per millesimi al momento della generazione</p>
            </div>
            {quotaError && <p className="text-xs px-3 py-2 rounded-lg" style={{ backgroundColor: '#fef2f2', color: 'var(--primary)' }}>{quotaError}</p>}
            <div className="flex gap-2 pt-2">
              <button onClick={() => setShowQuotaModal(false)} className="flex-1 py-2 rounded-lg text-sm font-semibold" style={{ backgroundColor: '#f5f5f5', color: '#555' }}>Annulla</button>
              <button onClick={handleCreateQuota} disabled={quotaSaving} className="flex-1 py-2 rounded-lg text-sm font-semibold text-white hover:opacity-90" style={{ backgroundColor: 'var(--primary)', opacity: quotaSaving ? 0.7 : 1 }}>
                {quotaSaving ? 'Salvataggio...' : 'Crea Quota'}
              </button>
            </div>
          </div>
        </Modal>
      )}

      {/* Modal: Registra pagamento */}
      {editingPagamento && (
        <Modal title={`Pagamento – ${editingPagamento.condomino.nome} ${editingPagamento.condomino.cognome}`} onClose={() => setEditingPagamento(null)}>
          <div className="flex flex-col gap-4">
            <div>
              <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Stato <span style={{ color: 'var(--primary)' }}>*</span></label>
              <div className="flex gap-2">
                {Object.entries(STATO_CONFIG).map(([k, v]) => (
                  <button
                    key={k}
                    onClick={() => setEditStato(k)}
                    className="flex-1 py-2 rounded-lg text-xs font-semibold transition"
                    style={{ backgroundColor: editStato === k ? v.bg : '#f5f5f5', color: editStato === k ? v.color : '#888', border: editStato === k ? `1.5px solid ${v.color}` : '1.5px solid #f0f0f0' }}
                  >
                    {v.label}
                  </button>
                ))}
              </div>
            </div>
            <div>
              <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Importo (€) <span style={{ color: 'var(--primary)' }}>*</span></label>
              <input type="number" step="0.01" value={editImporto} onChange={(e) => setEditImporto(e.target.value)} className="w-full px-3 py-2 rounded-lg text-sm outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }} />
            </div>
            {editStato === 'pagato' && (
              <>
                <div>
                  <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Data pagamento</label>
                  <input type="date" value={editData} onChange={(e) => setEditData(e.target.value)} className="w-full px-3 py-2 rounded-lg text-sm outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }} />
                </div>
                <div>
                  <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Metodo di pagamento</label>
                  <select value={editMetodo} onChange={(e) => setEditMetodo(e.target.value)} className="w-full px-3 py-2 rounded-lg text-sm outline-none" style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}>
                    {METODI.map((m) => <option key={m} value={m}>{m.charAt(0).toUpperCase() + m.slice(1)}</option>)}
                  </select>
                </div>
              </>
            )}
            <div>
              <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Note</label>
              <textarea
                value={editNote}
                onChange={(e) => setEditNote(e.target.value)}
                placeholder="Note opzionali…"
                rows={2}
                className="w-full px-3 py-2 rounded-lg text-sm outline-none resize-none"
                style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}
              />
            </div>
            {editError && <p className="text-xs px-3 py-2 rounded-lg" style={{ backgroundColor: '#fef2f2', color: 'var(--primary)' }}>{editError}</p>}
            <div className="flex gap-2 pt-2">
              <button onClick={() => setEditingPagamento(null)} className="flex-1 py-2 rounded-lg text-sm font-semibold" style={{ backgroundColor: '#f5f5f5', color: '#555' }}>Annulla</button>
              <button onClick={handleSaveEdit} disabled={editSaving} className="flex-1 py-2 rounded-lg text-sm font-semibold text-white hover:opacity-90" style={{ backgroundColor: 'var(--primary)', opacity: editSaving ? 0.7 : 1 }}>
                {editSaving ? 'Salvataggio...' : 'Salva'}
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}
