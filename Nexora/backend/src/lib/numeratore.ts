// Numerazione progressiva dei documenti, per azienda, tipo e anno (senza buchi né duplicati). © Roberto Di Flumeri
import type { Tx } from "./db.js";

export const PREFISSI: Record<string, string> = {
  ordine: "OV", acquisto: "OA", preventivo: "PR", ddt: "DDT", fattura: "FT", nota_credito: "NC",
};

export async function prossimoNumero(tx: Tx, aziendaId: string, tipo: string, anno = new Date().getFullYear()) {
  const chiave = `${PREFISSI[tipo] || tipo}-${anno}`;
  const c = await tx.contatore.upsert({
    where: { aziendaId_chiave: { aziendaId, chiave } },
    create: { aziendaId, chiave, valore: 1 },
    update: { valore: { increment: 1 } },
  });
  return { progressivo: c.valore, numero: `${chiave}-${String(c.valore).padStart(4, "0")}`, anno };
}
