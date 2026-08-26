package com.example.turbobrothers.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Sfondo di Napoli disegnato a mano (nessuna foto disponibile nel poster originale):
 * cielo mediterraneo, Vesuvio, golfo e palazzine colorate sul lungomare.
 */
@Composable
fun NaplesBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.62f

        // Cielo
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF2D6FA0), Color(0xFF6FB8DE), Color(0xFFBFE6F2)),
                startY = 0f,
                endY = horizon
            ),
            size = androidx.compose.ui.geometry.Size(w, horizon)
        )

        // Sole
        drawCircle(
            color = Color(0xFFFFE9A8),
            radius = w * 0.11f,
            center = Offset(w * 0.78f, h * 0.16f)
        )
        drawCircle(
            color = Color(0xFFFFF6D8),
            radius = w * 0.07f,
            center = Offset(w * 0.78f, h * 0.16f)
        )

        // Nuvole semplici
        drawCloud(Offset(w * 0.18f, h * 0.14f), w * 0.11f)
        drawCloud(Offset(w * 0.42f, h * 0.22f), w * 0.08f)

        // Vesuvio (sagoma a doppia cima, la principale troncata)
        val vesuvius = Path().apply {
            moveTo(w * 0.30f, horizon)
            lineTo(w * 0.47f, h * 0.30f)
            lineTo(w * 0.53f, h * 0.34f)
            lineTo(w * 0.50f, h * 0.30f)
            lineTo(w * 0.62f, h * 0.20f)
            lineTo(w * 0.68f, h * 0.24f)
            lineTo(w * 0.80f, horizon)
            close()
        }
        drawPath(vesuvius, color = Color(0xFF6E6FA0))
        val vesuviusHaze = Path().apply {
            moveTo(w * 0.30f, horizon)
            lineTo(w * 0.47f, h * 0.30f)
            lineTo(w * 0.62f, h * 0.20f)
            lineTo(w * 0.80f, horizon)
            close()
        }
        drawPath(vesuviusHaze, color = Color(0xFF6E6FA0).copy(alpha = 0.55f))

        // Collina lontana a sinistra
        val hill = Path().apply {
            moveTo(0f, horizon)
            quadraticTo(w * 0.12f, h * 0.42f, w * 0.28f, horizon)
            close()
        }
        drawPath(hill, color = Color(0xFF7C8FAE).copy(alpha = 0.7f))

        // Golfo di Napoli (mare)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF1D77A6), Color(0xFF15547A)),
                startY = horizon,
                endY = h
            ),
            topLeft = Offset(0f, horizon),
            size = androidx.compose.ui.geometry.Size(w, h - horizon)
        )
        // Riflessi/onde
        val waveColor = Color.White.copy(alpha = 0.25f)
        for (i in 0 until 6) {
            val y = horizon + (h - horizon) * (0.2f + i * 0.13f)
            drawLine(
                color = waveColor,
                start = Offset(w * (0.05f + (i % 2) * 0.1f), y),
                end = Offset(w * (0.4f + (i % 2) * 0.1f), y),
                strokeWidth = 3f
            )
        }

        // Palazzine colorate sul lungomare (linea del "livello")
        val buildingColors = listOf(
            Color(0xFFE8B04B), Color(0xFFD9724C), Color(0xFFE8DDA8),
            Color(0xFFC96A6A), Color(0xFFE8B04B), Color(0xFFD9C05A),
            Color(0xFFCF8B5C), Color(0xFFE0A8A0)
        )
        val buildingCount = buildingColors.size
        val buildingWidth = w / buildingCount
        for (i in 0 until buildingCount) {
            val bh = h * (0.10f + (i % 3) * 0.03f)
            val left = i * buildingWidth
            drawRect(
                color = buildingColors[i],
                topLeft = Offset(left, horizon - bh),
                size = androidx.compose.ui.geometry.Size(buildingWidth - 2f, bh)
            )
            // finestrelle
            val winColor = Color.White.copy(alpha = 0.55f)
            val rows = 3
            val cols = 2
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val wx = left + buildingWidth * (0.25f + c * 0.4f)
                    val wy = horizon - bh + bh * (0.2f + r * 0.28f)
                    drawRect(color = winColor, topLeft = Offset(wx, wy), size = androidx.compose.ui.geometry.Size(buildingWidth * 0.14f, bh * 0.12f))
                }
            }
        }
    }
}

private fun DrawScope.drawCloud(center: Offset, radius: Float) {
    val color = Color.White.copy(alpha = 0.85f)
    drawCircle(color = color, radius = radius, center = center)
    drawCircle(color = color, radius = radius * 0.7f, center = center + Offset(radius * 0.9f, radius * 0.15f))
    drawCircle(color = color, radius = radius * 0.6f, center = center + Offset(-radius * 0.8f, radius * 0.2f))
}
