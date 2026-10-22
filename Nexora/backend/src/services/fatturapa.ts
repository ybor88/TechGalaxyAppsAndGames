// Fattura elettronica in formato FatturaPA 1.2 (FPR12, tra privati) per il Sistema di Interscambio.
// Il file XML è generato localmente e gratuitamente; l'invio allo SdI avviene tramite PEC o canale
// accreditato a scelta dell'azienda. © Roberto Di Flumeri
import type { Azienda, Cliente, Documento, RigaDocumento, Scadenza } from "@prisma/client";
import { conflitto, nonValido } from "../lib/errori.js";
import { codiceDestinatarioValido, partitaIvaValida } from "../lib/fiscale.js";
import { decimale, imponibileRiga, totali } from "../lib/importi.js";

const esc = (s: string | null | undefined) =>
  (s ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&apos;");

const tag = (nome: string, valore: string | number | null | undefined) =>
  valore === null || valore === undefined || valore === "" ? "" : `<${nome}>${esc(String(valore))}</${nome}>`;

const data = (d: Date) => d.toISOString().slice(0, 10);
const qta = (n: number) => n.toFixed(2);
const prezzo = (centesimi: number) => (centesimi / 100).toFixed(2);

/** Controlli bloccanti prima della generazione (gli stessi che lo SdI applicherebbe con uno scarto). */
export function verificaDatiSdi(azienda: Azienda, cliente: Cliente): string[] {
  const errori: string[] = [];
  if (!partitaIvaValida(azienda.partitaIva)) errori.push("Partita IVA dell'azienda non valida");
  if (!azienda.indirizzo || !azienda.cap || !azienda.citta || !azienda.provincia) errori.push("Sede dell'azienda incompleta");
  if (cliente.nazione === "IT") {
    if (!cliente.partitaIva && !cliente.codiceFiscale) errori.push("Il cliente deve avere partita IVA o codice fiscale");
    if (cliente.partitaIva && !partitaIvaValida(cliente.partitaIva)) errori.push("Partita IVA del cliente non valida");
    if (cliente.tipo !== "privato" && !cliente.codiceDestinatario && !cliente.pec) errori.push("Al cliente manca il codice destinatario SDI o la PEC");
    if (cliente.codiceDestinatario && !codiceDestinatarioValido(cliente.codiceDestinatario)) errori.push("Codice destinatario SDI non valido");
    if (!cliente.indirizzo || !cliente.cap || !cliente.citta) errori.push("Indirizzo del cliente incompleto");
  }
  return errori;
}

export function nomeFileSdi(azienda: Azienda, progressivo: number) {
  const p = progressivo.toString(36).toUpperCase().padStart(5, "0");
  return `IT${azienda.partitaIva.replace(/^IT/i, "")}_${p}.xml`;
}

export function generaXml(
  azienda: Azienda,
  cliente: Cliente,
  doc: Documento & { righe: RigaDocumento[]; scadenze: Scadenza[] },
  origine?: { numero: string | null; data: Date } | null,
): string {
  if (!doc.numero || !doc.progressivo) throw conflitto("Il documento deve essere emesso prima di generare la fattura elettronica");
  const errori = verificaDatiSdi(azienda, cliente);
  if (errori.length) throw nonValido(errori.join("; "));
  const piva = azienda.partitaIva.replace(/^IT/i, "");
  const estero = cliente.nazione !== "IT";
  const codiceDest = estero ? "XXXXXXX" : (cliente.codiceDestinatario || "0000000").toUpperCase();
  const t = totali(doc.righe);

  const cessionarioId = cliente.partitaIva
    ? `<IdFiscaleIVA><IdPaese>${esc(cliente.nazione)}</IdPaese><IdCodice>${esc(cliente.partitaIva.replace(/^[A-Z]{2}/i, ""))}</IdCodice></IdFiscaleIVA>`
    : "";
  const anagrafica = cliente.tipo === "privato" && cliente.ragioneSociale.includes(" ")
    ? `<Nome>${esc(cliente.ragioneSociale.split(" ")[0])}</Nome><Cognome>${esc(cliente.ragioneSociale.split(" ").slice(1).join(" "))}</Cognome>`
    : `<Denominazione>${esc(cliente.ragioneSociale)}</Denominazione>`;

  const linee = doc.righe.map((r, i) => [
    "<DettaglioLinee>",
    tag("NumeroLinea", i + 1),
    tag("Descrizione", r.descrizione.slice(0, 1000)),
    tag("Quantita", qta(r.quantita)),
    tag("UnitaMisura", r.unita),
    tag("PrezzoUnitario", prezzo(r.prezzoUnitario)),
    r.sconto ? `<ScontoMaggiorazione><Tipo>SC</Tipo><Percentuale>${r.sconto.toFixed(2)}</Percentuale></ScontoMaggiorazione>` : "",
    tag("PrezzoTotale", decimale(imponibileRiga(r))),
    tag("AliquotaIVA", r.aliquotaIva.toFixed(2)),
    r.aliquotaIva === 0 ? tag("Natura", r.naturaIva || "N2.2") : "",
    "</DettaglioLinee>",
  ].join("")).join("");

  const riepilogo = t.riepilogo.map((g) => [
    "<DatiRiepilogo>",
    tag("AliquotaIVA", g.aliquota.toFixed(2)),
    g.aliquota === 0 ? tag("Natura", g.natura || "N2.2") : "",
    tag("ImponibileImporto", decimale(g.imponibile)),
    tag("Imposta", decimale(g.imposta)),
    g.aliquota > 0 ? tag("EsigibilitaIVA", "I") : "",
    "</DatiRiepilogo>",
  ].join("")).join("");

  const pagamenti = doc.scadenze.length
    ? `<DatiPagamento>${tag("CondizioniPagamento", doc.scadenze.length > 1 ? "TP01" : "TP02")}${doc.scadenze.map((s) =>
      `<DettaglioPagamento>${tag("ModalitaPagamento", "MP05")}${tag("DataScadenzaPagamento", data(s.dataScadenza))}${tag("ImportoPagamento", decimale(s.importo))}${tag("IBAN", azienda.iban)}</DettaglioPagamento>`).join("")}</DatiPagamento>`
    : "";

  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<p:FatturaElettronica versione="FPR12" xmlns:ds="http://www.w3.org/2000/09/xmldsig#" ',
    'xmlns:p="http://ivaservizi.agenziaentrate.gov.it/docs/xsd/fatture/v1.2" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" ',
    'xsi:schemaLocation="http://ivaservizi.agenziaentrate.gov.it/docs/xsd/fatture/v1.2 http://www.fatturapa.gov.it/export/fatturazione/sdi/fatturapa/v1.2/Schema_del_file_xml_FatturaPA_versione_1.2.xsd">',
    "<FatturaElettronicaHeader>",
    `<DatiTrasmissione><IdTrasmittente><IdPaese>IT</IdPaese><IdCodice>${esc(piva)}</IdCodice></IdTrasmittente>`,
    tag("ProgressivoInvio", String(doc.progressivo).padStart(5, "0")),
    tag("FormatoTrasmissione", "FPR12"),
    tag("CodiceDestinatario", codiceDest),
    codiceDest === "0000000" && cliente.pec ? tag("PECDestinatario", cliente.pec) : "",
    "</DatiTrasmissione>",
    "<CedentePrestatore><DatiAnagrafici>",
    `<IdFiscaleIVA><IdPaese>IT</IdPaese><IdCodice>${esc(piva)}</IdCodice></IdFiscaleIVA>`,
    tag("CodiceFiscale", azienda.codiceFiscale),
    `<Anagrafica><Denominazione>${esc(azienda.ragioneSociale)}</Denominazione></Anagrafica>`,
    tag("RegimeFiscale", azienda.regimeFiscale),
    "</DatiAnagrafici>",
    `<Sede>${tag("Indirizzo", azienda.indirizzo)}${tag("CAP", azienda.cap)}${tag("Comune", azienda.citta)}${tag("Provincia", azienda.provincia)}${tag("Nazione", azienda.nazione)}</Sede>`,
    "</CedentePrestatore>",
    "<CessionarioCommittente><DatiAnagrafici>",
    cessionarioId,
    tag("CodiceFiscale", cliente.codiceFiscale),
    `<Anagrafica>${anagrafica}</Anagrafica>`,
    "</DatiAnagrafici>",
    `<Sede>${tag("Indirizzo", cliente.indirizzo)}${tag("CAP", estero ? "00000" : cliente.cap)}${tag("Comune", cliente.citta)}${estero ? "" : tag("Provincia", cliente.provincia)}${tag("Nazione", cliente.nazione)}</Sede>`,
    "</CessionarioCommittente>",
    "</FatturaElettronicaHeader>",
    "<FatturaElettronicaBody>",
    "<DatiGenerali><DatiGeneraliDocumento>",
    tag("TipoDocumento", doc.tipo === "nota_credito" ? "TD04" : "TD01"),
    tag("Divisa", azienda.valuta),
    tag("Data", data(doc.data)),
    tag("Numero", doc.numero),
    tag("ImportoTotaleDocumento", decimale(t.totale)),
    doc.note ? tag("Causale", doc.note.slice(0, 200)) : "",
    "</DatiGeneraliDocumento>",
    origine?.numero ? `<DatiFattureCollegate>${tag("IdDocumento", origine.numero)}${tag("Data", data(origine.data))}</DatiFattureCollegate>` : "",
    "</DatiGenerali>",
    `<DatiBeniServizi>${linee}${riepilogo}</DatiBeniServizi>`,
    pagamenti,
    "</FatturaElettronicaBody>",
    "</p:FatturaElettronica>",
  ].join("");
}
