"use client";
// Registrazione di carichi, scarichi, trasferimenti tra sedi e rettifiche, con lotti, scadenze e numeri di serie. © Roberto Di Flumeri
import { ArrowDownToLine, ArrowLeftRight, ArrowUpFromLine, CheckCircle2, SlidersHorizontal } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { CercaArticolo } from "@/components/articoli";
import { Avviso, Campo, Titolo, useAzione, useDati } from "@/components/ui";
import { api, qs, type Articolo, type Magazzino } from "@/lib/api";
import { aCentesimi, daCentesimi, numero } from "@/lib/formato";

const TIPI = [
  { id: "carico", nome: "Carico", icona: ArrowDownToLine, testo: "Merce in entrata (giacenza iniziale, resi, produzione)" },
  { id: "scarico", nome: "Scarico", icona: ArrowUpFromLine, testo: "Merce in uscita non venduta (omaggi, rotture, uso interno)" },
  { id: "trasferimento", nome: "Trasferimento", icona: ArrowLeftRight, testo: "Spostamento tra due sedi, lotti e seriali compresi" },
  { id: "rettifica", nome: "Rettifica", icona: SlidersHorizontal, testo: "Correzione con segno (es. −2 dopo una verifica)" },
] as const;

export default function NuovoMovimento() {
  const { dati: magazzini } = useDati<Magazzino[]>("/api/magazzini");
  const [tipo, setTipo] = useState<(typeof TIPI)[number]["id"]>("carico");
  const [articolo, setArticolo] = useState<Articolo | null>(null);
  const [magazzinoId, setMagazzinoId] = useState("");
  const [destId, setDestId] = useState("");
  const [quantita, setQuantita] = useState("1");
  const [lotto, setLotto] = useState({ codice: "", scadenza: "" });
  const [lottoId, setLottoId] = useState("");
  const [seriali, setSeriali] = useState("");
  const [scelti, setScelti] = useState<string[]>([]);
  const [causale, setCausale] = useState("");
  const [costo, setCosto] = useState("");
  const [lotti, setLotti] = useState<any[]>([]);
  const [serialiDisponibili, setSerialiDisponibili] = useState<string[]>([]);
  const [fatto, setFatto] = useState<string | null>(null);
  const { inCorso, errore, esegui } = useAzione();
  const uscita = tipo === "scarico" || tipo === "trasferimento";

  useEffect(() => { if (magazzini && !magazzinoId) setMagazzinoId(magazzini.find((m) => m.principale)?.id ?? magazzini[0]?.id ?? ""); }, [magazzini, magazzinoId]);
  useEffect(() => {
    // arrivando dalla scheda prodotto l'articolo è già scelto
    const v = new URLSearchParams(window.location.search).get("varianteId");
    if (v) api<Articolo[]>(`/api/articoli${qs({ varianteId: v })}`).then((r) => r[0] && scegli(r[0])).catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  // lotti e seriali presenti nella sede di partenza, per le uscite
  useEffect(() => {
    setLottoId(""); setScelti([]);
    if (!articolo || !magazzinoId) return;
    if (articolo.gestioneLotti) api<any[]>(`/api/lotti${qs({ varianteId: articolo.varianteId })}`).then((l) => setLotti(l.filter((x) => x.perMagazzino.some((m: any) => m.magazzinoId === magazzinoId)))).catch(() => setLotti([]));
    if (articolo.gestioneSeriali) api(`/api/prodotti/${articolo.prodottoId}`).then((p) => setSerialiDisponibili(p.varianti.find((v: any) => v.id === articolo.varianteId)?.seriali
      .filter((s: any) => s.stato === "disponibile" && s.magazzinoId === magazzinoId).map((s: any) => s.numero) ?? [])).catch(() => setSerialiDisponibili([]));
  }, [articolo, magazzinoId]);

  const scegli = (a: Articolo) => { setArticolo(a); setCosto(daCentesimi(a.costo)); setFatto(null); };
  const elencoSeriali = seriali.split(/[\s,;]+/).map((s) => s.trim()).filter(Boolean);

  const salva = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => {
      if (!articolo) throw new Error("Scegli l'articolo");
      let q = Number(quantita.replace(",", "."));
      const corpo: Record<string, unknown> = { tipo, varianteId: articolo.varianteId, magazzinoId, causale: causale || null };
      if (tipo === "trasferimento") corpo.magazzinoDestId = destId;
      if (articolo.gestioneSeriali && tipo !== "rettifica") {
        // per gli articoli con seriali la quantità è il numero di seriali indicati
        const s = uscita ? scelti : elencoSeriali;
        if (!s.length) throw new Error("Indica i numeri di serie da movimentare");
        corpo.seriali = s;
        q = s.length;
      }
      if (articolo.gestioneLotti) {
        if (tipo === "carico" || (tipo === "rettifica" && q > 0)) corpo.lotto = { codice: lotto.codice.trim(), scadenza: lotto.scadenza || null };
        else if (lottoId) corpo.lottoId = lottoId;
      }
      if (tipo === "carico" && costo) corpo.costoUnitario = aCentesimi(costo);
      corpo.quantita = q;
      await api("/api/movimenti", { metodo: "POST", corpo });
      setFatto(`${TIPI.find((t) => t.id === tipo)!.nome} registrato: ${numero(q)} ${articolo.unita} di ${articolo.nome}`);
      setArticolo(null); setQuantita("1"); setLotto({ codice: "", scadenza: "" }); setSeriali(""); setScelti([]); setCausale("");
    });
  };

  return (
    <>
      <Titolo titolo="Registra movimento di magazzino" indietro={<Link href="/app/magazzino?scheda=movimenti" className="link">← Magazzino</Link>}
        sottotitolo="Ogni movimento aggiorna all'istante giacenze, lotti e seriali, e resta nello storico" />
      <div className="mb-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4" role="radiogroup" aria-label="Tipo di movimento">
        {TIPI.map(({ id, nome, icona: I, testo }) => (
          <button key={id} type="button" role="radio" aria-checked={tipo === id} onClick={() => setTipo(id)}
            className={`scheda p-4 text-left transition ${tipo === id ? "border-primario ring-2 ring-primario/40" : "hover:border-primario/40"}`}>
            <span className={`mb-2 inline-flex h-9 w-9 items-center justify-center rounded-xl ${tipo === id ? "gradiente text-white" : "bg-fondo"}`}><I className="h-4 w-4" /></span>
            <p className="font-semibold">{nome}</p><p className="text-xs text-tenue">{testo}</p>
          </button>
        ))}
      </div>
      {fatto && <Avviso tipo="ok" className="mb-4"><CheckCircle2 className="sr-only" />{fatto}. Puoi registrare subito il successivo.</Avviso>}
      <form onSubmit={salva} className="scheda grid gap-4 md:grid-cols-2">
        <Campo etichetta="Articolo" className="md:col-span-2">
          {articolo ? (
            <div className="flex items-center justify-between gap-2 rounded-xl border border-bordo bg-fondo px-3 py-2">
              <span><span className="font-medium">{articolo.nome}</span> <span className="font-mono text-xs text-tenue">{articolo.sku}</span>
                <span className="block text-xs text-tenue">disponibile {numero(articolo.disponibile)} {articolo.unita}{articolo.gestioneLotti ? " · a lotti" : ""}{articolo.gestioneSeriali ? " · con seriali" : ""}</span></span>
              <button type="button" className="link text-sm" onClick={() => setArticolo(null)}>Cambia</button>
            </div>
          ) : <CercaArticolo onScelto={scegli} autoFocus />}
        </Campo>
        <Campo etichetta={tipo === "trasferimento" ? "Da sede" : "Sede"}>
          <select className="campo" value={magazzinoId} onChange={(e) => setMagazzinoId(e.target.value)} required>
            {magazzini?.map((m) => <option key={m.id} value={m.id}>{m.nome}</option>)}
          </select>
        </Campo>
        {tipo === "trasferimento" ? (
          <Campo etichetta="A sede">
            <select className="campo" value={destId} onChange={(e) => setDestId(e.target.value)} required>
              <option value="">Scegli…</option>{magazzini?.filter((m) => m.id !== magazzinoId).map((m) => <option key={m.id} value={m.id}>{m.nome}</option>)}
            </select>
          </Campo>
        ) : (
          !(articolo?.gestioneSeriali && tipo !== "rettifica") && (
            <Campo etichetta={tipo === "rettifica" ? "Differenza (con segno, es. −2)" : "Quantità"}>
              <input className="campo" value={quantita} onChange={(e) => setQuantita(e.target.value)} inputMode="decimal" required />
            </Campo>
          )
        )}
        {tipo === "trasferimento" && !articolo?.gestioneSeriali && (
          <Campo etichetta="Quantità"><input className="campo" value={quantita} onChange={(e) => setQuantita(e.target.value)} inputMode="decimal" required /></Campo>
        )}

        {articolo?.gestioneLotti && (tipo === "carico" || tipo === "rettifica") && (
          <>
            <Campo etichetta="Codice lotto"><input className="campo font-mono" value={lotto.codice} onChange={(e) => setLotto({ ...lotto, codice: e.target.value })} required={tipo === "carico"} /></Campo>
            <Campo etichetta="Scadenza"><input className="campo" type="date" value={lotto.scadenza} onChange={(e) => setLotto({ ...lotto, scadenza: e.target.value })} /></Campo>
          </>
        )}
        {articolo?.gestioneLotti && uscita && (
          <Campo etichetta="Lotto" aiuto="Vuoto: prelievo automatico FEFO (prima il lotto che scade prima)" className="md:col-span-2">
            <select className="campo" value={lottoId} onChange={(e) => setLottoId(e.target.value)}>
              <option value="">Automatico (FEFO)</option>
              {lotti.map((l) => <option key={l.id} value={l.id}>{l.codice} · scad. {l.scadenza ? new Date(l.scadenza).toLocaleDateString("it-IT") : "—"} · {numero(l.perMagazzino.find((m: any) => m.magazzinoId === magazzinoId)?.quantita)} disp.</option>)}
            </select>
          </Campo>
        )}
        {articolo?.gestioneSeriali && tipo === "carico" && (
          <Campo etichetta={`Numeri di serie (${elencoSeriali.length})`} aiuto="Uno per riga, oppure separati da virgola: la quantità è il numero di seriali" className="md:col-span-2">
            <textarea className="campo font-mono" rows={4} value={seriali} onChange={(e) => setSeriali(e.target.value)} required />
          </Campo>
        )}
        {articolo?.gestioneSeriali && uscita && (
          <fieldset className="md:col-span-2">
            <legend className="etichetta">Seriali da movimentare ({scelti.length})</legend>
            <div className="flex max-h-48 flex-wrap gap-2 overflow-y-auto rounded-xl border border-bordo p-3">
              {serialiDisponibili.map((s) => (
                <label key={s} className={`pillola cursor-pointer border font-mono ${scelti.includes(s) ? "gradiente border-transparent text-white" : "border-bordo"}`}>
                  <input type="checkbox" className="sr-only" checked={scelti.includes(s)} onChange={(e) => setScelti(e.target.checked ? [...scelti, s] : scelti.filter((x) => x !== s))} />{s}
                </label>
              ))}
              {!serialiDisponibili.length && <span className="text-sm text-tenue">Nessun seriale disponibile in questa sede.</span>}
            </div>
          </fieldset>
        )}
        {tipo === "carico" && <Campo etichetta="Costo unitario (€)" aiuto="Aggiorna il costo medio"><input className="campo" value={costo} onChange={(e) => setCosto(e.target.value)} inputMode="decimal" /></Campo>}
        <Campo etichetta="Causale" className={tipo === "carico" ? "" : "md:col-span-2"}><input className="campo" value={causale} onChange={(e) => setCausale(e.target.value)} maxLength={200} placeholder="es. Giacenza iniziale, Rottura in negozio…" /></Campo>
        {errore && <div className="md:col-span-2"><Avviso tipo="errore">{errore}</Avviso></div>}
        <div className="flex justify-end md:col-span-2"><button className="btn-primario" disabled={inCorso || !articolo}>Registra {TIPI.find((t) => t.id === tipo)!.nome.toLowerCase()}</button></div>
      </form>
    </>
  );
}
