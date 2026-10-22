// Integrazioni (Shopify, WooCommerce, Magento, Stripe, PayPal, DHL) e SSO OpenID Connect, con servizi esterni simulati in locale. © Roberto Di Flumeri
import { createHash } from "node:crypto";
import Fastify, { type FastifyInstance } from "fastify";
import { exportJWK, generateKeyPair, SignJWT } from "jose";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { hmac } from "../src/lib/crypto.js";
import { api, azienda, carica, cliente, nuovaApp, prodotto, utenteConRuolo } from "./aiuti.js";

let app: FastifyInstance;
let finto: FastifyInstance;
const richieste: { metodo: string; url: string; corpo: any; auth?: string }[] = [];
const URL_FINTO = "http://127.0.0.1:8399";
let chiaviOidc: Awaited<ReturnType<typeof generateKeyPair>>;
let ultimaSfida = "";
let ultimoNonce = "";

beforeAll(async () => {
  app = await nuovaApp();
  chiaviOidc = await generateKeyPair("RS256");
  const jwk = { ...(await exportJWK(chiaviOidc.publicKey)), kid: "k1", alg: "RS256", use: "sig" };
  finto = Fastify();
  finto.addContentTypeParser("application/x-www-form-urlencoded", { parseAs: "string" }, (_r, b: string, f) => f(null, Object.fromEntries(new URLSearchParams(b))));
  finto.addHook("onRequest", async (req) => { if (req.url.startsWith("/oidc/autorizza")) return; });
  finto.addHook("preHandler", async (req) => { richieste.push({ metodo: req.method, url: req.url, corpo: req.body, auth: req.headers.authorization }); });
  // Stripe
  finto.post("/v1/checkout/sessions", async (req) => ({ id: "cs_test_1", url: `https://checkout.stripe.com/c/pay/cs_test_1?importo=${(req.body as any)["line_items[0][price_data][unit_amount]"]}` }));
  // PayPal
  finto.post("/v1/oauth2/token", async () => ({ access_token: "tok-paypal" }));
  finto.post("/v2/checkout/orders", async () => ({ id: "PP-1", links: [{ rel: "approve", href: "https://www.sandbox.paypal.com/checkoutnow?token=PP-1" }] }));
  // DHL tracking
  finto.get("/track/shipments", async () => ({ shipments: [{ status: { statusCode: "delivered" }, events: [{ timestamp: "2026-10-20T10:00:00Z", statusCode: "delivered", description: "Consegnato" }] }] }));
  // WooCommerce
  finto.get("/woo/wp-json/wc/v3/products", async (req) => ((req.query as any).sku?.startsWith("SKU-") ? [{ id: 77 }] : []));
  finto.put("/woo/wp-json/wc/v3/products/:id", async () => ({ ok: true }));
  // provider OpenID Connect
  finto.get("/oidc/.well-known/openid-configuration", async () => ({
    issuer: `${URL_FINTO}/oidc`, authorization_endpoint: `${URL_FINTO}/oidc/autorizza`, token_endpoint: `${URL_FINTO}/oidc/token`, jwks_uri: `${URL_FINTO}/oidc/jwks`,
  }));
  finto.get("/oidc/jwks", async () => ({ keys: [jwk] }));
  finto.post("/oidc/token", async (req, reply) => {
    const b = req.body as Record<string, string>;
    // PKCE: il verifier deve corrispondere alla challenge ricevuta all'inizio del flusso
    if (createHash("sha256").update(b.code_verifier).digest("base64url") !== ultimaSfida) return reply.status(400).send({ error: "invalid_grant" });
    const id_token = await new SignJWT({ email: b.code === "codice-ospite" ? "ospite@altrove.it" : "sso.utente@example.com", nonce: ultimoNonce })
      .setProtectedHeader({ alg: "RS256", kid: "k1" }).setIssuer(`${URL_FINTO}/oidc`).setAudience("nexora-client").setSubject("utente-123")
      .setIssuedAt().setExpirationTime("5m").sign(chiaviOidc.privateKey);
    return { id_token, access_token: "x", token_type: "Bearer" };
  });
  await finto.listen({ port: 8399, host: "127.0.0.1" });
});
afterAll(async () => { await app.close(); await finto.close(); });

async function attiva(a: { h: any }, tipo: string, valori: Record<string, string> = {}) {
  const r = await api(app, "PUT", `/api/integrazioni/${tipo}`, a.h, { attiva: true, valori });
  expect(r.status).toBe(200);
  return r.json;
}

