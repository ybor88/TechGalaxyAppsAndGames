"use client";
// Condivisione avatar con link temporaneo (F08) — © Roberto Di Flumeri
import clsx from "clsx";
import { Copy, Eye, Link2, ShieldOff } from "lucide-react";
import { useEffect, useState } from "react";
import { Avviso, Caricamento, Titolo, useAzione } from "@/components/ui";
import { api, CATEGORIE, dataOraIt } from "@/lib/api";

interface Condivisione {
  id: string; nome: string; categorie: string[]; max_domande: number; domande_usate: number; scadenza: string;
  revocato_at: string | null; bloccato_moderazione: boolean; created_at: string; attiva: boolean;
}

export default function PaginaCondivisione() {
  const [elenco, setElenco] = useState<Condivisione[] | null>(null);
  const [form, setForm] = useState({ nome: "", categorie: ["gusti"] as string[], max_domande: 10, durata_ore: 72 });
  const [link, setLink] = useState<string | null>(null);
  const [copiato, setCopiato] = useState(false);
  const [accessi, setAccessi] = useState<{ id: string; voci: { evento: string; timestamp: string }[] } | null>(null);
  const { inCorso, errore, esegui } = useAzione();

  const carica = () => api<Condivisione[]>("/v1/shares").then(setElenco).catch((e) => { setElenco([]); throw e; });
  useEffect(() => { esegui(carica); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const crea = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => {
      const r = await api<{ percorso: string }>("/v1/shares", { metodo: "POST", corpo: form });
      setLink(`${window.location.origin}${r.percorso}`);
      setCopiato(false);
      await carica();
    });
  };

  const copia = async () => {
    if (!link) return;
    await navigator.clipboard.writeText(link).catch(() => undefined);
    setCopiato(true);
  };

  const alterna = (c: string) =>
    setForm((f) => ({ ...f, categorie: f.categorie.includes(c) ? f.categorie.filter((x) => x !== c) : [...f.categorie, c] }));

  const stato = (c: Condivisione) =>
    c.bloccato_moderazione ? "Sospeso dalla moderazione" : c.revocato_at ? "Revocato" : !c.attiva && c.domande_usate >= c.max_domande
      ? "Domande esaurite" : !c.attiva ? "Scaduto" : "Attivo";

  return (
    <>
      <Titolo titolo="Condivisione dell'avatar" sottotitolo="Crea un link temporaneo: chi lo riceve può fare un numero limitato di domande, solo sulle dichiarazioni che hai segnato come condivisibili." />
      <form onSubmit={crea} className="scheda mb-6 space-y-4">
        <div className="grid gap-3 sm:grid-cols-3">
          <label className="text-sm sm:col-span-1">Nome del link
            <input className="campo mt-1" required maxLength={60} placeholder="Es. Per gli amici" value={form.nome} onChange={(e) => setForm({ ...form, nome: e.target.value })} />
          </label>
          <label className="text-sm">Domande massime
            <input type="number" min={1} max={50} className="campo mt-1" value={form.max_domande} onChange={(e) => setForm({ ...form, max_domande: Number(e.target.value) })} />
          </label>
          <label className="text-sm">Scadenza
            <select className="campo mt-1" value={form.durata_ore} onChange={(e) => setForm({ ...form, durata_ore: Number(e.target.value) })}>
              <option value={1}>1 ora</option><option value={24}>1 giorno</option><option value={72}>3 giorni</option>
              <option value={168}>7 giorni</option><option value={720}>30 giorni</option>
            </select>
          </label>
        </div>
        <fieldset>
          <legend className="etichetta">Categorie visibili</legend>
          <div className="flex flex-wrap gap-2">
            {Object.entries(CATEGORIE).map(([k, v]) => (
              <button key={k} type="button" aria-pressed={form.categorie.includes(k)} onClick={() => alterna(k)}
                className={clsx("pillola border px-3 py-1", form.categorie.includes(k) ? "gradiente text-white" : "border-bordo")}>{v}</button>
            ))}
          </div>
        </fieldset>
        {errore && <Avviso tipo="errore">{errore}</Avviso>}
        <button className="btn-primario" disabled={inCorso || !form.categorie.length}><Link2 className="h-4 w-4" /> Crea link</button>
      </form>

      {link && (
        <div className="mb-6">
          <Avviso tipo="ok">
            <p className="font-semibold">Link creato. Copialo ora: per sicurezza non verrà più mostrato.</p>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              <code className="break-all rounded bg-pannello px-2 py-1 text-xs">{link}</code>
              <button className="btn-secondario py-1 text-sm" onClick={copia}><Copy className="h-4 w-4" /> {copiato ? "Copiato" : "Copia"}</button>
            </div>
          </Avviso>
        </div>
      )}

      {!elenco ? <Caricamento /> : elenco.length === 0 ? <p className="text-tenue">Nessun link creato.</p> : (
        <ul className="space-y-3">
          {elenco.map((c) => (
            <li key={c.id} className="scheda">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <div className="flex items-center gap-2 font-semibold">{c.nome}
                    <span className={clsx("pillola", c.attiva ? "bg-ok/15 text-ok" : "bg-fondo text-tenue")}>{stato(c)}</span>
                  </div>
                  <div className="mt-1 text-sm text-tenue">
                    {c.categorie.map((x) => CATEGORIE[x] || x).join(", ")} · {c.domande_usate}/{c.max_domande} domande · scade {dataOraIt(c.scadenza)}
                  </div>
                </div>
                <div className="flex gap-2">
                  <button className="btn-secondario text-sm" onClick={() => esegui(async () =>
                    setAccessi({ id: c.id, voci: await api(`/v1/shares/${c.id}/accessi`) }))}><Eye className="h-4 w-4" /> Accessi</button>
                  {c.attiva && (
                    <button className="btn-secondario text-sm text-errore" onClick={() => confirm("Revocare il link? Smetterà subito di funzionare.") &&
                      esegui(async () => { await api(`/v1/shares/${c.id}`, { metodo: "DELETE" }); await carica(); })}>
                      <ShieldOff className="h-4 w-4" /> Revoca
                    </button>
                  )}
                </div>
              </div>
              {accessi?.id === c.id && (
                <ul className="mt-3 max-h-40 space-y-1 overflow-y-auto text-sm">
                  {accessi.voci.length === 0 ? <li className="text-tenue">Nessun accesso.</li> : accessi.voci.map((a, i) => (
                    <li key={i}>{dataOraIt(a.timestamp)} · {a.evento === "domanda" ? "domanda ricevuta" : a.evento === "rifiutata" ? "domanda rifiutata" : "apertura"}</li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
