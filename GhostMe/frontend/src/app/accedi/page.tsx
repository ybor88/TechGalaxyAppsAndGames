"use client";
// Accesso — © Roberto Di Flumeri
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import SchermataAccesso from "@/components/SchermataAccesso";
import { Avviso, useAzione } from "@/components/ui";
import { useSessione } from "@/lib/sessione";

export default function Accedi() {
  const { accedi, utente } = useSessione();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const { inCorso, errore, esegui } = useAzione();

  useEffect(() => {
    if (utente) router.replace("/app");
  }, [utente, router]);

  const invia = async (e: React.FormEvent) => {
    e.preventDefault();
    await esegui(() => accedi(email, password));
  };

  return (
    <SchermataAccesso titolo="Accedi">
      <form onSubmit={invia} className="space-y-4">
        <div>
          <label htmlFor="email" className="etichetta">Email</label>
          <input id="email" type="email" autoComplete="email" required className="campo" value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>
        <div>
          <label htmlFor="password" className="etichetta">Password</label>
          <input id="password" type="password" autoComplete="current-password" required className="campo" value={password} onChange={(e) => setPassword(e.target.value)} />
        </div>
        {errore && <Avviso tipo="errore">{errore}</Avviso>}
        <button className="btn-primario w-full" disabled={inCorso}>{inCorso ? "Accesso…" : "Accedi"}</button>
      </form>
      <p className="mt-4 text-sm text-tenue">
        Non hai un account? <Link href="/registrati" className="font-medium text-blu underline">Registrati</Link>
      </p>
    </SchermataAccesso>
  );
}
