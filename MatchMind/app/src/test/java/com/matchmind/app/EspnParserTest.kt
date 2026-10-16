package com.matchmind.app

import com.matchmind.app.dati.EspnParser
import com.matchmind.app.dominio.Esito
import com.matchmind.app.dominio.Esperienza
import com.matchmind.app.dominio.Generatore
import com.matchmind.app.dominio.ModelliProbabilistici
import com.matchmind.app.dominio.Sport
import com.matchmind.app.dominio.StatoEvento
import com.matchmind.app.dominio.Strategia
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Lettura di risposte ESPN reali salvate in test/resources/espn. © Roberto Di Flumeri
class EspnParserTest {

    private fun leggi(nome: String) = javaClass.getResource("/espn/$nome")!!.readText()

    @Test fun partiteProgrammateConQuoteReali() {
        val ev = EspnParser.partite(leggi("calcio_programmate.json"), Sport.CALCIO, "eng.1")
        assertTrue(ev.isNotEmpty())
        val e = ev.first { it.casa == "AFC Bournemouth" }
        assertEquals("Sunderland", e.ospite)
        assertEquals(StatoEvento.PROGRAMMATO, e.stato)
        assertNull(e.esito)
        val q = e.quote!!
        assertEquals(2.10, q.casa, 1e-9) // +110
        assertEquals(3.45, q.ospite, 1e-9) // +245
        assertEquals(3.55, q.pareggio!!, 1e-9) // +255
        val m = ModelliProbabilistici.margine(q)
        assertTrue("margine $m", m in 0.0..0.12)
    }

    @Test fun calcioConclusoConPareggiERisultati() {
        val ev = EspnParser.partite(leggi("calcio_concluse.json"), Sport.CALCIO, "ita.1")
        assertTrue(ev.all { it.stato == StatoEvento.CONCLUSO })
        val como = ev.first { it.id == "401874940" }
        assertEquals("0-0", como.risultato)
        assertEquals(Esito.PAREGGIO, como.esito)
        val lazio = ev.first { it.id == "401874951" }
        assertEquals(Esito.CASA, lazio.esito)
        assertEquals("3-1", lazio.risultato)
        assertEquals(Esito.OSPITE, ev.first { it.id == "401874776" }.esito)
        assertTrue(ev.all { it.quote == null }) // a partita finita il feed non riporta più le quote
    }

    @Test fun basketConclusoESupplementariContano() {
        val ev = EspnParser.partite(leggi("basket_concluse.json"), Sport.BASKET, "nba")
        val phi = ev.first { it.casa == "Philadelphia 76ers" }
        assertEquals(Esito.CASA, phi.esito)
        assertEquals("109-97", phi.risultato)
        assertEquals(Esito.OSPITE, ev.first { it.casa == "LA Clippers" }.esito)
        assertNull(EspnParser.esito(Sport.BASKET, "STATUS_FINAL", 100, 100))
        assertEquals(Esito.CASA, EspnParser.esito(Sport.BASKET, "STATUS_FINAL_OT", 110, 108))
    }

    @Test fun calcioSupplementariNonDecisi() {
        assertNull(EspnParser.esito(Sport.CALCIO, "STATUS_FINAL_AET", 2, 1))
        assertNull(EspnParser.esito(Sport.CALCIO, "STATUS_FINAL_PEN", 1, 1))
        assertEquals(Esito.PAREGGIO, EspnParser.esito(Sport.CALCIO, "STATUS_FULL_TIME", 1, 1))
    }

    @Test fun basketProgrammatoSenzaPareggio() {
        val ev = EspnParser.partite(leggi("basket_programmate.json"), Sport.BASKET, "nba")
        val conQuote = ev.filter { it.quote != null }
        assertTrue(conQuote.isNotEmpty())
        assertTrue(conQuote.all { it.quote!!.pareggio == null })
        val ny = ev.first { it.casa == "New York Knicks" }
        assertEquals(1 + 100.0 / 205, ny.quote!!.casa, 0.01)
    }

