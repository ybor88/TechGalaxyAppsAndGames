// Copyright © Roberto Di Flumeri
package com.scouttable.app

import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.athlete.AthleteData
import com.scouttable.app.data.athlete.AthleteMetric
import com.scouttable.app.data.athlete.AthleteScoring
import com.scouttable.app.data.athlete.GENERE_FEMMINILE
import com.scouttable.app.data.athlete.GENERE_MASCHILE
import com.scouttable.app.data.athlete.WikipediaAthleteStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parser delle voci it.wikipedia (wikitext reale salvato in test/resources/wiki) e calcoli. */
class AthleteStatsTest {

    private fun wikitext(name: String): String =
        javaClass.classLoader!!.getResource("wiki/$name.txt")!!.readText()

    private fun found(sport: Sport, page: String): AthleteData {
        val result = WikipediaAthleteStats.parse(sport, wikitext(page), page.replace('_', ' '))
        assertTrue("$page: $result", result is WikipediaAthleteStats.Result.Found)
        return (result as WikipediaAthleteStats.Result.Found).data
    }

    @Test
    fun valentinoRossi_onlyMotomondiale() {
        val d = found(Sport.MOTOGP, "Valentino_Rossi")
        assertEquals("Valentino Rossi", d.nome)
        assertEquals(1979, d.anno)
        assertEquals("Italy", d.nazione)
        assertEquals(9, d.mondiali)
        assertEquals(432, d.gare)
        assertEquals(115, d.gareVinte)
        assertEquals(235, d.podi)
        assertEquals(6357, d.punti)
        assertEquals(65, d.pole)
        assertEquals(96, d.giriVeloci)
        assertEquals("Motomondiale", d.competizione)
        assertEquals("1996-2021", d.stagioni)
        assertTrue(d.retired)
    }

    @Test
    fun valentinoRossi_isNotAFormulaOneDriver() {
        val result = WikipediaAthleteStats.parse(Sport.FORMULA1, wikitext("Valentino_Rossi"), "Valentino Rossi")
        assertTrue(result is WikipediaAthleteStats.Result.Missing)
    }

    @Test
    fun michaelSchumacher_onlyFormulaOne() {
        val d = found(Sport.FORMULA1, "Michael_Schumacher")
        assertEquals("Michael Schumacher", d.nome)
        assertEquals(1969, d.anno)
        assertEquals("Germany", d.nazione)
        assertEquals(7, d.mondiali)
        assertEquals(308, d.gare)
        assertEquals(91, d.gareVinte)
        assertEquals(155, d.podi)
        assertEquals(1566, d.punti)
        assertEquals(68, d.pole)
        assertEquals(77, d.giriVeloci)
        // Ferrari 1996-2006 (11 stagioni) batte Benetton e Mercedes; il blocco sportprototipi è ignorato.
        assertEquals("Ferrari", d.team)
        assertTrue(d.retired)
    }

    @Test
    fun mikeTyson_fightRecord() {
        val d = found(Sport.PUGILATO, "Mike_Tyson")
        assertEquals("Mike Tyson", d.nome)
        assertEquals(1966, d.anno)
        assertEquals("United States", d.nazione)
        assertEquals(false, d.female)
        assertEquals(59, d.incontri)
        assertEquals(50, d.vinti)
        assertEquals(44, d.vintiKo)
        assertEquals(7, d.persi)
        assertEquals(5, d.persiKo)
        assertEquals(5, d.titoli)
        assertEquals("Pesi massimi", d.team)
        assertEquals("Pugilato", d.competizione)
        assertTrue(d.retired)
    }

    @Test
    fun hulkHogan_wrestlingProfileFromWikipedia() {
        val d = found(Sport.WRESTLING, "Hulk_Hogan")
        assertEquals("Hulk Hogan", d.nome)
        assertEquals(1953, d.anno)
        assertEquals("United States", d.nazione)
        assertEquals("Wrestling", d.competizione)
        assertEquals("1977-2012", d.stagioni)
        assertTrue(d.retired)
        // Nessun record su Wikipedia: arriverà da cagematch.net.
        assertEquals(0, d.incontri)
    }

    @Test
    fun cagematch_matchStatistics() {
        val html = javaClass.classLoader!!.getResource("cagematch/Hulk_Hogan_page22.html")!!.readText()
        val r = com.scouttable.app.data.athlete.CagematchStats.parse(html, 504)!!
        assertEquals("Hulk Hogan", r.nome)
        assertEquals(2132, r.incontri)
        assertEquals(1601, r.vinti)
        assertEquals(395, r.persi)
        assertEquals(136, r.pareggi)
    }

