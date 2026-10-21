// Termini d'uso e linee guida della community — © Roberto Di Flumeri
import Image from "next/image";
import Link from "next/link";
import { Copyright } from "@/components/ui";

export const metadata = { title: "Termini e linee guida" };

const SEZIONI: [string, string[]][] = [
  ["Cos'è Neighbour", [
    "Un'app gratuita per persone dello stesso quartiere: scambiare oggetti, chiedere e offrire piccoli aiuti, organizzare eventi, segnalare cose utili e scoprire servizi di prossimità.",
    "Non è un social network generalista e non ospita pubblicità invasiva.",
  ]],
  ["La tua posizione", [
    "Salviamo solo una zona approssimata del tuo quartiere (una cella di circa un chilometro) e il raggio che scegli tu. L'indirizzo non viene mai chiesto né mostrato.",
    "I contenuti compaiono sulla mappa come aree di circa 150 metri. Il punto d'incontro preciso si concorda solo in chat.",
    "La posizione del dispositivo si usa solo con il tuo consenso, e solo per proporti il quartiere.",
  ]],
  ["Linee guida della community", [
    "Rispetto prima di tutto: niente insulti, molestie, discriminazioni o minacce.",
    "Pubblica solo ciò che è tuo e che è lecito scambiare. Niente animali in vendita, farmaci, armi o oggetti pericolosi.",
    "Niente spam: al massimo 10 pubblicazioni al giorno, e i contenuti ripetuti vengono fermati. I professionisti usano un profilo professionale, ben riconoscibile.",
    "Non pubblicare dati personali (tuoi o di altri) nei testi pubblici: telefono, email e indirizzo si condividono in chat.",
    "Le richieste di aiuto sono per piccole cose non urgenti: per le emergenze chiama sempre il 112.",
  ]],
  ["Sicurezza e moderazione", [
    "Puoi bloccare chiunque in un clic: non vedrete più i rispettivi contenuti e non potrete scrivervi.",
    "Puoi segnalare contenuti, messaggi e profili con un motivo preciso. La moderazione esamina ogni segnalazione e registra la decisione.",
    "Le decisioni possibili sono: archiviazione, rimozione del contenuto, avviso, sospensione di 7 giorni (l'account resta consultabile ma non può pubblicare) e blocco dell'account.",
    "Contro rimozioni, avvisi e sospensioni puoi presentare un ricorso, esaminato da un moderatore diverso da chi ha deciso.",
  ]],
  ["Fiducia tra vicini", [
    "Il feedback si lascia solo dopo uno scambio o un evento realmente concluso insieme, e mostra sempre il suo contesto.",
    "Non esiste un punteggio globale: nessuno viene ridotto a un numero.",
  ]],
  ["I tuoi dati", [
    "Email conservata solo come hash, messaggi privati cifrati, foto ripulite dai metadati (compresa la posizione GPS dello scatto).",
    "Consensi separati per posizione, notifiche, feed personalizzato e statistiche, con registro storico delle versioni.",
    "Puoi esportare tutti i tuoi dati (JSON o testo leggibile) e cancellare l'account in autonomia: ricevi una ricevuta di cancellazione.",
  ]],
];

export default function Termini() {
  return (
    <main className="mx-auto max-w-3xl px-4 py-10">
      <Link href="/" className="mb-6 flex items-center gap-3">
        <Image src="/logo.png" alt="" width={44} height={44} className="rounded-xl" />
        <span className="text-xl font-bold">Neighbour</span>
      </Link>
      <h1 className="text-3xl font-bold">Termini d&apos;uso e linee guida</h1>
      <p className="mt-2 text-tenue">Versione 1.0 · linee-guida/1.0 · consensi/1.0</p>
      <div className="mt-8 space-y-6">
        {SEZIONI.map(([titolo, punti]) => (
          <section key={titolo} className="scheda">
            <h2 className="mb-2 text-lg font-semibold">{titolo}</h2>
            <ul className="list-disc space-y-1 pl-5 text-sm">{punti.map((p) => <li key={p}>{p}</li>)}</ul>
          </section>
        ))}
      </div>
      <div className="mt-8 flex flex-wrap gap-3">
        <Link href="/registrati" className="btn-primario">Crea un account</Link>
        <Link href="/" className="btn-secondario">Torna alla home</Link>
      </div>
      <div className="mt-10"><Copyright /></div>
    </main>
  );
}
