// Copyright (c) Roberto Di Flumeri
package com.volcanoescape.app.data.repository

import com.volcanoescape.app.data.local.RouteCacheStore
import com.volcanoescape.app.data.model.EscapeRoute
import com.volcanoescape.app.data.model.EscapeRouteOptions
import com.volcanoescape.app.data.model.GeoPoint
import com.volcanoescape.app.data.model.RouteSource
import com.volcanoescape.app.data.model.Volcano
import com.volcanoescape.app.data.remote.NetworkModule
import com.volcanoescape.app.data.remote.TomTomRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Velocità media assunta per stimare il tempo di percorrenza del fallback offline (linea
 * d'aria, nessun dato stradale né di traffico): prudenziale, per strade extraurbane/di
 * collina tipiche delle zone vulcaniche.
 */
private const val OFFLINE_FALLBACK_SPEED_KMH = 50.0

class RoutingRepository(
    private val tomTomApiKey: String,
    private val cacheStore: RouteCacheStore,
) {

    /**
     * Calcola la via di fuga verso una destinazione sicura: un punto ad almeno
     * [safetyRadiusMeters] dal cratere del [volcano], lungo la direzione che si allontana da
     * esso a partire da [userLocation]. Prova, in ordine:
     * 1. TomTom con traffico reale (percorso stradale, scegliendo l'alternativa con minor
     *    ritardo da traffico) — se riesce, il risultato viene salvato in cache;
     * 2. se la rete non è disponibile, l'ultimo percorso TomTom calcolato con successo per
     *    questo vulcano, salvato in cache;
     * 3. se non c'è nemmeno una cache, una linea d'aria verso il punto sicuro — non segue le
     *    strade reali, ma garantisce comunque una direzione utile in emergenza.
     */
    suspend fun findLeastCongestedEscapeRoute(
        volcano: Volcano,
        userLocation: GeoPoint,
        safetyRadiusMeters: Double = 30_000.0,
    ): EscapeRouteOptions = withContext(Dispatchers.IO) {
        val volcanoPoint = GeoPoint(volcano.latitude, volcano.longitude)
        val destination = safeDestinationAwayFromVolcano(volcanoPoint, userLocation, safetyRadiusMeters)

        fetchLiveRoute(userLocation, destination)?.let { options ->
            cacheStore.save(volcano.id, options)
            return@withContext options
        }

        cacheStore.load(volcano.id)?.let { cached ->
            return@withContext cached.copy(source = RouteSource.CACHED)
        }

        offlineDirectRoute(userLocation, destination)
    }

    private suspend fun fetchLiveRoute(userLocation: GeoPoint, destination: GeoPoint): EscapeRouteOptions? {
        if (tomTomApiKey.isBlank()) return null

        val locations = String.format(
            Locale.US,
            "%f,%f:%f,%f",
            userLocation.latitude, userLocation.longitude,
            destination.latitude, destination.longitude,
        )

        return runCatching {
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
                source = RouteSource.LIVE,
            )
        }.getOrNull()
    }

    private fun offlineDirectRoute(userLocation: GeoPoint, destination: GeoPoint): EscapeRouteOptions {
        val lengthMeters = distanceMeters(userLocation, destination)
        val estimatedSeconds = (lengthMeters / (OFFLINE_FALLBACK_SPEED_KMH * 1000.0 / 3600.0)).roundToInt()
        val route = EscapeRoute(
            points = listOf(userLocation, destination),
            travelTimeSeconds = estimatedSeconds,
            trafficDelaySeconds = 0,
            lengthMeters = lengthMeters.roundToInt(),
        )
        return EscapeRouteOptions(best = route, alternatives = emptyList(), source = RouteSource.OFFLINE_DIRECT)
    }

    private fun TomTomRoute.toEscapeRoute(): EscapeRoute = EscapeRoute(
        points = legs.flatMap { leg -> leg.points.map { GeoPoint(it.latitude, it.longitude) } },
        travelTimeSeconds = summary.travelTimeInSeconds,
        trafficDelaySeconds = summary.trafficDelayInSeconds,
        lengthMeters = summary.lengthInMeters,
    )
}
