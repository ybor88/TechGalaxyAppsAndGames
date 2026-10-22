// Anagrafiche: CRM clienti (con storico ordini e fatture) e fornitori. © Roberto Di Flumeri
import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { db } from "../lib/db.js";
import { conflitto, nonTrovato, nonValido } from "../lib/errori.js";
import { codiceDestinatarioValido, codiceFiscaleValido, partitaIvaValida } from "../lib/fiscale.js";
import { audit, autenticato, leggibile, richiede } from "../lib/sicurezza.js";
import { analisiClienti } from "../services/ai.js";

const opz = z.string().trim().max(200).nullish().transform((v) => (v ? v : null));
const indirizzo = {
  indirizzo: opz, cap: z.string().trim().regex(/^\d{5}$|^$/, "CAP di 5 cifre").nullish().transform((v) => v || null),
  citta: opz, provincia: z.string().trim().max(2).nullish().transform((v) => (v ? v.toUpperCase() : null)), nazione: z.string().trim().length(2).default("IT"),
};

const clienteSchema = z.object({
  tipo: z.enum(["azienda", "privato", "pa"]).default("azienda"), ragioneSociale: z.string().trim().min(2).max(120),
  partitaIva: opz, codiceFiscale: opz, codiceDestinatario: opz, pec: z.string().email().nullish().or(z.literal("")).transform((v) => v || null),
  email: z.string().email().nullish().or(z.literal("")).transform((v) => v || null), telefono: opz, ...indirizzo,
  scontoPercentuale: z.number().min(0).max(90).default(0), giorniPagamento: z.number().int().min(0).max(365).default(30), note: z.string().max(2000).nullish(),
});

const fornitoreSchema = z.object({
  ragioneSociale: z.string().trim().min(2).max(120), partitaIva: opz, codiceFiscale: opz,
  email: z.string().email().nullish().or(z.literal("")).transform((v) => v || null), telefono: opz, ...indirizzo, iban: opz,
  leadTimeGiorni: z.number().int().min(0).max(365).default(7), giorniPagamento: z.number().int().min(0).max(365).default(30), note: z.string().max(2000).nullish(),
});

function verificaFiscale(d: { nazione?: string; partitaIva?: string | null; codiceFiscale?: string | null; codiceDestinatario?: string | null; tipo?: string }) {
  if ((d.nazione ?? "IT") !== "IT") return;
  if (d.partitaIva && !partitaIvaValida(d.partitaIva)) throw nonValido("Partita IVA: non valida (controlla le 11 cifre)");
  if (d.codiceFiscale && !codiceFiscaleValido(d.codiceFiscale)) throw nonValido("Codice fiscale: non valido");
  if (d.codiceDestinatario && !codiceDestinatarioValido(d.codiceDestinatario)) throw nonValido("Codice destinatario SDI: 7 caratteri alfanumerici");
  if (d.tipo === "azienda" && !d.partitaIva) throw nonValido("Per un cliente azienda serve la partita IVA");
}

