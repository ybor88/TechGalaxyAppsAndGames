"use client";
// Avatar condiviso: area pubblica per l'utente invitato — © Roberto Di Flumeri
import { Flag, Send } from "lucide-react";
import Image from "next/image";
import { use, useEffect, useState } from "react";
import { Avviso, CambiaTema, Caricamento, Confidenza, Copyright, EtichettaGenerata } from "@/components/ui";
import { api, dataOraIt } from "@/lib/api";

interface Info {
  nome: string; proprietario: string | null; categorie: string[]; domande_rimaste: number; scadenza: string;
  attiva: boolean; motivo: string | null; watermark: string;
}
interface Scambio { domanda: string; risposta: string; confidenza: string; avvisi: string[] }

export default function AvatarCondiviso({ params }: { params: Promise<{ token: string }> }) {
  const { token } = use(params);
  const [info, setInfo] = useState<Info | null>(null);
  const [errore, setErrore] = useState<string | null>(null);
  const [scambi, setScambi] = useState<Scambio[]>([]);
  const [domanda, setDomanda] = useState("");
  const [inAttesa, setInAttesa] = useState(false);
  const [segnala, setSegnala] = useState(false);
  const [motivo, setMotivo] = useState("contenuto_offensivo");
  const [dettagli, setDettagli] = useState("");
  const [segnalato, setSegnalato] = useState(false);

  useEffect(() => {
    api<Info>(`/v1/public/shares/${encodeURIComponent(token)}`, { pubblica: true }).then(setInfo).catch((e) => setErrore(e.message));
  }, [token]);

  const invia = async (e: React.FormEvent) => {
    e.preventDefault();
    const q = domanda.trim();
    if (!q || inAttesa) return;
    setInAttesa(true);
    setErrore(null);
    try {
      const r = await api<{ risposta: string; confidenza: string; avvisi: string[]; domande_rimaste: number }>(
        `/v1/public/shares/${encodeURIComponent(token)}/chat`, { metodo: "POST", corpo: { domanda: q }, pubblica: true });
      setScambi((s) => [...s, { domanda: q, risposta: r.risposta, confidenza: r.confidenza, avvisi: r.avvisi }]);
      setInfo((i) => (i ? { ...i, domande_rimaste: r.domande_rimaste, attiva: r.domande_rimaste > 0 } : i));
      setDomanda("");
    } catch (err) {
      setErrore(err instanceof Error ? err.message : "Errore");
    } finally {
      setInAttesa(false);
    }
  };

  const inviaSegnalazione = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api(`/v1/public/shares/${encodeURIComponent(token)}/report`, { metodo: "POST", corpo: { motivo, dettagli: dettagli || null }, pubblica: true });
      setSegnalato(true);
      setSegnala(false);
    } catch (err) {
      setErrore(err instanceof Error ? err.message : "Errore");
    }
  };

  return (
    <div className="mx-auto flex min-h-screen max-w-2xl flex-col px-4 py-6">
      <header className="mb-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Image src="/logo.png" alt="" width={44} height={44} className="rounded-xl" />
          <div>
            <h1 className="font-bold">{info ? `Avatar digitale di ${info.proprietario ?? "un utente"}` : "Avatar condiviso"}</h1>
            <p className="text-xs text-tenue">Ghost Me · {info?.nome}</p>
          </div>
        </div>
        <CambiaTema />
      </header>

      {!info && !errore && <Caricamento />}
      {info && (
        <>
          <Avviso tipo="info">
            {info.watermark}. Puoi chiedere su: {info.categorie.join(", ")}. Domande rimaste: <strong>{info.domande_rimaste}</strong> · scade il {dataOraIt(info.scadenza)}.
          </Avviso>
          {!info.attiva && info.motivo && <div className="mt-3"><Avviso tipo="attenzione">{info.motivo}</Avviso></div>}

          <main className="my-4 flex-1 space-y-4" aria-live="polite">
            {scambi.map((s, i) => (
              <div key={i} className="space-y-2">
                <div className="flex justify-end"><div className="gradiente max-w-[85%] rounded-2xl px-4 py-2 text-white">{s.domanda}</div></div>
                <div className="max-w-[85%] rounded-2xl border border-bordo bg-pannello px-4 py-3">
                  <div className="mb-2 flex gap-2"><EtichettaGenerata /><Confidenza livello={s.confidenza} /></div>
                  <p className="whitespace-pre-wrap">{s.risposta}</p>
                  {s.avvisi.map((a) => <p key={a} className="mt-2 text-xs text-attenzione">{a}</p>)}
                  <p className="mt-2 text-[11px] uppercase tracking-wide text-tenue">Risposta generata · non è la persona reale</p>
                </div>
              </div>
            ))}
            {inAttesa && <Caricamento testo="L'avatar sta rispondendo…" />}
          </main>

          {errore && <div className="mb-3"><Avviso tipo="errore">{errore}</Avviso></div>}
          {info.attiva && (
            <form onSubmit={invia} className="flex gap-2">
              <label htmlFor="domanda" className="sr-only">Domanda</label>
              <input id="domanda" className="campo" maxLength={500} placeholder="Fai una domanda all'avatar…" value={domanda}
                onChange={(e) => setDomanda(e.target.value)} disabled={inAttesa} />
              <button className="btn-primario" disabled={inAttesa || !domanda.trim()} aria-label="Invia"><Send className="h-4 w-4" /></button>
            </form>
          )}

          <div className="mt-6">
            {segnalato ? <Avviso tipo="ok">Segnalazione inviata alla moderazione. Grazie.</Avviso> : segnala ? (
              <form onSubmit={inviaSegnalazione} className="scheda space-y-2">
                <label className="text-sm">Motivo
                  <select className="campo mt-1" value={motivo} onChange={(e) => setMotivo(e.target.value)}>
                    <option value="contenuto_offensivo">Contenuto offensivo</option>
                    <option value="imitazione_terzi">Imita un&apos;altra persona</option>
                    <option value="dati_altrui">Contiene dati di altre persone</option>
                    <option value="spam">Spam</option><option value="altro">Altro</option>
                  </select>
                </label>
                <textarea className="campo" rows={2} maxLength={1000} placeholder="Dettagli (facoltativi)" aria-label="Dettagli"
                  value={dettagli} onChange={(e) => setDettagli(e.target.value)} />
                <div className="flex justify-end gap-2">
                  <button type="button" className="btn-secondario" onClick={() => setSegnala(false)}>Annulla</button>
                  <button className="btn-pericolo">Invia segnalazione</button>
                </div>
              </form>
            ) : (
              <button className="text-sm text-tenue underline" onClick={() => setSegnala(true)}><Flag className="mr-1 inline h-3 w-3" />Segnala questo avatar</button>
            )}
          </div>
        </>
      )}
      {!info && errore && <Avviso tipo="errore">{errore}</Avviso>}
      <footer className="mt-8"><Copyright /></footer>
    </div>
  );
}
