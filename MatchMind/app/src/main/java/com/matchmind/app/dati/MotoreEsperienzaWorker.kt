package com.matchmind.app.dati

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.matchmind.app.MatchMindApp
import java.util.concurrent.TimeUnit

/**
 * Motore esperienza in background: due volte al giorno, anche ad app chiusa e anche se non giochi, chiude le
 * partite già giocate e analizza tutte quelle dei prossimi giorni (vedi [Repository.motoreEsperienza]).
 * Parte solo con la rete disponibile; Android sceglie il momento esatto per risparmiare batteria.
 *
 * © Roberto Di Flumeri
 */
class MotoreEsperienzaWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as MatchMindApp).repository
        return try {
            val r = repo.motoreEsperienza()
            if (r.analizzate == 0 && r.erroriRete > 0 && runAttemptCount < 3) Result.retry() else Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val NOME = "motore_esperienza"
        const val ORE_TRA_GIRI = 12L

        /** Programma il giro periodico; se è già programmato non lo tocca. */
        fun programma(context: Context) {
            val richiesta = PeriodicWorkRequestBuilder<MotoreEsperienzaWorker>(ORE_TRA_GIRI, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NOME, ExistingPeriodicWorkPolicy.KEEP, richiesta)
        }
    }
}
