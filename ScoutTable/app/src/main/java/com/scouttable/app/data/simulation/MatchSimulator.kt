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
 * Giocatore di una squadra letta per "Simula partita".
 * Basket: medie a partita ([minutes], [points], [rebounds]...), [eff] è la valutazione media se la
 * fonte la riporta (Proballers "Val"). Calcio: totali di stagione ([goals], [assistsTotal],
 * [minutesTotal]) e, per i portieri, i gol subiti ogni 90 minuti ([goalsAgainstPer90]).
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
)

data class SimTeam(val name: String, val logoUrl: String?, val sourceUrl: String, val players: List<SimPlayer>)

/** Un canestro o un gol: [team] 0 = squadra di casa, 1 = ospite; [second] secondi di gioco dall'inizio. */
data class SimEvent(val second: Int, val team: Int, val player: String, val points: Int, val assist: String? = null)

/** Duello tra un giocatore di casa e uno ospite: ogni giocatore ne disputa uno contro ciascun avversario. */
data class SimDuel(val home: SimPlayer, val away: SimPlayer, val homeWon: Boolean)

/** Riepilogo di un giocatore: punti (o gol) segnati e duelli vinti contro tutti gli avversari. */
data class SimPlayerLine(val team: Int, val player: SimPlayer, val points: Int, val duelsWon: Int, val duelsPlayed: Int)

