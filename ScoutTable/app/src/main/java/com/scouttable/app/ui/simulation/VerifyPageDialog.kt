// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.simulation

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scouttable.app.data.lookup.CHROME_USER_AGENT
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONTokener

/**
 * Pagina aperta in una WebView visibile quando il sito (es. FBref dietro Cloudflare) chiede una
 * verifica anti-bot che la WebView invisibile non può superare da sola: l'utente completa la
 * verifica, e appena la pagina vera è caricata (niente più "Just a moment") l'HTML viene passato a
 * [onHtml] e la finestra si chiude. Il cookie ottenuto resta nella WebView dell'app, quindi le
 * richieste successive allo stesso sito passano senza chiedere di nuovo.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VerifyPageDialog(url: String, onHtml: (String) -> Unit, onDismiss: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    LaunchedEffect(webView) {
        val view = webView ?: return@LaunchedEffect
        while (true) {
            delay(1_000)
            val html = suspendCancellableCoroutine { cont ->
                view.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                    if (cont.isActive) cont.resume(runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull().orEmpty())
                }
            }
            if (html.length > 5_000 && !html.contains("Just a moment") && html.contains("<table")) {
                onHtml(html)
                return@LaunchedEffect
            }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(modifier = Modifier.fillMaxSize().padding(12.dp), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    "Il sito chiede una verifica anti-bot: completala qui sotto (di solito basta toccare la casella). " +
                        "Appena la pagina si apre, i dati vengono letti in automatico.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = CHROME_USER_AGENT
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = WebViewClient()
                            loadUrl(url)
                            webView = this
                        }
                    },
                    onRelease = { it.destroy() },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                TextButton(onClick = onDismiss, modifier = Modifier.padding(4.dp)) { Text("Annulla") }
            }
        }
    }
}
