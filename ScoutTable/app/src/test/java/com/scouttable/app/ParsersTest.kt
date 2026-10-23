// Copyright © Roberto Di Flumeri
package com.scouttable.app

import com.scouttable.app.data.lookup.PlaybasketStats
import com.scouttable.app.data.lookup.RealGmCareerStats
import com.scouttable.app.data.nbaintl.NbaIntlParser
import com.scouttable.app.data.simulation.TeamSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parser delle fonti verificati sulle pagine reali salvate in src/test/resources/fixtures. */
class ParsersTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource("fixtures/$name")!!.readText(Charsets.UTF_8)

    @Test
    fun realGm_ncaaPlayerWithSchoolColumn_isFound() {
        // Bug segnalato: "non trova il giocatore" per un profilo con sole stagioni NCAA.
        val url = "https://basketball.realgm.com/player/Stephen-Behil/Summary/209470"
        val t = RealGmCareerStats.parse(fixture("realgm_behil.html"), url)
        assertNotNull(t)
        t!!
        // 27 + 25 + ... stagioni a Whitworth: totale dalla riga CAREER del sito per le prime due = 52.
        assertTrue("presenze ${t.presenze}", t.presenze >= 52)
        assertTrue(t.punteggio > 0)
        assertEquals("Whitworth University", t.squadraPrincipale)
        assertEquals("NCAA DIII", t.competizione)
        assertEquals(2026, t.ultimaStagione)
    }

    @Test
    fun proballersTeam_readsAveragesTable() {
        val url = "https://www.proballers.com/it/pallacanestro/squadra/101/boston-celtics"
        val team = TeamSources.parse(url, fixture("proballers_celtics.html"))
        assertNotNull(team)
        team!!
        assertEquals("Boston Celtics", team.name)
        assertEquals("https://www.proballers.com/media/team/101.svg", team.logoUrl)
        val george = team.players.first { it.name == "Paul George" }
        assertEquals(38.0, george.points, 0.01)
        assertEquals(5.0, george.rebounds, 0.01)
        assertEquals(3.0, george.assists, 0.01)
        assertEquals(31.0, george.minutes, 0.01)
        assertEquals(38.0, george.eff!!, 0.01)
        assertTrue(team.players.size >= 10)
    }

    @Test
    fun basketballReferenceTeam_readsPerGameTable() {
        val url = "https://www.basketball-reference.com/teams/CHI/2025.html"
        val team = TeamSources.parse(url, fixture("bref_chi_2025.html"))
        assertNotNull(team)
        team!!
        assertEquals("Chicago Bulls", team.name)
        assertTrue(team.logoUrl!!.endsWith("CHI-2025.png"))
        val lavine = team.players.first { it.name == "Zach LaVine" }
        assertEquals(24.0, lavine.points, 0.01)
        assertEquals(34.1, lavine.minutes, 0.01)
        assertEquals(0.446, lavine.threePct, 0.001)
        assertTrue(team.players.none { it.name == "Team Totals" })
    }

    @Test
    fun transfermarktTeam_readsDetailedPerformance() {
        assertEquals(
            "https://www.transfermarkt.it/atalanta-bergamo/leistungsdaten/verein/800/plus/1",
            TeamSources.transfermarktStatsUrl("https://www.transfermarkt.it/atalanta-bergamo/startseite/verein/800"),
        )
        assertEquals(
            "https://www.transfermarkt.com/atalanta-bc/leistungsdaten/verein/800/reldata/%262025/plus/1",
            TeamSources.transfermarktStatsUrl("https://www.transfermarkt.com/atalanta-bc/kader/verein/800/saison_id/2025"),
        )
        val url = "https://www.transfermarkt.it/atalanta-bergamo/leistungsdaten/verein/800/plus/1"
        val team = TeamSources.parse(url, fixture("transfermarkt_atalanta.html"))
        assertNotNull(team)
        team!!
        assertEquals("Atalanta", team.name)
        assertTrue(team.logoUrl!!.contains("wappen/head/800"))
        val keeper = team.players.first { it.name == "Marco Carnesecchi" }
        assertEquals("GK", keeper.position)
        assertEquals(11, keeper.games)
        assertEquals(990, keeper.minutesTotal)
        val bellanova = team.players.first { it.name == "Raoul Bellanova" }
        assertEquals("DF", bellanova.position)
        assertEquals(1, bellanova.assistsTotal)
        assertEquals(709, bellanova.minutesTotal)
        // Chi non ha mai giocato non entra in squadra.
        assertTrue(team.players.none { it.name == "Paolo Vismara" })
        assertTrue(team.players.size >= 15)
    }

    @Test
    fun playbasket_profileAndAllSeasons() {
        val url = "https://www.playbasket.it/campania/profile.php?season=2027&subj=1&exteid=4&extobj=14632&obj=204567&action=view&eid=5"
        val t = PlaybasketStats.parse(fixture("playbasket_profile.html"), fixture("playbasket_seasons.html"), url)
        assertNotNull(t)
        t!!
        assertEquals("Lucja Grzenkowicz", t.nome)
        assertEquals(2000, t.annoNascita) // 26 anni nella stagione 2026-27
        assertEquals("Centro", t.ruolo)
        // 26/27 Benevento (5 pres, 76 pt) + 24/25 Bari (22 pres, 233 pt).
        assertEquals(27, t.presenze)
        assertEquals(309, t.punti)
        assertEquals("Pink Sport Time Bari", t.squadraPrincipale)
        assertEquals("Serie B", t.competizione)
        assertEquals(2027, t.ultimaStagione)
        val row = PlaybasketStats.toImportRow(t, null, url)
        assertEquals(11, row.effMedio) // 309 / 27
    }

    @Test
    fun playbasket_withoutSeasonsPage_usesOpenSeason() {
        val url = "https://www.playbasket.it/campania/profile.php?season=2027&subj=1&exteid=4&extobj=14632&obj=204567&action=view&eid=5"
        val t = PlaybasketStats.parse(fixture("playbasket_profile.html"), null, url)!!
        assertEquals(5, t.presenze)
        assertEquals(76, t.punti)
        assertEquals("Serie B Femminile Campania", t.competizione)
        assertTrue(t.logoUrl!!.startsWith("https://www.playbasket.it/images/club/"))
    }

    @Test
    fun playbasket_allSeasonsUrl() {
        val url = "https://www.playbasket.it/campania/profile.php?season=2027&obj=204567&action=view&eid=5"
        assertEquals("$url&mod=ws", PlaybasketStats.allSeasonsUrl(url))
    }

    @Test
    fun nbaInternational_parsesMatchesAndRecords() {
        val parsed = NbaIntlParser.parse(fixture("nba_international.wiki"))
        assertTrue("partite ${parsed.matches.size}", parsed.matches.size >= 120)
        val first = parsed.matches.first()
        assertEquals("Washington Bullets", first.nba.name)
        assertEquals("Maccabi Elite Tel Aviv", first.intl.name)
        assertEquals("Maccabi Tel Aviv B.C.", first.intl.wikiTitle)
        assertEquals(97, first.nbaScore)
        assertEquals(98, first.intlScore)
        assertEquals(1978, first.year)
        val suns = parsed.records.first { it.isNba && it.team.name == "Phoenix Suns" }
        assertEquals(15, suns.games)
        assertEquals(13, suns.wins)
        assertEquals(suns, parsed.records.filter { it.isNba }.maxBy { it.wins })
        val adelaide = parsed.records.first { !it.isNba && it.team.name == "Adelaide 36ers" }
        assertEquals(1, adelaide.wins)
        assertEquals("Australia", adelaide.team.country)
        // Una partita del 2022 scritta tutta su una riga, con i parametri in ordine diverso.
        assertTrue(parsed.matches.any { it.intl.name == "Adelaide 36ers" && it.intlScore == 134 && it.nbaScore == 124 })
    }
}