export default async function rotteAnagrafiche(app: FastifyInstance) {
  app.addHook("preHandler", autenticato);

  // ── Clienti ──────────────────────────────────────────────────────────────
  app.get("/api/clienti", { preHandler: leggibile("clienti") }, async (req) => {
    const { q } = z.object({ q: z.string().optional() }).parse(req.query);
    const clienti = await db.cliente.findMany({
      where: { aziendaId: req.identita.aziendaId, ...(q ? { OR: [{ ragioneSociale: { contains: q } }, { partitaIva: { contains: q } }, { email: { contains: q } }, { citta: { contains: q } }] } : {}) },
      orderBy: { ragioneSociale: "asc" },
    });
    const totali = await db.ordineVendita.groupBy({
      by: ["clienteId"], where: { aziendaId: req.identita.aziendaId, stato: { notIn: ["bozza", "annullato"] } }, _sum: { imponibile: true }, _count: true, _max: { data: true },
    });
    const crediti = await db.scadenza.findMany({ where: { pagata: false, documento: { aziendaId: req.identita.aziendaId, tipo: "fattura", stato: "emessa" } }, include: { documento: true } });
    const perCliente = new Map(totali.map((t) => [t.clienteId, t]));
    return clienti.map((c) => ({
      ...c, fatturato: perCliente.get(c.id)?._sum.imponibile ?? 0, ordini: perCliente.get(c.id)?._count ?? 0, ultimoOrdine: perCliente.get(c.id)?._max.data ?? null,
      creditoAperto: crediti.filter((s) => s.documento.clienteId === c.id).reduce((s, x) => s + x.importo, 0),
    }));
  });

  app.get("/api/clienti/:id", { preHandler: leggibile("clienti") }, async (req) => {
    const { id } = req.params as { id: string };
    const c = await db.cliente.findFirst({ where: { id, aziendaId: req.identita.aziendaId } });
    if (!c) throw nonTrovato("Cliente");
    const ordini = await db.ordineVendita.findMany({ where: { clienteId: id }, orderBy: { data: "desc" }, take: 100, include: { _count: { select: { righe: true } } } });
    const documenti = await db.documento.findMany({ where: { clienteId: id }, orderBy: { data: "desc" }, take: 100, include: { scadenze: true } });
    const profilo = (await analisiClienti(req.identita.aziendaId)).find((p) => p.clienteId === id) ?? null;
    const prodotti = await db.rigaOrdine.groupBy({ by: ["descrizione"], where: { ordine: { clienteId: id, stato: { notIn: ["bozza", "annullato"] } } },
      _sum: { quantita: true }, orderBy: { _sum: { quantita: "desc" } }, take: 5 });
    return {
      ...c, ordini, documenti, profilo, prodottiPreferiti: prodotti.map((p) => ({ descrizione: p.descrizione, quantita: p._sum.quantita })),
      fatturato: ordini.filter((o) => !["bozza", "annullato"].includes(o.stato)).reduce((s, o) => s + o.imponibile, 0),
      creditoAperto: documenti.filter((d) => d.tipo === "fattura" && d.stato === "emessa").flatMap((d) => d.scadenze).filter((s) => !s.pagata).reduce((s, x) => s + x.importo, 0),
    };
  });

  app.post("/api/clienti", { preHandler: richiede("clienti") }, async (req, reply) => {
    const d = clienteSchema.parse(req.body);
    verificaFiscale(d);
    const c = await db.cliente.create({ data: { ...d, aziendaId: req.identita.aziendaId, codiceDestinatario: d.codiceDestinatario?.toUpperCase() ?? null } });
    await audit(req, "cliente_creato", "cliente", c.id);
    return reply.status(201).send(c);
  });

  app.patch("/api/clienti/:id", { preHandler: richiede("clienti") }, async (req) => {
    const { id } = req.params as { id: string };
    const c = await db.cliente.findFirst({ where: { id, aziendaId: req.identita.aziendaId } });
    if (!c) throw nonTrovato("Cliente");
    const d = clienteSchema.partial().parse(req.body);
    verificaFiscale({ ...c, ...d });
    return db.cliente.update({ where: { id }, data: { ...d, codiceDestinatario: d.codiceDestinatario === undefined ? undefined : d.codiceDestinatario?.toUpperCase() ?? null } });
  });

  app.delete("/api/clienti/:id", { preHandler: richiede("clienti") }, async (req, reply) => {
    const { id } = req.params as { id: string };
    const c = await db.cliente.findFirst({ where: { id, aziendaId: req.identita.aziendaId } });
    if (!c) throw nonTrovato("Cliente");
    if (await db.ordineVendita.count({ where: { clienteId: id } }) || await db.documento.count({ where: { clienteId: id } }))
      throw conflitto("Il cliente ha ordini o documenti: non può essere eliminato (i documenti fiscali vanno conservati)");
    await db.cliente.delete({ where: { id } });
    await audit(req, "cliente_eliminato", "cliente", id);
    return reply.status(204).send();
  });

  // ── Fornitori ────────────────────────────────────────────────────────────
  app.get("/api/fornitori", { preHandler: leggibile("clienti") }, async (req) => {
    const f = await db.fornitore.findMany({ where: { aziendaId: req.identita.aziendaId }, include: { _count: { select: { prodotti: true, acquisti: true } } }, orderBy: { ragioneSociale: "asc" } });
    const spesa = await db.ordineAcquisto.groupBy({ by: ["fornitoreId"], where: { aziendaId: req.identita.aziendaId, stato: { not: "annullato" } }, _sum: { imponibile: true } });
    const m = new Map(spesa.map((s) => [s.fornitoreId, s._sum.imponibile ?? 0]));
    return f.map((x) => ({ ...x, prodotti: x._count.prodotti, acquisti: x._count.acquisti, speso: m.get(x.id) ?? 0 }));
  });

  app.get("/api/fornitori/:id", { preHandler: leggibile("clienti") }, async (req) => {
    const { id } = req.params as { id: string };
    const f = await db.fornitore.findFirst({ where: { id, aziendaId: req.identita.aziendaId },
      include: { prodotti: { select: { id: true, sku: true, nome: true, costoAcquisto: true, attivo: true } }, acquisti: { orderBy: { data: "desc" }, take: 50 } } });
    if (!f) throw nonTrovato("Fornitore");
    // puntualità: consegne ricevute rispetto alla data prevista
    const ricevuti = f.acquisti.filter((a) => a.stato === "ricevuto" && a.dataPrevista);
    return { ...f, ordiniRicevuti: ricevuti.length };
  });

  app.post("/api/fornitori", { preHandler: richiede("fornitori") }, async (req, reply) => {
    const d = fornitoreSchema.parse(req.body);
    verificaFiscale(d);
    const f = await db.fornitore.create({ data: { ...d, aziendaId: req.identita.aziendaId } });
    await audit(req, "fornitore_creato", "fornitore", f.id);
    return reply.status(201).send(f);
  });

  app.patch("/api/fornitori/:id", { preHandler: richiede("fornitori") }, async (req) => {
    const { id } = req.params as { id: string };
    const f = await db.fornitore.findFirst({ where: { id, aziendaId: req.identita.aziendaId } });
    if (!f) throw nonTrovato("Fornitore");
    const d = fornitoreSchema.partial().parse(req.body);
    verificaFiscale({ ...f, ...d });
    return db.fornitore.update({ where: { id }, data: d });
  });

  app.delete("/api/fornitori/:id", { preHandler: richiede("fornitori") }, async (req, reply) => {
    const { id } = req.params as { id: string };
    const f = await db.fornitore.findFirst({ where: { id, aziendaId: req.identita.aziendaId } });
    if (!f) throw nonTrovato("Fornitore");
    if (await db.ordineAcquisto.count({ where: { fornitoreId: id } })) throw conflitto("Il fornitore ha ordini di acquisto: non può essere eliminato");
    await db.fornitore.delete({ where: { id } });
    return reply.status(204).send();
  });
}
