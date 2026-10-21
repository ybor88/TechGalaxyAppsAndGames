"use client";
// Reputazione contestuale (F09): conteggi spiegati e singoli feedback, niente punteggio globale — © Roberto Di Flumeri
import { CalendarDays, Gift, Meh, ThumbsDown, ThumbsUp } from "lucide-react";
import { dataIt, type UtentePubblico } from "@/lib/api";

export interface Reputazione {
  spiegazione: string; totale: number; conteggi: { positivo: number; neutro: number; negativo: number };
  feedback: { esito: string; commento: string | null; contesto_tipo: string; contesto: string; da: UtentePubblico | null; created_at: string }[];
}

const ICONE = { positivo: ThumbsUp, neutro: Meh, negativo: ThumbsDown } as const;
const COLORI = { positivo: "text-ok", neutro: "text-tenue", negativo: "text-errore" } as const;
const NOMI = { positivo: ["positivo", "positivi"], neutro: ["neutro", "neutri"], negativo: ["negativo", "negativi"] } as const;

export default function RiepilogoReputazione({ r }: { r: Reputazione }) {
  return (
    <section className="scheda space-y-3">
      <h2 className="font-semibold">Feedback dei vicini</h2>
      <p className="text-sm text-tenue">{r.spiegazione}</p>
      {r.totale === 0 ? <p className="text-sm">Ancora nessun feedback.</p> : (
        <>
          <ul className="flex flex-wrap gap-3 text-sm">
            {(["positivo", "neutro", "negativo"] as const).map((k) => {
              const Icona = ICONE[k];
              return <li key={k} className={`flex items-center gap-1 ${COLORI[k]}`}><Icona className="h-4 w-4" /> {r.conteggi[k]} {NOMI[k][r.conteggi[k] === 1 ? 0 : 1]}</li>;
            })}
          </ul>
          <ul className="space-y-2">
            {r.feedback.map((f, i) => {
              const Icona = ICONE[f.esito as keyof typeof ICONE] || Meh;
              return (
                <li key={i} className="rounded-xl bg-fondo p-3 text-sm">
                  <div className="flex items-center gap-2">
                    <Icona className={`h-4 w-4 ${COLORI[f.esito as keyof typeof COLORI] || ""}`} aria-label={f.esito} />
                    <span className="flex items-center gap-1 text-xs text-tenue">
                      {f.contesto_tipo === "evento" ? <CalendarDays className="h-3 w-3" /> : <Gift className="h-3 w-3" />} {f.contesto}
                    </span>
                  </div>
                  {f.commento && <p className="mt-1">«{f.commento}»</p>}
                  <p className="mt-1 text-xs text-tenue">{f.da?.alias || "Utente eliminato"} · {dataIt(f.created_at)}</p>
                </li>
              );
            })}
          </ul>
        </>
      )}
    </section>
  );
}
