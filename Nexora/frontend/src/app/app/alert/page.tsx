"use client";
// Alert intelligenti: scorte, riordino, fatture scadute, lotti in scadenza, anomalie di vendita, clienti a rischio. © Roberto Di Flumeri
import { AlertTriangle, Bell, CalendarClock, CheckCheck, PackageX, RefreshCw, TrendingUp, UserX, Wallet } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { Avviso, Caricamento, Errore, Gravita, Schede, Titolo, useAzione, useDati, Vuoto } from "@/components/ui";
import { api, type Alert } from "@/lib/api";
import { dataOra, fa } from "@/lib/formato";

const TIPI: Record<string, { nome: string; icona: React.ComponentType<{ className?: string }>; link: (a: Alert) => string }> = {
  scorta_bassa: { nome: "Scorta bassa", icona: PackageX, link: (a) => `/app/prodotti/${a.entitaId}` },
  riordino: { nome: "Riordino", icona: RefreshCw, link: () => "/app/ai?scheda=riordino" },
  fattura_scaduta: { nome: "Fattura scaduta", icona: Wallet, link: (a) => (a.entitaId ? `/app/documenti/${a.entitaId}` : "/app/scadenze") },
  lotto_in_scadenza: { nome: "Lotto in scadenza", icona: CalendarClock, link: (a) => `/app/prodotti/${a.entitaId}` },
  anomalia_vendite: { nome: "Anomalia vendite", icona: TrendingUp, link: (a) => `/app/prodotti/${a.entitaId}` },
  cliente_a_rischio: { nome: "Cliente a rischio", icona: UserX, link: (a) => `/app/clienti/${a.entitaId}` },
};

export default function PaginaAlert() {
  const [stato, setStato] = useState<"aperti" | "risolti">("aperti");
  const [tipo, setTipo] = useState("");
  const { dati, errore, ricarica } = useDati<Alert[]>(`/api/alert?stato=${stato}`);
  const [esito, setEsito] = useState<string | null>(null);
  const azione = useAzione();
  const lista = (dati ?? []).filter((a) => !tipo || a.tipo === tipo);
  const conteggi = (dati ?? []).reduce<Record<string, number>>((m, a) => ({ ...m, [a.tipo]: (m[a.tipo] ?? 0) + 1 }), {});
  const notifica = () => window.dispatchEvent(new Event("nx-alert"));

  const aggiorna = () => azione.esegui(async () => {
    const r = await api("/api/alert/aggiorna", { metodo: "POST" });
    setEsito(`Analisi completata: ${r.attivi} alert attivi${r.risolti ? `, ${r.risolti} risolti perché la condizione non c'è più` : ""}${r.ordiniCreati ? `, ${r.ordiniCreati} ordini di riordino creati in bozza` : ""}.`);
    await ricarica(); notifica();
  });
  const letto = (a: Alert) => azione.esegui(async () => { await api(`/api/alert/${a.id}/letto`, { metodo: "POST" }); await ricarica(); notifica(); });
  const tuttiLetti = () => azione.esegui(async () => { for (const a of lista.filter((x) => !x.letto)) await api(`/api/alert/${a.id}/letto`, { metodo: "POST" }); await ricarica(); notifica(); });

  return (
    <>
      <Titolo titolo="Alert intelligenti" sottotitolo="Calcolati ogni ora sui dati aggiornati; si chiudono da soli quando la condizione sparisce"
        azioni={<>
          {stato === "aperti" && lista.some((a) => !a.letto) && <button className="btn-secondario" onClick={tuttiLetti} disabled={azione.inCorso}><CheckCheck className="h-4 w-4" /> Segna tutti letti</button>}
          <button className="btn-primario" onClick={aggiorna} disabled={azione.inCorso}><RefreshCw className={`h-4 w-4 ${azione.inCorso ? "animate-spin" : ""}`} /> Analizza ora</button>
        </>} />
      {esito && <Avviso tipo="ok" className="mb-4">{esito}</Avviso>}
      {azione.errore && <Avviso tipo="errore" className="mb-4">{azione.errore}</Avviso>}
      <Schede attiva={stato} onCambia={setStato} voci={[{ id: "aperti", etichetta: "Aperti" }, { id: "risolti", etichetta: "Risolti" }]} />
      <div className="mb-4 flex flex-wrap gap-2">
        <button className={`btn btn-piccolo ${!tipo ? "gradiente text-white" : "border border-bordo"}`} onClick={() => setTipo("")}>Tutti ({dati?.length ?? 0})</button>
        {Object.entries(TIPI).filter(([k]) => conteggi[k]).map(([k, t]) => (
          <button key={k} className={`btn btn-piccolo ${tipo === k ? "gradiente text-white" : "border border-bordo"}`} onClick={() => setTipo(k)}><t.icona className="h-3.5 w-3.5" /> {t.nome} ({conteggi[k]})</button>
        ))}
      </div>
      {errore && <Errore messaggio={errore} riprova={ricarica} />}
      {!dati && !errore && <Caricamento />}
      {dati && !lista.length && <div className="scheda"><Vuoto icona={Bell} titolo={stato === "aperti" ? "Nessun alert aperto" : "Nessun alert risolto"} testo={stato === "aperti" ? "Tutto sotto controllo." : undefined} /></div>}
      <ul className="space-y-3">
        {lista.map((a) => {
          const t = TIPI[a.tipo] ?? { nome: a.tipo, icona: AlertTriangle, link: () => "/app" };
          return (
            <li key={a.id} className={`scheda flex flex-wrap items-start gap-4 p-4 ${!a.letto && !a.risolto ? "border-l-4 border-l-viola" : ""}`}>
              <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${a.gravita === "critico" ? "bg-errore/15 text-errore" : a.gravita === "attenzione" ? "bg-attenzione/15 text-attenzione" : "bg-primario/15 text-primario"}`}>
                <t.icona className="h-5 w-5" /></span>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2"><p className="font-semibold">{a.titolo}</p><Gravita gravita={a.gravita} /><span className="pillola bg-fondo text-tenue">{t.nome}</span></div>
                <p className="mt-1 text-sm text-tenue">{a.messaggio}</p>
                <p className="mt-1 text-xs text-tenue">{a.risolto ? `Risolto il ${dataOra(a.risolto)}` : `Rilevato ${fa(a.createdAt)}`}</p>
              </div>
              <div className="flex gap-2">
                <Link href={t.link(a)} className="btn-secondario btn-piccolo">Vai</Link>
                {!a.letto && !a.risolto && <button className="btn-secondario btn-piccolo" onClick={() => letto(a)}>Letto</button>}
              </div>
            </li>
          );
        })}
      </ul>
    </>
  );
}
