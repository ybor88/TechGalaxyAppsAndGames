"use client";
// Scelta del quartiere e del raggio di visibilità — © Roberto Di Flumeri
import { Crosshair, MapPin } from "lucide-react";
import dynamic from "next/dynamic";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { Avviso, Caricamento, Raggio, Titolo, useAzione } from "@/components/ui";
import { api } from "@/lib/api";
import { CITTA } from "@/lib/mappa";
import { useSessione } from "@/lib/sessione";

const Mappa = dynamic(() => import("@/components/Mappa"), { ssr: false, loading: () => <Caricamento testo="Carico la mappa…" /> });

function SceltaQuartiere() {
  const { utente, ricarica } = useSessione();
  const router = useRouter();
  const benvenuto = useSearchParams().get("benvenuto");
  const [centro, setCentro] = useState<{ lat: number; lon: number }>({ lat: CITTA.Milano[0], lon: CITTA.Milano[1] });
  const [scelta, setScelta] = useState<{ lat: number; lon: number } | null>(null);
  const [nome, setNome] = useState("");
  const [raggio, setRaggio] = useState(1500);
  const [daGps, setDaGps] = useState(false);
  const [consensoGps, setConsensoGps] = useState<boolean | null>(null);
  const { inCorso, errore, setErrore, esegui } = useAzione();

  useEffect(() => {
    if (utente?.area) {
      setCentro({ lat: utente.area.lat, lon: utente.area.lon });
      setScelta({ lat: utente.area.lat, lon: utente.area.lon });
      setNome(utente.area.nome);
      setRaggio(utente.area.raggio_m);
    }
    api<{ consensi: Record<string, boolean> }>("/v1/preferences").then((p) => setConsensoGps(p.consensi.geolocalizzazione)).catch(() => undefined);
  }, [utente?.area]);

  const usaGps = async () => {
    setErrore(null);
    if (!consensoGps) {
      if (!confirm("Per usare la posizione del dispositivo serve il tuo consenso. Salviamo solo una zona approssimata. Attivarlo?")) return;
      await api("/v1/preferences", { metodo: "PATCH", corpo: { consensi: { geolocalizzazione: true } } });
      setConsensoGps(true);
    }
    if (!("geolocation" in navigator)) return setErrore("Il dispositivo non fornisce la posizione: scegli il quartiere sulla mappa.");
    navigator.geolocation.getCurrentPosition(
      (p) => {
        const pos = { lat: p.coords.latitude, lon: p.coords.longitude };
        setCentro(pos); setScelta(pos); setDaGps(true);
      },
      () => setErrore("Posizione non disponibile: scegli il quartiere toccando la mappa."),
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 600000 },
    );
  };

  const salva = (e: React.FormEvent) => {
    e.preventDefault();
    if (!scelta) return setErrore("Tocca la mappa per indicare il tuo quartiere.");
    esegui(async () => {
      await api("/v1/area", { metodo: "PUT", corpo: { nome, lat: scelta.lat, lon: scelta.lon, raggio_m: raggio, da_gps: daGps } });
      await ricarica();
      router.replace("/app");
    });
  };

  return (
    <>
      <Titolo titolo={benvenuto ? "Benvenuto! Dove abiti?" : "Il mio quartiere"}
        sottotitolo="Indica la zona in cui vivi: salveremo solo un'area approssimata, mai il tuo indirizzo." />
      <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
        <div className="space-y-3">
          <div className="flex flex-wrap gap-2">
            <select className="campo w-auto" aria-label="Vai alla città" defaultValue=""
              onChange={(e) => { const c = CITTA[e.target.value]; if (c) setCentro({ lat: c[0], lon: c[1] }); }}>
              <option value="" disabled>Vai alla città…</option>
              {Object.keys(CITTA).map((c) => <option key={c}>{c}</option>)}
            </select>
            <button type="button" className="btn-secondario" onClick={usaGps}><Crosshair className="h-4 w-4" /> Usa la mia posizione</button>
          </div>
          <Mappa centro={centro} raggio={scelta ? raggio : undefined} selezione={scelta} raggioSelezione={600} altezza={460}
            onClic={(lat, lon) => { setScelta({ lat, lon }); setDaGps(false); }} />
          <p className="flex items-center gap-1 text-sm text-tenue"><MapPin className="h-4 w-4" /> Tocca la mappa vicino a casa: non serve essere precisi.</p>
        </div>
        <form onSubmit={salva} className="scheda h-fit space-y-4">
          <div>
            <label htmlFor="nome-area" className="etichetta">Nome del quartiere</label>
            <input id="nome-area" className="campo" required minLength={2} maxLength={80} placeholder="Es. Isola, Milano"
              value={nome} onChange={(e) => setNome(e.target.value)} />
          </div>
          <Raggio valore={raggio} onChange={setRaggio} etichetta="Raggio del quartiere" />
          <p className="text-xs text-tenue">
            È la distanza entro cui vedi i contenuti dei vicini e entro cui i tuoi contenuti sono visibili. Potrai cambiarla per ogni pubblicazione.
          </p>
          {errore && <Avviso tipo="errore">{errore}</Avviso>}
          <button className="btn-primario w-full" disabled={inCorso || !scelta}>{inCorso ? "Salvataggio…" : "Salva il quartiere"}</button>
        </form>
      </div>
    </>
  );
}

export default function Pagina() {
  return <Suspense fallback={<Caricamento />}><SceltaQuartiere /></Suspense>;
}
