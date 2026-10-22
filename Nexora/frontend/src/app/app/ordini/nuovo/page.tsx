"use client";
// Nuovo ordine di vendita: cliente, magazzino di evasione, righe da catalogo (anche con scansione barcode). © Roberto Di Flumeri
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import EditorRighe, { type Riga } from "@/components/EditorRighe";
import SceltaCliente, { type ClienteBreve } from "@/components/SceltaCliente";
import { Avviso, Campo, Titolo, useAzione, useDati } from "@/components/ui";
import { api, type Magazzino } from "@/lib/api";
import { aCentesimi } from "@/lib/formato";

export default function NuovoOrdine() {
  const router = useRouter();
  const [cliente, setCliente] = useState<ClienteBreve | null>(null);
  const [magazzinoId, setMagazzinoId] = useState("");
  const [righe, setRighe] = useState<Riga[]>([]);
  const [note, setNote] = useState("");
  const [consegna, setConsegna] = useState("");
  const [conferma, setConferma] = useState(true);
  const { dati: magazzini } = useDati<Magazzino[]>("/api/magazzini");
  const { inCorso, errore, esegui } = useAzione();

  useEffect(() => { if (magazzini && !magazzinoId) setMagazzinoId(magazzini.find((m) => m.principale)?.id ?? magazzini[0]?.id ?? ""); }, [magazzini, magazzinoId]);
  useEffect(() => {
    const id = new URLSearchParams(window.location.search).get("clienteId");
    if (id) api(`/api/clienti/${id}`).then(setCliente).catch(() => undefined);
  }, []);

  const salva = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => {
      if (!cliente) throw new Error("Scegli il cliente");
      if (!righe.length) throw new Error("Aggiungi almeno un articolo");
      const o = await api("/api/ordini", { metodo: "POST", corpo: {
        clienteId: cliente.id, magazzinoId, note: note || null, dataConsegnaPrevista: consegna || null,
        righe: righe.map((r) => ({ varianteId: r.varianteId, quantita: Number(r.quantita.replace(",", ".")), prezzoUnitario: aCentesimi(r.prezzo) ?? 0, sconto: Number(r.sconto.replace(",", ".")) || 0 })),
      } });
      // la conferma impegna subito la merce: se non basta, l'ordine resta in bozza e lo si dice chiaramente
      if (conferma) {
        try { await api(`/api/ordini/${o.id}/stato`, { metodo: "POST", corpo: { stato: "confermato" } }); }
        catch (err) { router.replace(`/app/ordini/${o.id}?avviso=${encodeURIComponent(err instanceof Error ? err.message : "Conferma non riuscita")}`); return; }
      }
      router.replace(`/app/ordini/${o.id}`);
    });
  };

  return (
    <>
      <Titolo titolo="Nuovo ordine di vendita" indietro={<Link href="/app/ordini" className="link">← Ordini</Link>} />
      <form onSubmit={salva} className="space-y-6">
        <section className="scheda grid gap-4 md:grid-cols-3">
          <Campo etichetta="Cliente" className="md:col-span-2"><SceltaCliente valore={cliente} onScelto={setCliente} /></Campo>
          <Campo etichetta="Magazzino di evasione">
            <select className="campo" value={magazzinoId} onChange={(e) => setMagazzinoId(e.target.value)}>
              {magazzini?.filter((m) => m.attivo).map((m) => <option key={m.id} value={m.id}>{m.nome}</option>)}
            </select>
          </Campo>
          <Campo etichetta="Consegna prevista"><input className="campo" type="date" value={consegna} onChange={(e) => setConsegna(e.target.value)} /></Campo>
          <Campo etichetta="Note" className="md:col-span-2"><input className="campo" value={note} onChange={(e) => setNote(e.target.value)} maxLength={1000} /></Campo>
        </section>
        <section className="scheda">
          <h2 className="mb-3 font-semibold">Articoli</h2>
          <EditorRighe righe={righe} onChange={setRighe} modo="ordine" magazzinoId={magazzinoId} sconto={cliente?.scontoPercentuale ?? 0} />
          {!righe.length && <p className="mt-3 text-sm text-tenue">Cerca un articolo o scansiona il barcode: ogni scansione dello stesso codice aumenta la quantità.</p>}
        </section>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={conferma} onChange={(e) => setConferma(e.target.checked)} /> Conferma subito l&apos;ordine (impegna la merce in magazzino)</label>
        {errore && <Avviso tipo="errore">{errore}</Avviso>}
        <div className="flex justify-end"><button className="btn-primario" disabled={inCorso}>Crea ordine</button></div>
      </form>
    </>
  );
}
