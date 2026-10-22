// Integrazioni: Shopify, WooCommerce, Magento, Stripe, PayPal, GLS, DHL.
// Senza credenziali ogni connettore lavora in modalità dimostrativa (nessun costo, nessun account);
// con le credenziali gratuite di sandbox/sviluppatore passa alle API reali. © Roberto Di Flumeri
import bwipjs from "bwip-js";
import PDFDocument from "pdfkit";
import { SignJWT, jwtVerify } from "jose";
import type { Integrazione } from "@prisma/client";
import { config } from "../lib/config.js";
import { cifra, decifra, hmac, tokenCasuale, ugualiSicuro } from "../lib/crypto.js";
import { db } from "../lib/db.js";
import { HttpError, nonTrovato, nonValido } from "../lib/errori.js";
import { decimale } from "../lib/importi.js";
import { cambiaStato, creaOrdine } from "./ordini.js";
import { registraPagamento } from "./documenti.js";

export const TIPI_INTEGRAZIONE = ["shopify", "woocommerce", "magento", "stripe", "paypal", "gls", "dhl"] as const;
export type TipoIntegrazione = (typeof TIPI_INTEGRAZIONE)[number];

/** Campi di configurazione per connettore: quelli marcati come segreti non tornano mai al client in chiaro. */
export const CAMPI: Record<TipoIntegrazione, { chiave: string; etichetta: string; segreto?: boolean; richiesto?: boolean }[]> = {
  shopify: [{ chiave: "negozio", etichetta: "Dominio negozio (es. mio-negozio.myshopify.com)", richiesto: true },
    { chiave: "token", etichetta: "Admin API access token", segreto: true, richiesto: true }, { chiave: "locationId", etichetta: "ID location di magazzino" }],
  woocommerce: [{ chiave: "url", etichetta: "URL del sito (https://…)", richiesto: true },
    { chiave: "consumerKey", etichetta: "Consumer key", segreto: true, richiesto: true }, { chiave: "consumerSecret", etichetta: "Consumer secret", segreto: true, richiesto: true }],
  magento: [{ chiave: "url", etichetta: "URL del sito (https://…)", richiesto: true }, { chiave: "token", etichetta: "Integration access token", segreto: true, richiesto: true }],
  stripe: [{ chiave: "secretKey", etichetta: "Secret key (sk_test_… gratuita in modalità test)", segreto: true, richiesto: true },
    { chiave: "webhookSecret", etichetta: "Webhook signing secret (whsec_…)", segreto: true }],
  paypal: [{ chiave: "clientId", etichetta: "Client ID (sandbox gratuita)", richiesto: true }, { chiave: "clientSecret", etichetta: "Client secret", segreto: true, richiesto: true },
    { chiave: "webhookId", etichetta: "Webhook ID" }, { chiave: "ambiente", etichetta: "Ambiente (sandbox o live)" }],
  gls: [{ chiave: "sedeGls", etichetta: "Codice sede GLS (per l'etichetta)" }],
  dhl: [{ chiave: "apiKey", etichetta: "DHL API key (Shipment Tracking, gratuita)", segreto: true, richiesto: true }],
};

export const URL_BASE = {
  stripe: process.env.NEXORA_STRIPE_API || "https://api.stripe.com",
  paypal: (amb?: string) => process.env.NEXORA_PAYPAL_API || (amb === "live" ? "https://api-m.paypal.com" : "https://api-m.sandbox.paypal.com"),
  dhl: process.env.NEXORA_DHL_API || "https://api-eu.dhl.com",
};

export async function integrazione(aziendaId: string, tipo: TipoIntegrazione) {
  return db.integrazione.upsert({
    where: { aziendaId_tipo: { aziendaId, tipo } },
    create: { aziendaId, tipo, webhookSecret: cifra(tokenCasuale(24)) },
    update: {},
  });
}

export function leggiConfigurazione(i: Integrazione): Record<string, string> {
  try { return JSON.parse(decifra(i.configurazione) || "{}"); } catch { return {}; }
}

export function modalita(i: Integrazione): "reale" | "demo" {
  const c = leggiConfigurazione(i);
  return CAMPI[i.tipo as TipoIntegrazione].filter((x) => x.richiesto).every((x) => c[x.chiave]) ? "reale" : "demo";
}

