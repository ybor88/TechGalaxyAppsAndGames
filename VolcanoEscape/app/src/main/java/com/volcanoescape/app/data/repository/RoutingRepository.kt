package com.volcanoescape.app.data.repository

import com.volcanoescape.app.data.model.EscapeRoute
import com.volcanoescape.app.data.model.EscapeRouteOptions
import com.volcanoescape.app.data.model.GeoPoint
import com.volcanoescape.app.data.model.Volcano
import com.volcanoescape.app.data.remote.NetworkModule
import com.volcanoescape.app.data.remote.TomTomRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class RoutingRepository(private val tomTomApiKey: String) {

    /**
     * Calcola la via di fuga meno trafficata dal punto [userLocation] verso una destinazione
     * sicura, individuata proseguendo lontano dal [volcano] per [safeDistanceMeters].
     * Tra le rotte alternative restituite da TomTom (con traffico reale), sceglie quella
     * con minor ritardo da traffico.
     */
    suspend fun findLeastCongestedEscapeRoute(
        volcano: Volcano,
        userLocation: GeoPoint,
        safeDistanceMeters: Double = 15_000.0,
    ): EscapeRouteOptions = withContext(Dispatchers.IO) {
        check(tomTomApiKey.isNotBlank()) {
            "TOMTOM_API_KEY mancante: impostala in local.properties (vedi local.properties.example)"
        }

        val volcanoPoint = GeoPoint(volcano.latitude, volcano.longitude)
        val destination = safeDestinationAwayFromVolcano(volcanoPoint, userLocation, safeDistanceMeters)

        val locations = String.format(
            Locale.US,
            "%f,%f:%f,%f",
            userLocation.latitude, userLocation.longitude,
            destination.latitude, destination.longitude,
        )

        val response = NetworkModule.tomTomRoutingApi.calculateRoute(
            locations = locations,
            apiKey = tomTomApiKey,
        )

        val routes = response.routes.map { it.toEscapeRoute() }
        require(routes.isNotEmpty()) { "TomTom non ha restituito alcun percorso" }

        val best = routes.minByOrNull { it.trafficDelaySeconds } ?: routes.first()
        EscapeRouteOptions(
            best = best,
            alternatives = routes.filterNot { it === best },
        )
    }

    private fun TomTomRoute.toEscapeRoute(): EscapeRoute = EscapeRoute(
        points = legs.flatMap { leg -> leg.points.map { GeoPoint(it.latitude, it.longitude) } },
        travelTimeSeconds = summary.travelTimeInSeconds,
        trafficDelaySeconds = summary.trafficDelayInSeconds,
        lengthMeters = summary.lengthInMeters,
    )
}
