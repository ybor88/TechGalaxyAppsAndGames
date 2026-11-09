package com.matchmind.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.matchmind.app.ui.AppMatchMind
import com.matchmind.app.ui.MatchMindViewModel
import com.matchmind.app.ui.TemaMatchMind

// Unica activity: tutta l'interfaccia è in Compose. © Roberto Di Flumeri
class MainActivity : ComponentActivity() {
    private val vm: MatchMindViewModel by viewModels()
    private val richiestaNotifiche = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // barre di sistema sempre scure come il logo, anche se il telefono usa il tema chiaro
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#1A242C")),
        )
        setContent { TemaMatchMind { AppMatchMind(vm) } }
        // Android 13+: senza questo permesso gli avvisi "Fai cashout!" non compaiono
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            richiestaNotifiche.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
