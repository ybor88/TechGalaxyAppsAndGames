"use client";
// Dettaglio ordine: flusso di stato, righe con lotti e seriali, spedizioni GLS/DHL con tracciamento ed etichetta, documenti collegati. © Roberto Di Flumeri
import { Ban, Check, ExternalLink, FileText, PackageCheck, PackageOpen, RefreshCw, Tag, Trash2, Truck } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Avviso, Campo, Canale, Caricamento, Errore, Modale, NOMI_TIPO_DOC, Stato, Titolo, useAzione, useDati } from "@/components/ui";
import { api, apriPdf } from "@/lib/api";
import { data, dataOra, euro, numero } from "@/lib/formato";
import { useSessione } from "@/lib/sessione";

const AZIONI: Record<string, { etichetta: string; icona: React.ComponentType<{ className?: string }>; stile: string; permesso: "ordini" | "spedizioni" }> = {
  confermato: { etichetta: "Conferma e impegna merce", icona: Check, stile: "btn-primario", permesso: "ordini" },
  in_preparazione: { etichetta: "In preparazione", icona: PackageOpen, stile: "btn-secondario", permesso: "spedizioni" },
  spedito: { etichetta: "Spedisci (scarico + DDT)", icona: Truck, stile: "btn-primario", permesso: "spedizioni" },
  consegnato: { etichetta: "Segna consegnato", icona: PackageCheck, stile: "btn-primario", permesso: "spedizioni" },
  annullato: { etichetta: "Annulla ordine", icona: Ban, stile: "btn-secondario text-errore", permesso: "ordini" },
};
const NOMI_CORRIERE: Record<string, string> = { gls: "GLS", dhl: "DHL", ritiro: "Ritiro in sede" };

