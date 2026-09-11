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

    /**
     * "Genera nuova lista" e "Aggiorna nuova lista": entrambe aggiungono in coda alla tabella
     * esistente. Se un giocatore è già presente (stesso id, oppure stesso nome+nazione) viene
     * aggiornato con le informazioni nuove invece di creare un duplicato; se non c'è ancora,
     * viene inserito. Non si cancella mai la lista precedente.
     */
    suspend fun generateList(sport: Sport, rows: List<PlayerImportRow>) = updateList(sport, rows)

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

    /** Svuota l'intera lista di uno sport (bottone "Svuota lista" in "Mostra lista"): irreversibile. */
    suspend fun deleteAllPlayers(sport: Sport) = dao.deleteAllBySport(sport)

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
        golSubiti = golSubiti,
        presenzeNazionale = presenzeNazionale,
        punteggioNazionale = punteggioNazionale,
        assistNazionale = assistNazionale,
        rimbalzi = rimbalzi,
        palleRecuperate = palleRecuperate,
        tiriDaDue = tiriDaDue,
        tiriDaTre = tiriDaTre,
        rimbalziNazionale = rimbalziNazionale,
        palleRecuperateNazionale = palleRecuperateNazionale,
        tackle = tackle,
        golEvitati = golEvitati,
        golSubitiNazionale = golSubitiNazionale,
        percentualeTiriDaDue = percentualeTiriDaDue,
        percentualeTiriDaTre = percentualeTiriDaTre,
        proballersUrl = proballersUrl,
        updatedAt = now,
        needsReview = false,
    )
}
