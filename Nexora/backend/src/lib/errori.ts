// Errori HTTP e messaggi di validazione in italiano. © Roberto Di Flumeri
import type { FastifyError, FastifyReply, FastifyRequest } from "fastify";
import { ZodError, type ZodIssue } from "zod";
import { Prisma } from "./db.js";

export class HttpError extends Error {
  constructor(public stato: number, messaggio: string, public dettagli?: unknown) {
    super(messaggio);
  }
}

export const nonTrovato = (cosa = "Elemento") => new HttpError(404, `${cosa} non trovato`);
export const conflitto = (msg: string) => new HttpError(409, msg);
export const nonValido = (msg: string) => new HttpError(422, msg);
export const vietato = (msg = "Operazione non consentita per il tuo ruolo") => new HttpError(403, msg);

const CAMPI: Record<string, string> = {
  email: "Email", password: "Password", nome: "Nome", ragioneSociale: "Ragione sociale", partitaIva: "Partita IVA",
  codiceFiscale: "Codice fiscale", sku: "SKU", prezzoVendita: "Prezzo di vendita", quantita: "Quantità",
  righe: "Righe", clienteId: "Cliente", fornitoreId: "Fornitore", magazzinoId: "Magazzino", varianteId: "Articolo",
  codiceDestinatario: "Codice destinatario", cap: "CAP", provincia: "Provincia", indirizzo: "Indirizzo", citta: "Città",
  aliquotaIva: "Aliquota IVA", costoAcquisto: "Costo di acquisto", codice: "Codice", ruolo: "Ruolo",
};

function messaggio(i: ZodIssue): string {
  const campo = [...i.path].reverse().find((p) => typeof p === "string") as string | undefined;
  const nome = campo ? CAMPI[campo] || campo : "";
  const pre = nome ? `${nome}: ` : "";
  switch (i.code) {
    case "invalid_type":
      return i.received === "undefined" ? `${nome || "Un campo"} è obbligatorio` : `${pre}valore non valido`;
    case "too_small":
      return i.type === "string" ? `${pre}inserisci almeno ${i.minimum} caratteri`
        : i.type === "array" ? `${pre}serve almeno ${i.minimum} elemento` : `${pre}il valore minimo è ${i.minimum}`;
    case "too_big":
      return i.type === "string" ? `${pre}massimo ${i.maximum} caratteri` : `${pre}il valore massimo è ${i.maximum}`;
    case "invalid_string":
      return i.validation === "email" ? "Inserisci un indirizzo email valido" : `${pre}formato non valido`;
    case "invalid_enum_value":
      return `${pre}valore non ammesso`;
    default:
      return i.message.startsWith("Required") ? `${nome} è obbligatorio` : `${pre}${i.message}`;
  }
}

export function gestoreErrori(err: FastifyError | Error, req: FastifyRequest, reply: FastifyReply) {
  if (err instanceof ZodError) {
    return reply.status(422).send({ errore: messaggio(err.issues[0]), dettagli: err.issues.map((i) => ({ campo: i.path.join("."), messaggio: messaggio(i) })) });
  }
  if (err instanceof HttpError) {
    return reply.status(err.stato).send({ errore: err.message, dettagli: err.dettagli });
  }
  if (err instanceof Prisma.PrismaClientKnownRequestError) {
    if (err.code === "P2002") return reply.status(409).send({ errore: "Esiste già un elemento con questi dati (codice o numero duplicato)" });
    if (err.code === "P2003") return reply.status(409).send({ errore: "L'elemento è collegato ad altri dati e non può essere eliminato" });
    if (err.code === "P2025") return reply.status(404).send({ errore: "Elemento non trovato" });
  }
  const fe = err as FastifyError;
  if (fe.statusCode && fe.statusCode < 500) return reply.status(fe.statusCode).send({ errore: fe.message });
  req.log.error(err);
  return reply.status(500).send({ errore: "Errore interno: riprova tra poco" });
}
