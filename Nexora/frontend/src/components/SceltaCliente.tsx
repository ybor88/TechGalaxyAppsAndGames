"use client";
// Selezione del cliente con ricerca. © Roberto Di Flumeri
import { useEffect, useState } from "react";
import { api, qs } from "@/lib/api";

export interface ClienteBreve { id: string; ragioneSociale: string; citta: string | null; scontoPercentuale: number; giorniPagamento: number; tipo: string; partitaIva: string | null }

export default function SceltaCliente({ valore, onScelto }: { valore: ClienteBreve | null; onScelto: (c: ClienteBreve | null) => void }) {
  const [q, setQ] = useState("");
  const [lista, setLista] = useState<ClienteBreve[]>([]);
  const [aperto, setAperto] = useState(false);
  useEffect(() => {
    if (!aperto) return;
    const t = setTimeout(() => api<ClienteBreve[]>(`/api/clienti${qs({ q })}`).then((r) => setLista(r.slice(0, 30))).catch(() => setLista([])), 180);
    return () => clearTimeout(t);
  }, [q, aperto]);

  if (valore) {
    return (
      <div className="flex items-center justify-between gap-2 rounded-xl border border-bordo bg-fondo px-3 py-2">
        <span className="min-w-0"><span className="block truncate font-medium">{valore.ragioneSociale}</span>
          <span className="text-xs text-tenue">{valore.citta ?? ""}{valore.scontoPercentuale ? ` · sconto ${valore.scontoPercentuale}%` : ""} · pagamento {valore.giorniPagamento} gg</span></span>
        <button type="button" className="link text-sm" onClick={() => onScelto(null)}>Cambia</button>
      </div>
    );
  }
  return (
    <div className="relative">
      <input className="campo" placeholder="Cerca cliente per nome, P.IVA o città…" value={q} onChange={(e) => { setQ(e.target.value); setAperto(true); }}
        onFocus={() => setAperto(true)} onBlur={() => setTimeout(() => setAperto(false), 180)} aria-label="Cliente" />
      {aperto && (
        <ul className="absolute z-50 mt-1 max-h-72 w-full overflow-y-auto rounded-xl border border-bordo bg-pannello shadow-lg">
          {!lista.length && <li className="p-3 text-sm text-tenue">Nessun cliente</li>}
          {lista.map((c) => (
            <li key={c.id}><button type="button" className="w-full px-3 py-2 text-left text-sm hover:bg-fondo" onMouseDown={(e) => e.preventDefault()} onClick={() => { onScelto(c); setQ(""); setAperto(false); }}>
              <span className="font-medium">{c.ragioneSociale}</span> <span className="text-xs text-tenue">{c.citta ?? ""}</span></button></li>
          ))}
        </ul>
      )}
    </div>
  );
}
