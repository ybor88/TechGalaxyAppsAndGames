"use client";
// Dashboard KPI: fatturato, ordini aperti, prodotti più venduti, valore di magazzino, scadenze, previsione e alert. © Roberto Di Flumeri
import { ArrowDownRight, ArrowUpRight, BrainCircuit, Boxes, Euro, ShoppingCart, Sparkles, Wallet } from "lucide-react";
import Link from "next/link";
import { GraficoBarreOrizzontali, GraficoSerie } from "@/components/grafici";
import { Caricamento, Errore, Gravita, Kpi, Stato, Titolo, useDati } from "@/components/ui";
import type { Alert } from "@/lib/api";
import { euro, fa, mese, numero, percentuale } from "@/lib/formato";
import { useSessione } from "@/lib/sessione";

interface Dashboard {
  fatturato: { meseCorrente: number; mesePrecedente: number; mesePrecedenteStessoPeriodo: number; annoCorrente: number; variazione: number | null };
  ordiniAperti: { numero: number; bozze: number; valore: number; perStato: Record<string, number> };
  prodottiPiuVenduti: { descrizione: string; varianteId: string; quantita: number; ricavi: number }[];
  valoreMagazzino: number; sottoScorta: number;
  scadenze: { creditiAperti: number; scaduto: number; numeroScadute: number; prossimi30: number; acquistiDaRicevere: number; ordiniFornitoreAperti: number };
  andamento: { mese: string; fatturato: number; ordinato: number; ordini: number }[];
  previsione30: { totale: number; spiegazione: string; accuratezza: string };
  alert: Alert[];
}

