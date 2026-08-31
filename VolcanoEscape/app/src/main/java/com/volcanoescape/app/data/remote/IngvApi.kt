package com.volcanoescape.app.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Webservice pubblico e gratuito INGV (FDSN-event, ISIDe), nessuna API key richiesta.
 * Documentazione: https://terremoti.ingv.it/en/fdsnws-event
 * Il formato "text" restituisce righe pipe-separated:
 * EventID|Time|Latitude|Longitude|Depth/Km|Author|Catalog|Contributor|ContributorID|
 * MagType|Magnitude|MagAuthor|EventLocationName|EventType
 */
interface IngvApi {

    @GET("fdsnws/event/1/query")
    suspend fun queryEvents(
        @Query("format") format: String = "text",
        @Query("starttime") startTime: String,
        @Query("endtime") endTime: String,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("maxradiuskm") maxRadiusKm: Double,
        @Query("minmagnitude") minMagnitude: Double = 0.0,
        @Query("orderby") orderBy: String = "time",
    ): ResponseBody
}
