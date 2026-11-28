# PayRecap — © Roberto Di Flumeri
# Genera cedolini fittizi per il test end-to-end di PayRecap (dati inventati)
import sys
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas
from reportlab.lib.pdfencrypt import StandardEncryption
import fitz

out = sys.argv[1]

def disegna(c, mese, righe_voci, tot, ferie, rol, extra):
    w, h = A4
    c.setFont("Helvetica-Bold", 14); c.drawString(40, h-50, "BETA LOGISTICA S.P.A.")
    c.setFont("Helvetica", 10)
    c.drawString(40, h-68, "Ragione sociale: Beta Logistica S.p.A.")
    c.drawString(360, h-68, "Cedolino paga")
    c.drawString(40, h-86, f"Periodo di paga: {mese}")
    c.drawString(40, h-104, "Contratto: tempo determinato")
    c.drawString(300, h-104, "Data fine rapporto: 15/01/2027")
    c.drawString(40, h-122, "CCNL Logistica, Trasporto Merci e Spedizione")
    c.drawString(360, h-122, "Livello 5° Super")
    y = h-160
    c.setFont("Helvetica-Bold", 10)
    for x, t in [(40,"Descrizione"),(260,"Quantita"),(340,"Competenze"),(450,"Trattenute")]: c.drawString(x, y, t)
    c.setFont("Helvetica", 10)
    for d, q, comp, tratt in righe_voci:
        y -= 18
        c.drawString(40, y, d); c.drawRightString(300, y, q); c.drawRightString(410, y, comp); c.drawRightString(520, y, tratt)
    y -= 34
    c.setFont("Helvetica-Bold", 10)
    c.drawString(40, y, "Totale competenze"); c.drawRightString(200, y, tot[0])
    c.drawString(240, y, "Totale trattenute"); c.drawRightString(400, y, tot[1])
    y -= 22
    c.drawString(40, y, "NETTO A PAGARE"); c.drawRightString(200, y, tot[2])
    c.setFont("Helvetica", 10)
    y -= 40
    for x, t in [(40,"Ferie/Permessi"),(170,"Maturato"),(260,"Goduto"),(350,"Residuo")]: c.drawString(x, y, t)
    for nome, v in [("Ferie gg", ferie), ("Permessi ROL ore", rol)]:
        y -= 18; c.drawString(40, y, nome)
        for x, val in zip((210, 300, 390), v): c.drawRightString(x, y, val)
    for t in extra:
        y -= 18; c.drawString(40, y, t)
    c.showPage(); c.save()

voci = [
    ("Paga base", "168,00", "2.150,00", ""),
    ("Straordinario 30%", "4,00", "66,80", ""),
    ("Contributi previdenziali 9,49%", "", "", "210,38"),
    ("Ritenuta IRPEF", "", "", "296,40"),
    ("Add.le regionale", "", "", "21,10"),
    ("Add.le comunale", "", "", "7,90"),
]
tot = ("2.216,80", "535,78", "1.681,02")
ferie = ("22,00", "9,00", "13,00"); rol = ("56,00", "16,00", "40,00")
extra = ["Imponibile fiscale 2.006,42", "Saldo banca ore 6,00"]

disegna(canvas.Canvas(f"{out}/cedolino_dicembre_2026.pdf", pagesize=A4), "Dicembre 2026", voci, tot, ferie, rol, extra)
enc = StandardEncryption("RSSMRA80A01H501U", canPrint=1)
disegna(canvas.Canvas(f"{out}/cedolino_settembre_2026_protetto.pdf", pagesize=A4, encrypt=enc), "Settembre 2026", voci, tot, ferie, rol, extra)
# "foto" del cedolino di agosto: PDF disegnato e rasterizzato in JPG (nessun testo selezionabile, serve l'OCR)
disegna(canvas.Canvas(f"{out}/_agosto.pdf", pagesize=A4), "Agosto 2026", voci, tot, ferie, rol, extra)
doc = fitz.open(f"{out}/_agosto.pdf")
doc[0].get_pixmap(dpi=200).save(f"{out}/foto_cedolino_agosto_2026.jpg")
print("ok")
