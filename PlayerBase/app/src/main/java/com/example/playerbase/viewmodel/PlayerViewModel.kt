package com.example.playerbase.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.example.playerbase.data.BackupManager
import com.example.playerbase.data.Player
import com.example.playerbase.data.PlayerCsvRepository
import com.example.playerbase.data.Sport
import com.example.playerbase.data.age
import com.example.playerbase.data.isScoutingExpiring
import com.example.playerbase.notification.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlayerCsvRepository(application)
    private val backupManager = BackupManager(application)

    private val _players = MutableStateFlow<List<Player>>(emptyList())
    val players: StateFlow<List<Player>> = _players.asStateFlow()

    private val _draftPlayer = MutableStateFlow(Player.blank(Sport.BASKET))
    val draftPlayer: StateFlow<Player> = _draftPlayer.asStateFlow()

    private val _draftIsNew = MutableStateFlow(true)
    val draftIsNew: StateFlow<Boolean> = _draftIsNew.asStateFlow()

    init {
        _players.value = repository.loadAll()
    }

    fun playersForSport(sport: Sport): List<Player> =
        _players.value.filter { it.sport == sport }
            .sortedWith(compareBy({ it.surname.lowercase() }, { it.name.lowercase() }))

    fun startNewPlayer(sport: Sport) {
        _draftPlayer.value = Player.blank(sport)
        _draftIsNew.value = true
    }

    /** Apre un giocatore esistente: come da funzionalità, la data di scouting si aggiorna alla vista. */
    fun startEditPlayer(playerId: String) {
        refreshScouting(playerId)
        _players.value.find { it.id == playerId }?.let { updated ->
            _draftPlayer.value = updated
            _draftIsNew.value = false
        }
    }

    fun updateDraft(transform: (Player) -> Player) {
        _draftPlayer.value = transform(_draftPlayer.value)
    }

    fun saveDraft() {
        val draft = _draftPlayer.value
        if (draft.name.isBlank() || draft.surname.isBlank()) return
        val existingIndex = _players.value.indexOfFirst { it.id == draft.id }
        _players.value = if (existingIndex >= 0) {
            _players.value.toMutableList().also { it[existingIndex] = draft }
        } else {
            _players.value + draft
        }
        persist()
    }

    fun deletePlayer(playerId: String) {
        _players.value = _players.value.filter { it.id != playerId }
        persist()
    }

    private fun refreshScouting(playerId: String) {
        _players.value = _players.value.map {
            if (it.id == playerId) it.copy(scoutingTimestamp = System.currentTimeMillis()) else it
        }
        persist()
    }

    fun expiringScouting(): List<Player> =
        _players.value.filter { it.isScoutingExpiring() }.sortedBy { it.scoutingTimestamp }

    /** Conta i giocatori per nome squadra (Max Team), ordinati dal più numeroso. */
    fun teamCounts(sport: Sport): List<Pair<String, Int>> =
        playersForSport(sport)
            .map { it.maxTeam.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

    /** Conta i giocatori per Max Career raggiunto, ordinati dal più numeroso. */
    fun maxCareerCounts(sport: Sport): List<Pair<String, Int>> =
        playersForSport(sport)
            .map { it.maxCareer.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

    /** Conta i giocatori per età (da anno di nascita), ordinati dal più giovane al più vecchio. */
    fun ageCounts(sport: Sport): List<Pair<String, Int>> =
        playersForSport(sport)
            .mapNotNull { it.age() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedBy { it.key }
            .map { "${it.key} anni" to it.value }

    private fun persist() {
        repository.saveAll(_players.value)
    }

    /** Esporta l'intera anagrafica (dati + foto giocatori + loghi squadra) in un file .zip scelto dall'utente. */
    fun exportDatabase(targetUri: Uri): Boolean = backupManager.exportTo(targetUri, _players.value)

    /**
     * Importa un backup .zip esportato da PlayerBase (dati + immagini), sostituendo l'anagrafica attuale.
     * Ritorna il numero di giocatori importati, o null se il file non è un backup valido.
     */
    fun importDatabase(sourceUri: Uri): Int? {
        val imported = backupManager.importFrom(sourceUri) ?: return null
        _players.value = imported
        persist()
        return imported.size
    }

    fun checkScoutingNotifications() {
        NotificationHelper.notifyExpiringScouting(getApplication(), expiringScouting().size)
    }
}
