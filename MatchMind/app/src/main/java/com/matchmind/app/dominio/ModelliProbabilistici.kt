package com.matchmind.app.dominio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Le tre fonti di probabilità di MatchMind:
 *  1. MERCATO   — quote reali del bookmaker, convertite in probabilità togliendo il margine (aggio).
 *                 È il predittore singolo più accurato: incorpora informazioni che le statistiche non vedono
 *                 (infortuni, turnover, motivazioni).
 *  2. MODELLO   — statistica delle squadre dalla classifica: Poisson su gol fatti/subiti per il calcio,
 *                 differenza punti per il basket, con vantaggio del campo di casa.
 *  3. ESPERIENZA — (vedi Esperienza.kt) impara dai risultati reali quanto fidarsi di mercato e modello,
 *                 corregge le probabilità troppo ottimiste e misura l'affidabilità di ogni lega.
 *
 * © Roberto Di Flumeri
 */
object ModelliProbabilistici {

    // ── Quote ────────────────────────────────────────────────────────────────

    /** Quota americana ("+145", "-180", "EVEN") → quota decimale. */
    fun decimaleDaAmericana(testo: String?): Double? {
        val t = testo?.trim()?.uppercase() ?: return null
        if (t == "EVEN" || t == "EV" || t == "PK") return 2.0
        val n = t.removePrefix("+").toDoubleOrNull() ?: return null
        return when {
            n >= 100 -> 1 + n / 100.0
            n <= -100 -> 1 + 100.0 / abs(n)
            else -> null // valori fuori scala: quota non affidabile
        }
    }

    fun decimaleDaAmericana(n: Int?): Double? = n?.let { decimaleDaAmericana(it.toString()) }

    /** Margine del bookmaker: somma delle probabilità implicite - 1 (es. 0.05 = 5%). */
    fun margine(q: Quote): Double = 1 / q.casa + (q.pareggio?.let { 1 / it } ?: 0.0) + 1 / q.ospite - 1

    /** Probabilità "pulite" dalle quote: implicite normalizzate per togliere il margine. */
    fun daQuote(q: Quote): Probabilita =
        Probabilita(1 / q.casa, q.pareggio?.let { 1 / it } ?: 0.0, 1 / q.ospite).normalizzata()

    /** Quota equa con margine tipico, usata solo quando il bookmaker non quota la partita. */
    fun quotaStimata(p: Double, margine: Double = 0.06): Double =
        max(1.01, 1.0 / (p * (1 + margine))).let { Math.round(it * 100) / 100.0 }

    // ── Calcio: modello di Poisson ──────────────────────────────────────────

    const val VANTAGGIO_CASA_CALCIO = 1.12
    // prudenza a inizio stagione: ogni squadra parte da alcune partite "fittizie" nella media di lega
    private const val PARTITE_FITTIZIE_CALCIO = 8.0
    private const val PARTITE_FITTIZIE_BASKET = 5.0

    fun mediaGolLega(classifica: Collection<StatSquadra>): Double {
        val partite = classifica.sumOf { it.partite }
        return if (partite < 10) 1.35 else classifica.sumOf { it.fatti } / partite
    }

    /** Gol attesi di casa e ospite. Attacco e difesa sono rapporti rispetto alla media di lega. */
    fun golAttesi(casa: StatSquadra, ospite: StatSquadra, media: Double): Pair<Double, Double> {
        fun forza(gol: Double, partite: Int) = (gol + PARTITE_FITTIZIE_CALCIO * media) / (partite + PARTITE_FITTIZIE_CALCIO) / media
        val attCasa = forza(casa.fatti, casa.partite)
        val difCasa = forza(casa.subiti, casa.partite)
        val attOspite = forza(ospite.fatti, ospite.partite)
        val difOspite = forza(ospite.subiti, ospite.partite)
        val lambdaCasa = media * attCasa * difOspite * VANTAGGIO_CASA_CALCIO
        val lambdaOspite = media * attOspite * difCasa / VANTAGGIO_CASA_CALCIO
        return lambdaCasa to lambdaOspite
    }

