// Copyright © Roberto Di Flumeri
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

    @Query("SELECT * FROM players WHERE sport = :sport AND needsReview = 1 ORDER BY nome ASC")
    suspend fun getFlaggedForReview(sport: Sport): List<Player>

    @Query("SELECT * FROM players WHERE sport = :sport AND id = :id LIMIT 1")
    suspend fun findById(sport: Sport, id: String): Player?

    @Query("SELECT * FROM players WHERE sport = :sport AND id = :id LIMIT 1")
    fun observeById(sport: Sport, id: String): Flow<Player?>

    @Query("SELECT * FROM players WHERE sport = :sport AND nome = :nome AND nazione = :nazione LIMIT 1")
    suspend fun findByNomeNazione(sport: Sport, nome: String, nazione: String): Player?

    // Usate per "prestare" lo stemma di un club da un altro giocatore già in lista quando la
    // ricerca automatica non lo trova (vedi PlayerRepository.borrowClubLogo): un club può comparire
    // sia come "carrieraMigliore" (logoPath) sia come "secondLogoClub" (secondLogoPath) di un
    // qualsiasi altro giocatore, quindi entrambe le colonne sono fonti valide dello stesso stemma.
    @Query(
        "SELECT logoPath FROM players WHERE sport = :sport AND logoPath IS NOT NULL AND logoPath != '' " +
            "AND LOWER(TRIM(carrieraMigliore)) = LOWER(TRIM(:club)) LIMIT 1"
    )
    suspend fun findLogoByCarrieraMigliore(sport: Sport, club: String): String?

    @Query(
        "SELECT secondLogoPath FROM players WHERE sport = :sport AND secondLogoPath IS NOT NULL AND secondLogoPath != '' " +
            "AND LOWER(TRIM(secondLogoClub)) = LOWER(TRIM(:club)) LIMIT 1"
    )
    suspend fun findLogoBySecondLogoClub(sport: Sport, club: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(players: List<Player>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOne(player: Player)

    @Query("DELETE FROM players WHERE sport = :sport")
    suspend fun deleteAllBySport(sport: Sport)

    @Query("DELETE FROM players WHERE id = :id")
    suspend fun deleteById(id: String)

    // Scadenza individuale: ogni giocatore attivo torna in Revisione :intervalMillis dopo la SUA
    // ultima revisione (il controllo gira ogni giorno). Prima c'era anche un giro globale mensile
    // per sport, che faceva slittare chi era stato revisionato dopo il giro fino a 60 giorni.
    // "needsReview = 0" serve solo a contare i nuovi segnalati (valore restituito).
    @Query(
        "UPDATE players SET needsReview = 1 WHERE sport = :sport AND stato = :statoAttivo " +
            "AND needsReview = 0 AND (lastReviewedAt = 0 OR :now - lastReviewedAt >= :intervalMillis)"
    )
    suspend fun flagActiveForReview(
        sport: Sport,
        now: Long,
        intervalMillis: Long,
        statoAttivo: String = PlayerStatus.ATTIVO,
    ): Int

    @Query("UPDATE players SET needsReview = 0, lastReviewedAt = :now WHERE id = :id")
    suspend fun clearReview(id: String, now: Long)
}
