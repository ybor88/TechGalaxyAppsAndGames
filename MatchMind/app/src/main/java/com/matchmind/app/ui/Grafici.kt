package com.matchmind.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matchmind.app.dominio.Mese
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

// Grafici disegnati con Canvas: andamento mensile e saldo nel tempo. © Roberto Di Flumeri

private val formatoMese = DateTimeFormatter.ofPattern("MMM yy", Locale.ITALY)

private fun compatto(v: Double): String = when {
    abs(v) >= 10_000 -> String.format(Locale.ITALY, "%.0fk", v / 1000)
    abs(v) >= 1_000 -> String.format(Locale.ITALY, "%.1fk", v / 1000)
    else -> String.format(Locale.ITALY, "%.0f", v)
}

@Composable
fun Legenda(vararg voci: Pair<String, Color>) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        voci.forEach { (t, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pallino(c)
                Spacer(Modifier.width(6.dp))
                Text(t, style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
            }
        }
    }
}

/** Barre affiancate per mese: incassato (verde) e perso (rosso); il trattino indica il netto del mese. */
@Composable
fun GraficoMensile(mesi: List<Mese>, modifier: Modifier = Modifier) {
    val ultimi = mesi.takeLast(12)
    val misura = rememberTextMeasurer()
    val stileAsse = TextStyle(color = Colori.Tenue, fontSize = 10.sp)
    val descrizione = ultimi.joinToString("; ") { "${it.mese.format(formatoMese)}: incassati ${it.incassato.euro()}, persi ${it.perso.euro()}" }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = "Andamento mensile. $descrizione" }) {
            val margineSx = 40.dp.toPx()
            val margineBasso = 22.dp.toPx()
            val altezza = size.height - margineBasso
            val larghezza = size.width - margineSx
            val massimo = max(1.0, ultimi.maxOf { max(it.incassato, it.perso) }) * 1.1

            // griglia e scala
            for (i in 0..4) {
                val y = altezza - altezza * i / 4f
                drawLine(Colori.Bordo, Offset(margineSx, y), Offset(size.width, y), 1f,
                    pathEffect = if (i == 0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                val etichetta = misura.measure(compatto(massimo * i / 4), stileAsse)
                drawText(etichetta, topLeft = Offset(margineSx - etichetta.size.width - 6.dp.toPx(), y - etichetta.size.height / 2))
            }

            val gruppo = larghezza / ultimi.size
            val barra = (gruppo * 0.3f).coerceAtMost(28.dp.toPx())
            ultimi.forEachIndexed { i, m ->
                val centro = margineSx + gruppo * i + gruppo / 2
                fun altezzaDi(v: Double) = (altezza * (v / massimo)).toFloat()
                val hIn = altezzaDi(m.incassato)
                val hOut = altezzaDi(m.perso)
                drawRoundRect(Colori.Verde, Offset(centro - barra - 2, altezza - hIn), Size(barra, hIn), CornerRadius(6f, 6f))
                drawRoundRect(Colori.Rosso, Offset(centro + 2, altezza - hOut), Size(barra, hOut), CornerRadius(6f, 6f))
                // netto del mese
                val hNetto = altezzaDi(abs(m.netto))
                val yNetto = altezza - hNetto
                drawLine(if (m.netto >= 0) Colori.Testo else Colori.Arancio, Offset(centro - barra - 4, yNetto), Offset(centro + barra + 4, yNetto), 3f, cap = StrokeCap.Round)
                val testo = misura.measure(m.mese.format(formatoMese), stileAsse)
                drawText(testo, topLeft = Offset(centro - testo.size.width / 2, altezza + 4.dp.toPx()))
            }
        }
        Legenda("Incassato" to Colori.Verde, "Perso" to Colori.Rosso, "Netto" to Colori.Testo)
    }
}

/** Linea del saldo nel tempo, con area sfumata e linea dello zero. */
@Composable
fun GraficoSaldo(punti: List<Pair<Long, Double>>, modifier: Modifier = Modifier) {
    val misura = rememberTextMeasurer()
    val stileAsse = TextStyle(color = Colori.Tenue, fontSize = 10.sp)
    val finale = punti.lastOrNull()?.second ?: 0.0
    val colore = if (finale >= 0) Colori.Verde else Colori.Rosso
    Canvas(modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = "Andamento del saldo: ora ${finale.euro()}" }) {
        if (punti.size < 2) return@Canvas
        val margineSx = 40.dp.toPx()
        val valori = punti.map { it.second }
        val minimo = minOf(0.0, valori.min())
        val massimo = max(valori.max(), minimo + 1)
        val ampiezza = massimo - minimo
        val larghezza = size.width - margineSx
        fun x(i: Int) = margineSx + larghezza * i / (punti.size - 1)
        fun y(v: Double) = (size.height - 8f - (size.height - 16f) * ((v - minimo) / ampiezza)).toFloat()

        listOf(minimo, (minimo + massimo) / 2, massimo).forEach { v ->
            val t = misura.measure(compatto(v), stileAsse)
            drawText(t, topLeft = Offset(margineSx - t.size.width - 6.dp.toPx(), y(v) - t.size.height / 2))
        }
        drawLine(Colori.Tenue.copy(alpha = 0.5f), Offset(margineSx, y(0.0)), Offset(size.width, y(0.0)), 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))

        val linea = Path().apply {
            moveTo(x(0), y(valori[0]))
            valori.indices.drop(1).forEach { lineTo(x(it), y(valori[it])) }
        }
        val area = Path().apply {
            addPath(linea)
            lineTo(x(valori.lastIndex), size.height)
            lineTo(x(0), size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(colore.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(linea, colore, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(colore, 5.dp.toPx(), Offset(x(valori.lastIndex), y(valori.last())))
    }
}
