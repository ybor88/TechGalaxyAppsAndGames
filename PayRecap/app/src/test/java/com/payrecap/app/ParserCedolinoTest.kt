// PayRecap — © Roberto Di Flumeri
package com.payrecap.app

import com.payrecap.app.dominio.Cedolino
import com.payrecap.app.dominio.Esempi
import com.payrecap.app.dominio.ParserCedolino
import com.payrecap.app.dominio.meseDaNome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserCedolinoTest {

    private fun analizza(t: String) = ParserCedolino.analizza(t.trimIndent(), 2026, 1).cedolino

    // ------------------------------------------------------------ numeri

    @Test fun numeroItaliano() = assertEquals(3250.0, ParserCedolino.parseNumero("3.250,00")!!, 0.001)
    @Test fun numeroItalianoSenzaMigliaia() = assertEquals(845.32, ParserCedolino.parseNumero("845,32")!!, 0.001)
    @Test fun numeroInglese() = assertEquals(1234.56, ParserCedolino.parseNumero("1,234.56")!!, 0.001)
    @Test fun numeroPuntoDecimale() = assertEquals(12.5, ParserCedolino.parseNumero("12.50")!!, 0.001)
    @Test fun numeroPuntoMigliaia() = assertEquals(1250.0, ParserCedolino.parseNumero("1.250")!!, 0.001)
    @Test fun numeroMilioni() = assertEquals(1234567.89, ParserCedolino.parseNumero("1.234.567,89")!!, 0.001)
    @Test fun numeroNegativoInCoda() = assertEquals(-15.0, ParserCedolino.parseNumero("15,00-")!!, 0.001)
    @Test fun numeroQuattroDecimali() = assertEquals(22.68, ParserCedolino.parseNumero("22,6800")!!, 0.0001)

    @Test fun numeriIgnoranoDatePercentualiEFrazioni() {
        assertEquals(listOf(33.4), ParserCedolino.numeri("Addizionale regionale rata 10/11 dal 01/01/2026 al 9,19% 33,40"))
    }

    @Test fun meseDaNomeCompletoEBreve() {
        assertEquals(11, meseDaNome("Novembre"))
        assertEquals(3, meseDaNome("mar"))
        assertEquals(12, meseDaNome("dic."))
        assertNull(meseDaNome("xx"))
    }

    // ------------------------------------------------------------ cedolino di esempio completo

    @Test fun esempioNovembreTuttiICampi() {
        val r = ParserCedolino.analizza(Esempi.NOVEMBRE_2026, 2025, 1, "nov.pdf")
        val c = r.cedolino
        assertEquals(11, c.mese)
        assertEquals(2026, c.anno)
        assertFalse(c.aggiuntiva)
        assertEquals("Acme Servizi S.r.l.", c.azienda)
        assertEquals(3250.0, c.lordo!!, 0.001)
        assertEquals(2404.68, c.netto!!, 0.001)
        assertEquals(2951.65, c.imponibileFiscale!!, 0.001)
        assertEquals(498.57, c.irpef!!, 0.001)
        assertEquals(33.40, c.addizionaleRegionale!!, 0.001)
        assertEquals(15.0, c.addizionaleComunale!!, 0.001) // saldo 10 + acconto 5
        assertEquals(298.35, c.contributiPrevidenziali!!, 0.001)
        assertEquals(845.32, c.totaleTasseEContributi!!, 0.001)
        assertEquals(546.97, c.totaleTasse!!, 0.001)
        assertEquals("Tempo indeterminato", c.tipoContratto)
        assertEquals("Commercio Terziario Distribuzione e Servizi", c.ccnl)
        assertEquals("7°", c.livello)
        assertNull(c.scadenza)
        // ferie: residuo AP 4 + maturato 20 = 24, goduto 6, saldo 18
        assertEquals(24.0, c.ferieMaturate!!, 0.001)
        assertEquals(6.0, c.ferieGodute!!, 0.001)
        assertEquals(18.0, c.ferieResidue!!, 0.001)
        assertEquals(Cedolino.UNITA_GIORNI, c.unitaFerie)
        assertEquals(62.0, c.rolMaturati!!, 0.001)
        assertEquals(14.0, c.rolGoduti!!, 0.001)
        assertEquals(48.0, c.rolResidui!!, 0.001)
        assertEquals(Cedolino.UNITA_ORE, c.unitaRol)
        // straordinario: 8 + 2 pagate, 2 accantonate
        assertEquals(10.0, c.straordinarioPagate!!, 0.001)
        assertEquals(2.0, c.straordinarioAccantonate!!, 0.001)
        assertEquals(12.0, c.straordinarioEffettuate!!, 0.001)
        assertEquals(10.0, c.bancaOreResidua!!, 0.001)
        assertEquals(r.campiTotali, r.campiRiconosciuti)
        assertTrue(r.periodoTrovato)
        assertEquals("nov.pdf", c.nomeFile)
    }

    @Test fun esempioOttobre() {
        val c = ParserCedolino.analizza(Esempi.OTTOBRE_2026, 2026, 1).cedolino
        assertEquals(10, c.mese)
        assertEquals(2895.60, c.lordo!!, 0.001)
        assertEquals(2182.90, c.netto!!, 0.001)
        assertEquals(712.70, c.totaleTasseEContributi!!, 0.001)
        assertEquals(6.0, c.straordinarioPagate!!, 0.001)
        assertEquals(6.0, c.straordinarioEffettuate!!, 0.001)
        assertNull(c.straordinarioAccantonate)
        assertEquals(20.0, c.ferieResidue!!, 0.001)
    }

    // ------------------------------------------------------------ formati alternativi

    @Test fun etichetteSullaStessaRiga() {
        val c = analizza("""
            Mese di Marzo 2026
            Totale lordo: 2.100,00
            Ritenute previdenziali 193,20
            Ritenute IRPEF 280,15
            Add.le Reg. 25,00
            Add.le Com. 8,00
            Netto a pagare 1.593,65
        """)
        assertEquals(3, c.mese)
        assertEquals(2100.0, c.lordo!!, 0.001)
        assertEquals(193.20, c.contributiPrevidenziali!!, 0.001)
        assertEquals(280.15, c.irpef!!, 0.001)
        assertEquals(25.0, c.addizionaleRegionale!!, 0.001)
        assertEquals(8.0, c.addizionaleComunale!!, 0.001)
        assertEquals(1593.65, c.netto!!, 0.001)
    }

    @Test fun piuEtichetteSullaStessaRiga() {
        val c = analizza("""
            Totale competenze 2.216,80 Totale trattenute 535,78
            NETTO A PAGARE 1.681,02
            Lordo 2.216,80 Netto 1.681,02
        """)
        assertEquals(2216.80, c.lordo!!, 0.001)
        assertEquals(1681.02, c.netto!!, 0.001)
    }

    @Test fun aziendaSenzaTestoDelleAltreColonne() {
        assertEquals("Beta Logistica S.p.A.", analizza("Ragione sociale: Beta Logistica S.p.A. Cedolino paga").azienda)
        assertEquals("Gamma S.r.l.", analizza("Azienda: Gamma S.r.l. P.IVA 01234567890").azienda)
        assertEquals("Delta Servizi", analizza("Datore di lavoro: Delta Servizi  Via Roma 1").azienda)
    }

    @Test fun livelloSuper() {
        assertEquals("5° Super", ParserCedolino.trovaLivello(listOf("ccnl logistica livello 5° super")))
        assertEquals("7°", ParserCedolino.trovaLivello(listOf("livello: 7   tipo rapporto")))
    }

    @Test fun irpefNonConfusaConImponibileODetrazioni() {
        val c = analizza("""
            Imponibile IRPEF 2.500,00
            IRPEF lorda 575,00
            Detrazioni IRPEF 150,00
            IRPEF 425,00
        """)
        assertEquals(425.0, c.irpef!!, 0.001)
        assertEquals(2500.0, c.imponibileFiscale!!, 0.001)
    }

    @Test fun progressiviIgnorati() {
        val c = analizza("""
            Imponibile progressivo 25.000,00   Netto progressivo 19.000,00
            Netto del mese 1.700,00
        """)
        assertEquals(1700.0, c.netto!!, 0.001)
    }

    @Test fun periodoNumericoETredicesima() {
        val c = analizza("""
            Periodo 13/2026
            Netto 2.000,00
        """)
        assertEquals(12, c.mese)
        assertEquals(2026, c.anno)
        assertTrue(c.aggiuntiva)
    }

    @Test fun tredicesimaDaTesto() {
        val c = analizza("""
            Cedolino tredicesima mensilità - Dicembre 2026
            Netto 1.900,00
        """)
        assertTrue(c.aggiuntiva)
        assertEquals(12, c.mese)
    }

    @Test fun rateoTredicesimaNonRendeAggiuntiva() {
        val c = analizza("""
            Periodo: Maggio 2026
            Rateo tredicesima mese 250,00
        """)
        assertFalse(c.aggiuntiva)
    }

    @Test fun periodoMancanteUsaIlDefault() {
        val r = ParserCedolino.analizza("Netto 1.000,00", 2025, 7)
        assertEquals(7, r.cedolino.mese)
        assertEquals(2025, r.cedolino.anno)
        assertFalse(r.periodoTrovato)
        assertTrue(r.campiMancanti.contains("Lordo"))
    }

    @Test fun contrattoDeterminatoConScadenzaEPartTime() {
        val c = analizza("""
            Rapporto: tempo determinato part-time 50%
            C.C.N.L. Metalmeccanici Industria   Livello: C2
            Data scadenza contratto: 31/03/2027
        """)
        assertEquals("Tempo determinato · Part-time 50%", c.tipoContratto)
        assertEquals("Metalmeccanici Industria", c.ccnl)
        assertEquals("C2", c.livello)
        assertEquals("31/03/2027", c.scadenza)
    }

    @Test fun livelloConSimboloPrima() {
        assertEquals("3°", ParserCedolino.trovaLivello(listOf("inquadramento 3° livello super")))
        assertEquals("Quadro", ParserCedolino.trovaLivello(listOf("livello: quadro")))
    }

    @Test fun apprendistato() = assertEquals("Apprendistato", ParserCedolino.trovaTipoContratto("contratto di apprendistato professionalizzante"))

    @Test fun ferieEtichetteEsplicite() {
        val c = analizza("""
            Ferie maturate 20,00 gg
            Ferie godute 5,00
            Permessi ROL residui 30,00
            ROL maturati 40,00
        """)
        assertEquals(20.0, c.ferieMaturate!!, 0.001)
        assertEquals(5.0, c.ferieGodute!!, 0.001)
        assertEquals(15.0, c.ferieResidue!!, 0.001) // calcolato
        assertEquals(30.0, c.rolResidui!!, 0.001)
        assertEquals(40.0, c.rolMaturati!!, 0.001)
        assertEquals(10.0, c.rolGoduti!!, 0.001)
    }

    @Test fun ferieInOreRiconosciute() {
        val c = analizza("""
            Ferie (ore)   160,00   40,00   120,00
        """)
        assertEquals(Cedolino.UNITA_ORE, c.unitaFerie)
        assertEquals(120.0, c.ferieResidue!!, 0.001)
    }

    @Test fun ferieTabellaTreColonneSenzaIntestazione() {
        val c = analizza("Ferie 22,00 4,00 18,00")
        assertEquals(22.0, c.ferieMaturate!!, 0.001)
        assertEquals(4.0, c.ferieGodute!!, 0.001)
        assertEquals(18.0, c.ferieResidue!!, 0.001)
        assertEquals(Cedolino.UNITA_GIORNI, c.unitaFerie)
    }

    @Test fun rolNonConfusoConParoleCheContengonoRol() {
        val c = analizza("Controllo presenze 10,00 20,00 30,00")
        assertNull(c.rolMaturati)
        assertNull(c.rolResidui)
    }

    @Test fun straordinarioEspliciti() {
        val c = analizza("""
            Ore straordinario effettuate 15,00
            Straordinario pagato 10,00
            Ore accantonate 3,00
            Saldo banca ore 21,50
        """)
        assertEquals(15.0, c.straordinarioEffettuate!!, 0.001)
        assertEquals(10.0, c.straordinarioPagate!!, 0.001)
        assertEquals(3.0, c.straordinarioAccantonate!!, 0.001)
        assertEquals(21.5, c.bancaOreResidua!!, 0.001)
    }

    @Test fun straordinarioSoloImportoNonDiventaOre() {
        val c = analizza("Straordinario 150,00")
        assertNull(c.straordinarioPagate)
        assertNull(c.straordinarioEffettuate)
    }

    @Test fun testoVuotoNessunCampo() {
        val r = ParserCedolino.analizza("", 2026, 5)
        assertEquals(0, r.campiRiconosciuti)
        assertNull(r.cedolino.lordo)
    }
}
