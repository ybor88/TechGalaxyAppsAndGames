"use client";
// Layout dell'area riservata: barra laterale blu notte (come il logo), menu per ruolo, alert in evidenza. © Roberto Di Flumeri

import clsx from "clsx";
import {
  BarChart3, Bell, Boxes, BrainCircuit, Building, FileText, LayoutDashboard, LogOut, Menu, Package, PlugZap, Settings,
  ShoppingCart, Truck, Users, Wallet, X,
} from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Marchio } from "@/components/Accoglienza";
import { CambiaTema, Caricamento, Copyright, NOMI_RUOLO } from "@/components/ui";
import { api } from "@/lib/api";
import { useSessione } from "@/lib/sessione";

type Voce = { href: string; etichetta: string; icona: React.ComponentType<{ className?: string }>; badge?: boolean };
const SEZIONI: { titolo: string; voci: Voce[] }[] = [
  { titolo: "Panoramica", voci: [
    { href: "/app", etichetta: "Dashboard", icona: LayoutDashboard },
    { href: "/app/alert", etichetta: "Alert", icona: Bell, badge: true },
  ] },
  { titolo: "Vendite", voci: [
    { href: "/app/ordini", etichetta: "Ordini", icona: ShoppingCart },
    { href: "/app/clienti", etichetta: "Clienti", icona: Users },
    { href: "/app/documenti", etichetta: "Documenti", icona: FileText },
    { href: "/app/scadenze", etichetta: "Scadenze e incassi", icona: Wallet },
  ] },
  { titolo: "Operazioni", voci: [
    { href: "/app/prodotti", etichetta: "Prodotti", icona: Package },
    { href: "/app/magazzino", etichetta: "Magazzino", icona: Boxes },
    { href: "/app/acquisti", etichetta: "Acquisti", icona: Truck },
    { href: "/app/fornitori", etichetta: "Fornitori", icona: Building },
  ] },
  { titolo: "Intelligenza", voci: [
    { href: "/app/analytics", etichetta: "Analytics", icona: BarChart3 },
    { href: "/app/ai", etichetta: "Motore AI", icona: BrainCircuit },
  ] },
  { titolo: "Sistema", voci: [
    { href: "/app/integrazioni", etichetta: "Integrazioni", icona: PlugZap },
    { href: "/app/impostazioni", etichetta: "Impostazioni", icona: Settings },
  ] },
];

const attiva = (href: string, p: string) => (href === "/app" ? p === "/app" : p === href || p.startsWith(`${href}/`));

