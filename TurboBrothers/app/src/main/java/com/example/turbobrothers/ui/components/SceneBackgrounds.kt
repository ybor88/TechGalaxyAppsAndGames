package com.example.turbobrothers.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.random.Random

/**
 * Sfondi di gioco disegnati interamente a vettori (niente bitmap): restano
 * sempre nitidi a qualsiasi risoluzione, a differenza di un ritaglio fotografico
 * ingrandito a schermo intero.
 */

@Composable
fun NewYorkBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.66f

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF1B1F3B), Color(0xFF4A3B6B), Color(0xFFE8895C)),
            ),
            size = Size(w, horizon)
        )
        // Sole/luna al tramonto
        drawCircle(color = Color(0xFFFFD79A), radius = w * 0.09f, center = Offset(w * 0.5f, horizon - h * 0.02f))

        val rnd = Random(42)
        val buildingColor = Color(0xFF241F3D)
        val litWindow = Color(0xFFFFE79A)
        var x = 0f
        var i = 0
        while (x < w) {
            val bw = w * (0.09f + rnd.nextFloat() * 0.06f)
            val bh = horizon * (0.35f + rnd.nextFloat() * 0.45f)
            drawRect(color = buildingColor, topLeft = Offset(x, horizon - bh), size = Size(bw - 3f, bh))
            // finestre
            val cols = 3
            val rows = (bh / (h * 0.045f)).toInt().coerceIn(3, 10)
            for (c in 0 until cols) {
                for (r in 0 until rows) {
                    if (rnd.nextFloat() < 0.55f) {
                        val wx = x + bw * (0.15f + c * 0.28f)
                        val wy = horizon - bh + bh * (0.08f + r * (0.85f / rows))
                        drawRect(color = litWindow.copy(alpha = 0.85f), topLeft = Offset(wx, wy), size = Size(bw * 0.12f, bh * 0.04f))
                    }
                }
            }
            i++
            // guglia sull'edificio piu' alto ogni tanto
            if (i % 4 == 0) {
                drawLine(buildingColor, Offset(x + bw / 2, horizon - bh), Offset(x + bw / 2, horizon - bh - h * 0.06f), strokeWidth = 5f)
            }
            x += bw
        }

        drawRect(color = Color(0xFF15121F), topLeft = Offset(0f, horizon), size = Size(w, h - horizon))
        // strada con riga centrale
        val roadLineY = horizon + (h - horizon) * 0.5f
        var lx = 0f
        while (lx < w) {
            drawLine(Color(0xFFFFD79A).copy(alpha = 0.5f), Offset(lx, roadLineY), Offset(lx + w * 0.05f, roadLineY), strokeWidth = 4f)
            lx += w * 0.11f
        }
    }
}

@Composable
fun ForestBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.68f

        drawRect(
            brush = Brush.verticalGradient(colors = listOf(Color(0xFF6FC6E8), Color(0xFFBFE8F5))),
            size = Size(w, horizon)
        )
        drawCircle(color = Color(0xFFFFE9A0), radius = w * 0.1f, center = Offset(w * 0.82f, h * 0.14f))
        drawCloudShape(Offset(w * 0.2f, h * 0.16f), w * 0.1f)
        drawCloudShape(Offset(w * 0.5f, h * 0.1f), w * 0.07f)

        // collina lontana
        val farHill = Path().apply {
            moveTo(0f, horizon)
            quadraticTo(w * 0.5f, horizon - h * 0.14f, w, horizon)
            close()
        }
        drawPath(farHill, color = Color(0xFF6FAE5C))

        // collina vicina
        val nearHill = Path().apply {
            moveTo(0f, horizon + h * 0.02f)
            quadraticTo(w * 0.35f, horizon - h * 0.08f, w, horizon + h * 0.03f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(nearHill, color = Color(0xFF3F8F52))

        val rnd = Random(7)
        val treeXs = listOf(0.08f, 0.2f, 0.33f, 0.47f, 0.6f, 0.73f, 0.86f, 0.95f)
        for (fx in treeXs) {
            val tx = w * fx
            val ty = horizon + h * (0.01f + rnd.nextFloat() * 0.03f)
            val scale = 0.7f + rnd.nextFloat() * 0.5f
            drawPineTree(tx, ty, w * 0.05f * scale, h * 0.14f * scale)
        }
    }
}

