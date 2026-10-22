"use client";
// Motore AI: previsione delle vendite (Holt-Winters con stagionalità settimanale), riordino automatico e analisi dei clienti (RFM). © Roberto Di Flumeri
import { BrainCircuit, PackagePlus, Sparkles, TrendingUp, Users } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { CercaArticolo } from "@/components/articoli";
import { GraficoPrevisione } from "@/components/grafici";
import { Avviso, Caricamento, Errore, Interruttore, Kpi, Schede, Segmento, Titolo, useAzione, useDati } from "@/components/ui";
import { api, qs, type Articolo } from "@/lib/api";
import { euro, numero } from "@/lib/formato";
import { useSessione } from "@/lib/sessione";

type Scheda = "previsione" | "riordino" | "clienti";

export default function MotoreAi() {
  const [scheda, setScheda] = useState<Scheda>("previsione");
  const [varianteIniziale, setVarianteIniziale] = useState<string | null>(null);
  useEffect(() => {
    const p = new URLSearchParams(window.location.search);
    if (p.get("scheda")) setScheda(p.get("scheda") as Scheda);
    if (p.get("varianteId")) setVarianteIniziale(p.get("varianteId"));
  }, []);
  return (
    <>
      <Titolo titolo={<span className="flex items-center gap-2"><BrainCircuit className="h-6 w-6 text-viola" /> Motore AI</span>}
        sottotitolo="Modelli statistici trasparenti calcolati sui tuoi dati, senza servizi esterni a pagamento: ogni suggerimento spiega il perché" />
      <Schede attiva={scheda} onCambia={setScheda} voci={[
        { id: "previsione", etichetta: <><TrendingUp className="h-4 w-4" /> Previsione vendite</> },
        { id: "riordino", etichetta: <><PackagePlus className="h-4 w-4" /> Riordino automatico</> },
        { id: "clienti", etichetta: <><Users className="h-4 w-4" /> Analisi clienti</> },
      ]} />
      {scheda === "previsione" && <Previsione varianteIniziale={varianteIniziale} />}
      {scheda === "riordino" && <Riordino />}
      {scheda === "clienti" && <Clienti />}
    </>
  );
}

function Previsione({ varianteIniziale }: { varianteIniziale: string | null }) {
  const [articolo, setArticolo] = useState<Articolo | null>(null);
  const [giorni, setGiorni] = useState(30);
  const [storico, setStorico] = useState(120);
  useEffect(() => {
    if (varianteIniziale) api<Articolo[]>(`/api/articoli${qs({ varianteId: varianteIniziale })}`).then((r) => r[0] && setArticolo(r[0])).catch(() => undefined);
  }, [varianteIniziale]);
  const { dati: p, errore, caricamento } = useDati<any>(`/api/ai/previsione${qs({ varianteId: articolo?.varianteId, giorni, storico: 182 })}`);
  const quantita = p?.misura === "quantita";
  const visibile = p ? p.storico.slice(-storico) : [];

  return (
    <div className="space-y-6">
      <div className="scheda grid gap-3 p-4 md:grid-cols-[2fr_1fr_1fr]">
        <div>
          <span className="etichetta">Cosa prevedere</span>
          {articolo ? (
            <div className="flex items-center justify-between rounded-xl border border-bordo bg-fondo px-3 py-2">
              <span><span className="font-medium">{articolo.nome}</span> <span className="font-mono text-xs text-tenue">{articolo.sku}</span></span>
              <button className="link text-sm" onClick={() => setArticolo(null)}>Fatturato totale</button>
            </div>
          ) : <CercaArticolo onScelto={setArticolo} segnaposto="Fatturato totale, oppure scegli un articolo…" />}
        </div>
        <label className="text-sm"><span className="etichetta">Orizzonte</span>
          <select className="campo" value={giorni} onChange={(e) => setGiorni(Number(e.target.value))}>{[14, 30, 60, 90].map((g) => <option key={g} value={g}>{g} giorni</option>)}</select></label>
        <label className="text-sm"><span className="etichetta">Storico mostrato</span>
          <select className="campo" value={storico} onChange={(e) => setStorico(Number(e.target.value))}>{[[60, "2 mesi"], [120, "4 mesi"], [182, "6 mesi"]].map(([g, n]) => <option key={g} value={g}>{n}</option>)}</select></label>
      </div>
      {errore && <Errore messaggio={errore} />}
      {!p && caricamento && <Caricamento testo="Calcolo della previsione…" />}
      {p && (
        <>
          <section className="grid gap-4 sm:grid-cols-3">
            <Kpi etichetta={`Previsto nei prossimi ${giorni} giorni`} valore={quantita ? `${numero(Math.round(p.totalePrevisto))} ${articolo?.unita ?? ""}` : euro(p.totalePrevisto, true)} icona={Sparkles} tono="viola" />
            <Kpi etichetta="Media giornaliera prevista" valore={quantita ? numero(Math.round((p.totalePrevisto / giorni) * 10) / 10) : euro(p.totalePrevisto / giorni, true)} icona={TrendingUp} />
            <Kpi etichetta="Accuratezza sul passato" valore={p.accuratezza.mape === null ? "—" : `${numero(Math.max(0, 100 - p.accuratezza.mape))}%`} tono="ok" nota={p.accuratezza.descrizione} />
          </section>
          <section className={`scheda ${caricamento ? "opacity-60" : ""}`}>
            <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
              <h2 className="font-semibold">{articolo ? `Unità vendute al giorno · ${articolo.nome}` : "Fatturato giornaliero"}</h2>
              <span className="pillola bg-viola/15 text-viola">{p.metodo}</span>
            </div>
            <GraficoPrevisione storico={visibile} previsione={p.previsione} quantita={quantita} />
            <p className="mt-3 text-sm text-tenue">{p.spiegazione}</p>
          </section>
        </>
      )}
    </div>
  );
}

