"use client";
// Versioni passate e simulazioni future (F04) — © Roberto Di Flumeri
import { CheckCircle2, GitCompare, MessageCircle, Plus, ShieldAlert, Sparkles, Trash2, X } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Avviso, Caricamento, Categoria, Titolo, useAzione } from "@/components/ui";
import { api, CATEGORIE, dataIt, type Dichiarazione, type Versione } from "@/lib/api";

interface VoceDati { id: string; testo: string; categoria: string; peso: number }
interface Confronto {
  da: string; a: string; aggiunte: VoceDati[]; rimosse: VoceDati[];
  modificate: { prima: VoceDati; dopo: VoceDati; campi: string[] }[];
  per_categoria: Record<string, { prima: number; dopo: number }>; nota: string;
}
interface Ipotesi { azione: "aggiungi" | "modifica" | "rimuovi"; id?: string; testo?: string; categoria?: string; peso?: number }

export default function Versioni() {
  const [versioni, setVersioni] = useState<Versione[] | null>(null);
  const [etichetta, setEtichetta] = useState("");
  const [da, setDa] = useState("");
  const [a, setA] = useState("attuale");
  const [confronto, setConfronto] = useState<Confronto | null>(null);
  const [dettaglio, setDettaglio] = useState<Versione | null>(null);
  const [simula, setSimula] = useState(false);
  const { inCorso, errore, esegui } = useAzione();

  const carica = () => api<Versione[]>("/v1/snapshots").then((v) => { setVersioni(v); if (!da && v.length) setDa(v[v.length - 1].id); });
  useEffect(() => { carica(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const salva = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => {
      await api("/v1/snapshots", { metodo: "POST", corpo: { etichetta: etichetta || null } });
      setEtichetta("");
      await carica();
    });
  };

  const confronta = () => esegui(async () => setConfronto(await api<Confronto>(`/v1/snapshots/confronto?da=${da}&a=${a}`)));
  const apri = (v: Versione) => esegui(async () => setDettaglio(await api<Versione>(`/v1/snapshots/${v.id}`)));
  const elimina = (v: Versione) => confirm(`Eliminare "${v.etichetta}"?`) &&
    esegui(async () => { await api(`/v1/snapshots/${v.id}`, { metodo: "DELETE" }); setDettaglio(null); await carica(); });

  return (
    <>
      <Titolo titolo="Versioni del profilo" sottotitolo="Fotografie immutabili di ciò che hai dichiarato, per confrontare come cambi. Le simulazioni sono sempre ipotesi."
        azioni={<button className="btn-secondario" onClick={() => setSimula(true)}><Sparkles className="h-4 w-4" /> Simula il me futuro</button>} />
      {errore && <div className="mb-4"><Avviso tipo="errore">{errore}</Avviso></div>}

      {simula && <Simulazione onFatto={() => { setSimula(false); carica(); }} onAnnulla={() => setSimula(false)} />}

      <form onSubmit={salva} className="scheda mb-6 flex flex-wrap items-end gap-3">
        <div className="flex-1">
          <label htmlFor="etichetta" className="etichetta">Salva il profilo di oggi</label>
          <input id="etichetta" className="campo" maxLength={80} placeholder="Es. Prima del trasferimento" value={etichetta} onChange={(e) => setEtichetta(e.target.value)} />
        </div>
        <button className="btn-primario" disabled={inCorso}><Plus className="h-4 w-4" /> Salva versione</button>
      </form>

      {!versioni ? <Caricamento /> : versioni.length === 0 ? (
        <Avviso tipo="info">Nessuna versione salvata. Salvane una oggi per poterla confrontare tra qualche mese.</Avviso>
      ) : (
        <>
          <section className="scheda mb-6 space-y-3">
            <h2 className="font-semibold"><GitCompare className="mr-1 inline h-4 w-4" /> Confronta</h2>
            <div className="flex flex-wrap items-center gap-2">
              <select className="campo w-full min-w-0 sm:w-auto sm:max-w-xs" aria-label="Versione di partenza" value={da} onChange={(e) => setDa(e.target.value)}>
                {versioni.map((v) => <option key={v.id} value={v.id}>{v.etichetta}</option>)}
              </select>
              <span>→</span>
              <select className="campo w-full min-w-0 sm:w-auto sm:max-w-xs" aria-label="Versione di arrivo" value={a} onChange={(e) => setA(e.target.value)}>
                <option value="attuale">Profilo attuale</option>
                {versioni.map((v) => <option key={v.id} value={v.id}>{v.etichetta}</option>)}
              </select>
              <button className="btn-primario" onClick={confronta} disabled={inCorso || !da}>Confronta</button>
            </div>
            {confronto && <EsitoConfronto c={confronto} />}
          </section>

          <ul className="grid gap-3 sm:grid-cols-2">
            {versioni.map((v) => (
              <li key={v.id} className="scheda">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-semibold">{v.etichetta}</span>
                      {v.tipo === "simulazione_futura" && <span className="pillola bg-attenzione/15 text-attenzione">Ipotesi</span>}
                    </div>
                    <div className="text-xs text-tenue">v{v.versione} · riferita al {dataIt(v.data_riferimento)}</div>
                  </div>
                  <button className="rounded p-1 text-tenue hover:text-errore" onClick={() => elimina(v)} aria-label={`Elimina ${v.etichetta}`}><Trash2 className="h-4 w-4" /></button>
                </div>
                <p className="mt-2 line-clamp-3 text-sm text-tenue">{v.riepilogo}</p>
                <div className="mt-3 flex gap-2">
                  <button className="btn-secondario text-sm" onClick={() => apri(v)}>Dettagli</button>
                  <Link href={`/app/chat?versione=${v.id}`} className="btn-secondario text-sm"><MessageCircle className="h-4 w-4" /> Parlaci in chat</Link>
                </div>
              </li>
            ))}
          </ul>
        </>
      )}

      {dettaglio && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" role="dialog" aria-modal="true" aria-label={dettaglio.etichetta}>
          <div className="scheda max-h-[85vh] w-full max-w-2xl overflow-y-auto">
            <div className="flex items-start justify-between gap-2">
              <h2 className="text-lg font-semibold">{dettaglio.etichetta}</h2>
              <button className="btn-secondario px-2" onClick={() => setDettaglio(null)} aria-label="Chiudi"><X className="h-4 w-4" /></button>
            </div>
            <p className="mt-1 text-sm">
              {dettaglio.integro
                ? <span className="text-ok"><CheckCircle2 className="mr-1 inline h-4 w-4" />Integrità verificata (impronta SHA-256)</span>
                : <span className="text-errore"><ShieldAlert className="mr-1 inline h-4 w-4" />Attenzione: i dati non corrispondono all&apos;impronta originale</span>}
            </p>
            <p className="mt-3 text-sm text-tenue">{dettaglio.riepilogo}</p>
            <ul className="mt-4 space-y-2">
              {(dettaglio.dati as unknown as VoceDati[] || []).map((d) => (
                <li key={d.id} className="rounded-lg bg-fondo p-2 text-sm"><Categoria nome={d.categoria} /> {d.testo} <span className="text-tenue">({d.peso}/5)</span></li>
              ))}
            </ul>
          </div>
        </div>
      )}
    </>
  );
}

