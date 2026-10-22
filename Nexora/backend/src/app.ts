// Costruzione dell'applicazione Fastify (usata dal server e dai test). © Roberto Di Flumeri
import cookie from "@fastify/cookie";
import cors from "@fastify/cors";
import multipart from "@fastify/multipart";
import Fastify from "fastify";
import { config } from "./lib/config.js";
import { gestoreErrori } from "./lib/errori.js";
import rotteAnagrafiche from "./routes/anagrafiche.js";
import rotteAnalisi from "./routes/analisi.js";
import rotteAuth from "./routes/auth.js";
import rotteCatalogo, { rotteFile } from "./routes/catalogo.js";
import rotteDocumenti from "./routes/documenti.js";
import rotteImpostazioni from "./routes/impostazioni.js";
import rotteIntegrazioni from "./routes/integrazioni.js";
import rotteMagazzino from "./routes/magazzino.js";
import rotteVendite from "./routes/vendite.js";

export async function creaApp(opzioni: { log?: boolean } = {}) {
  const app = Fastify({
    logger: opzioni.log ? { level: "info", redact: ["req.headers.authorization", "req.headers.cookie"] } : false,
    bodyLimit: 2 * 1024 * 1024, trustProxy: true,
    maxParamLength: 600, // i token firmati nei link di pagamento superano il limite predefinito di 100 caratteri
  });
  app.setErrorHandler(gestoreErrori);

  // il corpo grezzo serve per verificare la firma HMAC dei webhook
  app.addContentTypeParser("application/json", { parseAs: "buffer" }, (req, corpo: Buffer, fatto) => {
    req.corpoGrezzo = corpo;
    if (!corpo.length) return fatto(null, {});
    try { fatto(null, JSON.parse(corpo.toString("utf8"))); } catch { fatto(Object.assign(new Error("JSON non valido"), { statusCode: 400 }), undefined); }
  });
  app.addContentTypeParser("application/x-www-form-urlencoded", { parseAs: "string" }, (_req, corpo: string, fatto) =>
    fatto(null, Object.fromEntries(new URLSearchParams(corpo))));

  await app.register(cookie);
  await app.register(cors, { origin: [config.frontendOrigin], credentials: true });
  await app.register(multipart, { limits: { fileSize: config.maxUploadMb * 1024 * 1024, files: 1 } });

  app.addHook("onSend", async (_req, reply) => {
    reply.header("X-Content-Type-Options", "nosniff");
    reply.header("Referrer-Policy", "same-origin");
  });

  app.get("/api/health", async () => ({ stato: "ok", app: "Nexora", versione: "1.0.0" }));
  for (const r of [rotteAuth, rotteImpostazioni, rotteCatalogo, rotteFile, rotteAnagrafiche, rotteMagazzino, rotteVendite, rotteDocumenti, rotteAnalisi, rotteIntegrazioni])
    await app.register(r);
  return app;
}
