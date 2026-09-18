"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { ChevronLeft, Plus, Trash2, Boxes, AlertCircle, CheckCircle2, X } from "lucide-react";
import { prodottiApi, Prodotto, ProdottoCreate, TipoProdotto, DistintaBaseResponse, DistintaBaseRigaCreate } from "@/lib/api";

const EUR = (n: number | string) =>
  new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(Number(n));

const TIPO_LABEL: Record<TipoProdotto, string> = {
  materia_prima: "Materia prima", semilavorato: "Semilavorato", prodotto_finito: "Prodotto finito",
};

const emptyForm = (): ProdottoCreate => ({ codice: "", descrizione: "", tipo: "materia_prima", unita_misura: "pz" });

export default function ProdottiPage() {
  const [prodotti, setProdotti] = useState<Prodotto[]>([]);
  const [filtroTipo, setFiltroTipo] = useState<TipoProdotto | "">("");
  const [selezionato, setSelezionato] = useState<Prodotto | null>(null);
  const [distinta, setDistinta] = useState<DistintaBaseResponse | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<ProdottoCreate>(emptyForm());
  const [formComponente, setFormComponente] = useState<DistintaBaseRigaCreate>({ componente_id: 0, quantita: 0 });
  const [errore, setErrore] = useState<string | null>(null);
  const [successo, setSuccesso] = useState<string | null>(null);

  const err = (e: unknown, fallback: string) =>
    (e as { response?: { data?: { detail?: string } } })?.response?.data?.detail ?? fallback;

  const carica = useCallback(async () => {
    try {
      const res = await prodottiApi.list();
      setProdotti(res.data);
    } catch { setErrore("Impossibile caricare i prodotti."); }
  }, []);

  useEffect(() => { carica(); }, [carica]);

  const apriDettaglio = async (p: Prodotto) => {
    setSelezionato(p);
    setShowForm(false);
    if (p.tipo === "materia_prima") { setDistinta(null); return; }
    try {
      const res = await prodottiApi.getDistintaBase(p.id);
      setDistinta(res.data);
    } catch { setErrore("Impossibile caricare la distinta base."); }
  };

  const submitProdotto = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.codice.trim() || !form.descrizione.trim()) { setErrore("Compila codice e descrizione."); return; }
    try {
      await prodottiApi.create(form);
      setSuccesso("Prodotto creato.");
      setForm(emptyForm());
      setShowForm(false);
      carica();
    } catch (e) { setErrore(err(e, "Errore durante il salvataggio.")); }
  };

  const eliminaProdotto = async (id: number) => {
    if (!confirm("Eliminare questo prodotto?")) return;
    try {
      await prodottiApi.delete(id);
      if (selezionato?.id === id) setSelezionato(null);
      carica();
    } catch (e) { setErrore(err(e, "Impossibile eliminare: verifica che non sia usato in commesse o distinte base.")); }
  };

  const submitComponente = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selezionato || !formComponente.componente_id || formComponente.quantita <= 0) {
      setErrore("Seleziona un componente e una quantità valida.");
      return;
    }
    try {
      await prodottiApi.aggiungiComponente(selezionato.id, formComponente);
      setSuccesso("Componente aggiunto alla distinta base.");
      setFormComponente({ componente_id: 0, quantita: 0 });
      const res = await prodottiApi.getDistintaBase(selezionato.id);
      setDistinta(res.data);
      carica();
    } catch (e) { setErrore(err(e, "Impossibile aggiungere il componente.")); }
  };

  const rimuoviComponente = async (rigaId: number) => {
    if (!selezionato) return;
    try {
      await prodottiApi.rimuoviComponente(rigaId);
      const res = await prodottiApi.getDistintaBase(selezionato.id);
      setDistinta(res.data);
      carica();
    } catch { setErrore("Impossibile rimuovere il componente."); }
  };

  const prodottiFiltrati = filtroTipo ? prodotti.filter((p) => p.tipo === filtroTipo) : prodotti;
  const possibiliComponenti = selezionato ? prodotti.filter((p) => p.id !== selezionato.id) : [];

  return (
    <div className="min-h-screen bg-[#f8fafc]">
      <header className="bg-white border-b border-gray-200 px-6 py-4">
        <div className="max-w-7xl mx-auto flex items-center gap-4">
          <Link href="/" className="text-gray-400 hover:text-gray-600 transition-colors"><ChevronLeft className="w-5 h-5" /></Link>
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-lime-100 flex items-center justify-center"><Boxes className="w-5 h-5 text-lime-600" /></div>
            <div>
              <h1 className="text-lg font-semibold text-gray-900">Prodotti e Distinta Base</h1>
              <p className="text-xs text-gray-500">Materie prime · Semilavorati · Prodotti finiti · Costo standard</p>
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
        <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
          <div className="lg:col-span-2 space-y-4">
            <div className="flex items-center justify-between gap-2">
              <select value={filtroTipo} onChange={(e) => setFiltroTipo(e.target.value as TipoProdotto | "")}
                className="border border-gray-200 rounded-lg px-2 py-1.5 text-xs bg-white">
                <option value="">Tutti i tipi</option>
                <option value="materia_prima">Materie prime</option>
                <option value="semilavorato">Semilavorati</option>
                <option value="prodotto_finito">Prodotti finiti</option>
              </select>
              <button onClick={() => { setShowForm(!showForm); setSelezionato(null); setForm(emptyForm()); }}
                className="flex items-center gap-1.5 bg-lime-600 hover:bg-lime-700 text-white text-sm px-3 py-1.5 rounded-lg transition-colors">
                <Plus className="w-4 h-4" /> Nuovo
              </button>
            </div>
            {prodottiFiltrati.length === 0 ? (
              <div className="text-center text-gray-400 py-12 bg-white rounded-xl border border-gray-100">Nessun prodotto.</div>
            ) : (
              <div className="space-y-2">
                {prodottiFiltrati.map((p) => (
                  <button key={p.id} onClick={() => apriDettaglio(p)}
                    className={`w-full text-left bg-white border rounded-xl px-4 py-3 hover:border-lime-300 transition-colors ${selezionato?.id === p.id ? "border-lime-400 ring-1 ring-lime-200" : "border-gray-100"}`}>
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-xs text-gray-400">{p.codice} · {TIPO_LABEL[p.tipo]}</span>
                      {p.sotto_scorta && <span className="text-xs bg-amber-50 text-amber-700 px-1.5 py-0.5 rounded">Sotto scorta</span>}
                    </div>
                    <p className="text-sm font-medium text-gray-800 truncate">{p.descrizione}</p>
                    <div className="flex gap-4 mt-1 text-xs text-gray-500">
                      <span>Giacenza: {p.giacenza_attuale} {p.unita_misura}</span>
                      <span className="font-medium text-gray-700">Std: {EUR(p.costo_standard_unitario_totale)}</span>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>

          <div className="lg:col-span-3">
            {showForm ? (
              <div className="bg-white border border-gray-100 rounded-xl p-5">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="font-semibold text-gray-800">Nuovo prodotto</h3>
                  <button onClick={() => setShowForm(false)}><X className="w-4 h-4 text-gray-400 hover:text-gray-600" /></button>
                </div>
                <form onSubmit={submitProdotto} className="space-y-3">
                  <div className="grid grid-cols-2 gap-3">
                    <input required placeholder="Codice" value={form.codice} onChange={(e) => setForm((f) => ({ ...f, codice: e.target.value }))}
                      className="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                    <select value={form.tipo} onChange={(e) => setForm((f) => ({ ...f, tipo: e.target.value as TipoProdotto }))}
                      className="border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white">
                      <option value="materia_prima">Materia prima</option>
                      <option value="semilavorato">Semilavorato</option>
                      <option value="prodotto_finito">Prodotto finito</option>
                    </select>
                  </div>
                  <input required placeholder="Descrizione" value={form.descrizione} onChange={(e) => setForm((f) => ({ ...f, descrizione: e.target.value }))}
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                  <div className="grid grid-cols-2 gap-3">
                    <input placeholder="Unità di misura" value={form.unita_misura} onChange={(e) => setForm((f) => ({ ...f, unita_misura: e.target.value }))}
                      className="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                    <input type="number" step="0.001" min="0" placeholder="Scorta minima" value={form.scorta_minima ?? ""}
                      onChange={(e) => setForm((f) => ({ ...f, scorta_minima: Number(e.target.value) }))}
                      className="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                  </div>
                  {form.tipo === "materia_prima" ? (
                    <input type="number" step="0.0001" min="0" placeholder="Prezzo standard di acquisto (€)" value={form.prezzo_standard ?? ""}
                      onChange={(e) => setForm((f) => ({ ...f, prezzo_standard: Number(e.target.value) }))}
                      className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                  ) : (
                    <div className="grid grid-cols-2 gap-3">
                      <input type="number" step="0.001" min="0" placeholder="Ore manodopera standard" value={form.ore_manodopera_standard ?? ""}
                        onChange={(e) => setForm((f) => ({ ...f, ore_manodopera_standard: Number(e.target.value) }))}
                        className="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                      <input type="number" step="0.01" min="0" placeholder="Costo orario std (€)" value={form.costo_orario_manodopera_standard ?? ""}
                        onChange={(e) => setForm((f) => ({ ...f, costo_orario_manodopera_standard: Number(e.target.value) }))}
                        className="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                      <input type="number" step="0.0001" min="0" placeholder="Quota indiretti std (€/unità)" value={form.costo_indiretto_standard_unitario ?? ""}
                        onChange={(e) => setForm((f) => ({ ...f, costo_indiretto_standard_unitario: Number(e.target.value) }))}
                        className="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                      {form.tipo === "prodotto_finito" && (
                        <input type="number" step="0.01" min="0" placeholder="Prezzo di vendita (€)" value={form.prezzo_vendita ?? ""}
                          onChange={(e) => setForm((f) => ({ ...f, prezzo_vendita: Number(e.target.value) }))}
                          className="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                      )}
                    </div>
                  )}
                  <button type="submit" className="w-full bg-lime-600 hover:bg-lime-700 text-white font-medium py-2.5 rounded-lg text-sm transition-colors">Crea prodotto</button>
                </form>
              </div>
            ) : selezionato ? (
              <div className="bg-white border border-gray-100 rounded-xl p-5 space-y-5">
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="font-semibold text-gray-800">{selezionato.descrizione}</h3>
                    <p className="text-xs text-gray-400">{selezionato.codice} · {TIPO_LABEL[selezionato.tipo]}</p>
                  </div>
                  <div className="flex gap-2">
                    <button onClick={() => eliminaProdotto(selezionato.id)} className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg"><Trash2 className="w-4 h-4" /></button>
                    <button onClick={() => setSelezionato(null)}><X className="w-4 h-4 text-gray-400 hover:text-gray-600" /></button>
                  </div>
                </div>

                <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-xs">
                  <div className="bg-gray-50 rounded-lg p-3"><p className="text-gray-400 mb-1">Giacenza</p><p className="font-semibold text-gray-700">{selezionato.giacenza_attuale} {selezionato.unita_misura}</p></div>
                  <div className="bg-gray-50 rounded-lg p-3"><p className="text-gray-400 mb-1">Costo medio ponderato</p><p className="font-semibold text-gray-700">{EUR(selezionato.costo_medio_ponderato)}</p></div>
                  <div className="bg-gray-50 rounded-lg p-3"><p className="text-gray-400 mb-1">Costo standard unitario</p><p className="font-semibold text-lime-700">{EUR(selezionato.costo_standard_unitario_totale)}</p></div>
                  <div className="bg-gray-50 rounded-lg p-3"><p className="text-gray-400 mb-1">Scorta minima</p><p className="font-semibold text-gray-700">{selezionato.scorta_minima}</p></div>
                </div>

                {selezionato.tipo !== "materia_prima" && distinta && (
                  <div>
                    <h4 className="text-xs font-semibold text-gray-600 uppercase tracking-wide mb-2">Distinta base</h4>
                    <table className="w-full text-sm mb-3">
                      <thead><tr className="text-left text-xs text-gray-500 border-b border-gray-100">
                        <th className="pb-2">Componente</th><th className="pb-2 text-right">Qtà</th><th className="pb-2 text-right">Costo std</th><th></th>
                      </tr></thead>
                      <tbody>
                        {distinta.righe.map((r) => (
                          <tr key={r.id} className="border-b border-gray-50">
                            <td className="py-2 text-gray-700">{r.componente_codice} — {r.componente_descrizione}</td>
                            <td className="py-2 text-right text-gray-500">{r.quantita}</td>
                            <td className="py-2 text-right text-gray-500">{EUR(r.costo_standard_componente)}</td>
                            <td className="py-2 text-right"><button onClick={() => rimuoviComponente(r.id)} className="text-gray-400 hover:text-red-600"><Trash2 className="w-3.5 h-3.5" /></button></td>
                          </tr>
                        ))}
                        {distinta.righe.length === 0 && <tr><td colSpan={4} className="text-center text-gray-400 py-4 text-xs">Nessun componente in distinta base.</td></tr>}
                      </tbody>
                      {distinta.righe.length > 0 && (
                        <tfoot><tr><td className="pt-2 font-medium text-gray-700">Costo materiale unitario</td><td></td><td className="pt-2 text-right font-semibold text-gray-800">{EUR(distinta.costo_materiale_unitario)}</td><td></td></tr></tfoot>
                      )}
                    </table>
                    <form onSubmit={submitComponente} className="flex gap-2">
                      <select value={formComponente.componente_id || ""} onChange={(e) => setFormComponente((f) => ({ ...f, componente_id: Number(e.target.value) }))}
                        className="flex-1 border border-gray-200 rounded-lg px-2 py-1.5 text-xs bg-white">
                        <option value="">— aggiungi componente —</option>
                        {possibiliComponenti.map((p) => <option key={p.id} value={p.id}>{p.codice} — {p.descrizione}</option>)}
                      </select>
                      <input type="number" step="0.0001" min="0.0001" placeholder="Qtà" value={formComponente.quantita || ""}
                        onChange={(e) => setFormComponente((f) => ({ ...f, quantita: Number(e.target.value) }))}
                        className="w-24 border border-gray-200 rounded-lg px-2 py-1.5 text-xs" />
                      <button type="submit" className="bg-lime-600 hover:bg-lime-700 text-white text-xs px-3 py-1.5 rounded-lg">Aggiungi</button>
                    </form>
                  </div>
                )}
              </div>
            ) : (
              <div className="flex items-center justify-center h-48 text-gray-300 text-sm bg-white border border-gray-100 rounded-xl">
                Seleziona un prodotto o creane uno nuovo
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
