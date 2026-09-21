package com.volcanoescape.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GeoPoint(val latitude: Double, val longitude: Double)

@Serializable
data class EscapeRoute(
    val points: List<GeoPoint>,
    val travelTimeSeconds: Int,
    val trafficDelaySeconds: Int,
    val lengthMeters: Int,
)

/** Origine del percorso mostrato, usata dalla UI per avvisare l'utente quando non è un
 *  instradamento stradale reale e aggiornato. */
@Serializable
enum class RouteSource {
    /** Calcolato ora da TomTom con traffico reale. */
    LIVE,
    /** Rete assente: ultimo percorso TomTom salvato in cache per questo vulcano. */
    CACHED,
    /** Rete assente e nessuna cache disponibile: linea d'aria verso il punto sicuro. */
    OFFLINE_DIRECT,
}

/** Riassume tutte le alternative calcolate; [best] è quella con minor traffico. */
@Serializable
data class EscapeRouteOptions(
    val best: EscapeRoute,
    val alternatives: List<EscapeRoute>,
    val source: RouteSource = RouteSource.LIVE,
    val calculatedAt: Long = System.currentTimeMillis(),
)
