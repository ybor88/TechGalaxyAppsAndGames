"use client";
// Knowledge base manuale — © Roberto Di Flumeri
import { ShieldAlert, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import { Avviso, Caricamento, Categoria, Interruttore, Titolo, useAzione } from "@/components/ui";
import { api, CATEGORIE, dataIt } from "@/lib/api";

interface Conoscenza {
  id: string; titolo: string; contenuto: string; tipo: string; categoria: string;
  usa_in_ai: boolean; visibilita: string; sospetta_injection: boolean; created_at: string;
}

const VUOTA = { titolo: "", contenuto: "", tipo: "nota", categoria: "altro", usa_in_ai: true, visibilita: "privata" };

export default function Conoscenze() {
  const [elenco, setElenco] = useState<Conoscenza[] | null>(null);
  const [nuova, setNuova] = useState({ ...VUOTA });
  const { inCorso, errore, esegui } = useAzione();

  const carica = () => api<Conoscenza[]>("/v1/knowledge").then(setElenco);
  useEffect(() => { carica(); }, []);

  const salva = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => {
      await api("/v1/knowledge", { metodo: "POST", corpo: nuova });
      setNuova({ ...VUOTA });
      await carica();
    });
  };

  return (
    <>
      <Titolo titolo="Conoscenze" sottotitolo="Note, ricordi e testi che vuoi far conoscere al tuo alter ego. Origine sempre visibile e cancellabile." />
      <form onSubmit={salva} className="scheda mb-6 space-y-3">
        <div className="grid gap-3 sm:grid-cols-3">
          <div className="sm:col-span-2">
            <label htmlFor="titolo" className="etichetta">Titolo</label>
            <input id="titolo" required maxLength={120} className="campo" value={nuova.titolo} onChange={(e) => setNuova({ ...nuova, titolo: e.target.value })} />
          </div>
          <div>
            <label htmlFor="tipo" className="etichetta">Tipo</label>
            <select id="tipo" className="campo" value={nuova.tipo} onChange={(e) => setNuova({ ...nuova, tipo: e.target.value })}>
              <option value="nota">Nota</option><option value="ricordo">Ricordo</option><option value="testo">Testo</option><option value="link">Link</option>
            </select>
          </div>
        </div>
        <div>
          <label htmlFor="contenuto" className="etichetta">Contenuto</label>
          <textarea id="contenuto" required rows={4} maxLength={8000} className="campo" value={nuova.contenuto}
            onChange={(e) => setNuova({ ...nuova, contenuto: e.target.value })} />
        </div>
        <div className="flex flex-wrap items-center gap-4">
          <select className="campo w-auto" aria-label="Categoria" value={nuova.categoria} onChange={(e) => setNuova({ ...nuova, categoria: e.target.value })}>
            {Object.entries(CATEGORIE).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
          <Interruttore attivo={nuova.usa_in_ai} onChange={(v) => setNuova({ ...nuova, usa_in_ai: v })} etichetta="Usabile dall'alter ego" />
          <button className="btn-primario ml-auto" disabled={inCorso}>{inCorso ? "Salvataggio…" : "Aggiungi"}</button>
        </div>
        {errore && <Avviso tipo="errore">{errore}</Avviso>}
      </form>

      {!elenco ? <Caricamento /> : elenco.length === 0 ? <p className="text-tenue">Nessuna conoscenza ancora.</p> : (
        <ul className="space-y-3">
          {elenco.map((k) => (
            <li key={k.id} className="scheda">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <h2 className="font-semibold">{k.titolo}</h2>
                  <p className="mt-1 whitespace-pre-wrap text-sm">{k.contenuto}</p>
                  <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-tenue">
                    <Categoria nome={k.categoria} /> <span>{k.tipo}</span> <span>· {dataIt(k.created_at)}</span>
                  </div>
                  {k.sospetta_injection && (
                    <div className="mt-2">
                      <Avviso tipo="attenzione">
                        <ShieldAlert className="mr-1 inline h-4 w-4" /> Il testo contiene frasi che sembrano istruzioni per l&apos;AI:
                        verranno trattate come semplici dati e neutralizzate.
                      </Avviso>
                    </div>
                  )}
                </div>
                <div className="flex items-center gap-2">
                  <Interruttore attivo={k.usa_in_ai} etichetta="AI"
                    onChange={(v) => esegui(async () => { await api(`/v1/knowledge/${k.id}`, { metodo: "PATCH", corpo: { usa_in_ai: v } }); await carica(); })} />
                  <button className="btn-secondario px-2 text-errore" aria-label={`Elimina ${k.titolo}`}
                    onClick={() => confirm("Eliminare questa conoscenza?") && esegui(async () => { await api(`/v1/knowledge/${k.id}`, { metodo: "DELETE" }); await carica(); })}>
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
