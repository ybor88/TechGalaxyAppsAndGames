// Copyright © Roberto Di Flumeri
package com.scouttable.app

import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.lookup.WikipediaItCareerStats
import com.scouttable.app.data.lookup.isPortiere
import com.scouttable.app.data.ranking.PlayerScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ruoli da it.wikipedia (portieri, ex giocatori diventati allenatori) e classifica portieri. */
class RoleParsingTest {

    @Test
    fun coach_exGoalkeeper_keepsPlayingRole() {
        // Campo Ruolo reale di Walter Zenga.
        val ruolo = WikipediaItCareerStats.playingRole("[[Allenatore]] <small>(ex [[Portiere (calcio)|portiere]])</Small>")
        assertEquals("Portiere", ruolo)
        assertTrue(isPortiere(ruolo))
    }

    @Test
    fun coach_exMidfielder_keepsPlayingRole() {
        assertEquals("Centrocampista", WikipediaItCareerStats.playingRole("[[Allenatore]] <small>(ex [[centrocampista]])</small>"))
        assertEquals("Attaccante", WikipediaItCareerStats.playingRole("[[Allenatore]] <small>(ex [[Attaccante (calcio)|attaccante]])</small>"))
    }

    @Test
    fun plainRole_isUnchanged() {
        assertEquals("Portiere", WikipediaItCareerStats.playingRole("[[Portiere (calcio)|Portiere]]"))
        assertEquals("Portiere", WikipediaItCareerStats.playingRole("[[portiere (sport)|Portiere]]"))
    }

    @Test
    fun goalkeeper_withoutGoalsConceded_isNotTopOfRanking() {
        val base = Player(id = "1", sport = Sport.CALCIO, nome = "Test", anno = 1990, carrieraMigliore = "", stato = "",
            nazione = "", logoPath = null, ruolo = "Portiere", presenze = 300, updatedAt = 0L)
        assertTrue(PlayerScoring.missingGoalsConceded(base))
        assertEquals(0, PlayerScoring.goalkeeperScore(base))
        val withData = base.copy(golSubiti = 150)
        assertFalse(PlayerScoring.missingGoalsConceded(withData))
        assertEquals(50, PlayerScoring.goalkeeperScore(withData))
    }
}
