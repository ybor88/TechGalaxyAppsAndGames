'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import {
  FileText, Upload, X, Search, Trash2, Download, Edit2,
  Building2, RefreshCw, Plus, Eye, EyeOff, Lock, Tag,
  FileArchive, Scale, Receipt, Wrench, Shield, Map,
  ScrollText,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import {
  fetchCondominii, CondominioListItem,
  fetchDocumenti, fetchMieiDocumenti,
  uploadDocumento, uploadNuovaVersione, updateDocumento, deleteDocumento, downloadDocumento,
  DocumentoItem,
} from '@/lib/api';

// ─── Costanti ─────────────────────────────────────────────────────────────────

const CATEGORIE = [
  'regolamento', 'verbali', 'fatture_pagamenti', 'contratti',
  'certificazioni', 'polizze', 'planimetrie',
];

const CAT_CFG: Record<string, { label: string; icon: React.ReactNode; color: string; bg: string }> = {
  regolamento:       { label: 'Regolamento',       icon: <ScrollText size={14} />,  color: '#7c3aed', bg: '#faf5ff' },
  verbali:           { label: 'Verbali',            icon: <FileText size={14} />,    color: '#2563eb', bg: '#eff6ff' },
  fatture_pagamenti: { label: 'Fatture/Pagamenti',  icon: <Receipt size={14} />,     color: '#d97706', bg: '#fffbeb' },
  contratti:         { label: 'Contratti',          icon: <Scale size={14} />,       color: '#059669', bg: '#ecfdf5' },
  certificazioni:    { label: 'Certificazioni',     icon: <Shield size={14} />,      color: '#dc2626', bg: '#fef2f2' },
  polizze:           { label: 'Polizze',            icon: <FileArchive size={14} />, color: '#0891b2', bg: '#ecfeff' },
  planimetrie:       { label: 'Planimetrie',        icon: <Map size={14} />,         color: '#65a30d', bg: '#f7fee7' },
};

const VIS_CFG: Record<string, { label: string; icon: React.ReactNode; color: string }> = {
  pubblica:   { label: 'Pubblica',    icon: <Eye size={12} />,    color: '#16a34a' },
  privata:    { label: 'Privata',     icon: <Lock size={12} />,   color: '#dc2626' },
  selettiva:  { label: 'Selettiva',   icon: <EyeOff size={12} />, color: '#d97706' },
};

function fmtSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function fmtDate(s: string) {
  return new Date(s).toLocaleDateString('it-IT', { day: '2-digit', month: 'short', year: 'numeric' });
}

// ─── Componenti riutilizzabili ────────────────────────────────────────────────

function Modal({
  title, onClose, children, wide,
}: { title: string; onClose: () => void; children: React.ReactNode; wide?: boolean }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center"
      style={{ backgroundColor: 'rgba(0,0,0,0.45)' }}
      onClick={onClose}>
      <div className="w-full rounded-2xl p-6 shadow-2xl"
        style={{ maxWidth: wide ? 600 : 480, backgroundColor: '#fff', maxHeight: '90vh', overflowY: 'auto' }}
        onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between mb-5">
          <h2 className="text-sm font-bold" style={{ color: '#1a1a1a' }}>{title}</h2>
          <button onClick={onClose} style={{ color: '#aaa' }}><X size={18} /></button>
        </div>
        {children}
      </div>
    </div>
  );
}

function CategoriaBadge({ cat }: { cat: string }) {
  const cfg = CAT_CFG[cat] ?? { label: cat, icon: <FileText size={14} />, color: '#888', bg: '#f5f5f5' };
  return (
    <span className="flex items-center gap-1 text-xs px-2 py-0.5 rounded-full font-semibold"
      style={{ backgroundColor: cfg.bg, color: cfg.color }}>
      {cfg.icon} {cfg.label}
    </span>
  );
}

function VisBadge({ vis }: { vis: string }) {
  const cfg = VIS_CFG[vis] ?? { label: vis, icon: null, color: '#888' };
  return (
    <span className="flex items-center gap-1 text-xs font-semibold" style={{ color: cfg.color }}>
      {cfg.icon} {cfg.label}
    </span>
  );
}

// ─── Modale upload / modifica ─────────────────────────────────────────────────

