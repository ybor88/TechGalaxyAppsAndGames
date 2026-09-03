package com.romanopetroli.rpfidelity.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.romanopetroli.rpfidelity.data.model.User
import com.romanopetroli.rpfidelity.data.model.Voucher
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Genera il PDF del voucher direttamente sul dispositivo (nessuna libreria esterna, solo android.graphics.pdf). */
object VoucherPdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    fun generate(context: Context, voucher: Voucher, cliente: User?): Uri {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val navy = Color.rgb(11, 20, 64)
        val gray = Color.rgb(102, 102, 102)

        val headerPaint = Paint().apply { color = navy; isAntiAlias = true }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 62f, headerPaint)

        val titleWhite = Paint().apply {
            color = Color.WHITE; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        }
        canvas.drawText("RP FIDELITY", 40f, 40f, titleWhite)
        val subWhite = Paint().apply { color = Color.WHITE; textSize = 11f; isAntiAlias = true }
        canvas.drawText("Romano Petroli", 40f, 58f, subWhite)

        val heading = Paint().apply {
            color = navy; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        }
        canvas.drawText("Voucher Premio", 40f, 110f, heading)

        val linePaint = Paint().apply { color = Color.BLACK; strokeWidth = 1f }
        canvas.drawLine(40f, 122f, 555f, 122f, linePaint)

        val body = Paint().apply { color = Color.BLACK; textSize = 14f; isAntiAlias = true }
        canvas.drawText("${voucher.nome} — %.2f €".format(voucher.importoPremio), 40f, 150f, body)

        val hint = Paint().apply { color = gray; textSize = 10f; isAntiAlias = true }
        canvas.drawText("Codice voucher (mostralo al gestore o comunicalo a voce)", 40f, 180f, hint)

        canvas.drawRect(35f, 190f, 560f, 235f, Paint().apply {
            color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1f
        })
        val codeText = Paint().apply {
            color = Color.BLACK; textSize = 24f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        }
        canvas.drawText(voucher.codiceVoucher, 50f, 220f, codeText)

        val info = Paint().apply { color = Color.BLACK; textSize = 12f; isAntiAlias = true }
        val nomeCliente = cliente?.let { "${it.nome} ${it.cognome}" } ?: "-"
        canvas.drawText("Cliente: $nomeCliente", 40f, 260f, info)
        canvas.drawText("Data scadenza: ${voucher.dataScadenza}", 40f, 280f, info)
        canvas.drawText("Stato: ${voucher.stato.replaceFirstChar { it.uppercase() }}", 40f, 300f, info)

        val note = Paint().apply { color = gray; textSize = 10f; isAntiAlias = true }
        canvas.drawText(
            "Mostra questo codice al gestore Romano Petroli al momento del rifornimento:",
            40f, 340f, note
        )
        canvas.drawText(
            "verrà scalato dall'importo da pagare. Il voucher non è più utilizzabile una volta usato.",
            40f, 356f, note
        )

        canvas.drawLine(40f, 780f, 555f, 780f, linePaint)
        val footer = Paint().apply { color = gray; textSize = 8f; isAntiAlias = true }
        val generatoIl = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date())
        canvas.drawText("RP Fidelity — Romano Petroli. Documento generato il $generatoIl.", 40f, 795f, footer)
        canvas.drawText("© Roberto Di Flumeri", 40f, 808f, footer)

        document.finishPage(page)

        val dir = File(context.cacheDir, "voucher_pdf").apply { mkdirs() }
        val file = File(dir, "voucher-${voucher.codiceVoucher}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
