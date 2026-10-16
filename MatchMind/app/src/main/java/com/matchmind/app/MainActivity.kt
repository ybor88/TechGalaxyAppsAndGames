package com.matchmind.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.matchmind.app.ui.AppMatchMind
import com.matchmind.app.ui.MatchMindViewModel
import com.matchmind.app.ui.TemaMatchMind

// Unica activity: tutta l'interfaccia è in Compose. © Roberto Di Flumeri
class MainActivity : ComponentActivity() {
    private val vm: MatchMindViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // barre di sistema sempre scure come il logo, anche se il telefono usa il tema chiaro
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#1A242C")),
        )
        setContent { TemaMatchMind { AppMatchMind(vm) } }
    }
}