export function vista(i: Integrazione) {
  const c = leggiConfigurazione(i);
  const campi = CAMPI[i.tipo as TipoIntegrazione].map((f) => ({
    ...f, valore: f.segreto ? (c[f.chiave] ? `••••${c[f.chiave].slice(-4)}` : "") : c[f.chiave] || "", impostato: !!c[f.chiave],
  }));
  return {
    id: i.id, tipo: i.tipo, attiva: i.attiva, modalita: modalita(i), campi, ultimaSincronizzazione: i.ultimaSincronizzazione,
    webhookUrl: ["shopify", "woocommerce", "stripe", "paypal"].includes(i.tipo) ? `${config.urlPubblico}/api/webhooks/${i.tipo}/${i.aziendaId}` : null,
    webhookSecret: ["shopify", "woocommerce"].includes(i.tipo) ? decifra(i.webhookSecret) : null,
  };
}

export async function salvaConfigurazione(aziendaId: string, tipo: TipoIntegrazione, valori: Record<string, string>, attiva?: boolean) {
  const i = await integrazione(aziendaId, tipo);
  const attuale = leggiConfigurazione(i);
  for (const f of CAMPI[tipo]) {
    const v = valori[f.chiave];
    if (v === undefined || (f.segreto && v.startsWith("••••"))) continue; // il segreto mascherato non si sovrascrive
    if (v === "") delete attuale[f.chiave];
    else attuale[f.chiave] = v.trim();
  }
  return db.integrazione.update({
    where: { id: i.id },
    data: { configurazione: cifra(JSON.stringify(attuale)), ...(attiva === undefined ? {} : { attiva }) },
  });
}

export async function registraEvento(integrazioneId: string, direzione: "in" | "out", tipo: string, esito: "ok" | "errore" | "ignorato", dettagli?: unknown) {
  await db.eventoIntegrazione.create({ data: {
    integrazioneId, direzione, tipo, esito, dettagli: dettagli === undefined ? null : JSON.stringify(dettagli).slice(0, 2000),
  } });
}

async function chiamata(url: string, init: RequestInit & { timeoutMs?: number } = {}) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), init.timeoutMs ?? 15000);
  try {
    const r = await fetch(url, { ...init, signal: ctrl.signal });
    const testo = await r.text();
    let corpo: unknown = testo;
    try { corpo = JSON.parse(testo); } catch { /* risposta non JSON */ }
    if (!r.ok) throw new HttpError(502, `Il servizio esterno ha risposto ${r.status}`, corpo);
    return corpo as Record<string, any>;
  } catch (e) {
    if (e instanceof HttpError) throw e;
    throw new HttpError(502, "Servizio esterno non raggiungibile");
  } finally {
    clearTimeout(t);
  }
}

// ── Webhook e-commerce: verifica della firma e import degli ordini ───────────

export function verificaFirmaShopify(segreto: string, corpo: Buffer, firma?: string) {
  return !!firma && ugualiSicuro(hmac("sha256", segreto, corpo, "base64"), firma);
}
export const verificaFirmaWoo = verificaFirmaShopify; // stesso schema: HMAC-SHA256 in base64 del corpo

/** Stripe-Signature: t=<timestamp>,v1=<hmac hex di "t.corpo">; tolleranza 5 minuti contro i replay. */
export function verificaFirmaStripe(segreto: string, corpo: Buffer, intestazione?: string, tolleranzaS = 300) {
  if (!intestazione) return false;
  const parti = Object.fromEntries(intestazione.split(",").map((p) => p.split("=") as [string, string]));
  const t = Number(parti.t);
  if (!t || Math.abs(Date.now() / 1000 - t) > tolleranzaS) return false;
  return ugualiSicuro(hmac("sha256", segreto, `${t}.${corpo.toString("utf8")}`), parti.v1 || "");
}

interface OrdineEsterno {
  riferimento: string;
  cliente: { email?: string; nome: string; indirizzo?: string; cap?: string; citta?: string; provincia?: string; nazione?: string };
  righe: { sku: string; quantita: number; prezzo: number; nome?: string }[]; // prezzo in centesimi IVA esclusa
}

export function daShopify(p: any): OrdineEsterno {
  const ind = p.shipping_address || p.billing_address || {};
  const nome = [p.customer?.first_name, p.customer?.last_name].filter(Boolean).join(" ") || ind.name || p.email || "Cliente Shopify";
  return {
    riferimento: String(p.id), cliente: { email: p.email || p.customer?.email, nome, indirizzo: ind.address1, cap: ind.zip, citta: ind.city, provincia: ind.province_code, nazione: ind.country_code || "IT" },
    righe: (p.line_items || []).map((l: any) => ({ sku: l.sku, quantita: Number(l.quantity), nome: l.title,
      prezzo: Math.round((Number(l.price) * 100) / (p.taxes_included ? 1 + (Number(l.tax_lines?.[0]?.rate) || 0.22) : 1)) })),
  };
}

