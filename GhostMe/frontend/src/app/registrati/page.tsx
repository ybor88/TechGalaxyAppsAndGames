"use client";
// Registrazione con consensi espliciti — © Roberto Di Flumeri
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import SchermataAccesso from "@/components/SchermataAccesso";
import { Avviso, Interruttore, useAzione } from "@/components/ui";
import { useSessione } from "@/lib/sessione";

export default function Registrati() {
  const { registrati, utente } = useSessione();
  const router = useRouter();
  const [f, setF] = useState({ nome: "", email: "", password: "" });
  const [termini, setTermini] = useState(false);
  const [consensi, setConsensi] = useState({ trattamento_profilo: false, elaborazione_ai: true, condivisione: false, analytics: false });
  const { inCorso, errore, esegui, setErrore } = useAzione();

  useEffect(() => {
    if (utente) router.replace("/app/profilo?benvenuto=1");
  }, [utente, router]);

  const invia = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!termini || !consensi.trattamento_profilo) {
      setErrore("Per continuare accetta i termini d'uso e il trattamento del profilo.");
      return;
    }
    await esegui(() => registrati({ ...f, accetto_termini: termini, consensi }));
  };

  const imposta = (k: keyof typeof consensi) => (v: boolean) => setConsensi((c) => ({ ...c, [k]: v }));

  return (
    <SchermataAccesso titolo="Crea il tuo alter ego">
      <form onSubmit={invia} className="space-y-4">
        <div>
          <label htmlFor="nome" className="etichetta">Come vuoi essere chiamato</label>
          <input id="nome" required maxLength={60} className="campo" value={f.nome} onChange={(e) => setF({ ...f, nome: e.target.value })} />
        </div>
        <div>
          <label htmlFor="email" className="etichetta">Email</label>
          <input id="email" type="email" autoComplete="email" required className="campo" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} />
          <p className="mt-1 text-xs text-tenue">La conserviamo solo in forma cifrata (hash), per il login.</p>
        </div>
        <div>
          <label htmlFor="password" className="etichetta">Password</label>
          <input id="password" type="password" autoComplete="new-password" required minLength={10} className="campo"
            value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} aria-describedby="regola-pwd" />
          <p id="regola-pwd" className="mt-1 text-xs text-tenue">Almeno 10 caratteri, con lettere e numeri.</p>
        </div>

        <fieldset className="space-y-3 rounded-xl border border-bordo p-3">
          <legend className="px-1 text-sm font-semibold">Consensi</legend>
          <Interruttore attivo={consensi.trattamento_profilo} onChange={imposta("trattamento_profilo")}
            etichetta="Trattamento del profilo (obbligatorio)" descrizione="Conservare le dichiarazioni e i contenuti che inserisci, cifrati." />
          <Interruttore attivo={consensi.elaborazione_ai} onChange={imposta("elaborazione_ai")}
            etichetta="Elaborazione AI (facoltativo)" descrizione="Necessario per chat e simulatore. Solo sui dati che autorizzi." />
          <Interruttore attivo={consensi.condivisione} onChange={imposta("condivisione")}
            etichetta="Condivisione (facoltativo)" descrizione="Link condivisi e test di compatibilità con altri utenti." />
          <Interruttore attivo={consensi.analytics} onChange={imposta("analytics")}
            etichetta="Statistiche d'uso anonime (facoltativo)" descrizione="Solo conteggi d'uso, mai i contenuti." />
          <p className="text-xs text-tenue">Puoi cambiare idea in qualsiasi momento da Privacy e dati.</p>
        </fieldset>

        <label className="flex items-start gap-2 text-sm">
          <input type="checkbox" className="mt-1 h-4 w-4" checked={termini} onChange={(e) => setTermini(e.target.checked)} />
          <span>
            Ho letto e accetto i <Link href="/termini" className="text-blu underline" target="_blank">termini d&apos;uso</Link>: Ghost Me è
            uno strumento di riflessione personale, non sostituisce professionisti della salute, legali o finanziari.
          </span>
        </label>

        {errore && <Avviso tipo="errore">{errore}</Avviso>}
        <button className="btn-primario w-full" disabled={inCorso}>{inCorso ? "Creazione…" : "Crea account"}</button>
      </form>
      <p className="mt-4 text-sm text-tenue">
        Hai già un account? <Link href="/accedi" className="font-medium text-blu underline">Accedi</Link>
      </p>
    </SchermataAccesso>
  );
}
