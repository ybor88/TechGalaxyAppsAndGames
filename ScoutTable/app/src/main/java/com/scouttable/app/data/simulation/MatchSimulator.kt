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
 * Motore di "Simula partita", basato solo sui valori inseriti dall'utente per ogni posizione
 * (vedi ManualTeams): le squadre non arrivano più da un link.
 *
 * Basket: 10 giocatori per squadra in posizioni fisse (Playmaker, Guardia, Ala piccola, Ala grande,
 * Centro, prima i 5 titolari poi i 5 di panchina). Ogni giocatore sfida solo chi occupa la stessa
 * posizione nell'altra squadra (titolare contro titolare, panchina contro panchina) e vince chi ha
 * l'Eff più alto. I punti di squadra dipendono dal rapporto tra le forze (Eff dei titolari pieno,
 * della panchina al 55%); i punti di ogni giocatore sono la sua quota di Eff, +15% se vince il suo
 * duello e −13% se lo perde, con una variazione del 15%. In caso di parità, supplementari da 5'.
 *
 * Calcio: 11 contro 11 in 4-4-2 (portiere, 4 difensori, 4 centrocampisti, 2 attaccanti). Duello tra
 * pari posizione: vince il rapporto gol / presenze più alto; tra i portieri vince chi subisce meno
 * gol a partita. I gol attesi di ogni giocatore sono il suo rapporto gol / presenze, ×1,25 se vince
 * il duello e ×0,8 se lo perde, corretti per il portiere avversario (chi subisce poco fa segnare
 * meno); poi estrazione di Poisson, quindi il pareggio è possibile.
 *
 * Con lo stesso [seed] il risultato è sempre lo stesso; "Rigioca" usa un seed nuovo.
 */
object MatchSimulator {

    const val BASKET_QUARTER = 12 * 60
    const val SOCCER_HALF = 45 * 60
    private const val BASKET_OT = 5 * 60
    /** Numero di titolari nel basket: le prime 5 posizioni. */
    private const val BASKET_STARTERS = 5

    // ------------------------------------------------------------------ comune

    /** Duelli tra giocatori nella stessa posizione ([SimPlayer.slot]); chi non ha un avversario
     *  nella sua posizione non disputa duelli. */
    private fun slotDuels(home: List<SimPlayer>, away: List<SimPlayer>, homeWins: (SimPlayer, SimPlayer) -> Boolean?, rnd: Random): List<SimDuel> {
        val awayBySlot = away.associateBy { it.slot }
        return home.mapNotNull { h ->
            val a = awayBySlot[h.slot] ?: return@mapNotNull null
            SimDuel(h, a, homeWins(h, a) ?: rnd.nextBoolean())
        }
    }

    private fun duelOf(p: SimPlayer, team: Int, duels: List<SimDuel>): SimDuel? =
        duels.firstOrNull { (if (team == 0) it.home else it.away) === p }

    /** true se [p] ha vinto il suo duello, false se l'ha perso, null se non ne ha (posizione vuota). */
    private fun wonDuel(p: SimPlayer, team: Int, duels: List<SimDuel>): Boolean? =
        duelOf(p, team, duels)?.let { it.homeWon == (team == 0) }

    private fun lines(team: Int, players: List<SimPlayer>, points: IntArray, duels: List<SimDuel>) = players.mapIndexed { i, p ->
        val won = wonDuel(p, team, duels)
        SimPlayerLine(team, p, points[i], if (won == true) 1 else 0, if (won == null) 0 else 1)
    }

    // ------------------------------------------------------------------ basket

    /** Eff a partita inserito per il giocatore (0 se mancante). */
    fun basketValue(p: SimPlayer): Double = p.eff ?: 0.0

    private fun strength(players: List<SimPlayer>): Double =
        players.sumOf { p -> max(basketValue(p), 0.0) * if (isStarter(p)) 1.0 else 0.55 } + 1.0

    /** Titolare = una delle prime 5 posizioni. */
    private fun isStarter(p: SimPlayer) = p.slot in 0 until BASKET_STARTERS

