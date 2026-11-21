// Copyright © Roberto Di Flumeri
package com.scouttable.app

import com.scouttable.app.data.Player
import com.scouttable.app.data.PlayerDuplicates
import com.scouttable.app.data.Sport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDuplicatesTest {

    private fun p(
        id: String, nome: String, anno: Int = 1990, nazione: String = "Italia", url: String? = null,
        presenze: Int = 0, star: Boolean = false,
    ) = Player(
        id = id, sport = Sport.CALCIO, nome = nome, anno = anno, carrieraMigliore = "", stato = "Attivo",
        nazione = nazione, logoPath = null, presenze = presenze, proballersUrl = url, updatedAt = 0, star = star,
    )

    @Test fun stessoNomeEAnnoSenzaAccentiEMaiuscole() {
        val g = PlayerDuplicates.find(listOf(p("1", "Luka Dončić"), p("2", "luka doncic "), p("3", "Altro")))
        assertEquals(1, g.size)
        assertEquals(setOf("1", "2"), g.single().players.map { it.id }.toSet())
    }

    @Test fun stessoLinkAncheConNomeDiverso() {
        val g = PlayerDuplicates.find(listOf(
            p("1", "Mario Rossi", url = "https://it.wikipedia.org/wiki/Mario_Rossi/"),
            p("2", "M. Rossi", anno = 0, url = "http://www.it.wikipedia.org/wiki/Mario_Rossi"),
        ))
        assertEquals("stesso link della fonte", g.single().reasons.single())
    }

    @Test fun omonimiConAnniDiversiNonSonoDuplicati() {
        assertTrue(PlayerDuplicates.find(listOf(p("1", "Paolo Maldini", 1968), p("2", "Paolo Maldini", 2001))).isEmpty())
        // anno mancante ma nazioni diverse: due persone diverse
        assertTrue(PlayerDuplicates.find(listOf(p("1", "Juan Lopez", 0, "Spagna"), p("2", "Juan Lopez", 1995, "Messico"))).isEmpty())
    }

    @Test fun restaLaSchedaPiuCompletaEEreditaLaStella() {
        val g = PlayerDuplicates.find(listOf(p("1", "Rossi", star = true), p("2", "Rossi", presenze = 300))).single()
        assertEquals("2", g.keepId)
        assertEquals(listOf("1"), g.remove.map { it.id })
        assertTrue(PlayerDuplicates.merged(g).star)
    }

    @Test fun treSchedeNelloStessoGruppo() {
        val g = PlayerDuplicates.find(listOf(
            p("1", "Rossi", url = "a.com/x"), p("2", "Rossi"), p("3", "Bianchi", url = "a.com/x"),
        ))
        assertEquals(3, g.single().players.size)
    }
}
