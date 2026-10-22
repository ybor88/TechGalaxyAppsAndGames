"use client";
// Ordini di vendita: elenco filtrabile e kanban del flusso bozza → confermato → in preparazione → spedito → consegnato. © Roberto Di Flumeri
import clsx from "clsx";
import { ChevronRight, Columns3, List, Plus, ShoppingCart } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Avviso, Canale, Caricamento, Errore, Stato, Titolo, useAzione, useDati, useLimite, Vuoto } from "@/components/ui";
import { api, qs } from "@/lib/api";
import { data, euro, fa } from "@/lib/formato";
import { useSessione } from "@/lib/sessione";

const COLONNE = ["bozza", "confermato", "in_preparazione", "spedito", "consegnato"] as const;
const PROSSIMO: Record<string, [string, string] | undefined> = {
  bozza: ["confermato", "Conferma"], confermato: ["in_preparazione", "Prepara"], in_preparazione: ["spedito", "Spedisci"], spedito: ["consegnato", "Consegnato"],
};

export default function PaginaOrdini() {
  const { puo } = useSessione();
  const router = useRouter();
  const [vista, setVista] = useState<"elenco" | "kanban">("elenco");
  const [stato, setStato] = useState("");
  const [canale, setCanale] = useState("");
  const [q, setQ] = useState("");
  const azione = useAzione();

  useEffect(() => {
    const p = new URLSearchParams(window.location.search);
    if (p.get("stato")) setStato(p.get("stato")!);
    if (p.get("vista") === "kanban") setVista("kanban");
  }, []);

  const { dati, errore, ricarica } = useDati<any[]>(`/api/ordini${qs({ stato: vista === "kanban" ? COLONNE.join(",") : stato, canale, q, limite: vista === "kanban" ? 400 : 300 })}`);
  const { visibili, Altri } = useLimite(dati, 60);

  const avanza = (o: any) => {
    const p = PROSSIMO[o.stato];
    if (p) azione.esegui(async () => { await api(`/api/ordini/${o.id}/stato`, { metodo: "POST", corpo: { stato: p[0] } }); await ricarica(); });
  };

  return (
    <>
      <Titolo titolo="Ordini di vendita" sottotitolo="Ordini diretti e dai canali e-commerce: la conferma impegna la merce, la spedizione emette il DDT"
        azioni={<>
          <div className="flex rounded-xl border border-bordo bg-pannello p-0.5" role="group" aria-label="Vista">
            <button className={clsx("btn px-3 py-1.5", vista === "elenco" ? "gradiente text-white" : "text-tenue")} onClick={() => setVista("elenco")} aria-pressed={vista === "elenco"}><List className="h-4 w-4" /> Elenco</button>
            <button className={clsx("btn px-3 py-1.5", vista === "kanban" ? "gradiente text-white" : "text-tenue")} onClick={() => setVista("kanban")} aria-pressed={vista === "kanban"}><Columns3 className="h-4 w-4" /> Kanban</button>
          </div>
          {puo("ordini") && <Link href="/app/ordini/nuovo" className="btn-primario"><Plus className="h-4 w-4" /> Nuovo ordine</Link>}
        </>} />
      <div className="scheda mb-4 grid gap-3 p-4 md:grid-cols-[2fr_1fr_1fr]">
        <input className="campo" placeholder="Cerca per numero, cliente o riferimento e-commerce…" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Cerca ordini" />
        {vista === "elenco" ? (
          <select className="campo" value={stato} onChange={(e) => setStato(e.target.value)} aria-label="Stato">
            <option value="">Tutti gli stati</option>
            <option value="bozza,confermato,in_preparazione">Da evadere</option>
            {[...COLONNE, "annullato"].map((s) => <option key={s} value={s}>{s.replace("_", " ").replace(/^./, (c) => c.toUpperCase())}</option>)}
          </select>
        ) : <div />}
        <select className="campo" value={canale} onChange={(e) => setCanale(e.target.value)} aria-label="Canale">
          <option value="">Tutti i canali</option><option value="diretto">Diretto</option><option value="shopify">Shopify</option><option value="woocommerce">WooCommerce</option><option value="magento">Magento</option>
        </select>
      </div>
      {azione.errore && <Avviso tipo="errore" className="mb-3">{azione.errore}</Avviso>}
      {errore && <Errore messaggio={errore} riprova={ricarica} />}
      {!dati && !errore && <Caricamento />}
      {dati && !dati.length && <div className="scheda"><Vuoto icona={ShoppingCart} titolo="Nessun ordine" testo="Crea un ordine o collega un canale e-commerce dalle integrazioni." /></div>}

      {dati && dati.length > 0 && vista === "elenco" && (
        <>
          <div className="scheda overflow-x-auto p-0">
            <table className="tabella">
              <thead><tr><th>Ordine</th><th>Data</th><th>Cliente</th><th>Canale</th><th>Stato</th><th className="num">Righe</th><th className="num">Totale</th></tr></thead>
              <tbody>{visibili.map((o) => (
                <tr key={o.id} className="cursor-pointer" onClick={() => router.push(`/app/ordini/${o.id}`)}>
                  <td><Link href={`/app/ordini/${o.id}`} className="font-medium hover:underline" onClick={(e) => e.stopPropagation()}>{o.numero}</Link>
                    {o.riferimentoEsterno && <p className="text-xs text-tenue">{o.riferimentoEsterno}</p>}</td>
                  <td className="whitespace-nowrap">{data(o.data)}</td><td>{o.cliente.ragioneSociale}</td><td><Canale canale={o.canale} /></td>
                  <td><Stato stato={o.stato} /></td><td className="num">{o.righe}</td><td className="num font-medium">{euro(o.totale)}</td>
                </tr>
              ))}</tbody>
            </table>
          </div>
          <Altri />
        </>
      )}

      {dati && vista === "kanban" && (
        <div className="grid gap-4 overflow-x-auto pb-2 lg:grid-cols-5">
          {COLONNE.map((col) => {
            const ordini = dati.filter((o) => o.stato === col);
            const mostrati = col === "consegnato" ? ordini.slice(0, 15) : ordini;
            return (
              <section key={col} className="min-w-[240px] rounded-2xl border border-bordo bg-pannello/60 p-3" aria-label={col}>
                <header className="mb-3 flex items-center justify-between"><Stato stato={col} /><span className="text-sm font-semibold text-tenue">{ordini.length}</span></header>
                <div className="space-y-2">
                  {mostrati.map((o) => (
                    <article key={o.id} className="rounded-xl border border-bordo bg-pannello p-3 shadow-sm">
                      <div className="flex items-start justify-between gap-2">
                        <Link href={`/app/ordini/${o.id}`} className="font-semibold hover:underline">{o.numero}</Link>
                        <Canale canale={o.canale} />
                      </div>
                      <p className="mt-1 truncate text-sm">{o.cliente.ragioneSociale}</p>
                      <p className="text-xs text-tenue">{euro(o.totale)} · {o.righe} righe · {fa(o.data)}</p>
                      {PROSSIMO[col] && (puo("ordini") || (col !== "bozza" && puo("spedizioni"))) && (
                        <button className="btn-secondario btn-piccolo mt-2 w-full" disabled={azione.inCorso} onClick={() => avanza(o)}>
                          {PROSSIMO[col]![1]} <ChevronRight className="h-3.5 w-3.5" />
                        </button>
                      )}
                    </article>
                  ))}
                  {col === "consegnato" && ordini.length > mostrati.length && <p className="text-center text-xs text-tenue">e altri {ordini.length - mostrati.length}</p>}
                  {!ordini.length && <p className="py-4 text-center text-xs text-tenue">Nessun ordine</p>}
                </div>
              </section>
            );
          })}
        </div>
      )}
    </>
  );
}
