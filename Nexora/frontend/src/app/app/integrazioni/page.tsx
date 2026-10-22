"use client";
// Integrazioni: Shopify, WooCommerce, Magento, Stripe, PayPal, GLS, DHL. Senza credenziali lavorano in modalità dimostrativa;
// con le chiavi gratuite di sandbox/sviluppatore usano le API reali. © Roberto Di Flumeri
import { Check, Copy, CreditCard, History, PlayCircle, RefreshCw, ShoppingBag, Store, Truck } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { Avviso, Caricamento, Errore, Interruttore, Modale, Titolo, useAzione, useDati } from "@/components/ui";
import { api } from "@/lib/api";
import { dataOra } from "@/lib/formato";
import { useSessione } from "@/lib/sessione";

const INFO: Record<string, { nome: string; gruppo: string; icona: React.ComponentType<{ className?: string }>; testo: string; gratis: string }> = {
  shopify: { nome: "Shopify", gruppo: "E-commerce", icona: ShoppingBag, testo: "Gli ordini arrivano via webhook firmato (HMAC) e vengono confermati in automatico; le giacenze tornano al negozio.",
    gratis: "Store di sviluppo gratuito con un account Shopify Partner." },
  woocommerce: { nome: "WooCommerce", gruppo: "E-commerce", icona: Store, testo: "Webhook «Order created» firmato e sincronizzazione delle giacenze tramite REST API.", gratis: "Plugin gratuito per WordPress." },
  magento: { nome: "Magento / Adobe Commerce", gruppo: "E-commerce", icona: Store, testo: "Importazione periodica degli ordini e aggiornamento delle giacenze via REST API.", gratis: "Magento Open Source è gratuito." },
  stripe: { nome: "Stripe", gruppo: "Pagamenti", icona: CreditCard, testo: "Link di pagamento (Checkout) per le fatture; il webhook firmato registra l'incasso.", gratis: "Chiavi sk_test_ gratuite in modalità test." },
  paypal: { nome: "PayPal", gruppo: "Pagamenti", icona: CreditCard, testo: "Ordini di pagamento con approvazione del cliente; webhook verificato dalle API PayPal.", gratis: "Sandbox gratuita su developer.paypal.com." },
  gls: { nome: "GLS", gruppo: "Spedizioni", icona: Truck, testo: "Etichetta 10×15 con barcode e link di tracciamento pubblico.", gratis: "Nessun costo: tracking dal sito pubblico GLS." },
  dhl: { nome: "DHL", gruppo: "Spedizioni", icona: Truck, testo: "Tracciamento reale con la Shipment Tracking API, etichetta 10×15.", gratis: "Chiave gratuita su developer.dhl.com." },
};

export default function Integrazioni() {
  const { puo } = useSessione();
  const { dati, errore, ricarica } = useDati<any[]>("/api/integrazioni");
  if (errore) return <Errore messaggio={errore} riprova={ricarica} />;
  return (
    <>
      <Titolo titolo="Integrazioni" sottotitolo="Ogni connettore funziona subito in modalità dimostrativa, senza account né costi; inserendo le credenziali gratuite di sandbox passa alle API reali" />
      {!puo("integrazioni") && <Avviso tipo="info" className="mb-4">Solo gli amministratori possono modificare le integrazioni.</Avviso>}
      {!dati ? <Caricamento /> : (
        ["E-commerce", "Pagamenti", "Spedizioni"].map((g) => (
          <section key={g} className="mb-8">
            <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-tenue">{g}</h2>
            <div className="grid gap-4 xl:grid-cols-2">
              {dati.filter((i) => INFO[i.tipo].gruppo === g).map((i) => <Connettore key={i.tipo} i={i} onAggiornato={ricarica} modificabile={puo("integrazioni")} />)}
            </div>
          </section>
        ))
      )}
      <section className="scheda">
        <h2 className="font-semibold">API REST per altri gestionali</h2>
        <p className="mt-1 text-sm text-tenue">Contabilità, BI o app esterne si collegano con OAuth2 (client credentials) e scope limitati. Crea le credenziali in <Link href="/app/impostazioni?scheda=api" className="link">Impostazioni → Client API</Link>.</p>
      </section>
    </>
  );
}

