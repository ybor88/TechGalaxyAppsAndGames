"use client";
// Registrazione di una nuova azienda con il primo amministratore. © Roberto Di Flumeri
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import Accoglienza from "@/components/Accoglienza";
import { Avviso, Campo, useAzione } from "@/components/ui";
import { useSessione } from "@/lib/sessione";

export default function Registrati() {
  const { registrati } = useSessione();
  const router = useRouter();
  const [d, setD] = useState({ ragioneSociale: "", partitaIva: "", indirizzo: "", cap: "", citta: "", provincia: "", nome: "", email: "", password: "" });
  const [privacy, setPrivacy] = useState(false);
  const { inCorso, errore, esegui } = useAzione();
  const set = (k: keyof typeof d) => (e: React.ChangeEvent<HTMLInputElement>) => setD({ ...d, [k]: e.target.value });

  const invia = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => {
      await registrati({ ...d, provincia: d.provincia.toUpperCase() });
      router.replace("/app");
    });
  };

  return (
    <Accoglienza larga>
      <h1 className="text-xl font-bold">Crea la tua azienda su Nexora</h1>
      <p className="mt-1 text-sm text-tenue">Bastano pochi dati: il resto (magazzini, utenti, integrazioni) si configura dopo.</p>
      <form onSubmit={invia} className="mt-5 space-y-5">
        <fieldset className="grid gap-3 sm:grid-cols-2">
          <legend className="mb-2 text-sm font-semibold text-primario">Azienda</legend>
          <Campo etichetta="Ragione sociale" className="sm:col-span-2"><input className="campo" value={d.ragioneSociale} onChange={set("ragioneSociale")} required minLength={2} /></Campo>
          <Campo etichetta="Partita IVA" aiuto="11 cifre: viene verificata la cifra di controllo"><input className="campo" value={d.partitaIva} onChange={set("partitaIva")} required inputMode="numeric" pattern="(IT)?\d{11}" /></Campo>
          <Campo etichetta="Indirizzo"><input className="campo" value={d.indirizzo} onChange={set("indirizzo")} required /></Campo>
          <Campo etichetta="CAP"><input className="campo" value={d.cap} onChange={set("cap")} required pattern="\d{5}" inputMode="numeric" /></Campo>
          <div className="grid grid-cols-[1fr_5rem] gap-3">
            <Campo etichetta="Città"><input className="campo" value={d.citta} onChange={set("citta")} required /></Campo>
            <Campo etichetta="Prov."><input className="campo uppercase" value={d.provincia} onChange={set("provincia")} required maxLength={2} minLength={2} /></Campo>
          </div>
        </fieldset>
        <fieldset className="grid gap-3 sm:grid-cols-2">
          <legend className="mb-2 text-sm font-semibold text-primario">Amministratore</legend>
          <Campo etichetta="Nome e cognome"><input className="campo" value={d.nome} onChange={set("nome")} required autoComplete="name" /></Campo>
          <Campo etichetta="Email"><input className="campo" type="email" value={d.email} onChange={set("email")} required autoComplete="email" /></Campo>
          <Campo etichetta="Password" aiuto="Almeno 10 caratteri, con lettere e numeri" className="sm:col-span-2">
            <input className="campo" type="password" value={d.password} onChange={set("password")} required minLength={10} autoComplete="new-password" />
          </Campo>
        </fieldset>
        <label className="flex items-start gap-2 text-sm">
          <input type="checkbox" className="mt-1" checked={privacy} onChange={(e) => setPrivacy(e.target.checked)} required />
          <span>Ho letto l&apos;informativa privacy: i dati sono trattati solo per l&apos;erogazione del servizio (GDPR).</span>
        </label>
        {errore && <Avviso tipo="errore">{errore}</Avviso>}
        <button className="btn-primario w-full" disabled={inCorso || !privacy}>Crea azienda e accedi</button>
      </form>
      <p className="mt-5 text-center text-sm text-tenue">Hai già un account? <Link href="/accedi" className="link font-medium">Accedi</Link></p>
    </Accoglienza>
  );
}