    fun poisson(lambda: Double, k: Int): Double {
        var f = 1.0
        for (i in 2..k) f *= i
        return exp(-lambda) * Math.pow(lambda, k.toDouble()) / f
    }

    fun probabilitaCalcio(casa: StatSquadra, ospite: StatSquadra, media: Double): Probabilita {
        val (lc, lo) = golAttesi(casa, ospite, media)
        var p1 = 0.0
        var px = 0.0
        var p2 = 0.0
        for (i in 0..10) for (j in 0..10) {
            val p = poisson(lc, i) * poisson(lo, j)
            when {
                i > j -> p1 += p
                i == j -> px += p
                else -> p2 += p
            }
        }
        return Probabilita(p1, px, p2).normalizzata()
    }

    // ── Calcio: gol, Goal/NoGoal e Under/Over ────────────────────────────────

    const val LINEA_GOL = 2.5

    private fun distribuzione(lambda: Double): DoubleArray = DoubleArray(11) { poisson(lambda, it) }

    /** Probabilità 1X2 e di almeno 3 gol, dati i gol attesi di casa e ospite (Poisson indipendenti). */
    fun esitiDaGol(lambdaCasa: Double, lambdaOspite: Double): Pair<Probabilita, Double> {
        val c = distribuzione(lambdaCasa)
        val o = distribuzione(lambdaOspite)
        var p1 = 0.0; var px = 0.0; var p2 = 0.0; var fino2 = 0.0
        for (i in 0..10) for (j in 0..10) {
            val p = c[i] * o[j]
            when {
                i > j -> p1 += p
                i == j -> px += p
                else -> p2 += p
            }
            if (i + j <= 2) fino2 += p
        }
        val tot = p1 + px + p2
        return Probabilita(p1 / tot, px / tot, p2 / tot) to (1 - fino2 / tot)
    }

    /** Probabilità che segnino entrambe le squadre. */
    fun probabilitaGoal(lambdaCasa: Double, lambdaOspite: Double): Double = (1 - exp(-lambdaCasa)) * (1 - exp(-lambdaOspite))

    /** Probabilità di almeno 3 gol. */
    fun probabilitaOver(lambdaCasa: Double, lambdaOspite: Double): Double = esitiDaGol(lambdaCasa, lambdaOspite).second

    /** Probabilità "pulita" di Over dalle quote Over/Under (senza margine). */
    fun overDaQuote(over: Double, under: Double): Double = (1 / over) / (1 / over + 1 / under)

    /**
     * Gol attesi di casa e ospite coerenti con le probabilità 1X2 ([obiettivo]) e, se quotato, con la probabilità
     * di Over 2,5 del mercato. Se manca l'Over si usa il totale gol atteso [totaleAtteso] come riferimento debole.
     * Ricerca a griglia grossolana e poi fine (veloce anche sul telefono).
     */
    fun golAttesiCoerenti(obiettivo: Probabilita, pOver: Double?, totaleAtteso: Double): Pair<Double, Double> {
        fun errore(lc: Double, lo: Double): Double {
            val (p, over) = esitiDaGol(lc, lo)
            var e = (p.casa - obiettivo.casa).let { it * it } + (p.pareggio - obiettivo.pareggio).let { it * it } +
                (p.ospite - obiettivo.ospite).let { it * it }
            e += if (pOver != null) 2 * (over - pOver).let { it * it } else 0.004 * (lc + lo - totaleAtteso).let { it * it }
            return e
        }
        var migliore = 1.3 to 1.1
        var minimo = Double.MAX_VALUE
        var lc = 0.2
        while (lc <= 4.0) {
            var lo = 0.2
            while (lo <= 4.0) {
                val e = errore(lc, lo)
                if (e < minimo) { minimo = e; migliore = lc to lo }
                lo += 0.1
            }
            lc += 0.1
        }
        val (c0, o0) = migliore
        for (i in -5..5) for (j in -5..5) {
            val c = (c0 + i * 0.02).coerceAtLeast(0.05)
            val o = (o0 + j * 0.02).coerceAtLeast(0.05)
            val e = errore(c, o)
            if (e < minimo) { minimo = e; migliore = c to o }
        }
        return migliore
    }

