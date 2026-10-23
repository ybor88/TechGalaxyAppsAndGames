// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.ranking

import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.ranking.PlayerScoring

// effMedio = 0 significa "mai calcolato" (nessun URL Proballers incollato), non un vero Eff
// pari a zero: va escluso invece di comparire come ultimo in classifica.
internal fun basketRanking(players: List<Player>): List<Player> =
    players.filter { it.effMedio > 0 }.sortedByDescending { PlayerScoring.basketScore(it) }

// presenze = 0 significa "nessuna statistica di carriera trovata": va escluso invece di
// comparire come ultimo in classifica, stesso criterio di effMedio = 0 nel basket.
internal fun calcioRanking(filtro: CalcioRuoloFiltro, players: List<Player>): List<Player> =
    players.filter { it.presenze > 0 && matchesRuolo(filtro, it.ruolo) }
        .sortedByDescending { scoreFor(filtro, it) }

/** Posizione di un giocatore nella sua classifica: [position] su [total] giocatori classificati,
 *  [label] è il nome della classifica (es. "Eff medio", "Attaccanti"). */
data class RankPosition(val position: Int, val total: Int, val label: String)

/**
 * Posizione del giocatore nella stessa classifica mostrata nella tab "Classifica" (basket per Eff
 * medio, calcio in quella del suo ruolo), calcolata su tutti i giocatori della lista dello sport
 * e non sui filtri. null se il giocatore non è in classifica (Eff medio o presenze non disponibili).
 */
fun rankPositionOf(sport: Sport, player: Player, players: List<Player>): RankPosition? {
    val (ranked, label) = when (sport) {
        Sport.BASKET, Sport.BASKET_REGIONALE -> basketRanking(players) to "Eff medio"
        Sport.CALCIO -> {
            val filtro = CalcioRuoloFiltro.entries.first { matchesRuolo(it, player.ruolo) }
            calcioRanking(filtro, players) to filtro.label
        }
    }
    val index = ranked.indexOfFirst { it.id == player.id }
    return if (index < 0) null else RankPosition(index + 1, ranked.size, label)
}

/** Come [rankPositionOf], ma per tutti i giocatori in una volta (id -> posizione): ogni
 *  classifica viene ordinata una sola volta invece che una per riga della lista. */
fun rankPositions(sport: Sport, players: List<Player>): Map<String, RankPosition> {
    val rankings = when (sport) {
        Sport.BASKET, Sport.BASKET_REGIONALE -> listOf(basketRanking(players) to "Eff medio")
        Sport.CALCIO -> CalcioRuoloFiltro.entries.map { calcioRanking(it, players) to it.label }
    }
    return buildMap {
        rankings.forEach { (ranked, label) ->
            ranked.forEachIndexed { index, p -> put(p.id, RankPosition(index + 1, ranked.size, label)) }
        }
    }
}
