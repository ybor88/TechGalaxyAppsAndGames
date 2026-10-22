"use client";
// Nuovo documento: preventivo, DDT, fattura o nota di credito, con righe da catalogo o libere. © Roberto Di Flumeri
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import EditorRighe, { type Riga } from "@/components/EditorRighe";
import SceltaCliente, { type ClienteBreve } from "@/components/SceltaCliente";
import { Avviso, Campo, NOMI_TIPO_DOC, Titolo, useAzione } from "@/components/ui";
import { api } from "@/lib/api";
import { aCentesimi } from "@/lib/formato";

type Tipo = "preventivo" | "ddt" | "fattura" | "nota_credito";

export default function NuovoDocumento() {
  const router = useRouter();
  const [tipo, setTipo] = useState<Tipo>("preventivo");
  const [cliente, setCliente] = useState<ClienteBreve | null>(null);
  const [righe, setRighe] = useState<Riga[]>([]);
  const [note, setNote] = useState("");
  const [validoFino, setValidoFino] = useState(new Date(Date.now() + 30 * 86400_000).toISOString().slice(0, 10));
  const [ddt, setDdt] = useState({ causale: "Vendita", colli: "1", peso: "" });
  const { inCorso, errore, esegui } = useAzione();

  useEffect(() => {
    const p = new URLSearchParams(window.location.search);
    if (p.get("tipo")) setTipo(p.get("tipo") as Tipo);
    if (p.get("clienteId")) api(`/api/clienti/${p.get("clienteId")}`).then(setCliente).catch(() => undefined);
  }, []);

  const salva = (e: React.FormEvent) => {
    e.preventDefault();
    esegui(async () => {
      if (!cliente) throw new Error("Scegli il cliente");
      if (!righe.length) throw new Error("Aggiungi almeno una riga");
      const d = await api("/api/documenti", { metodo: "POST", corpo: {
        tipo, clienteId: cliente.id, note: note || null,
        ...(tipo === "preventivo" ? { validoFino } : {}),
        ...(tipo === "ddt" ? { causaleTrasporto: ddt.causale, colli: Number(ddt.colli) || null, pesoKg: ddt.peso ? Number(ddt.peso.replace(",", ".")) : null } : {}),
        righe: righe.map((r) => ({ varianteId: r.varianteId, descrizione: r.descrizione, quantita: Number(r.quantita.replace(",", ".")), unita: r.unita,
          prezzoUnitario: aCentesimi(r.prezzo) ?? 0, sconto: Number(r.sconto.replace(",", ".")) || 0, aliquotaIva: r.aliquotaIva, naturaIva: r.aliquotaIva === 0 ? r.naturaIva || null : null })),
      } });
      router.replace(`/app/documenti/${d.id}`);
    });
  };

  return (
    <>
      <Titolo titolo={`Nuovo ${NOMI_TIPO_DOC[tipo].toLowerCase()}`} indietro={<Link href={`/app/documenti?tipo=${tipo}`} className="link">← Documenti</Link>}
        sottotitolo="Il documento nasce in bozza: il numero progressivo si assegna all'emissione" />
      <form onSubmit={salva} className="space-y-6">
        <section className="scheda grid gap-4 md:grid-cols-3">
          <Campo etichetta="Tipo di documento">
            <select className="campo" value={tipo} onChange={(e) => setTipo(e.target.value as Tipo)}>
              {Object.entries(NOMI_TIPO_DOC).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </Campo>
          <Campo etichetta="Cliente" className="md:col-span-2"><SceltaCliente valore={cliente} onScelto={setCliente} /></Campo>
          {tipo === "preventivo" && <Campo etichetta="Valido fino al"><input className="campo" type="date" value={validoFino} onChange={(e) => setValidoFino(e.target.value)} /></Campo>}
          {tipo === "ddt" && <>
            <Campo etichetta="Causale del trasporto"><input className="campo" value={ddt.causale} onChange={(e) => setDdt({ ...ddt, causale: e.target.value })} /></Campo>
            <Campo etichetta="Colli"><input className="campo" type="number" min={1} value={ddt.colli} onChange={(e) => setDdt({ ...ddt, colli: e.target.value })} /></Campo>
            <Campo etichetta="Peso (kg)"><input className="campo" value={ddt.peso} onChange={(e) => setDdt({ ...ddt, peso: e.target.value })} inputMode="decimal" /></Campo>
          </>}
          <Campo etichetta="Note" className={tipo === "ddt" || tipo === "preventivo" ? "md:col-span-3" : "md:col-span-3"}><textarea className="campo" rows={2} value={note} onChange={(e) => setNote(e.target.value)} maxLength={1000} /></Campo>
        </section>
        <section className="scheda">
          <h2 className="mb-3 font-semibold">Righe</h2>
          <EditorRighe righe={righe} onChange={setRighe} modo="documento" sconto={cliente?.scontoPercentuale ?? 0} />
        </section>
        {tipo === "nota_credito" && <Avviso tipo="info">Per stornare una fattura esistente è più rapido aprirla e usare «Nota di credito»: le righe e il riferimento alla fattura vengono compilati in automatico.</Avviso>}
        {errore && <Avviso tipo="errore">{errore}</Avviso>}
        <div className="flex justify-end"><button className="btn-primario" disabled={inCorso}>Salva in bozza</button></div>
      </form>
    </>
  );
}
