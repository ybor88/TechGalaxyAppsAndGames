package com.scouttable.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.scouttable.app.navigation.AppNavGraph
import com.scouttable.app.ui.theme.ScoutTableTheme
import com.scouttable.app.work.MonthlyReviewWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MonthlyReviewWorker.schedule(applicationContext)
        setContent {
            ScoutTableTheme {
                AppNavGraph()
            }
        }
    }
}
