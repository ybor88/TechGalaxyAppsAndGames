"use client";
// © Roberto Di Flumeri

import { useEffect, useMemo, useState } from "react";
import { ChevronLeft, ChevronRight, Search } from "lucide-react";

export const PER_PAGINA = 50;

interface PaginazioneProps {
  pagina: number; // 0-based
  totale: number;
  perPagina?: number;
  onCambia: (pagina: number) => void;
}

/** Barra di paginazione: "1–50 di 5.000" con pulsanti precedente/successiva. */
export default function Paginazione({ pagina, totale, perPagina = PER_PAGINA, onCambia }: PaginazioneProps) {
  const pagine = Math.max(1, Math.ceil(totale / perPagina));
  if (totale <= perPagina) return null;
  const da = pagina * perPagina + 1;
  const a = Math.min(totale, (pagina + 1) * perPagina);
  const fmt = (n: number) => n.toLocaleString("it-IT");
  const btn =
    "flex items-center gap-1 px-3 py-1.5 rounded-lg text-sm border border-gray-200 bg-white text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed";
  return (
    <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100 text-sm text-gray-500">
      <span>
        {fmt(da)}–{fmt(a)} di {fmt(totale)}
      </span>
      <div className="flex items-center gap-2">
        <button className={btn} disabled={pagina === 0} onClick={() => onCambia(pagina - 1)}>
          <ChevronLeft className="w-4 h-4" /> Precedente
        </button>
        <span className="px-2">
          Pagina {pagina + 1} di {fmt(pagine)}
        </span>
        <button className={btn} disabled={pagina >= pagine - 1} onClick={() => onCambia(pagina + 1)}>
          Successiva <ChevronRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}

/** Paginazione lato client per liste già caricate interamente. */
export function usePaginaLocale<T>(elementi: T[], perPagina = PER_PAGINA) {
  const [pagina, setPagina] = useState(0);
  const pagine = Math.max(1, Math.ceil(elementi.length / perPagina));
  // se la lista si accorcia (filtro, eliminazione) non restare su una pagina vuota
  useEffect(() => {
    if (pagina > pagine - 1) setPagina(pagine - 1);
  }, [pagina, pagine]);
  const visibili = useMemo(
    () => elementi.slice(pagina * perPagina, (pagina + 1) * perPagina),
    [elementi, pagina, perPagina],
  );
  return { visibili, pagina, setPagina, totale: elementi.length, perPagina };
}

/** Valore con ritardo, per non interrogare il server a ogni tasto premuto. */
export function useDebounce<T>(valore: T, ms = 350): T {
  const [v, setV] = useState(valore);
  useEffect(() => {
    const t = setTimeout(() => setV(valore), ms);
    return () => clearTimeout(t);
  }, [valore, ms]);
  return v;
}

/** Campo di ricerca con icona, nello stile delle pagine dell'app. */
export function CampoRicerca({
  valore,
  onCambia,
  placeholder = "Cerca…",
}: {
  valore: string;
  onCambia: (v: string) => void;
  placeholder?: string;
}) {
  return (
    <div className="relative">
      <Search className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 -translate-y-1/2" />
      <input
        type="search"
        value={valore}
        onChange={(e) => onCambia(e.target.value)}
        placeholder={placeholder}
        className="pl-9 pr-3 py-2 w-64 rounded-lg border border-gray-200 bg-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-200"
      />
    </div>
  );
}
