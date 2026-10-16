package com.matchmind.app.dominio

import kotlin.math.max

/**
 * Apprendimento dall'esperienza.
 *
 * Ogni partita analizzata (non solo quelle giocate in schedina) diventa un'OSSERVAZIONE con le
 * probabilità previste da mercato e modello; quando arriva il risultato reale l'osservazione si chiude.
 * Da questo storico l'app impara, separatamente per calcio e basket:
 *
 *  1. PESO DEL MERCATO — quanto fidarsi delle quote rispetto alle statistiche: si sceglie il peso che
 *     avrebbe previsto meglio i risultati già visti (minima log-loss). Con pochi dati resta vicino a 0.75.
 *  2. CALIBRAZIONE PER TIPO DI PRONOSTICO — per vincente, pareggio, doppia chance, Goal/NoGoal e Under/Over
 *     confronta, a fasce di probabilità, quanto spesso il pronostico si è avverato davvero e corregge le
 *     previsioni troppo ottimiste o pessimiste.
 *  3. AFFIDABILITÀ DELLE LEGHE — leghe dove le favorite vincono meno del previsto vengono penalizzate nella
 *     scelta, quelle più prevedibili premiate.
 *
 * Tutte le correzioni sono "prudenti" (stimatori bayesiani): pesano poco finché i dati sono pochi.
 *
 * © Roberto Di Flumeri
 */
data class Osservazione(
    val eventoId: String,
    val sport: Sport,
    val lega: String,
    val mercato: Probabilita?,
    val modello: Probabilita?,
    val esito: Esito,
    val pGoal: Double? = null,
    val pOver: Double? = null,
    val golCasa: Int? = null,
    val golOspite: Int? = null,
)

data class Fascia(val da: Double, val a: Double, val n: Int, val prevista: Double, val reale: Double) {
    val etichetta get() = "${(da * 100).toInt()}–${(a * 100).toInt()}%"
}

/** Come si sono comportati i pronostici più probabili di un tipo: quanto previsto e quanto avvenuto. */
data class RiepilogoMercato(val mercato: Mercato, val n: Int, val prevista: Double, val reale: Double)

data class AffidabilitaLega(val lega: String, val n: Int, val previste: Double, val vinte: Int, val fattore: Double)

