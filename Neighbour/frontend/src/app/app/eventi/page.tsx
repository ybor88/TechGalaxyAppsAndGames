"use client";
// Eventi locali (F03): nel quartiere, organizzati e a cui partecipo — © Roberto Di Flumeri
import clsx from "clsx";
import { CalendarDays, Plus } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import SchedaElemento from "@/components/SchedaElemento";
import { Avviso, Caricamento, Titolo, Vuoto } from "@/components/ui";
import { api, type Evento } from "@/lib/api";

export default function Eventi() {
  const [scheda, setScheda] = useState<"quartiere" | "miei">("quartiere");
  const [quartiere, setQuartiere] = useState<Evento[] | null>(null);
  const [miei, setMiei] = useState<Evento[] | null>(null);
  const [errore, setErrore] = useState<string | null>(null);

  useEffect(() => {
    api<{ elementi: Evento[] }>("/v1/feed?tipo=evento&limite=50")
      .then((r) => setQuartiere([...r.elementi].sort((a, b) => a.inizio.localeCompare(b.inizio)))).catch((e) => setErrore(e.message));
    api<Evento[]>("/v1/events").then(setMiei).catch((e) => setErrore(e.message));
  }, []);

  const elenco = scheda === "quartiere" ? quartiere : miei;
  return (
    <>
      <Titolo titolo="Eventi" sottotitolo="Partite, laboratori, iniziative: con capienza e lista d'attesa."
        azioni={<Link href="/app/eventi/nuovo" className="btn-primario"><Plus className="h-4 w-4" /> Crea un evento</Link>} />
      <div className="mb-4 flex gap-2" role="tablist">
        {([["quartiere", "Nel quartiere"], ["miei", "Organizzo e partecipo"]] as const).map(([id, nome]) => (
          <button key={id} role="tab" aria-selected={scheda === id} onClick={() => setScheda(id)}
            className={clsx("filtro", scheda === id ? "gradiente border-transparent text-white" : "border-bordo bg-pannello")}>
            {nome}{id === "miei" && miei ? ` (${miei.length})` : ""}</button>
        ))}
      </div>
      {errore && <Avviso tipo="errore">{errore}</Avviso>}
      {!elenco ? <Caricamento /> : elenco.length === 0 ? (
        <Vuoto icona={CalendarDays} titolo={scheda === "quartiere" ? "Nessun evento in programma" : "Non hai ancora eventi"}
          testo="Proponi tu qualcosa: una partita, una passeggiata, un mercatino del baratto."
          azione={<Link href="/app/eventi/nuovo" className="btn-primario"><Plus className="h-4 w-4" /> Crea un evento</Link>} />
      ) : (
        <ul className="space-y-3">{elenco.map((e) => <li key={e.id}><SchedaElemento e={e} /></li>)}</ul>
      )}
    </>
  );
}
