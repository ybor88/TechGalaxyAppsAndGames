// Motore AI: previsione, riordino automatico, analisi clienti e alert intelligenti. © Roberto Di Flumeri
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import type { FastifyInstance } from "fastify";
import { db } from "../src/lib/db.js";
import { prevedi } from "../src/services/ai.js";
import { api, azienda, carica, cliente, fornitore, nuovaApp, prodotto } from "./aiuti.js";

let app: FastifyInstance;
beforeAll(async () => { app = await nuovaApp(); });
afterAll(async () => { await app.close(); });

const GIORNO = 86400_000;

/** Storico di vendite (movimenti) negli ultimi `giorni` giorni, con una quantità giornaliera data. */
async function storicoVendite(aziendaId: string, varianteId: string, magazzinoId: string, giorni: number, quantita: (g: number) => number) {
  const dati = [];
  for (let g = giorni; g >= 1; g--) {
    const q = quantita(g);
    if (q > 0) dati.push({ aziendaId, tipo: "vendita", varianteId, magazzinoId, quantita: q, createdAt: new Date(Date.now() - g * GIORNO + 3600_000) });
  }
  await db.movimento.createMany({ data: dati });
}

describe("previsione vendite", () => {
  it("riconosce la stagionalità settimanale e misura l'errore", () => {
    const schema = [60, 80, 90, 100, 140, 180, 50]; // vendite tipiche per giorno della settimana
    const storico = Array.from({ length: 140 }, (_, i) => ({ data: new Date(Date.UTC(2026, 0, 5) + i * GIORNO).toISOString().slice(0, 10),
      valore: schema[i % 7] + (i % 3) - 1 + i * 0.1 }));
    const p = prevedi(storico, 14);
    expect(p.metodo).toContain("Holt-Winters");
    expect(p.accuratezza.mape).toBeLessThan(5);
    // il picco del sabato (posizione 5) resta il giorno più alto anche nella previsione
    const settimana = p.previsione.slice(0, 7).map((x) => x.valore);
    const sabato = p.previsione.findIndex((_, i) => (140 + i) % 7 === 5);
    expect(Math.max(...settimana)).toBe(settimana[sabato]);
    expect(p.previsione.every((x) => x.minimo <= x.valore && x.valore <= x.massimo)).toBe(true);
  });

  it("con storico breve usa un metodo semplice e non va mai sotto zero", () => {
    const p = prevedi([{ data: "2026-10-01", valore: 3 }, { data: "2026-10-02", valore: 0 }, { data: "2026-10-03", valore: 1 }], 7);
    expect(p.metodo).toContain("semplice");
    expect(p.previsione.every((x) => x.valore >= 0 && x.minimo >= 0)).toBe(true);
  });

  it("endpoint di previsione per articolo", async () => {
    const a = await azienda(app);
    const p = await prodotto(app, a.h);
    await storicoVendite(a.aziendaId, p.varianteId, a.magazzinoId, 90, (g) => (g % 7 === 0 ? 6 : 2));
    const r = (await api(app, "GET", `/api/ai/previsione?varianteId=${p.varianteId}&giorni=14`, a.h)).json;
    expect(r.misura).toBe("quantita");
    expect(r.previsione).toHaveLength(14);
    expect(r.totalePrevisto).toBeGreaterThan(30);
    expect(r.totalePrevisto).toBeLessThan(60);
  });
});

