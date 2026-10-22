"use client";
// Dettaglio documento: emissione, PDF, fattura elettronica XML e stato SdI, scadenze e incassi, conversioni (preventivo → ordine, fattura → nota di credito). © Roberto Di Flumeri
import { Ban, Check, CheckCircle2, FileCode2, FileMinus2, FileText, Link2, Send, ShoppingCart, Trash2, Wallet, XCircle } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { LinkPagamento, RegistraPagamento } from "@/components/Pagamenti";
import { Avviso, Caricamento, Errore, Modale, NOMI_TIPO_DOC, Stato, Titolo, useAzione, useDati } from "@/components/ui";
import { api, apriPdf, scarica, type Magazzino } from "@/lib/api";
import { data, dataOra, euro, numero } from "@/lib/formato";
import { useSessione } from "@/lib/sessione";

export default function DettaglioDocumento() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { puo } = useSessione();
  const { dati: d, errore, ricarica } = useDati<any>(`/api/documenti/${id}`);
  const [emissione, setEmissione] = useState(false);
  const [pagamento, setPagamento] = useState<any>(null);
  const [link, setLink] = useState<string | null>(null);
  const azione = useAzione();
  if (errore) return <Errore messaggio={errore} riprova={ricarica} />;
  if (!d) return <Caricamento />;
  const scrive = puo("documenti");
  const elettronica = d.tipo === "fattura" || d.tipo === "nota_credito";
  const post = (percorso: string, corpo?: unknown) => azione.esegui(async () => { await api(percorso, { metodo: "POST", corpo: corpo ?? {} }); await ricarica(); });
  const nome = `${NOMI_TIPO_DOC[d.tipo]} ${d.numero ?? "(bozza)"}`;
  const riepilogo = new Map<string, { aliquota: number; natura: string | null; base: number }>();
  for (const r of d.righe) {
    const k = `${r.aliquotaIva}-${r.naturaIva ?? ""}`;
    const x = riepilogo.get(k) ?? { aliquota: r.aliquotaIva, natura: r.naturaIva, base: 0 };
    x.base += Math.round(r.quantita * r.prezzoUnitario * (1 - r.sconto / 100));
    riepilogo.set(k, x);
  }

  return (
    <>
      <Titolo indietro={<Link href={`/app/documenti?tipo=${d.tipo}`} className="link">← Documenti</Link>}
        titolo={<span className="flex flex-wrap items-center gap-3">{nome} <Stato stato={d.stato} />{d.statoSdi && <Stato stato={d.statoSdi} sdi />}</span>}
        sottotitolo={<>del {data(d.data)} · <Link href={`/app/clienti/${d.clienteId}`} className="link">{d.cliente.ragioneSociale}</Link>
          {d.ordine && <> · ordine <Link href={`/app/ordini/${d.ordine.id}`} className="link">{d.ordine.numero}</Link></>}
          {d.origine && <> · da <Link href={`/app/documenti/${d.origine.id}`} className="link">{NOMI_TIPO_DOC[d.origine.tipo]} {d.origine.numero}</Link></>}</>}
        azioni={<>
          {scrive && d.stato === "bozza" && <button className="btn-primario" onClick={() => setEmissione(true)}><Send className="h-4 w-4" /> Emetti</button>}
          <button className="btn-secondario" onClick={() => azione.esegui(() => apriPdf(`/api/documenti/${id}/pdf`))}><FileText className="h-4 w-4" /> PDF</button>
          {elettronica && d.stato !== "bozza" && <button className="btn-secondario" onClick={() => azione.esegui(async () => {
            await scarica(`/api/documenti/${id}/xml`, d.nomeFileSdi ?? `fattura-${d.numero}.xml`); await ricarica();
          })}><FileCode2 className="h-4 w-4" /> XML FatturaPA</button>}
          {scrive && d.tipo === "preventivo" && ["inviato", "bozza"].includes(d.stato) && <>
            <button className="btn-secondario" onClick={() => post(`/api/documenti/${id}/stato`, { stato: "accettato" })}><Check className="h-4 w-4" /> Accettato</button>
            <button className="btn-secondario" onClick={() => post(`/api/documenti/${id}/stato`, { stato: "rifiutato" })}><XCircle className="h-4 w-4" /> Rifiutato</button>
          </>}
          {puo("ordini") && d.tipo === "preventivo" && ["inviato", "accettato"].includes(d.stato) && !d.collegati.length &&
            <button className="btn-primario" onClick={() => azione.esegui(async () => { const o = await api(`/api/documenti/${id}/ordine`, { metodo: "POST" }); router.push(`/app/ordini/${o.id}`); })}>
              <ShoppingCart className="h-4 w-4" /> Crea ordine</button>}
          {scrive && d.tipo === "fattura" && d.stato !== "bozza" &&
            <button className="btn-secondario" onClick={() => azione.esegui(async () => { const nc = await api(`/api/documenti/${id}/nota-credito`, { metodo: "POST" }); router.push(`/app/documenti/${nc.id}`); })}>
              <FileMinus2 className="h-4 w-4" /> Nota di credito</button>}
          {scrive && d.stato === "bozza" && <button className="btn-secondario text-errore" aria-label="Elimina bozza"
            onClick={() => confirm("Eliminare la bozza?") && azione.esegui(async () => { await api(`/api/documenti/${id}`, { metodo: "DELETE" }); router.replace(`/app/documenti?tipo=${d.tipo}`); })}><Trash2 className="h-4 w-4" /></button>}
        </>} />
      {azione.errore && <Avviso tipo="errore" className="mb-4">{azione.errore}</Avviso>}
      {elettronica && d.controlliSdi.length > 0 && (
        <Avviso tipo="attenzione" className="mb-4"><strong>Prima dell&apos;invio allo SdI:</strong><ul className="ml-4 list-disc">{d.controlliSdi.map((c: string) => <li key={c}>{c}</li>)}</ul></Avviso>
      )}

      <div className="grid gap-6 xl:grid-cols-3">
        <section className="scheda overflow-x-auto xl:col-span-2">
          <table className="tabella">
            <thead><tr><th>Descrizione</th><th className="num">Quantità</th><th className="num">Prezzo</th><th className="num">Sconto</th><th className="num">IVA</th><th className="num">Imponibile</th></tr></thead>
            <tbody>{d.righe.map((r: any) => (
              <tr key={r.id}><td>{r.descrizione}</td><td className="num">{numero(r.quantita)} {r.unita}</td><td className="num">{euro(r.prezzoUnitario)}</td>
                <td className="num">{r.sconto ? `${r.sconto}%` : "—"}</td><td className="num">{r.aliquotaIva}%{r.naturaIva ? ` ${r.naturaIva}` : ""}</td>
                <td className="num font-medium">{euro(Math.round(r.quantita * r.prezzoUnitario * (1 - r.sconto / 100)))}</td></tr>
            ))}</tbody>
          </table>
          <div className="mt-4 flex flex-wrap justify-between gap-4">
            <div className="text-sm text-tenue">
              {[...riepilogo.values()].map((x) => <p key={`${x.aliquota}${x.natura}`}>IVA {x.aliquota}%{x.natura ? ` (${x.natura})` : ""}: imponibile {euro(x.base)}</p>)}
              {d.note && <p className="mt-2">Note: {d.note}</p>}
              {d.tipo === "preventivo" && d.validoFino && <p className="mt-2">Valido fino al {data(d.validoFino)}</p>}
              {d.tipo === "ddt" && <p className="mt-2">Causale: {d.causaleTrasporto ?? "—"} · colli {d.colli ?? "—"}{d.pesoKg ? ` · ${numero(d.pesoKg)} kg` : ""}</p>}
            </div>
            <div className="w-full max-w-xs space-y-1 rounded-xl bg-fondo p-4 text-sm">
              <div className="flex justify-between"><span>Imponibile</span><span>{euro(d.imponibile)}</span></div>
              <div className="flex justify-between text-tenue"><span>IVA</span><span>{euro(d.iva)}</span></div>
              <div className="flex justify-between border-t border-bordo pt-1 text-base font-bold"><span>Totale</span><span>{euro(d.totale)}</span></div>
            </div>
          </div>
        </section>

        <div className="space-y-6">
          {d.tipo === "fattura" && d.stato !== "bozza" && (
            <section className="scheda">
              <h2 className="mb-3 flex items-center gap-2 font-semibold"><Wallet className="h-4 w-4 text-primario" /> Scadenze e incassi</h2>
              <ul className="space-y-3">{d.scadenze.map((s: any, i: number) => {
                const scaduta = !s.pagata && new Date(s.dataScadenza) < new Date();
                return (
                  <li key={s.id} className="rounded-xl border border-bordo p-3 text-sm">
                    <div className="flex items-center justify-between gap-2"><span className="font-medium">Rata {i + 1} · {euro(s.importo)}</span>
                      {s.pagata ? <span className="pillola bg-ok/15 text-ok"><CheckCircle2 className="h-3 w-3" /> Pagata</span> : scaduta ? <span className="pillola bg-errore/15 text-errore">Scaduta</span> : <span className="pillola bg-attenzione/15 text-attenzione">Da incassare</span>}</div>
                    <p className="text-tenue">scadenza {data(s.dataScadenza)}{s.pagata && ` · incassata il ${data(s.dataPagamento)} (${s.metodo}${s.riferimento ? `, ${s.riferimento}` : ""})`}</p>
                    {!s.pagata && puo("pagamenti") && (
                      <div className="mt-2 flex flex-wrap gap-2">
                        <button className="btn-primario btn-piccolo" onClick={() => setPagamento(s)}><Check className="h-3.5 w-3.5" /> Registra incasso</button>
                        <button className="btn-secondario btn-piccolo" onClick={() => setLink(s.id)}><Link2 className="h-3.5 w-3.5" /> Link di pagamento</button>
                      </div>
                    )}
                  </li>
                );
              })}</ul>
            </section>
          )}
          {elettronica && d.stato !== "bozza" && (
            <section className="scheda text-sm">
              <h2 className="mb-2 font-semibold">Fattura elettronica (SdI)</h2>
              <p className="text-tenue">{d.nomeFileSdi ? <>File <span className="font-mono">{d.nomeFileSdi}</span></> : "Scarica l'XML per generare il file da trasmettere."}</p>
              <p className="mt-1 text-tenue">Carica il file sul portale «Fatture e Corrispettivi» dell&apos;Agenzia delle Entrate (gratuito) o invialo via PEC a sdi01@pec.fatturapa.it, poi registra qui l&apos;esito.</p>
              {scrive && d.statoSdi && d.statoSdi !== "da_generare" && (
                <div className="mt-3 flex flex-wrap gap-2">
                  {[["inviato", "Inviata"], ["consegnato", "Consegnata"], ["scartato", "Scartata"]].map(([k, v]) => (
                    <button key={k} className={`btn btn-piccolo ${d.statoSdi === k ? "gradiente text-white" : "border border-bordo"}`} onClick={() => post(`/api/documenti/${id}/sdi`, { stato: k })}>{v}</button>
                  ))}
                </div>
              )}
            </section>
          )}
          {d.collegati.length > 0 && (
            <section className="scheda text-sm">
              <h2 className="mb-2 font-semibold">Documenti collegati</h2>
              <ul className="space-y-1">{d.collegati.map((c: any) => <li key={c.id} className="flex justify-between"><Link href={`/app/documenti/${c.id}`} className="link">{NOMI_TIPO_DOC[c.tipo]} {c.numero ?? "(bozza)"}</Link><span>{euro(c.totale)}</span></li>)}</ul>
            </section>
          )}
          <section className="scheda text-sm text-tenue">
            <p>Creato il {dataOra(d.createdAt)}</p>
            <p>Cliente: {d.cliente.partitaIva ? `P.IVA ${d.cliente.partitaIva}` : d.cliente.codiceFiscale ? `CF ${d.cliente.codiceFiscale}` : "—"}{d.cliente.codiceDestinatario ? ` · SDI ${d.cliente.codiceDestinatario}` : ""}{d.cliente.pec ? ` · PEC ${d.cliente.pec}` : ""}</p>
          </section>
        </div>
      </div>
      {emissione && <Emissione documento={d} onChiudi={() => setEmissione(false)} onFatto={() => { setEmissione(false); ricarica(); }} />}
      {pagamento && <RegistraPagamento scadenza={pagamento} onChiudi={() => setPagamento(null)} onFatto={() => { setPagamento(null); ricarica(); }} />}
      {link && <LinkPagamento scadenzaId={link} onChiudi={() => setLink(null)} />}
    </>
  );
}

