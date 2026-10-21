"use client";
// Scheda di un contenuto o di un evento nel feed — © Roberto Di Flumeri
import clsx from "clsx";
import { CalendarDays, MapPin, Users } from "lucide-react";
import Link from "next/link";
import type { ElementoFeed } from "@/lib/api";
import { dataOraIt, fa } from "@/lib/api";
import { Categoria, EtichettaStato, EtichettaTipo, ICONE_TIPO, NomeUtente } from "./ui";

export default function SchedaElemento({ e }: { e: ElementoFeed }) {
  if (e.genere === "evento") {
    const pieno = e.posti_liberi === 0;
    return (
      <Link href={`/app/eventi/${e.id}`} className="scheda block transition hover:border-verde">
        <div className="flex gap-4">
          <div className="flex w-16 shrink-0 flex-col items-center justify-center rounded-xl bg-verde/10 py-2 text-verde">
            <span className="text-xs uppercase">{new Date(e.inizio + "Z").toLocaleDateString("it-IT", { month: "short" })}</span>
            <span className="text-2xl font-bold leading-none">{new Date(e.inizio + "Z").getDate()}</span>
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2"><EtichettaTipo tipo="evento" /><Categoria tipo="evento" nome={e.categoria} />
              {e.mia_partecipazione === "confermato" && <span className="pillola bg-ok/15 text-ok">Partecipi</span>}
              {e.mia_partecipazione === "lista_attesa" && <span className="pillola bg-attenzione/15 text-attenzione">In lista d&apos;attesa</span>}
            </div>
            <h3 className="mt-1 font-semibold">{e.titolo}</h3>
            <p className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-sm text-tenue">
              <span className="flex items-center gap-1"><CalendarDays className="h-3.5 w-3.5" /> {dataOraIt(e.inizio)}</span>
              <span className="flex items-center gap-1"><MapPin className="h-3.5 w-3.5" /> {e.luogo}{e.posizione.distanza ? ` · ${e.posizione.distanza}` : ""}</span>
              <span className={clsx("flex items-center gap-1", pieno && "text-attenzione")}><Users className="h-3.5 w-3.5" />
                {e.confermati}{e.capienza ? `/${e.capienza}` : ""} partecipanti{pieno ? " · al completo" : ""}</span>
            </p>
            <p className="mt-2 text-xs text-tenue">Organizza <NomeUtente u={e.organizzatore} /></p>
          </div>
        </div>
      </Link>
    );
  }
  const Icona = ICONE_TIPO[e.tipo];
  const foto = e.foto[0];
  return (
    <Link href={`/app/annunci/${e.id}`} className="scheda block transition hover:border-verde">
      <div className="flex gap-4">
        <div className="h-20 w-20 shrink-0 overflow-hidden rounded-xl bg-fondo">
          {foto
            // eslint-disable-next-line @next/next/no-img-element
            ? <img src={foto.url} alt="" className="h-full w-full object-cover" loading="lazy" />
            : <div className="flex h-full w-full items-center justify-center text-prato">{Icona && <Icona className="h-8 w-8" />}</div>}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <EtichettaTipo tipo={e.tipo} /><Categoria tipo={e.tipo} nome={e.categoria} />
            {e.stato !== "pubblicato" && <EtichettaStato stato={e.stato} />}
          </div>
          <h3 className="mt-1 font-semibold">{e.titolo}</h3>
          <p className="mt-0.5 line-clamp-2 text-sm text-tenue">{e.descrizione}</p>
          <p className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-tenue">
            <NomeUtente u={e.autore} />
            {e.posizione.distanza && <span className="flex items-center gap-1"><MapPin className="h-3 w-3" /> {e.posizione.distanza}</span>}
            <span>{fa(e.created_at)}</span>
            {e.prezzo && <span className="font-semibold text-testo">{e.prezzo}</span>}
            {e.tipo === "aiuto" && e.disponibilita && <span>Disponibilità: {e.disponibilita}</span>}
          </p>
        </div>
      </div>
    </Link>
  );
}
