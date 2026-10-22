"use client";
// Accesso: email e password, verifica in due passaggi (TOTP o codice di recupero) e SSO aziendale. © Roberto Di Flumeri
import { Building2, KeyRound, LogIn, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import Accoglienza from "@/components/Accoglienza";
import { Avviso, useAzione } from "@/components/ui";
import { useSessione } from "@/lib/sessione";

export default function Accedi() {
  const { utente, caricamento, accedi, verificaMfa } = useSessione();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [mfaToken, setMfaToken] = useState<string | null>(null);
  const [codice, setCodice] = useState("");
  const [sso, setSso] = useState(false);
  const [erroreSso, setErroreSso] = useState<string | null>(null);
  const { inCorso, errore, setErrore, esegui } = useAzione();

  useEffect(() => { if (!caricamento && utente) router.replace("/app"); }, [utente, caricamento, router]);
  useEffect(() => {
    const p = new URLSearchParams(window.location.search);
    if (p.get("sso_errore")) setErroreSso(p.get("sso_errore"));
  }, []);

  const invia = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => {
      const r = await accedi(email, password);
      if (r.mfa) setMfaToken(r.token);
      else router.replace("/app");
    });
  };

  const inviaMfa = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => {
      await verificaMfa(mfaToken!, codice.replace(/\s/g, ""));
      router.replace("/app");
    });
  };

  const avviaSso = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => {
      // verifica preliminare: se l'SSO non è configurato il server risponde con un errore leggibile
      const url = `/api/auth/sso/inizia?email=${encodeURIComponent(email)}`;
      const r = await fetch(url, { redirect: "manual" });
      if (r.type !== "opaqueredirect" && !r.ok) throw new Error((await r.json().catch(() => null))?.errore || "SSO non disponibile");
      window.location.href = url;
    });
  };

  if (mfaToken) {
    return (
      <Accoglienza>
        <h1 className="flex items-center gap-2 text-xl font-bold"><ShieldCheck className="h-5 w-5 text-primario" /> Verifica in due passaggi</h1>
        <p className="mt-1 text-sm text-tenue">Inserisci il codice a 6 cifre generato dall&apos;app di autenticazione, oppure uno dei codici di recupero.</p>
        <form onSubmit={inviaMfa} className="mt-5 space-y-4">
          <input className="campo text-center text-2xl tracking-[0.4em]" value={codice} onChange={(e) => setCodice(e.target.value)} autoFocus
            inputMode="text" autoComplete="one-time-code" maxLength={20} placeholder="000000" aria-label="Codice di verifica" required />
          {errore && <Avviso tipo="errore">{errore}</Avviso>}
          <button className="btn-primario w-full" disabled={inCorso || codice.length < 6}>Verifica e accedi</button>
          <button type="button" className="btn-secondario w-full" onClick={() => { setMfaToken(null); setCodice(""); setErrore(null); }}>Torna indietro</button>
        </form>
      </Accoglienza>
    );
  }

  return (
    <Accoglienza>
      <h1 className="text-xl font-bold">{sso ? "Accesso aziendale (SSO)" : "Accedi a Nexora"}</h1>
      <p className="mt-1 text-sm text-tenue">{sso ? "Verrai indirizzato al provider di identità della tua azienda (OpenID Connect)." : "Gestisci vendite, magazzino e fatture della tua azienda."}</p>
      {erroreSso && <Avviso tipo="errore" className="mt-4">{erroreSso}</Avviso>}
      <form onSubmit={sso ? avviaSso : invia} className="mt-5 space-y-4">
        <label className="block text-sm">
          <span className="etichetta">Email</span>
          <input className="campo" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" required autoFocus />
        </label>
        {!sso && (
          <label className="block text-sm">
            <span className="etichetta">Password</span>
            <input className="campo" type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
          </label>
        )}
        {errore && <Avviso tipo="errore">{errore}</Avviso>}
        <button className="btn-primario w-full" disabled={inCorso}>{sso ? <><Building2 className="h-4 w-4" /> Continua con SSO</> : <><LogIn className="h-4 w-4" /> Accedi</>}</button>
      </form>
      <div className="my-5 flex items-center gap-3 text-xs text-tenue"><span className="h-px flex-1 bg-bordo" /> oppure <span className="h-px flex-1 bg-bordo" /></div>
      <button className="btn-secondario w-full" onClick={() => { setSso(!sso); setErrore(null); }}>
        {sso ? <><KeyRound className="h-4 w-4" /> Accedi con email e password</> : <><Building2 className="h-4 w-4" /> Accedi con SSO aziendale</>}
      </button>
      <div className="mt-5 rounded-xl border border-primario/30 bg-primario/5 p-3 text-sm">
        <p className="font-medium">Azienda dimostrativa</p>
        <p className="text-tenue">admin@nexora.demo · NexoraDemo2026</p>
        <button type="button" className="link mt-1 text-sm font-medium" onClick={() => { setSso(false); setEmail("admin@nexora.demo"); setPassword("NexoraDemo2026"); }}>Compila automaticamente</button>
      </div>
      <p className="mt-5 text-center text-sm text-tenue">Nuova azienda? <Link href="/registrati" className="link font-medium">Registrati gratis</Link></p>
    </Accoglienza>
  );
}
