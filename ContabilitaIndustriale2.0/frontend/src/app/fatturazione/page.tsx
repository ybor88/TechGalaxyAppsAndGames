"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import {
  anagraficheApi,
  documentiApi,
  Anagrafica,
  Documento,
  DocumentoCreate,
  DocumentoUpdate,
  RigaDocumentoCreate,
  TipoDocumento,
  StatoDocumento,
} from "@/lib/api";
import {
  ChevronLeft,
  Plus,
  Trash2,
  FileText,
  Download,
  Users,
  Edit2,
  X,
  Check,
} from "lucide-react";

// ── Helpers ──────────────────────────────────────────────────────────────────

const today = () => new Date().toISOString().split("T")[0];

const fmt = (n: number | string) =>
  new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(Number(n));

const fmtData = (s: string | null) =>
  s ? new Date(s).toLocaleDateString("it-IT") : "—";

const TIPO_LABEL: Record<TipoDocumento, string> = {
  preventivo: "Preventivi",
  ordine: "Ordini",
  fattura_attiva: "Fatture Attive",
  fattura_passiva: "Fatture Passive",
  nota_credito: "Note Credito",
};

const TIPO_BADGE: Record<string, string> = {
  preventivo: "bg-violet-100 text-violet-700",
  ordine: "bg-blue-100 text-blue-700",
  fattura_attiva: "bg-green-100 text-green-700",
  fattura_passiva: "bg-orange-100 text-orange-700",
  nota_credito: "bg-pink-100 text-pink-700",
};

const STATO_BADGE: Record<string, string> = {
  bozza: "bg-gray-100 text-gray-600",
  emesso: "bg-blue-100 text-blue-700",
  pagato: "bg-green-100 text-green-700",
  annullato: "bg-red-100 text-red-600",
};

const STATI: StatoDocumento[] = ["bozza", "emesso", "pagato", "annullato"];

// ── Riga form default ─────────────────────────────────────────────────────────

const RIGA_VUOTA: RigaDocumentoCreate = {
  descrizione: "",
  quantita: 1,
  prezzo_unitario: 0,
  iva_percentuale: 22,
};

// ── Tipi tab ──────────────────────────────────────────────────────────────────

type Tab = "tutti" | TipoDocumento | "anagrafiche";

// ── Componente principale ─────────────────────────────────────────────────────

