// Copyright © Roberto Di Flumeri
package com.scouttable.app

import com.scouttable.app.data.simulation.MatchSimulator
import com.scouttable.app.data.simulation.SimPlayer
import com.scouttable.app.data.simulation.SimTeam
import com.scouttable.app.data.simulation.SoccerRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchSimulatorTest {

    private fun basketTeam(name: String, ppg: Double) = SimTeam(
        name, null, "url",
        (1..10).map { i -> SimPlayer("$name P$i", minutes = 34.0 - i * 2, points = ppg - i, rebounds = 5.0, assists = 3.0) },
    )

    private fun soccerTeam(name: String, goals: Int) = SimTeam(
        name, null, "url",
        listOf(SimPlayer("$name GK", position = "GK", minutesTotal = 3000, games = 34, goalsAgainstPer90 = 1.2)) +
            (1..12).map { i -> SimPlayer("$name P$i", position = listOf("DF", "MF", "FW")[i % 3], minutesTotal = 3000 - i * 100, games = 30, goals = goals - i / 2, assistsTotal = 3) },
    )

    @Test
    fun basket_scoresMatchEventsAndNoTie() {
        repeat(30) { seed ->
            val r = MatchSimulator.simulateBasket(basketTeam("A", 20.0), basketTeam("B", 20.0), seed.toLong())
            assertNotEquals(r.homeScore, r.awayScore)
            assertEquals(r.homeScore, r.events.filter { it.team == 0 }.sumOf { it.points })
            assertEquals(r.awayScore, r.events.filter { it.team == 1 }.sumOf { it.points })
            assertEquals(r.homeScore, r.lines.filter { it.team == 0 }.sumOf { it.points })
            assertTrue(r.events.all { it.second in 0 until r.totalSeconds })
            assertTrue("punteggio realistico ${r.homeScore}", r.homeScore in 60..180)
            assertEquals(5, r.homeLineup.size)
            // Ognuno contro tutti: 10 × 10 duelli, e ogni giocatore ne disputa 10.
            assertEquals(100, r.duels.size)
            assertTrue(r.lines.all { it.duelsPlayed == 10 })
            assertEquals(100, r.homeDuelsWon + r.awayDuelsWon)
        }
    }

    @Test
    fun basket_sameSeedSameResult() {
        val a = MatchSimulator.simulateBasket(basketTeam("A", 20.0), basketTeam("B", 18.0), 42)
        val b = MatchSimulator.simulateBasket(basketTeam("A", 20.0), basketTeam("B", 18.0), 42)
        assertEquals(a, b)
    }

    @Test
    fun basket_strongerTeamWinsMostOfTheTime() {
        val wins = (0 until 200).count { seed ->
            val r = MatchSimulator.simulateBasket(basketTeam("Forte", 24.0), basketTeam("Debole", 14.0), seed.toLong())
            r.homeScore > r.awayScore
        }
        assertTrue("vittorie $wins su 200", wins > 160)
    }

    @Test
    fun soccer_lineupAndGoals() {
        repeat(30) { seed ->
            val r = MatchSimulator.simulateSoccer(soccerTeam("A", 8), soccerTeam("B", 8), seed.toLong())
            assertEquals(11, r.homeLineup.size)
            assertEquals("A GK", r.homeLineup.first().name)
            assertEquals(r.homeScore, r.events.count { it.team == 0 })
            assertTrue(r.events.all { it.second in 0 until r.totalSeconds })
            assertTrue("gol ${r.homeScore}", r.homeScore in 0..10)
            // Duelli solo nello stesso reparto, 4-4-2: 1×1 portieri + 4×4 difensori + 4×4 centrocampisti + 2×2 attaccanti.
            assertEquals(37, r.duels.size)
            assertTrue(r.duels.all { MatchSimulator.soccerRole(it.home) == MatchSimulator.soccerRole(it.away) })
            val expected = mapOf(SoccerRole.PORTIERE to 1, SoccerRole.DIFENSORE to 4, SoccerRole.CENTROCAMPISTA to 4, SoccerRole.ATTACCANTE to 2)
            assertTrue(r.lines.all { it.duelsPlayed == expected.getValue(MatchSimulator.soccerRole(it.player)) })
            assertEquals(listOf(1, 4, 4, 2), SoccerRole.entries.map { role -> r.homeLineup.count { MatchSimulator.soccerRole(it) == role } })
        }
        val avg = (0 until 300).map { MatchSimulator.simulateSoccer(soccerTeam("A", 8), soccerTeam("B", 8), it.toLong()) }
            .map { it.homeScore + it.awayScore }.average()
        assertTrue("media gol $avg", avg in 1.0..6.0)
    }
}
