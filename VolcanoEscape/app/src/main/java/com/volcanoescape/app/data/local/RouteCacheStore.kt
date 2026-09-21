// Copyright (c) Roberto Di Flumeri
package com.volcanoescape.app.data.local

import android.content.Context
import com.volcanoescape.app.data.model.EscapeRouteOptions
import kotlinx.serialization.json.Json

private const val PREFS_NAME = "route_cache"

/**
 * Salva in locale l'ultimo percorso di fuga calcolato con successo per ciascun vulcano, così
 * da poterlo riproporre (marcato come non aggiornato) se una successiva richiesta fallisce per
 * mancanza di rete durante un'emergenza.
 */
class RouteCacheStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun save(volcanoId: String, options: EscapeRouteOptions) {
        prefs.edit()
            .putString(volcanoId, json.encodeToString(EscapeRouteOptions.serializer(), options))
            .apply()
    }

    fun load(volcanoId: String): EscapeRouteOptions? =
        prefs.getString(volcanoId, null)?.let { stored ->
            runCatching { json.decodeFromString(EscapeRouteOptions.serializer(), stored) }.getOrNull()
        }
}
