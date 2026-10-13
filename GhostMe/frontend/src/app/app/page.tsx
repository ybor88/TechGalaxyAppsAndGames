"use client";
// Home dell'area riservata — © Roberto Di Flumeri
import Link from "next/link";
import { ArrowRight, Layers, MessageCircle, NotebookPen, Puzzle, Scale, UserRound } from "lucide-react";
import { useEffect, useState } from "react";
import { Avviso, Caricamento, Titolo } from "@/components/ui";
import { api, type Dichiarazione } from "@/lib/api";
import { useSessione } from "@/lib/sessione";

interface Riepilogo {
  dichiarazioni: Dichiarazione[];
  versioni: number;
  conversazioni: number;
  ai: boolean;
  motore: { ollama_disponibile: boolean; modello: string };
}

export default function HomeApp() {
  const { utente } = useSessione();
  const [r, setR] = useState<Riepilogo | null>(null);
  const [errore, setErrore] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([
      api<Dichiarazione[]>("/v1/profile/statements"),
      api<unknown[]>("/v1/snapshots"),
      api<unknown[]>("/v1/conversations"),
      api<{ stato: { finalita: string; concesso: boolean }[] }>("/v1/consents"),
      api<{ ollama_disponibile: boolean; modello: string }>("/v1/ai/stato"),
    ])
      .then(([d, v, c, cons, motore]) =>
        setR({ dichiarazioni: d, versioni: v.length, conversazioni: c.length, motore,
               ai: cons.stato.some((x) => x.finalita === "elaborazione_ai" && x.concesso) }))
      .catch((e) => setErrore(e.message));
  }, []);

  if (errore) return <Avviso tipo="errore">{errore}</Avviso>;
  if (!r) return <Caricamento />;

  const azioni = [
    { href: "/app/profilo", titolo: "Completa il profilo", testo: "Rispondi al questionario e aggiungi dichiarazioni.", icona: UserRound },
    { href: "/app/chat", titolo: "Parla con il tuo alter ego", testo: "Chiedigli cosa preferiresti o come la pensi.", icona: MessageCircle },
    { href: "/app/decisioni", titolo: "Simula una decisione", testo: "Confronta opzioni con le tue priorità.", icona: Scale },
    { href: "/app/versioni", titolo: "Salva una versione", testo: "Fotografa il profilo per confrontarlo in futuro.", icona: Layers },
    { href: "/app/diario", titolo: "Scrivi nel diario", testo: "Un check-in rapido su come stai cambiando.", icona: NotebookPen },
    { href: "/app/sfide", titolo: "Prova una sfida", testo: "Mini esercizi per conoscerti meglio.", icona: Puzzle },
  ];

  return (
    <>
      <Titolo titolo={`Ciao ${utente?.nome_visualizzato ?? ""}`} sottotitolo="Il tuo alter ego cresce solo con ciò che decidi di condividere." />

      {r.dichiarazioni.length === 0 && (
        <div className="mb-6">
          <Avviso tipo="info">
            Il tuo profilo è vuoto. <Link href="/app/profilo" className="font-semibold underline">Inizia dal questionario</Link>: bastano pochi minuti.
          </Avviso>
        </div>
      )}
      {!r.ai && (
        <div className="mb-6">
          <Avviso tipo="attenzione">
            Chat e simulatore sono disattivati perché non hai dato il consenso all&apos;elaborazione AI.{" "}
            <Link href="/app/privacy" className="font-semibold underline">Gestisci i consensi</Link>
          </Avviso>
        </div>
      )}

      <section className="mb-8 grid gap-4 sm:grid-cols-3" aria-label="Riepilogo">
        <div className="scheda"><div className="text-sm text-tenue">Dichiarazioni</div><div className="text-3xl font-bold">{r.dichiarazioni.length}</div></div>
        <div className="scheda"><div className="text-sm text-tenue">Versioni salvate</div><div className="text-3xl font-bold">{r.versioni}</div></div>
        <div className="scheda"><div className="text-sm text-tenue">Conversazioni</div><div className="text-3xl font-bold">{r.conversazioni}</div></div>
      </section>

      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-label="Azioni rapide">
        {azioni.map(({ href, titolo, testo, icona: Icona }) => (
          <Link key={href} href={href} className="scheda group transition hover:border-blu">
            <Icona className="h-6 w-6 text-viola" aria-hidden />
            <div className="mt-3 flex items-center justify-between font-semibold">
              {titolo} <ArrowRight className="h-4 w-4 transition group-hover:translate-x-1" aria-hidden />
            </div>
            <p className="mt-1 text-sm text-tenue">{testo}</p>
          </Link>
        ))}
      </section>

      <p className="mt-8 text-sm text-tenue">
        Motore AI: {utente?.motore_ai === "locale" ? "integrato (istantaneo)" : `Ollama ${r.motore.modello}`}
        {utente?.motore_ai !== "locale" && !r.motore.ollama_disponibile && " — non raggiungibile, verrà usato il motore integrato"}.
      </p>
    </>
  );
}
