// Integrazioni e webhook pubblici (Shopify, WooCommerce, Magento, Stripe, PayPal, GLS, DHL). © Roberto Di Flumeri
import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { decifra, hmac } from "../lib/crypto.js";
import { db } from "../lib/db.js";
import { conflitto, nonTrovato } from "../lib/errori.js";
import { audit, autenticato, controllaRate, puo, richiede } from "../lib/sicurezza.js";
import {
  CAMPI, confermaPagamentoDemo, datiPagamentoDemo, importaMagento, integrazione, salvaConfigurazione, sincronizzaGiacenze,
  TIPI_INTEGRAZIONE, vista, webhookEcommerce, webhookPaypal, webhookStripe,
} from "../services/integrazioni.js";

declare module "fastify" {
  interface FastifyRequest { corpoGrezzo?: Buffer }
}

export default async function rotteIntegrazioni(app: FastifyInstance) {
  // ── Area riservata ───────────────────────────────────────────────────────
  app.register(async (riservata) => {
    riservata.addHook("preHandler", autenticato);

    // il segreto di firma dei webhook lo vede solo chi può configurare le integrazioni
    riservata.get("/api/integrazioni", async (req) => {
      const tutte = await Promise.all(TIPI_INTEGRAZIONE.map(async (t) => vista(await integrazione(req.identita.aziendaId, t))));
      return puo(req, "integrazioni") ? tutte : tutte.map((i) => ({ ...i, webhookSecret: null }));
    });

    riservata.put("/api/integrazioni/:tipo", { preHandler: richiede("integrazioni") }, async (req) => {
      const tipo = z.enum(TIPI_INTEGRAZIONE).parse((req.params as { tipo: string }).tipo);
      const d = z.object({ valori: z.record(z.string().max(500)).default({}), attiva: z.boolean().optional() }).parse(req.body);
      const ammessi = new Set(CAMPI[tipo].map((c) => c.chiave));
      const valori = Object.fromEntries(Object.entries(d.valori).filter(([k]) => ammessi.has(k)));
      const i = await salvaConfigurazione(req.identita.aziendaId, tipo, valori, d.attiva);
      await audit(req, "integrazione_configurata", "integrazione", i.id, `${tipo} attiva=${i.attiva}`);
      return vista(i);
    });

    riservata.get("/api/integrazioni/:tipo/eventi", async (req) => {
      const tipo = z.enum(TIPI_INTEGRAZIONE).parse((req.params as { tipo: string }).tipo);
      const i = await integrazione(req.identita.aziendaId, tipo);
      return db.eventoIntegrazione.findMany({ where: { integrazioneId: i.id }, orderBy: { createdAt: "desc" }, take: 50 });
    });

    riservata.post("/api/integrazioni/:tipo/sincronizza", { preHandler: richiede("integrazioni") }, async (req) => {
      const tipo = z.enum(["shopify", "woocommerce", "magento"]).parse((req.params as { tipo: string }).tipo);
      return sincronizzaGiacenze(req.identita.aziendaId, tipo);
    });

    riservata.post("/api/integrazioni/magento/importa", { preHandler: richiede("integrazioni") }, async (req) => importaMagento(req.identita.aziendaId));

    /** Invia al proprio webhook un ordine di prova firmato correttamente: verifica di funzionamento senza negozio reale. */
    riservata.post("/api/integrazioni/:tipo/ordine-di-prova", { preHandler: richiede("integrazioni") }, async (req) => {
      const tipo = z.enum(["shopify", "woocommerce"]).parse((req.params as { tipo: string }).tipo);
      const i = await integrazione(req.identita.aziendaId, tipo);
      if (!i.attiva) throw conflitto("Attiva prima l'integrazione");
      // un articolo disponibile (anche a lotti: si scalano in FEFO alla spedizione), così l'ordine si conferma davvero
      const candidati = await db.variante.findMany({ where: { aziendaId: req.identita.aziendaId, attivo: true, prodotto: { attivo: true, gestioneSeriali: false } },
        include: { prodotto: true, giacenze: true }, orderBy: { sku: "asc" } });
      const disponibile = (x: (typeof candidati)[number]) => x.giacenze.reduce((s, g) => s + g.quantita - g.riservata, 0);
      const v = candidati.find((x) => disponibile(x) >= 1) ?? candidati[0];
      if (!v) throw nonTrovato("Articolo vendibile a catalogo");
      const id = Date.now();
      const prezzo = ((v.prezzo ?? v.prodotto.prezzoVendita) / 100).toFixed(2);
      const ordine = tipo === "shopify"
        ? { id, email: "cliente.shopify@example.com", taxes_included: false, customer: { first_name: "Cliente", last_name: "Shopify" },
          shipping_address: { address1: "Via Torino 10", zip: "10121", city: "Torino", province_code: "TO", country_code: "IT" },
          line_items: [{ sku: v.sku, quantity: 1, price: prezzo, title: v.prodotto.nome }] }
        : { id, billing: { first_name: "Cliente", last_name: "WooCommerce", email: "cliente.woo@example.com", address_1: "Via Napoli 5", postcode: "80100", city: "Napoli", state: "NA", country: "IT" },
          line_items: [{ sku: v.sku, quantity: 1, subtotal: prezzo, name: v.prodotto.nome }] };
      const corpo = Buffer.from(JSON.stringify(ordine));
      return webhookEcommerce(req.identita.aziendaId, tipo, corpo, hmac("sha256", decifra(i.webhookSecret)!, corpo, "base64"), "ordine_di_prova");
    });
  });

  // ── Webhook pubblici (firma verificata, nessuna sessione) ─────────────────
  app.post("/api/webhooks/:tipo/:aziendaId", async (req, reply) => {
    const { tipo, aziendaId } = req.params as { tipo: string; aziendaId: string };
    controllaRate(`webhook:${aziendaId}:${tipo}`, 120);
    const corpo = req.corpoGrezzo ?? Buffer.from(JSON.stringify(req.body ?? {}));
    const h = req.headers as Record<string, string | undefined>;
    if (tipo === "shopify") return webhookEcommerce(aziendaId, "shopify", corpo, h["x-shopify-hmac-sha256"], h["x-shopify-topic"]);
    if (tipo === "woocommerce") return webhookEcommerce(aziendaId, "woocommerce", corpo, h["x-wc-webhook-signature"], h["x-wc-webhook-topic"]);
    if (tipo === "stripe") return webhookStripe(aziendaId, corpo, h["stripe-signature"]);
    if (tipo === "paypal") return webhookPaypal(aziendaId, corpo, h);
    return reply.status(404).send({ errore: "Webhook sconosciuto" });
  });

  // ── Pagamento dimostrativo (nessun addebito reale) ───────────────────────
  app.get("/api/pagamenti-demo/:token", async (req) => {
    const { s, provider } = await datiPagamentoDemo((req.params as { token: string }).token);
    return { provider, importo: s.importo, pagata: s.pagata, scadenza: s.dataScadenza, documento: s.documento.numero,
      cliente: s.documento.cliente.ragioneSociale, azienda: s.documento.azienda.ragioneSociale };
  });
  app.post("/api/pagamenti-demo/:token/conferma", async (req) => {
    controllaRate(`pagademo:${req.ip}`, 20);
    return confermaPagamentoDemo((req.params as { token: string }).token);
  });
}

