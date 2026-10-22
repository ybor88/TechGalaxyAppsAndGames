// Cifratura a riposo (AES-256-GCM) per segreti di integrazioni, MFA e SSO. © Roberto Di Flumeri
import { createCipheriv, createDecipheriv, createHash, createHmac, randomBytes, timingSafeEqual } from "node:crypto";
import { config } from "./config.js";

export function cifra(testo: string): string {
  const iv = randomBytes(12);
  const c = createCipheriv("aes-256-gcm", config.dataKey, iv);
  const dati = Buffer.concat([c.update(testo, "utf8"), c.final()]);
  return ["v1", iv.toString("base64"), c.getAuthTag().toString("base64"), dati.toString("base64")].join(".");
}

export function decifra(valore: string | null | undefined): string | null {
  if (!valore) return null;
  const [v, iv, tag, dati] = valore.split(".");
  if (v !== "v1") return null;
  const d = createDecipheriv("aes-256-gcm", config.dataKey, Buffer.from(iv, "base64"));
  d.setAuthTag(Buffer.from(tag, "base64"));
  return Buffer.concat([d.update(Buffer.from(dati, "base64")), d.final()]).toString("utf8");
}

export const sha256 = (s: string | Buffer) => createHash("sha256").update(s).digest("hex");
export const tokenCasuale = (byte = 32) => randomBytes(byte).toString("base64url");

export function hmac(algoritmo: "sha256", chiave: string, dati: string | Buffer, encoding: "hex" | "base64" = "hex") {
  return createHmac(algoritmo, chiave).update(dati).digest(encoding);
}

export function ugualiSicuro(a: string, b: string): boolean {
  const x = Buffer.from(a);
  const y = Buffer.from(b);
  return x.length === y.length && timingSafeEqual(x, y);
}
