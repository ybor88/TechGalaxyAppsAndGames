// Catalogo prodotti: SKU, barcode, categorie, prezzi, varianti e immagini. © Roberto Di Flumeri
import { mkdirSync, readFileSync, existsSync, unlinkSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import bwipjs from "bwip-js";
import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { config } from "../lib/config.js";
import { tokenCasuale } from "../lib/crypto.js";
import { db } from "../lib/db.js";
import { conflitto, nonTrovato, nonValido } from "../lib/errori.js";
import { NATURE_IVA } from "../lib/fiscale.js";
import { arrotonda3 } from "../lib/importi.js";
import { audit, autenticato, leggibile, richiede } from "../lib/sicurezza.js";

const centesimi = z.number().int().min(0).max(1_000_000_00);
const varianteSchema = z.object({
  sku: z.string().trim().min(1).max(40), nome: z.string().trim().min(1).max(80), barcode: z.string().trim().max(32).nullish(),
  prezzo: centesimi.nullish(), attributi: z.record(z.string()).nullish(),
});
const prodottoSchema = z.object({
  sku: z.string().trim().min(1).max(40).regex(/^[A-Za-z0-9._-]+$/, "solo lettere, numeri, punto, trattino e trattino basso"),
  nome: z.string().trim().min(2).max(120), descrizione: z.string().max(2000).nullish(), categoriaId: z.string().nullish(),
  unita: z.enum(["pz", "kg", "lt", "m", "conf"]).default("pz"), prezzoVendita: centesimi, costoAcquisto: centesimi.default(0),
  aliquotaIva: z.number().refine((v) => [22, 10, 5, 4, 0].includes(v), "aliquota non ammessa"), naturaIva: z.string().nullish(),
  barcode: z.string().trim().max(32).nullish(), gestioneLotti: z.boolean().default(false), gestioneSeriali: z.boolean().default(false),
  scortaMinima: z.number().min(0).default(0), leadTimeGiorni: z.number().int().min(0).max(365).default(7), fornitoreId: z.string().nullish(),
  attivo: z.boolean().default(true), varianti: z.array(varianteSchema).max(100).optional(),
});

/** EAN-13 / EAN-8 / UPC: verifica della cifra di controllo. */
export function barcodeValido(codice: string): boolean {
  if (!/^\d{8}$|^\d{12,13}$/.test(codice)) return true; // codici interni (Code 128) liberi
  const cifre = codice.split("").map(Number);
  const controllo = cifre.pop()!;
  const somma = cifre.reverse().reduce((s, c, i) => s + c * (i % 2 === 0 ? 3 : 1), 0);
  return (10 - (somma % 10)) % 10 === controllo;
}

async function verificaRiferimenti(aziendaId: string, d: { categoriaId?: string | null; fornitoreId?: string | null; aliquotaIva?: number; naturaIva?: string | null; barcode?: string | null; gestioneLotti?: boolean; gestioneSeriali?: boolean }) {
  if (d.categoriaId && !(await db.categoria.findFirst({ where: { id: d.categoriaId, aziendaId } }))) throw nonTrovato("Categoria");
  if (d.fornitoreId && !(await db.fornitore.findFirst({ where: { id: d.fornitoreId, aziendaId } }))) throw nonTrovato("Fornitore");
  if (d.aliquotaIva === 0 && (!d.naturaIva || !NATURE_IVA[d.naturaIva])) throw nonValido("Con IVA 0% indica la natura dell'operazione (es. N2.2)");
  if (d.barcode && !barcodeValido(d.barcode)) throw nonValido("Barcode: cifra di controllo EAN non valida");
  if (d.gestioneLotti && d.gestioneSeriali) throw nonValido("Un prodotto si gestisce a lotti oppure a numeri di serie, non entrambi");
}

function disponibilita(giacenze: { quantita: number; riservata: number }[]) {
  const q = giacenze.reduce((s, g) => s + g.quantita, 0), r = giacenze.reduce((s, g) => s + g.riservata, 0);
  return { giacenza: arrotonda3(q), riservata: arrotonda3(r), disponibile: arrotonda3(q - r) };
}

export default async function rotteCatalogo(app: FastifyInstance) {
  app.addHook("preHandler", autenticato);

  // ── Categorie ────────────────────────────────────────────────────────────
  app.get("/api/categorie", { preHandler: leggibile("catalogo") }, async (req) => {
    const c = await db.categoria.findMany({ where: { aziendaId: req.identita.aziendaId }, include: { _count: { select: { prodotti: true } } }, orderBy: { nome: "asc" } });
    return c.map((x) => ({ id: x.id, nome: x.nome, parentId: x.parentId, prodotti: x._count.prodotti }));
  });
  app.post("/api/categorie", { preHandler: richiede("catalogo") }, async (req, reply) => {
    const d = z.object({ nome: z.string().trim().min(2).max(60), parentId: z.string().nullish() }).parse(req.body);
    return reply.status(201).send(await db.categoria.create({ data: { aziendaId: req.identita.aziendaId, nome: d.nome, parentId: d.parentId ?? null } }));
  });
  app.delete("/api/categorie/:id", { preHandler: richiede("catalogo") }, async (req, reply) => {
    const r = await db.categoria.deleteMany({ where: { id: (req.params as { id: string }).id, aziendaId: req.identita.aziendaId } });
    if (!r.count) throw nonTrovato("Categoria");
    return reply.status(204).send();
  });

  // ── Prodotti ─────────────────────────────────────────────────────────────
  app.get("/api/prodotti", { preHandler: leggibile("catalogo") }, async (req) => {
    const q = z.object({ q: z.string().optional(), categoriaId: z.string().optional(), stato: z.enum(["attivi", "disattivi", "tutti"]).default("attivi"),
      sottoScorta: z.coerce.boolean().optional() }).parse(req.query);
    const prodotti = await db.prodotto.findMany({
      where: {
        aziendaId: req.identita.aziendaId, ...(q.categoriaId ? { categoriaId: q.categoriaId } : {}),
        ...(q.stato === "tutti" ? {} : { attivo: q.stato === "attivi" }),
        ...(q.q ? { OR: [{ nome: { contains: q.q } }, { sku: { contains: q.q } }, { barcode: { contains: q.q } }, { varianti: { some: { OR: [{ sku: { contains: q.q } }, { barcode: { contains: q.q } }] } } }] } : {}),
      },
      include: { categoria: true, varianti: { include: { giacenze: true } }, immagini: { orderBy: { ordine: "asc" }, take: 1 }, fornitore: { select: { ragioneSociale: true } } },
      orderBy: { nome: "asc" },
    });
    const out = prodotti.map((p) => {
      const d = disponibilita(p.varianti.flatMap((v) => v.giacenze));
      return {
        id: p.id, sku: p.sku, nome: p.nome, categoria: p.categoria?.nome ?? null, unita: p.unita, prezzoVendita: p.prezzoVendita, costoAcquisto: p.costoAcquisto,
        aliquotaIva: p.aliquotaIva, attivo: p.attivo, varianti: p.varianti.length, gestioneLotti: p.gestioneLotti, gestioneSeriali: p.gestioneSeriali,
        scortaMinima: p.scortaMinima, sottoScorta: p.scortaMinima > 0 && d.disponibile < p.scortaMinima, immagine: p.immagini[0]?.file ?? null,
        fornitore: p.fornitore?.ragioneSociale ?? null, barcode: p.barcode, ...d,
      };
    });
    return q.sottoScorta ? out.filter((p) => p.sottoScorta) : out;
  });

  app.get("/api/prodotti/:id", { preHandler: leggibile("catalogo") }, async (req) => {
    const { id } = req.params as { id: string };
    const p = await db.prodotto.findFirst({
      where: { id, aziendaId: req.identita.aziendaId },
      include: {
        categoria: true, fornitore: true, immagini: { orderBy: { ordine: "asc" } },
        varianti: { include: { giacenze: { include: { magazzino: true } }, lotti: { include: { giacenze: { include: { magazzino: true } } }, orderBy: { scadenza: "asc" } },
          seriali: { where: { stato: { in: ["disponibile", "riservato"] } }, orderBy: { numero: "asc" }, take: 200 } }, orderBy: { sku: "asc" } },
      },
    });
    if (!p) throw nonTrovato("Prodotto");
    const movimenti = await db.movimento.findMany({ where: { varianteId: { in: p.varianti.map((v) => v.id) } }, orderBy: { createdAt: "desc" }, take: 30 });
    const magazzini = new Map((await db.magazzino.findMany({ where: { aziendaId: req.identita.aziendaId } })).map((m) => [m.id, m.nome]));
    const venduto90 = await db.movimento.aggregate({ where: { varianteId: { in: p.varianti.map((v) => v.id) }, tipo: "vendita", createdAt: { gte: new Date(Date.now() - 90 * 86400_000) } }, _sum: { quantita: true } });
    return {
      ...p, ...disponibilita(p.varianti.flatMap((v) => v.giacenze)), venduto90giorni: venduto90._sum.quantita ?? 0,
      margine: p.prezzoVendita > 0 ? Math.round(((p.prezzoVendita - p.costoAcquisto) / p.prezzoVendita) * 1000) / 10 : 0,
      varianti: p.varianti.map((v) => ({ ...v, attributi: v.attributi ? JSON.parse(v.attributi) : null, ...disponibilita(v.giacenze),
        lotti: v.lotti.map((l) => ({ id: l.id, codice: l.codice, scadenza: l.scadenza, quantita: arrotonda3(l.giacenze.reduce((s, g) => s + g.quantita, 0)),
          perMagazzino: l.giacenze.filter((g) => g.quantita > 0).map((g) => ({ magazzino: g.magazzino.nome, quantita: g.quantita })) })) })),
      movimenti: movimenti.map((m) => ({ ...m, magazzino: magazzini.get(m.magazzinoId), magazzinoDest: m.magazzinoDestId ? magazzini.get(m.magazzinoDestId) : null,
        sku: p.varianti.find((v) => v.id === m.varianteId)?.sku })),
    };
  });

  app.post("/api/prodotti", { preHandler: richiede("catalogo") }, async (req, reply) => {
    const d = prodottoSchema.parse(req.body);
    await verificaRiferimenti(req.identita.aziendaId, d);
    const aziendaId = req.identita.aziendaId;
    const varianti = d.varianti?.length ? d.varianti : [{ sku: d.sku, nome: "Standard", barcode: d.barcode, prezzo: null, attributi: null }];
    for (const v of varianti) if (v.barcode && !barcodeValido(v.barcode)) throw nonValido(`Barcode della variante ${v.sku}: cifra di controllo non valida`);
    const { varianti: _v, ...dati } = d;
    const p = await db.prodotto.create({ data: {
      ...dati, aziendaId,
      varianti: { create: varianti.map((v) => ({ aziendaId, sku: v.sku, nome: v.nome, barcode: v.barcode ?? null, prezzo: v.prezzo ?? null,
        attributi: v.attributi ? JSON.stringify(v.attributi) : null })) },
    } });
    await audit(req, "prodotto_creato", "prodotto", p.id, p.sku);
    return reply.status(201).send(p);
  });

  app.patch("/api/prodotti/:id", { preHandler: richiede("catalogo") }, async (req) => {
    const { id } = req.params as { id: string };
    const d = prodottoSchema.omit({ varianti: true }).partial().parse(req.body);
    const p = await db.prodotto.findFirst({ where: { id, aziendaId: req.identita.aziendaId }, include: { varianti: true } });
    if (!p) throw nonTrovato("Prodotto");
    await verificaRiferimenti(req.identita.aziendaId, { ...p, ...d });
    if ((d.gestioneLotti !== undefined && d.gestioneLotti !== p.gestioneLotti) || (d.gestioneSeriali !== undefined && d.gestioneSeriali !== p.gestioneSeriali)) {
      const mov = await db.movimento.count({ where: { varianteId: { in: p.varianti.map((v) => v.id) } } });
      if (mov) throw conflitto("Lotti e seriali non si possono cambiare dopo i primi movimenti di magazzino");
    }
    const agg = await db.prodotto.update({ where: { id }, data: d });
    // l'unica variante "Standard" segue SKU e barcode del prodotto
    if (p.varianti.length === 1 && p.varianti[0].nome === "Standard" && (d.sku || d.barcode !== undefined))
      await db.variante.update({ where: { id: p.varianti[0].id }, data: { sku: d.sku ?? p.sku, barcode: d.barcode ?? p.barcode } });
    await audit(req, "prodotto_modificato", "prodotto", id);
    return agg;
  });

  app.delete("/api/prodotti/:id", { preHandler: richiede("catalogo") }, async (req) => {
    const { id } = req.params as { id: string };
    const p = await db.prodotto.findFirst({ where: { id, aziendaId: req.identita.aziendaId }, include: { varianti: true } });
    if (!p) throw nonTrovato("Prodotto");
    const ids = p.varianti.map((v) => v.id);
    const usato = await db.movimento.count({ where: { varianteId: { in: ids } } }) + await db.rigaOrdine.count({ where: { varianteId: { in: ids } } })
      + await db.rigaAcquisto.count({ where: { varianteId: { in: ids } } });
    if (usato) {
      await db.prodotto.update({ where: { id }, data: { attivo: false } });
      await audit(req, "prodotto_disattivato", "prodotto", id);
      return { eliminato: false, disattivato: true, messaggio: "Il prodotto ha uno storico: è stato disattivato invece di essere eliminato." };
    }
    await db.prodotto.delete({ where: { id } });
    await audit(req, "prodotto_eliminato", "prodotto", id, p.sku);
    return { eliminato: true };
  });

  // ── Varianti ─────────────────────────────────────────────────────────────
  app.post("/api/prodotti/:id/varianti", { preHandler: richiede("catalogo") }, async (req, reply) => {
    const { id } = req.params as { id: string };
    const d = varianteSchema.parse(req.body);
    const p = await db.prodotto.findFirst({ where: { id, aziendaId: req.identita.aziendaId }, include: { varianti: true } });
    if (!p) throw nonTrovato("Prodotto");
    if (d.barcode && !barcodeValido(d.barcode)) throw nonValido("Barcode: cifra di controllo non valida");
    // la variante "Standard" senza movimenti viene sostituita dalla prima variante reale
    const standard = p.varianti.length === 1 && p.varianti[0].nome === "Standard" ? p.varianti[0] : null;
    if (standard && !(await db.movimento.count({ where: { varianteId: standard.id } })) && !(await db.rigaOrdine.count({ where: { varianteId: standard.id } })))
      await db.variante.delete({ where: { id: standard.id } });
    const v = await db.variante.create({ data: { aziendaId: req.identita.aziendaId, prodottoId: id, sku: d.sku, nome: d.nome, barcode: d.barcode ?? null,
      prezzo: d.prezzo ?? null, attributi: d.attributi ? JSON.stringify(d.attributi) : null } });
    return reply.status(201).send(v);
  });

  app.patch("/api/varianti/:id", { preHandler: richiede("catalogo") }, async (req) => {
    const { id } = req.params as { id: string };
    const d = varianteSchema.partial().extend({ attivo: z.boolean().optional() }).parse(req.body);
    const v = await db.variante.findFirst({ where: { id, aziendaId: req.identita.aziendaId } });
    if (!v) throw nonTrovato("Variante");
    if (d.barcode && !barcodeValido(d.barcode)) throw nonValido("Barcode: cifra di controllo non valida");
    return db.variante.update({ where: { id }, data: { ...d, attributi: d.attributi === undefined ? undefined : d.attributi ? JSON.stringify(d.attributi) : null } });
  });

  // ── Ricerca articoli (righe d'ordine, scansione barcode) ────────────────
  app.get("/api/articoli", { preHandler: leggibile("catalogo") }, async (req) => {
    const q = z.object({ q: z.string().default(""), magazzinoId: z.string().optional(), varianteId: z.string().optional() }).parse(req.query);
    const v = await db.variante.findMany({
      where: { aziendaId: req.identita.aziendaId, attivo: true, prodotto: { attivo: true }, ...(q.varianteId ? { id: q.varianteId } : {}),
        ...(q.q ? { OR: [{ sku: { contains: q.q } }, { barcode: q.q }, { nome: { contains: q.q } }, { prodotto: { nome: { contains: q.q } } }, { prodotto: { barcode: q.q } }] } : {}) },
      include: { prodotto: true, giacenze: true }, take: 25, orderBy: { sku: "asc" },
    });
    return v.map((x) => {
      const g = q.magazzinoId ? x.giacenze.filter((y) => y.magazzinoId === q.magazzinoId) : x.giacenze;
      return {
        varianteId: x.id, prodottoId: x.prodottoId, sku: x.sku, nome: x.nome === "Standard" ? x.prodotto.nome : `${x.prodotto.nome} - ${x.nome}`,
        barcode: x.barcode ?? x.prodotto.barcode, prezzo: x.prezzo ?? x.prodotto.prezzoVendita, costo: x.prodotto.costoAcquisto,
        aliquotaIva: x.prodotto.aliquotaIva, unita: x.prodotto.unita, gestioneLotti: x.prodotto.gestioneLotti, gestioneSeriali: x.prodotto.gestioneSeriali,
        ...disponibilita(g),
      };
    });
  });

  app.get("/api/barcode/:codice", { preHandler: leggibile("catalogo") }, async (req) => {
    const { codice } = req.params as { codice: string };
    const v = await db.variante.findFirst({
      where: { aziendaId: req.identita.aziendaId, OR: [{ barcode: codice }, { sku: codice }, { prodotto: { barcode: codice } }] },
      include: { prodotto: true },
    });
    if (!v) throw nonTrovato("Articolo con questo codice");
    return { varianteId: v.id, prodottoId: v.prodottoId, sku: v.sku, nome: v.nome === "Standard" ? v.prodotto.nome : `${v.prodotto.nome} - ${v.nome}`,
      gestioneLotti: v.prodotto.gestioneLotti, gestioneSeriali: v.prodotto.gestioneSeriali, unita: v.prodotto.unita };
  });

  app.get("/api/varianti/:id/etichetta.png", async (req, reply) => {
    const v = await db.variante.findFirst({ where: { id: (req.params as { id: string }).id, aziendaId: req.identita.aziendaId }, include: { prodotto: true } });
    if (!v) throw nonTrovato("Variante");
    const codice = v.barcode || v.prodotto.barcode || v.sku;
    const ean = /^\d{13}$/.test(codice) ? "ean13" : /^\d{8}$/.test(codice) ? "ean8" : "code128";
    const png = await bwipjs.toBuffer({ bcid: ean, text: codice, scale: 3, height: 12, includetext: true, textxalign: "center" });
    return reply.type("image/png").send(png);
  });

  // ── Immagini ─────────────────────────────────────────────────────────────
  app.post("/api/prodotti/:id/immagini", { preHandler: richiede("catalogo") }, async (req, reply) => {
    const { id } = req.params as { id: string };
    const p = await db.prodotto.findFirst({ where: { id, aziendaId: req.identita.aziendaId }, include: { immagini: true } });
    if (!p) throw nonTrovato("Prodotto");
    if (p.immagini.length >= 8) throw conflitto("Massimo 8 immagini per prodotto");
    const file = await req.file({ limits: { fileSize: config.maxUploadMb * 1024 * 1024 } });
    if (!file) throw nonValido("Nessun file inviato");
    const buf = await file.toBuffer();
    // controllo del contenuto reale (firma del file), non solo dell'estensione
    const tipo = buf.subarray(0, 3).equals(Buffer.from([0xff, 0xd8, 0xff])) ? "jpg"
      : buf.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])) ? "png"
      : buf.subarray(0, 4).toString() === "RIFF" && buf.subarray(8, 12).toString() === "WEBP" ? "webp" : null;
    if (!tipo) throw nonValido("Formato non supportato: carica un'immagine JPEG, PNG o WEBP");
    mkdirSync(config.cartellaFile, { recursive: true });
    const nome = `${tokenCasuale(16)}.${tipo}`;
    writeFileSync(join(config.cartellaFile, nome), buf);
    const img = await db.prodottoImmagine.create({ data: { prodottoId: id, file: nome, ordine: p.immagini.length } });
    return reply.status(201).send(img);
  });

  app.delete("/api/immagini/:id", { preHandler: richiede("catalogo") }, async (req, reply) => {
    const img = await db.prodottoImmagine.findFirst({ where: { id: (req.params as { id: string }).id, prodotto: { aziendaId: req.identita.aziendaId } } });
    if (!img) throw nonTrovato("Immagine");
    await db.prodottoImmagine.delete({ where: { id: img.id } });
    const percorso = join(config.cartellaFile, img.file);
    if (existsSync(percorso)) unlinkSync(percorso);
    return reply.status(204).send();
  });

  // ── Esportazione CSV ─────────────────────────────────────────────────────
  app.get("/api/prodotti-export.csv", { preHandler: leggibile("catalogo") }, async (req, reply) => {
    const v = await db.variante.findMany({ where: { aziendaId: req.identita.aziendaId }, include: { prodotto: { include: { categoria: true } }, giacenze: true }, orderBy: { sku: "asc" } });
    const cella = (x: unknown) => `"${String(x ?? "").replace(/"/g, '""')}"`;
    const righe = [["SKU", "Prodotto", "Variante", "Categoria", "Barcode", "Prezzo", "Costo", "IVA", "Giacenza", "Disponibile"].map(cella).join(";")];
    for (const x of v) {
      const d = disponibilita(x.giacenze);
      righe.push([x.sku, x.prodotto.nome, x.nome, x.prodotto.categoria?.nome, x.barcode ?? x.prodotto.barcode,
        ((x.prezzo ?? x.prodotto.prezzoVendita) / 100).toFixed(2).replace(".", ","), (x.prodotto.costoAcquisto / 100).toFixed(2).replace(".", ","),
        x.prodotto.aliquotaIva, d.giacenza, d.disponibile].map(cella).join(";"));
    }
    return reply.type("text/csv; charset=utf-8").header("Content-Disposition", 'attachment; filename="nexora-prodotti.csv"').send("﻿" + righe.join("\r\n"));
  });
}

/** File statici caricati (immagini prodotto): nome casuale non indovinabile. */
export async function rotteFile(app: FastifyInstance) {
  app.get("/api/file/:nome", async (req, reply) => {
    const { nome } = req.params as { nome: string };
    if (!/^[A-Za-z0-9_-]+\.(jpg|png|webp)$/.test(nome)) throw nonTrovato("File");
    const percorso = join(config.cartellaFile, nome);
    if (!existsSync(percorso)) throw nonTrovato("File");
    const tipi: Record<string, string> = { jpg: "image/jpeg", png: "image/png", webp: "image/webp" };
    return reply.type(tipi[nome.split(".").pop()!]).header("Cache-Control", "public, max-age=86400").header("X-Content-Type-Options", "nosniff").send(readFileSync(percorso));
  });
}