function EsitoConfronto({ c }: { c: Confronto }) {
  const vuoto = !c.aggiunte.length && !c.rimosse.length && !c.modificate.length;
  return (
    <div className="space-y-3 border-t border-bordo pt-3 text-sm">
      <p className="text-tenue">{c.da} → {c.a}</p>
      {vuoto && <p>Nessuna differenza.</p>}
      {c.aggiunte.length > 0 && <div><h3 className="font-semibold text-ok">Nuove dichiarazioni</h3>
        <ul className="ml-4 list-disc">{c.aggiunte.map((d) => <li key={d.id}>{d.testo} <span className="text-tenue">({CATEGORIE[d.categoria]})</span></li>)}</ul></div>}
      {c.rimosse.length > 0 && <div><h3 className="font-semibold text-errore">Non più presenti</h3>
        <ul className="ml-4 list-disc">{c.rimosse.map((d) => <li key={d.id}>{d.testo}</li>)}</ul></div>}
      {c.modificate.length > 0 && <div><h3 className="font-semibold text-attenzione">Cambiate</h3>
        <ul className="ml-4 list-disc space-y-1">{c.modificate.map((m) => (
          <li key={m.dopo.id}>«{m.prima.testo}» → «{m.dopo.testo}»{m.campi.includes("peso") && ` (importanza ${m.prima.peso} → ${m.dopo.peso})`}</li>
        ))}</ul></div>}
      <p className="text-xs text-tenue">{c.nota}</p>
    </div>
  );
}

