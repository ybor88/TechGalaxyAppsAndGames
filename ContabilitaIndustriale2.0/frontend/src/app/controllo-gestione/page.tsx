"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { ChevronLeft, Gauge, AlertCircle, CheckCircle2, X, Scale } from "lucide-react";
import { controlloGestioneApi, ClassificazioneCosto, BreakEvenAziendale, BreakEvenProdotto } from "@/lib/api";

const EUR = (n: number | string) =>
  new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(Number(n));

export default function ControlloGestionePage() {
  const [classificazioni, setClassificazioni] = useState<ClassificazioneCosto[]>([]);
  const [breakEven, setBreakEven] = useState<BreakEvenAziendale | null>(null);
  const [breakEvenProdotti, setBreakEvenProdotti] = useState<BreakEvenProdotto[]>([]);
  const [errore, setErrore] = useState<string | null>(null);
  const [successo, setSuccesso] = useState<string | null>(null);

  const carica = useCallback(async () => {
    try {
      const [c, be, bep] = await Promise.all([
        controlloGestioneApi.listClassificazioni(),
        controlloGestioneApi.breakEven(),
        controlloGestioneApi.breakEvenProdotti(),
      ]);
      setClassificazioni(c.data);
      setBreakEven(be.data);
      setBreakEvenProdotti(bep.data);
    } catch { setErrore("Impossibile caricare i dati di controllo di gestione."); }
  }, []);

  useEffect(() => { carica(); }, [carica]);

  const classifica = async (conto_id: number, classificazione: "fisso" | "variabile") => {
    try {
      await controlloGestioneApi.setClassificazione(conto_id, classificazione);
      setSuccesso("Classificazione aggiornata.");
      carica();
    } catch { setErrore("Impossibile aggiornare la classificazione."); }
  };

  return (
    <div className="min-h-screen bg-[#f8fafc]">
      <header className="bg-white border-b border-gray-200 px-6 py-4">
        <div className="max-w-7xl mx-auto flex items-center gap-4">
          <Link href="/" className="text-gray-400 hover:text-gray-600 transition-colors"><ChevronLeft className="w-5 h-5" /></Link>
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-slate-200 flex items-center justify-center"><Gauge className="w-5 h-5 text-slate-700" /></div>
            <div>
              <h1 className="text-lg font-semibold text-gray-900">Controllo di Gestione</h1>
              <p className="text-xs text-gray-500">Direct costing · Margine di contribuzione · Punto di pareggio (break-even)</p>
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
        {breakEven && (
          <div>
            <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide mb-3">Break-even aziendale</h2>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              <div className="bg-white border border-gray-100 rounded-xl p-4"><p className="text-xs text-gray-500 mb-1">Ricavi</p><p className="text-lg font-bold text-slate-700">{EUR(breakEven.totale_ricavi)}</p></div>
              <div className="bg-white border border-gray-100 rounded-xl p-4"><p className="text-xs text-gray-500 mb-1">Costi fissi</p><p className="text-lg font-bold text-slate-700">{EUR(breakEven.totale_costi_fissi)}</p></div>
              <div className="bg-white border border-gray-100 rounded-xl p-4"><p className="text-xs text-gray-500 mb-1">Costi variabili</p><p className="text-lg font-bold text-slate-700">{EUR(breakEven.totale_costi_variabili)}</p></div>
              <div className="bg-white border border-gray-100 rounded-xl p-4"><p className="text-xs text-gray-500 mb-1">Margine di contribuzione</p><p className="text-lg font-bold text-slate-700">{EUR(breakEven.margine_di_contribuzione)} <span className="text-xs font-normal text-gray-400">({breakEven.percentuale_margine_di_contribuzione}%)</span></p></div>
            </div>
            <div className="mt-4 bg-slate-800 text-white rounded-xl p-5 flex items-center gap-3">
              <Scale className="w-6 h-6 flex-shrink-0" />
              <div>
                <p className="text-xs text-slate-300">Punto di pareggio (fatturato minimo per coprire i costi fissi)</p>
                <p className="text-xl font-bold">{breakEven.punto_di_pareggio_valore != null ? EUR(breakEven.punto_di_pareggio_valore) : "Non calcolabile (margine di contribuzione nullo o negativo)"}</p>
              </div>
            </div>
            {breakEven.conti_non_classificati > 0 && (
              <p className="text-xs text-amber-600 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2 mt-3">
                {breakEven.conti_non_classificati} conti di costo non sono ancora classificati come fissi/variabili: non entrano nel calcolo del break-even. Classificali qui sotto.
              </p>
            )}
          </div>
        )}

        {breakEvenProdotti.length > 0 && (
          <div>
            <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide mb-3">Break-even per prodotto (direct costing)</h2>
            <div className="bg-white border border-gray-100 rounded-xl overflow-hidden overflow-x-auto">
              <table className="w-full text-xs">
                <thead><tr className="text-left text-gray-500 bg-gray-50">
                  <th className="px-3 py-2">Prodotto</th><th className="px-3 py-2 text-right">Prezzo vendita</th>
                  <th className="px-3 py-2 text-right">Costo variabile</th><th className="px-3 py-2 text-right">Margine unitario</th>
                  <th className="px-3 py-2 text-right">% margine</th><th className="px-3 py-2 text-right">BEP quantità</th>
                </tr></thead>
                <tbody>
                  {breakEvenProdotti.map((p) => (
                    <tr key={p.prodotto_id} className="border-t border-gray-50">
                      <td className="px-3 py-2 text-gray-700">{p.codice} — {p.descrizione}</td>
                      <td className="px-3 py-2 text-right text-gray-600">{EUR(p.prezzo_vendita)}</td>
                      <td className="px-3 py-2 text-right text-gray-600">{EUR(p.costo_variabile_unitario)}</td>
                      <td className="px-3 py-2 text-right font-medium text-gray-800">{EUR(p.margine_di_contribuzione_unitario)}</td>
                      <td className="px-3 py-2 text-right text-gray-600">{p.percentuale_margine_di_contribuzione}%</td>
                      <td className="px-3 py-2 text-right text-gray-600">{p.punto_di_pareggio_quantita ?? "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        <div>
          <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide mb-3">Classificazione conti (fissi / variabili)</h2>
          <div className="bg-white border border-gray-100 rounded-xl overflow-hidden">
            <table className="w-full text-xs">
              <thead><tr className="text-left text-gray-500 bg-gray-50">
                <th className="px-3 py-2">Conto</th><th className="px-3 py-2">Tipo</th><th className="px-3 py-2 text-right">Classificazione</th>
              </tr></thead>
              <tbody>
                {classificazioni.map((c) => (
                  <tr key={c.conto_id} className="border-t border-gray-50">
                    <td className="px-3 py-2 text-gray-700">{c.conto_codice} — {c.conto_descrizione}</td>
                    <td className="px-3 py-2 text-gray-500 capitalize">{c.tipo_conto}</td>
                    <td className="px-3 py-2 text-right">
                      {c.tipo_conto === "ricavo" ? (
                        <span className="text-gray-400">n/d</span>
                      ) : (
                        <div className="inline-flex rounded-lg border border-gray-200 overflow-hidden">
                          <button onClick={() => classifica(c.conto_id, "fisso")}
                            className={`px-2 py-1 text-xs ${c.classificazione === "fisso" ? "bg-slate-700 text-white" : "bg-white text-gray-500 hover:bg-gray-50"}`}>Fisso</button>
                          <button onClick={() => classifica(c.conto_id, "variabile")}
                            className={`px-2 py-1 text-xs border-l border-gray-200 ${c.classificazione === "variabile" ? "bg-slate-700 text-white" : "bg-white text-gray-500 hover:bg-gray-50"}`}>Variabile</button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
                {classificazioni.length === 0 && <tr><td colSpan={3} className="text-center text-gray-400 py-8">Nessun conto di costo/ricavo trovato.</td></tr>}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}
