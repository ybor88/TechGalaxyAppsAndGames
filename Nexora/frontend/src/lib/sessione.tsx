"use client";
// Sessione utente: accesso con password, verifica in due passaggi (TOTP) e SSO. © Roberto Di Flumeri

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { api, impostaToken, rinnovaSessione, type Permesso, type Utente } from "./api";

type EsitoAccesso = { mfa: false } | { mfa: true; token: string };

interface StatoSessione {
  utente: Utente | null;
  caricamento: boolean;
  accedi: (email: string, password: string) => Promise<EsitoAccesso>;
  verificaMfa: (token: string, codice: string) => Promise<void>;
  registrati: (dati: Record<string, unknown>) => Promise<void>;
  esci: () => Promise<void>;
  ricarica: () => Promise<void>;
  puo: (p: Permesso) => boolean;
}

const Ctx = createContext<StatoSessione | null>(null);

export function ProviderSessione({ children }: { children: React.ReactNode }) {
  const [utente, setUtente] = useState<Utente | null>(null);
  const [caricamento, setCaricamento] = useState(true);

  const ricarica = useCallback(async () => {
    try { setUtente(await api<Utente>("/api/auth/me")); } catch { setUtente(null); }
  }, []);

  useEffect(() => {
    // al primo caricamento si riprende la sessione dal cookie httpOnly (anche dopo il ritorno dall'SSO)
    rinnovaSessione().then((ok) => (ok ? ricarica() : undefined)).finally(() => setCaricamento(false));
  }, [ricarica]);

  const accedi = async (email: string, password: string): Promise<EsitoAccesso> => {
    const r = await api<{ access_token?: string; mfa_richiesta?: boolean; mfa_token?: string }>("/api/auth/login", { metodo: "POST", corpo: { email, password }, pubblica: true });
    if (r.mfa_richiesta) return { mfa: true, token: r.mfa_token! };
    impostaToken(r.access_token!);
    await ricarica();
    return { mfa: false };
  };

  const verificaMfa = async (token: string, codice: string) => {
    const r = await api<{ access_token: string }>("/api/auth/mfa/verifica", { metodo: "POST", corpo: { mfa_token: token, codice }, pubblica: true });
    impostaToken(r.access_token);
    await ricarica();
  };

  const registrati = async (dati: Record<string, unknown>) => {
    const r = await api<{ access_token: string }>("/api/auth/register", { metodo: "POST", corpo: dati, pubblica: true });
    impostaToken(r.access_token);
    await ricarica();
  };

  const esci = async () => {
    try { await api("/api/auth/logout", { metodo: "POST" }); } catch {} finally {
      impostaToken(null);
      setUtente(null);
    }
  };

  const puo = (p: Permesso) => !!utente?.permessi.includes(p);

  return <Ctx.Provider value={{ utente, caricamento, accedi, verificaMfa, registrati, esci, ricarica, puo }}>{children}</Ctx.Provider>;
}

export function useSessione() {
  const c = useContext(Ctx);
  if (!c) throw new Error("useSessione fuori da ProviderSessione");
  return c;
}