export default function LayoutApp({ children }: { children: React.ReactNode }) {
  const { utente, caricamento, esci } = useSessione();
  const router = useRouter();
  const percorso = usePathname();
  const [menu, setMenu] = useState(false);
  const [alert, setAlert] = useState(0);

  useEffect(() => { if (!caricamento && !utente) router.replace("/accedi"); }, [utente, caricamento, router]);
  useEffect(() => { setMenu(false); }, [percorso]);
  useEffect(() => {
    if (!utente) return;
    let vivo = true;
    const conta = () => api<{ letto: boolean }[]>("/api/alert").then((a) => vivo && setAlert(a.filter((x) => !x.letto).length)).catch(() => undefined);
    conta();
    const t = setInterval(conta, 60_000);
    window.addEventListener("nx-alert", conta);
    return () => { vivo = false; clearInterval(t); window.removeEventListener("nx-alert", conta); };
  }, [utente]);

  if (caricamento || !utente) return <div className="flex min-h-screen items-center justify-center"><Caricamento testo="Apertura di Nexora…" /></div>;

  const iniziali = utente.nome.split(/\s+/).map((p) => p[0]).join("").slice(0, 2).toUpperCase();
  const barra = (
    <nav className="flex h-full flex-col" aria-label="Menu principale">
      <div className="flex items-center justify-between px-5 pb-4 pt-5">
        <Link href="/app"><Marchio dimensione={36} /></Link>
        <button className="rounded-lg p-1 text-white/70 hover:bg-white/10 lg:hidden" onClick={() => setMenu(false)} aria-label="Chiudi menu"><X className="h-5 w-5" /></button>
      </div>
      <div className="mx-4 mb-3 rounded-xl border border-white/10 bg-white/5 px-3 py-2">
        <p className="truncate text-sm font-semibold text-white">{utente.azienda.ragioneSociale}</p>
        <p className="text-xs text-[#98a4cc]">P.IVA {utente.azienda.partitaIva}</p>
      </div>
      <div className="flex-1 space-y-4 overflow-y-auto px-3 pb-4">
        {SEZIONI.map((s) => (
          <div key={s.titolo}>
            <p className="px-2 pb-1 text-[11px] font-semibold uppercase tracking-wider text-[#7d89b8]">{s.titolo}</p>
            <ul className="space-y-0.5">
              {s.voci.map(({ href, etichetta, icona: I, badge }) => {
                const on = attiva(href, percorso);
                return (
                  <li key={href}>
                    <Link href={href} aria-current={on ? "page" : undefined}
                      className={clsx("flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium transition",
                        on ? "gradiente text-white shadow-lg shadow-indigo-900/40" : "text-[#c3cbeb] hover:bg-white/10 hover:text-white")}>
                      <I className="h-4 w-4 shrink-0" aria-hidden />
                      <span className="flex-1">{etichetta}</span>
                      {badge && alert > 0 && <span className="rounded-full bg-[#8b5cf6] px-2 py-0.5 text-[11px] font-bold text-white" aria-label={`${alert} non letti`}>{alert}</span>}
                    </Link>
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </div>
      <div className="border-t border-white/10 p-4">
        <div className="flex items-center gap-3">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full gradiente-logo text-xs font-bold text-white" aria-hidden>{iniziali}</span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-white">{utente.nome}</p>
            <p className="truncate text-xs text-[#98a4cc]">{NOMI_RUOLO[utente.ruolo] || utente.ruolo}</p>
          </div>
          <CambiaTema chiaro />
          <button className="btn border border-white/20 px-3 text-white hover:bg-white/10" title="Esci" aria-label="Esci"
            onClick={async () => { await esci(); router.replace("/accedi"); }}><LogOut className="h-4 w-4" /></button>
        </div>
        <Copyright className="mt-3 text-[#7d89b8]" />
      </div>
    </nav>
  );

  return (
    <div className="min-h-screen lg:pl-64">
      <aside className="sfondo-notte fixed inset-y-0 left-0 z-40 hidden w-64 lg:block">{barra}</aside>
      {menu && (
        <div className="fixed inset-0 z-[900] lg:hidden" role="dialog" aria-modal="true" aria-label="Menu">
          <div className="absolute inset-0 bg-black/50" onClick={() => setMenu(false)} />
          <aside className="sfondo-notte absolute inset-y-0 left-0 w-72 max-w-[85vw] shadow-2xl">{barra}</aside>
        </div>
      )}
      <header className="barra sfondo-notte sticky top-0 z-30 flex items-center justify-between px-4 py-3 lg:hidden">
        <button className="rounded-lg p-1.5 text-white hover:bg-white/10" onClick={() => setMenu(true)} aria-label="Apri menu"><Menu className="h-6 w-6" /></button>
        <Link href="/app"><Marchio dimensione={30} /></Link>
        <Link href="/app/alert" className="relative rounded-lg p-1.5 text-white hover:bg-white/10" aria-label="Alert">
          <Bell className="h-6 w-6" />{alert > 0 && <span className="absolute -right-0.5 -top-0.5 rounded-full bg-[#8b5cf6] px-1.5 text-[10px] font-bold">{alert}</span>}
        </Link>
      </header>
      <main className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8 lg:py-8">{children}</main>
      <footer className="no-stampa px-8 pb-6 lg:hidden"><Copyright /></footer>
    </div>
  );
}

