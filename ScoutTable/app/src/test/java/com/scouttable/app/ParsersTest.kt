// Copyright © Roberto Di Flumeri
package com.scouttable.app

import com.scouttable.app.data.lookup.PlaybasketStats
import com.scouttable.app.data.lookup.RealGmCareerStats
import com.scouttable.app.data.nbaintl.NbaIntlParser
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
