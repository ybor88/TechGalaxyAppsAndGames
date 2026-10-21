"use client";
// Pubblicazione di annunci, richieste di aiuto e segnalazioni (F02, F04, F06) — © Roberto Di Flumeri
// La bozza resta salvata sul dispositivo anche offline; la posizione è sempre approssimata.
import { CalendarDays, Camera, MapPin, Trash2, X } from "lucide-react";
import dynamic from "next/dynamic";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import { Avviso, Caricamento, ICONE_TIPO, Raggio, Titolo, useAzione } from "@/components/ui";
import { api, caricaFoto, categorieDi, localeAIso, type Contenuto, type Foto } from "@/lib/api";
import { useCatalogo } from "@/lib/catalogo";
import { useSessione } from "@/lib/sessione";

const Mappa = dynamic(() => import("@/components/Mappa"), { ssr: false, loading: () => <Caricamento testo="Carico la mappa…" /> });

const TIPI: { id: string; titolo: string; testo: string }[] = [
  { id: "regalo", titolo: "Regalo", testo: "Un oggetto che non ti serve più" },
  { id: "offerta", titolo: "Offro o vendo", testo: "Un oggetto, anche a prezzo simbolico" },
  { id: "richiesta", titolo: "Cerco", testo: "Qualcosa che ti servirebbe" },
  { id: "scambio", titolo: "Scambio", testo: "Baratto tra vicini" },
  { id: "aiuto", titolo: "Chiedo aiuto", testo: "Piccole cose, non emergenze" },
  { id: "segnalazione", titolo: "Segnalazione", testo: "Un avviso utile per il quartiere" },
];
const BOZZA = "nb_bozza_pubblica";

