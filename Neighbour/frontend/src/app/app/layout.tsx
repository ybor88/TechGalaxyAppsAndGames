"use client";
// Layout dell'area riservata: menu laterale (desktop) e barra in basso (mobile). © Roberto Di Flumeri

import clsx from "clsx";
import {
  Bell, CalendarDays, Gauge, Home, LogOut, Map, MessageCircle, Plus, Shield, Store, User, Users,
} from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { CambiaTema, Caricamento, Copyright } from "@/components/ui";
import { api } from "@/lib/api";
import { useSessione } from "@/lib/sessione";

const VOCI = [
  { href: "/app", etichetta: "Il mio quartiere", icona: Home },
  { href: "/app/mappa", etichetta: "Mappa", icona: Map },
  { href: "/app/eventi", etichetta: "Eventi", icona: CalendarDays },
  { href: "/app/gruppi", etichetta: "Gruppi", icona: Users },
  { href: "/app/servizi", etichetta: "Servizi locali", icona: Store },
  { href: "/app/chat", etichetta: "Messaggi", icona: MessageCircle, contatore: "chat" },
  { href: "/app/notifiche", etichetta: "Notifiche", icona: Bell, contatore: "notifiche" },
  { href: "/app/profilo", etichetta: "Il mio profilo", icona: User },
  { href: "/app/privacy", etichetta: "Privacy e sicurezza", icona: Shield },
] as const;

const BARRA = [
  { href: "/app", etichetta: "Quartiere", icona: Home },
  { href: "/app/mappa", etichetta: "Mappa", icona: Map },
  { href: "/app/pubblica", etichetta: "Pubblica", icona: Plus },
  { href: "/app/chat", etichetta: "Messaggi", icona: MessageCircle, contatore: "chat" },
  { href: "/app/profilo", etichetta: "Profilo", icona: User },
] as const;

const SENZA_AREA = ["/app/quartiere", "/app/privacy"];

function attiva(href: string, percorso: string) {
  return href === "/app" ? percorso === "/app" : percorso.startsWith(href);
}

function Badge({ n }: { n: number }) {
  if (!n) return null;
  return <span className="ml-auto rounded-full bg-arancio px-2 py-0.5 text-xs font-bold text-white" aria-label={`${n} da leggere`}>{n > 99 ? "99+" : n}</span>;
}

