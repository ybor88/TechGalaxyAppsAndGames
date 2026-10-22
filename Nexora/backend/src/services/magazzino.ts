// Movimenti di magazzino multi-sede con lotti (FEFO) e numeri di serie. Ogni funzione lavora dentro
// una transazione: giacenze, lotti, seriali e storico dei movimenti restano sempre coerenti. © Roberto Di Flumeri
import type { Tx } from "../lib/db.js";
import { conflitto, nonTrovato, nonValido } from "../lib/errori.js";
import { arrotonda3 } from "../lib/importi.js";

export type TipoMovimento = "carico" | "scarico" | "trasferimento" | "rettifica" | "vendita" | "acquisto" | "reso";
const IN_ENTRATA: TipoMovimento[] = ["carico", "acquisto", "reso"];

export interface DatiMovimento {
  aziendaId: string;
  tipo: TipoMovimento;
  varianteId: string;
  magazzinoId: string;
  magazzinoDestId?: string;
  quantita: number; // sempre positiva, tranne rettifica (differenza con segno)
  lotto?: { codice: string; scadenza?: Date | null };
  lottoId?: string;
  seriali?: string[];
  costoUnitario?: number;
  causale?: string;
  riferimentoTipo?: string;
  riferimentoId?: string;
  utenteId?: string | null;
  daRiservato?: boolean; // la vendita consuma merce già impegnata dall'ordine
}

export async function articolo(tx: Tx, aziendaId: string, varianteId: string) {
  const v = await tx.variante.findFirst({ where: { id: varianteId, aziendaId }, include: { prodotto: true } });
  if (!v) throw nonTrovato("Articolo");
  return v;
}

async function verificaMagazzino(tx: Tx, aziendaId: string, magazzinoId: string) {
  const m = await tx.magazzino.findFirst({ where: { id: magazzinoId, aziendaId } });
  if (!m) throw nonTrovato("Magazzino");
  return m;
}

async function giacenza(tx: Tx, magazzinoId: string, varianteId: string) {
  return tx.giacenza.upsert({
    where: { magazzinoId_varianteId: { magazzinoId, varianteId } },
    create: { magazzinoId, varianteId }, update: {},
  });
}

async function variaGiacenza(tx: Tx, magazzinoId: string, varianteId: string, delta: number, deltaRiservata = 0) {
  const g = await giacenza(tx, magazzinoId, varianteId);
  return tx.giacenza.update({
    where: { id: g.id },
    data: { quantita: arrotonda3(g.quantita + delta), riservata: Math.max(0, arrotonda3(g.riservata + deltaRiservata)) },
  });
}

async function variaLotto(tx: Tx, lottoId: string, magazzinoId: string, delta: number) {
  const lg = await tx.lottoGiacenza.upsert({
    where: { lottoId_magazzinoId: { lottoId, magazzinoId } }, create: { lottoId, magazzinoId }, update: {},
  });
  const nuova = arrotonda3(lg.quantita + delta);
  if (nuova < 0) throw conflitto("Quantità insufficiente nel lotto indicato");
  await tx.lottoGiacenza.update({ where: { id: lg.id }, data: { quantita: nuova } });
}

/** Lotti disponibili in ordine di scadenza (FEFO: first expired, first out). */
export async function allocaFefo(tx: Tx, varianteId: string, magazzinoId: string, quantita: number) {
  const lotti = await tx.lottoGiacenza.findMany({
    where: { magazzinoId, quantita: { gt: 0 }, lotto: { varianteId } },
    include: { lotto: true },
  });
  lotti.sort((a, b) => (a.lotto.scadenza?.getTime() ?? Infinity) - (b.lotto.scadenza?.getTime() ?? Infinity));
  const out: { lottoId: string; quantita: number }[] = [];
  let resto = quantita;
  for (const l of lotti) {
    if (resto <= 0) break;
    const q = Math.min(resto, l.quantita);
    out.push({ lottoId: l.lottoId, quantita: arrotonda3(q) });
    resto = arrotonda3(resto - q);
  }
  if (resto > 0) throw conflitto(`Lotti insufficienti: mancano ${resto} unità`);
  return out;
}

