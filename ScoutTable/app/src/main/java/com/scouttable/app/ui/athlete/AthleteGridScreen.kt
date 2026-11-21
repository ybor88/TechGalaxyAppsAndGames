// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.athlete

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.athlete.AthleteMetric
import com.scouttable.app.data.athlete.AthleteScoring
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.common.LoadingBar
import com.scouttable.app.ui.common.PlayerAvatar
import com.scouttable.app.ui.common.flagEmojiFor
import com.scouttable.app.ui.theme.ScoutGreen

private val Asphalt = Color(0xFF3B3F45)
private val AsphaltDark = Color(0xFF2B2E33)
private val KerbRed = Color(0xFFD32F2F)
private val GridWhite = Color(0xFFF5F5F5)

/**
 * "Formazione" degli sport individuali.
 * - MotoGP / Formula 1: griglia di partenza su una pista, dal più forte (in pole) al più debole,
 *   con l'auto o la moto nel colore della scuderia; chi non entra in griglia aspetta ai box.
 * - Lotta: il ring con i due migliori uno contro l'altro (angolo rosso e blu) e gli sfidanti a
 *   bordo ring; maschile e femminile separati.
 */
@Composable
fun AthleteGridScreen(sport: Sport, padding: PaddingValues, onOpenPlayer: (String) -> Unit) {
    when {
        sport.isCombat -> RingScreen(sport, padding, onOpenPlayer)
        sport.isTeamSport -> TeamFieldScreen(sport, padding, onOpenPlayer)
        else -> StartingGridScreen(sport, padding, onOpenPlayer)
    }
}

// ---------------------------------------------------------------- griglia di partenza