function Emissione({ documento: d, onChiudi, onFatto }: { documento: any; onChiudi: () => void; onFatto: () => void }) {
  const [rate, setRate] = useState(1);
  const [reso, setReso] = useState("");
  const { dati: magazzini } = useDati<Magazzino[]>(d.tipo === "nota_credito" ? "/api/magazzini" : null);
  const { inCorso, errore, esegui } = useAzione();
  return (
    <Modale titolo={`Emetti ${NOMI_TIPO_DOC[d.tipo].toLowerCase()}`} onChiudi={onChiudi}>
      <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); esegui(async () => {
        await api(`/api/documenti/${d.id}/emetti`, { metodo: "POST", corpo: { ...(d.tipo === "fattura" ? { rate } : {}), ...(reso ? { resoInMagazzino: reso } : {}) } });
        onFatto();
      }); }}>
        <p className="text-sm text-tenue">All&apos;emissione il documento riceve il numero progressivo dell&apos;anno e non è più modificabile.</p>
        {d.tipo === "fattura" && (
          <label className="block text-sm"><span className="etichetta">Pagamento in rate</span>
            <select className="campo" value={rate} onChange={(e) => setRate(Number(e.target.value))}>
              {[1, 2, 3, 4, 6, 12].map((n) => <option key={n} value={n}>{n === 1 ? `Unica soluzione a ${d.cliente.giorniPagamento} giorni` : `${n} rate ogni ${d.cliente.giorniPagamento || 30} giorni`}</option>)}
            </select></label>
        )}
        {d.tipo === "nota_credito" && (
          <label className="block text-sm"><span className="etichetta">Reso fisico della merce</span>
            <select className="campo" value={reso} onChange={(e) => setReso(e.target.value)}>
              <option value="">Nessun reso (solo storno contabile)</option>{magazzini?.map((m) => <option key={m.id} value={m.id}>Rientra in {m.nome}</option>)}
            </select></label>
        )}
        {errore && <Avviso tipo="errore">{errore}</Avviso>}
        <div className="flex justify-end gap-2"><button type="button" className="btn-secondario" onClick={onChiudi}><Ban className="h-4 w-4" /> Annulla</button>
          <button className="btn-primario" disabled={inCorso}><Send className="h-4 w-4" /> Emetti</button></div>
      </form>
    </Modale>
  );
}

