"use client";
// Conversazione privata: messaggi cifrati, segnalazione e blocco immediati (F05) — © Roberto Di Flumeri
import clsx from "clsx";
import { Ban, Flag, Lock, Send, XCircle } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { Avatar, Avviso, Caricamento, NomeUtente, Segnala, useAzione } from "@/components/ui";
import { api, daServer, oraIt, type Conversazione } from "@/lib/api";

const LINK: Record<string, string> = { post: "/app/annunci/", evento: "/app/eventi/", servizio: "/app/servizi?id=" };

export default function PaginaChat() {
  const { id } = useParams<{ id: string }>();
  const [c, setC] = useState<Conversazione | null>(null);
  const [errore, setErrore] = useState<string | null>(null);
  const [testo, setTesto] = useState("");
  const [segnala, setSegnala] = useState<{ tipo: string; id: string } | null>(null);
  const fondo = useRef<HTMLDivElement>(null);
  const azione = useAzione();

  const carica = useCallback(() => api<Conversazione>(`/v1/conversations/${id}`).then(setC).catch((e) => setErrore(e.message)), [id]);
  useEffect(() => {
    carica();
    const t = setInterval(carica, 5000);  // aggiornamento leggero, senza connessioni permanenti
    return () => clearInterval(t);
  }, [carica]);
  const quanti = c?.messaggi?.length;
  useEffect(() => { fondo.current?.scrollIntoView({ block: "end" }); }, [quanti]);

  if (errore) return <Avviso tipo="errore">{errore}. <Link href="/app/chat" className="underline">Torna ai messaggi</Link></Avviso>;
  if (!c) return <Caricamento />;

  const invia = (e: React.FormEvent) => {
    e.preventDefault();
    if (!testo.trim()) return;
    azione.esegui(async () => {
      setC(await api<Conversazione>(`/v1/conversations/${c.id}/messages`, { metodo: "POST", corpo: { testo } }));
      setTesto("");
    });
  };
  const blocca = () => confirm(`Bloccare ${c.con.alias}? Non potrete più scrivervi né vedere i rispettivi contenuti.`) &&
    azione.esegui(async () => { await api(`/v1/blocks/${c.con.id}`, { metodo: "POST" }); await carica(); });
  const chiudi = () => confirm("Chiudere la conversazione? Nessuno dei due potrà più scrivere.") &&
    azione.esegui(async () => { await api(`/v1/conversations/${c.id}/chiudi`, { metodo: "POST" }); await carica(); });

  let giorno = "";
  return (
    <div className="mx-auto flex h-[calc(100vh-11rem)] max-w-3xl flex-col md:h-[calc(100vh-5rem)]">
      <header className="scheda mb-3 flex flex-wrap items-center justify-between gap-2 p-3">
        <div className="flex min-w-0 items-center gap-3">
          <Avatar alias={c.con.alias} />
          <div className="min-w-0">
            {c.con.id ? <Link href={`/app/utenti/${c.con.id}`}><NomeUtente u={c.con} /></Link> : <NomeUtente u={c.con} />}
            <Link href={`${LINK[c.contesto_tipo]}${c.contesto_id}`} className="block truncate text-xs text-verde underline">{c.contesto_titolo}</Link>
          </div>
        </div>
        {c.con.id && (
          <div className="flex gap-1">
            <button className="btn-secondario px-2 text-sm" onClick={() => setSegnala({ tipo: "utente", id: c.con.id! })} aria-label="Segnala l'utente"><Flag className="h-4 w-4" /></button>
            {c.stato !== "bloccata" && <button className="btn-secondario px-2 text-sm" onClick={blocca} aria-label="Blocca"><Ban className="h-4 w-4" /></button>}
            {c.stato === "attiva" && <button className="btn-secondario px-2 text-sm" onClick={chiudi} aria-label="Chiudi la conversazione"><XCircle className="h-4 w-4" /></button>}
          </div>
        )}
      </header>

      <div className="scheda flex-1 space-y-2 overflow-y-auto p-4" aria-live="polite">
        <p className="mb-2 flex items-center justify-center gap-1 text-center text-xs text-tenue"><Lock className="h-3 w-3" />
          Messaggi visibili solo a voi due e cifrati sul server. Concordate qui il punto d&apos;incontro.</p>
        {c.messaggi?.map((m) => {
          const g = daServer(m.created_at).toLocaleDateString("it-IT", { weekday: "long", day: "numeric", month: "long" });
          const separatore = g !== giorno;
          giorno = g;
          return (
            <div key={m.id}>
              {separatore && <p className="my-3 text-center text-xs font-medium text-tenue">{g}</p>}
              <div className={clsx("group flex items-end gap-1", m.mio ? "justify-end" : "justify-start")}>
                {!m.mio && c.con.id && (
                  <button className="order-2 opacity-0 transition group-hover:opacity-100 focus:opacity-100" aria-label="Segnala questo messaggio"
                    onClick={() => setSegnala({ tipo: "messaggio", id: m.id })}><Flag className="h-3.5 w-3.5 text-tenue" /></button>
                )}
                <div className={clsx("max-w-[80%] rounded-2xl px-3 py-2 text-sm", m.mio ? "gradiente rounded-br-sm text-white" : "rounded-bl-sm bg-fondo")}>
                  <p className="whitespace-pre-wrap">{m.testo}</p>
                  <p className={clsx("mt-0.5 text-right text-[10px]", m.mio ? "text-white/80" : "text-tenue")}>{oraIt(m.created_at)}{m.mio && m.letto ? " · letto" : ""}</p>
                </div>
              </div>
            </div>
          );
        })}
        <div ref={fondo} />
      </div>

      {azione.errore && <div className="mt-2"><Avviso tipo="errore">{azione.errore}</Avviso></div>}
      {c.stato === "attiva" ? (
        <form onSubmit={invia} className="mt-3 flex gap-2">
          <label htmlFor="testo" className="sr-only">Messaggio</label>
          <input id="testo" className="campo" maxLength={2000} placeholder="Scrivi un messaggio…" value={testo} onChange={(e) => setTesto(e.target.value)} autoComplete="off" />
          <button className="btn-primario" disabled={azione.inCorso || !testo.trim()} aria-label="Invia"><Send className="h-4 w-4" /></button>
        </form>
      ) : (
        <div className="mt-3"><Avviso tipo="info">{c.stato === "bloccata" ? "Non potete più scrivervi: uno dei due ha bloccato l'altro." : "La conversazione è chiusa."}</Avviso></div>
      )}
      {segnala && <Segnala oggettoTipo={segnala.tipo} oggettoId={segnala.id} alias={c.con.alias} onChiudi={() => setSegnala(null)} onFatto={() => carica()} />}
    </div>
  );
}
