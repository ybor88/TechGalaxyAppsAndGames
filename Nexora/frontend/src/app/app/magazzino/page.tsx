"use client";
// Magazzino multi-sede: giacenze, movimenti, lotti in scadenza, numeri di serie, sedi e inventari. © Roberto Di Flumeri
import { AlertTriangle, ArrowLeftRight, ClipboardList, Plus, Search, Star, Warehouse } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Avviso, Campo, Caricamento, Errore, Kpi, Modale, NOMI_MOVIMENTO, Schede, Stato, Titolo, useAzione, useDati, useLimite, Vuoto } from "@/components/ui";
import { api, qs, type Magazzino } from "@/lib/api";
import { data, dataOra, euro, numero } from "@/lib/formato";
import { useSessione } from "@/lib/sessione";

type Scheda = "giacenze" | "movimenti" | "lotti" | "seriali" | "sedi" | "inventari";

export default function PaginaMagazzino() {
  const { puo } = useSessione();
  const [scheda, setScheda] = useState<Scheda>("giacenze");
  const { dati: magazzini, ricarica: ricaricaSedi } = useDati<Magazzino[]>("/api/magazzini");
  useEffect(() => {
    const s = new URLSearchParams(window.location.search).get("scheda") as Scheda | null;
    if (s) setScheda(s);
  }, []);
  const valore = magazzini?.reduce((s, m) => s + m.valore, 0) ?? 0;

  return (
    <>
      <Titolo titolo="Magazzino" sottotitolo="Giacenze in tempo reale su tutte le sedi, con lotti, scadenze e numeri di serie"
        azioni={puo("magazzino") && <Link href="/app/magazzino/movimento" className="btn-primario"><ArrowLeftRight className="h-4 w-4" /> Registra movimento</Link>} />
      {magazzini && (
        <section className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <Kpi etichetta="Valore totale a costo" valore={euro(valore, true)} icona={Warehouse} />
          {magazzini.slice(0, 3).map((m, i) => (
            <Kpi key={m.id} etichetta={m.nome} valore={euro(m.valore, true)} tono={(["viola", "ciano", "ok"] as const)[i]} nota={`${m.articoli} articoli · ${numero(m.pezzi)} unità${m.principale ? " · principale" : ""}`} />
          ))}
        </section>
      )}
      <Schede attiva={scheda} onCambia={setScheda} voci={[
        { id: "giacenze", etichetta: "Giacenze" }, { id: "movimenti", etichetta: "Movimenti" }, { id: "lotti", etichetta: "Lotti e scadenze" },
        { id: "seriali", etichetta: "Numeri di serie" }, { id: "inventari", etichetta: "Inventari" }, { id: "sedi", etichetta: "Sedi" },
      ]} />
      {scheda === "giacenze" && <Giacenze magazzini={magazzini ?? []} />}
      {scheda === "movimenti" && <Movimenti magazzini={magazzini ?? []} />}
      {scheda === "lotti" && <Lotti />}
      {scheda === "seriali" && <Seriali />}
      {scheda === "inventari" && <Inventari magazzini={magazzini ?? []} />}
      {scheda === "sedi" && <Sedi magazzini={magazzini} ricarica={ricaricaSedi} />}
    </>
  );
}

