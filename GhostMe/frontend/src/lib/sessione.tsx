"use client";
// Sessione utente — © Roberto Di Flumeri

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { api, impostaToken, rinnovaSessione, type Utente } from "./api";

interface StatoSessione {
  utente: Utente | null;
  caricamento: boolean;
  accedi: (email: string, password: string) => Promise<void>;
  registrati: (dati: Record<string, unknown>) => Promise<void>;
  esci: () => Promise<void>;
  ricarica: () => Promise<void>;
}

const Ctx = createContext<StatoSessione | null>(null);

export function ProviderSessione({ children }: { children: React.ReactNode }) {
  const [utente, setUtente] = useState<Utente | null>(null);
  const [caricamento, setCaricamento] = useState(true);

  const ricarica = useCallback(async () => {
    try {
      setUtente(await api<Utente>("/v1/auth/me"));
    } catch {
      setUtente(null);
    }
  }, []);

  useEffect(() => {
    // al primo caricamento si prova a riprendere la sessione dal cookie httpOnly
    rinnovaSessione()
      .then((ok) => (ok ? ricarica() : undefined))
      .finally(() => setCaricamento(false));
  }, [ricarica]);

  const accedi = async (email: string, password: string) => {
    const r = await api<{ access_token: string }>("/v1/auth/login", { metodo: "POST", corpo: { email, password }, pubblica: true });
    impostaToken(r.access_token);
    await ricarica();
  };

  const registrati = async (dati: Record<string, unknown>) => {
    const r = await api<{ access_token: string }>("/v1/auth/register", { metodo: "POST", corpo: dati, pubblica: true });
    impostaToken(r.access_token);
    await ricarica();
  };

  const esci = async () => {
    try {
      await api("/v1/auth/logout", { metodo: "POST" });
    } finally {
      impostaToken(null);
      setUtente(null);
    }
  };

  return (
    <Ctx.Provider value={{ utente, caricamento, accedi, registrati, esci, ricarica }}>{children}</Ctx.Provider>
  );
}

export function useSessione() {
  const c = useContext(Ctx);
  if (!c) throw new Error("useSessione fuori da ProviderSessione");
  return c;
}