    /** Quota di doppia chance ricavata dalle quote reali dei due esiti (mantiene il margine del bookmaker). */
    fun quotaDerivata(a: Double, b: Double): Double = Math.round(100.0 / (1 / a + 1 / b)) / 100.0

    // ── Basket: differenza punti ─────────────────────────────────────────────

    const val VANTAGGIO_CASA_BASKET = 2.5 // punti
    const val DEVIAZIONE_MARGINE_BASKET = 12.5

    /** Forza = differenza punti media per partita, ridotta se le partite sono poche. */
    fun forzaBasket(s: StatSquadra): Double {
        if (s.partite == 0) return 0.0
        val diff = (s.fatti - s.subiti) / s.partite
        return diff * s.partite / (s.partite + PARTITE_FITTIZIE_BASKET)
    }

    fun probabilitaBasket(casa: StatSquadra, ospite: StatSquadra): Probabilita {
        val margine = forzaBasket(casa) - forzaBasket(ospite) + VANTAGGIO_CASA_BASKET
        val p = normaleCumulata(margine / DEVIAZIONE_MARGINE_BASKET)
        return Probabilita(p, 0.0, 1 - p)
    }

    /** Funzione di ripartizione della normale standard (approssimazione di Abramowitz-Stegun). */
    fun normaleCumulata(x: Double): Double {
        val t = 1 / (1 + 0.2316419 * abs(x))
        val d = 0.3989423 * exp(-x * x / 2)
        val p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))))
        return if (x >= 0) 1 - p else p
    }

    /**
     * Inizio stagione: alla classifica in corso si aggiunge la stagione precedente "compressa" in
     * [partitePrecedenti] partite equivalenti. Più partite si giocano, meno pesa il passato.
     * Le squadre senza storico precedente restano invariate.
     */
    fun unisciStagioni(corrente: Map<String, StatSquadra>, precedente: Map<String, StatSquadra>, partitePrecedenti: Int = 10): Map<String, StatSquadra> {
        val ids = corrente.keys + precedente.keys
        return ids.associateWith { id ->
            val c = corrente[id]
            val p = precedente[id]?.takeIf { it.partite > 0 }
            when {
                p == null -> c!!
                else -> {
                    val f = partitePrecedenti.toDouble() / p.partite
                    StatSquadra(
                        id = id, nome = c?.nome ?: p.nome,
                        partite = (c?.partite ?: 0) + partitePrecedenti,
                        vinte = (c?.vinte ?: 0) + Math.round(p.vinte * f).toInt(),
                        pareggi = (c?.pareggi ?: 0) + Math.round(p.pareggi * f).toInt(),
                        perse = (c?.perse ?: 0) + Math.round(p.perse * f).toInt(),
                        fatti = (c?.fatti ?: 0.0) + p.fatti * f,
                        subiti = (c?.subiti ?: 0.0) + p.subiti * f,
                    )
                }
            }
        }
    }

    // ── Combinazione ─────────────────────────────────────────────────────────

    /** Media pesata di mercato e modello; se manca una fonte si usa l'altra. */
    fun combina(mercato: Probabilita?, modello: Probabilita?, pesoMercato: Double): Probabilita? = when {
        mercato != null && modello != null -> Probabilita(
            pesoMercato * mercato.casa + (1 - pesoMercato) * modello.casa,
            pesoMercato * mercato.pareggio + (1 - pesoMercato) * modello.pareggio,
            pesoMercato * mercato.ospite + (1 - pesoMercato) * modello.ospite,
        ).normalizzata()
        mercato != null -> mercato
        else -> modello
    }

    /** Log-loss dell'esito reale: misura quanto una previsione è stata buona (più basso = meglio). */
    fun logLoss(p: Probabilita, esito: Esito): Double = -ln(p.di(esito).coerceIn(1e-6, 1.0))

    /** Brier score di una singola probabilità rispetto all'esito (0 = perfetto). */
    fun brier(p: Double, vero: Boolean): Double = (p - if (vero) 1.0 else 0.0).let { it * it }

    fun erroreStandard(p: Double, n: Int) = if (n == 0) 1.0 else sqrt(p * (1 - p) / n)
}
