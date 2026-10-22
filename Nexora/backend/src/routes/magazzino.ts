// Magazzino multi-sede: sedi, giacenze, movimenti, lotti, seriali e inventari. © Roberto Di Flumeri
import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { db } from "../lib/db.js";
import { conflitto, nonTrovato } from "../lib/errori.js";
import { arrotonda3 } from "../lib/importi.js";
import { audit, autenticato, leggibile, richiede } from "../lib/sicurezza.js";
import { movimenta } from "../services/magazzino.js";

const nomeArticolo = (v: { nome: string; prodotto: { nome: string } }) => (v.nome === "Standard" ? v.prodotto.nome : `${v.prodotto.nome} - ${v.nome}`);

export default async function rotteMagazzino(app: FastifyInstance) {
  app.addHook("preHandler", autenticato);
  app.addHook("preHandler", leggibile("magazzino"));

  app.get("/api/magazzini", async (req) => {
    const m = await db.magazzino.findMany({ where: { aziendaId: req.identita.aziendaId }, include: { giacenze: { include: { variante: { include: { prodotto: true } } } } }, orderBy: [{ principale: "desc" }, { nome: "asc" }] });
    return m.map(({ giacenze, ...x }) => ({
      ...x, articoli: giacenze.filter((g) => g.quantita > 0).length,
      valore: Math.round(giacenze.reduce((s, g) => s + Math.max(0, g.quantita) * g.variante.prodotto.costoAcquisto, 0)),
      pezzi: arrotonda3(giacenze.reduce((s, g) => s + Math.max(0, g.quantita), 0)),
    }));
  });

  app.post("/api/magazzini", { preHandler: richiede("magazzino") }, async (req, reply) => {
    const d = z.object({ codice: z.string().trim().min(2).max(10).toUpperCase(), nome: z.string().trim().min(2).max(60), indirizzo: z.string().nullish(), citta: z.string().nullish(),
      principale: z.boolean().default(false) }).parse(req.body);
    if (d.principale) await db.magazzino.updateMany({ where: { aziendaId: req.identita.aziendaId }, data: { principale: false } });
    const m = await db.magazzino.create({ data: { ...d, aziendaId: req.identita.aziendaId } });
    await audit(req, "magazzino_creato", "magazzino", m.id);
    return reply.status(201).send(m);
  });

  app.patch("/api/magazzini/:id", { preHandler: richiede("magazzino") }, async (req) => {
    const { id } = req.params as { id: string };
    const d = z.object({ nome: z.string().trim().min(2).max(60), indirizzo: z.string().nullish(), citta: z.string().nullish(), principale: z.boolean(), attivo: z.boolean() }).partial().parse(req.body);
    const m = await db.magazzino.findFirst({ where: { id, aziendaId: req.identita.aziendaId }, include: { giacenze: true } });
    if (!m) throw nonTrovato("Magazzino");
    if (d.attivo === false && m.giacenze.some((g) => g.quantita > 0)) throw conflitto("Il magazzino contiene merce: trasferiscila prima di disattivarlo");
    if (d.principale) await db.magazzino.updateMany({ where: { aziendaId: req.identita.aziendaId }, data: { principale: false } });
    return db.magazzino.update({ where: { id }, data: d });
  });

  app.get("/api/giacenze", async (req) => {
    const q = z.object({ magazzinoId: z.string().optional(), q: z.string().optional(), sottoScorta: z.coerce.boolean().optional() }).parse(req.query);
    const varianti = await db.variante.findMany({
      where: { aziendaId: req.identita.aziendaId, prodotto: { attivo: true },
        ...(q.q ? { OR: [{ sku: { contains: q.q } }, { barcode: q.q }, { prodotto: { nome: { contains: q.q } } }] } : {}) },
      include: { prodotto: { include: { categoria: true } }, giacenze: { include: { magazzino: true } } }, orderBy: { sku: "asc" },
    });
    const righe = varianti.map((v) => {
      const g = q.magazzinoId ? v.giacenze.filter((x) => x.magazzinoId === q.magazzinoId) : v.giacenze;
      const quantita = arrotonda3(g.reduce((s, x) => s + x.quantita, 0)), riservata = arrotonda3(g.reduce((s, x) => s + x.riservata, 0));
      return {
        varianteId: v.id, prodottoId: v.prodottoId, sku: v.sku, nome: nomeArticolo(v), categoria: v.prodotto.categoria?.nome ?? null, unita: v.prodotto.unita,
        quantita, riservata, disponibile: arrotonda3(quantita - riservata), scortaMinima: v.prodotto.scortaMinima, costo: v.prodotto.costoAcquisto,
        valore: Math.round(Math.max(0, quantita) * v.prodotto.costoAcquisto), gestioneLotti: v.prodotto.gestioneLotti, gestioneSeriali: v.prodotto.gestioneSeriali,
        perMagazzino: v.giacenze.map((x) => ({ magazzinoId: x.magazzinoId, magazzino: x.magazzino.nome, quantita: x.quantita, riservata: x.riservata })),
        sottoScorta: v.prodotto.scortaMinima > 0 && quantita - riservata < v.prodotto.scortaMinima,
      };
    });
    return q.sottoScorta ? righe.filter((r) => r.sottoScorta) : righe;
  });

  app.post("/api/movimenti", { preHandler: richiede("magazzino") }, async (req, reply) => {
    const d = z.object({
      tipo: z.enum(["carico", "scarico", "trasferimento", "rettifica"]), varianteId: z.string(), magazzinoId: z.string(), magazzinoDestId: z.string().nullish(),
      quantita: z.number().refine((n) => n !== 0, "deve essere diversa da zero"), lotto: z.object({ codice: z.string().trim().min(1).max(40), scadenza: z.coerce.date().nullish() }).nullish(),
      lottoId: z.string().nullish(), seriali: z.array(z.string()).nullish(), causale: z.string().max(200).nullish(), costoUnitario: z.number().int().min(0).nullish(),
    }).parse(req.body);
    const movimenti = await db.$transaction((tx) => movimenta(tx, {
      aziendaId: req.identita.aziendaId, tipo: d.tipo, varianteId: d.varianteId, magazzinoId: d.magazzinoId, magazzinoDestId: d.magazzinoDestId ?? undefined,
      quantita: d.quantita, lotto: d.lotto ?? undefined, lottoId: d.lottoId ?? undefined, seriali: d.seriali ?? undefined, causale: d.causale ?? undefined,
      costoUnitario: d.costoUnitario ?? undefined, utenteId: req.identita.utenteId,
    }));
    await audit(req, `movimento_${d.tipo}`, "variante", d.varianteId, `${d.quantita}`);
    return reply.status(201).send(movimenti);
  });

  app.get("/api/movimenti", async (req) => {
    const q = z.object({ tipo: z.string().optional(), varianteId: z.string().optional(), magazzinoId: z.string().optional(),
      dal: z.coerce.date().optional(), al: z.coerce.date().optional(), limite: z.coerce.number().int().min(1).max(500).default(100) }).parse(req.query);
    const m = await db.movimento.findMany({
      where: { aziendaId: req.identita.aziendaId, ...(q.tipo ? { tipo: q.tipo } : {}), ...(q.varianteId ? { varianteId: q.varianteId } : {}),
        ...(q.magazzinoId ? { OR: [{ magazzinoId: q.magazzinoId }, { magazzinoDestId: q.magazzinoId }] } : {}),
        ...(q.dal || q.al ? { createdAt: { ...(q.dal ? { gte: q.dal } : {}), ...(q.al ? { lte: q.al } : {}) } } : {}) },
      include: { variante: { include: { prodotto: true } } }, orderBy: { createdAt: "desc" }, take: q.limite,
    });
    const magazzini = new Map((await db.magazzino.findMany({ where: { aziendaId: req.identita.aziendaId } })).map((x) => [x.id, x.nome]));
    const lotti = new Map((await db.lotto.findMany({ where: { id: { in: m.map((x) => x.lottoId).filter((x): x is string => !!x) } } })).map((l) => [l.id, l.codice]));
    return m.map((x) => ({
      id: x.id, tipo: x.tipo, data: x.createdAt, sku: x.variante.sku, articolo: nomeArticolo(x.variante), quantita: x.quantita, unita: x.variante.prodotto.unita,
      magazzino: magazzini.get(x.magazzinoId), magazzinoDest: x.magazzinoDestId ? magazzini.get(x.magazzinoDestId) : null,
      lotto: x.lottoId ? lotti.get(x.lottoId) : null, seriali: x.seriali ? JSON.parse(x.seriali) : null, causale: x.causale, riferimentoTipo: x.riferimentoTipo, riferimentoId: x.riferimentoId,
    }));
  });

  app.get("/api/lotti", async (req) => {
    const q = z.object({ entroGiorni: z.coerce.number().int().min(1).max(3650).default(3650), varianteId: z.string().optional() }).parse(req.query);
    const limite = new Date(Date.now() + q.entroGiorni * 86400_000);
    const l = await db.lotto.findMany({
      where: { variante: { aziendaId: req.identita.aziendaId }, ...(q.varianteId ? { varianteId: q.varianteId } : {}), OR: [{ scadenza: { lte: limite } }, { scadenza: null }] },
      include: { variante: { include: { prodotto: true } }, giacenze: { include: { magazzino: true } } }, orderBy: { scadenza: "asc" },
    });
    return l.map((x) => ({
      id: x.id, codice: x.codice, scadenza: x.scadenza, varianteId: x.varianteId, sku: x.variante.sku, articolo: nomeArticolo(x.variante),
      quantita: arrotonda3(x.giacenze.reduce((s, g) => s + g.quantita, 0)), giorniAllaScadenza: x.scadenza ? Math.ceil((x.scadenza.getTime() - Date.now()) / 86400_000) : null,
      perMagazzino: x.giacenze.filter((g) => g.quantita > 0).map((g) => ({ magazzinoId: g.magazzinoId, magazzino: g.magazzino.nome, quantita: g.quantita })),
    })).filter((x) => x.quantita > 0);
  });

  app.get("/api/seriali", async (req) => {
    const q = z.object({ q: z.string().min(1) }).parse(req.query);
    const s = await db.seriale.findMany({ where: { numero: { contains: q.q }, variante: { aziendaId: req.identita.aziendaId } }, include: { variante: { include: { prodotto: true } } }, take: 50 });
    const ordini = new Map((await db.ordineVendita.findMany({ where: { id: { in: s.map((x) => x.ordineId).filter((x): x is string => !!x) } }, include: { cliente: true } })).map((o) => [o.id, o]));
    const magazzini = new Map((await db.magazzino.findMany({ where: { aziendaId: req.identita.aziendaId } })).map((x) => [x.id, x.nome]));
    return s.map((x) => ({ numero: x.numero, stato: x.stato, sku: x.variante.sku, articolo: nomeArticolo(x.variante), magazzino: x.magazzinoId ? magazzini.get(x.magazzinoId) : null,
      ordine: x.ordineId ? { id: x.ordineId, numero: ordini.get(x.ordineId)?.numero, cliente: ordini.get(x.ordineId)?.cliente.ragioneSociale } : null }));
  });

  // ── Inventari ────────────────────────────────────────────────────────────
  app.get("/api/inventari", async (req) => {
    const i = await db.inventario.findMany({ where: { aziendaId: req.identita.aziendaId }, include: { magazzino: true, righe: true }, orderBy: { createdAt: "desc" } });
    return i.map(({ righe, ...x }) => ({ ...x, righe: righe.length, contate: righe.filter((r) => r.quantitaContata !== null).length,
      differenze: righe.filter((r) => r.quantitaContata !== null && r.quantitaContata !== r.quantitaSistema).length }));
  });

  app.post("/api/inventari", { preHandler: richiede("magazzino") }, async (req, reply) => {
    const d = z.object({ magazzinoId: z.string(), categoriaId: z.string().nullish(), note: z.string().max(200).nullish() }).parse(req.body);
    const m = await db.magazzino.findFirst({ where: { id: d.magazzinoId, aziendaId: req.identita.aziendaId } });
    if (!m) throw nonTrovato("Magazzino");
    if (await db.inventario.findFirst({ where: { magazzinoId: m.id, stato: "aperto" } })) throw conflitto("C'è già un inventario aperto per questo magazzino");
    const varianti = await db.variante.findMany({ where: { aziendaId: req.identita.aziendaId, attivo: true, prodotto: { attivo: true, gestioneSeriali: false, ...(d.categoriaId ? { categoriaId: d.categoriaId } : {}) } },
      include: { giacenze: { where: { magazzinoId: m.id } } } });
    const inv = await db.inventario.create({ data: { aziendaId: req.identita.aziendaId, magazzinoId: m.id, note: d.note ?? null,
      righe: { create: varianti.map((v) => ({ varianteId: v.id, quantitaSistema: v.giacenze[0]?.quantita ?? 0 })) } } });
    await audit(req, "inventario_aperto", "inventario", inv.id);
    return reply.status(201).send(inv);
  });

  app.get("/api/inventari/:id", async (req) => {
    const i = await db.inventario.findFirst({ where: { id: (req.params as { id: string }).id, aziendaId: req.identita.aziendaId }, include: { magazzino: true, righe: true } });
    if (!i) throw nonTrovato("Inventario");
    const v = new Map((await db.variante.findMany({ where: { id: { in: i.righe.map((r) => r.varianteId) } }, include: { prodotto: true } })).map((x) => [x.id, x]));
    return { ...i, righe: i.righe.map((r) => ({ ...r, sku: v.get(r.varianteId)?.sku, articolo: v.get(r.varianteId) ? nomeArticolo(v.get(r.varianteId)!) : "",
      barcode: v.get(r.varianteId)?.barcode ?? v.get(r.varianteId)?.prodotto.barcode, unita: v.get(r.varianteId)?.prodotto.unita,
      differenza: r.quantitaContata === null ? null : arrotonda3(r.quantitaContata - r.quantitaSistema) })).sort((a, b) => (a.sku || "").localeCompare(b.sku || "")) };
  });

  app.patch("/api/inventari/:id/righe", { preHandler: richiede("magazzino") }, async (req) => {
    const d = z.object({ varianteId: z.string(), quantitaContata: z.number().min(0).nullable(), somma: z.boolean().default(false) }).parse(req.body);
    const i = await db.inventario.findFirst({ where: { id: (req.params as { id: string }).id, aziendaId: req.identita.aziendaId } });
    if (!i) throw nonTrovato("Inventario");
    if (i.stato !== "aperto") throw conflitto("L'inventario è chiuso");
    const r = await db.inventarioRiga.findUnique({ where: { inventarioId_varianteId: { inventarioId: i.id, varianteId: d.varianteId } } });
    if (!r) throw nonTrovato("Articolo nell'inventario");
    // con «somma» ogni scansione del barcode aggiunge una unità al conteggio
    const q = d.somma ? arrotonda3((r.quantitaContata ?? 0) + (d.quantitaContata ?? 1)) : d.quantitaContata;
    return db.inventarioRiga.update({ where: { id: r.id }, data: { quantitaContata: q } });
  });

  app.post("/api/inventari/:id/chiudi", { preHandler: richiede("magazzino") }, async (req) => {
    const i = await db.inventario.findFirst({ where: { id: (req.params as { id: string }).id, aziendaId: req.identita.aziendaId }, include: { righe: true } });
    if (!i) throw nonTrovato("Inventario");
    if (i.stato !== "aperto") throw conflitto("L'inventario è già chiuso");
    const rettifiche = await db.$transaction(async (tx) => {
      let n = 0;
      for (const r of i.righe) {
        if (r.quantitaContata === null) continue;
        // si rettifica la differenza tra conteggio e giacenza fotografata all'apertura:
        // i movimenti registrati durante la conta restano validi e si sommano
        const attesa = arrotonda3(r.quantitaContata - r.quantitaSistema);
        if (attesa === 0) continue;
        const v = await tx.variante.findUnique({ where: { id: r.varianteId }, include: { prodotto: true } });
        await movimenta(tx, { aziendaId: i.aziendaId, tipo: "rettifica", varianteId: r.varianteId, magazzinoId: i.magazzinoId, quantita: attesa,
          lotto: v?.prodotto.gestioneLotti && attesa > 0 ? { codice: `INV-${new Date().toISOString().slice(0, 10)}` } : undefined,
          causale: "Rettifica da inventario", riferimentoTipo: "inventario", riferimentoId: i.id, utenteId: req.identita.utenteId });
        n++;
      }
      await tx.inventario.update({ where: { id: i.id }, data: { stato: "chiuso", chiusoAt: new Date() } });
      return n;
    });
    await audit(req, "inventario_chiuso", "inventario", i.id, `${rettifiche} rettifiche`);
    return { chiuso: true, rettifiche };
  });
}
