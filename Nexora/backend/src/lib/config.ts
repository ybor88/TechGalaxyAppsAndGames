// Configurazione da variabili d'ambiente. I segreti mancanti vengono generati al primo avvio
// e salvati in backend/.secrets.json (escluso da git). © Roberto Di Flumeri
import { randomBytes } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

export const BASE_DIR = join(dirname(fileURLToPath(import.meta.url)), "..", "..").replace(/[\\/]dist$/, "");
const FILE_SEGRETI = join(BASE_DIR, ".secrets.json");

function segreti(): { jwtSecret: string; dataKey: string } {
  let dati: Record<string, string> = {};
  if (existsSync(FILE_SEGRETI)) dati = JSON.parse(readFileSync(FILE_SEGRETI, "utf8"));
  let cambiati = false;
  if (!dati.jwtSecret) { dati.jwtSecret = randomBytes(48).toString("base64url"); cambiati = true; }
  if (!dati.dataKey) { dati.dataKey = randomBytes(32).toString("base64"); cambiati = true; }
  if (cambiati) writeFileSync(FILE_SEGRETI, JSON.stringify(dati, null, 2));
  return { jwtSecret: dati.jwtSecret, dataKey: dati.dataKey };
}

const s = process.env.NEXORA_JWT_SECRET && process.env.NEXORA_DATA_KEY
  ? { jwtSecret: process.env.NEXORA_JWT_SECRET, dataKey: process.env.NEXORA_DATA_KEY }
  : segreti();

export const config = {
  porta: Number(process.env.PORT || 8300),
  host: process.env.HOST || "127.0.0.1",
  frontendOrigin: process.env.NEXORA_FRONTEND_ORIGIN || "http://localhost:3300",
  urlPubblico: process.env.NEXORA_URL_PUBBLICO || "http://localhost:3300",
  jwtSecret: s.jwtSecret,
  dataKey: Buffer.from(s.dataKey, "base64"),
  accessTokenMinuti: 15,
  refreshTokenGiorni: 30,
  cookieSecure: process.env.NEXORA_COOKIE_SECURE === "true",
  cartellaFile: process.env.NEXORA_UPLOADS || join(BASE_DIR, "uploads"),
  maxUploadMb: 5,
  rateLimitLoginMinuto: 10,
};
