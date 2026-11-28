// PayRecap — © Roberto Di Flumeri
package com.payrecap.app.dati

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.payrecap.app.dominio.Frammento
import com.payrecap.app.dominio.ricomponiRighe
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class EsitoEstrazione {
    data class Testo(val testo: String, val metodo: String, val nomeFile: String?) : EsitoEstrazione()
    data object PasswordRichiesta : EsitoEstrazione()
    data object PasswordErrata : EsitoEstrazione()
    data class Errore(val messaggio: String) : EsitoEstrazione()
}

/**
 * Legge il testo di un cedolino. Tutto avviene sul telefono, senza connessione:
 *  - PDF digitale: testo estratto direttamente (anche se protetto da password);
 *  - PDF scansionato: le pagine vengono disegnate e lette con l'OCR;
 *  - foto o immagine: OCR.
 */
class Estrattore(private val context: Context) {

    @Volatile private var pdfboxPronto = false

    suspend fun estrai(uri: Uri, password: String? = null): EsitoEstrazione = withContext(Dispatchers.IO) {
        val nome = nomeFile(uri)
        val mime = context.contentResolver.getType(uri).orEmpty()
        try {
            if (mime == "application/pdf" || nome?.lowercase()?.endsWith(".pdf") == true) estraiPdf(uri, password, nome)
            else EsitoEstrazione.Testo(ocr(InputImage.fromFilePath(context, uri)), "OCR immagine", nome)
        } catch (e: OutOfMemoryError) {
            EsitoEstrazione.Errore("Il documento è troppo grande da elaborare.")
        } catch (e: Exception) {
            EsitoEstrazione.Errore("Impossibile leggere il documento: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun estraiPdf(uri: Uri, password: String?, nome: String?): EsitoEstrazione {
        if (!pdfboxPronto) {
            PDFBoxResourceLoader.init(context.applicationContext)
            pdfboxPronto = true
        }
        val input = context.contentResolver.openInputStream(uri) ?: return EsitoEstrazione.Errore("File non accessibile.")
        val doc = try {
            input.use { if (password != null) PDDocument.load(it, password) else PDDocument.load(it) }
        } catch (e: InvalidPasswordException) {
            return if (password == null) EsitoEstrazione.PasswordRichiesta else EsitoEstrazione.PasswordErrata
        }
        doc.use {
            if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
                endPage = minOf(doc.numberOfPages, 6)
            }
            val testo = stripper.getText(doc)
            if (testo.count { it.isLetterOrDigit() } >= 80) return EsitoEstrazione.Testo(testo, "Testo PDF", nome)

            // PDF scansionato: OCR delle prime pagine
            val renderer = PDFRenderer(doc)
            val sb = StringBuilder()
            for (p in 0 until minOf(doc.numberOfPages, 4)) {
                val bmp = renderer.renderImage(p, 2.5f, ImageType.RGB)
                sb.appendLine(ocr(InputImage.fromBitmap(bmp, 0)))
                bmp.recycle()
            }
            return EsitoEstrazione.Testo(sb.toString(), "OCR PDF scansionato", nome)
        }
    }

    private suspend fun ocr(img: InputImage): String {
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val risultato = suspendCancellableCoroutine { cont ->
                client.process(img)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            val frammenti = risultato.textBlocks.flatMap { it.lines }.mapNotNull { l ->
                l.boundingBox?.let { b -> Frammento(l.text, b.left, b.top, b.bottom) }
            }
            return ricomponiRighe(frammenti)
        } finally {
            client.close()
        }
    }

    private fun nomeFile(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        uri.lastPathSegment
    }
}
