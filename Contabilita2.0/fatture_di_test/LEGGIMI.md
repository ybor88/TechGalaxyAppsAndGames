# Fatture di test per l'upload OCR

© Roberto Di Flumeri

File fittizi da caricare nella pagina **OCR Fatture** (http://localhost:3000/ocr) per collaudare l'acquisizione automatica.
L'esito atteso di ogni file (codice HTTP, stato, P.IVA, importi…) è in `attesi.json`.

| File | Cosa verifica |
|---|---|
| 01–07 `.pdf` | PDF digitali: aliquote 22/10/4%, importi con "EUR", fattura in inglese (formato 1,234.56), "Subtotale" prima del "Totale", professionista con codice fiscale, fattura multipagina da 80 righe |
| 08 `.pdf` | PDF scansionato (solo immagine): passa dall'OCR Tesseract |
| 09–13 | Foto della fattura in PNG, JPG, TIFF, BMP, WEBP (OCR Tesseract) |
| 20 | Documento senza P.IVA/CF → stato **revisione** |
| 21 | PDF danneggiato → stato **errore** (nessun crash) |
| 22 | File vuoto → rifiutato (HTTP 400) |
| 23 | Formato non supportato `.txt` → rifiutato (HTTP 415) |

Rigenerare i file (anche in un'altra cartella) o aggiungere il file da 21 MB per il limite di dimensione (HTTP 413):

```
cd backend
.venv\Scripts\python scripts\genera_fatture_test.py
.venv\Scripts\python scripts\genera_fatture_test.py --grande
```

Gli stessi file sono caricati automaticamente dal test `backend/tests/test_upload_fatture_generate.py`.

Nota: Tesseract è installato con la sola lingua inglese (`eng`). Per un OCR migliore delle fatture italiane
scarica `ita.traineddata` da https://github.com/tesseract-ocr/tessdata e copialo in
`C:\Program Files\Tesseract-OCR\tessdata`: l'app lo userà automaticamente.