function UploadModal({
  condominioId, token, existing, onClose, onSaved,
}: {
  condominioId: number;
  token: string;
  existing?: DocumentoItem;
  onClose: () => void;
  onSaved: (d: DocumentoItem) => void;
}) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [nome, setNome] = useState(existing?.nome ?? '');
  const [categoria, setCategoria] = useState(existing?.categoria ?? 'regolamento');
  const [tag, setTag] = useState(existing?.tag ?? '');
  const [visibilita, setVisibilita] = useState(existing?.visibilita ?? 'pubblica');
  const [unitaAccesso, setUnitaAccesso] = useState(existing?.unitaAccesso ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (!f) return;
    setFile(f);
    if (!nome) setNome(f.name.replace(/\.[^.]+$/, ''));
  };

  const handleSave = async () => {
    if (!nome.trim()) { setError('Nome obbligatorio'); return; }
    if (!existing && !file) { setError('Seleziona un file'); return; }
    setSaving(true);
    setError('');
    try {
      let saved: DocumentoItem;
      if (existing) {
        // Aggiorna metadati
        saved = await updateDocumento(token, existing.id, { nome, categoria, tag, visibilita, unitaAccesso });
        // Se è stato selezionato un nuovo file, carica nuova versione
        if (file) {
          saved = await uploadNuovaVersione(token, existing.id, file);
          saved = await updateDocumento(token, existing.id, { nome, categoria, tag, visibilita, unitaAccesso });
        }
      } else {
        saved = await uploadDocumento(token, file!, { nome, categoria, tag, visibilita, unitaAccesso, condominioId });
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
    <Modal title={existing ? 'Modifica documento' : 'Carica documento'} onClose={onClose} wide>
      <div className="space-y-4">
        {/* File drop zone */}
        <div
          onClick={() => fileRef.current?.click()}
          className="border-2 border-dashed rounded-xl p-6 text-center cursor-pointer transition-colors"
          style={{ borderColor: file ? 'var(--primary)' : '#e5e7eb', backgroundColor: file ? '#eff6ff' : '#fafafa' }}>
          <Upload size={24} style={{ color: file ? 'var(--primary)' : '#ccc', margin: '0 auto 8px' }} />
          {file ? (
            <p className="text-sm font-semibold" style={{ color: 'var(--primary)' }}>{file.name} ({fmtSize(file.size)})</p>
          ) : (
            <>
              <p className="text-sm" style={{ color: '#aaa' }}>
                {existing ? 'Trascina o clicca per caricare una nuova versione' : 'Trascina o clicca per selezionare il file'}
              </p>
              <p className="text-xs mt-1" style={{ color: '#ccc' }}>Max 20 MB · PDF, immagini, Word, Excel…</p>
            </>
          )}
          <input ref={fileRef} type="file" className="hidden" onChange={handleFileChange}
            accept=".pdf,.doc,.docx,.xls,.xlsx,.png,.jpg,.jpeg,.zip" />
        </div>

        <div>
          <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Nome *</label>
          <input value={nome} onChange={(e) => setNome(e.target.value)}
            placeholder="Nome del documento"
            className="w-full px-3 py-2 rounded-lg text-sm outline-none"
            style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }} />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Categoria</label>
            <select value={categoria} onChange={(e) => setCategoria(e.target.value)}
              className="w-full px-3 py-2 rounded-lg text-sm outline-none"
              style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}>
              {CATEGORIE.map((c) => <option key={c} value={c}>{CAT_CFG[c]?.label ?? c}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>Visibilità</label>
            <select value={visibilita} onChange={(e) => setVisibilita(e.target.value)}
              className="w-full px-3 py-2 rounded-lg text-sm outline-none"
              style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}>
              <option value="pubblica">Pubblica (tutti)</option>
              <option value="privata">Privata (solo admin)</option>
              <option value="selettiva">Selettiva (per unità)</option>
            </select>
          </div>
        </div>

        {visibilita === 'selettiva' && (
          <div>
            <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>
              Unità con accesso <span style={{ color: '#aaa', fontWeight: 400 }}>(separate da virgola, es. A1, A2, B3)</span>
            </label>
            <input value={unitaAccesso} onChange={(e) => setUnitaAccesso(e.target.value)}
              placeholder="A1, A2, B3"
              className="w-full px-3 py-2 rounded-lg text-sm outline-none"
              style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }} />
          </div>
        )}

        <div>
          <label className="block text-xs font-semibold mb-1" style={{ color: '#555' }}>
            Tag <span style={{ color: '#aaa', fontWeight: 400 }}>(opzionale, separati da virgola)</span>
          </label>
          <input value={tag} onChange={(e) => setTag(e.target.value)}
            placeholder="es. 2026, assemblea, urgente"
            className="w-full px-3 py-2 rounded-lg text-sm outline-none"
            style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }} />
        </div>

        {error && <p className="text-xs" style={{ color: '#dc2626' }}>{error}</p>}
        <div className="flex justify-end gap-2 pt-2">
          <button onClick={onClose} className="px-4 py-2 rounded-lg text-sm"
            style={{ border: '1px solid #e5e7eb', color: '#666' }}>
            Annulla
          </button>
          <button onClick={handleSave} disabled={saving}
            className="px-4 py-2 rounded-lg text-sm font-semibold text-white"
            style={{ backgroundColor: 'var(--primary)', opacity: saving ? 0.7 : 1 }}>
            {saving ? 'Caricamento...' : existing ? 'Salva' : 'Carica'}
          </button>
        </div>
      </div>
    </Modal>
  );
}