describe("riordino automatico", () => {
  it("calcola punto di riordino e quantità, poi genera l'ordine al fornitore", async () => {
    const a = await azienda(app);
    const f = await fornitore(app, a.h, { leadTimeGiorni: 5 });
    const p = await prodotto(app, a.h, { fornitoreId: f.id, costoAcquisto: 500 });
    await carica(app, a.h, p.varianteId, a.magazzinoId, 8);
    await storicoVendite(a.aziendaId, p.varianteId, a.magazzinoId, 90, () => 2); // 2 al giorno, costante
    const s = (await api(app, "GET", "/api/ai/riordino", a.h)).json.find((x: any) => x.varianteId === p.varianteId);
    expect(s.domandaGiornaliera).toBe(2);
    expect(s.leadTime).toBe(5);
    expect(s.puntoRiordino).toBe(10); // domanda × lead time (variabilità nulla → scorta di sicurezza 0)
    expect(s.quantitaSuggerita).toBe(10 + 2 * 14 - 8);
    expect(s.urgenza).toBe("media");
    expect(s.motivazione).toContain("2 pz/giorno");
    const g = (await api(app, "POST", "/api/ai/riordino/genera", a.h, {})).json;
    expect(g.ordini).toHaveLength(1);
    expect(g.ordini[0].generatoDaAi).toBe(true);
    expect(g.ordini[0].righe[0].quantita).toBe(30);
    // con l'ordine in arrivo il suggerimento sparisce
    expect((await api(app, "GET", "/api/ai/riordino", a.h)).json.find((x: any) => x.varianteId === p.varianteId)).toBeUndefined();
  });

  it("rispetta la scorta minima anche senza vendite", async () => {
    const a = await azienda(app);
    const p = await prodotto(app, a.h, { scortaMinima: 5 });
    await carica(app, a.h, p.varianteId, a.magazzinoId, 2);
    const s = (await api(app, "GET", "/api/ai/riordino", a.h)).json[0];
    expect(s.motivazione).toContain("scorta minima");
    expect(s.fornitoreId).toBeNull();
  });

  it("modalità automatica: crea le bozze una volta sola", async () => {
    const a = await azienda(app);
    await api(app, "PATCH", "/api/azienda", a.h, { riordinoAutomatico: true });
    const f = await fornitore(app, a.h);
    const p = await prodotto(app, a.h, { fornitoreId: f.id, scortaMinima: 10 });
    await carica(app, a.h, p.varianteId, a.magazzinoId, 1);
    expect((await api(app, "POST", "/api/alert/aggiorna", a.h)).json.ordiniCreati).toBe(1);
    expect((await api(app, "POST", "/api/alert/aggiorna", a.h)).json.ordiniCreati).toBe(0);
    expect((await api(app, "GET", "/api/acquisti", a.h)).json).toHaveLength(1);
  });
});

describe("analisi clienti (RFM)", () => {
  it("assegna segmenti, rischio di abbandono e consiglio", async () => {
    const a = await azienda(app);
    const p = await prodotto(app, a.h, { prezzoVendita: 10000 });
    await carica(app, a.h, p.varianteId, a.magazzinoId, 500);
    const ordina = async (clienteId: string, giorniFa: number, q: number) => {
      const o = (await api(app, "POST", "/api/ordini", a.h, { clienteId, righe: [{ varianteId: p.varianteId, quantita: q }] })).json;
      await api(app, "POST", `/api/ordini/${o.id}/stato`, a.h, { stato: "confermato" });
      await db.ordineVendita.update({ where: { id: o.id }, data: { data: new Date(Date.now() - giorniFa * GIORNO) } });
    };
    const campione = await cliente(app, a.h, { ragioneSociale: "Cliente Campione" });
    const rischio = await cliente(app, a.h, { ragioneSociale: "Cliente A Rischio" });
    const nuovo = await cliente(app, a.h, { ragioneSociale: "Cliente Nuovo" });
    const altri = await Promise.all([1, 2, 3].map((i) => cliente(app, a.h, { ragioneSociale: `Medio ${i}` })));
    for (const g of [2, 15, 30, 45, 60, 75]) await ordina(campione.id, g, 5);
    for (const g of [150, 170, 190, 210, 230]) await ordina(rischio.id, g, 4);
    await ordina(nuovo.id, 3, 1);
    for (const [i, c] of altri.entries()) { await ordina(c.id, 40 + i * 10, 1); await ordina(c.id, 90 + i * 10, 1); }
    const r = (await api(app, "GET", "/api/ai/clienti", a.h)).json;
    const di = (nome: string) => r.profili.find((x: any) => x.nome === nome);
    expect(di("Cliente Campione").segmento).toBe("Campioni");
    expect(di("Cliente A Rischio").segmento).toBe("A rischio");
    expect(di("Cliente A Rischio").rischioAbbandono).toBe("alto");
    expect(di("Cliente Nuovo").segmento).toBe("Nuovi");
    expect(di("Cliente Campione").consiglio).toBeTruthy();
    expect(r.segmenti.length).toBeGreaterThan(2);
    // e il cliente importante a rischio genera un alert
    await api(app, "POST", "/api/alert/aggiorna", a.h);
    const alert = (await api(app, "GET", "/api/alert", a.h)).json;
    expect(alert.some((x: any) => x.tipo === "cliente_a_rischio" && x.titolo.includes("Cliente A Rischio"))).toBe(true);
  });
});

