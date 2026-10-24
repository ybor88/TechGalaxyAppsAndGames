// Ciclo commerciale: ordini, spedizioni, DDT, fatture, fattura elettronica, note di credito, incassi e acquisti. © Roberto Di Flumeri
import { XMLParser, XMLValidator } from "fast-xml-parser";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import type { FastifyInstance } from "fastify";
import { totali } from "../src/lib/importi.js";
import { api, azienda, carica, cliente, disponibile, fornitore, nuovaApp, prodotto, utenteConRuolo } from "./aiuti.js";

let app: FastifyInstance;
beforeAll(async () => { app = await nuovaApp(); });
afterAll(async () => { await app.close(); });

async function ordineConfermato(a: Awaited<ReturnType<typeof azienda>>, quantita = 2) {
  const p = await prodotto(app, a.h, { prezzoVendita: 2500, costoAcquisto: 1000 });
  await carica(app, a.h, p.varianteId, a.magazzinoId, 10);
  const c = await cliente(app, a.h);
  const o = (await api(app, "POST", "/api/ordini", a.h, { clienteId: c.id, righe: [{ varianteId: p.varianteId, quantita }] })).json;
  return { p, c, o };
}

describe("importi", () => {
  it("IVA calcolata per aliquota sul totale, con sconti di riga", () => {
    const t = totali([
      { quantita: 3, prezzoUnitario: 333, aliquotaIva: 22 },
      { quantita: 1, prezzoUnitario: 1000, sconto: 10, aliquotaIva: 22 },
      { quantita: 2, prezzoUnitario: 450, aliquotaIva: 10 },
    ]);
    expect(t.imponibile).toBe(999 + 900 + 900);
    expect(t.riepilogo.find((r) => r.aliquota === 22)!.imposta).toBe(Math.round(1899 * 0.22));
    expect(t.iva).toBe(418 + 90);
    expect(t.totale).toBe(2799 + 508);
  });
});

