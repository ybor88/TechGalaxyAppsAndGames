// Dashboard KPI, analytics e reporting, motore AI e alert. © Roberto Di Flumeri
import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { db } from "../lib/db.js";
import { nonTrovato } from "../lib/errori.js";
import { arrotonda3, imponibileRiga } from "../lib/importi.js";
import { autenticato, richiede } from "../lib/sicurezza.js";
import { aggiornaAlert, analisiClienti, generaRiordini, prevedi, serieGiornaliera, suggerimentiRiordino } from "../services/ai.js";

const STATI_VENDUTO = ["confermato", "in_preparazione", "spedito", "consegnato"];
const inizioMese = (d: Date, delta = 0) => new Date(d.getFullYear(), d.getMonth() + delta, 1);

async function fatturato(aziendaId: string, dal: Date, al: Date) {
  const doc = await db.documento.groupBy({ by: ["tipo"], where: { aziendaId, tipo: { in: ["fattura", "nota_credito"] }, stato: { not: "bozza" }, data: { gte: dal, lt: al } }, _sum: { imponibile: true } });
  const f = doc.find((d) => d.tipo === "fattura")?._sum.imponibile ?? 0, nc = doc.find((d) => d.tipo === "nota_credito")?._sum.imponibile ?? 0;
  return f - nc;
}

function chiavePeriodo(d: Date, raggruppa: string) {
  if (raggruppa === "giorno") return d.toISOString().slice(0, 10);
  if (raggruppa === "mese") return d.toISOString().slice(0, 7);
  const lun = new Date(d); lun.setUTCDate(d.getUTCDate() - ((d.getUTCDay() + 6) % 7));
  return lun.toISOString().slice(0, 10);
}

