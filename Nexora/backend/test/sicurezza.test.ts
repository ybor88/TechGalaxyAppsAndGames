// Autenticazione, MFA, ruoli, isolamento tra aziende e OAuth2 client credentials. © Roberto Di Flumeri
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import type { FastifyInstance } from "fastify";
import { codiceTotp } from "../src/lib/sicurezza.js";
import { db } from "../src/lib/db.js";
import { api, azienda, cliente, nuovaApp, PASSWORD, partitaIva, prodotto, utenteConRuolo } from "./aiuti.js";

let app: FastifyInstance;
beforeAll(async () => { app = await nuovaApp(); });
afterAll(async () => { await app.close(); });

describe("registrazione e accesso", () => {
  it("rifiuta partita IVA non valida e password deboli", async () => {
    const base = { ragioneSociale: "Test S.r.l.", indirizzo: "Via Roma 1", cap: "20121", citta: "Milano", provincia: "MI", nome: "Anna", email: "x@example.com" };
    let r = await api(app, "POST", "/api/auth/register", null, { ...base, partitaIva: "12345678901", password: PASSWORD });
    expect(r.status).toBe(422);
    expect(r.json.errore).toContain("Partita IVA");
    r = await api(app, "POST", "/api/auth/register", null, { ...base, partitaIva: partitaIva(), password: "solamentelettere" });
    expect(r.status).toBe(422);
    expect(r.json.errore).toContain("lettere e numeri");
  });

  it("crea l'azienda con il magazzino principale e l'amministratore", async () => {
    const a = await azienda(app);
    const me = await api(app, "GET", "/api/auth/me", a.h);
    expect(me.json.ruolo).toBe("admin");
    expect(me.json.permessi).toContain("impostazioni");
    const m = await api(app, "GET", "/api/magazzini", a.h);
    expect(m.json).toHaveLength(1);
    expect(m.json[0].principale).toBe(true);
  });

  it("email duplicata, password errata e utente inesistente", async () => {
    const a = await azienda(app);
    const dup = await api(app, "POST", "/api/auth/register", null, { ragioneSociale: "Altra", partitaIva: partitaIva(), indirizzo: "Via X 1", cap: "00100",
      citta: "Roma", provincia: "RM", nome: "Bea", email: a.email, password: PASSWORD });
    expect(dup.status).toBe(409);
    expect((await api(app, "POST", "/api/auth/login", null, { email: a.email, password: "Sbagliata123" })).status).toBe(401);
    expect((await api(app, "POST", "/api/auth/login", null, { email: "nessuno@example.com", password: PASSWORD })).status).toBe(401);
    expect((await api(app, "POST", "/api/auth/login", null, { email: a.email.toUpperCase(), password: PASSWORD })).status).toBe(200);
  });

  it("refresh token ruotato: il riuso revoca la sessione", async () => {
    const a = await azienda(app);
    const cookie = String(a.cookie).split(";")[0];
    const r1 = await app.inject({ method: "POST", url: "/api/auth/refresh", headers: { cookie } });
    expect(r1.statusCode).toBe(200);
    const nuovo = String(r1.headers["set-cookie"]).split(";")[0];
    expect(nuovo).not.toBe(cookie);
    const riuso = await app.inject({ method: "POST", url: "/api/auth/refresh", headers: { cookie } });
    expect(riuso.statusCode).toBe(401);
    // dopo il riuso anche il token nuovo della stessa famiglia è revocato
    expect((await app.inject({ method: "POST", url: "/api/auth/refresh", headers: { cookie: nuovo } })).statusCode).toBe(401);
  });

  it("token mancante o falsificato", async () => {
    expect((await api(app, "GET", "/api/prodotti")).status).toBe(401);
    expect((await api(app, "GET", "/api/prodotti", { authorization: "Bearer abc.def.ghi" })).status).toBe(401);
  });

  it("cambio password: la vecchia non funziona più", async () => {
    const a = await azienda(app);
    expect((await api(app, "POST", "/api/auth/password", a.h, { attuale: "Sbagliata123", nuova: "NuovaPassword2026" })).status).toBe(403);
    expect((await api(app, "POST", "/api/auth/password", a.h, { attuale: PASSWORD, nuova: "NuovaPassword2026" })).status).toBe(204);
    expect((await api(app, "POST", "/api/auth/login", null, { email: a.email, password: PASSWORD })).status).toBe(401);
    expect((await api(app, "POST", "/api/auth/login", null, { email: a.email, password: "NuovaPassword2026" })).status).toBe(200);
  });
});

