package com.matchmind.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.matchmind.app.dominio.EsitoSelezione
import com.matchmind.app.dominio.StatoSchedina
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Componenti grafici e formattazione condivisi. © Roberto Di Flumeri

private val italiano = Locale.ITALY
private val euro = NumberFormat.getCurrencyInstance(italiano)
private val dataOra = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", italiano)
private val data = DateTimeFormatter.ofPattern("d MMM yyyy", italiano)

fun Double.euro(): String = euro.format(this)
fun Double.euroConSegno(): String = (if (this > 0) "+" else "") + euro.format(this)
fun Double.quota(): String = String.format(italiano, "%.2f", this)
fun Double.percentuale(): String = String.format(italiano, "%.0f%%", this * 100)
fun Double.percentualeFine(): String = when {
    this >= 0.01 -> String.format(italiano, "%.1f%%", this * 100)
    this > 0 -> String.format(italiano, "%.2f%%", this * 100)
    else -> "0%"
}
fun Long.dataOra(): String = dataOra.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))
fun Long.data(): String = data.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

fun coloreStato(s: StatoSchedina): Color = when (s) {
    StatoSchedina.VINTA -> Colori.Verde
    StatoSchedina.PERSA -> Colori.Rosso
    StatoSchedina.RIMBORSATA -> Colori.Arancio
    StatoSchedina.IN_CORSO -> Colori.Grigio
}

fun coloreEsito(e: EsitoSelezione): Color = when (e) {
    EsitoSelezione.VINTA -> Colori.Verde
    EsitoSelezione.PERSA -> Colori.Rosso
    EsitoSelezione.NULLA -> Colori.Arancio
    EsitoSelezione.IN_ATTESA -> Colori.Grigio
}

@Composable
fun Scheda(
    modifier: Modifier = Modifier,
    bordo: Color? = null,
    contenuto: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Colori.Superficie),
        border = BorderStroke(1.dp, bordo ?: Colori.Bordo),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = contenuto)
    }
}

@Composable
fun Etichetta(testo: String, colore: Color = Colori.Tenue) {
    Text(testo.uppercase(), style = MaterialTheme.typography.labelSmall, color = colore)
}

@Composable
fun Pillola(testo: String, colore: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(colore.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(testo, color = colore, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun Pallino(colore: Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(colore))
}

@Composable
fun Valore(etichetta: String, valore: String, colore: Color = Colori.Testo, modifier: Modifier = Modifier) {
    Column(modifier) {
        Etichetta(etichetta)
        Text(valore, style = MaterialTheme.typography.titleMedium, color = colore, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RigaValori(vararg valori: Triple<String, String, Color>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        valori.forEach { (e, v, c) -> Valore(e, v, c, Modifier.weight(1f)) }
    }
}