export function daWoo(p: any): OrdineEsterno {
  const b = p.shipping?.address_1 ? p.shipping : p.billing || {};
  return {
    riferimento: String(p.id), cliente: { email: p.billing?.email, nome: [b.first_name, b.last_name].filter(Boolean).join(" ") || b.company || "Cliente WooCommerce",
      indirizzo: b.address_1, cap: b.postcode, citta: b.city, provincia: b.state, nazione: b.country || "IT" },
    righe: (p.line_items || []).map((l: any) => ({ sku: l.sku, quantita: Number(l.quantity), nome: l.name,
      prezzo: Math.round((Number(l.subtotal) / Math.max(1, Number(l.quantity))) * 100) })),
  };
}

export function daMagento(p: any): OrdineEsterno {
  const a = p.extension_attributes?.shipping_assignments?.[0]?.shipping?.address || p.billing_address || {};
  return {
    riferimento: String(p.entity_id ?? p.increment_id), cliente: { email: p.customer_email, nome: [p.customer_firstname, p.customer_lastname].filter(Boolean).join(" ") || "Cliente Magento",
      indirizzo: a.street?.join?.(" ") ?? a.street, cap: a.postcode, citta: a.city, provincia: a.region_code, nazione: a.country_id || "IT" },
    righe: (p.items || []).filter((l: any) => !l.parent_item_id).map((l: any) => ({ sku: l.sku, quantita: Number(l.qty_ordered), nome: l.name,
      prezzo: Math.round(Number(l.price) * 100) })),
  };
}

/** Importa un ordine dal canale e-commerce: idempotente (stesso ordine = nessun duplicato). */
export async function importaOrdine(aziendaId: string, canale: "shopify" | "woocommerce" | "magento", e: OrdineEsterno) {
  const esiste = await db.ordineVendita.findUnique({ where: { aziendaId_canale_riferimentoEsterno: { aziendaId, canale, riferimentoEsterno: e.riferimento } } });
  if (esiste) return { ordine: esiste, nuovo: false, avvisi: [] as string[] };
  const avvisi: string[] = [];
  const righe: { varianteId: string; quantita: number; prezzoUnitario: number; sconto: number }[] = [];
  for (const r of e.righe) {
    const v = r.sku ? await db.variante.findFirst({ where: { aziendaId, sku: r.sku } }) : null;
    if (!v) { avvisi.push(`SKU «${r.sku || r.nome}» non presente a catalogo: riga ignorata`); continue; }
    righe.push({ varianteId: v.id, quantita: r.quantita, prezzoUnitario: r.prezzo, sconto: 0 });
  }
  if (!righe.length) throw nonValido(`Nessun articolo dell'ordine ${e.riferimento} corrisponde al catalogo`);
  let cliente = e.cliente.email ? await db.cliente.findFirst({ where: { aziendaId, email: e.cliente.email } }) : null;
  if (!cliente) cliente = await db.cliente.create({ data: {
    aziendaId, tipo: "privato", ragioneSociale: e.cliente.nome, email: e.cliente.email, indirizzo: e.cliente.indirizzo, cap: e.cliente.cap,
    citta: e.cliente.citta, provincia: e.cliente.provincia, nazione: e.cliente.nazione || "IT", canale, giorniPagamento: 0,
  } });
  const ordine = await db.$transaction(async (tx) => {
    const o = await creaOrdine(tx, aziendaId, { clienteId: cliente!.id, righe, canale, riferimentoEsterno: e.riferimento,
      note: avvisi.length ? avvisi.join("\n") : `Ordine importato da ${canale}` });
    try {
      return (await cambiaStato(tx, aziendaId, o.id, "confermato")).ordine;  // l'ordine e-commerce è già pagato: si impegna subito la merce
    } catch (err) {
      avvisi.push(err instanceof Error ? `Non confermato automaticamente: ${err.message}` : "Non confermato automaticamente");
      return o;
    }
  });
  return { ordine, nuovo: true, avvisi };
}

