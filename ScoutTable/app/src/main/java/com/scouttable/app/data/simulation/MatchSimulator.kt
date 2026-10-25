// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.simulation

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Giocatore di una squadra di "Simula partita", con i valori inseriti dall'utente (vedi ManualTeams).
 * Basket: [eff] a partita, [position] PG/SG/SF/PF/C. Calcio: [games] = presenze, [goals] = gol e,
 * per i portieri, [goalsAgainstPer90] = gol subiti a partita; [position] GK/DF/MF/FW.
 * Gli altri campi restano a disposizione per eventuali fonti future.
 */
data class SimPlayer(
    val name: String,
    val position: String = "",
    val games: Int = 0,
    val minutes: Double = 0.0,
    val points: Double = 0.0,
    val rebounds: Double = 0.0,
    val assists: Double = 0.0,
    val steals: Double = 0.0,
    val blocks: Double = 0.0,
    val turnovers: Double = 0.0,
    val threePct: Double = 0.0,
    val eff: Double? = null,
    val goals: Int = 0,
    val assistsTotal: Int = 0,
    val minutesTotal: Int = 0,
    val goalsAgainstPer90: Double? = null,
    /** Indice della posizione nella squadra inserita (vedi ManualTeams.slotsFor): i duelli sono tra
     *  giocatori con lo stesso indice, anche se una squadra lascia vuote alcune posizioni. */
    val slot: Int = -1,
)

data class SimTeam(
    val name: String,
    val logoUrl: String?,
    val sourceUrl: String,
    val players: List<SimPlayer>,
    /** Avviso sulla stagione, se diversa da quella del link (es. stagione chiesta bloccata, usata quella in corso). */
    val seasonNote: String? = null,
    /** Valore della squadra inserito dall'utente: basket efficienza totale (vince il più alto); calcio
     *  valore percentuale/decimale (vince il più basso). Vedi [MatchSimulator]. */
    val teamValue: Double? = null,
)

/** Un canestro o un gol: [team] 0 = squadra di casa, 1 = ospite; [second] secondi di gioco dall'inizio. */
data class SimEvent(val second: Int, val team: Int, val player: String, val points: Int, val assist: String? = null)

/** Reparto di un calciatore: nel calcio i duelli sono solo tra giocatori dello stesso reparto. */
enum class SoccerRole(val code: String, val label: String) {
    PORTIERE("GK", "Portieri"),
    DIFENSORE("DF", "Difensori"),
    CENTROCAMPISTA("MF", "Centrocampisti"),
    ATTACCANTE("FW", "Attaccanti"),
}

/** Duello tra un giocatore di casa e uno ospite nella stessa posizione. */
data class SimDuel(val home: SimPlayer, val away: SimPlayer, val homeWon: Boolean)

/** Riepilogo di un giocatore: punti (o gol) segnati e duello vinto (1/1) o perso (0/1). */
data class SimPlayerLine(val team: Int, val player: SimPlayer, val points: Int, val duelsWon: Int, val duelsPlayed: Int)

data class SimResult(
    val events: List<SimEvent>,
    val homeScore: Int,
    val awayScore: Int,
    /** I duelli giocatore contro giocatore, uno per posizione. */
    val duels: List<SimDuel>,
    /** Una riga per giocatore in campo, casa poi ospite, nell'ordine della rotazione. */
    val lines: List<SimPlayerLine>,
    /** Durata totale in secondi di gioco (con eventuali supplementari). */
    val totalSeconds: Int,
    /** Durata di un periodo (quarto da 12' o tempo da 45'). */
    val periodSeconds: Int,
    val regulationPeriods: Int,
    val overtimes: Int,
    /** Basket: i cinque titolari usati in campo (stesso ordine della rotazione). */
    val homeLineup: List<SimPlayer>,
    val awayLineup: List<SimPlayer>,
) {
    val homeDuelsWon: Int get() = duels.count { it.homeWon }
    val awayDuelsWon: Int get() = duels.size - homeDuelsWon
}

