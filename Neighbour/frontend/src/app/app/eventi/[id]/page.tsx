"use client";
// Dettaglio evento: adesione idempotente, lista d'attesa, approvazioni, aggiornamenti — © Roberto Di Flumeri
import { Ban, CalendarDays, Check, Flag, MapPin, Megaphone, MessageCircle, Users, X } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import FeedbackDaLasciare from "@/components/Feedback";
import { Avatar, Avviso, Caricamento, Categoria, EtichettaStato, EtichettaTipo, Modale, NomeUtente, Segnala, useAzione } from "@/components/ui";
import { api, dataOraIt, fa, oraIt, type Evento } from "@/lib/api";

const STATO_MIO: Record<string, [string, "ok" | "attenzione" | "info" | "errore"]> = {
  confermato: ["Partecipi a questo evento.", "ok"],
  lista_attesa: ["Sei in lista d'attesa: se si libera un posto entri automaticamente e ti avvisiamo.", "attenzione"],
  in_attesa: ["Richiesta inviata: l'organizzatore deve confermarla.", "info"],
  rifiutato: ["L'organizzatore non ha confermato la tua partecipazione.", "errore"],
};
const NOMI_STATO: Record<string, string> = { confermato: "Confermato", in_attesa: "Da approvare", lista_attesa: "Lista d'attesa", rifiutato: "Rifiutato" };

