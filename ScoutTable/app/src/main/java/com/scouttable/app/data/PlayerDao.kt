package com.scouttable.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {

    @Query("SELECT * FROM players WHERE sport = :sport ORDER BY nome ASC")
    fun observeBySport(sport: Sport): Flow<List<Player>>

    @Query("SELECT * FROM players WHERE sport = :sport ORDER BY nome ASC")
    suspend fun getAllBySport(sport: Sport): List<Player>

    @Query("SELECT * FROM players WHERE sport = :sport AND needsReview = 1 ORDER BY nome ASC")
    fun observeFlaggedForReview(sport: Sport): Flow<List<Player>>

    @Query(
        "SELECT carrieraMigliore AS club, COUNT(*) AS count FROM players " +
            "WHERE sport = :sport GROUP BY carrieraMigliore ORDER BY count DESC, club ASC"
    )
    fun observeBestClubs(sport: Sport): Flow<List<ClubCount>>

    @Query("SELECT * FROM players WHERE sport = :sport AND id = :id LIMIT 1")
    suspend fun findById(sport: Sport, id: String): Player?

    @Query("SELECT * FROM players WHERE sport = :sport AND nome = :nome AND nazione = :nazione LIMIT 1")
    suspend fun findByNomeNazione(sport: Sport, nome: String, nazione: String): Player?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(players: List<Player>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOne(player: Player)

    @Query("DELETE FROM players WHERE sport = :sport")
    suspend fun deleteAllBySport(sport: Sport)

    @Query("DELETE FROM players WHERE id = :id")
    suspend fun deleteById(id: String)

    // Esclude chi è già stato revisionato da meno di :intervalMillis: senza questa condizione un
    // giocatore aperto in Revisione un giorno prima del prossimo giro mensile veniva rimesso in
    // coda il giorno dopo, quando il giro globale per sport scattava di nuovo per TUTTI gli attivi
    // senza guardare quando ciascuno era stato revisionato per l'ultima volta.
    @Query(
        "UPDATE players SET needsReview = 1 WHERE sport = :sport AND stato = :statoAttivo " +
            "AND (lastReviewedAt = 0 OR :now - lastReviewedAt >= :intervalMillis)"
    )
    suspend fun flagActiveForReview(
        sport: Sport,
        now: Long,
        intervalMillis: Long,
        statoAttivo: String = PlayerStatus.ATTIVO,
    )

    @Query("UPDATE players SET needsReview = 0, lastReviewedAt = :now WHERE id = :id")
    suspend fun clearReview(id: String, now: Long)
}
