// Impostazioni: azienda, utenti e ruoli, client API OAuth2, SSO e audit. © Roberto Di Flumeri
import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { cifra, tokenCasuale } from "../lib/crypto.js";
import { db } from "../lib/db.js";
import { conflitto, nonTrovato, nonValido } from "../lib/errori.js";
import { partitaIvaValida } from "../lib/fiscale.js";
import { audit, autenticato, hashPassword, PERMESSI, richiede, revocaTutti, RUOLI } from "../lib/sicurezza.js";

export const SCOPES = ["catalogo", "clienti", "ordini", "magazzino", "documenti"].flatMap((a) => [`${a}:lettura`, `${a}:scrittura`]);

const vistaAzienda = (a: Awaited<ReturnType<typeof db.azienda.findUniqueOrThrow>>) => ({
  ...a, ssoClientSecret: a.ssoClientSecret ? "••••••••" : null,
});

export default async function rotteImpostazioni(app: FastifyInstance) {
  app.addHook("preHandler", autenticato);

  app.get("/api/azienda", async (req) => vistaAzienda(await db.azienda.findUniqueOrThrow({ where: { id: req.identita.aziendaId } })));

  app.patch("/api/azienda", { preHandler: richiede("impostazioni") }, async (req) => {
    const d = z.object({
      ragioneSociale: z.string().min(2).max(120), partitaIva: z.string(), codiceFiscale: z.string().nullish(), indirizzo: z.string().min(2),
      cap: z.string().regex(/^\d{5}$/), citta: z.string().min(2), provincia: z.string().length(2), regimeFiscale: z.string().regex(/^RF\d{2}$/),
      email: z.string().email().nullish().or(z.literal("")), pec: z.string().email().nullish().or(z.literal("")), telefono: z.string().nullish(),
      iban: z.string().regex(/^[A-Z]{2}\d{2}[A-Z0-9]{11,30}$/, "IBAN non valido").nullish().or(z.literal("")),
      riordinoAutomatico: z.boolean().optional(),
      ssoIssuer: z.string().url().nullish().or(z.literal("")), ssoClientId: z.string().nullish(), ssoClientSecret: z.string().nullish(),
    }).partial().parse(req.body);
    if (d.partitaIva && !partitaIvaValida(d.partitaIva)) throw nonValido("Partita IVA: non valida");
    const dati: Record<string, unknown> = { ...d };
    for (const k of ["email", "pec", "iban", "ssoIssuer"]) if (dati[k] === "") dati[k] = null;
    if (d.provincia) dati.provincia = d.provincia.toUpperCase();
    if (d.ssoClientSecret !== undefined) dati.ssoClientSecret = d.ssoClientSecret && !d.ssoClientSecret.startsWith("••") ? cifra(d.ssoClientSecret) : undefined;
    const a = await db.azienda.update({ where: { id: req.identita.aziendaId }, data: dati });
    await audit(req, "azienda_modificata");
    return vistaAzienda(a);
  });

  // ── Utenti ──────────────────────────────────────────────────────────────
  app.get("/api/utenti", async (req) => {
    const u = await db.utente.findMany({ where: { aziendaId: req.identita.aziendaId }, orderBy: { nome: "asc" } });
    return u.map((x) => ({ id: x.id, nome: x.nome, email: x.email, ruolo: x.ruolo, attivo: x.attivo, mfaAttivo: x.mfaAttivo, ultimoAccesso: x.ultimoAccesso }));
  });

  app.get("/api/ruoli", async () => RUOLI.map((r) => ({ ruolo: r, permessi: PERMESSI[r] })));

  app.post("/api/utenti", { preHandler: richiede("impostazioni") }, async (req, reply) => {
    const d = z.object({ nome: z.string().min(2).max(80), email: z.string().email(), ruolo: z.enum(RUOLI) }).parse(req.body);
    if (await db.utente.findUnique({ where: { email: d.email.toLowerCase() } })) throw conflitto("Esiste già un utente con questa email");
    const temporanea = `Nx${tokenCasuale(6)}7`;
    const u = await db.utente.create({ data: {
      aziendaId: req.identita.aziendaId, nome: d.nome, email: d.email.toLowerCase(), ruolo: d.ruolo, passwordHash: await hashPassword(temporanea),
    } });
    await audit(req, "utente_creato", "utente", u.id, d.ruolo);
    return reply.status(201).send({ id: u.id, email: u.email, passwordTemporanea: temporanea });
  });

  app.patch("/api/utenti/:id", { preHandler: richiede("impostazioni") }, async (req) => {
    const { id } = req.params as { id: string };
    const d = z.object({ ruolo: z.enum(RUOLI).optional(), attivo: z.boolean().optional() }).parse(req.body);
    const u = await db.utente.findFirst({ where: { id, aziendaId: req.identita.aziendaId } });
    if (!u) throw nonTrovato("Utente");
    if (u.id === req.identita.utenteId && (d.ruolo && d.ruolo !== "admin" || d.attivo === false))
      throw conflitto("Non puoi togliere a te stesso il ruolo di amministratore o disattivarti");
    if (u.ruolo === "admin" && (d.ruolo && d.ruolo !== "admin" || d.attivo === false)) {
      const admin = await db.utente.count({ where: { aziendaId: req.identita.aziendaId, ruolo: "admin", attivo: true } });
      if (admin <= 1) throw conflitto("Deve restare almeno un amministratore attivo");
    }
    const agg = await db.utente.update({ where: { id }, data: d });
    if (d.attivo === false) await revocaTutti(id);
    await audit(req, "utente_modificato", "utente", id, JSON.stringify(d));
    return { id: agg.id, ruolo: agg.ruolo, attivo: agg.attivo };
  });

  // ── Client API (OAuth2 client credentials) ──────────────────────────────
  app.get("/api/api-client", { preHandler: richiede("impostazioni") }, async (req) =>
    (await db.apiClient.findMany({ where: { aziendaId: req.identita.aziendaId }, orderBy: { createdAt: "desc" } }))
      .map(({ secretHash: _s, ...c }) => ({ ...c, scopes: c.scopes.split(" ") })));

  app.get("/api/api-client/scopes", async () => SCOPES);

  app.post("/api/api-client", { preHandler: richiede("impostazioni") }, async (req, reply) => {
    const d = z.object({ nome: z.string().min(2).max(60), scopes: z.array(z.enum(SCOPES as [string, ...string[]])).min(1) }).parse(req.body);
    const clientId = `nx_${tokenCasuale(12)}`;
    const segreto = tokenCasuale(32);
    const c = await db.apiClient.create({ data: { aziendaId: req.identita.aziendaId, nome: d.nome, clientId, secretHash: await hashPassword(segreto), scopes: d.scopes.join(" ") } });
    await audit(req, "api_client_creato", "api_client", c.id, d.scopes.join(" "));
    return reply.status(201).send({ id: c.id, clientId, clientSecret: segreto, avviso: "Copia ora il secret: non verrà più mostrato." });
  });

  app.delete("/api/api-client/:id", { preHandler: richiede("impostazioni") }, async (req, reply) => {
    const { id } = req.params as { id: string };
    const r = await db.apiClient.updateMany({ where: { id, aziendaId: req.identita.aziendaId }, data: { revocato: new Date() } });
    if (!r.count) throw nonTrovato("Client API");
    await audit(req, "api_client_revocato", "api_client", id);
    return reply.status(204).send();
  });

  app.get("/api/audit", { preHandler: richiede("impostazioni") }, async (req) => {
    const righe = await db.auditLog.findMany({ where: { aziendaId: req.identita.aziendaId }, orderBy: { createdAt: "desc" }, take: 300 });
    const utenti = new Map((await db.utente.findMany({ where: { aziendaId: req.identita.aziendaId } })).map((u) => [u.id, u.nome]));
    return righe.map((r) => ({ ...r, utente: r.utenteId ? utenti.get(r.utenteId) ?? "utente eliminato" : "sistema / API" }));
  });
}
