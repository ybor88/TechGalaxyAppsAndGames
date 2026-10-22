"use client";
// Scheda prodotto: varianti, giacenze per sede, lotti e seriali, immagini, etichette barcode e movimenti. © Roberto Di Flumeri
import { ArrowLeftRight, Barcode, BrainCircuit, ImagePlus, Pencil, Plus, Printer, Trash2 } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import FormProdotto from "@/components/FormProdotto";
import { Avviso, Caricamento, Errore, Kpi, Modale, NOMI_MOVIMENTO, Titolo, useAzione, useDati } from "@/components/ui";
import { api, blob, caricaFile } from "@/lib/api";
import { aCentesimi, data, dataOra, euro, numero } from "@/lib/formato";
import { useSessione } from "@/lib/sessione";

export default function SchedaProdotto() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { puo } = useSessione();
  const { dati: p, errore, ricarica } = useDati<any>(`/api/prodotti/${id}`);
  const [modifica, setModifica] = useState(false);
  const [etichetta, setEtichetta] = useState<{ sku: string; url: string } | null>(null);
  const [nuovaVariante, setNuovaVariante] = useState(false);
  const [messaggio, setMessaggio] = useState<string | null>(null);
  const azione = useAzione();

  useEffect(() => () => { if (etichetta) URL.revokeObjectURL(etichetta.url); }, [etichetta]);

  if (errore) return <Errore messaggio={errore} riprova={ricarica} />;
  if (!p) return <Caricamento />;
  const scrive = puo("catalogo");

  const mostraEtichetta = (v: { id: string; sku: string }) => azione.esegui(async () => setEtichetta({ sku: v.sku, url: URL.createObjectURL(await blob(`/api/varianti/${v.id}/etichetta.png`)) }));
  const elimina = () => {
    if (!confirm(`Eliminare «${p.nome}»? Se il prodotto ha uno storico verrà solo disattivato.`)) return;
    azione.esegui(async () => {
      const r = await api(`/api/prodotti/${id}`, { metodo: "DELETE" });
      if (r.eliminato) router.replace("/app/prodotti");
      else { setMessaggio(r.messaggio); ricarica(); }
    });
  };
  const carica = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    e.target.value = "";
    if (f) azione.esegui(async () => { await caricaFile(`/api/prodotti/${id}/immagini`, f); await ricarica(); });
  };

  if (modifica) {
    return (
      <>
        <Titolo titolo={`Modifica · ${p.nome}`} indietro={<button className="link" onClick={() => setModifica(false)}>← Torna alla scheda</button>} />
        <FormProdotto iniziale={p} haMovimenti={p.movimenti.length > 0} onSalvato={() => { setModifica(false); ricarica(); }} />
      </>
    );
  }

  return (
    <>
      <Titolo indietro={<Link href="/app/prodotti" className="link">← Prodotti</Link>}
        titolo={<span className="flex items-center gap-3">{p.nome}{!p.attivo && <span className="pillola bg-errore/15 text-errore">Disattivato</span>}</span>}
        sottotitolo={<><span className="font-mono">{p.sku}</span>{p.categoria ? ` · ${p.categoria.nome}` : ""}{p.fornitore ? ` · fornitore ${p.fornitore.ragioneSociale}` : ""}</>}
        azioni={<>
          <Link href={`/app/ai?varianteId=${p.varianti[0]?.id}`} className="btn-secondario"><BrainCircuit className="h-4 w-4" /> Previsione</Link>
          {puo("magazzino") && <Link href={`/app/magazzino/movimento?varianteId=${p.varianti[0]?.id}`} className="btn-secondario"><ArrowLeftRight className="h-4 w-4" /> Movimento</Link>}
          {scrive && <button className="btn-secondario" onClick={() => setModifica(true)}><Pencil className="h-4 w-4" /> Modifica</button>}
          {scrive && <button className="btn-secondario text-errore" onClick={elimina}><Trash2 className="h-4 w-4" /> Elimina</button>}
        </>} />
      {messaggio && <Avviso tipo="info" className="mb-4">{messaggio}</Avviso>}
      {azione.errore && <Avviso tipo="errore" className="mb-4">{azione.errore}</Avviso>}

      <section className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi etichetta="Prezzo di vendita" valore={euro(p.prezzoVendita)} nota={`IVA ${p.aliquotaIva}%${p.naturaIva ? ` · ${p.naturaIva}` : ""} · per ${p.unita}`} />
        <Kpi etichetta="Costo medio" valore={euro(p.costoAcquisto)} nota={`margine ${numero(p.margine)}%`} tono="viola" />
        <Kpi etichetta="Disponibile" valore={`${numero(p.disponibile)} ${p.unita}`} tono={p.scortaMinima && p.disponibile < p.scortaMinima ? "attenzione" : "ok"}
          nota={`giacenza ${numero(p.giacenza)} · impegnati ${numero(p.riservata)} · min. ${numero(p.scortaMinima)}`} />
        <Kpi etichetta="Venduto ultimi 90 giorni" valore={`${numero(p.venduto90giorni)} ${p.unita}`} tono="ciano" nota={`riordino in ${p.leadTimeGiorni} giorni`} />
      </section>

      <div className="grid gap-6 xl:grid-cols-3">
        <section className="scheda xl:col-span-2">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="font-semibold">Varianti e giacenze</h2>
            {scrive && <button className="btn-secondario btn-piccolo" onClick={() => setNuovaVariante(true)}><Plus className="h-3.5 w-3.5" /> Variante</button>}
          </div>
          <div className="overflow-x-auto">
            <table className="tabella">
              <thead><tr><th>Variante</th><th>Barcode</th><th className="num">Prezzo</th><th>Per sede</th><th className="num">Disponibile</th><th /></tr></thead>
              <tbody>
                {p.varianti.map((v: any) => (
                  <tr key={v.id}>
                    <td><p className="font-medium">{v.nome}</p><p className="font-mono text-xs text-tenue">{v.sku}</p>
                      {v.attributi && <p className="text-xs text-tenue">{Object.entries(v.attributi).map(([k, x]) => `${k}: ${x}`).join(" · ")}</p>}</td>
                    <td className="font-mono text-xs">{v.barcode ?? p.barcode ?? "—"}</td>
                    <td className="num">{euro(v.prezzo ?? p.prezzoVendita)}</td>
                    <td className="text-xs">{v.giacenze.filter((g: any) => g.quantita || g.riservata).map((g: any) => (
                      <span key={g.id} className="mr-1 inline-block rounded-lg bg-fondo px-2 py-0.5">{g.magazzino.nome}: <strong>{numero(g.quantita)}</strong>{g.riservata ? ` (${numero(g.riservata)} imp.)` : ""}</span>
                    ))}{!v.giacenze.some((g: any) => g.quantita) && <span className="text-tenue">nessuna giacenza</span>}</td>
                    <td className="num font-semibold">{numero(v.disponibile)}</td>
                    <td><button className="btn-secondario btn-piccolo" onClick={() => mostraEtichetta(v)} title="Etichetta barcode"><Barcode className="h-3.5 w-3.5" /> Etichetta</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {p.descrizione && <p className="mt-4 text-sm text-tenue">{p.descrizione}</p>}
        </section>

        <section className="scheda">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="font-semibold">Immagini</h2>
            {scrive && <label className="btn-secondario btn-piccolo cursor-pointer"><ImagePlus className="h-3.5 w-3.5" /> Carica
              <input type="file" accept="image/jpeg,image/png,image/webp" className="sr-only" onChange={carica} /></label>}
          </div>
          {p.immagini.length ? (
            <div className="grid grid-cols-2 gap-3">
              {p.immagini.map((im: any) => (
                <div key={im.id} className="group relative">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={`/api/file/${im.file}`} alt={p.nome} className="aspect-square w-full rounded-xl object-cover" />
                  {scrive && <button className="absolute right-2 top-2 rounded-lg bg-black/60 p-1.5 text-white opacity-0 transition group-hover:opacity-100 focus:opacity-100" aria-label="Elimina immagine"
                    onClick={() => azione.esegui(async () => { await api(`/api/immagini/${im.id}`, { metodo: "DELETE" }); await ricarica(); })}><Trash2 className="h-4 w-4" /></button>}
                </div>
              ))}
            </div>
          ) : <p className="text-sm text-tenue">Nessuna immagine. Formati JPEG, PNG o WEBP, fino a 8 per prodotto.</p>}
        </section>
      </div>

      {p.gestioneLotti && (
        <section className="scheda mt-6">
          <h2 className="mb-3 font-semibold">Lotti (prelievo FEFO: prima scade, prima esce)</h2>
          <div className="overflow-x-auto"><table className="tabella">
            <thead><tr><th>Lotto</th><th>Variante</th><th>Scadenza</th><th>Sedi</th><th className="num">Quantità</th></tr></thead>
            <tbody>{p.varianti.flatMap((v: any) => v.lotti.filter((l: any) => l.quantita > 0).map((l: any) => {
              const giorni = l.scadenza ? Math.ceil((new Date(l.scadenza).getTime() - Date.now()) / 86400_000) : null;
              return (
                <tr key={l.id}>
                  <td className="font-mono">{l.codice}</td><td>{v.nome}</td>
                  <td className={giorni !== null && giorni <= 30 ? "font-semibold text-errore" : ""}>{data(l.scadenza)}{giorni !== null && giorni <= 30 && ` (${giorni} gg)`}</td>
                  <td className="text-xs">{l.perMagazzino.map((m: any) => `${m.magazzino}: ${numero(m.quantita)}`).join(" · ")}</td>
                  <td className="num">{numero(l.quantita)}</td>
                </tr>
              );
            }))}</tbody>
          </table></div>
        </section>
      )}

      {p.gestioneSeriali && (
        <section className="scheda mt-6">
          <h2 className="mb-3 font-semibold">Numeri di serie disponibili</h2>
          <div className="flex flex-wrap gap-2">
            {p.varianti.flatMap((v: any) => v.seriali).map((s: any) => (
              <span key={s.id} className={`pillola font-mono ${s.stato === "riservato" ? "bg-attenzione/15 text-attenzione" : "bg-fondo"}`}>{s.numero}{s.stato === "riservato" ? " · riservato" : ""}</span>
            ))}
            {!p.varianti.some((v: any) => v.seriali.length) && <p className="text-sm text-tenue">Nessun seriale in giacenza.</p>}
          </div>
        </section>
      )}

      <section className="scheda mt-6">
        <h2 className="mb-3 font-semibold">Ultimi movimenti</h2>
        {p.movimenti.length ? (
          <div className="overflow-x-auto"><table className="tabella">
            <thead><tr><th>Data</th><th>Tipo</th><th>Variante</th><th>Sede</th><th className="num">Quantità</th><th>Causale</th></tr></thead>
            <tbody>{p.movimenti.map((m: any) => (
              <tr key={m.id}>
                <td className="whitespace-nowrap">{dataOra(m.createdAt)}</td><td>{NOMI_MOVIMENTO[m.tipo] ?? m.tipo}</td><td className="font-mono text-xs">{m.sku}</td>
                <td>{m.magazzino}{m.magazzinoDest ? ` → ${m.magazzinoDest}` : ""}</td>
                <td className={`num font-medium ${["vendita", "scarico"].includes(m.tipo) || m.quantita < 0 ? "text-errore" : "text-ok"}`}>
                  {["vendita", "scarico"].includes(m.tipo) ? "−" : m.tipo === "trasferimento" ? "" : m.quantita > 0 ? "+" : ""}{numero(Math.abs(m.quantita))}</td>
                <td className="text-tenue">{m.causale ?? "—"}</td>
              </tr>
            ))}</tbody>
          </table></div>
        ) : <p className="text-sm text-tenue">Nessun movimento: registra un carico per la giacenza iniziale.</p>}
      </section>

      {etichetta && (
        <Modale titolo={`Etichetta ${etichetta.sku}`} onChiudi={() => setEtichetta(null)}>
          <div className="space-y-4 text-center">
            <div className="rounded-xl bg-white p-4">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={etichetta.url} alt={`Barcode ${etichetta.sku}`} className="mx-auto max-h-40" />
              <p className="mt-2 text-sm font-medium text-black">{p.nome}</p>
              <p className="text-xs text-gray-700">{euro(p.prezzoVendita)} + IVA</p>
            </div>
            <a className="btn-primario" href={etichetta.url} download={`etichetta-${etichetta.sku}.png`}><Printer className="h-4 w-4" /> Scarica per la stampa</a>
          </div>
        </Modale>
      )}
      {nuovaVariante && <NuovaVariante prodottoId={id} skuBase={p.sku} onChiudi={() => setNuovaVariante(false)} onFatto={() => { setNuovaVariante(false); ricarica(); }} />}
    </>
  );
}

function NuovaVariante({ prodottoId, skuBase, onChiudi, onFatto }: { prodottoId: string; skuBase: string; onChiudi: () => void; onFatto: () => void }) {
  const [v, setV] = useState({ sku: `${skuBase}-`, nome: "", barcode: "", prezzo: "", chiave: "", valore: "" });
  const { inCorso, errore, esegui } = useAzione();
  const invia = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => {
      await api(`/api/prodotti/${prodottoId}/varianti`, { metodo: "POST", corpo: {
        sku: v.sku, nome: v.nome, barcode: v.barcode || null, prezzo: v.prezzo ? aCentesimi(v.prezzo) : null,
        attributi: v.chiave && v.valore ? { [v.chiave]: v.valore } : null } });
      onFatto();
    });
  };
  return (
    <Modale titolo="Nuova variante" onChiudi={onChiudi}>
      <form onSubmit={invia} className="grid gap-3 sm:grid-cols-2">
        <label className="text-sm"><span className="etichetta">SKU</span><input className="campo font-mono" value={v.sku} onChange={(e) => setV({ ...v, sku: e.target.value })} required /></label>
        <label className="text-sm"><span className="etichetta">Nome</span><input className="campo" value={v.nome} onChange={(e) => setV({ ...v, nome: e.target.value })} required placeholder="es. Taglia XL" /></label>
        <label className="text-sm"><span className="etichetta">Barcode</span><input className="campo font-mono" value={v.barcode} onChange={(e) => setV({ ...v, barcode: e.target.value })} /></label>
        <label className="text-sm"><span className="etichetta">Prezzo (vuoto = prezzo base)</span><input className="campo" value={v.prezzo} onChange={(e) => setV({ ...v, prezzo: e.target.value })} inputMode="decimal" /></label>
        <label className="text-sm"><span className="etichetta">Attributo</span><input className="campo" value={v.chiave} onChange={(e) => setV({ ...v, chiave: e.target.value })} placeholder="es. taglia" /></label>
        <label className="text-sm"><span className="etichetta">Valore</span><input className="campo" value={v.valore} onChange={(e) => setV({ ...v, valore: e.target.value })} placeholder="es. XL" /></label>
        {errore && <div className="sm:col-span-2"><Avviso tipo="errore">{errore}</Avviso></div>}
        <button className="btn-primario sm:col-span-2" disabled={inCorso}>Aggiungi variante</button>
      </form>
    </Modale>
  );
}