function Copia({ testo }: { testo: string }) {
  const [ok, setOk] = useState(false);
  return <button type="button" className="btn-secondario btn-piccolo" onClick={() => { navigator.clipboard?.writeText(testo); setOk(true); setTimeout(() => setOk(false), 1500); }} aria-label="Copia">
    {ok ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}</button>;
}

function Connettore({ i, onAggiornato, modificabile }: { i: any; onAggiornato: () => void; modificabile: boolean }) {
  const info = INFO[i.tipo];
  const [valori, setValori] = useState<Record<string, string>>(Object.fromEntries(i.campi.map((c: any) => [c.chiave, c.valore])));
  const [esito, setEsito] = useState<string | null>(null);
  const [eventi, setEventi] = useState(false);
  const azione = useAzione();
  const salva = (attiva?: boolean) => azione.esegui(async () => {
    await api(`/api/integrazioni/${i.tipo}`, { metodo: "PUT", corpo: { valori, ...(attiva !== undefined ? { attiva } : {}) } });
    setEsito(attiva === undefined ? "Configurazione salvata" : attiva ? "Integrazione attivata" : "Integrazione disattivata");
    onAggiornato();
  });
  const azioneRemota = (percorso: string, messaggio: (r: any) => string) => azione.esegui(async () => { setEsito(messaggio(await api(percorso, { metodo: "POST" }))); onAggiornato(); });

  return (
    <article className="scheda">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl gradiente text-white"><info.icona className="h-5 w-5" /></span>
          <div>
            <h3 className="flex flex-wrap items-center gap-2 font-semibold">{info.nome}
              <span className={`pillola ${i.modalita === "reale" ? "bg-ok/15 text-ok" : "bg-attenzione/15 text-attenzione"}`}>{i.modalita === "reale" ? "API reali" : "Modalità demo"}</span></h3>
            <p className="text-sm text-tenue">{info.testo}</p>
            <p className="text-xs text-tenue">Gratis: {info.gratis}</p>
          </div>
        </div>
        <Interruttore attivo={i.attiva} onChange={(v) => salva(v)} etichetta={i.attiva ? "Attiva" : "Spenta"} disabilitato={!modificabile || azione.inCorso} />
      </header>

      <form className="mt-4 grid gap-3 sm:grid-cols-2" onSubmit={(e) => { e.preventDefault(); salva(); }}>
        {i.campi.map((c: any) => (
          <label key={c.chiave} className="text-sm"><span className="etichetta">{c.etichetta}{c.richiesto && " *"}</span>
            <input className="campo font-mono text-xs" value={valori[c.chiave] ?? ""} onChange={(e) => setValori({ ...valori, [c.chiave]: e.target.value })}
              type={c.segreto && !String(valori[c.chiave]).startsWith("••") ? "password" : "text"} autoComplete="off" disabled={!modificabile}
              placeholder={c.segreto && c.impostato ? "impostato" : ""} /></label>
        ))}
        {modificabile && <div className="sm:col-span-2"><button className="btn-secondario btn-piccolo" disabled={azione.inCorso}>Salva credenziali</button></div>}
      </form>

      {i.webhookUrl && (
        <div className="mt-4 space-y-2 rounded-xl bg-fondo p-3 text-xs">
          <p className="font-medium">Webhook da configurare su {info.nome}</p>
          <div className="flex items-center gap-2"><code className="min-w-0 flex-1 truncate">{i.webhookUrl}</code><Copia testo={i.webhookUrl} /></div>
          {i.webhookSecret && modificabile && <div className="flex items-center gap-2"><span className="text-tenue">Segreto di firma:</span><code className="min-w-0 flex-1 truncate">{i.webhookSecret}</code><Copia testo={i.webhookSecret} /></div>}
        </div>
      )}

      {esito && <Avviso tipo="ok" className="mt-3">{esito}</Avviso>}
      {azione.errore && <Avviso tipo="errore" className="mt-3">{azione.errore}</Avviso>}
      <footer className="mt-4 flex flex-wrap items-center gap-2">
        {modificabile && ["shopify", "woocommerce"].includes(i.tipo) && i.attiva && <button className="btn-primario btn-piccolo" disabled={azione.inCorso}
          onClick={() => azioneRemota(`/api/integrazioni/${i.tipo}/ordine-di-prova`, (r) => `Webhook firmato ricevuto: ordine ${r.ordine} ${r.nuovo ? "importato e confermato" : "già presente"}${r.avvisi?.length ? ` (${r.avvisi.join("; ")})` : ""}`)}>
          <PlayCircle className="h-3.5 w-3.5" /> Invia ordine di prova</button>}
        {modificabile && ["shopify", "woocommerce", "magento"].includes(i.tipo) && i.attiva && <button className="btn-secondario btn-piccolo" disabled={azione.inCorso}
          onClick={() => azioneRemota(`/api/integrazioni/${i.tipo}/sincronizza`, (r) => `Giacenze ${r.modalita === "demo" ? "simulate" : "inviate"}: ${r.esiti.length} articoli`)}>
          <RefreshCw className="h-3.5 w-3.5" /> Sincronizza giacenze</button>}
        {modificabile && i.tipo === "magento" && i.attiva && <button className="btn-secondario btn-piccolo" disabled={azione.inCorso}
          onClick={() => azioneRemota("/api/integrazioni/magento/importa", (r) => `Importazione ${r.modalita === "demo" ? "dimostrativa" : ""}: ${r.esiti.length} ordini letti`)}>
          Importa ordini</button>}
        <button className="btn-secondario btn-piccolo" onClick={() => setEventi(true)}><History className="h-3.5 w-3.5" /> Registro eventi</button>
        {i.ultimaSincronizzazione && <span className="text-xs text-tenue">Ultima sincronizzazione {dataOra(i.ultimaSincronizzazione)}</span>}
      </footer>
      {eventi && <Eventi tipo={i.tipo} nome={info.nome} onChiudi={() => setEventi(false)} />}
    </article>
  );
}