/** Riceve un webhook, verifica la firma e importa l'ordine. */
export async function webhookEcommerce(aziendaId: string, tipo: "shopify" | "woocommerce", corpo: Buffer, firma?: string, argomento?: string) {
  const i = await db.integrazione.findUnique({ where: { aziendaId_tipo: { aziendaId, tipo } } });
  if (!i || !i.attiva) throw nonTrovato("Integrazione");
  if (!verificaFirmaShopify(decifra(i.webhookSecret)!, corpo, firma)) {
    await registraEvento(i.id, "in", argomento || "webhook", "errore", "Firma non valida");
    throw new HttpError(401, "Firma del webhook non valida");
  }
  const p = JSON.parse(corpo.toString("utf8"));
  if (tipo === "woocommerce" && !p.id) { // ping di verifica di WooCommerce alla creazione del webhook
    await registraEvento(i.id, "in", "ping", "ignorato");
    return { ok: true };
  }
  const r = await importaOrdine(aziendaId, tipo, tipo === "shopify" ? daShopify(p) : daWoo(p));
  await registraEvento(i.id, "in", argomento || "ordine", r.avvisi.length ? "ignorato" : "ok",
    { ordine: r.ordine.numero, nuovo: r.nuovo, avvisi: r.avvisi });
  await db.integrazione.update({ where: { id: i.id }, data: { ultimaSincronizzazione: new Date() } });
  return { ok: true, ordine: r.ordine.numero, nuovo: r.nuovo, avvisi: r.avvisi };
}

/** Magento: importa gli ordini recenti tramite REST API (in demo: un ordine di esempio). */
export async function importaMagento(aziendaId: string) {
  const i = await integrazione(aziendaId, "magento");
  const c = leggiConfigurazione(i);
  let ordini: OrdineEsterno[];
  if (modalita(i) === "reale") {
    const r = await chiamata(`${c.url.replace(/\/$/, "")}/rest/V1/orders?searchCriteria[pageSize]=50&searchCriteria[sortOrders][0][field]=created_at&searchCriteria[sortOrders][0][direction]=DESC`, {
      headers: { Authorization: `Bearer ${c.token}` },
    });
    ordini = (r.items || []).map(daMagento);
  } else {
    const v = await db.variante.findFirst({ where: { aziendaId, attivo: true, prodotto: { gestioneSeriali: false, gestioneLotti: false } }, include: { prodotto: true } });
    ordini = v ? [daMagento({ entity_id: `DEMO-${Date.now()}`, customer_email: "cliente.magento@example.com", customer_firstname: "Cliente", customer_lastname: "Magento",
      billing_address: { street: ["Via Roma 1"], postcode: "20121", city: "Milano", region_code: "MI", country_id: "IT" },
      items: [{ sku: v.sku, qty_ordered: 1, name: v.prodotto.nome, price: v.prodotto.prezzoVendita / 100 }] })] : [];
  }
  const esiti = [];
  for (const o of ordini) {
    try { esiti.push(await importaOrdine(aziendaId, "magento", o)); } catch (e) { esiti.push({ errore: e instanceof Error ? e.message : String(e) }); }
  }
  await registraEvento(i.id, "in", "import_ordini", "ok", { importati: esiti.length, modalita: modalita(i) });
  await db.integrazione.update({ where: { id: i.id }, data: { ultimaSincronizzazione: new Date() } });
  return { modalita: modalita(i), esiti };
}

