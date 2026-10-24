// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.lookup

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import org.json.JSONTokener

/**
 * Ultima risorsa quando la richiesta HTTP diretta viene bloccata da Cloudflare (Proballers,
 * RealGM, FBref, playbasket): una richiesta OkHttp non esegue JavaScript, quindi non può superare
 * la sfida. Una WebView usa il vero motore del dispositivo (Chromium), come se l'utente aprisse
 * il link nel browser.
 *
 * Due fasi:
 * 1. automatica: la WebView, invisibile ma agganciata alla finestra dell'app (staccata, Chromium
 *    la considera nascosta e sospende i timer con cui Cloudflare completa la verifica), aspetta
 *    che la sfida si risolva da sola;
 * 2. verifica a mano: se dopo [AUTO_WAIT_MS] la pagina è ancora quella di Cloudflare (il riquadro
 *    Turnstile resta su "Verifying..." o chiede di toccare la casella, verificato su FBref), la
 *    pagina viene mostrata a schermo con un messaggio, così l'utente può completare la verifica.
 *
 * Superata una volta, Cloudflare lascia un cookie (cf_clearance) che resta nella WebView: i
 * giocatori successivi dello stesso sito passano senza chiedere più nulla, e [HtmlFetch] lo
 * riusa anche per le richieste dirette. Le pagine sono lette una alla volta ([lock]), così non
 * compaiono mai due verifiche insieme.
 */
object ProballersWebViewFetcher {

    private const val POLL_INTERVAL_MS = 500L
    /** Attesa della risoluzione automatica, prima di chiedere la verifica a mano. */
    private const val AUTO_WAIT_MS = 10_000L
    /** Tempo lasciato all'utente per completare la verifica a schermo. */
    private const val MANUAL_WAIT_MS = 120_000L

    // Dimensioni "desktop" forzate sulla WebView: senza, anche con lo User-Agent desktop, il
    // layout risulterebbe comunque quello di un telefono (viewport = pixel reali dello schermo),
    // e Proballers serve una pagina ridotta (niente colonna Eff, niente "secondo logo", sezione
    // Nazionale assente) — verificato confrontando con l'HTML scaricato dalla richiesta diretta
    // (desktop), che invece le contiene tutte.
    private const val DESKTOP_WIDTH_PX = 1920
    private const val DESKTOP_HEIGHT_PX = 1080

    private val lock = Mutex()

    /** Firma del motore WebView del dispositivo, senza "; wv" né "Mobile" (Cloudflare lega il suo
     *  cookie a questa firma). */
    internal fun webViewUserAgent(context: Context): String =
        runCatching { android.webkit.WebSettings.getDefaultUserAgent(context) }.getOrNull()
            ?.replace("; wv", "")?.replace(Regex("""Version/\d+(\.\d+)* """), "")
            // Senza "Mobile" i siti la trattano come un tablet e servono la pagina completa (a
            // Proballers serve per l'Eff e il secondo logo), restando coerente con il motore.
            ?.replace(" Mobile Safari", " Safari")
            ?: CHROME_USER_AGENT

    /** Vero se [html] è ancora la pagina di verifica di Cloudflare e non quella richiesta. */
    internal fun isChallenge(html: String): Boolean =
        html.contains("Just a moment") || html.contains("challenge-platform") && html.contains("cf-turnstile") ||
            html.contains("Attention Required! | Cloudflare")

