"use client";
// Il mio profilo: dati visibili, quartiere, contenuti, feedback — © Roberto Di Flumeri
import clsx from "clsx";
import { MapPin, Pencil } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import FeedbackDaLasciare from "@/components/Feedback";
import RiepilogoReputazione, { type Reputazione } from "@/components/Reputazione";
import SchedaElemento from "@/components/SchedaElemento";
import { Avatar, Avviso, Caricamento, Modale, NomeUtente, Titolo, useAzione, Vuoto } from "@/components/ui";
import { api, dataIt, type Contenuto } from "@/lib/api";
import { useSessione } from "@/lib/sessione";

const SCHEDE = [["attivi", "Attivi"], ["bozze", "Bozze"], ["conclusi", "Conclusi"]] as const;

export default function Profilo() {
  const { utente, ricarica } = useSessione();
  const [miei, setMiei] = useState<Contenuto[] | null>(null);
  const [rep, setRep] = useState<Reputazione | null>(null);
  const [scheda, setScheda] = useState<(typeof SCHEDE)[number][0]>("attivi");
  const [modifica, setModifica] = useState(false);
  const [f, setF] = useState({ alias: "", bio: "", tipo_profilo: "privato", attivita: "" });
  const azione = useAzione();

  useEffect(() => {
    api<Contenuto[]>("/v1/me/posts").then(setMiei);
    api<Reputazione>("/v1/reputation/me").then(setRep);
  }, []);
  if (!utente) return <Caricamento />;

  const salva = (e: React.FormEvent) => {
    e.preventDefault();
    azione.esegui(async () => {
      await api("/v1/auth/me", { metodo: "PATCH", corpo: { ...f, attivita: f.tipo_profilo === "professionale" ? f.attivita : "" } });
      await ricarica(); setModifica(false);
    });
  };
  const filtrati = (miei || []).filter((p) => scheda === "bozze" ? p.stato === "bozza"
    : scheda === "conclusi" ? ["chiuso", "risolto", "rimosso"].includes(p.stato) : ["pubblicato", "prenotato"].includes(p.stato));

  return (
    <>
      <Titolo titolo="Il mio profilo" />
      <div className="grid gap-6 lg:grid-cols-[340px_1fr]">
        <aside className="space-y-4">
          <section className="scheda space-y-3">
            <div className="flex items-center gap-3">
              <Avatar alias={utente.alias} grande />
              <div><NomeUtente u={{ ...utente, id: utente.id }} />{utente.attivita && <p className="text-sm text-tenue">{utente.attivita}</p>}
                <p className="text-xs text-tenue">Nel quartiere dal {dataIt(utente.created_at)}</p></div>
            </div>
            <p className="text-sm">{utente.bio || <span className="text-tenue">Aggiungi due righe di presentazione per i vicini.</span>}</p>
            <button className="btn-secondario w-full" onClick={() => { setF({ alias: utente.alias, bio: utente.bio || "", tipo_profilo: utente.tipo_profilo, attivita: utente.attivita || "" }); setModifica(true); }}>
              <Pencil className="h-4 w-4" /> Modifica profilo</button>
          </section>
          {utente.area && (
            <section className="scheda space-y-2">
              <h2 className="flex items-center gap-1 font-semibold"><MapPin className="h-4 w-4 text-verde" /> {utente.area.nome}</h2>
              <p className="text-sm text-tenue">Raggio {(utente.area.raggio_m / 1000).toFixed(1).replace(".", ",")} km · posizione salvata come zona approssimata.</p>
              <Link href="/app/quartiere" className="btn-secondario w-full text-sm">Cambia quartiere o raggio</Link>
            </section>
          )}
          {rep && <RiepilogoReputazione r={rep} />}
        </aside>

        <div className="space-y-4">
          <FeedbackDaLasciare />
          <section>
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <h2 className="text-lg font-semibold">I miei contenuti</h2>
              <div className="flex gap-2" role="tablist">
                {SCHEDE.map(([id, nome]) => (
                  <button key={id} role="tab" aria-selected={scheda === id} onClick={() => setScheda(id)}
                    className={clsx("filtro", scheda === id ? "gradiente border-transparent text-white" : "border-bordo bg-pannello")}>{nome}</button>
                ))}
              </div>
            </div>
            {!miei ? <Caricamento /> : filtrati.length === 0 ? (
              <Vuoto titolo="Niente qui" testo={scheda === "bozze" ? "Le bozze salvate dal modulo di pubblicazione compariranno qui." : undefined}
                azione={<Link href="/app/pubblica" className="btn-primario">Pubblica</Link>} />
            ) : <ul className="space-y-3">{filtrati.map((p) => <li key={p.id}><SchedaElemento e={p} /></li>)}</ul>}
          </section>
        </div>
      </div>

      {modifica && (
        <Modale titolo="Modifica profilo" onChiudi={() => setModifica(false)}>
          <form onSubmit={salva} className="space-y-3">
            <label className="block text-sm"><span className="font-medium">Nome visibile</span>
              <input className="campo mt-1" required minLength={2} maxLength={40} value={f.alias} onChange={(e) => setF({ ...f, alias: e.target.value })} /></label>
            <label className="block text-sm"><span className="font-medium">Presentazione</span>
              <textarea className="campo mt-1" rows={3} maxLength={300} value={f.bio} onChange={(e) => setF({ ...f, bio: e.target.value })} /></label>
            <fieldset className="grid grid-cols-2 gap-2">
              <legend className="etichetta">Tipo di profilo</legend>
              {[["privato", "Privato"], ["professionale", "Professionale"]].map(([v, t]) => (
                <label key={v} className={clsx("cursor-pointer rounded-xl border p-2 text-center text-sm", f.tipo_profilo === v ? "border-verde bg-verde/10 font-semibold" : "border-bordo")}>
                  <input type="radio" name="tipo" className="sr-only" checked={f.tipo_profilo === v} onChange={() => setF({ ...f, tipo_profilo: v })} />{t}
                </label>
              ))}
            </fieldset>
            {f.tipo_profilo === "professionale" && (
              <input className="campo" required maxLength={80} placeholder="Nome dell'attività" aria-label="Nome dell'attività" value={f.attivita}
                onChange={(e) => setF({ ...f, attivita: e.target.value })} />
            )}
            {azione.errore && <Avviso tipo="errore">{azione.errore}</Avviso>}
            <button className="btn-primario w-full" disabled={azione.inCorso}>Salva</button>
          </form>
        </Modale>
      )}
    </>
  );
}
