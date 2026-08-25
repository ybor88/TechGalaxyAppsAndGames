package com.example.playerbase.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.playerbase.data.PlayerCsvRepository
import com.example.playerbase.data.isScoutingExpiring
import java.util.concurrent.TimeUnit

/**
 * Controllo periodico in background: se ci sono giocatori non ritirati con
 * scouting scaduto da oltre un mese, mostra una notifica sul telefono.
 */
class ScoutingCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val players = PlayerCsvRepository(applicationContext).loadAll()
        val expiring = players.count { it.isScoutingExpiring() }
        NotificationHelper.notifyExpiringScouting(applicationContext, expiring)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "scouting_check_periodic"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScoutingCheckWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