    @Test
    fun profightdb_winLossRecord() {
        val html = javaClass.classLoader!!.getResource("profightdb/hulk-hogan-19_winloss.html")!!.readText()
        val r = com.scouttable.app.data.athlete.ProfightdbStats.parse(html)!!
        assertEquals("Hulk Hogan", r.nome)
        assertEquals(370, r.vinti)
        assertEquals(40, r.pareggi)
        assertEquals(147, r.persi)
        assertEquals(557, r.incontri)
        assertEquals("hulk-hogan-19", found(Sport.WRESTLING, "Hulk_Hogan").profightdbSlug)
    }

    @Test
    fun cagematch_wrestlerNumberFromLinks() {
        val cage = com.scouttable.app.data.athlete.CagematchStats
        assertEquals(504, cage.wrestlerNr("https://www.cagematch.net/?id=2&nr=504&page=22"))
        // Dalla voce Wikipedia: il riferimento cagematch del wrestler (id=2), non quelli di eventi (id=1).
        assertEquals(504, found(Sport.WRESTLING, "Hulk_Hogan").cagematchNr)
        assertNull(cage.wrestlerNr("https://www.cagematch.net/?id=1&nr=12345"))
    }

    private fun enriched(sport: Sport, itPage: String, enPage: String) =
        com.scouttable.app.data.athlete.EnglishFightStats.enrich(sport, found(sport, itPage), wikitext(enPage))

    @Test
    fun sumo_yushoAndRankFromEnglish() {
        val d = enriched(Sport.SUMO, "Hakuho_Sho", "en_Hakuho_Sho")
        // 45 tornei vinti nella massima divisione (makuuchi), non quello in jūryō.
        assertEquals(45, d.titoli)
        assertEquals("Yokozuna", d.team)
        // Il record della voce italiana resta quello.
        assertEquals(1187, d.vinti)
        assertEquals(247, d.persi)
    }

    @Test
    fun kickboxing_titlesFromEnglish() {
        val d = enriched(Sport.KICKBOXING, "Giorgio_Petrosyan", "en_Giorgio_Petrosyan")
        assertEquals(104, d.vinti)
        assertTrue("titoli ${d.titoli}", d.titoli in 10..20)
    }

    @Test
    fun kickboxing_recordAndTitlesFromEnglishWhenItalianHasNone() {
        // Buakaw: la voce italiana non ha il record.
        assertEquals(0, found(Sport.KICKBOXING, "Buakaw_Banchamek").incontri)
        val d = enriched(Sport.KICKBOXING, "Buakaw_Banchamek", "en_Buakaw_Banchamek")
        assertEquals(246, d.vinti)
        assertEquals(74, d.vintiKo)
        assertEquals(25, d.persi)
        assertEquals(14, d.pareggi)
        assertEquals(246 + 25 + 14, d.incontri)
        // Titoli vinti (K-1 World MAX 2004 e 2006, WMC, Omnoi...), senza i secondi posti e la Hall of Fame.
        assertTrue("titoli ${d.titoli}", d.titoli in 10..25)
    }

    @Test
    fun motoGp_teamAndLogoFromEnglish() {
        val moto = com.scouttable.app.data.athlete.EnglishMotoStats
        // Rossi: Honda 4 stagioni, Yamaha 7+9, Ducati 2 -> Yamaha, voce "Yamaha Motor Company".
        assertEquals("Yamaha" to "Yamaha Motor Company", moto.mainManufacturer(wikitext("en_Valentino_Rossi")))
        val file = moto.logoFile(wikitext("en_Yamaha_Motor_Company"))
        assertEquals("Yamaha Motor 2025.svg", file)
        assertTrue(moto.fileUrl(file!!).startsWith("https://en.wikipedia.org/wiki/Special:FilePath/Yamaha_Motor_2025.svg"))
    }

    @Test
    fun khabib_mma() {
        val d = found(Sport.MMA, "Chabib_Nurmagomedov")
        assertEquals("Khabib Nurmagomedov", d.nome)
        assertEquals("Russia", d.nazione)
        assertEquals("Arti marziali miste", d.competizione)
        assertEquals("Pesi leggeri", d.team)
        assertEquals(29, d.vinti)
        assertEquals(8, d.vintiKo)
        assertEquals(0, d.persi)
        assertEquals(29, d.incontri)
        // un solo titolo UFC (pesi leggeri 2018): le tre difese non contano
        assertEquals(1, d.titoli)
    }

