"use client";
// Feed del quartiere (§6.1 GET /v1/feed) con filtri e copia offline — © Roberto Di Flumeri
import clsx from "clsx";
import { Plus, Search, SlidersHorizontal, WifiOff } from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import SchedaElemento from "@/components/SchedaElemento";
import { Avviso, Caricamento, Raggio, Titolo, Vuoto } from "@/components/ui";
import { api, categorieDi, ErroreApi, type ElementoFeed } from "@/lib/api";
import { useCatalogo } from "@/lib/catalogo";
import { useSessione } from "@/lib/sessione";

const SCHEDE = [
  { id: "tutti", nome: "Tutto" }, { id: "mercatino", nome: "Mercatino" }, { id: "aiuto", nome: "Aiuti" },
  { id: "evento", nome: "Eventi" }, { id: "segnalazione", nome: "Segnalazioni" }, { id: "per_te", nome: "Per te" },
];
const CACHE = "nb_feed_ultimo";

interface Risposta { elementi: ElementoFeed[]; cursore: string | null; area: { nome: string; raggio_m: number } }

export default function Feed() {
  const { utente } = useSessione();
  const catalogo = useCatalogo();
  const [scheda, setScheda] = useState("tutti");
  const [categoria, setCategoria] = useState("");
  const [periodo, setPeriodo] = useState("");
  const [q, setQ] = useState("");
  const [cerca, setCerca] = useState("");
  const [raggio, setRaggio] = useState<number | null>(null);
  const [filtri, setFiltri] = useState(false);
  const [dati, setDati] = useState<Risposta | null>(null);
  const [altri, setAltri] = useState(false);
  const [errore, setErrore] = useState<string | null>(null);
  const [offline, setOffline] = useState<string | null>(null);

  const query = useCallback((cursore?: string | null) => {
    const p = new URLSearchParams();
    if (scheda === "per_te") p.set("per_te", "true"); else p.set("tipo", scheda);
    if (categoria) p.set("categoria", categoria);
    if (periodo) p.set("periodo", periodo);
    if (cerca) p.set("q", cerca);
    if (raggio) p.set("raggio_m", String(raggio));
    if (cursore) p.set("cursore", cursore);
    return `/v1/feed?${p}`;
  }, [scheda, categoria, periodo, cerca, raggio]);

  useEffect(() => {
    let vivo = true;
    setDati(null); setErrore(null);
    api<Risposta>(query()).then((r) => {
      if (!vivo) return;
      setDati(r); setOffline(null);
      if (scheda === "tutti" && !categoria && !periodo && !cerca) {
        try { localStorage.setItem(CACHE, JSON.stringify({ quando: new Date().toISOString(), dati: r })); } catch {}
      }
    }).catch((e: ErroreApi) => {
      if (!vivo) return;
      if (e.stato === 0) {
        // offline: si mostrano gli ultimi contenuti consultati, salvati sul dispositivo
        try {
          const c = JSON.parse(localStorage.getItem(CACHE) || "null");
          if (c) { setDati(c.dati); setOffline(c.quando); return; }
        } catch {}
      }
      setErrore(e.message);
    });
    return () => { vivo = false; };
  }, [query, scheda, categoria, periodo, cerca]);

  const caricaAltri = async () => {
    if (!dati?.cursore) return;
    setAltri(true);
    try {
      const r = await api<Risposta>(query(dati.cursore));
      setDati({ ...r, elementi: [...dati.elementi, ...r.elementi] });
    } finally { setAltri(false); }
  };

  const tipoCategorie = scheda === "mercatino" ? "offerta" : scheda;
  const categorie = ["mercatino", "aiuto", "evento", "segnalazione"].includes(scheda) ? categorieDi(catalogo, tipoCategorie) : {};

  return (
    <>
      <Titolo titolo={utente?.area?.nome || "Il mio quartiere"}
        sottotitolo={`Cosa succede entro ${((raggio || utente?.area?.raggio_m || 1500) / 1000).toFixed(1).replace(".", ",")} km da te`}
        azioni={<Link href="/app/pubblica" className="btn-primario hidden md:inline-flex"><Plus className="h-4 w-4" /> Pubblica</Link>} />

      <div className="-mx-4 mb-3 flex gap-2 overflow-x-auto px-4 pb-1" role="tablist" aria-label="Tipo di contenuti">
        {SCHEDE.map((s) => (
          <button key={s.id} role="tab" aria-selected={scheda === s.id} onClick={() => { setScheda(s.id); setCategoria(""); }}
            className={clsx("filtro shrink-0", scheda === s.id ? "gradiente border-transparent text-white" : "border-bordo bg-pannello")}>
            {s.nome}
          </button>
        ))}
      </div>

      <form className="mb-4 flex gap-2" onSubmit={(e) => { e.preventDefault(); setCerca(q.trim()); }} role="search">
        <label className="relative flex-1">
          <span className="sr-only">Cerca nel quartiere</span>
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-tenue" aria-hidden />
          <input className="campo pl-9" placeholder="Cerca: bici, passeggino, spesa…" value={q} maxLength={60}
            onChange={(e) => { setQ(e.target.value); if (!e.target.value) setCerca(""); }} />
        </label>
        <button type="button" className={clsx("btn-secondario", filtri && "border-verde")} onClick={() => setFiltri(!filtri)}
          aria-expanded={filtri} aria-controls="filtri"><SlidersHorizontal className="h-4 w-4" /> <span className="hidden sm:inline">Filtri</span></button>
      </form>

      {filtri && (
        <div id="filtri" className="scheda mb-4 grid gap-4 sm:grid-cols-3">
          <Raggio valore={raggio || utente?.area?.raggio_m || 1500} onChange={setRaggio} etichetta="Distanza massima" />
          <label className="text-sm"><span className="font-medium">Categoria</span>
            <select className="campo mt-1" value={categoria} onChange={(e) => setCategoria(e.target.value)} disabled={!Object.keys(categorie).length}>
              <option value="">{Object.keys(categorie).length ? "Tutte" : "Scegli prima un tipo"}</option>
              {Object.entries(categorie).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </label>
          <label className="text-sm"><span className="font-medium">Data</span>
            <select className="campo mt-1" value={periodo} onChange={(e) => setPeriodo(e.target.value)}>
              <option value="">Qualsiasi</option><option value="oggi">Ultime 24 ore</option>
              <option value="settimana">Ultimi 7 giorni</option><option value="mese">Ultimi 30 giorni</option>
            </select>
          </label>
        </div>
      )}

      {offline && (
        <div className="mb-4"><Avviso tipo="attenzione"><WifiOff className="mr-1 inline h-4 w-4" />Sei offline: questi sono i contenuti
          della tua ultima visita ({new Date(offline).toLocaleString("it-IT")}).</Avviso></div>
      )}
      {errore && <Avviso tipo={errore.includes("consenso") ? "info" : "errore"}>{errore}
        {errore.includes("consenso") && <> <Link href="/app/privacy" className="font-semibold underline">Attivalo in Privacy e sicurezza</Link>.</>}</Avviso>}

      {!dati && !errore ? <Caricamento /> : dati && dati.elementi.length === 0 ? (
        <Vuoto titolo="Ancora niente qui" testo={cerca ? `Nessun risultato per «${cerca}».` : "Sii il primo: regala un oggetto, chiedi una mano o proponi un evento."}
          azione={<Link href="/app/pubblica" className="btn-primario"><Plus className="h-4 w-4" /> Pubblica</Link>} />
      ) : dati && (
        <>
          <ul className="space-y-3">
            {dati.elementi.map((e) => <li key={`${e.genere}-${e.id}`}><SchedaElemento e={e} /></li>)}
          </ul>
          {dati.cursore && !offline && (
            <button className="btn-secondario mx-auto mt-4 flex" onClick={caricaAltri} disabled={altri}>{altri ? "Caricamento…" : "Mostra altri"}</button>
          )}
        </>
      )}
    </>
  );
}
