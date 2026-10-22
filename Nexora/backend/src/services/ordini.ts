// Ordini di vendita: bozza → confermato → in preparazione → spedito → consegnato (o annullato).
// La conferma impegna la merce, la spedizione la scarica ed emette il DDT. © Roberto Di Flumeri
import type { Tx } from "../lib/db.js";
import { conflitto, nonTrovato, nonValido } from "../lib/errori.js";
import { totali } from "../lib/importi.js";
import { prossimoNumero } from "../lib/numeratore.js";
import { creaDocumento, emetti, righeDaOrdine } from "./documenti.js";
import { movimenta, riserva, rilascia } from "./magazzino.js";

export const STATI_ORDINE = ["bozza", "confermato", "in_preparazione", "spedito", "consegnato", "annullato"] as const;
export type StatoOrdine = (typeof STATI_ORDINE)[number];

const TRANSIZIONI: Record<StatoOrdine, StatoOrdine[]> = {
  bozza: ["confermato", "annullato"],
  confermato: ["in_preparazione", "spedito", "annullato"],
  in_preparazione: ["spedito", "annullato"],
  spedito: ["consegnato"],
  consegnato: [],
  annullato: [],
};

export interface RigaOrdineInput {
  varianteId: string;
  quantita: number;
  prezzoUnitario?: number; // se assente: prezzo di listino della variante
  sconto?: number;
  descrizione?: string;
  lottoId?: string | null;
  seriali?: string[] | null;
}

export async function caricaOrdine(tx: Tx, aziendaId: string, id: string) {
  const o = await tx.ordineVendita.findFirst({
    where: { id, aziendaId }, include: { righe: true, cliente: true, magazzino: true, spedizioni: true },
  });
  if (!o) throw nonTrovato("Ordine");
  return o;
}

async function preparaRighe(tx: Tx, aziendaId: string, righe: RigaOrdineInput[], scontoCliente: number) {
  if (!righe.length) throw nonValido("Righe: serve almeno un articolo");
  const out = [];
  for (const r of righe) {
    if (!(r.quantita > 0)) throw nonValido("Quantità: deve essere maggiore di zero");
    const v = await tx.variante.findFirst({ where: { id: r.varianteId, aziendaId }, include: { prodotto: true } });
    if (!v) throw nonTrovato("Articolo");
    if (!v.attivo || !v.prodotto.attivo) throw conflitto(`L'articolo ${v.sku} non è più in vendita`);
    out.push({
      varianteId: v.id, quantita: r.quantita,
      descrizione: r.descrizione || (v.nome === "Standard" ? v.prodotto.nome : `${v.prodotto.nome} - ${v.nome}`),
      prezzoUnitario: r.prezzoUnitario ?? v.prezzo ?? v.prodotto.prezzoVendita,
      sconto: r.sconto ?? scontoCliente, aliquotaIva: v.prodotto.aliquotaIva, naturaIva: v.prodotto.naturaIva,
      costoUnitario: v.prodotto.costoAcquisto, lottoId: r.lottoId ?? null, seriali: r.seriali?.length ? JSON.stringify(r.seriali) : null,
    });
  }
  return out;
}

export async function creaOrdine(tx: Tx, aziendaId: string, dati: {
  clienteId: string; magazzinoId?: string; righe: RigaOrdineInput[]; canale?: string; riferimentoEsterno?: string | null;
  note?: string | null; indirizzoSpedizione?: string | null; dataConsegnaPrevista?: Date | null; preventivoId?: string | null; data?: Date;
}) {
  const cliente = await tx.cliente.findFirst({ where: { id: dati.clienteId, aziendaId } });
  if (!cliente) throw nonTrovato("Cliente");
  const magazzino = dati.magazzinoId
    ? await tx.magazzino.findFirst({ where: { id: dati.magazzinoId, aziendaId } })
    : await tx.magazzino.findFirst({ where: { aziendaId, principale: true } }) ?? await tx.magazzino.findFirst({ where: { aziendaId } });
  if (!magazzino) throw nonTrovato("Magazzino");
  const righe = await preparaRighe(tx, aziendaId, dati.righe, cliente.scontoPercentuale);
  const t = totali(righe);
  const data = dati.data ?? new Date();
  const { numero } = await prossimoNumero(tx, aziendaId, "ordine", data.getFullYear());
  const o = await tx.ordineVendita.create({ data: {
    aziendaId, numero, clienteId: cliente.id, magazzinoId: magazzino.id, canale: dati.canale ?? "diretto",
    riferimentoEsterno: dati.riferimentoEsterno ?? null, note: dati.note ?? null, data,
    indirizzoSpedizione: dati.indirizzoSpedizione ?? [cliente.indirizzo, cliente.cap, cliente.citta].filter(Boolean).join(", "),
    dataConsegnaPrevista: dati.dataConsegnaPrevista ?? null, preventivoId: dati.preventivoId ?? null,
    imponibile: t.imponibile, iva: t.iva, totale: t.totale, righe: { create: righe },
  } });
  return caricaOrdine(tx, aziendaId, o.id);
}

