"use client";
// Sfide di auto-riflessione (F09) — © Roberto Di Flumeri
import { CheckCircle2 } from "lucide-react";
import { useEffect, useState } from "react";
import { Avviso, Caricamento, Categoria, Titolo, useAzione } from "@/components/ui";
import { api, dataIt } from "@/lib/api";

interface Sfida {
  id: string; titolo: string; categoria: string; prompt: string;
  risposte: { id: string; risposta: string; statement_id: string | null; created_at: string }[];
}

export default function Sfide() {
  const [sfide, setSfide] = useState<Sfida[] | null>(null);
  const [aperta, setAperta] = useState<string | null>(null);
  const [risposta, setRisposta] = useState("");
  const [salvaProfilo, setSalvaProfilo] = useState(true);
  const { inCorso, errore, esegui } = useAzione();

  const carica = () => api<Sfida[]>("/v1/challenges").then(setSfide);
  useEffect(() => { carica(); }, []);

  const invia = (s: Sfida) => esegui(async () => {
    await api(`/v1/challenges/${s.id}/risposte`, { metodo: "POST", corpo: { risposta, salva_come_dichiarazione: salvaProfilo } });
    setRisposta(""); setAperta(null);
    await carica();
  });

  if (!sfide) return <Caricamento />;
  const fatte = sfide.filter((s) => s.risposte.length).length;

  return (
    <>
      <Titolo titolo="Sfide di auto-riflessione" sottotitolo={`Mini esercizi non clinici per esplorare preferenze e obiettivi. Completate: ${fatte}/${sfide.length}.`} />
      <ul className="grid gap-4 sm:grid-cols-2">
        {sfide.map((s) => (
          <li key={s.id} className="scheda flex flex-col">
            <div className="flex items-center justify-between gap-2">
              <h2 className="font-semibold">{s.titolo}</h2>
              {s.risposte.length > 0 && <CheckCircle2 className="h-5 w-5 text-ok" aria-label="Completata" />}
            </div>
            <div className="mt-1"><Categoria nome={s.categoria} /></div>
            <p className="mt-2 flex-1 text-sm">{s.prompt}</p>
            {s.risposte.slice(0, 1).map((r) => (
              <blockquote key={r.id} className="mt-2 rounded-lg bg-fondo p-2 text-sm text-tenue">
                «{r.risposta}» <span className="text-xs">— {dataIt(r.created_at)}{r.statement_id ? " · nel profilo" : ""}</span>
              </blockquote>
            ))}
            {aperta === s.id ? (
              <div className="mt-3 space-y-2">
                <label htmlFor={`r-${s.id}`} className="sr-only">La tua risposta</label>
                <textarea id={`r-${s.id}`} rows={3} maxLength={2000} className="campo" value={risposta} onChange={(e) => setRisposta(e.target.value)} autoFocus />
                <label className="flex items-center gap-2 text-sm">
                  <input type="checkbox" checked={salvaProfilo} onChange={(e) => setSalvaProfilo(e.target.checked)} />
                  Aggiungi al mio profilo come dichiarazione
                </label>
                {errore && <Avviso tipo="errore">{errore}</Avviso>}
                <div className="flex justify-end gap-2">
                  <button className="btn-secondario" onClick={() => setAperta(null)}>Annulla</button>
                  <button className="btn-primario" disabled={inCorso || risposta.trim().length < 2} onClick={() => invia(s)}>Salva</button>
                </div>
              </div>
            ) : (
              <button className="btn-secondario mt-3" onClick={() => { setAperta(s.id); setRisposta(""); }}>
                {s.risposte.length ? "Rispondi di nuovo" : "Rispondi"}
              </button>
            )}
          </li>
        ))}
      </ul>
    </>
  );
}
