"use client";
// Scheda prodotto (nuovo o modifica): SKU, barcode, categoria, prezzi, IVA, lotti/seriali, scorte e varianti. © Roberto Di Flumeri
import { Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { api } from "@/lib/api";
import { aCentesimi, daCentesimi } from "@/lib/formato";
import { Avviso, Campo, Interruttore, useAzione, useDati } from "./ui";

export const ALIQUOTE = [22, 10, 5, 4, 0];
export const NATURE: Record<string, string> = {
  N1: "N1 · Escluse ex art. 15", "N2.1": "N2.1 · Non soggette (artt. 7-7septies)", "N2.2": "N2.2 · Non soggette, altri casi",
  "N3.1": "N3.1 · Esportazioni", "N3.2": "N3.2 · Cessioni intracomunitarie", "N3.5": "N3.5 · Dichiarazione d'intento", N4: "N4 · Esenti",
  N5: "N5 · Regime del margine", "N6.1": "N6.1 · Reverse charge rottami", "N6.9": "N6.9 · Reverse charge altri casi", N7: "N7 · IVA assolta in altro stato UE",
};

type Variante = { sku: string; nome: string; barcode: string; prezzo: string; attributi: string };

export interface ProdottoBase {
  id?: string; sku: string; nome: string; descrizione: string | null; categoriaId: string | null; unita: string; prezzoVendita: number; costoAcquisto: number;
  aliquotaIva: number; naturaIva: string | null; barcode: string | null; gestioneLotti: boolean; gestioneSeriali: boolean; scortaMinima: number;
  leadTimeGiorni: number; fornitoreId: string | null; attivo: boolean;
}

export default function FormProdotto({ iniziale, onSalvato, haMovimenti }: { iniziale?: ProdottoBase; onSalvato: (id: string) => void; haMovimenti?: boolean }) {
  const { dati: categorie, ricarica: ricaricaCategorie } = useDati<{ id: string; nome: string }[]>("/api/categorie");
  const { dati: fornitori } = useDati<{ id: string; ragioneSociale: string; leadTimeGiorni: number }[]>("/api/fornitori");
  const [p, setP] = useState({
    sku: iniziale?.sku ?? "", nome: iniziale?.nome ?? "", descrizione: iniziale?.descrizione ?? "", categoriaId: iniziale?.categoriaId ?? "",
    unita: iniziale?.unita ?? "pz", prezzo: daCentesimi(iniziale?.prezzoVendita ?? null), costo: daCentesimi(iniziale?.costoAcquisto ?? null),
    aliquotaIva: iniziale?.aliquotaIva ?? 22, naturaIva: iniziale?.naturaIva ?? "", barcode: iniziale?.barcode ?? "", gestione: iniziale?.gestioneSeriali ? "seriali" : iniziale?.gestioneLotti ? "lotti" : "nessuna",
    scortaMinima: String(iniziale?.scortaMinima ?? 0), leadTimeGiorni: String(iniziale?.leadTimeGiorni ?? 7), fornitoreId: iniziale?.fornitoreId ?? "", attivo: iniziale?.attivo ?? true,
  });
  const [varianti, setVarianti] = useState<Variante[]>([]);
  const [nuovaCategoria, setNuovaCategoria] = useState("");
  const { inCorso, errore, esegui, setErrore } = useAzione();
  const set = (k: keyof typeof p) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => setP({ ...p, [k]: e.target.value });
  const margine = (() => {
    const pr = aCentesimi(p.prezzo), co = aCentesimi(p.costo);
    return pr && co !== null ? Math.round(((pr - co) / pr) * 1000) / 10 : null;
  })();

  const salva = (e: React.FormEvent) => {
    e.preventDefault();
    const prezzoVendita = aCentesimi(p.prezzo), costoAcquisto = aCentesimi(p.costo || "0");
    if (prezzoVendita === null) return setErrore("Prezzo di vendita non valido");
    if (costoAcquisto === null) return setErrore("Costo di acquisto non valido");
    esegui(async () => {
      const corpo: Record<string, unknown> = {
        sku: p.sku.trim(), nome: p.nome.trim(), descrizione: p.descrizione || null, categoriaId: p.categoriaId || null, unita: p.unita, prezzoVendita, costoAcquisto,
        aliquotaIva: Number(p.aliquotaIva), naturaIva: Number(p.aliquotaIva) === 0 ? p.naturaIva || null : null, barcode: p.barcode.trim() || null,
        gestioneLotti: p.gestione === "lotti", gestioneSeriali: p.gestione === "seriali", scortaMinima: Number(p.scortaMinima.replace(",", ".")) || 0,
        leadTimeGiorni: Number(p.leadTimeGiorni) || 0, fornitoreId: p.fornitoreId || null, attivo: p.attivo,
      };
      if (!iniziale?.id && varianti.length) {
        corpo.varianti = varianti.map((v) => {
          const attributi = Object.fromEntries(v.attributi.split(",").map((x) => x.split(":").map((y) => y.trim())).filter((x) => x.length === 2 && x[0] && x[1]));
          return { sku: v.sku.trim(), nome: v.nome.trim(), barcode: v.barcode.trim() || null, prezzo: v.prezzo ? aCentesimi(v.prezzo) : null, attributi: Object.keys(attributi).length ? attributi : null };
        });
      }
      const r = iniziale?.id
        ? await api(`/api/prodotti/${iniziale.id}`, { metodo: "PATCH", corpo })
        : await api("/api/prodotti", { metodo: "POST", corpo });
      onSalvato(r.id);
    });
  };

  const aggiungiCategoria = () => esegui(async () => {
    const c = await api("/api/categorie", { metodo: "POST", corpo: { nome: nuovaCategoria.trim() } });
    await ricaricaCategorie();
    setP({ ...p, categoriaId: c.id });
    setNuovaCategoria("");
  });

  return (
    <form onSubmit={salva} className="space-y-6">
      <section className="scheda grid gap-4 md:grid-cols-2">
        <h2 className="font-semibold md:col-span-2">Dati principali</h2>
        <Campo etichetta="Nome del prodotto" className="md:col-span-2"><input className="campo" value={p.nome} onChange={set("nome")} required minLength={2} maxLength={120} /></Campo>
        <Campo etichetta="SKU (codice articolo)" aiuto="Lettere, numeri, punto e trattini"><input className="campo font-mono uppercase" value={p.sku} onChange={set("sku")} required pattern="[A-Za-z0-9._\-]+" maxLength={40} /></Campo>
        <Campo etichetta="Barcode (EAN-13, EAN-8 o codice interno)" aiuto="Per gli EAN viene verificata la cifra di controllo"><input className="campo font-mono" value={p.barcode} onChange={set("barcode")} maxLength={32} inputMode="numeric" /></Campo>
        <Campo etichetta="Categoria">
          <select className="campo" value={p.categoriaId} onChange={set("categoriaId")}>
            <option value="">Senza categoria</option>
            {categorie?.map((c) => <option key={c.id} value={c.id}>{c.nome}</option>)}
          </select>
        </Campo>
        <Campo etichetta="Nuova categoria">
          <span className="flex gap-2">
            <input className="campo" value={nuovaCategoria} onChange={(e) => setNuovaCategoria(e.target.value)} placeholder="es. Cialde" />
            <button type="button" className="btn-secondario" disabled={nuovaCategoria.trim().length < 2} onClick={aggiungiCategoria}><Plus className="h-4 w-4" /></button>
          </span>
        </Campo>
        <Campo etichetta="Descrizione" className="md:col-span-2"><textarea className="campo" rows={3} value={p.descrizione} onChange={set("descrizione")} maxLength={2000} /></Campo>
      </section>

      <section className="scheda grid gap-4 md:grid-cols-4">
        <h2 className="font-semibold md:col-span-4">Prezzi e IVA</h2>
        <Campo etichetta="Prezzo di vendita (€, IVA esclusa)"><input className="campo" value={p.prezzo} onChange={set("prezzo")} required inputMode="decimal" placeholder="0,00" /></Campo>
        <Campo etichetta="Costo di acquisto (€)" aiuto={margine !== null ? `Margine ${margine.toLocaleString("it-IT")}%` : undefined}><input className="campo" value={p.costo} onChange={set("costo")} inputMode="decimal" placeholder="0,00" /></Campo>
        <Campo etichetta="Aliquota IVA">
          <select className="campo" value={p.aliquotaIva} onChange={(e) => setP({ ...p, aliquotaIva: Number(e.target.value) })}>
            {ALIQUOTE.map((a) => <option key={a} value={a}>{a}%</option>)}
          </select>
        </Campo>
        <Campo etichetta="Unità di misura"><select className="campo" value={p.unita} onChange={set("unita")}>
          {[["pz", "Pezzi"], ["conf", "Confezioni"], ["kg", "Chilogrammi"], ["lt", "Litri"], ["m", "Metri"]].map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select></Campo>
        {Number(p.aliquotaIva) === 0 && (
          <Campo etichetta="Natura dell'operazione (obbligatoria con IVA 0%)" className="md:col-span-2"><select className="campo" value={p.naturaIva} onChange={set("naturaIva")} required>
            <option value="">Seleziona…</option>{Object.entries(NATURE).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select></Campo>
        )}
      </section>

      <section className="scheda grid gap-4 md:grid-cols-4">
        <h2 className="font-semibold md:col-span-4">Magazzino e riordino</h2>
        <Campo etichetta="Tracciabilità" aiuto={haMovimenti ? "Non modificabile dopo i primi movimenti" : "Lotti con scadenza (FEFO) o numeri di serie"}>
          <select className="campo" value={p.gestione} onChange={set("gestione")} disabled={haMovimenti}>
            <option value="nessuna">Nessuna</option><option value="lotti">Lotti e scadenze</option><option value="seriali">Numeri di serie</option>
          </select>
        </Campo>
        <Campo etichetta="Scorta minima"><input className="campo" value={p.scortaMinima} onChange={set("scortaMinima")} inputMode="decimal" /></Campo>
        <Campo etichetta="Fornitore abituale">
          <select className="campo" value={p.fornitoreId} onChange={(e) => {
            const f = fornitori?.find((x) => x.id === e.target.value);
            setP({ ...p, fornitoreId: e.target.value, leadTimeGiorni: f ? String(f.leadTimeGiorni) : p.leadTimeGiorni });
          }}>
            <option value="">Nessuno</option>{fornitori?.map((f) => <option key={f.id} value={f.id}>{f.ragioneSociale}</option>)}
          </select>
        </Campo>
        <Campo etichetta="Tempo di consegna (giorni)"><input className="campo" type="number" min={0} max={365} value={p.leadTimeGiorni} onChange={set("leadTimeGiorni")} /></Campo>
        {iniziale?.id && <div className="md:col-span-4"><Interruttore attivo={p.attivo} onChange={(v) => setP({ ...p, attivo: v })} etichetta="Prodotto attivo" descrizione="I prodotti disattivati non compaiono negli ordini e nelle ricerche" /></div>}
      </section>

      {!iniziale?.id && (
        <section className="scheda space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h2 className="font-semibold">Varianti (facoltative)</h2>
              <p className="text-sm text-tenue">Taglie, colori o formati, ognuno con SKU, barcode e giacenza propri. Senza varianti il prodotto ha un&apos;unica variante «Standard».</p>
            </div>
            <button type="button" className="btn-secondario" onClick={() => setVarianti([...varianti, { sku: `${p.sku || "SKU"}-${varianti.length + 1}`, nome: "", barcode: "", prezzo: "", attributi: "" }])}>
              <Plus className="h-4 w-4" /> Aggiungi variante
            </button>
          </div>
          {varianti.map((v, i) => (
            <div key={i} className="grid gap-2 rounded-xl border border-bordo p-3 md:grid-cols-[1fr_1fr_1fr_0.7fr_1.2fr_auto]">
              {(["sku", "nome", "barcode", "prezzo", "attributi"] as const).map((k) => (
                <input key={k} className="campo" value={v[k]} aria-label={k} required={k === "sku" || k === "nome"}
                  placeholder={{ sku: "SKU", nome: "Nome (es. Taglia M)", barcode: "Barcode", prezzo: "Prezzo (vuoto = base)", attributi: "taglia: M, colore: Blu" }[k]}
                  onChange={(e) => setVarianti(varianti.map((x, j) => (j === i ? { ...x, [k]: e.target.value } : x)))} />
              ))}
              <button type="button" className="btn-secondario px-2" aria-label="Rimuovi variante" onClick={() => setVarianti(varianti.filter((_, j) => j !== i))}><Trash2 className="h-4 w-4" /></button>
            </div>
          ))}
        </section>
      )}

      {errore && <Avviso tipo="errore">{errore}</Avviso>}
      <div className="flex justify-end gap-2"><button className="btn-primario" disabled={inCorso}>{iniziale?.id ? "Salva modifiche" : "Crea prodotto"}</button></div>
    </form>
  );
}
