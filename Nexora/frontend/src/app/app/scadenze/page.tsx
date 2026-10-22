"use client";
// Scadenziario clienti: rate aperte, scadute e incassate, con registrazione incassi e link di pagamento. © Roberto Di Flumeri
import { Check, Link2, Wallet } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { LinkPagamento, RegistraPagamento } from "@/components/Pagamenti";
import { Caricamento, Errore, Kpi, Schede, Titolo, useDati, useLimite, Vuoto } from "@/components/ui";
import { data, euro } from "@/lib/formato";
import { useSessione } from "@/lib/sessione";

type Filtro = "aperte" | "scadute" | "pagate" | "tutte";

export default function Scadenziario() {
  const { puo } = useSessione();
  const [filtro, setFiltro] = useState<Filtro>("scadute");
  const { dati, errore, ricarica } = useDati<any[]>(`/api/scadenze?stato=${filtro}`);
  const { dati: aperte, ricarica: ricaricaAperte } = useDati<any[]>("/api/scadenze?stato=aperte");
  const [pagamento, setPagamento] = useState<any>(null);
  const [link, setLink] = useState<string | null>(null);
  const lista = filtro === "pagate" ? [...(dati ?? [])].reverse() : dati ?? [];
  const { visibili, Altri } = useLimite(lista, 60);
  const ora = Date.now();
  const somma = (f: (s: any) => boolean) => (aperte ?? []).filter(f).reduce((t, s) => t + s.importo, 0);

  return (
    <>
      <Titolo titolo="Scadenze e incassi" sottotitolo="Rate delle fatture emesse: incassi manuali o automatici tramite Stripe e PayPal" />
      {aperte && (
        <section className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <Kpi etichetta="Da incassare" valore={euro(somma(() => true), true)} icona={Wallet} nota={`${aperte.length} rate aperte`} />
          <Kpi etichetta="Scaduto" valore={euro(somma((s) => new Date(s.dataScadenza).getTime() < ora), true)} tono="errore" nota={`${aperte.filter((s) => new Date(s.dataScadenza).getTime() < ora).length} rate`} />
          <Kpi etichetta="Scaduto da oltre 30 giorni" valore={euro(somma((s) => s.giorniRitardo > 30), true)} tono="attenzione" />
          <Kpi etichetta="In scadenza a 30 giorni" valore={euro(somma((s) => { const t = new Date(s.dataScadenza).getTime(); return t >= ora && t <= ora + 30 * 86400_000; }), true)} tono="ok" />
        </section>
      )}
      <Schede attiva={filtro} onCambia={setFiltro} voci={[{ id: "scadute", etichetta: "Scadute" }, { id: "aperte", etichetta: "Aperte" }, { id: "pagate", etichetta: "Incassate" }, { id: "tutte", etichetta: "Tutte" }]} />
      {errore && <Errore messaggio={errore} riprova={ricarica} />}
      {!dati && !errore && <Caricamento />}
      {dati && !lista.length && <div className="scheda"><Vuoto icona={Wallet} titolo={filtro === "scadute" ? "Nessuna rata scaduta" : "Nessuna rata"} testo={filtro === "scadute" ? "Ottimo: i clienti sono in regola con i pagamenti." : undefined} /></div>}
      {lista.length > 0 && (
        <div className="scheda overflow-x-auto p-0">
          <table className="tabella">
            <thead><tr><th>Fattura</th><th>Cliente</th><th>Scadenza</th><th className="num">Importo</th><th>Stato</th><th /></tr></thead>
            <tbody>{visibili.map((s) => (
              <tr key={s.id}>
                <td><Link href={`/app/documenti/${s.documentoId}`} className="link font-medium">{s.documento.numero}</Link><p className="text-xs text-tenue">del {data(s.documento.data)}</p></td>
                <td>{s.documento.cliente.ragioneSociale}</td>
                <td className={s.giorniRitardo ? "font-medium text-errore" : ""}>{data(s.dataScadenza)}{s.giorniRitardo > 0 && <span className="block text-xs">{s.giorniRitardo} giorni di ritardo</span>}</td>
                <td className="num font-medium">{euro(s.importo)}</td>
                <td>{s.pagata ? <span className="pillola bg-ok/15 text-ok">Incassata {data(s.dataPagamento)} · {s.metodo}</span>
                  : s.giorniRitardo ? <span className="pillola bg-errore/15 text-errore">Scaduta</span> : <span className="pillola bg-attenzione/15 text-attenzione">Aperta</span>}</td>
                <td className="whitespace-nowrap">{!s.pagata && puo("pagamenti") && <span className="flex gap-1">
                  <button className="btn-primario btn-piccolo" onClick={() => setPagamento(s)}><Check className="h-3.5 w-3.5" /> Incassa</button>
                  <button className="btn-secondario btn-piccolo" onClick={() => setLink(s.id)} aria-label="Link di pagamento"><Link2 className="h-3.5 w-3.5" /></button></span>}</td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
      <Altri />
      {pagamento && <RegistraPagamento scadenza={pagamento} onChiudi={() => setPagamento(null)} onFatto={() => { setPagamento(null); ricarica(); ricaricaAperte(); }} />}
      {link && <LinkPagamento scadenzaId={link} onChiudi={() => setLink(null)} />}
    </>
  );
}
