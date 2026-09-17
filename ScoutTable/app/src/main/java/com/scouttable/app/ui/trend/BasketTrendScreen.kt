package com.scouttable.app.ui.trend

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.theme.ScoutBlue
import com.scouttable.app.ui.theme.ScoutGreen
import kotlin.math.min
import kotlin.math.roundToInt

private const val YEAR_BUCKET_SIZE = 10
private const val EFF_BUCKET_SIZE = 5

// Valori di Eff medio >= questa soglia finiscono tutti nell'ultima fascia ("30+"): oltre non ha
// senso moltiplicare le fette per outlier rari, la fascia resta comunque leggibile.
private const val EFF_MAX_BUCKET = 30

private data class Slice(val label: String, val count: Int, val color: Color)

// Rampa sequenziale (blu -> verde, coerente col brand) sotto-campionata in base a quante fasce
// sono realmente popolate: con poche fette i colori restano ben distanziati invece di stiparsi
// tutti vicini sulla stessa porzione della rampa.
private val TrendColorRamp = List(8) { i -> lerp(ScoutBlue, ScoutGreen, i / 7f) }

/**
 * Andamento storico del basket in lista: due grafici a torta separati, uno per decennio di
 * nascita e uno per fascia di efficienza media di carriera (Eff, da Proballers, vedi
 * [Player.effMedio]) — sostituisce il precedente grafico 3D isometrico (ruotabile trascinando),
 * segnalato poco leggibile: due torte statiche mostrano le stesse due dimensioni senza bisogno di
 * interazione per essere lette.
 */
@Composable
fun BasketTrendScreen(padding: PaddingValues) {
    val repository = rememberPlayerRepository()
    val players by repository.observePlayers(Sport.BASKET).collectAsState(initial = emptyList())

    // Serve sia l'anno di nascita sia l'Eff (solo da Proballers): chi manca di uno dei due non
    // può essere posizionato in nessuna delle due torte.
    val validPlayers = remember(players) { players.filter { it.anno > 0 && it.effMedio > 0 } }
    // Stessi due grafici, ma limitati ai soli preferiti (stella, flag manuale in EditPlayerDialog):
    // sezione separata richiesta esplicitamente per tenere d'occhio l'andamento della rosa
    // "ristretta" senza che i tanti giocatori non stellati la diluiscano nelle torte principali.
    val starPlayers = remember(validPlayers) { validPlayers.filter { it.star } }

    Column(
        modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text("Andamento storico", style = MaterialTheme.typography.titleMedium)
        Text(
            "Distribuzione dei giocatori per decennio di nascita e per fascia di efficienza media (Eff).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        if (validPlayers.size < 2) {
            Text(
                "Servono almeno due giocatori con anno di nascita ed Eff (impostati incollando " +
                    "l'URL Proballers) per costruire i grafici.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TrendPieCard("Per decennio di nascita", decadeSlices(validPlayers))
            Spacer(modifier = Modifier.height(20.dp))
            TrendPieCard("Per fascia di efficienza (Eff)", effSlices(validPlayers))

            Spacer(modifier = Modifier.height(28.dp))
            Text("Solo preferiti ⭐", style = MaterialTheme.typography.titleMedium)
            Text(
                "Stessa distribuzione, limitata ai giocatori segnati come preferiti.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            if (starPlayers.size < 2) {
                Text(
                    "Servono almeno due preferiti con anno di nascita ed Eff per costruire i grafici.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TrendPieCard("Per decennio di nascita", decadeSlices(starPlayers))
                Spacer(modifier = Modifier.height(20.dp))
                TrendPieCard("Per fascia di efficienza (Eff)", effSlices(starPlayers))
            }
        }
    }
}

private fun decadeSlices(players: List<Player>): List<Slice> {
    val counts = players.groupingBy { (it.anno / YEAR_BUCKET_SIZE) * YEAR_BUCKET_SIZE }.eachCount()
    val decades = counts.keys.sorted()
    return colorize(decades.map { d -> (d.toString() + "s") to (counts[d] ?: 0) })
}

private fun effSlices(players: List<Player>): List<Slice> {
    fun bucketOf(eff: Int) = min((eff / EFF_BUCKET_SIZE) * EFF_BUCKET_SIZE, EFF_MAX_BUCKET)
    val counts = players.groupingBy { bucketOf(it.effMedio) }.eachCount()
    val buckets = counts.keys.sorted()
    val labels = buckets.map { b -> if (b >= EFF_MAX_BUCKET) "${b}+" else "$b-${b + EFF_BUCKET_SIZE}" }
    return colorize(labels.zip(buckets.map { counts[it] ?: 0 }))
}

// Assegna un colore della rampa a ciascuna fascia popolata, distribuendo gli step in modo uniforme
// sull'intera rampa indipendentemente da quante fasce ci sono (stesso approccio del vecchio
// grafico a torta della distribuzione rating).
private fun colorize(entries: List<Pair<String, Int>>): List<Slice> =
    entries.mapIndexed { index, (label, count) ->
        val stepIndex = if (entries.size <= 1) {
            TrendColorRamp.size - 1
        } else {
            (index * (TrendColorRamp.size - 1).toFloat() / (entries.size - 1)).roundToInt()
        }
        Slice(label, count, TrendColorRamp[stepIndex])
    }

@Composable
private fun TrendPieCard(title: String, slices: List<Slice>) {
    val total = slices.sumOf { it.count }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1.4f).padding(top = 12.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                DonutChart(
                    slices = slices,
                    total = total,
                    modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$total",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "giocatori",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                slices.forEach { slice ->
                    val pct = (slice.count.toDouble() / total * 1000).roundToInt() / 10.0
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(slice.color))
                        Text(
                            slice.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(56.dp).padding(start = 8.dp),
                        )
                        Text(
                            "${slice.count} giocatori",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "$pct%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutChart(slices: List<Slice>, total: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.22f
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        // Piccolo distacco angolare tra le fette, come nel vecchio grafico rating: aiuta a
        // distinguere fette adiacenti dello stesso colore o di colori simili sulla rampa.
        val gapDegrees = if (slices.size > 1) 2.5f else 0f
        var startAngle = -90f
        slices.forEach { slice ->
            val rawSweep = if (total == 0) 0f else 360f * slice.count / total
            val drawSweep = (rawSweep - gapDegrees).coerceAtLeast(0f)
            drawArc(
                color = slice.color,
                startAngle = startAngle + gapDegrees / 2f,
                sweepAngle = drawSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )
            startAngle += rawSweep
        }
    }
}
