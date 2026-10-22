"use client";
// Scheda cliente: anagrafica, profilo AI (RFM e rischio di abbandono), prodotti preferiti, storico ordini e documenti. © Roberto Di Flumeri
import { BrainCircuit, FilePlus2, Pencil, ShoppingCart, Trash2 } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import FormAnagrafica from "@/components/FormAnagrafica";
import { Avviso, Canale, Caricamento, Errore, Kpi, NOMI_TIPO_DOC, Schede, Segmento, Stato, Titolo, useAzione, useDati } from "@/components/ui";
import { api } from "@/lib/api";
import { data, euro, numero } from "@/lib/formato";
import { useSessione } from "@/lib/sessione";

export default function SchedaCliente() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { puo } = useSessione();
  const { dati: c, errore, ricarica } = useDati<any>(`/api/clienti/${id}`);
  const [scheda, setScheda] = useState<"ordini" | "documenti" | "anagrafica">("ordini");
  const [modifica, setModifica] = useState(false);
  const azione = useAzione();
  if (errore) return <Errore messaggio={errore} riprova={ricarica} />;
  if (!c) return <Caricamento />;
  const pr = c.profilo;

  const elimina = () => {
    if (confirm(`Eliminare il cliente «${c.ragioneSociale}»?`)) azione.esegui(async () => { await api(`/api/clienti/${id}`, { metodo: "DELETE" }); router.replace("/app/clienti"); });
  };

  return (
    <>
      <Titolo indietro={<Link href="/app/clienti" className="link">← Clienti</Link>} titolo={c.ragioneSociale}
        sottotitolo={<span className="flex flex-wrap items-center gap-2">{c.tipo === "privato" ? "Privato" : c.tipo === "pa" ? "Pubblica amministrazione" : "Azienda"}
          <Canale canale={c.canale} />{pr && <Segmento nome={pr.segmento} />}{c.citta && <span>{c.citta}</span>}</span>}
        azioni={<>
          {puo("ordini") && <Link href={`/app/ordini/nuovo?clienteId=${id}`} className="btn-primario"><ShoppingCart className="h-4 w-4" /> Nuovo ordine</Link>}
          {puo("documenti") && <Link href={`/app/documenti/nuovo?tipo=preventivo&clienteId=${id}`} className="btn-secondario"><FilePlus2 className="h-4 w-4" /> Preventivo</Link>}
          {puo("clienti") && <button className="btn-secondario" onClick={() => setModifica(true)}><Pencil className="h-4 w-4" /> Modifica</button>}
          {puo("clienti") && <button className="btn-secondario text-errore" onClick={elimina} aria-label="Elimina cliente"><Trash2 className="h-4 w-4" /></button>}
        </>} />
      {azione.errore && <Avviso tipo="errore" className="mb-4">{azione.errore}</Avviso>}

      <section className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi etichetta="Fatturato (imponibile)" valore={euro(c.fatturato, true)} nota={`${c.ordini.filter((o: any) => !["bozza", "annullato"].includes(o.stato)).length} ordini`} />
        <Kpi etichetta="Credito aperto" valore={euro(c.creditoAperto)} tono={c.creditoAperto ? "attenzione" : "ok"} nota={`pagamento a ${c.giorniPagamento} giorni`} />
        <Kpi etichetta="Valore annuo stimato" valore={pr ? euro(pr.valoreAnnuoStimato, true) : "—"} tono="viola" nota={pr?.intervalloMedioGiorni ? `ordina ogni ${pr.intervalloMedioGiorni} giorni circa` : "storico insufficiente"} />
        <Kpi etichetta="Rischio di abbandono" valore={pr ? pr.rischioAbbandono : "—"} tono={pr?.rischioAbbandono === "alto" ? "errore" : pr?.rischioAbbandono === "medio" ? "attenzione" : "ok"}
          nota={pr ? `ultimo ordine ${pr.recenzaGiorni} giorni fa` : undefined} />
      </section>

      {pr && (
        <section className="scheda mb-6 flex flex-wrap items-start gap-4 border-viola/30 bg-gradient-to-r from-viola/5 to-primario/5">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl gradiente text-white"><BrainCircuit className="h-5 w-5" /></span>
          <div className="min-w-0 flex-1">
            <p className="font-semibold">Analisi AI: segmento «{pr.segmento}»</p>
            <p className="text-sm text-tenue">Punteggi RFM: recenza {pr.r}/5 · frequenza {pr.f}/5 · valore {pr.m}/5. {pr.consiglio}</p>
          </div>
          {c.prodottiPreferiti.length > 0 && (
            <div className="text-sm"><p className="font-medium">Prodotti preferiti</p>
              <ul className="text-tenue">{c.prodottiPreferiti.map((p: any) => <li key={p.descrizione}>{p.descrizione} · {numero(p.quantita)}</li>)}</ul></div>
          )}
        </section>
      )}

      <Schede voci={[{ id: "ordini", etichetta: `Ordini (${c.ordini.length})` }, { id: "documenti", etichetta: `Documenti (${c.documenti.length})` }, { id: "anagrafica", etichetta: "Anagrafica" }]}
        attiva={scheda} onCambia={setScheda} />

      {scheda === "ordini" && (
        <div className="scheda overflow-x-auto p-0">
          {c.ordini.length ? <table className="tabella">
            <thead><tr><th>Numero</th><th>Data</th><th>Canale</th><th>Stato</th><th className="num">Righe</th><th className="num">Totale</th></tr></thead>
            <tbody>{c.ordini.map((o: any) => (
              <tr key={o.id}><td><Link className="link font-medium" href={`/app/ordini/${o.id}`}>{o.numero}</Link></td><td>{data(o.data)}</td><td><Canale canale={o.canale} /></td>
                <td><Stato stato={o.stato} /></td><td className="num">{o._count.righe}</td><td className="num">{euro(o.totale)}</td></tr>
            ))}</tbody>
          </table> : <p className="p-5 text-sm text-tenue">Nessun ordine.</p>}
        </div>
      )}
      {scheda === "documenti" && (
        <div className="scheda overflow-x-auto p-0">
          {c.documenti.length ? <table className="tabella">
            <thead><tr><th>Documento</th><th>Data</th><th>Stato</th><th className="num">Totale</th><th className="num">Da incassare</th></tr></thead>
            <tbody>{c.documenti.map((d: any) => (
              <tr key={d.id}><td><Link className="link font-medium" href={`/app/documenti/${d.id}`}>{NOMI_TIPO_DOC[d.tipo]} {d.numero ?? "(bozza)"}</Link></td><td>{data(d.data)}</td>
                <td><Stato stato={d.stato} /></td><td className="num">{euro(d.totale)}</td>
                <td className="num">{d.tipo === "fattura" ? euro(d.scadenze.filter((s: any) => !s.pagata).reduce((t: number, s: any) => t + s.importo, 0)) : "—"}</td></tr>
            ))}</tbody>
          </table> : <p className="p-5 text-sm text-tenue">Nessun documento.</p>}
        </div>
      )}
      {scheda === "anagrafica" && (
        <dl className="scheda grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
          {[["Partita IVA", c.partitaIva], ["Codice fiscale", c.codiceFiscale], ["Codice destinatario SDI", c.codiceDestinatario], ["PEC", c.pec], ["Email", c.email], ["Telefono", c.telefono],
            ["Indirizzo", [c.indirizzo, c.cap, c.citta, c.provincia && `(${c.provincia})`, c.nazione !== "IT" && c.nazione].filter(Boolean).join(" ")],
            ["Sconto abituale", `${c.scontoPercentuale}%`], ["Pagamento", `${c.giorniPagamento} giorni`], ["Cliente dal", data(c.createdAt)], ["Note", c.note]].map(([k, v]) => (
            <div key={k as string}><dt className="text-xs uppercase tracking-wide text-tenue">{k}</dt><dd className="mt-0.5 font-medium">{v || "—"}</dd></div>
          ))}
        </dl>
      )}
      {modifica && <FormAnagrafica tipo="cliente" iniziale={c} onChiudi={() => setModifica(false)} onSalvato={() => { setModifica(false); ricarica(); }} />}
    </>
  );
}
