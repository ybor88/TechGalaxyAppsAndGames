// Copyright (c) Roberto Di Flumeri
package com.soniqai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.soniqai.app.navigation.AppNavGraph
import com.soniqai.app.ui.theme.SoniqAiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SoniqAiTheme {
                AppNavGraph()
            }
        }
    }
}
