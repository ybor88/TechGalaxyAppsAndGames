"use client";
// Nuovo ordine di acquisto al fornitore. © Roberto Di Flumeri
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import EditorRighe, { type Riga } from "@/components/EditorRighe";
import { Avviso, Campo, Titolo, useAzione, useDati } from "@/components/ui";
import { api, type Magazzino } from "@/lib/api";
import { aCentesimi } from "@/lib/formato";

export default function NuovoAcquisto() {
  const router = useRouter();
  const { dati: fornitori } = useDati<any[]>("/api/fornitori");
  const { dati: magazzini } = useDati<Magazzino[]>("/api/magazzini");
  const [fornitoreId, setFornitoreId] = useState("");
  const [magazzinoId, setMagazzinoId] = useState("");
  const [righe, setRighe] = useState<Riga[]>([]);
  const [note, setNote] = useState("");
  const [invia, setInvia] = useState(true);
  const { inCorso, errore, esegui } = useAzione();

  useEffect(() => { const f = new URLSearchParams(window.location.search).get("fornitoreId"); if (f) setFornitoreId(f); }, []);
  useEffect(() => { if (magazzini && !magazzinoId) setMagazzinoId(magazzini.find((m) => m.principale)?.id ?? ""); }, [magazzini, magazzinoId]);
  const fornitore = fornitori?.find((f) => f.id === fornitoreId);

  const salva = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => {
      if (!righe.length) throw new Error("Aggiungi almeno un articolo");
      const o = await api("/api/acquisti", { metodo: "POST", corpo: { fornitoreId, magazzinoId, note: note || null,
        righe: righe.map((r) => ({ varianteId: r.varianteId, quantita: Number(r.quantita.replace(",", ".")), costoUnitario: aCentesimi(r.prezzo) ?? 0 })) } });
      if (invia) await api(`/api/acquisti/${o.id}/invia`, { metodo: "POST" });
      router.replace(`/app/acquisti/${o.id}`);
    });
  };

  return (
    <>
      <Titolo titolo="Nuovo ordine di acquisto" indietro={<Link href="/app/acquisti" className="link">← Acquisti</Link>} />
      <form onSubmit={salva} className="space-y-6">
        <section className="scheda grid gap-4 md:grid-cols-3">
          <Campo etichetta="Fornitore" aiuto={fornitore ? `Consegna in circa ${fornitore.leadTimeGiorni} giorni · pagamento a ${fornitore.giorniPagamento} giorni` : undefined}>
            <select className="campo" value={fornitoreId} onChange={(e) => setFornitoreId(e.target.value)} required>
              <option value="">Scegli…</option>{fornitori?.map((f) => <option key={f.id} value={f.id}>{f.ragioneSociale}</option>)}
            </select>
          </Campo>
          <Campo etichetta="Magazzino di destinazione">
            <select className="campo" value={magazzinoId} onChange={(e) => setMagazzinoId(e.target.value)}>
              {magazzini?.map((m) => <option key={m.id} value={m.id}>{m.nome}</option>)}
            </select>
          </Campo>
          <Campo etichetta="Note per il fornitore"><input className="campo" value={note} onChange={(e) => setNote(e.target.value)} maxLength={1000} /></Campo>
        </section>
        <section className="scheda">
          <h2 className="mb-3 font-semibold">Articoli da ordinare</h2>
          <EditorRighe righe={righe} onChange={setRighe} modo="acquisto" magazzinoId={magazzinoId} />
        </section>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={invia} onChange={(e) => setInvia(e.target.checked)} /> Segna subito come inviato al fornitore</label>
        {errore && <Avviso tipo="errore">{errore}</Avviso>}
        <div className="flex justify-end"><button className="btn-primario" disabled={inCorso}>Crea ordine di acquisto</button></div>
      </form>
    </>
  );
}
