// Catalogo (prodotti, varianti, barcode, immagini) e magazzino multi-sede (lotti, seriali, inventario). © Roberto Di Flumeri
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import type { FastifyInstance } from "fastify";
import { api, azienda, carica, disponibile, fornitore, nuovaApp, prodotto } from "./aiuti.js";

let app: FastifyInstance;
beforeAll(async () => { app = await nuovaApp(); });
afterAll(async () => { await app.close(); });

describe("catalogo prodotti", () => {
  it("crea un prodotto con variante standard e lo ritrova per SKU e barcode", async () => {
    const a = await azienda(app);
    const p = await prodotto(app, a.h, { sku: "CAFFE-1KG", nome: "Caffè in grani 1 kg", barcode: "8001234567897", unita: "kg" });
    expect(p.varianti).toHaveLength(1);
    expect(p.varianti[0].nome).toBe("Standard");
    expect((await api(app, "GET", "/api/barcode/8001234567897", a.h)).json.sku).toBe("CAFFE-1KG");
    expect((await api(app, "GET", "/api/articoli?q=grani", a.h)).json[0].varianteId).toBe(p.varianteId);
    expect((await api(app, "GET", "/api/prodotti?q=CAFFE", a.h)).json).toHaveLength(1);
  });

  it("valida barcode EAN, SKU duplicato, IVA 0% senza natura, lotti+seriali insieme", async () => {
    const a = await azienda(app);
    const base = { sku: "X-1", nome: "Prodotto", prezzoVendita: 100, aliquotaIva: 22 };
    expect((await api(app, "POST", "/api/prodotti", a.h, { ...base, barcode: "8001234567890" })).status).toBe(422);
    expect((await api(app, "POST", "/api/prodotti", a.h, base)).status).toBe(201);
    expect((await api(app, "POST", "/api/prodotti", a.h, base)).status).toBe(409);
    expect((await api(app, "POST", "/api/prodotti", a.h, { ...base, sku: "X-2", aliquotaIva: 0 })).status).toBe(422);
    expect((await api(app, "POST", "/api/prodotti", a.h, { ...base, sku: "X-3", aliquotaIva: 0, naturaIva: "N4" })).status).toBe(201);
    expect((await api(app, "POST", "/api/prodotti", a.h, { ...base, sku: "X-4", gestioneLotti: true, gestioneSeriali: true })).status).toBe(422);
    expect((await api(app, "POST", "/api/prodotti", a.h, { ...base, sku: "con spazi" })).status).toBe(422);
  });

  it("varianti (taglia/colore) sostituiscono la variante standard", async () => {
    const a = await azienda(app);
    const p = await prodotto(app, a.h, { sku: "TSHIRT", nome: "T-shirt" });
    for (const t of ["S", "M", "L"])
      expect((await api(app, "POST", `/api/prodotti/${p.id}/varianti`, a.h, { sku: `TSHIRT-${t}`, nome: `Taglia ${t}`, attributi: { taglia: t }, prezzo: t === "L" ? 1200 : null })).status).toBe(201);
    const det = (await api(app, "GET", `/api/prodotti/${p.id}`, a.h)).json;
    expect(det.varianti.map((v: any) => v.sku)).toEqual(["TSHIRT-L", "TSHIRT-M", "TSHIRT-S"]);
    expect(det.varianti[0].attributi).toEqual({ taglia: "L" });
    const art = (await api(app, "GET", "/api/articoli?q=TSHIRT-L", a.h)).json[0];
    expect(art.prezzo).toBe(1200);
  });

  it("un prodotto con storico non si cancella: viene disattivato", async () => {
    const a = await azienda(app);
    const nuovo = await prodotto(app, a.h);
    expect((await api(app, "DELETE", `/api/prodotti/${nuovo.id}`, a.h)).json.eliminato).toBe(true);
    const usato = await prodotto(app, a.h);
    await carica(app, a.h, usato.varianteId, a.magazzinoId, 3);
    const r = await api(app, "DELETE", `/api/prodotti/${usato.id}`, a.h);
    expect(r.json.disattivato).toBe(true);
    expect((await api(app, "GET", `/api/prodotti/${usato.id}`, a.h)).json.attivo).toBe(false);
  });

  it("immagini: accetta solo file immagine reali", async () => {
    const a = await azienda(app);
    const p = await prodotto(app, a.h);
    const png = Buffer.from("89504E470D0A1A0A0000000D49484452000000010000000108060000001F15C4890000000A49444154789C6300010000050001", "hex");
    const invia = (buf: Buffer, nome: string) => app.inject({
      method: "POST", url: `/api/prodotti/${p.id}/immagini`, headers: { ...a.h, "content-type": "multipart/form-data; boundary=X" },
      payload: Buffer.concat([Buffer.from(`--X\r\nContent-Disposition: form-data; name="file"; filename="${nome}"\r\nContent-Type: image/png\r\n\r\n`), buf, Buffer.from("\r\n--X--\r\n")]),
    });
    expect((await invia(Buffer.from("<script>alert(1)</script>"), "finto.png")).statusCode).toBe(422);
    const ok = await invia(png, "foto.png");
    expect(ok.statusCode).toBe(201);
    const file = await app.inject({ method: "GET", url: `/api/file/${ok.json().file}` });
    expect(file.headers["content-type"]).toBe("image/png");
    expect((await app.inject({ method: "GET", url: "/api/file/..%2F..%2F.env" })).statusCode).toBe(404);
  });

  it("etichetta con codice a barre ed esportazione CSV", async () => {
    const a = await azienda(app);
    const p = await prodotto(app, a.h, { barcode: "8001234567897" });
    const png = await app.inject({ method: "GET", url: `/api/varianti/${p.varianteId}/etichetta.png`, headers: a.h });
    expect(png.headers["content-type"]).toBe("image/png");
    expect(png.rawPayload.subarray(1, 4).toString()).toBe("PNG");
    const csv = await app.inject({ method: "GET", url: "/api/prodotti-export.csv", headers: a.h });
    expect(csv.body).toContain(p.sku);
  });
});

