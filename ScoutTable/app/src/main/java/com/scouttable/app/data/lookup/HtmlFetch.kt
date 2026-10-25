// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.lookup

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Pagina di un sito protetto da Cloudflare (Proballers, RealGM, FBref, playbasket, pagine squadra
 * per "Simula partita"): richiesta diretta con le intestazioni di Chrome e, se Cloudflare la
 * blocca con la sfida "Just a moment", la stessa pagina aperta nella WebView di
 * [ProballersWebViewFetcher] (con verifica a mano se serve).
 *
 * La richiesta diretta porta anche i cookie della WebView per quel sito: dopo una verifica
 * superata, il cookie di Cloudflare (cf_clearance, legato allo stesso User-Agent usato da
 * entrambe) fa passare anche le richieste dirette, molto più veloci della WebView.
 */
internal suspend fun fetchHtmlWithFallback(url: String, allowWebView: Boolean = true): String? {
    // Sempre su un thread di rete, anche se chiamata dall'interfaccia (es. "Simula partita").
    val direct = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder().url(url).withBrowserHeaders()
            webViewCookies(url)?.let { cookies ->
                // Il cookie di Cloudflare vale solo con la stessa firma con cui è stato ottenuto.
                builder.header("Cookie", cookies)
                AppContext.contextOrNull?.let { builder.header("User-Agent", ProballersWebViewFetcher.webViewUserAgent(it)) }
            }
            lookupHttpClient.newCall(builder.build()).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }
    if (!direct.isNullOrBlank() && !ProballersWebViewFetcher.isChallenge(direct)) return direct
    if (!allowWebView) return null
    val context = AppContext.contextOrNull ?: return null
    return ProballersWebViewFetcher.fetchHtml(context, url)
}

/** Cookie salvati dalla WebView per [url] (null se nessuno o WebView non disponibile). */
private fun webViewCookies(url: String): String? =
    runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()?.ifBlank { null }