describe("ordini di vendita", () => {
  it("ciclo completo: conferma impegna, spedizione scarica ed emette il DDT, consegna", async () => {
    const a = await azienda(app);
    const { p, o } = await ordineConfermato(a, 3);
    expect(o.numero).toMatch(/^OV-\d{4}-0001$/);
    expect(o.totale).toBe(Math.round(7500 * 1.22));
    await api(app, "POST", `/api/ordini/${o.id}/stato`, a.h, { stato: "confermato" });
    let g = await disponibile(app, a.h, p.varianteId);
    expect([g.quantita, g.riservata, g.disponibile]).toEqual([10, 3, 7]);
    await api(app, "POST", `/api/ordini/${o.id}/stato`, a.h, { stato: "in_preparazione" });
    const sped = await api(app, "POST", `/api/ordini/${o.id}/stato`, a.h, { stato: "spedito" });
    expect(sped.json.ddtId).toBeTruthy();
    g = await disponibile(app, a.h, p.varianteId);
    expect([g.quantita, g.riservata]).toEqual([7, 0]);
    const ddt = (await api(app, "GET", `/api/documenti/${sped.json.ddtId}`, a.h)).json;
    expect(ddt.tipo).toBe("ddt");
    expect(ddt.numero).toMatch(/^DDT-/);
    await api(app, "POST", `/api/ordini/${o.id}/stato`, a.h, { stato: "consegnato" });
    const det = (await api(app, "GET", `/api/ordini/${o.id}`, a.h)).json;
    expect(det.stato).toBe("consegnato");
    expect(det.documenti.map((d: any) => d.tipo)).toEqual(["ddt"]);
  });

  it("transizioni non consentite e conferma senza merce", async () => {
    const a = await azienda(app);
    const { o } = await ordineConfermato(a, 2);
    expect((await api(app, "POST", `/api/ordini/${o.id}/stato`, a.h, { stato: "consegnato" })).status).toBe(409);
    const p = await prodotto(app, a.h);
    const c = await cliente(app, a.h);
    const vuoto = (await api(app, "POST", "/api/ordini", a.h, { clienteId: c.id, righe: [{ varianteId: p.varianteId, quantita: 1 }] })).json;
    const r = await api(app, "POST", `/api/ordini/${vuoto.id}/stato`, a.h, { stato: "confermato" });
    expect(r.status).toBe(409);
    expect(r.json.errore).toContain("Disponibilità insufficiente");
  });

  it("annullare un ordine confermato libera la merce impegnata", async () => {
    const a = await azienda(app);
    const { p, o } = await ordineConfermato(a, 4);
    await api(app, "POST", `/api/ordini/${o.id}/stato`, a.h, { stato: "confermato" });
    await api(app, "POST", `/api/ordini/${o.id}/stato`, a.h, { stato: "annullato" });
    expect((await disponibile(app, a.h, p.varianteId)).riservata).toBe(0);
    expect((await api(app, "PATCH", `/api/ordini/${o.id}`, a.h, { note: "x" })).status).toBe(409);
  });

  it("la merce impegnata non si può scaricare a mano", async () => {
    const a = await azienda(app);
    const { p, o } = await ordineConfermato(a, 8);
    await api(app, "POST", `/api/ordini/${o.id}/stato`, a.h, { stato: "confermato" });
    const r = await api(app, "POST", "/api/movimenti", a.h, { tipo: "scarico", varianteId: p.varianteId, magazzinoId: a.magazzinoId, quantita: 3 });
    expect(r.status).toBe(409);
  });

  it("spedizione con lotti FEFO e seriali assegnati automaticamente (tracciabilità)", async () => {
    const a = await azienda(app);
    const lotti = await prodotto(app, a.h, { gestioneLotti: true });
    const seriali = await prodotto(app, a.h, { gestioneSeriali: true });
    await carica(app, a.h, lotti.varianteId, a.magazzinoId, 5, { lotto: { codice: "LOT-B", scadenza: "2028-01-01" } });
    await carica(app, a.h, lotti.varianteId, a.magazzinoId, 5, { lotto: { codice: "LOT-A", scadenza: "2027-06-01" } });
    await carica(app, a.h, seriali.varianteId, a.magazzinoId, 3, { seriali: ["MAC-1", "MAC-2", "MAC-3"] });
    const c = await cliente(app, a.h);
    const o = (await api(app, "POST", "/api/ordini", a.h, { clienteId: c.id, righe: [{ varianteId: lotti.varianteId, quantita: 2 }, { varianteId: seriali.varianteId, quantita: 2 }] })).json;
    await api(app, "POST", `/api/ordini/${o.id}/stato`, a.h, { stato: "confermato" });
    await api(app, "POST", `/api/ordini/${o.id}/stato`, a.h, { stato: "spedito" });
    const det = (await api(app, "GET", `/api/ordini/${o.id}`, a.h)).json;
    expect(det.righe.find((r: any) => r.varianteId === lotti.varianteId).lotto).toBe("LOT-A");
    expect(det.righe.find((r: any) => r.varianteId === seriali.varianteId).seriali).toEqual(["MAC-1", "MAC-2"]);
    const s = (await api(app, "GET", "/api/seriali?q=MAC-1", a.h)).json[0];
    expect(s.stato).toBe("venduto");
    expect(s.ordine.numero).toBe(o.numero);
  });

  it("il magazzino spedisce ma non conferma; le vendite confermano", async () => {
    const a = await azienda(app);
    const mag = await utenteConRuolo(app, a.h, "magazzino");
    const { o } = await ordineConfermato(a, 1);
    expect((await api(app, "POST", `/api/ordini/${o.id}/stato`, mag, { stato: "confermato" })).status).toBe(403);
    await api(app, "POST", `/api/ordini/${o.id}/stato`, a.h, { stato: "confermato" });
    expect((await api(app, "POST", `/api/ordini/${o.id}/stato`, mag, { stato: "spedito" })).status).toBe(200);
  });

  it("spedizione GLS: tracking, etichetta PDF e avanzamento", async () => {
    const a = await azienda(app);
    const { o } = await ordineConfermato(a, 1);
    const s = (await api(app, "POST", `/api/ordini/${o.id}/spedizioni`, a.h, { corriere: "gls", colli: 2, pesoKg: 3.5 })).json;
    expect(s.tracking).toMatch(/^\d{11}$/);
    const pdf = await app.inject({ method: "GET", url: `/api/spedizioni/${s.id}/etichetta.pdf`, headers: a.h });
    expect(pdf.rawPayload.subarray(0, 4).toString()).toBe("%PDF");
    const t = (await api(app, "GET", `/api/spedizioni/${s.id}`, a.h)).json;
    expect(t.eventi[0].stato).toBe("creata");
    expect(t.link).toContain(s.tracking);
  });
});

