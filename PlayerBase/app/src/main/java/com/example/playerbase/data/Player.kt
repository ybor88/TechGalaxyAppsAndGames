package com.example.playerbase.data

import java.util.UUID

enum class PlayerRole(val label: String) {
    PLAYMAKER("Playmaker"),
    GUARDIA("Guardia"),
    ALA_PICCOLA("Ala piccola"),
    ALA_GRANDE("Ala grande"),
    CENTRO("Centro"),
    PORTIERE("Portiere"),
    DIFENSORE("Difensore"),
    CENTROCAMPISTA("Centrocampista"),
    ATTACCANTE("Attaccante"),
    NON_SPECIFICATO("Non specificato");

    companion object {
        fun optionsFor(sport: Sport): List<PlayerRole> = when (sport) {
            Sport.BASKET -> listOf(PLAYMAKER, GUARDIA, ALA_PICCOLA, ALA_GRANDE, CENTRO, NON_SPECIFICATO)
            Sport.CALCIO -> listOf(PORTIERE, DIFENSORE, CENTROCAMPISTA, ATTACCANTE, NON_SPECIFICATO)
        }
    }
}

data class Player(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val surname: String = "",
    val gender: Gender = Gender.MASCHIO,
    val sport: Sport = Sport.BASKET,
    val role: PlayerRole = PlayerRole.NON_SPECIFICATO,
    val birthYear: Int? = null,
    val heightCm: Int? = null,
    val maxCareer: String = "",
    val maxTeam: String = "",
    val powerDouble: Int? = null,
    val retired: Boolean = false,
    val scoutingTimestamp: Long = System.currentTimeMillis()
) {
    /** Nome completo "Nome Cognome", per titoli e liste. */
    val fullName: String get() = listOf(name, surname).filter { it.isNotBlank() }.joinToString(" ")

    companion object {
        fun blank(sport: Sport): Player = Player(sport = sport)
    }
}

/** Giorni interi trascorsi dall'ultimo scouting fino ad ora. */
fun Player.daysSinceScouting(nowMillis: Long = System.currentTimeMillis()): Long =
    (nowMillis - scoutingTimestamp) / (1000L * 60 * 60 * 24)

/** Un giocatore è "in scadenza" se non è ritirato e non viene scoutato da più di 30 giorni. */
fun Player.isScoutingExpiring(nowMillis: Long = System.currentTimeMillis()): Boolean =
    !retired && daysSinceScouting(nowMillis) > 30