const inviaWebhook = (tipo: string, aziendaId: string, corpo: object, intestazioni: Record<string, string>) =>
  app.inject({ method: "POST", url: `/api/webhooks/${tipo}/${aziendaId}`, headers: { "content-type": "application/json", ...intestazioni }, payload: JSON.stringify(corpo) });

describe("e-commerce: webhook firmati e import ordini", () => {
  it("Shopify: firma verificata, ordine importato e confermato, nessun duplicato", async () => {
    const a = await azienda(app);
    const p = await prodotto(app, a.h, { sku: "CAFFE-250", prezzoVendita: 800 });
    await carica(app, a.h, p.varianteId, a.magazzinoId, 10);
    const i = await attiva(a, "shopify");
    expect(i.modalita).toBe("demo");
    expect(i.webhookUrl).toContain(`/api/webhooks/shopify/${a.aziendaId}`);
    // il segreto di firma resta riservato a chi configura le integrazioni
    const lettore = await utenteConRuolo(app, a.h, "lettura");
    expect((await api(app, "GET", "/api/integrazioni", lettore)).json.find((x: any) => x.tipo === "shopify").webhookSecret).toBeNull();
    const ordine = { id: 5551, email: "anna@negozio.it", taxes_included: true, customer: { first_name: "Anna", last_name: "Verdi" },
      shipping_address: { address1: "Via Po 3", zip: "10123", city: "Torino", province_code: "TO", country_code: "IT" },
      line_items: [{ sku: "CAFFE-250", quantity: 3, price: "9.76", title: "Caffè", tax_lines: [{ rate: 0.22 }] }, { sku: "SCONOSCIUTO", quantity: 1, price: "5.00", title: "Altro" }] };
    const firma = (corpo: object) => hmac("sha256", i.webhookSecret, Buffer.from(JSON.stringify(corpo)), "base64");
    expect((await inviaWebhook("shopify", a.aziendaId, ordine, { "x-shopify-hmac-sha256": "firma-falsa" })).statusCode).toBe(401);
    const r = await inviaWebhook("shopify", a.aziendaId, ordine, { "x-shopify-hmac-sha256": firma(ordine), "x-shopify-topic": "orders/create" });
    expect(r.statusCode).toBe(200);
    expect(r.json().nuovo).toBe(true);
    expect(r.json().avvisi[0]).toContain("SCONOSCIUTO");
    const ordini = (await api(app, "GET", "/api/ordini?canale=shopify", a.h)).json;
    expect(ordini).toHaveLength(1);
    expect(ordini[0].stato).toBe("confermato");
    expect(ordini[0].imponibile).toBe(3 * 800); // prezzo IVA inclusa scorporato
    const dup = await inviaWebhook("shopify", a.aziendaId, ordine, { "x-shopify-hmac-sha256": firma(ordine) });
    expect(dup.json().nuovo).toBe(false);
    expect((await api(app, "GET", "/api/ordini?canale=shopify", a.h)).json).toHaveLength(1);
    const clienti = (await api(app, "GET", "/api/clienti?q=Verdi", a.h)).json;
    expect(clienti[0].canale).toBe("shopify");
    const eventi = (await api(app, "GET", "/api/integrazioni/shopify/eventi", a.h)).json;
    expect(eventi.map((e: any) => e.esito)).toEqual(expect.arrayContaining(["errore", "ignorato"]));
  });

  it("WooCommerce: ping di verifica, ordine di prova firmato e integrazione disattiva", async () => {
    const a = await azienda(app);
    const p = await prodotto(app, a.h);
    await carica(app, a.h, p.varianteId, a.magazzinoId, 5);
    expect((await inviaWebhook("woocommerce", a.aziendaId, { webhook_id: 1 }, { "x-wc-webhook-signature": "x" })).statusCode).toBe(404); // non attiva
    const i = await attiva(a, "woocommerce");
    const ping = { webhook_id: 9 };
    const r = await inviaWebhook("woocommerce", a.aziendaId, ping, { "x-wc-webhook-signature": hmac("sha256", i.webhookSecret, Buffer.from(JSON.stringify(ping)), "base64") });
    expect(r.json().ok).toBe(true);
    const prova = await api(app, "POST", "/api/integrazioni/woocommerce/ordine-di-prova", a.h);
    expect(prova.json.nuovo).toBe(true);
    expect((await api(app, "GET", "/api/ordini?canale=woocommerce", a.h)).json[0].stato).toBe("confermato");
  });

  it("Magento in modalità dimostrativa e sincronizzazione giacenze", async () => {
    const a = await azienda(app);
    const p = await prodotto(app, a.h);
    await carica(app, a.h, p.varianteId, a.magazzinoId, 7);
    await attiva(a, "magento");
    const imp = (await api(app, "POST", "/api/integrazioni/magento/importa", a.h)).json;
    expect(imp.modalita).toBe("demo");
    expect((await api(app, "GET", "/api/ordini?canale=magento", a.h)).json).toHaveLength(1);
    const s = (await api(app, "POST", "/api/integrazioni/magento/sincronizza", a.h)).json;
    expect(s.esiti[0]).toMatchObject({ sku: p.sku, disponibile: 6, esito: "simulato" });
  });

  it("WooCommerce reale: aggiorna le giacenze tramite REST API con autenticazione", async () => {
    const a = await azienda(app);
    const p = await prodotto(app, a.h);
    await carica(app, a.h, p.varianteId, a.magazzinoId, 12);
    const i = await attiva(a, "woocommerce", { url: `${URL_FINTO}/woo`, consumerKey: "ck_123", consumerSecret: "cs_456" });
    expect(i.modalita).toBe("reale");
    expect(i.campi.find((c: any) => c.chiave === "consumerSecret").valore).toBe("••••_456"); // segreto mascherato
    const s = (await api(app, "POST", "/api/integrazioni/woocommerce/sincronizza", a.h)).json;
    expect(s.esiti[0].esito).toBe("aggiornato");
    const put = richieste.find((r) => r.metodo === "PUT" && r.url.includes("/products/77"));
    expect(put!.corpo).toEqual({ manage_stock: true, stock_quantity: 12 });
    expect(put!.auth).toBe("Basic " + Buffer.from("ck_123:cs_456").toString("base64"));
    // salvare di nuovo il segreto mascherato non lo sovrascrive
    await api(app, "PUT", "/api/integrazioni/woocommerce", a.h, { valori: { consumerSecret: "••••_456" } });
    expect((await api(app, "POST", "/api/integrazioni/woocommerce/sincronizza", a.h)).json.esiti[0].esito).toBe("aggiornato");
  });
});