function Riordino() {
  const { puo } = useSessione();
  const { dati, errore, ricarica } = useDati<any[]>("/api/ai/riordino");
  const { dati: azienda, ricarica: ricaricaAzienda } = useDati<any>("/api/azienda");
  const [scelti, setScelti] = useState<string[] | null>(null);
  const [esito, setEsito] = useState<any>(null);
  const azione = useAzione();
  const selezione = scelti ?? dati?.map((s) => s.varianteId) ?? [];
  const totale = (dati ?? []).filter((s) => selezione.includes(s.varianteId)).reduce((t, s) => t + s.quantitaSuggerita * s.costoUnitario, 0);

  const genera = () => azione.esegui(async () => {
    const r = await api("/api/ai/riordino/genera", { metodo: "POST", corpo: { varianteIds: selezione } });
    setEsito(r); setScelti(null); await ricarica();
  });

  return (
    <div className="space-y-4">
      <Avviso tipo="info">
        Per ogni articolo: domanda media e variabilità degli ultimi 90 giorni, tempo di consegna del fornitore e scorta di sicurezza al 95% di livello di servizio.
        Si riordina quando disponibile + in arrivo scende sotto il punto di riordino, per coprire anche i 14 giorni successivi.
      </Avviso>
      {azienda && puo("impostazioni") && (
        <div className="scheda p-4">
          <Interruttore attivo={azienda.riordinoAutomatico} etichetta="Riordino automatico"
            descrizione="Ogni ora il sistema crea le bozze degli ordini ai fornitori per gli articoli da riordinare: tu le controlli e le invii."
            onChange={(v) => azione.esegui(async () => { await api("/api/azienda", { metodo: "PATCH", corpo: { riordinoAutomatico: v } }); await ricaricaAzienda(); })} />
        </div>
      )}
      {esito && <Avviso tipo="ok">Creati {esito.ordini.length} ordini di acquisto in bozza:{" "}
        {esito.ordini.map((o: any, i: number) => <span key={o.id}>{i ? ", " : ""}<Link className="link font-medium" href={`/app/acquisti/${o.id}`}>{o.numero}</Link></span>)}.
        {esito.senzaFornitore > 0 && ` ${esito.senzaFornitore} articoli non hanno un fornitore abituale.`}</Avviso>}
      {azione.errore && <Avviso tipo="errore">{azione.errore}</Avviso>}
      {errore && <Errore messaggio={errore} riprova={ricarica} />}
      {!dati && !errore && <Caricamento />}
      {dati && !dati.length && <div className="scheda"><p className="py-6 text-center text-tenue">Nessun articolo da riordinare: scorte e ordini in arrivo coprono la domanda prevista.</p></div>}
      {dati && dati.length > 0 && (
        <div className="scheda overflow-x-auto p-0">
          <table className="tabella">
            <thead><tr><th><input type="checkbox" aria-label="Seleziona tutti" checked={selezione.length === dati.length} onChange={(e) => setScelti(e.target.checked ? dati.map((s) => s.varianteId) : [])} /></th>
              <th>Articolo</th><th>Urgenza</th><th className="num">Disponibile</th><th className="num">In arrivo</th><th className="num">Domanda/giorno</th><th className="num">Punto di riordino</th><th className="num">Da ordinare</th><th>Perché</th></tr></thead>
            <tbody>{dati.map((s) => (
              <tr key={s.varianteId}>
                <td><input type="checkbox" aria-label={`Seleziona ${s.nome}`} checked={selezione.includes(s.varianteId)} onChange={(e) => setScelti(e.target.checked ? [...selezione, s.varianteId] : selezione.filter((x) => x !== s.varianteId))} /></td>
                <td><p className="font-medium">{s.nome}</p><p className="text-xs text-tenue">{s.sku} · {s.fornitore ?? "nessun fornitore"}</p></td>
                <td><span className={`pillola ${s.urgenza === "alta" ? "bg-errore/15 text-errore" : "bg-attenzione/15 text-attenzione"}`}>{s.urgenza}</span></td>
                <td className="num">{numero(s.disponibile)}</td><td className="num">{numero(s.inArrivo)}</td><td className="num">{numero(s.domandaGiornaliera)}</td>
                <td className="num">{numero(s.puntoRiordino)}</td><td className="num font-bold">{numero(s.quantitaSuggerita)}<span className="block text-xs font-normal text-tenue">{euro(s.quantitaSuggerita * s.costoUnitario)}</span></td>
                <td className="max-w-sm text-xs text-tenue">{s.motivazione}</td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
      {dati && dati.length > 0 && puo("acquisti") && (
        <div className="flex flex-wrap items-center justify-end gap-3">
          <span className="text-sm text-tenue">{selezione.length} articoli · costo stimato {euro(totale)}</span>
          <button className="btn-primario" disabled={!selezione.length || azione.inCorso} onClick={genera}><PackagePlus className="h-4 w-4" /> Genera ordini ai fornitori</button>
        </div>
      )}
    </div>
  );
}

function Clienti() {
  const { dati, errore, ricarica } = useDati<any>("/api/ai/clienti");
  const [segmento, setSegmento] = useState("");
  const [rischio, setRischio] = useState("");
  if (errore) return <Errore messaggio={errore} riprova={ricarica} />;
  if (!dati) return <Caricamento />;
  const profili = dati.profili.filter((p: any) => (!segmento || p.segmento === segmento) && (!rischio || p.rischioAbbandono === rischio));
  return (
    <div className="space-y-6">
      <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-7">
        {dati.segmenti.map((s: any) => (
          <button key={s.segmento} onClick={() => setSegmento(segmento === s.segmento ? "" : s.segmento)}
            className={`scheda p-3 text-left transition ${segmento === s.segmento ? "border-primario ring-2 ring-primario/30" : "hover:border-primario/40"}`}>
            <Segmento nome={s.segmento} /><p className="mt-2 text-xl font-bold">{s.clienti}</p><p className="text-xs text-tenue">{euro(s.valore, true)} di fatturato</p>
          </button>
        ))}
      </section>
      <div className="flex flex-wrap items-center gap-3">
        <select className="campo max-w-xs" value={rischio} onChange={(e) => setRischio(e.target.value)} aria-label="Rischio di abbandono">
          <option value="">Qualsiasi rischio di abbandono</option><option value="alto">Rischio alto</option><option value="medio">Rischio medio</option><option value="basso">Rischio basso</option>
        </select>
        <p className="text-sm text-tenue">RFM: recenza, frequenza e valore degli acquisti degli ultimi 12 mesi, in quintili. Il rischio confronta i giorni dall&apos;ultimo ordine con il ritmo abituale del cliente.</p>
      </div>
      <div className="scheda overflow-x-auto p-0">
        <table className="tabella">
          <thead><tr><th>Cliente</th><th>Segmento</th><th className="num">R·F·M</th><th className="num">Ultimo ordine</th><th className="num">Ordini</th><th className="num">Valore 12 mesi</th><th className="num">Valore annuo stimato</th><th>Rischio</th><th>Azione consigliata</th></tr></thead>
          <tbody>{profili.slice(0, 150).map((p: any) => (
            <tr key={p.clienteId}>
              <td><Link className="link font-medium" href={`/app/clienti/${p.clienteId}`}>{p.nome}</Link></td><td><Segmento nome={p.segmento} /></td>
              <td className="num font-mono text-xs">{p.r}·{p.f}·{p.m}</td><td className="num">{p.recenzaGiorni} gg fa{p.intervalloMedioGiorni && <span className="block text-xs text-tenue">di solito ogni {p.intervalloMedioGiorni}</span>}</td>
              <td className="num">{p.frequenza}</td><td className="num">{euro(p.valore)}</td><td className="num">{euro(p.valoreAnnuoStimato)}</td>
              <td><span className={`pillola ${p.rischioAbbandono === "alto" ? "bg-errore/15 text-errore" : p.rischioAbbandono === "medio" ? "bg-attenzione/15 text-attenzione" : "bg-ok/15 text-ok"}`}>{p.rischioAbbandono}</span></td>
              <td className="max-w-xs text-xs text-tenue">{p.consiglio}</td>
            </tr>
          ))}</tbody>
        </table>
      </div>
    </div>
  );
}
