"use client";
// Area riservata: protezione e navigazione — © Roberto Di Flumeri
import clsx from "clsx";
import {
  BookOpen, CalendarClock, Gauge, HeartHandshake, Home, Layers, LogOut, Menu, MessageCircle,
  NotebookPen, Puzzle, Scale, Share2, Shield, UserRound, X,
} from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { CambiaTema, Caricamento, Copyright } from "@/components/ui";
import { useSessione } from "@/lib/sessione";

const VOCI = [
  { href: "/app", etichetta: "Home", icona: Home },
  { href: "/app/profilo", etichetta: "Il mio profilo", icona: UserRound },
  { href: "/app/conoscenze", etichetta: "Conoscenze", icona: BookOpen },
  { href: "/app/chat", etichetta: "Chat con l'alter ego", icona: MessageCircle },
  { href: "/app/decisioni", etichetta: "Simulatore decisioni", icona: Scale },
  { href: "/app/versioni", etichetta: "Versioni", icona: Layers },
  { href: "/app/timeline", etichetta: "Timeline", icona: CalendarClock },
  { href: "/app/diario", etichetta: "Diario", icona: NotebookPen },
  { href: "/app/sfide", etichetta: "Sfide", icona: Puzzle },
  { href: "/app/condivisione", etichetta: "Condivisione", icona: Share2 },
  { href: "/app/compatibilita", etichetta: "Compatibilità", icona: HeartHandshake },
  { href: "/app/privacy", etichetta: "Privacy e dati", icona: Shield },
];

export default function LayoutApp({ children }: { children: React.ReactNode }) {
  const { utente, caricamento, esci } = useSessione();
  const router = useRouter();
  const percorso = usePathname();
  const [menuAperto, setMenuAperto] = useState(false);

  useEffect(() => {
    if (!caricamento && !utente) router.replace("/accedi");
  }, [caricamento, utente, router]);

  useEffect(() => { setMenuAperto(false); }, [percorso]);

  if (caricamento || !utente) {
    return <div className="flex min-h-screen items-center justify-center"><Caricamento /></div>;
  }

  const voci = utente.ruolo === "moderatore"
    ? [...VOCI, { href: "/app/moderazione", etichetta: "Moderazione", icona: Gauge }]
    : VOCI;

  const menu = (
    <nav aria-label="Navigazione principale" className="flex flex-col gap-1">
      {voci.map(({ href, etichetta, icona: Icona }) => {
        const attiva = href === "/app" ? percorso === "/app" : percorso.startsWith(href);
        return (
          <Link key={href} href={href} aria-current={attiva ? "page" : undefined}
            className={clsx("flex items-center gap-3 rounded-xl px-3 py-2 text-sm",
              attiva ? "gradiente font-semibold text-white" : "text-tenue hover:bg-fondo hover:text-testo")}>
            <Icona className="h-4 w-4" aria-hidden /> {etichetta}
          </Link>
        );
      })}
    </nav>
  );

  const esciEVai = async () => {
    await esci();
    router.replace("/");
  };

  return (
    <div className="min-h-screen md:flex">
      <a href="#contenuto" className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 btn-primario">
        Vai al contenuto
      </a>

      {/* barra superiore mobile */}
      <div className="sticky top-0 z-30 flex items-center justify-between border-b border-bordo bg-pannello px-4 py-3 md:hidden">
        <div className="flex items-center gap-2">
          <Image src="/logo.png" alt="" width={32} height={32} className="rounded-lg" />
          <span className="font-bold">Ghost Me</span>
        </div>
        <button className="btn-secondario px-3" onClick={() => setMenuAperto(true)} aria-label="Apri il menu" aria-expanded={menuAperto}>
          <Menu className="h-5 w-5" />
        </button>
      </div>

      {/* menu laterale (desktop) e cassetto (mobile) */}
      <aside className={clsx(
        "fixed inset-y-0 left-0 z-40 flex w-72 flex-col gap-4 border-r border-bordo bg-pannello p-4 transition-transform md:sticky md:top-0 md:h-screen md:translate-x-0",
        menuAperto ? "translate-x-0" : "-translate-x-full",
      )}>
        <div className="flex items-center justify-between">
          <Link href="/app" className="flex items-center gap-3">
            <Image src="/logo.png" alt="" width={40} height={40} className="rounded-xl" />
            <div>
              <div className="font-bold">Ghost Me</div>
              <div className="text-xs text-tenue">{utente.nome_visualizzato}</div>
            </div>
          </Link>
          <button className="btn-secondario px-2 md:hidden" onClick={() => setMenuAperto(false)} aria-label="Chiudi il menu">
            <X className="h-4 w-4" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto">{menu}</div>
        <div className="flex items-center gap-2">
          <CambiaTema />
          <button onClick={esciEVai} className="btn-secondario flex-1"><LogOut className="h-4 w-4" /> Esci</button>
        </div>
        <Copyright />
      </aside>
      {menuAperto && <div className="fixed inset-0 z-30 bg-black/40 md:hidden" onClick={() => setMenuAperto(false)} aria-hidden />}

      <main id="contenuto" className="mx-auto w-full max-w-5xl flex-1 px-4 py-6 md:px-8 md:py-10">
        {children}
      </main>
    </div>
  );
}

