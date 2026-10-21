"use client";
// Tutte le sezioni (menu completo su mobile) — © Roberto Di Flumeri
import { Bell, CalendarDays, Gauge, LogOut, Map, MessageCircle, Shield, Store, User, Users } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { CambiaTema, Titolo } from "@/components/ui";
import { useSessione } from "@/lib/sessione";

const SEZIONI = [
  { href: "/app/mappa", titolo: "Mappa", testo: "Cosa succede intorno a te", icona: Map },
  { href: "/app/eventi", titolo: "Eventi", testo: "Partite, laboratori, iniziative", icona: CalendarDays },
  { href: "/app/gruppi", titolo: "Gruppi", testo: "Sport, animali, genitori, hobby", icona: Users },
  { href: "/app/servizi", titolo: "Servizi locali", testo: "Artigiani e negozi consigliati", icona: Store },
  { href: "/app/chat", titolo: "Messaggi", testo: "Le tue conversazioni", icona: MessageCircle },
  { href: "/app/notifiche", titolo: "Notifiche", testo: "Avvisi e preferenze", icona: Bell },
  { href: "/app/profilo", titolo: "Il mio profilo", testo: "Annunci, feedback, quartiere", icona: User },
  { href: "/app/privacy", titolo: "Privacy e sicurezza", testo: "Consensi, blocchi, dati", icona: Shield },
];

export default function Menu() {
  const { utente, esci } = useSessione();
  const router = useRouter();
  const sezioni = utente?.ruolo === "moderatore"
    ? [...SEZIONI, { href: "/app/moderazione", titolo: "Moderazione", testo: "Segnalazioni e ricorsi", icona: Gauge }]
    : SEZIONI;
  return (
    <>
      <Titolo titolo="Tutte le sezioni" sottotitolo={utente?.area?.nome} azioni={<CambiaTema />} />
      <ul className="grid gap-3 sm:grid-cols-2">
        {sezioni.map(({ href, titolo, testo, icona: Icona }) => (
          <li key={href}>
            <Link href={href} className="scheda flex items-center gap-3 transition hover:border-verde">
              <span className="rounded-xl bg-verde/10 p-2 text-verde"><Icona className="h-5 w-5" aria-hidden /></span>
              <span><span className="block font-semibold">{titolo}</span><span className="text-sm text-tenue">{testo}</span></span>
            </Link>
          </li>
        ))}
      </ul>
      <button className="btn-secondario mt-6 w-full" onClick={async () => { await esci(); router.replace("/"); }}>
        <LogOut className="h-4 w-4" /> Esci
      </button>
    </>
  );
}
