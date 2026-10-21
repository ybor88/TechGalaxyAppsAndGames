"use client";
// Privacy e sicurezza: consensi, messaggi, blocchi, segnalazioni e ricorsi, export, cancellazione — © Roberto Di Flumeri
import { Ban, Download, Flag, KeyRound, Scale, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import { Avatar, Avviso, Caricamento, Interruttore, NomeUtente, Titolo, useAzione } from "@/components/ui";
import { api, dataIt, dataOraIt, impostaToken, scarica, type UtentePubblico } from "@/lib/api";
import { useSessione } from "@/lib/sessione";

interface Consensi {
  stato: { finalita: string; descrizione: string; obbligatorio: boolean; concesso: boolean }[];
  registro: { finalita: string; concesso: boolean; versione: string; timestamp: string; revocato_at: string | null }[];
}
interface Segnalazione {
  id: string; oggetto_tipo: string; motivo_testo: string; stato: string; decisione: string | null; decisione_testo: string | null;
  nota_decisione: string | null; ricorso: string | null; ricorso_stato: string | null; created_at: string; titolo: string | null; ricorribile?: boolean;
}
const NOMI: Record<string, string> = {
  trattamento_dati: "Trattamento dei dati", geolocalizzazione: "Posizione del dispositivo", notifiche: "Notifiche",
  profilazione: "Feed «Per te»", analytics: "Statistiche d'uso",
};
const STATI: Record<string, string> = { aperta: "In esame", risolta: "Decisa", archiviata: "Archiviata" };

function Ricorso({ s, onFatto }: { s: Segnalazione; onFatto: () => void }) {
  const [testo, setTesto] = useState("");
  const [aperto, setAperto] = useState(false);
  const { inCorso, errore, esegui } = useAzione();
  if (!aperto) return <button className="btn-secondario text-sm" onClick={() => setAperto(true)}><Scale className="h-4 w-4" /> Presenta ricorso</button>;
  return (
    <form className="space-y-2" onSubmit={(e) => { e.preventDefault(); esegui(async () => { await api(`/v1/reports/${s.id}/ricorso`, { metodo: "POST", corpo: { testo } }); onFatto(); }); }}>
      <textarea className="campo" rows={3} required minLength={10} maxLength={1000} value={testo} onChange={(e) => setTesto(e.target.value)}
        placeholder="Spiega perché la decisione non è corretta (almeno 10 caratteri)" aria-label="Motivo del ricorso" />
      {errore && <Avviso tipo="errore">{errore}</Avviso>}
      <button className="btn-primario text-sm" disabled={inCorso}>Invia ricorso</button>
    </form>
  );
}

export default function Privacy() {
  const { utente, ricarica } = useSessione();
  const [consensi, setConsensi] = useState<Consensi | null>(null);
  const [bloccati, setBloccati] = useState<(UtentePubblico & { bloccato_il: string })[] | null>(null);
  const [segn, setSegn] = useState<{ inviate: Segnalazione[]; ricevute: Segnalazione[] } | null>(null);
  const [pwd, setPwd] = useState({ attuale: "", nuova: "" });
  const [cancella, setCancella] = useState({ password: "", conferma: "" });
  const [esito, setEsito] = useState<string | null>(null);
  const azione = useAzione();

  const carica = () => {
    api<Consensi>("/v1/consents").then(setConsensi);
    api<(UtentePubblico & { bloccato_il: string })[]>("/v1/blocks").then(setBloccati);
    api<{ inviate: Segnalazione[]; ricevute: Segnalazione[] }>("/v1/reports/me").then(setSegn);
  };
  useEffect(() => { carica(); }, []);
  useEffect(() => { if (segn && location.hash) document.querySelector(location.hash)?.scrollIntoView(); }, [segn]);
  if (!utente || !consensi) return <Caricamento />;

  const cambiaConsenso = (f: string, v: boolean) => {
    if (!v && f === "notifiche" && !confirm("Revocando il consenso gli avvisi in attesa verranno cancellati. Continuare?")) return;
    azione.esegui(async () => { await api("/v1/preferences", { metodo: "PATCH", corpo: { consensi: { [f]: v } } }); carica(); });
  };
  const messaggi = (v: boolean) => azione.esegui(async () => {
    await api("/v1/preferences", { metodo: "PATCH", corpo: { accetta_messaggi: v ? "contesto" : "nessuno" } }); await ricarica();
  });
  const sblocca = (u: UtentePubblico) => azione.esegui(async () => { await api(`/v1/blocks/${u.id}`, { metodo: "DELETE" }); carica(); });
  const cambiaPassword = (e: React.FormEvent) => {
    e.preventDefault();
    azione.esegui(async () => {
      await api("/v1/auth/password", { metodo: "POST", corpo: { password_attuale: pwd.attuale, nuova_password: pwd.nuova } });
      setPwd({ attuale: "", nuova: "" }); setEsito("Password aggiornata: le altre sessioni sono state chiuse.");
    });
  };
  const elimina = (e: React.FormEvent) => {
    e.preventDefault();
    azione.esegui(async () => {
      const r = await api<{ ricevuta: string; messaggio: string }>("/v1/account", { metodo: "DELETE", corpo: cancella });
      impostaToken(null);
      alert(`${r.messaggio}\nRicevuta: ${r.ricevuta}`);
      // ricarica completa: la sessione in memoria sparisce (il cookie è già stato cancellato dal server)
      location.replace("/");
    });
  };

  return (
    <>
      <Titolo titolo="Privacy e sicurezza" sottotitolo="Decidi tu cosa condividere. Puoi esportare tutto o cancellare l'account quando vuoi." />
      {azione.errore && <div className="mb-4"><Avviso tipo="errore">{azione.errore}</Avviso></div>}
      {esito && <div className="mb-4"><Avviso tipo="ok">{esito}</Avviso></div>}
      <div className="grid gap-6 lg:grid-cols-2">
        <section className="scheda space-y-4">
          <h2 className="text-lg font-semibold">Consensi</h2>
          {consensi.stato.map((c) => (
            <Interruttore key={c.finalita} attivo={c.concesso} disabilitato={c.obbligatorio} onChange={(v) => cambiaConsenso(c.finalita, v)}
              etichetta={`${NOMI[c.finalita] || c.finalita}${c.obbligatorio ? " (necessario)" : ""}`}
              descrizione={c.obbligatorio ? `${c.descrizione} Per revocarlo cancella l'account.` : c.descrizione} />
          ))}
          <details>
            <summary className="cursor-pointer text-sm text-tenue">Registro dei consensi ({consensi.registro.length})</summary>
            <ul className="mt-2 space-y-1 text-sm">
              {consensi.registro.map((r, i) => (
                <li key={i}>{dataOraIt(r.timestamp)} · {NOMI[r.finalita]}: {r.concesso ? "concesso" : "negato"} ({r.versione})
                  {r.revocato_at && <span className="text-tenue"> · sostituito il {dataIt(r.revocato_at)}</span>}</li>
              ))}
            </ul>
          </details>
        </section>

        <div className="space-y-6">
          <section className="scheda space-y-3">
            <h2 className="text-lg font-semibold">Messaggi</h2>
            <Interruttore attivo={utente.accetta_messaggi === "contesto"} onChange={messaggi} etichetta="I vicini possono scrivermi"
              descrizione="Solo a partire da un tuo annuncio, evento o servizio. Se disattivi, nessuno potrà aprire nuove conversazioni." />
          </section>
          <section className="scheda space-y-3">
            <h2 className="flex items-center gap-2 text-lg font-semibold"><Ban className="h-4 w-4" /> Utenti bloccati</h2>
            {!bloccati ? <Caricamento /> : bloccati.length === 0 ? <p className="text-sm text-tenue">Non hai bloccato nessuno.</p> : (
              <ul className="divide-y divide-bordo">{bloccati.map((u) => (
                <li key={u.id} className="flex items-center justify-between py-2">
                  <span className="flex items-center gap-2"><Avatar alias={u.alias} /><NomeUtente u={u} /></span>
                  <button className="btn-secondario text-sm" onClick={() => sblocca(u)}>Sblocca</button>
                </li>
              ))}</ul>
            )}
          </section>
        </div>

        <section id="segnalazioni" className="scheda space-y-3 lg:col-span-2">
          <h2 className="flex items-center gap-2 text-lg font-semibold"><Flag className="h-4 w-4" /> Segnalazioni e decisioni</h2>
          {!segn ? <Caricamento /> : (
            <div className="grid gap-4 md:grid-cols-2">
              <div>
                <h3 className="mb-2 text-sm font-semibold">Decisioni che ti riguardano</h3>
                {segn.ricevute.length === 0 ? <p className="text-sm text-tenue">Nessuna decisione della moderazione sul tuo account.</p> : (
                  <ul className="space-y-2">{segn.ricevute.map((s) => (
                    <li key={s.id} className="space-y-2 rounded-xl border border-bordo p-3 text-sm">
                      <p className="font-semibold">{s.decisione_testo}</p>
                      <p className="text-tenue">{s.titolo} · motivo: {s.motivo_testo}</p>
                      {s.nota_decisione && <p>Nota della moderazione: {s.nota_decisione}</p>}
                      {s.ricorso_stato && <p className="pillola bg-fondo">Ricorso {s.ricorso_stato}</p>}
                      {s.ricorribile && <Ricorso s={s} onFatto={carica} />}
                    </li>
                  ))}</ul>
                )}
              </div>
              <div>
                <h3 className="mb-2 text-sm font-semibold">Le tue segnalazioni</h3>
                {segn.inviate.length === 0 ? <p className="text-sm text-tenue">Non hai inviato segnalazioni.</p> : (
                  <ul className="space-y-2">{segn.inviate.map((s) => (
                    <li key={s.id} className="rounded-xl border border-bordo p-3 text-sm">
                      <p className="font-medium">{s.titolo || s.oggetto_tipo}</p>
                      <p className="text-tenue">{s.motivo_testo} · {dataIt(s.created_at)} · <strong>{STATI[s.stato] || s.stato}</strong></p>
                      {s.decisione_testo && <p className="mt-1">{s.decisione_testo}</p>}
                    </li>
                  ))}</ul>
                )}
              </div>
            </div>
          )}
        </section>

        <section className="scheda space-y-3">
          <h2 className="text-lg font-semibold">Esporta i tuoi dati</h2>
          <p className="text-sm text-tenue">Profilo, quartiere, contenuti, eventi, conversazioni, feedback e consensi.</p>
          <div className="flex flex-wrap gap-2">
            <button className="btn-secondario" onClick={() => azione.esegui(() => scarica("/v1/account/export?formato=json", "neighbour-dati.json"))}>
              <Download className="h-4 w-4" /> JSON (portabile)</button>
            <button className="btn-secondario" onClick={() => azione.esegui(() => scarica("/v1/account/export?formato=markdown", "neighbour-dati.md"))}>
              <Download className="h-4 w-4" /> Testo leggibile</button>
          </div>
        </section>

        <form onSubmit={cambiaPassword} className="scheda space-y-3">
          <h2 className="flex items-center gap-2 text-lg font-semibold"><KeyRound className="h-4 w-4" /> Cambia password</h2>
          <input type="password" className="campo" required autoComplete="current-password" placeholder="Password attuale" aria-label="Password attuale"
            value={pwd.attuale} onChange={(e) => setPwd({ ...pwd, attuale: e.target.value })} />
          <input type="password" className="campo" required minLength={10} autoComplete="new-password" placeholder="Nuova password (10+ caratteri, lettere e numeri)"
            aria-label="Nuova password" value={pwd.nuova} onChange={(e) => setPwd({ ...pwd, nuova: e.target.value })} />
          <button className="btn-secondario" disabled={azione.inCorso}>Aggiorna password</button>
        </form>

        <form onSubmit={elimina} className="scheda space-y-3 border-errore/40 lg:col-span-2">
          <h2 className="flex items-center gap-2 text-lg font-semibold text-errore"><Trash2 className="h-4 w-4" /> Cancella account</h2>
          <p className="text-sm">Elimina definitivamente account, contenuti, foto e messaggi che hai scritto. L&apos;operazione non è reversibile.</p>
          <div className="grid gap-3 sm:grid-cols-2">
            <input type="password" className="campo" required autoComplete="current-password" placeholder="Password per confermare" aria-label="Password"
              value={cancella.password} onChange={(e) => setCancella({ ...cancella, password: e.target.value })} />
            <input className="campo" required placeholder='Scrivi "ELIMINA"' aria-label="Conferma" value={cancella.conferma}
              onChange={(e) => setCancella({ ...cancella, conferma: e.target.value })} />
          </div>
          <button className="btn-pericolo" disabled={azione.inCorso || cancella.conferma !== "ELIMINA"}>Elimina definitivamente</button>
        </form>
      </div>
    </>
  );
}
