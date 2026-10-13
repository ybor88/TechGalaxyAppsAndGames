"use client";
// Moderazione (solo moderatori): segnalazioni, audit, metriche — © Roberto Di Flumeri
import { useEffect, useState } from "react";
import { Avviso, Caricamento, Titolo, useAzione } from "@/components/ui";
import { api, dataOraIt } from "@/lib/api";

interface Segnalazione {
  id: string; share_id: string | null; utente_segnalato_id: string | null; motivo: string; dettagli: string | null;
  stato: string; azione: string | null; created_at: string;
}
interface Metriche {
  utenti: Record<string, number>; uso_funzionalita_con_consenso: Record<string, number>; segnalazioni: Record<string, number>;
  api: { richieste: number; tasso_errori_5xx: number; errori_4xx: number; latenza_p95_ms: Record<string, number> };
}

export default function Moderazione() {
  const [segnalazioni, setSegnalazioni] = useState<Segnalazione[] | null>(null);
  const [audit, setAudit] = useState<{ azione: string; oggetto: string | null; timestamp: string }[]>([]);
  const [metriche, setMetriche] = useState<Metriche | null>(null);
  const { errore, esegui, inCorso } = useAzione();

  const carica = () => esegui(async () => {
    const [s, a, m] = await Promise.all([
      api<Segnalazione[]>("/v1/admin/reports"), api<typeof audit>("/v1/admin/audit"), api<Metriche>("/v1/admin/metriche"),
    ]);
    setSegnalazioni(s); setAudit(a); setMetriche(m);
  });
  useEffect(() => { carica(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const risolvi = (id: string, azione: string) => {
    const nota = prompt("Nota per l'audit (facoltativa)") ?? undefined;
    esegui(async () => { await api(`/v1/admin/reports/${id}/risolvi`, { metodo: "POST", corpo: { azione, nota } }); await carica(); });
  };

  if (errore && !segnalazioni) return <Avviso tipo="errore">{errore}</Avviso>;
  if (!segnalazioni || !metriche) return <Caricamento />;

  return (
    <>
      <Titolo titolo="Moderazione" sottotitolo="Gestione abusi e metriche aggregate. I contenuti personali degli utenti non sono visibili." />
      <section className="mb-6 grid gap-4 sm:grid-cols-4">
        <div className="scheda"><div className="text-sm text-tenue">Utenti attivi</div><div className="text-2xl font-bold">{metriche.utenti.attivo ?? 0}</div></div>
        <div className="scheda"><div className="text-sm text-tenue">Utenti bloccati</div><div className="text-2xl font-bold">{metriche.utenti.bloccato ?? 0}</div></div>
        <div className="scheda"><div className="text-sm text-tenue">Segnalazioni aperte</div><div className="text-2xl font-bold">{metriche.segnalazioni.aperta ?? 0}</div></div>
        <div className="scheda"><div className="text-sm text-tenue">Errori 5xx</div><div className="text-2xl font-bold">{(metriche.api.tasso_errori_5xx * 100).toFixed(2)}%</div></div>
      </section>

      <section className="scheda mb-6">
        <h2 className="mb-3 font-semibold">Segnalazioni</h2>
        {segnalazioni.length === 0 ? <p className="text-tenue">Nessuna segnalazione.</p> : (
          <ul className="space-y-3">
            {segnalazioni.map((s) => (
              <li key={s.id} className="rounded-xl border border-bordo p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-medium">{s.motivo.replace("_", " ")}</span>
                  <span className="text-xs text-tenue">{dataOraIt(s.created_at)} · {s.stato}{s.azione ? ` (${s.azione})` : ""}</span>
                </div>
                {s.dettagli && <p className="mt-1 text-sm">{s.dettagli}</p>}
                {s.stato === "aperta" && (
                  <div className="mt-2 flex flex-wrap gap-2">
                    <button className="btn-secondario text-sm" disabled={inCorso} onClick={() => risolvi(s.id, "nessuna")}>Archivia</button>
                    <button className="btn-secondario text-sm" disabled={inCorso} onClick={() => risolvi(s.id, "revoca_condivisione")}>Sospendi il link</button>
                    <button className="btn-pericolo text-sm" disabled={inCorso}
                      onClick={() => confirm("Bloccare l'account segnalato?") && risolvi(s.id, "blocca_utente")}>Blocca utente</button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="scheda">
          <h2 className="mb-3 font-semibold">Latenza p95 per API (ms)</h2>
          <ul className="max-h-72 space-y-1 overflow-y-auto font-mono text-xs">
            {Object.entries(metriche.api.latenza_p95_ms).map(([r, ms]) => (
              <li key={r} className="flex justify-between gap-2"><span className="truncate">{r}</span><span>{ms}</span></li>
            ))}
          </ul>
        </section>
        <section className="scheda">
          <h2 className="mb-3 font-semibold">Registro audit</h2>
          <ul className="max-h-72 space-y-1 overflow-y-auto text-xs">
            {audit.map((a, i) => <li key={i}>{dataOraIt(a.timestamp)} · {a.azione}{a.oggetto ? ` · ${a.oggetto.slice(0, 8)}` : ""}</li>)}
          </ul>
        </section>
      </div>
    </>
  );
}
