// Client API Neighbour — © Roberto Di Flumeri
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

export async function caricaFoto(file: File): Promise<Foto> {
  const dati = new FormData();
  dati.append("file", file);
  const invia = () => fetch("/v1/files", { method: "POST", body: dati, credentials: "same-origin",
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {} });
  let r = await invia();
  if (r.status === 401 && (await rinnovaSessione())) r = await invia();
  const corpo = await r.json().catch(() => null);
  if (!r.ok) throw new ErroreApi(r.status, messaggioDa(r.status, corpo));
  return corpo as Foto;
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

export interface Area { nome: string; lat: number; lon: number; raggio_m: number }

export interface Utente {
  id: string;
  alias: string;
  bio: string | null;
  tipo_profilo: "privato" | "professionale";
  attivita: string | null;
  ruolo: "utente" | "moderatore";
  accetta_messaggi: "contesto" | "nessuno";
  created_at: string;
  area: Area | null;
}

export interface UtentePubblico { id: string | null; alias: string; tipo_profilo: "privato" | "professionale"; attivita: string | null }
export interface Posizione { lat: number; lon: number; distanza?: string }
export interface Foto { id: string; url: string; larghezza: number; altezza: number }

export interface Contenuto {
  id: string;
  genere: "post";
  tipo: string;
  categoria: string;
  titolo: string;
  descrizione: string;
  stato: "bozza" | "pubblicato" | "prenotato" | "chiuso" | "risolto" | "rimosso";
  scadenza: string | null;
  disponibilita: string | null;
  prezzo: string | null;
  posizione: Posizione;
  autore: UtentePubblico;
  foto: Foto[];
  gruppo_id: string | null;
  created_at: string;
  updated_at: string;
  mio: boolean;
  scaduto: boolean;
  prenotato_da?: string | null;
  raggio_m?: number;
  interlocutori?: (UtentePubblico & { conversazione_id: string })[];
  mia_conversazione_id?: string | null;
  avviso_emergenza?: string;
  vicini_avvisati?: number;
}

export interface Evento {
  id: string;
  genere: "evento";
  tipo: "evento";
  titolo: string;
  descrizione: string;
  categoria: string;
  inizio: string;
  fine: string | null;
  luogo: string;
  posizione: Posizione;
  capienza: number | null;
  modalita: "libera" | "su_approvazione";
  stato: "pubblicato" | "annullato" | "rimosso";
  concluso: boolean;
  gruppo_id: string | null;
  created_at: string;
  organizzatore: UtentePubblico;
  mio: boolean;
  confermati: number;
  in_lista_attesa: number;
  posti_liberi: number | null;
  mia_partecipazione: "confermato" | "in_attesa" | "lista_attesa" | "rifiutato" | null;
  aggiornamenti?: { testo: string; created_at: string }[];
  partecipanti?: (UtentePubblico & { stato: string; dal?: string })[];
}

export type ElementoFeed = Contenuto | Evento;

export interface Conversazione {
  id: string; contesto_tipo: string; contesto_id: string; contesto_titolo: string; con: UtentePubblico;
  stato: "attiva" | "chiusa" | "bloccata"; updated_at: string; non_lette: number; sono_richiedente: boolean;
  ultimo: { testo: string; mio: boolean; created_at: string } | null;
  messaggi?: { id: string; testo: string; mio: boolean; letto: boolean; created_at: string }[];
}


export interface Catalogo {
  tipi_post: Record<string, string>;
  categorie: Record<"mercatino" | "aiuto" | "segnalazione" | "evento" | "gruppo" | "servizio", Record<string, string>>;
  motivi_segnalazione: Record<string, string>;
  decisioni_moderazione: Record<string, string>;
  raggio: { min: number; max: number };
  avviso_emergenza: string;
}

export const TIPI_MERCATINO = ["offerta", "richiesta", "regalo", "scambio"];

export function categorieDi(c: Catalogo | null, tipo: string): Record<string, string> {
  if (!c) return {};
  if (TIPI_MERCATINO.includes(tipo)) return c.categorie.mercatino;
  if (tipo === "aiuto" || tipo === "segnalazione" || tipo === "evento" || tipo === "servizio") return c.categorie[tipo];
  return c.categorie.gruppo;
}

/** Le date del server sono in UTC senza fuso: si aggiunge la "Z" prima di convertirle. */
export const daServer = (s: string) => new Date(s + (s.endsWith("Z") ? "" : "Z"));
export const dataIt = (s: string | null | undefined) => (s ? daServer(s).toLocaleDateString("it-IT") : "—");
export const dataOraIt = (s: string) =>
  daServer(s).toLocaleString("it-IT", { weekday: "short", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
export const oraIt = (s: string) => daServer(s).toLocaleTimeString("it-IT", { hour: "2-digit", minute: "2-digit" });

export function fa(s: string): string {
  const sec = (Date.now() - daServer(s).getTime()) / 1000;
  if (sec < 60) return "adesso";
  if (sec < 3600) return `${Math.floor(sec / 60)} min fa`;
  if (sec < 86400) return `${Math.floor(sec / 3600)} h fa`;
  if (sec < 7 * 86400) return `${Math.floor(sec / 86400)} g fa`;
  return dataIt(s);
}

/** Da <input type="datetime-local"> (ora locale) a ISO UTC per il server. */
export const localeAIso = (v: string) => (v ? new Date(v).toISOString() : null);
