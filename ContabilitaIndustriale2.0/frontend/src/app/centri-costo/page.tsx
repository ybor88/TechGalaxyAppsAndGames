"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { ChevronLeft, Plus, Trash2, Factory, AlertCircle, CheckCircle2, X, Calculator } from "lucide-react";
import {
  centriCostoApi,
  CentroCosto, CentroCostoCreate,
  BaseRiparto, BaseRipartoCreate,
  ValoreBaseRiparto, ValoreBaseRipartoCreate,
  RipartoCostoIndiretto, RipartoCostoIndirettoCreate,
  RipartoCalcolato,
} from "@/lib/api";

const EUR = (n: number | string) =>
  new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(Number(n));

const periodoCorrente = () => new Date().toISOString().slice(0, 7);

type Tab = "centri" | "basi" | "riparti";

export default function CentriCostoPage() {
  const [tab, setTab] = useState<Tab>("centri");
  const [centri, setCentri] = useState<CentroCosto[]>([]);
  const [basi, setBasi] = useState<BaseRiparto[]>([]);
  const [valori, setValori] = useState<ValoreBaseRiparto[]>([]);
  const [riparti, setRiparti] = useState<RipartoCostoIndiretto[]>([]);
  const [errore, setErrore] = useState<string | null>(null);
  const [successo, setSuccesso] = useState<string | null>(null);

  const [formCentro, setFormCentro] = useState<CentroCostoCreate>({ codice: "", descrizione: "", tipo: "produttivo" });
  const [showFormCentro, setShowFormCentro] = useState(false);

  const [formBase, setFormBase] = useState<BaseRipartoCreate>({ descrizione: "", unita_misura: "" });
  const [showFormBase, setShowFormBase] = useState(false);
  const [formValore, setFormValore] = useState<ValoreBaseRipartoCreate>({
    base_riparto_id: 0, centro_costo_id: 0, periodo: periodoCorrente(), quantita: 0,
  });

  const [formRiparto, setFormRiparto] = useState<RipartoCostoIndirettoCreate>({
    descrizione: "", centro_costo_origine_id: 0, importo: 0, base_riparto_id: 0, periodo: periodoCorrente(),
  });
  const [showFormRiparto, setShowFormRiparto] = useState(false);
  const [calcolo, setCalcolo] = useState<RipartoCalcolato | null>(null);

  const carica = useCallback(async () => {
    try {
      const [c, b, v, r] = await Promise.all([
        centriCostoApi.listCentri(),
        centriCostoApi.listBasiRiparto(),
        centriCostoApi.listValoriBase(),
        centriCostoApi.listRiparti(),
      ]);
      setCentri(c.data);
      setBasi(b.data);
      setValori(v.data);
      setRiparti(r.data);
    } catch {
      setErrore("Impossibile caricare i dati.");
    }
  }, []);

  useEffect(() => { carica(); }, [carica]);

  const err = (e: unknown, fallback: string) =>
    (e as { response?: { data?: { detail?: string } } })?.response?.data?.detail ?? fallback;

  const submitCentro = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await centriCostoApi.createCentro(formCentro);
      setSuccesso("Centro di costo creato.");
      setFormCentro({ codice: "", descrizione: "", tipo: "produttivo" });
      setShowFormCentro(false);
      carica();
    } catch (e) { setErrore(err(e, "Errore durante il salvataggio.")); }
  };

  const eliminaCentro = async (id: number) => {
    if (!confirm("Eliminare questo centro di costo?")) return;
    try { await centriCostoApi.deleteCentro(id); carica(); } catch (e) { setErrore(err(e, "Impossibile eliminare.")); }
  };

  const submitBase = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await centriCostoApi.createBaseRiparto(formBase);
      setSuccesso("Base di riparto creata.");
      setFormBase({ descrizione: "", unita_misura: "" });
      setShowFormBase(false);
      carica();
    } catch (e) { setErrore(err(e, "Errore durante il salvataggio.")); }
  };

  const submitValore = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formValore.base_riparto_id || !formValore.centro_costo_id) {
      setErrore("Seleziona base di riparto e centro di costo.");
      return;
    }
    try {
      await centriCostoApi.createValoreBase(formValore);
      setSuccesso("Valore registrato.");
      setFormValore((f) => ({ ...f, quantita: 0 }));
      carica();
    } catch (e) { setErrore(err(e, "Errore durante il salvataggio.")); }
  };

  const eliminaValore = async (id: number) => {
    try { await centriCostoApi.deleteValoreBase(id); carica(); } catch (e) { setErrore(err(e, "Impossibile eliminare.")); }
  };

  const submitRiparto = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formRiparto.centro_costo_origine_id || !formRiparto.base_riparto_id) {
      setErrore("Seleziona centro di origine e base di riparto.");
      return;
    }
    try {
      await centriCostoApi.createRiparto(formRiparto);
      setSuccesso("Riparto creato.");
      setFormRiparto({ descrizione: "", centro_costo_origine_id: 0, importo: 0, base_riparto_id: 0, periodo: periodoCorrente() });
      setShowFormRiparto(false);
      carica();
    } catch (e) { setErrore(err(e, "Errore durante il salvataggio.")); }
  };

  const eliminaRiparto = async (id: number) => {
    if (!confirm("Eliminare questo riparto?")) return;
    try {
      await centriCostoApi.deleteRiparto(id);
      if (calcolo?.riparto_id === id) setCalcolo(null);
      carica();
    } catch (e) { setErrore(err(e, "Impossibile eliminare.")); }
  };

  const vediCalcolo = async (id: number) => {
    try {
      const res = await centriCostoApi.calcolaRiparto(id);
      setCalcolo(res.data);
    } catch (e) { setErrore(err(e, "Impossibile calcolare il riparto: inserisci prima i valori della base per i centri destinatari.")); }
  };

  return (
    <div className="min-h-screen bg-[#f8fafc]">
      <header className="bg-white border-b border-gray-200 px-6 py-4">
        <div className="max-w-7xl mx-auto flex items-center gap-4">
          <Link href="/" className="text-gray-400 hover:text-gray-600 transition-colors"><ChevronLeft className="w-5 h-5" /></Link>
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-cyan-100 flex items-center justify-center"><Factory className="w-5 h-5 text-cyan-600" /></div>
            <div>
              <h1 className="text-lg font-semibold text-gray-900">Centri di Costo</h1>
              <p className="text-xs text-gray-500">Centri produttivi/ausiliari · Basi di riparto · Ribaltamento costi indiretti</p>
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
        <div className="flex gap-2 border-b border-gray-200">
          {([
            ["centri", "Centri di costo"],
            ["basi", "Basi di riparto"],
            ["riparti", "Riparto costi indiretti"],
          ] as [Tab, string][]).map(([t, label]) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
                tab === t ? "border-cyan-600 text-cyan-700" : "border-transparent text-gray-400 hover:text-gray-600"
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        {tab === "centri" && (
          <div className="space-y-4">
            <div className="flex justify-end">
              <button onClick={() => setShowFormCentro(!showFormCentro)} className="flex items-center gap-1.5 bg-cyan-600 hover:bg-cyan-700 text-white text-sm px-3 py-1.5 rounded-lg transition-colors">
                <Plus className="w-4 h-4" /> Nuovo centro
              </button>
            </div>
            {showFormCentro && (
              <form onSubmit={submitCentro} className="bg-white border border-gray-100 rounded-xl p-5 grid grid-cols-2 gap-3">
                <input required placeholder="Codice (es. CC-05)" value={formCentro.codice}
                  onChange={(e) => setFormCentro((f) => ({ ...f, codice: e.target.value }))}
                  className="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                <select value={formCentro.tipo} onChange={(e) => setFormCentro((f) => ({ ...f, tipo: e.target.value as CentroCostoCreate["tipo"] }))}
                  className="border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white">
                  <option value="produttivo">Produttivo</option>
                  <option value="ausiliario">Ausiliario</option>
                  <option value="comune">Comune / struttura</option>
                </select>
                <input required placeholder="Descrizione" value={formCentro.descrizione}
                  onChange={(e) => setFormCentro((f) => ({ ...f, descrizione: e.target.value }))}
                  className="col-span-2 border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                <select value={formCentro.centro_padre_id ?? ""} onChange={(e) => setFormCentro((f) => ({ ...f, centro_padre_id: e.target.value ? Number(e.target.value) : undefined }))}
                  className="col-span-2 border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white">
                  <option value="">Nessun centro padre</option>
                  {centri.map((c) => <option key={c.id} value={c.id}>{c.codice} — {c.descrizione}</option>)}
                </select>
                <button type="submit" className="col-span-2 bg-cyan-600 hover:bg-cyan-700 text-white font-medium py-2 rounded-lg text-sm">Crea centro</button>
              </form>
            )}
            <div className="bg-white border border-gray-100 rounded-xl overflow-hidden">
              <table className="w-full text-sm">
                <thead><tr className="text-left text-xs text-gray-500 bg-gray-50">
                  <th className="px-4 py-2 font-medium">Codice</th><th className="px-4 py-2 font-medium">Descrizione</th>
                  <th className="px-4 py-2 font-medium">Tipo</th><th className="px-4 py-2 font-medium">Padre</th><th></th>
                </tr></thead>
                <tbody>
                  {centri.map((c) => (
                    <tr key={c.id} className="border-t border-gray-50">
                      <td className="px-4 py-2 font-medium text-gray-700">{c.codice}</td>
                      <td className="px-4 py-2 text-gray-600">{c.descrizione}</td>
                      <td className="px-4 py-2 text-gray-500 capitalize">{c.tipo}</td>
                      <td className="px-4 py-2 text-gray-400">{c.centro_padre_descrizione ?? "—"}</td>
                      <td className="px-4 py-2 text-right">
                        <button onClick={() => eliminaCentro(c.id)} className="p-1 text-gray-400 hover:text-red-600"><Trash2 className="w-3.5 h-3.5" /></button>
                      </td>
                    </tr>
                  ))}
                  {centri.length === 0 && <tr><td colSpan={5} className="text-center text-gray-400 py-8">Nessun centro di costo.</td></tr>}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {tab === "basi" && (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">Basi di riparto</h2>
                <button onClick={() => setShowFormBase(!showFormBase)} className="flex items-center gap-1 bg-cyan-600 hover:bg-cyan-700 text-white text-xs px-2.5 py-1.5 rounded-lg"><Plus className="w-3.5 h-3.5" /> Nuova</button>
              </div>
              {showFormBase && (
                <form onSubmit={submitBase} className="bg-white border border-gray-100 rounded-xl p-4 space-y-2">
                  <input required placeholder="Descrizione (es. Ore macchina)" value={formBase.descrizione}
                    onChange={(e) => setFormBase((f) => ({ ...f, descrizione: e.target.value }))}
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                  <input required placeholder="Unità di misura (es. ore, mq, kWh)" value={formBase.unita_misura}
                    onChange={(e) => setFormBase((f) => ({ ...f, unita_misura: e.target.value }))}
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                  <button type="submit" className="w-full bg-cyan-600 hover:bg-cyan-700 text-white font-medium py-2 rounded-lg text-sm">Crea</button>
                </form>
              )}
              <div className="bg-white border border-gray-100 rounded-xl divide-y divide-gray-50">
                {basi.map((b) => (
                  <div key={b.id} className="px-4 py-2.5 text-sm flex justify-between">
                    <span className="text-gray-700">{b.descrizione}</span><span className="text-gray-400">{b.unita_misura}</span>
                  </div>
                ))}
                {basi.length === 0 && <div className="text-center text-gray-400 py-8 text-sm">Nessuna base di riparto.</div>}
              </div>
            </div>

            <div className="space-y-4">
              <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">Consumi per centro/periodo</h2>
              <form onSubmit={submitValore} className="bg-white border border-gray-100 rounded-xl p-4 space-y-2">
                <select value={formValore.base_riparto_id || ""} onChange={(e) => setFormValore((f) => ({ ...f, base_riparto_id: Number(e.target.value) }))}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white" required>
                  <option value="">— base di riparto —</option>
                  {basi.map((b) => <option key={b.id} value={b.id}>{b.descrizione}</option>)}
                </select>
                <select value={formValore.centro_costo_id || ""} onChange={(e) => setFormValore((f) => ({ ...f, centro_costo_id: Number(e.target.value) }))}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white" required>
                  <option value="">— centro di costo —</option>
                  {centri.map((c) => <option key={c.id} value={c.id}>{c.codice} — {c.descrizione}</option>)}
                </select>
                <div className="grid grid-cols-2 gap-2">
                  <input required placeholder="Periodo YYYY-MM" value={formValore.periodo}
                    onChange={(e) => setFormValore((f) => ({ ...f, periodo: e.target.value }))}
                    className="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                  <input required type="number" step="0.001" min="0.001" placeholder="Quantità" value={formValore.quantita || ""}
                    onChange={(e) => setFormValore((f) => ({ ...f, quantita: Number(e.target.value) }))}
                    className="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                </div>
                <button type="submit" className="w-full bg-cyan-600 hover:bg-cyan-700 text-white font-medium py-2 rounded-lg text-sm">Registra valore</button>
              </form>
              <div className="bg-white border border-gray-100 rounded-xl overflow-hidden">
                <table className="w-full text-xs">
                  <thead><tr className="text-left text-gray-500 bg-gray-50"><th className="px-3 py-2">Centro</th><th className="px-3 py-2">Periodo</th><th className="px-3 py-2 text-right">Quantità</th><th></th></tr></thead>
                  <tbody>
                    {valori.map((v) => (
                      <tr key={v.id} className="border-t border-gray-50">
                        <td className="px-3 py-2 text-gray-700">{v.centro_costo_descrizione}</td>
                        <td className="px-3 py-2 text-gray-500">{v.periodo}</td>
                        <td className="px-3 py-2 text-right text-gray-700">{v.quantita}</td>
                        <td className="px-3 py-2 text-right"><button onClick={() => eliminaValore(v.id)} className="text-gray-400 hover:text-red-600"><Trash2 className="w-3.5 h-3.5" /></button></td>
                      </tr>
                    ))}
                    {valori.length === 0 && <tr><td colSpan={4} className="text-center text-gray-400 py-6">Nessun valore registrato.</td></tr>}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {tab === "riparti" && (
          <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
            <div className="lg:col-span-2 space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">Riparti</h2>
                <button onClick={() => setShowFormRiparto(!showFormRiparto)} className="flex items-center gap-1 bg-cyan-600 hover:bg-cyan-700 text-white text-xs px-2.5 py-1.5 rounded-lg"><Plus className="w-3.5 h-3.5" /> Nuovo</button>
              </div>
              {showFormRiparto && (
                <form onSubmit={submitRiparto} className="bg-white border border-gray-100 rounded-xl p-4 space-y-2">
                  <input required placeholder="Descrizione costo" value={formRiparto.descrizione}
                    onChange={(e) => setFormRiparto((f) => ({ ...f, descrizione: e.target.value }))}
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                  <select value={formRiparto.centro_costo_origine_id || ""} onChange={(e) => setFormRiparto((f) => ({ ...f, centro_costo_origine_id: Number(e.target.value) }))}
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white" required>
                    <option value="">— centro che sostiene il costo —</option>
                    {centri.map((c) => <option key={c.id} value={c.id}>{c.codice} — {c.descrizione}</option>)}
                  </select>
                  <select value={formRiparto.base_riparto_id || ""} onChange={(e) => setFormRiparto((f) => ({ ...f, base_riparto_id: Number(e.target.value) }))}
                    className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white" required>
                    <option value="">— base di riparto —</option>
                    {basi.map((b) => <option key={b.id} value={b.id}>{b.descrizione}</option>)}
                  </select>
                  <div className="grid grid-cols-2 gap-2">
                    <input required type="number" step="0.01" min="0.01" placeholder="Importo €" value={formRiparto.importo || ""}
                      onChange={(e) => setFormRiparto((f) => ({ ...f, importo: Number(e.target.value) }))}
                      className="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                    <input required placeholder="Periodo YYYY-MM" value={formRiparto.periodo}
                      onChange={(e) => setFormRiparto((f) => ({ ...f, periodo: e.target.value }))}
                      className="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
                  </div>
                  <button type="submit" className="w-full bg-cyan-600 hover:bg-cyan-700 text-white font-medium py-2 rounded-lg text-sm">Crea riparto</button>
                </form>
              )}
              <div className="space-y-2">
                {riparti.map((r) => (
                  <div key={r.id} className="bg-white border border-gray-100 rounded-xl px-4 py-3">
                    <div className="flex justify-between items-start">
                      <div>
                        <p className="text-sm font-medium text-gray-800">{r.descrizione}</p>
                        <p className="text-xs text-gray-400">{r.centro_costo_origine_descrizione} · {r.base_riparto_descrizione} · {r.periodo}</p>
                      </div>
                      <span className="text-sm font-semibold text-gray-700">{EUR(r.importo)}</span>
                    </div>
                    <div className="flex gap-2 mt-2">
                      <button onClick={() => vediCalcolo(r.id)} className="text-xs bg-cyan-50 text-cyan-700 hover:bg-cyan-100 px-2 py-1 rounded inline-flex items-center gap-1"><Calculator className="w-3 h-3" /> Calcola allocazione</button>
                      <button onClick={() => eliminaRiparto(r.id)} className="text-xs text-gray-400 hover:text-red-600 px-2 py-1">Elimina</button>
                    </div>
                  </div>
                ))}
                {riparti.length === 0 && <div className="text-center text-gray-400 py-8 bg-white rounded-xl border border-gray-100 text-sm">Nessun riparto registrato.</div>}
              </div>
            </div>

            <div className="lg:col-span-3">
              {calcolo ? (
                <div className="bg-white border border-gray-100 rounded-xl p-5">
                  <h3 className="font-semibold text-gray-800 mb-1">{calcolo.descrizione}</h3>
                  <p className="text-xs text-gray-400 mb-4">
                    {EUR(calcolo.importo_totale)} ripartiti su base &quot;{calcolo.base_riparto_descrizione}&quot; — periodo {calcolo.periodo}
                  </p>
                  <table className="w-full text-sm">
                    <thead><tr className="text-left text-xs text-gray-500 border-b border-gray-100">
                      <th className="pb-2">Centro destinatario</th><th className="pb-2 text-right">Quantità base</th><th className="pb-2 text-right">%</th><th className="pb-2 text-right">Importo allocato</th>
                    </tr></thead>
                    <tbody>
                      {calcolo.allocazioni.map((a) => (
                        <tr key={a.centro_costo_id} className="border-b border-gray-50">
                          <td className="py-2 text-gray-700">{a.centro_costo_descrizione}</td>
                          <td className="py-2 text-right text-gray-500">{a.quantita_base}</td>
                          <td className="py-2 text-right text-gray-500">{a.percentuale}%</td>
                          <td className="py-2 text-right font-medium text-gray-800">{EUR(a.importo_allocato)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className="flex items-center justify-center h-48 text-gray-300 text-sm bg-white border border-gray-100 rounded-xl">
                  Seleziona &quot;Calcola allocazione&quot; su un riparto per vedere la ripartizione sui centri
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
