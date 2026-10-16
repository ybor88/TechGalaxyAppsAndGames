package com.matchmind.app

import com.matchmind.app.dominio.ModelliProbabilistici as M
import com.matchmind.app.dominio.Probabilita
import com.matchmind.app.dominio.Quote
import com.matchmind.app.dominio.StatSquadra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Quote, modelli statistici e combinazione. © Roberto Di Flumeri
class ModelliProbabilisticiTest {

    private fun squadra(id: String, partite: Int, fatti: Double, subiti: Double) =
        StatSquadra(id, id, partite, 0, 0, 0, fatti, subiti)

    @Test fun quoteAmericaneInDecimali() {
        assertEquals(2.45, M.decimaleDaAmericana("+145")!!, 1e-9)
        assertEquals(1.0 + 100.0 / 180, M.decimaleDaAmericana("-180")!!, 1e-9)
        assertEquals(2.0, M.decimaleDaAmericana("EVEN")!!, 1e-9)
        assertEquals(2.0, M.decimaleDaAmericana("+100")!!, 1e-9)
        assertEquals(3.55, M.decimaleDaAmericana(255)!!, 1e-9)
        assertNull(M.decimaleDaAmericana("abc"))
        assertNull(M.decimaleDaAmericana("+50")) // fuori scala
        assertNull(M.decimaleDaAmericana(null as String?))
    }

    @Test fun margineTolto() {
        val q = Quote(2.10, 3.40, 3.60)
        assertTrue(M.margine(q) > 0.0)
        val p = M.daQuote(q)
        assertEquals(1.0, p.casa + p.pareggio + p.ospite, 1e-9)
        assertTrue(p.casa > p.ospite && p.casa < 1 / 2.10 + 0.01)
    }

    @Test fun quotaStimataConMargine() {
        assertEquals(1.25, M.quotaStimata(0.75), 0.02)
        assertTrue(M.quotaStimata(0.99) >= 1.01)
    }

    @Test fun poissonSommaAUno() {
        val s = (0..30).sumOf { M.poisson(1.4, it) }
        assertEquals(1.0, s, 1e-9)
    }

    @Test fun calcioSquadraForteFavorita() {
        val forte = squadra("a", 10, 25.0, 5.0)
        val debole = squadra("b", 10, 6.0, 22.0)
        val p = M.probabilitaCalcio(forte, debole, 1.35)
        assertEquals(1.0, p.casa + p.pareggio + p.ospite, 1e-9)
        assertTrue(p.casa > 0.6 && p.ospite < 0.15)
        // a parità di forza conta il campo di casa
        val pari = M.probabilitaCalcio(squadra("c", 10, 13.0, 13.0), squadra("d", 10, 13.0, 13.0), 1.3)
        assertTrue(pari.casa > pari.ospite && pari.pareggio > 0.2)
    }

    @Test fun inizioStagioneProdente() {
        // dopo una sola partita 5-0 il modello non deve gridare alla certezza
        val p = M.probabilitaCalcio(squadra("a", 1, 5.0, 0.0), squadra("b", 1, 0.0, 5.0), 1.35)
        assertTrue("prob ${p.casa}", p.casa < 0.75)
    }

    @Test fun basketSimmetricoEVantaggioCasa() {
        val uguali = M.probabilitaBasket(squadra("a", 20, 2200.0, 2200.0), squadra("b", 20, 2200.0, 2200.0))
        assertTrue(uguali.casa > 0.5 && uguali.casa < 0.62)
        assertEquals(0.0, uguali.pareggio, 0.0)
        val forte = M.probabilitaBasket(squadra("a", 20, 2400.0, 2200.0), squadra("b", 20, 2150.0, 2350.0))
        assertTrue(forte.casa > 0.8)
    }

    @Test fun normaleCumulata() {
        assertEquals(0.5, M.normaleCumulata(0.0), 1e-6)
        assertEquals(0.8413, M.normaleCumulata(1.0), 1e-3)
        assertEquals(0.0228, M.normaleCumulata(-2.0), 1e-3)
    }

    @Test fun combinazioneEFontiMancanti() {
        val a = Probabilita(0.6, 0.2, 0.2)
        val b = Probabilita(0.4, 0.3, 0.3)
        val c = M.combina(a, b, 0.75)!!
        assertEquals(0.55, c.casa, 1e-9)
        assertEquals(a, M.combina(a, null, 0.3))
        assertEquals(b, M.combina(null, b, 0.3))
        assertNull(M.combina(null, null, 0.5))
    }

