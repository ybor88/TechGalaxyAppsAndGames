package com.example.playerbase.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.playerbase.data.Sport

/**
 * Identità visiva per modalità sport: colore d'accento, gradiente da usare
 * nelle top bar e sfondi di sezione, ed etichetta dei campionati di riferimento
 * (NBA/FIBA per il basket, FIFA per il calcio).
 */
fun Sport.accentColor(): Color = if (this == Sport.BASKET) BrandColors.basket else BrandColors.calcio

fun Sport.accentDark(): Color = accentColor().darken(0.42f)

fun Sport.accentLight(): Color = accentColor().lighten(0.35f)

fun Sport.headerBrush(): Brush = Brush.horizontalGradient(
    colors = listOf(accentDark(), accentColor())
)

fun Sport.leagueBadges(): List<String> = if (this == Sport.BASKET) listOf("NBA", "WNBA", "FIBA") else listOf("FIFA")

/**
 * Sfondo decorativo a bassa opacità: linee di un campo da basket (parquet + area/arco)
 * oppure di un campo da calcio (metà campo, cerchio, aree di rigore), per rompere la
 * piattezza delle schermate quando si è entrati in una modalità sport specifica.
 */
@Composable
fun SportFieldBackdrop(sport: Sport, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val lineColor = Color.White.copy(alpha = 0.06f)
        val stroke = Stroke(width = w * 0.006f)

        if (sport == Sport.BASKET) {
            // Cerchio di centrocampo e arco dell'area, come su un parquet.
            drawCircle(lineColor, radius = w * 0.34f, center = Offset(w * 0.5f, h * 0.02f), style = stroke)
            drawCircle(lineColor, radius = w * 0.08f, center = Offset(w * 0.5f, h * 0.02f), style = stroke)
            drawArc(
                color = lineColor,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(w * 0.14f, h * 0.55f),
                size = androidx.compose.ui.geometry.Size(w * 0.72f, w * 0.72f),
                style = stroke
            )
            drawLine(lineColor, Offset(0f, h * 0.02f), Offset(w, h * 0.02f), strokeWidth = stroke.width)
        } else {
            // Linea di metà campo, cerchio centrale e aree di rigore stilizzate.
            drawLine(lineColor, Offset(0f, h * 0.42f), Offset(w, h * 0.42f), strokeWidth = stroke.width)
            drawCircle(lineColor, radius = w * 0.22f, center = Offset(w * 0.5f, h * 0.42f), style = stroke)
            drawCircle(lineColor, radius = w * 0.015f, center = Offset(w * 0.5f, h * 0.42f))
            val boxW = w * 0.5f
            val boxH = h * 0.16f
            drawRect(lineColor, topLeft = Offset((w - boxW) / 2f, -boxH * 0.4f), size = androidx.compose.ui.geometry.Size(boxW, boxH), style = stroke)
            drawRect(lineColor, topLeft = Offset((w - boxW) / 2f, h - boxH * 0.6f), size = androidx.compose.ui.geometry.Size(boxW, boxH), style = stroke)
        }
    }
}
