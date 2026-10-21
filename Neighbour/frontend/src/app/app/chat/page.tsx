"use client";
// Elenco delle conversazioni (F05) — © Roberto Di Flumeri
import clsx from "clsx";
import { MessageCircle } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Avatar, Avviso, Caricamento, NomeUtente, Titolo, Vuoto } from "@/components/ui";
import { api, fa, type Conversazione } from "@/lib/api";

export default function Chat() {
  const [elenco, setElenco] = useState<Conversazione[] | null>(null);
  const [errore, setErrore] = useState<string | null>(null);
  useEffect(() => {
    const carica = () => api<Conversazione[]>("/v1/conversations").then(setElenco).catch((e) => setErrore(e.message));
    carica();
    const t = setInterval(carica, 15000);
    return () => clearInterval(t);
  }, []);

  return (
    <>
      <Titolo titolo="Messaggi" sottotitolo="Le conversazioni nascono da un annuncio, un evento o un servizio: sono visibili solo a voi due." />
      {errore && <Avviso tipo="errore">{errore}</Avviso>}
      {!elenco ? <Caricamento /> : elenco.length === 0 ? (
        <Vuoto icona={MessageCircle} titolo="Nessuna conversazione" testo="Scrivi a un vicino da un annuncio o da un evento che ti interessa." />
      ) : (
        <ul className="scheda divide-y divide-bordo p-0">
          {elenco.map((c) => (
            <li key={c.id}>
              <Link href={`/app/chat/${c.id}`} className="flex items-center gap-3 p-4 transition hover:bg-fondo">
                <Avatar alias={c.con.alias} />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between gap-2">
                    <NomeUtente u={c.con} />
                    {c.ultimo && <span className="shrink-0 text-xs text-tenue">{fa(c.ultimo.created_at)}</span>}
                  </div>
                  <p className="truncate text-xs font-medium text-verde">{c.contesto_titolo}</p>
                  <p className={clsx("truncate text-sm", c.non_lette ? "font-semibold" : "text-tenue")}>
                    {c.stato === "bloccata" ? "Conversazione bloccata" : c.ultimo ? `${c.ultimo.mio ? "Tu: " : ""}${c.ultimo.testo}` : ""}
                  </p>
                </div>
                {c.non_lette > 0 && <span className="rounded-full bg-arancio px-2 py-0.5 text-xs font-bold text-white">{c.non_lette}</span>}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
