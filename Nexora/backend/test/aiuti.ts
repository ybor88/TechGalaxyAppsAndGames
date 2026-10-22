// Funzioni di supporto ai test: azienda di prova, chiamate API, anagrafiche e merce. © Roberto Di Flumeri
import type { FastifyInstance } from "fastify";
import { creaApp } from "../src/app.js";
import { azzeraRate } from "../src/lib/sicurezza.js";

export const PASSWORD = "PasswordNexora2026";

let contatore = 0;
const unico = () => `${Date.now().toString(36)}${(contatore++).toString(36)}`;

/** Partita IVA italiana valida (cifra di controllo calcolata). */
export function partitaIva(): string {
  const base = Array.from({ length: 10 }, (_, i) => (i === 0 ? 1 + Math.floor(Math.random() * 9) : Math.floor(Math.random() * 10)));
  let somma = 0;
  base.forEach((c, i) => {
    let x = c;
    if (i % 2 === 1) { x *= 2; if (x > 9) x -= 9; }
    somma += x;
  });
  return base.join("") + ((10 - (somma % 10)) % 10);
}

export async function nuovaApp() {
  azzeraRate();
  return creaApp();
}

export type Intestazioni = { authorization: string };

export interface Chiamata { status: number; json: any; body: string; headers: Record<string, any> }

export async function api(app: FastifyInstance, metodo: string, url: string, h?: Intestazioni | null, corpo?: unknown): Promise<Chiamata> {
  const r = await app.inject({ method: metodo as any, url, headers: h ?? {}, ...(corpo !== undefined ? { payload: corpo as any } : {}) });
  let json: any = null;
  try { json = r.json(); } catch { /* non JSON */ }
  return { status: r.statusCode, json, body: r.body, headers: r.headers };
}

export async function azienda(app: FastifyInstance, opzioni: Partial<{ email: string; ragioneSociale: string }> = {}) {
  const email = opzioni.email ?? `admin.${unico()}@example.com`;
  azzeraRate(); // la preparazione dei test non deve consumare il limite dei tentativi
  const r = await api(app, "POST", "/api/auth/register", null, {
    ragioneSociale: opzioni.ragioneSociale ?? `Azienda di prova ${unico()} S.r.l.`, partitaIva: partitaIva(), indirizzo: "Via Roma 1", cap: "20121",
    citta: "Milano", provincia: "MI", nome: "Amministratore", email, password: PASSWORD,
  });
  if (r.status !== 201) throw new Error(`registrazione: ${r.status} ${r.body}`);
  const h = { authorization: `Bearer ${r.json.access_token}` };
  const me = await api(app, "GET", "/api/auth/me", h);
  const magazzini = await api(app, "GET", "/api/magazzini", h);
  return { h, email, aziendaId: me.json.azienda.id as string, utenteId: me.json.id as string, magazzinoId: magazzini.json[0].id as string, cookie: r.headers["set-cookie"] };
}

export async function utenteConRuolo(app: FastifyInstance, admin: Intestazioni, ruolo: string) {
  const email = `${ruolo}.${unico()}@example.com`;
  const r = await api(app, "POST", "/api/utenti", admin, { nome: `Utente ${ruolo}`, email, ruolo });
  azzeraRate();
  const l = await api(app, "POST", "/api/auth/login", null, { email, password: r.json.passwordTemporanea });
  return { authorization: `Bearer ${l.json.access_token}` };
}

export async function prodotto(app: FastifyInstance, h: Intestazioni, campi: Record<string, unknown> = {}) {
  const r = await api(app, "POST", "/api/prodotti", h, {
    sku: `SKU-${unico()}`, nome: "Prodotto di prova", prezzoVendita: 1000, costoAcquisto: 400, aliquotaIva: 22, ...campi,
  });
  if (r.status !== 201) throw new Error(`prodotto: ${r.status} ${r.body}`);
  const det = await api(app, "GET", `/api/prodotti/${r.json.id}`, h);
  return { ...det.json, varianteId: det.json.varianti[0].id as string };
}

export async function cliente(app: FastifyInstance, h: Intestazioni, campi: Record<string, unknown> = {}) {
  const r = await api(app, "POST", "/api/clienti", h, {
    tipo: "azienda", ragioneSociale: `Cliente ${unico()} S.p.A.`, partitaIva: partitaIva(), codiceDestinatario: "M5UXCR1",
    indirizzo: "Via Verdi 2", cap: "10121", citta: "Torino", provincia: "TO", email: `cliente.${unico()}@example.com`, ...campi,
  });
  if (r.status !== 201) throw new Error(`cliente: ${r.status} ${r.body}`);
  return r.json;
}

export async function fornitore(app: FastifyInstance, h: Intestazioni, campi: Record<string, unknown> = {}) {
  const r = await api(app, "POST", "/api/fornitori", h, { ragioneSociale: `Fornitore ${unico()} S.r.l.`, partitaIva: partitaIva(), leadTimeGiorni: 5, ...campi });
  if (r.status !== 201) throw new Error(`fornitore: ${r.status} ${r.body}`);
  return r.json;
}

export async function carica(app: FastifyInstance, h: Intestazioni, varianteId: string, magazzinoId: string, quantita: number, extra: Record<string, unknown> = {}) {
  const r = await api(app, "POST", "/api/movimenti", h, { tipo: "carico", varianteId, magazzinoId, quantita, causale: "Carico iniziale", ...extra });
  if (r.status !== 201) throw new Error(`carico: ${r.status} ${r.body}`);
  return r.json;
}

export async function disponibile(app: FastifyInstance, h: Intestazioni, varianteId: string, magazzinoId?: string) {
  const r = await api(app, "GET", `/api/giacenze${magazzinoId ? `?magazzinoId=${magazzinoId}` : ""}`, h);
  return r.json.find((x: any) => x.varianteId === varianteId);
}
