// Fatturazione: preventivi, DDT, fatture, note di credito, fattura elettronica SdI, scadenze e incassi. © Roberto Di Flumeri
import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { db } from "../lib/db.js";
import { conflitto, nonValido } from "../lib/errori.js";
import { NATURE_IVA } from "../lib/fiscale.js";
import { prossimoNumero } from "../lib/numeratore.js";
import { audit, autenticato, leggibile, richiede } from "../lib/sicurezza.js";
import { aggiornaRighe, caricaDocumento, creaDocumento, emetti, registraPagamento, TIPI_DOCUMENTO } from "../services/documenti.js";
import { generaXml, nomeFileSdi, verificaDatiSdi } from "../services/fatturapa.js";
import { linkPagamento } from "../services/integrazioni.js";
import { creaOrdine } from "../services/ordini.js";
import { pdfDocumento } from "../services/pdf.js";

const riga = z.object({
  varianteId: z.string().nullish(), descrizione: z.string().trim().min(1).max(1000), quantita: z.number().positive(), unita: z.string().max(10).default("pz"),
  prezzoUnitario: z.number().int().min(0), sconto: z.number().min(0).max(100).default(0),
  aliquotaIva: z.number().refine((v) => [22, 10, 5, 4, 0].includes(v), "aliquota non ammessa"),
  naturaIva: z.string().nullish().refine((v) => !v || NATURE_IVA[v], "natura IVA non valida"),
});

