"use client";
// Test di compatibilità consensuale (F05) — © Roberto Di Flumeri
import clsx from "clsx";
import { Copy, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import { Avviso, Caricamento, Titolo, useAzione } from "@/components/ui";
import { api, CATEGORIE, dataIt } from "@/lib/api";

interface CategoriaRisultato {
  categoria: string; etichetta: string; descrizione: string;
  punti_in_comune: { tuo: string; altro: string }[]; dichiarazioni_confrontate: { proprietario: number; invitato: number };
}
interface Invito {
  id: string; ruolo: "proprietario" | "invitato"; categorie: string[]; scadenza: string; accettato: boolean;
  con: string | null; risultato: { criteri: string; categorie: CategoriaRisultato[] } | null; created_at: string;
}

export default function Compatibilita() {
  const [inviti, setInviti] = useState<Invito[] | null>(null);
  const [categorie, setCategorie] = useState<string[]>(["valori", "gusti"]);
  const [codiceCreato, setCodiceCreato] = useState<string | null>(null);
  const [codice, setCodice] = useState("");
  const { inCorso, errore, esegui } = useAzione();

  const carica = () => api<Invito[]>("/v1/compatibility").then(setInviti).catch((e) => { setInviti([]); throw e; });
  useEffect(() => { esegui(carica); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const crea = () => esegui(async () => {
    const r = await api<{ codice: string }>("/v1/compatibility/inviti", { metodo: "POST", corpo: { categorie, durata_giorni: 7 } });
    setCodiceCreato(r.codice);
    await carica();
  });

  const accetta = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => { await api("/v1/compatibility/accetta", { metodo: "POST", corpo: { codice } }); setCodice(""); await carica(); });
  };

  return (
    <>
      <Titolo titolo="Test di compatibilità" sottotitolo="Confronto consensuale tra due profili, con criteri visibili. Non è un giudizio sulla relazione." />
      <Avviso tipo="info">
        Vengono confrontate solo le dichiarazioni che entrambi avete segnato come <strong>condivisibili</strong>, nelle categorie scelte.
        Accettando l&apos;invito, l&apos;altra persona vedrà i punti in comune che coinvolgono le tue dichiarazioni condivisibili.
      </Avviso>

      <div className="my-6 grid gap-4 md:grid-cols-2">
        <section className="scheda space-y-3">
          <h2 className="font-semibold">Invita qualcuno</h2>
          <div className="flex flex-wrap gap-2" role="group" aria-label="Categorie da confrontare">
            {Object.entries(CATEGORIE).map(([k, v]) => (
              <button key={k} type="button" aria-pressed={categorie.includes(k)}
                onClick={() => setCategorie(categorie.includes(k) ? categorie.filter((x) => x !== k) : [...categorie, k])}
                className={clsx("pillola border px-3 py-1", categorie.includes(k) ? "gradiente text-white" : "border-bordo")}>{v}</button>
            ))}
          </div>
          <button className="btn-primario" onClick={crea} disabled={inCorso || !categorie.length}>Genera codice invito</button>
          {codiceCreato && (
            <Avviso tipo="ok">
              Codice (valido 7 giorni, mostrato una sola volta): <strong className="font-mono text-lg">{codiceCreato}</strong>{" "}
              <button className="ml-1 underline" onClick={() => navigator.clipboard.writeText(codiceCreato)}><Copy className="inline h-3 w-3" /> copia</button>
            </Avviso>
          )}
        </section>
        <form onSubmit={accetta} className="scheda space-y-3">
          <h2 className="font-semibold">Ho ricevuto un codice</h2>
          <label htmlFor="codice" className="sr-only">Codice invito</label>
          <input id="codice" className="campo font-mono uppercase" required minLength={6} maxLength={40} placeholder="Es. A1B2C3D4E5"
            value={codice} onChange={(e) => setCodice(e.target.value)} />
          <button className="btn-primario" disabled={inCorso}>Accetta e confronta</button>
        </form>
      </div>
      {errore && <div className="mb-4"><Avviso tipo="errore">{errore}</Avviso></div>}

      {!inviti ? <Caricamento /> : inviti.length === 0 ? <p className="text-tenue">Nessun confronto ancora.</p> : (
        <ul className="space-y-4">
          {inviti.map((inv) => (
            <li key={inv.id} className="scheda">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <div className="font-semibold">{inv.accettato ? `Confronto con ${inv.con ?? "un utente"}` : "Invito in attesa"}</div>
                  <div className="text-sm text-tenue">{inv.categorie.map((c) => CATEGORIE[c]).join(", ")} · creato il {dataIt(inv.created_at)}
                    {!inv.accettato && ` · scade il ${dataIt(inv.scadenza)}`}</div>
                </div>
                <button className="rounded p-1 text-tenue hover:text-errore" aria-label="Elimina confronto"
                  onClick={() => confirm("Eliminare questo confronto per entrambi?") && esegui(async () => { await api(`/v1/compatibility/${inv.id}`, { metodo: "DELETE" }); await carica(); })}>
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
              {inv.risultato && (
                <div className="mt-3 space-y-3">
                  {inv.risultato.categorie.map((c) => (
                    <div key={c.categoria} className="rounded-xl bg-fondo p-3">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className="font-medium">{c.etichetta}</span>
                        <span className="pillola bg-viola/15 text-viola">{c.descrizione}</span>
                      </div>
                      {c.punti_in_comune.length > 0 && (
                        <ul className="mt-2 space-y-1 text-sm">
                          {c.punti_in_comune.map((p, i) => (
                            <li key={i}>
                              «{inv.ruolo === "proprietario" ? p.tuo : p.altro}» ↔ «{inv.ruolo === "proprietario" ? p.altro : p.tuo}»
                            </li>
                          ))}
                        </ul>
                      )}
                      <p className="mt-1 text-xs text-tenue">Dichiarazioni confrontate: {c.dichiarazioni_confrontate.proprietario} e {c.dichiarazioni_confrontate.invitato}</p>
                    </div>
                  ))}
                  <p className="text-xs text-tenue">{inv.risultato.criteri}</p>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
