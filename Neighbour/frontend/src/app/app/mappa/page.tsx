"use client";
// Mappa di quartiere (F01) — © Roberto Di Flumeri
import clsx from "clsx";
import dynamic from "next/dynamic";
import Link from "next/link";
import { useEffect, useState } from "react";
import Legenda from "@/components/Legenda";
import { Avviso, Caricamento, Raggio, Titolo } from "@/components/ui";
import { api } from "@/lib/api";
import type { PuntoMappa } from "@/lib/mappa";
import { NOMI } from "@/lib/mappa";

const Mappa = dynamic(() => import("@/components/Mappa"), { ssr: false, loading: () => <Caricamento testo="Carico la mappa…" /> });

const TIPI = [["tutti", "Tutto"], ["mercatino", "Mercatino"], ["aiuto", "Aiuti"], ["evento", "Eventi"],
  ["segnalazione", "Segnalazioni"], ["servizio", "Servizi"]];

interface Risposta { centro: { lat: number; lon: number; nome: string }; raggio_m: number; punti: PuntoMappa[]; nota: string }

export default function PaginaMappa() {
  const [tipo, setTipo] = useState("tutti");
  const [raggio, setRaggio] = useState<number | null>(null);
  const [dati, setDati] = useState<Risposta | null>(null);
  const [errore, setErrore] = useState<string | null>(null);

  useEffect(() => {
    const p = new URLSearchParams({ tipo });
    if (raggio) p.set("raggio_m", String(raggio));
    api<Risposta>(`/v1/map?${p}`).then(setDati).catch((e) => setErrore(e.message));
  }, [tipo, raggio]);

  return (
    <>
      <Titolo titolo="Mappa del quartiere" sottotitolo="Le zone colorate sono approssimative: nessun indirizzo viene mostrato." />
      <div className="-mx-4 mb-3 flex gap-2 overflow-x-auto px-4 pb-1">
        {TIPI.map(([id, nome]) => (
          <button key={id} onClick={() => setTipo(id)} aria-pressed={tipo === id}
            className={clsx("filtro shrink-0", tipo === id ? "gradiente border-transparent text-white" : "border-bordo bg-pannello")}>{nome}</button>
        ))}
      </div>
      {errore && <Avviso tipo="errore">{errore}</Avviso>}
      {!dati ? <Caricamento /> : (
        <div className="grid gap-4 lg:grid-cols-[1fr_300px]">
          <div className="space-y-3">
            <Mappa centro={dati.centro} raggio={dati.raggio_m} punti={dati.punti} altezza={520} />
            <Legenda />
          </div>
          <aside className="space-y-4">
            <div className="scheda"><Raggio valore={raggio || dati.raggio_m} onChange={setRaggio} etichetta="Distanza" /></div>
            <div className="scheda">
              <h2 className="mb-2 font-semibold">{dati.punti.length} {dati.punti.length === 1 ? "elemento" : "elementi"} in zona</h2>
              <ul className="max-h-80 space-y-2 overflow-y-auto text-sm">
                {dati.punti.map((p) => (
                  <li key={`${p.genere}-${p.id}`}>
                    <Link href={p.link} className="block rounded-lg p-2 hover:bg-fondo">
                      <span className="text-xs font-semibold uppercase text-verde">{NOMI[p.tipo] || p.tipo}</span>
                      <span className="block font-medium">{p.titolo}</span>
                      {p.distanza && <span className="text-xs text-tenue">{p.distanza}</span>}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          </aside>
        </div>
      )}
    </>
  );
}