    fun simulateBasket(home: SimTeam, away: SimTeam, seed: Long): SimResult {
        val rnd = Random(seed)
        val rotH = home.players
        val rotA = away.players
        val duels = slotDuels(rotH, rotA, { h, a -> if (basketValue(h) == basketValue(a)) null else basketValue(h) > basketValue(a) }, rnd)

        // Punti di squadra: circa 105 a pari forza, spostati dal rapporto tra le forze (Eff), in una
        // fascia da partita vera; un piccolo vantaggio a chi gioca in casa.
        val ratio = strength(rotH) / strength(rotA)
        val totalH = (105 * ratio.pow(0.35) * 1.02).coerceIn(75.0, 135.0)
        val totalA = (105 / ratio.pow(0.35)).coerceIn(75.0, 135.0)
        fun expected(players: List<SimPlayer>, team: Int, total: Double): DoubleArray {
            val weights = players.map { p ->
                val duel = when (wonDuel(p, team, duels)) { true -> 1.15; false -> 0.87; null -> 1.0 }
                (max(basketValue(p), 0.0) + 2.0) * (if (isStarter(p)) 1.0 else 0.55) * duel
            }
            val sum = weights.sum().takeIf { it > 0 } ?: 1.0
            return DoubleArray(players.size) { i -> total * weights[i] / sum }
        }
        val expectedH = expected(rotH, 0, totalH)
        val expectedA = expected(rotA, 1, totalA)

        val events = mutableListOf<SimEvent>()
        val regulation = 4 * BASKET_QUARTER
        val pointsH = IntArray(rotH.size)
        val pointsA = IntArray(rotA.size)
        fun play(from: Int, to: Int, share: Double) {
            rotH.forEachIndexed { i, p ->
                val pts = noisyPoints(expectedH[i] * share, rnd)
                pointsH[i] += pts
                events += basketEvents(p, pts, 0, rotH, from, to, rnd)
            }
            rotA.forEachIndexed { i, p ->
                val pts = noisyPoints(expectedA[i] * share, rnd)
                pointsA[i] += pts
                events += basketEvents(p, pts, 1, rotA, from, to, rnd)
            }
        }
        play(0, regulation, 1.0)

        var overtimes = 0
        while (pointsH.sum() == pointsA.sum() && rotH.isNotEmpty() && rotA.isNotEmpty()) {
            val start = regulation + overtimes * BASKET_OT
            play(start, start + BASKET_OT, BASKET_OT.toDouble() / regulation)
            overtimes++
            // Dopo 4 supplementari ancora pari: decide un tiro libero della squadra con più Eff.
            if (overtimes >= 4 && pointsH.sum() == pointsA.sum()) {
                val homeStronger = ratio >= 1.0
                val shooter = (if (homeStronger) rotH else rotA).first()
                events += SimEvent(regulation + overtimes * BASKET_OT - 1, if (homeStronger) 0 else 1, shooter.name, 1)
                if (homeStronger) pointsH[0]++ else pointsA[0]++
            }
        }

        return SimResult(
            events = events.sortedBy { it.second },
            homeScore = pointsH.sum(),
            awayScore = pointsA.sum(),
            duels = duels,
            lines = lines(0, rotH, pointsH, duels) + lines(1, rotA, pointsA, duels),
            totalSeconds = regulation + overtimes * BASKET_OT,
            periodSeconds = BASKET_QUARTER,
            regulationPeriods = 4,
            overtimes = overtimes,
            homeLineup = rotH.filter(::isStarter).ifEmpty { rotH.take(BASKET_STARTERS) },
            awayLineup = rotA.filter(::isStarter).ifEmpty { rotA.take(BASKET_STARTERS) },
        )
    }

    private fun noisyPoints(expected: Double, rnd: Random): Int =
        max(0.0, expected * gaussian(rnd, 1.0, 0.15)).roundToInt()

