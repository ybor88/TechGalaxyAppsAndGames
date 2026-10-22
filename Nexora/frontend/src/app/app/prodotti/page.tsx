"use client";
// Catalogo prodotti con ricerca (anche da barcode), filtri ed esportazione CSV. © Roberto Di Flumeri
import { AlertTriangle, Download, Package, Plus, ScanLine } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Scanner } from "@/components/articoli";
import { Caricamento, Errore, Miniatura, Titolo, useDati, useLimite, Vuoto } from "@/components/ui";
import { api, qs, scarica } from "@/lib/api";
import { euro, numero } from "@/lib/formato";
import { useSessione } from "@/lib/sessione";

interface RigaProdotto {
  id: string; sku: string; nome: string; categoria: string | null; unita: string; prezzoVendita: number; costoAcquisto: number; aliquotaIva: number; attivo: boolean;
  varianti: number; gestioneLotti: boolean; gestioneSeriali: boolean; scortaMinima: number; sottoScorta: boolean; immagine: string | null; fornitore: string | null;
  barcode: string | null; giacenza: number; riservata: number; disponibile: number;
}

export default function PaginaProdotti() {
  const { puo } = useSessione();
  const router = useRouter();
  const [q, setQ] = useState("");
  const [categoriaId, setCategoriaId] = useState("");
  const [stato, setStato] = useState("attivi");
  const [sottoScorta, setSottoScorta] = useState(false);
  const [scanner, setScanner] = useState(false);
  const [erroreScan, setErroreScan] = useState<string | null>(null);
  const { dati: categorie } = useDati<{ id: string; nome: string; prodotti: number }[]>("/api/categorie");
  const { dati, errore, ricarica, caricamento } = useDati<RigaProdotto[]>(`/api/prodotti${qs({ q, categoriaId, stato, sottoScorta: sottoScorta || undefined })}`);
  const { visibili, Altri } = useLimite(dati, 60);

  const daBarcode = async (codice: string) => {
    setScanner(false);
    try { router.push(`/app/prodotti/${(await api(`/api/barcode/${encodeURIComponent(codice)}`)).prodottoId}`); }
    catch (e) { setErroreScan(e instanceof Error ? e.message : "Codice non trovato"); }
  };

  return (
    <>
      <Titolo titolo="Prodotti" sottotitolo="Catalogo con SKU, barcode, varianti, prezzi e disponibilità in tutti i magazzini"
        azioni={<>
          <button className="btn-secondario" onClick={() => setScanner(true)}><ScanLine className="h-4 w-4" /> Scansiona</button>
          <button className="btn-secondario" onClick={() => scarica("/api/prodotti-export.csv", "nexora-prodotti.csv")}><Download className="h-4 w-4" /> CSV</button>
          {puo("catalogo") && <Link href="/app/prodotti/nuovo" className="btn-primario"><Plus className="h-4 w-4" /> Nuovo prodotto</Link>}
        </>} />
      <div className="scheda mb-4 grid gap-3 p-4 md:grid-cols-[2fr_1fr_1fr_auto]">
        <input className="campo" placeholder="Cerca per nome, SKU o barcode…" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Cerca prodotti" />
        <select className="campo" value={categoriaId} onChange={(e) => setCategoriaId(e.target.value)} aria-label="Categoria">
          <option value="">Tutte le categorie</option>
          {categorie?.map((c) => <option key={c.id} value={c.id}>{c.nome} ({c.prodotti})</option>)}
        </select>
        <select className="campo" value={stato} onChange={(e) => setStato(e.target.value)} aria-label="Stato">
          <option value="attivi">Attivi</option><option value="disattivi">Disattivati</option><option value="tutti">Tutti</option>
        </select>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={sottoScorta} onChange={(e) => setSottoScorta(e.target.checked)} /> Sotto scorta</label>
      </div>
      {erroreScan && <p className="mb-3 text-sm text-errore" role="alert">{erroreScan}</p>}
      {errore && <Errore messaggio={errore} riprova={ricarica} />}
      {!dati && caricamento && <Caricamento />}
      {dati && !dati.length && <div className="scheda"><Vuoto icona={Package} titolo="Nessun prodotto trovato" testo="Modifica i filtri o crea il primo prodotto del catalogo." /></div>}
      {dati && dati.length > 0 && (
        <div className="scheda overflow-x-auto p-0">
          <table className="tabella">
            <thead><tr><th>Prodotto</th><th>Categoria</th><th className="num">Prezzo</th><th className="num">Margine</th><th className="num">Giacenza</th><th className="num">Disponibile</th><th>Gestione</th></tr></thead>
            <tbody>
              {visibili.map((p) => (
                <tr key={p.id} className="cursor-pointer" onClick={() => router.push(`/app/prodotti/${p.id}`)}>
                  <td>
                    <div className="flex items-center gap-3">
                      <Miniatura file={p.immagine} nome={p.nome} />
                      <div className="min-w-0">
                        <Link href={`/app/prodotti/${p.id}`} className="font-medium hover:underline" onClick={(e) => e.stopPropagation()}>{p.nome}</Link>
                        <p className="font-mono text-xs text-tenue">{p.sku}{p.varianti > 1 ? ` · ${p.varianti} varianti` : ""}{!p.attivo ? " · disattivato" : ""}</p>
                      </div>
                    </div>
                  </td>
                  <td className="text-tenue">{p.categoria ?? "—"}</td>
                  <td className="num">{euro(p.prezzoVendita)}<span className="block text-xs text-tenue">IVA {p.aliquotaIva}%</span></td>
                  <td className="num">{p.prezzoVendita ? `${Math.round(((p.prezzoVendita - p.costoAcquisto) / p.prezzoVendita) * 100)}%` : "—"}</td>
                  <td className="num">{numero(p.giacenza)} {p.unita}{p.riservata > 0 && <span className="block text-xs text-tenue">{numero(p.riservata)} impegnati</span>}</td>
                  <td className="num">
                    <span className={p.sottoScorta ? "font-semibold text-attenzione" : p.disponibile <= 0 ? "text-errore" : ""}>
                      {p.sottoScorta && <AlertTriangle className="mr-1 inline h-3.5 w-3.5" aria-label="Sotto scorta" />}{numero(p.disponibile)}
                    </span>
                    {p.scortaMinima > 0 && <span className="block text-xs text-tenue">min. {numero(p.scortaMinima)}</span>}
                  </td>
                  <td>{p.gestioneLotti ? <span className="pillola bg-ciano/15 text-ciano">Lotti</span> : p.gestioneSeriali ? <span className="pillola bg-viola/15 text-viola">Seriali</span> : <span className="text-tenue">—</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Altri />
      {scanner && <Scanner onCodice={daBarcode} onChiudi={() => setScanner(false)} />}
    </>
  );
}