describe("fatturazione", () => {
  it("numerazione progressiva solo all'emissione; scadenze rateali; incassi", async () => {
    const a = await azienda(app);
    const { o } = await ordineConfermato(a, 2);
    await api(app, "POST", `/api/ordini/${o.id}/stato`, a.h, { stato: "confermato" });
    await api(app, "POST", `/api/ordini/${o.id}/stato`, a.h, { stato: "spedito" });
    const f = (await api(app, "POST", `/api/ordini/${o.id}/fattura`, a.h)).json;
    expect(f.numero).toBeNull();
    expect((await api(app, "POST", `/api/ordini/${o.id}/fattura`, a.h)).json.id).toBe(f.id); // nessun doppione
    const e = (await api(app, "POST", `/api/documenti/${f.id}/emetti`, a.h, { rate: 2 })).json;
    expect(e.numero).toMatch(/^FT-\d{4}-0001$/);
    expect(e.scadenze).toHaveLength(2);
    expect(e.scadenze[0].importo + e.scadenze[1].importo).toBe(e.totale);
    expect((await api(app, "DELETE", `/api/documenti/${f.id}`, a.h)).status).toBe(409);
    await api(app, "POST", `/api/scadenze/${e.scadenze[0].id}/pagamento`, a.h, { metodo: "bonifico" });
    expect((await api(app, "GET", `/api/documenti/${f.id}`, a.h)).json.stato).toBe("emessa");
    await api(app, "POST", `/api/scadenze/${e.scadenze[1].id}/pagamento`, a.h, { metodo: "bonifico" });
    expect((await api(app, "GET", `/api/documenti/${f.id}`, a.h)).json.stato).toBe("pagata");
    // una seconda fattura prende il numero successivo
    const f2 = (await api(app, "POST", "/api/documenti", a.h, { tipo: "fattura", clienteId: o.clienteId, righe: [{ descrizione: "Consulenza", quantita: 1, prezzoUnitario: 10000, aliquotaIva: 22 }] })).json;
    expect((await api(app, "POST", `/api/documenti/${f2.id}/emetti`, a.h)).json.numero).toMatch(/-0002$/);
  });

  it("fattura elettronica FatturaPA: XML valido con dati, righe, riepilogo e pagamento", async () => {
    const a = await azienda(app);
    await api(app, "PATCH", "/api/azienda", a.h, { iban: "IT60X0542811101000000123456" });
    const c = await cliente(app, a.h, { ragioneSociale: "Rossi & Figli S.r.l." });
    const f = (await api(app, "POST", "/api/documenti", a.h, { tipo: "fattura", clienteId: c.id, righe: [
      { descrizione: "Macchina caffè", quantita: 1, prezzoUnitario: 45000, sconto: 10, aliquotaIva: 22 },
      { descrizione: "Libro <ricette>", quantita: 2, prezzoUnitario: 1500, aliquotaIva: 4 },
      { descrizione: "Servizio esente", quantita: 1, prezzoUnitario: 2000, aliquotaIva: 0, naturaIva: "N4" },
    ] })).json;
    expect((await api(app, "GET", `/api/documenti/${f.id}/xml`, a.h)).status).toBe(409); // ancora bozza: niente numero
    await api(app, "POST", `/api/documenti/${f.id}/emetti`, a.h);
    const r = await app.inject({ method: "GET", url: `/api/documenti/${f.id}/xml`, headers: a.h });
    expect(r.headers["content-disposition"]).toMatch(/IT\d{11}_\w{5}\.xml/);
    expect(XMLValidator.validate(r.body)).toBe(true);
    const x = new XMLParser({ ignoreAttributes: false, removeNSPrefix: true, parseTagValue: false }).parse(r.body).FatturaElettronica;
    expect(x["@_versione"]).toBe("FPR12");
    expect(x.FatturaElettronicaHeader.DatiTrasmissione.CodiceDestinatario).toBe("M5UXCR1");
    expect(x.FatturaElettronicaHeader.CessionarioCommittente.DatiAnagrafici.Anagrafica.Denominazione).toBe("Rossi & Figli S.r.l.");
    const body = x.FatturaElettronicaBody;
    expect(body.DatiGenerali.DatiGeneraliDocumento.TipoDocumento).toBe("TD01");
    expect(body.DatiBeniServizi.DettaglioLinee).toHaveLength(3);
    expect(body.DatiBeniServizi.DettaglioLinee[0].PrezzoTotale).toBe("405.00");
    expect(body.DatiBeniServizi.DettaglioLinee[1].Descrizione).toBe("Libro <ricette>");
    expect(body.DatiBeniServizi.DettaglioLinee[2].Natura).toBe("N4");
    const riep = body.DatiBeniServizi.DatiRiepilogo;
    expect(riep.find((g: any) => g.AliquotaIVA === "22.00").Imposta).toBe("89.10");
    expect(body.DatiGenerali.DatiGeneraliDocumento.ImportoTotaleDocumento).toBe("545.30"); // 405 + 89,10 + 30 + 1,20 + 20
    expect(body.DatiPagamento.DettaglioPagamento.IBAN).toBe("IT60X0542811101000000123456");
    expect((await api(app, "GET", `/api/documenti/${f.id}`, a.h)).json.statoSdi).toBe("generato");
    expect((await api(app, "POST", `/api/documenti/${f.id}/sdi`, a.h, { stato: "consegnato" })).json.statoSdi).toBe("consegnato");
  });

  it("azienda senza codice destinatario né PEC: XML con 0000000 (consegna nel cassetto fiscale)", async () => {
    const a = await azienda(app);
    const c = await cliente(app, a.h, { codiceDestinatario: null });
    const f = (await api(app, "POST", "/api/documenti", a.h, { tipo: "fattura", clienteId: c.id, righe: [{ descrizione: "X", quantita: 1, prezzoUnitario: 100, aliquotaIva: 22 }] })).json;
    await api(app, "POST", `/api/documenti/${f.id}/emetti`, a.h);
    const r = await app.inject({ method: "GET", url: `/api/documenti/${f.id}/xml`, headers: a.h });
    expect(r.statusCode).toBe(200);
    const x = new XMLParser({ ignoreAttributes: false, removeNSPrefix: true, parseTagValue: false }).parse(r.body).FatturaElettronica;
    expect(x.FatturaElettronicaHeader.DatiTrasmissione.CodiceDestinatario).toBe("0000000");
    expect(x.FatturaElettronicaHeader.DatiTrasmissione.PECDestinatario).toBeUndefined();
  });

  it("dati SdI mancanti bloccano la fattura elettronica con un messaggio chiaro", async () => {
    const a = await azienda(app);
    const c = (await api(app, "POST", "/api/clienti", a.h, { tipo: "privato", ragioneSociale: "Mario Bianchi" })).json;
    const f = (await api(app, "POST", "/api/documenti", a.h, { tipo: "fattura", clienteId: c.id, righe: [{ descrizione: "X", quantita: 1, prezzoUnitario: 100, aliquotaIva: 22 }] })).json;
    await api(app, "POST", `/api/documenti/${f.id}/emetti`, a.h);
    const det = (await api(app, "GET", `/api/documenti/${f.id}`, a.h)).json;
    expect(det.controlliSdi.join(" ")).toContain("partita IVA o codice fiscale");
    expect((await api(app, "GET", `/api/documenti/${f.id}/xml`, a.h)).status).toBe(422);
  });

  it("nota di credito parziale con reso in magazzino; XML TD04 collegato alla fattura", async () => {
    const a = await azienda(app);
    const { p, o } = await ordineConfermato(a, 3);
    await api(app, "POST", `/api/ordini/${o.id}/stato`, a.h, { stato: "confermato" });
    await api(app, "POST", `/api/ordini/${o.id}/stato`, a.h, { stato: "spedito" });
    const f = (await api(app, "POST", `/api/ordini/${o.id}/fattura`, a.h)).json;
    const fe = (await api(app, "POST", `/api/documenti/${f.id}/emetti`, a.h)).json;
    expect((await api(app, "POST", `/api/documenti/${f.id}/nota-credito`, a.h, { righe: [{ indice: 0, quantita: 5 }] })).status).toBe(422);
    const nc = (await api(app, "POST", `/api/documenti/${f.id}/nota-credito`, a.h, { righe: [{ indice: 0, quantita: 1 }] })).json;
    expect(nc.totale).toBe(Math.round(2500 * 1.22));
    const ne = (await api(app, "POST", `/api/documenti/${nc.id}/emetti`, a.h, { resoInMagazzino: a.magazzinoId })).json;
    expect(ne.numero).toMatch(/^NC-/);
    expect((await disponibile(app, a.h, p.varianteId)).quantita).toBe(8);
    const xml = (await app.inject({ method: "GET", url: `/api/documenti/${nc.id}/xml`, headers: a.h })).body;
    expect(xml).toContain("<TipoDocumento>TD04</TipoDocumento>");
    expect(xml).toContain(`<IdDocumento>${fe.numero}</IdDocumento>`);
    const nomeF = (await api(app, "GET", `/api/documenti/${f.id}`, a.h)).json;
    await app.inject({ method: "GET", url: `/api/documenti/${f.id}/xml`, headers: a.h });
    const nomi = [(await api(app, "GET", `/api/documenti/${nc.id}`, a.h)).json.nomeFileSdi, (await api(app, "GET", `/api/documenti/${nomeF.id}`, a.h)).json.nomeFileSdi];
    expect(new Set(nomi).size).toBe(2); // nomi file SdI univoci
  });

  it("preventivo: PDF, accettazione e conversione in ordine (una sola volta)", async () => {
    const a = await azienda(app);
    const p = await prodotto(app, a.h, { prezzoVendita: 5000 });
    const c = await cliente(app, a.h);
    const pr = (await api(app, "POST", "/api/documenti", a.h, { tipo: "preventivo", clienteId: c.id, righe: [
      { varianteId: p.varianteId, descrizione: "Articolo a catalogo", quantita: 4, prezzoUnitario: 4500, sconto: 5, aliquotaIva: 22 }] })).json;
    expect(pr.validoFino).toBeTruthy();
    await api(app, "POST", `/api/documenti/${pr.id}/emetti`, a.h);
    const pdf = await app.inject({ method: "GET", url: `/api/documenti/${pr.id}/pdf`, headers: a.h });
    expect(pdf.rawPayload.subarray(0, 4).toString()).toBe("%PDF");
    const o = await api(app, "POST", `/api/documenti/${pr.id}/ordine`, a.h);
    expect(o.status).toBe(201);
    expect(o.json.righe[0].prezzoUnitario).toBe(4500);
    expect(o.json.righe[0].sconto).toBe(5);
    expect((await api(app, "GET", `/api/documenti/${pr.id}`, a.h)).json.stato).toBe("accettato");
    expect((await api(app, "POST", `/api/documenti/${pr.id}/ordine`, a.h)).status).toBe(409);
  });

  it("IVA 0% senza natura rifiutata in fattura", async () => {
    const a = await azienda(app);
    const c = await cliente(app, a.h);
    const r = await api(app, "POST", "/api/documenti", a.h, { tipo: "fattura", clienteId: c.id, righe: [{ descrizione: "X", quantita: 1, prezzoUnitario: 100, aliquotaIva: 0 }] });
    expect(r.status).toBe(422);
  });

  it("scadenziario: crediti scaduti e aperti", async () => {
    const a = await azienda(app);
    const c = await cliente(app, a.h, { giorniPagamento: 0 });
    const f = (await api(app, "POST", "/api/documenti", a.h, { tipo: "fattura", clienteId: c.id, righe: [{ descrizione: "X", quantita: 1, prezzoUnitario: 10000, aliquotaIva: 22 }] })).json;
    await api(app, "POST", `/api/documenti/${f.id}/emetti`, a.h);
    expect((await api(app, "GET", "/api/scadenze?stato=aperte", a.h)).json).toHaveLength(1);
  });
});

