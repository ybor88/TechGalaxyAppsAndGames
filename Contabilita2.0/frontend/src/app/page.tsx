import Link from "next/link";
import Image from "next/image";
import {
  LayoutDashboard,
  FileText,
  ScanLine,
  BookOpen,
  Users,
  GitBranch,
  TrendingUp,
  Bot,
} from "lucide-react";

const MODULI = [
  {
    id: 1,
    titolo: "Dashboard Finanziaria",
    descrizione: "Saldo operativo, entrate/uscite, cashflow e KPI economici in tempo reale.",
    icona: LayoutDashboard,
    href: "/dashboard",
    attivo: true,
    colore: "navy",
  },
  {
    id: 1.1,
    titolo: "Movimenti",
    descrizione: "Inserisci e gestisci entrate e uscite aziendali manualmente.",
    icona: TrendingUp,
    href: "/movimenti",
    attivo: true,
    colore: "green",
  },
  {
    id: 1.2,
    titolo: "Piano dei Conti",
    descrizione: "Crea e gestisci il piano dei conti aziendale.",
    icona: BookOpen,
    href: "/conti",
    attivo: true,
    colore: "orange",
  },
  {
    id: 2,
    titolo: "Fatturazione",
    descrizione: "Preventivi, ordini, fatture attive/passive, note credito e generazione PDF.",
    icona: FileText,
    href: "/fatturazione",
    attivo: true,
    colore: "violet",
  },
  {
    id: 3,
    titolo: "OCR Contabile",
    descrizione: "Importazione automatica dati da fatture scansionate o PDF caricati.",
    icona: ScanLine,
    href: "/ocr",
    attivo: true,
    colore: "teal",
  },
  {
    id: 4,
    titolo: "Contabilità Generale",
    descrizione: "Piano dei conti, partita doppia, prima nota, IVA e bilancio contabile.",
    icona: BookOpen,
    href: "/contabilita",
    attivo: true,
    colore: "orange",
  },
  {
    id: 5,
    titolo: "CRM Economico",
    descrizione: "Anagrafica clienti e fornitori, storico pagamenti, scadenze e pipeline.",
    icona: Users,
    href: "/crm",
    attivo: true,
    colore: "pink",
  },
  {
    id: 6,
    titolo: "Workflow Aziendale",
    descrizione: "Task management, approvazioni, reminder e automazione acquisti interni.",
    icona: GitBranch,
    href: "/workflow",
    attivo: true,
    colore: "yellow",
  },
  {
    id: 7,
    titolo: "Forecasting",
    descrizione: "Previsione vendite, liquidità, simulazione scenari e rischio insolvenza.",
    icona: TrendingUp,
    href: "/forecasting",
    attivo: true,
    colore: "green",
  },
  {
    id: 8,
    titolo: "AI Assistant",
    descrizione: "Interroga i dati in linguaggio naturale. Report automatici e analisi KPI.",
    icona: Bot,
    href: "/ai-assistant",
    attivo: true,
    colore: "indigo",
  },
];

const COLOR_MAP: Record<string, { ring: string; icon: string; badge: string }> = {
  navy:   { ring: "group-hover:ring-[#1e3a8a]",  icon: "text-[#1e3a8a] bg-blue-50",      badge: "bg-[#1e3a8a]" },
  violet: { ring: "group-hover:ring-violet-400",  icon: "text-violet-600 bg-violet-50",   badge: "bg-violet-500" },
  teal:   { ring: "group-hover:ring-teal-400",    icon: "text-teal-600 bg-teal-50",        badge: "bg-teal-500" },
  orange: { ring: "group-hover:ring-orange-400",  icon: "text-[#f97316] bg-orange-50",    badge: "bg-[#ea580c]" },
  pink:   { ring: "group-hover:ring-pink-400",    icon: "text-pink-600 bg-pink-50",        badge: "bg-pink-500" },
  yellow: { ring: "group-hover:ring-amber-400",   icon: "text-[#d97706] bg-amber-50",     badge: "bg-amber-500" },
  green:  { ring: "group-hover:ring-green-500",   icon: "text-[#16a34a] bg-green-50",     badge: "bg-[#16a34a]" },
  indigo: { ring: "group-hover:ring-indigo-400",  icon: "text-indigo-600 bg-indigo-50",   badge: "bg-indigo-500" },
};

