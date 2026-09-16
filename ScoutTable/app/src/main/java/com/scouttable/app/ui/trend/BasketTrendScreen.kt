package com.scouttable.app.ui.trend

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.theme.ScoutGreen
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val YEAR_BUCKET_SIZE = 10
private const val EFF_BUCKET_SIZE = 5

// Valori di Eff medio >= questa soglia finiscono tutti nell'ultima fascia ("30+"): oltre non ha
// senso moltiplicare le colonne per outlier rari, la fascia resta comunque leggibile.
private const val EFF_MAX_BUCKET = 30

private data class TrendCell(val yearIndex: Int, val effIndex: Int, val count: Int)

/**
 * Andamento storico del basket in lista: quanti giocatori per fascia di anno di nascita (decennio)
 * ed efficienza media di carriera (Eff, da Proballers, vedi [Player.effMedio]) — utile per farsi
 * un'idea di come l'efficienza media dei giocatori sia cambiata nelle diverse epoche.
 */
@Composable
fun BasketTrendScreen(padding: PaddingValues) {
    val repository = rememberPlayerRepository()
    val players by repository.observePlayers(Sport.BASKET).collectAsState(initial = emptyList())

    // Serve sia l'anno di nascita sia l'Eff (solo da Proballers): chi manca di uno dei due non
    // può essere posizionato in una cella della griglia.
    val validPlayers = remember(players) { players.filter { it.anno > 0 && it.effMedio > 0 } }

    Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
        Text(
            "Andamento storico: anno di nascita × efficienza",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Trascina per ruotare, pizzica per zoomare. L'altezza di ogni barra è il numero di " +
                "giocatori in quella fascia di anno/efficienza.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        if (validPlayers.size < 2) {
            Text(
                "Servono almeno due giocatori con anno di nascita ed Eff (impostati incollando " +
                    "l'URL Proballers) per costruire il grafico.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            IsometricTrendChart(players = validPlayers, modifier = Modifier.fillMaxWidth().height(420.dp))
        }
    }
}