describe("acquisti", () => {
  it("ordine al fornitore, ricevimento parziale con lotto e costo medio ponderato", async () => {
    const a = await azienda(app);
    const f = await fornitore(app, a.h, { leadTimeGiorni: 10 });
    const p = await prodotto(app, a.h, { costoAcquisto: 1000, gestioneLotti: true, fornitoreId: f.id });
    await carica(app, a.h, p.varianteId, a.magazzinoId, 10, { lotto: { codice: "OLD" }, costoUnitario: 1000 });
    const oa = (await api(app, "POST", "/api/acquisti", a.h, { fornitoreId: f.id, righe: [{ varianteId: p.varianteId, quantita: 20, costoUnitario: 1300 }] })).json;
    expect(oa.numero).toMatch(/^OA-/);
    expect(new Date(oa.dataPrevista).getTime() - new Date(oa.data).getTime()).toBe(10 * 86400_000);
    expect((await api(app, "POST", `/api/acquisti/${oa.id}/ricevi`, a.h, { righe: [{ rigaId: oa.righe[0].id, quantita: 5 }] })).status).toBe(409); // non inviato
    await api(app, "POST", `/api/acquisti/${oa.id}/invia`, a.h);
    expect((await api(app, "POST", `/api/acquisti/${oa.id}/ricevi`, a.h, { righe: [{ rigaId: oa.righe[0].id, quantita: 25, lotto: { codice: "NEW" } }] })).status).toBe(422);
    const r = (await api(app, "POST", `/api/acquisti/${oa.id}/ricevi`, a.h, { righe: [{ rigaId: oa.righe[0].id, quantita: 10, lotto: { codice: "NEW", scadenza: "2028-01-01" } }] })).json;
    expect(r.stato).toBe("parziale");
    expect((await api(app, "GET", `/api/prodotti/${p.id}`, a.h)).json.costoAcquisto).toBe(1150); // (10×10 + 10×13) / 20
    expect((await api(app, "POST", `/api/acquisti/${oa.id}/annulla`, a.h)).status).toBe(409);
    const fine = (await api(app, "POST", `/api/acquisti/${oa.id}/ricevi`, a.h, { righe: [{ rigaId: oa.righe[0].id, quantita: 10, lotto: { codice: "NEW" } }] })).json;
    expect(fine.stato).toBe("ricevuto");
    expect((await disponibile(app, a.h, p.varianteId)).quantita).toBe(30);
  });
});