// ─── Icona tipo file ──────────────────────────────────────────────────────────

function FileIcon({ mimeType }: { mimeType: string }) {
  const color =
    mimeType.includes('pdf') ? '#dc2626' :
    mimeType.includes('word') || mimeType.includes('msword') ? '#2563eb' :
    mimeType.includes('excel') || mimeType.includes('spreadsheet') ? '#16a34a' :
    mimeType.includes('image') ? '#7c3aed' :
    mimeType.includes('zip') ? '#d97706' : '#888';
  return <FileText size={28} style={{ color, flexShrink: 0 }} />;
}

// ─── Card documento ────────────────────────────────────────────────────────────

function DocCard({
  doc, token, isAdmin,
  onDeleted, onUpdated,
}: {
  doc: DocumentoItem;
  token: string;
  isAdmin: boolean;
  onDeleted: (id: number) => void;
  onUpdated: (d: DocumentoItem) => void;
}) {
  const [downloading, setDownloading] = useState(false);
  const [editing, setEditing] = useState(false);

  const handleDownload = async () => {
    setDownloading(true);
    try { await downloadDocumento(token, doc.id, doc.nome); }
    catch (e) { alert((e as Error).message); }
    finally { setDownloading(false); }
  };

  const handleDelete = async () => {
    if (!confirm(`Eliminare "${doc.nome}"?`)) return;
    await deleteDocumento(token, doc.id);
    onDeleted(doc.id);
  };

  return (
    <>
      <div className="flex items-start gap-3 p-4 rounded-2xl transition-shadow hover:shadow-sm"
        style={{ backgroundColor: '#fff', border: '1px solid #f0f0f0' }}>
        <FileIcon mimeType={doc.mimeType} />
        <div className="flex-1 min-w-0">
          <div className="flex items-start justify-between gap-2">
            <div className="flex-1 min-w-0">
              <p className="text-sm font-semibold truncate" style={{ color: '#1a1a1a' }}>{doc.nome}</p>
              <div className="flex items-center flex-wrap gap-2 mt-1.5">
                <CategoriaBadge cat={doc.categoria} />
                {isAdmin && <VisBadge vis={doc.visibilita} />}
                {doc.versione > 1 && (
                  <span className="text-xs px-1.5 py-0.5 rounded" style={{ backgroundColor: '#f0f0f0', color: '#888' }}>
                    v{doc.versione}
                  </span>
                )}
              </div>
              {doc.tag && (
                <div className="flex items-center gap-1 mt-1.5">
                  <Tag size={10} style={{ color: '#ccc' }} />
                  <span className="text-xs" style={{ color: '#aaa' }}>{doc.tag}</span>
                </div>
              )}
            </div>
            <div className="flex items-center gap-1 flex-shrink-0">
              {isAdmin && (
                <button onClick={() => setEditing(true)} className="p-1.5 rounded-lg"
                  style={{ border: '1px solid #e5e7eb', color: '#888' }}>
                  <Edit2 size={12} />
                </button>
              )}
              <button onClick={handleDownload} disabled={downloading}
                className="p-1.5 rounded-lg"
                style={{ backgroundColor: '#eff6ff', color: '#2563eb', opacity: downloading ? 0.7 : 1 }}>
                <Download size={12} />
              </button>
              {isAdmin && (
                <button onClick={handleDelete} className="p-1.5 rounded-lg"
                  style={{ backgroundColor: '#fef2f2', color: '#dc2626' }}>
                  <Trash2 size={12} />
                </button>
              )}
            </div>
          </div>
          <p className="text-xs mt-1.5" style={{ color: '#ccc' }}>
            {fmtSize(doc.fileSize)} · {fmtDate(doc.updatedAt)}
          </p>
        </div>
      </div>

      {editing && (
        <UploadModal
          condominioId={doc.condominioId}
          token={token}
          existing={doc}
          onClose={() => setEditing(false)}
          onSaved={(saved) => { onUpdated(saved); setEditing(false); }}
        />
      )}
    </>
  );
}

