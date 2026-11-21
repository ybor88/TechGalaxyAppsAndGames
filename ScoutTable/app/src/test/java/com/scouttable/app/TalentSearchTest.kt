// Copyright © Roberto Di Flumeri
package com.scouttable.app

import com.scouttable.app.data.Sport
import com.scouttable.app.data.talent.TalentSearch
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Talenti: query e risposta reale di Wikidata (calciatori italiani, salvata il 21/11/2026). */
class TalentSearchTest {

    private val today = LocalDate.of(2026, 11, 21)

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource("wiki/$name")!!.readText(Charsets.UTF_8)

    @Test
    fun maxAge25_fromCurrentDate() {
        // Chi è nato il 21/11/2000 compie 26 anni oggi: escluso; chi è nato il giorno dopo ne ha 25.
        assertEquals(LocalDate.of(2000, 11, 21), TalentSearch.bornAfter(today))
        val query = TalentSearch.buildQuery(TalentSearch.occupations(Sport.CALCIO), "IT", today)
        assertTrue(query.contains("\"2000-11-21T00:00:00Z\""))
        assertTrue(query.contains("wdt:P297 \"IT\""))
        assertTrue(query.contains("wd:Q937857"))
    }

    @Test
    fun everySportHasAnOccupation() {
        Sport.entries.forEach { assertTrue(it.name, TalentSearch.occupations(it).isNotEmpty()) }
    }

    @Test
    fun parsesRealWikidataAnswer() {
        val talents = TalentSearch.parse(fixture("wikidata_talenti_calcio_IT.json"), today)
        assertEquals(40, talents.size)
        assertTrue(talents.all { it.eta <= 25 })
        val calafiori = talents.first()
        assertEquals("Riccardo Calafiori", calafiori.nome)
        assertEquals(24, calafiori.eta)
        assertEquals("difensore", calafiori.ruolo)
        // La nazionale non è la squadra attuale.
        assertEquals("Arsenal Football Club", calafiori.squadra)
        assertTrue(calafiori.link!!.startsWith("https://it.wikipedia.org/"))
        // Più squadre senza data di fine: la più recente (Gimenez: Porto da agosto 2026, non il Milan).
        assertEquals("Futebol Clube do Porto", talents.first { it.nome.startsWith("Santiago") }.squadra)
        // Solo nazionali giovanili + Tottenham: resta il club.
        assertEquals("Tottenham Hotspur F.C.", talents.first { it.nome == "Destiny Udogie" }.squadra)
        assertEquals(talents.sortedByDescending { it.notorieta }, talents)
    }
}
