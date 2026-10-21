"use client";
// Feedback contestuale dopo un'interazione conclusa (F09) — © Roberto Di Flumeri
import clsx from "clsx";
import { Meh, ThumbsDown, ThumbsUp } from "lucide-react";
import { useEffect, useState } from "react";
import { api, type UtentePubblico } from "@/lib/api";
import { Avviso, useAzione } from "./ui";

interface DaLasciare { contesto_tipo: "post" | "evento"; contesto_id: string; contesto: string; con: UtentePubblico }

const ESITI = [
  { id: "positivo", nome: "Positivo", icona: ThumbsUp }, { id: "neutro", nome: "Neutro", icona: Meh },
  { id: "negativo", nome: "Negativo", icona: ThumbsDown },
];

function Modulo({ v, onFatto }: { v: DaLasciare; onFatto: () => void }) {
  const [esito, setEsito] = useState("positivo");
  const [commento, setCommento] = useState("");
  const { inCorso, errore, esegui } = useAzione();
  const invia = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => {
      await api("/v1/reputation", { metodo: "POST", corpo: { destinatario_id: v.con.id, contesto_tipo: v.contesto_tipo,
        contesto_id: v.contesto_id, esito, commento: commento || null } });
      onFatto();
    });
  };
  return (
    <form onSubmit={invia} className="space-y-3 rounded-xl border border-bordo p-3">
      <p className="text-sm">Com&apos;è andata con <strong>{v.con.alias}</strong>?</p>
      <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Esito">
        {ESITI.map(({ id, nome, icona: Icona }) => (
          <button key={id} type="button" role="radio" aria-checked={esito === id} onClick={() => setEsito(id)}
            className={clsx("filtro", esito === id ? "gradiente border-transparent text-white" : "border-bordo")}>
            <Icona className="h-4 w-4" /> {nome}
          </button>
        ))}
      </div>
      <input className="campo" maxLength={300} placeholder="Un commento per i vicini (facoltativo)" value={commento}
        onChange={(e) => setCommento(e.target.value)} aria-label="Commento" />
      {errore && <Avviso tipo="errore">{errore}</Avviso>}
      <button className="btn-primario" disabled={inCorso}>Invia feedback</button>
    </form>
  );
}

/** Mostra i feedback ancora da lasciare, eventualmente solo per un contesto. */
export default function FeedbackDaLasciare({ contestoId, titolo = true }: { contestoId?: string; titolo?: boolean }) {
  const [voci, setVoci] = useState<DaLasciare[] | null>(null);
  const [inviati, setInviati] = useState(0);
  const carica = () => api<DaLasciare[]>("/v1/reputation/da-lasciare").then((v) =>
    setVoci(contestoId ? v.filter((x) => x.contesto_id === contestoId) : v)).catch(() => setVoci([]));
  useEffect(() => { carica(); }, [contestoId]); // eslint-disable-line react-hooks/exhaustive-deps
  if (!voci || (voci.length === 0 && !inviati)) return null;
  return (
    <section className="scheda space-y-3">
      {titolo && <h2 className="font-semibold">Lascia un feedback</h2>}
      <p className="text-sm text-tenue">Il feedback è legato a questa interazione concreta: niente punteggi globali.</p>
      {voci.length === 0 ? <Avviso tipo="ok">Grazie per il tuo feedback!</Avviso> : voci.map((v) => (
        <div key={`${v.contesto_id}-${v.con.id}`}>
          {!contestoId && <p className="mb-1 text-xs font-semibold uppercase text-tenue">{v.contesto}</p>}
          <Modulo v={v} onFatto={() => { setInviati((n) => n + 1); carica(); }} />
        </div>
      ))}
    </section>
  );
}
