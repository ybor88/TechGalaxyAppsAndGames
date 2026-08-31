package com.volcanoescape.app.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * TomTom Routing API - piano gratuito senza carta di credito richiesta
 * (2.500 richieste/giorno, traffico reale incluso).
 * https://developer.tomtom.com/routing-api/documentation/routing/calculate-route
 */
interface TomTomRoutingApi {

    @GET("routing/1/calculateRoute/{locations}/json")
    suspend fun calculateRoute(
        @Path("locations", encoded = true) locations: String,
        @Query("key") apiKey: String,
        @Query("traffic") traffic: Boolean = true,
        @Query("routeType") routeType: String = "fastest",
        @Query("travelMode") travelMode: String = "car",
        @Query("maxAlternatives") maxAlternatives: Int = 2,
    ): TomTomRouteResponse
}

@Serializable
data class TomTomRouteResponse(
    val routes: List<TomTomRoute> = emptyList(),
)

@Serializable
data class TomTomRoute(
    val summary: TomTomRouteSummary,
    val legs: List<TomTomRouteLeg> = emptyList(),
)

@Serializable
data class TomTomRouteSummary(
    val lengthInMeters: Int,
    val travelTimeInSeconds: Int,
    val trafficDelayInSeconds: Int = 0,
)

@Serializable
data class TomTomRouteLeg(
    val points: List<TomTomPoint> = emptyList(),
)

@Serializable
data class TomTomPoint(
    val latitude: Double,
    val longitude: Double,
)
