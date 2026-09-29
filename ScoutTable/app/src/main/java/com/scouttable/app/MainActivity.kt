// Copyright © Roberto Di Flumeri
package com.scouttable.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.scouttable.app.navigation.AppNavGraph
import com.scouttable.app.ui.theme.ScoutTableTheme
import com.scouttable.app.work.MonthlyReviewWorker

class MainActivity : ComponentActivity() {
    // Esito ignorato: se negato, i giocatori restano comunque visibili nella scheda "Revisione".
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MonthlyReviewWorker.schedule(applicationContext)
        // Android 13+: senza questo permesso la notifica "giocatori da revisionare" non compare.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            ScoutTableTheme {
                AppNavGraph()
            }
        }
    }
}