    @Test fun golAttesiCoerentiConLeQuote() {
        // si parte da gol attesi noti: il fit deve ritrovarli dalle sole probabilità
        val (p, over) = M.esitiDaGol(1.8, 0.9)
        val (lc, lo) = M.golAttesiCoerenti(p, over, 2.6)
        assertEquals(1.8, lc, 0.06)
        assertEquals(0.9, lo, 0.06)
        // senza Over di mercato il totale di riferimento orienta la soluzione
        val (c2, o2) = M.golAttesiCoerenti(Probabilita(0.45, 0.28, 0.27), null, 2.4)
        assertTrue(c2 > o2)
        assertEquals(2.4, c2 + o2, 0.6)
    }

    @Test fun goalEOver() {
        assertEquals((1 - Math.exp(-1.5)) * (1 - Math.exp(-1.2)), M.probabilitaGoal(1.5, 1.2), 1e-12)
        assertTrue(M.probabilitaGoal(0.3, 0.3) < 0.1)
        val over = M.probabilitaOver(1.5, 1.2) // totale atteso 2.7
        assertTrue(over in 0.45..0.6)
        assertEquals(0.5, M.overDaQuote(1.9, 1.9), 1e-9)
        assertEquals(1 / 1.7 / (1 / 1.7 + 1 / 2.15), M.overDaQuote(1.70, 2.15), 1e-9)
    }

    @Test fun quotaDoppiaChanceDerivata() {
        // 1 a 1,50 e X a 4,20: la 1X costa circa 1,11
        assertEquals(1.11, M.quotaDerivata(1.50, 4.20), 0.005)
        assertTrue(M.quotaDerivata(2.0, 2.0) == 1.0)
    }

    @Test fun inizioStagioneUsaLaStagionePrecedente() {
        val precedente = mapOf(
            "forte" to StatSquadra("forte", "Forte", 82, 60, 0, 22, 82 * 118.0, 82 * 108.0),
            "debole" to StatSquadra("debole", "Debole", 82, 20, 0, 62, 82 * 108.0, 82 * 118.0),
        )
        val corrente = mapOf(
            "forte" to StatSquadra("forte", "Forte", 0, 0, 0, 0, 0.0, 0.0),
            "debole" to StatSquadra("debole", "Debole", 0, 0, 0, 0, 0.0, 0.0),
            "nuova" to StatSquadra("nuova", "Nuova", 0, 0, 0, 0, 0.0, 0.0),
        )
        val unite = M.unisciStagioni(corrente, precedente, 10)
        assertEquals(10, unite["forte"]!!.partite)
        assertEquals(1180.0, unite["forte"]!!.fatti, 1e-6)
        assertEquals(0, unite["nuova"]!!.partite) // senza storico resta com'è
        val p = M.probabilitaBasket(unite["forte"]!!, unite["debole"]!!)
        assertTrue("prob ${p.casa}", p.casa > 0.7)
        // a stagione avanzata il passato pesa poco: la squadra "forte" che ora perde non è più favorita
        val ora = mapOf(
            "forte" to StatSquadra("forte", "Forte", 40, 10, 0, 30, 40 * 105.0, 40 * 115.0),
            "debole" to StatSquadra("debole", "Debole", 40, 30, 0, 10, 40 * 115.0, 40 * 105.0),
        )
        val dopo = M.unisciStagioni(ora, precedente, 10)
        assertTrue(M.probabilitaBasket(dopo["forte"]!!, dopo["debole"]!!).casa < 0.4)
    }

    @Test fun importiInseritiDallUtente() {
        assertEquals(1000.5, com.matchmind.app.ui.leggiImporto("1.000,50")!!, 1e-9)
        assertEquals(1000.5, com.matchmind.app.ui.leggiImporto("1000.5")!!, 1e-9)
        assertEquals(20.0, com.matchmind.app.ui.leggiImporto("€ 20")!!, 1e-9)
        assertEquals(null, com.matchmind.app.ui.leggiImporto("abc"))
        assertEquals(null, com.matchmind.app.ui.leggiImporto("0"))
        assertEquals(null, com.matchmind.app.ui.leggiImporto("-5"))
    }
}