    @Test
    fun amandaNunes_mmaFemale() {
        val d = found(Sport.MMA, "Amanda_Nunes")
        assertTrue(d.female)
        // titoli UFC: pesi gallo 2016, pesi piuma 2018, pesi gallo di nuovo nel 2022
        assertEquals(3, d.titoli)
        assertEquals("Brazil", d.nazione)
        assertEquals(28, d.incontri)
        assertEquals(23, d.vinti)
        assertEquals(13, d.vintiKo)
        assertEquals(5, d.persi)
        assertEquals(2, d.persiKo)
    }

    @Test
    fun petrosyan_kickboxing() {
        val d = found(Sport.KICKBOXING, "Giorgio_Petrosyan")
        assertEquals("Kickboxing", d.competizione)
        assertEquals("Armenia", d.nazione)
        assertEquals(109, d.incontri)
        assertEquals(104, d.vinti)
        assertEquals(43, d.vintiKo)
        assertEquals(3, d.persi)
        assertEquals(2, d.persiKo)
        assertEquals(2, d.pareggi)
    }

    @Test
    fun hakuho_sumo() {
        val d = found(Sport.SUMO, "Hakuho_Sho")
        assertEquals("Hakuhō Shō", d.nome)
        assertEquals("Sumo", d.competizione)
        assertEquals("Mongolia", d.nazione)
        assertEquals(1434, d.incontri)
        assertEquals(1187, d.vinti)
        assertEquals(247, d.persi)
    }

    private fun albo(file: String, index: Int = 0) =
        com.scouttable.app.data.competitions.AwardTableParser.parse(
            javaClass.classLoader!!.getResource("albo/$file.html")!!.readText(), index,
        )

    @Test
    fun albo_formulaOne() {
        val winners = albo("Campionato_mondiale_piloti_di_Formula_1")
        assertEquals("1950", winners.first().season)
        assertEquals("Nino Farina", winners.first().player)
        assertEquals("Alfa Romeo", winners.first().team)
        assertEquals(7, winners.count { it.player == "Michael Schumacher" })
    }

    @Test
    fun albo_motoGpAndClasse500() {
        val motogp = albo("Albo_d_oro_del_motomondiale", 13)
        assertEquals("2002", motogp.first().season)
        assertEquals("Valentino Rossi", motogp.first().player)
        val c500 = albo("Albo_d_oro_del_motomondiale", 5)
        assertEquals("Leslie Graham", c500.first().player)
        assertEquals("2001", c500.last().season)
    }

    @Test
    fun albo_boxingHeavyweights() {
        val reigns = albo("Campioni_del_mondo_dei_pesi_massimi_di_pugilato")
        assertEquals("John Sullivan", reigns.first().player)
        assertEquals("1885", reigns.first().season)
        assertTrue(reigns.any { it.player == "Mike Tyson" })
    }

    @Test
    fun albo_mmaCurrentChampions() {
        val champions = albo("Pesi_massimi__MMA_")
        assertTrue(champions.any { it.player == "Ciryl Gane" && it.team == "UFC" })
    }

    @Test
    fun importRow_mapsColumns() {
        val tyson = found(Sport.PUGILATO, "Mike_Tyson")
        val row = WikipediaAthleteStats.toImportRow(Sport.PUGILATO, tyson, null, "https://it.wikipedia.org/wiki/Mike_Tyson", null)
        assertEquals(GENERE_MASCHILE, row.ruolo)
        assertEquals(59, row.presenze)
        assertEquals(50, row.punteggio)
        assertEquals(7, row.assist)
        val rossi = found(Sport.MOTOGP, "Valentino_Rossi")
        val r2 = WikipediaAthleteStats.toImportRow(Sport.MOTOGP, rossi, null, null, null)
        assertEquals(6357, r2.punteggio)
        assertEquals(235, r2.assist)
        assertEquals(9, r2.palleRecuperate)
        assertEquals("https://it.wikipedia.org/wiki/Valentino_Rossi", r2.proballersUrl)
    }