export default function PaginaDashboard() {
  const { utente } = useSessione();
  const { dati: d, errore, ricarica } = useDati<Dashboard>("/api/dashboard");
  const ora = new Date();
  const saluto = ora.getHours() < 13 ? "Buongiorno" : ora.getHours() < 18 ? "Buon pomeriggio" : "Buonasera";

  return (
    <>
      <Titolo titolo={`${saluto}, ${utente?.nome.split(" ")[0]}`}
        sottotitolo={`${ora.toLocaleDateString("it-IT", { weekday: "long", day: "numeric", month: "long", year: "numeric" })} · ecco come sta andando ${utente?.azienda.ragioneSociale}`}
        azioni={<><Link href="/app/ordini/nuovo" className="btn-primario"><ShoppingCart className="h-4 w-4" /> Nuovo ordine</Link><Link href="/app/ai" className="btn-secondario"><BrainCircuit className="h-4 w-4" /> Motore AI</Link></>} />
      {errore && <Errore messaggio={errore} riprova={ricarica} />}
      {!d && !errore && <Caricamento />}
      {d && (
        <div className="space-y-6">
          <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="Indicatori principali">
            <Kpi etichetta="Fatturato del mese" valore={euro(d.fatturato.meseCorrente, true)} icona={Euro} href="/app/analytics"
              nota={d.fatturato.variazione === null ? "primo mese" : (
                <span className={d.fatturato.variazione >= 0 ? "text-ok" : "text-errore"}>
                  {d.fatturato.variazione >= 0 ? <ArrowUpRight className="inline h-3 w-3" /> : <ArrowDownRight className="inline h-3 w-3" />}
                  {percentuale(d.fatturato.variazione)} rispetto allo stesso periodo del mese scorso
                </span>)} />
            <Kpi etichetta="Ordini aperti" valore={numero(d.ordiniAperti.numero)} icona={ShoppingCart} tono="viola" href="/app/ordini"
              nota={`${euro(d.ordiniAperti.valore, true)} da evadere · ${d.ordiniAperti.bozze} bozze`} />
            <Kpi etichetta="Valore di magazzino" valore={euro(d.valoreMagazzino, true)} icona={Boxes} tono="ciano" href="/app/magazzino"
              nota={d.sottoScorta ? <span className="text-attenzione">{d.sottoScorta} articoli sotto scorta</span> : "nessun articolo sotto scorta"} />
            <Kpi etichetta="Crediti da incassare" valore={euro(d.scadenze.creditiAperti, true)} icona={Wallet} tono={d.scadenze.scaduto ? "attenzione" : "ok"} href="/app/scadenze"
              nota={d.scadenze.scaduto ? <span className="text-errore">{euro(d.scadenze.scaduto, true)} scaduti ({d.scadenze.numeroScadute} rate)</span> : "nessuna rata scaduta"} />
          </section>

          <section className="grid gap-6 xl:grid-cols-3">
            <div className="scheda xl:col-span-2">
              <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
                <h2 className="font-semibold">Andamento degli ultimi 12 mesi</h2>
                <p className="text-sm text-tenue">Fatturato dell&apos;anno: <strong className="text-testo">{euro(d.fatturato.annoCorrente, true)}</strong></p>
              </div>
              <GraficoSerie dati={d.andamento} asseX="mese" formattaX={mese}
                serie={[{ chiave: "fatturato", nome: "Fatturato (netto NC)" }, { chiave: "ordinato", nome: "Ordinato", tipo: "linea", colore: "#8b5cf6" }]} />
            </div>
            <div className="scheda flex flex-col">
              <h2 className="flex items-center gap-2 font-semibold"><Sparkles className="h-4 w-4 text-viola" /> Previsione AI · prossimi 30 giorni</h2>
              <p className="mt-3 text-3xl font-bold testo-gradiente">{euro(d.previsione30.totale, true)}</p>
              <p className="mt-2 text-sm text-tenue">{d.previsione30.spiegazione}</p>
              <p className="mt-2 text-xs text-tenue">{d.previsione30.accuratezza}</p>
              <div className="mt-auto grid grid-cols-2 gap-3 pt-4 text-sm">
                <div className="rounded-xl bg-fondo p-3"><p className="text-xs text-tenue">Incassi attesi a 30 gg</p><p className="font-semibold">{euro(d.scadenze.prossimi30, true)}</p></div>
                <div className="rounded-xl bg-fondo p-3"><p className="text-xs text-tenue">Acquisti in arrivo</p><p className="font-semibold">{euro(d.scadenze.acquistiDaRicevere, true)}</p>
                  <p className="text-xs text-tenue">{d.scadenze.ordiniFornitoreAperti} ordini fornitore</p></div>
              </div>
              <Link href="/app/ai" className="link mt-3 text-sm font-medium">Apri il motore AI →</Link>
            </div>
          </section>

          <section className="grid gap-6 xl:grid-cols-3">
            <div className="scheda xl:col-span-2">
              <h2 className="mb-3 font-semibold">Prodotti più venduti · ultimi 30 giorni</h2>
              {d.prodottiPiuVenduti.length ? <GraficoBarreOrizzontali dati={d.prodottiPiuVenduti} chiave="ricavi" etichetta="descrizione" />
                : <p className="text-sm text-tenue">Ancora nessuna vendita negli ultimi 30 giorni.</p>}
            </div>
            <div className="scheda">
              <h2 className="mb-3 font-semibold">Ordini da evadere</h2>
              <ul className="space-y-2">
                {(["bozza", "confermato", "in_preparazione"] as const).map((s) => (
                  <li key={s}>
                    <Link href={`/app/ordini?stato=${s}`} className="flex items-center justify-between rounded-xl border border-bordo px-3 py-2.5 hover:bg-fondo">
                      <Stato stato={s} /><span className="text-lg font-bold tabular-nums">{d.ordiniAperti.perStato[s] ?? 0}</span>
                    </Link>
                  </li>
                ))}
              </ul>
              <Link href="/app/ordini?vista=kanban" className="link mt-3 inline-block text-sm font-medium">Vista kanban →</Link>
            </div>
          </section>

          <section className="scheda">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="font-semibold">Alert intelligenti</h2>
              <Link href="/app/alert" className="link text-sm font-medium">Tutti gli alert →</Link>
            </div>
            {d.alert.length ? (
              <ul className="divide-y divide-bordo">
                {d.alert.map((a) => (
                  <li key={a.id} className="flex flex-wrap items-start gap-3 py-2.5">
                    <Gravita gravita={a.gravita} />
                    <div className="min-w-0 flex-1">
                      <p className="font-medium">{a.titolo}</p>
                      <p className="text-sm text-tenue">{a.messaggio}</p>
                    </div>
                    <span className="text-xs text-tenue">{fa(a.createdAt)}</span>
                  </li>
                ))}
              </ul>
            ) : <p className="text-sm text-tenue">Nessun alert aperto: tutto sotto controllo.</p>}
          </section>
        </div>
      )}
    </>
  );
}
