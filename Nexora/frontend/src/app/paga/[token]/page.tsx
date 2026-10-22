"use client";
// Pagina di pagamento dimostrativa (Stripe/PayPal in modalità demo): nessun addebito reale, la rata risulta incassata. © Roberto Di Flumeri
import { CheckCircle2, CreditCard, Lock } from "lucide-react";
import { useParams } from "next/navigation";
import { useState } from "react";
import Accoglienza from "@/components/Accoglienza";
import { Avviso, Caricamento, useAzione, useDati } from "@/components/ui";
import { api } from "@/lib/api";
import { data, euro } from "@/lib/formato";

export default function PaginaPagamento() {
  const { token } = useParams<{ token: string }>();
  const { dati: p, errore, ricarica } = useDati<any>(`/api/pagamenti-demo/${token}`);
  const [carta, setCarta] = useState({ numero: "4242 4242 4242 4242", scadenza: "12/30", cvc: "123" });
  const { inCorso, errore: erroreInvio, esegui } = useAzione();
  const paypal = p?.provider === "paypal";

  return (
    <Accoglienza>
      {errore && <Avviso tipo="errore">{errore}</Avviso>}
      {!p && !errore && <Caricamento />}
      {p && (p.pagata ? (
        <div className="space-y-3 text-center">
          <CheckCircle2 className="mx-auto h-14 w-14 text-ok" />
          <h1 className="text-xl font-bold">Pagamento ricevuto</h1>
          <p className="text-tenue">La rata di {euro(p.importo)} della fattura {p.documento} risulta saldata. Grazie!</p>
        </div>
      ) : (
        <div className="space-y-5">
          <div>
            <p className="text-sm text-tenue">{p.azienda} ti chiede il pagamento di</p>
            <p className="text-3xl font-bold testo-gradiente">{euro(p.importo)}</p>
            <p className="text-sm text-tenue">Fattura {p.documento} · {p.cliente} · scadenza {data(p.scadenza)}</p>
          </div>
          <Avviso tipo="info">Pagina dimostrativa {paypal ? "PayPal" : "Stripe"}: nessun dato viene inviato e nessun importo addebitato. Con le chiavi sandbox il cliente vedrebbe il checkout reale.</Avviso>
          <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); esegui(async () => { await api(`/api/pagamenti-demo/${token}/conferma`, { metodo: "POST", pubblica: true }); await ricarica(); }); }}>
            {!paypal && (
              <>
                <label className="block text-sm"><span className="etichetta">Numero carta (di prova)</span>
                  <input className="campo font-mono" value={carta.numero} onChange={(e) => setCarta({ ...carta, numero: e.target.value })} inputMode="numeric" autoComplete="off" /></label>
                <div className="grid grid-cols-2 gap-3">
                  <label className="text-sm"><span className="etichetta">Scadenza</span><input className="campo font-mono" value={carta.scadenza} onChange={(e) => setCarta({ ...carta, scadenza: e.target.value })} autoComplete="off" /></label>
                  <label className="text-sm"><span className="etichetta">CVC</span><input className="campo font-mono" value={carta.cvc} onChange={(e) => setCarta({ ...carta, cvc: e.target.value })} autoComplete="off" /></label>
                </div>
              </>
            )}
            {erroreInvio && <Avviso tipo="errore">{erroreInvio}</Avviso>}
            <button className="btn-primario w-full py-3" disabled={inCorso}>{paypal ? "Paga con PayPal (demo)" : <><CreditCard className="h-4 w-4" /> Paga {euro(p.importo)}</>}</button>
            <p className="flex items-center justify-center gap-1 text-xs text-tenue"><Lock className="h-3 w-3" /> Link firmato e valido 14 giorni</p>
          </form>
        </div>
      ))}
    </Accoglienza>
  );
}
