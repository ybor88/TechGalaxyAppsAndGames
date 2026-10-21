"use client";
// Creazione di un evento (F03) — © Roberto Di Flumeri
import dynamic from "next/dynamic";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { Avviso, Caricamento, Interruttore, Raggio, Titolo, useAzione } from "@/components/ui";
import { api, localeAIso, type Evento } from "@/lib/api";
import { useCatalogo } from "@/lib/catalogo";
import { useSessione } from "@/lib/sessione";

const Mappa = dynamic(() => import("@/components/Mappa"), { ssr: false, loading: () => <Caricamento testo="Carico la mappa…" /> });

function Modulo() {
  const { utente } = useSessione();
  const catalogo = useCatalogo();
  const router = useRouter();
  const gruppo = useSearchParams().get("gruppo");
  const [f, setF] = useState({ titolo: "", descrizione: "", categoria: "", inizio: "", fine: "", luogo: "", capienza: "" });
  const [approvazione, setApprovazione] = useState(false);
  const [raggio, setRaggio] = useState(utente?.area?.raggio_m || 1500);
  const [punto, setPunto] = useState<{ lat: number; lon: number } | null>(null);
  const { inCorso, errore, esegui } = useAzione();
  const aggiorna = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
    setF({ ...f, [k]: e.target.value });

  const invia = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => {
      const ev = await api<Evento>("/v1/events", { metodo: "POST", corpo: {
        titolo: f.titolo, descrizione: f.descrizione, categoria: f.categoria, luogo: f.luogo,
        inizio: localeAIso(f.inizio), fine: localeAIso(f.fine), capienza: f.capienza ? Number(f.capienza) : null,
        modalita: approvazione ? "su_approvazione" : "libera", raggio_m: raggio, gruppo_id: gruppo, ...(punto || {}),
      } });
      router.replace(`/app/eventi/${ev.id}`);
    });
  };

  return (
    <form onSubmit={invia} className="mx-auto max-w-2xl space-y-5">
      <Titolo titolo={gruppo ? "Nuovo evento del gruppo" : "Nuovo evento"} sottotitolo="Indica un luogo approssimativo: i dettagli li condividi in chat con i partecipanti." />
      <div className="scheda space-y-4">
        <label className="block text-sm"><span className="font-medium">Titolo</span>
          <input className="campo mt-1" required minLength={3} maxLength={120} value={f.titolo} onChange={aggiorna("titolo")} placeholder="Es. Calcetto del giovedì" />
        </label>
        <label className="block text-sm"><span className="font-medium">Descrizione</span>
          <textarea className="campo mt-1" rows={3} required minLength={3} maxLength={2000} value={f.descrizione} onChange={aggiorna("descrizione")} />
        </label>
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="text-sm"><span className="font-medium">Categoria</span>
            <select className="campo mt-1" required value={f.categoria} onChange={aggiorna("categoria")}>
              <option value="" disabled>Scegli…</option>
              {Object.entries(catalogo?.categorie.evento || {}).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </label>
          <label className="text-sm"><span className="font-medium">Luogo (approssimativo)</span>
            <input className="campo mt-1" required minLength={3} maxLength={120} value={f.luogo} onChange={aggiorna("luogo")} placeholder="Es. Campetto del parco" />
          </label>
          <label className="text-sm"><span className="font-medium">Inizio</span>
            <input type="datetime-local" className="campo mt-1" required value={f.inizio} onChange={aggiorna("inizio")} />
          </label>
          <label className="text-sm"><span className="font-medium">Fine (facoltativa)</span>
            <input type="datetime-local" className="campo mt-1" value={f.fine} min={f.inizio} onChange={aggiorna("fine")} />
          </label>
          <label className="text-sm"><span className="font-medium">Posti (vuoto = senza limite)</span>
            <input type="number" min={1} max={500} className="campo mt-1" value={f.capienza} onChange={aggiorna("capienza")} />
          </label>
        </div>
        <Interruttore attivo={approvazione} onChange={setApprovazione} etichetta="Approvo io le adesioni"
          descrizione="Le richieste restano in attesa finché non le confermi. Altrimenti si entra subito, fino al completo, poi in lista d'attesa." />
      </div>
      {!gruppo && utente?.area && (
        <div className="scheda space-y-3">
          <Raggio valore={raggio} onChange={setRaggio} etichetta="Visibile ai vicini entro" />
          <p className="text-sm text-tenue">Tocca la mappa per indicare la zona dell&apos;evento (facoltativo: altrimenti è il tuo quartiere).</p>
          <Mappa centro={punto || utente.area} raggio={raggio} selezione={punto} onClic={(lat, lon) => setPunto({ lat, lon })} altezza={300} />
        </div>
      )}
      {errore && <Avviso tipo="errore">{errore}</Avviso>}
      <div className="flex justify-end gap-2">
        <button type="button" className="btn-secondario" onClick={() => router.back()}>Annulla</button>
        <button className="btn-primario" disabled={inCorso}>{inCorso ? "Creazione…" : "Crea evento"}</button>
      </div>
    </form>
  );
}

export default function NuovoEvento() {
  return <Suspense fallback={<Caricamento />}><Modulo /></Suspense>;
}
