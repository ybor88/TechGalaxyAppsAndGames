package com.scouttable.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.scouttable.app.data.Sport
import com.scouttable.app.data.buildRepository
import java.util.concurrent.TimeUnit

/**
 * WorkManager non supporta un intervallo "mensile" diretto (minimo pratico ~1 giorno):
 * gira ogni giorno ma non fa nulla finché non sono passati 30 giorni dall'ultima esecuzione
 * per sport (vedi ReviewPrefs / PlayerRepository.runMonthlyReviewIfDue).
 */
class MonthlyReviewWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = buildRepository(applicationContext)
        Sport.entries.forEach { sport -> repository.runMonthlyReviewIfDue(sport) }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "monthly_review_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonthlyReviewWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
