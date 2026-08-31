package com.volcanoescape.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.volcanoescape.app.ui.navigation.VolcanoEscapeNavHost
import com.volcanoescape.app.ui.theme.VolcanoEscapeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VolcanoEscapeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VolcanoEscapeNavHost()
                }
            }
        }
    }
}
