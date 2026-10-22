// PDF di preventivi, DDT, fatture e note di credito (pdfkit, gratuito). © Roberto Di Flumeri
import { existsSync } from "node:fs";
import { join } from "node:path";
import type { Azienda, Cliente, Documento, RigaDocumento, Scadenza } from "@prisma/client";
import PDFDocument from "pdfkit";
import { BASE_DIR } from "../lib/config.js";
import { euro, imponibileRiga, totali } from "../lib/importi.js";

const TITOLI: Record<string, string> = { preventivo: "Preventivo", ddt: "Documento di trasporto", fattura: "Fattura", nota_credito: "Nota di credito" };
const BLU = "#0b1a5c";
const VIOLA = "#6d4aff";
const GRIGIO = "#5b6478";
const LOGO = join(BASE_DIR, "assets", "logo.png");

export function pdfDocumento(
  azienda: Azienda,
  cliente: Cliente,
  doc: Documento & { righe: RigaDocumento[]; scadenze: Scadenza[] },
): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const pdf = new PDFDocument({ size: "A4", margin: 40, info: { Title: `${TITOLI[doc.tipo]} ${doc.numero ?? "bozza"}`, Author: azienda.ragioneSociale } });
    const parti: Buffer[] = [];
    pdf.on("data", (c: Buffer) => parti.push(c));
    pdf.on("end", () => resolve(Buffer.concat(parti)));
    pdf.on("error", reject);

    const ddt = doc.tipo === "ddt";
    // intestazione
    pdf.rect(0, 0, pdf.page.width, 6).fill(VIOLA);
    if (existsSync(LOGO)) pdf.image(LOGO, 40, 26, { width: 46 });
    pdf.fillColor(BLU).font("Helvetica-Bold").fontSize(14).text(azienda.ragioneSociale, 96, 28);
    pdf.font("Helvetica").fontSize(8.5).fillColor(GRIGIO)
      .text(`${azienda.indirizzo} · ${azienda.cap} ${azienda.citta} (${azienda.provincia})`, 96, 46)
      .text(`P.IVA ${azienda.partitaIva}${azienda.codiceFiscale ? ` · C.F. ${azienda.codiceFiscale}` : ""}`)
      .text([azienda.email, azienda.pec ? `PEC ${azienda.pec}` : "", azienda.telefono].filter(Boolean).join(" · "));

    pdf.fillColor(BLU).font("Helvetica-Bold").fontSize(18).text(TITOLI[doc.tipo], 330, 28, { width: 225, align: "right" });
    pdf.font("Helvetica").fontSize(9.5).fillColor("#111")
      .text(`N. ${doc.numero ?? "BOZZA"} del ${doc.data.toLocaleDateString("it-IT")}`, 330, 52, { width: 225, align: "right" });
    if (doc.tipo === "preventivo" && doc.validoFino)
      pdf.text(`Valido fino al ${doc.validoFino.toLocaleDateString("it-IT")}`, 330, 66, { width: 225, align: "right" });

    // destinatario
    pdf.roundedRect(330, 92, 225, 82, 6).strokeColor("#d9def0").stroke();
    pdf.fontSize(7.5).fillColor(GRIGIO).text(ddt ? "DESTINATARIO" : "CLIENTE", 340, 100);
    pdf.fontSize(10).fillColor("#111").font("Helvetica-Bold").text(cliente.ragioneSociale, 340, 112, { width: 205 });
    pdf.font("Helvetica").fontSize(8.5)
      .text([cliente.indirizzo, [cliente.cap, cliente.citta, cliente.provincia && `(${cliente.provincia})`].filter(Boolean).join(" ")].filter(Boolean).join("\n"), { width: 205 })
      .text([cliente.partitaIva && `P.IVA ${cliente.partitaIva}`, cliente.codiceFiscale && `C.F. ${cliente.codiceFiscale}`].filter(Boolean).join(" · "), { width: 205 });
    if (ddt) {
      pdf.fontSize(8.5).fillColor(GRIGIO).text(`Causale del trasporto: ${doc.causaleTrasporto || "Vendita"}`, 40, 110)
        .text(`Colli: ${doc.colli ?? 1} · Peso: ${(doc.pesoKg ?? 1).toFixed(2)} kg`, 40, 124)
        .text("Trasporto a cura del: mittente", 40, 138);
    }

    // righe
    let y = 192;
    const col = ddt
      ? [{ t: "Descrizione", x: 40, w: 380 }, { t: "U.M.", x: 425, w: 40 }, { t: "Quantità", x: 470, w: 85, a: "right" as const }]
      : [{ t: "Descrizione", x: 40, w: 230 }, { t: "Q.tà", x: 272, w: 45, a: "right" as const }, { t: "Prezzo", x: 320, w: 65, a: "right" as const },
        { t: "Sc.%", x: 388, w: 35, a: "right" as const }, { t: "IVA", x: 426, w: 35, a: "right" as const }, { t: "Importo", x: 465, w: 90, a: "right" as const }];
    pdf.rect(40, y, 515, 18).fill(BLU);
    pdf.fillColor("#fff").font("Helvetica-Bold").fontSize(8.5);
    for (const c of col) pdf.text(c.t, c.x + 4, y + 5, { width: c.w - 8, align: c.a || "left" });
    y += 22;
    pdf.font("Helvetica").fontSize(8.5).fillColor("#111");
    doc.righe.forEach((r, i) => {
      const h = Math.max(14, pdf.heightOfString(r.descrizione, { width: col[0].w - 8 }) + 4);
      if (y + h > pdf.page.height - 170) { pdf.addPage(); y = 50; }
      if (i % 2 === 1) pdf.rect(40, y - 3, 515, h + 2).fill("#f3f5fc").fillColor("#111");
      const valori = ddt
        ? [r.descrizione, r.unita, r.quantita.toLocaleString("it-IT")]
        : [r.descrizione, r.quantita.toLocaleString("it-IT"), euro(r.prezzoUnitario), r.sconto ? r.sconto.toFixed(0) : "",
          r.aliquotaIva ? `${r.aliquotaIva}%` : (r.naturaIva || "0%"), euro(imponibileRiga(r))];
      col.forEach((c, j) => pdf.text(String(valori[j]), c.x + 4, y, { width: c.w - 8, align: c.a || "left" }));
      y += h;
    });

    if (!ddt) {
      // riepilogo IVA e totali
      const t = totali(doc.righe);
      y = Math.max(y + 16, pdf.page.height - 260);
      pdf.fontSize(8).fillColor(GRIGIO).text("Riepilogo IVA", 40, y);
      y += 12;
      for (const g of t.riepilogo) {
        pdf.fillColor("#111").text(`${g.aliquota ? `${g.aliquota}%` : g.natura}  imponibile ${euro(g.imponibile)}  imposta ${euro(g.imposta)}`, 40, y);
        y += 12;
      }
      const ty = pdf.page.height - 260;
      pdf.roundedRect(355, ty, 200, 70, 6).fill("#f3f5fc");
      pdf.fillColor("#111").fontSize(9)
        .text("Imponibile", 365, ty + 10).text(euro(t.imponibile), 455, ty + 10, { width: 90, align: "right" })
        .text("IVA", 365, ty + 26).text(euro(t.iva), 455, ty + 26, { width: 90, align: "right" });
      pdf.font("Helvetica-Bold").fontSize(11).fillColor(BLU)
        .text("Totale", 365, ty + 46).text(euro(t.totale), 435, ty + 46, { width: 110, align: "right" });
      if (doc.scadenze.length) {
        pdf.font("Helvetica").fontSize(8.5).fillColor(GRIGIO).text("Scadenze di pagamento", 40, ty + 84);
        doc.scadenze.forEach((s, i) => pdf.fillColor("#111").text(
          `${s.dataScadenza.toLocaleDateString("it-IT")}  ${euro(s.importo)}${s.pagata ? "  (pagata)" : ""}`, 40, ty + 98 + i * 12));
        if (azienda.iban) pdf.fillColor(GRIGIO).text(`Bonifico su IBAN ${azienda.iban}`, 40, ty + 100 + doc.scadenze.length * 12);
      }
    }
    if (doc.note) pdf.font("Helvetica").fontSize(8.5).fillColor(GRIGIO).text(`Note: ${doc.note}`, 40, pdf.page.height - 110, { width: 515 });
    if (doc.tipo === "fattura" || doc.tipo === "nota_credito")
      pdf.fontSize(7.5).fillColor(GRIGIO).text("Copia di cortesia. L'originale è il file XML trasmesso tramite il Sistema di Interscambio.", 40, pdf.page.height - 72, { width: 515, align: "center" });
    pdf.fontSize(7.5).fillColor(GRIGIO).text("Generato con Nexora · © Roberto Di Flumeri", 40, pdf.page.height - 58, { width: 515, align: "center" });
    pdf.end();
  });
}
