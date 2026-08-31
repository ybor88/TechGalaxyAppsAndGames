package com.volcanoescape.app.data.model

import java.time.OffsetDateTime

data class SeismicEvent(
    val eventId: String,
    val time: OffsetDateTime,
    val latitude: Double,
    val longitude: Double,
    val depthKm: Double,
    val magnitude: Double,
    val magnitudeType: String,
    val locationName: String,
)
