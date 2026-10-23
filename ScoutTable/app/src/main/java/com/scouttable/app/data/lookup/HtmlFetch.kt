// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.lookup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

private const val CHALLENGE_MARKER = "Just a moment"

/**
 * Come Proballers/RealGM/FBref: richiesta diretta con le intestazioni di Chrome e, se Cloudflare
 * la blocca con la sfida "Just a moment", la stessa pagina aperta in una WebView invisibile.
 * Usata dalle fonti aggiunte dopo (playbasket.it, pagine squadra per "Simula partita").
 */
internal suspend fun fetchHtmlWithFallback(url: String): String? {
    // Sempre su un thread di rete, anche se chiamata dall'interfaccia (es. "Simula partita").
    val direct = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).withBrowserHeaders().build()
            lookupHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }
    if (!direct.isNullOrBlank() && !direct.contains(CHALLENGE_MARKER)) return direct
    val context = AppContext.contextOrNull ?: return null
    return ProballersWebViewFetcher.fetchHtml(context, url)
}
