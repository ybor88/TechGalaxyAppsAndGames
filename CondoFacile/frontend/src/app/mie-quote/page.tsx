'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  CreditCard, CheckCircle, Clock, AlertTriangle, Building2,
  Home, Printer, X, TrendingUp, BarChart2,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { fetchMieQuote, MieQuoteData, MioPagamento } from '@/lib/api';

// ─── Helpers ──────────────────────────────────────────────────────────────────

const MESI = ['Gennaio', 'Febbraio', 'Marzo', 'Aprile', 'Maggio', 'Giugno',
  'Luglio', 'Agosto', 'Settembre', 'Ottobre', 'Novembre', 'Dicembre'];

const MESI_SHORT = ['Gen', 'Feb', 'Mar', 'Apr', 'Mag', 'Giu', 'Lug', 'Ago', 'Set', 'Ott', 'Nov', 'Dic'];

const STATO_CONFIG: Record<string, { label: string; bg: string; color: string; icon: React.ReactNode }> = {
  pagato: { label: 'Pagato', bg: '#f0fdf4', color: '#16a34a', icon: <CheckCircle size={12} /> },
  in_attesa: { label: 'In attesa', bg: '#fffbeb', color: '#d97706', icon: <Clock size={12} /> },
  in_mora: { label: 'In mora', bg: '#fef2f2', color: '#dc2626', icon: <AlertTriangle size={12} /> },
};

const TIPO_CONFIG: Record<string, { label: string; bg: string; color: string }> = {
  proprietario: { label: 'Proprietario', bg: '#eff6ff', color: '#2563eb' },
  inquilino: { label: 'Inquilino', bg: '#f0fdf4', color: '#16a34a' },
  delegato: { label: 'Delegato', bg: '#faf5ff', color: '#7c3aed' },
};

