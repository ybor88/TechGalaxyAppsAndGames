// Pagina di presentazione — © Roberto Di Flumeri
import Image from "next/image";
import Link from "next/link";
import { BrainCircuit, History, Lock, MessageCircle, Scale, Share2 } from "lucide-react";
import { CambiaTema, Copyright } from "@/components/ui";

const FUNZIONI = [
  { icona: MessageCircle, titolo: "Dialoga con il tuo alter ego", testo: "Risponde solo con ciò che hai deciso di condividere, mostrando le fonti." },
  { icona: Scale, titolo: "Simula le tue decisioni", testo: "Confronta opzioni con le tue priorità dichiarate. La scelta resta sempre tua." },
  { icona: History, titolo: "Guarda come cambi", testo: "Versioni, timeline e diario per esplorare l'evoluzione delle tue idee." },
  { icona: Share2, titolo: "Condividi con controllo", testo: "Link temporanei, con limiti di domande, scadenza e revoca in un clic." },
  { icona: Lock, titolo: "Privacy by design", testo: "Dati cifrati, consensi espliciti, esportazione e cancellazione self-service." },
  { icona: BrainCircuit, titolo: "AI sul tuo dispositivo", testo: "Motore integrato istantaneo o modello locale con Ollama: i tuoi dati restano con te." },
];

export default function Home() {
  return (
    <div className="min-h-screen">
      <header className="mx-auto flex max-w-6xl items-center justify-between px-4 py-4">
        <div className="flex items-center gap-3">
          <Image src="/logo.png" alt="" width={40} height={40} className="rounded-xl" priority />
          <span className="text-lg font-bold">Ghost Me</span>
        </div>
        <nav className="flex items-center gap-2" aria-label="Accesso">
          <CambiaTema />
          <Link href="/accedi" className="btn-secondario">Accedi</Link>
        </nav>
      </header>

      <main className="mx-auto max-w-6xl px-4">
        <section className="grid items-center gap-10 py-12 md:grid-cols-2 md:py-20">
          <div>
            <h1 className="text-4xl font-extrabold leading-tight md:text-5xl">
              Una copia digitale di te, <span className="testo-gradiente">sotto il tuo controllo.</span>
            </h1>
            <p className="mt-5 text-lg text-tenue">
              Ghost Me costruisce un alter ego a partire da ciò che scegli di raccontare: valori, abitudini, gusti e obiettivi.
              Puoi parlarci, esplorare scelte e vedere come cambi nel tempo.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link href="/registrati" className="btn-primario px-6 py-3 text-base">Crea il tuo alter ego</Link>
            </div>
            <p className="mt-6 text-sm text-tenue">
              Le risposte sono generate: non sono previsioni certe, diagnosi o la tua volontà reale.
            </p>
          </div>
          <div className="flex justify-center">
            <Image src="/logo.png" alt="Logo Ghost Me: un volto diviso tra reale e digitale" width={320} height={320}
              className="rounded-[2.5rem] shadow-2xl shadow-viola/30" priority />
          </div>
        </section>

        <section aria-labelledby="funzioni" className="pb-16">
          <h2 id="funzioni" className="sr-only">Funzionalità</h2>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {FUNZIONI.map(({ icona: Icona, titolo, testo }) => (
              <div key={titolo} className="scheda">
                <Icona className="h-6 w-6 text-viola" aria-hidden />
                <h3 className="mt-3 font-semibold">{titolo}</h3>
                <p className="mt-1 text-sm text-tenue">{testo}</p>
              </div>
            ))}
          </div>
        </section>
      </main>

      <footer className="mx-auto max-w-6xl border-t border-bordo px-4 py-6">
        <Copyright />
      </footer>
    </div>
  );
}
