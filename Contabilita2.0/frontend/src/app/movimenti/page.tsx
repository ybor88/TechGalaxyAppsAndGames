"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { movimentiApi, contiApi, Movimento, Conto } from "@/lib/api";
import { ChevronLeft, Plus, Trash2, TrendingUp, TrendingDown } from "lucide-react";

const today = () => new Date().toISOString().split("T")[0];

const fmt = (n: number | string) =>
  new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(Number(n));

const CATEGORIE_ENTRATA = ["Vendite", "Servizi", "Incassi", "Rimborsi", "Altro"];
const CATEGORIE_USCITA = ["Fornitori", "Personale", "Affitti", "Utenze", "Marketing", "IT", "Assicurazioni", "Tasse", "Altro"];

interface FormState {
  data: string;
  tipo: "entrata" | "uscita";
  importo: string;
  descrizione: string;
  categoria: string;
  conto_id: string;
  note: string;
}

const FORM_VUOTO: FormState = {
  data: today(),
  tipo: "entrata",
  importo: "",
  descrizione: "",
  categoria: "",
  conto_id: "",
  note: "",
};

export default function MovimentiPage() {
  const [movimenti, setMovimenti] = useState<Movimento[]>([]);
  const [conti, setConti] = useState<Conto[]>([]);
  const [form, setForm] = useState<FormState>(FORM_VUOTO);
  const [loading, setLoading] = useState(true);
  const [salvataggio, setSalvataggio] = useState(false);
  const [errore, setErrore] = useState<string | null>(null);
  const [filtroTipo, setFiltroTipo] = useState<"tutti" | "entrata" | "uscita">("tutti");

  const carica = async () => {
    setLoading(true);
    try {
      const [resM, resC] = await Promise.all([movimentiApi.list(), contiApi.list()]);
      setMovimenti(resM.data);
      setConti(resC.data);
    } catch {
      setErrore("Impossibile caricare i dati.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { carica(); }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.importo || !form.descrizione || !form.data) return;
    setSalvataggio(true);
    setErrore(null);
    try {
      await movimentiApi.create({
        data: form.data,
        tipo: form.tipo,
        importo: parseFloat(form.importo),
        descrizione: form.descrizione,
        categoria: form.categoria || undefined,
        conto_id: form.conto_id ? parseInt(form.conto_id) : undefined,
        note: form.note || undefined,
      });
      setForm({ ...FORM_VUOTO, data: today() });
      await carica();
    } catch {
      setErrore("Errore durante il salvataggio del movimento.");
    } finally {
      setSalvataggio(false);
    }
  };

  const handleElimina = async (id: number) => {
    if (!confirm("Eliminare questo movimento?")) return;
    try {
      await movimentiApi.delete(id);
      setMovimenti((prev) => prev.filter((m) => m.id !== id));
    } catch {
      setErrore("Impossibile eliminare il movimento.");
    }
  };

  const categorieDisponibili = form.tipo === "entrata" ? CATEGORIE_ENTRATA : CATEGORIE_USCITA;
  const movimentiFiltrati = filtroTipo === "tutti" ? movimenti : movimenti.filter((m) => m.tipo === filtroTipo);

  return (
    <div className="min-h-screen bg-[#f8fafc] flex flex-col">
      {/* Navbar */}
      <header className="bg-white border-b border-gray-100 shadow-sm">
        <div className="max-w-7xl mx-auto px-6 py-3 flex items-center gap-4">
          <Link href="/" className="flex items-center gap-3 focus:outline-none">
            <Image src="/logo.jpeg" alt="Contabilità 2.0" width={44} height={44} className="rounded-xl object-contain" priority />
            <div>
              <span className="text-lg font-bold leading-none">
                <span className="text-[#1e3a8a]">Contabilità</span>{" "}
                <span className="text-[#f97316]">2</span>
                <span className="text-[#16a34a]">.</span>
                <span className="text-[#f59e0b]">0</span>
              </span>
              <p className="text-[10px] text-gray-400 mt-0.5">ERP aziendale 100% open source</p>
            </div>
          </Link>
          <nav className="ml-8 flex gap-4 text-sm">
            <Link href="/dashboard" className="text-gray-500 hover:text-[#1e3a8a] transition-colors">Dashboard</Link>
            <Link href="/movimenti" className="text-[#1e3a8a] font-semibold border-b-2 border-[#1e3a8a] pb-0.5">Movimenti</Link>
            <Link href="/conti" className="text-gray-500 hover:text-[#1e3a8a] transition-colors">Piano dei Conti</Link>
          </nav>
        </div>
      </header>

      <main className="flex-1 p-6 max-w-7xl mx-auto w-full space-y-6">
        {/* Breadcrumb + titolo */}
        <div>
          <Link href="/" className="inline-flex items-center gap-1 text-xs text-gray-400 hover:text-[#1e3a8a] transition-colors mb-1">
            <ChevronLeft size={14} /> Home
          </Link>
          <h1 className="text-2xl font-bold text-[#1e3a8a]">Movimenti Finanziari</h1>
          <p className="text-sm text-gray-400">Inserisci e gestisci entrate e uscite aziendali</p>
        </div>

        {errore && (
          <div className="bg-red-50 border border-red-200 text-red-700 rounded-xl px-4 py-3 text-sm">{errore}</div>
        )}

        {/* Form inserimento */}
        <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6">
          <h2 className="text-base font-semibold text-gray-800 mb-4 flex items-center gap-2">
            <Plus size={16} className="text-[#1e3a8a]" /> Nuovo Movimento
          </h2>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {/* Tipo */}
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1">Tipo *</label>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setForm((f) => ({ ...f, tipo: "entrata", categoria: "" }))}
                  className={`flex-1 py-2 rounded-lg text-sm font-medium border transition-colors ${
                    form.tipo === "entrata"
                      ? "bg-green-600 text-white border-green-600"
                      : "bg-white text-gray-500 border-gray-200 hover:border-green-400"
                  }`}
                >
                  <TrendingUp size={14} className="inline mr-1" /> Entrata
                </button>
                <button
                  type="button"
                  onClick={() => setForm((f) => ({ ...f, tipo: "uscita", categoria: "" }))}
                  className={`flex-1 py-2 rounded-lg text-sm font-medium border transition-colors ${
                    form.tipo === "uscita"
                      ? "bg-red-500 text-white border-red-500"
                      : "bg-white text-gray-500 border-gray-200 hover:border-red-400"
                  }`}
                >
                  <TrendingDown size={14} className="inline mr-1" /> Uscita
                </button>
              </div>
            </div>

            {/* Data */}
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1">Data *</label>
              <input
                type="date"
                value={form.data}
                onChange={(e) => setForm((f) => ({ ...f, data: e.target.value }))}
                required
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/20 focus:border-[#1e3a8a]"
              />
            </div>

            {/* Importo */}
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1">Importo (€) *</label>
              <input
                type="number"
                min="0.01"
                step="0.01"
                placeholder="0,00"
                value={form.importo}
                onChange={(e) => setForm((f) => ({ ...f, importo: e.target.value }))}
                required
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/20 focus:border-[#1e3a8a]"
              />
            </div>

            {/* Descrizione */}
            <div className="sm:col-span-2">
              <label className="block text-xs font-medium text-gray-500 mb-1">Descrizione *</label>
              <input
                type="text"
                placeholder="Es. Fattura cliente Rossi, Affitto ufficio..."
                value={form.descrizione}
                onChange={(e) => setForm((f) => ({ ...f, descrizione: e.target.value }))}
                required
                maxLength={500}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/20 focus:border-[#1e3a8a]"
              />
            </div>

            {/* Categoria */}
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1">Categoria</label>
              <select
                value={form.categoria}
                onChange={(e) => setForm((f) => ({ ...f, categoria: e.target.value }))}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/20 focus:border-[#1e3a8a] bg-white"
              >
                <option value="">— nessuna —</option>
                {categorieDisponibili.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </div>

            {/* Conto */}
            {conti.length > 0 && (
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1">Conto</label>
                <select
                  value={form.conto_id}
                  onChange={(e) => setForm((f) => ({ ...f, conto_id: e.target.value }))}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/20 focus:border-[#1e3a8a] bg-white"
                >
                  <option value="">— nessuno —</option>
                  {conti.map((c) => (
                    <option key={c.id} value={c.id}>{c.codice} — {c.descrizione}</option>
                  ))}
                </select>
              </div>
            )}

            {/* Note */}
            <div className="sm:col-span-2 lg:col-span-3">
              <label className="block text-xs font-medium text-gray-500 mb-1">Note</label>
              <input
                type="text"
                placeholder="Annotazioni opzionali..."
                value={form.note}
                onChange={(e) => setForm((f) => ({ ...f, note: e.target.value }))}
                maxLength={1000}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/20 focus:border-[#1e3a8a]"
              />
            </div>

            {/* Submit */}
            <div className="sm:col-span-2 lg:col-span-3 flex justify-end">
              <button
                type="submit"
                disabled={salvataggio}
                className="bg-[#1e3a8a] hover:bg-[#1e40af] text-white px-6 py-2 rounded-lg text-sm font-medium transition-colors disabled:opacity-60"
              >
                {salvataggio ? "Salvataggio..." : "Salva Movimento"}
              </button>
            </div>
          </form>
        </div>

        {/* Tabella movimenti */}
        <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-base font-semibold text-gray-800">
              Movimenti registrati{" "}
              <span className="text-xs text-gray-400 font-normal">({movimentiFiltrati.length})</span>
            </h2>
            <div className="flex gap-2 text-xs">
              {(["tutti", "entrata", "uscita"] as const).map((t) => (
                <button
                  key={t}
                  onClick={() => setFiltroTipo(t)}
                  className={`px-3 py-1 rounded-full border transition-colors ${
                    filtroTipo === t
                      ? "bg-[#1e3a8a] text-white border-[#1e3a8a]"
                      : "text-gray-500 border-gray-200 hover:border-[#1e3a8a]"
                  }`}
                >
                  {t === "tutti" ? "Tutti" : t === "entrata" ? "Entrate" : "Uscite"}
                </button>
              ))}
            </div>
          </div>

          {loading ? (
            <div className="text-center text-gray-400 py-8 text-sm">Caricamento...</div>
          ) : movimentiFiltrati.length === 0 ? (
            <div className="text-center text-gray-400 py-12 text-sm">
              Nessun movimento registrato. Usa il form sopra per inserire il primo.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-100 text-xs text-gray-400 uppercase tracking-wide">
                    <th className="text-left py-2 pr-4">Data</th>
                    <th className="text-left py-2 pr-4">Tipo</th>
                    <th className="text-right py-2 pr-4">Importo</th>
                    <th className="text-left py-2 pr-4">Descrizione</th>
                    <th className="text-left py-2 pr-4">Categoria</th>
                    <th className="py-2"></th>
                  </tr>
                </thead>
                <tbody>
                  {movimentiFiltrati.map((m) => (
                    <tr key={m.id} className="border-b border-gray-50 hover:bg-gray-50 transition-colors">
                      <td className="py-2.5 pr-4 text-gray-600 whitespace-nowrap">{m.data}</td>
                      <td className="py-2.5 pr-4">
                        <span
                          className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${
                            m.tipo === "entrata"
                              ? "bg-green-50 text-green-700"
                              : "bg-red-50 text-red-600"
                          }`}
                        >
                          {m.tipo === "entrata" ? <TrendingUp size={11} /> : <TrendingDown size={11} />}
                          {m.tipo === "entrata" ? "Entrata" : "Uscita"}
                        </span>
                      </td>
                      <td className={`py-2.5 pr-4 text-right font-semibold whitespace-nowrap ${m.tipo === "entrata" ? "text-green-700" : "text-red-600"}`}>
                        {m.tipo === "uscita" ? "−" : "+"}{fmt(m.importo)}
                      </td>
                      <td className="py-2.5 pr-4 text-gray-800 max-w-xs truncate">{m.descrizione}</td>
                      <td className="py-2.5 pr-4 text-gray-400 text-xs">{m.categoria ?? "—"}</td>
                      <td className="py-2.5">
                        <button
                          onClick={() => handleElimina(m.id)}
                          className="text-gray-300 hover:text-red-500 transition-colors"
                          title="Elimina"
                        >
                          <Trash2 size={15} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </main>

      <footer className="border-t border-gray-100 py-5">
        <div className="flex items-center justify-center gap-2">
          <Image src="/logo.jpeg" alt="Contabilità 2.0" width={22} height={22} className="rounded opacity-60" />
          <p className="text-xs text-gray-300">Contabilità 2.0 — v1.0.0 — 100% open source, 0€</p>
        </div>
      </footer>
    </div>
  );
}
