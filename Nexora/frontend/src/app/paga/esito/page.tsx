"use client";
// Ritorno dal checkout reale di Stripe o PayPal. © Roberto Di Flumeri
import { CheckCircle2, XCircle } from "lucide-react";
import { useEffect, useState } from "react";
import Accoglienza from "@/components/Accoglienza";

export default function EsitoPagamento() {
  const [ok, setOk] = useState<boolean | null>(null);
  useEffect(() => { setOk(new URLSearchParams(window.location.search).get("stato") === "ok"); }, []);
  if (ok === null) return <Accoglienza><p className="text-center text-tenue">…</p></Accoglienza>;
  return (
    <Accoglienza>
      <div className="space-y-3 text-center">
        {ok ? <CheckCircle2 className="mx-auto h-14 w-14 text-ok" /> : <XCircle className="mx-auto h-14 w-14 text-errore" />}
        <h1 className="text-xl font-bold">{ok ? "Pagamento completato" : "Pagamento annullato"}</h1>
        <p className="text-tenue">{ok ? "Grazie! La conferma arriva al fornitore in automatico: puoi chiudere questa pagina." : "Nessun importo è stato addebitato. Puoi riprovare dal link ricevuto."}</p>
      </div>
    </Accoglienza>
  );
}
