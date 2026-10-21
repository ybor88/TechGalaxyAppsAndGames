"use client";
// Pannello di moderazione: segnalazioni, ricorsi, recidivi, dashboard qualità e audit — © Roberto Di Flumeri
import clsx from "clsx";
import { AlertTriangle, Gauge, Scale, ShieldAlert } from "lucide-react";
import { useEffect, useState } from "react";
import { Avviso, Caricamento, Titolo, useAzione, Vuoto } from "@/components/ui";
import { api, dataOraIt, fa, type UtentePubblico } from "@/lib/api";
import { useCatalogo } from "@/lib/catalogo";
import { useSessione } from "@/lib/sessione";

interface Report {
  id: string; oggetto_tipo: string; oggetto_id: string; motivo_testo: string; dettagli: string | null; stato: string;
  decisione: string | null; decisione_testo: string | null; ricorso: string | null; created_at: string;
  evidenze: Record<string, string> | null; segnalato: UtentePubblico | null; stato_account?: string;
  violazioni_precedenti?: number; segnalazioni_stesso_contenuto?: number; nota_decisione: string | null;
  motivi?: string[]; commenti?: string[];
}
interface Metriche {
  utenti: Record<string, number>;
  contenuti: Record<string, number>;
  fiducia: { segnalazioni: Record<string, number>; tasso_risoluzione: number | null; ore_medie_risoluzione: number | null;
    blocchi_tra_utenti: number; ricorsi_aperti: number; richieste_privacy: Record<string, number> };
  api: { richieste: number; tasso_errori_5xx: number; latenza_p95_ms: Record<string, number> };
}
const SCHEDE = [["coda", "Segnalazioni"], ["ricorsi", "Ricorsi"], ["recidivi", "Recidivi"], ["qualita", "Qualità"], ["audit", "Audit"]] as const;

function Decisione({ r, onFatto }: { r: Report; onFatto: () => void }) {
  const catalogo = useCatalogo();
  const [decisione, setDecisione] = useState("");
  const [nota, setNota] = useState("");
  const { inCorso, errore, esegui } = useAzione();
  return (
    <form className="space-y-2 border-t border-bordo pt-3" onSubmit={(e) => { e.preventDefault();
      esegui(async () => { await api(`/v1/admin/reports/${r.id}/decidi`, { metodo: "POST", corpo: { decisione, nota: nota || null } }); onFatto(); }); }}>
      <div className="flex flex-wrap gap-2">
        {Object.entries(catalogo?.decisioni_moderazione || {}).map(([k, v]) => (
          <label key={k} className={clsx("cursor-pointer rounded-xl border px-3 py-1 text-sm", decisione === k ? "border-verde bg-verde/10 font-semibold" : "border-bordo")}>
            <input type="radio" name={`d-${r.id}`} className="sr-only" checked={decisione === k} onChange={() => setDecisione(k)} required />{v}
          </label>
        ))}
      </div>
      <input className="campo" maxLength={500} placeholder="Motivazione (visibile all'utente)" value={nota} onChange={(e) => setNota(e.target.value)} aria-label="Motivazione" />
      {errore && <Avviso tipo="errore">{errore}</Avviso>}
      <button className="btn-primario text-sm" disabled={inCorso || !decisione}>Registra decisione</button>
    </form>
  );
}

