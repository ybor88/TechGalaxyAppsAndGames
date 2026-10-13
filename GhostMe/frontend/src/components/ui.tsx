"use client";
// Componenti UI condivisi — © Roberto Di Flumeri

import clsx from "clsx";
import { AlertTriangle, CheckCircle2, Info, Loader2, Moon, Sparkles, Sun } from "lucide-react";
import { useEffect, useState } from "react";
import { CATEGORIE, type Fonte } from "@/lib/api";

export function Titolo({ titolo, sottotitolo, azioni }: { titolo: string; sottotitolo?: string; azioni?: React.ReactNode }) {
  return (
    <header className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-2xl font-bold">{titolo}</h1>
        {sottotitolo && <p className="mt-1 text-tenue">{sottotitolo}</p>}
      </div>
      {azioni && <div className="flex flex-wrap gap-2">{azioni}</div>}
    </header>
  );
}

export function Avviso({ tipo = "info", children }: { tipo?: "info" | "ok" | "attenzione" | "errore"; children: React.ReactNode }) {
  const Icona = { info: Info, ok: CheckCircle2, attenzione: AlertTriangle, errore: AlertTriangle }[tipo];
  const colori = {
    info: "border-blu/30 bg-blu/10",
    ok: "border-ok/30 bg-ok/10",
    attenzione: "border-attenzione/40 bg-attenzione/10",
    errore: "border-errore/40 bg-errore/10",
  }[tipo];
  return (
    <div role={tipo === "errore" ? "alert" : "status"} className={clsx("flex gap-2 rounded-xl border p-3 text-sm", colori)}>
      <Icona className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
      <div>{children}</div>
    </div>
  );
}

export function Caricamento({ testo = "Caricamento…" }: { testo?: string }) {
  return (
    <p className="flex items-center gap-2 text-tenue" role="status">
      <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> {testo}
    </p>
  );
}

export function EtichettaGenerata({ testo }: { testo?: string }) {
  return (
    <span className="pillola bg-viola/15 text-viola" title={testo || "Contenuto generato: è una simulazione"}>
      <Sparkles className="h-3 w-3" aria-hidden /> Contenuto generato
    </span>
  );
}

export function Confidenza({ livello }: { livello?: string | null }) {
  if (!livello) return null;
  const c = { alta: "bg-ok/15 text-ok", media: "bg-attenzione/15 text-attenzione", bassa: "bg-errore/15 text-errore" }[livello] || "";
  return <span className={clsx("pillola", c)}>Confidenza {livello}</span>;
}

export function Categoria({ nome }: { nome: string }) {
  return <span className="pillola bg-blu/10 text-blu">{CATEGORIE[nome] || nome}</span>;
}

export function ElencoFonti({ fonti }: { fonti?: Fonte[] | null }) {
  if (!fonti || fonti.length === 0) return null;
  return (
    <details className="mt-2 text-sm">
      <summary className="cursor-pointer text-tenue">Fonti personali usate ({fonti.length})</summary>
      <ol className="mt-2 space-y-1">
        {fonti.map((f, i) => (
          <li key={f.id + i} className="rounded-lg bg-fondo p-2">
            <span className="mr-1 font-mono text-xs text-tenue">[{i + 1}]</span>
            <Categoria nome={f.categoria} /> <span className="text-xs text-tenue">{f.tipo}</span>
            <div className="mt-1">{f.estratto}</div>
          </li>
        ))}
      </ol>
    </details>
  );
}

export function Peso({ valore, onChange, id }: { valore: number; onChange: (v: number) => void; id?: string }) {
  return (
    <div className="flex items-center gap-1" role="radiogroup" aria-label="Importanza" id={id}>
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          role="radio"
          aria-checked={valore === n}
          aria-label={`Importanza ${n} su 5`}
          onClick={() => onChange(n)}
          className={clsx("h-7 w-7 rounded-lg border text-sm", n <= valore ? "gradiente border-transparent text-white" : "border-bordo")}
        >
          {n}
        </button>
      ))}
    </div>
  );
}

export function Interruttore({ attivo, onChange, etichetta, descrizione }: {
  attivo: boolean; onChange: (v: boolean) => void; etichetta: string; descrizione?: string;
}) {
  return (
    <label className="flex cursor-pointer items-start gap-3">
      <input type="checkbox" className="peer sr-only" checked={attivo} onChange={(e) => onChange(e.target.checked)} />
      <span aria-hidden className={clsx("mt-0.5 inline-flex h-6 w-11 shrink-0 items-center rounded-full p-0.5 transition peer-focus-visible:ring-2 peer-focus-visible:ring-blu",
        attivo ? "gradiente" : "bg-bordo")}>
        <span className={clsx("h-5 w-5 rounded-full bg-white shadow transition", attivo && "translate-x-5")} />
      </span>
      <span>
        <span className="font-medium">{etichetta}</span>
        {descrizione && <span className="block text-sm text-tenue">{descrizione}</span>}
      </span>
    </label>
  );
}

export function CambiaTema() {
  const [scuro, setScuro] = useState(false);
  useEffect(() => { setScuro(document.documentElement.classList.contains("dark")); }, []);
  const alterna = () => {
    const nuovo = !scuro;
    document.documentElement.classList.toggle("dark", nuovo);
    try {
      localStorage.setItem("gm_tema", nuovo ? "scuro" : "chiaro");
    } catch {}
    setScuro(nuovo);
  };
  return (
    <button onClick={alterna} className="btn-secondario px-3" aria-label={scuro ? "Passa al tema chiaro" : "Passa al tema scuro"}>
      {scuro ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
    </button>
  );
}

export function Copyright() {
  return <p className="text-xs text-tenue">© {new Date().getFullYear()} Roberto Di Flumeri · Ghost Me</p>;
}

/** Gestisce stato di invio ed errore di un'azione asincrona, evitando doppi invii. */
export function useAzione() {
  const [inCorso, setInCorso] = useState(false);
  const [errore, setErrore] = useState<string | null>(null);
  const esegui = async (fn: () => Promise<unknown>) => {
    if (inCorso) return false;
    setInCorso(true);
    setErrore(null);
    try {
      await fn();
      return true;
    } catch (e) {
      setErrore(e instanceof Error ? e.message : "Operazione non riuscita");
      return false;
    } finally {
      setInCorso(false);
    }
  };
  return { inCorso, errore, setErrore, esegui };
}
