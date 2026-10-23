// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.simulation

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Colori tra cui scegliere a mano la casacca di una squadra. */
val JerseyPalette = listOf(
    Color(0xFFD32F2F), Color(0xFFF57C00), Color(0xFFFBC02D), Color(0xFF388E3C), Color(0xFF00897B),
    Color(0xFF1976D2), Color(0xFF283593), Color(0xFF6A1B9A), Color(0xFF000000), Color(0xFFFFFFFF),
)

/**
 * Colore dominante dello stemma (per il colore della casacca): l'immagine viene ridotta a 48px,
 * i colori raggruppati per tonalità ignorando trasparenza e quasi-bianco, e si sceglie il gruppo
 * più frequente tra quelli saturi (altrimenti il più frequente in assoluto). null se lo stemma
 * non si carica.
 */
suspend fun dominantLogoColor(context: Context, url: String?): Color? {
    if (url.isNullOrBlank()) return null
    val request = ImageRequest.Builder(context).data(url).allowHardware(false).size(48).build()
    val result = runCatching { context.imageLoader.execute(request) }.getOrNull() as? SuccessResult ?: return null
    return withContext(Dispatchers.Default) {
        runCatching { dominantColor(result.drawable.toBitmap(48, 48, Bitmap.Config.ARGB_8888)) }.getOrNull()
    }
}

private fun dominantColor(bitmap: Bitmap): Color? {
    val counts = mutableMapOf<Int, Int>()
    val sums = mutableMapOf<Int, LongArray>()
    for (x in 0 until bitmap.width) for (y in 0 until bitmap.height) {
        val c = bitmap.getPixel(x, y)
        if ((c ushr 24) < 160) continue
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        if (r > 225 && g > 225 && b > 225) continue
        val key = (r shr 5 shl 6) or (g shr 5 shl 3) or (b shr 5)
        counts[key] = (counts[key] ?: 0) + 1
        val s = sums.getOrPut(key) { LongArray(3) }
        s[0] += r.toLong(); s[1] += g.toLong(); s[2] += b.toLong()
    }
    if (counts.isEmpty()) return null
    fun average(key: Int): Color {
        val n = counts.getValue(key)
        val s = sums.getValue(key)
        return Color((s[0] / n).toInt(), (s[1] / n).toInt(), (s[2] / n).toInt())
    }
    val saturated = counts.keys.filter { saturation(average(it)) > 0.3f }
    val pick = (saturated.ifEmpty { counts.keys.toList() }).maxBy { counts.getValue(it) }
    return average(pick)
}

private fun saturation(c: Color): Float {
    val mx = max(c.red, max(c.green, c.blue))
    val mn = min(c.red, min(c.green, c.blue))
    return if (mx == 0f) 0f else (mx - mn) / mx
}

fun colorDistance(a: Color, b: Color): Float {
    val dr = a.red - b.red
    val dg = a.green - b.green
    val db = a.blue - b.blue
    return sqrt(dr * dr + dg * dg + db * db)
}

/** Colore del testo leggibile sopra la casacca. */
fun contentColorFor(jersey: Color): Color =
    if (0.299f * jersey.red + 0.587f * jersey.green + 0.114f * jersey.blue > 0.6f) Color.Black else Color.White

/** Se le due casacche si confondono, l'ospite passa al bianco (o al nero se la casa è chiara). */
fun distinctAwayColor(home: Color, away: Color): Color =
    if (colorDistance(home, away) >= 0.35f) away
    else if (contentColorFor(home) == Color.White) Color.White else Color(0xFF111111)
