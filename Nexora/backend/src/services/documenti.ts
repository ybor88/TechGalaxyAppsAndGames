// Ciclo dei documenti: preventivi, DDT, fatture, note di credito, scadenze e pagamenti. © Roberto Di Flumeri
import type { Tx } from "../lib/db.js";
import { conflitto, nonTrovato, nonValido } from "../lib/errori.js";
import { totali } from "../lib/importi.js";
import { prossimoNumero } from "../lib/numeratore.js";
import { movimenta } from "./magazzino.js";

export const TIPI_DOCUMENTO = ["preventivo", "ddt", "fattura", "nota_credito"] as const;
export type TipoDocumento = (typeof TIPI_DOCUMENTO)[number];

export interface RigaInput {
  varianteId?: string | null;
  descrizione: string;
  quantita: number;
  unita?: string;
  prezzoUnitario: number;
  sconto?: number;
  aliquotaIva: number;
  naturaIva?: string | null;
}

export async function caricaDocumento(tx: Tx, aziendaId: string, id: string) {
  const d = await tx.documento.findFirst({
    where: { id, aziendaId },
    include: { righe: true, scadenze: { orderBy: { dataScadenza: "asc" } }, cliente: true },
  });
  if (!d) throw nonTrovato("Documento");
  return d;
}

export async function creaDocumento(tx: Tx, aziendaId: string, dati: {
  tipo: TipoDocumento; clienteId: string; righe: RigaInput[]; ordineId?: string | null; documentoOrigineId?: string | null;
  note?: string | null; validoFino?: Date | null; causaleTrasporto?: string | null; colli?: number | null; pesoKg?: number | null; data?: Date;
}) {
  const cliente = await tx.cliente.findFirst({ where: { id: dati.clienteId, aziendaId } });
  if (!cliente) throw nonTrovato("Cliente");
  if (!dati.righe.length) throw nonValido("Righe: serve almeno una riga");
  for (const r of dati.righe) {
    if (r.quantita <= 0) throw nonValido("Quantità: deve essere maggiore di zero");
    if (r.aliquotaIva === 0 && dati.tipo !== "ddt" && dati.tipo !== "preventivo" && !r.naturaIva)
      throw nonValido(`Riga «${r.descrizione}»: con IVA 0% indica la natura dell'operazione (es. N2.2)`);
  }
  const t = totali(dati.righe);
  const data = dati.data ?? new Date();
  return tx.documento.create({
    data: {
      aziendaId, tipo: dati.tipo, clienteId: cliente.id, anno: data.getFullYear(), data, ordineId: dati.ordineId ?? null,
      documentoOrigineId: dati.documentoOrigineId ?? null, note: dati.note ?? null, validoFino: dati.validoFino ?? null,
      causaleTrasporto: dati.causaleTrasporto ?? null, colli: dati.colli ?? null, pesoKg: dati.pesoKg ?? null,
      imponibile: t.imponibile, iva: t.iva, totale: t.totale,
      statoSdi: dati.tipo === "fattura" || dati.tipo === "nota_credito" ? "da_generare" : null,
      righe: { create: dati.righe.map((r) => ({
        varianteId: r.varianteId ?? null, descrizione: r.descrizione, quantita: r.quantita, unita: r.unita || "pz",
        prezzoUnitario: r.prezzoUnitario, sconto: r.sconto ?? 0, aliquotaIva: r.aliquotaIva, naturaIva: r.naturaIva ?? null,
      })) },
    },
  });
}

export async function aggiornaRighe(tx: Tx, aziendaId: string, id: string, righe: RigaInput[], extra: { note?: string | null; clienteId?: string } = {}) {
  const d = await caricaDocumento(tx, aziendaId, id);
  if (d.stato !== "bozza") throw conflitto("Solo i documenti in bozza si possono modificare");
  if (extra.clienteId && !(await tx.cliente.findFirst({ where: { id: extra.clienteId, aziendaId } }))) throw nonTrovato("Cliente");
  const t = totali(righe);
  await tx.rigaDocumento.deleteMany({ where: { documentoId: id } });
  await tx.documento.update({
    where: { id },
    data: {
      imponibile: t.imponibile, iva: t.iva, totale: t.totale, note: extra.note ?? d.note, clienteId: extra.clienteId ?? d.clienteId,
      righe: { create: righe.map((r) => ({
        varianteId: r.varianteId ?? null, descrizione: r.descrizione, quantita: r.quantita, unita: r.unita || "pz",
        prezzoUnitario: r.prezzoUnitario, sconto: r.sconto ?? 0, aliquotaIva: r.aliquotaIva, naturaIva: r.naturaIva ?? null,
      })) },
    },
  });
  return caricaDocumento(tx, aziendaId, id);
}