async function scadenzaAperta(a: { h: any }) {
  const c = await cliente(app, a.h);
  const f = (await api(app, "POST", "/api/documenti", a.h, { tipo: "fattura", clienteId: c.id, righe: [{ descrizione: "Servizio", quantita: 1, prezzoUnitario: 10000, aliquotaIva: 22 }] })).json;
  return { fattura: f, scadenza: (await api(app, "POST", `/api/documenti/${f.id}/emetti`, a.h)).json.scadenze[0] };
}

describe("pagamenti", () => {
  it("link dimostrativo: pagina di pagamento e incasso registrato", async () => {
    const a = await azienda(app);
    const { fattura, scadenza } = await scadenzaAperta(a);
    const l = (await api(app, "POST", `/api/scadenze/${scadenza.id}/link`, a.h, { provider: "stripe" })).json;
    expect(l.modalita).toBe("demo");
    const token = l.url.split("/paga/")[1];
    const pagina = (await api(app, "GET", `/api/pagamenti-demo/${token}`)).json;
    expect(pagina.importo).toBe(12200);
    expect((await api(app, "POST", `/api/pagamenti-demo/${token}/conferma`)).json.ok).toBe(true);
    const doc = (await api(app, "GET", `/api/documenti/${fattura.id}`, a.h)).json;
    expect(doc.stato).toBe("pagata");
    expect(doc.scadenze[0].metodo).toBe("stripe");
    expect((await api(app, "GET", "/api/pagamenti-demo/token-falso")).status).toBe(404);
  });

  it("Stripe reale: sessione di checkout e webhook firmato (con protezione dai replay)", async () => {
    const a = await azienda(app);
    await attiva(a, "stripe", { secretKey: "sk_test_abc", webhookSecret: "whsec_test" });
    const { fattura, scadenza } = await scadenzaAperta(a);
    const l = (await api(app, "POST", `/api/scadenze/${scadenza.id}/link`, a.h, { provider: "stripe" })).json;
    expect(l.url).toContain("checkout.stripe.com");
    const sess = richieste.find((r) => r.url === "/v1/checkout/sessions")!;
    expect(sess.auth).toBe("Bearer sk_test_abc");
    expect(sess.corpo["metadata[scadenzaId]"]).toBe(scadenza.id);
    const evento = { type: "checkout.session.completed", data: { object: { id: "cs_test_1", payment_intent: "pi_1", metadata: { scadenzaId: scadenza.id } } } };
    const corpo = JSON.stringify(evento);
    const firma = (t: number) => `t=${t},v1=${hmac("sha256", "whsec_test", `${t}.${corpo}`)}`;
    const vecchio = Math.floor(Date.now() / 1000) - 3600;
    expect((await inviaWebhook("stripe", a.aziendaId, evento, { "stripe-signature": firma(vecchio) })).statusCode).toBe(401);
    expect((await inviaWebhook("stripe", a.aziendaId, evento, { "stripe-signature": "t=1,v1=00" })).statusCode).toBe(401);
    const ok = await inviaWebhook("stripe", a.aziendaId, evento, { "stripe-signature": firma(Math.floor(Date.now() / 1000)) });
    expect(ok.statusCode).toBe(200);
    expect((await api(app, "GET", `/api/documenti/${fattura.id}`, a.h)).json.stato).toBe("pagata");
  });

  it("PayPal reale: ordine creato in sandbox con link di approvazione", async () => {
    const a = await azienda(app);
    await attiva(a, "paypal", { clientId: "client-sb", clientSecret: "secret-sb" });
    const { scadenza } = await scadenzaAperta(a);
    const l = (await api(app, "POST", `/api/scadenze/${scadenza.id}/link`, a.h, { provider: "paypal" })).json;
    expect(l.url).toContain("sandbox.paypal.com");
    const ordine = richieste.find((r) => r.url === "/v2/checkout/orders")!;
    expect(ordine.corpo.purchase_units[0].amount).toEqual({ currency_code: "EUR", value: "122.00" });
    expect(ordine.auth).toBe("Bearer tok-paypal");
  });
});

