// Termini d'uso — © Roberto Di Flumeri
import Link from "next/link";
import { Copyright } from "@/components/ui";

export const metadata = { title: "Termini d'uso" };

export default function Termini() {
  return (
    <main className="mx-auto max-w-3xl px-4 py-10">
      <Link href="/" className="text-sm text-blu underline">← Torna a Ghost Me</Link>
      <h1 className="mt-4 text-3xl font-bold">Termini d&apos;uso</h1>
      <div className="mt-6 space-y-4 leading-relaxed">
        <p>Ghost Me crea un alter ego digitale <strong>esclusivamente a partire dai dati che inserisci volontariamente</strong>.</p>
        <h2 className="text-xl font-semibold">Cosa è e cosa non è</h2>
        <ul className="list-disc space-y-1 pl-6">
          <li>Le risposte sono <strong>contenuti generati</strong>: non sono previsioni certe, né la tua volontà reale.</li>
          <li>Non è uno strumento di diagnosi né un sostituto di assistenza psicologica, medica, legale o finanziaria.</li>
          <li>Il simulatore di decisioni evidenzia la coerenza con ciò che hai dichiarato: la decisione resta tua.</li>
          <li>Non pubblica nulla a tuo nome in automatico.</li>
        </ul>
        <h2 className="text-xl font-semibold">Regole d&apos;uso</h2>
        <ul className="list-disc space-y-1 pl-6">
          <li>Puoi creare solo il <strong>tuo</strong> profilo: è vietato creare avatar di altre persone.</li>
          <li>Non inserire dati di terzi senza il loro consenso.</li>
          <li>Gli avatar condivisi possono essere segnalati e sospesi in caso di abuso.</li>
        </ul>
        <h2 className="text-xl font-semibold">I tuoi dati</h2>
        <ul className="list-disc space-y-1 pl-6">
          <li>I contenuti sono cifrati a riposo; l&apos;email è conservata solo come impronta (hash).</li>
          <li>Ogni elaborazione facoltativa richiede un consenso che puoi revocare in qualsiasi momento.</li>
          <li>Puoi esportare tutti i dati (JSON o testo leggibile) e cancellare l&apos;account in autonomia.</li>
          <li>Non vengono dedotte caratteristiche delicate (salute, orientamento, religione, idee politiche, origine) non dichiarate.</li>
        </ul>
        <p className="text-sm text-tenue">Versione policy: ghostme-policy/1.0 · consensi/1.0</p>
      </div>
      <div className="mt-10"><Copyright /></div>
    </main>
  );
}
