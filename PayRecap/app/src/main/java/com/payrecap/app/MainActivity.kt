// PayRecap — © Roberto Di Flumeri
package com.payrecap.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.payrecap.app.ui.AppPayRecap
import com.payrecap.app.ui.PayRecapViewModel
import com.payrecap.app.ui.Scheda
import com.payrecap.app.ui.TemaPayRecap

class MainActivity : ComponentActivity() {

    private val vm: PayRecapViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) gestisci(intent)
        setContent {
            TemaPayRecap { AppPayRecap(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        gestisci(intent)
    }

    /** PDF aperti o condivisi da altre app (file manager, email, home banking). */
    private fun gestisci(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        }
        if (uri != null) {
            vm.caricaDocumento(uri)
            return
        }
        // Solo build di debug: scorciatoie usate da start.bat per demo e screenshot
        if (BuildConfig.DEBUG) {
            if (intent.getBooleanExtra("esempi", false)) vm.caricaEsempi()
            intent.getStringExtra("scheda")?.let { s -> Scheda.entries.firstOrNull { it.name.equals(s, true) }?.let { vm.vaiA(it) } }
            if (intent.getBooleanExtra("revisione_esempio", false)) vm.apriRevisioneEsempio()
        }
    }
}