/** Sincronizza le giacenze disponibili verso i negozi online collegati. */
export async function sincronizzaGiacenze(aziendaId: string, tipo: "shopify" | "woocommerce" | "magento") {
  const i = await integrazione(aziendaId, tipo);
  const c = leggiConfigurazione(i);
  const varianti = await db.variante.findMany({ where: { aziendaId, attivo: true }, include: { giacenze: true } });
  const dati = varianti.map((v) => ({ sku: v.sku, disponibile: Math.max(0, Math.floor(v.giacenze.reduce((s, g) => s + g.quantita - g.riservata, 0))) }));
  const esiti: { sku: string; disponibile: number; esito: string }[] = [];
  if (modalita(i) === "demo") {
    for (const d of dati) esiti.push({ ...d, esito: "simulato" });
  } else {
    for (const d of dati) {
      try {
        if (tipo === "woocommerce") {
          const auth = "Basic " + Buffer.from(`${c.consumerKey}:${c.consumerSecret}`).toString("base64");
          const base = `${c.url.replace(/\/$/, "")}/wp-json/wc/v3`;
          const trovati = await chiamata(`${base}/products?sku=${encodeURIComponent(d.sku)}`, { headers: { Authorization: auth } }) as unknown as any[];
          if (!trovati?.[0]) { esiti.push({ ...d, esito: "non presente nel negozio" }); continue; }
          await chiamata(`${base}/products/${trovati[0].id}`, { method: "PUT", headers: { Authorization: auth, "Content-Type": "application/json" },
            body: JSON.stringify({ manage_stock: true, stock_quantity: d.disponibile }) });
        } else if (tipo === "magento") {
          await chiamata(`${c.url.replace(/\/$/, "")}/rest/V1/products/${encodeURIComponent(d.sku)}/stockItems/1`, { method: "PUT",
            headers: { Authorization: `Bearer ${c.token}`, "Content-Type": "application/json" },
            body: JSON.stringify({ stockItem: { qty: d.disponibile, is_in_stock: d.disponibile > 0 } }) });
        } else {
          const gql = (query: string, variables: object) => chiamata(`https://${c.negozio}/admin/api/2024-10/graphql.json`, { method: "POST",
            headers: { "X-Shopify-Access-Token": c.token, "Content-Type": "application/json" }, body: JSON.stringify({ query, variables }) });
          const r = await gql(`query($q:String!){productVariants(first:1,query:$q){nodes{inventoryItem{id}}}}`, { q: `sku:${d.sku}` });
          const item = r.data?.productVariants?.nodes?.[0]?.inventoryItem?.id;
          if (!item || !c.locationId) { esiti.push({ ...d, esito: "non presente nel negozio" }); continue; }
          await gql(`mutation($i:InventorySetQuantitiesInput!){inventorySetQuantities(input:$i){userErrors{message}}}`, { i: {
            name: "available", reason: "correction", ignoreCompareQuantity: true,
            quantities: [{ inventoryItemId: item, locationId: `gid://shopify/Location/${c.locationId}`, quantity: d.disponibile }] } });
        }
        esiti.push({ ...d, esito: "aggiornato" });
      } catch (e) {
        esiti.push({ ...d, esito: e instanceof Error ? e.message : "errore" });
      }
    }
  }
  await registraEvento(i.id, "out", "sincronizza_giacenze", esiti.some((e) => !["aggiornato", "simulato", "non presente nel negozio"].includes(e.esito)) ? "errore" : "ok",
    { articoli: esiti.length, modalita: modalita(i) });
  await db.integrazione.update({ where: { id: i.id }, data: { ultimaSincronizzazione: new Date() } });
  return { modalita: modalita(i), esiti };
}

// ── Pagamenti: Stripe e PayPal ──────────────────────────────────────────────

const CHIAVE_DEMO = new TextEncoder().encode(config.jwtSecret + ":pagamenti-demo");

