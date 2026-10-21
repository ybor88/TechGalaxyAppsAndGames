"use client";
// Notifiche geolocalizzate (F10): avvisi, preferenze, ore di silenzio e riepilogo — © Roberto Di Flumeri
import clsx from "clsx";
import { Bell, BellOff, CheckCheck, Moon } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Avviso, Caricamento, Interruttore, Raggio, Titolo, useAzione, Vuoto } from "@/components/ui";
import { api, daServer, fa } from "@/lib/api";

interface Notifica { id: string; tipo: string; titolo: string; testo: string; link: string | null; letta: boolean; data: string }
interface Preferenze {
  notifiche: { tipi: string[]; categorie: string[]; raggio_m: number; frequenza: string; quiet_inizio: number | null; quiet_fine: number | null };
  accetta_messaggi: string;
  consensi: Record<string, boolean>;
}

const TIPI = [["regalo", "Regali"], ["offerta", "Offerte"], ["richiesta", "Ricerche"], ["scambio", "Scambi"], ["aiuto", "Richieste di aiuto"],
  ["segnalazione", "Segnalazioni"], ["evento", "Eventi"]];
const ORE = Array.from({ length: 24 }, (_, h) => h);

export default function Notifiche() {
  const [elenco, setElenco] = useState<{ notifiche: Notifica[]; in_attesa: number } | null>(null);
  const [p, setP] = useState<Preferenze | null>(null);
  const [permesso, setPermesso] = useState<string>("default");
  const [salvato, setSalvato] = useState(false);
  const azione = useAzione();

  const carica = () => api<{ notifiche: Notifica[]; in_attesa: number }>("/v1/notifications").then(setElenco);
  useEffect(() => {
    carica();
    api<Preferenze>("/v1/preferences").then(setP);
    if ("Notification" in window) setPermesso(Notification.permission);
  }, []);

  const salva = (corpo: Record<string, unknown>) => azione.esegui(async () => {
    setP(await api<Preferenze>("/v1/preferences", { metodo: "PATCH", corpo }));
    setSalvato(true); setTimeout(() => setSalvato(false), 2000);
  });
  const n = p?.notifiche;
  const alternaTipo = (t: string) => {
    if (!n) return;
    const attuali = n.tipi.length ? n.tipi : TIPI.map(([k]) => k);
    const nuovi = attuali.includes(t) ? attuali.filter((x) => x !== t) : [...attuali, t];
    salva({ notifiche: { tipi: nuovi.length === TIPI.length ? [] : nuovi } });
  };
  const leggi = (x: Notifica) => { if (!x.letta) api(`/v1/notifications/${x.id}/letta`, { metodo: "POST" }).catch(() => undefined); };
  const tutteLette = () => azione.esegui(async () => { await api("/v1/notifications/lette", { metodo: "POST" }); await carica(); });
  const chiediPermesso = async () => {
    if (!("Notification" in window)) return;
    setPermesso(await Notification.requestPermission());
  };

  return (
    <>
      <Titolo titolo="Notifiche" sottotitolo="Avvisi dal quartiere, messaggi ed esiti, con la frequenza che scegli tu."
        azioni={elenco?.notifiche.some((x) => !x.letta) && <button className="btn-secondario" onClick={tutteLette}><CheckCheck className="h-4 w-4" /> Segna tutte lette</button>} />
      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        <section>
          {!elenco ? <Caricamento /> : (
            <>
              {elenco.in_attesa > 0 && (
                <div className="mb-3"><Avviso tipo="info"><Moon className="mr-1 inline h-4 w-4" />
                  {elenco.in_attesa} {elenco.in_attesa === 1 ? "avviso arriverà" : "avvisi arriveranno"} alla fine delle ore di silenzio o nel prossimo riepilogo.</Avviso></div>
              )}
              {elenco.notifiche.length === 0 ? <Vuoto icona={Bell} titolo="Nessuna notifica" testo="Ti avviseremo delle novità vicino a te, secondo le tue preferenze." /> : (
                <ul className="scheda divide-y divide-bordo p-0">
                  {elenco.notifiche.map((x) => {
                    const corpo = (
                      <div className={clsx("flex gap-3 p-4", !x.letta && "bg-verde/5")}>
                        <span className={clsx("mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full", x.letta ? "bg-transparent" : "bg-arancio")} aria-hidden />
                        <div className="min-w-0">
                          <p className={clsx("text-sm", !x.letta && "font-semibold")}>{x.titolo}</p>
                          <p className="text-sm text-tenue">{x.testo}</p>
                          <p className="mt-1 text-xs text-tenue" title={daServer(x.data).toLocaleString("it-IT")}>{fa(x.data)}</p>
                        </div>
                      </div>
                    );
                    return <li key={x.id}>{x.link ? <Link href={x.link} onClick={() => leggi(x)} className="block hover:bg-fondo">{corpo}</Link> : corpo}</li>;
                  })}
                </ul>
              )}
            </>
          )}
        </section>

        <aside className="space-y-4">
          {!p || !n ? <Caricamento /> : (
            <>
              {!p.consensi.notifiche ? (
                <Avviso tipo="attenzione"><BellOff className="mr-1 inline h-4 w-4" />Le notifiche sono disattivate.
                  <button className="ml-1 font-semibold underline" onClick={() => salva({ consensi: { notifiche: true } })}>Attivale</button></Avviso>
              ) : null}
              <div className="scheda space-y-4">
                <h2 className="font-semibold">Cosa seguire nel quartiere</h2>
                <div className="flex flex-wrap gap-2">
                  {TIPI.map(([k, v]) => {
                    const attivo = !n.tipi.length || n.tipi.includes(k);
                    return <button key={k} aria-pressed={attivo} onClick={() => alternaTipo(k)}
                      className={clsx("filtro", attivo ? "gradiente border-transparent text-white" : "border-bordo")}>{v}</button>;
                  })}
                </div>
                <Raggio valore={n.raggio_m} onChange={(v) => setP({ ...p, notifiche: { ...n, raggio_m: v } })} etichetta="Avvisami entro" />
                <button className="btn-secondario w-full text-sm" onClick={() => salva({ notifiche: { raggio_m: n.raggio_m } })}>Salva distanza</button>
              </div>
              <div className="scheda space-y-3">
                <h2 className="font-semibold">Frequenza</h2>
                {[["immediata", "Subito", "Al massimo qualche avviso all'ora: gli altri finiscono in un riepilogo."],
                  ["giornaliera", "Riepilogo giornaliero", "Un solo messaggio al mattino con tutte le novità."],
                  ["disattivata", "Nessun avviso di quartiere", "Ricevi solo messaggi ed esiti che ti riguardano."]].map(([v, t, d]) => (
                  <label key={v} className="flex cursor-pointer items-start gap-2 rounded-xl border border-bordo p-3 text-sm">
                    <input type="radio" name="frequenza" className="mt-1" checked={n.frequenza === v} onChange={() => salva({ notifiche: { frequenza: v } })} />
                    <span><span className="font-medium">{t}</span><span className="block text-tenue">{d}</span></span>
                  </label>
                ))}
              </div>
              <div className="scheda space-y-3">
                <h2 className="font-semibold">Ore di silenzio</h2>
                <Interruttore attivo={n.quiet_inizio !== null} etichetta="Non disturbare"
                  descrizione="Gli avvisi arrivati in queste ore ti aspettano al risveglio."
                  onChange={(v) => salva({ notifiche: v ? { quiet_inizio: 22, quiet_fine: 8 } : { senza_silenzio: true } })} />
                {n.quiet_inizio !== null && (
                  <div className="flex items-center gap-2 text-sm">
                    dalle <select className="campo w-auto" aria-label="Inizio silenzio" value={n.quiet_inizio}
                      onChange={(e) => salva({ notifiche: { quiet_inizio: Number(e.target.value) } })}>{ORE.map((h) => <option key={h} value={h}>{h}:00</option>)}</select>
                    alle <select className="campo w-auto" aria-label="Fine silenzio" value={n.quiet_fine ?? 8}
                      onChange={(e) => salva({ notifiche: { quiet_fine: Number(e.target.value) } })}>{ORE.map((h) => <option key={h} value={h}>{h}:00</option>)}</select>
                  </div>
                )}
              </div>
              <div className="scheda space-y-2">
                <h2 className="font-semibold">Avvisi sul dispositivo</h2>
                {permesso === "granted" ? <p className="text-sm text-ok">Attivi: riceverai un avviso quando l&apos;app è in secondo piano.</p>
                  : permesso === "denied" ? <p className="text-sm text-tenue">Bloccati dal browser: puoi riattivarli dalle impostazioni del sito.</p>
                  : <button className="btn-secondario w-full" onClick={chiediPermesso}><Bell className="h-4 w-4" /> Attiva sul dispositivo</button>}
              </div>
              {salvato && <Avviso tipo="ok">Preferenze salvate.</Avviso>}
              {azione.errore && <Avviso tipo="errore">{azione.errore}</Avviso>}
            </>
          )}
        </aside>
      </div>
    </>
  );
}
