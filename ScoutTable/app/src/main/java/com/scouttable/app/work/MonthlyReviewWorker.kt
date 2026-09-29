// Copyright © Roberto Di Flumeri
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
 * Gira ogni giorno e segnala i giocatori attivi la cui ultima revisione risale ad almeno 30 giorni
 * fa: la scadenza è per giocatore, non un giro mensile globale (vedi PlayerRepository.flagDueForReview).
 * Se ne segnala di nuovi, manda una notifica con l'elenco (vedi [ReviewNotifier]).
 */
class MonthlyReviewWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = buildRepository(applicationContext)
        Sport.entries.forEach { sport ->
            // Notifica solo quando entrano giocatori nuovi (non ogni giorno finché la coda non si
            // svuota), ma con l'elenco completo di chi è ancora da revisionare.
            if (repository.flagDueForReview(sport) > 0) {
                ReviewNotifier.notify(applicationContext, sport, repository.getFlaggedForReview(sport))
            }
        }
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
