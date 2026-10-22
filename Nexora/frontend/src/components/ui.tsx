"use client";
// Componenti UI condivisi — © Roberto Di Flumeri

import clsx from "clsx";
import { AlertTriangle, CheckCircle2, Info, Loader2, Moon, OctagonAlert, Search, Sun, X } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";

export function Titolo({ titolo, sottotitolo, azioni, indietro }: { titolo: React.ReactNode; sottotitolo?: React.ReactNode; azioni?: React.ReactNode; indietro?: React.ReactNode }) {
  return (
    <header className="mb-6">
      {indietro && <div className="mb-2 text-sm">{indietro}</div>}
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="min-w-0">
          <h1 className="text-2xl font-bold tracking-tight">{titolo}</h1>
          {sottotitolo && <p className="mt-1 text-tenue">{sottotitolo}</p>}
        </div>
        {azioni && <div className="flex flex-wrap gap-2">{azioni}</div>}
      </div>
    </header>
  );
}

type TipoAvviso = "info" | "ok" | "attenzione" | "errore";
export function Avviso({ tipo = "info", children, className }: { tipo?: TipoAvviso; children: React.ReactNode; className?: string }) {
  const Icona = { info: Info, ok: CheckCircle2, attenzione: AlertTriangle, errore: OctagonAlert }[tipo];
  const colori = { info: "border-primario/30 bg-primario/10", ok: "border-ok/30 bg-ok/10", attenzione: "border-attenzione/40 bg-attenzione/10", errore: "border-errore/40 bg-errore/10" }[tipo];
  return (
    <div role={tipo === "errore" ? "alert" : "status"} className={clsx("flex gap-2 rounded-xl border p-3 text-sm", colori, className)}>
      <Icona className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
      <div className="min-w-0">{children}</div>
    </div>
  );
}

export function Caricamento({ testo = "Caricamento…" }: { testo?: string }) {
  return <p className="flex items-center gap-2 p-2 text-tenue" role="status"><Loader2 className="h-4 w-4 animate-spin" aria-hidden /> {testo}</p>;
}

export function Vuoto({ icona: Icona = Search, titolo, testo, azione }: { icona?: React.ComponentType<{ className?: string }>; titolo: string; testo?: string; azione?: React.ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 py-10 text-center">
      <span className="flex h-14 w-14 items-center justify-center rounded-2xl gradiente text-white"><Icona className="h-7 w-7" aria-hidden /></span>
      <p className="font-semibold">{titolo}</p>
      {testo && <p className="max-w-md text-sm text-tenue">{testo}</p>}
      {azione && <div className="mt-2">{azione}</div>}
    </div>
  );
}