describe("magazzino multi-sede", () => {
  it("carico, scarico, trasferimento e rettifica aggiornano le giacenze per sede", async () => {
    const a = await azienda(app);
    const m2 = (await api(app, "POST", "/api/magazzini", a.h, { codice: "NA", nome: "Napoli" })).json;
    const p = await prodotto(app, a.h);
    await carica(app, a.h, p.varianteId, a.magazzinoId, 10);
    expect((await api(app, "POST", "/api/movimenti", a.h, { tipo: "scarico", varianteId: p.varianteId, magazzinoId: a.magazzinoId, quantita: 2, causale: "Rotto" })).status).toBe(201);
    expect((await api(app, "POST", "/api/movimenti", a.h, { tipo: "trasferimento", varianteId: p.varianteId, magazzinoId: a.magazzinoId, magazzinoDestId: m2.id, quantita: 3 })).status).toBe(201);
    expect((await api(app, "POST", "/api/movimenti", a.h, { tipo: "rettifica", varianteId: p.varianteId, magazzinoId: m2.id, quantita: -1 })).status).toBe(201);
    expect((await disponibile(app, a.h, p.varianteId, a.magazzinoId)).quantita).toBe(5);
    expect((await disponibile(app, a.h, p.varianteId, m2.id)).quantita).toBe(2);
    expect((await disponibile(app, a.h, p.varianteId)).quantita).toBe(7);
    const mov = (await api(app, "GET", `/api/movimenti?varianteId=${p.varianteId}`, a.h)).json;
    expect(mov.map((m: any) => m.tipo)).toEqual(["rettifica", "trasferimento", "scarico", "carico"]);
  });

  it("non scarica più di quanto disponibile e non trasferisce sullo stesso magazzino", async () => {
    const a = await azienda(app);
    const p = await prodotto(app, a.h);
    await carica(app, a.h, p.varianteId, a.magazzinoId, 2);
    const r = await api(app, "POST", "/api/movimenti", a.h, { tipo: "scarico", varianteId: p.varianteId, magazzinoId: a.magazzinoId, quantita: 3 });
    expect(r.status).toBe(409);
    expect(r.json.errore).toContain("Giacenza insufficiente");
    expect((await api(app, "POST", "/api/movimenti", a.h, { tipo: "trasferimento", varianteId: p.varianteId, magazzinoId: a.magazzinoId, magazzinoDestId: a.magazzinoId, quantita: 1 })).status).toBe(422);
  });

  it("lotti: carico con scadenza e scarico FEFO (prima il lotto che scade prima)", async () => {
    const a = await azienda(app);
    const p = await prodotto(app, a.h, { gestioneLotti: true });
    expect((await api(app, "POST", "/api/movimenti", a.h, { tipo: "carico", varianteId: p.varianteId, magazzinoId: a.magazzinoId, quantita: 5 })).status).toBe(422);
    await carica(app, a.h, p.varianteId, a.magazzinoId, 5, { lotto: { codice: "L-TARDI", scadenza: "2027-12-31" } });
    await carica(app, a.h, p.varianteId, a.magazzinoId, 4, { lotto: { codice: "L-PRESTO", scadenza: "2027-03-31" } });
    await api(app, "POST", "/api/movimenti", a.h, { tipo: "scarico", varianteId: p.varianteId, magazzinoId: a.magazzinoId, quantita: 6 });
    const lotti = (await api(app, "GET", `/api/lotti?varianteId=${p.varianteId}`, a.h)).json;
    expect(lotti).toHaveLength(1);
    expect(lotti[0].codice).toBe("L-TARDI");
    expect(lotti[0].quantita).toBe(3);
  });

  it("seriali: numeri obbligatori, unici e tracciati", async () => {
    const a = await azienda(app);
    const p = await prodotto(app, a.h, { gestioneSeriali: true });
    expect((await api(app, "POST", "/api/movimenti", a.h, { tipo: "carico", varianteId: p.varianteId, magazzinoId: a.magazzinoId, quantita: 2, seriali: ["SN1"] })).status).toBe(422);
    expect((await api(app, "POST", "/api/movimenti", a.h, { tipo: "carico", varianteId: p.varianteId, magazzinoId: a.magazzinoId, quantita: 1.5, seriali: ["A", "B"] })).status).toBe(422);
    await carica(app, a.h, p.varianteId, a.magazzinoId, 2, { seriali: ["SN-001", "SN-002"] });
    expect((await api(app, "POST", "/api/movimenti", a.h, { tipo: "carico", varianteId: p.varianteId, magazzinoId: a.magazzinoId, quantita: 1, seriali: ["SN-001"] })).status).toBe(409);
    await api(app, "POST", "/api/movimenti", a.h, { tipo: "scarico", varianteId: p.varianteId, magazzinoId: a.magazzinoId, quantita: 1, seriali: ["SN-002"] });
    const s = (await api(app, "GET", "/api/seriali?q=SN-00", a.h)).json;
    expect(s.find((x: any) => x.numero === "SN-002").stato).toBe("scaricato");
    expect(s.find((x: any) => x.numero === "SN-001").stato).toBe("disponibile");
  });

  it("inventario: conteggio con scansione e rettifiche automatiche alla chiusura", async () => {
    const a = await azienda(app);
    const p1 = await prodotto(app, a.h);
    const p2 = await prodotto(app, a.h);
    await carica(app, a.h, p1.varianteId, a.magazzinoId, 10);
    await carica(app, a.h, p2.varianteId, a.magazzinoId, 4);
    const inv = (await api(app, "POST", "/api/inventari", a.h, { magazzinoId: a.magazzinoId })).json;
    expect((await api(app, "POST", "/api/inventari", a.h, { magazzinoId: a.magazzinoId })).status).toBe(409);
    await api(app, "PATCH", `/api/inventari/${inv.id}/righe`, a.h, { varianteId: p1.varianteId, quantitaContata: 8 });
    for (let i = 0; i < 5; i++) await api(app, "PATCH", `/api/inventari/${inv.id}/righe`, a.h, { varianteId: p2.varianteId, quantitaContata: 1, somma: true });
    const det = (await api(app, "GET", `/api/inventari/${inv.id}`, a.h)).json;
    expect(det.righe.find((r: any) => r.varianteId === p2.varianteId).differenza).toBe(1);
    const c = await api(app, "POST", `/api/inventari/${inv.id}/chiudi`, a.h);
    expect(c.json.rettifiche).toBe(2);
    expect((await disponibile(app, a.h, p1.varianteId)).quantita).toBe(8);
    expect((await disponibile(app, a.h, p2.varianteId)).quantita).toBe(5);
    expect((await api(app, "PATCH", `/api/inventari/${inv.id}/righe`, a.h, { varianteId: p1.varianteId, quantitaContata: 1 })).status).toBe(409);
  });

  it("valore di magazzino e articoli sotto scorta", async () => {
    const a = await azienda(app);
    const f = await fornitore(app, a.h);
    const p = await prodotto(app, a.h, { costoAcquisto: 250, scortaMinima: 10, fornitoreId: f.id });
    await carica(app, a.h, p.varianteId, a.magazzinoId, 4);
    expect((await api(app, "GET", "/api/magazzini", a.h)).json[0].valore).toBe(1000);
    const sotto = (await api(app, "GET", "/api/giacenze?sottoScorta=true", a.h)).json;
    expect(sotto.map((x: any) => x.varianteId)).toContain(p.varianteId);
  });

  it("un magazzino con merce non si disattiva", async () => {
    const a = await azienda(app);
    const p = await prodotto(app, a.h);
    await carica(app, a.h, p.varianteId, a.magazzinoId, 1);
    expect((await api(app, "PATCH", `/api/magazzini/${a.magazzinoId}`, a.h, { attivo: false })).status).toBe(409);
  });
});
