// Copyright © Roberto Di Flumeri
package com.scouttable.app

import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.athlete.AthleteData
import com.scouttable.app.data.athlete.AthleteMetric
import com.scouttable.app.data.athlete.AthleteScoring
import com.scouttable.app.data.athlete.TeamRole
import com.scouttable.app.data.athlete.TeamRoles
import com.scouttable.app.data.athlete.TeamSportStats
import com.scouttable.app.data.athlete.WikipediaAthleteStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Football americano, Rugby e Hockey: parser delle voci Wikipedia (wikitext reale in test/resources/wiki) e classifiche. */
class TeamSportStatsTest {

    private fun wikitext(name: String): String =
        javaClass.classLoader!!.getResource("wiki/$name.txt")!!.readText()

    private fun found(sport: Sport, page: String): AthleteData {
        val result = WikipediaAthleteStats.parse(sport, wikitext(page), page.replace('_', ' '))
        assertTrue("$page: $result", result is WikipediaAthleteStats.Result.Found)
        return (result as WikipediaAthleteStats.Result.Found).data
    }

    @Test
    fun parisse_rugbyClubAndNationalTeam() {
        val d = found(Sport.RUGBY, "Sergio_Parisse")
        assertEquals("Sergio Parisse", d.nome)
        assertEquals("Italy", d.nazione)
        assertEquals("Terza linea centro", d.ruolo)
        assertEquals(47 + 265 + 65, d.partite)
        assertEquals(65 + 238 + 45, d.segnati)
        assertEquals(142, d.partiteNaz)
        assertEquals(83, d.segnatiNaz)
        assertEquals("Stade Français", d.team)
        assertEquals("2002-2023", d.stagioni)
        assertTrue(d.retired)
    }

    @Test
    fun wilkinson_rugbyNestedTemplatesAndLions() {
        val d = found(Sport.RUGBY, "Jonny_Wilkinson")
        assertEquals("Mediano d'apertura", d.ruolo)
        assertEquals(139 + 104, d.partite)
        assertEquals(1489 + 1425, d.segnati)
        assertEquals("Newcastle", d.team)
        // Solo l'Inghilterra, non i British Lions (seconda riga).
        assertEquals(91, d.partiteNaz)
        assertEquals(1179, d.segnatiNaz)
    }

    @Test
    fun gretzky_hockeyCareerAndSeniorNationalTeam() {
        val d = found(Sport.HOCKEY, "Wayne_Gretzky")
        assertEquals("Centro", d.ruolo)
        assertEquals("Canada", d.nazione)
        assertEquals("Edmonton", d.team)
        assertEquals(8 + 901 + 599 + 31 + 249, d.partite)
        assertEquals(3 + 723 + 272 + 10 + 67, d.segnati)
        assertEquals(3 + 1371 + 737 + 27 + 202, d.assistTeam)
        // Nazionale maggiore {{Naz|HG|CAN|M|}}, non l'Under 20 {{NazU|...}}.
        assertEquals(63, d.partiteNaz)
        assertEquals(31, d.segnatiNaz)
        assertEquals(65, d.assistNaz)
        assertTrue(d.retired)
    }

    @Test
    fun crosby_activeHockeyPlayerWithTitles() {
        val d = found(Sport.HOCKEY, "Sidney_Crosby")
        assertEquals("Pittsburgh", d.team)
        assertEquals(1141, d.partite)
        assertEquals(536, d.segnati)
        assertEquals(916, d.assistTeam)
        assertEquals(16, d.partiteNaz)
        assertFalse(d.retired)
        assertEquals("2005-", d.stagioni)
        // 2 ori olimpici + 1 mondiale + 3 Stanley Cup; l'oro U20 non conta.
        assertEquals(6, d.titoli)
    }

    @Test
    fun brady_footballFromItalianAndEnglishPages() {
        val it = found(Sport.FOOTBALL, "Tom_Brady")
        assertEquals("Quarterback", it.ruolo)
        assertEquals("United States", it.nazione)
        assertEquals("New England Patriots", it.team)
        assertEquals(23, it.partite)
        assertEquals(7, it.titoli)
        assertEquals(15, it.proBowl)
        assertEquals(6, it.allPro)
        assertEquals(3, it.mvp)
        assertTrue(it.retired)
        val en = TeamSportStats.englishFootball(wikitext("en_Tom_Brady"))!!
        assertEquals(649, en.touchdown)
        assertEquals(89214, en.yard)
        assertEquals(0, en.tackle)
        val d = TeamSportStats.enrichFootball(it, en)
        val row = WikipediaAthleteStats.toImportRow(Sport.FOOTBALL, d, null, null, null)
        assertEquals(23, row.presenze)
        assertEquals(649, row.punteggio)
        assertEquals(89214, row.assist)
        assertEquals(15, row.presenzeNazionale)
        assertEquals(7, row.palleRecuperate)
        assertEquals("Quarterback", row.ruolo)
    }