export default async function rotteDocumenti(app: FastifyInstance) {
  app.addHook("preHandler", autenticato);
  app.addHook("preHandler", leggibile("documenti"));

  app.get("/api/documenti", async (req) => {
    const q = z.object({ tipo: z.enum(TIPI_DOCUMENTO).optional(), stato: z.string().optional(), q: z.string().optional(), clienteId: z.string().optional(),
      anno: z.coerce.number().int().optional() }).parse(req.query);
    return db.documento.findMany({
      where: { aziendaId: req.identita.aziendaId, ...(q.tipo ? { tipo: q.tipo } : {}), ...(q.stato ? { stato: { in: q.stato.split(",") } } : {}),
        ...(q.clienteId ? { clienteId: q.clienteId } : {}), ...(q.anno ? { anno: q.anno } : {}),
        ...(q.q ? { OR: [{ numero: { contains: q.q } }, { cliente: { ragioneSociale: { contains: q.q } } }] } : {}) },
      include: { cliente: { select: { ragioneSociale: true } }, scadenze: true }, orderBy: [{ data: "desc" }, { progressivo: "desc" }], take: 500,
    });
  });

  app.get("/api/documenti/:id", async (req) => {
    const d = await caricaDocumento(db, req.identita.aziendaId, (req.params as { id: string }).id);
    const origine = d.documentoOrigineId ? await db.documento.findUnique({ where: { id: d.documentoOrigineId }, select: { id: true, tipo: true, numero: true, data: true } }) : null;
    const collegati = await db.documento.findMany({ where: { documentoOrigineId: d.id }, select: { id: true, tipo: true, numero: true, stato: true, totale: true } });
    const ordine = d.ordineId ? await db.ordineVendita.findUnique({ where: { id: d.ordineId }, select: { id: true, numero: true, stato: true } }) : null;
    const azienda = await db.azienda.findUniqueOrThrow({ where: { id: req.identita.aziendaId } });
    return { ...d, origine, collegati, ordine, controlliSdi: d.tipo === "fattura" || d.tipo === "nota_credito" ? verificaDatiSdi(azienda, d.cliente) : [] };
  });

  app.post("/api/documenti", { preHandler: richiede("documenti") }, async (req, reply) => {
    const d = z.object({ tipo: z.enum(TIPI_DOCUMENTO), clienteId: z.string(), righe: z.array(riga).min(1), note: z.string().max(1000).nullish(),
      validoFino: z.coerce.date().nullish(), causaleTrasporto: z.string().max(100).nullish(), colli: z.number().int().min(1).nullish(), pesoKg: z.number().positive().nullish(),
    }).parse(req.body);
    const doc = await db.$transaction((tx) => creaDocumento(tx, req.identita.aziendaId, {
      ...d, validoFino: d.tipo === "preventivo" ? d.validoFino ?? new Date(Date.now() + 30 * 86400_000) : null,
    }));
    await audit(req, `${d.tipo}_creato`, "documento", doc.id);
    return reply.status(201).send(doc);
  });

  app.patch("/api/documenti/:id", { preHandler: richiede("documenti") }, async (req) => {
    const d = z.object({ righe: z.array(riga).min(1), note: z.string().max(1000).nullish(), clienteId: z.string().optional() }).parse(req.body);
    return db.$transaction((tx) => aggiornaRighe(tx, req.identita.aziendaId, (req.params as { id: string }).id, d.righe, d));
  });

  app.delete("/api/documenti/:id", { preHandler: richiede("documenti") }, async (req, reply) => {
    const d = await caricaDocumento(db, req.identita.aziendaId, (req.params as { id: string }).id);
    if (d.stato !== "bozza") throw conflitto("Un documento emesso non si elimina: per una fattura emetti una nota di credito");
    await db.documento.delete({ where: { id: d.id } });
    return reply.status(204).send();
  });

  app.post("/api/documenti/:id/emetti", { preHandler: richiede("documenti") }, async (req) => {
    const o = z.object({ rate: z.number().int().min(1).max(12).optional(), resoInMagazzino: z.string().nullish() }).parse(req.body ?? {});
    const { id } = req.params as { id: string };
    const d = await db.$transaction((tx) => emetti(tx, req.identita.aziendaId, id, { ...o, utenteId: req.identita.utenteId }));
    await audit(req, `${d.tipo}_emesso`, "documento", id, d.numero ?? undefined);
    return d;
  });

  app.post("/api/documenti/:id/stato", { preHandler: richiede("documenti") }, async (req) => {
    const { stato } = z.object({ stato: z.enum(["accettato", "rifiutato", "annullata"]) }).parse(req.body);
    const d = await caricaDocumento(db, req.identita.aziendaId, (req.params as { id: string }).id);
    if (d.tipo === "preventivo" && ["accettato", "rifiutato"].includes(stato) && ["inviato", "bozza"].includes(d.stato)) {
      return db.documento.update({ where: { id: d.id }, data: { stato } });
    }
    throw conflitto("Cambio di stato non consentito per questo documento");
  });

  /** Preventivo accettato → ordine di vendita in bozza con le stesse righe. */
  app.post("/api/documenti/:id/ordine", { preHandler: richiede("ordini") }, async (req, reply) => {
    const d = await caricaDocumento(db, req.identita.aziendaId, (req.params as { id: string }).id);
    if (d.tipo !== "preventivo") throw conflitto("Solo un preventivo può diventare un ordine");
    if (await db.ordineVendita.findFirst({ where: { preventivoId: d.id, stato: { not: "annullato" } } })) throw conflitto("Da questo preventivo è già stato creato un ordine");
    const righe = d.righe.filter((r) => r.varianteId);
    if (!righe.length) throw nonValido("Il preventivo non contiene articoli di catalogo");
    const o = await db.$transaction(async (tx) => {
      const ordine = await creaOrdine(tx, req.identita.aziendaId, { clienteId: d.clienteId, preventivoId: d.id, note: `Da preventivo ${d.numero ?? ""}`.trim(),
        righe: righe.map((r) => ({ varianteId: r.varianteId!, quantita: r.quantita, prezzoUnitario: r.prezzoUnitario, sconto: r.sconto, descrizione: r.descrizione })) });
      await tx.documento.update({ where: { id: d.id }, data: { stato: "accettato" } });
      return ordine;
    });
    await audit(req, "preventivo_convertito", "ordine", o.id, o.numero);
    return reply.status(201).send(o);
  });

  /** Nota di credito (in bozza) da una fattura emessa: intera o per le righe indicate. */
  app.post("/api/documenti/:id/nota-credito", { preHandler: richiede("documenti") }, async (req, reply) => {
    const q = z.object({ righe: z.array(z.object({ indice: z.number().int().min(0), quantita: z.number().positive() })).optional(), note: z.string().max(500).optional() }).parse(req.body ?? {});
    const f = await caricaDocumento(db, req.identita.aziendaId, (req.params as { id: string }).id);
    if (f.tipo !== "fattura" || f.stato === "bozza") throw conflitto("La nota di credito si emette solo da una fattura emessa");
    const righe = q.righe?.length
      ? q.righe.map((x) => {
        const r = f.righe[x.indice];
        if (!r) throw nonValido("Riga della fattura non trovata");
        if (x.quantita > r.quantita) throw nonValido(`«${r.descrizione}»: puoi stornare al massimo ${r.quantita}`);
        return { ...r, quantita: x.quantita };
      })
      : f.righe;
    const nc = await db.$transaction((tx) => creaDocumento(tx, req.identita.aziendaId, {
      tipo: "nota_credito", clienteId: f.clienteId, documentoOrigineId: f.id, ordineId: f.ordineId, note: q.note ?? `Storno della fattura ${f.numero}`,
      righe: righe.map((r) => ({ varianteId: r.varianteId, descrizione: r.descrizione, quantita: r.quantita, unita: r.unita, prezzoUnitario: r.prezzoUnitario,
        sconto: r.sconto, aliquotaIva: r.aliquotaIva, naturaIva: r.naturaIva })),
    }));
    await audit(req, "nota_credito_creata", "documento", nc.id, f.numero ?? undefined);
    return reply.status(201).send(nc);
  });

  app.get("/api/documenti/:id/pdf", async (req, reply) => {
    const d = await caricaDocumento(db, req.identita.aziendaId, (req.params as { id: string }).id);
    const azienda = await db.azienda.findUniqueOrThrow({ where: { id: req.identita.aziendaId } });
    const nome = `${d.tipo}-${d.numero ?? "bozza"}.pdf`;
    return reply.type("application/pdf").header("Content-Disposition", `inline; filename="${nome}"`).send(await pdfDocumento(azienda, d.cliente, d));
  });

  /** Genera il file XML FatturaPA da caricare sullo SdI (PEC o canale accreditato). */
  app.get("/api/documenti/:id/xml", async (req, reply) => {
    const d = await caricaDocumento(db, req.identita.aziendaId, (req.params as { id: string }).id);
    if (d.tipo !== "fattura" && d.tipo !== "nota_credito") throw conflitto("La fattura elettronica riguarda solo fatture e note di credito");
    const azienda = await db.azienda.findUniqueOrThrow({ where: { id: req.identita.aziendaId } });
    const origine = d.documentoOrigineId ? await db.documento.findUnique({ where: { id: d.documentoOrigineId } }) : null;
    const xml = generaXml(azienda, d.cliente, d, origine);
    // il nome del file usa un progressivo unico per mittente (contatore dedicato, non legato all'anno)
    let nome = d.nomeFileSdi;
    if (!nome) {
      nome = await db.$transaction(async (tx) => nomeFileSdi(azienda, (await prossimoNumero(tx, req.identita.aziendaId, "sdi", 0)).progressivo));
      await db.documento.update({ where: { id: d.id }, data: { statoSdi: "generato", nomeFileSdi: nome } });
    }
    return reply.type("application/xml; charset=utf-8").header("Content-Disposition", `attachment; filename="${nome}"`).send(xml);
  });

  /** Esito dello SdI registrato manualmente (ricevuta di consegna o di scarto). */
  app.post("/api/documenti/:id/sdi", { preHandler: richiede("documenti") }, async (req) => {
    const { stato } = z.object({ stato: z.enum(["inviato", "consegnato", "scartato"]) }).parse(req.body);
    const d = await caricaDocumento(db, req.identita.aziendaId, (req.params as { id: string }).id);
    if (!d.statoSdi || d.statoSdi === "da_generare") throw conflitto("Genera prima il file XML della fattura elettronica");
    const agg = await db.documento.update({ where: { id: d.id }, data: { statoSdi: stato } });
    await audit(req, `sdi_${stato}`, "documento", d.id, d.numero ?? undefined);
    return agg;
  });

  // ── Scadenze e incassi ───────────────────────────────────────────────────
  app.get("/api/scadenze", async (req) => {
    const q = z.object({ stato: z.enum(["aperte", "scadute", "pagate", "tutte"]).default("aperte") }).parse(req.query);
    const ora = new Date();
    const s = await db.scadenza.findMany({
      where: { documento: { aziendaId: req.identita.aziendaId, tipo: "fattura", stato: { not: "bozza" } },
        ...(q.stato === "aperte" ? { pagata: false } : q.stato === "scadute" ? { pagata: false, dataScadenza: { lt: ora } } : q.stato === "pagate" ? { pagata: true } : {}) },
      include: { documento: { include: { cliente: { select: { ragioneSociale: true, email: true } } } } }, orderBy: { dataScadenza: "asc" },
    });
    return s.map((x) => ({ ...x, giorniRitardo: !x.pagata && x.dataScadenza < ora ? Math.floor((ora.getTime() - x.dataScadenza.getTime()) / 86400_000) : 0 }));
  });

  app.post("/api/scadenze/:id/pagamento", { preHandler: richiede("pagamenti") }, async (req) => {
    const d = z.object({ metodo: z.enum(["bonifico", "carta", "contanti", "assegno", "stripe", "paypal"]), data: z.coerce.date().optional(), riferimento: z.string().max(100).nullish() }).parse(req.body);
    const { id } = req.params as { id: string };
    const doc = await db.$transaction((tx) => registraPagamento(tx, req.identita.aziendaId, id, d));
    await audit(req, "incasso_registrato", "scadenza", id, d.metodo);
    return doc;
  });

  app.post("/api/scadenze/:id/link", { preHandler: richiede("pagamenti") }, async (req) => {
    const { provider } = z.object({ provider: z.enum(["stripe", "paypal"]) }).parse(req.body);
    const r = await linkPagamento(req.identita.aziendaId, (req.params as { id: string }).id, provider);
    await audit(req, "link_pagamento", "scadenza", (req.params as { id: string }).id, provider);
    return r;
  });
}
