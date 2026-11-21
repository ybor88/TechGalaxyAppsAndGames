package com.matchmind.app

import com.matchmind.app.dati.Report
import com.matchmind.app.dati.SchedinaCompleta
import com.matchmind.app.dati.SchedinaEntity
import com.matchmind.app.dati.SelezioneEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

// Report delle bollette perse: cosa è andato storto e perché. © Roberto Di Flumeri
class ReportTest {

    private fun sel(id: Long, casa: String, ospite: String, scelta: String, p: Double, esito: String, risultato: String?,
                    pMercato: Double? = p, pModello: Double? = p, storico: String? = "$casa in stagione: 10 vinte su 12") =
        SelezioneEntity(id, 1, "e$id", "ita.1", 1_800_000_000_000L + id * 3_600_000L, casa, ospite, scelta, 1.05, false, p,
            pMercato, pModello, esito, risultato, storico = storico)

    private fun bolletta(stato: String, vararg s: SelezioneEntity) = SchedinaCompleta(
        SchedinaEntity(7, "CALCIO", "SICURA", 1_799_990_000_000L, 200.0, 1.10, 0.91, stato, chiusaIl = 1_800_100_000_000L), s.toList())

    @Test fun reportDiceChiHaFattoPerdereEPerche() {
        val b = bolletta("PERSA",
            sel(1, "Inter", "Lecce", "CASA", 0.93, "PERSA", "1-1", pMercato = 0.94, pModello = 0.84),
            sel(2, "Napoli", "Empoli", "CASA_O_PAREGGIO", 0.97, "VINTA", "2-0"),
        )
        val r = Report.sconfitta(b, ZoneOffset.UTC)!!
        assertTrue(r, r.contains("Bolletta n. 7 · Calcio"))
        assertTrue(r, r.contains("Puntata 200,00 € · quota 1,10 · vincita mancata 220,00 €"))
        assertTrue(r, r.contains("✗ Serie A"))
        assertTrue(r, r.contains("✓ Serie A"))
        assertTrue(r, r.contains("Giocato: 1 · vince Inter · risultato 1-1 → SBAGLIATO"))
        assertTrue(r, r.contains("Affidabilità: Inter in stagione: 10 vinte su 12"))
        // analisi automatica
        assertTrue(r, r.contains("Ha fatto perdere Inter – Lecce (Serie A): pareggio 1-1: Inter favorita ma non ha vinto"))
        assertTrue(r, r.contains("capita circa 1 volta su 14"))
        assertTrue(r, r.contains("non erano d'accordo"))
        assertTrue(r, r.contains("meno probabile della bolletta"))
    }

    @Test fun diagnosiPerOgniTipoDiPronostico() {
        assertTrue(Report.diagnosi(sel(1, "A", "B", "PAREGGIO_O_OSPITE", 0.9, "PERSA", "2-0"), false).contains("ha vinto A 2-0"))
        assertTrue(Report.diagnosi(sel(1, "A", "B", "UNDER", 0.9, "PERSA", "2-2"), false).contains("4 gol"))
        assertTrue(Report.diagnosi(sel(1, "A", "B", "OSPITE", 0.9, "PERSA", "3-1"), false).contains("sorpresa completa"))
        assertTrue(Report.diagnosi(sel(1, "A", "B", "CASA", 0.9, "PERSA", "0-0", storico = null), false).contains("senza dati di affidabilità"))
    }

    @Test fun soloBollettePerse() {
        assertNull(Report.sconfitta(bolletta("VINTA", sel(1, "A", "B", "CASA", 0.9, "VINTA", "1-0"))))
        val tutte = Report.tutte(listOf(
            bolletta("VINTA", sel(1, "A", "B", "CASA", 0.9, "VINTA", "1-0")),
            bolletta("PERSA", sel(2, "C", "D", "CASA", 0.9, "PERSA", "0-1")),
        ), ZoneOffset.UTC)
        assertEquals(1, Regex("Report bolletta persa").findAll(tutte).count())
    }
}
