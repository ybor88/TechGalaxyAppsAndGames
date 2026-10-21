"use client";
// Servizi locali (F07): suggeriti dai vicini o schede dei professionisti — © Roberto Di Flumeri
import clsx from "clsx";
import { BadgeCheck, Flag, Heart, MessageCircle, Plus, Store, ThumbsUp } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { Avviso, Caricamento, Modale, NomeUtente, Segnala, Titolo, useAzione, Vuoto } from "@/components/ui";
import { api, type Posizione, type UtentePubblico } from "@/lib/api";
import { useCatalogo } from "@/lib/catalogo";
import { useSessione } from "@/lib/sessione";

interface Servizio {
  id: string; nome: string; categoria: string; descrizione: string; tipo_scheda: "suggerito" | "professionale";
  etichetta_scheda: string; contatto: string | null; zona: string; posizione: Posizione; autore: UtentePubblico; mio: boolean;
  consigli: number; consigliato_da_me: boolean; commenti: { testo: string; autore: UtentePubblico }[];
}

function Elenco() {
  const { utente } = useSessione();
  const catalogo = useCatalogo();
  const router = useRouter();
  const evidenziato = useSearchParams().get("id");
  const [categoria, setCategoria] = useState("");
  const [servizi, setServizi] = useState<Servizio[] | null>(null);
  const [nuovo, setNuovo] = useState(false);
  const [consiglia, setConsiglia] = useState<Servizio | null>(null);
  const [commento, setCommento] = useState("");
  const [segnala, setSegnala] = useState<string | null>(null);
  const [f, setF] = useState({ nome: "", categoria: "", descrizione: "", contatto: "", zona: "", tipo_scheda: "suggerito" });
  const azione = useAzione();

  const carica = () => api<Servizio[]>(`/v1/services${categoria ? `?categoria=${categoria}` : ""}`).then(setServizi).catch((e) => azione.setErrore(e.message));
  useEffect(() => { carica(); }, [categoria]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (evidenziato && servizi) document.getElementById(`s-${evidenziato}`)?.scrollIntoView({ block: "center" }); }, [evidenziato, servizi]);

  const crea = (e: React.FormEvent) => {
    e.preventDefault();
    azione.esegui(async () => {
      await api("/v1/services", { metodo: "POST", corpo: { ...f, contatto: f.contatto || null } });
      setNuovo(false); await carica();
    });
  };
  const inviaConsiglio = (e: React.FormEvent) => {
    e.preventDefault();
    if (!consiglia) return;
    azione.esegui(async () => {
      await api(`/v1/services/${consiglia.id}/consigli`, { metodo: "POST", corpo: { commento: commento || null } });
      setConsiglia(null); setCommento(""); await carica();
    });
  };
  const ritira = (s: Servizio) => azione.esegui(async () => { await api(`/v1/services/${s.id}/consigli`, { metodo: "DELETE" }); await carica(); });
  const scrivi = (s: Servizio) => azione.esegui(async () => {
    const c = await api<{ id: string }>("/v1/conversations", { metodo: "POST",
      corpo: { contesto_tipo: "servizio", contesto_id: s.id, messaggio: `Ciao! Ti scrivo per «${s.nome}».` } });
    router.push(`/app/chat/${c.id}`);
  });

  const categorie = catalogo?.categorie.servizio || {};
  const professionale = utente?.tipo_profilo === "professionale";
  return (
    <>
      <Titolo titolo="Servizi locali" sottotitolo="Artigiani, negozi e attività consigliati da chi abita qui."
        azioni={<button className="btn-primario" onClick={() => setNuovo(true)}><Plus className="h-4 w-4" /> Aggiungi</button>} />
      <div className="-mx-4 mb-4 flex gap-2 overflow-x-auto px-4 pb-1">
        {[["", "Tutti"], ...Object.entries(categorie)].map(([k, v]) => (
          <button key={k} onClick={() => setCategoria(k)} aria-pressed={categoria === k}
            className={clsx("filtro shrink-0", categoria === k ? "gradiente border-transparent text-white" : "border-bordo bg-pannello")}>{v}</button>
        ))}
      </div>
      {azione.errore && !nuovo && !consiglia && <Avviso tipo="errore">{azione.errore}</Avviso>}
      {!servizi ? <Caricamento /> : servizi.length === 0 ? (
        <Vuoto icona={Store} titolo="Nessun servizio in questa categoria" testo="Conosci un artigiano bravo? Suggeriscilo ai vicini." />
      ) : (
        <ul className="grid gap-3 md:grid-cols-2">
          {servizi.map((s) => (
            <li key={s.id} id={`s-${s.id}`} className={clsx("scheda flex flex-col gap-2", evidenziato === s.id && "border-verde ring-2 ring-verde/30")}>
              <div className="flex flex-wrap items-center gap-2">
                <span className={clsx("pillola", s.tipo_scheda === "professionale" ? "bg-arancio/15 text-arancio" : "bg-prato/15 text-verde")}>
                  {s.tipo_scheda === "professionale" ? <BadgeCheck className="h-3 w-3" /> : <Heart className="h-3 w-3" />} {s.etichetta_scheda}</span>
                <span className="pillola bg-fondo text-tenue">{categorie[s.categoria] || s.categoria}</span>
              </div>
              <h2 className="text-lg font-semibold">{s.nome}</h2>
              <p className="text-sm">{s.descrizione}</p>
              <p className="text-xs text-tenue">{s.zona}{s.posizione.distanza ? ` · ${s.posizione.distanza}` : ""}{s.contatto ? ` · ${s.contatto}` : ""}</p>
              <p className="text-xs text-tenue">{s.tipo_scheda === "suggerito" ? "Suggerito da " : "Gestito da "}<NomeUtente u={s.autore} /></p>
              {s.commenti.length > 0 && (
                <ul className="space-y-1 rounded-xl bg-fondo p-2 text-sm">
                  {s.commenti.slice(0, 2).map((c, i) => <li key={i}>«{c.testo}» <span className="text-xs text-tenue">— {c.autore.alias}</span></li>)}
                </ul>
              )}
              <div className="mt-auto flex flex-wrap items-center gap-2 pt-1">
                <span className="flex items-center gap-1 text-sm font-semibold text-verde"><ThumbsUp className="h-4 w-4" /> {s.consigli} {s.consigli === 1 ? "vicino lo consiglia" : "vicini lo consigliano"}</span>
                <span className="ml-auto flex gap-1">
                  {!s.mio && (s.consigliato_da_me
                    ? <button className="btn-secondario text-sm" onClick={() => ritira(s)}>Ritira consiglio</button>
                    : <button className="btn-secondario text-sm" onClick={() => { setConsiglia(s); azione.setErrore(null); }}>Consiglia</button>)}
                  {!s.mio && s.tipo_scheda === "professionale" && <button className="btn-secondario px-2" aria-label="Scrivi all'attività" onClick={() => scrivi(s)}><MessageCircle className="h-4 w-4" /></button>}
                  {!s.mio && <button className="btn-secondario px-2" aria-label="Segnala" onClick={() => setSegnala(s.id)}><Flag className="h-4 w-4" /></button>}
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}
      {nuovo && (
        <Modale titolo="Aggiungi un servizio" onChiudi={() => setNuovo(false)}>
          <form onSubmit={crea} className="space-y-3">
            <fieldset className="grid grid-cols-2 gap-2">
              <legend className="etichetta">Tipo di scheda</legend>
              {[["suggerito", "Lo consiglio", "Un'attività che conosci"], ["professionale", "È la mia attività", "Solo profili professionali"]].map(([v, t, d]) => (
                <label key={v} className={clsx("cursor-pointer rounded-xl border p-2 text-sm", f.tipo_scheda === v ? "border-verde bg-verde/10" : "border-bordo",
                  v === "professionale" && !professionale && "opacity-50")}>
                  <input type="radio" name="tipo_scheda" className="sr-only" disabled={v === "professionale" && !professionale}
                    checked={f.tipo_scheda === v} onChange={() => setF({ ...f, tipo_scheda: v })} />
                  <span className="block font-semibold">{t}</span><span className="text-xs text-tenue">{d}</span>
                </label>
              ))}
            </fieldset>
            <input className="campo" required minLength={2} maxLength={80} placeholder="Nome dell'attività" aria-label="Nome" value={f.nome} onChange={(e) => setF({ ...f, nome: e.target.value })} />
            <select className="campo" required value={f.categoria} onChange={(e) => setF({ ...f, categoria: e.target.value })} aria-label="Categoria">
              <option value="" disabled>Categoria…</option>
              {Object.entries(categorie).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
            <textarea className="campo" rows={3} required minLength={10} maxLength={1000} placeholder="Perché la consigli? Cosa offre?" aria-label="Descrizione"
              value={f.descrizione} onChange={(e) => setF({ ...f, descrizione: e.target.value })} />
            <input className="campo" required minLength={2} maxLength={80} placeholder="Zona (es. Isola, vicino al mercato)" aria-label="Zona" value={f.zona} onChange={(e) => setF({ ...f, zona: e.target.value })} />
            <input className="campo" maxLength={120} placeholder="Contatto pubblico dell'attività (facoltativo)" aria-label="Contatto" value={f.contatto} onChange={(e) => setF({ ...f, contatto: e.target.value })} />
            {azione.errore && <Avviso tipo="errore">{azione.errore}</Avviso>}
            <button className="btn-primario w-full" disabled={azione.inCorso}>Aggiungi</button>
          </form>
        </Modale>
      )}
      {consiglia && (
        <Modale titolo={`Consiglia ${consiglia.nome}`} onChiudi={() => setConsiglia(null)}>
          <form onSubmit={inviaConsiglio} className="space-y-3">
            <textarea className="campo" rows={2} maxLength={300} placeholder="Un commento per i vicini (facoltativo)" aria-label="Commento"
              value={commento} onChange={(e) => setCommento(e.target.value)} />
            {azione.errore && <Avviso tipo="errore">{azione.errore}</Avviso>}
            <button className="btn-primario w-full" disabled={azione.inCorso}><ThumbsUp className="h-4 w-4" /> Consiglia</button>
          </form>
        </Modale>
      )}
      {segnala && <Segnala oggettoTipo="servizio" oggettoId={segnala} onChiudi={() => setSegnala(null)} />}
    </>
  );
}

export default function Servizi() {
  return <Suspense fallback={<Caricamento />}><Elenco /></Suspense>;
}
