// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.talent

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Un paese del globo: codice ISO a 2 lettere e contorni (lon, lat alternati, in gradi). */
class GlobeCountry(val iso2: String, val rings: List<FloatArray>)

/**
 * Confini dei paesi (Natural Earth 1:110m, pubblico dominio) dall'asset world_countries.txt:
 * una riga per paese "ISO|lon,lat lon,lat ...;anello2 ...". I paesi più piccoli (Malta, Singapore,
 * San Marino...) non ci sono: si scelgono dalla ricerca per nome sotto il globo.
 */
object WorldCountries {
    @Volatile private var loaded: List<GlobeCountry>? = null

    fun load(context: Context): List<GlobeCountry> = loaded ?: synchronized(this) {
        loaded ?: context.assets.open("world_countries.txt").bufferedReader().useLines { lines ->
            lines.filter { '|' in it }.map { line ->
                val iso = line.substringBefore('|')
                val rings = line.substringAfter('|').split(';').map { ring ->
                    val pts = ring.trim().split(' ')
                    FloatArray(pts.size * 2).also { arr ->
                        pts.forEachIndexed { i, p ->
                            arr[i * 2] = p.substringBefore(',').toFloat()
                            arr[i * 2 + 1] = p.substringAfter(',').toFloat()
                        }
                    }
                }
                GlobeCountry(iso, rings)
            }.toList()
        }.also { loaded = it }
    }

    /** Paese che contiene il punto (lon, lat), o null se è mare. */
    fun at(countries: List<GlobeCountry>, lon: Float, lat: Float): GlobeCountry? =
        countries.firstOrNull { c -> c.rings.any { contains(it, lon, lat) } }

    /** Punto nel poligono (ray casting) in coordinate lon/lat. */
    private fun contains(ring: FloatArray, lon: Float, lat: Float): Boolean {
        var inside = false
        val n = ring.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = ring[i * 2]; val yi = ring[i * 2 + 1]
            val xj = ring[j * 2]; val yj = ring[j * 2 + 1]
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    /** Centro approssimato del paese (media dei punti del contorno più grande): per ruotarci il globo. */
    fun center(c: GlobeCountry): Pair<Float, Float> {
        val ring = c.rings.maxBy { it.size }
        var lon = 0f; var lat = 0f
        for (i in ring.indices step 2) { lon += ring[i]; lat += ring[i + 1] }
        val n = ring.size / 2
        return lon / n to lat / n
    }
}

private const val DEG = (Math.PI / 180).toFloat()

/**
 * Globo terrestre 3D (proiezione ortografica, come una sfera vista da lontano): si ruota trascinando,
 * si ingrandisce con due dita, un tocco su un paese lo seleziona ([onSelect] con il codice ISO).
 * [focus] = paese da portare al centro (es. scelto dalla ricerca per nome).
 */
@Composable
fun WorldGlobe(
    countries: List<GlobeCountry>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    focus: Pair<Float, Float>? = null,
) {
    // Centro della vista (longitudine, latitudine) e zoom; all'inizio l'Europa.
    var lon0 by rememberSaveable { mutableFloatStateOf(12f) }
    var lat0 by rememberSaveable { mutableFloatStateOf(35f) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    LaunchedEffect(focus) {
        focus?.let { (lon, lat) -> lon0 = lon; lat0 = lat.coerceIn(-80f, 80f) }
    }

    val ocean = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
    val land = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
    val border = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    val highlight = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val rim = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)

    Canvas(
        modifier = modifier
            .pointerInput(countries) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    val radius = min(size.width, size.height) / 2f * zoom
                    // Trascinare di un raggio = ruotare di ~57°: il punto sotto il dito segue il dito.
                    lon0 -= pan.x / radius / DEG
                    lat0 = (lat0 + pan.y / radius / DEG).coerceIn(-85f, 85f)
                    zoom = (zoom * gestureZoom).coerceIn(0.8f, 6f)
                }
            }
            .pointerInput(countries) {
                detectTapGestures { tap ->
                    val radius = min(size.width, size.height) / 2f * zoom * 0.95f
                    val x = (tap.x - size.width / 2f) / radius
                    val y = -(tap.y - size.height / 2f) / radius
                    unproject(x, y, lon0, lat0)?.let { (lon, lat) ->
                        WorldCountries.at(countries, lon, lat)?.let { onSelect(it.iso2) }
                    }
                }
            },
    ) {
        val radius = min(size.width, size.height) / 2f * zoom * 0.95f
        val c = Offset(size.width / 2f, size.height / 2f)
        // Mare con luce da in alto a sinistra: dà il volume della sfera.
        drawCircle(
            brush = Brush.radialGradient(
                listOf(ocean.copy(alpha = 0.10f), ocean, ocean.copy(alpha = 0.45f)),
                center = c + Offset(-radius * 0.35f, -radius * 0.35f),
                radius = radius * 1.4f,
            ),
            radius = radius,
            center = c,
        )
        drawGraticule(c, radius, lon0, lat0, grid)
        for (country in countries) {
            val isSelected = country.iso2 == selected
            for (ring in country.rings) {
                val path = ringPath(ring, c, radius, lon0, lat0) ?: continue
                drawPath(path, if (isSelected) highlight else land)
                drawPath(path, if (isSelected) highlight else border, style = Stroke(width = if (isSelected) 2.5f else 0.8f))
            }
        }
        // Ombra sul bordo opposto alla luce e contorno della sfera.
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.28f)),
                center = c + Offset(-radius * 0.25f, -radius * 0.25f),
                radius = radius * 1.25f,
            ),
            radius = radius,
            center = c,
        )
        drawCircle(rim, radius = radius, center = c, style = Stroke(width = 2f))
    }
}

