package com.volcanoescape.app.data.model

data class GeoPoint(val latitude: Double, val longitude: Double)

data class EscapeRoute(
    val points: List<GeoPoint>,
    val travelTimeSeconds: Int,
    val trafficDelaySeconds: Int,
    val lengthMeters: Int,
)

/** Riassume tutte le alternative calcolate; [best] è quella con minor traffico. */
data class EscapeRouteOptions(
    val best: EscapeRoute,
    val alternatives: List<EscapeRoute>,
)
