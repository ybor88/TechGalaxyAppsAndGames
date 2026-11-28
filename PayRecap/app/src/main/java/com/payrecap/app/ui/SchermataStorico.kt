// PayRecap — © Roberto Di Flumeri
package com.payrecap.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payrecap.app.dominio.Analisi
import com.payrecap.app.dominio.Cedolino
import com.payrecap.app.dominio.Gravita
import com.payrecap.app.dominio.Impostazioni
import com.payrecap.app.dominio.nomeMeseBreve
import java.time.LocalDate

@Composable
fun SchermataStorico(
    cedolini: List<Cedolino>,
    impostazioni: Impostazioni,
    padding: PaddingValues,
    onSeleziona: (Long) -> Unit,
) {
    if (cedolini.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.History, null, tint = Colori.TestoSecondario, modifier = Modifier.size(48.dp))
                SpazioVerticale()
                Text("Nessun cedolino ancora.\nCaricane uno dal Riepilogo per vedere l'andamento mese per mese.",
                    textAlign = TextAlign.Center, color = Colori.TestoSecondario)
            }
        }
        return
    }
    val oggi = LocalDate.now()
    val ordinari = cedolini.filter { !it.aggiuntiva }.sortedBy { it.chiavePeriodo }
    val ultimi = ordinari.takeLast(12)
    val annoCorrente = cedolini.maxOf { it.anno }
    val dellAnno = cedolini.filter { it.anno == annoCorrente }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = SpaziaturaElenco,
    ) {
        item {
            Text("Storico", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text("${cedolini.size} cedolini salvati", fontSize = 13.sp, color = Colori.TestoSecondario)
        }
        item {
            Scheda(titolo = "Andamento netto e lordo", icona = Icons.Filled.BarChart) {
                GraficoMensile(ultimi)
                SpazioVerticale(8.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Legenda(Colori.BluChiaro, "Lordo"); Spacer(Modifier.width(16.dp)); Legenda(Colori.Verde, "Netto")
                }
            }
        }
        item {
            Scheda(titolo = "Totali $annoCorrente") {
                RigaValore("Lordo complessivo", euro(dellAnno.mapNotNull { it.lordo }.takeIf { it.isNotEmpty() }?.sum()))
                RigaValore("Netto complessivo", euro(dellAnno.mapNotNull { it.netto }.takeIf { it.isNotEmpty() }?.sum()), enfasi = true, coloreValore = Colori.Verde)
                RigaValore("Tasse e contributi", euro(dellAnno.mapNotNull { it.totaleTasseEContributi }.takeIf { it.isNotEmpty() }?.sum()), coloreValore = Colori.Rosso)
                RigaValore("Netto medio mensile", euro(dellAnno.filter { !it.aggiuntiva }.mapNotNull { it.netto }.takeIf { it.isNotEmpty() }?.average()))
                RigaValore("Ore di straordinario", dellAnno.mapNotNull { it.straordinarioEffettuate }.takeIf { it.isNotEmpty() }?.sum()?.let { "${numero(it)} ore" } ?: "—")
            }
        }
        items(cedolini, key = { it.id }) { c ->
            val prec = Analisi.precedente(c, cedolini)
            val avvisi = Analisi.avvisi(c, prec, oggi, impostazioni).count { it.gravita != Gravita.INFO }
            RigaStorico(c, prec, avvisi) { onSeleziona(c.id) }
        }
        item { Copyright() }
    }
}

@Composable
private fun Legenda(colore: Color, testo: String) {
    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(colore))
    Spacer(Modifier.width(6.dp))
    Text(testo, fontSize = 12.sp, color = Colori.TestoSecondario)
}

@Composable
private fun RigaStorico(c: Cedolino, prec: Cedolino?, avvisi: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Colori.Card).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(if (c.aggiuntiva) Colori.ArancioChiaro else Colori.BluChiaro),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text(nomeMeseBreve(c.mese).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Colori.Navy)
            Text("${c.anno}", fontSize = 10.sp, color = Colori.TestoSecondario)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.etichettaPeriodo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("Lordo ${euro(c.lordo)}", fontSize = 12.sp, color = Colori.TestoSecondario)
            if (avvisi > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = Colori.Arancio, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$avvisi avvis${if (avvisi == 1) "o" else "i"}", fontSize = 11.sp, color = Colori.Arancio)
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(euro(c.netto), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Colori.Verde)
            val d = if (c.netto != null && prec?.netto != null) c.netto - prec.netto!! else null
            if (d != null && kotlin.math.abs(d) >= 0.005) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (d > 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown, null,
                        tint = if (d > 0) Colori.Verde else Colori.Rosso, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text((if (d > 0) "+" else "") + euro(d), fontSize = 11.sp, color = if (d > 0) Colori.Verde else Colori.Rosso)
                }
            }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = Colori.TestoSecondario)
    }
}

/** Barre affiancate: lordo (chiaro) dietro, netto (verde) davanti, una coppia per mese. */
@Composable
private fun GraficoMensile(dati: List<Cedolino>) {
    if (dati.isEmpty()) return
    val massimo = (dati.mapNotNull { it.lordo } + dati.mapNotNull { it.netto }).maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Column {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = dati.size
            val slot = size.width / n
            val larghezza = (slot * 0.55f).coerceAtMost(44.dp.toPx())
            for (y in 1..3) {
                val yy = size.height * (1 - y / 4f)
                drawLine(Colori.Bordo, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 1f)
            }
            dati.forEachIndexed { i, c ->
                val x = slot * i + (slot - larghezza) / 2
                c.lordo?.let {
                    val h = (it / massimo).toFloat() * size.height
                    drawRoundRect(Colori.BluChiaro, Offset(x, size.height - h), Size(larghezza, h), CornerRadius(8f, 8f))
                    drawRoundRect(Colori.Blu.copy(alpha = 0.25f), Offset(x, size.height - h), Size(larghezza, h), CornerRadius(8f, 8f), style = Stroke(2f))
                }
                c.netto?.let {
                    val h = (it / massimo).toFloat() * size.height
                    val w = larghezza * 0.6f
                    drawRoundRect(Colori.Verde, Offset(x + (larghezza - w) / 2, size.height - h), Size(w, h), CornerRadius(8f, 8f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            dati.forEach { c ->
                Text(nomeMeseBreve(c.mese), fontSize = 10.sp, color = Colori.TestoSecondario, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}
