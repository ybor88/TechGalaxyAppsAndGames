from decimal import Decimal
from datetime import date, datetime
import io

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.models.fatturazione import Anagrafica, Documento, RigaDocumento
from app.schemas.fatturazione import (
    AnagraficaCreate,
    AnagraficaUpdate,
    DocumentoCreate,
    DocumentoUpdate,
    RigaDocumentoCreate,
)

# ── Anagrafica service ───────────────────────────────────────────────────────

class AnagraficheService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def list(self, tipo: str | None = None) -> list[Anagrafica]:
        q = select(Anagrafica).order_by(Anagrafica.nome)
        if tipo:
            q = q.where(Anagrafica.tipo == tipo)
        result = await self.db.execute(q)
        return list(result.scalars().all())

    async def get(self, anagrafica_id: int) -> Anagrafica | None:
        result = await self.db.execute(
            select(Anagrafica).where(Anagrafica.id == anagrafica_id)
        )
        return result.scalar_one_or_none()

    async def create(self, payload: AnagraficaCreate) -> Anagrafica:
        ana = Anagrafica(**payload.model_dump())
        self.db.add(ana)
        await self.db.commit()
        await self.db.refresh(ana)
        return ana

    async def update(self, anagrafica_id: int, payload: AnagraficaUpdate) -> Anagrafica | None:
        ana = await self.get(anagrafica_id)
        if not ana:
            return None
        for campo, valore in payload.model_dump(exclude_none=True).items():
            setattr(ana, campo, valore)
        await self.db.commit()
        await self.db.refresh(ana)
        return ana

    async def delete(self, anagrafica_id: int) -> bool:
        ana = await self.get(anagrafica_id)
        if not ana:
            return False
        await self.db.delete(ana)
        await self.db.commit()
        return True


# ── Documenti service ────────────────────────────────────────────────────────

_PREFIX_MAP = {
    "preventivo": "PRE",
    "ordine": "ORD",
    "fattura_attiva": "FAT",
    "fattura_passiva": "AQU",
    "nota_credito": "NC",
}


def _calcola_totali(righe_data: list[RigaDocumentoCreate]) -> dict:
    subtotale = Decimal("0.00")
    totale_iva = Decimal("0.00")
    for r in righe_data:
        imponibile = (r.quantita * r.prezzo_unitario).quantize(Decimal("0.01"))
        iva = (imponibile * r.iva_percentuale / 100).quantize(Decimal("0.01"))
        subtotale += imponibile
        totale_iva += iva
    return {
        "subtotale": subtotale,
        "totale_iva": totale_iva,
        "totale": subtotale + totale_iva,
    }


