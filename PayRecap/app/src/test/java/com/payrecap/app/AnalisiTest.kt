// PayRecap — © Roberto Di Flumeri
package com.payrecap.app

import com.payrecap.app.dominio.Analisi
import com.payrecap.app.dominio.Cedolino
import com.payrecap.app.dominio.Esempi
import com.payrecap.app.dominio.Frammento
import com.payrecap.app.dominio.Gravita
import com.payrecap.app.dominio.Impostazioni
import com.payrecap.app.dominio.ParserCedolino
import com.payrecap.app.dominio.ricomponiRighe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalisiTest {

    private val oggi = LocalDate.of(2026, 11, 28)
    private fun base(id: Long = 1, mese: Int = 11, anno: Int = 2026) = Cedolino(
        id = id, mese = mese, anno = anno, lordo = 3000.0, netto = 2200.0, irpef = 500.0,
        addizionaleRegionale = 20.0, addizionaleComunale = 5.0, contributiPrevidenziali = 275.0,
    )
    private fun titoli(c: Cedolino, prec: Cedolino? = null, imp: Impostazioni = Impostazioni()) =
        Analisi.avvisi(c, prec, oggi, imp).map { it.titolo }

    // ------------------------------------------------------------ totali e periodo

    @Test fun totali() {
        val c = base()
        assertEquals(525.0, c.totaleTasse!!, 0.001)
        assertEquals(800.0, c.totaleTasseEContributi!!, 0.001)
    }

    @Test fun totaliNullSeNessunaVoce() = assertNull(Cedolino(mese = 1, anno = 2026).totaleTasse)

    @Test fun etichettaPeriodo() {
        assertEquals("Novembre 2026", base().etichettaPeriodo)
        assertEquals("Mensilità aggiuntiva · Dicembre 2026", base().copy(mese = 12, aggiuntiva = true).etichettaPeriodo)
    }

    @Test fun precedenteSaltaLeAggiuntive() {
        val ott = base(id = 1, mese = 10)
        val tred = base(id = 2, mese = 10).copy(aggiuntiva = true)
        val nov = base(id = 3, mese = 11)
        val dic = base(id = 4, mese = 12)
        val tutti = listOf(ott, tred, nov, dic)
        assertEquals(3L, Analisi.precedente(dic, tutti)!!.id)
        assertEquals(1L, Analisi.precedente(nov, tutti)!!.id)
        assertNull(Analisi.precedente(ott, tutti))
        assertNull(Analisi.precedente(tred, tutti))
    }

    @Test fun precedenteTraAnniDiversi() {
        val dic25 = base(id = 1, mese = 12, anno = 2025)
        val gen26 = base(id = 2, mese = 1, anno = 2026)
        assertEquals(1L, Analisi.precedente(gen26, listOf(gen26, dic25))!!.id)
    }

    // ------------------------------------------------------------ confronto

    @Test fun confrontoDeltaEPercentuale() {
        val prec = base(id = 1, mese = 10).copy(netto = 2000.0)
        val voce = Analisi.confronto(base(), prec).first { it.voce == "Netto" }
        assertEquals(200.0, voce.delta!!, 0.001)
        assertEquals(10.0, voce.deltaPercentuale!!, 0.001)
    }

    @Test fun confrontoSenzaPrecedente() {
        val voce = Analisi.confronto(base(), null).first { it.voce == "Lordo" }
        assertNull(voce.delta)
        assertNull(voce.deltaPercentuale)
    }

    // ------------------------------------------------------------ avvisi

    @Test fun cedolinoCoerenteNessunAvviso() = assertTrue(titoli(base()).isEmpty())

    @Test fun nettoInCaloOltreSoglia() {
        val prec = base(id = 1, mese = 10).copy(netto = 2600.0)
        val avvisi = Analisi.avvisi(base(), prec, oggi)
        val a = avvisi.first { it.titolo.startsWith("Netto in calo") }
        assertEquals(Gravita.AVVISO, a.gravita)
        assertTrue(a.dettaglio.contains("Ottobre 2026"))
    }

    @Test fun variazioneSottoSogliaIgnorata() {
        val prec = base(id = 1, mese = 10).copy(netto = 2150.0) // +2,3%
        assertTrue(titoli(base(), prec).none { it.startsWith("Netto") })
    }

    @Test fun sogliaPersonalizzata() {
        val prec = base(id = 1, mese = 10).copy(netto = 2150.0)
        assertTrue(titoli(base(), prec, Impostazioni(sogliaVariazione = 2.0)).any { it.startsWith("Netto in aumento") })
    }

    @Test fun nettoSuperioreAlLordo() = assertTrue(titoli(base().copy(netto = 3100.0)).contains("Netto superiore al lordo"))

    @Test fun vociNonRiepilogate() {
        // 3000 - 800 = 2200 atteso; netto 2050 → 150 € di altre trattenute
        assertTrue(titoli(base().copy(netto = 2050.0)).contains("Altre voci non riepilogate"))
    }

    @Test fun datiMancanti() = assertTrue(titoli(Cedolino(mese = 1, anno = 2026)).contains("Dati principali incompleti"))

    @Test fun livelloECcnlCambiati() {
        val prec = base(id = 1, mese = 10).copy(livello = "6°", ccnl = "Commercio")
        val t = titoli(base().copy(livello = "5°", ccnl = "Turismo"), prec)
        assertTrue(t.contains("Livello cambiato"))
        assertTrue(t.contains("CCNL cambiato"))
    }

    @Test fun ferieNegativeEConti() {
        val t = titoli(base().copy(ferieMaturate = 10.0, ferieGodute = 12.0, ferieResidue = -2.0))
        assertTrue(t.contains("Ferie in negativo"))
        val t2 = titoli(base().copy(rolMaturati = 40.0, rolGoduti = 10.0, rolResidui = 20.0))
        assertTrue(t2.contains("ROL: conti da verificare"))
    }

    @Test fun troppeFerieAccumulate() {
        assertTrue(titoli(base().copy(ferieResidue = 35.0)).contains("Molte ferie accumulate"))
        assertFalse(titoli(base().copy(ferieResidue = 35.0, unitaFerie = Cedolino.UNITA_ORE)).contains("Molte ferie accumulate"))
    }

    @Test fun straordinarioNonRiconosciuto() {
        val c = base().copy(straordinarioEffettuate = 20.0, straordinarioPagate = 10.0, straordinarioAccantonate = 4.0)
        assertTrue(titoli(c).contains("Straordinario non tutto riconosciuto"))
        val ok = base().copy(straordinarioEffettuate = 14.0, straordinarioPagate = 10.0, straordinarioAccantonate = 4.0)
        assertFalse(titoli(ok).contains("Straordinario non tutto riconosciuto"))
    }

    @Test fun scadenzaVicinaEPassata() {
        val vicina = Analisi.avvisi(base().copy(scadenza = "15/12/2026"), null, oggi)
        assertEquals(Gravita.AVVISO, vicina.first { it.titolo == "Contratto in scadenza" }.gravita)
        val passata = Analisi.avvisi(base().copy(scadenza = "01/11/2026"), null, oggi)
        assertEquals(Gravita.CRITICO, passata.first().gravita) // i critici vengono prima
        assertTrue(titoli(base().copy(scadenza = "31/12/2027")).none { it.startsWith("Contratto") })
        assertTrue(titoli(base().copy(scadenza = "15/12/2026"), imp = Impostazioni(giorniPreavvisoScadenza = 10)).none { it.startsWith("Contratto") })
    }

    @Test fun dataNonValidaIgnorata() = assertNull(Analisi.parseData("32/13/2026"))

    @Test fun esempiCaricatiProduconoConfrontoRealistico() {
        val ott = ParserCedolino.analizza(Esempi.OTTOBRE_2026, 2026, 1).cedolino.copy(id = 1)
        val nov = ParserCedolino.analizza(Esempi.NOVEMBRE_2026, 2026, 1).cedolino.copy(id = 2)
        assertEquals(1L, Analisi.precedente(nov, listOf(ott, nov))!!.id)
        val netto = Analisi.confronto(nov, ott).first { it.voce == "Netto" }
        assertEquals(221.78, netto.delta!!, 0.001)
        // ottobre aveva un'assenza non retribuita: netto +10,2% → un solo avviso, più le info su lordo e tasse
        val avvisi = Analisi.avvisi(nov, ott, oggi)
        assertEquals(listOf("Netto in aumento del +10,2%"), avvisi.filter { it.gravita == Gravita.AVVISO }.map { it.titolo })
        assertTrue(avvisi.any { it.titolo.startsWith("Lordo in aumento") })
        // cedolino coerente: lordo − trattenute = netto
        assertTrue(avvisi.none { it.titolo == "Altre voci non riepilogate" })
    }

    // ------------------------------------------------------------ stima IRPEF

    @Test fun scaglioniIrpef2026() {
        assertEquals(0.0, Analisi.irpefLorda(0.0), 0.001)
        assertEquals(4600.0, Analisi.irpefLorda(20_000.0), 0.001)
        assertEquals(6440.0 + 3300.0, Analisi.irpefLorda(38_000.0), 0.001)
        assertEquals(6440.0 + 7260.0 + 4300.0, Analisi.irpefLorda(60_000.0), 0.001)
    }

    @Test fun detrazioneLavoroDipendente() {
        assertEquals(1955.0, Analisi.detrazioneLavoroDipendente(10_000.0), 0.001)
        assertEquals(1910.0 + 1190.0 * 8000 / 13000, Analisi.detrazioneLavoroDipendente(20_000.0), 0.01)
        assertEquals(1910.0 * 20000 / 22000 + 65, Analisi.detrazioneLavoroDipendente(30_000.0), 0.01)
        assertEquals(0.0, Analisi.detrazioneLavoroDipendente(60_000.0), 0.001)
    }

    @Test fun stimaUsaImponibileOLordoMenoContributi() {
        val conImponibile = Analisi.stimaIrpef(base().copy(imponibileFiscale = 2000.0))!!
        assertEquals(24_000.0, conImponibile.imponibileAnnuo, 0.001)
        val senza = Analisi.stimaIrpef(base())!!
        assertEquals(2725.0, senza.imponibileMensile, 0.001)
        val attesa = (Analisi.irpefLorda(32_700.0) - Analisi.detrazioneLavoroDipendente(32_700.0)) / 12
        assertEquals(attesa, senza.irpefMensileStimata, 0.01)
        assertEquals(500.0 - attesa, senza.differenza!!, 0.01)
    }

    @Test fun stimaNonDisponibile() {
        assertNull(Analisi.stimaIrpef(Cedolino(mese = 1, anno = 2026, lordo = 2000.0)))
        assertNull(Analisi.stimaIrpef(base().copy(aggiuntiva = true)))
    }

    @Test fun stimaMaiNegativa() {
        val s = Analisi.stimaIrpef(Cedolino(mese = 1, anno = 2026, imponibileFiscale = 600.0))!!
        assertEquals(0.0, s.irpefMensileStimata, 0.001)
    }

    @Test fun irpefLontanaDallaStimaSegnalata() {
        assertTrue(titoli(base().copy(irpef = 20.0, netto = 2680.0)).contains("IRPEF lontana dalla stima"))
    }

    // ------------------------------------------------------------ formattazione

    @Test fun formatoEuro() {
        assertEquals("€ 3.250,00", Analisi.euro(3250.0))
        assertEquals("−€ 12,50", Analisi.euro(-12.5))
    }

    @Test fun formatoNumero() {
        assertEquals("18", Analisi.num(18.0))
        assertEquals("7,50", Analisi.num(7.5))
    }

    // ------------------------------------------------------------ OCR: ricomposizione righe

    @Test fun righeRicompostePerAltezza() {
        val f = listOf(
            Frammento("2.404,68", 600, 102, 122),
            Frammento("Netto in busta", 20, 100, 120),
            Frammento("Lordo", 20, 50, 70),
            Frammento("3.250,00", 600, 52, 71),
        )
        assertEquals("Lordo  3.250,00\nNetto in busta  2.404,68", ricomponiRighe(f))
    }

    @Test fun ocrRicompostoVieneRiconosciuto() {
        val testo = ricomponiRighe(listOf(
            Frammento("Periodo: Novembre 2026", 20, 10, 30),
            Frammento("NETTO IN BUSTA", 20, 200, 220),
            Frammento("2.404,68", 700, 203, 221),
        ))
        val c = ParserCedolino.analizza(testo, 2026, 1).cedolino
        assertEquals(11, c.mese)
        assertNotNull(c.netto)
        assertEquals(2404.68, c.netto!!, 0.001)
    }

    @Test fun righeVuote() = assertEquals("", ricomponiRighe(emptyList()))
}