describe("spedizioni DHL", () => {
  it("tracciamento reale tramite Shipment Tracking API", async () => {
    const a = await azienda(app);
    await attiva(a, "dhl", { apiKey: "chiave-dhl" });
    const p = await prodotto(app, a.h);
    const c = await cliente(app, a.h);
    const o = (await api(app, "POST", "/api/ordini", a.h, { clienteId: c.id, righe: [{ varianteId: p.varianteId, quantita: 1 }] })).json;
    const s = (await api(app, "POST", `/api/ordini/${o.id}/spedizioni`, a.h, { corriere: "dhl", tracking: "1234567890" })).json;
    const t = (await api(app, "GET", `/api/spedizioni/${s.id}`, a.h)).json;
    expect(t.stato).toBe("consegnata");
    expect(t.eventi[0].descrizione).toBe("Consegnato");
    expect(richieste.some((r) => r.url.includes("trackingNumber=1234567890"))).toBe(true);
  });
});

describe("SSO OpenID Connect", () => {
  it("accesso con PKCE e verifica dell'id_token; utenti non invitati rifiutati", async () => {
    const a = await azienda(app);
    await api(app, "PATCH", "/api/azienda", a.h, { ssoIssuer: `${URL_FINTO}/oidc`, ssoClientId: "nexora-client", ssoClientSecret: "segreto-sso" });
    expect((await api(app, "GET", "/api/azienda", a.h)).json.ssoClientSecret).toBe("••••••••");
    await api(app, "POST", "/api/utenti", a.h, { nome: "Utente SSO", email: "sso.utente@example.com", ruolo: "vendite" });

    const inizio = await app.inject({ method: "GET", url: "/api/auth/sso/inizia?email=sso.utente@example.com" });
    expect(inizio.statusCode).toBe(302);
    const redirect = new URL(inizio.headers.location as string);
    expect(redirect.origin + redirect.pathname).toBe(`${URL_FINTO}/oidc/autorizza`);
    expect(redirect.searchParams.get("code_challenge_method")).toBe("S256");
    ultimaSfida = redirect.searchParams.get("code_challenge")!;
    ultimoNonce = redirect.searchParams.get("nonce")!;
    const stato = redirect.searchParams.get("state")!;

    const cb = await app.inject({ method: "GET", url: `/api/auth/sso/callback?code=codice-ok&state=${encodeURIComponent(stato)}` });
    expect(cb.statusCode).toBe(302);
    expect(cb.headers.location).toBe("http://localhost:3300/app");
    expect(String(cb.headers["set-cookie"])).toContain("nx_refresh=");

    const ospite = await app.inject({ method: "GET", url: `/api/auth/sso/callback?code=codice-ospite&state=${encodeURIComponent(stato)}` });
    // gli errori riportano alla pagina di accesso con un messaggio leggibile, senza sessione
    expect(ospite.statusCode).toBe(302);
    expect(decodeURIComponent(ospite.headers.location as string)).toContain("/accedi?sso_errore=Nessun utente Nexora attivo");
    expect(ospite.headers["set-cookie"]).toBeUndefined();
    const falso = await app.inject({ method: "GET", url: "/api/auth/sso/callback?code=x&state=falso" });
    expect(decodeURIComponent(falso.headers.location as string)).toContain("Richiesta SSO scaduta");
  });
});