function SchedaReport({ r, children }: { r: Report; children?: React.ReactNode }) {
  return (
    <li className="scheda space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        {(r.motivi?.length ? r.motivi : [r.motivo_testo]).map((m) => <span key={m} className="pillola bg-errore/15 text-errore">{m}</span>)}
        <span className="pillola bg-fondo text-tenue">{r.oggetto_tipo}</span>
        {(r.segnalazioni_stesso_contenuto || 0) > 1 && <span className="pillola bg-attenzione/15 text-attenzione">{r.segnalazioni_stesso_contenuto} vicini hanno segnalato</span>}
        <span className="ml-auto text-xs text-tenue">{fa(r.created_at)}</span>
      </div>
      <div className="rounded-xl bg-fondo p-3 text-sm">
        <p className="font-semibold">{r.evidenze?.titolo}</p>
        <p className="whitespace-pre-wrap">{r.evidenze?.testo}</p>
      </div>
      {(r.commenti?.length ? r.commenti : r.dettagli ? [r.dettagli] : []).map((d, i) => (
        <p key={i} className="text-sm"><span className="text-tenue">Chi segnala:</span> «{d}»</p>
      ))}
      {r.segnalato && (
        <p className="flex flex-wrap items-center gap-2 text-sm">
          <span className="text-tenue">Autore:</span> <strong>{r.segnalato.alias}</strong>
          {r.stato_account && r.stato_account !== "attivo" && <span className="pillola bg-errore/15 text-errore">{r.stato_account}</span>}
          {(r.violazioni_precedenti || 0) > 0 && <span className="pillola bg-attenzione/15 text-attenzione"><AlertTriangle className="h-3 w-3" /> {r.violazioni_precedenti} violazioni precedenti</span>}
        </p>
      )}
      {children}
    </li>
  );
}