function Giacenze({ magazzini }: { magazzini: Magazzino[] }) {
  const [magazzinoId, setMagazzinoId] = useState("");
  const [q, setQ] = useState("");
  const [sotto, setSotto] = useState(false);
  const { dati, errore, ricarica } = useDati<any[]>(`/api/giacenze${qs({ magazzinoId, q, sottoScorta: sotto || undefined })}`);
  const { visibili, Altri } = useLimite(dati, 60);
  return (
    <>
      <div className="scheda mb-4 grid gap-3 p-4 md:grid-cols-[2fr_1fr_auto]">
        <input className="campo" placeholder="Cerca articolo, SKU o barcode…" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Cerca" />
        <select className="campo" value={magazzinoId} onChange={(e) => setMagazzinoId(e.target.value)} aria-label="Sede">
          <option value="">Tutte le sedi</option>{magazzini.map((m) => <option key={m.id} value={m.id}>{m.nome}</option>)}
        </select>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={sotto} onChange={(e) => setSotto(e.target.checked)} /> Solo sotto scorta</label>
      </div>
      {errore && <Errore messaggio={errore} riprova={ricarica} />}
      {!dati && !errore && <Caricamento />}
      {dati && (
        <div className="scheda overflow-x-auto p-0">
          <table className="tabella">
            <thead><tr><th>Articolo</th><th>Categoria</th><th>Per sede</th><th className="num">Giacenza</th><th className="num">Impegnata</th><th className="num">Disponibile</th><th className="num">Valore</th></tr></thead>
            <tbody>{visibili.map((g) => (
              <tr key={g.varianteId}>
                <td><Link href={`/app/prodotti/${g.prodottoId}`} className="font-medium hover:underline">{g.nome}</Link><p className="font-mono text-xs text-tenue">{g.sku}</p></td>
                <td className="text-tenue">{g.categoria ?? "—"}</td>
                <td className="text-xs">{g.perMagazzino.filter((m: any) => m.quantita).map((m: any) => <span key={m.magazzinoId} className="mr-1 inline-block rounded-lg bg-fondo px-2 py-0.5">{m.magazzino}: {numero(m.quantita)}</span>)}</td>
                <td className="num">{numero(g.quantita)} {g.unita}</td>
                <td className="num text-tenue">{g.riservata ? numero(g.riservata) : "—"}</td>
                <td className={`num font-semibold ${g.sottoScorta ? "text-attenzione" : g.disponibile <= 0 ? "text-errore" : ""}`}>
                  {g.sottoScorta && <AlertTriangle className="mr-1 inline h-3.5 w-3.5" aria-label="Sotto scorta" />}{numero(g.disponibile)}
                  {g.scortaMinima > 0 && <span className="block text-xs font-normal text-tenue">min. {numero(g.scortaMinima)}</span>}</td>
                <td className="num">{euro(g.valore)}</td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
      <Altri />
    </>
  );
}

function Movimenti({ magazzini }: { magazzini: Magazzino[] }) {
  const [tipo, setTipo] = useState("");
  const [magazzinoId, setMagazzinoId] = useState("");
  const { dati, errore, ricarica } = useDati<any[]>(`/api/movimenti${qs({ tipo, magazzinoId, limite: 300 })}`);
  const { visibili, Altri } = useLimite(dati, 60);
  const uscita = (t: string, q: number) => ["vendita", "scarico"].includes(t) || (t === "rettifica" && q < 0);
  return (
    <>
      <div className="scheda mb-4 grid gap-3 p-4 md:grid-cols-2">
        <select className="campo" value={tipo} onChange={(e) => setTipo(e.target.value)} aria-label="Tipo">
          <option value="">Tutti i tipi</option>{Object.entries(NOMI_MOVIMENTO).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <select className="campo" value={magazzinoId} onChange={(e) => setMagazzinoId(e.target.value)} aria-label="Sede">
          <option value="">Tutte le sedi</option>{magazzini.map((m) => <option key={m.id} value={m.id}>{m.nome}</option>)}
        </select>
      </div>
      {errore && <Errore messaggio={errore} riprova={ricarica} />}
      {!dati && !errore && <Caricamento />}
      {dati && (
        <div className="scheda overflow-x-auto p-0">
          <table className="tabella">
            <thead><tr><th>Data</th><th>Tipo</th><th>Articolo</th><th>Sede</th><th>Lotto / seriali</th><th className="num">Quantità</th><th>Causale</th></tr></thead>
            <tbody>{visibili.map((m) => (
              <tr key={m.id}>
                <td className="whitespace-nowrap">{dataOra(m.data)}</td><td><span className="pillola bg-fondo">{NOMI_MOVIMENTO[m.tipo] ?? m.tipo}</span></td>
                <td><p className="font-medium">{m.articolo}</p><p className="font-mono text-xs text-tenue">{m.sku}</p></td>
                <td>{m.magazzino}{m.magazzinoDest ? ` → ${m.magazzinoDest}` : ""}</td>
                <td className="text-xs">{m.lotto && <span className="font-mono text-ciano">{m.lotto}</span>}{m.seriali?.length ? <span className="font-mono text-viola"> {m.seriali.join(", ")}</span> : null}{!m.lotto && !m.seriali && "—"}</td>
                <td className={`num font-medium ${uscita(m.tipo, m.quantita) ? "text-errore" : m.tipo === "trasferimento" ? "" : "text-ok"}`}>
                  {uscita(m.tipo, m.quantita) ? "−" : m.tipo === "trasferimento" ? "⇄ " : "+"}{numero(Math.abs(m.quantita))} {m.unita}</td>
                <td className="text-tenue">{m.riferimentoTipo === "ordine" ? <Link className="link" href={`/app/ordini/${m.riferimentoId}`}>{m.causale}</Link>
                  : m.riferimentoTipo === "acquisto" ? <Link className="link" href={`/app/acquisti/${m.riferimentoId}`}>{m.causale}</Link> : m.causale ?? "—"}</td>
              </tr>
            ))}</tbody>
          </table>
          {!dati.length && <Vuoto titolo="Nessun movimento" />}
        </div>
      )}
      <Altri />
    </>
  );
}

function Lotti() {
  const [giorni, setGiorni] = useState(60);
  const { dati, errore, ricarica } = useDati<any[]>(`/api/lotti${qs({ entroGiorni: giorni })}`);
  return (
    <>
      <div className="scheda mb-4 flex flex-wrap items-center gap-3 p-4 text-sm">
        <span>Lotti in scadenza entro</span>
        {[30, 60, 180, 3650].map((g) => <button key={g} className={`btn btn-piccolo ${giorni === g ? "gradiente text-white" : "border border-bordo"}`} onClick={() => setGiorni(g)}>{g === 3650 ? "tutti" : `${g} giorni`}</button>)}
        <span className="text-tenue">Le uscite prelevano sempre il lotto che scade prima (FEFO).</span>
      </div>
      {errore && <Errore messaggio={errore} riprova={ricarica} />}
      {!dati && !errore && <Caricamento />}
      {dati && (
        <div className="scheda overflow-x-auto p-0">
          <table className="tabella">
            <thead><tr><th>Lotto</th><th>Articolo</th><th>Scadenza</th><th>Sedi</th><th className="num">Quantità</th></tr></thead>
            <tbody>{dati.map((l) => (
              <tr key={l.id}>
                <td className="font-mono">{l.codice}</td><td><p className="font-medium">{l.articolo}</p><p className="font-mono text-xs text-tenue">{l.sku}</p></td>
                <td className={l.giorniAllaScadenza !== null && l.giorniAllaScadenza <= 30 ? "font-semibold text-errore" : l.giorniAllaScadenza !== null && l.giorniAllaScadenza <= 60 ? "text-attenzione" : ""}>
                  {data(l.scadenza)}{l.giorniAllaScadenza !== null && <span className="block text-xs">{l.giorniAllaScadenza < 0 ? "scaduto" : `tra ${l.giorniAllaScadenza} giorni`}</span>}</td>
                <td className="text-xs">{l.perMagazzino.map((m: any) => `${m.magazzino}: ${numero(m.quantita)}`).join(" · ")}</td>
                <td className="num font-medium">{numero(l.quantita)}</td>
              </tr>
            ))}</tbody>
          </table>
          {!dati.length && <Vuoto titolo="Nessun lotto in scadenza nel periodo" />}
        </div>
      )}
    </>
  );
}

function Seriali() {
  const [q, setQ] = useState("");
  const [cerca, setCerca] = useState("");
  const { dati, errore } = useDati<any[]>(cerca ? `/api/seriali${qs({ q: cerca })}` : null);
  return (
    <>
      <form className="scheda mb-4 flex gap-2 p-4" onSubmit={(e) => { e.preventDefault(); setCerca(q.trim()); }}>
        <input className="campo" placeholder="Numero di serie (anche parziale), es. PRO-1000" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Numero di serie" />
        <button className="btn-primario" disabled={!q.trim()}><Search className="h-4 w-4" /> Cerca</button>
      </form>
      {errore && <Errore messaggio={errore} />}
      {dati && (
        <div className="scheda overflow-x-auto p-0">
          <table className="tabella">
            <thead><tr><th>Seriale</th><th>Articolo</th><th>Stato</th><th>Sede</th><th>Ordine / cliente</th></tr></thead>
            <tbody>{dati.map((s) => (
              <tr key={s.numero + s.sku}>
                <td className="font-mono">{s.numero}</td><td>{s.articolo}</td>
                <td><span className={`pillola ${s.stato === "disponibile" ? "bg-ok/15 text-ok" : s.stato === "venduto" ? "bg-primario/15 text-primario" : "bg-attenzione/15 text-attenzione"}`}>{s.stato}</span></td>
                <td>{s.magazzino ?? "—"}</td>
                <td>{s.ordine ? <Link href={`/app/ordini/${s.ordine.id}`} className="link">{s.ordine.numero} · {s.ordine.cliente}</Link> : "—"}</td>
              </tr>
            ))}</tbody>
          </table>
          {!dati.length && <Vuoto titolo="Nessun seriale trovato" />}
        </div>
      )}
      {!cerca && <p className="text-sm text-tenue">Traccia ogni pezzo: dove si trova, a chi è stato venduto e con quale ordine (utile per garanzie e assistenza).</p>}
    </>
  );
}

function Inventari({ magazzini }: { magazzini: Magazzino[] }) {
  const { puo } = useSessione();
  const { dati, errore, ricarica } = useDati<any[]>("/api/inventari");
  const [nuovo, setNuovo] = useState(false);
  const [magazzinoId, setMagazzinoId] = useState("");
  const azione = useAzione();
  return (
    <>
      {puo("magazzino") && <div className="mb-4 flex justify-end"><button className="btn-primario" onClick={() => setNuovo(true)}><ClipboardList className="h-4 w-4" /> Nuovo inventario</button></div>}
      {errore && <Errore messaggio={errore} riprova={ricarica} />}
      {!dati && !errore && <Caricamento />}
      {dati && !dati.length && <div className="scheda"><Vuoto icona={ClipboardList} titolo="Nessun inventario" testo="Apri un inventario per contare la merce con il barcode e rettificare le differenze." /></div>}
      {dati && dati.length > 0 && (
        <div className="scheda overflow-x-auto p-0">
          <table className="tabella">
            <thead><tr><th>Aperto il</th><th>Sede</th><th>Stato</th><th className="num">Articoli contati</th><th className="num">Differenze</th><th /></tr></thead>
            <tbody>{dati.map((i) => (
              <tr key={i.id}><td>{dataOra(i.createdAt)}</td><td>{i.magazzino.nome}</td><td><Stato stato={i.stato} /></td>
                <td className="num">{i.contate} / {i.righe}</td><td className={`num ${i.differenze ? "text-attenzione" : ""}`}>{i.differenze}</td>
                <td><Link href={`/app/magazzino/inventari/${i.id}`} className="btn-secondario btn-piccolo">{i.stato === "aperto" ? "Continua la conta" : "Apri"}</Link></td></tr>
            ))}</tbody>
          </table>
        </div>
      )}
      {nuovo && (
        <Modale titolo="Nuovo inventario" onChiudi={() => setNuovo(false)}>
          <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); azione.esegui(async () => {
            const i = await api("/api/inventari", { metodo: "POST", corpo: { magazzinoId } });
            window.location.href = `/app/magazzino/inventari/${i.id}`;
          }); }}>
            <Campo etichetta="Sede da inventariare">
              <select className="campo" value={magazzinoId} onChange={(e) => setMagazzinoId(e.target.value)} required>
                <option value="">Scegli…</option>{magazzini.map((m) => <option key={m.id} value={m.id}>{m.nome}</option>)}
              </select>
            </Campo>
            <p className="text-sm text-tenue">Viene fotografata la giacenza attuale di ogni articolo; alla chiusura le differenze contate diventano rettifiche di magazzino.</p>
            {azione.errore && <Avviso tipo="errore">{azione.errore}</Avviso>}
            <button className="btn-primario w-full" disabled={azione.inCorso}>Apri inventario</button>
          </form>
        </Modale>
      )}
    </>
  );
}

function Sedi({ magazzini, ricarica }: { magazzini: Magazzino[] | null; ricarica: () => void }) {
  const { puo } = useSessione();
  const [nuova, setNuova] = useState(false);
  const [d, setD] = useState({ codice: "", nome: "", citta: "", indirizzo: "" });
  const azione = useAzione();
  if (!magazzini) return <Caricamento />;
  return (
    <>
      {puo("magazzino") && <div className="mb-4 flex justify-end"><button className="btn-primario" onClick={() => setNuova(true)}><Plus className="h-4 w-4" /> Nuova sede</button></div>}
      {azione.errore && <Avviso tipo="errore" className="mb-3">{azione.errore}</Avviso>}
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {magazzini.map((m) => (
          <div key={m.id} className="scheda">
            <div className="flex items-start justify-between gap-2">
              <div><p className="font-semibold">{m.nome}</p><p className="text-sm text-tenue">{m.codice} · {[m.indirizzo, m.citta].filter(Boolean).join(", ") || "—"}</p></div>
              {m.principale ? <span className="pillola gradiente text-white"><Star className="h-3 w-3" /> Principale</span>
                : puo("magazzino") && <button className="btn-secondario btn-piccolo" onClick={() => azione.esegui(async () => { await api(`/api/magazzini/${m.id}`, { metodo: "PATCH", corpo: { principale: true } }); ricarica(); })}>Rendi principale</button>}
            </div>
            <dl className="mt-4 grid grid-cols-3 gap-2 text-center text-sm">
              <div className="rounded-xl bg-fondo p-2"><dt className="text-xs text-tenue">Articoli</dt><dd className="font-semibold">{m.articoli}</dd></div>
              <div className="rounded-xl bg-fondo p-2"><dt className="text-xs text-tenue">Unità</dt><dd className="font-semibold">{numero(m.pezzi)}</dd></div>
              <div className="rounded-xl bg-fondo p-2"><dt className="text-xs text-tenue">Valore</dt><dd className="font-semibold">{euro(m.valore, true)}</dd></div>
            </dl>
          </div>
        ))}
      </div>
      {nuova && (
        <Modale titolo="Nuova sede di magazzino" onChiudi={() => setNuova(false)}>
          <form className="grid gap-3 sm:grid-cols-3" onSubmit={(e) => { e.preventDefault(); azione.esegui(async () => {
            await api("/api/magazzini", { metodo: "POST", corpo: { ...d, indirizzo: d.indirizzo || null, citta: d.citta || null } }); setNuova(false); ricarica();
          }); }}>
            <Campo etichetta="Codice"><input className="campo uppercase" value={d.codice} onChange={(e) => setD({ ...d, codice: e.target.value })} required minLength={2} maxLength={10} /></Campo>
            <Campo etichetta="Nome" className="sm:col-span-2"><input className="campo" value={d.nome} onChange={(e) => setD({ ...d, nome: e.target.value })} required /></Campo>
            <Campo etichetta="Indirizzo" className="sm:col-span-2"><input className="campo" value={d.indirizzo} onChange={(e) => setD({ ...d, indirizzo: e.target.value })} /></Campo>
            <Campo etichetta="Città"><input className="campo" value={d.citta} onChange={(e) => setD({ ...d, citta: e.target.value })} /></Campo>
            {azione.errore && <div className="sm:col-span-3"><Avviso tipo="errore">{azione.errore}</Avviso></div>}
            <button className="btn-primario sm:col-span-3" disabled={azione.inCorso}>Crea sede</button>
          </form>
        </Modale>
      )}
    </>
  );
}
