package com.scouttable.app.data.ranking

import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.lookup.isCentrocampista
import com.scouttable.app.data.lookup.isDifensore
import com.scouttable.app.data.lookup.isPortiere

/**
 * Valori di rendimento usati dalle classifiche (Eff+bonus NBA per il basket, 4 classifiche per
 * ruolo per il calcio, vedi [com.scouttable.app.ui.ranking]) e dalla regola con cui la stella
 * ([Player.star]) viene assegnata automaticamente in fase di generazione/aggiornamento lista
 * (vedi [com.scouttable.app.data.PlayerRepository.updateList]): un valore assoluto di rendimento
 * sopra soglia, non la posizione in classifica, così la soglia non dipende da chi altro c'è in
 * lista in quel momento. L'utente può comunque forzarla a mano da "Modifica giocatore": quel
 * tocco manuale resta finché non arriva un nuovo Genera/Aggiorna, che la ricalcola da zero
 * (comportamento richiesto esplicitamente, invece di un flag di override persistente).
 */
object PlayerScoring {

    // ---- Basket: Eff medio di carriera + bonus NBA ----

    const val NBA_BONUS = 5

    // Confrontato contro il campo "competizione" (massima competizione disputata, es. "NBA"),
    // valorizzato da TheSportsDB/Proballers.
    fun isInNba(player: Player): Boolean = player.competizione.equals("NBA", ignoreCase = true)

    fun nbaBonus(player: Player): Int = if (isInNba(player)) NBA_BONUS else 0

    /** Eff medio + eventuale bonus NBA: valore usato per l'ordinamento della classifica basket. */
    fun basketScore(player: Player): Int = player.effMedio + nbaBonus(player)

    // Un Eff medio Proballers >= 20 è già da top player europeo; con il bonus NBA un titolare NBA
    // "normale" (eff ~15-20) lo supera facilmente: soglia scelta per premiare un rendimento
    // davvero alto, non semplicemente l'aver incollato un URL Proballers.
    private const val BASKET_STAR_THRESHOLD = 25

    // ---- Calcio: 4 classifiche per ruolo ----

    /**
     * "Rendimento gol" (attaccanti e centrocampisti, stessa formula richiesta): gol totali di
     * carriera rapportati alle presenze totali (non solo alla stagione migliore), scalato *100
     * per leggibilità (es. 0.5 gol/presenza -> 50).
     */
    fun goalScore(player: Player): Int {
        if (player.presenze <= 0) return 0
        return ((player.punteggio.toDouble() / player.presenze) * 100).toInt()
    }

    /**
     * Presenze totali di carriera (A) + rendimento gol, stessa formula di [goalScore] usata per
     * attaccanti/centrocampisti (B), come bonus per il contributo offensivo del difensore: A + B.
     */
    fun defenderScore(player: Player): Int = player.presenze + goalScore(player)

    /**
     * "Rendimento portiere": capacità di non subire gol rapportata a presenze e gol subiti totali
     * di carriera (1 meno la media gol subiti a partita, scalato *100; sotto zero — quindi 0 — se
     * la media supera 1 gol/partita).
     */
    fun goalkeeperScore(player: Player): Int {
        if (player.presenze <= 0) return 0
        val avgConceded = player.golSubiti.toDouble() / player.presenze
        return ((1.0 - avgConceded) * 100).toInt().coerceAtLeast(0)
    }

    private const val ATTACKER_STAR_THRESHOLD = 35
    private const val MIDFIELDER_STAR_THRESHOLD = 15
    private const val DEFENDER_STAR_THRESHOLD = 250
    private const val GOALKEEPER_STAR_THRESHOLD = 40

    /**
     * Valore di rendimento calcio corretto per il ruolo del giocatore (vedi [goalScore]/
     * [defenderScore]/[goalkeeperScore]): i ruoli non riconosciuti (es. un'etichetta non tradotta)
     * ricadono sulla formula attaccanti/centrocampisti, la più generica.
     */
    fun calcioScore(player: Player): Int = when {
        isPortiere(player.ruolo) -> goalkeeperScore(player)
        isDifensore(player.ruolo) -> defenderScore(player)
        else -> goalScore(player)
    }

    private fun calcioStarThreshold(ruolo: String): Int = when {
        isPortiere(ruolo) -> GOALKEEPER_STAR_THRESHOLD
        isDifensore(ruolo) -> DEFENDER_STAR_THRESHOLD
        isCentrocampista(ruolo) -> MIDFIELDER_STAR_THRESHOLD
        else -> ATTACKER_STAR_THRESHOLD
    }

    private fun score(player: Player): Int =
        if (player.sport == Sport.BASKET) basketScore(player) else calcioScore(player)

    private fun starThreshold(player: Player): Int =
        if (player.sport == Sport.BASKET) BASKET_STAR_THRESHOLD else calcioStarThreshold(player.ruolo)

    /**
     * Vero se il rendimento del giocatore (vedi [score]) supera la soglia stella del suo
     * sport/ruolo: valutata ad ogni "Genera"/"Aggiorna lista" (vedi
     * [com.scouttable.app.data.PlayerRepository.updateList]) e scritta su [Player.star],
     * sovrascrivendo un'eventuale stella impostata a mano in precedenza.
     */
    fun computeStar(player: Player): Boolean = score(player) >= starThreshold(player)

    private fun scoreLabel(player: Player): String = when {
        player.sport == Sport.BASKET -> "Eff"
        isPortiere(player.ruolo) -> "rendimento portiere"
        isDifensore(player.ruolo) -> "presenze+rendimento gol"
        else -> "rendimento gol"
    }

    /**
     * Spiegazione testuale per la stella mostrata in [com.scouttable.app.ui.common.PlayerRow]:
     * null se il giocatore non ha la stella. Se il rendimento attuale è sopra soglia, la stella è
     * quella assegnata automaticamente (l'unico modo in cui può nascere o restare dopo un
     * Genera/Aggiorna); se è sotto, vuol dire che l'utente l'ha forzata a mano da "Modifica
     * giocatore" dall'ultima rigenerazione, e la prossima la rimuoverà.
     */
    fun starReason(player: Player): String? {
        if (!player.star) return null
        val value = score(player)
        val threshold = starThreshold(player)
        val label = scoreLabel(player)
        return if (value >= threshold) {
            "Stella automatica: $label $value (soglia $threshold)"
        } else {
            "Stella manuale: $label $value, sotto soglia $threshold"
        }
    }
}
