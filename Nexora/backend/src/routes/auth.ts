// Autenticazione: registrazione azienda, accesso con MFA, sessione, SSO OpenID Connect (PKCE)
// e OAuth2 client credentials per le integrazioni. © Roberto Di Flumeri
import { createHash } from "node:crypto";
import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import { createRemoteJWKSet, jwtVerify, SignJWT } from "jose";
import QRCode from "qrcode";
import { z } from "zod";
import { config } from "../lib/config.js";
import { cifra, decifra, sha256, tokenCasuale } from "../lib/crypto.js";
import { db } from "../lib/db.js";
import { conflitto, HttpError, nonValido } from "../lib/errori.js";
import { partitaIvaValida } from "../lib/fiscale.js";
import {
  audit, autenticato, codiciRecupero, confrontoFittizio, controllaRate, emettiRefresh, firmaAccessToken, firmaTokenMfa,
  hashPassword, impostaCookieRefresh, PERMESSI, revocaTutti, ruotaRefresh, segretoTotp, uriTotp, verificaPassword,
  verificaTokenMfa, verificaTotp, type Ruolo,
} from "../lib/sicurezza.js";

const password = z.string().min(10).max(128).refine((p) => /[A-Za-z]/.test(p) && /\d/.test(p), "la password deve contenere lettere e numeri");

async function sessione(utente: { id: string; aziendaId: string; ruolo: string }) {
  await db.utente.update({ where: { id: utente.id }, data: { ultimoAccesso: new Date() } });
  return {
    access_token: await firmaAccessToken({ sub: utente.id, azienda: utente.aziendaId, ruolo: utente.ruolo, tipo: "utente" }),
    token_type: "Bearer", expires_in: config.accessTokenMinuti * 60,
  };
}

export async function profilo(utenteId: string) {
  const u = await db.utente.findUniqueOrThrow({ where: { id: utenteId }, include: { azienda: true } });
  return {
    id: u.id, email: u.email, nome: u.nome, ruolo: u.ruolo, mfaAttivo: u.mfaAttivo, permessi: PERMESSI[u.ruolo as Ruolo],
    azienda: { id: u.azienda.id, ragioneSociale: u.azienda.ragioneSociale, partitaIva: u.azienda.partitaIva, ssoAttivo: !!u.azienda.ssoIssuer },
  };
}

