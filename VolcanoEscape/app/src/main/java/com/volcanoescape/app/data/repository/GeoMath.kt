package com.volcanoescape.app.data.repository

import com.volcanoescape.app.data.model.GeoPoint
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val EARTH_RADIUS_METERS = 6_371_000.0

private fun Double.toRad() = this * PI / 180.0
private fun Double.toDeg() = this * 180.0 / PI

/** Rotta iniziale (in radianti) da [from] a [to], calcolata con la formula del "great circle". */
internal fun bearingRad(from: GeoPoint, to: GeoPoint): Double {
    val lat1 = from.latitude.toRad()
    val lat2 = to.latitude.toRad()
    val dLon = (to.longitude - from.longitude).toRad()
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return atan2(y, x)
}

/** Punto a distanza [distanceMeters] da [origin] seguendo la rotta [bearingRad]. */
internal fun destinationPoint(origin: GeoPoint, bearingRad: Double, distanceMeters: Double): GeoPoint {
    val angularDistance = distanceMeters / EARTH_RADIUS_METERS
    val lat1 = origin.latitude.toRad()
    val lon1 = origin.longitude.toRad()

    val lat2 = asin(sin(lat1) * cos(angularDistance) + cos(lat1) * sin(angularDistance) * cos(bearingRad))
    val lon2 = lon1 + atan2(
        sin(bearingRad) * sin(angularDistance) * cos(lat1),
        cos(angularDistance) - sin(lat1) * sin(lat2),
    )

    return GeoPoint(lat2.toDeg(), lon2.toDeg())
}

/**
 * Punto sicuro verso cui instradare l'evacuazione: dalla posizione dell'utente,
 * proseguendo lungo la direzione "vulcano -> utente" per un'ulteriore [extraDistanceMeters].
 */
internal fun safeDestinationAwayFromVolcano(
    volcano: GeoPoint,
    userLocation: GeoPoint,
    extraDistanceMeters: Double = 15_000.0,
): GeoPoint {
    val bearingAway = bearingRad(volcano, userLocation)
    return destinationPoint(userLocation, bearingAway, extraDistanceMeters)
}