export function Modale({ titolo, onChiudi, children, larga }: { titolo: string; onChiudi: () => void; children: React.ReactNode; larga?: boolean }) {
  useEffect(() => {
    const esc = (e: KeyboardEvent) => e.key === "Escape" && onChiudi();
    document.addEventListener("keydown", esc);
    return () => document.removeEventListener("keydown", esc);
  }, [onChiudi]);
  return (
    <div className="fixed inset-0 z-[1000] flex items-end justify-center bg-black/50 sm:items-center sm:p-4" role="dialog" aria-modal="true" aria-label={titolo} onMouseDown={onChiudi}>
      <div className={clsx("scheda max-h-[92vh] w-full overflow-y-auto rounded-b-none sm:rounded-2xl", larga ? "max-w-3xl" : "max-w-lg")} onMouseDown={(e) => e.stopPropagation()}>
        <div className="mb-4 flex items-start justify-between gap-2">
          <h2 className="text-lg font-semibold">{titolo}</h2>
          <button className="btn-secondario px-2 py-1" onClick={onChiudi} aria-label="Chiudi"><X className="h-4 w-4" /></button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function Schede<T extends string>({ voci, attiva, onCambia }: { voci: { id: T; etichetta: React.ReactNode }[]; attiva: T; onCambia: (v: T) => void }) {
  return (
    <div className="mb-5 flex gap-1 overflow-x-auto rounded-2xl border border-bordo bg-pannello p-1" role="tablist">
      {voci.map((v) => (
        <button key={v.id} role="tab" aria-selected={attiva === v.id} onClick={() => onCambia(v.id)}
          className={clsx("btn shrink-0 px-3 py-1.5", attiva === v.id ? "gradiente text-white shadow" : "text-tenue hover:bg-fondo hover:text-testo")}>
          {v.etichetta}
        </button>
      ))}
    </div>
  );
}

export function Kpi({ etichetta, valore, nota, icona: Icona, tono = "primario", href }: {
  etichetta: string; valore: React.ReactNode; nota?: React.ReactNode; icona?: React.ComponentType<{ className?: string }>; tono?: "primario" | "ok" | "attenzione" | "errore" | "viola" | "ciano"; href?: string;
}) {
  const colore = { primario: "text-primario bg-primario/10", ok: "text-ok bg-ok/10", attenzione: "text-attenzione bg-attenzione/10", errore: "text-errore bg-errore/10", viola: "text-viola bg-viola/10", ciano: "text-ciano bg-ciano/10" }[tono];
  const corpo = (
    <div className="scheda flex h-full items-start gap-3 p-4">
      {Icona && <span className={clsx("flex h-10 w-10 shrink-0 items-center justify-center rounded-xl", colore)}><Icona className="h-5 w-5" aria-hidden /></span>}
      <div className="min-w-0">
        <p className="text-xs font-medium uppercase tracking-wide text-tenue">{etichetta}</p>
        <p className="mt-0.5 truncate text-xl font-bold tabular-nums">{valore}</p>
        {nota && <div className="mt-0.5 text-xs text-tenue">{nota}</div>}
      </div>
    </div>
  );
  return href ? <a href={href} className="block transition hover:-translate-y-0.5">{corpo}</a> : corpo;
}

export function Interruttore({ attivo, onChange, etichetta, descrizione, disabilitato }: { attivo: boolean; onChange: (v: boolean) => void; etichetta: string; descrizione?: string; disabilitato?: boolean }) {
  return (
    <label className={clsx("flex items-start gap-3", disabilitato ? "cursor-not-allowed opacity-60" : "cursor-pointer")}>
      <input type="checkbox" className="peer sr-only" checked={attivo} disabled={disabilitato} onChange={(e) => onChange(e.target.checked)} />
      <span aria-hidden className={clsx("mt-0.5 inline-flex h-6 w-11 shrink-0 items-center rounded-full p-0.5 transition peer-focus-visible:ring-2 peer-focus-visible:ring-primario", attivo ? "gradiente" : "bg-bordo")}>
        <span className={clsx("h-5 w-5 rounded-full bg-white shadow transition", attivo && "translate-x-5")} />
      </span>
      <span>
        <span className="font-medium">{etichetta}</span>
        {descrizione && <span className="block text-sm text-tenue">{descrizione}</span>}
      </span>
    </label>
  );
}

export function Campo({ etichetta, children, aiuto, className }: { etichetta: string; children: React.ReactNode; aiuto?: string; className?: string }) {
  return (
    <label className={clsx("block text-sm", className)}>
      <span className="etichetta">{etichetta}</span>
      {children}
      {aiuto && <span className="mt-1 block text-xs text-tenue">{aiuto}</span>}
    </label>
  );
}

// ── Stati colorati ─────────────────────────────────────────────────────────

const STATI: Record<string, [string, string]> = {
  // ordini di vendita
  bozza: ["Bozza", "bg-bordo text-tenue"], confermato: ["Confermato", "bg-primario/15 text-primario"], in_preparazione: ["In preparazione", "bg-attenzione/15 text-attenzione"],
  spedito: ["Spedito", "bg-ciano/15 text-ciano"], consegnato: ["Consegnato", "bg-ok/15 text-ok"], annullato: ["Annullato", "bg-errore/15 text-errore"],
  // acquisti
  inviato: ["Inviato", "bg-primario/15 text-primario"], parziale: ["Ricevuto in parte", "bg-attenzione/15 text-attenzione"], ricevuto: ["Ricevuto", "bg-ok/15 text-ok"],
  // documenti
  emessa: ["Emessa", "bg-primario/15 text-primario"], emesso: ["Emesso", "bg-primario/15 text-primario"], pagata: ["Pagata", "bg-ok/15 text-ok"], annullata: ["Annullata", "bg-errore/15 text-errore"],
  accettato: ["Accettato", "bg-ok/15 text-ok"], rifiutato: ["Rifiutato", "bg-errore/15 text-errore"],
  // inventari e spedizioni
  aperto: ["Aperto", "bg-attenzione/15 text-attenzione"], chiuso: ["Chiuso", "bg-ok/15 text-ok"], creata: ["Creata", "bg-bordo text-tenue"],
  in_transito: ["In transito", "bg-ciano/15 text-ciano"], consegnata: ["Consegnata", "bg-ok/15 text-ok"],
};
const STATI_SDI: Record<string, [string, string]> = {
  da_generare: ["SdI: da generare", "bg-bordo text-tenue"], generato: ["SdI: XML pronto", "bg-attenzione/15 text-attenzione"], inviato: ["SdI: inviata", "bg-primario/15 text-primario"],
  consegnato: ["SdI: consegnata", "bg-ok/15 text-ok"], scartato: ["SdI: scartata", "bg-errore/15 text-errore"],
};

export function Stato({ stato, sdi }: { stato: string; sdi?: boolean }) {
  const [nome, c] = (sdi ? STATI_SDI : STATI)[stato] || [stato, "bg-bordo text-tenue"];
  return <span className={clsx("pillola", c)}>{nome}</span>;
}

export const NOMI_TIPO_DOC: Record<string, string> = { preventivo: "Preventivo", ddt: "DDT", fattura: "Fattura", nota_credito: "Nota di credito" };
export const NOMI_CANALE: Record<string, string> = { diretto: "Diretto", shopify: "Shopify", woocommerce: "WooCommerce", magento: "Magento" };
export const NOMI_MOVIMENTO: Record<string, string> = {
  carico: "Carico", scarico: "Scarico", trasferimento: "Trasferimento", rettifica: "Rettifica", vendita: "Vendita", acquisto: "Acquisto", reso: "Reso",
};
export const NOMI_RUOLO: Record<string, string> = { admin: "Amministratore", vendite: "Vendite", magazzino: "Magazzino", contabilita: "Contabilità", lettura: "Sola lettura" };

export function Canale({ canale }: { canale: string }) {
  const c = { shopify: "bg-ok/15 text-ok", woocommerce: "bg-viola/15 text-viola", magento: "bg-attenzione/15 text-attenzione" }[canale] || "bg-bordo text-tenue";
  return <span className={clsx("pillola", c)}>{NOMI_CANALE[canale] || canale}</span>;
}

const COLORE_SEGMENTO: Record<string, string> = {
  Campioni: "bg-ok/15 text-ok", Fedeli: "bg-primario/15 text-primario", Promettenti: "bg-ciano/15 text-ciano", Nuovi: "bg-viola/15 text-viola",
  "A rischio": "bg-errore/15 text-errore", Dormienti: "bg-attenzione/15 text-attenzione", "Da coltivare": "bg-bordo text-tenue",
};
export function Segmento({ nome }: { nome: string }) { return <span className={`pillola ${COLORE_SEGMENTO[nome] ?? "bg-bordo"}`}>{nome}</span>; }

export function Gravita({ gravita }: { gravita: string }) {
  const [n, c] = ({ critico: ["Critico", "bg-errore/15 text-errore"], attenzione: ["Attenzione", "bg-attenzione/15 text-attenzione"], info: ["Info", "bg-primario/15 text-primario"] } as Record<string, [string, string]>)[gravita] || [gravita, "bg-bordo"];
  return <span className={clsx("pillola", c)}>{n}</span>;
}

// ── Tema e copyright ────────────────────────────────────────────────────────

export function CambiaTema({ chiaro }: { chiaro?: boolean }) {
  const [scuro, setScuro] = useState(false);
  useEffect(() => { setScuro(document.documentElement.classList.contains("dark")); }, []);
  const alterna = () => {
    const nuovo = !scuro;
    document.documentElement.classList.toggle("dark", nuovo);
    try { localStorage.setItem("nx_tema", nuovo ? "scuro" : "chiaro"); } catch {}
    setScuro(nuovo);
  };
  return (
    <button onClick={alterna} className={clsx("btn px-3", chiaro ? "border border-white/20 text-white hover:bg-white/10" : "btn-secondario")} aria-label={scuro ? "Passa al tema chiaro" : "Passa al tema scuro"} title="Tema chiaro / scuro">
      {scuro ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
    </button>
  );
}

export function Copyright({ className }: { className?: string }) {
  return <p className={clsx("text-xs", className ?? "text-tenue")}>© {new Date().getFullYear()} Roberto Di Flumeri · Nexora</p>;
}

// ── Hook ────────────────────────────────────────────────────────────────────

/** Stato di invio ed errore di un'azione asincrona, senza doppi invii. */
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

/** Carica dati da un endpoint e li ricarica quando cambia il percorso. */
export function useDati<T = any>(percorso: string | null) {
  const [dati, setDati] = useState<T | null>(null);
  const [errore, setErrore] = useState<string | null>(null);
  const [caricamento, setCaricamento] = useState(!!percorso);
  // solo l'ultima richiesta aggiorna lo stato: una risposta lenta di un filtro precedente non sovrascrive quella nuova
  const ultima = useRef(0);
  const ricarica = useCallback(async () => {
    if (!percorso) return;
    const n = ++ultima.current;
    setCaricamento(true);
    try {
      const r = await api<T>(percorso);
      if (n !== ultima.current) return;
      setDati(r);
      setErrore(null);
    } catch (e) {
      if (n === ultima.current) setErrore(e instanceof Error ? e.message : "Errore di caricamento");
    } finally {
      if (n === ultima.current) setCaricamento(false);
    }
  }, [percorso]);
  useEffect(() => { ricarica(); }, [ricarica]);
  return { dati, errore, caricamento, ricarica, setDati };
}

/** Mostra i primi N elementi di una lista lunga, con «mostra altri». */
export function useLimite<T>(lista: T[] | null | undefined, passo = 50) {
  const [n, setN] = useState(passo);
  const visibili = (lista ?? []).slice(0, n);
  const altri = (lista?.length ?? 0) - visibili.length;
  const Altri = () => (altri > 0 ? (
    <div className="mt-3 text-center"><button className="btn-secondario" onClick={() => setN(n + passo)}>Mostra altri {Math.min(passo, altri)} (di {altri})</button></div>
  ) : null);
  return { visibili, Altri };
}

export function Errore({ messaggio, riprova }: { messaggio: string; riprova?: () => void }) {
  return (
    <Avviso tipo="errore">
      {messaggio} {riprova && <button className="link ml-1 font-medium" onClick={riprova}>Riprova</button>}
    </Avviso>
  );
}

export function Miniatura({ file, nome, grande }: { file: string | null; nome: string; grande?: boolean }) {
  const c = grande ? "h-16 w-16" : "h-10 w-10";
  return file
    // eslint-disable-next-line @next/next/no-img-element
    ? <img src={`/api/file/${file}`} alt="" className={`${c} shrink-0 rounded-xl object-cover`} loading="lazy" />
    : <span className={`${c} flex shrink-0 items-center justify-center rounded-xl gradiente text-sm font-bold text-white`} aria-hidden>{nome.slice(0, 1)}</span>;
}