/** Proiezione ortografica: (lon, lat) -> (x, y, visibile) sulla sfera di raggio 1 centrata in (lon0, lat0). */
private fun project(lon: Float, lat: Float, lon0: Float, lat0: Float, out: FloatArray) {
    val phi = lat * DEG; val lam = (lon - lon0) * DEG; val phi0 = lat0 * DEG
    val cosPhi = cos(phi)
    out[0] = cosPhi * sin(lam)
    out[1] = cos(phi0) * sin(phi) - sin(phi0) * cosPhi * cos(lam)
    out[2] = sin(phi0) * sin(phi) + cos(phi0) * cosPhi * cos(lam) // >= 0: faccia visibile
}

/** Inversa della proiezione: punto sullo schermo (raggio 1) -> (lon, lat), null fuori dalla sfera. */
private fun unproject(x: Float, y: Float, lon0: Float, lat0: Float): Pair<Float, Float>? {
    val rho = sqrt(x * x + y * y)
    if (rho > 1f) return null
    if (rho < 1e-6f) return lon0 to lat0
    val cc = asin(rho)
    val phi0 = lat0 * DEG
    val lat = asin(cos(cc) * sin(phi0) + y * sin(cc) * cos(phi0) / rho)
    val lon = lon0 * DEG + atan2(x * sin(cc), rho * cos(cc) * cos(phi0) - y * sin(cc) * sin(phi0))
    var lonDeg = lon / DEG
    while (lonDeg > 180f) lonDeg -= 360f
    while (lonDeg < -180f) lonDeg += 360f
    return lonDeg to lat / DEG
}

/**
 * Contorno di un paese sullo schermo: i punti sulla faccia nascosta vengono schiacciati sul bordo
 * della sfera, così un paese a cavallo dell'orizzonte resta chiuso e si riempie correttamente.
 * null se è tutto dietro.
 */
private fun ringPath(ring: FloatArray, c: Offset, radius: Float, lon0: Float, lat0: Float): Path? {
    val p = FloatArray(3)
    val path = Path()
    var anyVisible = false
    for (i in ring.indices step 2) {
        project(ring[i], ring[i + 1], lon0, lat0, p)
        var x = p[0]; var y = p[1]
        if (p[2] >= 0f) {
            anyVisible = true
        } else {
            val len = sqrt(x * x + y * y).coerceAtLeast(1e-6f)
            x /= len; y /= len
        }
        val sx = c.x + x * radius; val sy = c.y - y * radius
        if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
    }
    if (!anyVisible) return null
    path.close()
    return path
}

/** Meridiani e paralleli ogni 30°, solo sulla faccia visibile. */
private fun DrawScope.drawGraticule(c: Offset, radius: Float, lon0: Float, lat0: Float, color: Color) {
    val p = FloatArray(3)
    fun line(points: List<Pair<Float, Float>>) {
        var prev: Offset? = null
        for ((lon, lat) in points) {
            project(lon, lat, lon0, lat0, p)
            val cur = if (p[2] >= 0f) Offset(c.x + p[0] * radius, c.y - p[1] * radius) else null
            if (prev != null && cur != null) drawLine(color, prev, cur, strokeWidth = 1f)
            prev = cur
        }
    }
    for (lon in -180 until 180 step 30) line((-90..90 step 5).map { lon.toFloat() to it.toFloat() })
    for (lat in -60..60 step 30) line((-180..180 step 5).map { it.toFloat() to lat.toFloat() })
}