class Esperienza(
    val sport: Sport,
    val pesoMercato: Double,
    val fasce: Map<Mercato, List<Fascia>>,
    val riepilogo: List<RiepilogoMercato>,
    val leghe: Map<String, AffidabilitaLega>,
    val osservazioni: Int,
    /** Quota di favorite (1 o 2) che hanno vinto davvero. */
    val precisione: Double,
    val brierMercato: Double?,
    val brierCombinato: Double?,
) {
    /** Probabilità corretta con la calibrazione appresa per quel tipo di pronostico. */
    fun calibra(p: Double, mercato: Mercato = Mercato.VINCENTE): Double {
        val f = fasce[mercato]?.firstOrNull { p >= it.da && p < it.a } ?: return p
        if (f.n == 0) return p
        val corretta = (f.reale * f.n + f.prevista * K_CALIBRAZIONE) / (f.n + K_CALIBRAZIONE)
        return (p + (corretta - f.prevista)).coerceIn(0.01, 0.99)
    }

    fun affidabilita(lega: String): Double = leghe[lega]?.fattore ?: 1.0

    companion object {
        const val PESO_MERCATO_INIZIALE = 0.75
        const val MINIMO_PER_IMPARARE = 30
        private const val K_PESO = 100.0
        private const val K_CALIBRAZIONE = 25.0
        private const val K_LEGA = 10.0
        /** Fasce di probabilità da 10 punti percentuali. */
        val LIMITI_FASCE = (0..10).map { it / 10.0 }.toMutableList().also { it[10] = 1.0001 }

        fun vuota(sport: Sport) = Esperienza(sport, PESO_MERCATO_INIZIALE, emptyMap(), emptyList(), emptyMap(), 0, 0.0, null, null)

        /** La vincente più probabile tra 1 e 2 (usata per l'affidabilità delle leghe). */
        fun favorito(p: Probabilita): Esito = if (p.casa >= p.ospite) Esito.CASA else Esito.OSPITE

        private data class Punto(val mercato: Mercato, val p: Double, val vero: Boolean)

        /** Tutte le previsioni verificabili di un'osservazione, per tipo di pronostico. */
        private fun punti(o: Osservazione, c: Probabilita): List<Punto> = buildList {
            val e = o.esito
            add(Punto(Mercato.VINCENTE, c.casa, e == Esito.CASA))
            add(Punto(Mercato.VINCENTE, c.ospite, e == Esito.OSPITE))
            if (o.sport == Sport.CALCIO) {
                add(Punto(Mercato.PAREGGIO, c.pareggio, e == Esito.PAREGGIO))
                add(Punto(Mercato.DOPPIA_CHANCE, c.casa + c.pareggio, e != Esito.OSPITE))
                add(Punto(Mercato.DOPPIA_CHANCE, c.pareggio + c.ospite, e != Esito.CASA))
                add(Punto(Mercato.DOPPIA_CHANCE, c.casa + c.ospite, e != Esito.PAREGGIO))
                if (o.golCasa != null && o.golOspite != null) {
                    o.pGoal?.let { pg ->
                        val gg = Scommessa.GOAL.vinta(o.golCasa, o.golOspite)
                        add(Punto(Mercato.GOAL, pg, gg)); add(Punto(Mercato.GOAL, 1 - pg, !gg))
                    }
                    o.pOver?.let { po ->
                        val over = Scommessa.OVER.vinta(o.golCasa, o.golOspite)
                        add(Punto(Mercato.UNDER_OVER, po, over)); add(Punto(Mercato.UNDER_OVER, 1 - po, !over))
                    }
                }
            }
        }

        fun impara(sport: Sport, tutte: List<Osservazione>): Esperienza {
            val oss = tutte.filter { it.sport == sport && (it.mercato != null || it.modello != null) }
            if (oss.isEmpty()) return vuota(sport)

            // 1. peso del mercato
            val entrambe = oss.filter { it.mercato != null && it.modello != null }
            var peso = PESO_MERCATO_INIZIALE
            if (entrambe.size >= MINIMO_PER_IMPARARE) {
                var migliore = PESO_MERCATO_INIZIALE
                var minimo = Double.MAX_VALUE
                for (passo in 0..20) {
                    val w = passo / 20.0
                    val loss = entrambe.sumOf { ModelliProbabilistici.logLoss(ModelliProbabilistici.combina(it.mercato, it.modello, w)!!, it.esito) }
                    if (loss < minimo - 1e-9) { minimo = loss; migliore = w }
                }
                val n = entrambe.size
                peso = (migliore * n + PESO_MERCATO_INIZIALE * K_PESO) / (n + K_PESO)
            }

            val combinate = oss.map { it to ModelliProbabilistici.combina(it.mercato, it.modello, peso)!! }

            // 2. calibrazione per tipo di pronostico, su tutte le previsioni
            val tuttiPunti = combinate.flatMap { (o, c) -> punti(o, c) }
            val fasce = tuttiPunti.groupBy { it.mercato }.mapValues { (_, lista) ->
                LIMITI_FASCE.zipWithNext().map { (da, a) ->
                    val dentro = lista.filter { it.p >= da && it.p < a }
                    Fascia(da, minOf(a, 1.0), dentro.size,
                        if (dentro.isEmpty()) (da + minOf(a, 1.0)) / 2 else dentro.map { it.p }.average(),
                        if (dentro.isEmpty()) 0.0 else dentro.count { it.vero }.toDouble() / dentro.size)
                }
            }
            // riepilogo: per ogni partita il pronostico più probabile di ciascun tipo
            val riepilogo = Mercato.entries.mapNotNull { m ->
                val migliori = combinate.mapNotNull { (o, c) -> punti(o, c).filter { it.mercato == m }.maxByOrNull { it.p } }
                if (migliori.isEmpty()) null
                else RiepilogoMercato(m, migliori.size, migliori.map { it.p }.average(), migliori.count { it.vero }.toDouble() / migliori.size)
            }

            // 3. affidabilità delle leghe: vittorie reali delle favorite rispetto a quelle previste
            data class Fav(val lega: String, val p: Double, val vinta: Boolean)
            val favorite = combinate.map { (o, c) -> val f = favorito(c); Fav(o.lega, c.di(f), o.esito == f) }
            val leghe = favorite.groupBy { it.lega }.mapValues { (lega, lp) ->
                val previste = lp.sumOf { it.p }
                val vinte = lp.count { it.vinta }
                AffidabilitaLega(lega, lp.size, previste, vinte, ((vinte + K_LEGA) / (previste + K_LEGA)).coerceIn(0.85, 1.15))
            }

            val brierMercato = oss.filter { it.mercato != null }.takeIf { it.isNotEmpty() }?.map {
                val fav = favorito(it.mercato!!)
                ModelliProbabilistici.brier(it.mercato.di(fav), it.esito == fav)
            }?.average()
            val brierCombinato = favorite.map { ModelliProbabilistici.brier(it.p, it.vinta) }.average()

            return Esperienza(sport, peso, fasce, riepilogo, leghe, oss.size,
                favorite.count { it.vinta }.toDouble() / max(1, favorite.size), brierMercato, brierCombinato)
        }
    }
}
