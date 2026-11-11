package com.matchmind.app

import com.matchmind.app.dati.SelezioneEntity
import com.matchmind.app.dati.perCashout
import com.matchmind.app.dominio.Cashout
import com.matchmind.app.dominio.Contabilita
import com.matchmind.app.dominio.EsitoSelezione
import com.matchmind.app.dominio.Movimento
import com.matchmind.app.dominio.SchedinaChiusa
import com.matchmind.app.dominio.SelezioneCashout
import com.matchmind.app.dominio.Sport
import com.matchmind.app.dominio.StatoSchedina
import com.matchmind.app.dominio.TipoMovimento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

// Cashout simulato: offerta come un bookmaker e contabilità del credito virtuale. © Roberto Di Flumeri
class CashoutTest {

    private fun sel(quota: Double, p: Double, esito: EsitoSelezione = EsitoSelezione.IN_ATTESA, svantaggio: Boolean = false, persa: Boolean = false) =
        SelezioneCashout(quota, p, esito, svantaggio, persa)

    @Test fun primaDellePartiteVincitaPerProbabilitaMenoMargine() {
        // 10 € × 1,75³ = 53,59 potenziali; probabilità 0,7³ = 0,343 → 53,59 × 0,343 × 0,95 = 17,46
        val o = Cashout.offerta(10.0, List(3) { sel(1.75, 0.70) })!!
        assertEquals(10.0 * 1.75 * 1.75 * 1.75 * 0.343 * Cashout.MARGINE, o, 0.01)
    }

    @Test fun piuPartiteIndovinatePiuSaleLOfferta() {
        val zero = Cashout.offerta(10.0, List(3) { sel(1.75, 0.70) })!!
        val una = Cashout.offerta(10.0, listOf(sel(1.75, 0.70, EsitoSelezione.VINTA), sel(1.75, 0.70), sel(1.75, 0.70)))!!
        val due = Cashout.offerta(10.0, listOf(sel(1.75, 0.70, EsitoSelezione.VINTA), sel(1.75, 0.70, EsitoSelezione.VINTA), sel(1.75, 0.70)))!!
        assertTrue(zero < una && una < due)
        // due indovinate su tre: 10 × 5,36 × 0,7 × 0,95
        assertEquals(10.0 * 1.75 * 1.75 * 1.75 * 0.70 * 0.95, due, 0.01)
    }

    @Test fun inSvantaggioLOffertaScende() {
        val normale = Cashout.offerta(10.0, listOf(sel(1.75, 0.70, EsitoSelezione.VINTA), sel(1.75, 0.70)))!!
        val sotto = Cashout.offerta(10.0, listOf(sel(1.75, 0.70, EsitoSelezione.VINTA), sel(1.75, 0.70, svantaggio = true)))!!
        assertEquals(normale * Cashout.PESO_SVANTAGGIO, sotto, 0.01)
    }

    @Test fun nonDisponibileSePersaODecisa() {
        assertNull(Cashout.offerta(10.0, listOf(sel(1.75, 0.70, EsitoSelezione.PERSA), sel(1.75, 0.70))))
        assertNull(Cashout.offerta(10.0, listOf(sel(1.75, 0.70, EsitoSelezione.VINTA), sel(1.75, 0.70, persa = true))))
        assertNull(Cashout.offerta(10.0, listOf(sel(1.75, 0.70, EsitoSelezione.VINTA), sel(1.75, 0.70, EsitoSelezione.VINTA))))
    }

    @Test fun annullataContaUnoEMaiOltreIlPotenziale() {
        val o = Cashout.offerta(10.0, listOf(sel(2.0, 0.70, EsitoSelezione.NULLA), sel(1.5, 0.99)))!!
        assertEquals(10.0 * 1.5 * 0.99 * 0.95, o, 0.01)
        assertTrue(Cashout.offerta(10.0, listOf(sel(1.2, 1.5)))!! <= 12.0)
    }

    @Test fun selezioneSalvataLettaDalCashout() {
        fun s(scelta: String, risultato: String?, allerta: String?) = SelezioneEntity(1, 1, "e", "ita.1", 0, "A", "B", scelta,
            1.8, false, 0.7, 0.7, 0.7, EsitoSelezione.IN_ATTESA.name, risultato, allerta)
        assertTrue(s("UNDER", "2-1", "2-1").perCashout().giaPersa)
        assertFalse(s("UNDER", "1-1", "1-1").perCashout().giaPersa)
        assertTrue(s("CASA", "0-1", "0-1").perCashout().inSvantaggio)
        assertFalse(s("CASA", "1-0", null).perCashout().inSvantaggio)
    }

    @Test fun cashoutEntraNellIncassato() {
        val mov = listOf(Movimento(0, TipoMovimento.RICARICA, 100.0), Movimento(1, TipoMovimento.PUNTATA, -10.0, 1),
            Movimento(2, TipoMovimento.CASHOUT, 17.46, 1))
        val b = Contabilita.bilancio(mov)
        assertEquals(17.46, b.incassato, 1e-9)
        assertEquals(7.46, b.utileMetodo, 1e-9)
        assertEquals(107.46, b.saldo, 1e-9)
        val mese = Contabilita.riepilogoMensile(listOf(SchedinaChiusa(Sport.CALCIO, 0, 10.0, 17.46, StatoSchedina.CASHOUT)), ZoneOffset.UTC).single()
        assertEquals(17.46, mese.incassato, 1e-9)
        assertEquals(7.46, mese.netto, 1e-9)
        assertEquals(0, mese.vinte)
    }
}
