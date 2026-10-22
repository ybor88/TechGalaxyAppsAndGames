// Ordini di vendita, spedizioni e ordini di acquisto. © Roberto Di Flumeri
import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { db } from "../lib/db.js";
import { conflitto } from "../lib/errori.js";
import { audit, autenticato, leggibile, puo, richiede } from "../lib/sicurezza.js";
import { annullaAcquisto, caricaAcquisto, creaAcquisto, inviaAcquisto, riceviAcquisto } from "../services/acquisti.js";
import { creaSpedizione, etichettaPdf, tracciamento, URL_TRACCIAMENTO } from "../services/integrazioni.js";
import { caricaOrdine, cambiaStato, creaOrdine, fatturaDaOrdine, modificaOrdine, STATI_ORDINE } from "../services/ordini.js";

const rigaOrdine = z.object({
  varianteId: z.string(), quantita: z.number().positive(), prezzoUnitario: z.number().int().min(0).optional(), sconto: z.number().min(0).max(100).optional(),
  descrizione: z.string().max(300).optional(), lottoId: z.string().nullish(), seriali: z.array(z.string()).nullish(),
});

export default async function rotteVendite(app: FastifyInstance) {
  app.addHook("preHandler", autenticato);

  // ── Ordini di vendita ────────────────────────────────────────────────────
  app.get("/api/ordini", { preHandler: leggibile("ordini") }, async (req) => {
    const q = z.object({ stato: z.string().optional(), q: z.string().optional(), canale: z.string().optional(), clienteId: z.string().optional(),
      limite: z.coerce.number().int().min(1).max(1000).default(300) }).parse(req.query);
    const o = await db.ordineVendita.findMany({
      where: { aziendaId: req.identita.aziendaId, ...(q.stato ? { stato: { in: q.stato.split(",") } } : {}), ...(q.canale ? { canale: q.canale } : {}),
        ...(q.clienteId ? { clienteId: q.clienteId } : {}),
        ...(q.q ? { OR: [{ numero: { contains: q.q } }, { cliente: { ragioneSociale: { contains: q.q } } }, { riferimentoEsterno: { contains: q.q } }] } : {}) },
      include: { cliente: { select: { ragioneSociale: true } }, magazzino: { select: { nome: true } }, _count: { select: { righe: true } } },
      orderBy: { data: "desc" }, take: q.limite,
    });
    return o.map(({ _count, ...x }) => ({ ...x, righe: _count.righe }));
  });

  app.get("/api/ordini/:id", { preHandler: leggibile("ordini") }, async (req) => {
    const { id } = req.params as { id: string };
    const o = await caricaOrdine(db, req.identita.aziendaId, id);
    const documenti = await db.documento.findMany({ where: { ordineId: id }, orderBy: { createdAt: "asc" }, select: { id: true, tipo: true, numero: true, stato: true, totale: true, data: true } });
    const varianti = new Map((await db.variante.findMany({ where: { id: { in: o.righe.map((r) => r.varianteId) } }, include: { prodotto: true, giacenze: { where: { magazzinoId: o.magazzinoId } } } })).map((v) => [v.id, v]));
    const lotti = new Map((await db.lotto.findMany({ where: { id: { in: o.righe.map((r) => r.lottoId).filter((x): x is string => !!x) } } })).map((l) => [l.id, l]));
    return {
      ...o, documenti,
      righe: o.righe.map((r) => {
        const v = varianti.get(r.varianteId);
        const g = v?.giacenze[0];
        return { ...r, sku: v?.sku, unita: v?.prodotto.unita, gestioneLotti: v?.prodotto.gestioneLotti, gestioneSeriali: v?.prodotto.gestioneSeriali,
          seriali: r.seriali ? JSON.parse(r.seriali) : null, lotto: r.lottoId ? lotti.get(r.lottoId)?.codice : null,
          disponibile: g ? g.quantita - g.riservata : 0 };
      }),
      spedizioni: o.spedizioni.map((s) => ({ ...s, eventi: JSON.parse(s.eventi || "[]"), link: URL_TRACCIAMENTO[s.corriere]?.(s.tracking) ?? null })),
      transizioni: ({ bozza: ["confermato", "annullato"], confermato: ["in_preparazione", "spedito", "annullato"], in_preparazione: ["spedito", "annullato"],
        spedito: ["consegnato"], consegnato: [], annullato: [] } as Record<string, string[]>)[o.stato],
    };
  });

  app.post("/api/ordini", { preHandler: richiede("ordini") }, async (req, reply) => {
    const d = z.object({ clienteId: z.string(), magazzinoId: z.string().optional(), righe: z.array(rigaOrdine).min(1), note: z.string().max(1000).nullish(),
      indirizzoSpedizione: z.string().max(300).nullish(), dataConsegnaPrevista: z.coerce.date().nullish() }).parse(req.body);
    const o = await db.$transaction((tx) => creaOrdine(tx, req.identita.aziendaId, d));
    await audit(req, "ordine_creato", "ordine", o.id, o.numero);
    return reply.status(201).send(o);
  });

  app.patch("/api/ordini/:id", { preHandler: richiede("ordini") }, async (req) => {
    const d = z.object({ righe: z.array(rigaOrdine).min(1).optional(), note: z.string().max(1000).nullish(), indirizzoSpedizione: z.string().max(300).nullish(), magazzinoId: z.string().optional() }).parse(req.body);
    return db.$transaction((tx) => modificaOrdine(tx, req.identita.aziendaId, (req.params as { id: string }).id, d));
  });

  app.post("/api/ordini/:id/stato", async (req) => {
    const { stato } = z.object({ stato: z.enum(STATI_ORDINE) }).parse(req.body);
    // preparare, spedire e consegnare è anche compito del magazzino; confermare e annullare spetta alle vendite
    await richiede(["in_preparazione", "spedito", "consegnato"].includes(stato) && puo(req, "spedizioni") ? "spedizioni" : "ordini")(req);
    const { id } = req.params as { id: string };
    const r = await db.$transaction((tx) => cambiaStato(tx, req.identita.aziendaId, id, stato, { utenteId: req.identita.utenteId }), { timeout: 20000 });
    await audit(req, `ordine_${stato}`, "ordine", id, r.ordine.numero);
    return r;
  });

  app.post("/api/ordini/:id/fattura", { preHandler: richiede("documenti") }, async (req, reply) => {
    const f = await db.$transaction((tx) => fatturaDaOrdine(tx, req.identita.aziendaId, (req.params as { id: string }).id));
    await audit(req, "fattura_da_ordine", "documento", f.id);
    return reply.status(201).send(f);
  });

  app.delete("/api/ordini/:id", { preHandler: richiede("ordini") }, async (req, reply) => {
    const o = await caricaOrdine(db, req.identita.aziendaId, (req.params as { id: string }).id);
    if (o.stato !== "bozza") throw conflitto("Si possono eliminare solo gli ordini in bozza: gli altri si annullano");
    await db.ordineVendita.delete({ where: { id: o.id } });
    await audit(req, "ordine_eliminato", "ordine", o.id, o.numero);
    return reply.status(204).send();
  });

  // ── Spedizioni ───────────────────────────────────────────────────────────
  app.post("/api/ordini/:id/spedizioni", { preHandler: richiede("spedizioni") }, async (req, reply) => {
    const d = z.object({ corriere: z.enum(["gls", "dhl", "ritiro"]), colli: z.number().int().min(1).max(99).default(1), pesoKg: z.number().positive().max(1000).default(1),
      tracking: z.string().trim().max(40).optional() }).parse(req.body);
    const s = await creaSpedizione(req.identita.aziendaId, (req.params as { id: string }).id, d);
    await audit(req, "spedizione_creata", "spedizione", s.id, `${d.corriere} ${s.tracking}`);
    return reply.status(201).send(s);
  });

  app.get("/api/spedizioni/:id", { preHandler: leggibile("ordini") }, async (req) => tracciamento(req.identita.aziendaId, (req.params as { id: string }).id));

  app.get("/api/spedizioni/:id/etichetta.pdf", { preHandler: leggibile("ordini") }, async (req, reply) =>
    reply.type("application/pdf").header("Content-Disposition", "inline; filename=etichetta.pdf").send(await etichettaPdf(req.identita.aziendaId, (req.params as { id: string }).id)));

  // ── Acquisti ─────────────────────────────────────────────────────────────
  app.get("/api/acquisti", { preHandler: leggibile("magazzino") }, async (req) => {
    const q = z.object({ stato: z.string().optional() }).parse(req.query);
    const o = await db.ordineAcquisto.findMany({ where: { aziendaId: req.identita.aziendaId, ...(q.stato ? { stato: { in: q.stato.split(",") } } : {}) },
      include: { fornitore: { select: { ragioneSociale: true } }, magazzino: { select: { nome: true } }, righe: true }, orderBy: { data: "desc" }, take: 300 });
    return o.map(({ righe, ...x }) => ({ ...x, righe: righe.length, avanzamento: righe.length ? Math.round((righe.reduce((s, r) => s + Math.min(1, r.quantitaRicevuta / r.quantita), 0) / righe.length) * 100) : 0 }));
  });

  app.get("/api/acquisti/:id", { preHandler: leggibile("magazzino") }, async (req) => {
    const o = await caricaAcquisto(db, req.identita.aziendaId, (req.params as { id: string }).id);
    const v = new Map((await db.variante.findMany({ where: { id: { in: o.righe.map((r) => r.varianteId) } }, include: { prodotto: true } })).map((x) => [x.id, x]));
    return { ...o, righe: o.righe.map((r) => ({ ...r, sku: v.get(r.varianteId)?.sku, unita: v.get(r.varianteId)?.prodotto.unita,
      gestioneLotti: v.get(r.varianteId)?.prodotto.gestioneLotti, gestioneSeriali: v.get(r.varianteId)?.prodotto.gestioneSeriali })) };
  });

  app.post("/api/acquisti", { preHandler: richiede("acquisti") }, async (req, reply) => {
    const d = z.object({ fornitoreId: z.string(), magazzinoId: z.string().optional(), note: z.string().max(1000).nullish(),
      righe: z.array(z.object({ varianteId: z.string(), quantita: z.number().positive(), costoUnitario: z.number().int().min(0).optional() })).min(1) }).parse(req.body);
    const o = await db.$transaction((tx) => creaAcquisto(tx, req.identita.aziendaId, d));
    await audit(req, "acquisto_creato", "acquisto", o.id, o.numero);
    return reply.status(201).send(o);
  });

  app.post("/api/acquisti/:id/invia", { preHandler: richiede("acquisti") }, async (req) => {
    const o = await db.$transaction((tx) => inviaAcquisto(tx, req.identita.aziendaId, (req.params as { id: string }).id));
    await audit(req, "acquisto_inviato", "acquisto", o.id, o.numero);
    return o;
  });

  app.post("/api/acquisti/:id/annulla", { preHandler: richiede("acquisti") }, async (req) =>
    db.$transaction((tx) => annullaAcquisto(tx, req.identita.aziendaId, (req.params as { id: string }).id)));

  app.post("/api/acquisti/:id/ricevi", { preHandler: richiede("acquisti") }, async (req) => {
    const d = z.object({ righe: z.array(z.object({ rigaId: z.string(), quantita: z.number().positive(),
      lotto: z.object({ codice: z.string().trim().min(1), scadenza: z.coerce.date().nullish() }).optional(), seriali: z.array(z.string()).optional() })).min(1) }).parse(req.body);
    const { id } = req.params as { id: string };
    const o = await db.$transaction((tx) => riceviAcquisto(tx, req.identita.aziendaId, id, d.righe, req.identita.utenteId), { timeout: 20000 });
    await audit(req, "acquisto_ricevuto", "acquisto", id, o.stato);
    return o;
  });

  app.delete("/api/acquisti/:id", { preHandler: richiede("acquisti") }, async (req, reply) => {
    const o = await caricaAcquisto(db, req.identita.aziendaId, (req.params as { id: string }).id);
    if (o.stato !== "bozza") throw conflitto("Si possono eliminare solo gli ordini in bozza");
    await db.ordineAcquisto.delete({ where: { id: o.id } });
    return reply.status(204).send();
  });

  app.get("/api/ordini-stati", async () => STATI_ORDINE);
}