    @Test fun quoteUnderOverReali() {
        val e = EspnParser.partite(leggi("calcio_programmate.json"), Sport.CALCIO, "eng.1").first { it.casa == "AFC Bournemouth" }
        val q = e.quote!!
        assertEquals(2.5, q.lineaGol!!, 0.0)
        assertEquals(1 + 100.0 / 120, q.over!!, 0.01) // -120
        assertEquals(2.0, q.under!!, 0.01) // +100
        // nel basket niente Under/Over gol
        assertTrue(EspnParser.partite(leggi("basket_programmate.json"), Sport.BASKET, "nba").all { it.quote?.over == null })
    }

    @Test fun preseasonRiconosciuta() {
        assertTrue(EspnParser.partite(leggi("basket_programmate.json"), Sport.BASKET, "nba").all { it.amichevole })
        assertTrue(EspnParser.partite(leggi("basket_concluse.json"), Sport.BASKET, "nba").none { it.amichevole }) // play-in: ufficiale
        assertTrue(EspnParser.partite(leggi("calcio_programmate.json"), Sport.CALCIO, "eng.1").none { it.amichevole })
    }

    @Test fun statiDelFeed() {
        fun s(nome: String, stato: String, completo: Boolean) =
            EspnParser.stato(JSONObject().put("name", nome).put("state", stato).put("completed", completo))
        assertEquals(StatoEvento.PROGRAMMATO, s("STATUS_SCHEDULED", "pre", false))
        assertEquals(StatoEvento.IN_CORSO, s("STATUS_HALFTIME", "in", false))
        assertEquals(StatoEvento.CONCLUSO, s("STATUS_FULL_TIME", "post", true))
        assertEquals(StatoEvento.RINVIATO, s("STATUS_POSTPONED", "post", false))
        assertEquals(StatoEvento.ANNULLATO, s("STATUS_CANCELED", "post", false))
    }

    @Test fun classificheCalcioEBasket() {
        val calcio = EspnParser.classifica(leggi("classifica_calcio.json"))
        assertEquals(20, calcio.size)
        assertTrue(calcio.values.all { it.partite > 0 && it.fatti >= 0 })
        val basket = EspnParser.classifica(leggi("classifica_basket.json"))
        assertEquals(30, basket.size) // due conference
    }

    @Test fun datiNonValidiNonRompono() {
        assertTrue(EspnParser.partite("{}", Sport.CALCIO, "x").isEmpty())
        assertTrue(EspnParser.partite("""{"events":[{"id":"1"},null]}""", Sport.CALCIO, "x").isEmpty())
        assertNull(EspnParser.quote(null, Sport.CALCIO))
        assertTrue(EspnParser.classifica("{}").isEmpty())
    }

    @Test fun catenaCompletaSuDatiReali() {
        // partite reali di Premier con la classifica di Serie A non combaciano: deve bastare il mercato
        val ev = EspnParser.partite(leggi("calcio_programmate.json"), Sport.CALCIO, "eng.1")
        val classifica = EspnParser.classifica(leggi("classifica_calcio.json"))
        val esp = Esperienza.vuota(Sport.CALCIO)
        val analisi = ev.mapNotNull { Generatore.analizza(it, classifica, ModelliProbabilistici.mediaGolLega(classifica.values), esp) }
        assertEquals(ev.count { it.quote != null }, analisi.size)
        val s = Generatore.genera(Sport.CALCIO, analisi, Strategia.SICURA, ev.minOf { it.inizioMillis } - 86_400_000L, sicurezza = 0.1)
        assertTrue(s.pronostici.isNotEmpty())
        assertTrue(s.probabilita >= 0.1)
        assertTrue(s.pronostici.all { it.probabilita >= Generatore.SOGLIA_PRONOSTICO_SICURO && it.quota >= Generatore.QUOTA_MINIMA })
        // ogni partita analizzata ha probabilità di Goal e Over coerenti con le quote reali
        assertTrue(analisi.all { it.pGoal != null && it.pOver != null })
    }
}