function Simulazione({ onFatto, onAnnulla }: { onFatto: () => void; onAnnulla: () => void }) {
  const [mesi, setMesi] = useState(12);
  const [etichetta, setEtichetta] = useState("");
  const [ipotesi, setIpotesi] = useState<Ipotesi[]>([{ azione: "aggiungi", testo: "", categoria: "obiettivi", peso: 3 }]);
  const [dichiarazioni, setDichiarazioni] = useState<Dichiarazione[]>([]);
  const { inCorso, errore, esegui } = useAzione();

  useEffect(() => { api<Dichiarazione[]>("/v1/profile/statements").then(setDichiarazioni); }, []);
  const aggiorna = (i: number, p: Partial<Ipotesi>) => setIpotesi(ipotesi.map((x, k) => (k === i ? { ...x, ...p } : x)));

  const invia = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => {
      const pulite = ipotesi.map((ip) => ({ ...ip, testo: ip.testo?.trim() || undefined }));
      await api("/v1/snapshots/simulazione", { metodo: "POST", corpo: { mesi, etichetta: etichetta || null, ipotesi: pulite } });
      onFatto();
    });
  };

  return (
    <form onSubmit={invia} className="scheda mb-6 space-y-4 border-attenzione/50">
      <Avviso tipo="attenzione">Una simulazione è una versione <strong>ipotetica</strong> costruita dalle tue ipotesi: non è una previsione.</Avviso>
      <div className="flex flex-wrap gap-3">
        <label className="text-sm">Tra quanti mesi
          <input type="number" min={1} max={120} className="campo mt-1 w-28" value={mesi} onChange={(e) => setMesi(Number(e.target.value))} />
        </label>
        <label className="flex-1 text-sm">Nome
          <input className="campo mt-1" maxLength={80} placeholder={`Io tra ${mesi} mesi`} value={etichetta} onChange={(e) => setEtichetta(e.target.value)} />
        </label>
      </div>
      <h3 className="font-semibold">Ipotesi di cambiamento</h3>
      {ipotesi.map((ip, i) => (
        <div key={i} className="flex flex-wrap items-center gap-2 rounded-xl bg-fondo p-2">
          <select className="campo w-auto" aria-label="Tipo di ipotesi" value={ip.azione}
            onChange={(e) => aggiorna(i, { azione: e.target.value as Ipotesi["azione"], id: undefined })}>
            <option value="aggiungi">Aggiungo</option><option value="modifica">Cambio</option><option value="rimuovi">Non vale più</option>
          </select>
          {ip.azione !== "aggiungi" && (
            <select className="campo w-full min-w-0 sm:w-auto sm:max-w-xs" required aria-label="Dichiarazione" value={ip.id || ""} onChange={(e) => aggiorna(i, { id: e.target.value })}>
              <option value="">Scegli…</option>
              {dichiarazioni.map((d) => <option key={d.id} value={d.id}>{d.testo.slice(0, 60)}</option>)}
            </select>
          )}
          {ip.azione !== "rimuovi" && (
            <input className="campo flex-1" required={ip.azione === "aggiungi"} maxLength={1000} aria-label="Nuovo testo"
              placeholder={ip.azione === "aggiungi" ? "Es. Vivo vicino al mare" : "Nuovo testo"} value={ip.testo || ""} onChange={(e) => aggiorna(i, { testo: e.target.value })} />
          )}
          {ip.azione === "aggiungi" && (
            <select className="campo w-auto" aria-label="Categoria" value={ip.categoria} onChange={(e) => aggiorna(i, { categoria: e.target.value })}>
              {Object.entries(CATEGORIE).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          )}
          <button type="button" className="btn-secondario px-2" disabled={ipotesi.length <= 1} aria-label="Rimuovi ipotesi"
            onClick={() => setIpotesi(ipotesi.filter((_, k) => k !== i))}><X className="h-4 w-4" /></button>
        </div>
      ))}
      <button type="button" className="btn-secondario" onClick={() => setIpotesi([...ipotesi, { azione: "aggiungi", testo: "", categoria: "obiettivi" }])}>
        <Plus className="h-4 w-4" /> Ipotesi
      </button>
      {errore && <Avviso tipo="errore">{errore}</Avviso>}
      <div className="flex justify-end gap-2">
        <button type="button" className="btn-secondario" onClick={onAnnulla}>Annulla</button>
        <button className="btn-primario" disabled={inCorso}>Crea simulazione</button>
      </div>
    </form>
  );
}
