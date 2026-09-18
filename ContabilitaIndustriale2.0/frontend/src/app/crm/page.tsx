"use client";

import { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import {
  crmApi,
  anagraficheApi,
  Anagrafica,
  AnagraficaCreate,
  AffidabilitaCliente,
  Scadenza,
  ScadenzaCreate,
  OpportunitaPipeline,
  OpportunitaCreate,
  OpportunitaUpdate,
  StoricoPagamento,
  StoricoPagamentoCreate,
  CrmSummary,
  FasePipeline,
  StatoScadenza,
} from "@/lib/api";
import {
  ChevronLeft,
  Users,
  Clock,
  TrendingUp,
  CreditCard,
  Plus,
  Trash2,
  Star,
  X,
  Building2,
  ShoppingCart,
  AlertTriangle,
  CheckCircle2,
  ChevronRight,
} from "lucide-react";

// ── Helpers ───────────────────────────────────────────────────────────────────

const fmt = (n: number | null | undefined) =>
  n != null
    ? new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(Number(n))
    : "—";

const fmtData = (s: string | null) =>
  s ? new Date(s).toLocaleDateString("it-IT") : "—";

const today = () => new Date().toISOString().split("T")[0];

// ── Costanti ──────────────────────────────────────────────────────────────────

const FASI_PIPELINE: { value: FasePipeline; label: string; colore: string }[] = [
  { value: "prospecting", label: "Prospecting", colore: "bg-slate-100 text-slate-700" },
  { value: "qualifica", label: "Qualifica", colore: "bg-blue-100 text-blue-700" },
  { value: "proposta", label: "Proposta", colore: "bg-violet-100 text-violet-700" },
  { value: "trattativa", label: "Trattativa", colore: "bg-orange-100 text-orange-700" },
  { value: "chiusa_vinta", label: "Chiusa (Vinta)", colore: "bg-green-100 text-green-700" },
  { value: "chiusa_persa", label: "Chiusa (Persa)", colore: "bg-red-100 text-red-700" },
];

const STATI_SCADENZA: { value: StatoScadenza; label: string; colore: string }[] = [
  { value: "aperta", label: "Aperta", colore: "bg-blue-100 text-blue-700" },
  { value: "pagata", label: "Pagata", colore: "bg-green-100 text-green-700" },
  { value: "scaduta", label: "Scaduta", colore: "bg-red-100 text-red-700" },
  { value: "annullata", label: "Annullata", colore: "bg-gray-100 text-gray-500" },
];

const SCORE_COLORS = {
  ottimo: "text-green-600 bg-green-50 border-green-200",
  buono: "text-blue-600 bg-blue-50 border-blue-200",
  sufficiente: "text-orange-500 bg-orange-50 border-orange-200",
  scarso: "text-red-600 bg-red-50 border-red-200",
};

// ── Tipi tab ──────────────────────────────────────────────────────────────────

type Tab = "clienti" | "fornitori" | "scadenze" | "pipeline" | "storico";

// ── Componente principale ─────────────────────────────────────────────────────

export default function CrmPage() {
  const [tab, setTab] = useState<Tab>("clienti");
  const [summary, setSummary] = useState<CrmSummary | null>(null);

  // Clienti & fornitori
  const [clienti, setClienti] = useState<Anagrafica[]>([]);
  const [fornitori, setFornitori] = useState<Anagrafica[]>([]);
  const [selectedAna, setSelectedAna] = useState<Anagrafica | null>(null);
  const [affidabilita, setAffidabilita] = useState<AffidabilitaCliente | null>(null);
  const [showAnaForm, setShowAnaForm] = useState(false);
  const [anaFormTipo, setAnaFormTipo] = useState<"cliente" | "fornitore">("cliente");

  // Scadenze
  const [scadenze, setScadenze] = useState<Scadenza[]>([]);
  const [showScadenzaForm, setShowScadenzaForm] = useState(false);

  // Pipeline
  const [pipeline, setPipeline] = useState<OpportunitaPipeline[]>([]);
  const [showPipelineForm, setShowPipelineForm] = useState(false);

  // Storico pagamenti
  const [storico, setStorico] = useState<StoricoPagamento[]>([]);
  const [showStoricoForm, setShowStoricoForm] = useState(false);

  // Generic state
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Form states
  const [anaForm, setAnaForm] = useState<AnagraficaCreate>({ nome: "", tipo: "cliente" });
  const [scadenzaForm, setScadenzaForm] = useState<ScadenzaCreate>({
    titolo: "",
    data_scadenza: today(),
    tipo: "incasso",
    stato: "aperta",
  });
  const [pipelineForm, setPipelineForm] = useState<OpportunitaCreate>({
    titolo: "",
    fase: "prospecting",
    probabilita: 50,
  });
  const [storicoForm, setStoricoForm] = useState<StoricoPagamentoCreate>({
    anagrafica_id: 0,
    data_pagamento: today(),
    importo: 0,
    metodo_pagamento: "bonifico",
    giorni_ritardo: 0,
  });

  // ── Load data ───────────────────────────────────────────────────────────────

  const loadSummary = useCallback(async () => {
    try {
      const r = await crmApi.summary();
      setSummary(r.data);
    } catch {
      /* silent */
    }
  }, []);

  const loadClienti = useCallback(async () => {
    setLoading(true);
    try {
      const r = await crmApi.listClienti();
      setClienti(r.data);
    } finally {
      setLoading(false);
    }
  }, []);

  const loadFornitori = useCallback(async () => {
    setLoading(true);
    try {
      const r = await crmApi.listFornitori();
      setFornitori(r.data);
    } finally {
      setLoading(false);
    }
  }, []);

  const loadScadenze = useCallback(async () => {
    setLoading(true);
    try {
      const r = await crmApi.listScadenze();
      setScadenze(r.data);
    } finally {
      setLoading(false);
    }
  }, []);

  const loadPipeline = useCallback(async () => {
    setLoading(true);
    try {
      const r = await crmApi.listPipeline();
      setPipeline(r.data);
    } finally {
      setLoading(false);
    }
  }, []);

  const loadStorico = useCallback(async () => {
    setLoading(true);
    try {
      const r = await crmApi.listStorico();
      setStorico(r.data);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSummary();
  }, [loadSummary]);

  useEffect(() => {
    if (tab === "clienti") loadClienti();
    else if (tab === "fornitori") loadFornitori();
    else if (tab === "scadenze") loadScadenze();
    else if (tab === "pipeline") loadPipeline();
    else if (tab === "storico") loadStorico();
  }, [tab, loadClienti, loadFornitori, loadScadenze, loadPipeline, loadStorico]);

  // ── Seleziona anagrafica e carica affidabilità ───────────────────────────────

  const selectAnagrafica = async (ana: Anagrafica) => {
    setSelectedAna(ana);
    setAffidabilita(null);
    try {
      const r = await crmApi.getAffidabilita(ana.id);
      setAffidabilita(r.data);
    } catch {
      /* nessun dato storico */
    }
  };

  // ── Azioni anagrafiche ───────────────────────────────────────────────────────

  const handleCreateAnagrafica = async () => {
    if (!anaForm.nome.trim()) return;
    setError(null);
    try {
      await anagraficheApi.create({ ...anaForm, tipo: anaFormTipo });
      setShowAnaForm(false);
      setAnaForm({ nome: "", tipo: "cliente" });
      if (anaFormTipo === "cliente") loadClienti();
      else loadFornitori();
      loadSummary();
    } catch (e: unknown) {
      setError("Errore creazione anagrafica");
    }
  };

  const handleDeleteAnagrafica = async (id: number) => {
    if (!confirm("Eliminare questa anagrafica?")) return;
    try {
      await anagraficheApi.delete(id);
      if (tab === "clienti") loadClienti();
      else loadFornitori();
      if (selectedAna?.id === id) setSelectedAna(null);
      loadSummary();
    } catch {
      setError("Impossibile eliminare (potrebbe avere documenti collegati)");
    }
  };

  // ── Azioni scadenze ──────────────────────────────────────────────────────────

  const handleCreateScadenza = async () => {
    if (!scadenzaForm.titolo.trim()) return;
    setError(null);
    try {
      await crmApi.createScadenza(scadenzaForm);
      setShowScadenzaForm(false);
      setScadenzaForm({ titolo: "", data_scadenza: today(), tipo: "incasso", stato: "aperta" });
      loadScadenze();
      loadSummary();
    } catch {
      setError("Errore creazione scadenza");
    }
  };

  const handleUpdateScadenzaStato = async (id: number, stato: StatoScadenza) => {
    try {
      await crmApi.updateScadenza(id, { stato });
      loadScadenze();
      loadSummary();
    } catch {
      setError("Errore aggiornamento scadenza");
    }
  };

  const handleDeleteScadenza = async (id: number) => {
    if (!confirm("Eliminare questa scadenza?")) return;
    try {
      await crmApi.deleteScadenza(id);
      loadScadenze();
      loadSummary();
    } catch {
      setError("Errore eliminazione scadenza");
    }
  };

  // ── Azioni pipeline ──────────────────────────────────────────────────────────

  const handleCreateOpportunita = async () => {
    if (!pipelineForm.titolo.trim()) return;
    setError(null);
    try {
      await crmApi.createOpportunita(pipelineForm);
      setShowPipelineForm(false);
      setPipelineForm({ titolo: "", fase: "prospecting", probabilita: 50 });
      loadPipeline();
      loadSummary();
    } catch {
      setError("Errore creazione opportunità");
    }
  };

  const handleUpdateFase = async (id: number, fase: FasePipeline) => {
    try {
      await crmApi.updateOpportunita(id, { fase });
      loadPipeline();
      loadSummary();
    } catch {
      setError("Errore aggiornamento pipeline");
    }
  };

  const handleDeleteOpportunita = async (id: number) => {
    if (!confirm("Eliminare questa opportunità?")) return;
    try {
      await crmApi.deleteOpportunita(id);
      loadPipeline();
      loadSummary();
    } catch {
      setError("Errore eliminazione opportunità");
    }
  };

  // ── Azioni storico ───────────────────────────────────────────────────────────

  const handleCreateStorico = async () => {
    if (!storicoForm.anagrafica_id || storicoForm.importo <= 0) return;
    setError(null);
    try {
      await crmApi.createStorico(storicoForm);
      setShowStoricoForm(false);
      setStoricoForm({
        anagrafica_id: 0,
        data_pagamento: today(),
        importo: 0,
        metodo_pagamento: "bonifico",
        giorni_ritardo: 0,
      });
      loadStorico();
    } catch {
      setError("Errore registrazione pagamento");
    }
  };

  const handleDeleteStorico = async (id: number) => {
    if (!confirm("Eliminare questo pagamento?")) return;
    try {
      await crmApi.deleteStorico(id);
      loadStorico();
    } catch {
      setError("Errore eliminazione pagamento");
    }
  };

  // ── Render helpers ───────────────────────────────────────────────────────────

  const faseBadge = (fase: string) => {
    const f = FASI_PIPELINE.find((x) => x.value === fase);
    return (
      <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${f?.colore ?? "bg-gray-100 text-gray-600"}`}>
        {f?.label ?? fase}
      </span>
    );
  };

  const statoBadge = (stato: string) => {
    const s = STATI_SCADENZA.find((x) => x.value === stato);
    return (
      <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${s?.colore ?? "bg-gray-100 text-gray-600"}`}>
        {s?.label ?? stato}
      </span>
    );
  };

  // ── Render anagrafiche (clienti o fornitori) ─────────────────────────────────

  const renderAnagraficheList = (lista: Anagrafica[], tipo: "cliente" | "fornitore") => (
    <div className="flex gap-6">
      {/* Lista */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-semibold text-gray-800 text-lg">
            {tipo === "cliente" ? "Clienti" : "Fornitori"} ({lista.length})
          </h2>
          <button
            onClick={() => { setAnaFormTipo(tipo); setShowAnaForm(true); }}
            className="flex items-center gap-1.5 bg-pink-600 hover:bg-pink-700 text-white text-sm px-3 py-1.5 rounded-lg transition"
          >
            <Plus size={15} /> Nuovo {tipo === "cliente" ? "cliente" : "fornitore"}
          </button>
        </div>

        {loading ? (
          <p className="text-gray-400 text-sm">Caricamento...</p>
        ) : lista.length === 0 ? (
          <p className="text-gray-400 text-sm">Nessun {tipo} registrato.</p>
        ) : (
          <div className="space-y-2">
            {lista.map((ana) => (
              <div
                key={ana.id}
                onClick={() => selectAnagrafica(ana)}
                className={`cursor-pointer p-3 rounded-xl border transition ${
                  selectedAna?.id === ana.id
                    ? "border-pink-400 bg-pink-50"
                    : "border-gray-200 bg-white hover:border-pink-300"
                }`}
              >
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-medium text-gray-800 text-sm">{ana.nome}</p>
                    <p className="text-xs text-gray-500">
                      {ana.piva ? `P.IVA: ${ana.piva}` : ""}{ana.piva && ana.email ? " · " : ""}{ana.email ?? ""}
                    </p>
                    {(ana.citta || ana.telefono) && (
                      <p className="text-xs text-gray-400">
                        {[ana.citta, ana.telefono].filter(Boolean).join(" · ")}
                      </p>
                    )}
                  </div>
                  <div className="flex items-center gap-2">
                    <ChevronRight size={14} className="text-gray-400" />
                    <button
                      onClick={(e) => { e.stopPropagation(); handleDeleteAnagrafica(ana.id); }}
                      className="text-red-400 hover:text-red-600 transition"
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Dettaglio anagrafica */}
      {selectedAna && (
        <div className="w-80 flex-shrink-0">
          <div className="bg-white border border-gray-200 rounded-2xl p-5">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-semibold text-gray-800">{selectedAna.nome}</h3>
              <button onClick={() => setSelectedAna(null)} className="text-gray-400 hover:text-gray-600">
                <X size={16} />
              </button>
            </div>
            <div className="space-y-1.5 text-sm text-gray-600 mb-4">
              {selectedAna.piva && <p><span className="font-medium">P.IVA:</span> {selectedAna.piva}</p>}
              {selectedAna.cf && <p><span className="font-medium">CF:</span> {selectedAna.cf}</p>}
              {selectedAna.email && <p><span className="font-medium">Email:</span> {selectedAna.email}</p>}
              {selectedAna.telefono && <p><span className="font-medium">Tel:</span> {selectedAna.telefono}</p>}
              {selectedAna.indirizzo && (
                <p><span className="font-medium">Indirizzo:</span> {selectedAna.indirizzo}, {selectedAna.cap} {selectedAna.citta} ({selectedAna.provincia})</p>
              )}
              {selectedAna.note && <p className="italic text-gray-400">{selectedAna.note}</p>}
            </div>

            {/* Affidabilità */}
            {affidabilita ? (
              <div className={`border rounded-xl p-3 ${SCORE_COLORS[affidabilita.livello]}`}>
                <div className="flex items-center justify-between mb-2">
                  <span className="font-semibold text-sm">Affidabilità</span>
                  <span className="font-bold text-lg">{affidabilita.score}/100</span>
                </div>
                <p className="text-xs capitalize font-medium mb-1">{affidabilita.livello}</p>
                <div className="text-xs space-y-0.5 opacity-80">
                  <p>Pagamenti totali: {affidabilita.totale_pagamenti}</p>
                  <p>Puntuali: {affidabilita.pagamenti_puntuali} · In ritardo: {affidabilita.pagamenti_in_ritardo}</p>
                  {affidabilita.media_giorni_ritardo > 0 && (
                    <p>Media ritardo: {affidabilita.media_giorni_ritardo} gg</p>
                  )}
                </div>
              </div>
            ) : (
              <div className="border border-dashed border-gray-200 rounded-xl p-3 text-center">
                <Star size={18} className="text-gray-300 mx-auto mb-1" />
                <p className="text-xs text-gray-400">Nessun pagamento registrato</p>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );

  // ── UI principale ─────────────────────────────────────────────────────────────

  return (
    <div className="min-h-screen bg-[#f8fafc]">
      {/* Header */}
      <div className="bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Link href="/" className="text-gray-400 hover:text-gray-700 transition">
            <ChevronLeft size={20} />
          </Link>
          <Users size={22} className="text-pink-600" />
          <h1 className="text-xl font-bold text-gray-800">CRM Economico</h1>
        </div>
      </div>

      <div className="max-w-6xl mx-auto px-6 py-6">

        {/* KPI Summary */}
        {summary && (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 mb-6">
            {[
              { label: "Clienti", value: summary.totale_clienti, icon: Building2, color: "text-blue-600" },
              { label: "Fornitori", value: summary.totale_fornitori, icon: ShoppingCart, color: "text-violet-600" },
              { label: "Scadenze aperte", value: summary.scadenze_aperte, icon: Clock, color: "text-orange-500" },
              { label: "Scadute", value: summary.scadenze_scadute, icon: AlertTriangle, color: "text-red-500" },
              { label: "Opportunità", value: summary.opportunita_aperte, icon: TrendingUp, color: "text-green-600" },
              { label: "Valore pipeline", value: fmt(summary.valore_pipeline_attivo), icon: CreditCard, color: "text-pink-600", isText: true },
            ].map(({ label, value, icon: Icon, color, isText }) => (
              <div key={label} className="bg-white border border-gray-200 rounded-xl p-3 text-center">
                <Icon size={18} className={`${color} mx-auto mb-1`} />
                <p className={`font-bold text-lg ${color}`}>{isText ? value : value}</p>
                <p className="text-xs text-gray-500 leading-tight">{label}</p>
              </div>
            ))}
          </div>
        )}

        {/* Error */}
        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-xl px-4 py-2 mb-4 flex items-center justify-between">
            {error}
            <button onClick={() => setError(null)}><X size={14} /></button>
          </div>
        )}

        {/* Tabs */}
        <div className="flex gap-1 bg-gray-100 rounded-xl p-1 mb-6 w-fit">
          {(
            [
              { id: "clienti", label: "Clienti", icon: Building2 },
              { id: "fornitori", label: "Fornitori", icon: ShoppingCart },
              { id: "scadenze", label: "Scadenze", icon: Clock },
              { id: "pipeline", label: "Pipeline", icon: TrendingUp },
              { id: "storico", label: "Storico pagamenti", icon: CreditCard },
            ] as { id: Tab; label: string; icon: React.ElementType }[]
          ).map(({ id, label, icon: Icon }) => (
            <button
              key={id}
              onClick={() => setTab(id)}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium transition ${
                tab === id ? "bg-white shadow text-pink-600" : "text-gray-500 hover:text-gray-700"
              }`}
            >
              <Icon size={14} />
              {label}
            </button>
          ))}
        </div>

        {/* ── TAB CLIENTI ── */}
        {tab === "clienti" && renderAnagraficheList(clienti, "cliente")}

        {/* ── TAB FORNITORI ── */}
        {tab === "fornitori" && renderAnagraficheList(fornitori, "fornitore")}

        {/* ── TAB SCADENZE ── */}
        {tab === "scadenze" && (
          <div>
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-semibold text-gray-800 text-lg">Scadenze ({scadenze.length})</h2>
              <button
                onClick={() => setShowScadenzaForm(true)}
                className="flex items-center gap-1.5 bg-pink-600 hover:bg-pink-700 text-white text-sm px-3 py-1.5 rounded-lg transition"
              >
                <Plus size={15} /> Nuova scadenza
              </button>
            </div>

            {loading ? (
              <p className="text-gray-400 text-sm">Caricamento...</p>
            ) : scadenze.length === 0 ? (
              <p className="text-gray-400 text-sm">Nessuna scadenza registrata.</p>
            ) : (
              <div className="space-y-2">
                {scadenze.map((s) => {
                  const scaduta = s.giorni_alla_scadenza != null && s.giorni_alla_scadenza < 0 && s.stato === "aperta";
                  return (
                    <div
                      key={s.id}
                      className={`bg-white border rounded-xl p-4 flex items-center justify-between ${
                        scaduta ? "border-red-200 bg-red-50" : "border-gray-200"
                      }`}
                    >
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-0.5">
                          <span className="font-medium text-gray-800 text-sm">{s.titolo}</span>
                          {statoBadge(s.stato)}
                          <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                            s.tipo === "incasso" ? "bg-green-100 text-green-700" : s.tipo === "pagamento" ? "bg-orange-100 text-orange-700" : "bg-gray-100 text-gray-600"
                          }`}>{s.tipo}</span>
                        </div>
                        <div className="text-xs text-gray-500 flex gap-3">
                          <span>Scadenza: {fmtData(s.data_scadenza)}</span>
                          {s.importo != null && <span>{fmt(s.importo)}</span>}
                          {s.anagrafica_nome && <span>· {s.anagrafica_nome}</span>}
                          {s.giorni_alla_scadenza != null && s.stato === "aperta" && (
                            <span className={scaduta ? "text-red-600 font-medium" : s.giorni_alla_scadenza <= 7 ? "text-orange-500 font-medium" : ""}>
                              {scaduta ? `Scaduta da ${Math.abs(s.giorni_alla_scadenza)} gg` : `tra ${s.giorni_alla_scadenza} gg`}
                            </span>
                          )}
                        </div>
                      </div>
                      <div className="flex items-center gap-2 ml-4">
                        {s.stato === "aperta" && (
                          <button
                            onClick={() => handleUpdateScadenzaStato(s.id, "pagata")}
                            title="Segna come pagata"
                            className="text-green-500 hover:text-green-700 transition"
                          >
                            <CheckCircle2 size={16} />
                          </button>
                        )}
                        <button
                          onClick={() => handleDeleteScadenza(s.id)}
                          className="text-red-400 hover:text-red-600 transition"
                        >
                          <Trash2 size={15} />
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* ── TAB PIPELINE ── */}
        {tab === "pipeline" && (
          <div>
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-semibold text-gray-800 text-lg">Pipeline commerciale ({pipeline.length})</h2>
              <button
                onClick={() => setShowPipelineForm(true)}
                className="flex items-center gap-1.5 bg-pink-600 hover:bg-pink-700 text-white text-sm px-3 py-1.5 rounded-lg transition"
              >
                <Plus size={15} /> Nuova opportunità
              </button>
            </div>

            {loading ? (
              <p className="text-gray-400 text-sm">Caricamento...</p>
            ) : pipeline.length === 0 ? (
              <p className="text-gray-400 text-sm">Nessuna opportunità in pipeline.</p>
            ) : (
              <div className="space-y-2">
                {pipeline.map((op) => (
                  <div key={op.id} className="bg-white border border-gray-200 rounded-xl p-4 flex items-center justify-between">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-0.5">
                        <span className="font-medium text-gray-800 text-sm">{op.titolo}</span>
                        {faseBadge(op.fase)}
                      </div>
                      <div className="text-xs text-gray-500 flex flex-wrap gap-3">
                        {op.valore_stimato != null && <span>{fmt(op.valore_stimato)}</span>}
                        <span>Prob. {op.probabilita}%</span>
                        {op.anagrafica_nome && <span>· {op.anagrafica_nome}</span>}
                        {op.data_chiusura_prevista && <span>· Chiusura: {fmtData(op.data_chiusura_prevista)}</span>}
                      </div>
                    </div>
                    <div className="flex items-center gap-2 ml-4">
                      {/* Quick fase change */}
                      <select
                        value={op.fase}
                        onChange={(e) => handleUpdateFase(op.id, e.target.value as FasePipeline)}
                        className="text-xs border border-gray-200 rounded-lg px-2 py-1 focus:outline-none focus:ring-1 focus:ring-pink-300"
                      >
                        {FASI_PIPELINE.map((f) => (
                          <option key={f.value} value={f.value}>{f.label}</option>
                        ))}
                      </select>
                      <button
                        onClick={() => handleDeleteOpportunita(op.id)}
                        className="text-red-400 hover:text-red-600 transition"
                      >
                        <Trash2 size={15} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ── TAB STORICO PAGAMENTI ── */}
        {tab === "storico" && (
          <div>
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-semibold text-gray-800 text-lg">Storico pagamenti ({storico.length})</h2>
              <button
                onClick={() => setShowStoricoForm(true)}
                className="flex items-center gap-1.5 bg-pink-600 hover:bg-pink-700 text-white text-sm px-3 py-1.5 rounded-lg transition"
              >
                <Plus size={15} /> Registra pagamento
              </button>
            </div>

            {loading ? (
              <p className="text-gray-400 text-sm">Caricamento...</p>
            ) : storico.length === 0 ? (
              <p className="text-gray-400 text-sm">Nessun pagamento registrato.</p>
            ) : (
              <div className="space-y-2">
                {storico.map((p) => (
                  <div key={p.id} className="bg-white border border-gray-200 rounded-xl p-4 flex items-center justify-between">
                    <div>
                      <div className="flex items-center gap-2 mb-0.5">
                        <span className="font-medium text-gray-800 text-sm">{p.anagrafica_nome ?? `ID ${p.anagrafica_id}`}</span>
                        <span className="text-xs px-2 py-0.5 rounded-full bg-gray-100 text-gray-600">{p.metodo_pagamento}</span>
                        {p.giorni_ritardo > 0 && (
                          <span className="text-xs px-2 py-0.5 rounded-full bg-orange-100 text-orange-700">
                            +{p.giorni_ritardo} gg ritardo
                          </span>
                        )}
                        {p.giorni_ritardo <= 0 && (
                          <span className="text-xs px-2 py-0.5 rounded-full bg-green-100 text-green-700">puntuale</span>
                        )}
                      </div>
                      <div className="text-xs text-gray-500">
                        {fmtData(p.data_pagamento)} · {fmt(p.importo)}
                        {p.note && ` · ${p.note}`}
                      </div>
                    </div>
                    <button
                      onClick={() => handleDeleteStorico(p.id)}
                      className="text-red-400 hover:text-red-600 transition ml-4"
                    >
                      <Trash2 size={15} />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* ── Modal: nuova anagrafica ── */}
      {showAnaForm && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-semibold text-gray-800">Nuova anagrafica</h2>
              <button onClick={() => setShowAnaForm(false)}><X size={18} className="text-gray-400" /></button>
            </div>
            <div className="space-y-3">
              <div>
                <label className="text-xs font-medium text-gray-600 mb-1 block">Tipo *</label>
                <select
                  value={anaFormTipo}
                  onChange={(e) => setAnaFormTipo(e.target.value as "cliente" | "fornitore")}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-pink-300"
                >
                  <option value="cliente">Cliente</option>
                  <option value="fornitore">Fornitore</option>
                  <option value="entrambi">Entrambi</option>
                </select>
              </div>
              {(["nome", "piva", "cf", "email", "telefono", "indirizzo", "citta"] as const).map((field) => (
                <div key={field}>
                  <label className="text-xs font-medium text-gray-600 mb-1 block capitalize">
                    {field === "nome" ? "Ragione sociale / Nome *" : field === "piva" ? "P.IVA" : field === "cf" ? "Codice fiscale" : field}
                  </label>
                  <input
                    type="text"
                    value={(anaForm as unknown as Record<string, string>)[field] ?? ""}
                    onChange={(e) => setAnaForm({ ...anaForm, [field]: e.target.value })}
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-pink-300"
                  />
                </div>
              ))}
              <div>
                <label className="text-xs font-medium text-gray-600 mb-1 block">Note</label>
                <textarea
                  value={anaForm.note ?? ""}
                  onChange={(e) => setAnaForm({ ...anaForm, note: e.target.value })}
                  rows={2}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-pink-300 resize-none"
                />
              </div>
            </div>
            <div className="flex gap-3 mt-5">
              <button onClick={() => setShowAnaForm(false)} className="flex-1 border border-gray-200 text-gray-600 py-2 rounded-lg text-sm hover:bg-gray-50 transition">Annulla</button>
              <button onClick={handleCreateAnagrafica} className="flex-1 bg-pink-600 hover:bg-pink-700 text-white py-2 rounded-lg text-sm transition">Crea</button>
            </div>
          </div>
        </div>
      )}

      {/* ── Modal: nuova scadenza ── */}
      {showScadenzaForm && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-semibold text-gray-800">Nuova scadenza</h2>
              <button onClick={() => setShowScadenzaForm(false)}><X size={18} className="text-gray-400" /></button>
            </div>
            <div className="space-y-3">
              <div>
                <label className="text-xs font-medium text-gray-600 mb-1 block">Titolo *</label>
                <input type="text" value={scadenzaForm.titolo} onChange={(e) => setScadenzaForm({ ...scadenzaForm, titolo: e.target.value })} className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-pink-300" />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-medium text-gray-600 mb-1 block">Tipo</label>
                  <select value={scadenzaForm.tipo} onChange={(e) => setScadenzaForm({ ...scadenzaForm, tipo: e.target.value as ScadenzaCreate["tipo"] })} className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-pink-300">
                    <option value="incasso">Incasso</option>
                    <option value="pagamento">Pagamento</option>
                    <option value="altro">Altro</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600 mb-1 block">Data scadenza *</label>
                  <input type="date" value={scadenzaForm.data_scadenza} onChange={(e) => setScadenzaForm({ ...scadenzaForm, data_scadenza: e.target.value })} className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-pink-300" />
                </div>
              </div>
              <div>
                <label className="text-xs font-medium text-gray-600 mb-1 block">Importo (€)</label>
                <input type="number" min="0" step="0.01" value={scadenzaForm.importo ?? ""} onChange={(e) => setScadenzaForm({ ...scadenzaForm, importo: e.target.value ? parseFloat(e.target.value) : undefined })} className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-pink-300" />
              </div>
              <div>
                <label className="text-xs font-medium text-gray-600 mb-1 block">Note</label>
                <textarea value={scadenzaForm.note ?? ""} onChange={(e) => setScadenzaForm({ ...scadenzaForm, note: e.target.value })} rows={2} className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-pink-300 resize-none" />
              </div>
            </div>
            <div className="flex gap-3 mt-5">
              <button onClick={() => setShowScadenzaForm(false)} className="flex-1 border border-gray-200 text-gray-600 py-2 rounded-lg text-sm hover:bg-gray-50 transition">Annulla</button>
              <button onClick={handleCreateScadenza} className="flex-1 bg-pink-600 hover:bg-pink-700 text-white py-2 rounded-lg text-sm transition">Crea</button>
            </div>
          </div>
        </div>
      )}

      {/* ── Modal: nuova opportunità ── */}
      {showPipelineForm && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-semibold text-gray-800">Nuova opportunità</h2>
              <button onClick={() => setShowPipelineForm(false)}><X size={18} className="text-gray-400" /></button>
            </div>
            <div className="space-y-3">
              <div>
                <label className="text-xs font-medium text-gray-600 mb-1 block">Titolo *</label>
                <input type="text" value={pipelineForm.titolo} onChange={(e) => setPipelineForm({ ...pipelineForm, titolo: e.target.value })} className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-pink-300" />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-medium text-gray-600 mb-1 block">Fase</label>
                  <select value={pipelineForm.fase} onChange={(e) => setPipelineForm({ ...pipelineForm, fase: e.target.value as FasePipeline })} className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-pink-300">
                    {FASI_PIPELINE.map((f) => <option key={f.value} value={f.value}>{f.label}</option>)}
                  </select>
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600 mb-1 block">Probabilità (%)</label>
                  <input type="number" min="0" max="100" value={pipelineForm.probabilita ?? 50} onChange={(e) => setPipelineForm({ ...pipelineForm, probabilita: parseInt(e.target.value) || 0 })} className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-pink-300" />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-medium text-gray-600 mb-1 block">Valore stimato (€)</label>
                  <input type="number" min="0" step="0.01" value={pipelineForm.valore_stimato ?? ""} onChange={(e) => setPipelineForm({ ...pipelineForm, valore_stimato: e.target.value ? parseFloat(e.target.value) : undefined })} className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-pink-300" />
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600 mb-1 block">Chiusura prevista</label>
                  <input type="date" value={pipelineForm.data_chiusura_prevista ?? ""} onChange={(e) => setPipelineForm({ ...pipelineForm, data_chiusura_prevista: e.target.value || undefined })} className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-pink-300" />
                </div>
              </div>
              <div>
                <label className="text-xs font-medium text-gray-600 mb-1 block">Note</label>
                <textarea value={pipelineForm.note ?? ""} onChange={(e) => setPipelineForm({ ...pipelineForm, note: e.target.value })} rows={2} className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-pink-300 resize-none" />
              </div>
            </div>
            <div className="flex gap-3 mt-5">
              <button onClick={() => setShowPipelineForm(false)} className="flex-1 border border-gray-200 text-gray-600 py-2 rounded-lg text-sm hover:bg-gray-50 transition">Annulla</button>
              <button onClick={handleCreateOpportunita} className="flex-1 bg-pink-600 hover:bg-pink-700 text-white py-2 rounded-lg text-sm transition">Crea</button>
            </div>
          </div>
        </div>
      )}

      {/* ── Modal: registra pagamento ── */}
      {showStoricoForm && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-semibold text-gray-800">Registra pagamento</h2>
              <button onClick={() => setShowStoricoForm(false)}><X size={18} className="text-gray-400" /></button>
            </div>
            <div className="space-y-3">
              <div>
                <label className="text-xs font-medium text-gray-600 mb-1 block">Anagrafica *</label>
                <select
                  value={storicoForm.anagrafica_id}
                  onChange={(e) => setStoricoForm({ ...storicoForm, anagrafica_id: parseInt(e.target.value) || 0 })}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-pink-300"
                >
                  <option value={0}>— seleziona —</option>
                  {[...clienti, ...fornitori]
                    .filter((a, i, arr) => arr.findIndex((b) => b.id === a.id) === i)
                    .sort((a, b) => a.nome.localeCompare(b.nome))
                    .map((a) => <option key={a.id} value={a.id}>{a.nome}</option>)}
                </select>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-medium text-gray-600 mb-1 block">Data pagamento *</label>
                  <input type="date" value={storicoForm.data_pagamento} onChange={(e) => setStoricoForm({ ...storicoForm, data_pagamento: e.target.value })} className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-pink-300" />
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600 mb-1 block">Importo (€) *</label>
                  <input type="number" min="0.01" step="0.01" value={storicoForm.importo || ""} onChange={(e) => setStoricoForm({ ...storicoForm, importo: parseFloat(e.target.value) || 0 })} className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-pink-300" />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-medium text-gray-600 mb-1 block">Metodo</label>
                  <select value={storicoForm.metodo_pagamento} onChange={(e) => setStoricoForm({ ...storicoForm, metodo_pagamento: e.target.value as StoricoPagamentoCreate["metodo_pagamento"] })} className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-pink-300">
                    {["bonifico", "contanti", "assegno", "carta", "rid", "altro"].map((m) => <option key={m} value={m}>{m}</option>)}
                  </select>
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600 mb-1 block">Giorni di ritardo</label>
                  <input type="number" min="0" value={storicoForm.giorni_ritardo ?? 0} onChange={(e) => setStoricoForm({ ...storicoForm, giorni_ritardo: parseInt(e.target.value) || 0 })} className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-pink-300" />
                </div>
              </div>
              <div>
                <label className="text-xs font-medium text-gray-600 mb-1 block">Note</label>
                <input type="text" value={storicoForm.note ?? ""} onChange={(e) => setStoricoForm({ ...storicoForm, note: e.target.value })} className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-pink-300" />
              </div>
            </div>
            <div className="flex gap-3 mt-5">
              <button onClick={() => setShowStoricoForm(false)} className="flex-1 border border-gray-200 text-gray-600 py-2 rounded-lg text-sm hover:bg-gray-50 transition">Annulla</button>
              <button onClick={handleCreateStorico} className="flex-1 bg-pink-600 hover:bg-pink-700 text-white py-2 rounded-lg text-sm transition">Registra</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