    @Test
    fun mainTeam_sumsSeasons() {
        assertEquals(
            "Ferrari",
            WikipediaAthleteStats.mainTeam("{{Automobilismo Jordan|G}} 1991 <br/> {{Automobilismo Ferrari|G}} 1996-2006 <br/> {{Automobilismo Mercedes|G}} 2010-2012"),
        )
        assertEquals("Mercedes", WikipediaAthleteStats.mainTeam("{{Bandiera|DEU}} [[Mercedes-Benz (automobilismo)|Mercedes]] 1990-1991"))
        assertNull(WikipediaAthleteStats.mainTeam(""))
        // Periodo ancora aperto ("2016-"): conta fino a oggi, quindi Red Bull e non Toro Rosso.
        assertEquals("Red Bull", WikipediaAthleteStats.mainTeam("{{Automobilismo Toro Rosso|G}} 2015-2016<br/>{{Automobilismo Red Bull|G}} 2016-"))
    }

    private fun driver(id: String, gare: Int, punti: Int, podi: Int = 0, vinte: Int = 0) = Player(
        id = id, sport = Sport.FORMULA1, nome = id, anno = 1980, carrieraMigliore = "", stato = "", nazione = "",
        logoPath = null, presenze = gare, punteggio = punti, assist = podi, rimbalzi = vinte, updatedAt = 0,
    )

    private fun fighter(id: String, incontri: Int, vinti: Int, female: Boolean) = Player(
        id = id, sport = Sport.PUGILATO, nome = id, anno = 1980, carrieraMigliore = "", stato = "", nazione = "",
        logoPath = null, presenze = incontri, punteggio = vinti, ruolo = if (female) GENERE_FEMMINILE else GENERE_MASCHILE,
        updatedAt = 0,
    )

    @Test
    fun motorRanking_byWinRate() {
        val a = driver("A", gare = 100, punti = 3000, vinte = 20) // 20% gare vinte
        val b = driver("B", gare = 50, punti = 800, vinte = 15) // 30%
        val c = driver("C", gare = 10, punti = 40, vinte = 1) // 10%
        val all = listOf(a, b, c)
        // Classifica generale dei piloti: gare vinte / gare disputate.
        assertEquals(AthleteMetric.PERC_VITTORIE_GARE, AthleteScoring.generalMetric(Sport.FORMULA1))
        assertEquals(listOf("B", "A", "C"), AthleteScoring.ranking(all, AthleteMetric.PERC_VITTORIE_GARE).map { it.id })
        assertEquals(listOf("A", "B", "C"), AthleteScoring.ranking(all, AthleteMetric.PUNTI).map { it.id })
        // Media 20%: A pari alla media, B +10 punti % (su), C -10 (giù).
        assertEquals(0.0, AthleteScoring.distacco(a, all)!!, 1e-9)
        assertEquals(10.0, AthleteScoring.distacco(b, all)!!, 1e-9)
        assertEquals(0, AthleteScoring.trend(a, all))
        assertEquals(1, AthleteScoring.trend(b, all))
        assertEquals(-1, AthleteScoring.trend(c, all))
        assertEquals("30,0%", AthleteScoring.formatValue(b, AthleteMetric.PERC_VITTORIE_GARE))
    }

    @Test
    fun mainTeam_commaSeparatedTeams() {
        // Agostini: case separate da virgole (senza anni) -> una sola scuderia, non tutte insieme.
        val team = WikipediaAthleteStats.mainTeam("[[Moto Morini]], [[MV Agusta]], [[Yamaha]], [[Suzuki]]")
        assertTrue(team, team in setOf("Moto Morini", "MV Agusta", "Yamaha", "Suzuki"))
        // Le virgole tra parentesi restano insieme: Yamaha 7+9 stagioni batte Honda 4.
        assertEquals("Yamaha", WikipediaAthleteStats.mainTeam("[[Honda]] (2000–2003), [[Yamaha]] (2004–2010, 2013–2021)"))
    }

    @Test
    fun wrestlingRanking_isSplitByGender() {
        val m1 = fighter("M1", 10, 9, female = false) // 90%
        val m2 = fighter("M2", 10, 5, female = false) // 50%
        val f1 = fighter("F1", 10, 6, female = true) // 60%
        val f2 = fighter("F2", 10, 2, female = true) // 20%
        val all = listOf(m1, m2, f1, f2)
        // F1 (60%) è sopra la media femminile (40%) anche se sotto quella di tutti (55%).
        assertEquals(1, AthleteScoring.trend(f1, all))
        assertEquals(-1, AthleteScoring.trend(m2, all))
        val women = AthleteScoring.ranking(AthleteScoring.sameGroup(all, Sport.PUGILATO, true), AthleteMetric.PERCENTUALE)
        assertEquals(listOf("F1", "F2"), women.map { it.id })
    }
}