/**
 * Motore di "Simula partita", basato su un solo valore per squadra inserito dall'utente
 * ([SimTeam.teamValue]): basket l'efficienza totale, calcio un valore percentuale/decimale.
 * Basket: vince il valore più alto. Calcio: vince il valore più BASSO. Il punteggio rispecchia la distanza tra i due
 * valori (più sono lontani, più la vittoria è netta). A valori uguali: nel basket decidono i
 * supplementari (vincitore a sorte), nel calcio è pareggio.
 * Con lo stesso [seed] il punteggio è sempre lo stesso; "Rigioca" usa un seed nuovo (cambia il
 * punteggio, non il vincitore).
 */
object MatchSimulator {

    const val BASKET_QUARTER = 12 * 60
    const val SOCCER_HALF = 45 * 60
    private const val BASKET_OT = 5 * 60

    /** Valore della squadra (0 se non inserito). */
    fun teamValue(t: SimTeam): Double = t.teamValue ?: 0.0

    /** Distanza relativa tra i due valori (0 = uguali, 1 = uno dei due vale zero). */
    private fun gap(a: Double, b: Double): Double {
        val top = maxOf(kotlin.math.abs(a), kotlin.math.abs(b))
        return if (top <= 0.0) 0.0 else (kotlin.math.abs(a - b) / top).coerceAtMost(1.0)
    }

    /** Sagome per il campo animato (le squadre non hanno più i giocatori): "Knicks 1", "Knicks 2"... */
    private fun placeholders(team: SimTeam, count: Int, positions: List<String>): List<SimPlayer> =
        (0 until count).map { i -> SimPlayer(name = "${team.name} ${i + 1}", position = positions[i % positions.size], slot = i) }

    // ------------------------------------------------------------------ basket

    fun simulateBasket(home: SimTeam, away: SimTeam, seed: Long): SimResult {
        val rnd = Random(seed)
        val vH = teamValue(home)
        val vA = teamValue(away)
        val regulation = 4 * BASKET_QUARTER
        // Punteggio della squadra che perde intorno ai 95 punti; scarto da 2 a 35 punti secondo la
        // distanza tra i valori.
        val loserScore = (95 + gaussian(rnd, 0.0, 7.0)).roundToInt().coerceIn(70, 120)
        val margin = (2 + gap(vH, vA) * 60 + gaussian(rnd, 0.0, 2.0)).roundToInt().coerceIn(1, 35)
        var overtimes = 0
        var scoreH: Int
        var scoreA: Int
        if (vH == vA) {
            // Valori uguali: parità a fine tempi regolamentari, decide un supplementare a sorte.
            overtimes = 1
            val homeWins = rnd.nextBoolean()
            scoreH = loserScore + 8 + if (homeWins) 3 else 0
            scoreA = loserScore + 8 + if (homeWins) 0 else 3
        } else if (vH > vA) {
            scoreH = loserScore + margin; scoreA = loserScore
        } else {
            scoreH = loserScore; scoreA = loserScore + margin
        }
        val total = regulation + overtimes * BASKET_OT
        val events = basketEvents(home.name, 0, scoreH, total, rnd) + basketEvents(away.name, 1, scoreA, total, rnd)
        val basketPositions = listOf("PG", "SG", "SF", "PF", "C")
        return SimResult(
            events = events.sortedBy { it.second },
            homeScore = scoreH,
            awayScore = scoreA,
            duels = emptyList(),
            lines = emptyList(),
            totalSeconds = total,
            periodSeconds = BASKET_QUARTER,
            regulationPeriods = 4,
            overtimes = overtimes,
            homeLineup = placeholders(home, 5, basketPositions),
            awayLineup = placeholders(away, 5, basketPositions),
        )
    }

