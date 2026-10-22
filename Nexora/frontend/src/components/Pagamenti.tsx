"use client";
// Incassi: registrazione del pagamento di una rata e link di pagamento Stripe/PayPal da inviare al cliente. © Roberto Di Flumeri
import { Check, Copy, CreditCard, ExternalLink } from "lucide-react";
import { useState } from "react";
import { api } from "@/lib/api";
import { euro, oggiIso } from "@/lib/formato";
import { Avviso, Campo, Modale, useAzione } from "./ui";

export function RegistraPagamento({ scadenza, onChiudi, onFatto }: { scadenza: { id: string; importo: number }; onChiudi: () => void; onFatto: () => void }) {
  const [d, setD] = useState({ metodo: "bonifico", data: oggiIso(), riferimento: "" });
  const { inCorso, errore, esegui } = useAzione();
  return (
    <Modale titolo={`Registra incasso di ${euro(scadenza.importo)}`} onChiudi={onChiudi}>
      <form className="grid gap-3 sm:grid-cols-2" onSubmit={(e) => { e.preventDefault(); esegui(async () => {
        await api(`/api/scadenze/${scadenza.id}/pagamento`, { metodo: "POST", corpo: { metodo: d.metodo, data: d.data, riferimento: d.riferimento || null } });
        onFatto();
      }); }}>
        <Campo etichetta="Metodo">
          <select className="campo" value={d.metodo} onChange={(e) => setD({ ...d, metodo: e.target.value })}>
            {[["bonifico", "Bonifico"], ["carta", "Carta"], ["contanti", "Contanti"], ["assegno", "Assegno"], ["stripe", "Stripe"], ["paypal", "PayPal"]].map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </Campo>
        <Campo etichetta="Data incasso"><input className="campo" type="date" value={d.data} onChange={(e) => setD({ ...d, data: e.target.value })} max={oggiIso()} /></Campo>
        <Campo etichetta="Riferimento (CRO, n. transazione…)" className="sm:col-span-2"><input className="campo" value={d.riferimento} onChange={(e) => setD({ ...d, riferimento: e.target.value })} maxLength={100} /></Campo>
        {errore && <div className="sm:col-span-2"><Avviso tipo="errore">{errore}</Avviso></div>}
        <button className="btn-primario sm:col-span-2" disabled={inCorso}><Check className="h-4 w-4" /> Registra incasso</button>
      </form>
    </Modale>
  );
}

export function LinkPagamento({ scadenzaId, onChiudi }: { scadenzaId: string; onChiudi: () => void }) {
  const [esito, setEsito] = useState<{ url: string; modalita: string } | null>(null);
  const [copiato, setCopiato] = useState(false);
  const { inCorso, errore, esegui } = useAzione();
  const crea = (provider: "stripe" | "paypal") => esegui(async () => setEsito(await api(`/api/scadenze/${scadenzaId}/link`, { metodo: "POST", corpo: { provider } })));
  return (
    <Modale titolo="Link di pagamento" onChiudi={onChiudi}>
      {!esito ? (
        <div className="space-y-3">
          <p className="text-sm text-tenue">Genera un link da inviare al cliente: quando paga, la rata risulta incassata in automatico (webhook verificato).</p>
          <div className="grid grid-cols-2 gap-3">
            <button className="btn-primario" disabled={inCorso} onClick={() => crea("stripe")}><CreditCard className="h-4 w-4" /> Stripe</button>
            <button className="btn-secondario" disabled={inCorso} onClick={() => crea("paypal")}>PayPal</button>
          </div>
          {errore && <Avviso tipo="errore">{errore}</Avviso>}
        </div>
      ) : (
        <div className="space-y-3">
          {esito.modalita === "demo" && <Avviso tipo="info">Modalità dimostrativa: il link apre una pagina di pagamento simulata, senza addebiti. Con le chiavi sandbox gratuite (Integrazioni) il link diventa un checkout reale.</Avviso>}
          <input className="campo font-mono text-xs" readOnly value={esito.url} onFocus={(e) => e.target.select()} aria-label="Link di pagamento" />
          <div className="grid grid-cols-2 gap-3">
            <button className="btn-secondario" onClick={() => { navigator.clipboard?.writeText(esito.url); setCopiato(true); }}>{copiato ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />} {copiato ? "Copiato" : "Copia"}</button>
            <a className="btn-primario" href={esito.url} target="_blank" rel="noopener noreferrer"><ExternalLink className="h-4 w-4" /> Apri</a>
          </div>
        </div>
      )}
    </Modale>
  );
}