export default async function rotteAnalisi(app: FastifyInstance) {
  app.addHook("preHandler", autenticato);

  app.get("/api/dashboard", async (req) => {
    const az = req.identita.aziendaId;
    const ora = new Date();
    const [meseCorr, mesePrec, anno] = await Promise.all([
      fatturato(az, inizioMese(ora), inizioMese(ora, 1)), fatturato(az, inizioMese(ora, -1), inizioMese(ora)),
      fatturato(az, new Date(ora.getFullYear(), 0, 1), new Date(ora.getFullYear() + 1, 0, 1)),
    ]);
    // stesso periodo del mese precedente, per un confronto onesto a metà mese
    const giorno = ora.getDate();
    const mesePrecParziale = await fatturato(az, inizioMese(ora, -1), new Date(ora.getFullYear(), ora.getMonth() - 1, giorno + 1));
    const aperti = await db.ordineVendita.groupBy({ by: ["stato"], where: { aziendaId: az, stato: { in: ["bozza", "confermato", "in_preparazione"] } }, _count: true, _sum: { totale: true } });
    const righe30 = await db.rigaOrdine.findMany({ where: { ordine: { aziendaId: az, stato: { in: STATI_VENDUTO }, data: { gte: new Date(ora.getTime() - 30 * 86400_000) } } } });
    const top = new Map<string, { descrizione: string; varianteId: string; quantita: number; ricavi: number }>();
    for (const r of righe30) {
      const t = top.get(r.varianteId) || { descrizione: r.descrizione, varianteId: r.varianteId, quantita: 0, ricavi: 0 };
      t.quantita += r.quantita; t.ricavi += imponibileRiga(r);
      top.set(r.varianteId, t);
    }
    const giacenze = await db.giacenza.findMany({ where: { magazzino: { aziendaId: az } }, include: { variante: { include: { prodotto: true } } } });
    const valoreMagazzino = Math.round(giacenze.reduce((s, g) => s + Math.max(0, g.quantita) * g.variante.prodotto.costoAcquisto, 0));
    const scadenzeAperte = await db.scadenza.findMany({ where: { pagata: false, documento: { aziendaId: az, tipo: "fattura", stato: "emessa" } } });
    const tra30 = new Date(ora.getTime() + 30 * 86400_000);
    const debiti = await db.ordineAcquisto.aggregate({ where: { aziendaId: az, stato: { in: ["inviato", "parziale"] } }, _sum: { totale: true }, _count: true });
    // andamento degli ultimi 12 mesi
    const mesi = [];
    for (let i = 11; i >= 0; i--) {
      const da = inizioMese(ora, -i), a = inizioMese(ora, -i + 1);
      const ord = await db.ordineVendita.aggregate({ where: { aziendaId: az, stato: { in: STATI_VENDUTO }, data: { gte: da, lt: a } }, _sum: { imponibile: true }, _count: true });
      mesi.push({ mese: da.toISOString().slice(0, 7), fatturato: await fatturato(az, da, a), ordinato: ord._sum.imponibile ?? 0, ordini: ord._count });
    }
    const previsione = prevedi(await serieGiornaliera(az, { misura: "fatturato", giorni: 182 }), 30);
    const alert = await db.alert.findMany({ where: { aziendaId: az, risolto: null }, orderBy: [{ gravita: "asc" }, { createdAt: "desc" }], take: 6 });
    const sottoScorta = (await db.variante.findMany({ where: { aziendaId: az, attivo: true, prodotto: { attivo: true, scortaMinima: { gt: 0 } } }, include: { prodotto: true, giacenze: true } }))
      .filter((v) => v.giacenze.reduce((s, g) => s + g.quantita - g.riservata, 0) < v.prodotto.scortaMinima).length;
    return {
      fatturato: { meseCorrente: meseCorr, mesePrecedente: mesePrec, mesePrecedenteStessoPeriodo: mesePrecParziale, annoCorrente: anno,
        variazione: mesePrecParziale ? Math.round(((meseCorr - mesePrecParziale) / mesePrecParziale) * 1000) / 10 : null },
      ordiniAperti: { numero: aperti.filter((a) => a.stato !== "bozza").reduce((s, a) => s + a._count, 0), bozze: aperti.find((a) => a.stato === "bozza")?._count ?? 0,
        valore: aperti.filter((a) => a.stato !== "bozza").reduce((s, a) => s + (a._sum.totale ?? 0), 0),
        perStato: Object.fromEntries(aperti.map((a) => [a.stato, a._count])) },
      prodottiPiuVenduti: [...top.values()].sort((a, b) => b.ricavi - a.ricavi).slice(0, 6),
      valoreMagazzino, sottoScorta,
      scadenze: {
        creditiAperti: scadenzeAperte.reduce((s, x) => s + x.importo, 0),
        scaduto: scadenzeAperte.filter((x) => x.dataScadenza < ora).reduce((s, x) => s + x.importo, 0),
        numeroScadute: scadenzeAperte.filter((x) => x.dataScadenza < ora).length,
        prossimi30: scadenzeAperte.filter((x) => x.dataScadenza >= ora && x.dataScadenza <= tra30).reduce((s, x) => s + x.importo, 0),
        acquistiDaRicevere: debiti._sum.totale ?? 0, ordiniFornitoreAperti: debiti._count,
      },
      andamento: mesi,
      previsione30: { totale: Math.round(previsione.totalePrevisto), spiegazione: previsione.spiegazione, accuratezza: previsione.accuratezza.descrizione },
      alert,
    };
  });

  app.get("/api/analytics", async (req) => {
    const ora = new Date();
    const q = z.object({ dal: z.coerce.date().default(inizioMese(ora, -11)), al: z.coerce.date().default(ora), raggruppa: z.enum(["giorno", "settimana", "mese"]).default("mese") }).parse(req.query);
    const az = req.identita.aziendaId;
    const al = new Date(q.al.getTime() + 86400_000);
    const ordini = await db.ordineVendita.findMany({
      where: { aziendaId: az, stato: { in: STATI_VENDUTO }, data: { gte: q.dal, lt: al } },
      include: { righe: true, cliente: { select: { ragioneSociale: true } } },
    });
    const varianti = new Map((await db.variante.findMany({ where: { aziendaId: az }, include: { prodotto: { include: { categoria: true } } } })).map((v) => [v.id, v]));
    const serie = new Map<string, { periodo: string; ricavi: number; costo: number; ordini: number }>();
    const categorie = new Map<string, { categoria: string; ricavi: number; costo: number }>();
    const canali = new Map<string, { canale: string; ricavi: number; ordini: number }>();
    const clienti = new Map<string, { cliente: string; ricavi: number; ordini: number }>();
    const prodotti = new Map<string, { sku: string; nome: string; ricavi: number; costo: number; quantita: number }>();
    for (const o of ordini) {
      const k = chiavePeriodo(o.data, q.raggruppa);
      const s = serie.get(k) || { periodo: k, ricavi: 0, costo: 0, ordini: 0 };
      s.ordini++;
      const can = canali.get(o.canale) || { canale: o.canale, ricavi: 0, ordini: 0 };
      can.ordini++;
      const cl = clienti.get(o.clienteId) || { cliente: o.cliente.ragioneSociale, ricavi: 0, ordini: 0 };
      cl.ordini++;
      for (const r of o.righe) {
        const ricavo = imponibileRiga(r), costo = Math.round(r.costoUnitario * r.quantita);
        s.ricavi += ricavo; s.costo += costo; can.ricavi += ricavo; cl.ricavi += ricavo;
        const v = varianti.get(r.varianteId);
        const nomeCat = v?.prodotto.categoria?.nome ?? "Senza categoria";
        const c = categorie.get(nomeCat) || { categoria: nomeCat, ricavi: 0, costo: 0 };
        c.ricavi += ricavo; c.costo += costo; categorie.set(nomeCat, c);
        const p = prodotti.get(r.varianteId) || { sku: v?.sku ?? "?", nome: r.descrizione, ricavi: 0, costo: 0, quantita: 0 };
        p.ricavi += ricavo; p.costo += costo; p.quantita += r.quantita; prodotti.set(r.varianteId, p);
      }
      serie.set(k, s); canali.set(o.canale, can); clienti.set(o.clienteId, cl);
    }
    // analisi ABC: A = 80% dei ricavi, B = fino al 95%, C = il resto
    const ordinati = [...prodotti.values()].sort((a, b) => b.ricavi - a.ricavi);
    const totaleRicavi = ordinati.reduce((s, p) => s + p.ricavi, 0);
    let cumulato = 0;
    const abc = ordinati.map((p) => {
      // la classe dipende dalla quota cumulata raggiunta prima di questo prodotto
      const quotaPrima = totaleRicavi ? cumulato / totaleRicavi : 0;
      cumulato += p.ricavi;
      return { ...p, quantita: arrotonda3(p.quantita), margine: p.ricavi - p.costo, classe: quotaPrima < 0.8 ? "A" : quotaPrima < 0.95 ? "B" : "C" };
    });
    // rotazione: costo del venduto nel periodo / valore attuale di magazzino
    const giacenze = await db.giacenza.findMany({ where: { magazzino: { aziendaId: az } }, include: { variante: { include: { prodotto: true } } } });
    const valore = giacenze.reduce((s, g) => s + Math.max(0, g.quantita) * g.variante.prodotto.costoAcquisto, 0);
    const costoVenduto = [...serie.values()].reduce((s, x) => s + x.costo, 0);
    const giorniPeriodo = Math.max(1, (al.getTime() - q.dal.getTime()) / 86400_000);
    const rotazioneAnnua = valore ? Math.round((costoVenduto / valore) * (365 / giorniPeriodo) * 10) / 10 : null;
    const ricavi = [...serie.values()].reduce((s, x) => s + x.ricavi, 0);
    return {
      periodo: { dal: q.dal, al: q.al, raggruppa: q.raggruppa },
      kpi: { ricavi, costo: costoVenduto, margine: ricavi - costoVenduto, marginePercentuale: ricavi ? Math.round(((ricavi - costoVenduto) / ricavi) * 1000) / 10 : 0,
        ordini: ordini.length, scontrinoMedio: ordini.length ? Math.round(ricavi / ordini.length) : 0, clientiAttivi: clienti.size, rotazioneAnnua,
        giorniCopertura: rotazioneAnnua ? Math.round(365 / rotazioneAnnua) : null },
      serie: [...serie.values()].sort((a, b) => a.periodo.localeCompare(b.periodo)).map((s) => ({ ...s, margine: s.ricavi - s.costo })),
      perCategoria: [...categorie.values()].sort((a, b) => b.ricavi - a.ricavi).map((c) => ({ ...c, margine: c.ricavi - c.costo })),
      perCanale: [...canali.values()].sort((a, b) => b.ricavi - a.ricavi),
      topClienti: [...clienti.values()].sort((a, b) => b.ricavi - a.ricavi).slice(0, 10),
      abc,
      riepilogoAbc: ["A", "B", "C"].map((c) => ({ classe: c, prodotti: abc.filter((p) => p.classe === c).length, ricavi: abc.filter((p) => p.classe === c).reduce((s, p) => s + p.ricavi, 0) })),
    };
  });

  // ── Motore AI ────────────────────────────────────────────────────────────
  app.get("/api/ai/previsione", async (req) => {
    const q = z.object({ varianteId: z.string().optional(), giorni: z.coerce.number().int().min(7).max(90).default(30), storico: z.coerce.number().int().min(28).max(730).default(182) }).parse(req.query);
    if (q.varianteId && !(await db.variante.findFirst({ where: { id: q.varianteId, aziendaId: req.identita.aziendaId } }))) throw nonTrovato("Articolo");
    const serie = await serieGiornaliera(req.identita.aziendaId, { varianteId: q.varianteId, misura: q.varianteId ? "quantita" : "fatturato", giorni: q.storico });
    return { misura: q.varianteId ? "quantita" : "fatturato", ...prevedi(serie, q.giorni) };
  });

  app.get("/api/ai/riordino", async (req) => suggerimentiRiordino(req.identita.aziendaId));

  app.post("/api/ai/riordino/genera", { preHandler: richiede("acquisti") }, async (req, reply) => {
    const d = z.object({ varianteIds: z.array(z.string()).optional() }).parse(req.body ?? {});
    return reply.status(201).send(await generaRiordini(req.identita.aziendaId, d.varianteIds));
  });

  app.get("/api/ai/clienti", async (req) => {
    const profili = await analisiClienti(req.identita.aziendaId);
    const segmenti = new Map<string, { segmento: string; clienti: number; valore: number }>();
    for (const p of profili) {
      const s = segmenti.get(p.segmento) || { segmento: p.segmento, clienti: 0, valore: 0 };
      s.clienti++; s.valore += p.valore; segmenti.set(p.segmento, s);
    }
    return { profili, segmenti: [...segmenti.values()].sort((a, b) => b.valore - a.valore) };
  });

  app.get("/api/alert", async (req) => {
    const q = z.object({ stato: z.enum(["aperti", "risolti", "tutti"]).default("aperti") }).parse(req.query);
    return db.alert.findMany({
      where: { aziendaId: req.identita.aziendaId, ...(q.stato === "aperti" ? { risolto: null } : q.stato === "risolti" ? { risolto: { not: null } } : {}) },
      orderBy: [{ risolto: "asc" }, { createdAt: "desc" }], take: 200,
    });
  });

  app.post("/api/alert/aggiorna", async (req) => aggiornaAlert(req.identita.aziendaId));

  app.post("/api/alert/:id/letto", async (req) => {
    const r = await db.alert.updateMany({ where: { id: (req.params as { id: string }).id, aziendaId: req.identita.aziendaId }, data: { letto: true } });
    if (!r.count) throw nonTrovato("Alert");
    return { letto: true };
  });
}