private fun DrawScope.drawPineTree(x: Float, baseY: Float, radius: Float, height: Float) {
    drawRect(color = Color(0xFF6B4423), topLeft = Offset(x - radius * 0.12f, baseY - height * 0.18f), size = Size(radius * 0.24f, height * 0.18f))
    val green = Color(0xFF1F6B3A)
    for (layer in 0 until 3) {
        val ly = baseY - height * (0.18f + layer * 0.28f)
        val lw = radius * (1.1f - layer * 0.22f)
        val path = Path().apply {
            moveTo(x, ly - height * 0.32f)
            lineTo(x - lw, ly)
            lineTo(x + lw, ly)
            close()
        }
        drawPath(path, color = green)
    }
}

@Composable
fun BeachBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.58f
        val waterEnd = h * 0.82f

        drawRect(
            brush = Brush.verticalGradient(colors = listOf(Color(0xFF57C7EC), Color(0xFFCDEFF7))),
            size = Size(w, horizon)
        )
        drawCircle(color = Color(0xFFFFF0A8), radius = w * 0.1f, center = Offset(w * 0.22f, h * 0.15f))
        drawCloudShape(Offset(w * 0.65f, h * 0.13f), w * 0.09f)

        drawRect(
            brush = Brush.verticalGradient(colors = listOf(Color(0xFF1F9AC7), Color(0xFF0E6E99))),
            topLeft = Offset(0f, horizon),
            size = Size(w, waterEnd - horizon)
        )
        val foam = Color.White.copy(alpha = 0.5f)
        for (i in 0 until 5) {
            val y = horizon + (waterEnd - horizon) * (0.25f + i * 0.16f)
            drawLine(foam, Offset(w * 0.05f, y), Offset(w * 0.55f, y), strokeWidth = 3f)
        }

        drawRect(color = Color(0xFFEFD9A0), topLeft = Offset(0f, waterEnd), size = Size(w, h - waterEnd))

        // palma
        val px = w * 0.78f
        val py = waterEnd
        drawRect(color = Color(0xFF8A5A2B), topLeft = Offset(px - w * 0.015f, py - h * 0.28f), size = Size(w * 0.03f, h * 0.28f))
        val leafColor = Color(0xFF2E8B4E)
        val leafDirs = listOf(-1.0, -0.5, 0.0, 0.5, 1.0)
        for (d in leafDirs) {
            val path = Path().apply {
                moveTo(px, py - h * 0.27f)
                val endX = px + (w * 0.14f * d).toFloat()
                val endY = py - h * (0.30f + 0.05f * (1 - kotlin.math.abs(d))).toFloat()
                quadraticTo(px + (w * 0.07f * d).toFloat(), py - h * 0.34f, endX, endY)
            }
            drawPath(path, color = leafColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.02f))
        }
    }
}

/** Montagna: cime innevate, abeti imbiancati e un piccolo rifugio di legno. */
@Composable
fun MountainBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.62f

        drawRect(
            brush = Brush.verticalGradient(colors = listOf(Color(0xFF8FBEE0), Color(0xFFEAF4FA))),
            size = Size(w, horizon)
        )
        drawCircle(color = Color(0xFFFFF6D0), radius = w * 0.08f, center = Offset(w * 0.8f, h * 0.14f))
        drawCloudShape(Offset(w * 0.22f, h * 0.15f), w * 0.09f)
        drawCloudShape(Offset(w * 0.5f, h * 0.09f), w * 0.06f)

        // Catena montuosa lontana, sfumata
        val farPeaks = Path().apply {
            moveTo(0f, horizon)
            lineTo(w * 0.15f, horizon - h * 0.16f)
            lineTo(w * 0.30f, horizon)
            lineTo(w * 0.45f, horizon - h * 0.22f)
            lineTo(w * 0.62f, horizon)
            lineTo(w * 0.78f, horizon - h * 0.14f)
            lineTo(w, horizon)
            close()
        }
        drawPath(farPeaks, color = Color(0xFF9FB6D0).copy(alpha = 0.65f))

        // Picco principale con neve in cima
        val mainPeak = Path().apply {
            moveTo(w * 0.32f, horizon)
            lineTo(w * 0.55f, h * 0.20f)
            lineTo(w * 0.78f, horizon)
            close()
        }
        drawPath(mainPeak, color = Color(0xFF6E7C93))
        val snowCap = Path().apply {
            moveTo(w * 0.55f, h * 0.20f)
            lineTo(w * 0.47f, h * 0.34f)
            lineTo(w * 0.51f, h * 0.33f)
            lineTo(w * 0.55f, h * 0.38f)
            lineTo(w * 0.59f, h * 0.33f)
            lineTo(w * 0.63f, h * 0.34f)
            close()
        }
        drawPath(snowCap, color = Color.White)

        drawRect(color = Color(0xFFEFF6FA), topLeft = Offset(0f, horizon), size = Size(w, h - horizon))

        // Rifugio di legno
        val cabinX = w * 0.68f
        val cabinW = w * 0.14f
        val cabinH = h * 0.09f
        drawRect(color = Color(0xFF7A5230), topLeft = Offset(cabinX, horizon - cabinH), size = Size(cabinW, cabinH))
        val roof = Path().apply {
            moveTo(cabinX - w * 0.015f, horizon - cabinH)
            lineTo(cabinX + cabinW / 2, horizon - cabinH - h * 0.045f)
            lineTo(cabinX + cabinW + w * 0.015f, horizon - cabinH)
            close()
        }
        drawPath(roof, color = Color(0xFF8B3A2E))

        val treeXs = listOf(0.06f, 0.16f, 0.9f, 0.97f)
        for (fx in treeXs) {
            drawSnowyPine(w * fx, horizon + h * 0.01f, w * 0.045f, h * 0.15f)
        }
    }
}