    /** Scompone i punti in canestri da 3, da 2 e tiri liberi, sparsi nel tempo di gioco. */
    private fun basketEvents(p: SimPlayer, points: Int, team: Int, rotation: List<SimPlayer>, from: Int, to: Int, rnd: Random): List<SimEvent> {
        if (points <= 0) return emptyList()
        // Esterni (playmaker, guardie, ali piccole) tirano di più da 3.
        val shareFromThree = when (p.position.uppercase()) {
            "PG", "SG" -> 0.40
            "SF" -> 0.30
            else -> 0.12
        }
        val threes = (points * shareFromThree / 3).roundToInt().coerceAtMost(points / 3)
        val rest = points - threes * 3
        var twos = rest / 2
        var ones = rest % 2
        // Un paio di canestri da 2 diventano liberi, come in una partita vera.
        if (twos > 3 && rnd.nextDouble() < 0.5) { twos--; ones += 2 }
        val mates = rotation.filter { it.name != p.name }
        fun assist(): String? = if (mates.isEmpty() || rnd.nextDouble() > 0.55) null else mates[rnd.nextInt(mates.size)].name
        val out = mutableListOf<SimEvent>()
        repeat(threes) { out += SimEvent(rnd.nextInt(from, to), team, p.name, 3, assist()) }
        repeat(twos) { out += SimEvent(rnd.nextInt(from, to), team, p.name, 2, assist()) }
        repeat(ones) { out += SimEvent(rnd.nextInt(from, to), team, p.name, 1) }
        return out
    }

    // ------------------------------------------------------------------ calcio

    private fun isGoalkeeper(p: SimPlayer) = soccerRole(p) == SoccerRole.PORTIERE

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

    /** Gol subiti a partita di un portiere (null se non inseriti). */
    private fun concededPerGame(p: SimPlayer): Double? = p.goalsAgainstPer90

    fun simulateSoccer(home: SimTeam, away: SimTeam, seed: Long): SimResult {
        val rnd = Random(seed)
        val lineH = home.players
        val lineA = away.players
        // Duello tra pari posizione: rapporto gol/presenze più alto; tra portieri meno gol subiti.
        val duels = slotDuels(lineH, lineA, { h, a ->
            if (isGoalkeeper(h) && isGoalkeeper(a)) {
                val ch = concededPerGame(h)
                val ca = concededPerGame(a)
                if (ch == null || ca == null || ch == ca) null else ch < ca
            } else {
                val rh = goalsPerGame(h)
                val ra = goalsPerGame(a)
                if (rh == ra) null else rh > ra
            }
        }, rnd)
        // Portiere avversario: chi subisce meno della media (1,2 gol a partita) fa segnare meno.
        fun keeperFactor(line: List<SimPlayer>) =
            line.firstOrNull(::isGoalkeeper)?.let(::concededPerGame)?.let { sqrt((it / 1.2).coerceIn(0.5, 2.0)) } ?: 1.0
        val events = mutableListOf<SimEvent>()
        fun goals(team: Int, line: List<SimPlayer>, keeper: Double, bonus: Double): IntArray {
            val outfield = line.filter { !isGoalkeeper(it) }
            return IntArray(line.size) { i ->
                val p = line[i]
                if (isGoalkeeper(p)) return@IntArray 0
                val duel = when (wonDuel(p, team, duels)) { true -> 1.25; false -> 0.8; null -> 1.0 }
                val g = poisson(goalsPerGame(p) * duel * keeper * bonus, rnd)
                repeat(g) { events += SimEvent(goalSecond(rnd), team, p.name, 1, soccerAssist(p, outfield, rnd)) }
                g
            }
        }
        val goalsH = goals(0, lineH, keeperFactor(lineA), 1.05)
        val goalsA = goals(1, lineA, keeperFactor(lineH), 0.97)
        return SimResult(
            events = events.sortedBy { it.second },
            homeScore = goalsH.sum(),
            awayScore = goalsA.sum(),
            duels = duels,
            lines = lines(0, lineH, goalsH, duels) + lines(1, lineA, goalsA, duels),
            totalSeconds = 2 * SOCCER_HALF,
            periodSeconds = SOCCER_HALF,
            regulationPeriods = 2,
            overtimes = 0,
            homeLineup = lineH,
            awayLineup = lineA,
        )
    }

    private fun goalSecond(rnd: Random) = rnd.nextInt(30, 2 * SOCCER_HALF - 5)

    private fun soccerAssist(scorer: SimPlayer, mates: List<SimPlayer>, rnd: Random): String? {
        val others = mates.filter { it !== scorer }
        if (others.isEmpty() || rnd.nextDouble() > 0.7) return null
        return others[rnd.nextInt(others.size)].name
    }

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