export default function Moderazione() {
  const { utente } = useSessione();
  const [scheda, setScheda] = useState<(typeof SCHEDE)[number][0]>("coda");
  const [coda, setCoda] = useState<Report[] | null>(null);
  const [ricorsi, setRicorsi] = useState<Report[] | null>(null);
  const [recidivi, setRecidivi] = useState<(UtentePubblico & { violazioni: number; stato: string })[] | null>(null);
  const [metriche, setMetriche] = useState<Metriche | null>(null);
  const [audit, setAudit] = useState<{ azione: string; oggetto: string | null; dettagli: string | null; timestamp: string }[] | null>(null);
  const azione = useAzione();

  const carica = () => {
    api<Report[]>("/v1/admin/reports").then(setCoda).catch((e) => azione.setErrore(e.message));
    api<Report[]>("/v1/admin/ricorsi").then(setRicorsi).catch(() => undefined);
    api<(UtentePubblico & { violazioni: number; stato: string })[]>("/v1/admin/utenti-recidivi").then(setRecidivi).catch(() => undefined);
    api<Metriche>("/v1/admin/metriche").then(setMetriche).catch(() => undefined);
    api<typeof audit>("/v1/admin/audit").then(setAudit).catch(() => undefined);
  };
  useEffect(() => { if (utente?.ruolo === "moderatore") carica(); }, [utente?.ruolo]); // eslint-disable-line react-hooks/exhaustive-deps

  if (utente && utente.ruolo !== "moderatore") return <Avviso tipo="errore">Sezione riservata ai moderatori.</Avviso>;
  const ricorso = (r: Report, esito: string) => azione.esegui(async () => {
    await api(`/v1/admin/reports/${r.id}/ricorso`, { metodo: "POST", corpo: { esito } }); carica();
  });
  const sblocca = (id: string) => azione.esegui(async () => { await api(`/v1/admin/utenti/${id}/sblocca`, { metodo: "POST" }); carica(); });

  return (
    <>
      <Titolo titolo="Moderazione" sottotitolo="Ogni decisione è motivata, notificata e registrata. I ricorsi li esamina un moderatore diverso." />
      <div className="-mx-4 mb-4 flex gap-2 overflow-x-auto px-4 pb-1" role="tablist">
        {SCHEDE.map(([id, nome]) => (
          <button key={id} role="tab" aria-selected={scheda === id} onClick={() => setScheda(id)}
            className={clsx("filtro shrink-0", scheda === id ? "gradiente border-transparent text-white" : "border-bordo bg-pannello")}>
            {nome}{id === "coda" && coda ? ` (${coda.length})` : id === "ricorsi" && ricorsi ? ` (${ricorsi.length})` : ""}
          </button>
        ))}
      </div>
      {azione.errore && <div className="mb-3"><Avviso tipo="errore">{azione.errore}</Avviso></div>}

      {scheda === "coda" && (!coda ? <Caricamento /> : coda.length === 0 ? <Vuoto icona={ShieldAlert} titolo="Nessuna segnalazione aperta" /> : (
        <ul className="space-y-3">{coda.map((r) => <SchedaReport key={r.id} r={r}><Decisione r={r} onFatto={carica} /></SchedaReport>)}</ul>
      ))}

      {scheda === "ricorsi" && (!ricorsi ? <Caricamento /> : ricorsi.length === 0 ? <Vuoto icona={Scale} titolo="Nessun ricorso da esaminare" /> : (
        <ul className="space-y-3">{ricorsi.map((r) => (
          <SchedaReport key={r.id} r={r}>
            <div className="rounded-xl border border-bordo p-3 text-sm">
              <p><span className="text-tenue">Decisione:</span> <strong>{r.decisione_testo}</strong>{r.nota_decisione ? ` — ${r.nota_decisione}` : ""}</p>
              <p className="mt-1"><span className="text-tenue">Ricorso:</span> «{r.ricorso}»</p>
            </div>
            <div className="flex gap-2">
              <button className="btn-primario text-sm" onClick={() => ricorso(r, "accolto")}>Accogli (annulla la decisione)</button>
              <button className="btn-secondario text-sm" onClick={() => ricorso(r, "respinto")}>Respingi</button>
            </div>
          </SchedaReport>
        ))}</ul>
      ))}

      {scheda === "recidivi" && (!recidivi ? <Caricamento /> : recidivi.length === 0 ? <Vuoto titolo="Nessun utente con più violazioni" /> : (
        <ul className="scheda divide-y divide-bordo p-0">{recidivi.map((u) => (
          <li key={u.id} className="flex items-center justify-between gap-2 p-4">
            <span><strong>{u.alias}</strong> <span className="text-sm text-tenue">· {u.violazioni} violazioni · {u.stato}</span></span>
            {u.stato !== "attivo" && u.id && <button className="btn-secondario text-sm" onClick={() => sblocca(u.id!)}>Riattiva</button>}
          </li>
        ))}</ul>
      ))}

      {scheda === "qualita" && (!metriche ? <Caricamento /> : (
        <div className="space-y-4">
          <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {[["Utenti attivi", metriche.utenti.attivo || 0], ["Pubblicati in 7 giorni", metriche.contenuti.pubblicati_7_giorni],
              ["Scambi conclusi", metriche.contenuti.scambi_conclusi], ["Eventi in programma", metriche.contenuti.eventi_futuri],
              ["Tasso di risoluzione", metriche.fiducia.tasso_risoluzione === null ? "—" : `${Math.round(metriche.fiducia.tasso_risoluzione * 100)}%`],
              ["Ore medie per decidere", metriche.fiducia.ore_medie_risoluzione ?? "—"], ["Blocchi tra utenti", metriche.fiducia.blocchi_tra_utenti],
              ["Errori server (5xx)", `${(metriche.api.tasso_errori_5xx * 100).toFixed(2)}%`]].map(([k, v]) => (
              <li key={k as string} className="scheda"><p className="text-sm text-tenue">{k}</p><p className="text-2xl font-bold">{v}</p></li>
            ))}
          </ul>
          <section className="scheda">
            <h2 className="mb-2 flex items-center gap-2 font-semibold"><Gauge className="h-4 w-4" /> Latenza p95 per API (ms) · obiettivo &lt; 500</h2>
            <ul className="grid gap-x-6 font-mono text-xs sm:grid-cols-2">
              {Object.entries(metriche.api.latenza_p95_ms).map(([r, ms]) => (
                <li key={r} className="flex justify-between border-b border-bordo py-1"><span className="truncate">{r}</span>
                  <span className={ms > 500 ? "font-bold text-errore" : ""}>{ms}</span></li>
              ))}
            </ul>
          </section>
        </div>
      ))}

      {scheda === "audit" && (!audit ? <Caricamento /> : (
        <ul className="scheda divide-y divide-bordo p-0 text-sm">{audit.map((a, i) => (
          <li key={i} className="flex flex-wrap justify-between gap-2 px-4 py-2"><span><strong>{a.azione}</strong>{a.dettagli ? ` · ${a.dettagli}` : ""}</span>
            <span className="text-xs text-tenue">{dataOraIt(a.timestamp)}</span></li>
        ))}</ul>
      ))}
    </>
  );
}