private fun DrawScope.drawSnowyPine(x: Float, baseY: Float, radius: Float, height: Float) {
    drawRect(color = Color(0xFF5C4530), topLeft = Offset(x - radius * 0.12f, baseY - height * 0.18f), size = Size(radius * 0.24f, height * 0.18f))
    val green = Color(0xFF2E6B4E)
    for (layer in 0 until 3) {
        val ly = baseY - height * (0.18f + layer * 0.28f)
        val lw = radius * (1.1f - layer * 0.22f)
        val path = Path().apply {
            moveTo(x, ly - height * 0.32f)
            lineTo(x - lw, ly)
            lineTo(x + lw, ly)
            close()
        }
        drawPath(path, color = green)
        // cappuccio di neve sulla punta di ogni livello
        val snow = Path().apply {
            moveTo(x, ly - height * 0.32f)
            lineTo(x - lw * 0.4f, ly - height * 0.12f)
            lineTo(x + lw * 0.4f, ly - height * 0.12f)
            close()
        }
        drawPath(snow, color = Color.White.copy(alpha = 0.85f))
    }
}

/** Deserto: dune al tramonto, cactus e una piramide lontana. */
@Composable
fun DesertBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.64f

        drawRect(
            brush = Brush.verticalGradient(colors = listOf(Color(0xFFE8895C), Color(0xFFF7D28A))),
            size = Size(w, horizon)
        )
        drawCircle(color = Color(0xFFFFE9A0), radius = w * 0.13f, center = Offset(w * 0.5f, horizon - h * 0.03f))

        // Piramidi lontane, sfumate
        val pyramid1 = Path().apply {
            moveTo(w * 0.62f, horizon)
            lineTo(w * 0.72f, horizon - h * 0.14f)
            lineTo(w * 0.82f, horizon)
            close()
        }
        drawPath(pyramid1, color = Color(0xFFC98455).copy(alpha = 0.7f))
        val pyramid2 = Path().apply {
            moveTo(w * 0.76f, horizon)
            lineTo(w * 0.84f, horizon - h * 0.09f)
            lineTo(w * 0.92f, horizon)
            close()
        }
        drawPath(pyramid2, color = Color(0xFFC98455).copy(alpha = 0.55f))

        // Dune ondulate
        val duneFar = Path().apply {
            moveTo(0f, horizon)
            quadraticTo(w * 0.3f, horizon - h * 0.05f, w * 0.55f, horizon)
            quadraticTo(w * 0.8f, horizon + h * 0.03f, w, horizon - h * 0.02f)
            lineTo(w, horizon)
            close()
        }
        drawPath(duneFar, color = Color(0xFFE0AD6E))

        drawRect(color = Color(0xFFD9A066), topLeft = Offset(0f, horizon), size = Size(w, h - horizon))
        val duneNear = Path().apply {
            moveTo(0f, horizon + h * 0.05f)
            quadraticTo(w * 0.25f, horizon - h * 0.02f, w * 0.5f, horizon + h * 0.06f)
            quadraticTo(w * 0.75f, horizon + h * 0.13f, w, horizon + h * 0.04f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(duneNear, color = Color(0xFFCB8F52))

        drawCactus(w * 0.14f, horizon + h * 0.06f, w * 0.018f, h * 0.11f)
        drawCactus(w * 0.9f, horizon + h * 0.03f, w * 0.016f, h * 0.09f)
    }
}