describe("verifica in due passaggi (MFA TOTP)", () => {
  it("attivazione, accesso con codice e con codice di recupero monouso", async () => {
    const a = await azienda(app);
    const conf = await api(app, "POST", "/api/auth/mfa/configura", a.h);
    expect(conf.json.uri).toMatch(/^otpauth:\/\/totp\/Nexora/);
    expect(conf.json.qr).toMatch(/^data:image\/png;base64,/);
    expect((await api(app, "POST", "/api/auth/mfa/attiva", a.h, { codice: "000000" })).status).toBe(422);
    const att = await api(app, "POST", "/api/auth/mfa/attiva", a.h, { codice: codiceTotp(conf.json.segreto) });
    expect(att.json.codiciRecupero).toHaveLength(8);

    const l = await api(app, "POST", "/api/auth/login", null, { email: a.email, password: PASSWORD });
    expect(l.json.mfa_richiesta).toBe(true);
    expect(l.json.access_token).toBeUndefined();
    expect((await api(app, "POST", "/api/auth/mfa/verifica", null, { mfa_token: l.json.mfa_token, codice: "123456" })).status).toBe(401);
    const ok = await api(app, "POST", "/api/auth/mfa/verifica", null, { mfa_token: l.json.mfa_token, codice: codiceTotp(conf.json.segreto) });
    expect(ok.json.access_token).toBeTruthy();

    const rec = att.json.codiciRecupero[0];
    const l2 = await api(app, "POST", "/api/auth/login", null, { email: a.email, password: PASSWORD });
    expect((await api(app, "POST", "/api/auth/mfa/verifica", null, { mfa_token: l2.json.mfa_token, codice: rec })).status).toBe(200);
    const l3 = await api(app, "POST", "/api/auth/login", null, { email: a.email, password: PASSWORD });
    expect((await api(app, "POST", "/api/auth/mfa/verifica", null, { mfa_token: l3.json.mfa_token, codice: rec })).status).toBe(401); // già usato
    // il segreto è cifrato nel database
    const u = await db.utente.findUniqueOrThrow({ where: { email: a.email } });
    expect(u.mfaSecret).not.toContain(conf.json.segreto);
  });
});

