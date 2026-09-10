package com.scouttable.app.data

import com.scouttable.app.data.importexport.PlayerImportRow
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class PlayerRepository(
    private val dao: PlayerDao,
    private val reviewPrefs: ReviewPrefs,
) {
    fun observePlayers(sport: Sport): Flow<List<Player>> = dao.observeBySport(sport)

    fun observeBestClubs(sport: Sport): Flow<List<ClubCount>> = dao.observeBestClubs(sport)

    fun observeFlaggedForReview(sport: Sport): Flow<List<Player>> = dao.observeFlaggedForReview(sport)

    suspend fun exportPlayers(sport: Sport): List<Player> = dao.getAllBySport(sport)

    /** "Genera nuova lista": sostituisce interamente i dati dello sport con quelli importati. */
    suspend fun generateList(sport: Sport, rows: List<PlayerImportRow>) {
        val now = System.currentTimeMillis()
        val players = rows.map { it.toPlayer(sport, now, id = it.id ?: UUID.randomUUID().toString()) }
        dao.deleteAllBySport(sport)
        dao.upsertAll(players)
    }

    /** "Aggiorna nuova lista": update se il giocatore esiste già, insert se nuovo. */
    suspend fun updateList(sport: Sport, rows: List<PlayerImportRow>) {
        val now = System.currentTimeMillis()
        val toWrite = rows.map { row ->
            val existing = row.id?.let { dao.findById(sport, it) }
                ?: dao.findByNomeNazione(sport, row.nome, row.nazione)
            val id = existing?.id ?: row.id ?: UUID.randomUUID().toString()
            row.toPlayer(sport, now, id = id)
        }
        dao.upsertAll(toWrite)
    }

    /** Modifica manuale di un singolo giocatore (schermata "Mostra lista" / "Revisione"). */
    suspend fun updatePlayer(player: Player) = dao.upsertOne(player.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deletePlayer(id: String) = dao.deleteById(id)

    suspend fun clearReviewFlag(playerId: String) = dao.clearReview(playerId)

    /** Da eseguire periodicamente (WorkManager): se sono passati >=30 giorni, marca i giocatori attivi. */
    suspend fun runMonthlyReviewIfDue(sport: Sport, force: Boolean = false): Boolean {
        val last = reviewPrefs.lastRunMillis(sport)
        val due = force || System.currentTimeMillis() - last >= ReviewPrefs.REVIEW_INTERVAL_MILLIS
        if (due) {
            dao.flagActiveForReview(sport)
            reviewPrefs.markRunNow(sport)
        }
        return due
    }

    private fun PlayerImportRow.toPlayer(sport: Sport, now: Long, id: String) = Player(
        id = id,
        sport = sport,
        nome = nome,
        anno = anno,
        carrieraMigliore = carrieraMigliore,
        stato = stato,
        nazione = nazione,
        logoPath = logoPath,
        ruolo = ruolo,
        presenze = presenze,
        punteggio = punteggio,
        assist = assist,
        competizione = competizione,
        proballersUrl = proballersUrl,
        updatedAt = now,
        needsReview = false,
    )
}
