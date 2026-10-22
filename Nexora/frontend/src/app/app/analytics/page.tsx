"use client";
// Analytics e reporting: ricavi e margini nel tempo, categorie, canali, migliori clienti, analisi ABC e rotazione di magazzino. © Roberto Di Flumeri
import { Percent, Receipt, RotateCw, ShoppingBag, TrendingUp, Users } from "lucide-react";
import { useState } from "react";
import { GraficoBarreOrizzontali, GraficoSerie, GraficoTorta } from "@/components/grafici";
import { Caricamento, Errore, Kpi, NOMI_CANALE, Titolo, useDati } from "@/components/ui";
import { qs } from "@/lib/api";
import { euro, giornoBreve, mese, numero } from "@/lib/formato";

const iso = (d: Date) => d.toISOString().slice(0, 10);

export default function Analytics() {
  const oggi = new Date();
  const [dal, setDal] = useState(iso(new Date(oggi.getFullYear(), oggi.getMonth() - 11, 1)));
  const [al, setAl] = useState(iso(oggi));
  const [raggruppa, setRaggruppa] = useState<"giorno" | "settimana" | "mese">("mese");
  const [classe, setClasse] = useState<"" | "A" | "B" | "C">("");
  const { dati: a, errore, ricarica, caricamento } = useDati<any>(`/api/analytics${qs({ dal, al, raggruppa })}`);
  const periodo = (p: number) => { setDal(iso(new Date(Date.now() - p * 86400_000))); setAl(iso(oggi)); setRaggruppa(p <= 31 ? "giorno" : p <= 120 ? "settimana" : "mese"); };

  return (
    <>
      <Titolo titolo="Analytics" sottotitolo="Ricavi e margini sugli ordini evasi, con costo del venduto al momento della vendita" />
      <div className="scheda mb-6 flex flex-wrap items-end gap-3 p-4">
        <label className="text-sm"><span className="etichetta">Dal</span><input className="campo" type="date" value={dal} onChange={(e) => setDal(e.target.value)} /></label>
        <label className="text-sm"><span className="etichetta">Al</span><input className="campo" type="date" value={al} onChange={(e) => setAl(e.target.value)} /></label>
        <label className="text-sm"><span className="etichetta">Raggruppa per</span>
          <select className="campo" value={raggruppa} onChange={(e) => setRaggruppa(e.target.value as typeof raggruppa)}><option value="giorno">Giorno</option><option value="settimana">Settimana</option><option value="mese">Mese</option></select></label>
        <div className="flex flex-wrap gap-1">{[[30, "30 giorni"], [90, "Trimestre"], [365, "12 mesi"]].map(([p, n]) => <button key={p} className="btn-secondario btn-piccolo" onClick={() => periodo(p as number)}>{n}</button>)}</div>
      </div>
      {errore && <Errore messaggio={errore} riprova={ricarica} />}
      {!a && caricamento && <Caricamento />}
      {a && (
        <div className={`space-y-6 ${caricamento ? "opacity-60" : ""}`}>
          <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-6">
            <Kpi etichetta="Ricavi" valore={euro(a.kpi.ricavi, true)} icona={TrendingUp} />
            <Kpi etichetta="Margine" valore={euro(a.kpi.margine, true)} icona={Percent} tono="ok" nota={`${numero(a.kpi.marginePercentuale)}% dei ricavi`} />
            <Kpi etichetta="Ordini" valore={numero(a.kpi.ordini)} icona={ShoppingBag} tono="viola" />
            <Kpi etichetta="Scontrino medio" valore={euro(a.kpi.scontrinoMedio)} icona={Receipt} tono="ciano" />
            <Kpi etichetta="Clienti attivi" valore={numero(a.kpi.clientiAttivi)} icona={Users} tono="primario" />
            <Kpi etichetta="Rotazione magazzino" valore={a.kpi.rotazioneAnnua ? `${numero(a.kpi.rotazioneAnnua)}×/anno` : "—"} icona={RotateCw} tono="attenzione"
              nota={a.kpi.giorniCopertura ? `${a.kpi.giorniCopertura} giorni di copertura` : undefined} />
          </section>
          <section className="scheda">
            <h2 className="mb-3 font-semibold">Ricavi e margine</h2>
            <GraficoSerie dati={a.serie} asseX="periodo" formattaX={raggruppa === "mese" ? mese : giornoBreve}
              serie={[{ chiave: "ricavi", nome: "Ricavi" }, { chiave: "margine", nome: "Margine", colore: "#8b5cf6" }]} />
          </section>
          <section className="grid gap-6 xl:grid-cols-2">
            <div className="scheda"><h2 className="mb-3 font-semibold">Ricavi per categoria</h2><GraficoTorta dati={a.perCategoria} chiave="ricavi" etichetta="categoria" altezza={280} />
              <table className="tabella mt-3"><thead><tr><th>Categoria</th><th className="num">Ricavi</th><th className="num">Margine</th></tr></thead>
                <tbody>{a.perCategoria.map((c: any) => <tr key={c.categoria}><td>{c.categoria}</td><td className="num">{euro(c.ricavi)}</td><td className="num">{c.ricavi ? `${Math.round((c.margine / c.ricavi) * 100)}%` : "—"}</td></tr>)}</tbody></table></div>
            <div className="scheda"><h2 className="mb-3 font-semibold">Canali di vendita</h2>
              <GraficoBarreOrizzontali dati={a.perCanale.map((c: any) => ({ ...c, nome: NOMI_CANALE[c.canale] ?? c.canale }))} chiave="ricavi" etichetta="nome" />
              <table className="tabella mt-3"><thead><tr><th>Canale</th><th className="num">Ordini</th><th className="num">Ricavi</th><th className="num">Medio</th></tr></thead>
                <tbody>{a.perCanale.map((c: any) => <tr key={c.canale}><td>{NOMI_CANALE[c.canale] ?? c.canale}</td><td className="num">{c.ordini}</td><td className="num">{euro(c.ricavi)}</td><td className="num">{euro(c.ricavi / Math.max(1, c.ordini))}</td></tr>)}</tbody></table>
              <h2 className="mb-2 mt-6 font-semibold">Migliori clienti</h2>
              <table className="tabella"><tbody>{a.topClienti.map((c: any, i: number) => <tr key={c.cliente}><td className="w-6 text-tenue">{i + 1}</td><td>{c.cliente}</td><td className="num">{c.ordini} ordini</td><td className="num font-medium">{euro(c.ricavi)}</td></tr>)}</tbody></table>
            </div>
          </section>
          <section className="scheda overflow-x-auto">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="font-semibold">Analisi ABC dei prodotti</h2>
                <p className="text-sm text-tenue">Classe A: prodotti che fanno l&apos;80% dei ricavi · B: fino al 95% · C: il resto. Concentra scorte e attenzione sulla classe A.</p>
              </div>
              <div className="flex gap-2">{a.riepilogoAbc.map((r: any) => (
                <button key={r.classe} onClick={() => setClasse(classe === r.classe ? "" : r.classe)} className={`rounded-xl border px-3 py-2 text-left text-sm ${classe === r.classe ? "border-primario ring-2 ring-primario/30" : "border-bordo"}`}>
                  <span className="font-bold">Classe {r.classe}</span><span className="block text-xs text-tenue">{r.prodotti} prodotti · {euro(r.ricavi, true)}</span></button>
              ))}</div>
            </div>
            <table className="tabella">
              <thead><tr><th>Classe</th><th>SKU</th><th>Prodotto</th><th className="num">Quantità</th><th className="num">Ricavi</th><th className="num">Margine</th><th className="num">Margine %</th></tr></thead>
              <tbody>{a.abc.filter((p: any) => !classe || p.classe === classe).map((p: any) => (
                <tr key={p.sku + p.nome}><td><span className={`pillola ${p.classe === "A" ? "bg-ok/15 text-ok" : p.classe === "B" ? "bg-primario/15 text-primario" : "bg-bordo text-tenue"}`}>{p.classe}</span></td>
                  <td className="font-mono text-xs">{p.sku}</td><td>{p.nome}</td><td className="num">{numero(p.quantita)}</td><td className="num">{euro(p.ricavi)}</td><td className="num">{euro(p.margine)}</td>
                  <td className="num">{p.ricavi ? `${Math.round((p.margine / p.ricavi) * 100)}%` : "—"}</td></tr>
              ))}</tbody>
            </table>
          </section>
        </div>
      )}
    </>
  );
}
