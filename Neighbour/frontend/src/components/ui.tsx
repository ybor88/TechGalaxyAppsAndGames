"use client";
// Componenti UI condivisi — © Roberto Di Flumeri

import clsx from "clsx";
import {
  AlertTriangle, BadgeCheck, CalendarDays, CheckCircle2, Flag, Gift, HandHeart, Info, Loader2, Megaphone, Moon,
  Repeat, Search, ShoppingBag, Sun, X,
} from "lucide-react";
import { useEffect, useState } from "react";
import { api, type UtentePubblico } from "@/lib/api";
import { useCatalogo } from "@/lib/catalogo";

export function Titolo({ titolo, sottotitolo, azioni }: { titolo: string; sottotitolo?: string; azioni?: React.ReactNode }) {
  return (
    <header className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div className="min-w-0">
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
    info: "border-verde/30 bg-verde/10",
    ok: "border-ok/30 bg-ok/10",
    attenzione: "border-attenzione/40 bg-attenzione/10",
    errore: "border-errore/40 bg-errore/10",
  }[tipo];
  return (
    <div role={tipo === "errore" ? "alert" : "status"} className={clsx("flex gap-2 rounded-xl border p-3 text-sm", colori)}>
      <Icona className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
      <div className="min-w-0">{children}</div>
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

export function Vuoto({ icona: Icona = Search, titolo, testo, azione }: {
  icona?: React.ComponentType<{ className?: string }>; titolo: string; testo?: string; azione?: React.ReactNode;
}) {
  return (
    <div className="scheda flex flex-col items-center gap-2 py-10 text-center">
      <Icona className="h-10 w-10 text-prato" aria-hidden />
      <p className="font-semibold">{titolo}</p>
      {testo && <p className="max-w-md text-sm text-tenue">{testo}</p>}
      {azione && <div className="mt-2">{azione}</div>}
    </div>
  );
}

export const ICONE_TIPO: Record<string, React.ComponentType<{ className?: string }>> = {
  offerta: ShoppingBag, richiesta: Search, regalo: Gift, scambio: Repeat, aiuto: HandHeart,
  segnalazione: Megaphone, evento: CalendarDays, gruppo: Megaphone,
};

const COLORE_TIPO: Record<string, string> = {
  offerta: "bg-verde/10 text-verde", richiesta: "bg-verde/10 text-verde", regalo: "bg-prato/15 text-verde",
  scambio: "bg-prato/15 text-verde", aiuto: "bg-arancio/15 text-arancio", segnalazione: "bg-attenzione/15 text-attenzione",
  evento: "bg-verde/15 text-verde",
};

export function EtichettaTipo({ tipo }: { tipo: string }) {
  const c = useCatalogo();
  const Icona = ICONE_TIPO[tipo] || Info;
  const nome = tipo === "evento" ? "Evento" : c?.tipi_post[tipo] || tipo;
  return <span className={clsx("pillola", COLORE_TIPO[tipo])}><Icona className="h-3 w-3" aria-hidden /> {nome}</span>;
}

const STATI: Record<string, [string, string]> = {
  bozza: ["Bozza", "bg-bordo text-tenue"], pubblicato: ["Disponibile", "bg-ok/15 text-ok"],
  prenotato: ["Prenotato", "bg-attenzione/15 text-attenzione"], chiuso: ["Concluso", "bg-bordo text-tenue"],
  risolto: ["Risolto", "bg-ok/15 text-ok"], rimosso: ["Rimosso dalla moderazione", "bg-errore/15 text-errore"],
  annullato: ["Annullato", "bg-errore/15 text-errore"],
};

export function EtichettaStato({ stato }: { stato: string }) {
  const [nome, c] = STATI[stato] || [stato, "bg-bordo"];
  return <span className={clsx("pillola", c)}>{nome}</span>;
}

export function Categoria({ tipo, nome }: { tipo: string; nome: string }) {
  const c = useCatalogo();
  const gruppo = ["offerta", "richiesta", "regalo", "scambio"].includes(tipo) ? "mercatino" : tipo;
  const etichetta = (c?.categorie as Record<string, Record<string, string>> | undefined)?.[gruppo]?.[nome] || nome;
  return <span className="pillola bg-fondo text-tenue">{etichetta}</span>;
}

export function Avatar({ alias, grande }: { alias: string; grande?: boolean }) {
  const iniziali = alias.split(/\s+/).map((p) => p[0]).join("").slice(0, 2).toUpperCase();
  return (
    <span aria-hidden className={clsx("inline-flex shrink-0 items-center justify-center rounded-full gradiente font-semibold text-white",
      grande ? "h-14 w-14 text-lg" : "h-8 w-8 text-xs")}>{iniziali}</span>
  );
}

export function NomeUtente({ u }: { u: UtentePubblico }) {
  return (
    <span className="inline-flex flex-wrap items-center gap-1">
      <span className="font-medium">{u.alias}</span>
      {u.tipo_profilo === "professionale" && (
        <span className="pillola bg-arancio/15 text-arancio" title="Profilo professionale"><BadgeCheck className="h-3 w-3" /> Professionista</span>
      )}
    </span>
  );
}

export function Interruttore({ attivo, onChange, etichetta, descrizione, disabilitato }: {
  attivo: boolean; onChange: (v: boolean) => void; etichetta: string; descrizione?: string; disabilitato?: boolean;
}) {
  return (
    <label className={clsx("flex items-start gap-3", disabilitato ? "cursor-not-allowed opacity-70" : "cursor-pointer")}>
      <input type="checkbox" className="peer sr-only" checked={attivo} disabled={disabilitato} onChange={(e) => onChange(e.target.checked)} />
      <span aria-hidden className={clsx("mt-0.5 inline-flex h-6 w-11 shrink-0 items-center rounded-full p-0.5 transition peer-focus-visible:ring-2 peer-focus-visible:ring-verde",
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

export function Raggio({ valore, onChange, etichetta = "Raggio" }: { valore: number; onChange: (v: number) => void; etichetta?: string }) {
  return (
    <label className="block text-sm">
      <span className="flex justify-between"><span className="font-medium">{etichetta}</span>
        <span className="text-tenue">{valore >= 1000 ? `${(valore / 1000).toFixed(1).replace(".", ",")} km` : `${valore} m`}</span></span>
      <input type="range" min={300} max={5000} step={100} value={valore} onChange={(e) => onChange(Number(e.target.value))}
        className="mt-2 w-full accent-[rgb(var(--verde))]" aria-label={etichetta} />
    </label>
  );
}

export function Modale({ titolo, onChiudi, children }: { titolo: string; onChiudi: () => void; children: React.ReactNode }) {
  useEffect(() => {
    const esc = (e: KeyboardEvent) => e.key === "Escape" && onChiudi();
    document.addEventListener("keydown", esc);
    return () => document.removeEventListener("keydown", esc);
  }, [onChiudi]);
  return (
    <div className="fixed inset-0 z-[1000] flex items-end justify-center bg-black/50 p-0 sm:items-center sm:p-4" role="dialog"
      aria-modal="true" aria-label={titolo} onClick={onChiudi}>
      <div className="scheda max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-b-none sm:rounded-2xl" onClick={(e) => e.stopPropagation()}>
        <div className="mb-3 flex items-start justify-between gap-2">
          <h2 className="text-lg font-semibold">{titolo}</h2>
          <button className="btn-secondario px-2" onClick={onChiudi} aria-label="Chiudi"><X className="h-4 w-4" /></button>
        </div>
        {children}
      </div>
    </div>
  );
}

/** Segnalazione con motivo codificato e blocco immediato opzionale (F05, §8). */
export function Segnala({ oggettoTipo, oggettoId, alias, onChiudi, onFatto }: {
  oggettoTipo: string; oggettoId: string; alias?: string; onChiudi: () => void; onFatto?: (bloccato: boolean) => void;
}) {
  const c = useCatalogo();
  const [motivo, setMotivo] = useState("");
  const [dettagli, setDettagli] = useState("");
  const [blocca, setBlocca] = useState(oggettoTipo === "messaggio" || oggettoTipo === "utente");
  const [esito, setEsito] = useState<string | null>(null);
  const { inCorso, errore, esegui } = useAzione();
  const invia = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => {
      const r = await api<{ messaggio: string }>("/v1/reports", { metodo: "POST", corpo: {
        oggetto_tipo: oggettoTipo, oggetto_id: oggettoId, motivo, dettagli: dettagli || null, blocca_utente: blocca } });
      setEsito(r.messaggio);
      onFatto?.(blocca);
    });
  };
  return (
    <Modale titolo="Segnala alla moderazione" onChiudi={onChiudi}>
      {esito ? (
        <div className="space-y-3"><Avviso tipo="ok">{esito}</Avviso><button className="btn-primario w-full" onClick={onChiudi}>Chiudi</button></div>
      ) : (
        <form onSubmit={invia} className="space-y-3">
          <fieldset className="space-y-2">
            <legend className="etichetta">Motivo</legend>
            {Object.entries(c?.motivi_segnalazione || {}).map(([k, v]) => (
              <label key={k} className="flex cursor-pointer items-center gap-2 rounded-xl border border-bordo p-2 text-sm">
                <input type="radio" name="motivo" required value={k} checked={motivo === k} onChange={() => setMotivo(k)} /> {v}
              </label>
            ))}
          </fieldset>
          <label className="block text-sm">Dettagli (facoltativi)
            <textarea className="campo mt-1" rows={2} maxLength={1000} value={dettagli} onChange={(e) => setDettagli(e.target.value)} />
          </label>
          {alias && <Interruttore attivo={blocca} onChange={setBlocca} etichetta={`Blocca anche ${alias}`}
            descrizione="Non vedrete più i rispettivi contenuti e non potrete scrivervi." />}
          {errore && <Avviso tipo="errore">{errore}</Avviso>}
          <button className="btn-pericolo w-full" disabled={inCorso || !motivo}><Flag className="h-4 w-4" /> Invia segnalazione</button>
        </form>
      )}
    </Modale>
  );
}

export function CambiaTema() {
  const [scuro, setScuro] = useState(false);
  useEffect(() => { setScuro(document.documentElement.classList.contains("dark")); }, []);
  const alterna = () => {
    const nuovo = !scuro;
    document.documentElement.classList.toggle("dark", nuovo);
    try {
      localStorage.setItem("nb_tema", nuovo ? "scuro" : "chiaro");
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
  return <p className="text-xs text-tenue">© {new Date().getFullYear()} Roberto Di Flumeri · Neighbour</p>;
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
