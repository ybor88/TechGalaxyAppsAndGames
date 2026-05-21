"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  ChevronLeft,
  Plus,
  Trash2,
  Lock,
  BookOpen,
  BarChart2,
  Receipt,
  FileSpreadsheet,
  AlertCircle,
  CheckCircle2,
  X,
  RefreshCw,
} from "lucide-react";
import {
  contabilitaApi,
  contiApi,
  Conto,
  RegistrazioneSummary,
  RegistrazioneDetail,
  RigaRegistrazioneIn,
  BilancioResponse,
  LiquidazioneIVA,
  TipoCausale,
  TipoIVA,
} from "@/lib/api";

// ── Helpers ───────────────────────────────────────────────────────────────────

const EUR = (n: number | string) =>
  new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(Number(n));

const today = () => new Date().toISOString().split("T")[0];

const firstOfMonth = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-01`;
};

const TIPO_CAUSALE_LABEL: Record<TipoCausale, string> = {
  manuale: "Manuale",
  fattura_attiva: "Fattura attiva",
  fattura_passiva: "Fattura passiva",
  pagamento: "Pagamento",
  incasso: "Incasso",
  altro: "Altro",
};

const TIPO_BADGE: Record<string, string> = {
  attivo: "bg-blue-50 text-blue-700",
  passivo: "bg-orange-50 text-orange-700",
  costo: "bg-red-50 text-red-600",
  ricavo: "bg-green-50 text-green-700",
};

// ── Riga registrazione vuota ──────────────────────────────────────────────────

interface RigaForm extends RigaRegistrazioneIn {
  _key: number;
}

let _keyCounter = 0;
const newRiga = (): RigaForm => ({
  _key: ++_keyCounter,
  conto_id: 0,
  descrizione: "",
  dare: 0,
  avere: 0,
  aliquota_iva: undefined,
  tipo_iva: undefined,
});

// ── Componente principale ─────────────────────────────────────────────────────

type Tab = "prima_nota" | "bilancio" | "iva" | "piano_conti";

export default function ContabilitaPage() {
  const [tab, setTab] = useState<Tab>("prima_nota");
  const [conti, setConti] = useState<Conto[]>([]);
  const [registrazioni, setRegistrazioni] = useState<RegistrazioneSummary[]>([]);
  const [dettaglio, setDettaglio] = useState<RegistrazioneDetail | null>(null);
  const [bilancio, setBilancio] = useState<BilancioResponse | null>(null);
  const [liquidazione, setLiquidazione] = useState<LiquidazioneIVA | null>(null);
  const [ivaDa, setIvaDa] = useState(firstOfMonth());
  const [ivaA, setIvaA] = useState(today());
  const [showForm, setShowForm] = useState(false);
  const [loading, setLoading] = useState(false);
  const [errore, setErrore] = useState<string | null>(null);
  const [successo, setSuccesso] = useState<string | null>(null);

  // Form nuova registrazione
  const [fData, setFData] = useState(today());
  const [fCausale, setFCausale] = useState("");
  const [fTipoCausale, setFTipoCausale] = useState<TipoCausale>("manuale");
  const [fNote, setFNote] = useState("");
  const [fRighe, setFRighe] = useState<RigaForm[]>([newRiga(), newRiga()]);
  const [fSalvataggio, setFSalvataggio] = useState(false);
  const [fErrore, setFErrore] = useState<string | null>(null);

  // ── Caricamento dati ────────────────────────────────────────────────────────

  const caricaConti = useCallback(async () => {
    try {
      const res = await contiApi.list();
      setConti(res.data);
    } catch {
      /* ignore */
    }
  }, []);

  const caricaRegistrazioni = useCallback(async () => {
    setLoading(true);
    try {
      const res = await contabilitaApi.listRegistrazioni();
      setRegistrazioni(res.data);
    } catch {
      setErrore("Impossibile caricare le registrazioni.");
    } finally {
      setLoading(false);
    }
  }, []);

  const caricaBilancio = useCallback(async () => {
    setLoading(true);
    try {
      const res = await contabilitaApi.getBilancio();
      setBilancio(res.data);
    } catch {
      setErrore("Impossibile caricare il bilancio.");
    } finally {
      setLoading(false);
    }
  }, []);

  const caricaIVA = useCallback(async () => {
    setLoading(true);
    setErrore(null);
    try {
      const res = await contabilitaApi.getLiquidazioneIVA(ivaDa, ivaA);
      setLiquidazione(res.data);
    } catch {
      setErrore("Impossibile caricare la liquidazione IVA.");
    } finally {
      setLoading(false);
    }
  }, [ivaDa, ivaA]);

  useEffect(() => {
    caricaConti();
    caricaRegistrazioni();
  }, [caricaConti, caricaRegistrazioni]);

  useEffect(() => {
    if (tab === "bilancio") caricaBilancio();
    if (tab === "iva") caricaIVA();
    if (tab === "prima_nota") caricaRegistrazioni();
  }, [tab]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Azioni ──────────────────────────────────────────────────────────────────

  const apriDettaglio = async (id: number) => {
    try {
      const res = await contabilitaApi.getRegistrazione(id);
      setDettaglio(res.data);
    } catch {
      setErrore("Impossibile caricare il dettaglio.");
    }
  };

  const eliminaRegistrazione = async (id: number) => {
    if (!confirm("Eliminare questa registrazione? L'azione non è reversibile.")) return;
    try {
      await contabilitaApi.deleteRegistrazione(id);
      setSuccesso("Registrazione eliminata.");
      if (dettaglio?.id === id) setDettaglio(null);
      caricaRegistrazioni();
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { detail?: string } } })?.response?.data?.detail;
      setErrore(msg ?? "Impossibile eliminare la registrazione.");
    }
  };

  const chiudiRegistrazione = async (id: number) => {
    if (!confirm("Chiudere (bloccare) questa registrazione? Non sarà più eliminabile.")) return;
    try {
      await contabilitaApi.chiudiRegistrazione(id);
      setSuccesso("Registrazione chiusa.");
      caricaRegistrazioni();
      if (dettaglio?.id === id) {
        const res = await contabilitaApi.getRegistrazione(id);
        setDettaglio(res.data);
      }
    } catch {
      setErrore("Impossibile chiudere la registrazione.");
    }
  };

  const inizializzaPianoConti = async () => {
    if (!confirm("Inizializzare il piano dei conti standard italiano? Verranno creati i conti mancanti senza sovrascrivere quelli esistenti.")) return;
    try {
      const res = await contabilitaApi.inizializzaPianoConti();
      setSuccesso(res.data.message);
      caricaConti();
    } catch {
      setErrore("Impossibile inizializzare il piano dei conti.");
    }
  };

  // ── Form nuova registrazione ─────────────────────────────────────────────────

  const resetForm = () => {
    setFData(today());
    setFCausale("");
    setFTipoCausale("manuale");
    setFNote("");
    setFRighe([newRiga(), newRiga()]);
    setFErrore(null);
  };

  const aggiungiRiga = () => setFRighe((r) => [...r, newRiga()]);
  const rimuoviRiga = (key: number) =>
    setFRighe((r) => r.filter((x) => x._key !== key));

  const aggiornaRiga = (key: number, field: keyof RigaForm, value: unknown) =>
    setFRighe((rows) =>
      rows.map((r) => (r._key === key ? { ...r, [field]: value } : r))
    );

  const totDare = fRighe.reduce((s, r) => s + Number(r.dare || 0), 0);
  const totAvere = fRighe.reduce((s, r) => s + Number(r.avere || 0), 0);
  const bilanciata = Math.abs(totDare - totAvere) < 0.005 && totDare > 0;

  const submitRegistrazione = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!fCausale.trim()) { setFErrore("La causale è obbligatoria."); return; }
    if (!bilanciata) { setFErrore("La registrazione non è bilanciata (dare ≠ avere)."); return; }
    const righePulite = fRighe.filter((r) => r.conto_id > 0 && (Number(r.dare) > 0 || Number(r.avere) > 0));
    if (righePulite.length < 2) { setFErrore("Inserire almeno 2 righe valide."); return; }

    setFSalvataggio(true);
    setFErrore(null);
    try {
      await contabilitaApi.createRegistrazione({
        data: fData,
        causale: fCausale,
        tipo_causale: fTipoCausale,
        note: fNote || undefined,
        righe: righePulite.map((r) => ({
          conto_id: r.conto_id,
          descrizione: r.descrizione || undefined,
          dare: Number(r.dare),
          avere: Number(r.avere),
          aliquota_iva: r.aliquota_iva ? Number(r.aliquota_iva) : undefined,
          tipo_iva: r.tipo_iva || undefined,
        })),
      });
      setSuccesso("Registrazione salvata.");
      setShowForm(false);
      resetForm();
      caricaRegistrazioni();
    } catch (err: unknown) {
      const detail = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail;
      setFErrore(detail ?? "Errore durante il salvataggio.");
    } finally {
      setFSalvataggio(false);
    }
  };

  // ── Render ──────────────────────────────────────────────────────────────────

  return (
    <div className="min-h-screen bg-[#f8fafc]">
      {/* Header */}
      <header className="bg-white border-b border-gray-200 px-6 py-4">
        <div className="max-w-7xl mx-auto flex items-center gap-4">
          <Link href="/" className="text-gray-400 hover:text-gray-600 transition-colors">
            <ChevronLeft className="w-5 h-5" />
          </Link>
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-orange-100 flex items-center justify-center">
              <BookOpen className="w-5 h-5 text-orange-600" />
            </div>
            <div>
              <h1 className="text-lg font-semibold text-gray-900">Contabilità Generale</h1>
              <p className="text-xs text-gray-500">Partita doppia · Prima nota · IVA · Bilancio</p>
            </div>
          </div>
        </div>
      </header>

      {/* Alert globali */}
      {errore && (
        <div className="max-w-7xl mx-auto px-6 pt-4">
          <div className="flex items-center gap-2 bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-sm">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            <span className="flex-1">{errore}</span>
            <button onClick={() => setErrore(null)}><X className="w-4 h-4" /></button>
          </div>
        </div>
      )}
      {successo && (
        <div className="max-w-7xl mx-auto px-6 pt-4">
          <div className="flex items-center gap-2 bg-green-50 border border-green-200 text-green-700 rounded-lg px-4 py-3 text-sm">
            <CheckCircle2 className="w-4 h-4 flex-shrink-0" />
            <span className="flex-1">{successo}</span>
            <button onClick={() => setSuccesso(null)}><X className="w-4 h-4" /></button>
          </div>
        </div>
      )}

      <div className="max-w-7xl mx-auto px-6 py-6 space-y-6">
        {/* Tabs */}
        <div className="flex gap-1 border-b border-gray-200">
          {(
            [
              { key: "prima_nota", label: "Prima Nota", icon: FileSpreadsheet },
              { key: "bilancio", label: "Bilancio", icon: BarChart2 },
              { key: "iva", label: "Liquidazione IVA", icon: Receipt },
              { key: "piano_conti", label: "Piano dei Conti", icon: BookOpen },
            ] as const
          ).map(({ key, label, icon: Icon }) => (
            <button
              key={key}
              onClick={() => { setTab(key); setErrore(null); }}
              className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                tab === key
                  ? "border-orange-500 text-orange-600"
                  : "border-transparent text-gray-500 hover:text-gray-700"
              }`}
            >
              <Icon className="w-4 h-4" />
              {label}
            </button>
          ))}
        </div>

        {/* ── Prima Nota ─────────────────────────────────────────────────────── */}
        {tab === "prima_nota" && (
          <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
            {/* Lista registrazioni */}
            <div className="lg:col-span-2 space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">
                  Registrazioni
                </h2>
                <button
                  onClick={() => { setShowForm(!showForm); resetForm(); }}
                  className="flex items-center gap-1.5 bg-orange-600 hover:bg-orange-700 text-white text-sm px-3 py-1.5 rounded-lg transition-colors"
                >
                  <Plus className="w-4 h-4" />
                  Nuova
                </button>
              </div>

              {loading && !registrazioni.length ? (
                <div className="text-center text-gray-400 py-12">Caricamento…</div>
              ) : registrazioni.length === 0 ? (
                <div className="text-center text-gray-400 py-12 bg-white rounded-xl border border-gray-100">
                  Nessuna registrazione.<br />
                  <span className="text-xs">Usa &ldquo;Nuova&rdquo; per inserire la prima.</span>
                </div>
              ) : (
                <div className="space-y-2">
                  {registrazioni.map((r) => (
                    <button
                      key={r.id}
                      onClick={() => apriDettaglio(r.id)}
                      className={`w-full text-left bg-white border rounded-xl px-4 py-3 hover:border-orange-300 transition-colors ${
                        dettaglio?.id === r.id ? "border-orange-400 ring-1 ring-orange-200" : "border-gray-100"
                      }`}
                    >
                      <div className="flex items-center justify-between mb-1">
                        <span className="text-xs text-gray-400">#{r.numero} · {r.data}</span>
                        <div className="flex items-center gap-1.5">
                          {r.chiusa && (
                            <span className="text-xs bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded">
                              Chiusa
                            </span>
                          )}
                          <span className="text-xs bg-orange-50 text-orange-600 px-1.5 py-0.5 rounded">
                            {TIPO_CAUSALE_LABEL[r.tipo_causale as TipoCausale] ?? r.tipo_causale}
                          </span>
                        </div>
                      </div>
                      <p className="text-sm font-medium text-gray-800 truncate">{r.causale}</p>
                      <div className="flex gap-4 mt-1 text-xs text-gray-500">
                        <span>Dare: <span className="font-medium text-blue-700">{EUR(r.totale_dare)}</span></span>
                        <span>Avere: <span className="font-medium text-green-700">{EUR(r.totale_avere)}</span></span>
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* Dettaglio o form */}
            <div className="lg:col-span-3">
              {showForm ? (
                /* ── Form nuova registrazione ── */
                <div className="bg-white border border-gray-100 rounded-xl p-5">
                  <div className="flex items-center justify-between mb-4">
                    <h3 className="font-semibold text-gray-800">Nuova registrazione</h3>
                    <button onClick={() => { setShowForm(false); resetForm(); }}>
                      <X className="w-4 h-4 text-gray-400 hover:text-gray-600" />
                    </button>
                  </div>

                  {fErrore && (
                    <div className="mb-4 flex items-center gap-2 bg-red-50 border border-red-200 text-red-700 rounded-lg px-3 py-2 text-sm">
                      <AlertCircle className="w-4 h-4 flex-shrink-0" />
                      {fErrore}
                    </div>
                  )}

                  <form onSubmit={submitRegistrazione} className="space-y-4">
                    {/* Header */}
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="block text-xs font-medium text-gray-600 mb-1">Data *</label>
                        <input
                          type="date"
                          value={fData}
                          onChange={(e) => setFData(e.target.value)}
                          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-300"
                          required
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-medium text-gray-600 mb-1">Tipo</label>
                        <select
                          value={fTipoCausale}
                          onChange={(e) => setFTipoCausale(e.target.value as TipoCausale)}
                          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-300 bg-white"
                        >
                          {Object.entries(TIPO_CAUSALE_LABEL).map(([k, v]) => (
                            <option key={k} value={k}>{v}</option>
                          ))}
                        </select>
                      </div>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Causale *</label>
                      <input
                        type="text"
                        value={fCausale}
                        onChange={(e) => setFCausale(e.target.value)}
                        placeholder="Es. Acquisto merci da fornitore XYZ"
                        className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-300"
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Note</label>
                      <input
                        type="text"
                        value={fNote}
                        onChange={(e) => setFNote(e.target.value)}
                        placeholder="Facoltativo"
                        className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-300"
                      />
                    </div>

                    {/* Righe */}
                    <div>
                      <div className="flex items-center justify-between mb-2">
                        <label className="text-xs font-medium text-gray-600">Righe (partita doppia)</label>
                        <button
                          type="button"
                          onClick={aggiungiRiga}
                          className="text-xs text-orange-600 hover:text-orange-800 flex items-center gap-1"
                        >
                          <Plus className="w-3 h-3" /> Aggiungi riga
                        </button>
                      </div>

                      <div className="overflow-x-auto">
                        <table className="w-full text-xs">
                          <thead>
                            <tr className="text-gray-500 border-b border-gray-100">
                              <th className="text-left pb-1 font-medium w-1/3">Conto</th>
                              <th className="text-left pb-1 font-medium">Descrizione</th>
                              <th className="text-right pb-1 font-medium w-24">Dare (€)</th>
                              <th className="text-right pb-1 font-medium w-24">Avere (€)</th>
                              <th className="text-center pb-1 font-medium w-16">Aliq. IVA</th>
                              <th className="text-center pb-1 font-medium w-20">Tipo IVA</th>
                              <th className="w-6"></th>
                            </tr>
                          </thead>
                          <tbody>
                            {fRighe.map((riga) => (
                              <tr key={riga._key} className="border-b border-gray-50">
                                <td className="py-1 pr-1">
                                  <select
                                    value={riga.conto_id || ""}
                                    onChange={(e) =>
                                      aggiornaRiga(riga._key, "conto_id", Number(e.target.value))
                                    }
                                    className="w-full border border-gray-200 rounded px-1.5 py-1 text-xs bg-white focus:outline-none focus:ring-1 focus:ring-orange-300"
                                  >
                                    <option value="">— seleziona —</option>
                                    {conti.map((c) => (
                                      <option key={c.id} value={c.id}>
                                        {c.codice} — {c.descrizione}
                                      </option>
                                    ))}
                                  </select>
                                </td>
                                <td className="py-1 pr-1">
                                  <input
                                    type="text"
                                    value={riga.descrizione ?? ""}
                                    onChange={(e) =>
                                      aggiornaRiga(riga._key, "descrizione", e.target.value)
                                    }
                                    className="w-full border border-gray-200 rounded px-1.5 py-1 text-xs focus:outline-none focus:ring-1 focus:ring-orange-300"
                                    placeholder="facoltativo"
                                  />
                                </td>
                                <td className="py-1 pr-1">
                                  <input
                                    type="number"
                                    min="0"
                                    step="0.01"
                                    value={riga.dare || ""}
                                    onChange={(e) =>
                                      aggiornaRiga(riga._key, "dare", e.target.value)
                                    }
                                    className="w-full border border-gray-200 rounded px-1.5 py-1 text-xs text-right focus:outline-none focus:ring-1 focus:ring-orange-300"
                                    placeholder="0.00"
                                  />
                                </td>
                                <td className="py-1 pr-1">
                                  <input
                                    type="number"
                                    min="0"
                                    step="0.01"
                                    value={riga.avere || ""}
                                    onChange={(e) =>
                                      aggiornaRiga(riga._key, "avere", e.target.value)
                                    }
                                    className="w-full border border-gray-200 rounded px-1.5 py-1 text-xs text-right focus:outline-none focus:ring-1 focus:ring-orange-300"
                                    placeholder="0.00"
                                  />
                                </td>
                                <td className="py-1 pr-1">
                                  <select
                                    value={riga.aliquota_iva ?? ""}
                                    onChange={(e) =>
                                      aggiornaRiga(
                                        riga._key,
                                        "aliquota_iva",
                                        e.target.value ? Number(e.target.value) : undefined
                                      )
                                    }
                                    className="w-full border border-gray-200 rounded px-1 py-1 text-xs bg-white focus:outline-none focus:ring-1 focus:ring-orange-300"
                                  >
                                    <option value="">—</option>
                                    <option value="22">22%</option>
                                    <option value="10">10%</option>
                                    <option value="5">5%</option>
                                    <option value="4">4%</option>
                                    <option value="0">0%</option>
                                  </select>
                                </td>
                                <td className="py-1 pr-1">
                                  <select
                                    value={riga.tipo_iva ?? ""}
                                    onChange={(e) =>
                                      aggiornaRiga(
                                        riga._key,
                                        "tipo_iva",
                                        (e.target.value as TipoIVA) || undefined
                                      )
                                    }
                                    className="w-full border border-gray-200 rounded px-1 py-1 text-xs bg-white focus:outline-none focus:ring-1 focus:ring-orange-300"
                                  >
                                    <option value="">—</option>
                                    <option value="imponibile">Imponibile</option>
                                    <option value="iva">IVA</option>
                                    <option value="esente">Esente</option>
                                  </select>
                                </td>
                                <td className="py-1 text-center">
                                  {fRighe.length > 2 && (
                                    <button
                                      type="button"
                                      onClick={() => rimuoviRiga(riga._key)}
                                      className="text-red-400 hover:text-red-600"
                                    >
                                      <X className="w-3 h-3" />
                                    </button>
                                  )}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                          <tfoot>
                            <tr className="font-semibold text-xs border-t border-gray-200">
                              <td colSpan={2} className="pt-2 text-gray-600">Totali</td>
                              <td className="pt-2 text-right text-blue-700">{EUR(totDare)}</td>
                              <td className="pt-2 text-right text-green-700">{EUR(totAvere)}</td>
                              <td colSpan={3}></td>
                            </tr>
                          </tfoot>
                        </table>
                      </div>

                      {/* Indicatore bilanciamento */}
                      <div className={`mt-2 flex items-center gap-2 text-xs rounded-lg px-3 py-2 ${
                        bilanciata
                          ? "bg-green-50 text-green-700"
                          : "bg-amber-50 text-amber-700"
                      }`}>
                        {bilanciata ? (
                          <CheckCircle2 className="w-3.5 h-3.5" />
                        ) : (
                          <AlertCircle className="w-3.5 h-3.5" />
                        )}
                        {bilanciata
                          ? "Registrazione bilanciata"
                          : `Sbilancio: ${EUR(Math.abs(totDare - totAvere))}`}
                      </div>
                    </div>

                    <button
                      type="submit"
                      disabled={!bilanciata || fSalvataggio}
                      className="w-full bg-orange-600 hover:bg-orange-700 disabled:bg-gray-300 disabled:cursor-not-allowed text-white font-medium py-2.5 rounded-lg text-sm transition-colors"
                    >
                      {fSalvataggio ? "Salvataggio…" : "Salva registrazione"}
                    </button>
                  </form>
                </div>
              ) : dettaglio ? (
                /* ── Dettaglio registrazione ── */
                <div className="bg-white border border-gray-100 rounded-xl p-5">
                  <div className="flex items-center justify-between mb-4">
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="text-xs text-gray-400">Registrazione #{dettaglio.numero}</span>
                        {dettaglio.chiusa && (
                          <span className="text-xs bg-gray-100 text-gray-500 px-2 py-0.5 rounded-full flex items-center gap-1">
                            <Lock className="w-3 h-3" /> Chiusa
                          </span>
                        )}
                      </div>
                      <h3 className="font-semibold text-gray-800 mt-0.5">{dettaglio.causale}</h3>
                      <p className="text-xs text-gray-400">
                        {dettaglio.data} · {TIPO_CAUSALE_LABEL[dettaglio.tipo_causale as TipoCausale]}
                      </p>
                    </div>
                    <div className="flex gap-2">
                      {!dettaglio.chiusa && (
                        <>
                          <button
                            onClick={() => chiudiRegistrazione(dettaglio.id)}
                            title="Chiudi registrazione"
                            className="p-2 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                          >
                            <Lock className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => eliminaRegistrazione(dettaglio.id)}
                            title="Elimina registrazione"
                            className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </>
                      )}
                      <button onClick={() => setDettaglio(null)}>
                        <X className="w-4 h-4 text-gray-400 hover:text-gray-600" />
                      </button>
                    </div>
                  </div>

                  {dettaglio.note && (
                    <p className="text-xs text-gray-500 mb-4 italic">{dettaglio.note}</p>
                  )}

                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-xs text-gray-500 border-b border-gray-100">
                        <th className="text-left pb-2 font-medium">Conto</th>
                        <th className="text-left pb-2 font-medium">Descrizione</th>
                        <th className="text-right pb-2 font-medium">Dare</th>
                        <th className="text-right pb-2 font-medium">Avere</th>
                        <th className="text-center pb-2 font-medium">IVA</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dettaglio.righe.map((r) => (
                        <tr key={r.id} className="border-b border-gray-50">
                          <td className="py-2 pr-3">
                            <span className="font-mono text-xs text-gray-500">{r.conto_codice}</span>
                            <span className="block text-xs text-gray-700">{r.conto_descrizione}</span>
                          </td>
                          <td className="py-2 pr-3 text-xs text-gray-500">{r.descrizione ?? "—"}</td>
                          <td className="py-2 text-right font-medium text-blue-700">
                            {Number(r.dare) > 0 ? EUR(r.dare) : "—"}
                          </td>
                          <td className="py-2 text-right font-medium text-green-700">
                            {Number(r.avere) > 0 ? EUR(r.avere) : "—"}
                          </td>
                          <td className="py-2 text-center text-xs text-gray-400">
                            {r.aliquota_iva != null ? `${r.aliquota_iva}%` : ""}
                            {r.tipo_iva ? ` (${r.tipo_iva})` : ""}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                    <tfoot>
                      <tr className="text-sm font-semibold border-t border-gray-200">
                        <td colSpan={2} className="pt-3 text-gray-700">Totali</td>
                        <td className="pt-3 text-right text-blue-700">{EUR(dettaglio.totale_dare)}</td>
                        <td className="pt-3 text-right text-green-700">{EUR(dettaglio.totale_avere)}</td>
                        <td></td>
                      </tr>
                    </tfoot>
                  </table>
                </div>
              ) : (
                <div className="flex items-center justify-center h-48 text-gray-300 text-sm bg-white border border-gray-100 rounded-xl">
                  Seleziona una registrazione o creane una nuova
                </div>
              )}
            </div>
          </div>
        )}

        {/* ── Bilancio di verifica ────────────────────────────────────────────── */}
        {tab === "bilancio" && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">
                Bilancio di Verifica
              </h2>
              <button
                onClick={caricaBilancio}
                className="flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-700 px-3 py-1.5 rounded-lg border border-gray-200 hover:bg-gray-50 transition-colors"
              >
                <RefreshCw className="w-4 h-4" /> Aggiorna
              </button>
            </div>

            {bilancio && (
              <>
                {/* KPI Stato Patrimoniale + Conto Economico */}
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                  {[
                    { label: "Totale Attivo", value: bilancio.totale_attivo, color: "blue" },
                    { label: "Totale Passivo", value: bilancio.totale_passivo, color: "orange" },
                    { label: "Totale Ricavi", value: bilancio.totale_ricavi, color: "green" },
                    {
                      label: bilancio.utile_perdita >= 0 ? "Utile d'esercizio" : "Perdita d'esercizio",
                      value: bilancio.utile_perdita,
                      color: bilancio.utile_perdita >= 0 ? "emerald" : "red",
                    },
                  ].map(({ label, value, color }) => (
                    <div key={label} className="bg-white border border-gray-100 rounded-xl p-4">
                      <p className="text-xs text-gray-500 mb-1">{label}</p>
                      <p className={`text-lg font-bold text-${color}-600`}>{EUR(value)}</p>
                    </div>
                  ))}
                </div>

                {/* Tabella bilancio */}
                <div className="bg-white border border-gray-100 rounded-xl overflow-hidden">
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead className="bg-gray-50 border-b border-gray-100">
                        <tr>
                          <th className="text-left px-4 py-3 text-xs font-semibold text-gray-600">Codice</th>
                          <th className="text-left px-4 py-3 text-xs font-semibold text-gray-600">Conto</th>
                          <th className="text-center px-3 py-3 text-xs font-semibold text-gray-600">Tipo</th>
                          <th className="text-right px-4 py-3 text-xs font-semibold text-gray-600">Totale Dare</th>
                          <th className="text-right px-4 py-3 text-xs font-semibold text-gray-600">Totale Avere</th>
                          <th className="text-right px-4 py-3 text-xs font-semibold text-gray-600">Saldo</th>
                        </tr>
                      </thead>
                      <tbody>
                        {bilancio.conti.map((c) => (
                          <tr key={c.conto_id} className="border-b border-gray-50 hover:bg-gray-50">
                            <td className="px-4 py-2.5 font-mono text-xs text-gray-500">{c.codice}</td>
                            <td className="px-4 py-2.5 text-sm text-gray-800">{c.descrizione}</td>
                            <td className="px-3 py-2.5 text-center">
                              <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${TIPO_BADGE[c.tipo]}`}>
                                {c.tipo}
                              </span>
                            </td>
                            <td className="px-4 py-2.5 text-right text-blue-700 font-medium">
                              {Number(c.totale_dare) > 0 ? EUR(c.totale_dare) : "—"}
                            </td>
                            <td className="px-4 py-2.5 text-right text-green-700 font-medium">
                              {Number(c.totale_avere) > 0 ? EUR(c.totale_avere) : "—"}
                            </td>
                            <td className="px-4 py-2.5 text-right font-semibold">
                              {Number(c.saldo) !== 0 ? EUR(c.saldo) : "—"}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                      <tfoot className="bg-gray-50 border-t-2 border-gray-200 font-bold">
                        <tr>
                          <td colSpan={3} className="px-4 py-3 text-sm text-gray-700">Totali</td>
                          <td className="px-4 py-3 text-right text-blue-700">{EUR(bilancio.totale_dare)}</td>
                          <td className="px-4 py-3 text-right text-green-700">{EUR(bilancio.totale_avere)}</td>
                          <td className="px-4 py-3 text-right text-gray-700">
                            {Number(bilancio.totale_dare) === Number(bilancio.totale_avere) ? (
                              <span className="text-green-600 flex items-center justify-end gap-1">
                                <CheckCircle2 className="w-4 h-4" /> Bilanciato
                              </span>
                            ) : (
                              <span className="text-red-600">Sbilancio!</span>
                            )}
                          </td>
                        </tr>
                      </tfoot>
                    </table>
                  </div>
                </div>
              </>
            )}

            {loading && !bilancio && (
              <div className="text-center text-gray-400 py-12">Caricamento bilancio…</div>
            )}
          </div>
        )}

        {/* ── Liquidazione IVA ────────────────────────────────────────────────── */}
        {tab === "iva" && (
          <div className="space-y-4">
            <div className="flex flex-wrap items-end gap-3">
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Dal</label>
                <input
                  type="date"
                  value={ivaDa}
                  onChange={(e) => setIvaDa(e.target.value)}
                  className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-300"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Al</label>
                <input
                  type="date"
                  value={ivaA}
                  onChange={(e) => setIvaA(e.target.value)}
                  className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-300"
                />
              </div>
              <button
                onClick={caricaIVA}
                className="flex items-center gap-1.5 bg-orange-600 hover:bg-orange-700 text-white text-sm px-4 py-2 rounded-lg transition-colors"
              >
                <RefreshCw className="w-4 h-4" /> Calcola
              </button>
            </div>

            {liquidazione && (
              <>
                {/* KPI IVA */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div className="bg-white border border-gray-100 rounded-xl p-4">
                    <p className="text-xs text-gray-500 mb-1">IVA a credito (acquisti)</p>
                    <p className="text-lg font-bold text-blue-600">{EUR(liquidazione.iva_a_credito)}</p>
                  </div>
                  <div className="bg-white border border-gray-100 rounded-xl p-4">
                    <p className="text-xs text-gray-500 mb-1">IVA a debito (vendite)</p>
                    <p className="text-lg font-bold text-orange-600">{EUR(liquidazione.iva_a_debito)}</p>
                  </div>
                  <div className={`border rounded-xl p-4 ${
                    Number(liquidazione.saldo_iva) > 0
                      ? "bg-red-50 border-red-200"
                      : Number(liquidazione.saldo_iva) < 0
                      ? "bg-green-50 border-green-200"
                      : "bg-gray-50 border-gray-200"
                  }`}>
                    <p className="text-xs text-gray-500 mb-1">
                      {Number(liquidazione.saldo_iva) > 0
                        ? "IVA da versare"
                        : Number(liquidazione.saldo_iva) < 0
                        ? "Credito IVA"
                        : "Saldo IVA"}
                    </p>
                    <p className={`text-lg font-bold ${
                      Number(liquidazione.saldo_iva) > 0
                        ? "text-red-600"
                        : Number(liquidazione.saldo_iva) < 0
                        ? "text-green-600"
                        : "text-gray-600"
                    }`}>
                      {EUR(Math.abs(liquidazione.saldo_iva))}
                    </p>
                  </div>
                </div>

                {/* Dettaglio per aliquota */}
                {liquidazione.dettaglio.length > 0 ? (
                  <div className="bg-white border border-gray-100 rounded-xl overflow-hidden">
                    <div className="px-4 py-3 border-b border-gray-100">
                      <h3 className="text-sm font-semibold text-gray-700">Dettaglio per aliquota</h3>
                    </div>
                    <table className="w-full text-sm">
                      <thead className="bg-gray-50 border-b border-gray-100">
                        <tr>
                          <th className="text-center px-4 py-2.5 text-xs font-semibold text-gray-600">Aliquota</th>
                          <th className="text-right px-4 py-2.5 text-xs font-semibold text-gray-600">Imponibile acquisti</th>
                          <th className="text-right px-4 py-2.5 text-xs font-semibold text-gray-600">IVA a credito</th>
                          <th className="text-right px-4 py-2.5 text-xs font-semibold text-gray-600">Imponibile vendite</th>
                          <th className="text-right px-4 py-2.5 text-xs font-semibold text-gray-600">IVA a debito</th>
                        </tr>
                      </thead>
                      <tbody>
                        {liquidazione.dettaglio.map((r, i) => (
                          <tr key={i} className="border-b border-gray-50 hover:bg-gray-50">
                            <td className="px-4 py-2.5 text-center font-semibold text-gray-700">{r.aliquota_iva}%</td>
                            <td className="px-4 py-2.5 text-right text-gray-600">{EUR(r.imponibile_acquisti)}</td>
                            <td className="px-4 py-2.5 text-right text-blue-700 font-medium">{EUR(r.iva_a_credito)}</td>
                            <td className="px-4 py-2.5 text-right text-gray-600">{EUR(r.imponibile_vendite)}</td>
                            <td className="px-4 py-2.5 text-right text-orange-700 font-medium">{EUR(r.iva_a_debito)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <div className="text-center text-gray-400 py-8 bg-white border border-gray-100 rounded-xl">
                    Nessun movimento IVA nel periodo selezionato.
                  </div>
                )}
              </>
            )}

            {loading && !liquidazione && (
              <div className="text-center text-gray-400 py-12">Calcolo in corso…</div>
            )}
          </div>
        )}

        {/* ── Piano dei Conti ─────────────────────────────────────────────────── */}
        {tab === "piano_conti" && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">Piano dei Conti</h2>
                <p className="text-xs text-gray-400 mt-0.5">
                  {conti.length} conti presenti · Piano dei conti italiano standard (5 classi)
                </p>
              </div>
              <button
                onClick={inizializzaPianoConti}
                className="flex items-center gap-1.5 bg-orange-600 hover:bg-orange-700 text-white text-sm px-4 py-2 rounded-lg transition-colors"
              >
                <BookOpen className="w-4 h-4" />
                Inizializza piano standard
              </button>
            </div>

            <div className="bg-white border border-gray-100 rounded-xl overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="bg-gray-50 border-b border-gray-100">
                    <tr>
                      <th className="text-left px-4 py-3 text-xs font-semibold text-gray-600">Codice</th>
                      <th className="text-left px-4 py-3 text-xs font-semibold text-gray-600">Descrizione</th>
                      <th className="text-center px-4 py-3 text-xs font-semibold text-gray-600">Tipo</th>
                      <th className="text-right px-4 py-3 text-xs font-semibold text-gray-600">Saldo</th>
                    </tr>
                  </thead>
                  <tbody>
                    {conti.length === 0 ? (
                      <tr>
                        <td colSpan={4} className="px-4 py-8 text-center text-gray-400 text-sm">
                          Nessun conto presente. Clicca &ldquo;Inizializza piano standard&rdquo; per creare il piano dei conti italiano.
                        </td>
                      </tr>
                    ) : (
                      conti.map((c) => (
                        <tr key={c.id} className="border-b border-gray-50 hover:bg-gray-50">
                          <td className="px-4 py-2.5 font-mono text-xs text-gray-500">{c.codice}</td>
                          <td className="px-4 py-2.5 text-sm text-gray-800">{c.descrizione}</td>
                          <td className="px-4 py-2.5 text-center">
                            <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${TIPO_BADGE[c.tipo] ?? "bg-gray-100 text-gray-600"}`}>
                              {c.tipo}
                            </span>
                          </td>
                          <td className={`px-4 py-2.5 text-right font-medium ${
                            Number(c.saldo) > 0
                              ? "text-green-700"
                              : Number(c.saldo) < 0
                              ? "text-red-600"
                              : "text-gray-400"
                          }`}>
                            {EUR(c.saldo)}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            <p className="text-xs text-gray-400 text-center">
              Per aggiungere o modificare singoli conti vai in{" "}
              <Link href="/conti" className="text-orange-600 hover:underline">
                Piano dei Conti
              </Link>.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