export default function DettaglioOrdine() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { puo } = useSessione();
  const { dati: o, errore, ricarica } = useDati<any>(`/api/ordini/${id}`);
  const [avviso, setAvviso] = useState<string | null>(null);
  const [nuovaSped, setNuovaSped] = useState(false);
  const azione = useAzione();

  useEffect(() => { setAvviso(new URLSearchParams(window.location.search).get("avviso")); }, []);
  if (errore) return <Errore messaggio={errore} riprova={ricarica} />;
  if (!o) return <Caricamento />;

  const cambia = (stato: string) => {
    if (stato === "annullato" && !confirm("Annullare l'ordine? La merce impegnata torna disponibile.")) return;
    azione.esegui(async () => { await api(`/api/ordini/${id}/stato`, { metodo: "POST", corpo: { stato } }); setAvviso(null); await ricarica(); window.dispatchEvent(new Event("nx-alert")); });
  };
  const fattura = o.documenti.find((d: any) => d.tipo === "fattura" && d.stato !== "annullata");
  const creaFattura = () => azione.esegui(async () => { const f = await api(`/api/ordini/${id}/fattura`, { metodo: "POST" }); router.push(`/app/documenti/${f.id}`); });
  const aggiornaTracking = (s: any) => azione.esegui(async () => { await api(`/api/spedizioni/${s.id}`); await ricarica(); });

  return (
    <>
      <Titolo indietro={<Link href="/app/ordini" className="link">← Ordini</Link>}
        titolo={<span className="flex flex-wrap items-center gap-3">Ordine {o.numero} <Stato stato={o.stato} /> <Canale canale={o.canale} /></span>}
        sottotitolo={<>del {data(o.data)} · <Link href={`/app/clienti/${o.clienteId}`} className="link">{o.cliente.ragioneSociale}</Link> · evaso da {o.magazzino.nome}
          {o.riferimentoEsterno && ` · rif. ${o.riferimentoEsterno}`}</>}
        azioni={<>
          {o.transizioni.filter((t: string) => puo(AZIONI[t].permesso) || puo("ordini")).map((t: string) => {
            const A = AZIONI[t];
            return <button key={t} className={A.stile} disabled={azione.inCorso} onClick={() => cambia(t)}><A.icona className="h-4 w-4" /> {A.etichetta}</button>;
          })}
          {puo("documenti") && !fattura && ["confermato", "in_preparazione", "spedito", "consegnato"].includes(o.stato) &&
            <button className="btn-secondario" onClick={creaFattura}><FileText className="h-4 w-4" /> Crea fattura</button>}
          {fattura && <Link href={`/app/documenti/${fattura.id}`} className="btn-secondario"><FileText className="h-4 w-4" /> Fattura {fattura.numero ?? "(bozza)"}</Link>}
          {o.stato === "bozza" && puo("ordini") && <button className="btn-secondario text-errore" aria-label="Elimina bozza"
            onClick={() => confirm("Eliminare la bozza?") && azione.esegui(async () => { await api(`/api/ordini/${id}`, { metodo: "DELETE" }); router.replace("/app/ordini"); })}><Trash2 className="h-4 w-4" /></button>}
        </>} />
      {avviso && <Avviso tipo="attenzione" className="mb-4">L&apos;ordine è stato salvato in bozza ma non confermato: {avviso}</Avviso>}
      {azione.errore && <Avviso tipo="errore" className="mb-4">{azione.errore}</Avviso>}

      <div className="grid gap-6 xl:grid-cols-3">
        <section className="scheda overflow-x-auto xl:col-span-2">
          <h2 className="mb-3 font-semibold">Articoli</h2>
          <table className="tabella">
            <thead><tr><th>Articolo</th><th className="num">Quantità</th><th className="num">Prezzo</th><th className="num">Sconto</th><th className="num">IVA</th><th className="num">Imponibile</th></tr></thead>
            <tbody>{o.righe.map((r: any) => (
              <tr key={r.id}>
                <td><p className="font-medium">{r.descrizione}</p><p className="font-mono text-xs text-tenue">{r.sku}</p>
                  {r.lotto && <p className="text-xs text-ciano">Lotto {r.lotto}</p>}
                  {r.seriali?.length > 0 && <p className="text-xs text-viola">S/N {r.seriali.join(", ")}</p>}
                  {["bozza", "confermato"].includes(o.stato) && <p className={`text-xs ${r.disponibile < 0 ? "text-errore" : "text-tenue"}`}>disponibile in sede: {numero(r.disponibile)}</p>}</td>
                <td className="num">{numero(r.quantita)} {r.unita}</td><td className="num">{euro(r.prezzoUnitario)}</td><td className="num">{r.sconto ? `${r.sconto}%` : "—"}</td>
                <td className="num">{r.aliquotaIva}%</td><td className="num font-medium">{euro(Math.round(r.quantita * r.prezzoUnitario * (1 - r.sconto / 100)))}</td>
              </tr>
            ))}</tbody>
          </table>
          <div className="ml-auto mt-4 w-full max-w-xs space-y-1 rounded-xl bg-fondo p-4 text-sm">
            <div className="flex justify-between"><span>Imponibile</span><span>{euro(o.imponibile)}</span></div>
            <div className="flex justify-between text-tenue"><span>IVA</span><span>{euro(o.iva)}</span></div>
            <div className="flex justify-between border-t border-bordo pt-1 text-base font-bold"><span>Totale</span><span>{euro(o.totale)}</span></div>
          </div>
        </section>

        <div className="space-y-6">
          <section className="scheda text-sm">
            <h2 className="mb-3 font-semibold">Avanzamento</h2>
            <ol className="space-y-2">
              {[["Creato", o.createdAt], ["Confermato", o.confermatoAt], ["Spedito", o.spedito], ["Consegnato", o.consegnato]].map(([n, d]) => (
                <li key={n} className="flex items-center gap-2">
                  <span className={`h-2.5 w-2.5 rounded-full ${d ? "gradiente" : "bg-bordo"}`} aria-hidden />
                  <span className={d ? "font-medium" : "text-tenue"}>{n}</span><span className="ml-auto text-tenue">{d ? dataOra(d) : "—"}</span>
                </li>
              ))}
            </ol>
            <dl className="mt-4 space-y-2">
              <div><dt className="text-xs uppercase text-tenue">Spedizione a</dt><dd>{o.indirizzoSpedizione || "—"}</dd></div>
              {o.dataConsegnaPrevista && <div><dt className="text-xs uppercase text-tenue">Consegna prevista</dt><dd>{data(o.dataConsegnaPrevista)}</dd></div>}
              {o.note && <div><dt className="text-xs uppercase text-tenue">Note</dt><dd>{o.note}</dd></div>}
            </dl>
          </section>
          <section className="scheda text-sm">
            <h2 className="mb-3 font-semibold">Documenti collegati</h2>
            {o.documenti.length ? <ul className="space-y-2">{o.documenti.map((d: any) => (
              <li key={d.id} className="flex items-center justify-between gap-2"><Link href={`/app/documenti/${d.id}`} className="link">{NOMI_TIPO_DOC[d.tipo]} {d.numero ?? "(bozza)"}</Link>
                <span className="flex items-center gap-2"><Stato stato={d.stato} /> {euro(d.totale)}</span></li>
            ))}</ul> : <p className="text-tenue">Il DDT si emette alla spedizione; la fattura dall&apos;ordine confermato.</p>}
          </section>
        </div>
      </div>

      <section className="scheda mt-6">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="font-semibold">Spedizioni</h2>
          {puo("spedizioni") && !["bozza", "annullato"].includes(o.stato) && <button className="btn-secondario btn-piccolo" onClick={() => setNuovaSped(true)}><Truck className="h-3.5 w-3.5" /> Nuova spedizione</button>}
        </div>
        {o.spedizioni.length ? (
          <div className="grid gap-4 md:grid-cols-2">
            {o.spedizioni.map((s: any) => (
              <div key={s.id} className="rounded-xl border border-bordo p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="font-semibold">{NOMI_CORRIERE[s.corriere] ?? s.corriere} · <span className="font-mono">{s.tracking}</span></p>
                  <span className="flex items-center gap-2"><Stato stato={s.stato} />{s.modalita === "demo" && <span className="pillola bg-bordo text-tenue">demo</span>}</span>
                </div>
                <p className="text-xs text-tenue">{s.colli} colli · {numero(s.pesoKg)} kg</p>
                <ol className="mt-3 space-y-1.5 border-l-2 border-bordo pl-3 text-sm">
                  {s.eventi.map((e: any, i: number) => <li key={i}><span className="text-tenue">{dataOra(e.data)}</span> · {e.descrizione}</li>)}
                </ol>
                <div className="mt-3 flex flex-wrap gap-2">
                  {s.corriere !== "ritiro" && <button className="btn-secondario btn-piccolo" onClick={() => aggiornaTracking(s)}><RefreshCw className="h-3.5 w-3.5" /> Aggiorna tracking</button>}
                  {s.corriere !== "ritiro" && <button className="btn-secondario btn-piccolo" onClick={() => azione.esegui(() => apriPdf(`/api/spedizioni/${s.id}/etichetta.pdf`))}><Tag className="h-3.5 w-3.5" /> Etichetta PDF</button>}
                  {s.link && <a href={s.link} target="_blank" rel="noopener noreferrer" className="btn-secondario btn-piccolo"><ExternalLink className="h-3.5 w-3.5" /> Sito corriere</a>}
                </div>
              </div>
            ))}
          </div>
        ) : <p className="text-sm text-tenue">Nessuna spedizione registrata.</p>}
      </section>
      {nuovaSped && <NuovaSpedizione ordineId={id} onChiudi={() => setNuovaSped(false)} onFatto={() => { setNuovaSped(false); ricarica(); }} />}
    </>
  );
}