private fun DrawScope.drawCactus(x: Float, baseY: Float, trunkW: Float, height: Float) {
    val green = Color(0xFF3F7D4A)
    drawRect(color = green, topLeft = Offset(x - trunkW, baseY - height), size = Size(trunkW * 2, height))
    // braccio sinistro
    drawRect(color = green, topLeft = Offset(x - trunkW * 3.2f, baseY - height * 0.55f), size = Size(trunkW * 2, height * 0.32f))
    drawRect(color = green, topLeft = Offset(x - trunkW * 3.2f, baseY - height * 0.8f), size = Size(trunkW * 2, height * 0.25f))
    // braccio destro
    drawRect(color = green, topLeft = Offset(x + trunkW * 1.2f, baseY - height * 0.42f), size = Size(trunkW * 2, height * 0.32f))
    drawRect(color = green, topLeft = Offset(x + trunkW * 1.2f, baseY - height * 0.67f), size = Size(trunkW * 2, height * 0.25f))
}

private fun DrawScope.drawCloudShape(center: Offset, radius: Float) {
    val color = Color.White.copy(alpha = 0.9f)
    drawCircle(color = color, radius = radius, center = center)
    drawCircle(color = color, radius = radius * 0.7f, center = center + Offset(radius * 0.9f, radius * 0.15f))
    drawCircle(color = color, radius = radius * 0.6f, center = center + Offset(-radius * 0.8f, radius * 0.2f))
}

/** Volla: piccolo comune vesuviano, campanile in piazza e pini ombrella. */
@Composable
fun VollaBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.64f

        drawRect(
            brush = Brush.verticalGradient(colors = listOf(Color(0xFF6FAEDD), Color(0xFFF5DDA8))),
            size = Size(w, horizon)
        )
        drawCircle(color = Color(0xFFFFE39A), radius = w * 0.09f, center = Offset(w * 0.2f, h * 0.15f))
        drawCloudShape(Offset(w * 0.55f, h * 0.12f), w * 0.08f)

        // Vesuvio lontano e sfumato, sullo sfondo
        val vesuvius = Path().apply {
            moveTo(w * 0.62f, horizon)
            lineTo(w * 0.76f, h * 0.34f)
            lineTo(w * 0.80f, h * 0.37f)
            lineTo(w * 0.78f, h * 0.34f)
            lineTo(w * 0.90f, horizon)
            close()
        }
        drawPath(vesuvius, color = Color(0xFF8B93B8).copy(alpha = 0.6f))

        // Case basse color terracotta lungo l'orizzonte
        val houseColors = listOf(Color(0xFFE0B27A), Color(0xFFD99B7A), Color(0xFFE8C88F))
        var hx = 0f
        var i = 0
        while (hx < w) {
            val hw = w * 0.14f
            val hh = h * (0.08f + (i % 2) * 0.02f)
            drawRect(color = houseColors[i % houseColors.size], topLeft = Offset(hx, horizon - hh), size = Size(hw - 3f, hh))
            val roof = Path().apply {
                moveTo(hx - 3f, horizon - hh)
                lineTo(hx + hw / 2, horizon - hh - h * 0.035f)
                lineTo(hx + hw, horizon - hh)
                close()
            }
            drawPath(roof, color = Color(0xFFA5432E))
            hx += hw
            i++
        }

        // Campanile centrale in piazza
        val towerX = w * 0.42f
        val towerW = w * 0.07f
        val towerH = h * 0.30f
        drawRect(color = Color(0xFFE8DCC0), topLeft = Offset(towerX, horizon - towerH), size = Size(towerW, towerH))
        val spire = Path().apply {
            moveTo(towerX - 4f, horizon - towerH)
            lineTo(towerX + towerW / 2, horizon - towerH - h * 0.08f)
            lineTo(towerX + towerW + 4f, horizon - towerH)
            close()
        }
        drawPath(spire, color = Color(0xFF8B3A2E))
        drawCircle(color = Color(0xFF5C4A32), radius = towerW * 0.22f, center = Offset(towerX + towerW / 2, horizon - towerH * 0.7f))

        // Pini ad ombrello
        drawUmbrellaPine(w * 0.14f, horizon, w * 0.05f, h * 0.16f)
        drawUmbrellaPine(w * 0.85f, horizon, w * 0.045f, h * 0.14f)

        drawRect(color = Color(0xFFD8C49A), topLeft = Offset(0f, horizon), size = Size(w, h - horizon))
        val lineColor = Color(0xFFB89F6E)
        for (x in 0 until 8) {
            val lx = w * x / 8f
            drawLine(lineColor, Offset(lx, horizon), Offset(lx, h), strokeWidth = 2f)
        }
    }
}

