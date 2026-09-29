package com.scouttable.app.data

import com.scouttable.app.data.importexport.PlayerImportRow
import com.scouttable.app.data.ranking.PlayerScoring
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class PlayerRepository(
    private val dao: PlayerDao,
    private val reviewPrefs: ReviewPrefs,
) {
    fun observePlayers(sport: Sport): Flow<List<Player>> = dao.observeBySport(sport)

    fun observePlayer(sport: Sport, id: String): Flow<Player?> = dao.observeById(sport, id)


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
            // "Visionato" non arriva mai da una ricerca automatica (nessuna fonte può saperlo):
            // va preservato dal giocatore esistente, altrimenti ogni "Genera"/"Aggiorna" lo
            // azzererebbe silenziosamente per chi era già stato segnato come visionato dal vivo.
            val resolvedRow = row.copy(
                // Se Wikipedia/TheSportsDB non hanno trovato uno stemma: prima si tiene quello che
                // il giocatore aveva già (evita che un "Aggiorna" senza risultato per il logo
                // cancelli uno stemma impostato a mano in precedenza), poi si "prende in prestito"
                // quello di un altro giocatore già in lista con lo stesso club — richiesto
                // esplicitamente per non dover più impostare a mano lo stesso stemma più volte.
                logoPath = row.logoPath?.takeIf { it.isNotBlank() }
                    ?: existing?.logoPath?.takeIf { it.isNotBlank() }
                    ?: borrowClubLogo(sport, row.carrieraMigliore),
                secondLogoPath = row.secondLogoPath?.takeIf { it.isNotBlank() }
                    ?: existing?.secondLogoPath?.takeIf { it.isNotBlank() }
                    ?: borrowClubLogo(sport, row.secondLogoClub),
            )
            // La stella non è più un flag puramente manuale: viene ricalcolata da zero ad ogni
            // "Genera"/"Aggiorna lista" in base al rendimento del giocatore (vedi
            // PlayerScoring.computeStar), sovrascrivendo un eventuale tocco manuale precedente
            // dell'utente da "Modifica giocatore" — comportamento richiesto esplicitamente, invece
            // di preservare "existing?.star" come si faceva prima per "visionato" qui sopra.
            val player = resolvedRow.toPlayer(sport, now, id = id, visionato = existing?.visionato ?: false, star = false)
            player.copy(star = PlayerScoring.computeStar(player))
        }
        dao.upsertAll(toWrite)
    }

    private suspend fun borrowClubLogo(sport: Sport, club: String): String? {
        if (club.isBlank()) return null
        return dao.findLogoByCarrieraMigliore(sport, club) ?: dao.findLogoBySecondLogoClub(sport, club)
    }

    /** Modifica manuale di un singolo giocatore (schermata "Mostra lista" / "Revisione"). */
    suspend fun updatePlayer(player: Player) = dao.upsertOne(player.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deletePlayer(id: String) = dao.deleteById(id)

    /** Svuota l'intera lista di uno sport (bottone "Svuota lista" in "Mostra lista"): irreversibile. */
    suspend fun deleteAllPlayers(sport: Sport) = dao.deleteAllBySport(sport)

    /** Segna un giocatore come revisionato adesso (basta aprirlo da "Revisione"): non ricomparirà
     * per [ReviewPrefs.REVIEW_INTERVAL_MILLIS]. */
    suspend fun clearReviewFlag(playerId: String) = dao.clearReview(playerId, System.currentTimeMillis())

    /** Da eseguire ogni giorno (WorkManager) o a mano ("Esegui ora"): segnala i giocatori attivi
     * la cui ultima revisione risale ad almeno 30 giorni fa (vedi [PlayerDao.flagActiveForReview]).
     * Restituisce quanti ne sono stati segnalati adesso. */
    suspend fun flagDueForReview(sport: Sport): Int {
        val flagged = dao.flagActiveForReview(sport, System.currentTimeMillis(), ReviewPrefs.REVIEW_INTERVAL_MILLIS)
        reviewPrefs.markRunNow(sport)
        return flagged
    }

    /** Tutti i giocatori attualmente in attesa di revisione (per la notifica). */
    suspend fun getFlaggedForReview(sport: Sport): List<Player> = dao.getFlaggedForReview(sport)

    private fun PlayerImportRow.toPlayer(sport: Sport, now: Long, id: String, visionato: Boolean, star: Boolean) = Player(
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
        lastReviewedAt = now,
        secondLogoClub = secondLogoClub,
        secondLogoPath = secondLogoPath,
        secondLogoPeriodo = secondLogoPeriodo,
        secondLogoPresenze = secondLogoPresenze,
        secondLogoGol = secondLogoGol,
        giovanili = giovanili,
        secondLogoEff = secondLogoEff,
        effMedio = effMedio,
        minutiCarriera = minutiCarriera,
        minutiNazionale = minutiNazionale,
        college = college,
        visionato = visionato,
        star = star,
    )
}
