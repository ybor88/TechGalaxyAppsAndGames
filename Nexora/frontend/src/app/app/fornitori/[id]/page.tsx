"use client";
// Scheda fornitore: anagrafica, prodotti forniti e ordini di acquisto. © Roberto Di Flumeri
import { Pencil, Trash2, Truck } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import FormAnagrafica from "@/components/FormAnagrafica";
import { Avviso, Caricamento, Errore, Kpi, Stato, Titolo, useAzione, useDati } from "@/components/ui";
import { api } from "@/lib/api";
import { data, euro } from "@/lib/formato";
import { useSessione } from "@/lib/sessione";

export default function SchedaFornitore() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { puo } = useSessione();
  const { dati: f, errore, ricarica } = useDati<any>(`/api/fornitori/${id}`);
  const [modifica, setModifica] = useState(false);
  const azione = useAzione();
  if (errore) return <Errore messaggio={errore} riprova={ricarica} />;
  if (!f) return <Caricamento />;
  const speso = f.acquisti.filter((a: any) => a.stato !== "annullato").reduce((s: number, a: any) => s + a.totale, 0);

  return (
    <>
      <Titolo indietro={<Link href="/app/fornitori" className="link">← Fornitori</Link>} titolo={f.ragioneSociale}
        sottotitolo={`${f.citta ?? ""}${f.provincia ? ` (${f.provincia})` : ""} · P.IVA ${f.partitaIva ?? "—"}${f.email ? ` · ${f.email}` : ""}`}
        azioni={<>
          {puo("acquisti") && <Link href={`/app/acquisti/nuovo?fornitoreId=${id}`} className="btn-primario"><Truck className="h-4 w-4" /> Nuovo ordine di acquisto</Link>}
          {puo("fornitori") && <button className="btn-secondario" onClick={() => setModifica(true)}><Pencil className="h-4 w-4" /> Modifica</button>}
          {puo("fornitori") && <button className="btn-secondario text-errore" aria-label="Elimina fornitore" onClick={() => confirm("Eliminare il fornitore?") &&
            azione.esegui(async () => { await api(`/api/fornitori/${id}`, { metodo: "DELETE" }); router.replace("/app/fornitori"); })}><Trash2 className="h-4 w-4" /></button>}
        </>} />
      {azione.errore && <Avviso tipo="errore" className="mb-4">{azione.errore}</Avviso>}
      <section className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi etichetta="Tempo di consegna" valore={`${f.leadTimeGiorni} giorni`} nota="usato dal motore di riordino" />
        <Kpi etichetta="Pagamento" valore={`${f.giorniPagamento} giorni`} tono="viola" nota={f.iban ? `IBAN ${f.iban}` : "IBAN non indicato"} />
        <Kpi etichetta="Ordini di acquisto" valore={f.acquisti.length} tono="ciano" nota={`${f.ordiniRicevuti} ricevuti`} />
        <Kpi etichetta="Totale ordinato" valore={euro(speso, true)} tono="ok" />
      </section>
      <div className="grid gap-6 xl:grid-cols-2">
        <section className="scheda overflow-x-auto">
          <h2 className="mb-3 font-semibold">Prodotti forniti</h2>
          {f.prodotti.length ? <table className="tabella"><thead><tr><th>SKU</th><th>Prodotto</th><th className="num">Costo</th></tr></thead>
            <tbody>{f.prodotti.map((p: any) => <tr key={p.id}><td className="font-mono text-xs">{p.sku}</td><td><Link href={`/app/prodotti/${p.id}`} className="link">{p.nome}</Link>{!p.attivo && " (disattivato)"}</td><td className="num">{euro(p.costoAcquisto)}</td></tr>)}</tbody>
          </table> : <p className="text-sm text-tenue">Nessun prodotto collegato.</p>}
        </section>
        <section className="scheda overflow-x-auto">
          <h2 className="mb-3 font-semibold">Ordini di acquisto</h2>
          {f.acquisti.length ? <table className="tabella"><thead><tr><th>Numero</th><th>Data</th><th>Stato</th><th className="num">Totale</th></tr></thead>
            <tbody>{f.acquisti.map((a: any) => <tr key={a.id}><td><Link href={`/app/acquisti/${a.id}`} className="link font-medium">{a.numero}</Link>{a.generatoDaAi && <span className="pillola ml-2 bg-viola/15 text-viola">AI</span>}</td>
              <td>{data(a.data)}</td><td><Stato stato={a.stato} /></td><td className="num">{euro(a.totale)}</td></tr>)}</tbody>
          </table> : <p className="text-sm text-tenue">Nessun ordine.</p>}
        </section>
      </div>
      {modifica && <FormAnagrafica tipo="fornitore" iniziale={f} onChiudi={() => setModifica(false)} onSalvato={() => { setModifica(false); ricarica(); }} />}
    </>
  );
}
