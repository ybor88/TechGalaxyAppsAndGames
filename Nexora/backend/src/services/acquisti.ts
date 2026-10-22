// Ordini di acquisto: bozza → inviato → parziale / ricevuto (o annullato). Il ricevimento carica il magazzino
// con lotti e seriali e aggiorna il costo d'acquisto (costo medio ponderato). © Roberto Di Flumeri
import type { Tx } from "../lib/db.js";
import { conflitto, nonTrovato, nonValido } from "../lib/errori.js";
import { arrotonda3, totali } from "../lib/importi.js";
import { prossimoNumero } from "../lib/numeratore.js";
import { movimenta } from "./magazzino.js";

export interface RigaAcquistoInput { varianteId: string; quantita: number; costoUnitario?: number; descrizione?: string }

export async function caricaAcquisto(tx: Tx, aziendaId: string, id: string) {
  const o = await tx.ordineAcquisto.findFirst({ where: { id, aziendaId }, include: { righe: true, fornitore: true, magazzino: true } });
  if (!o) throw nonTrovato("Ordine di acquisto");
  return o;
}

export async function creaAcquisto(tx: Tx, aziendaId: string, dati: {
  fornitoreId: string; magazzinoId?: string; righe: RigaAcquistoInput[]; note?: string | null; generatoDaAi?: boolean; data?: Date;
}) {
  const f = await tx.fornitore.findFirst({ where: { id: dati.fornitoreId, aziendaId } });
  if (!f) throw nonTrovato("Fornitore");
  const m = dati.magazzinoId
    ? await tx.magazzino.findFirst({ where: { id: dati.magazzinoId, aziendaId } })
    : await tx.magazzino.findFirst({ where: { aziendaId, principale: true } });
  if (!m) throw nonTrovato("Magazzino");
  if (!dati.righe.length) throw nonValido("Righe: serve almeno un articolo");
  const righe = [];
  for (const r of dati.righe) {
    if (!(r.quantita > 0)) throw nonValido("Quantità: deve essere maggiore di zero");
    const v = await tx.variante.findFirst({ where: { id: r.varianteId, aziendaId }, include: { prodotto: true } });
    if (!v) throw nonTrovato("Articolo");
    righe.push({
      varianteId: v.id, quantita: r.quantita, costoUnitario: r.costoUnitario ?? v.prodotto.costoAcquisto,
      descrizione: r.descrizione || (v.nome === "Standard" ? v.prodotto.nome : `${v.prodotto.nome} - ${v.nome}`), aliquotaIva: v.prodotto.aliquotaIva,
    });
  }
  const t = totali(righe.map((r) => ({ quantita: r.quantita, prezzoUnitario: r.costoUnitario, aliquotaIva: r.aliquotaIva })));
  const data = dati.data ?? new Date();
  const { numero } = await prossimoNumero(tx, aziendaId, "acquisto", data.getFullYear());
  const o = await tx.ordineAcquisto.create({ data: {
    aziendaId, numero, fornitoreId: f.id, magazzinoId: m.id, note: dati.note ?? null, generatoDaAi: dati.generatoDaAi ?? false, data,
    dataPrevista: new Date(data.getTime() + f.leadTimeGiorni * 86400_000),
    imponibile: t.imponibile, iva: t.iva, totale: t.totale, righe: { create: righe },
  } });
  return caricaAcquisto(tx, aziendaId, o.id);
}

export async function inviaAcquisto(tx: Tx, aziendaId: string, id: string) {
  const o = await caricaAcquisto(tx, aziendaId, id);
  if (o.stato === "inviato") return o;
  if (o.stato !== "bozza") throw conflitto("Solo un ordine in bozza può essere inviato");
  await tx.ordineAcquisto.update({ where: { id }, data: { stato: "inviato" } });
  return caricaAcquisto(tx, aziendaId, id);
}

export async function annullaAcquisto(tx: Tx, aziendaId: string, id: string) {
  const o = await caricaAcquisto(tx, aziendaId, id);
  if (o.righe.some((r) => r.quantitaRicevuta > 0)) throw conflitto("Merce già ricevuta: l'ordine non si può annullare");
  await tx.ordineAcquisto.update({ where: { id }, data: { stato: "annullato" } });
  return caricaAcquisto(tx, aziendaId, id);
}

/** Ricevimento (anche parziale) della merce. */
export async function riceviAcquisto(tx: Tx, aziendaId: string, id: string, ricezioni: {
  rigaId: string; quantita: number; lotto?: { codice: string; scadenza?: Date | null }; seriali?: string[];
}[], utenteId?: string | null, data?: Date) {
  const o = await caricaAcquisto(tx, aziendaId, id);
  if (!["inviato", "parziale"].includes(o.stato)) throw conflitto("La merce si riceve solo per ordini inviati");
  for (const r of ricezioni) {
    const riga = o.righe.find((x) => x.id === r.rigaId);
    if (!riga) throw nonTrovato("Riga dell'ordine");
    const residuo = arrotonda3(riga.quantita - riga.quantitaRicevuta);
    if (r.quantita <= 0 || r.quantita > residuo) throw nonValido(`Quantità per «${riga.descrizione}»: puoi ricevere al massimo ${residuo}`);
    const v = await tx.variante.findUnique({ where: { id: riga.varianteId }, include: { prodotto: true, giacenze: true } });
    // costo medio ponderato del prodotto
    if (v) {
      const esistente = v.giacenze.reduce((s, g) => s + Math.max(0, g.quantita), 0);
      const nuovoCosto = esistente + r.quantita > 0
        ? Math.round((esistente * v.prodotto.costoAcquisto + r.quantita * riga.costoUnitario) / (esistente + r.quantita))
        : riga.costoUnitario;
      await tx.prodotto.update({ where: { id: v.prodottoId }, data: { costoAcquisto: nuovoCosto } });
    }
    const m = await movimenta(tx, {
      aziendaId, tipo: "acquisto", varianteId: riga.varianteId, magazzinoId: o.magazzinoId, quantita: r.quantita,
      lotto: r.lotto, seriali: r.seriali, costoUnitario: riga.costoUnitario, causale: `Ricevimento ${o.numero}`,
      riferimentoTipo: "acquisto", riferimentoId: o.id, utenteId,
    });
    if (data) await tx.movimento.updateMany({ where: { id: { in: m.map((x) => x.id) } }, data: { createdAt: data } });
    await tx.rigaAcquisto.update({ where: { id: riga.id }, data: { quantitaRicevuta: arrotonda3(riga.quantitaRicevuta + r.quantita) } });
  }
  const righe = await tx.rigaAcquisto.findMany({ where: { ordineId: id } });
  const completo = righe.every((r) => r.quantitaRicevuta >= r.quantita);
  await tx.ordineAcquisto.update({ where: { id }, data: { stato: completo ? "ricevuto" : "parziale" } });
  return caricaAcquisto(tx, aziendaId, id);
}