    @Test
    fun rayLewis_defensiveStats() {
        val en = TeamSportStats.englishFootball(wikitext("en_Ray_Lewis"))!!
        assertEquals(2059, en.tackle)
        assertEquals(41, en.sack)
        assertEquals(31, en.intercetti)
        assertEquals(3, en.touchdown)
        assertEquals(0, en.yard)
    }

    private fun player(sport: Sport, id: String, presenze: Int, punteggio: Int, assist: Int = 0, naz: Int = 0, puntiNaz: Int = 0) = Player(
        id = id, sport = sport, nome = id, anno = 1990, carrieraMigliore = "", stato = "", nazione = "", logoPath = null,
        presenze = presenze, punteggio = punteggio, assist = assist, presenzeNazionale = naz, punteggioNazionale = puntiNaz,
        updatedAt = 0,
    )

    @Test
    fun hockeyRanking_pointsPerGame() {
        val a = player(Sport.HOCKEY, "A", presenze = 100, punteggio = 50, assist = 50) // 1,00
        val b = player(Sport.HOCKEY, "B", presenze = 100, punteggio = 30, assist = 90) // 1,20
        val c = player(Sport.HOCKEY, "C", presenze = 0, punteggio = 0)
        assertEquals(AthleteMetric.PUNTEGGIO_RUOLO, AthleteScoring.generalMetric(Sport.HOCKEY))
        assertEquals(listOf("B", "A"), AthleteScoring.ranking(listOf(a, b, c), AthleteMetric.PUNTI_PARTITA).map { it.id })
        assertEquals("1,20", AthleteScoring.formatValue(b, AthleteMetric.PUNTI_PARTITA))
        assertEquals(1, AthleteScoring.trend(b, listOf(a, b)))
        assertTrue(AthleteScoring.computeStar(b))
    }

    @Test
    fun footballRanking_touchdownsAndInterceptionsPerSeason() {
        val qb = player(Sport.FOOTBALL, "QB", presenze = 20, punteggio = 600, assist = 80000, naz = 10)
        val lb = player(Sport.FOOTBALL, "LB", presenze = 17, punteggio = 3, naz = 13).copy(golEvitati = 31)
        assertEquals(AthleteMetric.PUNTEGGIO_RUOLO, AthleteScoring.generalMetric(Sport.FOOTBALL))
        assertEquals(listOf("QB", "LB"), AthleteScoring.ranking(listOf(qb, lb), AthleteMetric.TD_INT_STAGIONE).map { it.id })
        assertEquals("2,00", AthleteScoring.formatValue(lb, AthleteMetric.TD_INT_STAGIONE))
        // Il linebacker senza yard resta fuori dalla classifica delle yard.
        assertEquals(listOf("QB"), AthleteScoring.ranking(listOf(qb, lb), AthleteMetric.YARD).map { it.id })
        assertNull(AthleteScoring.value(lb, AthleteMetric.YARD))
        assertEquals("30,00", AthleteScoring.formatValue(qb, AthleteMetric.TD_INT_STAGIONE))
    }

    @Test
    fun bestSeason_footballFromEnglishSeasonTable() {
        val brady = TeamSportStats.englishFootballBestSeason(wikitext("en_Tom_Brady"))!!
        assertEquals("New England Patriots", brady.team)
        assertEquals("2007", brady.stagione)
        assertEquals(0, brady.extra) // gli intercetti lanciati da un quarterback non contano
        val lewis = TeamSportStats.englishFootballBestSeason(wikitext("en_Ray_Lewis"))!!
        assertEquals("Baltimore Ravens", lewis.team)
        assertTrue(lewis.extra > 0)
    }

    @Test
    fun bestSeason_hockeyFromEnglishSeasonTable() {
        val gretzky = TeamSportStats.englishHockeyBestSeason(wikitext("en_Wayne_Gretzky"))!!
        assertEquals("1985–86", gretzky.stagione)
        assertEquals("Edmonton Oilers", gretzky.team)
        assertEquals(215, gretzky.gol + gretzky.extra)
        val crosby = TeamSportStats.englishHockeyBestSeason(wikitext("en_Sidney_Crosby"))!!
        assertEquals("Pittsburgh Penguins", crosby.team)
    }

    @Test
    fun bestSeason_hockeyFromItalianSeasonTable() {
        // Tabella "Statistiche > Club" di it.wikipedia: squadre unite con rowspan, righe di totale.
        val seasons = TeamSportStats.italianHockeySeasons(wikitext("Wayne_Gretzky"))
        assertEquals(25, seasons.size) // OHL 2, WHA 2, Edmonton 9, Los Angeles 8, St. Louis 1, Rangers 3
        val s8586 = seasons.first { it.stagione == "1985-1986" }
        assertEquals("Edmonton Oilers", s8586.team)
        assertEquals("NHL", s8586.lega)
        assertEquals(80, s8586.partite)
        assertEquals(52, s8586.gol)
        assertEquals(163, s8586.assist)
        assertEquals("Los Angeles Kings", seasons.first { it.stagione == "1990-1991" }.team)
        assertEquals("New York Rangers", seasons.last().team)
        assertEquals(1487, seasons.filter { it.lega == "NHL" }.sumOf { it.partite })

        val gretzky = found(Sport.HOCKEY, "Wayne_Gretzky").best!!
        assertEquals("1985-1986", gretzky.stagione)
        assertEquals("Edmonton Oilers", gretzky.team)
        assertEquals("Edmonton Oilers", gretzky.link)
        assertEquals(215, gretzky.gol + gretzky.extra)

        // Portiere: vittorie e shutout (Brodeur 2006-07: 48 vittorie, 12 shutout).
        val brodeur = TeamSportStats.italianHockeyBestSeason(TeamSportStats.italianHockeySeasons(wikitext("Martin_Brodeur")))!!
        assertEquals("2006-2007", brodeur.stagione)
        assertEquals("New Jersey Devils", brodeur.team)
        assertEquals(78, brodeur.partite)
        assertEquals(48, brodeur.gol)
        assertEquals(12, brodeur.extra)
    }