describe("dashboard e analisi", () => {
  it("KPI coerenti con le operazioni registrate", async () => {
    const a = await azienda(app);
    const { o } = await ordineConfermato(a, 2);
    await api(app, "POST", `/api/ordini/${o.id}/stato`, a.h, { stato: "confermato" });
    await api(app, "POST", `/api/ordini/${o.id}/stato`, a.h, { stato: "spedito" });
    const f = (await api(app, "POST", `/api/ordini/${o.id}/fattura`, a.h)).json;
    await api(app, "POST", `/api/documenti/${f.id}/emetti`, a.h);
    const d = (await api(app, "GET", "/api/dashboard", a.h)).json;
    expect(d.fatturato.meseCorrente).toBe(5000);
    expect(d.valoreMagazzino).toBe(8 * 1000);
    expect(d.prodottiPiuVenduti[0].quantita).toBe(2);
    expect(d.scadenze.creditiAperti).toBe(6100);
    expect(d.andamento).toHaveLength(12);
    const an = (await api(app, "GET", "/api/analytics", a.h)).json;
    expect(an.kpi.ricavi).toBe(5000);
    expect(an.kpi.margine).toBe(3000);
    expect(an.kpi.marginePercentuale).toBe(60);
    expect(an.abc[0].classe).toBe("A");
  });
});
