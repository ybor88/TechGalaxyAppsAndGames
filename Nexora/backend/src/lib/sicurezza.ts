// Autenticazione e autorizzazione: JWT (OAuth2/OIDC compatibile), refresh token ruotato, MFA TOTP,
// ruoli e permessi, rate limiting e audit. © Roberto Di Flumeri
import { createHmac, randomBytes } from "node:crypto";
import bcrypt from "bcryptjs";
import type { FastifyReply, FastifyRequest } from "fastify";
import { jwtVerify, SignJWT } from "jose";
import { config } from "./config.js";
import { sha256, tokenCasuale } from "./crypto.js";
import { db } from "./db.js";
import { HttpError, vietato } from "./errori.js";

const CHIAVE = new TextEncoder().encode(config.jwtSecret);
const EMITTENTE = "nexora";

// ── Ruoli e permessi ────────────────────────────────────────────────────────

export const RUOLI = ["admin", "vendite", "magazzino", "contabilita", "lettura"] as const;
export type Ruolo = (typeof RUOLI)[number];
export type Permesso =
  | "catalogo" | "clienti" | "fornitori" | "ordini" | "spedizioni" | "magazzino" | "acquisti" | "documenti"
  | "pagamenti" | "ai" | "integrazioni" | "impostazioni";

/** Permessi di scrittura per ruolo: tutti i ruoli possono leggere i dati della propria azienda. */
export const PERMESSI: Record<Ruolo, Permesso[]> = {
  admin: ["catalogo", "clienti", "fornitori", "ordini", "spedizioni", "magazzino", "acquisti", "documenti", "pagamenti", "ai", "integrazioni", "impostazioni"],
  vendite: ["clienti", "ordini", "documenti", "spedizioni"],
  magazzino: ["catalogo", "magazzino", "acquisti", "spedizioni", "fornitori", "ai"],
  contabilita: ["documenti", "pagamenti", "clienti", "fornitori"],
  lettura: [],
};

export interface Identita {
  utenteId: string | null; // null per i client API (OAuth2 client credentials)
  aziendaId: string;
  ruolo: Ruolo;
  scopes?: string[];
  nome: string;
}

declare module "fastify" {
  interface FastifyRequest { identita: Identita }
}

// ── Password e token ────────────────────────────────────────────────────────

export const hashPassword = (p: string) => bcrypt.hash(p, 11);
export const verificaPassword = (p: string, h: string | null | undefined) => (h ? bcrypt.compare(p, h) : Promise.resolve(false));
const HASH_FITTIZIO = bcrypt.hashSync("nexora-password-fittizia-1", 11);
export const confrontoFittizio = (p: string) => bcrypt.compare(p, HASH_FITTIZIO);

export async function firmaAccessToken(dati: { sub: string; azienda: string; ruolo: string; tipo: "utente" | "client"; scope?: string }, minuti = config.accessTokenMinuti) {
  return new SignJWT({ azienda: dati.azienda, ruolo: dati.ruolo, tipo: dati.tipo, scope: dati.scope })
    .setProtectedHeader({ alg: "HS256", typ: "JWT" })
    .setSubject(dati.sub).setIssuer(EMITTENTE).setAudience("nexora-api")
    .setIssuedAt().setExpirationTime(`${minuti}m`).sign(CHIAVE);
}

/** Token di breve durata per il secondo passaggio dell'accesso (MFA). */
export async function firmaTokenMfa(utenteId: string) {
  return new SignJWT({ scopo: "mfa" }).setProtectedHeader({ alg: "HS256" }).setSubject(utenteId)
    .setIssuer(EMITTENTE).setAudience("nexora-mfa").setIssuedAt().setExpirationTime("5m").sign(CHIAVE);
}

export async function verificaTokenMfa(token: string): Promise<string> {
  try {
    const { payload } = await jwtVerify(token, CHIAVE, { issuer: EMITTENTE, audience: "nexora-mfa" });
    return payload.sub as string;
  } catch {
    throw new HttpError(401, "Sessione di verifica scaduta: accedi di nuovo");
  }
}

export async function emettiRefresh(utenteId: string, famiglia = tokenCasuale(12)) {
  const token = tokenCasuale(48);
  await db.refreshToken.create({ data: {
    utenteId, tokenHash: sha256(token), famiglia, scadenza: new Date(Date.now() + config.refreshTokenGiorni * 86400_000),
  } });
  return token;
}

/** Ogni refresh token vale una volta: il riuso di un token già ruotato revoca l'intera famiglia. */
export async function ruotaRefresh(token: string) {
  const rec = await db.refreshToken.findUnique({ where: { tokenHash: sha256(token) }, include: { utente: true } });
  if (!rec || rec.scadenza < new Date()) throw new HttpError(401, "Sessione scaduta, accedi di nuovo");
  if (rec.revocato) {
    await db.refreshToken.updateMany({ where: { famiglia: rec.famiglia }, data: { revocato: new Date() } });
    throw new HttpError(401, "Sessione non valida, accedi di nuovo");
  }
  if (!rec.utente.attivo) throw new HttpError(403, "Utente disattivato");
  await db.refreshToken.update({ where: { id: rec.id }, data: { revocato: new Date() } });
  return { utente: rec.utente, nuovo: await emettiRefresh(rec.utenteId, rec.famiglia) };
}

export const revocaTutti = (utenteId: string) =>
  db.refreshToken.updateMany({ where: { utenteId, revocato: null }, data: { revocato: new Date() } });

// ── MFA: TOTP (RFC 6238), compatibile con qualsiasi app di autenticazione gratuita ──

const BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

