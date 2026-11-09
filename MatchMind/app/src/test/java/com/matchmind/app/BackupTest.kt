package com.matchmind.app

import com.matchmind.app.dati.Backup
import com.matchmind.app.dati.MovimentoEntity
import com.matchmind.app.dati.OsservazioneEntity
import com.matchmind.app.dati.Preferenze
import com.matchmind.app.dati.SchedinaEntity
import com.matchmind.app.dati.SelezioneEntity
import com.matchmind.app.dominio.Mercato
import com.matchmind.app.dominio.Strategia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// Backup per il cambio di telefono: quello che si esporta si ritrova identico. © Roberto Di Flumeri
class BackupTest {

    private val contenuto = Backup.Contenuto(
        creatoIl = 1_800_000_000_000L,
        schedine = listOf(
            SchedinaEntity(1, "CALCIO", "SICURA", 100L, 10.0, 3.25, 0.31, "VINTA", chiusaIl = 500L, vincita = 32.5, verificataIl = 500L),
            SchedinaEntity(2, "BASKET", "VALORE", 200L, 5.0, 1.9, 0.5, "IN_CORSO"),
        ),
        selezioni = listOf(
            SelezioneEntity(10, 1, "e1", "ita.1", 150L, "Casa \"1\"", "Ospite, è", "CASA", 1.8, false, 0.7, 0.68, null, "VINTA", "2-1", "0-1"),
            SelezioneEntity(11, 2, "e2", "nba", 250L, "A", "B", "OSPITE", 1.9, true, 0.55, null, null, "IN_ATTESA"),
        ),
        movimenti = listOf(
            MovimentoEntity(1, 50L, "RICARICA", 1000.0),
            MovimentoEntity(2, 100L, "PUNTATA", -10.0, 1),
            MovimentoEntity(3, 500L, "VINCITA", 32.5, 1),
        ),
        osservazioni = listOf(
            OsservazioneEntity("e1", "CALCIO", "ita.1", 150L, 0.5, 0.3, 0.2, 0.45, 0.3, 0.25, "CASA", 0.52, 0.48, 2, 1),
            OsservazioneEntity("e3", "CALCIO", "eng.1", 300L, null, null, null, null, null, null),
        ),
        preferenze = Preferenze(puntata = 7.5, giorni = 2, strategia = Strategia.VALORE, configurato = true, sicurezza = 0.4,
            mercati = setOf(Mercato.VINCENTE, Mercato.GOAL), avvisiCashout = false),
    )

    @Test fun andataERitorno() {
        val letto = Backup.leggi(Backup.scrivi(contenuto))
        assertEquals(contenuto, letto)
    }

    @Test fun storicoVuoto() {
        val vuoto = Backup.Contenuto(0L, emptyList(), emptyList(), emptyList(), emptyList(), Preferenze(configurato = true))
        assertEquals(vuoto, Backup.leggi(Backup.scrivi(vuoto)))
    }

    private fun rifiutato(testo: String, parola: String) {
        try {
            Backup.leggi(testo)
            fail("doveva essere rifiutato: $testo")
        } catch (e: Backup.BackupNonValido) {
            assertTrue(e.message!!, e.message!!.contains(parola))
        }
    }

    @Test fun fileNonValidiRifiutati() {
        rifiutato("ciao", "non è un backup")
        rifiutato("""{"app":"Altro","formato":1}""", "non è un backup")
        rifiutato("""{"app":"MatchMind","formato":99}""", "più recente")
        rifiutato("""{"app":"MatchMind","formato":1,"schedine":[{"id":1}]}""", "danneggiato")
        // pronostico che punta a una schedina inesistente
        val orfano = Backup.scrivi(contenuto.copy(schedine = contenuto.schedine.take(1)))
        rifiutato(orfano, "senza schedina")
        // stato sconosciuto
        rifiutato(Backup.scrivi(contenuto).replace("\"stato\":\"VINTA\"", "\"stato\":\"BOH\""), "BOH")
    }
}
