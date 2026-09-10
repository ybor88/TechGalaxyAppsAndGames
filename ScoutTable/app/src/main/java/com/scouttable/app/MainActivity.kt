package com.scouttable.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.scouttable.app.data.drive.DriveSyncManager
import com.scouttable.app.navigation.AppNavGraph
import com.scouttable.app.ui.theme.ScoutTableTheme
import com.scouttable.app.work.MonthlyReviewWorker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MonthlyReviewWorker.schedule(applicationContext)
        lifecycleScope.launch { DriveSyncManager.syncOnLaunchIfNeeded(applicationContext) }
        setContent {
            ScoutTableTheme {
                AppNavGraph()
            }
        }
    }
}
