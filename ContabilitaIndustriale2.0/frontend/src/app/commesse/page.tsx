"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { ChevronLeft, Plus, Trash2, ClipboardList, AlertCircle, CheckCircle2, X, Lock } from "lucide-react";
import {
  commesseApi, Commessa, CommessaCreate,
  prodottiApi, Prodotto,
  centriCostoApi, CentroCosto,
  RigaCostoCommessa, RigaCostoCommessaCreate, TipoCostoCommessa,
  RiepilogoCostiCommessa,
} from "@/lib/api";

const EUR = (n: number | string) =>
  new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(Number(n));
const today = () => new Date().toISOString().split("T")[0];

const emptyForm = (): CommessaCreate => ({
  codice: "", descrizione: "", prodotto_id: 0, centro_costo_id: 0, quantita_pianificata: 0, data_apertura: today(),
});
const emptyCosto = (): RigaCostoCommessaCreate => ({ tipo: "manodopera", descrizione: "", data: today() });

const TIPO_COSTO_LABEL: Record<TipoCostoCommessa, string> = { materiale: "Materiale", manodopera: "Manodopera", indiretto: "Indiretto" };

export default function CommessePage() {
  const [commesse, setCommesse] = useState<Commessa[]>([]);
  const [prodottiFiniti, setProdottiFiniti] = useState<Prodotto[]>([]);
  const [materiePrimeESemilavorati, setMateriePrimeESemilavorati] = useState<Prodotto[]>([]);
  const [centri, setCentri] = useState<CentroCosto[]>([]);
  const [filtroStato, setFiltroStato] = useState<"" | "aperta" | "chiusa">("");
  const [selezionata, setSelezionata] = useState<Commessa | null>(null);
  const [costi, setCosti] = useState<RigaCostoCommessa[]>([]);
  const [riepilogo, setRiepilogo] = useState<RiepilogoCostiCommessa | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<CommessaCreate>(emptyForm());
  const [formCosto, setFormCosto] = useState<RigaCostoCommessaCreate>(emptyCosto());
  const [errore, setErrore] = useState<string | null>(null);
  const [successo, setSuccesso] = useState<string | null>(null);
  const [dataChiusura, setDataChiusura] = useState(today());
  const [quantitaProdotta, setQuantitaProdotta] = useState(0);

  const err = (e: unknown, fallback: string) =>
    (e as { response?: { data?: { detail?: string } } })?.response?.data?.detail ?? fallback;

  const carica = useCallback(async () => {
    try {
      const [c, pf, mp, cc] = await Promise.all([
        commesseApi.list(filtroStato || undefined),
        prodottiApi.list("prodotto_finito"),
        prodottiApi.list(),
        centriCostoApi.listCentri(true),
      ]);
      setCommesse(c.data);
      setProdottiFiniti(pf.data);
      setMateriePrimeESemilavorati(mp.data.filter((p) => p.tipo !== "prodotto_finito"));
      setCentri(cc.data);
    } catch { setErrore("Impossibile caricare le commesse."); }
  }, [filtroStato]);

  useEffect(() => { carica(); }, [carica]);

  const apriDettaglio = async (c: Commessa) => {
    setSelezionata(c);
    setShowForm(false);
    setQuantitaProdotta(Number(c.quantita_pianificata));
    try {
      const [costiRes, riepRes] = await Promise.all([commesseApi.listCosti(c.id), commesseApi.riepilogo(c.id)]);
      setCosti(costiRes.data);
      setRiepilogo(riepRes.data);
    } catch { setErrore("Impossibile caricare i costi della commessa."); }
  };

  const submitCommessa = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.codice.trim() || !form.prodotto_id || !form.centro_costo_id || form.quantita_pianificata <= 0) {
      setErrore("Compila tutti i campi obbligatori.");
      return;
    }
    try {
      await commesseApi.create(form);
      setSuccesso("Commessa creata.");
      setForm(emptyForm());
      setShowForm(false);
      carica();
    } catch (e) { setErrore(err(e, "Errore durante il salvataggio.")); }
  };

  const eliminaCommessa = async (id: number) => {
    if (!confirm("Eliminare questa commessa e tutti i suoi costi?")) return;
    try {
      await commesseApi.delete(id);
      if (selezionata?.id === id) setSelezionata(null);
      carica();
    } catch (e) { setErrore(err(e, "Impossibile eliminare la commessa.")); }
  };

  const submitCosto = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selezionata) return;
    if (!formCosto.descrizione.trim()) { setErrore("Inserisci una descrizione."); return; }
    if (formCosto.tipo === "materiale" && (!formCosto.prodotto_id || !formCosto.quantita)) { setErrore("Per un costo materiale seleziona prodotto e quantità."); return; }
    if (formCosto.tipo === "manodopera" && (!formCosto.quantita || !formCosto.costo_unitario)) { setErrore("Per un costo manodopera inserisci ore e costo orario."); return; }
    if (formCosto.tipo === "indiretto" && !formCosto.importo) { setErrore("Per un costo indiretto inserisci l'importo."); return; }
    try {
      await commesseApi.aggiungiCosto(selezionata.id, formCosto);
      setSuccesso("Costo registrato.");
      setFormCosto(emptyCosto());
      const [costiRes, riepRes] = await Promise.all([commesseApi.listCosti(selezionata.id), commesseApi.riepilogo(selezionata.id)]);
      setCosti(costiRes.data);
      setRiepilogo(riepRes.data);
      carica();
    } catch (e) { setErrore(err(e, "Errore durante la registrazione del costo.")); }
  };

  const eliminaCosto = async (rigaId: number) => {
    if (!selezionata) return;
    try {
      await commesseApi.eliminaCosto(rigaId);
      const [costiRes, riepRes] = await Promise.all([commesseApi.listCosti(selezionata.id), commesseApi.riepilogo(selezionata.id)]);
      setCosti(costiRes.data);
      setRiepilogo(riepRes.data);
      carica();
    } catch (e) { setErrore(err(e, "Impossibile eliminare questo costo.")); }
  };

  const chiudiCommessa = async () => {
    if (!selezionata) return;
    if (!confirm(`Chiudere la commessa con quantità prodotta ${quantitaProdotta}?`)) return;
    try {
      const res = await commesseApi.chiudi(selezionata.id, dataChiusura, quantitaProdotta);
      setSuccesso("Commessa chiusa.");
      setSelezionata(res.data);
      carica();
    } catch (e) { setErrore(err(e, "Impossibile chiudere la commessa.")); }
  };

  return (
    <div className="min-h-screen bg-[#f8fafc]">
      <header className="bg-white border-b border-gray-200 px-6 py-4">
        <div className="max-w-7xl mx-auto flex items-center gap-4">
          <Link href="/" className="text-gray-400 hover:text-gray-600 transition-colors"><ChevronLeft className="w-5 h-5" /></Link>
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-rose-100 flex items-center justify-center"><ClipboardList className="w-5 h-5 text-rose-600" /></div>
            <div>
              <h1 className="text-lg font-semibold text-gray-900">Commesse</h1>
              <p className="text-xs text-gray-500">Costi diretti e indiretti per commessa (job order costing)</p>
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

      <div className="max-w-7xl mx-auto px-6 py-6">
        <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
          <div className="lg:col-span-2 space-y-4">
            <div className="flex items-center justify-between gap-2">
              <select value={filtroStato} onChange={(e) => setFiltroStato(e.target.value as "" | "aperta" | "chiusa")}
                className="border border-gray-200 rounded-lg px-2 py-1.5 text-xs bg-white">
                <option value="">Tutte</option><option value="aperta">Aperte</option><option value="chiusa">Chiuse</option>
              </select>
              <button onClick={() => { setShowForm(!showForm); setSelezionata(null); setForm(emptyForm()); }}
                className="flex items-center gap-1.5 bg-rose-600 hover:bg-rose-700 text-white text-sm px-3 py-1.5 rounded-lg transition-colors">
                <Plus className="w-4 h-4" /> Nuova
              </button>
            </div>
            {commesse.length === 0 ? (
              <div className="text-center text-gray-400 py-12 bg-white rounded-xl border border-gray-100">Nessuna commessa.</div>
            ) : (
              <div className="space-y-2">
                {commesse.map((c) => (
                  <button key={c.id} onClick={() => apriDettaglio(c)}
                    className={`w-full text-left bg-white border rounded-xl px-4 py-3 hover:border-rose-300 transition-colors ${selezionata?.id === c.id ? "border-rose-400 ring-1 ring-rose-200" : "border-gray-100"}`}>
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-xs text-gray-400">{c.codice} · {c.data_apertura}</span>
                      <span className={`text-xs px-1.5 py-0.5 rounded ${c.stato === "aperta" ? "bg-blue-50 text-blue-700" : "bg-gray-100 text-gray-500"}`}>{c.stato}</span>
                    </div>
                    <p className="text-sm font-medium text-gray-800 truncate">{c.descrizione}</p>
                    <div className="flex gap-4 mt-1 text-xs text-gray-500">
                      <span>{c.prodotto_descrizione}</span>
                      <span className="font-medium text-gray-700">{EUR(c.costo_totale_consuntivo)}</span>
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
                  <h3 className="font-semibold text-gray-800">Nuova commessa</h3>
                  <button onClick={() => setShowForm(false)}><X className="w-4 h-4 text-gray-400 hover:text-gray-600" /></button>
                </div>
                <form onSubmit={submitCommessa} className="space-y-3">
                  <div className="grid grid-cols-2 gap-3">
                    <input required placeholder="Codice commessa" value={form.codice} onChange={(e) => setForm((f) => ({ ...f, codice: e.target.value }))}
                      className="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                    <input required type="date" value={form.data_apertura} onChange={(e) => setForm((f) => ({ ...f, data_apertura: e.target.value }))}
                      className="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                  </div>
                  <input required placeholder="Descrizione" value={form.descrizione} onChange={(e) => setForm((f) => ({ ...f, descrizione: e.target.value }))}
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                  <div className="grid grid-cols-2 gap-3">
                    <select value={form.prodotto_id || ""} onChange={(e) => setForm((f) => ({ ...f, prodotto_id: Number(e.target.value) }))}
                      className="border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white" required>
                      <option value="">— prodotto finito —</option>
                      {prodottiFiniti.map((p) => <option key={p.id} value={p.id}>{p.codice} — {p.descrizione}</option>)}
                    </select>
                    <select value={form.centro_costo_id || ""} onChange={(e) => setForm((f) => ({ ...f, centro_costo_id: Number(e.target.value) }))}
                      className="border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white" required>
                      <option value="">— centro di costo —</option>
                      {centri.map((c) => <option key={c.id} value={c.id}>{c.codice} — {c.descrizione}</option>)}
                    </select>
                  </div>
                  <input required type="number" step="0.001" min="0.001" placeholder="Quantità pianificata" value={form.quantita_pianificata || ""}
                    onChange={(e) => setForm((f) => ({ ...f, quantita_pianificata: Number(e.target.value) }))}
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                  <button type="submit" className="w-full bg-rose-600 hover:bg-rose-700 text-white font-medium py-2.5 rounded-lg text-sm transition-colors">Crea commessa</button>
                </form>
              </div>
            ) : selezionata ? (
              <div className="space-y-5">
                <div className="bg-white border border-gray-100 rounded-xl p-5">
                  <div className="flex items-center justify-between mb-1">
                    <h3 className="font-semibold text-gray-800">{selezionata.descrizione}</h3>
                    <div className="flex gap-2">
                      {selezionata.stato === "aperta" && (
                        <button onClick={() => eliminaCommessa(selezionata.id)} className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg"><Trash2 className="w-4 h-4" /></button>
                      )}
                      <button onClick={() => setSelezionata(null)}><X className="w-4 h-4 text-gray-400 hover:text-gray-600" /></button>
                    </div>
                  </div>
                  <p className="text-xs text-gray-400 mb-4">
                    {selezionata.codice} · {selezionata.prodotto_descrizione} · {selezionata.centro_costo_descrizione} · qtà pianificata {selezionata.quantita_pianificata}
                    {selezionata.quantita_prodotta != null && ` · prodotta ${selezionata.quantita_prodotta}`}
                  </p>

                  {riepilogo && (
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-xs mb-4">
                      <div className="bg-gray-50 rounded-lg p-3"><p className="text-gray-400 mb-1">Materiale</p><p className="font-semibold text-gray-700">{EUR(riepilogo.totale_materiale)}</p></div>
                      <div className="bg-gray-50 rounded-lg p-3"><p className="text-gray-400 mb-1">Manodopera</p><p className="font-semibold text-gray-700">{EUR(riepilogo.totale_manodopera)}</p></div>
                      <div className="bg-gray-50 rounded-lg p-3"><p className="text-gray-400 mb-1">Indiretti</p><p className="font-semibold text-gray-700">{EUR(riepilogo.totale_indiretti)}</p></div>
                      <div className="bg-rose-50 rounded-lg p-3"><p className="text-rose-400 mb-1">Totale</p><p className="font-semibold text-rose-700">{EUR(riepilogo.totale)}</p></div>
                    </div>
                  )}

                  {selezionata.stato === "aperta" ? (
                    <div className="flex items-center gap-2 border-t border-gray-100 pt-4">
                      <Lock className="w-3.5 h-3.5 text-gray-400" />
                      <input type="date" value={dataChiusura} onChange={(e) => setDataChiusura(e.target.value)} className="border border-gray-200 rounded-lg px-2 py-1.5 text-xs" />
                      <input type="number" step="0.001" min="0.001" value={quantitaProdotta} onChange={(e) => setQuantitaProdotta(Number(e.target.value))}
                        className="w-28 border border-gray-200 rounded-lg px-2 py-1.5 text-xs" placeholder="Qtà prodotta" />
                      <button onClick={chiudiCommessa} className="text-xs bg-gray-700 hover:bg-gray-800 text-white px-3 py-1.5 rounded-lg">Chiudi commessa</button>
                      <Link href={`/scostamenti?commessa=${selezionata.id}`} className="ml-auto text-xs text-rose-600 hover:underline">Analisi scostamenti →</Link>
                    </div>
                  ) : (
                    <div className="border-t border-gray-100 pt-3">
                      <Link href={`/scostamenti?commessa=${selezionata.id}`} className="text-xs text-rose-600 hover:underline">Vedi analisi scostamenti →</Link>
                    </div>
                  )}
                </div>

                <div className="bg-white border border-gray-100 rounded-xl p-5">
                  <h4 className="text-xs font-semibold text-gray-600 uppercase tracking-wide mb-3">Righe di costo</h4>
                  <table className="w-full text-xs mb-4">
                    <thead><tr className="text-left text-gray-500 border-b border-gray-100">
                      <th className="pb-2">Tipo</th><th className="pb-2">Descrizione</th><th className="pb-2">Data</th><th className="pb-2 text-right">Importo</th><th></th>
                    </tr></thead>
                    <tbody>
                      {costi.map((r) => (
                        <tr key={r.id} className="border-b border-gray-50">
                          <td className="py-2 text-gray-500 capitalize">{TIPO_COSTO_LABEL[r.tipo]}</td>
                          <td className="py-2 text-gray-700">{r.descrizione}</td>
                          <td className="py-2 text-gray-400">{r.data}</td>
                          <td className="py-2 text-right font-medium text-gray-800">{EUR(r.importo)}</td>
                          <td className="py-2 text-right">
                            {r.tipo !== "materiale" && <button onClick={() => eliminaCosto(r.id)} className="text-gray-400 hover:text-red-600"><Trash2 className="w-3.5 h-3.5" /></button>}
                          </td>
                        </tr>
                      ))}
                      {costi.length === 0 && <tr><td colSpan={5} className="text-center text-gray-400 py-6">Nessun costo registrato.</td></tr>}
                    </tbody>
                  </table>

                  {selezionata.stato === "aperta" && (
                    <form onSubmit={submitCosto} className="grid grid-cols-2 md:grid-cols-4 gap-2 border-t border-gray-100 pt-4">
                      <select value={formCosto.tipo} onChange={(e) => setFormCosto({ ...emptyCosto(), tipo: e.target.value as TipoCostoCommessa })}
                        className="border border-gray-200 rounded-lg px-2 py-1.5 text-xs bg-white">
                        <option value="manodopera">Manodopera</option>
                        <option value="materiale">Materiale</option>
                        <option value="indiretto">Indiretto</option>
                      </select>
                      <input required placeholder="Descrizione" value={formCosto.descrizione} onChange={(e) => setFormCosto((f) => ({ ...f, descrizione: e.target.value }))}
                        className="border border-gray-200 rounded-lg px-2 py-1.5 text-xs col-span-2 md:col-span-1" />
                      <input required type="date" value={formCosto.data} onChange={(e) => setFormCosto((f) => ({ ...f, data: e.target.value }))}
                        className="border border-gray-200 rounded-lg px-2 py-1.5 text-xs" />

                      {formCosto.tipo === "materiale" && (
                        <>
                          <select value={formCosto.prodotto_id || ""} onChange={(e) => setFormCosto((f) => ({ ...f, prodotto_id: Number(e.target.value) }))}
                            className="border border-gray-200 rounded-lg px-2 py-1.5 text-xs bg-white col-span-2">
                            <option value="">— materiale/semilavorato —</option>
                            {materiePrimeESemilavorati.map((p) => <option key={p.id} value={p.id}>{p.codice} — {p.descrizione} (giac. {p.giacenza_attuale})</option>)}
                          </select>
                          <input type="number" step="0.001" min="0.001" placeholder="Quantità" value={formCosto.quantita ?? ""}
                            onChange={(e) => setFormCosto((f) => ({ ...f, quantita: Number(e.target.value) }))}
                            className="border border-gray-200 rounded-lg px-2 py-1.5 text-xs" />
                        </>
                      )}
                      {formCosto.tipo === "manodopera" && (
                        <>
                          <input type="number" step="0.01" min="0.01" placeholder="Ore" value={formCosto.quantita ?? ""}
                            onChange={(e) => setFormCosto((f) => ({ ...f, quantita: Number(e.target.value) }))}
                            className="border border-gray-200 rounded-lg px-2 py-1.5 text-xs" />
                          <input type="number" step="0.01" min="0.01" placeholder="Costo orario (€)" value={formCosto.costo_unitario ?? ""}
                            onChange={(e) => setFormCosto((f) => ({ ...f, costo_unitario: Number(e.target.value) }))}
                            className="border border-gray-200 rounded-lg px-2 py-1.5 text-xs" />
                        </>
                      )}
                      {formCosto.tipo === "indiretto" && (
                        <input type="number" step="0.01" min="0.01" placeholder="Importo (€)" value={formCosto.importo ?? ""}
                          onChange={(e) => setFormCosto((f) => ({ ...f, importo: Number(e.target.value) }))}
                          className="border border-gray-200 rounded-lg px-2 py-1.5 text-xs" />
                      )}
                      <button type="submit" className="col-span-2 md:col-span-4 bg-rose-600 hover:bg-rose-700 text-white text-xs font-medium py-2 rounded-lg">Aggiungi costo</button>
                    </form>
                  )}
                </div>
              </div>
            ) : (
              <div className="flex items-center justify-center h-48 text-gray-300 text-sm bg-white border border-gray-100 rounded-xl">
                Seleziona una commessa o creane una nuova
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
