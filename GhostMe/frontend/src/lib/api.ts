// Client API Ghost Me — © Roberto Di Flumeri
// L'access token resta solo in memoria; il refresh token è un cookie httpOnly gestito dal server.

let accessToken: string | null = null;
let rinnovoInCorso: Promise<boolean> | null = null;
const ascoltatori = new Set<(connesso: boolean) => void>();

export function impostaToken(t: string | null) {
  accessToken = t;
  ascoltatori.forEach((f) => f(!!t));
}

export function suCambioSessione(f: (connesso: boolean) => void) {
  ascoltatori.add(f);
  return () => ascoltatori.delete(f);
}

export class ErroreApi extends Error {
  constructor(public stato: number, messaggio: string) {
    super(messaggio);
  }
}

function messaggioDa(stato: number, corpo: unknown): string {
  const d = (corpo as { detail?: unknown })?.detail;
  if (typeof d === "string") return d;
  if (Array.isArray(d) && d.length) {
    // il backend restituisce già messaggi in italiano che includono il nome del campo
    const p = d[0] as { msg?: string };
    return (p.msg || "Dati non validi").replace(/^Value error, /, "");
  }
  if (stato === 429) return "Troppe richieste: riprova tra un minuto.";
  if (stato >= 500) return "Il server non risponde correttamente. Riprova tra poco.";
  return "Operazione non riuscita.";
}

export async function rinnovaSessione(): Promise<boolean> {
  if (!rinnovoInCorso) {
    rinnovoInCorso = fetch("/v1/auth/refresh", { method: "POST", credentials: "same-origin" })
      .then(async (r) => {
        if (!r.ok || r.status === 204) {
          impostaToken(null);
          return false;
        }
        impostaToken((await r.json()).access_token);
        return true;
      })
      .catch(() => false)
      .finally(() => {
        rinnovoInCorso = null;
      });
  }
  return rinnovoInCorso;
}

type Opzioni = { metodo?: string; corpo?: unknown; pubblica?: boolean; testo?: boolean };

export async function api<T = unknown>(percorso: string, opz: Opzioni = {}, riprova = true): Promise<T> {
  const headers: Record<string, string> = {};
  if (opz.corpo !== undefined) headers["Content-Type"] = "application/json";
  if (accessToken && !opz.pubblica) headers["Authorization"] = `Bearer ${accessToken}`;
  let r: Response;
  try {
    r = await fetch(percorso, {
      method: opz.metodo || "GET",
      headers,
      body: opz.corpo !== undefined ? JSON.stringify(opz.corpo) : undefined,
      credentials: "same-origin",
    });
  } catch {
    throw new ErroreApi(0, "Connessione assente: controlla la rete e riprova.");
  }
  // access token scaduto: un solo tentativo di rinnovo automatico
  if (r.status === 401 && riprova && !opz.pubblica && percorso !== "/v1/auth/refresh") {
    if (await rinnovaSessione()) return api<T>(percorso, opz, false);
  }
  if (r.status === 204) return undefined as T;
  const corpo = opz.testo ? await r.text() : await r.json().catch(() => null);
  if (!r.ok) throw new ErroreApi(r.status, messaggioDa(r.status, corpo));
  return corpo as T;
}

export async function scarica(percorso: string, nomeFile: string) {
  const r = await fetch(percorso, { headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {} });
  if (!r.ok) throw new ErroreApi(r.status, "Download non riuscito");
  const url = URL.createObjectURL(await r.blob());
  const a = document.createElement("a");
  a.href = url;
  a.download = nomeFile;
  a.click();
  URL.revokeObjectURL(url);
}

// ── Tipi ────────────────────────────────────────────────────────────────────

export const CATEGORIE: Record<string, string> = {
  valori: "Valori",
  abitudini: "Abitudini",
  gusti: "Gusti",
  obiettivi: "Obiettivi",
  stile_comunicativo: "Stile comunicativo",
  altro: "Altro",
};

export interface Utente {
  id: string;
  nome_visualizzato: string | null;
  ruolo: "utente" | "moderatore";
  locale: string;
  motore_ai: "locale" | "ollama" | "auto";
  conservazione_chat_giorni: number | null;
  onboarding_completato: boolean;
  created_at: string;
}

export interface Dichiarazione {
  id: string;
  testo: string;
  categoria: string;
  peso: number;
  fonte: string;
  chiave_domanda: string | null;
  valido_da: string | null;
  valido_a: string | null;
  visibilita: "privata" | "condivisibile";
  usa_in_ai: boolean;
  created_at: string;
  updated_at: string;
}

export interface Fonte {
  id: string;
  tipo: string;
  categoria: string;
  estratto: string;
}

export interface Messaggio {
  id: string;
  ruolo: "utente" | "alter_ego";
  contenuto: string;
  fonti?: Fonte[] | null;
  confidenza?: "bassa" | "media" | "alta" | null;
  motore?: string | null;
  modello?: string | null;
  prompt_version?: string | null;
  policy_version?: string | null;
  created_at: string;
}

export interface Conversazione {
  id: string;
  titolo: string;
  snapshot_id: string | null;
  created_at: string;
  updated_at: string;
  messaggi?: Messaggio[];
}

export interface Versione {
  id: string;
  versione: number;
  tipo: "reale" | "simulazione_futura";
  etichetta: string;
  data_riferimento: string;
  riepilogo: string;
  impronta: string;
  created_at: string;
  dati?: Record<string, unknown>[];
  integro?: boolean;
}

export const dataIt = (s: string | null | undefined) =>
  s ? new Date(s.length === 10 ? s + "T00:00:00" : s + (s.endsWith("Z") ? "" : "Z")).toLocaleDateString("it-IT") : "—";
export const dataOraIt = (s: string) =>
  new Date(s + (s.endsWith("Z") ? "" : "Z")).toLocaleString("it-IT", { dateStyle: "short", timeStyle: "short" });
