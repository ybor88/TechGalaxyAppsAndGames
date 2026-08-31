// Copyright (c) Roberto Di Flumeri
package com.volcanoescape.app.data.repository

import com.volcanoescape.app.data.model.GeoPoint
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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

/** Distanza "great circle" in metri tra due punti, formula haversine. */
internal fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
    val lat1 = from.latitude.toRad()
    val lat2 = to.latitude.toRad()
    val dLat = lat2 - lat1
    val dLon = (to.longitude - from.longitude).toRad()
    val sinHalfLat = sin(dLat / 2)
    val sinHalfLon = sin(dLon / 2)
    val a = sinHalfLat * sinHalfLat + cos(lat1) * cos(lat2) * sinHalfLon * sinHalfLon
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
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
 * Punto sicuro verso cui instradare l'evacuazione: un punto lungo la direzione
 * "vulcano -> utente", ad almeno [safetyRadiusMeters] dal cratere (raggio di sicurezza minimo
 * da un'eruzione) e comunque almeno [minimumBufferMeters] oltre la posizione attuale
 * dell'utente, così da garantire un reale allontanamento anche se l'utente è già fuori
 * dal raggio di sicurezza.
 */
internal fun safeDestinationAwayFromVolcano(
    volcano: GeoPoint,
    userLocation: GeoPoint,
    safetyRadiusMeters: Double = 30_000.0,
    minimumBufferMeters: Double = 10_000.0,
): GeoPoint {
    val bearingAway = bearingRad(volcano, userLocation)
    val currentDistanceFromVolcano = distanceMeters(volcano, userLocation)
    val targetDistance = maxOf(safetyRadiusMeters, currentDistanceFromVolcano + minimumBufferMeters)
    return destinationPoint(volcano, bearingAway, targetDistance)
}
