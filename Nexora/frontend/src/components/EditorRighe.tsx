"use client";
// Editor delle righe di ordini, acquisti e documenti, con totali e riepilogo IVA calcolati come sul server. © Roberto Di Flumeri
import { Trash2 } from "lucide-react";
import type { Articolo } from "@/lib/api";
import { aCentesimi, daCentesimi, euro } from "@/lib/formato";
import { CercaArticolo } from "./articoli";
import { ALIQUOTE, NATURE } from "./FormProdotto";

export interface Riga {
  chiave: string; varianteId: string | null; sku?: string; descrizione: string; unita: string; quantita: string; prezzo: string; sconto: string;
  aliquotaIva: number; naturaIva: string; disponibile?: number;
}

export type Modo = "ordine" | "acquisto" | "documento";

export const imponibile = (r: Riga) => {
  const q = Number(r.quantita.replace(",", ".")) || 0, p = aCentesimi(r.prezzo) ?? 0, s = Number(r.sconto.replace(",", ".")) || 0;
  return Math.round(q * p * (1 - s / 100));
};

export function totali(righe: Riga[]) {
  const perAliquota = new Map<number, number>();
  for (const r of righe) perAliquota.set(r.aliquotaIva, (perAliquota.get(r.aliquotaIva) ?? 0) + imponibile(r));
  const riepilogo = [...perAliquota.entries()].map(([aliquota, base]) => ({ aliquota, base, imposta: Math.round((base * aliquota) / 100) }));
  const imp = riepilogo.reduce((s, x) => s + x.base, 0), iva = riepilogo.reduce((s, x) => s + x.imposta, 0);
  return { imponibile: imp, iva, totale: imp + iva, riepilogo };
}

export const rigaDaArticolo = (a: Articolo, modo: Modo, sconto = 0): Riga => ({
  chiave: `${a.varianteId}-${Date.now()}`, varianteId: a.varianteId, sku: a.sku, descrizione: a.nome, unita: a.unita, quantita: "1",
  prezzo: daCentesimi(modo === "acquisto" ? a.costo : a.prezzo), sconto: String(sconto), aliquotaIva: a.aliquotaIva, naturaIva: "", disponibile: a.disponibile,
});

export default function EditorRighe({ righe, onChange, modo, magazzinoId, sconto = 0 }: { righe: Riga[]; onChange: (r: Riga[]) => void; modo: Modo; magazzinoId?: string; sconto?: number }) {
  const t = totali(righe);
  const aggiorna = (i: number, k: keyof Riga, v: string | number) => onChange(righe.map((r, j) => (j === i ? { ...r, [k]: v } : r)));
  const aggiungi = (a: Articolo) => {
    // lo stesso articolo scansionato di nuovo aumenta la quantità
    const i = righe.findIndex((r) => r.varianteId === a.varianteId);
    if (i >= 0) aggiorna(i, "quantita", String((Number(righe[i].quantita.replace(",", ".")) || 0) + 1));
    else onChange([...righe, rigaDaArticolo(a, modo, sconto)]);
  };
  const libera = () => onChange([...righe, { chiave: `libera-${Date.now()}`, varianteId: null, descrizione: "", unita: "pz", quantita: "1", prezzo: "", sconto: "0", aliquotaIva: 22, naturaIva: "" }]);

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        <div className="min-w-[260px] flex-1"><CercaArticolo onScelto={aggiungi} magazzinoId={magazzinoId} segnaposto="Aggiungi articolo: nome, SKU o barcode (anche con lettore)…" /></div>
        {modo === "documento" && <button type="button" className="btn-secondario" onClick={libera}>+ Riga libera</button>}
      </div>
      {righe.length > 0 && (
        <div className="overflow-x-auto">
          <table className="tabella">
            <thead><tr><th>Descrizione</th><th className="num">Quantità</th><th className="num">{modo === "acquisto" ? "Costo" : "Prezzo"}</th>{modo !== "acquisto" && <th className="num">Sconto %</th>}
              {modo === "documento" && <th>IVA</th>}<th className="num">Imponibile</th><th /></tr></thead>
            <tbody>
              {righe.map((r, i) => {
                const q = Number(r.quantita.replace(",", ".")) || 0;
                return (
                  <tr key={r.chiave}>
                    <td className="min-w-[220px]">
                      {r.varianteId ? <><p className="font-medium">{r.descrizione}</p><p className="font-mono text-xs text-tenue">{r.sku}
                        {modo === "ordine" && r.disponibile !== undefined && <span className={q > r.disponibile ? " font-sans font-semibold text-errore" : " font-sans"}> · disponibili {r.disponibile}</span>}</p></>
                        : <input className="campo" value={r.descrizione} onChange={(e) => aggiorna(i, "descrizione", e.target.value)} placeholder="Descrizione (es. servizio di installazione)" required aria-label="Descrizione" />}
                    </td>
                    <td className="num"><input className="campo w-20 text-right" value={r.quantita} onChange={(e) => aggiorna(i, "quantita", e.target.value)} inputMode="decimal" aria-label="Quantità" required /></td>
                    <td className="num"><input className="campo w-28 text-right" value={r.prezzo} onChange={(e) => aggiorna(i, "prezzo", e.target.value)} inputMode="decimal" aria-label="Prezzo unitario" required /></td>
                    {modo !== "acquisto" && <td className="num"><input className="campo w-16 text-right" value={r.sconto} onChange={(e) => aggiorna(i, "sconto", e.target.value)} inputMode="decimal" aria-label="Sconto" /></td>}
                    {modo === "documento" && (
                      <td>
                        <select className="campo w-24" value={r.aliquotaIva} onChange={(e) => aggiorna(i, "aliquotaIva", Number(e.target.value))} aria-label="Aliquota IVA">
                          {ALIQUOTE.map((a) => <option key={a} value={a}>{a}%</option>)}
                        </select>
                        {r.aliquotaIva === 0 && <select className="campo mt-1 w-40" value={r.naturaIva} onChange={(e) => aggiorna(i, "naturaIva", e.target.value)} aria-label="Natura IVA" required>
                          <option value="">Natura…</option>{Object.keys(NATURE).map((k) => <option key={k} value={k}>{k}</option>)}</select>}
                      </td>
                    )}
                    <td className="num font-medium">{euro(imponibile(r))}</td>
                    <td><button type="button" className="btn-secondario px-2" aria-label="Rimuovi riga" onClick={() => onChange(righe.filter((_, j) => j !== i))}><Trash2 className="h-4 w-4" /></button></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      {righe.length > 0 && (
        <div className="ml-auto w-full max-w-xs space-y-1 rounded-xl bg-fondo p-4 text-sm">
          <div className="flex justify-between"><span>Imponibile</span><span className="tabular-nums">{euro(t.imponibile)}</span></div>
          {t.riepilogo.map((x) => <div key={x.aliquota} className="flex justify-between text-tenue"><span>IVA {x.aliquota}% su {euro(x.base)}</span><span className="tabular-nums">{euro(x.imposta)}</span></div>)}
          <div className="flex justify-between border-t border-bordo pt-1 text-base font-bold"><span>Totale</span><span className="tabular-nums">{euro(t.totale)}</span></div>
        </div>
      )}
    </div>
  );
}
