// Calcolo degli importi in centesimi: righe, sconti, riepilogo IVA per aliquota. © Roberto Di Flumeri

export interface RigaCalcolo {
  quantita: number;
  prezzoUnitario: number; // centesimi, IVA esclusa
  sconto?: number; // percentuale
  aliquotaIva: number;
  naturaIva?: string | null;
}

export const arrotonda3 = (n: number) => Math.round(n * 1000) / 1000;

/** Imponibile di riga in centesimi (arrotondato al centesimo). */
export function imponibileRiga(r: RigaCalcolo): number {
  return Math.round(r.quantita * r.prezzoUnitario * (1 - (r.sconto || 0) / 100));
}

export interface RiepilogoIva { aliquota: number; natura: string | null; imponibile: number; imposta: number }

/** L'IVA si calcola sul totale per aliquota (come nella fattura elettronica), non riga per riga. */
export function totali(righe: RigaCalcolo[]): { imponibile: number; iva: number; totale: number; riepilogo: RiepilogoIva[] } {
  const gruppi = new Map<string, RiepilogoIva>();
  for (const r of righe) {
    const chiave = `${r.aliquotaIva}|${r.naturaIva || ""}`;
    const g = gruppi.get(chiave) || { aliquota: r.aliquotaIva, natura: r.naturaIva || null, imponibile: 0, imposta: 0 };
    g.imponibile += imponibileRiga(r);
    gruppi.set(chiave, g);
  }
  const riepilogo = [...gruppi.values()].map((g) => ({ ...g, imposta: Math.round((g.imponibile * g.aliquota) / 100) }));
  const imponibile = riepilogo.reduce((s, g) => s + g.imponibile, 0);
  const iva = riepilogo.reduce((s, g) => s + g.imposta, 0);
  return { imponibile, iva, totale: imponibile + iva, riepilogo };
}

export const euro = (centesimi: number) =>
  (centesimi / 100).toLocaleString("it-IT", { style: "currency", currency: "EUR" });

/** Importo con due decimali e punto (formato richiesto dalla fattura elettronica). */
export const decimale = (centesimi: number) => (centesimi / 100).toFixed(2);
