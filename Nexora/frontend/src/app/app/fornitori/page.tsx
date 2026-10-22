"use client";
// Fornitori: tempi di consegna, prodotti forniti e spesa. © Roberto Di Flumeri
import { Building, Plus } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import FormAnagrafica from "@/components/FormAnagrafica";
import { Caricamento, Errore, Titolo, useDati, Vuoto } from "@/components/ui";
import { euro } from "@/lib/formato";
import { useSessione } from "@/lib/sessione";

export default function PaginaFornitori() {
  const { puo } = useSessione();
  const router = useRouter();
  const [nuovo, setNuovo] = useState(false);
  const [q, setQ] = useState("");
  const { dati, errore, ricarica } = useDati<any[]>("/api/fornitori");
  const lista = (dati ?? []).filter((f) => !q || `${f.ragioneSociale} ${f.citta ?? ""} ${f.partitaIva ?? ""}`.toLowerCase().includes(q.toLowerCase()));

  return (
    <>
      <Titolo titolo="Fornitori" sottotitolo="Anagrafiche, tempi di consegna usati dal riordino automatico e spesa per fornitore"
        azioni={puo("fornitori") && <button className="btn-primario" onClick={() => setNuovo(true)}><Plus className="h-4 w-4" /> Nuovo fornitore</button>} />
      <div className="scheda mb-4 p-4"><input className="campo" placeholder="Cerca fornitore…" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Cerca fornitori" /></div>
      {errore && <Errore messaggio={errore} riprova={ricarica} />}
      {!dati && !errore && <Caricamento />}
      {dati && !lista.length && <div className="scheda"><Vuoto icona={Building} titolo="Nessun fornitore" /></div>}
      {lista.length > 0 && (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {lista.map((f) => (
            <Link key={f.id} href={`/app/fornitori/${f.id}`} className="scheda block transition hover:-translate-y-0.5 hover:border-primario/40">
              <div className="flex items-start gap-3">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl gradiente text-white"><Building className="h-5 w-5" /></span>
                <div className="min-w-0">
                  <p className="truncate font-semibold">{f.ragioneSociale}</p>
                  <p className="text-sm text-tenue">{f.citta ?? "—"}{f.provincia ? ` (${f.provincia})` : ""} · P.IVA {f.partitaIva ?? "—"}</p>
                </div>
              </div>
              <dl className="mt-4 grid grid-cols-3 gap-2 text-center text-sm">
                <div className="rounded-xl bg-fondo p-2"><dt className="text-xs text-tenue">Consegna</dt><dd className="font-semibold">{f.leadTimeGiorni} gg</dd></div>
                <div className="rounded-xl bg-fondo p-2"><dt className="text-xs text-tenue">Prodotti</dt><dd className="font-semibold">{f.prodotti}</dd></div>
                <div className="rounded-xl bg-fondo p-2"><dt className="text-xs text-tenue">Ordinato</dt><dd className="font-semibold">{euro(f.speso, true)}</dd></div>
              </dl>
            </Link>
          ))}
        </div>
      )}
      {nuovo && <FormAnagrafica tipo="fornitore" onChiudi={() => setNuovo(false)} onSalvato={(f) => router.push(`/app/fornitori/${f.id}`)} />}
    </>
  );
}