export default function HomePage() {
  return (
    <div className="min-h-screen bg-[#f8fafc] flex flex-col">
      {/* ── Header ──────────────────────────────────────────── */}
      <header className="bg-white border-b border-gray-100 shadow-sm">
        <div className="max-w-7xl mx-auto px-6 py-4 flex items-center gap-4">
          {/* Logo */}
          <Image
            src="/logo.jpeg"
            alt="Contabilità 2.0 logo"
            width={52}
            height={52}
            className="logo-glow rounded-xl object-contain"
            priority
          />
          <div>
            <h1 className="text-xl font-bold leading-none">
              <span className="text-[#1e3a8a]">Contabilità</span>{" "}
              <span className="text-[#f97316]">2</span>
              <span className="text-[#16a34a]">.</span>
              <span className="text-[#f59e0b]">0</span>
            </h1>
            <p className="text-xs text-gray-400 mt-0.5">ERP aziendale 100% open source</p>
          </div>
        </div>
      </header>

      {/* ── Body ────────────────────────────────────────────── */}
      <main className="flex-1 max-w-7xl mx-auto w-full px-6 py-10">
        <div className="mb-8">
          <h2 className="text-2xl font-bold text-[#1e3a8a]">Seleziona un modulo</h2>
          <p className="text-sm text-gray-400 mt-1">
            I moduli contrassegnati come{" "}
            <span className="font-medium text-gray-500">In arrivo</span> sono pianificati e
            verranno attivati nelle prossime versioni.
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
          {MODULI.map((modulo) => {
            const c = COLOR_MAP[modulo.colore];
            const Icona = modulo.icona;

            const card = (
              <div
                className={`group relative bg-white rounded-2xl border border-gray-100 p-6 flex flex-col gap-4 shadow-sm transition-all duration-200
                  ${
                    modulo.attivo
                      ? `cursor-pointer hover:shadow-md ring-2 ring-transparent ${c.ring}`
                      : "opacity-55 cursor-default select-none"
                  }`}
              >
                {/* Badge stato */}
                <span
                  className={`absolute top-4 right-4 text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5 rounded-full text-white
                    ${modulo.attivo ? c.badge : "bg-gray-300"}`}
                >
                  {modulo.attivo ? "Attivo" : "In arrivo"}
                </span>

                {/* Icona */}
                <div className={`w-11 h-11 rounded-xl flex items-center justify-center ${c.icon}`}>
                  <Icona size={22} />
                </div>

                {/* Testo */}
                <div>
                  <h3 className="font-semibold text-gray-800 text-sm leading-snug mb-1">
                    {modulo.titolo}
                  </h3>
                  <p className="text-xs text-gray-400 leading-relaxed">{modulo.descrizione}</p>
                </div>
              </div>
            );

            return modulo.attivo ? (
              <Link key={modulo.id} href={modulo.href} className="focus:outline-none">
                {card}
              </Link>
            ) : (
              <div key={modulo.id}>{card}</div>
            );
          })}
        </div>
      </main>

      {/* ── Footer ──────────────────────────────────────────── */}
      <footer className="border-t border-gray-100 py-5">
        <div className="flex flex-col items-center justify-center gap-1">
          <div className="flex items-center gap-2">
            <Image
              src="/logo.jpeg"
              alt="Contabilità 2.0"
              width={22}
              height={22}
              className="rounded opacity-60"
            />
            <p className="text-xs text-gray-300">
              Contabilità 2.0 — v1.0.0 — 100% open source, 0€
            </p>
          </div>
          <p className="text-[11px] text-blue-900">
            © {new Date().getFullYear()} Roberto Di Flumeri — Full Stack Developer
          </p>
        </div>
      </footer>
    </div>
  );
}