data class SimResult(
    val events: List<SimEvent>,
    val homeScore: Int,
    val awayScore: Int,
    /** Tutti i duelli giocatore contro giocatore (casa × ospite). */
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
 * Motore di "Simula partita": ogni giocatore gioca contro TUTTI i giocatori dell'altra squadra.
 * Per ogni coppia (casa × ospite) si disputa un duello: vince chi ha la valutazione più alta, con
 * una variabilità casuale. Quanto segna un giocatore dipende dalle sue statistiche confrontate in
 * media con quelle di tutti gli avversari e dalla quota di duelli vinti:
 * - basket: punti a partita × media su tutti gli avversari di (valutazione propria / avversario)^0.25
 *   × (0.85 + 0.3 × duelli vinti), minuti riportati a 240 per squadra, totale di squadra in una
 *   fascia realistica, leggero vantaggio casalingo; in caso di parità supplementari da 5';
 * - calcio: gol ogni 90' × media su tutti gli avversari di √(rating proprio / avversario) ×
 *   (0.8 + 0.4 × duelli vinti) × efficacia del portiere avversario, poi estrazione di Poisson
 *   (il pareggio è un risultato possibile).
 * Con lo stesso [seed] il risultato è sempre lo stesso; "Rigioca" usa un seed nuovo.
 */
object MatchSimulator {

    const val BASKET_QUARTER = 12 * 60
    const val SOCCER_HALF = 45 * 60
    private const val BASKET_OT = 5 * 60
    private const val HOME_BONUS = 1.03
    private const val AWAY_MALUS = 0.98

    /** Tutti i duelli casa × ospite: vince chi ha il valore più alto dopo una variabilità del 20%. */
    private fun playDuels(home: List<SimPlayer>, away: List<SimPlayer>, value: (SimPlayer) -> Double, rnd: Random): List<SimDuel> =
        home.flatMap { h ->
            away.map { a ->
                val vh = (max(value(h), 0.0) + 1.0) * gaussian(rnd, 1.0, 0.2) * HOME_BONUS
                val va = (max(value(a), 0.0) + 1.0) * gaussian(rnd, 1.0, 0.2)
                SimDuel(h, a, vh >= va)
            }
        }

    private fun duelsOf(p: SimPlayer, team: Int, duels: List<SimDuel>) = duels.filter { (if (team == 0) it.home else it.away) === p }

    private fun winRate(p: SimPlayer, team: Int, duels: List<SimDuel>): Double {
        val mine = duelsOf(p, team, duels)
        return if (mine.isEmpty()) 0.5 else mine.count { it.homeWon == (team == 0) }.toDouble() / mine.size
    }

    private fun lines(team: Int, players: List<SimPlayer>, points: IntArray, duels: List<SimDuel>) = players.mapIndexed { i, p ->
        val mine = duelsOf(p, team, duels)
        SimPlayerLine(team, p, points[i], mine.count { it.homeWon == (team == 0) }, mine.size)
    }

    // ------------------------------------------------------------------ basket

    fun basketRotation(team: SimTeam): List<SimPlayer> =
        team.players.filter { it.minutes > 0 || it.points > 0 }
            .sortedWith(compareByDescending<SimPlayer> { it.minutes }.thenByDescending { it.points })
            .take(10)

    /** Valutazione a partita: quella della fonte, altrimenti la somma delle voci positive meno le palle perse. */
    fun basketValue(p: SimPlayer): Double = p.eff ?: (p.points + p.rebounds + p.assists + p.steals + p.blocks - p.turnovers)

    /** Media del confronto con tutti gli avversari, pesata sui loro minuti (chi gioca di più conta di più). */
    private fun averageFactor(own: SimPlayer, opponents: List<SimPlayer>): Double {
        if (opponents.isEmpty()) return 1.0
        val weights = opponents.map { max(it.minutes, 1.0) }
        val sum = opponents.indices.sumOf { j -> matchupFactor(basketValue(own), basketValue(opponents[j])) * weights[j] }
        return sum / weights.sum()
    }

    fun simulateBasket(home: SimTeam, away: SimTeam, seed: Long): SimResult {
        val rnd = Random(seed)
        val rotH = basketRotation(home)
        val rotA = basketRotation(away)
        val scaleH = minutesScale(rotH)
        val scaleA = minutesScale(rotA)
        val events = mutableListOf<SimEvent>()
        val regulation = 4 * BASKET_QUARTER
        val duels = playDuels(rotH, rotA, ::basketValue, rnd)

        // Punti attesi di ogni giocatore contro tutta la squadra avversaria (riusati nei supplementari).
        val expectedH = DoubleArray(rotH.size) { i ->
            val p = rotH[i]
            p.points * scaleH * averageFactor(p, rotA) * (0.85 + 0.3 * winRate(p, 0, duels)) * HOME_BONUS
        }
        val expectedA = DoubleArray(rotA.size) { i ->
            val p = rotA[i]
            p.points * scaleA * averageFactor(p, rotH) * (0.85 + 0.3 * winRate(p, 1, duels)) * AWAY_MALUS
        }
        // Totale atteso di squadra riportato in una fascia da partita vera (75-130): con fonti di
        // stagioni diverse o con pochissime partite giocate le medie possono sommarsi in modo
        // irrealistico (es. 157 punti); le proporzioni tra i giocatori restano invariate.
        normalizeTotal(expectedH)
        normalizeTotal(expectedA)
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
            // Dopo 4 supplementari ancora pari: decide un tiro libero della squadra che ha vinto più duelli.
            if (overtimes >= 4 && pointsH.sum() == pointsA.sum()) {
                val homeStronger = duels.count { it.homeWon } * 2 >= duels.size
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
            homeLineup = rotH.take(5),
            awayLineup = rotA.take(5),
        )
    }

    /** I minuti della rotazione vengono riportati ai 240 di una partita (5 giocatori × 48'). */
    private fun minutesScale(rotation: List<SimPlayer>): Double {
        val total = rotation.sumOf { it.minutes }
        return if (total <= 0) 1.0 else (240.0 / total).coerceIn(0.6, 1.6)
    }

    private fun matchupFactor(own: Double, opponent: Double): Double =
        ((max(own, 0.0) + 4.0) / (max(opponent, 0.0) + 4.0)).pow(0.25).coerceIn(0.8, 1.2)

    private fun normalizeTotal(expected: DoubleArray) {
        val total = expected.sum()
        if (total <= 0) return
        val target = total.coerceIn(75.0, 130.0)
        for (i in expected.indices) expected[i] *= target / total
    }

    private fun noisyPoints(expected: Double, rnd: Random): Int =
        max(0.0, expected * gaussian(rnd, 1.0, 0.18)).roundToInt()

    /** Scompone i punti in canestri da 3, da 2 e tiri liberi, sparsi nel tempo di gioco. */
    private fun basketEvents(p: SimPlayer, points: Int, team: Int, rotation: List<SimPlayer>, from: Int, to: Int, rnd: Random): List<SimEvent> {
        if (points <= 0) return emptyList()
        val shareFromThree = when {
            p.threePct >= 0.33 -> 0.40
            p.threePct > 0 -> 0.25
            else -> 0.15
        }
        val threes = (points * shareFromThree / 3).roundToInt().coerceAtMost(points / 3)
        val rest = points - threes * 3
        var twos = rest / 2
        var ones = rest % 2
        // Un paio di canestri da 2 diventano liberi, come in una partita vera.
        if (twos > 3 && rnd.nextDouble() < 0.5) { twos--; ones += 2 }
        val mates = rotation.filter { it.name != p.name }
        val totalAst = mates.sumOf { it.assists }.takeIf { it > 0 }
        fun assist(): String? {
            if (mates.isEmpty() || rnd.nextDouble() > 0.55) return null
            if (totalAst == null) return mates[rnd.nextInt(mates.size)].name
            var r = rnd.nextDouble() * totalAst
            for (m in mates) { r -= m.assists; if (r <= 0) return m.name }
            return mates.last().name
        }
        val out = mutableListOf<SimEvent>()
        repeat(threes) { out += SimEvent(rnd.nextInt(from, to), team, p.name, 3, assist()) }
        repeat(twos) { out += SimEvent(rnd.nextInt(from, to), team, p.name, 2, assist()) }
        repeat(ones) { out += SimEvent(rnd.nextInt(from, to), team, p.name, 1) }
        return out
    }

    // ------------------------------------------------------------------ calcio

    private fun isGoalkeeper(p: SimPlayer) = p.position.uppercase().startsWith("GK") || p.position.contains("portiere", ignoreCase = true)

    /** Undici titolari: il portiere con più minuti e i dieci giocatori di movimento con più minuti. */
    fun soccerLineup(team: SimTeam): List<SimPlayer> {
        val used = team.players.filter { it.minutesTotal > 0 || it.games > 0 }
            .sortedWith(compareByDescending<SimPlayer> { it.minutesTotal }.thenByDescending { it.games })
        val gk = used.firstOrNull(::isGoalkeeper)
        val outfield = used.filter { it !== gk && !isGoalkeeper(it) }.take(10)
        val lineup = listOfNotNull(gk) + outfield
        return if (lineup.size >= 11 || used.size <= lineup.size) lineup else (lineup + used.filter { it !in lineup }).take(11)
    }

    private fun per90(value: Int, minutes: Int): Double = if (minutes <= 0) 0.0 else value * 90.0 / max(minutes, 270)

    /** Rating per i duelli: gol e assist ogni 90' e affidabilità (minuti); per i portieri i gol subiti. */
    fun soccerRating(p: SimPlayer): Double {
        val reliability = (p.minutesTotal / 3000.0).coerceAtMost(1.0) * 0.4
        if (isGoalkeeper(p)) return 0.8 + (1.6 - (p.goalsAgainstPer90 ?: 1.3)).coerceIn(0.0, 1.2) + reliability
        return 0.6 + 3 * per90(p.goals, p.minutesTotal) + 2 * per90(p.assistsTotal, p.minutesTotal) + reliability
    }

    fun simulateSoccer(home: SimTeam, away: SimTeam, seed: Long): SimResult {
        val rnd = Random(seed)
        val lineH = soccerLineup(home)
        val lineA = soccerLineup(away)
        val gkH = lineH.firstOrNull(::isGoalkeeper)
        val gkA = lineA.firstOrNull(::isGoalkeeper)
        // Portiere che subisce meno della media (1.3 gol ogni 90') = attacco avversario meno efficace.
        fun keeperFactor(gk: SimPlayer?) = gk?.goalsAgainstPer90?.let { sqrt((it / 1.3).coerceIn(0.5, 2.0)) } ?: 1.0
        val duels = playDuels(lineH, lineA, ::soccerRating, rnd)
        fun averageFactor(p: SimPlayer, opponents: List<SimPlayer>) =
            if (opponents.isEmpty()) 1.0 else opponents.sumOf { sqrt(soccerRating(p) / soccerRating(it)) } / opponents.size
        val events = mutableListOf<SimEvent>()
        fun goals(team: Int, line: List<SimPlayer>, opponents: List<SimPlayer>, keeper: Double, bonus: Double): IntArray {
            val outfield = line.filter { !isGoalkeeper(it) }
            return IntArray(line.size) { i ->
                val p = line[i]
                if (isGoalkeeper(p)) return@IntArray 0
                val lambda = per90(p.goals, p.minutesTotal) * averageFactor(p, opponents) *
                    (0.8 + 0.4 * winRate(p, team, duels)) * keeper * bonus
                val g = poisson(lambda, rnd)
                repeat(g) { events += SimEvent(goalSecond(rnd), team, p.name, 1, soccerAssist(p, outfield, rnd)) }
                g
            }
        }
        val goalsH = goals(0, lineH, lineA, keeperFactor(gkA), 1.08)
        val goalsA = goals(1, lineA, lineH, keeperFactor(gkH), 0.95)
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
        val weights = others.map { per90(it.assistsTotal, it.minutesTotal) + 0.02 }
        if (others.isEmpty() || rnd.nextDouble() > 0.7) return null
        var r = rnd.nextDouble() * weights.sum()
        others.forEachIndexed { i, m -> r -= weights[i]; if (r <= 0) return m.name }
        return others.last().name
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
