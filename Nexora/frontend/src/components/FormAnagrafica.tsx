"use client";
// Anagrafica cliente o fornitore, con dati fiscali italiani (P.IVA, codice fiscale, codice SDI, PEC). © Roberto Di Flumeri
import { useState } from "react";
import { api } from "@/lib/api";
import { Avviso, Campo, Modale, useAzione } from "./ui";

type Tipo = "cliente" | "fornitore";

export default function FormAnagrafica({ tipo, iniziale, onChiudi, onSalvato }: { tipo: Tipo; iniziale?: Record<string, any>; onChiudi: () => void; onSalvato: (r: any) => void }) {
  const [d, setD] = useState<Record<string, any>>({
    tipo: "azienda", ragioneSociale: "", partitaIva: "", codiceFiscale: "", codiceDestinatario: "", pec: "", email: "", telefono: "", indirizzo: "", cap: "",
    citta: "", provincia: "", nazione: "IT", scontoPercentuale: 0, giorniPagamento: 30, leadTimeGiorni: 7, iban: "", note: "",
    ...Object.fromEntries(Object.entries(iniziale ?? {}).map(([k, v]) => [k, v ?? ""])),
  });
  const { inCorso, errore, esegui } = useAzione();
  const set = (k: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => setD({ ...d, [k]: e.target.value });
  const cliente = tipo === "cliente";

  const salva = (e: React.FormEvent) => {
    e.preventDefault();
    const comuni = ["ragioneSociale", "partitaIva", "codiceFiscale", "email", "telefono", "indirizzo", "cap", "citta", "provincia", "nazione", "note"];
    const campi = cliente ? [...comuni, "tipo", "codiceDestinatario", "pec"] : [...comuni, "iban"];
    const corpo: Record<string, unknown> = Object.fromEntries(campi.map((k) => [k, typeof d[k] === "string" ? d[k].trim() || null : d[k]]));
    corpo.nazione = (d.nazione || "IT").toUpperCase();
    corpo.giorniPagamento = Number(d.giorniPagamento) || 0;
    if (cliente) corpo.scontoPercentuale = Number(String(d.scontoPercentuale).replace(",", ".")) || 0;
    else corpo.leadTimeGiorni = Number(d.leadTimeGiorni) || 0;
    if (corpo.partitaIva) corpo.partitaIva = String(corpo.partitaIva).replace(/^IT/i, "").replace(/\s/g, "");
    if (corpo.codiceFiscale) corpo.codiceFiscale = String(corpo.codiceFiscale).toUpperCase();
    const base = cliente ? "/api/clienti" : "/api/fornitori";
    esegui(async () => onSalvato(iniziale?.id ? await api(`${base}/${iniziale.id}`, { metodo: "PATCH", corpo }) : await api(base, { metodo: "POST", corpo })));
  };

  return (
    <Modale titolo={iniziale?.id ? `Modifica ${cliente ? "cliente" : "fornitore"}` : `Nuovo ${cliente ? "cliente" : "fornitore"}`} onChiudi={onChiudi} larga>
      <form onSubmit={salva} className="grid gap-3 sm:grid-cols-6">
        {cliente && (
          <Campo etichetta="Tipo" className="sm:col-span-2">
            <select className="campo" value={d.tipo} onChange={set("tipo")}>
              <option value="azienda">Azienda / professionista</option><option value="privato">Privato</option><option value="pa">Pubblica amministrazione</option>
            </select>
          </Campo>
        )}
        <Campo etichetta={d.tipo === "privato" && cliente ? "Nome e cognome" : "Ragione sociale"} className={cliente ? "sm:col-span-4" : "sm:col-span-6"}>
          <input className="campo" value={d.ragioneSociale} onChange={set("ragioneSociale")} required minLength={2} maxLength={120} />
        </Campo>
        <Campo etichetta="Partita IVA" className="sm:col-span-2" aiuto={cliente && d.tipo === "azienda" ? "Obbligatoria per le aziende" : undefined}>
          <input className="campo font-mono" value={d.partitaIva} onChange={set("partitaIva")} inputMode="numeric" required={cliente && d.tipo === "azienda"} />
        </Campo>
        <Campo etichetta="Codice fiscale" className="sm:col-span-2"><input className="campo font-mono uppercase" value={d.codiceFiscale} onChange={set("codiceFiscale")} maxLength={16} /></Campo>
        {cliente ? (
          <Campo etichetta="Codice destinatario SDI" className="sm:col-span-2" aiuto="7 caratteri (6 per la PA); vuoto = 0000000">
            <input className="campo font-mono uppercase" value={d.codiceDestinatario} onChange={set("codiceDestinatario")} maxLength={7} />
          </Campo>
        ) : (
          <Campo etichetta="IBAN" className="sm:col-span-2"><input className="campo font-mono uppercase" value={d.iban} onChange={set("iban")} maxLength={34} /></Campo>
        )}
        <Campo etichetta="Email" className="sm:col-span-2"><input className="campo" type="email" value={d.email} onChange={set("email")} /></Campo>
        {cliente && <Campo etichetta="PEC" className="sm:col-span-2"><input className="campo" type="email" value={d.pec} onChange={set("pec")} /></Campo>}
        <Campo etichetta="Telefono" className="sm:col-span-2"><input className="campo" value={d.telefono} onChange={set("telefono")} type="tel" /></Campo>
        <Campo etichetta="Indirizzo" className="sm:col-span-3"><input className="campo" value={d.indirizzo} onChange={set("indirizzo")} /></Campo>
        <Campo etichetta="CAP"><input className="campo" value={d.cap} onChange={set("cap")} pattern="\d{5}" inputMode="numeric" /></Campo>
        <Campo etichetta="Città"><input className="campo" value={d.citta} onChange={set("citta")} /></Campo>
        <Campo etichetta="Prov."><input className="campo uppercase" value={d.provincia} onChange={set("provincia")} maxLength={2} /></Campo>
        <Campo etichetta="Nazione" aiuto="Codice ISO (IT, FR…)"><input className="campo uppercase" value={d.nazione} onChange={set("nazione")} maxLength={2} minLength={2} /></Campo>
        <Campo etichetta="Pagamento a (giorni)" className="sm:col-span-2"><input className="campo" type="number" min={0} max={365} value={d.giorniPagamento} onChange={set("giorniPagamento")} /></Campo>
        {cliente
          ? <Campo etichetta="Sconto abituale %" className="sm:col-span-3"><input className="campo" value={d.scontoPercentuale} onChange={set("scontoPercentuale")} inputMode="decimal" /></Campo>
          : <Campo etichetta="Tempo di consegna (giorni)" className="sm:col-span-3"><input className="campo" type="number" min={0} max={365} value={d.leadTimeGiorni} onChange={set("leadTimeGiorni")} /></Campo>}
        <Campo etichetta="Note" className="sm:col-span-6"><textarea className="campo" rows={2} value={d.note} onChange={set("note")} maxLength={2000} /></Campo>
        {errore && <div className="sm:col-span-6"><Avviso tipo="errore">{errore}</Avviso></div>}
        <div className="flex justify-end gap-2 sm:col-span-6">
          <button type="button" className="btn-secondario" onClick={onChiudi}>Annulla</button>
          <button className="btn-primario" disabled={inCorso}>Salva</button>
        </div>
      </form>
    </Modale>
  );
}
