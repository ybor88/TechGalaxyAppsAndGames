"use client";
// Pagina iniziale: presentazione dell'ERP e accesso. © Roberto Di Flumeri
import { BarChart3, Boxes, BrainCircuit, FileText, PlugZap, ShieldCheck, ShoppingCart, Users } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { Marchio } from "@/components/Accoglienza";
import { Copyright } from "@/components/ui";
import { useSessione } from "@/lib/sessione";

const MODULI = [
  { icona: ShoppingCart, titolo: "Ordini multicanale", testo: "Dalla bozza alla consegna, con gli ordini Shopify, WooCommerce e Magento importati in automatico." },
  { icona: Boxes, titolo: "Magazzino multi-sede", testo: "Carichi, scarichi, trasferimenti, inventari con barcode, lotti FEFO e numeri di serie." },
  { icona: FileText, titolo: "Fatturazione elettronica", testo: "Preventivi, DDT, fatture e note di credito con XML FatturaPA pronto per lo SdI." },
  { icona: Users, titolo: "CRM e fornitori", testo: "Anagrafiche con controllo di partita IVA e codice fiscale, storico e crediti aperti." },
  { icona: BarChart3, titolo: "Analytics", testo: "Margini, canali, categorie, analisi ABC e rotazione del magazzino." },
  { icona: BrainCircuit, titolo: "Motore AI", testo: "Previsione delle vendite, riordino automatico, segmentazione clienti e alert intelligenti." },
  { icona: PlugZap, titolo: "Integrazioni", testo: "E-commerce, pagamenti Stripe e PayPal, spedizioni GLS e DHL, API OAuth2." },
  { icona: ShieldCheck, titolo: "Sicurezza", testo: "Token JWT a rotazione, verifica in due passaggi, SSO OpenID Connect e registro attività." },
];

export default function Home() {
  const { utente, caricamento } = useSessione();
  const router = useRouter();
  useEffect(() => { if (!caricamento && utente) router.replace("/app"); }, [utente, caricamento, router]);

  return (
    <div className="sfondo-notte min-h-screen text-white">
      <header className="mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 py-5">
        <Marchio />
        <nav className="flex gap-2">
          <Link href="/accedi" className="btn border border-white/20 text-white hover:bg-white/10">Accedi</Link>
          <Link href="/registrati" className="btn-primario hidden sm:inline-flex">Prova gratis</Link>
        </nav>
      </header>
      <main className="mx-auto max-w-6xl px-4 pb-16">
        <section className="grid items-center gap-10 py-10 md:grid-cols-[1.2fr_1fr] md:py-16">
          <div>
            <p className="pillola mb-4 border border-white/15 bg-white/5 text-[#b8c2e6]">ERP cloud per PMI ed e-commerce</p>
            <h1 className="text-4xl font-extrabold leading-tight sm:text-5xl">
              Tutta l&apos;azienda in un&apos;unica piattaforma, <span className="testo-gradiente">guidata dall&apos;intelligenza artificiale</span>.
            </h1>
            <p className="mt-5 max-w-xl text-lg text-[#b8c2e6]">
              Vendite, magazzino, acquisti e fatturazione elettronica collegati tra loro: ogni ordine aggiorna giacenze, documenti e incassi,
              e il motore AI ti dice cosa riordinare, quali clienti contattare e dove intervenire.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link href="/registrati" className="btn-primario px-6 py-3 text-base">Crea la tua azienda</Link>
              <Link href="/accedi" className="btn border border-white/20 px-6 py-3 text-base text-white hover:bg-white/10">Entra nella demo</Link>
            </div>
            <p className="mt-4 text-sm text-[#98a4cc]">Demo: <code>admin@nexora.demo</code> · <code>NexoraDemo2026</code></p>
          </div>
          <div className="flex justify-center">
            <Image src="/logo.png" alt="Logo Nexora" width={340} height={340} priority className="drop-shadow-[0_30px_60px_rgba(59,130,246,0.35)]" />
          </div>
        </section>
        <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-label="Moduli">
          {MODULI.map(({ icona: I, titolo, testo }) => (
            <div key={titolo} className="rounded-2xl border border-white/10 bg-white/5 p-5 backdrop-blur">
              <span className="mb-3 inline-flex h-10 w-10 items-center justify-center rounded-xl gradiente"><I className="h-5 w-5 text-white" aria-hidden /></span>
              <h2 className="font-semibold">{titolo}</h2>
              <p className="mt-1 text-sm text-[#b8c2e6]">{testo}</p>
            </div>
          ))}
        </section>
      </main>
      <footer className="pb-8 text-center"><Copyright className="text-[#98a4cc]" /></footer>
    </div>
  );
}
