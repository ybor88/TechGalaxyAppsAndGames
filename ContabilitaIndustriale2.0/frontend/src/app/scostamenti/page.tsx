"use client";

import { Suspense, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ChevronLeft, GitCompare, AlertCircle, X, TrendingUp, TrendingDown } from "lucide-react";
import { commesseApi, Commessa, ScostamentiCommessaResponse } from "@/lib/api";

const EUR = (n: number | string) =>
  new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" }).format(Number(n));

const CATEGORIA_LABEL: Record<string, string> = { materiale: "Materiale", manodopera: "Manodopera", indiretti: "Costi indiretti" };

function ScostamentiContent() {
  const searchParams = useSearchParams();
  const [commesse, setCommesse] = useState<Commessa[]>([]);
  const [commessaId, setCommessaId] = useState<number | null>(null);
  const [dati, setDati] = useState<ScostamentiCommessaResponse | null>(null);
  const [errore, setErrore] = useState<string | null>(null);

  useEffect(() => {
    commesseApi.list().then((res) => {
      setCommesse(res.data);
      const daQuery = searchParams.get("commessa");
      if (daQuery) setCommessaId(Number(daQuery));
      else if (res.data.length > 0) setCommessaId(res.data[0].id);
    }).catch(() => setErrore("Impossibile caricare le commesse."));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const carica = useCallback(async (id: number) => {
    try {
      const res = await commesseApi.scostamenti(id);
      setDati(res.data);
    } catch (e) {
      setDati(null);
      setErrore((e as { response?: { data?: { detail?: string } } })?.response?.data?.detail ?? "Impossibile calcolare gli scostamenti per questa commessa.");
    }
  }, []);

  useEffect(() => { if (commessaId) carica(commessaId); }, [commessaId, carica]);

  const commessaSel = commesse.find((c) => c.id === commessaId);

  return (
    <div className="min-h-screen bg-[#f8fafc]">
      <header className="bg-white border-b border-gray-200 px-6 py-4">
        <div className="max-w-7xl mx-auto flex items-center gap-4">
          <Link href="/" className="text-gray-400 hover:text-gray-600 transition-colors"><ChevronLeft className="w-5 h-5" /></Link>
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-fuchsia-100 flex items-center justify-center"><GitCompare className="w-5 h-5 text-fuchsia-600" /></div>
            <div>
              <h1 className="text-lg font-semibold text-gray-900">Costi Standard & Analisi Scostamenti</h1>
              <p className="text-xs text-gray-500">Confronto costo standard vs consuntivo · Scostamento prezzo ed efficienza</p>
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

      <div className="max-w-4xl mx-auto px-6 py-6 space-y-6">
        <select value={commessaId ?? ""} onChange={(e) => setCommessaId(Number(e.target.value))}
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white">
          <option value="">— seleziona commessa —</option>
          {commesse.map((c) => <option key={c.id} value={c.id}>{c.codice} — {c.descrizione} ({c.stato})</option>)}
        </select>

        {commessaSel && dati && (
          <>
            <div className="bg-white border border-gray-100 rounded-xl p-5">
              <p className="text-xs text-gray-400">
                Riferimento: {dati.quantita_riferimento} unità ({dati.quantita_riferimento_tipo === "prodotta" ? "quantità prodotta" : "quantità pianificata, commessa ancora aperta"})
              </p>
              <div className="flex items-center gap-2 mt-2">
                {Number(dati.scostamento_totale) > 0 ? <TrendingUp className="w-4 h-4 text-red-500" /> : <TrendingDown className="w-4 h-4 text-green-500" />}
                <span className="text-sm text-gray-600">Scostamento totale:</span>
                <span className={`text-lg font-bold ${Number(dati.scostamento_totale) > 0 ? "text-red-600" : "text-green-600"}`}>{EUR(dati.scostamento_totale)}</span>
                <span className="text-xs text-gray-400">({Number(dati.scostamento_totale) > 0 ? "sfavorevole" : "favorevole"})</span>
              </div>
            </div>

            <div className="space-y-3">
              {dati.voci.map((v) => (
                <div key={v.categoria} className="bg-white border border-gray-100 rounded-xl p-5">
                  <div className="flex items-center justify-between mb-3">
                    <h3 className="font-semibold text-gray-800">{CATEGORIA_LABEL[v.categoria]}</h3>
                    <span className={`text-sm font-bold ${Number(v.scostamento) > 0 ? "text-red-600" : "text-green-600"}`}>{EUR(v.scostamento)}</span>
                  </div>
                  <div className="grid grid-cols-2 gap-3 text-xs mb-3">
                    <div className="bg-gray-50 rounded-lg p-3"><p className="text-gray-400 mb-1">Costo standard</p><p className="font-semibold text-gray-700">{EUR(v.standard)}</p></div>
                    <div className="bg-gray-50 rounded-lg p-3"><p className="text-gray-400 mb-1">Costo consuntivo</p><p className="font-semibold text-gray-700">{EUR(v.consuntivo)}</p></div>
                  </div>
                  {v.scostamento_prezzo !== null && v.scostamento_quantita !== null && (
                    <div className="grid grid-cols-2 gap-3 text-xs">
                      <div className="bg-fuchsia-50 rounded-lg p-3">
                        <p className="text-fuchsia-400 mb-1">{v.categoria === "manodopera" ? "Scostamento tariffa" : "Scostamento prezzo"}</p>
                        <p className={`font-semibold ${Number(v.scostamento_prezzo) > 0 ? "text-red-600" : "text-green-600"}`}>{EUR(v.scostamento_prezzo)}</p>
                      </div>
                      <div className="bg-fuchsia-50 rounded-lg p-3">
                        <p className="text-fuchsia-400 mb-1">{v.categoria === "manodopera" ? "Scostamento efficienza" : "Scostamento quantità"}</p>
                        <p className={`font-semibold ${Number(v.scostamento_quantita) > 0 ? "text-red-600" : "text-green-600"}`}>{EUR(v.scostamento_quantita)}</p>
                      </div>
                    </div>
                  )}
                </div>
              ))}
            </div>
            <p className="text-xs text-gray-400 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">
              Convenzione: scostamento positivo = sfavorevole (costo consuntivo superiore allo standard). Per materiale e manodopera,
              scostamento prezzo + scostamento quantità/efficienza = scostamento totale della voce.
            </p>
          </>
        )}

        {commesse.length === 0 && (
          <div className="text-center text-gray-400 py-12 bg-white rounded-xl border border-gray-100">
            Nessuna commessa disponibile. Crea prima una commessa nel modulo <Link href="/commesse" className="underline">Commesse</Link>.
          </div>
        )}
      </div>
    </div>
  );
}

export default function ScostamentiPage() {
  return (
    <Suspense>
      <ScostamentiContent />
    </Suspense>
  );
}