    suspend fun fetchHtml(context: Context, url: String): String? = lock.withLock {
        withTimeoutOrNull(AUTO_WAIT_MS + MANUAL_WAIT_MS + 20_000L) { load(context, url) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun load(context: Context, url: String): String? = withContext(Dispatchers.Main) {
        val activity = AppContext.activityOrNull
        val webView = WebView(activity ?: context)
        var overlay: View? = null
        try {
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            // Firma reale del motore della WebView (senza il marcatore "; wv"): una firma di Chrome
            // per Windows su un motore Android è incoerente con quello che il motore dichiara di sé
            // a Cloudflare, che rifiuta la verifica. La stessa firma va usata per riusare il cookie
            // di Cloudflare nelle richieste dirette (vedi [webViewUserAgent] e HtmlFetch).
            webView.settings.userAgentString = webViewUserAgent(context)
            webView.settings.useWideViewPort = true
            webView.settings.loadWithOverviewMode = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

            val host = activity?.window?.decorView as? ViewGroup
            if (host != null) {
                // Invisibile ma agganciata alla finestra: vedi la fase 1 nella documentazione.
                webView.alpha = 0f
                host.addView(webView, FrameLayout.LayoutParams(DESKTOP_WIDTH_PX, DESKTOP_HEIGHT_PX))
            } else {
                webView.measure(
                    View.MeasureSpec.makeMeasureSpec(DESKTOP_WIDTH_PX, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(DESKTOP_HEIGHT_PX, View.MeasureSpec.EXACTLY),
                )
                webView.layout(0, 0, DESKTOP_WIDTH_PX, DESKTOP_HEIGHT_PX)
            }

            loadAndWaitFinished(webView, url)

            // Fase 1: la sfida si risolve lato client dopo il caricamento iniziale (stessa pagina,
            // senza un secondo onPageFinished affidabile): polling del DOM.
            var html = pollUntilLoaded(webView, AUTO_WAIT_MS)

            // Fase 2: verifica a mano, solo se c'è un'activity su cui mostrarla.
            if (isChallenge(html) && host != null && activity != null) {
                var cancelled = false
                // Prima staccata dalla finestra: una vista non può avere due contenitori.
                host.removeView(webView)
                webView.alpha = 1f
                overlay = manualVerificationOverlay(activity, webView, url) { cancelled = true }
                host.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                html = pollUntilLoaded(webView, MANUAL_WAIT_MS) { cancelled }
                if (!isChallenge(html)) {
                    // Verifica superata: la pagina va riletta a dimensione "desktop" (a schermo era
                    // larga quanto il telefono, e Proballers servirebbe la versione ridotta). Il
                    // cookie di Cloudflare ora c'è, quindi il nuovo caricamento passa subito.
                    CookieManager.getInstance().flush()
                    (overlay.parent as? ViewGroup)?.removeView(overlay)
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    overlay = null
                    webView.alpha = 0f
                    host.addView(webView, FrameLayout.LayoutParams(DESKTOP_WIDTH_PX, DESKTOP_HEIGHT_PX))
                    loadAndWaitFinished(webView, url)
                    html = pollUntilLoaded(webView, AUTO_WAIT_MS)
                }
            }

            if (!isChallenge(html)) CookieManager.getInstance().flush()
            html.takeUnless { isChallenge(it) || it.isBlank() }
        } finally {
            overlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }
    }

    private suspend fun loadAndWaitFinished(webView: WebView, url: String) {
        // Senza limite, una pagina che non termina mai il caricamento bloccherebbe tutto.
        withTimeoutOrNull(30_000L) {
            suspendCancellableCoroutine { cont ->
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, loadedUrl: String) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
                webView.loadUrl(url)
            }
        }
    }

    private suspend fun pollUntilLoaded(webView: WebView, timeoutMs: Long, stop: () -> Boolean = { false }): String {
        var html = readHtml(webView)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (isChallenge(html) && System.currentTimeMillis() < deadline && !stop()) {
            delay(POLL_INTERVAL_MS)
            html = readHtml(webView)
        }
        return html
    }

    /** Schermata della verifica a mano: messaggio in alto, la pagina di Cloudflare, "Annulla". */
    private fun manualVerificationOverlay(activity: Activity, webView: WebView, url: String, onCancel: () -> Unit): View {
        val site = runCatching { android.net.Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: "il sito"
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            isClickable = true
            addView(TextView(activity).apply {
                text = "Verifica di sicurezza di $site\nSe compare la casella \"Verifica di essere umano\", toccala: " +
                    "serve una volta sola, poi la ricerca continua da sola."
                setTextColor(Color.BLACK)
                textSize = 15f
                setPadding(dp(16), dp(40), dp(16), dp(8))
            })
            addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(Button(activity).apply {
                text = "Annulla"
                setOnClickListener { onCancel() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                // Sopra la barra di navigazione del telefono.
                setMargins(dp(16), dp(8), dp(16), dp(56))
            })
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
