"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { contiApi, Conto } from "@/lib/api";
import { ChevronLeft, Plus, Trash2, BookOpen } from "lucide-react";

const fmt = (n: number | string) =>
  new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(Number(n));

const TIPI_CONTO = ["attivo", "passivo", "costo", "ricavo"];

const TIPO_BADGE: Record<string, string> = {
  attivo: "bg-blue-50 text-blue-700",
  passivo: "bg-orange-50 text-orange-700",
  costo: "bg-red-50 text-red-600",
  ricavo: "bg-green-50 text-green-700",
};

interface FormState {
  codice: string;
  descrizione: string;
  tipo: string;
  saldo: string;
}

const FORM_VUOTO: FormState = { codice: "", descrizione: "", tipo: "attivo", saldo: "0" };

export default function ContiPage() {
  const [conti, setConti] = useState<Conto[]>([]);
  const [form, setForm] = useState<FormState>(FORM_VUOTO);
  const [loading, setLoading] = useState(true);
  const [salvataggio, setSalvataggio] = useState(false);
  const [errore, setErrore] = useState<string | null>(null);

  const carica = async () => {
    setLoading(true);
    try {
      const res = await contiApi.list();
      setConti(res.data);
    } catch {
      setErrore("Impossibile caricare i conti.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { carica(); }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.codice || !form.descrizione) return;
    setSalvataggio(true);
    setErrore(null);
    try {
      await contiApi.create({
        codice: form.codice,
        descrizione: form.descrizione,
        tipo: form.tipo,
        saldo: parseFloat(form.saldo) || 0,
      });
      setForm(FORM_VUOTO);
      await carica();
    } catch {
      setErrore("Errore durante il salvataggio. Verifica che il codice conto non sia già in uso.");
    } finally {
      setSalvataggio(false);
    }
  };

  const handleElimina = async (id: number) => {
    if (!confirm("Eliminare questo conto? I movimenti collegati perderanno il riferimento.")) return;
    try {
      await contiApi.delete(id);
      setConti((prev) => prev.filter((c) => c.id !== id));
    } catch {
      setErrore("Impossibile eliminare il conto.");
    }
  };

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
            <Link href="/movimenti" className="text-gray-500 hover:text-[#1e3a8a] transition-colors">Movimenti</Link>
            <Link href="/conti" className="text-[#1e3a8a] font-semibold border-b-2 border-[#1e3a8a] pb-0.5">Piano dei Conti</Link>
          </nav>
        </div>
      </header>

      <main className="flex-1 p-6 max-w-7xl mx-auto w-full space-y-6">
        {/* Breadcrumb + titolo */}
        <div>
          <Link href="/" className="inline-flex items-center gap-1 text-xs text-gray-400 hover:text-[#1e3a8a] transition-colors mb-1">
            <ChevronLeft size={14} /> Home
          </Link>
          <h1 className="text-2xl font-bold text-[#1e3a8a]">Piano dei Conti</h1>
          <p className="text-sm text-gray-400">Gestisci il piano dei conti aziendale</p>
        </div>

        {errore && (
          <div className="bg-red-50 border border-red-200 text-red-700 rounded-xl px-4 py-3 text-sm">{errore}</div>
        )}

        {/* Form inserimento */}
        <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6">
          <h2 className="text-base font-semibold text-gray-800 mb-4 flex items-center gap-2">
            <Plus size={16} className="text-[#1e3a8a]" /> Nuovo Conto
          </h2>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {/* Codice */}
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1">Codice *</label>
              <input
                type="text"
                placeholder="Es. 1001"
                value={form.codice}
                onChange={(e) => setForm((f) => ({ ...f, codice: e.target.value }))}
                required
                maxLength={20}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/20 focus:border-[#1e3a8a]"
              />
            </div>

            {/* Descrizione */}
            <div className="sm:col-span-2">
              <label className="block text-xs font-medium text-gray-500 mb-1">Descrizione *</label>
              <input
                type="text"
                placeholder="Es. Banca c/c, Vendite prodotti..."
                value={form.descrizione}
                onChange={(e) => setForm((f) => ({ ...f, descrizione: e.target.value }))}
                required
                maxLength={200}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/20 focus:border-[#1e3a8a]"
              />
            </div>

            {/* Tipo */}
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1">Tipo *</label>
              <select
                value={form.tipo}
                onChange={(e) => setForm((f) => ({ ...f, tipo: e.target.value }))}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/20 focus:border-[#1e3a8a] bg-white"
              >
                {TIPI_CONTO.map((t) => (
                  <option key={t} value={t}>{t.charAt(0).toUpperCase() + t.slice(1)}</option>
                ))}
              </select>
            </div>

            {/* Saldo iniziale */}
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1">Saldo iniziale (€)</label>
              <input
                type="number"
                step="0.01"
                placeholder="0,00"
                value={form.saldo}
                onChange={(e) => setForm((f) => ({ ...f, saldo: e.target.value }))}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#1e3a8a]/20 focus:border-[#1e3a8a]"
              />
            </div>

            {/* Submit */}
            <div className="sm:col-span-2 lg:col-span-3 flex justify-end items-end">
              <button
                type="submit"
                disabled={salvataggio}
                className="bg-[#1e3a8a] hover:bg-[#1e40af] text-white px-6 py-2 rounded-lg text-sm font-medium transition-colors disabled:opacity-60"
              >
                {salvataggio ? "Salvataggio..." : "Salva Conto"}
              </button>
            </div>
          </form>
        </div>

        {/* Tabella conti */}
        <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-base font-semibold text-gray-800 flex items-center gap-2">
              <BookOpen size={16} className="text-[#1e3a8a]" />
              Conti registrati{" "}
              <span className="text-xs text-gray-400 font-normal">({conti.length})</span>
            </h2>
          </div>

          {loading ? (
            <div className="text-center text-gray-400 py-8 text-sm">Caricamento...</div>
          ) : conti.length === 0 ? (
            <div className="text-center text-gray-400 py-12 text-sm">
              Nessun conto registrato. Usa il form sopra per creare il primo conto.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-100 text-xs text-gray-400 uppercase tracking-wide">
                    <th className="text-left py-2 pr-4">Codice</th>
                    <th className="text-left py-2 pr-4">Descrizione</th>
                    <th className="text-left py-2 pr-4">Tipo</th>
                    <th className="text-right py-2 pr-4">Saldo</th>
                    <th className="py-2"></th>
                  </tr>
                </thead>
                <tbody>
                  {conti.map((c) => (
                    <tr key={c.id} className="border-b border-gray-50 hover:bg-gray-50 transition-colors">
                      <td className="py-2.5 pr-4 font-mono text-gray-700 font-medium">{c.codice}</td>
                      <td className="py-2.5 pr-4 text-gray-800">{c.descrizione}</td>
                      <td className="py-2.5 pr-4">
                        <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${TIPO_BADGE[c.tipo] ?? "bg-gray-100 text-gray-600"}`}>
                          {c.tipo}
                        </span>
                      </td>
                      <td className="py-2.5 pr-4 text-right font-semibold text-gray-700">{fmt(c.saldo)}</td>
                      <td className="py-2.5">
                        <button
                          onClick={() => handleElimina(c.id)}
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
