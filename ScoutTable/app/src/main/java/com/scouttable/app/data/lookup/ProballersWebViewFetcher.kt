package com.scouttable.app.data.lookup

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import org.json.JSONTokener

/**
 * Ultima risorsa quando la richiesta HTTP diretta a Proballers viene bloccata (403 con sfida
 * anti-bot Cloudflare, vedi [ProballersCareerStats]): una richiesta OkHttp non esegue JavaScript,
 * quindi non può superare la sfida. Una WebView usa invece il vero motore di rendering del
 * dispositivo (Chromium), esattamente come se l'utente aprisse il link nel browser: se la sfida è
 * "gestita" (nessuna interazione richiesta, il caso comune) viene risolta da sola in pochi secondi.
 * Non supera invece una sfida che richieda un click umano su un checkbox (Turnstile interattivo):
 * in quel caso resta la pagina di verifica e [fetchHtml] ritorna null dopo il timeout.
 */
object ProballersWebViewFetcher {

    private const val CHALLENGE_MARKER = "Just a moment"
    private const val POLL_INTERVAL_MS = 500L
    private const val MAX_WAIT_MS = 12_000L

    // Dimensioni "desktop" forzate sulla WebView: senza, anche con lo User-Agent desktop, il
    // layout risulterebbe comunque quello di un telefono (viewport = pixel reali dello schermo),
    // e Proballers serve una pagina ridotta (niente colonna Eff, niente "secondo logo", sezione
    // Nazionale assente) — verificato confrontando con l'HTML scaricato dalla richiesta diretta
    // (desktop), che invece le contiene tutte.
    private const val DESKTOP_WIDTH_PX = 1920
    private const val DESKTOP_HEIGHT_PX = 1080

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetchHtml(context: Context, url: String): String? = withTimeoutOrNull(MAX_WAIT_MS + 5_000L) {
        withContext(Dispatchers.Main) {
            val webView = WebView(context)
            try {
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.userAgentString = CHROME_USER_AGENT
                webView.settings.useWideViewPort = true
                webView.settings.loadWithOverviewMode = true
                webView.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(DESKTOP_WIDTH_PX, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(DESKTOP_HEIGHT_PX, android.view.View.MeasureSpec.EXACTLY),
                )
                webView.layout(0, 0, DESKTOP_WIDTH_PX, DESKTOP_HEIGHT_PX)

                suspendCancellableCoroutine { cont ->
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, loadedUrl: String) {
                            if (cont.isActive) cont.resume(Unit)
                        }
                    }
                    webView.loadUrl(url)
                }

                var html = readHtml(webView)
                val deadline = System.currentTimeMillis() + MAX_WAIT_MS
                // La sfida Cloudflare si risolve lato client dopo il caricamento iniziale (stessa
                // pagina, senza un secondo onPageFinished "affidabile"): si effettua quindi un
                // polling del DOM finché la pagina di sfida ("Just a moment...") non sparisce.
                while (html.contains(CHALLENGE_MARKER) && System.currentTimeMillis() < deadline) {
                    delay(POLL_INTERVAL_MS)
                    html = readHtml(webView)
                }
                html.takeUnless { it.contains(CHALLENGE_MARKER) || it.isBlank() }
            } finally {
                webView.stopLoading()
                webView.destroy()
            }
        }
    }

    private suspend fun readHtml(webView: WebView): String = suspendCancellableCoroutine { cont ->
        webView.evaluateJavascript("document.documentElement.outerHTML") { rawJsonString ->
            // evaluateJavascript ritorna il valore come stringa JSON (tra virgolette, con gli
            // escape del caso): va decodificata per riottenere l'HTML vero e proprio.
            val decoded = runCatching { JSONTokener(rawJsonString).nextValue() as? String }
                .getOrNull()
                .orEmpty()
            if (cont.isActive) cont.resume(decoded)
        }
    }
}
