"use client";
// Documenti: fatture, preventivi, DDT e note di credito, con stato SdI e incassi. © Roberto Di Flumeri
import { FilePlus2, FileText } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Caricamento, Errore, NOMI_TIPO_DOC, Schede, Stato, Titolo, useDati, useLimite, Vuoto } from "@/components/ui";
import { qs } from "@/lib/api";
import { data, euro } from "@/lib/formato";
import { useSessione } from "@/lib/sessione";

type Tipo = "fattura" | "preventivo" | "ddt" | "nota_credito";
const STATI: Record<Tipo, [string, string][]> = {
  fattura: [["bozza", "Bozze"], ["emessa", "Da incassare"], ["pagata", "Pagate"], ["annullata", "Annullate"]],
  preventivo: [["bozza", "Bozze"], ["inviato", "Inviati"], ["accettato", "Accettati"], ["rifiutato", "Rifiutati"]],
  ddt: [["bozza", "Bozze"], ["emesso", "Emessi"]],
  nota_credito: [["bozza", "Bozze"], ["emessa", "Emesse"]],
};

export default function PaginaDocumenti() {
  const { puo } = useSessione();
  const router = useRouter();
  const [tipo, setTipo] = useState<Tipo>("fattura");
  const [stato, setStato] = useState("");
  const [q, setQ] = useState("");
  const [anno, setAnno] = useState("");
  useEffect(() => { const t = new URLSearchParams(window.location.search).get("tipo") as Tipo | null; if (t) setTipo(t); }, []);
  const { dati, errore, ricarica } = useDati<any[]>(`/api/documenti${qs({ tipo, stato, q, anno })}`);
  const { visibili, Altri } = useLimite(dati, 60);
  const anni = Array.from({ length: 4 }, (_, i) => new Date().getFullYear() - i);

  return (
    <>
      <Titolo titolo="Documenti" sottotitolo="Numerazione progressiva per tipo e anno; fatture e note di credito generano l'XML FatturaPA per lo SdI"
        azioni={puo("documenti") && <Link href={`/app/documenti/nuovo?tipo=${tipo}`} className="btn-primario"><FilePlus2 className="h-4 w-4" /> Nuovo {NOMI_TIPO_DOC[tipo].toLowerCase()}</Link>} />
      <Schede attiva={tipo} onCambia={(t) => { setTipo(t); setStato(""); }} voci={[
        { id: "fattura", etichetta: "Fatture" }, { id: "preventivo", etichetta: "Preventivi" }, { id: "ddt", etichetta: "DDT" }, { id: "nota_credito", etichetta: "Note di credito" },
      ]} />
      <div className="scheda mb-4 grid gap-3 p-4 md:grid-cols-[2fr_1fr_1fr]">
        <input className="campo" placeholder="Cerca per numero o cliente…" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Cerca documenti" />
        <select className="campo" value={stato} onChange={(e) => setStato(e.target.value)} aria-label="Stato">
          <option value="">Tutti gli stati</option>{STATI[tipo].map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <select className="campo" value={anno} onChange={(e) => setAnno(e.target.value)} aria-label="Anno">
          <option value="">Tutti gli anni</option>{anni.map((a) => <option key={a} value={a}>{a}</option>)}
        </select>
      </div>
      {errore && <Errore messaggio={errore} riprova={ricarica} />}
      {!dati && !errore && <Caricamento />}
      {dati && !dati.length && <div className="scheda"><Vuoto icona={FileText} titolo="Nessun documento" /></div>}
      {dati && dati.length > 0 && (
        <div className="scheda overflow-x-auto p-0">
          <table className="tabella">
            <thead><tr><th>Numero</th><th>Data</th><th>Cliente</th><th>Stato</th>{(tipo === "fattura" || tipo === "nota_credito") && <th>SdI</th>}
              <th className="num">Imponibile</th><th className="num">Totale</th>{tipo === "fattura" && <th className="num">Da incassare</th>}</tr></thead>
            <tbody>{visibili.map((d) => {
              const aperto = d.scadenze.filter((s: any) => !s.pagata).reduce((t: number, s: any) => t + s.importo, 0);
              const scaduto = d.scadenze.some((s: any) => !s.pagata && new Date(s.dataScadenza) < new Date());
              return (
                <tr key={d.id} className="cursor-pointer" onClick={() => router.push(`/app/documenti/${d.id}`)}>
                  <td><Link href={`/app/documenti/${d.id}`} className="font-medium hover:underline" onClick={(e) => e.stopPropagation()}>{d.numero ?? "Bozza"}</Link></td>
                  <td>{data(d.data)}</td><td>{d.cliente.ragioneSociale}</td><td><Stato stato={d.stato} /></td>
                  {(tipo === "fattura" || tipo === "nota_credito") && <td>{d.statoSdi ? <Stato stato={d.statoSdi} sdi /> : "—"}</td>}
                  <td className="num">{euro(d.imponibile)}</td><td className="num font-medium">{euro(d.totale)}</td>
                  {tipo === "fattura" && <td className={`num ${scaduto ? "font-semibold text-errore" : aperto ? "text-attenzione" : "text-tenue"}`}>{aperto ? euro(aperto) : "—"}{scaduto && <span className="block text-xs">scaduta</span>}</td>}
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
