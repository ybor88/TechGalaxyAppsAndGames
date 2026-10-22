// Client API Nexora — © Roberto Di Flumeri
// L'access token resta solo in memoria; il refresh token è un cookie httpOnly gestito dal server (ruotato a ogni uso).

let accessToken: string | null = null;
let rinnovoInCorso: Promise<boolean> | null = null;

export function impostaToken(t: string | null) {
  accessToken = t;
}

export class ErroreApi extends Error {
  constructor(public stato: number, messaggio: string, public dettagli?: unknown) {
    super(messaggio);
  }
}

function messaggioDa(stato: number, corpo: unknown): string {
  const e = (corpo as { errore?: unknown })?.errore;
  if (typeof e === "string") return e;
  if (stato === 401) return "Sessione scaduta: accedi di nuovo.";
  if (stato === 403) return "Non hai i permessi per questa operazione.";
  if (stato === 429) return "Troppe richieste: riprova tra un minuto.";
  if (stato >= 500) return "Il server non risponde correttamente. Riprova tra poco.";
  return "Operazione non riuscita.";
}

export async function rinnovaSessione(): Promise<boolean> {
  if (!rinnovoInCorso) {
    rinnovoInCorso = fetch("/api/auth/refresh", { method: "POST", credentials: "same-origin" })
      .then(async (r) => {
        if (!r.ok || r.status === 204) {
          impostaToken(null);
          return false;
        }
        impostaToken((await r.json()).access_token);
        return true;
      })
      .catch(() => false)
      .finally(() => { rinnovoInCorso = null; });
  }
  return rinnovoInCorso;
}

type Opzioni = { metodo?: string; corpo?: unknown; pubblica?: boolean };

export async function api<T = any>(percorso: string, opz: Opzioni = {}, riprova = true): Promise<T> {
  const headers: Record<string, string> = {};
  if (opz.corpo !== undefined) headers["Content-Type"] = "application/json";
  if (accessToken && !opz.pubblica) headers.Authorization = `Bearer ${accessToken}`;
  let r: Response;
  try {
    r = await fetch(percorso, { method: opz.metodo || "GET", headers, credentials: "same-origin",
      body: opz.corpo !== undefined ? JSON.stringify(opz.corpo) : undefined });
  } catch {
    throw new ErroreApi(0, "Connessione assente: controlla la rete e riprova.");
  }
  // access token scaduto: un solo tentativo di rinnovo automatico
  if (r.status === 401 && riprova && !opz.pubblica && !percorso.startsWith("/api/auth/")) {
    if (await rinnovaSessione()) return api<T>(percorso, opz, false);
  }
  if (r.status === 204) return undefined as T;
  const corpo = await r.json().catch(() => null);
  if (!r.ok) throw new ErroreApi(r.status, messaggioDa(r.status, corpo), (corpo as { dettagli?: unknown })?.dettagli);
  return corpo as T;
}

/** Scarica un file protetto (PDF, XML, CSV, PNG) come Blob, con rinnovo della sessione se serve. */
export async function blob(percorso: string): Promise<Blob> {
  const invia = () => fetch(percorso, { headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {}, credentials: "same-origin" });
  let r = await invia();
  if (r.status === 401 && (await rinnovaSessione())) r = await invia();
  if (!r.ok) {
    const corpo = await r.json().catch(() => null);
    throw new ErroreApi(r.status, messaggioDa(r.status, corpo));
  }
  return r.blob();
}

export async function scarica(percorso: string, nomeFile: string) {
  const url = URL.createObjectURL(await blob(percorso));
  const a = document.createElement("a");
  a.href = url;
  a.download = nomeFile;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 5000);
}

/** Apre un PDF protetto in una nuova scheda (la finestra si apre subito per non essere bloccata dal browser). */
export async function apriPdf(percorso: string) {
  const finestra = window.open("", "_blank");
  try {
    const url = URL.createObjectURL(await blob(percorso));
    if (finestra) finestra.location.href = url;
    else window.location.href = url;
  } catch (e) {
    finestra?.close();
    throw e;
  }
}

export async function caricaFile(percorso: string, file: File) {
  const dati = new FormData();
  dati.append("file", file);
  const invia = () => fetch(percorso, { method: "POST", body: dati, credentials: "same-origin",
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {} });
  let r = await invia();
  if (r.status === 401 && (await rinnovaSessione())) r = await invia();
  const corpo = await r.json().catch(() => null);
  if (!r.ok) throw new ErroreApi(r.status, messaggioDa(r.status, corpo));
  return corpo;
}

export const qs = (p: Record<string, string | number | boolean | null | undefined>) => {
  const s = new URLSearchParams();
  for (const [k, v] of Object.entries(p)) if (v !== undefined && v !== null && v !== "") s.set(k, String(v));
  const t = s.toString();
  return t ? `?${t}` : "";
};

// ── Tipi principali ────────────────────────────────────────────────────────

export type Permesso = "catalogo" | "clienti" | "fornitori" | "ordini" | "spedizioni" | "magazzino" | "acquisti" | "documenti" | "pagamenti" | "ai" | "integrazioni" | "impostazioni";

export interface Utente {
  id: string; email: string; nome: string; ruolo: string; mfaAttivo: boolean; permessi: Permesso[];
  azienda: { id: string; ragioneSociale: string; partitaIva: string; ssoAttivo: boolean };
}

export interface Articolo {
  varianteId: string; prodottoId: string; sku: string; nome: string; barcode: string | null; prezzo: number; costo: number; aliquotaIva: number;
  unita: string; gestioneLotti: boolean; gestioneSeriali: boolean; giacenza: number; riservata: number; disponibile: number;
}

export interface Magazzino { id: string; codice: string; nome: string; citta: string | null; indirizzo: string | null; principale: boolean; attivo: boolean; articoli: number; valore: number; pezzi: number }

export interface Alert {
  id: string; tipo: string; gravita: "info" | "attenzione" | "critico"; titolo: string; messaggio: string; entita: string | null; entitaId: string | null;
  letto: boolean; risolto: string | null; createdAt: string;
}