export default async function rotteAuth(app: FastifyInstance) {
  app.post("/api/auth/register", async (req, reply) => {
    controllaRate(`register:${req.ip}`, config.rateLimitLoginMinuto);
    const d = z.object({
      ragioneSociale: z.string().min(2).max(120), partitaIva: z.string().trim(), indirizzo: z.string().min(2), cap: z.string().regex(/^\d{5}$/, "CAP di 5 cifre"),
      citta: z.string().min(2), provincia: z.string().length(2, "sigla di 2 lettere"), nome: z.string().min(2).max(80), email: z.string().email(), password,
    }).parse(req.body);
    if (!partitaIvaValida(d.partitaIva)) throw nonValido("Partita IVA: non valida (controlla le 11 cifre)");
    if (await db.utente.findUnique({ where: { email: d.email.toLowerCase() } })) throw conflitto("Esiste già un utente con questa email");
    const azienda = await db.azienda.create({ data: {
      ragioneSociale: d.ragioneSociale, partitaIva: d.partitaIva.replace(/^IT/i, ""), indirizzo: d.indirizzo, cap: d.cap, citta: d.citta,
      provincia: d.provincia.toUpperCase(), email: d.email.toLowerCase(),
      magazzini: { create: { codice: "MAG01", nome: "Magazzino principale", principale: true, citta: d.citta } },
    } });
    const u = await db.utente.create({ data: { aziendaId: azienda.id, email: d.email.toLowerCase(), nome: d.nome, ruolo: "admin", passwordHash: await hashPassword(d.password) } });
    await db.auditLog.create({ data: { aziendaId: azienda.id, utenteId: u.id, azione: "azienda_creata" } });
    impostaCookieRefresh(reply, await emettiRefresh(u.id));
    return reply.status(201).send(await sessione(u));
  });

  app.post("/api/auth/login", async (req, reply) => {
    controllaRate(`login:${req.ip}`, config.rateLimitLoginMinuto);
    const d = z.object({ email: z.string().email(), password: z.string().min(1) }).parse(req.body);
    const u = await db.utente.findUnique({ where: { email: d.email.toLowerCase() } });
    if (!u) { await confrontoFittizio(d.password); throw new HttpError(401, "Email o password non corretti"); }
    if (!(await verificaPassword(d.password, u.passwordHash))) {
      await db.auditLog.create({ data: { aziendaId: u.aziendaId, utenteId: u.id, azione: "accesso_fallito" } });
      throw new HttpError(401, "Email o password non corretti");
    }
    if (!u.attivo) throw new HttpError(403, "Utente disattivato: contatta l'amministratore");
    if (u.mfaAttivo) return { mfa_richiesta: true, mfa_token: await firmaTokenMfa(u.id) };
    await db.auditLog.create({ data: { aziendaId: u.aziendaId, utenteId: u.id, azione: "accesso" } });
    impostaCookieRefresh(reply, await emettiRefresh(u.id));
    return sessione(u);
  });

  app.post("/api/auth/mfa/verifica", async (req, reply) => {
    controllaRate(`mfa:${req.ip}`, config.rateLimitLoginMinuto);
    const d = z.object({ mfa_token: z.string(), codice: z.string().min(6).max(20) }).parse(req.body);
    const u = await db.utente.findUniqueOrThrow({ where: { id: await verificaTokenMfa(d.mfa_token) } });
    const segreto = decifra(u.mfaSecret);
    let ok = !!segreto && verificaTotp(segreto, d.codice);
    if (!ok && u.codiciRecupero) { // codice di recupero monouso
      const codici: string[] = JSON.parse(u.codiciRecupero);
      const h = sha256(d.codice.trim().toUpperCase());
      if (codici.includes(h)) {
        ok = true;
        await db.utente.update({ where: { id: u.id }, data: { codiciRecupero: JSON.stringify(codici.filter((c) => c !== h)) } });
      }
    }
    if (!ok) throw new HttpError(401, "Codice di verifica non valido");
    await db.auditLog.create({ data: { aziendaId: u.aziendaId, utenteId: u.id, azione: "accesso_mfa" } });
    impostaCookieRefresh(reply, await emettiRefresh(u.id));
    return sessione(u);
  });

  app.post("/api/auth/refresh", async (req, reply) => {
    const token = req.cookies.nx_refresh;
    if (!token) return reply.status(204).send();
    const { utente, nuovo } = await ruotaRefresh(token);
    impostaCookieRefresh(reply, nuovo);
    return sessione(utente);
  });

  app.post("/api/auth/logout", { preHandler: autenticato }, async (req, reply) => {
    if (req.identita.utenteId) await revocaTutti(req.identita.utenteId);
    reply.clearCookie("nx_refresh", { path: "/api/auth" });
    return reply.status(204).send();
  });

  app.get("/api/auth/me", { preHandler: autenticato }, async (req) => {
    if (!req.identita.utenteId) return { client: req.identita.nome, azienda: req.identita.aziendaId, scopes: req.identita.scopes };
    return profilo(req.identita.utenteId);
  });

  app.patch("/api/auth/me", { preHandler: autenticato }, async (req) => {
    const d = z.object({ nome: z.string().min(2).max(80) }).parse(req.body);
    await db.utente.update({ where: { id: req.identita.utenteId! }, data: { nome: d.nome } });
    return profilo(req.identita.utenteId!);
  });

  app.post("/api/auth/password", { preHandler: autenticato }, async (req, reply) => {
    const d = z.object({ attuale: z.string(), nuova: password }).parse(req.body);
    const u = await db.utente.findUniqueOrThrow({ where: { id: req.identita.utenteId! } });
    if (!(await verificaPassword(d.attuale, u.passwordHash))) throw new HttpError(403, "Password attuale non corretta");
    await db.utente.update({ where: { id: u.id }, data: { passwordHash: await hashPassword(d.nuova) } });
    await revocaTutti(u.id);
    await audit(req, "password_cambiata");
    reply.clearCookie("nx_refresh", { path: "/api/auth" });
    return reply.status(204).send();
  });

  // ── MFA ──────────────────────────────────────────────────────────────────
  app.post("/api/auth/mfa/configura", { preHandler: autenticato }, async (req) => {
    const u = await db.utente.findUniqueOrThrow({ where: { id: req.identita.utenteId! } });
    if (u.mfaAttivo) throw conflitto("La verifica in due passaggi è già attiva");
    const segreto = segretoTotp();
    await db.utente.update({ where: { id: u.id }, data: { mfaSecret: cifra(segreto) } });
    const uri = uriTotp(segreto, u.email);
    return { segreto, uri, qr: await QRCode.toDataURL(uri, { margin: 1, width: 220 }) };
  });

  app.post("/api/auth/mfa/attiva", { preHandler: autenticato }, async (req) => {
    const d = z.object({ codice: z.string().min(6).max(8) }).parse(req.body);
    const u = await db.utente.findUniqueOrThrow({ where: { id: req.identita.utenteId! } });
    const segreto = decifra(u.mfaSecret);
    if (!segreto || !verificaTotp(segreto, d.codice)) throw nonValido("Codice non valido: controlla l'ora del telefono e riprova");
    const codici = codiciRecupero();
    await db.utente.update({ where: { id: u.id }, data: { mfaAttivo: true, codiciRecupero: JSON.stringify(codici.map((c) => sha256(c))) } });
    await audit(req, "mfa_attivata");
    return { codiciRecupero: codici };
  });

  app.post("/api/auth/mfa/disattiva", { preHandler: autenticato }, async (req) => {
    const d = z.object({ password: z.string() }).parse(req.body);
    const u = await db.utente.findUniqueOrThrow({ where: { id: req.identita.utenteId! } });
    if (!(await verificaPassword(d.password, u.passwordHash))) throw new HttpError(403, "Password non corretta");
    await db.utente.update({ where: { id: u.id }, data: { mfaAttivo: false, mfaSecret: null, codiciRecupero: null } });
    await audit(req, "mfa_disattivata");
    return profilo(u.id);
  });

  // ── SSO OpenID Connect (authorization code + PKCE) ────────────────────────
  const CHIAVE_STATO = new TextEncoder().encode(config.jwtSecret + ":sso");
  const scoperta = new Map<string, { authorization_endpoint: string; token_endpoint: string; jwks_uri: string; issuer: string }>();
  async function metadatiOidc(issuer: string) {
    if (!scoperta.has(issuer)) {
      const r = await fetch(`${issuer.replace(/\/$/, "")}/.well-known/openid-configuration`);
      if (!r.ok) throw new HttpError(502, "Provider SSO non raggiungibile");
      scoperta.set(issuer, await r.json());
    }
    return scoperta.get(issuer)!;
  }
  const callbackSso = () => `${config.urlPubblico}/api/auth/sso/callback`;

  app.get("/api/auth/sso/inizia", async (req, reply) => {
    const { email } = z.object({ email: z.string().email() }).parse(req.query);
    const u = await db.utente.findUnique({ where: { email: email.toLowerCase() }, include: { azienda: true } });
    if (!u?.azienda.ssoIssuer || !u.azienda.ssoClientId) throw nonValido("Per questa email non è configurato l'accesso SSO");
    const m = await metadatiOidc(u.azienda.ssoIssuer);
    const verifier = tokenCasuale(48);
    const nonce = tokenCasuale(16);
    const stato = await new SignJWT({ az: u.aziendaId, v: verifier, n: nonce }).setProtectedHeader({ alg: "HS256" }).setExpirationTime("10m").sign(CHIAVE_STATO);
    const url = new URL(m.authorization_endpoint);
    url.search = new URLSearchParams({
      response_type: "code", client_id: u.azienda.ssoClientId, redirect_uri: callbackSso(), scope: "openid email profile",
      state: stato, nonce, code_challenge: createHash("sha256").update(verifier).digest("base64url"), code_challenge_method: "S256", login_hint: email,
    }).toString();
    return reply.redirect(url.toString());
  });

  app.get("/api/auth/sso/callback", async (req, reply) => {
    // il browser arriva qui dal provider: in caso di errore torna alla pagina di accesso con un messaggio leggibile
    try { return await callbackOidc(req, reply); } catch (e) {
      const messaggio = e instanceof HttpError ? e.message : "Accesso SSO non riuscito: riprova o contatta l'amministratore";
      return reply.redirect(`${config.urlPubblico}/accedi?sso_errore=${encodeURIComponent(messaggio)}`);
    }
  });

  async function callbackOidc(req: FastifyRequest, reply: FastifyReply) {
    const q = z.object({ code: z.string(), state: z.string() }).parse(req.query);
    let s;
    try { ({ payload: s } = await jwtVerify(q.state, CHIAVE_STATO)); } catch { throw new HttpError(400, "Richiesta SSO scaduta: riprova"); }
    const azienda = await db.azienda.findUniqueOrThrow({ where: { id: s.az as string } });
    const m = await metadatiOidc(azienda.ssoIssuer!);
    const r = await fetch(m.token_endpoint, {
      method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ grant_type: "authorization_code", code: q.code, redirect_uri: callbackSso(), client_id: azienda.ssoClientId!,
        client_secret: decifra(azienda.ssoClientSecret) || "", code_verifier: s.v as string }).toString(),
    });
    if (!r.ok) throw new HttpError(401, "Il provider SSO ha rifiutato l'accesso");
    const { id_token } = await r.json() as { id_token: string };
    const { payload } = await jwtVerify(id_token, createRemoteJWKSet(new URL(m.jwks_uri)), { issuer: m.issuer, audience: azienda.ssoClientId! });
    if (payload.nonce !== s.n) throw new HttpError(401, "Risposta SSO non valida");
    const email = String(payload.email || "").toLowerCase();
    const u = await db.utente.findFirst({ where: { aziendaId: azienda.id, email, attivo: true } });
    if (!u) throw new HttpError(403, "Nessun utente Nexora attivo con questa email: chiedi all'amministratore di invitarti");
    await db.utente.update({ where: { id: u.id }, data: { ssoSub: String(payload.sub) } });
    await db.auditLog.create({ data: { aziendaId: azienda.id, utenteId: u.id, azione: "accesso_sso" } });
    impostaCookieRefresh(reply, await emettiRefresh(u.id));
    return reply.redirect(`${config.urlPubblico}/app`);
  }

  // ── OAuth2 client credentials (integrazioni) ──────────────────────────────
  app.post("/api/oauth/token", async (req, reply) => {
    controllaRate(`oauth:${req.ip}`, 30);
    const corpo = (req.body || {}) as Record<string, string>;
    let clientId = corpo.client_id, segreto = corpo.client_secret;
    const h = req.headers.authorization;
    if (h?.startsWith("Basic ")) [clientId, segreto] = Buffer.from(h.slice(6), "base64").toString().split(":");
    if (corpo.grant_type !== "client_credentials") return reply.status(400).send({ error: "unsupported_grant_type" });
    const c = clientId ? await db.apiClient.findUnique({ where: { clientId } }) : null;
    if (!c || c.revocato || !(await verificaPassword(segreto || "", c.secretHash))) return reply.status(401).send({ error: "invalid_client" });
    const richiesti = corpo.scope ? corpo.scope.split(" ") : c.scopes.split(" ");
    const concessi = richiesti.filter((s) => c.scopes.split(" ").includes(s));
    await db.apiClient.update({ where: { id: c.id }, data: { ultimoUso: new Date() } });
    return {
      access_token: await firmaAccessToken({ sub: c.clientId, azienda: c.aziendaId, ruolo: "client", tipo: "client", scope: concessi.join(" ") }, 60),
      token_type: "Bearer", expires_in: 3600, scope: concessi.join(" "),
    };
  });
}
