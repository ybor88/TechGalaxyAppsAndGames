import Link from "next/link";
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
    colore: "blue",
  },
  {
    id: 2,
    titolo: "Fatturazione",
    descrizione: "Preventivi, ordini, fatture attive/passive, note credito e generazione PDF.",
    icona: FileText,
    href: "#",
    attivo: false,
    colore: "violet",
  },
  {
    id: 3,
    titolo: "OCR Contabile",
    descrizione: "Importazione automatica dati da fatture scansionate o PDF caricati.",
    icona: ScanLine,
    href: "#",
    attivo: false,
    colore: "teal",
  },
  {
    id: 4,
    titolo: "Contabilità Generale",
    descrizione: "Piano dei conti, partita doppia, prima nota, IVA e bilancio contabile.",
    icona: BookOpen,
    href: "#",
    attivo: false,
    colore: "orange",
  },
  {
    id: 5,
    titolo: "CRM Economico",
    descrizione: "Anagrafica clienti e fornitori, storico pagamenti, scadenze e pipeline.",
    icona: Users,
    href: "#",
    attivo: false,
    colore: "pink",
  },
  {
    id: 6,
    titolo: "Workflow Aziendale",
    descrizione: "Task management, approvazioni, reminder e automazione acquisti interni.",
    icona: GitBranch,
    href: "#",
    attivo: false,
    colore: "yellow",
  },
  {
    id: 7,
    titolo: "Forecasting",
    descrizione: "Previsione vendite, liquidità, simulazione scenari e rischio insolvenza.",
    icona: TrendingUp,
    href: "#",
    attivo: false,
    colore: "green",
  },
  {
    id: 8,
    titolo: "AI Assistant",
    descrizione: "Interroga i dati in linguaggio naturale. Report automatici e analisi KPI.",
    icona: Bot,
    href: "#",
    attivo: false,
    colore: "indigo",
  },
];

const COLOR_MAP: Record<string, { ring: string; icon: string; badge: string }> = {
  blue:   { ring: "group-hover:ring-blue-400",   icon: "text-blue-500 bg-blue-50",   badge: "bg-blue-500" },
  violet: { ring: "group-hover:ring-violet-400", icon: "text-violet-500 bg-violet-50", badge: "bg-violet-400" },
  teal:   { ring: "group-hover:ring-teal-400",   icon: "text-teal-500 bg-teal-50",   badge: "bg-teal-400" },
  orange: { ring: "group-hover:ring-orange-400", icon: "text-orange-500 bg-orange-50", badge: "bg-orange-400" },
  pink:   { ring: "group-hover:ring-pink-400",   icon: "text-pink-500 bg-pink-50",   badge: "bg-pink-400" },
  yellow: { ring: "group-hover:ring-yellow-400", icon: "text-yellow-500 bg-yellow-50", badge: "bg-yellow-400" },
  green:  { ring: "group-hover:ring-green-400",  icon: "text-green-500 bg-green-50",  badge: "bg-green-400" },
  indigo: { ring: "group-hover:ring-indigo-400", icon: "text-indigo-500 bg-indigo-50", badge: "bg-indigo-400" },
};

export default function HomePage() {
  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      {/* Header */}
      <header className="bg-white border-b border-gray-100 shadow-sm">
        <div className="max-w-7xl mx-auto px-6 py-5 flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-blue-600 flex items-center justify-center">
            <LayoutDashboard size={18} className="text-white" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-gray-900 leading-none">Contabilità 2.0</h1>
            <p className="text-xs text-gray-400 mt-0.5">ERP aziendale 100% open source</p>
          </div>
        </div>
      </header>

      {/* Body */}
      <main className="flex-1 max-w-7xl mx-auto w-full px-6 py-10">
        <div className="mb-8">
          <h2 className="text-2xl font-bold text-gray-900">Seleziona un modulo</h2>
          <p className="text-sm text-gray-400 mt-1">
            I moduli contrassegnati come <span className="font-medium text-gray-500">In arrivo</span> sono pianificati e verranno attivati nelle prossime versioni.
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
          {MODULI.map((modulo) => {
            const c = COLOR_MAP[modulo.colore];
            const Icona = modulo.icona;

            const card = (
              <div
                className={`group relative bg-white rounded-2xl border border-gray-100 p-6 flex flex-col gap-4 shadow-sm transition-all duration-200
                  ${modulo.attivo
                    ? `cursor-pointer hover:shadow-md ring-2 ring-transparent ${c.ring}`
                    : "opacity-60 cursor-default select-none"
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

      {/* Footer */}
      <footer className="border-t border-gray-100 py-4">
        <p className="text-center text-xs text-gray-300">
          Contabilità 2.0 — v1.0.0 — 100% open source, 0€
        </p>
      </footer>
    </div>
  );
}