private fun DrawScope.drawUmbrellaPine(x: Float, baseY: Float, trunkW: Float, height: Float) {
    drawRect(color = Color(0xFF6B4423), topLeft = Offset(x - trunkW * 0.15f, baseY - height * 0.5f), size = Size(trunkW * 0.3f, height * 0.5f))
    drawLine(Color(0xFF6B4423), Offset(x, baseY - height * 0.5f), Offset(x - trunkW * 0.3f, baseY - height * 0.6f), strokeWidth = trunkW * 0.15f)
    drawCircle(color = Color(0xFF3F7D4A), radius = trunkW * 1.6f, center = Offset(x, baseY - height * 0.72f))
    drawCircle(color = Color(0xFF4C8F56), radius = trunkW * 1.3f, center = Offset(x - trunkW * 0.5f, baseY - height * 0.8f))
}

/** Saviano: campagna dell'agro nolano, campi coltivati e piccola chiesa. */
@Composable
fun SavianoBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.6f

        drawRect(
            brush = Brush.verticalGradient(colors = listOf(Color(0xFF8FD0EC), Color(0xFFF2EFC8))),
            size = Size(w, horizon)
        )
        drawCircle(color = Color(0xFFFFF2B0), radius = w * 0.095f, center = Offset(w * 0.18f, h * 0.16f))
        drawCloudShape(Offset(w * 0.6f, h * 0.11f), w * 0.09f)
        drawCloudShape(Offset(w * 0.85f, h * 0.2f), w * 0.06f)

        // Campi coltivati a strisce, in prospettiva
        val fieldColors = listOf(Color(0xFF7CB559), Color(0xFF6BA84C), Color(0xFF8AC468))
        val fieldTop = horizon
        val fieldBottom = h
        var fy = 0
        val rows = 5
        while (fy < rows) {
            val t = fy / rows.toFloat()
            val yTop = fieldTop + (fieldBottom - fieldTop) * t
            val yBottom = fieldTop + (fieldBottom - fieldTop) * ((fy + 1) / rows.toFloat())
            drawRect(color = fieldColors[fy % fieldColors.size], topLeft = Offset(0f, yTop), size = Size(w, yBottom - yTop + 1f))
            fy++
        }

        // Chiesetta
        val churchX = w * 0.5f
        val churchW = w * 0.16f
        val churchH = h * 0.13f
        drawRect(color = Color(0xFFF3EEDD), topLeft = Offset(churchX - churchW / 2, horizon - churchH), size = Size(churchW, churchH))
        val pediment = Path().apply {
            moveTo(churchX - churchW / 2 - 4f, horizon - churchH)
            lineTo(churchX, horizon - churchH - h * 0.045f)
            lineTo(churchX + churchW / 2 + 4f, horizon - churchH)
            close()
        }
        drawPath(pediment, color = Color(0xFFDCCB9A))
        // campanile laterale
        val bellX = churchX + churchW * 0.55f
        val bellW = churchW * 0.22f
        val bellH = h * 0.20f
        drawRect(color = Color(0xFFF3EEDD), topLeft = Offset(bellX, horizon - bellH), size = Size(bellW, bellH))
        val bellRoof = Path().apply {
            moveTo(bellX - 3f, horizon - bellH)
            lineTo(bellX + bellW / 2, horizon - bellH - h * 0.05f)
            lineTo(bellX + bellW + 3f, horizon - bellH)
            close()
        }
        drawPath(bellRoof, color = Color(0xFF6E7C8A))

        // Filari di alberi da frutto
        val treeRowY = horizon + (h - horizon) * 0.12f
        for (col in 0 until 7) {
            val tx = w * (0.06f + col * 0.13f)
            drawOrchardTree(tx, treeRowY, w * 0.035f, h * 0.07f)
        }
        val treeRowY2 = horizon + (h - horizon) * 0.32f
        for (col in 0 until 6) {
            val tx = w * (0.1f + col * 0.15f)
            drawOrchardTree(tx, treeRowY2, w * 0.04f, h * 0.08f)
        }
    }
}

private fun DrawScope.drawOrchardTree(x: Float, y: Float, radius: Float, height: Float) {
    drawRect(color = Color(0xFF7A5230), topLeft = Offset(x - radius * 0.12f, y), size = Size(radius * 0.24f, height * 0.4f))
    drawCircle(color = Color(0xFF4C9A4E), radius = radius, center = Offset(x, y - height * 0.15f))
}