// ─── Vista Amministratore ─────────────────────────────────────────────────────

function AdminView({ token }: { token: string }) {
  const [condominii, setCondominii] = useState<CondominioListItem[]>([]);
  const [selectedCondo, setSelectedCondo] = useState<CondominioListItem | null>(null);
  const [documenti, setDocumenti] = useState<DocumentoItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [fCat, setFCat] = useState('tutti');
  const [showUpload, setShowUpload] = useState(false);

  useEffect(() => {
    fetchCondominii(token).then((data) => {
      setCondominii(data);
      if (data.length === 1) selectCondo(data[0]);
    });
  }, [token]); // eslint-disable-line react-hooks/exhaustive-deps

  const load = useCallback(async (condoId: number) => {
    setLoading(true);
    try {
      const data = await fetchDocumenti(token, condoId, {
        categoria: fCat !== 'tutti' ? fCat : undefined,
        search: search || undefined,
      });
      setDocumenti(data);
    } finally {
      setLoading(false);
    }
  }, [token, fCat, search]);

  const selectCondo = useCallback((c: CondominioListItem) => {
    setSelectedCondo(c);
    load(c.id);
  }, [load]);

  useEffect(() => {
    if (selectedCondo) load(selectedCondo.id);
  }, [fCat]); // eslint-disable-line react-hooks/exhaustive-deps

  // Ricerca con debounce leggero
  useEffect(() => {
    if (!selectedCondo) return;
    const t = setTimeout(() => load(selectedCondo.id), 300);
    return () => clearTimeout(t);
  }, [search]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleSaved = (saved: DocumentoItem) => {
    setDocumenti((prev) => {
      const idx = prev.findIndex((d) => d.id === saved.id);
      if (idx >= 0) { const n = [...prev]; n[idx] = saved; return n; }
      return [saved, ...prev];
    });
  };

  // Raggruppa per categoria
  const byCategory = CATEGORIE.reduce<Record<string, DocumentoItem[]>>((acc, cat) => {
    const docs = documenti.filter((d) => d.categoria === cat);
    if (docs.length > 0) acc[cat] = docs;
    return acc;
  }, {});

  return (
    <div className="flex flex-col flex-1 overflow-hidden">
      {/* Header */}
      <header className="flex items-center justify-between px-6 py-3 border-b flex-shrink-0"
        style={{ backgroundColor: '#fff', borderColor: '#f0f0f0' }}>
        <div>
          <h1 className="text-sm font-bold" style={{ color: '#1a1a1a' }}>Archivio Documentale</h1>
          <p className="text-xs" style={{ color: '#888' }}>Documenti e archivi del condominio</p>
        </div>
        <div className="flex items-center gap-2">
          <select value={selectedCondo?.id ?? ''}
            onChange={(e) => { const c = condominii.find((x) => x.id === Number(e.target.value)); if (c) selectCondo(c); }}
            className="px-3 py-2 rounded-lg text-xs outline-none"
            style={{ border: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}>
            <option value="">Seleziona condominio…</option>
            {condominii.map((c) => <option key={c.id} value={c.id}>{c.nome}</option>)}
          </select>
          {selectedCondo && (
            <button onClick={() => setShowUpload(true)}
              className="flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-semibold text-white"
              style={{ backgroundColor: 'var(--primary)' }}>
              <Upload size={14} /> Carica
            </button>
          )}
        </div>
      </header>

      {/* Filtri */}
      {selectedCondo && (
        <div className="flex items-center gap-2 px-6 py-2.5 flex-shrink-0 flex-wrap"
          style={{ backgroundColor: '#fafafa', borderBottom: '1px solid #f0f0f0' }}>
          <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg"
            style={{ backgroundColor: '#fff', border: '1px solid #e5e7eb', flex: '1 1 200px' }}>
            <Search size={13} style={{ color: '#aaa' }} />
            <input value={search} onChange={(e) => setSearch(e.target.value)}
              placeholder="Cerca per nome o tag…"
              className="text-xs outline-none w-full bg-transparent" />
          </div>
          <select value={fCat} onChange={(e) => setFCat(e.target.value)}
            className="px-2 py-1.5 rounded-lg text-xs outline-none"
            style={{ border: '1px solid #e5e7eb', backgroundColor: '#fff' }}>
            <option value="tutti">Tutte le categorie</option>
            {CATEGORIE.map((c) => <option key={c} value={c}>{CAT_CFG[c]?.label ?? c}</option>)}
          </select>
          <span className="text-xs" style={{ color: '#aaa' }}>{documenti.length} documenti</span>
        </div>
      )}

      {!selectedCondo ? (
        <div className="flex flex-1 items-center justify-center flex-col gap-2">
          <Building2 size={40} style={{ color: '#ddd' }} />
          <p className="text-sm" style={{ color: '#aaa' }}>Seleziona un condominio</p>
        </div>
      ) : (
        <div className="flex-1 overflow-y-auto px-6 py-5">
          {loading && <p className="text-xs text-center mt-8" style={{ color: '#aaa' }}>Caricamento...</p>}
          {!loading && documenti.length === 0 && (
            <div className="flex flex-col items-center justify-center py-24 gap-2">
              <FileText size={40} style={{ color: '#ddd' }} />
              <p className="text-sm" style={{ color: '#aaa' }}>Nessun documento caricato</p>
              <button onClick={() => setShowUpload(true)}
                className="flex items-center gap-1.5 mt-2 px-4 py-2 rounded-lg text-sm font-semibold text-white"
                style={{ backgroundColor: 'var(--primary)' }}>
                <Plus size={14} /> Carica il primo documento
              </button>
            </div>
          )}

          {/* Lista raggruppa per categoria */}
          {fCat !== 'tutti'
            ? (
              <div className="grid grid-cols-1 gap-3 max-w-3xl">
                {documenti.map((d) => (
                  <DocCard key={d.id} doc={d} token={token} isAdmin
                    onDeleted={(id) => setDocumenti((prev) => prev.filter((x) => x.id !== id))}
                    onUpdated={handleSaved}
                  />
                ))}
              </div>
            )
            : Object.entries(byCategory).map(([cat, docs]) => (
              <div key={cat} className="mb-6">
                <div className="flex items-center gap-2 mb-3">
                  <span style={{ color: CAT_CFG[cat]?.color ?? '#888' }}>{CAT_CFG[cat]?.icon}</span>
                  <h3 className="text-xs font-bold uppercase tracking-wide" style={{ color: '#555' }}>
                    {CAT_CFG[cat]?.label ?? cat}
                  </h3>
                  <span className="text-xs" style={{ color: '#ccc' }}>({docs.length})</span>
                </div>
                <div className="grid grid-cols-1 gap-3 max-w-3xl">
                  {docs.map((d) => (
                    <DocCard key={d.id} doc={d} token={token} isAdmin
                      onDeleted={(id) => setDocumenti((prev) => prev.filter((x) => x.id !== id))}
                      onUpdated={handleSaved}
                    />
                  ))}
                </div>
              </div>
            ))
          }
        </div>
      )}

      {showUpload && selectedCondo && (
        <UploadModal
          condominioId={selectedCondo.id}
          token={token}
          onClose={() => setShowUpload(false)}
          onSaved={(saved) => { handleSaved(saved); setShowUpload(false); }}
        />
      )}
    </div>
  );
}

// ─── Vista Condomino ──────────────────────────────────────────────────────────

function CondominoView({ token }: { token: string }) {
  const [documenti, setDocumenti] = useState<DocumentoItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [search, setSearch] = useState('');
  const [fCat, setFCat] = useState('tutti');

  const load = useCallback(async () => {
    try {
      const data = await fetchMieiDocumenti(token);
      setDocumenti(data);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [token]);

  useEffect(() => { load(); }, [load]);

  const displayed = documenti.filter((d) => {
    const matchCat = fCat === 'tutti' || d.categoria === fCat;
    const matchSearch = !search ||
      d.nome.toLowerCase().includes(search.toLowerCase()) ||
      d.tag.toLowerCase().includes(search.toLowerCase());
    return matchCat && matchSearch;
  });

  const byCategory = CATEGORIE.reduce<Record<string, DocumentoItem[]>>((acc, cat) => {
    const docs = displayed.filter((d) => d.categoria === cat);
    if (docs.length > 0) acc[cat] = docs;
    return acc;
  }, {});

  return (
    <div className="flex flex-col flex-1 overflow-hidden">
      {/* Header */}
      <header className="flex items-center justify-between px-6 py-3 border-b flex-shrink-0"
        style={{ backgroundColor: '#fff', borderColor: '#f0f0f0' }}>
        <div>
          <h1 className="text-sm font-bold" style={{ color: '#1a1a1a' }}>Documenti</h1>
          <p className="text-xs" style={{ color: '#888' }}>Documenti condominiali a tua disposizione</p>
        </div>
        <button onClick={() => { setRefreshing(true); load(); }} disabled={refreshing}
          className="p-2 rounded-lg" style={{ border: '1px solid #e5e7eb', color: '#666' }}>
          <RefreshCw size={14} className={refreshing ? 'animate-spin' : ''} />
        </button>
      </header>

      {/* Filtri */}
      <div className="flex items-center gap-2 px-6 py-2.5 flex-shrink-0 flex-wrap"
        style={{ backgroundColor: '#fafafa', borderBottom: '1px solid #f0f0f0' }}>
        <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg"
          style={{ backgroundColor: '#fff', border: '1px solid #e5e7eb', flex: '1 1 200px' }}>
          <Search size={13} style={{ color: '#aaa' }} />
          <input value={search} onChange={(e) => setSearch(e.target.value)}
            placeholder="Cerca documenti…"
            className="text-xs outline-none w-full bg-transparent" />
        </div>
        <select value={fCat} onChange={(e) => setFCat(e.target.value)}
          className="px-2 py-1.5 rounded-lg text-xs outline-none"
          style={{ border: '1px solid #e5e7eb', backgroundColor: '#fff' }}>
          <option value="tutti">Tutte le categorie</option>
          {CATEGORIE.map((c) => <option key={c} value={c}>{CAT_CFG[c]?.label ?? c}</option>)}
        </select>
        <span className="text-xs" style={{ color: '#aaa' }}>{displayed.length} documenti</span>
      </div>

      <div className="flex-1 overflow-y-auto px-6 py-5">
        {loading && <p className="text-xs text-center mt-8" style={{ color: '#aaa' }}>Caricamento...</p>}
        {!loading && displayed.length === 0 && (
          <div className="flex flex-col items-center justify-center py-24 gap-2">
            <FileText size={40} style={{ color: '#ddd' }} />
            <p className="text-sm" style={{ color: '#aaa' }}>Nessun documento disponibile</p>
          </div>
        )}

        {fCat !== 'tutti'
          ? (
            <div className="grid grid-cols-1 gap-3 max-w-3xl">
              {displayed.map((d) => (
                <DocCard key={d.id} doc={d} token={token} isAdmin={false}
                  onDeleted={() => {}} onUpdated={() => {}}
                />
              ))}
            </div>
          )
          : Object.entries(byCategory).map(([cat, docs]) => (
            <div key={cat} className="mb-6">
              <div className="flex items-center gap-2 mb-3">
                <span style={{ color: CAT_CFG[cat]?.color ?? '#888' }}>{CAT_CFG[cat]?.icon}</span>
                <h3 className="text-xs font-bold uppercase tracking-wide" style={{ color: '#555' }}>
                  {CAT_CFG[cat]?.label ?? cat}
                </h3>
                <span className="text-xs" style={{ color: '#ccc' }}>({docs.length})</span>
              </div>
              <div className="grid grid-cols-1 gap-3 max-w-3xl">
                {docs.map((d) => (
                  <DocCard key={d.id} doc={d} token={token} isAdmin={false}
                    onDeleted={() => {}} onUpdated={() => {}}
                  />
                ))}
              </div>
            </div>
          ))
        }
      </div>
    </div>
  );
}

// ─── Pagina principale ────────────────────────────────────────────────────────

export default function DocumentiPage() {
  const { token, user } = useAuth();
  if (!token || !user) return null;

  return user.role === 'AMMINISTRATORE'
    ? <AdminView token={token} />
    : <CondominoView token={token} />;
}