export default function FatturazionePage() {
  const [tab, setTab] = useState<Tab>("tutti");
  const [documenti, setDocumenti] = useState<Documento[]>([]);
  const [anagrafiche, setAnagrafiche] = useState<Anagrafica[]>([]);
  const [loading, setLoading] = useState(true);
  const [errore, setErrore] = useState<string | null>(null);

  // Pannello creazione documento
  const [showForm, setShowForm] = useState(false);
  const [editDoc, setEditDoc] = useState<Documento | null>(null);
  const [salvataggio, setSalvataggio] = useState(false);

  // Form documento
  const [fTipo, setFTipo] = useState<TipoDocumento>("fattura_attiva");
  const [fData, setFData] = useState(today());
  const [fScadenza, setFScadenza] = useState("");
  const [fAnagraficaId, setFAnagraficaId] = useState("");
  const [fOggetto, setFOggetto] = useState("");
  const [fNote, setFNote] = useState("");
  const [fRighe, setFRighe] = useState<RigaDocumentoCreate[]>([{ ...RIGA_VUOTA }]);

  // Pannello anagrafica
  const [showAnaForm, setShowAnaForm] = useState(false);
  const [editAna, setEditAna] = useState<Anagrafica | null>(null);
  const [aNome, setANome] = useState("");
  const [aTipo, setATipo] = useState<"cliente" | "fornitore" | "entrambi">("cliente");
  const [aPiva, setAPiva] = useState("");
  const [aCf, setACf] = useState("");
  const [aIndirizzo, setAIndirizzo] = useState("");
  const [aCap, setACap] = useState("");
  const [aCitta, setACitta] = useState("");
  const [aProvincia, setAProvincia] = useState("");
  const [aEmail, setAEmail] = useState("");
  const [aTelefono, setATelefono] = useState("");

  // ── Load ───────────────────────────────────────────────────────────────────

  const carica = async () => {
    setLoading(true);
    setErrore(null);
    try {
      const [resD, resA] = await Promise.all([
        documentiApi.list(),
        anagraficheApi.list(),
      ]);
      setDocumenti(resD.data);
      setAnagrafiche(resA.data);
    } catch {
      setErrore("Impossibile caricare i dati.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { carica(); }, []);

  // ── Filtra documenti per tab ───────────────────────────────────────────────

  const documentiVisibili =
    tab === "tutti" || tab === "anagrafiche"
      ? documenti
      : documenti.filter((d) => d.tipo === tab);

  // ── Calcola totale riga ────────────────────────────────────────────────────

  const rigaImporto = (r: RigaDocumentoCreate) =>
    (r.quantita * r.prezzo_unitario).toFixed(2);

  const totaleRighe = () =>
    fRighe.reduce((acc, r) => acc + r.quantita * r.prezzo_unitario, 0);

  const totaleIvaRighe = () =>
    fRighe.reduce(
      (acc, r) => acc + (r.quantita * r.prezzo_unitario * r.iva_percentuale) / 100,
      0
    );

  // ── Apri form documento ────────────────────────────────────────────────────

  const apriNuovoDoc = () => {
    setEditDoc(null);
    setFTipo("fattura_attiva");
    setFData(today());
    setFScadenza("");
    setFAnagraficaId("");
    setFOggetto("");
    setFNote("");
    setFRighe([{ ...RIGA_VUOTA }]);
    setShowForm(true);
  };

  const apriModificaDoc = (doc: Documento) => {
    setEditDoc(doc);
    setFTipo(doc.tipo as TipoDocumento);
    setFData(doc.data);
    setFScadenza(doc.data_scadenza ?? "");
    setFAnagraficaId(doc.anagrafica_id?.toString() ?? "");
    setFOggetto(doc.oggetto ?? "");
    setFNote(doc.note ?? "");
    setFRighe(
      doc.righe.map((r) => ({
        descrizione: r.descrizione,
        quantita: Number(r.quantita),
        prezzo_unitario: Number(r.prezzo_unitario),
        iva_percentuale: Number(r.iva_percentuale),
      }))
    );
    setShowForm(true);
  };

  // ── Salva documento ────────────────────────────────────────────────────────

  const salvaDocumento = async (e: React.FormEvent) => {
    e.preventDefault();
    if (fRighe.length === 0) {
      setErrore("Aggiungi almeno una riga al documento.");
      return;
    }
    setSalvataggio(true);
    setErrore(null);
    try {
      if (editDoc) {
        const payload: DocumentoUpdate = {
          data: fData,
          data_scadenza: fScadenza || undefined,
          anagrafica_id: fAnagraficaId ? parseInt(fAnagraficaId) : undefined,
          oggetto: fOggetto || undefined,
          note: fNote || undefined,
          righe: fRighe,
        };
        await documentiApi.update(editDoc.id, payload);
      } else {
        const payload: DocumentoCreate = {
          tipo: fTipo,
          data: fData,
          data_scadenza: fScadenza || undefined,
          anagrafica_id: fAnagraficaId ? parseInt(fAnagraficaId) : undefined,
          oggetto: fOggetto || undefined,
          note: fNote || undefined,
          righe: fRighe,
        };
        await documentiApi.create(payload);
      }
      setShowForm(false);
      await carica();
    } catch {
      setErrore("Errore durante il salvataggio.");
    } finally {
      setSalvataggio(false);
    }
  };

  // ── Elimina documento ──────────────────────────────────────────────────────

  const eliminaDoc = async (id: number) => {
    if (!confirm("Eliminare questo documento?")) return;
    try {
      await documentiApi.delete(id);
      setDocumenti((prev) => prev.filter((d) => d.id !== id));
    } catch {
      setErrore("Impossibile eliminare il documento.");
    }
  };

  // ── Cambia stato ───────────────────────────────────────────────────────────

  const cambiaStato = async (doc: Documento, stato: StatoDocumento) => {
    try {
      await documentiApi.update(doc.id, { stato });
      await carica();
    } catch {
      setErrore("Impossibile aggiornare lo stato.");
    }
  };

  // ── Righe form ─────────────────────────────────────────────────────────────

  const aggiungiRiga = () => setFRighe((prev) => [...prev, { ...RIGA_VUOTA }]);

  const rimuoviRiga = (i: number) =>
    setFRighe((prev) => prev.filter((_, idx) => idx !== i));

  const aggiornaRiga = (i: number, campo: keyof RigaDocumentoCreate, val: string) =>
    setFRighe((prev) =>
      prev.map((r, idx) =>
        idx === i
          ? { ...r, [campo]: campo === "descrizione" ? val : parseFloat(val) || 0 }
          : r
      )
    );

  // ── Anagrafica form ────────────────────────────────────────────────────────

  const apriNuovaAna = () => {
    setEditAna(null);
    setANome(""); setATipo("cliente"); setAPiva(""); setACf("");
    setAIndirizzo(""); setACap(""); setACitta(""); setAProvincia("");
    setAEmail(""); setATelefono("");
    setShowAnaForm(true);
  };

  const apriModificaAna = (a: Anagrafica) => {
    setEditAna(a);
    setANome(a.nome); setATipo(a.tipo); setAPiva(a.piva ?? "");
    setACf(a.cf ?? ""); setAIndirizzo(a.indirizzo ?? "");
    setACap(a.cap ?? ""); setACitta(a.citta ?? "");
    setAProvincia(a.provincia ?? ""); setAEmail(a.email ?? "");
    setATelefono(a.telefono ?? "");
    setShowAnaForm(true);
  };

  const salvaAnagrafica = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!aNome.trim()) return;
    setSalvataggio(true);
    setErrore(null);
    try {
      const payload = {
        nome: aNome, tipo: aTipo,
        piva: aPiva || undefined, cf: aCf || undefined,
        indirizzo: aIndirizzo || undefined, cap: aCap || undefined,
        citta: aCitta || undefined, provincia: aProvincia || undefined,
        email: aEmail || undefined, telefono: aTelefono || undefined,
      };
      if (editAna) {
        await anagraficheApi.update(editAna.id, payload);
      } else {
        await anagraficheApi.create({ ...payload, nome: aNome, tipo: aTipo });
      }
      setShowAnaForm(false);
      await carica();
    } catch {
      setErrore("Errore durante il salvataggio dell'anagrafica.");
    } finally {
      setSalvataggio(false);
    }
  };

  const eliminaAna = async (id: number) => {
    if (!confirm("Eliminare questa anagrafica?")) return;
    try {
      await anagraficheApi.delete(id);
      setAnagrafiche((prev) => prev.filter((a) => a.id !== id));
    } catch {
      setErrore("Impossibile eliminare l'anagrafica.");
    }
  };

  // ── Render ──────────────────────────────────────────────────────────────────

  const TABS: { key: Tab; label: string }[] = [
    { key: "tutti", label: "Tutti" },
    { key: "preventivo", label: "Preventivi" },
    { key: "ordine", label: "Ordini" },
    { key: "fattura_attiva", label: "Fatture Attive" },
    { key: "fattura_passiva", label: "Fatture Passive" },
    { key: "nota_credito", label: "Note Credito" },
    { key: "anagrafiche", label: "Clienti / Fornitori" },
  ];

  return (
    <div className="min-h-screen bg-[#f8fafc] flex flex-col">
      {/* ── Navbar ── */}
      <header className="bg-white border-b border-gray-100 shadow-sm">
        <div className="max-w-7xl mx-auto px-6 py-4 flex items-center gap-4">
          <Image src="/logo.jpeg" alt="logo" width={42} height={42} className="rounded-xl" />
          <div className="flex-1">
            <h1 className="text-lg font-bold leading-none">
              <span className="text-[#1e3a8a]">Contabilità</span>{" "}
              <span className="text-[#f97316]">2</span>
              <span className="text-[#16a34a]">.</span>
              <span className="text-[#f59e0b]">0</span>
            </h1>
            <p className="text-xs text-gray-400 mt-0.5">Fatturazione e Gestione Documentale</p>
          </div>
          <Link
            href="/"
            className="flex items-center gap-1 text-sm text-gray-500 hover:text-[#1e3a8a] transition-colors"
          >
            <ChevronLeft className="w-4 h-4" />
            Home
          </Link>
        </div>
      </header>

      <main className="flex-1 max-w-7xl mx-auto w-full px-6 py-8">
        {/* ── Errore ── */}
        {errore && (
          <div className="mb-4 px-4 py-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm flex items-center gap-2">
            <X className="w-4 h-4 flex-shrink-0" />
            {errore}
            <button onClick={() => setErrore(null)} className="ml-auto">
              <X className="w-4 h-4" />
            </button>
          </div>
        )}

        {/* ── Tabs + azioni ── */}
        <div className="flex flex-wrap items-center justify-between gap-4 mb-6">
          <div className="flex flex-wrap gap-1">
            {TABS.map((t) => (
              <button
                key={t.key}
                onClick={() => setTab(t.key)}
                className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                  tab === t.key
                    ? "bg-[#1e3a8a] text-white"
                    : "bg-white text-gray-600 hover:bg-gray-50 border border-gray-200"
                }`}
              >
                {t.label}
                {t.key !== "anagrafiche" && t.key !== "tutti" && (
                  <span className="ml-1.5 text-xs opacity-70">
                    ({documenti.filter((d) => d.tipo === t.key).length})
                  </span>
                )}
                {t.key === "tutti" && (
                  <span className="ml-1.5 text-xs opacity-70">({documenti.length})</span>
                )}
                {t.key === "anagrafiche" && (
                  <span className="ml-1.5 text-xs opacity-70">({anagrafiche.length})</span>
                )}
              </button>
            ))}
          </div>

          <div className="flex gap-2">
            {tab === "anagrafiche" ? (
              <button
                onClick={apriNuovaAna}
                className="flex items-center gap-2 bg-[#1e3a8a] hover:bg-[#1e40af] text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors"
              >
                <Plus className="w-4 h-4" />
                Nuova Anagrafica
              </button>
            ) : (
              <button
                onClick={apriNuovoDoc}
                className="flex items-center gap-2 bg-[#1e3a8a] hover:bg-[#1e40af] text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors"
              >
                <Plus className="w-4 h-4" />
                Nuovo Documento
              </button>
            )}
          </div>
        </div>

        {/* ── Contenuto tab ── */}
        {loading ? (
          <div className="text-center py-16 text-gray-400">Caricamento...</div>
        ) : tab === "anagrafiche" ? (
          /* ── Tabella anagrafiche ── */
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
            {anagrafiche.length === 0 ? (
              <div className="text-center py-16 text-gray-400">
                <Users className="w-10 h-10 mx-auto mb-3 opacity-30" />
                <p className="text-sm">Nessuna anagrafica. Crea clienti o fornitori.</p>
              </div>
            ) : (
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-gray-50 border-b border-gray-100 text-xs uppercase text-gray-500">
                    <th className="px-4 py-3 text-left">Nome</th>
                    <th className="px-4 py-3 text-left">Tipo</th>
                    <th className="px-4 py-3 text-left">P.IVA</th>
                    <th className="px-4 py-3 text-left">Città</th>
                    <th className="px-4 py-3 text-left">Email</th>
                    <th className="px-4 py-3 text-left">Telefono</th>
                    <th className="px-4 py-3 text-right">Azioni</th>
                  </tr>
                </thead>
                <tbody>
                  {anagrafiche.map((a) => (
                    <tr
                      key={a.id}
                      className="border-b border-gray-50 hover:bg-gray-50 transition-colors"
                    >
                      <td className="px-4 py-3 font-medium text-gray-900">{a.nome}</td>
                      <td className="px-4 py-3">
                        <span
                          className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                            a.tipo === "cliente"
                              ? "bg-blue-100 text-blue-700"
                              : a.tipo === "fornitore"
                              ? "bg-orange-100 text-orange-700"
                              : "bg-violet-100 text-violet-700"
                          }`}
                        >
                          {a.tipo}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600">{a.piva ?? "—"}</td>
                      <td className="px-4 py-3 text-gray-600">
                        {[a.citta, a.provincia ? `(${a.provincia})` : null]
                          .filter(Boolean)
                          .join(" ") || "—"}
                      </td>
                      <td className="px-4 py-3 text-gray-600">{a.email ?? "—"}</td>
                      <td className="px-4 py-3 text-gray-600">{a.telefono ?? "—"}</td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex justify-end gap-1">
                          <button
                            onClick={() => apriModificaAna(a)}
                            className="p-1.5 rounded-lg text-gray-400 hover:text-[#1e3a8a] hover:bg-blue-50 transition-colors"
                            title="Modifica"
                          >
                            <Edit2 className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => eliminaAna(a.id)}
                            className="p-1.5 rounded-lg text-gray-400 hover:text-red-600 hover:bg-red-50 transition-colors"
                            title="Elimina"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        ) : (
          /* ── Tabella documenti ── */
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
            {documentiVisibili.length === 0 ? (
              <div className="text-center py-16 text-gray-400">
                <FileText className="w-10 h-10 mx-auto mb-3 opacity-30" />
                <p className="text-sm">Nessun documento. Crea il primo!</p>
              </div>
            ) : (
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-gray-50 border-b border-gray-100 text-xs uppercase text-gray-500">
                    <th className="px-4 py-3 text-left">Numero</th>
                    <th className="px-4 py-3 text-left">Tipo</th>
                    <th className="px-4 py-3 text-left">Data</th>
                    <th className="px-4 py-3 text-left">Scadenza</th>
                    <th className="px-4 py-3 text-left">Cliente / Fornitore</th>
                    <th className="px-4 py-3 text-left">Oggetto</th>
                    <th className="px-4 py-3 text-right">Totale</th>
                    <th className="px-4 py-3 text-left">Stato</th>
                    <th className="px-4 py-3 text-right">Azioni</th>
                  </tr>
                </thead>
                <tbody>
                  {documentiVisibili.map((doc) => (
                    <tr
                      key={doc.id}
                      className="border-b border-gray-50 hover:bg-gray-50 transition-colors"
                    >
                      <td className="px-4 py-3 font-mono font-medium text-[#1e3a8a] text-xs">
                        {doc.numero}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                            TIPO_BADGE[doc.tipo] ?? "bg-gray-100 text-gray-600"
                          }`}
                        >
                          {TIPO_LABEL[doc.tipo as TipoDocumento] ?? doc.tipo}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600">{fmtData(doc.data)}</td>
                      <td className="px-4 py-3 text-gray-600">
                        {fmtData(doc.data_scadenza)}
                      </td>
                      <td className="px-4 py-3 text-gray-700 font-medium">
                        {doc.anagrafica?.nome ?? <span className="text-gray-400">—</span>}
                      </td>
                      <td className="px-4 py-3 text-gray-600 max-w-[200px] truncate">
                        {doc.oggetto ?? "—"}
                      </td>
                      <td className="px-4 py-3 text-right font-semibold text-gray-900">
                        {fmt(doc.totale)}
                      </td>
                      <td className="px-4 py-3">
                        <select
                          value={doc.stato}
                          onChange={(e) =>
                            cambiaStato(doc, e.target.value as StatoDocumento)
                          }
                          className={`px-2 py-0.5 rounded-full text-xs font-medium border-0 cursor-pointer ${
                            STATO_BADGE[doc.stato] ?? "bg-gray-100 text-gray-600"
                          }`}
                        >
                          {STATI.map((s) => (
                            <option key={s} value={s}>
                              {s}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex justify-end gap-1">
                          <a
                            href={documentiApi.pdfUrl(doc.id)}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="p-1.5 rounded-lg text-gray-400 hover:text-green-600 hover:bg-green-50 transition-colors"
                            title="Scarica PDF"
                          >
                            <Download className="w-4 h-4" />
                          </a>
                          <button
                            onClick={() => apriModificaDoc(doc)}
                            className="p-1.5 rounded-lg text-gray-400 hover:text-[#1e3a8a] hover:bg-blue-50 transition-colors"
                            title="Modifica"
                          >
                            <Edit2 className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => eliminaDoc(doc.id)}
                            className="p-1.5 rounded-lg text-gray-400 hover:text-red-600 hover:bg-red-50 transition-colors"
                            title="Elimina"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}
      </main>

      {/* ══ FORM DOCUMENTO (overlay) ══ */}
      {showForm && (
        <div className="fixed inset-0 z-50 flex">
          {/* Backdrop */}
          <div
            className="flex-1 bg-black/30 backdrop-blur-sm"
            onClick={() => setShowForm(false)}
          />
          {/* Panel */}
          <div className="w-full max-w-2xl bg-white shadow-2xl flex flex-col overflow-hidden">
            {/* Header panel */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 bg-[#1e3a8a]">
              <h2 className="text-white font-semibold">
                {editDoc ? `Modifica ${editDoc.numero}` : "Nuovo Documento"}
              </h2>
              <button
                onClick={() => setShowForm(false)}
                className="text-white/70 hover:text-white transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={salvaDocumento} className="flex-1 overflow-y-auto">
              <div className="px-6 py-5 space-y-5">
                {/* Tipo documento (solo creazione) */}
                {!editDoc && (
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 uppercase mb-1.5">
                      Tipo documento *
                    </label>
                    <select
                      value={fTipo}
                      onChange={(e) => setFTipo(e.target.value as TipoDocumento)}
                      className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/30"
                      required
                    >
                      {Object.entries(TIPO_LABEL).map(([v, l]) => (
                        <option key={v} value={v}>
                          {l}
                        </option>
                      ))}
                    </select>
                  </div>
                )}

                {/* Data e scadenza */}
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 uppercase mb-1.5">
                      Data *
                    </label>
                    <input
                      type="date"
                      value={fData}
                      onChange={(e) => setFData(e.target.value)}
                      required
                      className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/30"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 uppercase mb-1.5">
                      Scadenza
                    </label>
                    <input
                      type="date"
                      value={fScadenza}
                      onChange={(e) => setFScadenza(e.target.value)}
                      className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/30"
                    />
                  </div>
                </div>

                {/* Anagrafica */}
                <div>
                  <label className="block text-xs font-semibold text-gray-500 uppercase mb-1.5">
                    Cliente / Fornitore
                  </label>
                  <select
                    value={fAnagraficaId}
                    onChange={(e) => setFAnagraficaId(e.target.value)}
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/30"
                  >
                    <option value="">— Nessuno —</option>
                    {anagrafiche.map((a) => (
                      <option key={a.id} value={a.id}>
                        {a.nome} ({a.tipo})
                      </option>
                    ))}
                  </select>
                </div>

                {/* Oggetto */}
                <div>
                  <label className="block text-xs font-semibold text-gray-500 uppercase mb-1.5">
                    Oggetto
                  </label>
                  <input
                    type="text"
                    value={fOggetto}
                    onChange={(e) => setFOggetto(e.target.value)}
                    placeholder="Descrizione breve del documento"
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/30"
                  />
                </div>

                {/* Righe */}
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <label className="text-xs font-semibold text-gray-500 uppercase">
                      Righe *
                    </label>
                    <button
                      type="button"
                      onClick={aggiungiRiga}
                      className="flex items-center gap-1 text-xs text-[#1e3a8a] hover:text-[#1e40af] font-medium"
                    >
                      <Plus className="w-3.5 h-3.5" />
                      Aggiungi riga
                    </button>
                  </div>

                  <div className="space-y-2">
                    {/* Intestazioni colonne */}
                    <div className="grid gap-2 text-xs text-gray-400 font-medium px-1"
                      style={{ gridTemplateColumns: "1fr 80px 120px 70px 32px" }}>
                      <span>Descrizione</span>
                      <span>Qta</span>
                      <span>Prezzo €</span>
                      <span>IVA %</span>
                      <span></span>
                    </div>

                    {fRighe.map((riga, i) => (
                      <div
                        key={i}
                        className="grid gap-2 items-center"
                        style={{ gridTemplateColumns: "1fr 80px 120px 70px 32px" }}
                      >
                        <input
                          type="text"
                          value={riga.descrizione}
                          onChange={(e) => aggiornaRiga(i, "descrizione", e.target.value)}
                          placeholder="Descrizione"
                          required
                          className="border border-gray-200 rounded-lg px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/30"
                        />
                        <input
                          type="number"
                          value={riga.quantita}
                          onChange={(e) => aggiornaRiga(i, "quantita", e.target.value)}
                          min="0.01"
                          step="0.01"
                          required
                          className="border border-gray-200 rounded-lg px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/30"
                        />
                        <input
                          type="number"
                          value={riga.prezzo_unitario}
                          onChange={(e) =>
                            aggiornaRiga(i, "prezzo_unitario", e.target.value)
                          }
                          min="0"
                          step="0.01"
                          required
                          className="border border-gray-200 rounded-lg px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/30"
                        />
                        <input
                          type="number"
                          value={riga.iva_percentuale}
                          onChange={(e) =>
                            aggiornaRiga(i, "iva_percentuale", e.target.value)
                          }
                          min="0"
                          max="100"
                          step="0.01"
                          required
                          className="border border-gray-200 rounded-lg px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/30"
                        />
                        <button
                          type="button"
                          onClick={() => rimuoviRiga(i)}
                          disabled={fRighe.length === 1}
                          className="p-1 rounded-lg text-gray-300 hover:text-red-500 transition-colors disabled:opacity-30"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    ))}
                  </div>

                  {/* Subtotale anteprima */}
                  <div className="mt-3 pt-3 border-t border-gray-100 space-y-1 text-right text-xs text-gray-500">
                    <div>
                      Subtotale: <span className="font-medium">{fmt(totaleRighe())}</span>
                    </div>
                    <div>
                      IVA: <span className="font-medium">{fmt(totaleIvaRighe())}</span>
                    </div>
                    <div className="text-sm font-bold text-[#1e3a8a]">
                      Totale: {fmt(totaleRighe() + totaleIvaRighe())}
                    </div>
                  </div>
                </div>

                {/* Note */}
                <div>
                  <label className="block text-xs font-semibold text-gray-500 uppercase mb-1.5">
                    Note
                  </label>
                  <textarea
                    value={fNote}
                    onChange={(e) => setFNote(e.target.value)}
                    rows={3}
                    placeholder="Note aggiuntive..."
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/30"
                  />
                </div>
              </div>

              {/* Footer form */}
              <div className="px-6 py-4 border-t border-gray-100 flex justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setShowForm(false)}
                  className="px-4 py-2 text-sm text-gray-600 hover:text-gray-900 font-medium transition-colors"
                >
                  Annulla
                </button>
                <button
                  type="submit"
                  disabled={salvataggio}
                  className="flex items-center gap-2 bg-[#1e3a8a] hover:bg-[#1e40af] text-white px-5 py-2 rounded-lg text-sm font-medium transition-colors disabled:opacity-60"
                >
                  {salvataggio ? (
                    "Salvataggio..."
                  ) : (
                    <>
                      <Check className="w-4 h-4" />
                      {editDoc ? "Aggiorna" : "Crea Documento"}
                    </>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ══ FORM ANAGRAFICA (overlay) ══ */}
      {showAnaForm && (
        <div className="fixed inset-0 z-50 flex">
          <div
            className="flex-1 bg-black/30 backdrop-blur-sm"
            onClick={() => setShowAnaForm(false)}
          />
          <div className="w-full max-w-lg bg-white shadow-2xl flex flex-col overflow-hidden">
            <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 bg-[#1e3a8a]">
              <h2 className="text-white font-semibold">
                {editAna ? "Modifica Anagrafica" : "Nuova Anagrafica"}
              </h2>
              <button
                onClick={() => setShowAnaForm(false)}
                className="text-white/70 hover:text-white transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={salvaAnagrafica} className="flex-1 overflow-y-auto">
              <div className="px-6 py-5 space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div className="col-span-2">
                    <label className="block text-xs font-semibold text-gray-500 uppercase mb-1.5">
                      Nome / Ragione Sociale *
                    </label>
                    <input
                      type="text"
                      value={aNome}
                      onChange={(e) => setANome(e.target.value)}
                      required
                      className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/30"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 uppercase mb-1.5">
                      Tipo *
                    </label>
                    <select
                      value={aTipo}
                      onChange={(e) => setATipo(e.target.value as "cliente" | "fornitore" | "entrambi")}
                      className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/30"
                    >
                      <option value="cliente">Cliente</option>
                      <option value="fornitore">Fornitore</option>
                      <option value="entrambi">Entrambi</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 uppercase mb-1.5">
                      P. IVA
                    </label>
                    <input
                      type="text"
                      value={aPiva}
                      onChange={(e) => setAPiva(e.target.value)}
                      placeholder="12345678901"
                      className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/30"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 uppercase mb-1.5">
                      Codice Fiscale
                    </label>
                    <input
                      type="text"
                      value={aCf}
                      onChange={(e) => setACf(e.target.value)}
                      className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/30"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 uppercase mb-1.5">
                      Email
                    </label>
                    <input
                      type="email"
                      value={aEmail}
                      onChange={(e) => setAEmail(e.target.value)}
                      className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/30"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 uppercase mb-1.5">
                      Telefono
                    </label>
                    <input
                      type="text"
                      value={aTelefono}
                      onChange={(e) => setATelefono(e.target.value)}
                      className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/30"
                    />
                  </div>
                  <div className="col-span-2">
                    <label className="block text-xs font-semibold text-gray-500 uppercase mb-1.5">
                      Indirizzo
                    </label>
                    <input
                      type="text"
                      value={aIndirizzo}
                      onChange={(e) => setAIndirizzo(e.target.value)}
                      className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/30"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 uppercase mb-1.5">
                      CAP
                    </label>
                    <input
                      type="text"
                      value={aCap}
                      onChange={(e) => setACap(e.target.value)}
                      className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/30"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 uppercase mb-1.5">
                      Città
                    </label>
                    <input
                      type="text"
                      value={aCitta}
                      onChange={(e) => setACitta(e.target.value)}
                      className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/30"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-500 uppercase mb-1.5">
                      Provincia
                    </label>
                    <input
                      type="text"
                      value={aProvincia}
                      maxLength={2}
                      onChange={(e) => setAProvincia(e.target.value.toUpperCase())}
                      placeholder="RM"
                      className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/30"
                    />
                  </div>
                </div>
              </div>

              <div className="px-6 py-4 border-t border-gray-100 flex justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setShowAnaForm(false)}
                  className="px-4 py-2 text-sm text-gray-600 hover:text-gray-900 font-medium transition-colors"
                >
                  Annulla
                </button>
                <button
                  type="submit"
                  disabled={salvataggio}
                  className="flex items-center gap-2 bg-[#1e3a8a] hover:bg-[#1e40af] text-white px-5 py-2 rounded-lg text-sm font-medium transition-colors disabled:opacity-60"
                >
                  <Check className="w-4 h-4" />
                  {editAna ? "Aggiorna" : "Crea Anagrafica"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
