package com.scouttable.app.data.lookup

import android.content.Context

/**
 * Riferimento al Context applicativo, impostato da [com.scouttable.app.ScoutTableApp] all'avvio.
 * Serve solo a [ProballersWebViewFetcher] (l'unico punto del layer di lookup che ha bisogno di un
 * Context, per creare la WebView di fallback quando Proballers blocca la richiesta HTTP diretta):
 * il resto del layer è puro Kotlin/rete e non ne ha bisogno.
 */
object AppContext {
    var contextOrNull: Context? = null
        private set

    fun init(context: Context) {
        contextOrNull = context.applicationContext
    }
}
