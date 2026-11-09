package com.matchmind.app

import com.matchmind.app.dati.EspnParser
import com.matchmind.app.dominio.Evento
import com.matchmind.app.dominio.Scommessa
import com.matchmind.app.dominio.Sport
import com.matchmind.app.dominio.StatoEvento
import com.matchmind.app.dominio.Verifica
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Avvisi "Fai cashout!": quando una partita in corso sta facendo perdere il pronostico. © Roberto Di Flumeri
class AvvisiCashoutTest {

    private fun calcio(casa: Int, ospite: Int, periodo: Int, stato: StatoEvento = StatoEvento.IN_CORSO) =
        Evento("1", Sport.CALCIO, "ita.1", 0L, "a", "A", "b", "B", stato, casa, ospite, null, periodo = periodo)

    private fun basket(casa: Int, ospite: Int, periodo: Int, lega: String = "nba") =
        Evento("2", Sport.BASKET, lega, 0L, "a", "A", "b", "B", StatoEvento.IN_CORSO, casa, ospite, null, periodo = periodo)

    @Test fun nessunAvvisoAlCalcioDInizio() {
        // 0-0 nel primo tempo: 1, 2, 12, Over, GG non sono "persi", è solo l'inizio
        for (s in listOf(Scommessa.CASA, Scommessa.OSPITE, Scommessa.CASA_O_OSPITE, Scommessa.OVER, Scommessa.GOAL))
            assertFalse(s.name, Verifica.inSvantaggio(s, calcio(0, 0, 1)))
    }

    @Test fun risultatoControDopoIlGol() {
        assertTrue(Verifica.inSvantaggio(Scommessa.CASA, calcio(0, 1, 1)))
        assertTrue(Verifica.inSvantaggio(Scommessa.CASA, calcio(1, 1, 1)))
        assertTrue(Verifica.inSvantaggio(Scommessa.CASA_O_PAREGGIO, calcio(0, 1, 1)))
        assertTrue(Verifica.inSvantaggio(Scommessa.PAREGGIO, calcio(1, 0, 1)))
        assertFalse(Verifica.inSvantaggio(Scommessa.CASA, calcio(1, 0, 1)))
        assertFalse(Verifica.inSvantaggio(Scommessa.CASA_O_PAREGGIO, calcio(1, 1, 2)))
    }

    @Test fun nelSecondoTempoAncheSullo00() {
        assertTrue(Verifica.inSvantaggio(Scommessa.CASA, calcio(0, 0, 2)))
        assertTrue(Verifica.inSvantaggio(Scommessa.OVER, calcio(1, 0, 2)))
        assertTrue(Verifica.inSvantaggio(Scommessa.GOAL, calcio(1, 0, 2)))
        assertFalse(Verifica.inSvantaggio(Scommessa.OVER, calcio(1, 0, 1)))
        assertFalse(Verifica.inSvantaggio(Scommessa.UNDER, calcio(0, 0, 2)))
    }

    @Test fun underENoGoalGiaPersi() {
        assertTrue(Verifica.inSvantaggio(Scommessa.UNDER, calcio(2, 1, 1)))
        assertTrue(Verifica.giaPersa(Scommessa.UNDER, 2, 1))
        assertTrue(Verifica.inSvantaggio(Scommessa.NO_GOAL, calcio(1, 1, 1)))
        assertTrue(Verifica.giaPersa(Scommessa.NO_GOAL, 1, 1))
        assertFalse(Verifica.giaPersa(Scommessa.CASA, 0, 3))
    }

    @Test fun soloPartiteInCorso() {
        assertFalse(Verifica.inSvantaggio(Scommessa.CASA, calcio(0, 1, 2, StatoEvento.CONCLUSO)))
        assertFalse(Verifica.inSvantaggio(Scommessa.CASA, calcio(0, 1, 0, StatoEvento.PROGRAMMATO)))
        assertFalse(Verifica.inSvantaggio(Scommessa.CASA, null))
    }

    @Test fun basketSecondoTempoOGrandeDistacco() {
        assertFalse(Verifica.inSvantaggio(Scommessa.CASA, basket(20, 25, 1)))
        assertTrue(Verifica.inSvantaggio(Scommessa.CASA, basket(20, 30, 1)))
        assertTrue(Verifica.inSvantaggio(Scommessa.CASA, basket(60, 62, 3)))
        assertFalse(Verifica.inSvantaggio(Scommessa.CASA, basket(60, 62, 2)))
        // NCAA: due tempi, il secondo è il periodo 2
        assertTrue(Verifica.inSvantaggio(Scommessa.CASA, basket(40, 42, 2, "mens-college-basketball")))
        assertFalse(Verifica.inSvantaggio(Scommessa.OSPITE, basket(40, 42, 3)))
    }

    @Test fun periodoLettoDalFeed() {
        val json = """{"events":[{"id":"9","date":"2026-11-09T20:45Z","competitions":[{
            "status":{"period":2,"displayClock":"67'","type":{"name":"STATUS_SECOND_HALF","state":"in","completed":false}},
            "competitors":[
              {"homeAway":"home","score":"0","team":{"id":"1","displayName":"Casa"}},
              {"homeAway":"away","score":"1","team":{"id":"2","displayName":"Ospite"}}]}]}]}"""
        val e = EspnParser.partite(json, Sport.CALCIO, "ita.1").single()
        assertEquals(StatoEvento.IN_CORSO, e.stato)
        assertEquals(2, e.periodo)
        assertEquals("0-1", e.risultato)
        assertTrue(Verifica.inSvantaggio(Scommessa.CASA, e))
    }
}
