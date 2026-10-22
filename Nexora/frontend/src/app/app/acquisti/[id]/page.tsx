"use client";
// Ordine di acquisto: invio, annullamento e ricevimento merce (anche parziale) con lotti, scadenze e seriali. © Roberto Di Flumeri
import { Ban, PackageCheck, Send, Trash2 } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { Avviso, Caricamento, Errore, Modale, Stato, Titolo, useAzione, useDati } from "@/components/ui";
import { api } from "@/lib/api";
import { data, euro, numero } from "@/lib/formato";
import { useSessione } from "@/lib/sessione";

export default function DettaglioAcquisto() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { puo } = useSessione();
  const { dati: o, errore, ricarica } = useDati<any>(`/api/acquisti/${id}`);
  const [ricevi, setRicevi] = useState(false);
  const azione = useAzione();
  if (errore) return <Errore messaggio={errore} riprova={ricarica} />;
  if (!o) return <Caricamento />;
  const scrive = puo("acquisti");
  const esegui = (percorso: string, conferma?: string) => (!conferma || confirm(conferma)) && azione.esegui(async () => { await api(percorso, { metodo: "POST" }); await ricarica(); });

  return (
    <>
      <Titolo indietro={<Link href="/app/acquisti" className="link">← Acquisti</Link>}
        titolo={<span className="flex flex-wrap items-center gap-3">Ordine {o.numero} <Stato stato={o.stato} />{o.generatoDaAi && <span className="pillola bg-viola/15 text-viola">Proposto dall&apos;AI</span>}</span>}
        sottotitolo={<>del {data(o.data)} · <Link href={`/app/fornitori/${o.fornitoreId}`} className="link">{o.fornitore.ragioneSociale}</Link> · destinazione {o.magazzino.nome} · arrivo previsto {data(o.dataPrevista)}</>}
        azioni={scrive && <>
          {o.stato === "bozza" && <button className="btn-primario" onClick={() => esegui(`/api/acquisti/${id}/invia`)}><Send className="h-4 w-4" /> Invia al fornitore</button>}
          {["inviato", "parziale"].includes(o.stato) && <button className="btn-primario" onClick={() => setRicevi(true)}><PackageCheck className="h-4 w-4" /> Ricevi merce</button>}
          {["bozza", "inviato"].includes(o.stato) && <button className="btn-secondario text-errore" onClick={() => esegui(`/api/acquisti/${id}/annulla`, "Annullare l'ordine di acquisto?")}><Ban className="h-4 w-4" /> Annulla</button>}
          {o.stato === "bozza" && <button className="btn-secondario text-errore" aria-label="Elimina bozza" onClick={() => confirm("Eliminare la bozza?") &&
            azione.esegui(async () => { await api(`/api/acquisti/${id}`, { metodo: "DELETE" }); router.replace("/app/acquisti"); })}><Trash2 className="h-4 w-4" /></button>}
        </>} />
      {azione.errore && <Avviso tipo="errore" className="mb-4">{azione.errore}</Avviso>}
      <section className="scheda overflow-x-auto">
        <table className="tabella">
          <thead><tr><th>Articolo</th><th className="num">Ordinati</th><th className="num">Ricevuti</th><th className="num">Costo</th><th className="num">IVA</th><th className="num">Imponibile</th></tr></thead>
          <tbody>{o.righe.map((r: any) => (
            <tr key={r.id}>
              <td><p className="font-medium">{r.descrizione}</p><p className="font-mono text-xs text-tenue">{r.sku}{r.gestioneLotti ? " · lotti" : ""}{r.gestioneSeriali ? " · seriali" : ""}</p></td>
              <td className="num">{numero(r.quantita)} {r.unita}</td>
              <td className={`num ${r.quantitaRicevuta >= r.quantita ? "text-ok" : r.quantitaRicevuta > 0 ? "text-attenzione" : "text-tenue"}`}>{numero(r.quantitaRicevuta)}</td>
              <td className="num">{euro(r.costoUnitario)}</td><td className="num">{r.aliquotaIva}%</td><td className="num">{euro(Math.round(r.quantita * r.costoUnitario))}</td>
            </tr>
          ))}</tbody>
        </table>
        <div className="ml-auto mt-4 w-full max-w-xs space-y-1 rounded-xl bg-fondo p-4 text-sm">
          <div className="flex justify-between"><span>Imponibile</span><span>{euro(o.imponibile)}</span></div>
          <div className="flex justify-between text-tenue"><span>IVA</span><span>{euro(o.iva)}</span></div>
          <div className="flex justify-between border-t border-bordo pt-1 text-base font-bold"><span>Totale</span><span>{euro(o.totale)}</span></div>
        </div>
        {o.note && <p className="mt-3 text-sm text-tenue">Note: {o.note}</p>}
      </section>
      {ricevi && <Ricevimento ordine={o} onChiudi={() => setRicevi(false)} onFatto={() => { setRicevi(false); ricarica(); }} />}
    </>
  );
}