@Composable
private fun IsometricTrendChart(players: List<Player>, modifier: Modifier) {
    var rotationDeg by remember { mutableFloatStateOf(35f) }
    var scale by remember { mutableFloatStateOf(1f) }
    val textMeasurer = rememberTextMeasurer()

    val grid = remember(players) { buildGrid(players) }
    val maxCount = grid.cells.maxOfOrNull { it.count } ?: 1

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                rotationDeg += pan.x * 0.4f
                scale = (scale * zoom).coerceIn(0.5f, 2.5f)
            }
        },
    ) {
        val angleRad = Math.toRadians(rotationDeg.toDouble()).toFloat()
        val cellSize = 34f * scale
        val barSize = cellSize * 0.7f
        val heightScale = 3.2f * scale
        val originX = size.width / 2f
        val originY = size.height * 0.72f
        val cos30 = cos(Math.PI.toFloat() / 6f)
        val sin30 = sin(Math.PI.toFloat() / 6f)

        fun rotate(x: Float, z: Float): Offset {
            val rx = x * cos(angleRad) - z * sin(angleRad)
            val rz = x * sin(angleRad) + z * cos(angleRad)
            return Offset(rx, rz)
        }

        fun project(x: Float, y: Float, z: Float): Offset {
            val (rx, rz) = rotate(x, z)
            val isoX = (rx - rz) * cos30
            val isoY = (rx + rz) * sin30 - y
            return Offset(originX + isoX, originY + isoY)
        }

        // Griglia di base (linee anno/efficienza), per leggibilità sotto le barre.
        val gridColor = Color.Gray.copy(alpha = 0.35f)
        for (yi in 0..grid.years.size) {
            drawLine(
                color = gridColor,
                start = project(yi * cellSize, 0f, 0f),
                end = project(yi * cellSize, 0f, grid.effs.size * cellSize),
                strokeWidth = 1f,
            )
        }
        for (zi in 0..grid.effs.size) {
            drawLine(
                color = gridColor,
                start = project(0f, 0f, zi * cellSize),
                end = project(grid.years.size * cellSize, 0f, zi * cellSize),
                strokeWidth = 1f,
            )
        }

        // Ordine di disegno dal più lontano al più vicino (algoritmo del pittore), ricalcolato ad
        // ogni frame in base alla rotazione corrente: altrimenti, ruotando, barre vicine finirebbero
        // disegnate sotto quelle lontane invece che sopra.
        val sortedCells = grid.cells.sortedBy { cell ->
            val (rx, rz) = rotate((cell.yearIndex + 0.5f) * cellSize, (cell.effIndex + 0.5f) * cellSize)
            rx + rz
        }

        sortedCells.forEach { cell ->
            if (cell.count <= 0) return@forEach
            val x0 = cell.yearIndex * cellSize + (cellSize - barSize) / 2f
            val x1 = x0 + barSize
            val z0 = cell.effIndex * cellSize + (cellSize - barSize) / 2f
            val z1 = z0 + barSize
            val h = 6f + cell.count * heightScale

            val b1 = project(x1, 0f, z0)
            val b2 = project(x1, 0f, z1)
            val b3 = project(x0, 0f, z1)
            val t0 = project(x0, h, z0)
            val t1 = project(x1, h, z0)
            val t2 = project(x1, h, z1)
            val t3 = project(x0, h, z1)

            val intensity = (cell.count.toFloat() / maxCount).coerceIn(0.2f, 1f)
            val topColor = ScoutGreen.copy(alpha = 0.55f + 0.45f * intensity)
            val rightColor = lerp(ScoutGreen, Color.Black, 0.35f).copy(alpha = 0.55f + 0.45f * intensity)
            val frontColor = lerp(ScoutGreen, Color.Black, 0.55f).copy(alpha = 0.55f + 0.45f * intensity)

            drawFace(listOf(t0, t1, t2, t3), color = topColor)
            drawFace(listOf(t1, t2, b2, b1), color = rightColor)
            drawFace(listOf(t2, t3, b3, b2), color = frontColor)
        }

        // Etichette anno (asse X, alla base) ed efficienza (asse Z, alla base).
        grid.years.forEachIndexed { yi, label ->
            val p = project((yi + 0.5f) * cellSize, -14f, grid.effs.size * cellSize + 6f)
            drawText(
                textMeasurer,
                label,
                topLeft = Offset(p.x - 16f, p.y),
                style = TextStyle(fontSize = 9.sp, color = Color.Gray),
            )
        }
        grid.effs.forEachIndexed { zi, label ->
            val p = project(-10f, -8f, (zi + 0.5f) * cellSize)
            drawText(
                textMeasurer,
                label,
                topLeft = Offset(p.x - 24f, p.y),
                style = TextStyle(fontSize = 9.sp, color = Color.Gray),
            )
        }
    }
}

private fun DrawScope.drawFace(points: List<Offset>, color: Color) {
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        close()
    }
    drawPath(path, color = color)
}

private class TrendGrid(val cells: List<TrendCell>, val years: List<String>, val effs: List<String>)

private fun buildGrid(players: List<Player>): TrendGrid {
    fun yearBucketOf(anno: Int) = (anno / YEAR_BUCKET_SIZE) * YEAR_BUCKET_SIZE
    fun effBucketOf(eff: Int) = min((eff / EFF_BUCKET_SIZE) * EFF_BUCKET_SIZE, EFF_MAX_BUCKET)

    val yearBuckets = players.map(Player::anno).map(::yearBucketOf)
    val minYear = yearBuckets.minOrNull() ?: 0
    val maxYear = yearBuckets.maxOrNull() ?: 0
    val years = (minYear..maxYear step YEAR_BUCKET_SIZE).toList()
    val effs = (0..EFF_MAX_BUCKET step EFF_BUCKET_SIZE).toList()

    val counts = mutableMapOf<Pair<Int, Int>, Int>()
    players.forEach { p ->
        val key = yearBucketOf(p.anno) to effBucketOf(p.effMedio)
        counts[key] = (counts[key] ?: 0) + 1
    }

    val cells = years.mapIndexed { yi, yb ->
        effs.mapIndexed { zi, eb -> TrendCell(yi, zi, counts[yb to eb] ?: 0) }
    }.flatten()

    val yearLabels = years.map { "${it}s" }
    val effLabels = effs.map { if (it >= EFF_MAX_BUCKET) "${it}+" else "$it-${it + EFF_BUCKET_SIZE}" }

    return TrendGrid(cells, yearLabels, effLabels)
}
