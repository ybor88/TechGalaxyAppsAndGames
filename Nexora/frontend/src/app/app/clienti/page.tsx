"use client";
// CRM clienti: ricerca, fatturato, crediti aperti e ultimo ordine. © Roberto Di Flumeri
import { Plus, Users } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import FormAnagrafica from "@/components/FormAnagrafica";
import { Canale, Caricamento, Errore, Titolo, useDati, useLimite, Vuoto } from "@/components/ui";
import { qs } from "@/lib/api";
import { data, euro } from "@/lib/formato";
import { useSessione } from "@/lib/sessione";

export default function PaginaClienti() {
  const { puo } = useSessione();
  const router = useRouter();
  const [q, setQ] = useState("");
  const [tipo, setTipo] = useState("");
  const [ordina, setOrdina] = useState<"nome" | "fatturato" | "credito">("fatturato");
  const [nuovo, setNuovo] = useState(false);
  const { dati, errore, ricarica } = useDati<any[]>(`/api/clienti${qs({ q })}`);
  const lista = (dati ?? []).filter((c) => !tipo || c.tipo === tipo).sort((a, b) =>
    ordina === "nome" ? a.ragioneSociale.localeCompare(b.ragioneSociale) : ordina === "fatturato" ? b.fatturato - a.fatturato : b.creditoAperto - a.creditoAperto);
  const { visibili, Altri } = useLimite(lista, 50);
  const totali = { fatturato: lista.reduce((s, c) => s + c.fatturato, 0), credito: lista.reduce((s, c) => s + c.creditoAperto, 0) };

  return (
    <>
      <Titolo titolo="Clienti" sottotitolo={dati ? `${lista.length} clienti · fatturato ${euro(totali.fatturato, true)} · crediti aperti ${euro(totali.credito, true)}` : "Anagrafiche, storico ordini e fatture"}
        azioni={puo("clienti") && <button className="btn-primario" onClick={() => setNuovo(true)}><Plus className="h-4 w-4" /> Nuovo cliente</button>} />
      <div className="scheda mb-4 grid gap-3 p-4 md:grid-cols-[2fr_1fr_1fr]">
        <input className="campo" placeholder="Cerca per nome, P.IVA, email o città…" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Cerca clienti" />
        <select className="campo" value={tipo} onChange={(e) => setTipo(e.target.value)} aria-label="Tipo cliente">
          <option value="">Tutti i tipi</option><option value="azienda">Aziende</option><option value="privato">Privati</option><option value="pa">Pubblica amministrazione</option>
        </select>
        <select className="campo" value={ordina} onChange={(e) => setOrdina(e.target.value as typeof ordina)} aria-label="Ordina per">
          <option value="fatturato">Ordina per fatturato</option><option value="credito">Ordina per credito aperto</option><option value="nome">Ordina per nome</option>
        </select>
      </div>
      {errore && <Errore messaggio={errore} riprova={ricarica} />}
      {!dati && !errore && <Caricamento />}
      {dati && !lista.length && <div className="scheda"><Vuoto icona={Users} titolo="Nessun cliente" testo="Aggiungi il primo cliente o modifica la ricerca." /></div>}
      {lista.length > 0 && (
        <div className="scheda overflow-x-auto p-0">
          <table className="tabella">
            <thead><tr><th>Cliente</th><th>Città</th><th>Canale</th><th className="num">Ordini</th><th className="num">Fatturato</th><th className="num">Credito aperto</th><th>Ultimo ordine</th></tr></thead>
            <tbody>
              {visibili.map((c) => (
                <tr key={c.id} className="cursor-pointer" onClick={() => router.push(`/app/clienti/${c.id}`)}>
                  <td>
                    <Link href={`/app/clienti/${c.id}`} className="font-medium hover:underline" onClick={(e) => e.stopPropagation()}>{c.ragioneSociale}</Link>
                    <p className="text-xs text-tenue">{c.tipo === "privato" ? `CF ${c.codiceFiscale ?? "—"}` : `P.IVA ${c.partitaIva ?? "—"}`}{c.tipo === "pa" ? " · PA" : ""}</p>
                  </td>
                  <td>{c.citta ?? "—"}{c.provincia ? ` (${c.provincia})` : ""}</td>
                  <td><Canale canale={c.canale} /></td>
                  <td className="num">{c.ordini}</td>
                  <td className="num font-medium">{euro(c.fatturato)}</td>
                  <td className={`num ${c.creditoAperto ? "font-medium text-attenzione" : "text-tenue"}`}>{euro(c.creditoAperto)}</td>
                  <td className="text-tenue">{data(c.ultimoOrdine)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Altri />
      {nuovo && <FormAnagrafica tipo="cliente" onChiudi={() => setNuovo(false)} onSalvato={(c) => router.push(`/app/clienti/${c.id}`)} />}
    </>
  );
}
