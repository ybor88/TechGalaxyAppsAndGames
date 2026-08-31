package com.volcanoescape.app.data.repository

import com.volcanoescape.app.data.model.SeismicEvent
import java.time.OffsetDateTime
import kotlin.math.ln
import kotlin.math.pow

enum class SeismicAlertLevel(val label: String) {
    VERDE("Base"),
    GIALLO("Attenzione"),
    ARANCIONE("Preallarme"),
    ROSSO("Allarme"),
}

enum class SeismicTrend(val label: String) {
    IN_CALO("In calo"),
    STABILE("Stabile"),
    IN_CRESCITA("In crescita"),
}

/**
 * Punto giornaliero usato per disegnare il grafico dell'andamento sismico.
 * [cumulativeEnergy] è in unità relative (non joule), utile solo per il confronto tra giorni.
 */
data class DailyActivityPoint(
    val day: java.time.LocalDate,
    val eventCount: Int,
    val maxMagnitude: Double,
    val cumulativeEnergy: Double,
)

data class SeismicRiskAssessment(
    val level: SeismicAlertLevel,
    val score: Int,
    val trend: SeismicTrend,
    val eventsLast7Days: Int,
    val eventsLast30Days: Int,
    val maxMagnitudeLast7Days: Double,
    val isShallowing: Boolean,
    val dailyActivity: List<DailyActivityPoint>,
)

/**
 * Stima *non ufficiale* di un indice di attività sismica a scopo puramente informativo/didattico,
 * calcolata solo a partire dal bollettino sismico (frequenza, energia, magnitudo, profondità).
 *
 * NON è il livello di allerta ufficiale INGV/Protezione Civile: quello si basa anche su
 * deformazione del suolo, geochimica dei gas, dati termici e valutazione di vulcanologi, dati
 * che qui non sono disponibili. Va sempre usato solo come indicatore di massima, rimandando
 * l'utente al bollettino ufficiale del vulcano per qualunque decisione reale.
 *
 * Principi semplificati applicati:
 * - Frequenza: uno sciame sismico (tasso di eventi in aumento) è un classico segnale di unrest.
 * - Energia rilasciata: stimata con la legge di Gutenberg-Richter (E ~ 10^(1.5M)), in scala
 *   logaritmica per pesare correttamente pochi eventi forti rispetto a molti eventi deboli.
 * - Magnitudo massima recente: un singolo evento forte pesa di più della media.
 * - Approfondimento/risalita: una migrazione degli ipocentri verso profondità minori nel tempo
 *   è un classico indicatore di risalita di magma o fluidi.
 */
object SeismicRiskAnalyzer {

    fun analyze(events: List<SeismicEvent>, now: OffsetDateTime = OffsetDateTime.now()): SeismicRiskAssessment {
        val last7 = events.filter { it.time.isAfter(now.minusDays(7)) }
        val last30 = events.filter { it.time.isAfter(now.minusDays(30)) }
        val previous7 = events.filter { it.time.isAfter(now.minusDays(14)) && it.time.isBefore(now.minusDays(7)) }

        val frequencyScore = frequencyScore(last7.size, previous7.size)
        val energyScore = energyScore(last7)
        val magnitudeScore = magnitudeScore(last7.maxOfOrNull { it.magnitude } ?: 0.0)
        val shallowing = isShallowing(last30)
        val depthScore = if (shallowing) 15 else 0

        val score = (frequencyScore + energyScore + magnitudeScore + depthScore).coerceIn(0, 100)

        return SeismicRiskAssessment(
            level = levelFor(score),
            score = score,
            trend = trendFor(last7.size, previous7.size),
            eventsLast7Days = last7.size,
            eventsLast30Days = last30.size,
            maxMagnitudeLast7Days = last7.maxOfOrNull { it.magnitude } ?: 0.0,
            isShallowing = shallowing,
            dailyActivity = dailyActivity(last30, now),
        )
    }

    private fun frequencyScore(eventsLast7: Int, eventsPrevious7: Int): Int {
        val ratio = when {
            eventsPrevious7 <= 0 && eventsLast7 <= 0 -> 1.0
            eventsPrevious7 <= 0 -> 3.0
            else -> eventsLast7.toDouble() / eventsPrevious7
        }
        val ratioPoints = when {
            ratio >= 2.0 -> 20
            ratio >= 1.5 -> 14
            ratio >= 1.1 -> 8
            else -> 0
        }
        val volumePoints = when {
            eventsLast7 >= 100 -> 10
            eventsLast7 >= 40 -> 6
            eventsLast7 >= 10 -> 3
            else -> 0
        }
        return ratioPoints + volumePoints
    }

    private fun energyScore(eventsLast7: List<SeismicEvent>): Int {
        if (eventsLast7.isEmpty()) return 0
        val totalEnergy = eventsLast7.sumOf { relativeEnergy(it.magnitude) }
        // Scala log-relativa: un solo evento M2.0 (energia relativa 1000) resta vicino a 0 punti,
        // uno sciame equivalente a un M4+ satura il punteggio.
        val magnitudeEquivalent = ln(totalEnergy) / ln(10.0) / 1.5
        val points = ((magnitudeEquivalent - 2.0) * 12).coerceIn(0.0, 30.0)
        return points.toInt()
    }

    private fun relativeEnergy(magnitude: Double): Double = 10.0.pow(1.5 * magnitude)

    private fun magnitudeScore(maxMagnitude: Double): Int = when {
        maxMagnitude >= 4.0 -> 25
        maxMagnitude >= 3.0 -> 16
        maxMagnitude >= 2.0 -> 8
        else -> 0
    }

    private fun isShallowing(eventsLast30: List<SeismicEvent>): Boolean {
        if (eventsLast30.size < 6) return false
        val chronological = eventsLast30.sortedBy { it.time }
        val mid = chronological.size / 2
        val firstHalfAvgDepth = chronological.take(mid).map { it.depthKm }.average()
        val secondHalfAvgDepth = chronological.takeLast(chronological.size - mid).map { it.depthKm }.average()
        return (firstHalfAvgDepth - secondHalfAvgDepth) >= 0.5
    }

    private fun levelFor(score: Int): SeismicAlertLevel = when {
        score >= 75 -> SeismicAlertLevel.ROSSO
        score >= 50 -> SeismicAlertLevel.ARANCIONE
        score >= 25 -> SeismicAlertLevel.GIALLO
        else -> SeismicAlertLevel.VERDE
    }

    private fun trendFor(eventsLast7: Int, eventsPrevious7: Int): SeismicTrend {
        if (eventsPrevious7 <= 0) {
            return if (eventsLast7 <= 0) SeismicTrend.STABILE else SeismicTrend.IN_CRESCITA
        }
        val ratio = eventsLast7.toDouble() / eventsPrevious7
        return when {
            ratio >= 1.2 -> SeismicTrend.IN_CRESCITA
            ratio <= 0.8 -> SeismicTrend.IN_CALO
            else -> SeismicTrend.STABILE
        }
    }

    private fun dailyActivity(events: List<SeismicEvent>, now: OffsetDateTime): List<DailyActivityPoint> {
        val byDay = events.groupBy { it.time.toLocalDate() }
        val today = now.toLocalDate()
        return (29 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            val dayEvents = byDay[day].orEmpty()
            DailyActivityPoint(
                day = day,
                eventCount = dayEvents.size,
                maxMagnitude = dayEvents.maxOfOrNull { it.magnitude } ?: 0.0,
                cumulativeEnergy = dayEvents.sumOf { relativeEnergy(it.magnitude) },
            )
        }
    }
}