export default function LayoutApp({ children }: { children: React.ReactNode }) {
  const { utente, caricamento, esci } = useSessione();
  const router = useRouter();
  const percorso = usePathname();
  const [conteggi, setConteggi] = useState({ chat: 0, notifiche: 0 });
  const precedenti = useRef<number | null>(null);

  useEffect(() => {
    if (caricamento) return;
    if (!utente) router.replace("/accedi");
    else if (!utente.area && !SENZA_AREA.some((p) => percorso.startsWith(p))) router.replace("/app/quartiere");
  }, [utente, caricamento, percorso, router]);

  // contatori di messaggi e notifiche; avviso del dispositivo se l'utente l'ha permesso
  useEffect(() => {
    if (!utente?.area) return;
    let vivo = true;
    const aggiorna = async () => {
      try {
        const [c, n] = await Promise.all([
          api<{ non_lette: number }>("/v1/conversations/non-lette"),
          api<{ non_lette: number }>("/v1/notifications/conteggio"),
        ]);
        if (!vivo) return;
        setConteggi({ chat: c.non_lette, notifiche: n.non_lette });
        const totale = c.non_lette + n.non_lette;
        if (precedenti.current !== null && totale > precedenti.current && "Notification" in window
            && Notification.permission === "granted" && document.visibilityState === "hidden") {
          navigator.serviceWorker?.ready.then((sw) => sw.showNotification("Neighbour", {
            body: "Hai novità dal tuo quartiere", icon: "/icone/icona-192.png", data: { link: "/app/notifiche" } }))
            .catch(() => undefined);
        }
        precedenti.current = totale;
      } catch {}
    };
    aggiorna();
    const t = setInterval(aggiorna, 30000);
    return () => { vivo = false; clearInterval(t); };
  }, [utente?.area, percorso]);

  if (caricamento || !utente) {
    return <div className="flex min-h-screen items-center justify-center"><Caricamento /></div>;
  }

  const voci = utente.ruolo === "moderatore"
    ? [...VOCI, { href: "/app/moderazione", etichetta: "Moderazione", icona: Gauge } as const]
    : VOCI;

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
      <div className="sticky top-0 z-[500] flex items-center justify-between border-b border-bordo bg-pannello px-4 py-2 md:hidden">
        <Link href="/app" className="flex items-center gap-2">
          <Image src="/logo.png" alt="" width={32} height={32} className="rounded-lg" />
          <span className="font-bold">Neighbour</span>
        </Link>
        <div className="flex items-center gap-1">
          <Link href="/app/notifiche" className="btn-secondario relative px-3" aria-label="Notifiche">
            <Bell className="h-5 w-5" />
            {conteggi.notifiche > 0 && <span className="absolute -right-1 -top-1 h-3 w-3 rounded-full bg-arancio" aria-hidden />}
          </Link>
          <Link href="/app/menu" className="btn-secondario px-3" aria-label="Tutte le sezioni"><Users className="h-5 w-5" /></Link>
        </div>
      </div>

      {/* menu laterale desktop */}
      <aside className="sticky top-0 hidden h-screen w-72 shrink-0 flex-col gap-4 border-r border-bordo bg-pannello p-4 md:flex">
        <Link href="/app" className="flex items-center gap-3">
          <Image src="/logo.png" alt="" width={44} height={44} className="rounded-xl" />
          <div className="min-w-0">
            <div className="font-bold">Neighbour</div>
            <div className="truncate text-xs text-tenue">{utente.alias} · {utente.area?.nome || "scegli il quartiere"}</div>
          </div>
        </Link>
        <Link href="/app/pubblica" className="btn-primario"><Plus className="h-4 w-4" /> Pubblica</Link>
        <nav aria-label="Sezioni" className="flex-1 space-y-1 overflow-y-auto">
          {voci.map((v) => {
            const Icona = v.icona;
            const n = "contatore" in v ? conteggi[v.contatore] : 0;
            return (
              <Link key={v.href} href={v.href} aria-current={attiva(v.href, percorso) ? "page" : undefined}
                className={clsx("flex items-center gap-3 rounded-xl px-3 py-2 text-sm transition",
                  attiva(v.href, percorso) ? "gradiente text-white shadow" : "text-tenue hover:bg-fondo hover:text-testo")}>
                <Icona className="h-4 w-4" aria-hidden /> {v.etichetta} <Badge n={n} />
              </Link>
            );
          })}
        </nav>
        <div className="flex items-center gap-2">
          <CambiaTema />
          <button onClick={esciEVai} className="btn-secondario flex-1"><LogOut className="h-4 w-4" /> Esci</button>
        </div>
        <Copyright />
      </aside>

      <main id="contenuto" className="mx-auto w-full max-w-5xl flex-1 px-4 pb-28 pt-5 md:px-8 md:py-10">
        {children}
        <div className="mt-10 md:hidden"><Copyright /></div>
      </main>

      {/* barra in basso mobile (stile app Android) */}
      <nav aria-label="Navigazione principale"
        className="fixed inset-x-0 bottom-0 z-[500] grid grid-cols-5 border-t border-bordo bg-pannello pb-[env(safe-area-inset-bottom)] md:hidden">
        {BARRA.map((v) => {
          const Icona = v.icona;
          const centrale = v.href === "/app/pubblica";
          const n = "contatore" in v ? conteggi[v.contatore] : 0;
          return (
            <Link key={v.href} href={v.href} aria-current={attiva(v.href, percorso) ? "page" : undefined}
              className={clsx("relative flex flex-col items-center gap-0.5 py-2 text-[11px]",
                attiva(v.href, percorso) ? "font-semibold text-verde" : "text-tenue")}>
              <span className={clsx(centrale && "gradiente -mt-5 rounded-full p-3 text-white shadow-lg")}>
                <Icona className={centrale ? "h-6 w-6" : "h-5 w-5"} aria-hidden />
              </span>
              {v.etichetta}
              {n > 0 && <span className="absolute right-4 top-1 h-2.5 w-2.5 rounded-full bg-arancio" aria-label={`${n} da leggere`} />}
            </Link>
          );
        })}
      </nav>
    </div>
  );
}