class DocumentiService:
    def __init__(self, db: AsyncSession):
        self.db = db

    def _with_relations(self):
        return select(Documento).options(
            selectinload(Documento.anagrafica),
            selectinload(Documento.righe),
        )

    async def list(self, tipo: str | None = None, stato: str | None = None) -> list[Documento]:
        q = self._with_relations().order_by(Documento.data.desc(), Documento.id.desc())
        if tipo:
            q = q.where(Documento.tipo == tipo)
        if stato:
            q = q.where(Documento.stato == stato)
        result = await self.db.execute(q)
        return list(result.scalars().all())

    async def get(self, documento_id: int) -> Documento | None:
        result = await self.db.execute(
            self._with_relations().where(Documento.id == documento_id)
        )
        return result.scalar_one_or_none()

    async def _genera_numero(self, tipo: str, anno: int) -> str:
        prefix = _PREFIX_MAP.get(tipo, "DOC")
        result = await self.db.execute(
            select(func.count(Documento.id)).where(
                Documento.tipo == tipo,
                func.strftime("%Y", Documento.data) == str(anno),
            )
        )
        count = result.scalar() or 0
        return f"{prefix}-{anno}-{count + 1:04d}"

    async def create(self, payload: DocumentoCreate) -> Documento:
        anno = payload.data.year
        numero = await self._genera_numero(payload.tipo, anno)
        totali = _calcola_totali(payload.righe)

        doc = Documento(
            tipo=payload.tipo,
            numero=numero,
            data=payload.data,
            data_scadenza=payload.data_scadenza,
            anagrafica_id=payload.anagrafica_id,
            stato="bozza",
            oggetto=payload.oggetto,
            note=payload.note,
            **totali,
        )
        self.db.add(doc)
        await self.db.flush()  # ottieni doc.id prima di inserire righe

        for r in payload.righe:
            imponibile = (r.quantita * r.prezzo_unitario).quantize(Decimal("0.01"))
            riga = RigaDocumento(
                documento_id=doc.id,
                descrizione=r.descrizione,
                quantita=r.quantita,
                prezzo_unitario=r.prezzo_unitario,
                iva_percentuale=r.iva_percentuale,
                importo=imponibile,
            )
            self.db.add(riga)

        await self.db.commit()
        return await self.get(doc.id)

    async def update(self, documento_id: int, payload: DocumentoUpdate) -> Documento | None:
        doc = await self.get(documento_id)
        if not doc:
            return None

        campi = payload.model_dump(exclude_none=True, exclude={"righe"})
        for campo, valore in campi.items():
            setattr(doc, campo, valore)

        if payload.righe is not None:
            # Sostituisci tutte le righe
            for riga in list(doc.righe):
                await self.db.delete(riga)
            await self.db.flush()

            for r in payload.righe:
                imponibile = (r.quantita * r.prezzo_unitario).quantize(Decimal("0.01"))
                riga = RigaDocumento(
                    documento_id=doc.id,
                    descrizione=r.descrizione,
                    quantita=r.quantita,
                    prezzo_unitario=r.prezzo_unitario,
                    iva_percentuale=r.iva_percentuale,
                    importo=imponibile,
                )
                self.db.add(riga)

            await self.db.flush()
            # Ricarica righe per ricalcolare totali
            righe_schema = payload.righe
            totali = _calcola_totali(righe_schema)
            doc.subtotale = totali["subtotale"]
            doc.totale_iva = totali["totale_iva"]
            doc.totale = totali["totale"]

        doc.updated_at = datetime.utcnow()
        await self.db.commit()
        return await self.get(doc.id)

    async def delete(self, documento_id: int) -> bool:
        doc = await self.get(documento_id)
        if not doc:
            return False
        await self.db.delete(doc)
        await self.db.commit()
        return True


# ── PDF Generation (ReportLab) ───────────────────────────────────────────────

_TIPO_LABEL = {
    "preventivo": "PREVENTIVO",
    "ordine": "ORDINE",
    "fattura_attiva": "FATTURA",
    "fattura_passiva": "FATTURA FORNITORE",
    "nota_credito": "NOTA DI CREDITO",
}


