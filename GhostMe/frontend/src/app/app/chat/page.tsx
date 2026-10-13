"use client";
// Chat con l'alter ego (F02) — © Roberto Di Flumeri
import clsx from "clsx";
import { History, Plus, Send, Trash2 } from "lucide-react";
import Image from "next/image";
import { useEffect, useRef, useState } from "react";
import { Avviso, Confidenza, ElencoFonti, EtichettaGenerata, Titolo } from "@/components/ui";
import { api, dataIt, type Conversazione, type Messaggio, type Versione } from "@/lib/api";

const SUGGERIMENTI = [
  "Chi sono? Riassumi il mio profilo",
  "Cosa mi piace fare nel tempo libero?",
  "È meglio per me un weekend al mare o in montagna?",
  "Come prendo le decisioni importanti?",
];

interface Risposta {
  conversation_id: string;
  domanda: Messaggio;
  risposta: Messaggio;
  avvisi: string[];
  etichetta: string;
  durata_ms: number;
}

export default function Chat() {
  const [conversazioni, setConversazioni] = useState<Conversazione[]>([]);
  const [versioni, setVersioni] = useState<Versione[]>([]);
  const [attiva, setAttiva] = useState<Conversazione | null>(null);
  const [messaggi, setMessaggi] = useState<Messaggio[]>([]);
  const [avvisi, setAvvisi] = useState<Record<string, string[]>>({});
  const [testo, setTesto] = useState("");
  const [versioneNuova, setVersioneNuova] = useState("");
  const [inAttesa, setInAttesa] = useState(false);
  const [secondi, setSecondi] = useState(0);
  const [errore, setErrore] = useState<string | null>(null);
  const fine = useRef<HTMLDivElement>(null);

  const caricaElenco = () => api<Conversazione[]>("/v1/conversations").then(setConversazioni).catch(() => undefined);
  useEffect(() => {
    caricaElenco();
    api<Versione[]>("/v1/snapshots").then(setVersioni).catch(() => undefined);
    const v = new URLSearchParams(window.location.search).get("versione");
    if (v) setVersioneNuova(v);
  }, []);
  useEffect(() => { fine.current?.scrollIntoView({ behavior: "smooth" }); }, [messaggi, inAttesa]);
  useEffect(() => {
    if (!inAttesa) return;
    setSecondi(0);
    const t = setInterval(() => setSecondi((s) => s + 1), 1000);
    return () => clearInterval(t);
  }, [inAttesa]);

  const apri = async (c: Conversazione) => {
    const d = await api<Conversazione>(`/v1/conversations/${c.id}`);
    setAttiva(d);
    setMessaggi(d.messaggi || []);
    setErrore(null);
  };

  const nuova = () => {
    setAttiva(null);
    setMessaggi([]);
    setErrore(null);
  };

  const invia = async (domanda: string) => {
    const q = domanda.trim();
    if (!q || inAttesa) return;
    setInAttesa(true);
    setErrore(null);
    setTesto("");
    const provvisorio: Messaggio = { id: "tmp", ruolo: "utente", contenuto: q, created_at: new Date().toISOString() };
    setMessaggi((m) => [...m, provvisorio]);
    try {
      const r = await api<Risposta>("/v1/chat/completions", {
        metodo: "POST",
        corpo: { messaggio: q, conversation_id: attiva?.id, snapshot_id: !attiva && versioneNuova ? versioneNuova : undefined },
      });
      setMessaggi((m) => [...m.filter((x) => x.id !== "tmp"), r.domanda, r.risposta]);
      if (r.avvisi.length) setAvvisi((a) => ({ ...a, [r.risposta.id]: r.avvisi }));
      if (!attiva) {
        setAttiva({ id: r.conversation_id, titolo: q, snapshot_id: versioneNuova || null, created_at: "", updated_at: "" });
        caricaElenco();
      }
    } catch (e) {
      setMessaggi((m) => m.filter((x) => x.id !== "tmp"));
      setTesto(q); // nessuna perdita del testo: si può riprovare
      setErrore(e instanceof Error ? e.message : "Invio non riuscito");
    } finally {
      setInAttesa(false);
    }
  };

  const elimina = async (c: Conversazione) => {
    if (!confirm("Eliminare definitivamente questa conversazione?")) return;
    await api(`/v1/conversations/${c.id}`, { metodo: "DELETE" });
    if (attiva?.id === c.id) nuova();
    caricaElenco();
  };

  const versioneAttiva = versioni.find((v) => v.id === (attiva ? attiva.snapshot_id : versioneNuova));

  return (
    <>
      <Titolo titolo="Chat con l'alter ego" sottotitolo="Risponde solo con i dati che hai autorizzato. Ogni risposta è una simulazione." />
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[16rem_minmax(0,1fr)]">
        <aside className="scheda h-fit min-w-0 space-y-2 p-3" aria-label="Conversazioni">
          <button className="btn-primario w-full" onClick={nuova}><Plus className="h-4 w-4" /> Nuova conversazione</button>
          <ul className="max-h-96 space-y-1 overflow-y-auto">
            {conversazioni.map((c) => (
              <li key={c.id} className="group flex items-center gap-1">
                <button onClick={() => apri(c)} className={clsx("min-w-0 flex-1 truncate rounded-lg px-2 py-2 text-left text-sm",
                  attiva?.id === c.id ? "bg-blu/15 font-medium" : "hover:bg-fondo")}>
                  {c.snapshot_id && <History className="mr-1 inline h-3 w-3 text-viola" aria-label="Con una versione" />}
                  {c.titolo}
                  <span className="block text-xs text-tenue">{dataIt(c.updated_at)}</span>
                </button>
                <button onClick={() => elimina(c)} className="rounded p-1 text-tenue hover:text-errore" aria-label={`Elimina ${c.titolo}`}>
                  <Trash2 className="h-4 w-4" />
                </button>
              </li>
            ))}
          </ul>
        </aside>

        <section className="scheda flex min-h-[60vh] min-w-0 flex-col p-0" aria-label="Conversazione">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-bordo p-3">
            {attiva ? (
              <span className="min-w-0 truncate text-sm font-medium">{versioneAttiva ? `Con: ${versioneAttiva.etichetta}` : "Con il tuo alter ego attuale"}</span>
            ) : (
              <label className="flex w-full min-w-0 items-center gap-2 text-sm sm:w-auto">
                Parla con
                <select className="campo min-w-0 flex-1 py-1 sm:w-auto sm:max-w-xs sm:flex-none" value={versioneNuova} onChange={(e) => setVersioneNuova(e.target.value)}>
                  <option value="">il mio alter ego di oggi</option>
                  {versioni.map((v) => <option key={v.id} value={v.id}>{v.etichetta}</option>)}
                </select>
              </label>
            )}
            {versioneAttiva?.tipo === "simulazione_futura" && <span className="pillola bg-attenzione/15 text-attenzione">Simulazione ipotetica</span>}
          </div>

          <div className="flex-1 space-y-4 overflow-y-auto p-4" aria-live="polite">
            {messaggi.length === 0 && !inAttesa && (
              <div className="flex flex-col items-center gap-4 py-10 text-center">
                <Image src="/logo.png" alt="" width={72} height={72} className="rounded-2xl" />
                <p className="text-tenue">Fai una domanda per iniziare. Prova con:</p>
                <div className="flex flex-wrap justify-center gap-2">
                  {SUGGERIMENTI.map((s) => <button key={s} className="btn-secondario text-sm" onClick={() => invia(s)}>{s}</button>)}
                </div>
              </div>
            )}
            {messaggi.map((m) => (
              <div key={m.id} className={clsx("flex", m.ruolo === "utente" ? "justify-end" : "justify-start")}>
                <div className={clsx("max-w-[85%] rounded-2xl px-4 py-3", m.ruolo === "utente" ? "gradiente text-white" : "border border-bordo bg-fondo")}>
                  {m.ruolo === "alter_ego" && (
                    <div className="mb-2 flex flex-wrap items-center gap-2">
                      <EtichettaGenerata /> <Confidenza livello={m.confidenza} />
                      {m.motore && <span className="text-xs text-tenue">{m.motore === "policy" ? "regole di sicurezza" : m.motore === "ollama" ? `Ollama · ${m.modello}` : "motore integrato"}</span>}
                    </div>
                  )}
                  <p className="whitespace-pre-wrap">{m.contenuto}</p>
                  {(avvisi[m.id] || []).map((a) => <p key={a} className="mt-2 text-xs text-attenzione">{a}</p>)}
                  {m.ruolo === "alter_ego" && <ElencoFonti fonti={m.fonti} />}
                </div>
              </div>
            ))}
            {inAttesa && (
              <div className="flex justify-start" role="status">
                <div className="rounded-2xl border border-bordo bg-fondo px-4 py-3 text-sm text-tenue">
                  L&apos;alter ego sta elaborando… {secondi > 2 && `${secondi}s`}
                  {secondi > 8 && <span className="block text-xs">Il modello locale può richiedere tempo: la risposta arriverà qui.</span>}
                </div>
              </div>
            )}
            <div ref={fine} />
          </div>

          {errore && <div className="px-4"><Avviso tipo="errore">{errore}</Avviso></div>}
          <form className="flex gap-2 border-t border-bordo p-3" onSubmit={(e) => { e.preventDefault(); invia(testo); }}>
            <label htmlFor="domanda" className="sr-only">La tua domanda</label>
            <input id="domanda" className="campo" maxLength={2000} placeholder="Scrivi una domanda…" value={testo}
              onChange={(e) => setTesto(e.target.value)} disabled={inAttesa} autoComplete="off" />
            <button className="btn-primario" disabled={inAttesa || !testo.trim()} aria-label="Invia"><Send className="h-4 w-4" /></button>
          </form>
        </section>
      </div>
    </>
  );
}
