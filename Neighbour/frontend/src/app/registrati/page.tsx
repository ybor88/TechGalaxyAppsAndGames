"use client";
// Registrazione con consensi espliciti e separati (§8) — © Roberto Di Flumeri
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import SchermataAccesso from "@/components/SchermataAccesso";
import { Avviso, Interruttore, useAzione } from "@/components/ui";
import { useSessione } from "@/lib/sessione";

export default function Registrati() {
  const { registrati, utente } = useSessione();
  const router = useRouter();
  const [f, setF] = useState({ alias: "", email: "", password: "", tipo_profilo: "privato", attivita: "" });
  const [termini, setTermini] = useState(false);
  const [consensi, setConsensi] = useState({
    trattamento_dati: false, geolocalizzazione: false, notifiche: true, profilazione: false, analytics: false,
  });
  const { inCorso, errore, esegui, setErrore } = useAzione();

  useEffect(() => {
    if (utente) router.replace("/app/quartiere?benvenuto=1");
  }, [utente, router]);

  const invia = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!termini || !consensi.trattamento_dati) {
      setErrore("Per continuare accetta termini e linee guida e il trattamento dei dati.");
      return;
    }
    await esegui(() => registrati({ ...f, attivita: f.tipo_profilo === "professionale" ? f.attivita : null,
      accetto_termini: termini, consensi }));
  };

  const imposta = (k: keyof typeof consensi) => (v: boolean) => setConsensi((c) => ({ ...c, [k]: v }));

  return (
    <SchermataAccesso titolo="Entra nel tuo quartiere">
      <form onSubmit={invia} className="space-y-4">
        <div>
          <label htmlFor="alias" className="etichetta">Nome visibile ai vicini</label>
          <input id="alias" required minLength={2} maxLength={40} className="campo" value={f.alias} placeholder="Es. Giulia o Giulia R."
            onChange={(e) => setF({ ...f, alias: e.target.value })} aria-describedby="nota-alias" />
          <p id="nota-alias" className="mt-1 text-xs text-tenue">Non serve il cognome: i vicini vedono solo questo nome.</p>
        </div>
        <div>
          <label htmlFor="email" className="etichetta">Email</label>
          <input id="email" type="email" autoComplete="email" required className="campo" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} />
          <p className="mt-1 text-xs text-tenue">Non è mai mostrata a nessuno: la conserviamo solo come hash per l&apos;accesso.</p>
        </div>
        <div>
          <label htmlFor="password" className="etichetta">Password</label>
          <input id="password" type="password" autoComplete="new-password" required minLength={10} className="campo"
            value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} aria-describedby="regola-pwd" />
          <p id="regola-pwd" className="mt-1 text-xs text-tenue">Almeno 10 caratteri, con lettere e numeri.</p>
        </div>

        <fieldset className="space-y-2">
          <legend className="etichetta">Tipo di profilo</legend>
          <div className="grid grid-cols-2 gap-2">
            {[["privato", "Privato", "Residente del quartiere"], ["professionale", "Professionale", "Negozio, artigiano, attività"]].map(([v, t, d]) => (
              <label key={v} className={`cursor-pointer rounded-xl border p-3 text-sm ${f.tipo_profilo === v ? "border-verde bg-verde/10" : "border-bordo"}`}>
                <input type="radio" name="tipo" className="sr-only" checked={f.tipo_profilo === v} onChange={() => setF({ ...f, tipo_profilo: v })} />
                <span className="block font-semibold">{t}</span><span className="text-tenue">{d}</span>
              </label>
            ))}
          </div>
          {f.tipo_profilo === "professionale" && (
            <input className="campo" required maxLength={80} placeholder="Nome dell'attività" aria-label="Nome dell'attività"
              value={f.attivita} onChange={(e) => setF({ ...f, attivita: e.target.value })} />
          )}
        </fieldset>

        <fieldset className="space-y-3 rounded-xl border border-bordo p-3">
          <legend className="px-1 text-sm font-semibold">Consensi</legend>
          <Interruttore attivo={consensi.trattamento_dati} onChange={imposta("trattamento_dati")}
            etichetta="Trattamento dei dati (obbligatorio)" descrizione="Profilo, quartiere e contenuti che pubblichi." />
          <Interruttore attivo={consensi.geolocalizzazione} onChange={imposta("geolocalizzazione")}
            etichetta="Posizione del dispositivo (facoltativo)" descrizione="Solo per proporti il quartiere: salviamo una zona approssimata, mai l'indirizzo." />
          <Interruttore attivo={consensi.notifiche} onChange={imposta("notifiche")}
            etichetta="Notifiche (facoltativo)" descrizione="Avvisi di quartiere e messaggi, con ore di silenzio che scegli tu." />
          <Interruttore attivo={consensi.profilazione} onChange={imposta("profilazione")}
            etichetta="Feed «Per te» (facoltativo)" descrizione="Mostra prima le categorie e i gruppi che segui." />
          <Interruttore attivo={consensi.analytics} onChange={imposta("analytics")}
            etichetta="Statistiche d'uso (facoltativo)" descrizione="Solo conteggi pseudonimi, mai i contenuti." />
          <p className="text-xs text-tenue">Puoi cambiare idea in qualsiasi momento da Privacy e sicurezza.</p>
        </fieldset>

        <label className="flex items-start gap-2 text-sm">
          <input type="checkbox" className="mt-1 h-4 w-4" checked={termini} onChange={(e) => setTermini(e.target.checked)} />
          <span>Ho letto e accetto <Link href="/termini" className="text-verde underline" target="_blank">termini d&apos;uso e linee guida
            della community</Link>. Neighbour non sostituisce i servizi di emergenza (112).</span>
        </label>

        {errore && <Avviso tipo="errore">{errore}</Avviso>}
        <button className="btn-primario w-full" disabled={inCorso}>{inCorso ? "Creazione…" : "Crea account"}</button>
      </form>
      <p className="mt-4 text-sm text-tenue">
        Hai già un account? <Link href="/accedi" className="font-medium text-verde underline">Accedi</Link>
      </p>
    </SchermataAccesso>
  );
}