export async function linkPagamento(aziendaId: string, scadenzaId: string, provider: "stripe" | "paypal") {
  const s = await db.scadenza.findFirst({ where: { id: scadenzaId, documento: { aziendaId } }, include: { documento: { include: { cliente: true } } } });
  if (!s) throw nonTrovato("Scadenza");
  if (s.pagata) throw nonValido("La scadenza risulta già pagata");
  const i = await integrazione(aziendaId, provider);
  const c = leggiConfigurazione(i);
  const descrizione = `Fattura ${s.documento.numero} - ${s.documento.cliente.ragioneSociale}`;
  let url: string;
  if (modalita(i) === "demo") {
    const token = await new SignJWT({ scadenzaId, provider, aziendaId }).setProtectedHeader({ alg: "HS256" }).setExpirationTime("14d").sign(CHIAVE_DEMO);
    url = `${config.urlPubblico}/paga/${token}`;
  } else if (provider === "stripe") {
    const form = new URLSearchParams({
      mode: "payment", "line_items[0][quantity]": "1", "line_items[0][price_data][currency]": "eur",
      "line_items[0][price_data][unit_amount]": String(s.importo), "line_items[0][price_data][product_data][name]": descrizione,
      success_url: `${config.urlPubblico}/paga/esito?stato=ok`, cancel_url: `${config.urlPubblico}/paga/esito?stato=annullato`,
      "metadata[scadenzaId]": s.id, "metadata[aziendaId]": aziendaId, ...(s.documento.cliente.email ? { customer_email: s.documento.cliente.email } : {}),
    });
    const r = await chiamata(`${URL_BASE.stripe}/v1/checkout/sessions`, { method: "POST",
      headers: { Authorization: `Bearer ${c.secretKey}`, "Content-Type": "application/x-www-form-urlencoded" }, body: form.toString() });
    url = r.url;
  } else {
    const base = URL_BASE.paypal(c.ambiente);
    const tok = await chiamata(`${base}/v1/oauth2/token`, { method: "POST",
      headers: { Authorization: "Basic " + Buffer.from(`${c.clientId}:${c.clientSecret}`).toString("base64"), "Content-Type": "application/x-www-form-urlencoded" },
      body: "grant_type=client_credentials" });
    const r = await chiamata(`${base}/v2/checkout/orders`, { method: "POST",
      headers: { Authorization: `Bearer ${tok.access_token}`, "Content-Type": "application/json" },
      body: JSON.stringify({ intent: "CAPTURE", purchase_units: [{ reference_id: s.id, custom_id: `${aziendaId}:${s.id}`, description: descrizione,
        amount: { currency_code: "EUR", value: decimale(s.importo) } }],
        application_context: { return_url: `${config.urlPubblico}/paga/esito?stato=ok`, cancel_url: `${config.urlPubblico}/paga/esito?stato=annullato` } }) });
    url = (r.links || []).find((l: any) => l.rel === "approve")?.href;
  }
  await db.scadenza.update({ where: { id: s.id }, data: { linkPagamento: url } });
  await registraEvento(i.id, "out", "link_pagamento", "ok", { scadenza: s.id, modalita: modalita(i) });
  return { url, modalita: modalita(i) };
}

/** Pagina di pagamento dimostrativa (nessun addebito reale). */
export async function datiPagamentoDemo(token: string) {
  let payload;
  try { ({ payload } = await jwtVerify(token, CHIAVE_DEMO)); } catch { throw new HttpError(404, "Link di pagamento non valido o scaduto"); }
  const s = await db.scadenza.findUnique({ where: { id: payload.scadenzaId as string }, include: { documento: { include: { cliente: true, azienda: true } } } });
  if (!s) throw nonTrovato("Pagamento");
  return { s, provider: payload.provider as string, aziendaId: payload.aziendaId as string };
}

export async function confermaPagamentoDemo(token: string) {
  const { s, provider, aziendaId } = await datiPagamentoDemo(token);
  const i = await integrazione(aziendaId, provider as TipoIntegrazione);
  await db.$transaction((tx) => registraPagamento(tx, aziendaId, s.id, { metodo: provider, riferimento: `demo_${tokenCasuale(6)}` }));
  await registraEvento(i.id, "in", "pagamento_completato", "ok", { scadenza: s.id, modalita: "demo" });
  return { ok: true };
}

export async function webhookStripe(aziendaId: string, corpo: Buffer, firma?: string) {
  const i = await db.integrazione.findUnique({ where: { aziendaId_tipo: { aziendaId, tipo: "stripe" } } });
  if (!i) throw nonTrovato("Integrazione");
  const segreto = leggiConfigurazione(i).webhookSecret;
  if (!segreto || !verificaFirmaStripe(segreto, corpo, firma)) {
    await registraEvento(i.id, "in", "webhook", "errore", "Firma non valida");
    throw new HttpError(401, "Firma del webhook non valida");
  }
  const evento = JSON.parse(corpo.toString("utf8"));
  if (evento.type !== "checkout.session.completed") { await registraEvento(i.id, "in", evento.type, "ignorato"); return { ok: true }; }
  const scadenzaId = evento.data?.object?.metadata?.scadenzaId;
  if (!scadenzaId) { await registraEvento(i.id, "in", evento.type, "ignorato", "Pagamento non collegato a Nexora"); return { ok: true }; }
  await db.$transaction((tx) => registraPagamento(tx, aziendaId, scadenzaId, { metodo: "stripe", riferimento: evento.data.object.payment_intent || evento.data.object.id }));
  await registraEvento(i.id, "in", evento.type, "ok", { scadenza: scadenzaId });
  return { ok: true };
}

