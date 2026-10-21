"use client";
// Dettaglio di un annuncio, richiesta di aiuto o segnalazione — © Roberto Di Flumeri
import { Ban, CheckCircle2, Clock, Flag, MapPin, MessageCircle, Pencil, Send, Trash2, Undo2 } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import FeedbackDaLasciare from "@/components/Feedback";
import {
  Avatar, Avviso, Caricamento, Categoria, EtichettaStato, EtichettaTipo, Modale, NomeUtente, Segnala, useAzione,
} from "@/components/ui";
import { api, dataOraIt, fa, type Contenuto } from "@/lib/api";

export default function Annuncio() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [p, setP] = useState<Contenuto | null>(null);
  const [errore, setErrore] = useState<string | null>(null);
  const [foto, setFoto] = useState(0);
  const [messaggio, setMessaggio] = useState("");
  const [segnala, setSegnala] = useState(false);
  const [modifica, setModifica] = useState(false);
  const [bozza, setBozza] = useState({ titolo: "", descrizione: "", prezzo: "" });
  const [prenotaPer, setPrenotaPer] = useState("");
  const azione = useAzione();

  const carica = useCallback(() => api<Contenuto>(`/v1/posts/${id}`).then(setP).catch((e) => setErrore(e.message)), [id]);
  useEffect(() => { carica(); }, [carica]);

  if (errore) return <Avviso tipo="errore">{errore}. <Link href="/app" className="underline">Torna al quartiere</Link></Avviso>;
  if (!p) return <Caricamento />;

  const stato = (nuovo: string, prenotato_da?: string) => azione.esegui(async () => {
    setP(await api<Contenuto>(`/v1/posts/${p.id}/stato`, { metodo: "POST", corpo: { stato: nuovo, prenotato_da } }));
  });
  const contatta = (e: React.FormEvent) => {
    e.preventDefault();
    azione.esegui(async () => {
      const c = await api<{ id: string }>("/v1/conversations", { metodo: "POST",
        corpo: { contesto_tipo: "post", contesto_id: p.id, messaggio } });
      router.push(`/app/chat/${c.id}`);
    });
  };
  const salvaModifica = (e: React.FormEvent) => {
    e.preventDefault();
    azione.esegui(async () => {
      setP(await api<Contenuto>(`/v1/posts/${p.id}`, { metodo: "PATCH", corpo: { ...bozza, prezzo: bozza.prezzo || "" } }));
      setModifica(false);
    });
  };
  const blocca = () => confirm(`Bloccare ${p.autore.alias}? Non vedrete più i rispettivi contenuti e non potrete scrivervi.`) &&
    azione.esegui(async () => { await api(`/v1/blocks/${p.autore.id}`, { metodo: "POST" }); router.replace("/app"); });

  const aperto = p.stato === "pubblicato" || p.stato === "prenotato";
  const finale = p.tipo === "segnalazione" ? "risolto" : "chiuso";

  return (
    <article className="mx-auto max-w-3xl space-y-5">
      <Link href="/app" className="text-sm text-tenue hover:underline">← Il mio quartiere</Link>
      {p.avviso_emergenza && <Avviso tipo="errore"><strong>{p.avviso_emergenza}</strong></Avviso>}
      {p.stato === "rimosso" && <Avviso tipo="errore">Questo contenuto è stato rimosso dalla moderazione. Puoi presentare ricorso da Privacy e sicurezza.</Avviso>}

      {p.foto.length > 0 && (
        <div className="space-y-2">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={p.foto[foto].url} alt={`Foto ${foto + 1} di ${p.titolo}`} className="max-h-[420px] w-full rounded-2xl bg-fondo object-contain" />
          {p.foto.length > 1 && (
            <div className="flex gap-2">
              {p.foto.map((x, i) => (
                <button key={x.id} onClick={() => setFoto(i)} aria-label={`Mostra la foto ${i + 1}`} aria-pressed={i === foto}
                  className={i === foto ? "rounded-xl ring-2 ring-verde" : "rounded-xl opacity-70"}>
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={x.url} alt="" className="h-16 w-16 rounded-xl object-cover" />
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      <div className="scheda space-y-3">
        <div className="flex flex-wrap items-center gap-2">
          <EtichettaTipo tipo={p.tipo} /><Categoria tipo={p.tipo} nome={p.categoria} /><EtichettaStato stato={p.stato} />
          {p.scaduto && <span className="pillola bg-bordo text-tenue">Scaduto</span>}
        </div>
        <h1 className="text-2xl font-bold">{p.titolo}</h1>
        {p.prezzo && <p className="text-xl font-semibold text-verde">{p.prezzo}</p>}
        <p className="whitespace-pre-wrap">{p.descrizione}</p>
        <ul className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-tenue">
          {p.posizione.distanza && <li className="flex items-center gap-1"><MapPin className="h-4 w-4" /> {p.posizione.distanza} da te (zona approssimata)</li>}
          <li>Pubblicato {fa(p.created_at)}</li>
          {p.disponibilita && <li className="flex items-center gap-1"><Clock className="h-4 w-4" /> Disponibilità: {p.disponibilita}</li>}
          {p.scadenza && <li>{p.tipo === "aiuto" ? "Entro" : "Valido fino a"} {dataOraIt(p.scadenza)}</li>}
        </ul>
      </div>

      <div className="scheda flex flex-wrap items-center justify-between gap-3">
        <Link href={p.mio ? "/app/profilo" : `/app/utenti/${p.autore.id}`} className="flex items-center gap-3">
          <Avatar alias={p.autore.alias} />
          <span><NomeUtente u={p.autore} />{p.autore.attivita && <span className="block text-xs text-tenue">{p.autore.attivita}</span>}
            <span className="block text-xs text-verde underline">Vedi profilo e feedback</span></span>
        </Link>
        {!p.mio && (
          <div className="flex gap-2">
            <button className="btn-secondario" onClick={() => setSegnala(true)}><Flag className="h-4 w-4" /> Segnala</button>
            <button className="btn-secondario" onClick={blocca}><Ban className="h-4 w-4" /> Blocca</button>
          </div>
        )}
      </div>

      {azione.errore && <Avviso tipo="errore">{azione.errore}</Avviso>}

      {/* chi guarda: contatto in chat */}
      {!p.mio && aperto && (
        p.mia_conversazione_id ? (
          <Link href={`/app/chat/${p.mia_conversazione_id}`} className="btn-primario w-full"><MessageCircle className="h-4 w-4" /> Apri la conversazione</Link>
        ) : (
          <form onSubmit={contatta} className="scheda space-y-2">
            <label htmlFor="messaggio" className="etichetta">Scrivi a {p.autore.alias}</label>
            <textarea id="messaggio" className="campo" rows={2} required maxLength={2000} value={messaggio} onChange={(e) => setMessaggio(e.target.value)}
              placeholder={p.tipo === "aiuto" ? "Ciao! Posso darti una mano io…" : "Ciao! È ancora disponibile?"} />
            <p className="text-xs text-tenue">Il punto d&apos;incontro preciso lo concordate in chat.</p>
            <button className="btn-primario" disabled={azione.inCorso}><Send className="h-4 w-4" /> Invia messaggio</button>
          </form>
        )
      )}

      {/* autore: gestione */}
      {p.mio && p.stato !== "rimosso" && (
        <section className="scheda space-y-3">
          <h2 className="font-semibold">Gestisci</h2>
          <div className="flex flex-wrap gap-2">
            {p.stato === "bozza" && <button className="btn-primario" onClick={() => stato("pubblicato")}><Send className="h-4 w-4" /> Pubblica ora</button>}
            {aperto && (
              <button className="btn-secondario" onClick={() => { setBozza({ titolo: p.titolo, descrizione: p.descrizione, prezzo: p.prezzo || "" }); setModifica(true); }}>
                <Pencil className="h-4 w-4" /> Modifica</button>
            )}
            {p.stato === "prenotato" && <button className="btn-secondario" onClick={() => stato("pubblicato")}><Undo2 className="h-4 w-4" /> Annulla prenotazione</button>}
            {aperto && (
              <button className="btn-secondario" onClick={() => confirm(p.tipo === "segnalazione" ? "Segnare come risolta?" : "Segnare come concluso?") && stato(finale)}>
                <CheckCircle2 className="h-4 w-4" /> {p.tipo === "segnalazione" ? "Segna come risolta" : "Segna come concluso"}</button>
            )}
            <button className="btn-secondario text-errore" onClick={() => confirm("Eliminare definitivamente?") &&
              azione.esegui(async () => { await api(`/v1/posts/${p.id}`, { metodo: "DELETE" }); router.replace("/app/profilo"); })}>
              <Trash2 className="h-4 w-4" /> Elimina</button>
          </div>
          {p.stato === "pubblicato" && p.tipo !== "segnalazione" && (
            p.interlocutori && p.interlocutori.length > 0 ? (
              <div className="flex flex-wrap items-end gap-2 border-t border-bordo pt-3">
                <label className="text-sm"><span className="font-medium">Prenota per</span>
                  <select className="campo mt-1 w-auto" value={prenotaPer} onChange={(e) => setPrenotaPer(e.target.value)}>
                    <option value="">Scegli chi ti ha scritto…</option>
                    {p.interlocutori.map((u) => <option key={u.id} value={u.id || ""}>{u.alias}</option>)}
                  </select>
                </label>
                <button className="btn-primario" disabled={!prenotaPer} onClick={() => stato("prenotato", prenotaPer)}>Prenota</button>
              </div>
            ) : <p className="border-t border-bordo pt-3 text-sm text-tenue">Quando qualcuno ti scrive, potrai segnare il contenuto come prenotato per quella persona.</p>
          )}
          {p.interlocutori && p.interlocutori.length > 0 && (
            <div className="border-t border-bordo pt-3">
              <h3 className="mb-2 text-sm font-semibold">Ti hanno scritto</h3>
              <ul className="flex flex-wrap gap-2">
                {p.interlocutori.map((u) => (
                  <li key={u.conversazione_id}><Link href={`/app/chat/${u.conversazione_id}`} className="btn-secondario text-sm">
                    <MessageCircle className="h-4 w-4" /> {u.alias}</Link></li>
                ))}
              </ul>
            </div>
          )}
        </section>
      )}

      {p.stato === "chiuso" && <FeedbackDaLasciare contestoId={p.id} />}

      {segnala && <Segnala oggettoTipo="post" oggettoId={p.id} alias={p.autore.alias} onChiudi={() => setSegnala(false)}
        onFatto={(b) => b && setTimeout(() => router.replace("/app"), 1500)} />}
      {modifica && (
        <Modale titolo="Modifica" onChiudi={() => setModifica(false)}>
          <form onSubmit={salvaModifica} className="space-y-3">
            <input className="campo" required minLength={3} maxLength={120} aria-label="Titolo" value={bozza.titolo}
              onChange={(e) => setBozza({ ...bozza, titolo: e.target.value })} />
            <textarea className="campo" rows={4} required minLength={3} maxLength={2000} aria-label="Descrizione" value={bozza.descrizione}
              onChange={(e) => setBozza({ ...bozza, descrizione: e.target.value })} />
            {["offerta", "scambio", "regalo", "richiesta"].includes(p.tipo) && (
              <input className="campo" maxLength={40} aria-label="Prezzo" placeholder="Prezzo" value={bozza.prezzo}
                onChange={(e) => setBozza({ ...bozza, prezzo: e.target.value })} />
            )}
            {azione.errore && <Avviso tipo="errore">{azione.errore}</Avviso>}
            <button className="btn-primario w-full" disabled={azione.inCorso}>Salva</button>
          </form>
        </Modale>
      )}
    </article>
  );
}
