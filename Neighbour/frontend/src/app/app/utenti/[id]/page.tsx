"use client";
// Profilo pubblico di un vicino: solo alias, presentazione e feedback contestuali — © Roberto Di Flumeri
import { Ban, Flag } from "lucide-react";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import RiepilogoReputazione, { type Reputazione } from "@/components/Reputazione";
import { Avatar, Avviso, Caricamento, NomeUtente, Segnala, useAzione } from "@/components/ui";
import { api, dataIt, type UtentePubblico } from "@/lib/api";
import { useSessione } from "@/lib/sessione";

type Profilo = UtentePubblico & { bio: string | null; iscritto_dal: string; reputazione: Reputazione };

export default function PaginaUtente() {
  const { id } = useParams<{ id: string }>();
  const { utente } = useSessione();
  const router = useRouter();
  const [p, setP] = useState<Profilo | null>(null);
  const [errore, setErrore] = useState<string | null>(null);
  const [segnala, setSegnala] = useState(false);
  const azione = useAzione();

  useEffect(() => {
    if (utente?.id === id) { router.replace("/app/profilo"); return; }
    api<Profilo>(`/v1/users/${id}`).then(setP).catch((e) => setErrore(e.message));
  }, [id, utente?.id, router]);

  if (errore) return <Avviso tipo="errore">{errore}</Avviso>;
  if (!p) return <Caricamento />;
  const blocca = () => confirm(`Bloccare ${p.alias}? Non vedrete più i rispettivi contenuti e non potrete scrivervi.`) &&
    azione.esegui(async () => { await api(`/v1/blocks/${p.id}`, { metodo: "POST" }); router.replace("/app"); });

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <section className="scheda space-y-3">
        <div className="flex items-center gap-3">
          <Avatar alias={p.alias} grande />
          <div><NomeUtente u={p} />{p.attivita && <p className="text-sm text-tenue">{p.attivita}</p>}
            <p className="text-xs text-tenue">Nel quartiere dal {dataIt(p.iscritto_dal)}</p></div>
        </div>
        {p.bio && <p className="text-sm">{p.bio}</p>}
        <div className="flex gap-2">
          <button className="btn-secondario text-sm" onClick={() => setSegnala(true)}><Flag className="h-4 w-4" /> Segnala profilo</button>
          <button className="btn-secondario text-sm" onClick={blocca}><Ban className="h-4 w-4" /> Blocca</button>
        </div>
        {azione.errore && <Avviso tipo="errore">{azione.errore}</Avviso>}
      </section>
      <RiepilogoReputazione r={p.reputazione} />
      {segnala && p.id && <Segnala oggettoTipo="utente" oggettoId={p.id} alias={p.alias} onChiudi={() => setSegnala(false)}
        onFatto={(b) => b && setTimeout(() => router.replace("/app"), 1500)} />}
    </div>
  );
}
