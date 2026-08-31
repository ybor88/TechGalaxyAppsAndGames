package com.volcanoescape.app.data.repository

import com.volcanoescape.app.data.model.SeismicEvent
import com.volcanoescape.app.data.model.Volcano
import com.volcanoescape.app.data.model.VolcanoRepository
import com.volcanoescape.app.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class SeismicRepository {
    // L'FDSN webservice INGV vuole "YYYY-MM-DDThh:mm:ss" in UTC, senza offset/suffisso "Z".
    private val requestFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    /**
     * Ultime scosse registrate dall'INGV entro [radiusKm] dal vulcano, negli ultimi [days] giorni,
     * al netto delle scosse che l'INGV attribuisce esplicitamente a un vulcano vicino diverso
     * (vedi [SeismicEventParser.filterForVolcano]).
     */
    suspend fun recentEvents(
        volcano: Volcano,
        radiusKm: Double = 30.0,
        days: Long = 30,
    ): List<SeismicEvent> = withContext(Dispatchers.IO) {
        val end = LocalDateTime.now(ZoneOffset.UTC)
        val start = end.minusDays(days)

        val body = NetworkModule.ingvApi.queryEvents(
            startTime = requestFormatter.format(start),
            endTime = requestFormatter.format(end),
            latitude = volcano.latitude,
            longitude = volcano.longitude,
            maxRadiusKm = radiusKm,
        ).string()

        val events = SeismicEventParser.parse(body)
        SeismicEventParser.filterForVolcano(events, volcano, VolcanoRepository.italianVolcanoes)
    }
}
