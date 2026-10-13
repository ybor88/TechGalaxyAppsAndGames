"use client";
// Privacy e dati: consensi, motore AI, conservazione, export e cancellazione — © Roberto Di Flumeri
import { Download, KeyRound, Trash2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Avviso, Caricamento, Interruttore, Titolo, useAzione } from "@/components/ui";
import { api, dataOraIt, impostaToken, scarica } from "@/lib/api";
import { useSessione } from "@/lib/sessione";

interface StatoConsensi {
  stato: { finalita: string; descrizione: string; obbligatorio: boolean; concesso: boolean }[];
  registro: { finalita: string; concesso: boolean; versione: string; timestamp: string; revocato_at: string | null }[];
}
const NOMI: Record<string, string> = {
  trattamento_profilo: "Trattamento del profilo", elaborazione_ai: "Elaborazione AI", condivisione: "Condivisione", analytics: "Statistiche d'uso",
};

export default function Privacy() {
  const { utente, ricarica } = useSessione();
  const router = useRouter();
  const [consensi, setConsensi] = useState<StatoConsensi | null>(null);
  const [ai, setAi] = useState<{ ollama_disponibile: boolean; modello: string } | null>(null);
  const [pwd, setPwd] = useState({ attuale: "", nuova: "" });
  const [cancella, setCancella] = useState({ password: "", conferma: "" });
  const [esito, setEsito] = useState<string | null>(null);
  const azione = useAzione();

  const carica = () => api<StatoConsensi>("/v1/consents").then(setConsensi);
  useEffect(() => {
    carica();
    api<{ ollama_disponibile: boolean; modello: string }>("/v1/ai/stato").then(setAi).catch(() => undefined);
  }, []);

  if (!consensi || !utente) return <Caricamento />;

  const cambiaConsenso = (finalita: string, concesso: boolean) => {
    if (!concesso && finalita === "condivisione" && !confirm("Revocando la condivisione tutti i link attivi smetteranno subito di funzionare. Continuare?")) return;
    if (!concesso && finalita === "analytics" && !confirm("Revocando le statistiche verranno cancellati anche i dati d'uso già raccolti. Continuare?")) return;
    azione.esegui(async () => { await api(`/v1/consents/${finalita}`, { metodo: "PUT", corpo: { concesso } }); await carica(); });
  };

  const aggiornaUtente = (corpo: Record<string, unknown>) =>
    azione.esegui(async () => { await api("/v1/auth/me", { metodo: "PATCH", corpo }); await ricarica(); setEsito("Impostazioni salvate."); });

  const cambiaPassword = (e: React.FormEvent) => {
    e.preventDefault();
    azione.esegui(async () => {
      await api("/v1/auth/password", { metodo: "POST", corpo: { password_attuale: pwd.attuale, nuova_password: pwd.nuova } });
      setPwd({ attuale: "", nuova: "" });
      setEsito("Password aggiornata. Le altre sessioni sono state chiuse.");
    });
  };

  const eliminaAccount = (e: React.FormEvent) => {
    e.preventDefault();
    azione.esegui(async () => {
      const r = await api<{ ricevuta: string; messaggio: string }>("/v1/account", { metodo: "DELETE", corpo: cancella });
      impostaToken(null);
      alert(`${r.messaggio}\nRicevuta: ${r.ricevuta}`);
      router.replace("/");
    });
  };

  return (
    <>
      <Titolo titolo="Privacy e dati" sottotitolo="Decidi tu cosa viene elaborato. Puoi esportare tutto o cancellare l'account in qualsiasi momento." />
      {azione.errore && <div className="mb-4"><Avviso tipo="errore">{azione.errore}</Avviso></div>}
      {esito && <div className="mb-4"><Avviso tipo="ok">{esito}</Avviso></div>}

      <section className="scheda mb-6 space-y-4">
        <h2 className="text-lg font-semibold">Consensi</h2>
        {consensi.stato.map((c) => (
          <Interruttore key={c.finalita} attivo={c.concesso} onChange={(v) => !c.obbligatorio && cambiaConsenso(c.finalita, v)}
            etichetta={`${NOMI[c.finalita]}${c.obbligatorio ? " (necessario)" : ""}`}
            descrizione={c.obbligatorio ? `${c.descrizione} Per revocarlo cancella l'account.` : c.descrizione} />
        ))}
        <details>
          <summary className="cursor-pointer text-sm text-tenue">Registro dei consensi ({consensi.registro.length})</summary>
          <ul className="mt-2 space-y-1 text-sm">
            {consensi.registro.map((r, i) => (
              <li key={i}>{dataOraIt(r.timestamp)} · {NOMI[r.finalita]}: {r.concesso ? "concesso" : "negato"} ({r.versione})
                {r.revocato_at && <span className="text-tenue"> · sostituito il {dataOraIt(r.revocato_at)}</span>}</li>
            ))}
          </ul>
        </details>
      </section>

      <section className="scheda mb-6 space-y-4">
        <h2 className="text-lg font-semibold">Motore dell&apos;alter ego</h2>
        <div className="space-y-2" role="radiogroup" aria-label="Motore AI">
          {[
            ["locale", "Integrato", "Istantaneo, tutto sul server Ghost Me, nessun modello esterno."],
            ["auto", "Automatico", "Usa Ollama se è attivo, altrimenti il motore integrato."],
            ["ollama", "Ollama (locale)", "Risposte più naturali con un modello sul tuo computer. Può essere lento senza GPU."],
          ].map(([v, t, d]) => (
            <label key={v} className="flex cursor-pointer items-start gap-3 rounded-xl border border-bordo p-3">
              <input type="radio" name="motore" className="mt-1" checked={utente.motore_ai === v} onChange={() => aggiornaUtente({ motore_ai: v })} />
              <span><span className="font-medium">{t}</span><span className="block text-sm text-tenue">{d}</span></span>
            </label>
          ))}
        </div>
        {ai && <p className="text-sm text-tenue">Ollama {ai.ollama_disponibile ? `disponibile (modello ${ai.modello})` : "non raggiungibile in questo momento"}.</p>}
      </section>

      <section className="scheda mb-6 space-y-3">
        <h2 className="text-lg font-semibold">Conservazione delle conversazioni</h2>
        <label className="text-sm">Elimina automaticamente le conversazioni non usate da
          <select className="campo mt-1 w-auto" value={utente.conservazione_chat_giorni ?? 0}
            onChange={(e) => {
              const v = Number(e.target.value);
              aggiornaUtente(v ? { conservazione_chat_giorni: v } : { conservazione_illimitata: true });
            }}>
            <option value={0}>Mai (conserva sempre)</option><option value={7}>7 giorni</option><option value={30}>30 giorni</option>
            <option value={90}>90 giorni</option><option value={365}>1 anno</option>
          </select>
        </label>
      </section>

      <section className="scheda mb-6 space-y-3">
        <h2 className="text-lg font-semibold">Esporta i tuoi dati</h2>
        <p className="text-sm text-tenue">Profilo, conoscenze, versioni, conversazioni, decisioni, diario e consensi.</p>
        <div className="flex flex-wrap gap-2">
          <button className="btn-secondario" onClick={() => azione.esegui(() => scarica("/v1/account/export?formato=json", "ghostme-dati.json"))}>
            <Download className="h-4 w-4" /> JSON (portabile)</button>
          <button className="btn-secondario" onClick={() => azione.esegui(() => scarica("/v1/account/export?formato=markdown", "ghostme-dati.md"))}>
            <Download className="h-4 w-4" /> Testo leggibile (Markdown)</button>
        </div>
      </section>

      <form onSubmit={cambiaPassword} className="scheda mb-6 space-y-3">
        <h2 className="text-lg font-semibold"><KeyRound className="mr-1 inline h-4 w-4" /> Cambia password</h2>
        <div className="grid gap-3 sm:grid-cols-2">
          <input type="password" className="campo" required autoComplete="current-password" placeholder="Password attuale" aria-label="Password attuale"
            value={pwd.attuale} onChange={(e) => setPwd({ ...pwd, attuale: e.target.value })} />
          <input type="password" className="campo" required minLength={10} autoComplete="new-password" placeholder="Nuova password (10+ caratteri, lettere e numeri)"
            aria-label="Nuova password" value={pwd.nuova} onChange={(e) => setPwd({ ...pwd, nuova: e.target.value })} />
        </div>
        <button className="btn-secondario" disabled={azione.inCorso}>Aggiorna password</button>
      </form>

      <form onSubmit={eliminaAccount} className="scheda space-y-3 border-errore/40">
        <h2 className="text-lg font-semibold text-errore"><Trash2 className="mr-1 inline h-4 w-4" /> Cancella account</h2>
        <p className="text-sm">Elimina definitivamente account e dati. L&apos;operazione non è reversibile: esporta prima i dati se ti servono.</p>
        <div className="grid gap-3 sm:grid-cols-2">
          <input type="password" className="campo" required autoComplete="current-password" placeholder="Password per confermare" aria-label="Password"
            value={cancella.password} onChange={(e) => setCancella({ ...cancella, password: e.target.value })} />
          <input className="campo" required placeholder='Scrivi "ELIMINA"' aria-label="Conferma" value={cancella.conferma}
            onChange={(e) => setCancella({ ...cancella, conferma: e.target.value })} />
        </div>
        <button className="btn-pericolo" disabled={azione.inCorso || cancella.conferma !== "ELIMINA"}>Elimina definitivamente</button>
      </form>
    </>
  );
}