function fmt(n: number) {
  return n.toLocaleString('it-IT', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function fmtDate(s: string | null) {
  if (!s) return '—';
  return new Date(s).toLocaleDateString('it-IT', { day: '2-digit', month: 'long', year: 'numeric' });
}

// ─── Ricevuta HTML (aperta in nuova finestra per la stampa) ───────────────────

function buildReceiptHtml(p: MioPagamento, data: MieQuoteData): string {
  const now = new Date().toLocaleDateString('it-IT', { day: '2-digit', month: 'long', year: 'numeric' });
  const dataPag = fmtDate(p.dataPagamento);
  const mese = MESI[p.quota.mese - 1];
  const { condomino, condominio } = data;

  return `<!DOCTYPE html>
<html lang="it">
<head>
<meta charset="UTF-8"/>
<title>Ricevuta #${p.id}</title>
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  body { font-family: Arial, sans-serif; color: #1a1a1a; padding: 48px; max-width: 680px; margin: 0 auto; }
  .logo { font-size: 24px; font-weight: bold; color: #dc2626; letter-spacing: -0.5px; }
  .logo span { color: #1a1a1a; }
  .header { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 2px solid #dc2626; padding-bottom: 20px; margin-bottom: 28px; }
  .header-right { text-align: right; }
  .header-right p { font-size: 13px; color: #666; margin-top: 4px; }
  .header-right strong { font-size: 15px; color: #1a1a1a; }
  .ricevuta-num { font-size: 11px; text-transform: uppercase; letter-spacing: 2px; color: #aaa; margin-bottom: 4px; }
  .section { margin-bottom: 24px; }
  .section-title { font-size: 10px; text-transform: uppercase; letter-spacing: 2px; color: #aaa; font-weight: bold; margin-bottom: 10px; padding-bottom: 6px; border-bottom: 1px solid #f0f0f0; }
  .row { display: flex; justify-content: space-between; padding: 7px 0; font-size: 13px; border-bottom: 1px solid #f8f8f8; }
  .row:last-child { border-bottom: none; }
  .label { color: #666; }
  .value { font-weight: 600; }
  .amount-box { background: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 12px; padding: 20px; text-align: center; margin: 24px 0; }
  .amount-label { font-size: 11px; text-transform: uppercase; letter-spacing: 2px; color: #16a34a; margin-bottom: 8px; }
  .amount { font-size: 36px; font-weight: bold; color: #16a34a; }
  .badge { display: inline-block; background: #f0fdf4; color: #16a34a; padding: 4px 14px; border-radius: 20px; font-weight: bold; font-size: 13px; border: 1px solid #bbf7d0; }
  .footer { margin-top: 48px; padding-top: 20px; border-top: 1px solid #f0f0f0; display: flex; justify-content: space-between; font-size: 11px; color: #aaa; }
  .firma { text-align: center; margin-top: 48px; }
  .firma-line { width: 220px; border-top: 1px solid #333; margin: 0 auto 6px; }
  .firma p { font-size: 12px; color: #555; }
  @media print {
    body { padding: 20px; }
    button { display: none; }
  }
</style>
</head>
<body>
<div class="header">
  <div>
    <div class="logo">Condo<span>Facile</span></div>
    <p style="font-size:12px; color:#888; margin-top:6px;">${condominio.nome}</p>
    <p style="font-size:11px; color:#aaa;">${condominio.indirizzo}</p>
  </div>
  <div class="header-right">
    <div class="ricevuta-num">Ricevuta di pagamento</div>
    <strong>#RIC-${String(p.id).padStart(5, '0')}</strong>
    <p>Emessa il ${now}</p>
  </div>
</div>

<div class="section">
  <div class="section-title">Condòmino</div>
  <div class="row"><span class="label">Nome e cognome</span><span class="value">${condomino.nome} ${condomino.cognome}</span></div>
  <div class="row"><span class="label">Unità immobiliare</span><span class="value">${condomino.unita}</span></div>
  <div class="row"><span class="label">Millesimi</span><span class="value">${condomino.millesimi}</span></div>
</div>

<div class="section">
  <div class="section-title">Quota</div>
  <div class="row"><span class="label">Competenza</span><span class="value">${mese} ${p.quota.anno}</span></div>
  <div class="row"><span class="label">Importo quota condominio</span><span class="value">€ ${fmt(p.quota.importoTotale)}</span></div>
</div>

<div class="amount-box">
  <div class="amount-label">Importo pagato</div>
  <div class="amount">€ ${fmt(p.importo)}</div>
</div>

<div class="section">
  <div class="section-title">Dettaglio pagamento</div>
  <div class="row"><span class="label">Stato</span><span><span class="badge">✓ Pagato</span></span></div>
  <div class="row"><span class="label">Data pagamento</span><span class="value">${dataPag}</span></div>
  ${p.metodoPagamento ? `<div class="row"><span class="label">Metodo</span><span class="value">${p.metodoPagamento.charAt(0).toUpperCase() + p.metodoPagamento.slice(1)}</span></div>` : ''}
  ${p.note ? `<div class="row"><span class="label">Note</span><span class="value">${p.note}</span></div>` : ''}
</div>

<div class="firma">
  <div class="firma-line"></div>
  <p>Firma Amministratore</p>
</div>

<div class="footer">
  <span>CondoFacile – gestione condominiale digitale</span>
  <span>Documento generato automaticamente</span>
</div>

<div style="text-align:center; margin-top:32px;">
  <button onclick="window.print()" style="background:#dc2626; color:#fff; border:none; padding:10px 28px; border-radius:8px; font-size:14px; font-weight:bold; cursor:pointer;">
    Stampa / Salva PDF
  </button>
</div>
</body>
</html>`;
}

// ─── Pagina ───────────────────────────────────────────────────────────────────

export default function MieQuotePage() {
  const { token } = useAuth();
  const [data, setData] = useState<MieQuoteData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filterStato, setFilterStato] = useState<string>('tutti');
  const [previewPag, setPreviewPag] = useState<MioPagamento | null>(null);

  const load = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError(null);
    try {
      const d = await fetchMieQuote(token);
      setData(d);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => { load(); }, [load]);

  // Statistiche
  const stats = useMemo(() => {
    if (!data) return null;
    const pags = data.pagamenti;
    return {
      totale: pags.reduce((s, p) => s + p.importo, 0),
      pagato: pags.filter((p) => p.stato === 'pagato').reduce((s, p) => s + p.importo, 0),
      attesa: pags.filter((p) => p.stato === 'in_attesa').reduce((s, p) => s + p.importo, 0),
      mora: pags.filter((p) => p.stato === 'in_mora').reduce((s, p) => s + p.importo, 0),
      nPagato: pags.filter((p) => p.stato === 'pagato').length,
      nAttesa: pags.filter((p) => p.stato === 'in_attesa').length,
      nMora: pags.filter((p) => p.stato === 'in_mora').length,
    };
  }, [data]);

  // Filtro e raggruppamento per anno
  const filtered = useMemo(() => {
    if (!data) return [];
    return filterStato === 'tutti' ? data.pagamenti : data.pagamenti.filter((p) => p.stato === filterStato);
  }, [data, filterStato]);

  const byYear = useMemo(() => {
    const map = new Map<number, MioPagamento[]>();
    for (const p of filtered) {
      const y = p.quota.anno;
      if (!map.has(y)) map.set(y, []);
      map.get(y)!.push(p);
    }
    return Array.from(map.entries()).sort((a, b) => b[0] - a[0]);
  }, [filtered]);

  const handlePrint = (p: MioPagamento) => {
    if (!data) return;
    const win = window.open('', '_blank');
    if (!win) { alert('Abilita i popup per stampare la ricevuta'); return; }
    win.document.write(buildReceiptHtml(p, data));
    win.document.close();
  };

  if (loading) {
    return (
      <div className="flex flex-col flex-1 items-center justify-center">
        <p className="text-sm" style={{ color: '#aaa' }}>Caricamento quote...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col flex-1 items-center justify-center gap-3">
        <AlertTriangle size={32} style={{ color: '#dc2626' }} />
        <p className="text-sm font-medium" style={{ color: '#dc2626' }}>{error}</p>
        <button onClick={load} className="px-4 py-2 rounded-lg text-sm font-semibold" style={{ backgroundColor: '#fef2f2', color: '#dc2626' }}>Riprova</button>
      </div>
    );
  }

  if (!data) return null;

  const tipoCfg = TIPO_CONFIG[data.condomino.tipo] ?? { label: data.condomino.tipo, bg: '#f5f5f5', color: '#555' };

  return (
    <div className="flex flex-col flex-1 overflow-hidden" style={{ backgroundColor: 'var(--background)' }}>
      {/* ── Header ─────────────────────────────────────────────────────── */}
      <header className="flex-shrink-0 px-6 py-4 border-b" style={{ backgroundColor: '#fff', borderColor: '#f0f0f0' }}>
        <div className="flex items-start justify-between" style={{ maxWidth: 900 }}>
          <div>
            <h1 className="text-base font-bold" style={{ color: '#1a1a1a' }}>Le mie Quote</h1>
            <p className="text-xs mt-0.5" style={{ color: '#888' }}>Storico pagamenti e stato delle rate condominiali</p>
          </div>
        </div>

        {/* Card info condomino */}
        <div className="flex items-center gap-6 mt-4 p-4 rounded-xl" style={{ backgroundColor: '#fafafa', border: '1px solid #f0f0f0', maxWidth: 900 }}>
          <div className="w-12 h-12 flex-shrink-0 rounded-full flex items-center justify-center text-sm font-bold" style={{ backgroundColor: '#fef2f2', color: 'var(--primary)' }}>
            {data.condomino.nome[0]}{data.condomino.cognome[0]}
          </div>
          <div className="flex-1">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-sm font-bold" style={{ color: '#1a1a1a' }}>{data.condomino.nome} {data.condomino.cognome}</span>
              <span className="text-xs px-2 py-0.5 rounded-full font-semibold" style={{ backgroundColor: tipoCfg.bg, color: tipoCfg.color }}>{tipoCfg.label}</span>
            </div>
            <div className="flex items-center gap-4 mt-1 flex-wrap">
              <span className="flex items-center gap-1 text-xs" style={{ color: '#666' }}><Home size={11} /> Unità {data.condomino.unita}</span>
              <span className="flex items-center gap-1 text-xs" style={{ color: '#666' }}><BarChart2 size={11} /> {data.condomino.millesimi} millesimi</span>
              <span className="flex items-center gap-1 text-xs" style={{ color: '#666' }}><Building2 size={11} /> {data.condominio.nome}</span>
            </div>
          </div>
        </div>
      </header>

      <div className="flex-1 overflow-y-auto p-6">
        <div style={{ maxWidth: 900, margin: '0 auto' }}>
          {/* ── Stat cards ─────────────────────────────────────────────── */}
          {stats && (
            <div className="grid grid-cols-3 gap-3 mb-6">
              {[
                { label: 'Pagato', value: stats.pagato, count: stats.nPagato, color: '#16a34a', bg: '#f0fdf4', icon: <CheckCircle size={16} /> },
                { label: 'In attesa', value: stats.attesa, count: stats.nAttesa, color: '#d97706', bg: '#fffbeb', icon: <Clock size={16} /> },
                { label: 'In mora', value: stats.mora, count: stats.nMora, color: '#dc2626', bg: '#fef2f2', icon: <AlertTriangle size={16} /> },
              ].map((s) => (
                <div key={s.label} className="rounded-xl p-4 flex items-center gap-3" style={{ backgroundColor: s.bg, border: '1px solid', borderColor: s.bg }}>
                  <div style={{ color: s.color }}>{s.icon}</div>
                  <div>
                    <p className="text-xs mb-0.5" style={{ color: '#888' }}>{s.label} ({s.count})</p>
                    <p className="text-base font-bold" style={{ color: s.color }}>€ {fmt(s.value)}</p>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Barra incasso */}
          {stats && stats.totale > 0 && (
            <div className="rounded-xl p-4 mb-6" style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0' }}>
              <div className="flex justify-between mb-2">
                <span className="text-xs font-semibold" style={{ color: '#555' }}>Tasso pagamento</span>
                <span className="text-xs font-bold" style={{ color: '#16a34a' }}>
                  {Math.round((stats.pagato / stats.totale) * 100)}%
                </span>
              </div>
              <div className="w-full rounded-full h-2.5" style={{ backgroundColor: '#f0f0f0' }}>
                <div
                  className="h-2.5 rounded-full"
                  style={{ width: `${Math.round((stats.pagato / stats.totale) * 100)}%`, backgroundColor: '#16a34a' }}
                />
              </div>
              <div className="flex justify-between mt-2">
                <span className="text-xs" style={{ color: '#aaa' }}>Totale emesso: <strong style={{ color: '#1a1a1a' }}>€ {fmt(stats.totale)}</strong></span>
                <span className="text-xs" style={{ color: '#aaa' }}>{stats.nPagato} di {data.pagamenti.length} rate pagate</span>
              </div>
            </div>
          )}

          {/* ── Filtri ─────────────────────────────────────────────────── */}
          <div className="flex items-center gap-2 mb-4">
            {['tutti', 'pagato', 'in_attesa', 'in_mora'].map((s) => {
              const count = s === 'tutti' ? data.pagamenti.length : data.pagamenti.filter((p) => p.stato === s).length;
              return (
                <button
                  key={s}
                  onClick={() => setFilterStato(s)}
                  className="px-3 py-1.5 rounded-lg text-xs font-semibold transition"
                  style={{
                    backgroundColor: filterStato === s ? 'var(--primary)' : '#f0f0f0',
                    color: filterStato === s ? '#fff' : '#666',
                  }}
                >
                  {s === 'tutti' ? 'Tutte' : STATO_CONFIG[s]?.label} ({count})
                </button>
              );
            })}
          </div>

          {/* ── Lista pagamenti per anno ────────────────────────────────── */}
          {data.pagamenti.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-20">
              <CreditCard size={40} style={{ color: '#ddd', marginBottom: 12 }} />
              <p className="text-sm font-medium" style={{ color: '#aaa' }}>Nessuna quota assegnata</p>
              <p className="text-xs mt-1" style={{ color: '#ccc' }}>L'amministratore non ha ancora generato le rate</p>
            </div>
          ) : filtered.length === 0 ? (
            <p className="text-sm text-center py-12" style={{ color: '#aaa' }}>Nessuna quota con questo filtro</p>
          ) : (
            <div className="flex flex-col gap-6">
              {byYear.map(([anno, pags]) => (
                <div key={anno}>
                  <div className="flex items-center gap-2 mb-3">
                    <span className="text-xs font-bold px-2 py-0.5 rounded" style={{ backgroundColor: '#f0f0f0', color: '#555' }}>{anno}</span>
                    <span className="text-xs" style={{ color: '#aaa' }}>{pags.length} rate</span>
                  </div>
                  <div className="flex flex-col gap-2">
                    {pags.map((p) => {
                      const cfg = STATO_CONFIG[p.stato] ?? { label: p.stato, bg: '#f5f5f5', color: '#888', icon: null };
                      return (
                        <div
                          key={p.id}
                          className="flex items-center gap-4 rounded-xl px-5 py-4"
                          style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0' }}
                        >
                          {/* Mese */}
                          <div className="flex-shrink-0 w-14 text-center">
                            <p className="text-base font-bold" style={{ color: 'var(--primary)' }}>{MESI_SHORT[p.quota.mese - 1]}</p>
                            <p className="text-xs" style={{ color: '#aaa' }}>{p.quota.anno}</p>
                          </div>

                          {/* Divider */}
                          <div style={{ width: 1, height: 36, backgroundColor: '#f0f0f0', flexShrink: 0 }} />

                          {/* Info */}
                          <div className="flex-1">
                            <div className="flex items-center gap-2 flex-wrap">
                              <span className="text-sm font-bold" style={{ color: '#1a1a1a' }}>€ {fmt(p.importo)}</span>
                              <span className="flex items-center gap-1 text-xs px-2 py-0.5 rounded-full font-semibold" style={{ backgroundColor: cfg.bg, color: cfg.color }}>
                                {cfg.icon} {cfg.label}
                              </span>
                            </div>
                            {p.stato === 'pagato' && (
                              <div className="flex items-center gap-3 mt-0.5">
                                <span className="text-xs" style={{ color: '#aaa' }}>Pagato il {fmtDate(p.dataPagamento)}</span>
                                {p.metodoPagamento && <span className="text-xs" style={{ color: '#bbb' }}>· {p.metodoPagamento}</span>}
                              </div>
                            )}
                            {p.note && <p className="text-xs mt-0.5 truncate" style={{ color: '#bbb', maxWidth: 320 }}>"{p.note}"</p>}
                          </div>

                          {/* Azione */}
                          {p.stato === 'pagato' && (
                            <button
                              onClick={() => setPreviewPag(p)}
                              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold flex-shrink-0"
                              style={{ backgroundColor: '#f0fdf4', color: '#16a34a' }}
                            >
                              <Printer size={12} />
                              Ricevuta
                            </button>
                          )}

                          {/* Importo quota totale (piccolo) */}
                          <div className="flex-shrink-0 text-right" style={{ minWidth: 80 }}>
                            <p className="text-xs" style={{ color: '#ccc' }}>Quota cond.</p>
                            <p className="text-xs font-semibold" style={{ color: '#bbb' }}>€ {fmt(p.quota.importoTotale)}</p>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* ── Modal anteprima ricevuta ────────────────────────────────────── */}
      {previewPag && data && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center"
          style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}
          onClick={() => setPreviewPag(null)}
        >
          <div
            className="w-full rounded-2xl shadow-2xl p-6"
            style={{ maxWidth: 520, backgroundColor: '#fff', maxHeight: '90vh', overflowY: 'auto' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between mb-5">
              <div>
                <h2 className="text-sm font-bold" style={{ color: '#1a1a1a' }}>Anteprima Ricevuta</h2>
                <p className="text-xs" style={{ color: '#aaa' }}>#RIC-{String(previewPag.id).padStart(5, '0')}</p>
              </div>
              <button onClick={() => setPreviewPag(null)} style={{ color: '#aaa' }}><X size={18} /></button>
            </div>

            {/* Contenuto ricevuta */}
            <div className="rounded-xl p-5 mb-4" style={{ backgroundColor: '#fafafa', border: '1px solid #f0f0f0' }}>
              <div className="flex justify-between mb-3">
                <span className="text-base font-bold" style={{ color: 'var(--primary)' }}>Condo<span style={{ color: '#1a1a1a' }}>Facile</span></span>
                <span className="text-xs" style={{ color: '#aaa' }}>{new Date().toLocaleDateString('it-IT')}</span>
              </div>
              <p className="text-xs mb-4" style={{ color: '#888' }}>{data.condominio.nome} · {data.condominio.indirizzo}</p>

              {[
                { label: 'Condòmino', value: `${data.condomino.nome} ${data.condomino.cognome}` },
                { label: 'Unità', value: `Unità ${data.condomino.unita}` },
                { label: 'Competenza', value: `${MESI[previewPag.quota.mese - 1]} ${previewPag.quota.anno}` },
                { label: 'Data pagamento', value: fmtDate(previewPag.dataPagamento) },
                ...(previewPag.metodoPagamento ? [{ label: 'Metodo', value: previewPag.metodoPagamento }] : []),
              ].map((r) => (
                <div key={r.label} className="flex justify-between py-1.5" style={{ borderBottom: '1px solid #f0f0f0' }}>
                  <span className="text-xs" style={{ color: '#888' }}>{r.label}</span>
                  <span className="text-xs font-semibold" style={{ color: '#1a1a1a' }}>{r.value}</span>
                </div>
              ))}

              <div className="text-center mt-4 py-3 rounded-xl" style={{ backgroundColor: '#f0fdf4' }}>
                <p className="text-xs mb-1" style={{ color: '#16a34a' }}>IMPORTO PAGATO</p>
                <p className="text-2xl font-bold" style={{ color: '#16a34a' }}>€ {fmt(previewPag.importo)}</p>
                <span className="inline-flex items-center gap-1 mt-1 text-xs font-semibold px-3 py-1 rounded-full" style={{ backgroundColor: '#dcfce7', color: '#16a34a' }}>
                  <CheckCircle size={11} /> Pagato
                </span>
              </div>
            </div>

            <div className="flex gap-2">
              <button
                onClick={() => setPreviewPag(null)}
                className="flex-1 py-2 rounded-lg text-sm font-semibold"
                style={{ backgroundColor: '#f5f5f5', color: '#555' }}
              >
                Chiudi
              </button>
              <button
                onClick={() => handlePrint(previewPag)}
                className="flex-1 flex items-center justify-center gap-2 py-2 rounded-lg text-sm font-semibold text-white"
                style={{ backgroundColor: '#16a34a' }}
              >
                <Printer size={14} />
                Stampa / Salva PDF
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