function Ricevimento({ ordine, onChiudi, onFatto }: { ordine: any; onChiudi: () => void; onFatto: () => void }) {
  const aperte = ordine.righe.filter((r: any) => r.quantitaRicevuta < r.quantita);
  const [righe, setRighe] = useState<Record<string, { quantita: string; lotto: string; scadenza: string; seriali: string }>>(
    Object.fromEntries(aperte.map((r: any) => [r.id, { quantita: String(Math.round((r.quantita - r.quantitaRicevuta) * 1000) / 1000), lotto: "", scadenza: "", seriali: "" }])));
  const { inCorso, errore, esegui } = useAzione();
  const set = (id: string, k: string, v: string) => setRighe({ ...righe, [id]: { ...righe[id], [k]: v } });

  const conferma = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => {
      const corpo = aperte.map((r: any) => {
        const x = righe[r.id];
        const seriali = x.seriali.split(/[\s,;]+/).filter(Boolean);
        return { rigaId: r.id, quantita: r.gestioneSeriali ? seriali.length : Number(x.quantita.replace(",", ".")),
          ...(r.gestioneLotti ? { lotto: { codice: x.lotto.trim(), scadenza: x.scadenza || null } } : {}), ...(r.gestioneSeriali ? { seriali } : {}) };
      }).filter((r: any) => r.quantita > 0);
      if (!corpo.length) throw new Error("Indica almeno una quantità ricevuta");
      await api(`/api/acquisti/${ordine.id}/ricevi`, { metodo: "POST", corpo: { righe: corpo } });
      onFatto();
    });
  };

  return (
    <Modale titolo={`Ricevimento merce · ${ordine.numero}`} onChiudi={onChiudi} larga>
      <form onSubmit={conferma} className="space-y-4">
        <p className="text-sm text-tenue">Registra quanto è arrivato: puoi ricevere anche solo una parte. La merce entra in {ordine.magazzino.nome} e il costo medio dei prodotti si aggiorna.</p>
        {aperte.map((r: any) => (
          <div key={r.id} className="grid gap-3 rounded-xl border border-bordo p-3 md:grid-cols-4">
            <div className="md:col-span-4"><p className="font-medium">{r.descrizione}</p><p className="text-xs text-tenue">da ricevere {numero(r.quantita - r.quantitaRicevuta)} {r.unita}</p></div>
            {!r.gestioneSeriali && <label className="text-sm"><span className="etichetta">Quantità arrivata</span>
              <input className="campo" value={righe[r.id].quantita} onChange={(e) => set(r.id, "quantita", e.target.value)} inputMode="decimal" /></label>}
            {r.gestioneLotti && <>
              <label className="text-sm"><span className="etichetta">Lotto</span><input className="campo font-mono" value={righe[r.id].lotto} onChange={(e) => set(r.id, "lotto", e.target.value)} required /></label>
              <label className="text-sm"><span className="etichetta">Scadenza</span><input className="campo" type="date" value={righe[r.id].scadenza} onChange={(e) => set(r.id, "scadenza", e.target.value)} /></label>
            </>}
            {r.gestioneSeriali && <label className="text-sm md:col-span-4"><span className="etichetta">Numeri di serie arrivati (uno per riga)</span>
              <textarea className="campo font-mono" rows={3} value={righe[r.id].seriali} onChange={(e) => set(r.id, "seriali", e.target.value)} /></label>}
          </div>
        ))}
        {errore && <Avviso tipo="errore">{errore}</Avviso>}
        <button className="btn-primario w-full" disabled={inCorso}>Conferma ricevimento</button>
      </form>
    </Modale>
  );
}
