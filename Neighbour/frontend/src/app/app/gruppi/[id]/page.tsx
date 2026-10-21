"use client";
// Gruppo: bacheca riservata ai membri, eventi e membri — © Roberto Di Flumeri
import { CalendarDays, Flag, LogOut, Plus, Trash2, Users } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import SchedaElemento from "@/components/SchedaElemento";
import { Avatar, Avviso, Caricamento, Modale, NomeUtente, Segnala, useAzione, Vuoto } from "@/components/ui";
import { api, fa, type Contenuto, type Evento, type UtentePubblico } from "@/lib/api";
import { useCatalogo } from "@/lib/catalogo";

interface Dettaglio {
  id: string; nome: string; descrizione: string; categoria: string; regole: string | null; membri: number;
  mio_ruolo: "amministratore" | "membro" | null; creatore: UtentePubblico | null; bacheca?: Contenuto[]; eventi?: Evento[];
}

export default function PaginaGruppo() {
  const { id } = useParams<{ id: string }>();
  const catalogo = useCatalogo();
  const [g, setG] = useState<Dettaglio | null>(null);
  const [membri, setMembri] = useState<(UtentePubblico & { ruolo: string })[] | null>(null);
  const [errore, setErrore] = useState<string | null>(null);
  const [segnala, setSegnala] = useState<{ tipo: string; id: string } | null>(null);
  const azione = useAzione();

  const carica = useCallback(() => api<Dettaglio>(`/v1/groups/${id}`).then(setG).catch((e) => setErrore(e.message)), [id]);
  useEffect(() => { carica(); }, [carica]);

  if (errore) return <Avviso tipo="errore">{errore}</Avviso>;
  if (!g) return <Caricamento />;

  const entra = () => azione.esegui(async () => { await api(`/v1/groups/${g.id}/join`, { metodo: "POST" }); await carica(); });
  const esci = () => confirm("Uscire dal gruppo?") && azione.esegui(async () => { await api(`/v1/groups/${g.id}/join`, { metodo: "DELETE" }); await carica(); });
  const vediMembri = () => azione.esegui(async () => setMembri(await api(`/v1/groups/${g.id}/members`)));
  const rimuovi = (p: Contenuto) => confirm("Togliere il messaggio dalla bacheca?") &&
    azione.esegui(async () => { await api(`/v1/groups/${g.id}/posts/${p.id}/rimuovi`, { metodo: "POST" }); await carica(); });

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <Link href="/app/gruppi" className="text-sm text-tenue hover:underline">← Gruppi</Link>
      <header className="scheda space-y-3">
        <span className="pillola bg-prato/15 text-verde">{catalogo?.categorie.gruppo[g.categoria] || g.categoria}</span>
        <h1 className="text-2xl font-bold">{g.nome}</h1>
        <p>{g.descrizione}</p>
        {g.regole && <div className="rounded-xl bg-fondo p-3 text-sm"><strong>Regole:</strong> {g.regole}</div>}
        <div className="flex flex-wrap items-center gap-2">
          <button className="btn-secondario text-sm" onClick={vediMembri} disabled={!g.mio_ruolo}><Users className="h-4 w-4" /> {g.membri} membri</button>
          {g.mio_ruolo ? (
            <>
              <Link href={`/app/pubblica?gruppo=${g.id}`} className="btn-primario text-sm"><Plus className="h-4 w-4" /> Scrivi nel gruppo</Link>
              <Link href={`/app/eventi/nuovo?gruppo=${g.id}`} className="btn-secondario text-sm"><CalendarDays className="h-4 w-4" /> Evento del gruppo</Link>
              <button className="btn-secondario text-sm" onClick={esci}><LogOut className="h-4 w-4" /> Esci</button>
            </>
          ) : <button className="btn-primario text-sm" onClick={entra} disabled={azione.inCorso}>Unisciti al gruppo</button>}
          <button className="btn-secondario ml-auto px-2 text-sm" onClick={() => setSegnala({ tipo: "gruppo", id: g.id })} aria-label="Segnala il gruppo"><Flag className="h-4 w-4" /></button>
        </div>
      </header>
      {azione.errore && <Avviso tipo="errore">{azione.errore}</Avviso>}

      {!g.mio_ruolo ? (
        <Avviso tipo="info">La bacheca e gli eventi del gruppo sono visibili solo ai membri. Unisciti per partecipare.</Avviso>
      ) : (
        <>
          {g.eventi && g.eventi.length > 0 && (
            <section className="space-y-2">
              <h2 className="font-semibold">Prossimi eventi del gruppo</h2>
              <ul className="space-y-2">{g.eventi.map((e) => <li key={e.id}><SchedaElemento e={e} /></li>)}</ul>
            </section>
          )}
          <section className="space-y-2">
            <h2 className="font-semibold">Bacheca</h2>
            {!g.bacheca?.length ? <Vuoto titolo="Ancora nessun messaggio" testo="Rompi il ghiaccio: presentati o proponi un'iniziativa." /> : (
              <ul className="space-y-3">
                {g.bacheca.map((p) => (
                  <li key={p.id} className="scheda">
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex items-center gap-2"><Avatar alias={p.autore.alias} /><div><NomeUtente u={p.autore} />
                        <span className="block text-xs text-tenue">{fa(p.created_at)}</span></div></div>
                      <div className="flex gap-1">
                        {!p.mio && <button className="btn-secondario px-2" aria-label="Segnala il messaggio" onClick={() => setSegnala({ tipo: "post", id: p.id })}><Flag className="h-4 w-4" /></button>}
                        {(g.mio_ruolo === "amministratore" || p.mio) && <button className="btn-secondario px-2 text-errore" aria-label="Rimuovi" onClick={() => rimuovi(p)}><Trash2 className="h-4 w-4" /></button>}
                      </div>
                    </div>
                    <h3 className="mt-2 font-semibold">{p.titolo}</h3>
                    <p className="mt-1 whitespace-pre-wrap text-sm">{p.descrizione}</p>
                    {p.foto.length > 0 && (
                      <div className="mt-2 flex gap-2">{p.foto.map((f) => (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img key={f.id} src={f.url} alt="" className="h-24 w-24 rounded-xl object-cover" />
                      ))}</div>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}
      {membri && (
        <Modale titolo={`Membri di ${g.nome}`} onChiudi={() => setMembri(null)}>
          <ul className="divide-y divide-bordo">{membri.map((m) => (
            <li key={m.id} className="flex items-center justify-between py-2">
              <Link href={`/app/utenti/${m.id}`} className="flex items-center gap-2"><Avatar alias={m.alias} /><NomeUtente u={m} /></Link>
              {m.ruolo === "amministratore" && <span className="pillola bg-verde/10 text-verde">Amministratore</span>}
            </li>
          ))}</ul>
        </Modale>
      )}
      {segnala && <Segnala oggettoTipo={segnala.tipo} oggettoId={segnala.id} onChiudi={() => setSegnala(null)} />}
    </div>
  );
}
