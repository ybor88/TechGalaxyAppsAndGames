// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.simulation

import com.scouttable.app.data.nbafiba.SimMvp

/**
 * Le 20 caselle di TOP Galaxy Champions, nell'ordine di testa di serie (la prima è la n° 1).
 * Diciannove leghe indicate dall'utente più una "Wild Card" libera per arrivare a 20.
 */
val GalaxyLeagues: List<String> = listOf(
    "NBA", "EuroLeague", "EuroCup", "BCL", "FIBA Europe Cup", "Super League", "G League", "NCAA", "NBA Cup",
    "WNBA", "EuroLeague Women", "VTB", "ABA", "BCL Americas", "Arab Cup Champions", "Asia Champions", "BAL",
    "CBA", "NCAA femminile", "Wild Card",
)

/** Le 7 caselle di NBA G League Champions. Con 7 squadre la n° 1 va direttamente in semifinale e le
 *  n° 2-7 giocano il Play-in (2-7, 3-6, 4-5); poi semifinali e finale (vedi [GalaxyTournament]). */
val GLeagueLeagues: List<String> = listOf("NBA Cup", "NBA", "G League", "NCAA", "WNBA", "NBA Summer League", "NCAA Women")

/** Le 3 caselle di NCAA High School & Next Generation adidas (Play-in tra 2ª e 3ª, poi finale con la 1ª). */
val NcaaHsNextGenLeagues: List<String> = listOf("NCAA", "High School", "Next Generation adidas")

/** Una squadra iscritta al torneo: testa di serie (1 = la più alta) e lega della casella. */
data class GalaxyEntry(val seed: Int, val league: String, val team: SimTeam)

/** Una partita del tabellone: casa = testa di serie più alta. */
data class GalaxyGame(val home: GalaxyEntry, val away: GalaxyEntry, val result: SimResult) {
    val winner: GalaxyEntry get() = if (result.homeScore > result.awayScore) home else away
    val loser: GalaxyEntry get() = if (winner === home) away else home
}

/** Un turno del tabellone (Play-in, Ottavi, Quarti, Semifinali, Finale). */
data class GalaxyRound(val name: String, val games: List<GalaxyGame>)

data class GalaxyResult(
    val rounds: List<GalaxyRound>,
    val champion: GalaxyEntry,
    val runnerUp: GalaxyEntry,
    val mvp: SimMvp?,
    /** Terza classificata del podio (null con sole 2 squadre). */
    val third: GalaxyEntry? = null,
) {
    val final: GalaxyGame get() = rounds.last().games.single()
}

/** Punti del podio di ogni torneo: 1° 20, 2° 10, 3° 5 (si sommano torneo dopo torneo, per card). */
val PodiumPoints: List<Int> = listOf(20, 10, 5)

/**
 * Torneo a eliminazione diretta, partita secca, come un tabellone playoff. Con un numero di
 * squadre che non è una potenza di 2 (20 caselle: 16 + 4) le teste di serie più basse giocano
 * prima un turno di Play-in, e le vincitrici completano il tabellone principale. Accoppiamenti
 * classici: 1 contro l'ultima, 2 contro la penultima... così le prime teste di serie si possono
 * incontrare solo alla fine. Ogni partita usa [MatchSimulator.simulateBasket].
 */
object GalaxyTournament {

    fun play(entries: List<GalaxyEntry>, seed: Long): GalaxyResult {
        require(entries.size >= 2) { "Servono almeno due squadre" }
        var gameSeed = seed
        fun game(a: GalaxyEntry, b: GalaxyEntry): GalaxyGame {
            val (home, away) = if (a.seed <= b.seed) a to b else b to a
            return GalaxyGame(home, away, MatchSimulator.simulateBasket(home.team, away.team, gameSeed++))
        }

        val sorted = entries.sortedBy { it.seed }
        val rounds = mutableListOf<GalaxyRound>()
        var bracketSize = Integer.highestOneBit(sorted.size)
        var field = sorted
        val extra = sorted.size - bracketSize
        if (extra > 0) {
            // Le ultime 2 × extra teste di serie si giocano i posti mancanti.
            val direct = sorted.take(sorted.size - 2 * extra)
            val playIn = sorted.takeLast(2 * extra)
            val games = (0 until extra).map { i -> game(playIn[i], playIn[playIn.lastIndex - i]) }
            rounds += GalaxyRound("Play-in", games)
            field = direct + games.map { it.winner }
        }
        bracketSize = field.size
        // Ordine del tabellone: 1-16, 8-9, 5-12, 4-13, 6-11, 3-14, 7-10, 2-15 (per 16 squadre).
        var current = bracketOrder(bracketSize).map { field[it] }
        while (current.size > 1) {
            val games = current.chunked(2).map { (a, b) -> game(a, b) }
            rounds += GalaxyRound(roundName(current.size), games)
            current = games.map { it.winner }
        }
        // Podio: con due sconfitte in semifinale si giocano il 3° posto (turno prima della finale, così
        // la finale resta l'ultimo); con una sola (es. 3 squadre: Play-in e finale) è lei la terza.
        val semiLosers = rounds.getOrNull(rounds.lastIndex - 1)?.games?.map { it.loser }.orEmpty()
        val third = when (semiLosers.size) {
            0 -> null
            1 -> semiLosers.single()
            else -> {
                val bronze = game(semiLosers[0], semiLosers[1])
                rounds.add(rounds.lastIndex, GalaxyRound("Finale 3° posto", listOf(bronze)))
                bronze.winner
            }
        }
        val final = rounds.last().games.single()
        return GalaxyResult(rounds, final.winner, final.loser, tournamentMvp(rounds), third)
    }

    /** Posizioni (0-based, per testa di serie) nell'ordine del tabellone, per una potenza di 2. */
    private fun bracketOrder(size: Int): List<Int> {
        var order = listOf(0)
        while (order.size < size) {
            val n = order.size * 2
            order = order.flatMap { listOf(it, n - 1 - it) }
        }
        return order
    }

    private fun roundName(teams: Int) = when (teams) {
        2 -> "Finale"
        4 -> "Semifinali"
        8 -> "Quarti"
        16 -> "Ottavi"
        32 -> "Sedicesimi"
        else -> "Turno a $teams"
    }

    /** MVP del torneo: più punti in totale su tutte le partite giocate. */
    private fun tournamentMvp(rounds: List<GalaxyRound>): SimMvp? {
        val totals = mutableMapOf<Pair<String, String>, Int>()
        rounds.flatMap { it.games }.forEach { g ->
            g.result.lines.forEach { line ->
                val team = if (line.team == 0) g.home.team.name else g.away.team.name
                val key = line.player.name to team
                totals[key] = (totals[key] ?: 0) + line.points
            }
        }
        return totals.maxByOrNull { it.value }?.let { (key, pts) -> SimMvp(key.first, key.second, pts) }
    }
}

/** MVP di una partita: il miglior realizzatore della squadra vincitrice (in caso di pareggio, di
 *  tutta la partita); a parità di punti/gol, chi ha vinto più duelli. */
fun matchMvp(result: SimResult, homeName: String, awayName: String): SimMvp? {
    val winnerSide = when {
        result.homeScore > result.awayScore -> 0
        result.awayScore > result.homeScore -> 1
        else -> null
    }
    val best = result.lines.filter { winnerSide == null || it.team == winnerSide }
        .maxWithOrNull(compareBy({ it.points }, { it.duelsWon })) ?: return null
    return SimMvp(best.player.name, if (best.team == 0) homeName else awayName, best.points)
}