    /** Scompone i punti in canestri da 3, da 2 e liberi, sparsi in tutta la partita. */
    private fun basketEvents(team: String, side: Int, points: Int, total: Int, rnd: Random): List<SimEvent> {
        val threes = (points * 0.33 / 3).roundToInt()
        val rest = points - threes * 3
        val ones = (rest * 0.18).roundToInt().let { if ((rest - it) % 2 != 0) it + 1 else it }.coerceAtMost(rest)
        val twos = (rest - ones) / 2
        val out = mutableListOf<SimEvent>()
        repeat(threes) { out += SimEvent(rnd.nextInt(0, total), side, team, 3) }
        repeat(twos) { out += SimEvent(rnd.nextInt(0, total), side, team, 2) }
        repeat(ones) { out += SimEvent(rnd.nextInt(0, total), side, team, 1) }
        return out
    }

    // ------------------------------------------------------------------ calcio

    /** Reparto di un calciatore dalla sigla della posizione (GK, DF, MF, FW). */
    fun soccerRole(p: SimPlayer): SoccerRole {
        val pos = p.position.uppercase()
        return when {
            pos.startsWith("GK") || pos.contains("PORTIERE") -> SoccerRole.PORTIERE
            pos.startsWith("DF") -> SoccerRole.DIFENSORE
            pos.startsWith("FW") -> SoccerRole.ATTACCANTE
            else -> SoccerRole.CENTROCAMPISTA
        }
    }

    /** Gol segnati diviso presenze (0 senza presenze). */
    fun goalsPerGame(p: SimPlayer): Double = if (p.games <= 0) 0.0 else p.goals.toDouble() / p.games

    fun simulateSoccer(home: SimTeam, away: SimTeam, seed: Long): SimResult {
        val rnd = Random(seed)
        val vH = teamValue(home)
        val vA = teamValue(away)
        // Gol della squadra che perde (o di entrambe nel pareggio): 0-2; scarto da 1 a 4 gol secondo
        // la distanza tra i valori.
        val base = poisson(0.9, rnd).coerceAtMost(3)
        val margin = (1 + gap(vH, vA) * 4).roundToInt().coerceIn(1, 4)
        // Nel calcio vince il valore PIÙ BASSO (richiesto esplicitamente).
        val (goalsH, goalsA) = when {
            vH == vA -> base to base
            vH < vA -> (base + margin) to base
            else -> base to (base + margin)
        }
        val events = (List(goalsH) { SimEvent(goalSecond(rnd), 0, home.name, 1) } + List(goalsA) { SimEvent(goalSecond(rnd), 1, away.name, 1) })
        val soccerPositions = listOf("GK", "DF", "DF", "DF", "DF", "MF", "MF", "MF", "MF", "FW", "FW")
        return SimResult(
            events = events.sortedBy { it.second },
            homeScore = goalsH,
            awayScore = goalsA,
            duels = emptyList(),
            lines = emptyList(),
            totalSeconds = 2 * SOCCER_HALF,
            periodSeconds = SOCCER_HALF,
            regulationPeriods = 2,
            overtimes = 0,
            homeLineup = placeholders(home, 11, soccerPositions),
            awayLineup = placeholders(away, 11, soccerPositions),
        )
    }

    private fun goalSecond(rnd: Random) = rnd.nextInt(30, 2 * SOCCER_HALF - 5)

    // ------------------------------------------------------------------ casualità

    private fun gaussian(rnd: Random, mean: Double, sd: Double): Double {
        val u1 = rnd.nextDouble().coerceAtLeast(1e-9)
        val u2 = rnd.nextDouble()
        return mean + sd * sqrt(-2 * ln(u1)) * kotlin.math.cos(2 * Math.PI * u2)
    }

    private fun poisson(lambda: Double, rnd: Random): Int {
        if (lambda <= 0) return 0
        val l = exp(-lambda)
        var k = 0
        var p = 1.0
        do { k++; p *= rnd.nextDouble() } while (p > l)
        return k - 1
    }
}