@Composable
private fun StartingGridScreen(sport: Sport, padding: PaddingValues, onOpenPlayer: (String) -> Unit) {
    val repository = rememberPlayerRepository()
    val loaded by repository.observePlayers(sport).collectAsState<List<Player>, List<Player>?>(initial = null)
    val metrics = listOf(AthleteMetric.PERC_VITTORIE_GARE, AthleteMetric.PUNTI, AthleteMetric.PUNTI_GARA, AthleteMetric.MONDIALI, AthleteMetric.PODI)
    var metricName by rememberSaveable(sport) { mutableStateOf(AthleteMetric.PERC_VITTORIE_GARE.name) }
    val metric = AthleteMetric.valueOf(metricName)
    val ranked = remember(loaded, metric) { AthleteScoring.ranking(loaded.orEmpty(), metric) }
    // F1: due per fila sfalsati; MotoGP: tre per fila.
    val perRow = if (sport == Sport.FORMULA1) 2 else 3
    val gridSize = if (sport == Sport.FORMULA1) 20 else 21
    val grid = ranked.take(gridSize)
    val pits = ranked.drop(gridSize)

    Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Griglia di partenza", style = MaterialTheme.typography.titleMedium)
        Text(
            "In pole il pilota con il valore più alto, poi gli altri fila per fila; ${if (sport == Sport.FORMULA1) "auto" else "moto"} " +
                "nel colore della scuderia. Tocca un pilota per aprirlo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
            metrics.forEach { m ->
                FilterChip(selected = m == metric, onClick = { metricName = m.name }, label = { Text(m.label) }, modifier = Modifier.padding(end = 8.dp))
            }
        }
        when {
            loaded == null -> LoadingBar(label = "Caricamento griglia…")
            grid.isEmpty() -> Text("Nessun pilota con statistiche: usa \"Genera\" con i link Wikipedia.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Track(sport, grid, perRow, metric, onOpenPlayer)
        }
        if (pits.isNotEmpty()) {
            Text("Ai box (fuori griglia)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            pits.forEachIndexed { i, p ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenPlayer(p.id) }.padding(vertical = 4.dp),
                ) {
                    Text("${gridSize + i + 1}°", fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                    Box(modifier = Modifier.size(width = 18.dp, height = 28.dp)) { Vehicle(sport, athleteColor(p)) }
                    Text(p.nome, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    Text(AthleteScoring.formatValue(p, metric), color = ScoutGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            "© Roberto Di Flumeri",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
    }
}

@Composable
private fun Track(sport: Sport, grid: List<Player>, perRow: Int, metric: AthleteMetric, onOpenPlayer: (String) -> Unit) {
    val rows = grid.chunked(perRow)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .drawBehind { drawTrackSurface() }
            .padding(horizontal = 18.dp),
    ) {
        // Linea del via a scacchi.
        Canvas(modifier = Modifier.fillMaxWidth().height(22.dp).padding(top = 6.dp)) { drawCheckeredLine() }
        Text(
            "▲ PARTENZA",
            color = GridWhite,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
        )
        rows.forEachIndexed { rowIndex, row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                (0 until perRow).forEach { col ->
                    val p = row.getOrNull(col)
                    // Sfalsati come in una griglia vera: ogni colonna un po' più indietro della precedente.
                    Box(modifier = Modifier.weight(1f).padding(top = (col * 22).dp), contentAlignment = Alignment.TopCenter) {
                        if (p != null) {
                            GridSlot(sport, rowIndex * perRow + col + 1, p, metric) { onOpenPlayer(p.id) }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun GridSlot(sport: Sport, position: Int, p: Player, metric: AthleteMetric, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp)) {
        // Piazzola: la "L" bianca della griglia con il numero di posizione.
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 78.dp)
                .drawBehind { drawGridBox() },
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(width = if (sport == Sport.FORMULA1) 30.dp else 22.dp, height = 58.dp)) {
                Vehicle(sport, athleteColor(p))
            }
            Text(
                "$position",
                color = GridWhite,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 2.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(name = p.nome, logoPath = p.logoPath, size = 20.dp)
            Text(
                p.nome.substringAfterLast(' ').ifBlank { p.nome },
                color = GridWhite,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(
            "${flagEmojiFor(p.nazione)} ${AthleteScoring.formatValue(p, metric)}".trim(),
            color = Color(0xFFB9F6CA),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

/** Asfalto con i cordoli bianco/rossi ai lati e la linea di mezzeria tratteggiata. */
private fun DrawScope.drawTrackSurface() {
    drawRect(Asphalt)
    val kerbW = 10.dp.toPx()
    val step = 16.dp.toPx()
    var y = 0f
    var red = true
    while (y < size.height) {
        val c = if (red) KerbRed else GridWhite
        drawRect(c, topLeft = Offset(0f, y), size = Size(kerbW, step))
        drawRect(c, topLeft = Offset(size.width - kerbW, y), size = Size(kerbW, step))
        y += step
        red = !red
    }
    // Leggera texture dell'asfalto: strisce più scure.
    var stripe = 0f
    while (stripe < size.height) {
        drawRect(AsphaltDark.copy(alpha = 0.35f), topLeft = Offset(kerbW, stripe), size = Size(size.width - 2 * kerbW, 2.dp.toPx()))
        stripe += 40.dp.toPx()
    }
}

private fun DrawScope.drawCheckeredLine() {
    val cell = size.height / 2
    var x = 0f
    var col = 0
    while (x < size.width) {
        drawRect(if (col % 2 == 0) Color.White else Color.Black, topLeft = Offset(x, 0f), size = Size(cell, cell))
        drawRect(if (col % 2 == 0) Color.Black else Color.White, topLeft = Offset(x, cell), size = Size(cell, cell))
        x += cell
        col++
    }
}

private fun DrawScope.drawGridBox() {
    val stroke = 3.dp.toPx()
    val w = size.width
    // "L" rovesciata: linea davanti e i due lati corti.
    drawLine(GridWhite, Offset(0f, 0f), Offset(w, 0f), strokeWidth = stroke)
    drawLine(GridWhite, Offset(0f, 0f), Offset(0f, size.height * 0.35f), strokeWidth = stroke)
    drawLine(GridWhite, Offset(w, 0f), Offset(w, size.height * 0.35f), strokeWidth = stroke)
}

/** Auto (Formula 1) o moto (MotoGP) vista dall'alto, muso in avanti, nel colore [color]. */
@Composable
private fun Vehicle(sport: Sport, color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (sport == Sport.FORMULA1) drawCar(color) else drawMoto(color)
    }
}

private fun DrawScope.drawCar(color: Color) {
    val w = size.width
    val h = size.height
    val tyre = Color(0xFF111111)
    val cx = w / 2
    // Gomme.
    val tw = w * 0.24f
    val th = h * 0.16f
    listOf(h * 0.14f, h * 0.68f).forEach { ty ->
        drawRoundRect(tyre, topLeft = Offset(0f, ty), size = Size(tw, th), cornerRadius = CornerRadius(3f))
        drawRoundRect(tyre, topLeft = Offset(w - tw, ty), size = Size(tw, th), cornerRadius = CornerRadius(3f))
    }
    // Ala anteriore e posteriore.
    drawRoundRect(color, topLeft = Offset(w * 0.05f, 0f), size = Size(w * 0.9f, h * 0.07f), cornerRadius = CornerRadius(2f))
    drawRoundRect(color, topLeft = Offset(w * 0.12f, h * 0.9f), size = Size(w * 0.76f, h * 0.1f), cornerRadius = CornerRadius(2f))
    // Muso e scocca.
    drawRoundRect(color, topLeft = Offset(cx - w * 0.1f, h * 0.04f), size = Size(w * 0.2f, h * 0.4f), cornerRadius = CornerRadius(6f))
    drawRoundRect(color, topLeft = Offset(cx - w * 0.26f, h * 0.38f), size = Size(w * 0.52f, h * 0.5f), cornerRadius = CornerRadius(10f))
    // Abitacolo e casco.
    drawOval(Color.Black.copy(alpha = 0.55f), topLeft = Offset(cx - w * 0.13f, h * 0.42f), size = Size(w * 0.26f, h * 0.2f))
    drawCircle(Color(0xFFFFEB3B), radius = w * 0.09f, center = Offset(cx, h * 0.5f))
}

private fun DrawScope.drawMoto(color: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2
    val tyre = Color(0xFF111111)
    // Ruote.
    drawRoundRect(tyre, topLeft = Offset(cx - w * 0.16f, 0f), size = Size(w * 0.32f, h * 0.22f), cornerRadius = CornerRadius(8f))
    drawRoundRect(tyre, topLeft = Offset(cx - w * 0.2f, h * 0.76f), size = Size(w * 0.4f, h * 0.24f), cornerRadius = CornerRadius(8f))
    // Carena.
    drawOval(color, topLeft = Offset(cx - w * 0.36f, h * 0.14f), size = Size(w * 0.72f, h * 0.66f))
    // Manubrio.
    drawLine(Color.DarkGray, Offset(0f, h * 0.27f), Offset(w, h * 0.27f), strokeWidth = 3.dp.toPx())
    // Pilota (casco).
    drawCircle(Color(0xFFFFEB3B), radius = w * 0.26f, center = Offset(cx, h * 0.44f))
    drawCircle(Color.Black.copy(alpha = 0.5f), radius = w * 0.13f, center = Offset(cx, h * 0.4f))
}

// ---------------------------------------------------------------- ring

private val RedCorner = Color(0xFFD32F2F)
private val BlueCorner = Color(0xFF1565C0)

@Composable
private fun RingScreen(sport: Sport, padding: PaddingValues, onOpenPlayer: (String) -> Unit) {
    val repository = rememberPlayerRepository()
    val loaded by repository.observePlayers(sport).collectAsState<List<Player>, List<Player>?>(initial = null)
    var female by rememberSaveable(sport) { mutableStateOf(false) }
    val ranked = remember(loaded, female) {
        AthleteScoring.ranking(AthleteScoring.sameGroup(loaded.orEmpty(), sport, female), AthleteMetric.PERCENTUALE)
    }
    Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(arenaTitle(sport), style = MaterialTheme.typography.titleMedium)
        Text(
            when (sport) {
                Sport.SUMO -> "Sul dohyō i due migliori per % di vittorie: il 1° a est, il 2° a ovest, dietro le linee di partenza " +
                    "(shikiri-sen). Attorno al dohyō gli sfidanti in ordine di classifica."
                Sport.MMA -> "Nella gabbia ottagonale i due migliori per % di vittorie: il 1° all'angolo rosso, il 2° all'angolo blu. " +
                    "Fuori dalla gabbia gli sfidanti in ordine di classifica."
                else -> "Sul ring i due migliori per % di vittorie: il 1° all'angolo rosso, il 2° all'angolo blu. " +
                    "A bordo ring gli sfidanti in ordine di classifica."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        GenderSelector(female, { female = it })
        when {
            loaded == null -> LoadingBar(label = "Caricamento ring…")
            ranked.isEmpty() -> Text(
                "Nessun lottatore ${if (female) "femminile" else "maschile"} con statistiche: usa \"Genera\" con i link Wikipedia.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                Ring(sport = sport, red = ranked[0], blue = ranked.getOrNull(1), onOpenPlayer = onOpenPlayer)
                val challengers = ranked.drop(2)
                if (challengers.isNotEmpty()) {
                    Text("A bordo ring", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                    challengers.chunked(4).forEachIndexed { rowIndex, row ->
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            row.forEachIndexed { i, p ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f).clickable { onOpenPlayer(p.id) },
                                ) {
                                    PlayerAvatar(name = p.nome, logoPath = p.logoPath, size = 44.dp)
                                    Text("${rowIndex * 4 + i + 3}° ${p.nome}", style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = TextAlign.Center)
                                    Text(AthleteScoring.formatValue(p, AthleteMetric.PERCENTUALE), style = MaterialTheme.typography.labelSmall, color = ScoutGreen)
                                }
                            }
                            repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        Text(
            "© Roberto Di Flumeri",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
    }
}

@Composable
private fun Ring(sport: Sport, red: Player, blue: Player?, onOpenPlayer: (String) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(top = 8.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (sport) {
                Sport.SUMO -> drawDohyo()
                Sport.MMA -> drawCage()
                else -> drawRing()
            }
        }
        // Sumo: lottatori a est e ovest, uno di fronte all'altro dietro le linee di partenza.
        val sumo = sport == Sport.SUMO
        Fighter(
            red, RedCorner, if (sumo) "Est" else "Angolo rosso",
            Modifier.align(if (sumo) BiasAlignment(-0.62f, 0f) else BiasAlignment(-0.6f, -0.62f)),
        ) { onOpenPlayer(red.id) }
        if (blue != null) {
            Fighter(
                blue, BlueCorner, if (sumo) "Ovest" else "Angolo blu",
                Modifier.align(if (sumo) BiasAlignment(0.62f, 0f) else BiasAlignment(0.6f, 0.62f)),
            ) { onOpenPlayer(blue.id) }
            Text(
                "VS",
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Fighter(p: Player, corner: Color, cornerLabel: String, modifier: Modifier, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.width(130.dp).clickable(onClick = onClick)) {
        Box(modifier = Modifier.clip(CircleShape).border(4.dp, corner, CircleShape)) {
            PlayerAvatar(name = p.nome, logoPath = p.logoPath, size = 72.dp)
        }
        Text(
            p.nome,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A),
            textAlign = TextAlign.Center,
            maxLines = 2,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.85f)).padding(horizontal = 6.dp),
        )
        Text(
            "${AthleteScoring.formatValue(p, AthleteMetric.PERCENTUALE)} · ${cornerLabel}",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(6.dp)).background(corner).padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

/** Ring visto dall'alto: grembiule, tappeto, corde rosso/bianco/blu e i quattro paletti d'angolo. */
/** Titolo della schermata "Ring" per lo sport: dohyō per il sumo, gabbia per le MMA. */
fun arenaTitle(sport: Sport): String = when (sport) {
    Sport.SUMO -> "Il dohyō"
    Sport.MMA -> "La gabbia"
    else -> "Il ring"
}

/**
 * Dohyō del sumo visto dall'alto: piattaforma quadrata di argilla, cerchio di balle di paglia
 * (tawara) con le quattro aperture, sabbia all'interno, le due linee bianche di partenza
 * (shikiri-sen) e le quattro nappe colorate degli angoli del tetto (verde, rosso, bianco, nero).
 */
private fun DrawScope.drawDohyo() {
    val w = size.width
    drawRoundRect(Color(0xFF3E2723), cornerRadius = CornerRadius(24f))
    val platformInset = w * 0.06f
    drawRect(Color(0xFFB58B57), topLeft = Offset(platformInset, platformInset), size = Size(w - 2 * platformInset, w - 2 * platformInset))
    drawRect(Color(0xFF8D6E4A), topLeft = Offset(platformInset, platformInset), size = Size(w - 2 * platformInset, w - 2 * platformInset), style = Stroke(width = 4.dp.toPx()))
    val radius = w * 0.36f
    // Sabbia dentro il cerchio.
    drawCircle(Color(0xFFE9D3A6), radius = radius, center = center)
    // Tawara: anello di paglia con le balle (segmenti) e le quattro aperture tokudawara.
    val bales = 32
    val ringWidth = 10.dp.toPx()
    for (i in 0 until bales) {
        val start = i * 360f / bales
        val gapAt = listOf(0f, 90f, 180f, 270f).any { kotlin.math.abs(((start - it + 540f) % 360f) - 180f) < 4f }
        if (gapAt) continue
        drawArc(
            color = if (i % 2 == 0) Color(0xFFD9C27E) else Color(0xFFC8AE66),
            startAngle = start + 1f,
            sweepAngle = 360f / bales - 2f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = ringWidth),
        )
    }
    // Shikiri-sen: due linee bianche parallele al centro.
    val lineH = w * 0.07f
    val lineGap = w * 0.06f
    listOf(-1f, 1f).forEach { side ->
        val x = center.x + side * lineGap
        drawLine(Color.White, Offset(x, center.y - lineH / 2), Offset(x, center.y + lineH / 2), strokeWidth = 4.dp.toPx())
    }
    // Nappe (fusa) agli angoli: verde NE, rosso SE, bianco SO, nero NO.
    val r = w * 0.035f
    val p = platformInset + r * 1.6f
    drawCircle(Color(0xFF212121), r, Offset(p, p))
    drawCircle(Color(0xFF2E7D32), r, Offset(w - p, p))
    drawCircle(Color(0xFFC62828), r, Offset(w - p, w - p))
    drawCircle(Color.White, r, Offset(p, w - p))
}

/**
 * Gabbia ottagonale delle MMA vista dall'alto: tappeto chiaro, rete nera con i pali imbottiti ai
 * vertici (rosso e blu agli angoli dei due lottatori) e il logo centrale.
 */
private fun DrawScope.drawCage() {
    val w = size.width
    drawRoundRect(Color(0xFF1B1B1B), cornerRadius = CornerRadius(24f))
    val radius = w * 0.46f
    // Ottagono con un lato piatto in alto: vertici a 22,5° + k·45°.
    fun octagon(r: Float) = androidx.compose.ui.graphics.Path().apply {
        for (k in 0 until 8) {
            val angle = Math.toRadians(22.5 + k * 45.0)
            val x = center.x + r * kotlin.math.cos(angle).toFloat()
            val y = center.y + r * kotlin.math.sin(angle).toFloat()
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(octagon(radius), Color(0xFFECEFF1))
    drawPath(octagon(radius * 0.93f), Color(0xFFCFD8DC), style = Stroke(width = 1.5.dp.toPx()))
    // Logo centrale del tappeto.
    drawCircle(Color(0xFFB0BEC5), radius = radius * 0.28f, center = center)
    drawCircle(Color(0xFF90A4AE), radius = radius * 0.28f, center = center, style = Stroke(width = 3.dp.toPx()))
    // Rete.
    drawPath(octagon(radius), Color(0xFF212121), style = Stroke(width = 6.dp.toPx()))
    // Pali imbottiti: angolo rosso in alto a sinistra, blu in basso a destra.
    for (k in 0 until 8) {
        val angle = Math.toRadians(22.5 + k * 45.0)
        val pos = Offset(center.x + radius * kotlin.math.cos(angle).toFloat(), center.y + radius * kotlin.math.sin(angle).toFloat())
        val color = when (k) {
            5 -> RedCorner
            1 -> BlueCorner
            else -> Color(0xFF424242)
        }
        drawCircle(color, radius = w * 0.028f, center = pos)
    }
}

private fun DrawScope.drawRing() {
    val w = size.width
    val apron = w * 0.04f
    drawRoundRect(Color(0xFF263238), cornerRadius = CornerRadius(24f))
    drawRect(Color(0xFF90A4AE), topLeft = Offset(apron, apron), size = Size(w - 2 * apron, w - 2 * apron))
    val inset = w * 0.1f
    val mat = w - 2 * inset
    drawRect(Color(0xFFE3F2FD), topLeft = Offset(inset, inset), size = Size(mat, mat))
    // Logo centrale del tappeto.
    drawCircle(Color(0xFFBBDEFB), radius = mat * 0.18f, center = center)
    drawCircle(Color(0xFF90CAF9), radius = mat * 0.18f, center = center, style = Stroke(width = 3.dp.toPx()))
    // Tre corde per lato.
    val ropeColors = listOf(RedCorner, Color.White, BlueCorner)
    ropeColors.forEachIndexed { i, c ->
        val o = inset - (i + 1) * w * 0.012f
        val stroke = 3.dp.toPx()
        drawRect(c, topLeft = Offset(o, o), size = Size(w - 2 * o, w - 2 * o), style = Stroke(width = stroke))
    }
    // Paletti: rosso in alto a sinistra, blu in basso a destra, bianchi (neutri) gli altri.
    val postR = w * 0.035f
    val p = inset - w * 0.024f
    drawCircle(RedCorner, postR, Offset(p, p))
    drawCircle(Color.White, postR, Offset(w - p, p))
    drawCircle(Color.White, postR, Offset(p, w - p))
    drawCircle(BlueCorner, postR, Offset(w - p, w - p))
}
