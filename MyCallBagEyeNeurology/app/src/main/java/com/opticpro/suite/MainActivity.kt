package com.opticpro.suite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.opticpro.suite.navigation.AppNavGraph
import com.opticpro.suite.ui.theme.OpticProSuiteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpticProSuiteTheme {
                AppNavGraph()
            }
        }
    }
}