/** Registra un movimento e aggiorna giacenze, lotti e seriali. Restituisce i movimenti creati. */
export async function movimenta(tx: Tx, d: DatiMovimento) {
  const v = await articolo(tx, d.aziendaId, d.varianteId);
  await verificaMagazzino(tx, d.aziendaId, d.magazzinoId);
  const p = v.prodotto;
  if (d.tipo === "rettifica" ? d.quantita === 0 : d.quantita <= 0) throw nonValido("Quantità: deve essere maggiore di zero");
  if (p.gestioneSeriali && !Number.isInteger(d.quantita)) throw nonValido("Per gli articoli con numero di serie la quantità deve essere intera");
  if (d.tipo === "trasferimento") {
    if (!d.magazzinoDestId || d.magazzinoDestId === d.magazzinoId) throw nonValido("Indica un magazzino di destinazione diverso");
    await verificaMagazzino(tx, d.aziendaId, d.magazzinoDestId);
  }
  const entrata = IN_ENTRATA.includes(d.tipo) || (d.tipo === "rettifica" && d.quantita > 0);
  const q = Math.abs(d.quantita);

  // disponibilità in uscita: la merce impegnata dagli ordini non si può scaricare, tranne dalla vendita stessa
  if (!entrata) {
    const g = await giacenza(tx, d.magazzinoId, d.varianteId);
    const disponibile = d.daRiservato ? g.quantita : g.quantita - g.riservata;
    if (arrotonda3(disponibile - q) < 0)
      throw conflitto(`Giacenza insufficiente per ${v.sku}: disponibili ${arrotonda3(disponibile)} ${p.unita}, richiesti ${q}`);
  }

  // lotti
  let assegnazioni: { lottoId: string | null; quantita: number }[] = [{ lottoId: null, quantita: q }];
  if (p.gestioneLotti) {
    if (entrata) {
      let lottoId = d.lottoId;
      if (!lottoId) {
        if (!d.lotto?.codice) throw nonValido("Indica il lotto: l'articolo è gestito a lotti");
        const l = await tx.lotto.upsert({
          where: { varianteId_codice: { varianteId: v.id, codice: d.lotto.codice } },
          create: { varianteId: v.id, codice: d.lotto.codice, scadenza: d.lotto.scadenza ?? null },
          update: d.lotto.scadenza ? { scadenza: d.lotto.scadenza } : {},
        });
        lottoId = l.id;
      }
      assegnazioni = [{ lottoId, quantita: q }];
    } else {
      assegnazioni = d.lottoId ? [{ lottoId: d.lottoId, quantita: q }] : await allocaFefo(tx, v.id, d.magazzinoId, q);
    }
  }

  // seriali
  let seriali: string[] | undefined;
  if (p.gestioneSeriali) {
    if (entrata) {
      seriali = (d.seriali || []).map((s) => s.trim()).filter(Boolean);
      if (seriali.length !== q) throw nonValido(`Indica ${q} numeri di serie (ne hai indicati ${seriali.length})`);
      if (new Set(seriali).size !== seriali.length) throw nonValido("Numeri di serie duplicati");
      for (const numero of seriali) {
        const esiste = await tx.seriale.findUnique({ where: { varianteId_numero: { varianteId: v.id, numero } } });
        if (esiste && ["disponibile", "riservato"].includes(esiste.stato)) throw conflitto(`Il numero di serie ${numero} è già in magazzino`);
        await tx.seriale.upsert({
          where: { varianteId_numero: { varianteId: v.id, numero } },
          create: { varianteId: v.id, numero, magazzinoId: d.magazzinoId },
          update: { magazzinoId: d.magazzinoId, stato: "disponibile", ordineId: null },
        });
      }
    } else {
      let scelti = d.seriali?.map((s) => s.trim()).filter(Boolean);
      if (!scelti?.length) {
        const disp = await tx.seriale.findMany({
          where: { varianteId: v.id, magazzinoId: d.magazzinoId, stato: { in: d.daRiservato ? ["disponibile", "riservato"] : ["disponibile"] } },
          orderBy: { createdAt: "asc" }, take: q,
        });
        scelti = disp.map((s) => s.numero);
      }
      if (scelti.length !== q) throw conflitto(`Numeri di serie disponibili insufficienti per ${v.sku}`);
      for (const numero of scelti) {
        const s = await tx.seriale.findUnique({ where: { varianteId_numero: { varianteId: v.id, numero } } });
        if (!s || s.magazzinoId !== d.magazzinoId || !["disponibile", "riservato"].includes(s.stato))
          throw conflitto(`Il numero di serie ${numero} non è disponibile in questo magazzino`);
        await tx.seriale.update({
          where: { id: s.id },
          data: d.tipo === "trasferimento"
            ? { magazzinoId: d.magazzinoDestId }
            : { stato: d.tipo === "vendita" ? "venduto" : "scaricato", magazzinoId: null, ordineId: d.riferimentoTipo === "ordine" ? d.riferimentoId : s.ordineId },
        });
      }
      seriali = scelti;
    }
  }

  const creati = [];
  for (const a of assegnazioni) {
    const segno = entrata ? 1 : -1;
    await variaGiacenza(tx, d.magazzinoId, v.id, segno * a.quantita, d.daRiservato ? -a.quantita : 0);
    if (a.lottoId) await variaLotto(tx, a.lottoId, d.magazzinoId, segno * a.quantita);
    if (d.tipo === "trasferimento") {
      await variaGiacenza(tx, d.magazzinoDestId!, v.id, a.quantita);
      if (a.lottoId) await variaLotto(tx, a.lottoId, d.magazzinoDestId!, a.quantita);
    }
    creati.push(await tx.movimento.create({ data: {
      aziendaId: d.aziendaId, tipo: d.tipo, varianteId: v.id, magazzinoId: d.magazzinoId, magazzinoDestId: d.magazzinoDestId,
      quantita: d.tipo === "rettifica" ? d.quantita : a.quantita, lottoId: a.lottoId, seriali: seriali ? JSON.stringify(seriali) : null,
      costoUnitario: d.costoUnitario ?? p.costoAcquisto, causale: d.causale, riferimentoTipo: d.riferimentoTipo,
      riferimentoId: d.riferimentoId, utenteId: d.utenteId ?? null,
    } }));
  }
  return creati;
}

/** Impegna merce per un ordine confermato. */
export async function riserva(tx: Tx, aziendaId: string, magazzinoId: string, varianteId: string, quantita: number) {
  const v = await articolo(tx, aziendaId, varianteId);
  const g = await giacenza(tx, magazzinoId, varianteId);
  const disponibile = arrotonda3(g.quantita - g.riservata);
  if (disponibile < quantita)
    throw conflitto(`Disponibilità insufficiente per ${v.sku}: disponibili ${disponibile} ${v.prodotto.unita}, richiesti ${quantita}`);
  await variaGiacenza(tx, magazzinoId, varianteId, 0, quantita);
}

export async function rilascia(tx: Tx, magazzinoId: string, varianteId: string, quantita: number) {
  await variaGiacenza(tx, magazzinoId, varianteId, 0, -quantita);
}