function Eventi({ tipo, nome, onChiudi }: { tipo: string; nome: string; onChiudi: () => void }) {
  const { dati } = useDati<any[]>(`/api/integrazioni/${tipo}/eventi`);
  return (
    <Modale titolo={`Registro eventi · ${nome}`} onChiudi={onChiudi} larga>
      {!dati ? <Caricamento /> : !dati.length ? <p className="text-sm text-tenue">Nessun evento registrato.</p> : (
        <table className="tabella">
          <thead><tr><th>Quando</th><th>Direzione</th><th>Evento</th><th>Esito</th><th>Dettagli</th></tr></thead>
          <tbody>{dati.map((e) => (
            <tr key={e.id}><td className="whitespace-nowrap">{dataOra(e.createdAt)}</td><td>{e.direzione === "in" ? "Ricevuto" : "Inviato"}</td><td className="font-mono text-xs">{e.tipo}</td>
              <td><span className={`pillola ${e.esito === "ok" ? "bg-ok/15 text-ok" : e.esito === "errore" ? "bg-errore/15 text-errore" : "bg-bordo text-tenue"}`}>{e.esito}</span></td>
              <td className="max-w-xs truncate font-mono text-xs text-tenue" title={e.dettagli ?? ""}>{e.dettagli ?? "—"}</td></tr>
          ))}</tbody>
        </table>
      )}
    </Modale>
  );
}
