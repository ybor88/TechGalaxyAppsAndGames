// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.acos
import kotlin.math.sqrt

// Campo da calcio e parquet da basket in metri reali (vedi drawSoccerPitch/drawBasketCourt),
// condivisi da "Formazione" e "Simula partita".
internal const val PITCH_W = 68f
internal const val PITCH_H = 105f
internal const val PITCH_MARGIN_X = 2f
internal const val PITCH_MARGIN_Y = 3f

internal const val COURT_W = 15f
internal const val COURT_H = 28f
internal const val COURT_MARGIN = 1f

private val LineColor = Color.White.copy(alpha = 0.85f)

internal fun DrawScope.drawSoccerPitch() {
    val s = size.width / (PITCH_W + 2 * PITCH_MARGIN_X)
    val left = PITCH_MARGIN_X * s
    val top = PITCH_MARGIN_Y * s
    val w = PITCH_W * s
    val h = PITCH_H * s
    val stroke = Stroke(width = 2.dp.toPx())

    drawRect(Color(0xFF2E7D32))
    // Strisce del taglio dell'erba.
    val stripes = 10
    val stripeH = h / stripes
    for (i in 0 until stripes step 2) {
        drawRect(Color(0xFF388E3C), topLeft = Offset(0f, top + i * stripeH), size = Size(size.width, stripeH))
    }

    drawRect(LineColor, topLeft = Offset(left, top), size = Size(w, h), style = stroke)
    drawLine(LineColor, Offset(left, top + h / 2), Offset(left + w, top + h / 2), strokeWidth = stroke.width)
    drawCircle(LineColor, radius = 9.15f * s, center = Offset(left + w / 2, top + h / 2), style = stroke)
    drawCircle(LineColor, radius = 3.dp.toPx(), center = Offset(left + w / 2, top + h / 2))

    // Area di rigore, area piccola, dischetto, lunetta e porta per ciascuna metà (alto e basso).
    listOf(false, true).forEach { bottom ->
        fun y(meters: Float) = if (bottom) top + h - meters * s else top + meters * s
        fun rect(width: Float, depth: Float) {
            val x = left + (w - width * s) / 2
            val yTop = if (bottom) y(depth) else top
            drawRect(LineColor, topLeft = Offset(x, yTop), size = Size(width * s, depth * s), style = stroke)
        }
        rect(40.32f, 16.5f)
        rect(18.32f, 5.5f)
        drawCircle(LineColor, radius = 2.5.dp.toPx(), center = Offset(left + w / 2, y(11f)))
        // Lunetta: arco di raggio 9.15m dal dischetto, solo la parte fuori dall'area.
        val arcR = 9.15f * s
        val half = Math.toDegrees(acos((16.5 - 11.0) / 9.15)).toFloat()
        drawArc(
            LineColor,
            startAngle = if (bottom) 270f - half else 90f - half,
            sweepAngle = 2 * half,
            useCenter = false,
            topLeft = Offset(left + w / 2 - arcR, y(11f) - arcR),
            size = Size(arcR * 2, arcR * 2),
            style = stroke,
        )
        // Porta: 7.32m, disegnata oltre la linea di fondo.
        val goalW = 7.32f * s
        val goalDepth = 2f * s
        val goalTop = if (bottom) top + h else top - goalDepth
        drawRect(Color.White.copy(alpha = 0.25f), topLeft = Offset(left + (w - goalW) / 2, goalTop), size = Size(goalW, goalDepth))
        drawRect(Color.White, topLeft = Offset(left + (w - goalW) / 2, goalTop), size = Size(goalW, goalDepth), style = Stroke(width = 3.dp.toPx()))
    }
}

internal fun DrawScope.drawBasketCourt() {
    val s = size.width / (COURT_W + 2 * COURT_MARGIN)
    val left = COURT_MARGIN * s
    val top = COURT_MARGIN * s
    val w = COURT_W * s
    val h = COURT_H * s
    val stroke = Stroke(width = 2.dp.toPx())
    val cx = left + w / 2

    drawRect(Color(0xFF8D5A2B))
    drawRect(Color(0xFFD39B5F), topLeft = Offset(left, top), size = Size(w, h))
    // Listoni del parquet.
    val planks = 14
    for (i in 0 until planks step 2) {
        drawRect(Color(0xFFC98C50), topLeft = Offset(left + i * w / planks, top), size = Size(w / planks, h))
    }

    drawRect(LineColor, topLeft = Offset(left, top), size = Size(w, h), style = stroke)
    drawLine(LineColor, Offset(left, top + h / 2), Offset(left + w, top + h / 2), strokeWidth = stroke.width)
    drawCircle(LineColor, radius = 1.8f * s, center = Offset(cx, top + h / 2), style = stroke)

    // Misure FIBA: area 4.9 x 5.8m, cerchio del tiro libero 1.8m, canestro a 1.575m dalla linea di
    // fondo, tabellone a 1.2m largo 1.8m, linea da tre a 6.75m con i tratti dritti a 0.9m dai lati.
    val hoopDist = 1.575f
    val threeR = 6.75f
    val cornerX = 0.9f
    val cornerDx = COURT_W / 2 - cornerX
    val cornerDepth = hoopDist + sqrt(threeR * threeR - cornerDx * cornerDx)
    val arcHalf = Math.toDegrees(acos((cornerDx / threeR).toDouble())).toFloat()

    listOf(false, true).forEach { bottom ->
        fun y(meters: Float) = if (bottom) top + h - meters * s else top + meters * s
        val keyW = 4.9f * s
        val keyTop = if (bottom) y(5.8f) else top
        drawRect(Color(0xFF1F4E8C).copy(alpha = 0.75f), topLeft = Offset(cx - keyW / 2, keyTop), size = Size(keyW, 5.8f * s))
        drawRect(LineColor, topLeft = Offset(cx - keyW / 2, keyTop), size = Size(keyW, 5.8f * s), style = stroke)

        val ftR = 1.8f * s
        drawCircle(LineColor, radius = ftR, center = Offset(cx, y(5.8f)), style = stroke)

        // Tratti dritti dell'angolo + arco da tre.
        drawLine(LineColor, Offset(left + cornerX * s, y(0f)), Offset(left + cornerX * s, y(cornerDepth)), strokeWidth = stroke.width)
        drawLine(LineColor, Offset(left + w - cornerX * s, y(0f)), Offset(left + w - cornerX * s, y(cornerDepth)), strokeWidth = stroke.width)
        val r3 = threeR * s
        drawArc(
            LineColor,
            startAngle = if (bottom) 180f + arcHalf else arcHalf,
            sweepAngle = 180f - 2 * arcHalf,
            useCenter = false,
            topLeft = Offset(cx - r3, y(hoopDist) - r3),
            size = Size(r3 * 2, r3 * 2),
            style = stroke,
        )

        // Tabellone e canestro.
        drawLine(Color.White, Offset(cx - 0.9f * s, y(1.2f)), Offset(cx + 0.9f * s, y(1.2f)), strokeWidth = 4.dp.toPx())
        drawCircle(Color(0xFFFF6D00), radius = 0.3f * s, center = Offset(cx, y(hoopDist)), style = Stroke(width = 3.dp.toPx()))
    }
}

/** Rapporto larghezza/altezza del campo disegnato (margini compresi). */
internal fun fieldAspectRatio(basket: Boolean): Float =
    if (basket) (COURT_W + 2 * COURT_MARGIN) / (COURT_H + 2 * COURT_MARGIN)
    else (PITCH_W + 2 * PITCH_MARGIN_X) / (PITCH_H + 2 * PITCH_MARGIN_Y)

