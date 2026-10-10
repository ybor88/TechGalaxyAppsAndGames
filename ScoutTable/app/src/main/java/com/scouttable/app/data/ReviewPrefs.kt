// Copyright © Roberto Di Flumeri
package com.scouttable.app.data

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.reviewDataStore by preferencesDataStore(name = "scouttable_review_prefs")

/** Traccia quando è stato eseguito l'ultimo controllo delle revisioni, per sport. */
class ReviewPrefs(private val context: Context) {

    private fun keyFor(sport: Sport) = longPreferencesKey("last_review_${sport.name}")

    suspend fun lastRunMillis(sport: Sport): Long =
        context.reviewDataStore.data.first()[keyFor(sport)] ?: 0L

    suspend fun markRunNow(sport: Sport) {
        context.reviewDataStore.edit { it[keyFor(sport)] = System.currentTimeMillis() }
    }

    companion object {
        const val REVIEW_INTERVAL_MILLIS = 30L * 24 * 60 * 60 * 1000 // 30 giorni

        /** Giocatori al massimo in "Revisione" contemporaneamente, per sport: gli altri già
         *  scaduti aspettano in coda ed entrano man mano che questi vengono revisionati. */
        const val REVIEW_BATCH_SIZE = 10

        /**
         * Da quando [player] può essere segnalato per revisione (vedi [PlayerDao.flagActiveForReview]):
         * 30 giorni dopo la sua ultima revisione, subito se non è mai stato revisionato. Con la coda
         * a blocchi ([REVIEW_BATCH_SIZE]) può entrare in Revisione anche qualche giorno dopo.
         * Null se non è "Attivo"/"Svincolato" (non verrà mai segnalato) o se è già in attesa di revisione.
         */
        fun nextReviewAt(player: Player, now: Long = System.currentTimeMillis()): Long? {
            if (player.stato != PlayerStatus.ATTIVO && player.stato != PlayerStatus.SVINCOLATO) return null
            if (player.needsReview) return null
            return if (player.lastReviewedAt == 0L) now else player.lastReviewedAt + REVIEW_INTERVAL_MILLIS
        }
    }
}