export async function modificaOrdine(tx: Tx, aziendaId: string, id: string, dati: { righe?: RigaOrdineInput[]; note?: string | null; indirizzoSpedizione?: string | null; magazzinoId?: string }) {
  const o = await caricaOrdine(tx, aziendaId, id);
  if (o.stato !== "bozza") throw conflitto("Solo gli ordini in bozza si possono modificare");
  if (dati.magazzinoId && !(await tx.magazzino.findFirst({ where: { id: dati.magazzinoId, aziendaId } }))) throw nonTrovato("Magazzino");
  if (dati.righe) {
    const righe = await preparaRighe(tx, aziendaId, dati.righe, o.cliente.scontoPercentuale);
    const t = totali(righe);
    await tx.rigaOrdine.deleteMany({ where: { ordineId: id } });
    await tx.ordineVendita.update({ where: { id }, data: { imponibile: t.imponibile, iva: t.iva, totale: t.totale, righe: { create: righe } } });
  }
  await tx.ordineVendita.update({ where: { id }, data: {
    note: dati.note ?? o.note, indirizzoSpedizione: dati.indirizzoSpedizione ?? o.indirizzoSpedizione, magazzinoId: dati.magazzinoId ?? o.magazzinoId,
  } });
  return caricaOrdine(tx, aziendaId, id);
}

/** Cambio di stato con tutti gli effetti su magazzino e documenti. */
export async function cambiaStato(tx: Tx, aziendaId: string, id: string, nuovo: StatoOrdine, opzioni: { utenteId?: string | null; data?: Date } = {}) {
  const o = await caricaOrdine(tx, aziendaId, id);
  const stato = o.stato as StatoOrdine;
  if (stato === nuovo) return { ordine: o, ddtId: null as string | null }; // idempotente
  if (!TRANSIZIONI[stato].includes(nuovo)) throw conflitto(`Passaggio non consentito: da «${stato}» a «${nuovo}»`);
  const quando = opzioni.data ?? new Date();
  let ddtId: string | null = null;

  if (nuovo === "confermato") {
    for (const r of o.righe) await riserva(tx, aziendaId, o.magazzinoId, r.varianteId, r.quantita);
    await tx.ordineVendita.update({ where: { id }, data: { stato: nuovo, confermatoAt: quando } });
  } else if (nuovo === "in_preparazione") {
    await tx.ordineVendita.update({ where: { id }, data: { stato: nuovo } });
  } else if (nuovo === "spedito") {
    for (const r of o.righe) {
      const seriali = r.seriali ? JSON.parse(r.seriali) as string[] : undefined;
      const movimenti = await movimenta(tx, {
        aziendaId, tipo: "vendita", varianteId: r.varianteId, magazzinoId: o.magazzinoId, quantita: r.quantita,
        lottoId: r.lottoId ?? undefined, seriali, daRiservato: true, causale: `Spedizione ${o.numero}`,
        riferimentoTipo: "ordine", riferimentoId: o.id, utenteId: opzioni.utenteId,
      });
      // lotti e seriali effettivamente usati restano sulla riga (tracciabilità)
      const usati = movimenti.flatMap((m) => (m.seriali ? JSON.parse(m.seriali) as string[] : []));
      await tx.rigaOrdine.update({ where: { id: r.id }, data: {
        lottoId: movimenti.length === 1 ? movimenti[0].lottoId : r.lottoId, seriali: usati.length ? JSON.stringify(usati) : r.seriali,
      } });
    }
    const ddt = await creaDocumento(tx, aziendaId, {
      tipo: "ddt", clienteId: o.clienteId, ordineId: o.id, righe: await righeDaOrdine(tx, o.id), causaleTrasporto: "Vendita", data: quando,
    });
    await emetti(tx, aziendaId, ddt.id);
    ddtId = ddt.id;
    await tx.ordineVendita.update({ where: { id }, data: { stato: nuovo, spedito: quando } });
  } else if (nuovo === "consegnato") {
    await tx.ordineVendita.update({ where: { id }, data: { stato: nuovo, consegnato: quando } });
  } else if (nuovo === "annullato") {
    if (stato === "confermato" || stato === "in_preparazione") {
      for (const r of o.righe) await rilascia(tx, o.magazzinoId, r.varianteId, r.quantita);
    }
    await tx.ordineVendita.update({ where: { id }, data: { stato: nuovo } });
  }
  return { ordine: await caricaOrdine(tx, aziendaId, id), ddtId };
}

/** Fattura (in bozza) dall'ordine spedito o consegnato; una sola fattura attiva per ordine. */
export async function fatturaDaOrdine(tx: Tx, aziendaId: string, id: string, data?: Date) {
  const o = await caricaOrdine(tx, aziendaId, id);
  if (!["spedito", "consegnato", "confermato", "in_preparazione"].includes(o.stato)) throw conflitto("Si può fatturare solo un ordine confermato");
  const esistente = await tx.documento.findFirst({ where: { aziendaId, ordineId: id, tipo: "fattura", stato: { not: "annullata" } } });
  if (esistente) return esistente;
  return creaDocumento(tx, aziendaId, { tipo: "fattura", clienteId: o.clienteId, ordineId: id, righe: await righeDaOrdine(tx, id), data });
}