/** PayPal: la firma si verifica chiedendo conferma a PayPal (API gratuita verify-webhook-signature). */
export async function webhookPaypal(aziendaId: string, corpo: Buffer, intestazioni: Record<string, string | undefined>) {
  const i = await db.integrazione.findUnique({ where: { aziendaId_tipo: { aziendaId, tipo: "paypal" } } });
  if (!i) throw nonTrovato("Integrazione");
  const c = leggiConfigurazione(i);
  if (modalita(i) !== "reale" || !c.webhookId) throw new HttpError(400, "Configura client e webhook ID di PayPal");
  const evento = JSON.parse(corpo.toString("utf8"));
  const base = URL_BASE.paypal(c.ambiente);
  const tok = await chiamata(`${base}/v1/oauth2/token`, { method: "POST",
    headers: { Authorization: "Basic " + Buffer.from(`${c.clientId}:${c.clientSecret}`).toString("base64"), "Content-Type": "application/x-www-form-urlencoded" },
    body: "grant_type=client_credentials" });
  const v = await chiamata(`${base}/v1/notifications/verify-webhook-signature`, { method: "POST",
    headers: { Authorization: `Bearer ${tok.access_token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ auth_algo: intestazioni["paypal-auth-algo"], cert_url: intestazioni["paypal-cert-url"], transmission_id: intestazioni["paypal-transmission-id"],
      transmission_sig: intestazioni["paypal-transmission-sig"], transmission_time: intestazioni["paypal-transmission-time"], webhook_id: c.webhookId, webhook_event: evento }) });
  if (v.verification_status !== "SUCCESS") { await registraEvento(i.id, "in", "webhook", "errore", "Firma non valida"); throw new HttpError(401, "Firma del webhook non valida"); }
  if (!["CHECKOUT.ORDER.APPROVED", "PAYMENT.CAPTURE.COMPLETED"].includes(evento.event_type)) { await registraEvento(i.id, "in", evento.event_type, "ignorato"); return { ok: true }; }
  const custom = evento.resource?.custom_id || evento.resource?.purchase_units?.[0]?.custom_id || "";
  const [az, scadenzaId] = String(custom).split(":");
  if (az !== aziendaId || !scadenzaId) { await registraEvento(i.id, "in", evento.event_type, "ignorato"); return { ok: true }; }
  await db.$transaction((tx) => registraPagamento(tx, aziendaId, scadenzaId, { metodo: "paypal", riferimento: evento.resource?.id }));
  await registraEvento(i.id, "in", evento.event_type, "ok", { scadenza: scadenzaId });
  return { ok: true };
}

// ── Spedizioni: GLS e DHL ───────────────────────────────────────────────────

export const URL_TRACCIAMENTO: Record<string, (t: string) => string> = {
  gls: (t) => `https://gls-group.com/IT/it/servizi-online/ricerca-spedizioni?match=${t}`,
  dhl: (t) => `https://www.dhl.com/it-it/home/tracciabilita.html?tracking-id=${t}`,
};

function numeroTracking(corriere: string) {
  const cifre = (n: number) => Array.from({ length: n }, () => Math.floor(Math.random() * 10)).join("");
  return corriere === "dhl" ? `JD0146${cifre(14)}` : cifre(11);
}

/** Crea la spedizione: con il corriere reale si usa il numero restituito dal portale, altrimenti se ne genera uno dimostrativo. */
export async function creaSpedizione(aziendaId: string, ordineId: string, dati: { corriere: "gls" | "dhl" | "ritiro"; colli?: number; pesoKg?: number; tracking?: string }) {
  const o = await db.ordineVendita.findFirst({ where: { id: ordineId, aziendaId } });
  if (!o) throw nonTrovato("Ordine");
  const i = dati.corriere === "ritiro" ? null : await integrazione(aziendaId, dati.corriere);
  const tracking = dati.tracking?.trim() || (dati.corriere === "ritiro" ? `RITIRO-${o.numero}` : numeroTracking(dati.corriere));
  const s = await db.spedizione.create({ data: {
    ordineId, corriere: dati.corriere, tracking, colli: dati.colli ?? 1, pesoKg: dati.pesoKg ?? 1,
    modalita: dati.tracking ? "reale" : "demo", eventi: JSON.stringify([{ data: new Date(), stato: "creata", descrizione: "Spedizione registrata" }]),
  } });
  await db.ordineVendita.update({ where: { id: ordineId }, data: { corriere: dati.corriere, tracking } });
  if (i) await registraEvento(i.id, "out", "spedizione", "ok", { ordine: o.numero, tracking });
  return s;
}

/** Stato della spedizione: DHL reale tramite Shipment Tracking API (gratuita), altrimenti avanzamento simulato. */
export async function tracciamento(aziendaId: string, spedizioneId: string) {
  const s = await db.spedizione.findFirst({ where: { id: spedizioneId, ordine: { aziendaId } } });
  if (!s) throw nonTrovato("Spedizione");
  let eventi = JSON.parse(s.eventi || "[]") as { data: string | Date; stato: string; descrizione: string }[];
  let stato = s.stato;
  const dhl = s.corriere === "dhl" ? await integrazione(aziendaId, "dhl") : null;
  if (dhl && modalita(dhl) === "reale" && s.modalita === "reale") {
    const r = await chiamata(`${URL_BASE.dhl}/track/shipments?trackingNumber=${encodeURIComponent(s.tracking)}`, { headers: { "DHL-API-Key": leggiConfigurazione(dhl).apiKey } });
    const sh = r.shipments?.[0];
    if (sh) {
      eventi = (sh.events || []).map((e: any) => ({ data: e.timestamp, stato: e.statusCode, descrizione: e.description || e.status }));
      stato = sh.status?.statusCode === "delivered" ? "consegnata" : "in_transito";
    }
  } else if (s.corriere !== "ritiro") {
    const ore = (Date.now() - s.createdAt.getTime()) / 3600_000;
    const tappe = [
      { dopo: 0, stato: "creata", descrizione: "Spedizione registrata" },
      { dopo: 6, stato: "in_transito", descrizione: "Ritirata dal corriere" },
      { dopo: 24, stato: "in_transito", descrizione: "In transito presso l'hub di smistamento" },
      { dopo: 48, stato: "in_transito", descrizione: "In consegna" },
      { dopo: 54, stato: "consegnata", descrizione: "Consegnata al destinatario" },
    ].filter((t) => ore >= t.dopo);
    eventi = tappe.map((t) => ({ data: new Date(s.createdAt.getTime() + t.dopo * 3600_000), stato: t.stato, descrizione: t.descrizione }));
    stato = tappe[tappe.length - 1].stato;
  }
  if (stato !== s.stato || JSON.stringify(eventi) !== s.eventi) {
    await db.spedizione.update({ where: { id: s.id }, data: { stato, eventi: JSON.stringify(eventi) } });
  }
  return { ...s, stato, eventi, link: URL_TRACCIAMENTO[s.corriere]?.(s.tracking) ?? null };
}

export async function etichettaPdf(aziendaId: string, spedizioneId: string): Promise<Buffer> {
  const s = await db.spedizione.findFirst({ where: { id: spedizioneId, ordine: { aziendaId } }, include: { ordine: { include: { cliente: true, azienda: true } } } });
  if (!s) throw nonTrovato("Spedizione");
  const codice = await bwipjs.toBuffer({ bcid: "code128", text: s.tracking, scale: 3, height: 14, includetext: true, textxalign: "center" });
  return new Promise((resolve, reject) => {
    const pdf = new PDFDocument({ size: [283, 425], margin: 16 }); // 10 × 15 cm
    const parti: Buffer[] = [];
    pdf.on("data", (c: Buffer) => parti.push(c));
    pdf.on("end", () => resolve(Buffer.concat(parti)));
    pdf.on("error", reject);
    const a = s.ordine.azienda, c = s.ordine.cliente;
    pdf.font("Helvetica-Bold").fontSize(18).text(s.corriere.toUpperCase(), { align: "right" });
    if (s.modalita === "demo") pdf.fontSize(8).fillColor("#c00").text("ETICHETTA DIMOSTRATIVA - NON VALIDA PER LA SPEDIZIONE", { align: "right" }).fillColor("#000");
    pdf.moveDown(0.5).font("Helvetica").fontSize(8).text("MITTENTE").fontSize(9).text(`${a.ragioneSociale}\n${a.indirizzo}\n${a.cap} ${a.citta} (${a.provincia})`);
    pdf.moveDown(0.6).fontSize(8).text("DESTINATARIO").font("Helvetica-Bold").fontSize(12).text(c.ragioneSociale)
      .font("Helvetica").fontSize(10).text(s.ordine.indirizzoSpedizione || `${c.indirizzo ?? ""}\n${c.cap ?? ""} ${c.citta ?? ""}`);
    pdf.moveDown(0.6).fontSize(9).text(`Ordine ${s.ordine.numero} · Colli ${s.colli} · ${s.pesoKg.toFixed(1)} kg`);
    pdf.image(codice, 16, 300, { width: 251 });
    pdf.end();
  });
}