export default function PaginaEvento() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [e, setE] = useState<Evento | null>(null);
  const [errore, setErrore] = useState<string | null>(null);
  const [novita, setNovita] = useState("");
  const [capienza, setCapienza] = useState("");
  const [segnala, setSegnala] = useState(false);
  const [scrivi, setScrivi] = useState(false);
  const [msg, setMsg] = useState("");
  const azione = useAzione();

  const carica = useCallback(() => api<Evento>(`/v1/events/${id}`).then((x) => { setE(x); setCapienza(x.capienza ? String(x.capienza) : ""); })
    .catch((err) => setErrore(err.message)), [id]);
  useEffect(() => { carica(); }, [carica]);

  if (errore) return <Avviso tipo="errore">{errore}. <Link href="/app/eventi" className="underline">Torna agli eventi</Link></Avviso>;
  if (!e) return <Caricamento />;

  const esegui = (fn: () => Promise<Evento>) => azione.esegui(async () => setE(await fn()));
  const partecipa = () => esegui(() => api<Evento>(`/v1/events/${e.id}/join`, { metodo: "POST" }));
  const rinuncia = () => confirm("Rinunciare all'evento?") && esegui(() => api<Evento>(`/v1/events/${e.id}/join`, { metodo: "DELETE" }));
  const decidi = (uid: string, stato: string) => esegui(() => api<Evento>(`/v1/events/${e.id}/partecipanti/${uid}`, { metodo: "POST", corpo: { stato } }));
  const pubblicaNovita = (ev: React.FormEvent) => {
    ev.preventDefault();
    esegui(() => api<Evento>(`/v1/events/${e.id}/aggiornamenti`, { metodo: "POST", corpo: { testo: novita } })).then((ok) => ok && setNovita(""));
  };
  const cambiaCapienza = () => esegui(() => api<Evento>(`/v1/events/${e.id}`, { metodo: "PATCH",
    corpo: capienza ? { capienza: Number(capienza) } : { senza_limite: true } }));
  const contatta = (ev: React.FormEvent) => {
    ev.preventDefault();
    azione.esegui(async () => {
      const c = await api<{ id: string }>("/v1/conversations", { metodo: "POST", corpo: { contesto_tipo: "evento", contesto_id: e.id, messaggio: msg } });
      router.push(`/app/chat/${c.id}`);
    });
  };

  const aperto = e.stato === "pubblicato" && !e.concluso;
  const mio = STATO_MIO[e.mia_partecipazione || ""];
  return (
    <article className="mx-auto max-w-3xl space-y-5">
      <Link href="/app/eventi" className="text-sm text-tenue hover:underline">← Eventi</Link>
      <div className="scheda space-y-3">
        <div className="flex flex-wrap items-center gap-2">
          <EtichettaTipo tipo="evento" /><Categoria tipo="evento" nome={e.categoria} />
          {e.stato !== "pubblicato" && <EtichettaStato stato={e.stato} />}
          {e.concluso && <span className="pillola bg-bordo text-tenue">Concluso</span>}
          {e.modalita === "su_approvazione" && <span className="pillola bg-fondo text-tenue">Su approvazione</span>}
        </div>
        <h1 className="text-2xl font-bold">{e.titolo}</h1>
        <ul className="space-y-1 text-sm">
          <li className="flex items-center gap-2"><CalendarDays className="h-4 w-4 text-verde" /> {dataOraIt(e.inizio)}{e.fine ? ` – ${oraIt(e.fine)}` : ""}</li>
          <li className="flex items-center gap-2"><MapPin className="h-4 w-4 text-verde" /> {e.luogo}{e.posizione.distanza ? ` · ${e.posizione.distanza} da te` : ""}</li>
          <li className="flex items-center gap-2"><Users className="h-4 w-4 text-verde" /> {e.confermati}{e.capienza ? ` su ${e.capienza} posti` : " partecipanti"}
            {e.posti_liberi === 0 ? " · al completo" : e.posti_liberi ? ` · ${e.posti_liberi} liberi` : ""}{e.in_lista_attesa ? ` · ${e.in_lista_attesa} in lista d'attesa` : ""}</li>
        </ul>
        <p className="whitespace-pre-wrap">{e.descrizione}</p>
      </div>

      <div className="scheda flex flex-wrap items-center justify-between gap-3">
        <Link href={e.mio ? "/app/profilo" : `/app/utenti/${e.organizzatore.id}`} className="flex items-center gap-3">
          <Avatar alias={e.organizzatore.alias} /><span><span className="block text-xs text-tenue">Organizza</span><NomeUtente u={e.organizzatore} /></span>
        </Link>
        {!e.mio && (
          <div className="flex flex-wrap gap-2">
            {aperto && <button className="btn-secondario" onClick={() => setScrivi(true)}><MessageCircle className="h-4 w-4" /> Scrivi</button>}
            <button className="btn-secondario" onClick={() => setSegnala(true)}><Flag className="h-4 w-4" /> Segnala</button>
          </div>
        )}
      </div>

      {azione.errore && <Avviso tipo="errore">{azione.errore}</Avviso>}
      {mio && <Avviso tipo={mio[1]}>{mio[0]}</Avviso>}
      {e.stato === "annullato" && <Avviso tipo="errore">L&apos;evento è stato annullato dall&apos;organizzatore.</Avviso>}

      {!e.mio && aperto && e.mia_partecipazione !== "rifiutato" && (
        e.mia_partecipazione ? (
          <button className="btn-secondario w-full" onClick={rinuncia} disabled={azione.inCorso}><X className="h-4 w-4" /> Rinuncia</button>
        ) : (
          <button className="btn-primario w-full py-3" onClick={partecipa} disabled={azione.inCorso}>
            <Check className="h-4 w-4" /> {e.modalita === "su_approvazione" ? "Chiedi di partecipare" : e.posti_liberi === 0 ? "Mettiti in lista d'attesa" : "Partecipo"}
          </button>
        )
      )}

      {e.aggiornamenti && e.aggiornamenti.length > 0 && (
        <section className="scheda">
          <h2 className="mb-2 flex items-center gap-2 font-semibold"><Megaphone className="h-4 w-4 text-verde" /> Aggiornamenti</h2>
          <ul className="space-y-2">{e.aggiornamenti.map((u, i) => (
            <li key={i} className="rounded-xl bg-fondo p-3 text-sm"><p>{u.testo}</p><p className="mt-1 text-xs text-tenue">{fa(u.created_at)}</p></li>
          ))}</ul>
        </section>
      )}

      {e.mio && e.stato === "pubblicato" && (
        <section className="scheda space-y-4">
          <h2 className="font-semibold">Gestisci l&apos;evento</h2>
          {!e.concluso && (
            <>
              <form onSubmit={pubblicaNovita} className="flex flex-wrap gap-2">
                <input className="campo flex-1" required minLength={2} maxLength={500} placeholder="Avvisa i partecipanti di una novità…"
                  value={novita} onChange={(ev) => setNovita(ev.target.value)} aria-label="Aggiornamento per i partecipanti" />
                <button className="btn-primario" disabled={azione.inCorso}>Invia</button>
              </form>
              <div className="flex flex-wrap items-end gap-2">
                <label className="text-sm"><span className="font-medium">Posti</span>
                  <input type="number" min={1} max={500} className="campo mt-1 w-28" value={capienza} placeholder="∞" onChange={(ev) => setCapienza(ev.target.value)} />
                </label>
                <button className="btn-secondario" onClick={cambiaCapienza} disabled={azione.inCorso}>Aggiorna posti</button>
                <button className="btn-secondario ml-auto text-errore" onClick={() => confirm("Annullare l'evento? I partecipanti saranno avvisati.") &&
                  esegui(() => api<Evento>(`/v1/events/${e.id}/annulla`, { metodo: "POST" }))}><Ban className="h-4 w-4" /> Annulla evento</button>
              </div>
            </>
          )}
        </section>
      )}

      {e.partecipanti && e.partecipanti.length > 0 && (
        <section className="scheda">
          <h2 className="mb-3 font-semibold">{e.mio ? "Partecipanti e richieste" : "Chi partecipa"}</h2>
          <ul className="divide-y divide-bordo">
            {e.partecipanti.map((p) => (
              <li key={p.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                <span className="flex items-center gap-2"><Avatar alias={p.alias} /><NomeUtente u={p} />
                  {e.mio && <span className="pillola bg-fondo text-tenue">{NOMI_STATO[p.stato] || p.stato}</span>}</span>
                {e.mio && p.id && !e.concluso && (
                  <span className="flex gap-1">
                    {p.stato !== "confermato" && <button className="btn-secondario px-2 text-sm" onClick={() => decidi(p.id!, "confermato")}><Check className="h-4 w-4" /> Conferma</button>}
                    {p.stato !== "rifiutato" && <button className="btn-secondario px-2 text-sm text-errore" onClick={() => decidi(p.id!, "rifiutato")}><X className="h-4 w-4" /> Rifiuta</button>}
                  </span>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}

      {e.concluso && <FeedbackDaLasciare contestoId={e.id} />}
      {scrivi && (
        <Modale titolo={`Scrivi a ${e.organizzatore.alias}`} onChiudi={() => setScrivi(false)}>
          <form onSubmit={contatta} className="space-y-3">
            <textarea className="campo" rows={3} required maxLength={2000} value={msg} onChange={(ev) => setMsg(ev.target.value)}
              placeholder="Ciao! Avrei una domanda sull'evento…" aria-label="Messaggio" />
            {azione.errore && <Avviso tipo="errore">{azione.errore}</Avviso>}
            <button className="btn-primario w-full" disabled={azione.inCorso}><MessageCircle className="h-4 w-4" /> Invia</button>
          </form>
        </Modale>
      )}
      {segnala && <Segnala oggettoTipo="evento" oggettoId={e.id} alias={e.organizzatore.alias} onChiudi={() => setSegnala(false)} />}
    </article>
  );
}