    @Test
    fun bestSeason_rugbyBestSpell() {
        val d = found(Sport.RUGBY, "Sergio_Parisse")
        val best = d.best!!
        assertTrue(best.partite >= 20)
        val row = WikipediaAthleteStats.toImportRow(Sport.RUGBY, d, null, null, null)
        assertEquals(best.team, row.secondLogoClub)
        assertEquals(best.stagione, row.college)
    }

    @Test
    fun roleScore_everyPlayerGetsAScore() {
        // Defensive tackle senza touchdown né intercetti (Aaron Donald): conta sack e tackle.
        val donald = player(Sport.FOOTBALL, "Donald", presenze = 10, punteggio = 0).copy(ruolo = "Defensive tackle", tackle = 112, rimbalzi = 558)
        assertEquals(TeamRole.FB_DIFESA, TeamRoles.roleOf(donald))
        assertEquals(168, TeamRoles.score(donald))
        val qb = player(Sport.FOOTBALL, "Brady", presenze = 23, punteggio = 649).copy(ruolo = "Quarterback")
        assertEquals(TeamRole.FB_ATTACCO, TeamRoles.roleOf(qb))
        assertEquals(282, TeamRoles.score(qb))
        val guard = player(Sport.FOOTBALL, "G", presenze = 10, punteggio = 0, naz = 5, puntiNaz = 3).copy(ruolo = "Offensive guard")
        assertEquals(TeamRole.FB_LINEA, TeamRoles.roleOf(guard))
        assertEquals(80, TeamRoles.score(guard))
        val pilone = player(Sport.RUGBY, "P", presenze = 200, punteggio = 20, naz = 50).copy(ruolo = "Pilone")
        assertEquals(TeamRole.RU_AVANTI, TeamRoles.roleOf(pilone))
        assertEquals(TeamRole.RU_AVANTI, TeamRoles.roleOf(pilone.copy(ruolo = "Terza linea centro")))
        assertEquals(TeamRole.RU_MEDIANI, TeamRoles.roleOf(pilone.copy(ruolo = "Mediano d'apertura")))
        assertEquals(33, TeamRoles.score(pilone)) // 250/10 + 20/250*100
        val goalie = player(Sport.HOCKEY, "Gk", presenze = 500, punteggio = 0).copy(ruolo = "Portiere", palleRecuperate = 2)
        assertEquals(TeamRole.HO_PORTIERI, TeamRoles.roleOf(goalie))
        assertEquals(70, TeamRoles.score(goalie))
        // La freccia confronta con lo stesso ruolo.
        assertEquals(listOf(donald), AthleteScoring.comparisonGroup(donald, listOf(donald, qb, guard)))
    }

    @Test
    fun teamNameStripsSymbolsAndFootballTemplates() {
        assertEquals("St. Louis/L.A. Rams", TeamSportStats.teamName("{{simbolo|600px vertical bisection HEX-003594 HEX-FFD100.svg}} [[Los Angeles Rams|St. Louis/L.A. Rams]]"))
        assertEquals("San Francisco 49ers", TeamSportStats.teamName("{{Football San Francisco 49ers|G}}"))
    }

    @Test
    fun teamLinkAndTemplatePrefix() {
        assertEquals("Rugby Treviso", TeamSportStats.teamLink("[[Rugby Treviso|Treviso]]"))
        assertNull(TeamSportStats.teamLink("{{Rugby Newcastle|G}}"))
        assertEquals("Hockey su ghiaccio", TeamSportStats.teamTemplatePrefix(Sport.HOCKEY))
        assertEquals("Rugby", TeamSportStats.teamTemplatePrefix(Sport.RUGBY))
        assertEquals("Football", TeamSportStats.teamTemplatePrefix(Sport.FOOTBALL))
    }

    @Test
    fun rugbyStar_fiftyCaps() {
        assertTrue(AthleteScoring.computeStar(player(Sport.RUGBY, "R", presenze = 100, punteggio = 50, naz = 60)))
        assertFalse(AthleteScoring.computeStar(player(Sport.RUGBY, "S", presenze = 100, punteggio = 50, naz = 20)))
    }
}