export function segretoTotp(): string {
  const b = randomBytes(20);
  let bit = "";
  for (const x of b) bit += x.toString(2).padStart(8, "0");
  let out = "";
  for (let i = 0; i + 5 <= bit.length; i += 5) out += BASE32[parseInt(bit.slice(i, i + 5), 2)];
  return out;
}

function base32Decode(s: string): Buffer {
  let bit = "";
  for (const c of s.replace(/=+$/, "").toUpperCase()) bit += BASE32.indexOf(c).toString(2).padStart(5, "0");
  const out: number[] = [];
  for (let i = 0; i + 8 <= bit.length; i += 8) out.push(parseInt(bit.slice(i, i + 8), 2));
  return Buffer.from(out);
}

export function codiceTotp(segreto: string, istante = Date.now(), passo = 0): string {
  const contatore = Math.floor(istante / 1000 / 30) + passo;
  const buf = Buffer.alloc(8);
  buf.writeBigUInt64BE(BigInt(contatore));
  const h = createHmac("sha1", base32Decode(segreto)).update(buf).digest();
  const o = h[h.length - 1] & 0xf;
  const n = ((h[o] & 0x7f) << 24) | (h[o + 1] << 16) | (h[o + 2] << 8) | h[o + 3];
  return String(n % 1_000_000).padStart(6, "0");
}

/** Accetta il codice dell'intervallo corrente e di quello precedente/successivo (deriva dell'orologio). */
export function verificaTotp(segreto: string, codice: string): boolean {
  const c = codice.replace(/\s/g, "");
  return [-1, 0, 1].some((p) => codiceTotp(segreto, Date.now(), p) === c);
}

export const uriTotp = (segreto: string, email: string) =>
  `otpauth://totp/Nexora:${encodeURIComponent(email)}?secret=${segreto}&issuer=Nexora&algorithm=SHA1&digits=6&period=30`;

export function codiciRecupero(): string[] {
  return Array.from({ length: 8 }, () => randomBytes(5).toString("hex").toUpperCase().replace(/(.{5})/, "$1-"));
}

// ── Middleware ──────────────────────────────────────────────────────────────

export async function autenticato(req: FastifyRequest) {
  const h = req.headers.authorization;
  if (!h?.startsWith("Bearer ")) throw new HttpError(401, "Autenticazione richiesta");
  let payload;
  try {
    ({ payload } = await jwtVerify(h.slice(7), CHIAVE, { issuer: EMITTENTE, audience: "nexora-api" }));
  } catch {
    throw new HttpError(401, "Token non valido o scaduto");
  }
  if (payload.tipo === "client") {
    const c = await db.apiClient.findUnique({ where: { clientId: payload.sub as string } });
    if (!c || c.revocato) throw new HttpError(401, "Client API revocato");
    // valgono gli scope concessi nel token, sempre contenuti in quelli del client
    const concessi = String(payload.scope || "").split(" ").filter((x) => x && c.scopes.split(" ").includes(x));
    req.identita = { utenteId: null, aziendaId: c.aziendaId, ruolo: "lettura", scopes: concessi, nome: c.nome };
    return;
  }
  const u = await db.utente.findUnique({ where: { id: payload.sub as string } });
  if (!u || !u.attivo) throw new HttpError(401, "Utente non attivo");
  req.identita = { utenteId: u.id, aziendaId: u.aziendaId, ruolo: u.ruolo as Ruolo, nome: u.nome };
}

/** Verifica il permesso di scrittura (utenti) o lo scope (client API). */
export function richiede(permesso: Permesso) {
  return async (req: FastifyRequest) => {
    const i = req.identita;
    if (i.utenteId === null) {
      if (!i.scopes?.includes(`${permesso}:scrittura`)) throw vietato(`Lo scope «${permesso}:scrittura» non è concesso a questo client`);
      return;
    }
    if (!PERMESSI[i.ruolo]?.includes(permesso)) throw vietato();
  };
}

export function puo(req: FastifyRequest, permesso: Permesso): boolean {
  const i = req.identita;
  return i.utenteId === null ? !!i.scopes?.includes(`${permesso}:scrittura`) : PERMESSI[i.ruolo]?.includes(permesso);
}

/** Lettura per i client API: serve lo scope «<area>:lettura» (gli utenti leggono sempre). */
export function leggibile(area: Permesso) {
  return async (req: FastifyRequest) => {
    const i = req.identita;
    if (i.utenteId === null && !i.scopes?.some((s) => s === `${area}:lettura` || s === `${area}:scrittura`))
      throw vietato(`Lo scope «${area}:lettura» non è concesso a questo client`);
  };
}

// ── Rate limiting (finestra scorrevole in memoria; con più istanze va spostato su Redis) ──

const finestre = new Map<string, number[]>();
export function controllaRate(chiave: string, limite: number, finestraMs = 60_000, messaggio = "Troppi tentativi: riprova tra un minuto") {
  const ora = Date.now();
  const v = (finestre.get(chiave) || []).filter((t) => ora - t < finestraMs);
  if (v.length >= limite) throw new HttpError(429, messaggio);
  v.push(ora);
  finestre.set(chiave, v);
}
export const azzeraRate = () => finestre.clear();

// ── Audit ───────────────────────────────────────────────────────────────────

export async function audit(req: FastifyRequest | { identita: Identita }, azione: string, entita?: string, entitaId?: string, dettagli?: string) {
  const i = req.identita;
  await db.auditLog.create({ data: { aziendaId: i.aziendaId, utenteId: i.utenteId, azione, entita, entitaId, dettagli } });
}

export function impostaCookieRefresh(reply: FastifyReply, token: string) {
  reply.setCookie("nx_refresh", token, {
    httpOnly: true, secure: config.cookieSecure, sameSite: "strict", path: "/api/auth", maxAge: config.refreshTokenGiorni * 86400,
  });
}
