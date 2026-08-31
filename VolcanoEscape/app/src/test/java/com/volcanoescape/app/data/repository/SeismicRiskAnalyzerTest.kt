package com.volcanoescape.app.data.repository

import com.volcanoescape.app.data.model.SeismicEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SeismicRiskAnalyzerTest {

    private val now = OffsetDateTime.of(2026, 8, 31, 12, 0, 0, 0, ZoneOffset.UTC)

    private fun event(
        daysAgo: Long,
        magnitude: Double,
        depthKm: Double = 5.0,
        id: String = "$daysAgo-$magnitude-$depthKm",
    ) = SeismicEvent(
        eventId = id,
        time = now.minusDays(daysAgo),
        latitude = 40.82,
        longitude = 14.42,
        depthKm = depthKm,
        magnitude = magnitude,
        magnitudeType = "Md",
        locationName = "Vesuvio",
    )

    @Test
    fun `no events means minimum risk and no data to show`() {
        val assessment = SeismicRiskAnalyzer.analyze(emptyList(), now)

        assertEquals(0, assessment.score)
        assertEquals(SeismicAlertLevel.VERDE, assessment.level)
        assertEquals(SeismicTrend.STABILE, assessment.trend)
        assertEquals(0, assessment.eventsLast7Days)
        assertEquals(30, assessment.dailyActivity.size)
    }

    @Test
    fun `a quiet steady baseline stays green`() {
        // Un paio di scosse deboli a settimana, stabili nel tempo.
        val events = (0..27 step 4).map { event(daysAgo = it.toLong(), magnitude = 1.0) }

        val assessment = SeismicRiskAnalyzer.analyze(events, now)

        assertEquals(SeismicAlertLevel.VERDE, assessment.level)
    }

    @Test
    fun `a growing swarm raises the score and is flagged as increasing`() {
        val quietPreviousWeek = (8..13).map { event(daysAgo = it.toLong(), magnitude = 1.0) }
        val busyLastWeek = (0..6).flatMap { day ->
            (1..5).map { n -> event(daysAgo = day.toLong(), magnitude = 1.2, id = "swarm-$day-$n") }
        }

        val assessment = SeismicRiskAnalyzer.analyze(quietPreviousWeek + busyLastWeek, now)

        assertEquals(SeismicTrend.IN_CRESCITA, assessment.trend)
        assertTrue("expected score to reflect the swarm, was ${assessment.score}", assessment.score > 0)
    }

    @Test
    fun `a single strong recent earthquake pushes the level up`() {
        val events = listOf(event(daysAgo = 1, magnitude = 4.5))

        val assessment = SeismicRiskAnalyzer.analyze(events, now)

        assertEquals(4.5, assessment.maxMagnitudeLast7Days, 0.0001)
        assertTrue(assessment.level == SeismicAlertLevel.GIALLO || assessment.level == SeismicAlertLevel.ARANCIONE)
    }

    @Test
    fun `hypocenters migrating shallower over the last 30 days are detected`() {
        // Prima metà del periodo: scosse profonde. Seconda metà: scosse via via più superficiali.
        val deepEarlier = (16..29).map { event(daysAgo = it.toLong(), magnitude = 1.5, depthKm = 8.0) }
        val shallowRecent = (0..15).map { event(daysAgo = it.toLong(), magnitude = 1.5, depthKm = 1.0) }

        val assessment = SeismicRiskAnalyzer.analyze(deepEarlier + shallowRecent, now)

        assertTrue(assessment.isShallowing)
    }

    @Test
    fun `stable depth over time is not flagged as shallowing`() {
        val events = (0..29).map { event(daysAgo = it.toLong(), magnitude = 1.5, depthKm = 5.0) }

        val assessment = SeismicRiskAnalyzer.analyze(events, now)

        assertFalse(assessment.isShallowing)
    }

    @Test
    fun `daily activity covers exactly the last 30 days ending today and counts events per day`() {
        val events = listOf(event(daysAgo = 0, magnitude = 2.0), event(daysAgo = 0, magnitude = 1.0, id = "second-today"))

        val assessment = SeismicRiskAnalyzer.analyze(events, now)

        assertEquals(30, assessment.dailyActivity.size)
        assertEquals(now.toLocalDate(), assessment.dailyActivity.last().day)
        assertEquals(now.toLocalDate().minusDays(29), assessment.dailyActivity.first().day)
        assertEquals(2, assessment.dailyActivity.last().eventCount)
        assertEquals(2.0, assessment.dailyActivity.last().maxMagnitude, 0.0001)
    }

    @Test
    fun `score never exceeds the 0 to 100 range even for an extreme swarm`() {
        val extremeSwarm = (0..6).flatMap { day ->
            (1..50).map { n -> event(daysAgo = day.toLong(), magnitude = 5.5, depthKm = 0.2, id = "extreme-$day-$n") }
        }

        val assessment = SeismicRiskAnalyzer.analyze(extremeSwarm, now)

        assertTrue(assessment.score in 0..100)
        assertEquals(SeismicAlertLevel.ROSSO, assessment.level)
    }
}
