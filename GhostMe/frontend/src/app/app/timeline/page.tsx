"use client";
// Timeline della personalità (F10) — © Roberto Di Flumeri
import { useEffect, useState } from "react";
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Avviso, Caricamento, Categoria, Titolo } from "@/components/ui";
import { api, CATEGORIE, dataOraIt } from "@/lib/api";

interface Evento {
  tipo: "dichiarazione" | "versione" | "diario" | "decisione";
  azione: string; data: string; categoria?: string; testo?: string; prima?: string | null; fonte?: string; esito?: string;
}
interface Timeline { eventi: Evento[]; serie: Record<string, string | number>[]; nota: string }

const COLORI: Record<string, string> = {
  valori: "#6366f1", abitudini: "#0ea5e9", gusti: "#a855f7", obiettivi: "#f59e0b", stile_comunicativo: "#10b981", altro: "#94a3b8",
};
const DESCRIZIONE: Record<string, (e: Evento) => string> = {
  dichiarazione: (e) => ({ creata: "Nuova dichiarazione", modificata: "Dichiarazione modificata", eliminata: "Dichiarazione eliminata" }[e.azione] || e.azione),
  versione: (e) => (e.azione === "simulazione_futura" ? "Simulazione creata" : "Versione salvata"),
  diario: (e) => (e.azione === "check_in" ? "Check-in" : "Nota di diario"),
  decisione: (e) => (e.esito ? `Decisione valutata (${e.esito})` : "Decisione presa"),
};
const PER_PAGINA = 30;
const FILTRI = [["", "Tutto"], ["dichiarazione", "Dichiarazioni"], ["versione", "Versioni"], ["diario", "Diario"], ["decisione", "Decisioni"]];

export default function PaginaTimeline() {
  const [t, setT] = useState<Timeline | null>(null);
  const [filtro, setFiltro] = useState("");
  const [mostrati, setMostrati] = useState(PER_PAGINA);
  const [errore, setErrore] = useState<string | null>(null);

  useEffect(() => { api<Timeline>("/v1/timeline").then(setT).catch((e) => setErrore(e.message)); }, []);

  if (errore) return <Avviso tipo="errore">{errore}</Avviso>;
  if (!t) return <Caricamento />;
  const eventi = filtro ? t.eventi.filter((e) => e.tipo === filtro) : t.eventi;

  return (
    <>
      <Titolo titolo="Timeline" sottotitolo="Come sono cambiate le tue dichiarazioni nel tempo, con le loro fonti." />
      <section className="scheda mb-6" aria-label="Dichiarazioni per categoria nelle versioni">
        <h2 className="mb-1 font-semibold">Dichiarazioni per categoria</h2>
        <p className="mb-3 text-sm text-tenue">Numero di dichiarazioni in ogni versione salvata e oggi: misura quanto hai raccontato, non chi sei.</p>
        <div className="h-72 w-full">
          <ResponsiveContainer>
            <BarChart data={t.serie.map((s) => ({ ...s, nome: String(s.etichetta).replace("SIMULAZIONE · ", "≈ ") }))}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgb(var(--bordo))" />
              <XAxis dataKey="nome" tick={{ fill: "rgb(var(--tenue))", fontSize: 12 }} />
              <YAxis allowDecimals={false} tick={{ fill: "rgb(var(--tenue))", fontSize: 12 }} />
              <Tooltip contentStyle={{ background: "rgb(var(--pannello))", border: "1px solid rgb(var(--bordo))", borderRadius: 12 }} />
              <Legend />
              {Object.keys(CATEGORIE).map((c) => <Bar key={c} dataKey={c} name={CATEGORIE[c]} stackId="a" fill={COLORI[c]} />)}
            </BarChart>
          </ResponsiveContainer>
        </div>
      </section>

      <div className="mb-4 flex flex-wrap gap-2" role="group" aria-label="Filtra eventi">
        {FILTRI.map(([k, v]) => (
          <button key={k} onClick={() => { setFiltro(k); setMostrati(PER_PAGINA); }} className={`pillola border px-3 py-1 ${filtro === k ? "gradiente text-white" : "border-bordo"}`}>{v}</button>
        ))}
      </div>

      {eventi.length === 0 ? <p className="text-tenue">Nessun evento.</p> : (
        <ol className="relative space-y-4 border-l-2 border-bordo pl-6">
          {eventi.slice(0, mostrati).map((e, i) => (
            <li key={i} className="relative">
              <span className="gradiente absolute -left-[33px] top-1.5 h-4 w-4 rounded-full ring-4 ring-fondo" aria-hidden />
              <div className="text-xs text-tenue">{dataOraIt(e.data)}</div>
              <div className="flex flex-wrap items-center gap-2 font-medium">
                {DESCRIZIONE[e.tipo](e)} {e.categoria && <Categoria nome={e.categoria} />}
                {e.fonte && <span className="text-xs text-tenue">fonte: {e.fonte}</span>}
              </div>
              {e.prima && <p className="text-sm text-tenue line-through">{e.prima}</p>}
              {e.testo && <p className="text-sm">{e.testo}</p>}
            </li>
          ))}
        </ol>
      )}
      {eventi.length > mostrati && (
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <button className="btn-secondario" onClick={() => setMostrati((n) => n + PER_PAGINA)}>Mostra altri</button>
          <span className="text-sm text-tenue">{mostrati} di {eventi.length} eventi</span>
        </div>
      )}
      <p className="mt-6 text-xs text-tenue">{t.nota}</p>
    </>
  );
}
