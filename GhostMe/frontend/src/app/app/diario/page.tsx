"use client";
// Diario di evoluzione (F06) — © Roberto Di Flumeri
import { Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import { Avviso, Caricamento, Titolo, useAzione } from "@/components/ui";
import { api, dataOraIt } from "@/lib/api";

interface Voce { id: string; tipo: string; testo: string; energia: number | null; etichette: string[] | null; created_at: string }

const ENERGIA = ["", "Molto bassa", "Bassa", "Normale", "Buona", "Alta"];

export default function Diario() {
  const [voci, setVoci] = useState<Voce[] | null>(null);
  const [tipo, setTipo] = useState<"nota" | "check_in">("check_in");
  const [testo, setTesto] = useState("");
  const [energia, setEnergia] = useState<number | null>(null);
  const [etichette, setEtichette] = useState("");
  const { inCorso, errore, esegui } = useAzione();

  const carica = () => api<Voce[]>("/v1/journal").then(setVoci);
  useEffect(() => { carica(); }, []);

  const salva = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => {
      const tag = etichette.split(",").map((s) => s.trim()).filter(Boolean).slice(0, 10);
      await api("/v1/journal", { metodo: "POST", corpo: { tipo, testo, energia, etichette: tag.length ? tag : null } });
      setTesto(""); setEnergia(null); setEtichette("");
      await carica();
    });
  };

  return (
    <>
      <Titolo titolo="Diario di evoluzione" sottotitolo="Annotazioni e check-in personali. Restano tuoi e cifrati: l'alter ego non li legge." />
      <form onSubmit={salva} className="scheda mb-6 space-y-3">
        <div role="radiogroup" aria-label="Tipo di voce" className="flex gap-2">
          {(["check_in", "nota"] as const).map((t) => (
            <button key={t} type="button" role="radio" aria-checked={tipo === t} onClick={() => setTipo(t)}
              className={`pillola border px-3 py-1 ${tipo === t ? "gradiente text-white" : "border-bordo"}`}>
              {t === "check_in" ? "Check-in" : "Nota libera"}
            </button>
          ))}
        </div>
        <label htmlFor="testo" className="etichetta">{tipo === "check_in" ? "Com'è andata? Cosa è cambiato in te?" : "Scrivi liberamente"}</label>
        <textarea id="testo" required rows={4} maxLength={4000} className="campo" value={testo} onChange={(e) => setTesto(e.target.value)} />
        <div className="flex flex-wrap items-center gap-4">
          {tipo === "check_in" && (
            <label className="text-sm">Energia (facoltativa)
              <select className="campo ml-2 w-auto" value={energia ?? ""} onChange={(e) => setEnergia(e.target.value ? Number(e.target.value) : null)}>
                <option value="">—</option>{[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{ENERGIA[n]}</option>)}
              </select>
            </label>
          )}
          <label className="flex-1 text-sm">Etichette
            <input className="campo mt-1" placeholder="lavoro, sport" value={etichette} onChange={(e) => setEtichette(e.target.value)} />
          </label>
          <button className="btn-primario" disabled={inCorso}>Salva</button>
        </div>
        {errore && <Avviso tipo="errore">{errore}</Avviso>}
      </form>

      {!voci ? <Caricamento /> : voci.length === 0 ? <p className="text-tenue">Il diario è vuoto.</p> : (
        <ul className="space-y-3">
          {voci.map((v) => (
            <li key={v.id} className="scheda">
              <div className="flex items-start justify-between gap-2">
                <div className="text-xs text-tenue">
                  {dataOraIt(v.created_at)} · {v.tipo === "check_in" ? "Check-in" : "Nota"}
                  {v.energia ? ` · energia ${ENERGIA[v.energia].toLowerCase()}` : ""}
                </div>
                <button className="rounded p-1 text-tenue hover:text-errore" aria-label="Elimina voce"
                  onClick={() => confirm("Eliminare questa voce?") && esegui(async () => { await api(`/v1/journal/${v.id}`, { metodo: "DELETE" }); await carica(); })}>
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
              <p className="mt-1 whitespace-pre-wrap">{v.testo}</p>
              {v.etichette && <div className="mt-2 flex flex-wrap gap-1">{v.etichette.map((t) => <span key={t} className="pillola bg-fondo">#{t}</span>)}</div>}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
