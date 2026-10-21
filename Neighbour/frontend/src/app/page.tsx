"use client";
// Pagina di presentazione — © Roberto Di Flumeri
import { CalendarDays, Gift, HandHeart, Lock, MapPin, Megaphone, ShieldCheck, Store, Users } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { CambiaTema, Copyright } from "@/components/ui";
import { useSessione } from "@/lib/sessione";

const FUNZIONI = [
  { icona: Gift, titolo: "Mercatino e regali", testo: "Dai una seconda vita agli oggetti: regala, scambia o vendi a chi abita vicino." },
  { icona: HandHeart, titolo: "Piccoli aiuti", testo: "La spesa, un passaggio, un telefono da configurare: chiedi o dai una mano." },
  { icona: CalendarDays, titolo: "Eventi di quartiere", testo: "Partite, laboratori, pulizie dei giardini: con capienza e lista d'attesa." },
  { icona: Megaphone, titolo: "Segnalazioni utili", testo: "Una buca, delle chiavi trovate, l'acqua che manca: avvisa i vicini." },
  { icona: Users, titolo: "Gruppi di interesse", testo: "Cani, genitori, sport, orti sul balcone: trova chi condivide le tue passioni." },
  { icona: Store, titolo: "Servizi consigliati", testo: "Artigiani e negozi suggeriti dai vicini, distinti dalle schede dei professionisti." },
];

const PRIVACY = [
  { icona: MapPin, testo: "Posizione sempre approssimata: niente indirizzi, mai" },
  { icona: Lock, testo: "Messaggi cifrati e foto ripulite dai dati GPS" },
  { icona: ShieldCheck, testo: "Blocco e segnalazione in un clic, moderazione con ricorso" },
];

export default function Home() {
  const { utente } = useSessione();
  const router = useRouter();
  useEffect(() => { if (utente) router.replace("/app"); }, [utente, router]);

  return (
    <div className="min-h-screen">
      <header className="mx-auto flex max-w-6xl items-center justify-between px-4 py-4">
        <div className="flex items-center gap-3">
          <Image src="/logo.png" alt="" width={44} height={44} className="rounded-xl" priority />
          <span className="text-xl font-bold">Neighbour</span>
        </div>
        <div className="flex items-center gap-2">
          <CambiaTema />
          <Link href="/accedi" className="btn-secondario">Accedi</Link>
        </div>
      </header>

      <main>
        <section className="mx-auto grid max-w-6xl items-center gap-10 px-4 py-10 md:grid-cols-2 md:py-16">
          <div>
            <p className="pillola bg-prato/15 text-verde">App gratuita · Android e web</p>
            <h1 className="mt-4 text-4xl font-extrabold leading-tight md:text-5xl">Persone vere.<br /><span className="text-verde">Più vicine.</span></h1>
            <p className="mt-4 max-w-xl text-lg text-tenue">
              Neighbour collega chi abita nello stesso quartiere per scambiare oggetti, chiedere piccoli aiuti, scoprire eventi e
              condividere avvisi utili. Una rete circoscritta e basata sulla fiducia, non un social network.
            </p>
            <div className="mt-6 flex flex-wrap gap-3">
              <Link href="/registrati" className="btn-primario px-6 py-3 text-base">Entra nel tuo quartiere</Link>
              <Link href="/termini" className="btn-secondario px-6 py-3 text-base">Come funziona</Link>
            </div>
            <ul className="mt-8 space-y-2">
              {PRIVACY.map(({ icona: Icona, testo }) => (
                <li key={testo} className="flex items-center gap-2 text-sm"><Icona className="h-4 w-4 text-verde" aria-hidden /> {testo}</li>
              ))}
            </ul>
          </div>
          <div className="relative mx-auto w-full max-w-sm">
            <div className="absolute -inset-6 rounded-[3rem] bg-prato/20 blur-2xl" aria-hidden />
            <Image src="/logo.png" alt="Logo di Neighbour: una casa nel verde con un segnaposto" width={420} height={420}
              className="relative rounded-[2.5rem] shadow-2xl" priority />
          </div>
        </section>

        <section className="mx-auto max-w-6xl px-4 py-10">
          <h2 className="text-2xl font-bold">Tutto quello che serve, a pochi passi da casa</h2>
          <ul className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {FUNZIONI.map(({ icona: Icona, titolo, testo }) => (
              <li key={titolo} className="scheda">
                <span className="inline-flex rounded-xl bg-verde/10 p-2 text-verde"><Icona className="h-5 w-5" aria-hidden /></span>
                <h3 className="mt-3 font-semibold">{titolo}</h3>
                <p className="mt-1 text-sm text-tenue">{testo}</p>
              </li>
            ))}
          </ul>
        </section>

        <section className="mx-auto max-w-6xl px-4 pb-16">
          <div className="gradiente rounded-3xl p-8 text-white md:p-12">
            <h2 className="text-2xl font-bold md:text-3xl">Scegli tu fin dove arriva il tuo quartiere</h2>
            <p className="mt-2 max-w-2xl text-white/90">
              Da 300 metri a 5 chilometri: decidi chi vede i tuoi contenuti e da quanto lontano ricevere gli avvisi,
              con ore di silenzio e riepilogo giornaliero.
            </p>
            <Link href="/registrati" className="btn mt-6 bg-white text-[#0b6b3a] hover:brightness-95">Inizia ora</Link>
          </div>
        </section>
      </main>

      <footer className="border-t border-bordo py-6">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4">
          <Copyright />
          <nav className="flex gap-4 text-sm text-tenue"><Link href="/termini" className="hover:underline">Termini e linee guida</Link>
            <Link href="/accedi" className="hover:underline">Accedi</Link></nav>
        </div>
      </footer>
    </div>
  );
}