describe("ruoli e isolamento", () => {
  it("ogni ruolo scrive solo nelle sue aree", async () => {
    const a = await azienda(app);
    const lettura = await utenteConRuolo(app, a.h, "lettura");
    const vendite = await utenteConRuolo(app, a.h, "vendite");
    const magazzino = await utenteConRuolo(app, a.h, "magazzino");
    const nuovo = { sku: "R-1", nome: "Articolo", prezzoVendita: 100, aliquotaIva: 22 };
    expect((await api(app, "GET", "/api/prodotti", lettura)).status).toBe(200);
    expect((await api(app, "POST", "/api/prodotti", lettura, nuovo)).status).toBe(403);
    expect((await api(app, "POST", "/api/prodotti", vendite, nuovo)).status).toBe(403);
    expect((await api(app, "POST", "/api/prodotti", magazzino, nuovo)).status).toBe(201);
    expect((await api(app, "POST", "/api/clienti", magazzino, { ragioneSociale: "X", tipo: "privato" })).status).toBe(403);
    expect((await api(app, "POST", "/api/clienti", vendite, { ragioneSociale: "Mario Rossi", tipo: "privato" })).status).toBe(201);
    expect((await api(app, "POST", "/api/utenti", vendite, { nome: "Z", email: "z@example.com", ruolo: "admin" })).status).toBe(403);
  });

  it("i dati di un'azienda sono invisibili alle altre", async () => {
    const a = await azienda(app);
    const b = await azienda(app);
    const p = await prodotto(app, a.h);
    const c = await cliente(app, a.h);
    expect((await api(app, "GET", `/api/prodotti/${p.id}`, b.h)).status).toBe(404);
    expect((await api(app, "PATCH", `/api/prodotti/${p.id}`, b.h, { nome: "Rubato" })).status).toBe(404);
    expect((await api(app, "GET", `/api/clienti/${c.id}`, b.h)).status).toBe(404);
    expect((await api(app, "GET", "/api/prodotti", b.h)).json).toHaveLength(0);
    expect((await api(app, "POST", "/api/movimenti", b.h, { tipo: "carico", varianteId: p.varianteId, magazzinoId: (await api(app, "GET", "/api/magazzini", b.h)).json[0].id, quantita: 5 })).status).toBe(404);
  });

  it("deve restare almeno un amministratore", async () => {
    const a = await azienda(app);
    const me = await api(app, "GET", "/api/auth/me", a.h);
    expect((await api(app, "PATCH", `/api/utenti/${me.json.id}`, a.h, { ruolo: "lettura" })).status).toBe(409);
  });

  it("un utente disattivato non accede più", async () => {
    const a = await azienda(app);
    const r = await api(app, "POST", "/api/utenti", a.h, { nome: "Temp", email: "temp.disattivo@example.com", ruolo: "vendite" });
    await api(app, "PATCH", `/api/utenti/${r.json.id}`, a.h, { attivo: false });
    const l = await api(app, "POST", "/api/auth/login", null, { email: "temp.disattivo@example.com", password: r.json.passwordTemporanea });
    expect(l.status).toBe(403);
  });
});

describe("OAuth2 client credentials", () => {
  it("token con scope limitati agli scope concessi", async () => {
    const a = await azienda(app);
    const c = await api(app, "POST", "/api/api-client", a.h, { nome: "Gestionale e-commerce", scopes: ["catalogo:lettura", "ordini:scrittura"] });
    expect(c.status).toBe(201);
    const form = `grant_type=client_credentials&client_id=${c.json.clientId}&client_secret=${c.json.clientSecret}`;
    const t = await app.inject({ method: "POST", url: "/api/oauth/token", headers: { "content-type": "application/x-www-form-urlencoded" }, payload: form });
    expect(t.statusCode).toBe(200);
    const h = { authorization: `Bearer ${t.json().access_token}` };
    expect((await api(app, "GET", "/api/prodotti", h)).status).toBe(200);
    expect((await api(app, "POST", "/api/prodotti", h, { sku: "API-1", nome: "Da API", prezzoVendita: 100, aliquotaIva: 22 })).status).toBe(403);
    expect((await api(app, "GET", "/api/documenti", h)).status).toBe(403); // nessuno scope sui documenti
    const sbagliato = await app.inject({ method: "POST", url: "/api/oauth/token", headers: { "content-type": "application/x-www-form-urlencoded" },
      payload: `grant_type=client_credentials&client_id=${c.json.clientId}&client_secret=sbagliato` });
    expect(sbagliato.statusCode).toBe(401);
    await api(app, "DELETE", `/api/api-client/${c.json.id}`, a.h);
    expect((await api(app, "GET", "/api/prodotti", h)).status).toBe(401); // revocato
  });
});

describe("protezione da abusi", () => {
  it("limita i tentativi di accesso", async () => {
    const a = await azienda(app);
    let ultimo = 0;
    for (let i = 0; i < 12; i++) ultimo = (await api(app, "POST", "/api/auth/login", null, { email: a.email, password: "Errata12345" })).status;
    expect(ultimo).toBe(429);
  });

  it("registra le operazioni nel log di audit", async () => {
    const app2 = await nuovaApp();
    const a = await azienda(app2);
    await prodotto(app2, a.h);
    const log = await api(app2, "GET", "/api/audit", a.h);
    expect(log.json.map((x: any) => x.azione)).toEqual(expect.arrayContaining(["azienda_creata", "prodotto_creato"]));
    await app2.close();
  });
});
