"use client";
// Simulatore di decisioni (F03) e valutazione delle scelte (F07) — © Roberto Di Flumeri
import clsx from "clsx";
import { Lightbulb, Plus, Trash2, X } from "lucide-react";
import { useEffect, useState } from "react";
import { Avviso, Caricamento, EtichettaGenerata, Peso, Titolo, useAzione } from "@/components/ui";
import { api, dataIt } from "@/lib/api";

interface Criterio { nome: string; peso: number; statement_id?: string | null }
interface Opzione { nome: string; descrizione?: string | null }
interface VoceClassifica { indice: number; opzione: string; coerenza_percentuale: number; coerenza: string; pro: string[]; contro: string[] }
interface Decisione {
  id: string; domanda: string; opzioni: Opzione[]; criteri: Criterio[]; valutazioni: Record<string, Record<string, number>>;
  risultato: { classifica: VoceClassifica[]; sintesi: string; celle_stimate: { opzione: number; criterio: number }[];
               nota_stime: string | null; fonti: { id: string; estratto: string }[]; avviso: string };
  stato: string; scelta_finale: string | null; esito: string | null; apprendimento: string | null; created_at: string;
}

export default function Decisioni() {
  const [elenco, setElenco] = useState<Decisione[] | null>(null);
  const [selezionata, setSelezionata] = useState<Decisione | null>(null);
  const [nuova, setNuova] = useState(false);

  const carica = () => api<Decisione[]>("/v1/decisions").then(setElenco).catch(() => setElenco([]));
  useEffect(() => { carica(); }, []);

  return (
    <>
      <Titolo titolo="Simulatore di decisioni" sottotitolo="Confronta opzioni con le tue priorità dichiarate. Ti aiuta a ragionare: non decide al posto tuo."
        azioni={<button className="btn-primario" onClick={() => { setNuova(true); setSelezionata(null); }}><Plus className="h-4 w-4" /> Nuova decisione</button>} />
      {nuova ? (
        <NuovaDecisione onCreata={(d) => { setNuova(false); setSelezionata(d); carica(); }} onAnnulla={() => setNuova(false)} />
      ) : selezionata ? (
        <Dettaglio decisione={selezionata} onAggiornata={(d) => { setSelezionata(d); carica(); }}
          onChiudi={() => { setSelezionata(null); carica(); }} />
      ) : !elenco ? <Caricamento /> : elenco.length === 0 ? (
        <Avviso tipo="info">Nessuna decisione ancora. Crea la prima: ad esempio &quot;Quale offerta di lavoro accetto?&quot;.</Avviso>
      ) : (
        <ul className="space-y-3">
          {elenco.map((d) => (
            <li key={d.id}>
              <button className="scheda w-full text-left transition hover:border-blu" onClick={() => setSelezionata(d)}>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-semibold">{d.domanda}</span>
                  <span className="pillola bg-fondo">{d.stato === "valutata" ? `Esito: ${d.esito}` : d.stato === "decisa" ? "Decisa" : "Da decidere"}</span>
                </div>
                <p className="mt-1 text-sm text-tenue">{d.opzioni.map((o) => o.nome).join(" · ")} — {dataIt(d.created_at)}</p>
              </button>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

function NuovaDecisione({ onCreata, onAnnulla }: { onCreata: (d: Decisione) => void; onAnnulla: () => void }) {
  const [domanda, setDomanda] = useState("");
  const [opzioni, setOpzioni] = useState<Opzione[]>([{ nome: "", descrizione: "" }, { nome: "", descrizione: "" }]);
  const [criteri, setCriteri] = useState<Criterio[]>([{ nome: "", peso: 3 }]);
  const [valutazioni, setValutazioni] = useState<Record<string, Record<string, number>>>({});
  const { inCorso, errore, esegui } = useAzione();

  const suggeriti = () => esegui(async () => {
    const s = await api<Criterio[]>("/v1/decisions/criteri-suggeriti");
    if (!s.length) throw new Error("Nessun valore od obiettivo nel profilo da usare come criterio.");
    setCriteri(s);
  });

  const imposta = (i: number, j: number, v: string) => {
    const r = { ...(valutazioni[i] || {}) };
    if (v) r[j] = Number(v); else delete r[j];
    setValutazioni({ ...valutazioni, [i]: r });
  };

  const invia = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => {
      const corpo = {
        domanda,
        opzioni: opzioni.filter((o) => o.nome.trim()).map((o) => ({ nome: o.nome.trim(), descrizione: o.descrizione || null })),
        criteri: criteri.filter((c) => c.nome.trim()),
        valutazioni,
      };
      onCreata(await api<Decisione>("/v1/decisions", { metodo: "POST", corpo }));
    });
  };

  return (
    <form onSubmit={invia} className="space-y-6">
      <section className="scheda space-y-3">
        <label htmlFor="domanda" className="etichetta">Su cosa devi decidere?</label>
        <input id="domanda" className="campo" required minLength={3} maxLength={300} value={domanda}
          placeholder="Es. Quale offerta di lavoro accetto?" onChange={(e) => setDomanda(e.target.value)} />
      </section>

      <section className="scheda space-y-3">
        <h2 className="font-semibold">Opzioni (2-6)</h2>
        {opzioni.map((o, i) => (
          <div key={i} className="grid gap-2 sm:grid-cols-[1fr_2fr_auto]">
            <input className="campo" required maxLength={120} placeholder={`Opzione ${i + 1}`} aria-label={`Nome opzione ${i + 1}`}
              value={o.nome} onChange={(e) => setOpzioni(opzioni.map((x, k) => (k === i ? { ...x, nome: e.target.value } : x)))} />
            <input className="campo" maxLength={500} placeholder="Descrizione (facoltativa, aiuta le stime)" aria-label={`Descrizione opzione ${i + 1}`}
              value={o.descrizione || ""} onChange={(e) => setOpzioni(opzioni.map((x, k) => (k === i ? { ...x, descrizione: e.target.value } : x)))} />
            <button type="button" className="btn-secondario px-2" disabled={opzioni.length <= 2} aria-label="Rimuovi opzione"
              onClick={() => setOpzioni(opzioni.filter((_, k) => k !== i))}><X className="h-4 w-4" /></button>
          </div>
        ))}
        {opzioni.length < 6 && <button type="button" className="btn-secondario" onClick={() => setOpzioni([...opzioni, { nome: "" }])}><Plus className="h-4 w-4" /> Opzione</button>}
      </section>

      <section className="scheda space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="font-semibold">Criteri e importanza</h2>
          <button type="button" className="btn-secondario" onClick={suggeriti}><Lightbulb className="h-4 w-4" /> Usa i miei valori e obiettivi</button>
        </div>
        {criteri.map((c, j) => (
          <div key={j} className="flex flex-wrap items-center gap-2">
            <input className="campo flex-1" required maxLength={120} placeholder="Es. Tempo per la famiglia" aria-label={`Criterio ${j + 1}`}
              value={c.nome} onChange={(e) => setCriteri(criteri.map((x, k) => (k === j ? { ...x, nome: e.target.value, statement_id: null } : x)))} />
            <Peso valore={c.peso} onChange={(v) => setCriteri(criteri.map((x, k) => (k === j ? { ...x, peso: v } : x)))} />
            <button type="button" className="btn-secondario px-2" disabled={criteri.length <= 1} aria-label="Rimuovi criterio"
              onClick={() => setCriteri(criteri.filter((_, k) => k !== j))}><X className="h-4 w-4" /></button>
          </div>
        ))}
        {criteri.length < 10 && <button type="button" className="btn-secondario" onClick={() => setCriteri([...criteri, { nome: "", peso: 3 }])}><Plus className="h-4 w-4" /> Criterio</button>}
      </section>

      <section className="scheda space-y-3">
        <h2 className="font-semibold">Quanto ogni opzione soddisfa ogni criterio? <span className="text-sm font-normal text-tenue">(1-5, facoltativo)</span></h2>
        <p className="text-sm text-tenue">Le celle vuote vengono stimate in modo neutro e segnalate: puoi correggerle dopo.</p>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead><tr><th className="p-2 text-left">Opzione</th>{criteri.map((c, j) => <th key={j} className="p-2 text-left font-medium">{c.nome || `Criterio ${j + 1}`}</th>)}</tr></thead>
            <tbody>
              {opzioni.map((o, i) => (
                <tr key={i} className="border-t border-bordo">
                  <td className="p-2 font-medium">{o.nome || `Opzione ${i + 1}`}</td>
                  {criteri.map((_, j) => (
                    <td key={j} className="p-2">
                      <select className="campo w-20 py-1" aria-label={`Valutazione opzione ${i + 1} criterio ${j + 1}`}
                        value={valutazioni[i]?.[j] ?? ""} onChange={(e) => imposta(i, j, e.target.value)}>
                        <option value="">—</option>{[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n}</option>)}
                      </select>
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {errore && <Avviso tipo="errore">{errore}</Avviso>}
      <div className="flex justify-end gap-2">
        <button type="button" className="btn-secondario" onClick={onAnnulla}>Annulla</button>
        <button className="btn-primario" disabled={inCorso}>{inCorso ? "Analisi…" : "Analizza"}</button>
      </div>
    </form>
  );
}

function Dettaglio({ decisione: d, onAggiornata, onChiudi }: { decisione: Decisione; onAggiornata: (d: Decisione) => void; onChiudi: () => void }) {
  const [valutazioni, setValutazioni] = useState(d.valutazioni);
  const [esito, setEsito] = useState({ scelta_finale: d.scelta_finale || d.risultato.classifica[0]?.opzione || "", esito: d.esito || "",
    apprendimento: d.apprendimento || "", salva_come_dichiarazione: false });
  const { inCorso, errore, esegui } = useAzione();
  const stimata = (i: number, j: number) => d.risultato.celle_stimate.some((c) => c.opzione === i && c.criterio === j);

  useEffect(() => { setValutazioni(d.valutazioni); }, [d]);

  const ricalcola = () => esegui(async () =>
    onAggiornata(await api<Decisione>(`/v1/decisions/${d.id}/valutazioni`, { metodo: "PUT", corpo: { valutazioni } })));

  const salvaEsito = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => onAggiornata(await api<Decisione>(`/v1/decisions/${d.id}/esito`, {
      metodo: "PUT", corpo: { ...esito, esito: esito.esito || null, apprendimento: esito.apprendimento || null } })));
  };

  const elimina = () => confirm("Eliminare questa decisione?") &&
    esegui(async () => { await api(`/v1/decisions/${d.id}`, { metodo: "DELETE" }); onChiudi(); });

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <button className="btn-secondario" onClick={onChiudi}>← Tutte le decisioni</button>
        <button className="btn-secondario text-errore" onClick={elimina}><Trash2 className="h-4 w-4" /> Elimina</button>
      </div>

      <section className="scheda space-y-4">
        <div className="flex flex-wrap items-center gap-2"><h2 className="text-xl font-semibold">{d.domanda}</h2><EtichettaGenerata /></div>
        <p className="font-medium">{d.risultato.sintesi}</p>
        <ul className="space-y-3">
          {d.risultato.classifica.map((v) => (
            <li key={v.indice}>
              <div className="flex justify-between text-sm"><span className="font-medium">{v.opzione}</span><span>{v.coerenza_percentuale}% · coerenza {v.coerenza}</span></div>
              <div className="mt-1 h-3 overflow-hidden rounded-full bg-fondo" role="progressbar" aria-valuenow={v.coerenza_percentuale} aria-valuemin={0} aria-valuemax={100} aria-label={`Coerenza ${v.opzione}`}>
                <div className="gradiente h-full" style={{ width: `${v.coerenza_percentuale}%` }} />
              </div>
              <div className="mt-1 flex flex-wrap gap-x-4 text-xs">
                {v.pro.length > 0 && <span className="text-ok">Pro: {v.pro.join(", ")}</span>}
                {v.contro.length > 0 && <span className="text-errore">Contro: {v.contro.join(", ")}</span>}
              </div>
            </li>
          ))}
        </ul>
        {d.risultato.fonti.length > 0 && (
          <p className="text-sm text-tenue">Criteri presi dal tuo profilo: {d.risultato.fonti.map((f) => `«${f.estratto}»`).join(", ")}</p>
        )}
        <Avviso tipo="info">{d.risultato.avviso}</Avviso>
      </section>

      <section className="scheda space-y-3">
        <h3 className="font-semibold">Valutazioni</h3>
        {d.risultato.nota_stime && <Avviso tipo="attenzione">{d.risultato.nota_stime}</Avviso>}
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead><tr><th className="p-2 text-left">Opzione</th>{d.criteri.map((c, j) => <th key={j} className="p-2 text-left font-medium">{c.nome} <span className="text-tenue">({c.peso})</span></th>)}</tr></thead>
            <tbody>
              {d.opzioni.map((o, i) => (
                <tr key={i} className="border-t border-bordo">
                  <td className="p-2 font-medium">{o.nome}</td>
                  {d.criteri.map((_, j) => (
                    <td key={j} className="p-2">
                      <select className={clsx("campo w-20 py-1", stimata(i, j) && "border-dashed border-attenzione")}
                        aria-label={`Valutazione ${o.nome}, criterio ${j + 1}${stimata(i, j) ? " (stimata)" : ""}`}
                        value={valutazioni[i]?.[j] ?? 3}
                        onChange={(e) => setValutazioni({ ...valutazioni, [i]: { ...valutazioni[i], [j]: Number(e.target.value) } })}>
                        {[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n}</option>)}
                      </select>
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <button className="btn-secondario" onClick={ricalcola} disabled={inCorso}>Ricalcola</button>
      </section>

      <form onSubmit={salvaEsito} className="scheda space-y-3">
        <h3 className="font-semibold">Cosa hai deciso e com&apos;è andata?</h3>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="text-sm">Scelta finale
            <select className="campo mt-1" value={esito.scelta_finale} onChange={(e) => setEsito({ ...esito, scelta_finale: e.target.value })}>
              {d.opzioni.map((o) => <option key={o.nome} value={o.nome}>{o.nome}</option>)}
            </select>
          </label>
          <label className="text-sm">Esito (quando lo conosci)
            <select className="campo mt-1" value={esito.esito} onChange={(e) => setEsito({ ...esito, esito: e.target.value })}>
              <option value="">Non ancora</option><option value="positivo">Positivo</option><option value="neutro">Neutro</option><option value="negativo">Negativo</option>
            </select>
          </label>
        </div>
        <label className="block text-sm">Cosa hai imparato?
          <textarea className="campo mt-1" rows={2} maxLength={1000} value={esito.apprendimento}
            onChange={(e) => setEsito({ ...esito, apprendimento: e.target.value })} />
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={esito.salva_come_dichiarazione} onChange={(e) => setEsito({ ...esito, salva_come_dichiarazione: e.target.checked })} />
          Aggiungi l&apos;apprendimento al mio profilo
        </label>
        {errore && <Avviso tipo="errore">{errore}</Avviso>}
        <button className="btn-primario" disabled={inCorso}>Salva</button>
      </form>
    </div>
  );
}