function Modulo() {
  const { utente } = useSessione();
  const catalogo = useCatalogo();
  const router = useRouter();
  const gruppo = useSearchParams().get("gruppo");
  const [tipo, setTipo] = useState(gruppo ? "gruppo" : "");
  const [f, setF] = useState({ titolo: "", descrizione: "", categoria: "", prezzo: "", disponibilita: "", scadenza: "" });
  const [foto, setFoto] = useState<Foto[]>([]);
  const [raggio, setRaggio] = useState(utente?.area?.raggio_m || 1500);
  const [luogo, setLuogo] = useState<{ lat: number; lon: number } | null>(null);
  const [mostraMappa, setMostraMappa] = useState(false);
  const [pubblicato, setPubblicato] = useState<Contenuto | null>(null);
  const [caricando, setCaricando] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);
  const { inCorso, errore, setErrore, esegui } = useAzione();

  // bozza locale: ripristino e salvataggio automatico (supporto offline §7)
  useEffect(() => {
    if (gruppo) return;
    try {
      const b = JSON.parse(localStorage.getItem(BOZZA) || "null");
      if (b?.tipo) { setTipo(b.tipo); setF(b.f); }
    } catch {}
  }, [gruppo]);
  useEffect(() => {
    if (gruppo || !tipo) return;
    try { localStorage.setItem(BOZZA, JSON.stringify({ tipo, f })); } catch {}
  }, [tipo, f, gruppo]);

  const categorie = categorieDi(catalogo, tipo);
  const aggiorna = (campo: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
    setF({ ...f, [campo]: e.target.value });

  const aggiungiFoto = async (files: FileList | null) => {
    if (!files) return;
    setCaricando(true); setErrore(null);
    try {
      for (const file of Array.from(files).slice(0, 4 - foto.length)) {
        const nuova = await caricaFoto(file);
        setFoto((x) => [...x, nuova]);
      }
    } catch (e) { setErrore(e instanceof Error ? e.message : "Caricamento non riuscito"); }
    finally { setCaricando(false); if (fileRef.current) fileRef.current.value = ""; }
  };

  const invia = (bozza: boolean) => esegui(async () => {
    const corpo = {
      tipo, categoria: gruppo ? "altro" : f.categoria, titolo: f.titolo, descrizione: f.descrizione, bozza,
      prezzo: f.prezzo || null, disponibilita: f.disponibilita || null, scadenza: localeAIso(f.scadenza),
      raggio_m: raggio, foto_ids: foto.map((x) => x.id), gruppo_id: gruppo, ...(luogo ? luogo : {}),
    };
    const p = await api<Contenuto>("/v1/posts", { metodo: "POST", corpo });
    try { localStorage.removeItem(BOZZA); } catch {}
    if (gruppo) router.replace(`/app/gruppi/${gruppo}`);
    else setPubblicato(p);
  });

  if (pubblicato) {
    return (
      <div className="mx-auto max-w-xl space-y-4">
        <Titolo titolo={pubblicato.stato === "bozza" ? "Bozza salvata" : "Pubblicato!"} />
        {pubblicato.avviso_emergenza && <Avviso tipo="errore"><strong>{pubblicato.avviso_emergenza}</strong></Avviso>}
        <Avviso tipo="ok">
          {pubblicato.stato === "bozza" ? "La trovi nel tuo profilo: potrai pubblicarla quando vuoi."
            : `«${pubblicato.titolo}» è visibile ai vicini entro ${(raggio / 1000).toFixed(1).replace(".", ",")} km.` +
              (pubblicato.vicini_avvisati ? ` Abbiamo avvisato ${pubblicato.vicini_avvisati} ${pubblicato.vicini_avvisati === 1 ? "vicino interessato" : "vicini interessati"}.` : "")}
        </Avviso>
        <div className="flex flex-wrap gap-2">
          <Link href={`/app/annunci/${pubblicato.id}`} className="btn-primario">Apri</Link>
          <Link href="/app" className="btn-secondario">Torna al quartiere</Link>
        </div>
      </div>
    );
  }

  if (!tipo) {
    return (
      <>
        <Titolo titolo="Cosa vuoi pubblicare?" sottotitolo="Per un evento usa la sezione Eventi."
          azioni={<Link href="/app/eventi/nuovo" className="btn-secondario"><CalendarDays className="h-4 w-4" /> Crea un evento</Link>} />
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {TIPI.map((t) => {
            const Icona = ICONE_TIPO[t.id];
            return (
              <li key={t.id}>
                <button className="scheda flex w-full items-center gap-3 text-left transition hover:border-verde" onClick={() => setTipo(t.id)}>
                  <span className="rounded-xl bg-verde/10 p-3 text-verde"><Icona className="h-6 w-6" aria-hidden /></span>
                  <span><span className="block font-semibold">{t.titolo}</span><span className="text-sm text-tenue">{t.testo}</span></span>
                </button>
              </li>
            );
          })}
        </ul>
      </>
    );
  }

  const nomeTipo = gruppo ? "Messaggio nel gruppo" : TIPI.find((t) => t.id === tipo)?.titolo;
  return (
    <form onSubmit={(e) => { e.preventDefault(); invia(false); }} className="mx-auto max-w-2xl space-y-5">
      <Titolo titolo={nomeTipo || "Pubblica"} azioni={!gruppo && (
        <button type="button" className="btn-secondario" onClick={() => setTipo("")}>Cambia tipo</button>)} />

      {tipo === "aiuto" && <Avviso tipo="info">Le richieste di aiuto sono per piccole cose. Per un&apos;emergenza chiama il <strong>112</strong>.</Avviso>}
      {tipo === "segnalazione" && <Avviso tipo="attenzione">Neighbour non sostituisce i canali di emergenza: se c&apos;è un pericolo immediato chiama il <strong>112</strong>.</Avviso>}

      <div className="scheda space-y-4">
        <div>
          <label htmlFor="titolo" className="etichetta">Titolo</label>
          <input id="titolo" className="campo" required minLength={3} maxLength={120} value={f.titolo} onChange={aggiorna("titolo")}
            placeholder={tipo === "aiuto" ? "Es. Spesa del giovedì" : tipo === "segnalazione" ? "Es. Lampione spento" : "Es. Passeggino leggero"} />
        </div>
        <div>
          <label htmlFor="descrizione" className="etichetta">Descrizione</label>
          <textarea id="descrizione" className="campo" required minLength={3} maxLength={2000} rows={4} value={f.descrizione}
            onChange={aggiorna("descrizione")} aria-describedby="nota-privacy" />
          <p id="nota-privacy" className="mt-1 text-xs text-tenue">Non scrivere telefono, email o indirizzo: li condividerai in chat con chi ti contatta.</p>
        </div>
        {!gruppo && (
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="text-sm"><span className="font-medium">Categoria</span>
              <select className="campo mt-1" required value={f.categoria} onChange={aggiorna("categoria")}>
                <option value="" disabled>Scegli…</option>
                {Object.entries(categorie).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
            </label>
            {["offerta", "scambio", "regalo", "richiesta"].includes(tipo) && (
              <label className="text-sm"><span className="font-medium">{tipo === "richiesta" ? "Budget (facoltativo)" : "Prezzo (facoltativo)"}</span>
                <input className="campo mt-1" maxLength={40} placeholder={tipo === "regalo" ? "Gratis" : "Es. 10 €"} value={f.prezzo} onChange={aggiorna("prezzo")} />
              </label>
            )}
            {tipo === "aiuto" && (
              <label className="text-sm"><span className="font-medium">Quando sei disponibile</span>
                <input className="campo mt-1" required maxLength={200} placeholder="Es. giovedì mattina" value={f.disponibilita} onChange={aggiorna("disponibilita")} />
              </label>
            )}
            <label className="text-sm"><span className="font-medium">{tipo === "aiuto" ? "Entro quando" : "Valido fino al (facoltativo)"}</span>
              <input type="datetime-local" className="campo mt-1" required={tipo === "aiuto"} value={f.scadenza} onChange={aggiorna("scadenza")} />
            </label>
          </div>
        )}
      </div>

      <div className="scheda space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="font-semibold">Foto <span className="text-sm font-normal text-tenue">(fino a 4)</span></h2>
          <button type="button" className="btn-secondario" onClick={() => fileRef.current?.click()} disabled={caricando || foto.length >= 4}>
            <Camera className="h-4 w-4" /> {caricando ? "Caricamento…" : "Aggiungi"}
          </button>
          <input ref={fileRef} type="file" accept="image/jpeg,image/png,image/webp,image/gif" multiple className="sr-only"
            onChange={(e) => aggiungiFoto(e.target.files)} aria-label="Scegli le foto" />
        </div>
        {foto.length > 0 ? (
          <ul className="flex flex-wrap gap-2">
            {foto.map((x) => (
              <li key={x.id} className="relative">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={x.url} alt="" className="h-24 w-24 rounded-xl object-cover" />
                <button type="button" className="absolute -right-2 -top-2 rounded-full bg-errore p-1 text-white" aria-label="Togli la foto"
                  onClick={() => setFoto(foto.filter((y) => y.id !== x.id))}><X className="h-3 w-3" /></button>
              </li>
            ))}
          </ul>
        ) : <p className="text-sm text-tenue">Le foto vengono ripulite dai metadati, compresa la posizione GPS dello scatto.</p>}
      </div>

      {!gruppo && (
        <div className="scheda space-y-4">
          <Raggio valore={raggio} onChange={setRaggio} etichetta="Chi lo vede: vicini entro" />
          <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
            <span className="flex items-center gap-1 text-tenue"><MapPin className="h-4 w-4" />
              {luogo ? "Zona scelta sulla mappa (circa 150 m)" : "Zona: il tuo quartiere (approssimata)"}</span>
            <button type="button" className="btn-secondario" onClick={() => setMostraMappa(!mostraMappa)}>
              {mostraMappa ? "Nascondi mappa" : "Indica una zona diversa"}</button>
          </div>
          {mostraMappa && utente?.area && (
            <Mappa centro={luogo || utente.area} raggio={raggio} selezione={luogo} onClic={(lat, lon) => setLuogo({ lat, lon })} altezza={320} />
          )}
        </div>
      )}

      {errore && <Avviso tipo="errore">{errore}</Avviso>}
      <div className="flex flex-wrap justify-end gap-2">
        {!gruppo && (
          <button type="button" className="btn-secondario text-errore" onClick={() => {
            try { localStorage.removeItem(BOZZA); } catch {}
            setF({ titolo: "", descrizione: "", categoria: "", prezzo: "", disponibilita: "", scadenza: "" }); setTipo(""); setFoto([]);
          }}><Trash2 className="h-4 w-4" /> Annulla</button>
        )}
        {!gruppo && <button type="button" className="btn-secondario" disabled={inCorso} onClick={() => invia(true)}>Salva bozza</button>}
        <button className="btn-primario" disabled={inCorso || caricando}>{inCorso ? "Pubblicazione…" : "Pubblica"}</button>
      </div>
    </form>
  );
}

export default function Pubblica() {
  return <Suspense fallback={<Caricamento />}><Modulo /></Suspense>;
}
