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

    @Query("UPDATE players SET needsReview = 1 WHERE sport = :sport AND stato = :statoAttivo")
    suspend fun flagActiveForReview(sport: Sport, statoAttivo: String = PlayerStatus.ATTIVO)

    @Query("UPDATE players SET needsReview = 0 WHERE id = :id")
    suspend fun clearReview(id: String)
}
