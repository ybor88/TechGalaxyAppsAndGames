"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { ChevronLeft, Plus, Warehouse, AlertCircle, CheckCircle2, X, ArrowDownCircle, ArrowUpCircle } from "lucide-react";
import { prodottiApi, Prodotto, GiacenzaMagazzino, MovimentoMagazzino, MovimentoMagazzinoCreate } from "@/lib/api";

const EUR = (n: number | string) =>
  new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(Number(n));
const today = () => new Date().toISOString().split("T")[0];

const emptyForm = (): MovimentoMagazzinoCreate => ({ prodotto_id: 0, data: today(), tipo: "carico", quantita: 0, causale: "" });

export default function MagazzinoPage() {
  const [giacenze, setGiacenze] = useState<GiacenzaMagazzino[]>([]);
  const [prodotti, setProdotti] = useState<Prodotto[]>([]);
  const [movimenti, setMovimenti] = useState<MovimentoMagazzino[]>([]);
  const [soloSottoScorta, setSoloSottoScorta] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<MovimentoMagazzinoCreate>(emptyForm());
  const [errore, setErrore] = useState<string | null>(null);
  const [successo, setSuccesso] = useState<string | null>(null);

  const err = (e: unknown, fallback: string) =>
    (e as { response?: { data?: { detail?: string } } })?.response?.data?.detail ?? fallback;

  const carica = useCallback(async () => {
    try {
      const [g, p, m] = await Promise.all([
        prodottiApi.getGiacenze(soloSottoScorta),
        prodottiApi.list(),
        prodottiApi.listMovimenti(),
      ]);
      setGiacenze(g.data);
      setProdotti(p.data);
      setMovimenti(m.data);
    } catch { setErrore("Impossibile caricare i dati di magazzino."); }
  }, [soloSottoScorta]);

  useEffect(() => { carica(); }, [carica]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.prodotto_id || form.quantita <= 0 || !form.causale.trim()) { setErrore("Compila tutti i campi obbligatori."); return; }
    if (form.tipo === "carico" && (!form.costo_unitario || form.costo_unitario <= 0)) { setErrore("Il costo unitario è obbligatorio per un carico."); return; }
    try {
      await prodottiApi.registraMovimento(form);
      setSuccesso(`${form.tipo === "carico" ? "Carico" : "Scarico"} registrato.`);
      setForm(emptyForm());
      setShowForm(false);
      carica();
    } catch (e) { setErrore(err(e, "Errore durante la registrazione del movimento.")); }
  };

  const valoreMagazzino = giacenze.reduce((s, g) => s + Number(g.valore_giacenza), 0);
  const sottoScorta = giacenze.filter((g) => g.sotto_scorta).length;

  return (
    <div className="min-h-screen bg-[#f8fafc]">
      <header className="bg-white border-b border-gray-200 px-6 py-4">
        <div className="max-w-7xl mx-auto flex items-center gap-4">
          <Link href="/" className="text-gray-400 hover:text-gray-600 transition-colors"><ChevronLeft className="w-5 h-5" /></Link>
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-sky-100 flex items-center justify-center"><Warehouse className="w-5 h-5 text-sky-600" /></div>
            <div>
              <h1 className="text-lg font-semibold text-gray-900">Magazzino Industriale</h1>
              <p className="text-xs text-gray-500">Giacenze e movimenti · Valorizzazione a costo medio ponderato</p>
            </div>
          </div>
        </div>
      </header>

      {errore && (
        <div className="max-w-7xl mx-auto px-6 pt-4">
          <div className="flex items-center gap-2 bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-sm">
            <AlertCircle className="w-4 h-4 flex-shrink-0" /><span className="flex-1">{errore}</span>
            <button onClick={() => setErrore(null)}><X className="w-4 h-4" /></button>
          </div>
        </div>
      )}
      {successo && (
        <div className="max-w-7xl mx-auto px-6 pt-4">
          <div className="flex items-center gap-2 bg-green-50 border border-green-200 text-green-700 rounded-lg px-4 py-3 text-sm">
            <CheckCircle2 className="w-4 h-4 flex-shrink-0" /><span className="flex-1">{successo}</span>
            <button onClick={() => setSuccesso(null)}><X className="w-4 h-4" /></button>
          </div>
        </div>
      )}

      <div className="max-w-7xl mx-auto px-6 py-6 space-y-6">
        <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
          <div className="bg-white border border-gray-100 rounded-xl p-4"><p className="text-xs text-gray-500 mb-1">Valore di magazzino</p><p className="text-lg font-bold text-sky-600">{EUR(valoreMagazzino)}</p></div>
          <div className="bg-white border border-gray-100 rounded-xl p-4"><p className="text-xs text-gray-500 mb-1">Prodotti a magazzino</p><p className="text-lg font-bold text-sky-600">{giacenze.length}</p></div>
          <div className="bg-white border border-gray-100 rounded-xl p-4"><p className="text-xs text-gray-500 mb-1">Sotto scorta minima</p><p className="text-lg font-bold text-amber-600">{sottoScorta}</p></div>
        </div>

        <div className="flex items-center justify-between">
          <label className="flex items-center gap-2 text-xs text-gray-600">
            <input type="checkbox" checked={soloSottoScorta} onChange={(e) => setSoloSottoScorta(e.target.checked)} /> Mostra solo sotto scorta
          </label>
          <button onClick={() => setShowForm(!showForm)} className="flex items-center gap-1.5 bg-sky-600 hover:bg-sky-700 text-white text-sm px-3 py-1.5 rounded-lg transition-colors">
            <Plus className="w-4 h-4" /> Nuovo movimento
          </button>
        </div>

        {showForm && (
          <form onSubmit={submit} className="bg-white border border-gray-100 rounded-xl p-5 grid grid-cols-2 md:grid-cols-3 gap-3">
            <select value={form.prodotto_id || ""} onChange={(e) => setForm((f) => ({ ...f, prodotto_id: Number(e.target.value) }))}
              className="col-span-2 border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white" required>
              <option value="">— seleziona prodotto —</option>
              {prodotti.map((p) => <option key={p.id} value={p.id}>{p.codice} — {p.descrizione} (giac. {p.giacenza_attuale})</option>)}
            </select>
            <select value={form.tipo} onChange={(e) => setForm((f) => ({ ...f, tipo: e.target.value as "carico" | "scarico" }))}
              className="border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white">
              <option value="carico">Carico</option>
              <option value="scarico">Scarico</option>
            </select>
            <input required type="date" value={form.data} onChange={(e) => setForm((f) => ({ ...f, data: e.target.value }))}
              className="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
            <input required type="number" step="0.001" min="0.001" placeholder="Quantità" value={form.quantita || ""}
              onChange={(e) => setForm((f) => ({ ...f, quantita: Number(e.target.value) }))}
              className="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
            {form.tipo === "carico" && (
              <input required type="number" step="0.0001" min="0.0001" placeholder="Costo unitario (€)" value={form.costo_unitario ?? ""}
                onChange={(e) => setForm((f) => ({ ...f, costo_unitario: Number(e.target.value) }))}
                className="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
            )}
            <input required placeholder="Causale" value={form.causale} onChange={(e) => setForm((f) => ({ ...f, causale: e.target.value }))}
              className="col-span-2 md:col-span-3 border border-gray-200 rounded-lg px-3 py-2 text-sm" />
            <button type="submit" className="col-span-2 md:col-span-3 bg-sky-600 hover:bg-sky-700 text-white font-medium py-2 rounded-lg text-sm">Registra movimento</button>
          </form>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div>
            <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide mb-3">Giacenze</h2>
            <div className="bg-white border border-gray-100 rounded-xl overflow-hidden">
              <table className="w-full text-xs">
                <thead><tr className="text-left text-gray-500 bg-gray-50">
                  <th className="px-3 py-2">Prodotto</th><th className="px-3 py-2 text-right">Giacenza</th><th className="px-3 py-2 text-right">Costo medio</th><th className="px-3 py-2 text-right">Valore</th>
                </tr></thead>
                <tbody>
                  {giacenze.map((g) => (
                    <tr key={g.prodotto_id} className={`border-t border-gray-50 ${g.sotto_scorta ? "bg-amber-50/50" : ""}`}>
                      <td className="px-3 py-2 text-gray-700">{g.codice} — {g.descrizione}</td>
                      <td className="px-3 py-2 text-right text-gray-600">{g.giacenza_attuale} {g.unita_misura}</td>
                      <td className="px-3 py-2 text-right text-gray-500">{EUR(g.costo_medio_ponderato)}</td>
                      <td className="px-3 py-2 text-right font-medium text-gray-800">{EUR(g.valore_giacenza)}</td>
                    </tr>
                  ))}
                  {giacenze.length === 0 && <tr><td colSpan={4} className="text-center text-gray-400 py-8">Nessuna giacenza.</td></tr>}
                </tbody>
              </table>
            </div>
          </div>

          <div>
            <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide mb-3">Movimenti recenti</h2>
            <div className="bg-white border border-gray-100 rounded-xl divide-y divide-gray-50 max-h-[480px] overflow-y-auto">
              {movimenti.map((m) => (
                <div key={m.id} className="px-4 py-2.5 flex items-center gap-3 text-xs">
                  {m.tipo === "carico" ? <ArrowDownCircle className="w-4 h-4 text-green-500 flex-shrink-0" /> : <ArrowUpCircle className="w-4 h-4 text-red-500 flex-shrink-0" />}
                  <div className="flex-1 min-w-0">
                    <p className="text-gray-700 truncate">{m.prodotto_codice} — {m.causale}</p>
                    <p className="text-gray-400">{m.data} · {m.quantita} × {EUR(m.costo_unitario)}</p>
                  </div>
                  <span className={`font-medium ${m.tipo === "carico" ? "text-green-600" : "text-red-600"}`}>
                    {m.tipo === "carico" ? "+" : "-"}{EUR(m.importo)}
                  </span>
                </div>
              ))}
              {movimenti.length === 0 && <div className="text-center text-gray-400 py-8 text-xs">Nessun movimento.</div>}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