describe("alert intelligenti", () => {
  it("scorte, fatture scadute e lotti in scadenza; si risolvono da soli", async () => {
    const a = await azienda(app);
    const p = await prodotto(app, a.h, { scortaMinima: 5 });
    await carica(app, a.h, p.varianteId, a.magazzinoId, 2);
    const lot = await prodotto(app, a.h, { gestioneLotti: true, nome: "Latte fresco" });
    await carica(app, a.h, lot.varianteId, a.magazzinoId, 10, { lotto: { codice: "L-OGGI", scadenza: new Date(Date.now() + 4 * GIORNO).toISOString() } });
    const c = await cliente(app, a.h);
    const f = (await api(app, "POST", "/api/documenti", a.h, { tipo: "fattura", clienteId: c.id, righe: [{ descrizione: "X", quantita: 1, prezzoUnitario: 10000, aliquotaIva: 22 }] })).json;
    const e = (await api(app, "POST", `/api/documenti/${f.id}/emetti`, a.h)).json;
    await db.scadenza.update({ where: { id: e.scadenze[0].id }, data: { dataScadenza: new Date(Date.now() - 40 * GIORNO) } });

    await api(app, "POST", "/api/alert/aggiorna", a.h);
    const alert = (await api(app, "GET", "/api/alert", a.h)).json;
    const tipi = alert.map((x: any) => x.tipo);
    expect(tipi).toEqual(expect.arrayContaining(["scorta_bassa", "lotto_in_scadenza", "fattura_scaduta", "riordino"]));
    expect(alert.find((x: any) => x.tipo === "fattura_scaduta").gravita).toBe("critico");
    expect(alert.find((x: any) => x.tipo === "lotto_in_scadenza").gravita).toBe("critico");
    // il secondo aggiornamento non duplica
    await api(app, "POST", "/api/alert/aggiorna", a.h);
    expect((await api(app, "GET", "/api/alert", a.h)).json).toHaveLength(alert.length);
    // pagata la fattura e riassortita la merce, gli alert si chiudono
    await api(app, "POST", `/api/scadenze/${e.scadenze[0].id}/pagamento`, a.h, { metodo: "bonifico" });
    await carica(app, a.h, p.varianteId, a.magazzinoId, 10);
    await api(app, "POST", "/api/alert/aggiorna", a.h);
    const aperti = (await api(app, "GET", "/api/alert", a.h)).json.map((x: any) => x.tipo);
    expect(aperti).not.toContain("fattura_scaduta");
    expect(aperti).not.toContain("scorta_bassa");
    expect((await api(app, "GET", "/api/alert?stato=risolti", a.h)).json.length).toBeGreaterThanOrEqual(2);
  });

  it("picco anomalo di vendite rispetto alle settimane precedenti", async () => {
    const a = await azienda(app);
    const p = await prodotto(app, a.h, { nome: "Gelato artigianale" });
    await carica(app, a.h, p.varianteId, a.magazzinoId, 1000);
    await storicoVendite(a.aziendaId, p.varianteId, a.magazzinoId, 63, (g) => (g <= 7 ? 30 : 5 + (g % 3)));
    await api(app, "POST", "/api/alert/aggiorna", a.h);
    const anomalia = (await api(app, "GET", "/api/alert", a.h)).json.find((x: any) => x.tipo === "anomalia_vendite");
    expect(anomalia.titolo).toContain("Picco");
    expect(anomalia.messaggio).toContain("Verifica le scorte");
  });
});
