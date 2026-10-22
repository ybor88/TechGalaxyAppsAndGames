"use client";
// Ordini di acquisto ai fornitori, compresi quelli proposti dal motore di riordino AI. © Roberto Di Flumeri
import { BrainCircuit, Plus, Truck } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Caricamento, Errore, Titolo, Stato, useDati, useLimite, Vuoto } from "@/components/ui";
import { qs } from "@/lib/api";
import { data, euro } from "@/lib/formato";
import { useSessione } from "@/lib/sessione";

export default function PaginaAcquisti() {
  const { puo } = useSessione();
  const router = useRouter();
  const [stato, setStato] = useState("");
  const { dati, errore, ricarica } = useDati<any[]>(`/api/acquisti${qs({ stato })}`);
  const { visibili, Altri } = useLimite(dati, 50);
  const aperti = (dati ?? []).filter((a) => ["inviato", "parziale"].includes(a.stato));

  return (
    <>
      <Titolo titolo="Acquisti" sottotitolo={dati ? `${aperti.length} ordini in arrivo per ${euro(aperti.reduce((s, a) => s + a.totale, 0), true)}` : "Ordini ai fornitori e ricevimento merce"}
        azioni={<>
          <Link href="/app/ai?scheda=riordino" className="btn-secondario"><BrainCircuit className="h-4 w-4" /> Riordino AI</Link>
          {puo("acquisti") && <Link href="/app/acquisti/nuovo" className="btn-primario"><Plus className="h-4 w-4" /> Nuovo ordine</Link>}
        </>} />
      <div className="scheda mb-4 p-4">
        <select className="campo max-w-xs" value={stato} onChange={(e) => setStato(e.target.value)} aria-label="Stato">
          <option value="">Tutti gli stati</option><option value="bozza">Bozze</option><option value="inviato,parziale">In arrivo</option>
          <option value="ricevuto">Ricevuti</option><option value="annullato">Annullati</option>
        </select>
      </div>
      {errore && <Errore messaggio={errore} riprova={ricarica} />}
      {!dati && !errore && <Caricamento />}
      {dati && !dati.length && <div className="scheda"><Vuoto icona={Truck} titolo="Nessun ordine di acquisto" /></div>}
      {dati && dati.length > 0 && (
        <div className="scheda overflow-x-auto p-0">
          <table className="tabella">
            <thead><tr><th>Ordine</th><th>Data</th><th>Fornitore</th><th>Arrivo previsto</th><th>Stato</th><th>Ricevuto</th><th className="num">Totale</th></tr></thead>
            <tbody>{visibili.map((a) => {
              const ritardo = ["inviato", "parziale"].includes(a.stato) && a.dataPrevista && new Date(a.dataPrevista) < new Date();
              return (
                <tr key={a.id} className="cursor-pointer" onClick={() => router.push(`/app/acquisti/${a.id}`)}>
                  <td><Link href={`/app/acquisti/${a.id}`} className="font-medium hover:underline" onClick={(e) => e.stopPropagation()}>{a.numero}</Link>
                    {a.generatoDaAi && <span className="pillola ml-2 bg-viola/15 text-viola">AI</span>}</td>
                  <td>{data(a.data)}</td><td>{a.fornitore.ragioneSociale}</td>
                  <td className={ritardo ? "font-medium text-errore" : ""}>{data(a.dataPrevista)}{ritardo && " · in ritardo"}</td>
                  <td><Stato stato={a.stato} /></td>
                  <td><div className="h-2 w-24 overflow-hidden rounded-full bg-bordo"><div className="h-full gradiente" style={{ width: `${a.avanzamento}%` }} /></div></td>
                  <td className="num font-medium">{euro(a.totale)}</td>
                </tr>
              );
            })}</tbody>
          </table>
        </div>
      )}
      <Altri />
    </>
  );
}
