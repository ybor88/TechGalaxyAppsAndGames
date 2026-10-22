"use client";
// Conta di inventario: scansione continua dei barcode (ogni lettura somma un'unità) e chiusura con rettifiche automatiche. © Roberto Di Flumeri
import { CheckCheck, ScanLine } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useState } from "react";
import { Scanner } from "@/components/articoli";
import { Avviso, Caricamento, Errore, Kpi, Stato, Titolo, useAzione, useDati } from "@/components/ui";
import { api } from "@/lib/api";
import { dataOra, numero } from "@/lib/formato";
import { useSessione } from "@/lib/sessione";

export default function Inventario() {
  const { id } = useParams<{ id: string }>();
  const { puo } = useSessione();
  const { dati: inv, errore, ricarica, setDati } = useDati<any>(`/api/inventari/${id}`);
  const [scanner, setScanner] = useState(false);
  const [filtro, setFiltro] = useState<"tutti" | "da_contare" | "differenze">("tutti");
  const [q, setQ] = useState("");
  const [ultimo, setUltimo] = useState<string | null>(null);
  const [esito, setEsito] = useState<string | null>(null);
  const azione = useAzione();

  const salvaRiga = useCallback(async (varianteId: string, quantitaContata: number | null, somma = false) => {
    const r = await api(`/api/inventari/${id}/righe`, { metodo: "PATCH", corpo: { varianteId, quantitaContata, somma } });
    setDati((d: any) => d && ({ ...d, righe: d.righe.map((x: any) => (x.varianteId === varianteId
      ? { ...x, quantitaContata: r.quantitaContata, differenza: r.quantitaContata === null ? null : Math.round((r.quantitaContata - x.quantitaSistema) * 1000) / 1000 } : x)) }));
    return r;
  }, [id, setDati]);

  const daScansione = useCallback(async (codice: string) => {
    const riga = inv?.righe.find((r: any) => r.barcode === codice || r.sku === codice);
    if (!riga) { setUltimo(`Codice ${codice} non presente in questo inventario`); return; }
    const r = await salvaRiga(riga.varianteId, 1, true);
    setUltimo(`${riga.articolo}: contati ${numero(r.quantitaContata)}`);
  }, [inv, salvaRiga]);

  if (errore) return <Errore messaggio={errore} riprova={ricarica} />;
  if (!inv) return <Caricamento />;
  const aperto = inv.stato === "aperto" && puo("magazzino");
  const contate = inv.righe.filter((r: any) => r.quantitaContata !== null);
  const differenze = contate.filter((r: any) => r.differenza);
  const righe = inv.righe.filter((r: any) => (filtro === "da_contare" ? r.quantitaContata === null : filtro === "differenze" ? r.differenza : true)
    && (!q || `${r.articolo} ${r.sku} ${r.barcode ?? ""}`.toLowerCase().includes(q.toLowerCase())));

  const chiudi = () => {
    if (!confirm(`Chiudere l'inventario? Verranno registrate ${differenze.length} rettifiche; gli articoli non contati restano invariati.`)) return;
    azione.esegui(async () => { const r = await api(`/api/inventari/${id}/chiudi`, { metodo: "POST" }); setEsito(`Inventario chiuso con ${r.rettifiche} rettifiche di magazzino.`); await ricarica(); });
  };

  return (
    <>
      <Titolo indietro={<Link href="/app/magazzino?scheda=inventari" className="link">← Inventari</Link>}
        titolo={<span className="flex items-center gap-3">Inventario · {inv.magazzino.nome} <Stato stato={inv.stato} /></span>}
        sottotitolo={`Aperto il ${dataOra(inv.createdAt)}${inv.chiusoAt ? ` · chiuso il ${dataOra(inv.chiusoAt)}` : ""}`}
        azioni={aperto && <>
          <button className="btn-primario" onClick={() => setScanner(true)}><ScanLine className="h-4 w-4" /> Conta con barcode</button>
          <button className="btn-secondario" onClick={chiudi} disabled={azione.inCorso}><CheckCheck className="h-4 w-4" /> Chiudi e rettifica</button>
        </>} />
      {esito && <Avviso tipo="ok" className="mb-4">{esito}</Avviso>}
      {azione.errore && <Avviso tipo="errore" className="mb-4">{azione.errore}</Avviso>}
      <section className="mb-6 grid gap-4 sm:grid-cols-3">
        <Kpi etichetta="Articoli contati" valore={`${contate.length} / ${inv.righe.length}`} nota={`${Math.round((contate.length / Math.max(1, inv.righe.length)) * 100)}% completato`} />
        <Kpi etichetta="Con differenze" valore={differenze.length} tono={differenze.length ? "attenzione" : "ok"} />
        <Kpi etichetta="Differenza netta" valore={numero(differenze.reduce((s: number, r: any) => s + r.differenza, 0))} tono="viola" nota="unità (eccedenze meno ammanchi)" />
      </section>
      <div className="scheda mb-4 grid gap-3 p-4 md:grid-cols-[2fr_1fr]">
        <input className="campo" placeholder="Filtra articoli…" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Filtra" />
        <select className="campo" value={filtro} onChange={(e) => setFiltro(e.target.value as typeof filtro)} aria-label="Mostra">
          <option value="tutti">Tutti gli articoli</option><option value="da_contare">Da contare</option><option value="differenze">Solo differenze</option>
        </select>
      </div>
      <div className="scheda overflow-x-auto p-0">
        <table className="tabella">
          <thead><tr><th>Articolo</th><th>Barcode</th><th className="num">A sistema</th><th className="num">Contati</th><th className="num">Differenza</th></tr></thead>
          <tbody>{righe.map((r: any) => (
            <tr key={r.id}>
              <td><p className="font-medium">{r.articolo}</p><p className="font-mono text-xs text-tenue">{r.sku}</p></td>
              <td className="font-mono text-xs">{r.barcode ?? "—"}</td>
              <td className="num">{numero(r.quantitaSistema)} {r.unita}</td>
              <td className="num">{aperto
                ? <input className="campo ml-auto w-24 text-right" defaultValue={r.quantitaContata ?? ""} key={`${r.id}-${r.quantitaContata}`} inputMode="decimal" aria-label={`Contati ${r.articolo}`}
                  onBlur={(e) => {
                    const v = e.target.value.trim();
                    const n = v === "" ? null : Number(v.replace(",", "."));
                    if (n !== r.quantitaContata && (n === null || n >= 0)) azione.esegui(() => salvaRiga(r.varianteId, n));
                  }} />
                : r.quantitaContata === null ? "—" : numero(r.quantitaContata)}</td>
              <td className={`num font-semibold ${r.differenza > 0 ? "text-ok" : r.differenza < 0 ? "text-errore" : "text-tenue"}`}>
                {r.differenza === null ? "—" : `${r.differenza > 0 ? "+" : ""}${numero(r.differenza)}`}</td>
            </tr>
          ))}</tbody>
        </table>
      </div>
      {scanner && (
        <div>
          <Scanner continuo onCodice={(c) => { daScansione(c).catch((e) => setUltimo(e instanceof Error ? e.message : "Errore")); }} onChiudi={() => setScanner(false)} />
          {ultimo && <div className="fixed bottom-4 left-1/2 z-[1100] -translate-x-1/2 rounded-xl bg-notte px-4 py-2 text-sm text-white shadow-lg" role="status">{ultimo}</div>}
        </div>
      )}
    </>
  );
}
