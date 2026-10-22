"use client";
// Ricerca articoli a catalogo e scansione barcode con la fotocamera. © Roberto Di Flumeri
// La scansione usa la Barcode Detection API del browser (gratuita, integrata in Chrome, Edge e Android):
// dove non è disponibile resta l'inserimento manuale o il lettore USB, che si comporta come una tastiera.

import { Camera, Loader2, ScanLine, Search } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { api, qs, type Articolo } from "@/lib/api";
import { euro, numero } from "@/lib/formato";
import { Avviso, Modale } from "./ui";

export function CercaArticolo({ onScelto, magazzinoId, segnaposto = "Cerca per nome, SKU o barcode…", autoFocus }: {
  onScelto: (a: Articolo) => void; magazzinoId?: string; segnaposto?: string; autoFocus?: boolean;
}) {
  const [q, setQ] = useState("");
  const [risultati, setRisultati] = useState<Articolo[]>([]);
  const [aperto, setAperto] = useState(false);
  const [carica, setCarica] = useState(false);
  const [scanner, setScanner] = useState(false);
  const [errore, setErrore] = useState<string | null>(null);
  const [indice, setIndice] = useState(0);

  useEffect(() => {
    if (!aperto) return;
    const t = setTimeout(async () => {
      setCarica(true);
      try { setRisultati(await api<Articolo[]>(`/api/articoli${qs({ q, magazzinoId })}`)); } catch { setRisultati([]); } finally { setCarica(false); }
    }, 180);
    return () => clearTimeout(t);
  }, [q, aperto, magazzinoId]);

  const scegli = (a: Articolo) => {
    onScelto(a);
    setQ("");
    setAperto(false);
    setErrore(null);
  };

  /** Codice esatto (barcode o SKU) da scanner o da lettore USB: selezione diretta. */
  const daCodice = async (codice: string) => {
    setErrore(null);
    try {
      const trovati = await api<Articolo[]>(`/api/articoli${qs({ q: codice, magazzinoId })}`);
      const esatto = trovati.find((a) => a.barcode === codice || a.sku.toLowerCase() === codice.toLowerCase());
      if (esatto) scegli(esatto);
      else setErrore(`Nessun articolo con codice «${codice}»`);
    } catch (e) {
      setErrore(e instanceof Error ? e.message : "Ricerca non riuscita");
    }
  };

  return (
    <div className="relative">
      <div className="flex gap-2">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-tenue" aria-hidden />
          <input className="campo pl-9" value={q} placeholder={segnaposto} autoFocus={autoFocus} aria-label="Cerca articolo"
            onChange={(e) => { setQ(e.target.value); setAperto(true); setIndice(0); }} onFocus={() => setAperto(true)}
            onBlur={() => setTimeout(() => setAperto(false), 180)}
            onKeyDown={(e) => {
              if (e.key === "ArrowDown") { e.preventDefault(); setIndice((i) => Math.min(i + 1, risultati.length - 1)); }
              if (e.key === "ArrowUp") { e.preventDefault(); setIndice((i) => Math.max(i - 1, 0)); }
              if (e.key === "Enter") {
                e.preventDefault();
                // i lettori USB inviano il codice seguito da Invio
                if (/^\d{8,14}$/.test(q.trim())) daCodice(q.trim());
                else if (risultati[indice]) scegli(risultati[indice]);
              }
            }} />
        </div>
        <button type="button" className="btn-secondario px-3" onClick={() => setScanner(true)} title="Scansiona barcode" aria-label="Scansiona barcode con la fotocamera">
          <ScanLine className="h-4 w-4" />
        </button>
      </div>
      {aperto && (
        <ul className="absolute z-50 mt-1 max-h-80 w-full overflow-y-auto rounded-xl border border-bordo bg-pannello shadow-lg" role="listbox">
          {carica && !risultati.length && <li className="flex items-center gap-2 p-3 text-sm text-tenue"><Loader2 className="h-4 w-4 animate-spin" /> Ricerca…</li>}
          {!carica && !risultati.length && <li className="p-3 text-sm text-tenue">Nessun articolo trovato</li>}
          {risultati.map((a, i) => (
            <li key={a.varianteId} role="option" aria-selected={i === indice}>
              <button type="button" onMouseDown={(e) => e.preventDefault()} onClick={() => scegli(a)}
                className={`flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-fondo ${i === indice ? "bg-fondo" : ""}`}>
                <span className="min-w-0">
                  <span className="block truncate font-medium">{a.nome}</span>
                  <span className="text-xs text-tenue">{a.sku}{a.barcode ? ` · ${a.barcode}` : ""}{a.gestioneLotti ? " · lotti" : ""}{a.gestioneSeriali ? " · seriali" : ""}</span>
                </span>
                <span className="shrink-0 text-right text-xs">
                  <span className="block font-medium">{euro(a.prezzo)}</span>
                  <span className={a.disponibile > 0 ? "text-ok" : "text-errore"}>disp. {numero(a.disponibile)} {a.unita}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {errore && <p className="mt-1 text-sm text-errore" role="alert">{errore}</p>}
      {scanner && <Scanner onCodice={(c) => { setScanner(false); daCodice(c); }} onChiudi={() => setScanner(false)} />}
    </div>
  );
}

type Rilevatore = { detect: (s: CanvasImageSource) => Promise<{ rawValue: string }[]> };

export function Scanner({ onCodice, onChiudi, continuo }: { onCodice: (codice: string) => void; onChiudi: () => void; continuo?: boolean }) {
  const video = useRef<HTMLVideoElement>(null);
  const [stato, setStato] = useState<"avvio" | "attivo" | "non_supportato" | "negato">("avvio");
  const [manuale, setManuale] = useState("");
  const [ultimo, setUltimo] = useState<string | null>(null);
  const richiamo = useRef(onCodice);
  richiamo.current = onCodice; // la fotocamera non si riavvia quando cambia la funzione del genitore

  useEffect(() => {
    let flusso: MediaStream | null = null;
    let fermo = false;
    let ultimoCodice = "", ultimoIstante = 0;
    const Det = (window as unknown as { BarcodeDetector?: new (o: { formats: string[] }) => Rilevatore }).BarcodeDetector;
    if (!Det || !navigator.mediaDevices?.getUserMedia) { setStato("non_supportato"); return; }
    const rilevatore = new Det({ formats: ["ean_13", "ean_8", "upc_a", "upc_e", "code_128", "code_39", "qr_code"] });
    navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" } }).then(async (s) => {
      flusso = s;
      if (!video.current || fermo) return;
      video.current.srcObject = s;
      await video.current.play();
      setStato("attivo");
      const ciclo = async () => {
        if (fermo || !video.current) return;
        try {
          const trovati = await rilevatore.detect(video.current);
          const codice = trovati[0]?.rawValue;
          // in modalità continua lo stesso codice si accetta di nuovo dopo 1,5 secondi
          if (codice && (codice !== ultimoCodice || Date.now() - ultimoIstante > 1500)) {
            ultimoCodice = codice; ultimoIstante = Date.now();
            navigator.vibrate?.(60);
            setUltimo(codice);
            richiamo.current(codice);
            if (!continuo) return;
          }
        } catch {}
        setTimeout(ciclo, 160);
      };
      ciclo();
    }).catch(() => setStato("negato"));
    return () => { fermo = true; flusso?.getTracks().forEach((t) => t.stop()); };
  }, [continuo]);

  return (
    <Modale titolo="Scansiona barcode" onChiudi={onChiudi}>
      <div className="space-y-3">
        {stato !== "non_supportato" && stato !== "negato" && (
          <div className="relative overflow-hidden rounded-xl bg-black">
            <video ref={video} className="aspect-video w-full object-cover" muted playsInline />
            <div className="pointer-events-none absolute inset-x-8 top-1/2 h-0.5 -translate-y-1/2 bg-ciano shadow-[0_0_12px_2px_rgba(56,189,248,0.8)]" />
            {stato === "avvio" && <p className="absolute inset-0 flex items-center justify-center gap-2 text-sm text-white"><Camera className="h-4 w-4" /> Avvio fotocamera…</p>}
          </div>
        )}
        {stato === "non_supportato" && <Avviso tipo="info">Questo browser non supporta la lettura dei barcode con la fotocamera: inserisci il codice a mano o usa un lettore USB.</Avviso>}
        {stato === "negato" && <Avviso tipo="attenzione">Accesso alla fotocamera non consentito. Puoi abilitarlo dalle impostazioni del browser o inserire il codice a mano.</Avviso>}
        {ultimo && continuo && <Avviso tipo="ok">Letto: <strong>{ultimo}</strong></Avviso>}
        <form className="flex gap-2" onSubmit={(e) => { e.preventDefault(); if (manuale.trim()) { onCodice(manuale.trim()); setManuale(""); } }}>
          <input className="campo" value={manuale} onChange={(e) => setManuale(e.target.value)} placeholder="Codice EAN o SKU" aria-label="Codice manuale" inputMode="text" />
          <button className="btn-primario">Usa</button>
        </form>
      </div>
    </Modale>
  );
}