function NuovaSpedizione({ ordineId, onChiudi, onFatto }: { ordineId: string; onChiudi: () => void; onFatto: () => void }) {
  const [d, setD] = useState({ corriere: "gls", colli: "1", pesoKg: "1", tracking: "" });
  const { inCorso, errore, esegui } = useAzione();
  return (
    <Modale titolo="Nuova spedizione" onChiudi={onChiudi}>
      <form className="grid gap-3 sm:grid-cols-3" onSubmit={(e) => { e.preventDefault(); esegui(async () => {
        await api(`/api/ordini/${ordineId}/spedizioni`, { metodo: "POST", corpo: { corriere: d.corriere, colli: Number(d.colli), pesoKg: Number(d.pesoKg.replace(",", ".")), tracking: d.tracking || undefined } });
        onFatto();
      }); }}>
        <Campo etichetta="Corriere" className="sm:col-span-3">
          <select className="campo" value={d.corriere} onChange={(e) => setD({ ...d, corriere: e.target.value })}>
            <option value="gls">GLS</option><option value="dhl">DHL</option><option value="ritiro">Ritiro in sede</option>
          </select>
        </Campo>
        <Campo etichetta="Colli"><input className="campo" type="number" min={1} max={99} value={d.colli} onChange={(e) => setD({ ...d, colli: e.target.value })} /></Campo>
        <Campo etichetta="Peso (kg)"><input className="campo" value={d.pesoKg} onChange={(e) => setD({ ...d, pesoKg: e.target.value })} inputMode="decimal" /></Campo>
        <Campo etichetta="Tracking" aiuto="Vuoto: numero generato"><input className="campo font-mono" value={d.tracking} onChange={(e) => setD({ ...d, tracking: e.target.value })} /></Campo>
        <p className="text-xs text-tenue sm:col-span-3">Senza credenziali del corriere il tracciamento è simulato. Con la chiave gratuita DHL Shipment Tracking (Integrazioni) e un tracking reale, lo stato arriva dalle API DHL.</p>
        {errore && <div className="sm:col-span-3"><Avviso tipo="errore">{errore}</Avviso></div>}
        <button className="btn-primario sm:col-span-3" disabled={inCorso}>Registra spedizione</button>
      </form>
    </Modale>
  );
}
