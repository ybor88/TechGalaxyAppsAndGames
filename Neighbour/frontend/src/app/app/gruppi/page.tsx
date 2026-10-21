"use client";
// Gruppi di interesse locali (F08) — © Roberto Di Flumeri
import clsx from "clsx";
import { Plus, Users } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Avviso, Caricamento, Modale, Titolo, useAzione, Vuoto } from "@/components/ui";
import { api, type Posizione, type UtentePubblico } from "@/lib/api";
import { useCatalogo } from "@/lib/catalogo";

interface Gruppo {
  id: string; nome: string; descrizione: string; categoria: string; regole: string | null; membri: number;
  mio_ruolo: "amministratore" | "membro" | null; posizione: Posizione; creatore: UtentePubblico | null; created_at: string;
}

export default function Gruppi() {
  const catalogo = useCatalogo();
  const [categoria, setCategoria] = useState("");
  const [gruppi, setGruppi] = useState<Gruppo[] | null>(null);
  const [nuovo, setNuovo] = useState(false);
  const [f, setF] = useState({ nome: "", descrizione: "", categoria: "", regole: "" });
  const azione = useAzione();

  const carica = () => api<Gruppo[]>(`/v1/groups${categoria ? `?categoria=${categoria}` : ""}`).then(setGruppi).catch((e) => azione.setErrore(e.message));
  useEffect(() => { carica(); }, [categoria]); // eslint-disable-line react-hooks/exhaustive-deps

  const crea = (e: React.FormEvent) => {
    e.preventDefault();
    azione.esegui(async () => {
      const g = await api<Gruppo>("/v1/groups", { metodo: "POST", corpo: { ...f, regole: f.regole || null } });
      location.assign(`/app/gruppi/${g.id}`);
    });
  };
  const entra = (g: Gruppo) => azione.esegui(async () => { await api(`/v1/groups/${g.id}/join`, { metodo: "POST" }); await carica(); });

  const categorie = catalogo?.categorie.gruppo || {};
  return (
    <>
      <Titolo titolo="Gruppi del quartiere" sottotitolo="Spazi tematici per chi condivide una passione o un'esigenza."
        azioni={<button className="btn-primario" onClick={() => setNuovo(true)}><Plus className="h-4 w-4" /> Crea un gruppo</button>} />
      <div className="-mx-4 mb-4 flex gap-2 overflow-x-auto px-4 pb-1">
        {[["", "Tutti"], ...Object.entries(categorie)].map(([k, v]) => (
          <button key={k} onClick={() => setCategoria(k)} aria-pressed={categoria === k}
            className={clsx("filtro shrink-0", categoria === k ? "gradiente border-transparent text-white" : "border-bordo bg-pannello")}>{v}</button>
        ))}
      </div>
      {azione.errore && <Avviso tipo="errore">{azione.errore}</Avviso>}
      {!gruppi ? <Caricamento /> : gruppi.length === 0 ? (
        <Vuoto icona={Users} titolo="Nessun gruppo qui vicino" testo="Crea il primo: cani, genitori, corsa, orti sul balcone…" />
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {gruppi.map((g) => (
            <li key={g.id} className="scheda flex flex-col">
              <Link href={`/app/gruppi/${g.id}`} className="flex-1">
                <span className="pillola bg-prato/15 text-verde">{categorie[g.categoria] || g.categoria}</span>
                <h2 className="mt-2 font-semibold">{g.nome}</h2>
                <p className="mt-1 line-clamp-2 text-sm text-tenue">{g.descrizione}</p>
                <p className="mt-2 text-xs text-tenue">{g.membri} {g.membri === 1 ? "membro" : "membri"}{g.posizione.distanza ? ` · ${g.posizione.distanza}` : ""}</p>
              </Link>
              <div className="mt-3">
                {g.mio_ruolo ? <span className="pillola bg-ok/15 text-ok">{g.mio_ruolo === "amministratore" ? "Amministri" : "Ne fai parte"}</span>
                  : <button className="btn-secondario text-sm" onClick={() => entra(g)} disabled={azione.inCorso}>Unisciti</button>}
              </div>
            </li>
          ))}
        </ul>
      )}
      {nuovo && (
        <Modale titolo="Nuovo gruppo" onChiudi={() => setNuovo(false)}>
          <form onSubmit={crea} className="space-y-3">
            <input className="campo" required minLength={3} maxLength={80} placeholder="Nome del gruppo" aria-label="Nome" value={f.nome} onChange={(e) => setF({ ...f, nome: e.target.value })} />
            <select className="campo" required value={f.categoria} onChange={(e) => setF({ ...f, categoria: e.target.value })} aria-label="Categoria">
              <option value="" disabled>Categoria…</option>
              {Object.entries(categorie).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
            <textarea className="campo" rows={3} required minLength={10} maxLength={1000} placeholder="Di cosa si parla nel gruppo?" aria-label="Descrizione"
              value={f.descrizione} onChange={(e) => setF({ ...f, descrizione: e.target.value })} />
            <textarea className="campo" rows={2} maxLength={1000} placeholder="Regole del gruppo (facoltative)" aria-label="Regole"
              value={f.regole} onChange={(e) => setF({ ...f, regole: e.target.value })} />
            {azione.errore && <Avviso tipo="errore">{azione.errore}</Avviso>}
            <button className="btn-primario w-full" disabled={azione.inCorso}>Crea gruppo</button>
          </form>
        </Modale>
      )}
    </>
  );
}
