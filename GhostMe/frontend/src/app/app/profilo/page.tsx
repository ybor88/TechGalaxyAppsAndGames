"use client";
// Profilo dichiarato: questionario e dichiarazioni (F01) — © Roberto Di Flumeri
import clsx from "clsx";
import { Eye, EyeOff, Pencil, Plus, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import { Avviso, Caricamento, Categoria, Interruttore, Peso, Titolo, useAzione } from "@/components/ui";
import { api, CATEGORIE, dataIt, type Dichiarazione } from "@/lib/api";
import { useSessione } from "@/lib/sessione";

interface Domanda {
  chiave: string;
  categoria: string;
  domanda: string;
  esempio: string;
  risposta: (Dichiarazione & { peso: number }) | null;
}

const FONTI: Record<string, string> = {
  questionario: "Questionario", manuale: "Inserita da te", decisione: "Da una decisione", sfida: "Da una sfida",
};

export default function Profilo() {
  const [scheda, setScheda] = useState<"questionario" | "dichiarazioni">("questionario");
  const [benvenuto, setBenvenuto] = useState(false);
  useEffect(() => { setBenvenuto(new URLSearchParams(window.location.search).has("benvenuto")); }, []);

  return (
    <>
      <Titolo titolo="Il mio profilo" sottotitolo="Ciò che dichiari è sempre modificabile: non è una diagnosi né un giudizio." />
      {benvenuto && (
        <div className="mb-4">
          <Avviso tipo="ok">Account creato. Rispondi a qualche domanda: puoi saltare quelle che preferisci e tornare quando vuoi.</Avviso>
        </div>
      )}
      <div role="tablist" className="mb-6 inline-flex rounded-xl border border-bordo bg-pannello p-1">
        {(["questionario", "dichiarazioni"] as const).map((s) => (
          <button key={s} role="tab" aria-selected={scheda === s} onClick={() => setScheda(s)}
            className={clsx("rounded-lg px-4 py-2 text-sm font-medium", scheda === s ? "gradiente text-white" : "text-tenue")}>
            {s === "questionario" ? "Questionario" : "Tutte le dichiarazioni"}
          </button>
        ))}
      </div>
      {scheda === "questionario" ? <Questionario onSalvato={() => setScheda("dichiarazioni")} /> : <Dichiarazioni />}
    </>
  );
}

function Questionario({ onSalvato }: { onSalvato: () => void }) {
  const { ricarica } = useSessione();
  const [domande, setDomande] = useState<Domanda[] | null>(null);
  const [risposte, setRisposte] = useState<Record<string, { testo: string; peso: number; condivisibile: boolean }>>({});
  const { inCorso, errore, esegui } = useAzione();
  const [ok, setOk] = useState<string | null>(null);

  useEffect(() => {
    api<{ domande: Domanda[] }>("/v1/profile/questionnaire").then((q) => {
      setDomande(q.domande);
      const iniziali: typeof risposte = {};
      q.domande.forEach((d) => {
        iniziali[d.chiave] = d.risposta
          ? { testo: d.risposta.testo, peso: d.risposta.peso, condivisibile: d.risposta.visibilita === "condivisibile" }
          : { testo: "", peso: 3, condivisibile: false };
      });
      setRisposte(iniziali);
    });
  }, []);

  if (!domande) return <Caricamento />;

  const salva = async () => {
    const elenco = Object.entries(risposte)
      .filter(([, r]) => r.testo.trim().length >= 2)
      .map(([chiave, r]) => ({ chiave, testo: r.testo.trim(), peso: r.peso, visibilita: r.condivisibile ? "condivisibile" : "privata" }));
    if (!elenco.length) return;
    const fatto = await esegui(async () => {
      const r = await api<{ salvate: number }>("/v1/profile/questionnaire", { metodo: "POST", corpo: { risposte: elenco } });
      setOk(`${r.salvate} risposte salvate.`);
      await ricarica();
    });
    if (fatto) setTimeout(onSalvato, 800);
  };

  const compilate = Object.values(risposte).filter((r) => r.testo.trim().length >= 2).length;

  return (
    <div className="space-y-6">
      {Object.keys(CATEGORIE).filter((c) => domande.some((d) => d.categoria === c)).map((cat) => (
        <section key={cat} className="scheda space-y-5">
          <h2 className="text-lg font-semibold">{CATEGORIE[cat]}</h2>
          {domande.filter((d) => d.categoria === cat).map((d) => {
            const r = risposte[d.chiave];
            return (
              <div key={d.chiave} className="space-y-2">
                <label htmlFor={d.chiave} className="etichetta">{d.domanda}</label>
                <textarea id={d.chiave} rows={2} className="campo" maxLength={1000} placeholder={`Es. ${d.esempio}`}
                  value={r?.testo ?? ""} onChange={(e) => setRisposte({ ...risposte, [d.chiave]: { ...r, testo: e.target.value } })} />
                <div className="flex flex-wrap items-center gap-4 text-sm">
                  <span className="text-tenue">Importanza</span>
                  <Peso valore={r?.peso ?? 3} onChange={(v) => setRisposte({ ...risposte, [d.chiave]: { ...r, peso: v } })} />
                  <label className="flex items-center gap-2">
                    <input type="checkbox" checked={r?.condivisibile ?? false}
                      onChange={(e) => setRisposte({ ...risposte, [d.chiave]: { ...r, condivisibile: e.target.checked } })} />
                    Condivisibile nei link
                  </label>
                </div>
              </div>
            );
          })}
        </section>
      ))}
      <div className="sticky bottom-4 flex items-center justify-between gap-3 rounded-2xl border border-bordo bg-pannello/95 p-3 shadow-lg backdrop-blur">
        <span className="text-sm text-tenue">{compilate} di {domande.length} risposte</span>
        <div className="flex items-center gap-3">
          {errore && <span className="text-sm text-errore" role="alert">{errore}</span>}
          {ok && <span className="text-sm text-ok" role="status">{ok}</span>}
          <button className="btn-primario" onClick={salva} disabled={inCorso || compilate === 0}>
            {inCorso ? "Salvataggio…" : "Salva risposte"}
          </button>
        </div>
      </div>
    </div>
  );
}

const VUOTA = { testo: "", categoria: "valori", peso: 3, visibilita: "privata", usa_in_ai: true, valido_a: "" };

function Dichiarazioni() {
  const [elenco, setElenco] = useState<Dichiarazione[] | null>(null);
  const [filtro, setFiltro] = useState("");
  const [nuova, setNuova] = useState({ ...VUOTA });
  const [modifica, setModifica] = useState<string | null>(null);
  const [bozza, setBozza] = useState<Partial<Dichiarazione>>({});
  const { inCorso, errore, esegui } = useAzione();

  const carica = () => api<Dichiarazione[]>("/v1/profile/statements").then(setElenco);
  useEffect(() => { carica(); }, []);

  const aggiungi = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => {
      const corpo = { ...nuova, valido_a: nuova.valido_a || null };
      await api("/v1/profile/statements", { metodo: "POST", corpo });
      setNuova({ ...VUOTA, categoria: nuova.categoria });
      await carica();
    });
  };

  const aggiorna = (id: string, corpo: Partial<Dichiarazione>) =>
    esegui(async () => {
      await api(`/v1/profile/statements/${id}`, { metodo: "PATCH", corpo });
      setModifica(null);
      await carica();
    });

  const elimina = (d: Dichiarazione) => {
    if (!confirm(`Eliminare "${d.testo}"? L'operazione resta registrata nella timeline.`)) return;
    esegui(async () => {
      await api(`/v1/profile/statements/${d.id}`, { metodo: "DELETE" });
      await carica();
    });
  };

  if (!elenco) return <Caricamento />;
  const visibili = filtro ? elenco.filter((d) => d.categoria === filtro) : elenco;

  return (
    <div className="space-y-6">
      <form onSubmit={aggiungi} className="scheda space-y-3" aria-label="Nuova dichiarazione">
        <h2 className="font-semibold">Aggiungi una dichiarazione</h2>
        <label htmlFor="nuova-testo" className="sr-only">Testo</label>
        <textarea id="nuova-testo" rows={2} required minLength={2} maxLength={1000} className="campo"
          placeholder="Es. Preferisco le serate tranquille a casa con gli amici" value={nuova.testo}
          onChange={(e) => setNuova({ ...nuova, testo: e.target.value })} />
        <div className="flex flex-wrap items-center gap-4">
          <label className="text-sm">
            <span className="sr-only">Categoria</span>
            <select className="campo w-auto" value={nuova.categoria} onChange={(e) => setNuova({ ...nuova, categoria: e.target.value })}>
              {Object.entries(CATEGORIE).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </label>
          <Peso valore={nuova.peso} onChange={(v) => setNuova({ ...nuova, peso: v })} />
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={nuova.visibilita === "condivisibile"}
              onChange={(e) => setNuova({ ...nuova, visibilita: e.target.checked ? "condivisibile" : "privata" })} />
            Condivisibile
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={nuova.usa_in_ai} onChange={(e) => setNuova({ ...nuova, usa_in_ai: e.target.checked })} />
            Usabile dall&apos;alter ego
          </label>
          <label className="flex items-center gap-2 text-sm">
            Valida fino al
            <input type="date" className="campo w-auto" value={nuova.valido_a} onChange={(e) => setNuova({ ...nuova, valido_a: e.target.value })} />
          </label>
          <button className="btn-primario ml-auto" disabled={inCorso}><Plus className="h-4 w-4" /> Aggiungi</button>
        </div>
      </form>

      {errore && <Avviso tipo="errore">{errore}</Avviso>}

      <div className="flex flex-wrap gap-2" role="group" aria-label="Filtra per categoria">
        <button onClick={() => setFiltro("")} className={clsx("pillola border px-3 py-1", !filtro ? "gradiente text-white" : "border-bordo")}>
          Tutte ({elenco.length})
        </button>
        {Object.entries(CATEGORIE).map(([k, v]) => (
          <button key={k} onClick={() => setFiltro(k)} className={clsx("pillola border px-3 py-1", filtro === k ? "gradiente text-white" : "border-bordo")}>
            {v} ({elenco.filter((d) => d.categoria === k).length})
          </button>
        ))}
      </div>

      {visibili.length === 0 ? (
        <p className="text-tenue">Nessuna dichiarazione in questa categoria.</p>
      ) : (
        <ul className="space-y-3">
          {visibili.map((d) => (
            <li key={d.id} className="scheda">
              {modifica === d.id ? (
                <div className="space-y-3">
                  <label htmlFor={`m-${d.id}`} className="sr-only">Testo</label>
                  <textarea id={`m-${d.id}`} className="campo" rows={2} value={bozza.testo ?? ""} onChange={(e) => setBozza({ ...bozza, testo: e.target.value })} />
                  <div className="flex flex-wrap items-center gap-3">
                    <select className="campo w-auto" value={bozza.categoria} onChange={(e) => setBozza({ ...bozza, categoria: e.target.value })} aria-label="Categoria">
                      {Object.entries(CATEGORIE).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                    </select>
                    <Peso valore={bozza.peso ?? 3} onChange={(v) => setBozza({ ...bozza, peso: v })} />
                    <div className="ml-auto flex gap-2">
                      <button className="btn-secondario" onClick={() => setModifica(null)}>Annulla</button>
                      <button className="btn-primario" disabled={inCorso}
                        onClick={() => aggiorna(d.id, { testo: bozza.testo, categoria: bozza.categoria, peso: bozza.peso })}>Salva</button>
                    </div>
                  </div>
                </div>
              ) : (
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <p className={clsx(!d.usa_in_ai && "text-tenue")}>{d.testo}</p>
                    <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-tenue">
                      <Categoria nome={d.categoria} />
                      <span>Importanza {d.peso}/5</span>
                      <span>· {FONTI[d.fonte] ?? d.fonte}</span>
                      <span>· dal {dataIt(d.valido_da)}{d.valido_a ? ` al ${dataIt(d.valido_a)}` : ""}</span>
                      <span className="pillola bg-fondo">{d.visibilita === "condivisibile" ? <Eye className="h-3 w-3" /> : <EyeOff className="h-3 w-3" />}
                        {d.visibilita === "condivisibile" ? "condivisibile" : "privata"}</span>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <Interruttore attivo={d.usa_in_ai} onChange={(v) => aggiorna(d.id, { usa_in_ai: v })} etichetta="AI" />
                    <button className="btn-secondario px-2" aria-label="Cambia visibilità"
                      onClick={() => aggiorna(d.id, { visibilita: d.visibilita === "privata" ? "condivisibile" : "privata" })}>
                      {d.visibilita === "privata" ? <Eye className="h-4 w-4" /> : <EyeOff className="h-4 w-4" />}
                    </button>
                    <button className="btn-secondario px-2" aria-label="Modifica" onClick={() => { setModifica(d.id); setBozza(d); }}>
                      <Pencil className="h-4 w-4" />
                    </button>
                    <button className="btn-secondario px-2 text-errore" aria-label="Elimina" onClick={() => elimina(d)}>
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
