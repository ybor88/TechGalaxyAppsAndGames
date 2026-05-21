"use client";

import { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import {
  workflowApi,
  WorkflowTask,
  WorkflowSummary,
  TaskCreate,
  TaskUpdate,
  ApprovaPasso,
  TipoTask,
  StatoTask,
  PrioritaTask,
  PassoCreate,
  StatoPasso,
} from "@/lib/api";
import {
  ChevronLeft,
  GitBranch,
  Plus,
  Trash2,
  X,
  CheckCircle2,
  Clock,
  ShoppingCart,
  Bell,
  ClipboardList,
  PlayCircle,
  XCircle,
  ThumbsUp,
  ThumbsDown,
  ChevronDown,
  AlertTriangle,
  User,
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

const TIPI: { value: TipoTask; label: string; icon: React.ElementType; color: string }[] = [
  { value: "task", label: "Task", icon: ClipboardList, color: "bg-blue-100 text-blue-700" },
  { value: "approvazione", label: "Approvazione", icon: ThumbsUp, color: "bg-violet-100 text-violet-700" },
  { value: "reminder", label: "Reminder", icon: Bell, color: "bg-yellow-100 text-yellow-700" },
  { value: "acquisto", label: "Acquisto", icon: ShoppingCart, color: "bg-orange-100 text-orange-700" },
];

const PRIORITA: { value: PrioritaTask; label: string; color: string }[] = [
  { value: "bassa", label: "Bassa", color: "bg-gray-100 text-gray-600" },
  { value: "media", label: "Media", color: "bg-blue-100 text-blue-600" },
  { value: "alta", label: "Alta", color: "bg-orange-100 text-orange-700" },
  { value: "urgente", label: "Urgente", color: "bg-red-100 text-red-700" },
];

const STATI: { value: StatoTask; label: string; color: string }[] = [
  { value: "aperto", label: "Aperto", color: "bg-sky-100 text-sky-700" },
  { value: "in_corso", label: "In corso", color: "bg-blue-100 text-blue-700" },
  { value: "completato", label: "Completato", color: "bg-green-100 text-green-700" },
  { value: "approvato", label: "Approvato", color: "bg-emerald-100 text-emerald-700" },
  { value: "rifiutato", label: "Rifiutato", color: "bg-red-100 text-red-700" },
  { value: "annullato", label: "Annullato", color: "bg-gray-100 text-gray-500" },
];

type Tab = "tutti" | TipoTask;

// ── Componente principale ─────────────────────────────────────────────────────

export default function WorkflowPage() {
  const [tab, setTab] = useState<Tab>("tutti");
  const [tasks, setTasks] = useState<WorkflowTask[]>([]);
  const [summary, setSummary] = useState<WorkflowSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedTask, setSelectedTask] = useState<WorkflowTask | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [filterStato, setFilterStato] = useState<StatoTask | "">("");

  // Form
  const [form, setForm] = useState<TaskCreate & { _passi: string[] }>({
    titolo: "",
    tipo: "task",
    priorita: "media",
    _passi: [],
  });

  // Approvazione modal
  const [showApprModal, setShowApprModal] = useState(false);
  const [apprPasso, setApprPasso] = useState<{ passoId: number; approvatore: string } | null>(null);
  const [apprForm, setApprForm] = useState<ApprovaPasso>({ stato: "approvato", commento: "" });

  // ── Load ────────────────────────────────────────────────────────────────────

  const loadSummary = useCallback(async () => {
    try {
      const r = await workflowApi.summary();
      setSummary(r.data);
    } catch { /* silent */ }
  }, []);

  const loadTasks = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, string> = {};
      if (tab !== "tutti") params.tipo = tab;
      if (filterStato) params.stato = filterStato;
      const r = await workflowApi.listTasks(params as Parameters<typeof workflowApi.listTasks>[0]);
      setTasks(r.data);
    } finally {
      setLoading(false);
    }
  }, [tab, filterStato]);

  useEffect(() => { loadSummary(); }, [loadSummary]);
  useEffect(() => { loadTasks(); }, [loadTasks]);

  // ── Azioni ──────────────────────────────────────────────────────────────────

  const handleCreate = async () => {
    if (!form.titolo.trim()) return;
    setError(null);
    try {
      const passi: PassoCreate[] = form._passi
        .filter((a) => a.trim())
        .map((approvatore, i) => ({ approvatore: approvatore.trim(), ordine: i + 1 }));
      const { _passi, ...payload } = form;
      await workflowApi.createTask({ ...payload, passi });
      setShowForm(false);
      setForm({ titolo: "", tipo: "task", priorita: "media", _passi: [] });
      loadTasks();
      loadSummary();
    } catch {
      setError("Errore creazione task");
    }
  };

  const handleUpdateStato = async (id: number, stato: StatoTask) => {
    try {
      const updated = await workflowApi.updateTask(id, { stato });
      setTasks((prev) => prev.map((t) => (t.id === id ? updated.data : t)));
      if (selectedTask?.id === id) setSelectedTask(updated.data);
      loadSummary();
    } catch {
      setError("Errore aggiornamento stato");
    }
  };

  const handleDelete = async (id: number) => {
    if (!confirm("Eliminare questo task?")) return;
    try {
      await workflowApi.deleteTask(id);
      setTasks((prev) => prev.filter((t) => t.id !== id));
      if (selectedTask?.id === id) setSelectedTask(null);
      loadSummary();
    } catch {
      setError("Errore eliminazione task");
    }
  };

  const openApprModal = (taskId: number, passoId: number, approvatore: string) => {
    setApprPasso({ passoId, approvatore });
    setApprForm({ stato: "approvato", commento: "" });
    setShowApprModal(true);
  };

  const handleApprova = async () => {
    if (!selectedTask || !apprPasso) return;
    setError(null);
    try {
      const updated = await workflowApi.approvaPasso(selectedTask.id, apprPasso.passoId, apprForm);
      setSelectedTask(updated.data);
      setTasks((prev) => prev.map((t) => (t.id === updated.data.id ? updated.data : t)));
      setShowApprModal(false);
      loadSummary();
    } catch {
      setError("Errore approvazione");
    }
  };

  // ── Helpers UI ───────────────────────────────────────────────────────────────

  const tipoBadge = (tipo: string) => {
    const t = TIPI.find((x) => x.value === tipo);
    const Icon = t?.icon ?? ClipboardList;
    return (
      <span className={`inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full font-medium ${t?.color ?? "bg-gray-100 text-gray-600"}`}>
        <Icon size={11} />{t?.label ?? tipo}
      </span>
    );
  };

  const prioritaBadge = (p: string) => {
    const pr = PRIORITA.find((x) => x.value === p);
    return (
      <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${pr?.color ?? "bg-gray-100 text-gray-600"}`}>
        {pr?.label ?? p}
      </span>
    );
  };

  const statoBadge = (s: string) => {
    const st = STATI.find((x) => x.value === s);
    return (
      <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${st?.color ?? "bg-gray-100 text-gray-600"}`}>
        {st?.label ?? s}
      </span>
    );
  };

  const isActive = (stato: string) =>
    ["aperto", "in_corso"].includes(stato);

  // ── Render ────────────────────────────────────────────────────────────────────

  return (
    <div className="min-h-screen bg-[#f8fafc]">
      {/* Header */}
      <div className="bg-white border-b border-gray-200 px-6 py-4 flex items-center gap-3">
        <Link href="/" className="text-gray-400 hover:text-gray-700 transition">
          <ChevronLeft size={20} />
        </Link>
        <GitBranch size={22} className="text-yellow-500" />
        <h1 className="text-xl font-bold text-gray-800">Workflow Aziendale</h1>
      </div>

      <div className="max-w-6xl mx-auto px-6 py-6">

        {/* KPI Summary */}
        {summary && (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 mb-6">
            {[
              { label: "Task aperti", value: summary.task_aperti, color: "text-sky-600", icon: ClipboardList },
              { label: "In corso", value: summary.task_in_corso, color: "text-blue-600", icon: PlayCircle },
              { label: "Approvazioni", value: summary.approvazioni_in_attesa, color: "text-violet-600", icon: ThumbsUp },
              { label: "Reminder (7gg)", value: summary.reminder_in_scadenza, color: "text-yellow-600", icon: Bell },
              { label: "Acquisti", value: summary.acquisti_aperti, color: "text-orange-500", icon: ShoppingCart },
              { label: "Scaduti", value: summary.task_scaduti, color: "text-red-500", icon: AlertTriangle },
            ].map(({ label, value, color, icon: Icon }) => (
              <div key={label} className="bg-white border border-gray-200 rounded-xl p-3 text-center">
                <Icon size={18} className={`${color} mx-auto mb-1`} />
                <p className={`font-bold text-2xl ${color}`}>{value}</p>
                <p className="text-xs text-gray-500">{label}</p>
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

        {/* Toolbar */}
        <div className="flex flex-wrap items-center gap-3 mb-5">
          {/* Tabs tipo */}
          <div className="flex gap-1 bg-gray-100 rounded-xl p-1">
            {([
              { id: "tutti", label: "Tutti" },
              ...TIPI.map((t) => ({ id: t.value, label: t.label })),
            ] as { id: Tab; label: string }[]).map(({ id, label }) => (
              <button
                key={id}
                onClick={() => setTab(id)}
                className={`px-3 py-1.5 rounded-lg text-sm font-medium transition ${
                  tab === id ? "bg-white shadow text-yellow-600" : "text-gray-500 hover:text-gray-700"
                }`}
              >
                {label}
              </button>
            ))}
          </div>

          {/* Filtro stato */}
          <select
            value={filterStato}
            onChange={(e) => setFilterStato(e.target.value as StatoTask | "")}
            className="border border-gray-200 rounded-lg px-3 py-1.5 text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-yellow-300"
          >
            <option value="">Tutti gli stati</option>
            {STATI.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>

          <button
            onClick={() => setShowForm(true)}
            className="ml-auto flex items-center gap-1.5 bg-yellow-500 hover:bg-yellow-600 text-white text-sm px-4 py-1.5 rounded-lg transition font-medium"
          >
            <Plus size={15} /> Nuovo
          </button>
        </div>

        {/* ── Lista task + dettaglio ── */}
        <div className="flex gap-6">
          {/* Lista */}
          <div className="flex-1 min-w-0">
            {loading ? (
              <p className="text-gray-400 text-sm">Caricamento...</p>
            ) : tasks.length === 0 ? (
              <p className="text-gray-400 text-sm">Nessun elemento trovato.</p>
            ) : (
              <div className="space-y-2">
                {tasks.map((task) => {
                  const scaduto =
                    task.giorni_alla_scadenza != null &&
                    task.giorni_alla_scadenza < 0 &&
                    isActive(task.stato);
                  const urgente =
                    task.priorita === "urgente" && isActive(task.stato);

                  return (
                    <div
                      key={task.id}
                      onClick={() => setSelectedTask(task)}
                      className={`cursor-pointer bg-white border rounded-xl p-4 transition ${
                        selectedTask?.id === task.id
                          ? "border-yellow-400 shadow-sm"
                          : scaduto || urgente
                          ? "border-red-200 hover:border-red-300"
                          : "border-gray-200 hover:border-yellow-300"
                      }`}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="flex-1 min-w-0">
                          <div className="flex flex-wrap items-center gap-1.5 mb-1">
                            {tipoBadge(task.tipo)}
                            {statoBadge(task.stato)}
                            {prioritaBadge(task.priorita)}
                          </div>
                          <p className="font-medium text-gray-800 text-sm truncate">{task.titolo}</p>
                          <div className="flex flex-wrap gap-3 text-xs text-gray-500 mt-1">
                            {task.assegnato_a && (
                              <span className="flex items-center gap-1">
                                <User size={11} />{task.assegnato_a}
                              </span>
                            )}
                            {task.data_scadenza && (
                              <span className={`flex items-center gap-1 ${scaduto ? "text-red-600 font-medium" : task.giorni_alla_scadenza != null && task.giorni_alla_scadenza <= 3 && isActive(task.stato) ? "text-orange-500 font-medium" : ""}`}>
                                <Clock size={11} />
                                {fmtData(task.data_scadenza)}
                                {isActive(task.stato) && task.giorni_alla_scadenza != null && (
                                  <span>
                                    {scaduto
                                      ? ` (scaduto da ${Math.abs(task.giorni_alla_scadenza)} gg)`
                                      : ` (tra ${task.giorni_alla_scadenza} gg)`}
                                  </span>
                                )}
                              </span>
                            )}
                            {task.importo_stimato != null && (
                              <span>{fmt(task.importo_stimato)}</span>
                            )}
                            {task.tipo === "approvazione" && task.passi.length > 0 && (
                              <span>Passo {task.passo_corrente ?? "—"}/{task.passi.length}</span>
                            )}
                          </div>
                        </div>
                        <button
                          onClick={(e) => { e.stopPropagation(); handleDelete(task.id); }}
                          className="text-red-300 hover:text-red-500 transition flex-shrink-0"
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Dettaglio task */}
          {selectedTask && (
            <div className="w-80 flex-shrink-0">
              <div className="bg-white border border-gray-200 rounded-2xl p-5 sticky top-4">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="font-semibold text-gray-800 text-sm truncate pr-2">{selectedTask.titolo}</h3>
                  <button onClick={() => setSelectedTask(null)} className="text-gray-400 hover:text-gray-600 flex-shrink-0">
                    <X size={16} />
                  </button>
                </div>

                <div className="flex flex-wrap gap-1.5 mb-3">
                  {tipoBadge(selectedTask.tipo)}
                  {statoBadge(selectedTask.stato)}
                  {prioritaBadge(selectedTask.priorita)}
                </div>

                {selectedTask.descrizione && (
                  <p className="text-xs text-gray-500 mb-3">{selectedTask.descrizione}</p>
                )}

                <div className="text-xs text-gray-600 space-y-1 mb-4">
                  {selectedTask.assegnato_a && <p><span className="font-medium">Assegnato a:</span> {selectedTask.assegnato_a}</p>}
                  {selectedTask.creato_da && <p><span className="font-medium">Creato da:</span> {selectedTask.creato_da}</p>}
                  {selectedTask.data_scadenza && <p><span className="font-medium">Scadenza:</span> {fmtData(selectedTask.data_scadenza)}</p>}
                  {selectedTask.data_completamento && <p><span className="font-medium">Completato:</span> {fmtData(selectedTask.data_completamento)}</p>}
                  {selectedTask.anagrafica_nome && <p><span className="font-medium">Fornitore:</span> {selectedTask.anagrafica_nome}</p>}
                  {selectedTask.importo_stimato != null && <p><span className="font-medium">Importo stimato:</span> {fmt(selectedTask.importo_stimato)}</p>}
                  {selectedTask.importo_approvato != null && <p><span className="font-medium">Importo approvato:</span> {fmt(selectedTask.importo_approvato)}</p>}
                  {selectedTask.note && <p className="italic text-gray-400">{selectedTask.note}</p>}
                </div>

                {/* Azioni rapide stato */}
                {isActive(selectedTask.stato) && (
                  <div className="flex flex-wrap gap-1.5 mb-4">
                    {selectedTask.stato === "aperto" && (
                      <button
                        onClick={() => handleUpdateStato(selectedTask.id, "in_corso")}
                        className="flex items-center gap-1 text-xs bg-blue-50 text-blue-700 border border-blue-200 px-2 py-1 rounded-lg hover:bg-blue-100 transition"
                      >
                        <PlayCircle size={12} /> Inizia
                      </button>
                    )}
                    <button
                      onClick={() => handleUpdateStato(selectedTask.id, "completato")}
                      className="flex items-center gap-1 text-xs bg-green-50 text-green-700 border border-green-200 px-2 py-1 rounded-lg hover:bg-green-100 transition"
                    >
                      <CheckCircle2 size={12} /> Completa
                    </button>
                    <button
                      onClick={() => handleUpdateStato(selectedTask.id, "annullato")}
                      className="flex items-center gap-1 text-xs bg-gray-50 text-gray-600 border border-gray-200 px-2 py-1 rounded-lg hover:bg-gray-100 transition"
                    >
                      <XCircle size={12} /> Annulla
                    </button>
                  </div>
                )}

                {/* Passi approvazione */}
                {selectedTask.tipo === "approvazione" && selectedTask.passi.length > 0 && (
                  <div>
                    <p className="text-xs font-semibold text-gray-700 mb-2">Flusso approvazione</p>
                    <div className="space-y-2">
                      {selectedTask.passi.map((passo) => (
                        <div
                          key={passo.id}
                          className={`border rounded-xl p-2.5 text-xs ${
                            passo.stato === "approvato"
                              ? "border-green-200 bg-green-50"
                              : passo.stato === "rifiutato"
                              ? "border-red-200 bg-red-50"
                              : "border-gray-200 bg-gray-50"
                          }`}
                        >
                          <div className="flex items-center justify-between">
                            <span className="font-medium text-gray-700">
                              {passo.ordine}. {passo.approvatore}
                            </span>
                            <span className={`px-1.5 py-0.5 rounded-full font-medium text-xs ${
                              passo.stato === "approvato" ? "bg-green-100 text-green-700" :
                              passo.stato === "rifiutato" ? "bg-red-100 text-red-700" :
                              "bg-yellow-100 text-yellow-700"
                            }`}>
                              {passo.stato === "in_attesa" ? "In attesa" : passo.stato === "approvato" ? "Approvato" : "Rifiutato"}
                            </span>
                          </div>
                          {passo.commento && (
                            <p className="text-gray-500 italic mt-1">{passo.commento}</p>
                          )}
                          {passo.stato === "in_attesa" && isActive(selectedTask.stato) && (
                            <button
                              onClick={() => openApprModal(selectedTask.id, passo.id, passo.approvatore)}
                              className="mt-1.5 text-xs text-violet-600 hover:text-violet-800 underline"
                            >
                              Elabora →
                            </button>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* ── Modal: nuovo task ── */}
      {showForm && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-semibold text-gray-800">Nuovo elemento</h2>
              <button onClick={() => setShowForm(false)}><X size={18} className="text-gray-400" /></button>
            </div>

            <div className="space-y-3">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-medium text-gray-600 mb-1 block">Tipo *</label>
                  <select
                    value={form.tipo}
                    onChange={(e) => setForm({ ...form, tipo: e.target.value as TipoTask })}
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-300"
                  >
                    {TIPI.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                  </select>
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600 mb-1 block">Priorità</label>
                  <select
                    value={form.priorita}
                    onChange={(e) => setForm({ ...form, priorita: e.target.value as PrioritaTask })}
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-300"
                  >
                    {PRIORITA.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}
                  </select>
                </div>
              </div>

              <div>
                <label className="text-xs font-medium text-gray-600 mb-1 block">Titolo *</label>
                <input
                  type="text"
                  value={form.titolo}
                  onChange={(e) => setForm({ ...form, titolo: e.target.value })}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-300"
                />
              </div>

              <div>
                <label className="text-xs font-medium text-gray-600 mb-1 block">Descrizione</label>
                <textarea
                  value={form.descrizione ?? ""}
                  onChange={(e) => setForm({ ...form, descrizione: e.target.value })}
                  rows={2}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-300 resize-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-medium text-gray-600 mb-1 block">Assegnato a</label>
                  <input
                    type="text"
                    value={form.assegnato_a ?? ""}
                    onChange={(e) => setForm({ ...form, assegnato_a: e.target.value })}
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-300"
                  />
                </div>
                <div>
                  <label className="text-xs font-medium text-gray-600 mb-1 block">Creato da</label>
                  <input
                    type="text"
                    value={form.creato_da ?? ""}
                    onChange={(e) => setForm({ ...form, creato_da: e.target.value })}
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-300"
                  />
                </div>
              </div>

              <div>
                <label className="text-xs font-medium text-gray-600 mb-1 block">Data scadenza</label>
                <input
                  type="date"
                  value={form.data_scadenza ?? ""}
                  onChange={(e) => setForm({ ...form, data_scadenza: e.target.value || undefined })}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-300"
                />
              </div>

              {form.tipo === "acquisto" && (
                <div>
                  <label className="text-xs font-medium text-gray-600 mb-1 block">Importo stimato (€)</label>
                  <input
                    type="number"
                    min="0"
                    step="0.01"
                    value={form.importo_stimato ?? ""}
                    onChange={(e) => setForm({ ...form, importo_stimato: e.target.value ? parseFloat(e.target.value) : undefined })}
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-300"
                  />
                </div>
              )}

              {form.tipo === "approvazione" && (
                <div>
                  <label className="text-xs font-medium text-gray-600 mb-2 block">
                    Approvatori (in ordine)
                  </label>
                  <div className="space-y-2">
                    {form._passi.map((approvatore, i) => (
                      <div key={i} className="flex gap-2 items-center">
                        <span className="text-xs text-gray-400 w-5">{i + 1}.</span>
                        <input
                          type="text"
                          value={approvatore}
                          onChange={(e) => {
                            const nuovi = [...form._passi];
                            nuovi[i] = e.target.value;
                            setForm({ ...form, _passi: nuovi });
                          }}
                          placeholder={`Approvatore ${i + 1}`}
                          className="flex-1 border border-gray-200 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-300"
                        />
                        <button
                          onClick={() => setForm({ ...form, _passi: form._passi.filter((_, j) => j !== i) })}
                          className="text-red-400 hover:text-red-600"
                        >
                          <X size={14} />
                        </button>
                      </div>
                    ))}
                    <button
                      onClick={() => setForm({ ...form, _passi: [...form._passi, ""] })}
                      className="text-xs text-yellow-600 hover:text-yellow-800 flex items-center gap-1"
                    >
                      <Plus size={12} /> Aggiungi approvatore
                    </button>
                  </div>
                </div>
              )}

              <div>
                <label className="text-xs font-medium text-gray-600 mb-1 block">Note</label>
                <input
                  type="text"
                  value={form.note ?? ""}
                  onChange={(e) => setForm({ ...form, note: e.target.value })}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-300"
                />
              </div>
            </div>

            <div className="flex gap-3 mt-5">
              <button onClick={() => setShowForm(false)} className="flex-1 border border-gray-200 text-gray-600 py-2 rounded-lg text-sm hover:bg-gray-50 transition">Annulla</button>
              <button onClick={handleCreate} className="flex-1 bg-yellow-500 hover:bg-yellow-600 text-white py-2 rounded-lg text-sm font-medium transition">Crea</button>
            </div>
          </div>
        </div>
      )}

      {/* ── Modal: approvazione passo ── */}
      {showApprModal && apprPasso && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-sm p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-semibold text-gray-800">Elabora approvazione</h2>
              <button onClick={() => setShowApprModal(false)}><X size={18} className="text-gray-400" /></button>
            </div>
            <p className="text-sm text-gray-600 mb-4">
              Approvatore: <span className="font-medium">{apprPasso.approvatore}</span>
            </p>
            <div className="space-y-3">
              <div>
                <label className="text-xs font-medium text-gray-600 mb-1 block">Decisione</label>
                <div className="flex gap-3">
                  <button
                    onClick={() => setApprForm({ ...apprForm, stato: "approvato" })}
                    className={`flex-1 flex items-center justify-center gap-2 py-2 rounded-lg border text-sm font-medium transition ${
                      apprForm.stato === "approvato"
                        ? "bg-green-600 text-white border-green-600"
                        : "border-gray-200 text-gray-600 hover:bg-gray-50"
                    }`}
                  >
                    <ThumbsUp size={14} /> Approva
                  </button>
                  <button
                    onClick={() => setApprForm({ ...apprForm, stato: "rifiutato" })}
                    className={`flex-1 flex items-center justify-center gap-2 py-2 rounded-lg border text-sm font-medium transition ${
                      apprForm.stato === "rifiutato"
                        ? "bg-red-600 text-white border-red-600"
                        : "border-gray-200 text-gray-600 hover:bg-gray-50"
                    }`}
                  >
                    <ThumbsDown size={14} /> Rifiuta
                  </button>
                </div>
              </div>
              <div>
                <label className="text-xs font-medium text-gray-600 mb-1 block">Commento (opzionale)</label>
                <textarea
                  value={apprForm.commento ?? ""}
                  onChange={(e) => setApprForm({ ...apprForm, commento: e.target.value })}
                  rows={2}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-yellow-300 resize-none"
                />
              </div>
            </div>
            <div className="flex gap-3 mt-5">
              <button onClick={() => setShowApprModal(false)} className="flex-1 border border-gray-200 text-gray-600 py-2 rounded-lg text-sm hover:bg-gray-50 transition">Annulla</button>
              <button onClick={handleApprova} className="flex-1 bg-yellow-500 hover:bg-yellow-600 text-white py-2 rounded-lg text-sm font-medium transition">Conferma</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