/** Emissione: assegna il numero progressivo dell'anno e, per fatture e note di credito, le scadenze. */
export async function emetti(tx: Tx, aziendaId: string, id: string, opzioni: { rataUnica?: boolean; rate?: number; resoInMagazzino?: string | null; utenteId?: string | null } = {}) {
  const d = await caricaDocumento(tx, aziendaId, id);
  if (d.stato !== "bozza") return d; // idempotente
  const { numero, progressivo } = await prossimoNumero(tx, aziendaId, d.tipo, d.anno);
  const stato = d.tipo === "preventivo" ? "inviato" : d.tipo === "ddt" ? "emesso" : "emessa";
  await tx.documento.update({ where: { id }, data: { numero, progressivo, stato } });
  if (d.tipo === "fattura") {
    const rate = Math.max(1, Math.min(12, opzioni.rate ?? 1));
    const base = Math.floor(d.totale / rate);
    for (let i = 0; i < rate; i++) {
      const giorni = d.cliente.giorniPagamento * (i + 1);
      await tx.scadenza.create({ data: {
        documentoId: id, importo: i === rate - 1 ? d.totale - base * (rate - 1) : base,
        dataScadenza: new Date(d.data.getTime() + giorni * 86400_000),
      } });
    }
  }
  if (d.tipo === "nota_credito" && opzioni.resoInMagazzino) {
    // reso fisico della merce: rientra nel magazzino indicato
    for (const r of d.righe) {
      if (!r.varianteId) continue;
      const v = await tx.variante.findUnique({ where: { id: r.varianteId }, include: { prodotto: true } });
      if (!v || v.prodotto.gestioneLotti || v.prodotto.gestioneSeriali) continue; // lotti e seriali: reso da registrare con carico dedicato
      await movimenta(tx, { aziendaId, tipo: "reso", varianteId: r.varianteId, magazzinoId: opzioni.resoInMagazzino,
        quantita: r.quantita, causale: `Reso con ${numero}`, riferimentoTipo: "documento", riferimentoId: id, utenteId: opzioni.utenteId });
    }
  }
  return caricaDocumento(tx, aziendaId, id);
}

/** Registra un incasso su una scadenza; quando tutte sono saldate la fattura risulta pagata. */
export async function registraPagamento(tx: Tx, aziendaId: string, scadenzaId: string, dati: { metodo: string; riferimento?: string | null; data?: Date }) {
  const s = await tx.scadenza.findFirst({ where: { id: scadenzaId, documento: { aziendaId } } });
  if (!s) throw nonTrovato("Scadenza");
  if (!s.pagata) {
    await tx.scadenza.update({ where: { id: s.id }, data: { pagata: true, dataPagamento: dati.data ?? new Date(), metodo: dati.metodo, riferimento: dati.riferimento ?? null } });
  }
  const aperte = await tx.scadenza.count({ where: { documentoId: s.documentoId, pagata: false } });
  if (aperte === 0) await tx.documento.update({ where: { id: s.documentoId }, data: { stato: "pagata" } });
  return caricaDocumento(tx, aziendaId, s.documentoId);
}

/** Righe di documento a partire da un ordine di vendita. */
export async function righeDaOrdine(tx: Tx, ordineId: string): Promise<RigaInput[]> {
  const righe = await tx.rigaOrdine.findMany({ where: { ordineId } });
  const out: RigaInput[] = [];
  for (const r of righe) {
    const v = await tx.variante.findUnique({ where: { id: r.varianteId }, include: { prodotto: true } });
    out.push({ varianteId: r.varianteId, descrizione: r.descrizione, quantita: r.quantita, unita: v?.prodotto.unita || "pz",
      prezzoUnitario: r.prezzoUnitario, sconto: r.sconto, aliquotaIva: r.aliquotaIva, naturaIva: r.naturaIva });
  }
  return out;
}