def genera_pdf(documento: Documento) -> bytes:
    """Genera un PDF professionale del documento usando ReportLab."""
    from reportlab.lib.pagesizes import A4
    from reportlab.lib import colors
    from reportlab.lib.units import cm
    from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
    from reportlab.platypus import (
        SimpleDocTemplate,
        Table,
        TableStyle,
        Paragraph,
        Spacer,
        HRFlowable,
    )
    from reportlab.lib.enums import TA_RIGHT, TA_CENTER, TA_LEFT

    buffer = io.BytesIO()
    doc = SimpleDocTemplate(
        buffer,
        pagesize=A4,
        rightMargin=2 * cm,
        leftMargin=2 * cm,
        topMargin=2 * cm,
        bottomMargin=2 * cm,
    )

    styles = getSampleStyleSheet()
    blu = colors.HexColor("#1e3a8a")
    grigio = colors.HexColor("#6b7280")
    grigio_chiaro = colors.HexColor("#f3f4f6")

    style_normal = ParagraphStyle("normal", fontSize=9, leading=13)
    style_small = ParagraphStyle("small", fontSize=8, leading=11, textColor=grigio)
    style_bold = ParagraphStyle("bold", fontSize=9, leading=13, fontName="Helvetica-Bold")
    style_header = ParagraphStyle(
        "header", fontSize=20, fontName="Helvetica-Bold", textColor=blu
    )
    style_doc_type = ParagraphStyle(
        "doc_type", fontSize=16, fontName="Helvetica-Bold", textColor=blu,
        alignment=TA_RIGHT,
    )
    style_right = ParagraphStyle("right", fontSize=9, alignment=TA_RIGHT)
    style_right_bold = ParagraphStyle(
        "right_bold", fontSize=10, fontName="Helvetica-Bold", alignment=TA_RIGHT
    )
    style_totale = ParagraphStyle(
        "totale", fontSize=12, fontName="Helvetica-Bold", textColor=blu,
        alignment=TA_RIGHT,
    )

    def fmt_eur(val) -> str:
        return f"€ {float(val):,.2f}".replace(",", "X").replace(".", ",").replace("X", ".")

    def fmt_data(d: date | None) -> str:
        if d is None:
            return "—"
        return d.strftime("%d/%m/%Y")

    story = []

    # ── Header: azienda (sx) + tipo documento (dx) ────────────────────────
    ana = documento.anagrafica

    header_data = [
        [
            Paragraph("Contabilità 2.0", style_header),
            Paragraph(_TIPO_LABEL.get(documento.tipo, documento.tipo), style_doc_type),
        ],
        [
            Paragraph("ERP aziendale open source", style_small),
            Paragraph(
                f"<font color='grey'>N.</font> <b>{documento.numero}</b>",
                ParagraphStyle("np", fontSize=10, alignment=TA_RIGHT, fontName="Helvetica-Bold"),
            ),
        ],
        [
            Paragraph("", style_small),
            Paragraph(
                f"Data: {fmt_data(documento.data)}", style_right
            ),
        ],
    ]
    if documento.data_scadenza:
        header_data.append([
            Paragraph("", style_small),
            Paragraph(f"Scadenza: {fmt_data(documento.data_scadenza)}", style_right),
        ])

    header_table = Table(header_data, colWidths=["55%", "45%"])
    header_table.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]))
    story.append(header_table)
    story.append(Spacer(1, 0.5 * cm))
    story.append(HRFlowable(width="100%", thickness=2, color=blu))
    story.append(Spacer(1, 0.5 * cm))

    # ── Destinatario ──────────────────────────────────────────────────────
    if ana:
        intestazione = "Spett.le" if documento.tipo in ("fattura_attiva", "preventivo", "ordine", "nota_credito") else "Fornitore"
        dest_lines = [
            Paragraph(f"<b>{intestazione}</b>", style_bold),
            Paragraph(f"<b>{ana.nome}</b>", ParagraphStyle("nome", fontSize=11, fontName="Helvetica-Bold")),
        ]
        if ana.indirizzo:
            dest_lines.append(Paragraph(ana.indirizzo, style_normal))
        citta_line = " ".join(filter(None, [ana.cap, ana.citta, f"({ana.provincia})" if ana.provincia else None]))
        if citta_line.strip():
            dest_lines.append(Paragraph(citta_line, style_normal))
        if ana.paese and ana.paese != "Italia":
            dest_lines.append(Paragraph(ana.paese, style_normal))
        if ana.piva:
            dest_lines.append(Paragraph(f"P.IVA: {ana.piva}", style_small))
        if ana.cf:
            dest_lines.append(Paragraph(f"C.F.: {ana.cf}", style_small))
        if ana.email:
            dest_lines.append(Paragraph(f"Email: {ana.email}", style_small))

        for p in dest_lines:
            story.append(p)

    story.append(Spacer(1, 0.5 * cm))

    # ── Oggetto ───────────────────────────────────────────────────────────
    if documento.oggetto:
        story.append(
            Paragraph(f"<b>Oggetto:</b> {documento.oggetto}", style_normal)
        )
        story.append(Spacer(1, 0.4 * cm))

    # ── Tabella righe ─────────────────────────────────────────────────────
    col_headers = ["#", "Descrizione", "Qta", "Prezzo unitario", "IVA %", "Importo"]
    table_data = [col_headers]

    for i, riga in enumerate(documento.righe, 1):
        table_data.append([
            str(i),
            riga.descrizione,
            str(float(riga.quantita)).rstrip("0").rstrip("."),
            fmt_eur(riga.prezzo_unitario),
            f"{float(riga.iva_percentuale):.0f}%",
            fmt_eur(riga.importo),
        ])

    col_widths = [0.8 * cm, None, 1.5 * cm, 3.2 * cm, 1.5 * cm, 3.0 * cm]
    righe_table = Table(table_data, colWidths=col_widths, repeatRows=1)
    righe_table.setStyle(TableStyle([
        # Header
        ("BACKGROUND", (0, 0), (-1, 0), blu),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("FONTSIZE", (0, 0), (-1, 0), 8),
        ("ALIGN", (0, 0), (-1, 0), "CENTER"),
        ("BOTTOMPADDING", (0, 0), (-1, 0), 6),
        ("TOPPADDING", (0, 0), (-1, 0), 6),
        # Body
        ("FONTSIZE", (0, 1), (-1, -1), 8),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, grigio_chiaro]),
        ("ALIGN", (0, 1), (0, -1), "CENTER"),
        ("ALIGN", (2, 1), (2, -1), "CENTER"),
        ("ALIGN", (3, 1), (5, -1), "RIGHT"),
        ("TOPPADDING", (0, 1), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 1), (-1, -1), 5),
        # Grid
        ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#e5e7eb")),
        ("LINEBELOW", (0, 0), (-1, 0), 1, blu),
    ]))
    story.append(righe_table)
    story.append(Spacer(1, 0.5 * cm))

    # ── Totali ────────────────────────────────────────────────────────────
    totali_data = [
        ["Subtotale (imponibile):", fmt_eur(documento.subtotale)],
        ["IVA:", fmt_eur(documento.totale_iva)],
        ["TOTALE:", fmt_eur(documento.totale)],
    ]
    totali_table = Table(
        totali_data,
        colWidths=["*", 4 * cm],
        hAlign="RIGHT",
    )
    totali_table.setStyle(TableStyle([
        ("ALIGN", (0, 0), (-1, -1), "RIGHT"),
        ("FONTSIZE", (0, 0), (-1, -2), 9),
        ("FONTSIZE", (0, 2), (-1, 2), 11),
        ("FONTNAME", (0, 2), (-1, 2), "Helvetica-Bold"),
        ("TEXTCOLOR", (0, 2), (-1, 2), blu),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
        ("LINEABOVE", (0, 2), (-1, 2), 1, blu),
        ("BACKGROUND", (0, 2), (-1, 2), grigio_chiaro),
    ]))
    story.append(totali_table)

    # ── Note ──────────────────────────────────────────────────────────────
    if documento.note:
        story.append(Spacer(1, 0.6 * cm))
        story.append(HRFlowable(width="100%", thickness=0.5, color=colors.HexColor("#e5e7eb")))
        story.append(Spacer(1, 0.3 * cm))
        story.append(Paragraph("<b>Note</b>", style_bold))
        story.append(Paragraph(documento.note, style_normal))

    # ── Footer ────────────────────────────────────────────────────────────
    story.append(Spacer(1, 1 * cm))
    story.append(HRFlowable(width="100%", thickness=0.5, color=colors.HexColor("#e5e7eb")))
    story.append(Spacer(1, 0.2 * cm))
    stato_label = {
        "bozza": "BOZZA",
        "emesso": "EMESSO",
        "pagato": "PAGATO",
        "annullato": "ANNULLATO",
    }.get(documento.stato, documento.stato.upper())
    story.append(
        Paragraph(
            f"Documento generato da Contabilità 2.0 — Stato: {stato_label}",
            ParagraphStyle("footer", fontSize=7, textColor=grigio, alignment=TA_CENTER),
        )
    )

    doc.build(story)
    return buffer.getvalue()
