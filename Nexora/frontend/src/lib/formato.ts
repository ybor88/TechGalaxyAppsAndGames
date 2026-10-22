// Formattazione all'italiana di importi, quantità e date. © Roberto Di Flumeri

const EURO = new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR" });
const EURO_TONDO = new Intl.NumberFormat("it-IT", { style: "currency", currency: "EUR", maximumFractionDigits: 0 });
const NUM = new Intl.NumberFormat("it-IT", { maximumFractionDigits: 3 });

/** Importi in centesimi → «1.234,56 €». */
export const euro = (centesimi: number | null | undefined, tondo = false) => ((tondo ? EURO_TONDO : EURO).format((centesimi ?? 0) / 100));
export const numero = (n: number | null | undefined) => NUM.format(n ?? 0);
export const percentuale = (n: number | null | undefined) => (n === null || n === undefined ? "—" : `${n > 0 ? "+" : ""}${n.toLocaleString("it-IT", { maximumFractionDigits: 1 })}%`);

export const data = (d: string | Date | null | undefined) => (d ? new Date(d).toLocaleDateString("it-IT", { day: "2-digit", month: "2-digit", year: "numeric" }) : "—");
export const dataOra = (d: string | Date | null | undefined) =>
  d ? new Date(d).toLocaleString("it-IT", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" }) : "—";
export const mese = (chiave: string) => {
  const [a, m] = chiave.split("-").map(Number);
  return new Date(a, m - 1, 1).toLocaleDateString("it-IT", { month: "short", year: "2-digit" });
};
export const giornoBreve = (d: string) => new Date(d).toLocaleDateString("it-IT", { day: "2-digit", month: "short" });

export function fa(d: string | Date) {
  const s = Math.round((Date.now() - new Date(d).getTime()) / 1000);
  if (s < 60) return "adesso";
  if (s < 3600) return `${Math.floor(s / 60)} min fa`;
  if (s < 86400) return `${Math.floor(s / 3600)} h fa`;
  const g = Math.floor(s / 86400);
  return g === 1 ? "ieri" : `${g} giorni fa`;
}

/** Input «12,50» → 1250 centesimi (null se non valido). */
export function aCentesimi(v: string): number | null {
  const t = v.trim().replace(/\s|€/g, "").replace(/\.(?=\d{3}(\D|$))/g, "").replace(",", ".");
  if (!t) return null;
  const n = Number(t);
  return Number.isFinite(n) && n >= 0 ? Math.round(n * 100) : null;
}
export const daCentesimi = (c: number | null | undefined) => (c === null || c === undefined ? "" : (c / 100).toFixed(2).replace(".", ","));
export const oggiIso = () => new Date().toISOString().slice(0, 10);
