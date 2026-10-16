package com.matchmind.app

import com.matchmind.app.dominio.Contabilita
import com.matchmind.app.dominio.Esito
import com.matchmind.app.dominio.EsitoSelezione
import com.matchmind.app.dominio.Evento
import com.matchmind.app.dominio.Movimento
import com.matchmind.app.dominio.SchedinaChiusa
import com.matchmind.app.dominio.Scommessa
import com.matchmind.app.dominio.Sport
import com.matchmind.app.dominio.StatoEvento
import com.matchmind.app.dominio.StatoSchedina
import com.matchmind.app.dominio.TipoMovimento
import com.matchmind.app.dominio.Verifica
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

// Verifica dei risultati e contabilità del credito virtuale. © Roberto Di Flumeri
class VerificaContabilitaTest {

    private val inizio = 1_800_000_000_000L
    private val ora = 3_600_000L

    private fun evento(stato: StatoEvento, esito: Esito?, casa: Int = 1, ospite: Int = 0) =
        Evento("1", Sport.CALCIO, "ita.1", inizio, "a", "A", "b", "B", stato, casa, ospite, esito)

    @Test fun esitiDeiPronostici() {
        val dopo = inizio + 3 * ora
        assertEquals(EsitoSelezione.VINTA, Verifica.esitoSelezione(Scommessa.CASA, inizio, evento(StatoEvento.CONCLUSO, Esito.CASA), dopo))
        assertEquals(EsitoSelezione.PERSA, Verifica.esitoSelezione(Scommessa.CASA, inizio, evento(StatoEvento.CONCLUSO, Esito.PAREGGIO, 1, 1), dopo))
        assertEquals(EsitoSelezione.IN_ATTESA, Verifica.esitoSelezione(Scommessa.CASA, inizio, evento(StatoEvento.IN_CORSO, null), dopo))
        assertEquals(EsitoSelezione.IN_ATTESA, Verifica.esitoSelezione(Scommessa.CASA, inizio, evento(StatoEvento.PROGRAMMATO, null), dopo))
    }

    @Test fun ogniTipoDiPronosticoSulRisultatoReale() {
        fun esiti(casa: Int, ospite: Int) = Scommessa.entries.filter { it.vinta(casa, ospite) }.toSet()
        assertEquals(setOf(Scommessa.CASA, Scommessa.CASA_O_PAREGGIO, Scommessa.CASA_O_OSPITE, Scommessa.GOAL, Scommessa.OVER), esiti(2, 1))
        assertEquals(setOf(Scommessa.PAREGGIO, Scommessa.CASA_O_PAREGGIO, Scommessa.PAREGGIO_O_OSPITE, Scommessa.NO_GOAL, Scommessa.UNDER), esiti(0, 0))
        assertEquals(setOf(Scommessa.PAREGGIO, Scommessa.CASA_O_PAREGGIO, Scommessa.PAREGGIO_O_OSPITE, Scommessa.GOAL, Scommessa.UNDER), esiti(1, 1))
        assertEquals(setOf(Scommessa.OSPITE, Scommessa.PAREGGIO_O_OSPITE, Scommessa.CASA_O_OSPITE, Scommessa.NO_GOAL, Scommessa.OVER), esiti(0, 3))
        // verifica completa: Goal su un 2-2, Under su un 3-0
        val dopo = inizio + 3 * ora
        assertEquals(EsitoSelezione.VINTA, Verifica.esitoSelezione(Scommessa.GOAL, inizio, evento(StatoEvento.CONCLUSO, Esito.PAREGGIO, 2, 2), dopo))
        assertEquals(EsitoSelezione.PERSA, Verifica.esitoSelezione(Scommessa.UNDER, inizio, evento(StatoEvento.CONCLUSO, Esito.CASA, 3, 0), dopo))
        assertEquals(EsitoSelezione.VINTA, Verifica.esitoSelezione(Scommessa.PAREGGIO_O_OSPITE, inizio, evento(StatoEvento.CONCLUSO, Esito.PAREGGIO, 0, 0), dopo))
        // compatibilità con le schedine salvate prima: i nomi 1/X/2 non cambiano
        assertEquals(Scommessa.CASA, Scommessa.di("CASA"))
        assertEquals(Scommessa.PAREGGIO, Scommessa.di("PAREGGIO"))
        assertNull(Scommessa.di("INVENTATA"))
    }

    @Test fun rinviiEPartiteNonTrovate() {
        val rinviata = evento(StatoEvento.RINVIATO, null)
        assertEquals(EsitoSelezione.IN_ATTESA, Verifica.esitoSelezione(Scommessa.CASA, inizio, rinviata, inizio + 24 * ora))
        assertEquals(EsitoSelezione.NULLA, Verifica.esitoSelezione(Scommessa.CASA, inizio, rinviata, inizio + 49 * ora))
        assertEquals(EsitoSelezione.NULLA, Verifica.esitoSelezione(Scommessa.CASA, inizio, evento(StatoEvento.ANNULLATO, null), inizio + 50 * ora))
        assertEquals(EsitoSelezione.IN_ATTESA, Verifica.esitoSelezione(Scommessa.CASA, inizio, null, inizio + 24 * ora))
        assertEquals(EsitoSelezione.NULLA, Verifica.esitoSelezione(Scommessa.CASA, inizio, null, inizio + 5 * 24 * ora))
        // conclusa ai supplementari: esito dei 90' sconosciuto → nulla dopo 48 ore
        val aet = evento(StatoEvento.CONCLUSO, null)
        assertEquals(EsitoSelezione.IN_ATTESA, Verifica.esitoSelezione(Scommessa.CASA, inizio, aet, inizio + 5 * ora))
        assertEquals(EsitoSelezione.NULLA, Verifica.esitoSelezione(Scommessa.CASA, inizio, aet, inizio + 50 * ora))
    }

    @Test fun statoDellaSchedina() {
        val v = EsitoSelezione.VINTA
        val p = EsitoSelezione.PERSA
        val a = EsitoSelezione.IN_ATTESA
        val n = EsitoSelezione.NULLA
        assertEquals(StatoSchedina.PERSA, Verifica.statoSchedina(listOf(v, a, p))) // persa subito, anche con partite da giocare
        assertEquals(StatoSchedina.IN_CORSO, Verifica.statoSchedina(listOf(v, v, a)))
        assertEquals(StatoSchedina.VINTA, Verifica.statoSchedina(listOf(v, v, n)))
        assertEquals(StatoSchedina.RIMBORSATA, Verifica.statoSchedina(listOf(n, n)))
    }

    @Test fun vincitaConPronosticoNullo() {
        val q = Verifica.quotaEffettiva(listOf(1.5 to EsitoSelezione.VINTA, 2.0 to EsitoSelezione.NULLA, 1.2 to EsitoSelezione.VINTA))
        assertEquals(1.8, q, 1e-9)
        assertEquals(18.0, Verifica.vincita(10.0, StatoSchedina.VINTA, q), 1e-9)
        assertEquals(0.0, Verifica.vincita(10.0, StatoSchedina.PERSA, q), 0.0)
        assertEquals(10.0, Verifica.vincita(10.0, StatoSchedina.RIMBORSATA, 1.0), 0.0)
    }

    private fun mov(t: TipoMovimento, importo: Double, giorno: Long = 0) = Movimento(inizio + giorno * 24 * ora, t, importo)

    @Test fun saldoRicaricaEAzzeramento() {
        val m = mutableListOf(mov(TipoMovimento.RICARICA, 100.0), mov(TipoMovimento.PUNTATA, -10.0, 1),
            mov(TipoMovimento.VINCITA, 34.5, 2), mov(TipoMovimento.PUNTATA, -20.0, 3))
        assertEquals(104.5, Contabilita.saldo(m), 1e-9)
        val az = Contabilita.azzeramento(m, inizio + 4 * 24 * ora)!!
        assertEquals(-104.5, az.importo, 1e-9)
        m += az
        assertEquals(0.0, Contabilita.saldo(m), 1e-9)
        assertNull(Contabilita.azzeramento(m, inizio))
        m += mov(TipoMovimento.RICARICA, 50.0, 5)
        val b = Contabilita.bilancio(m)
        assertEquals(50.0, b.saldo, 1e-9)
        assertEquals(150.0, b.ricaricato, 1e-9)
        // il risultato del metodo non dipende da ricariche e azzeramenti
        assertEquals(4.5, b.utileMetodo, 1e-9)
        assertEquals(listOf(100.0, 90.0, 124.5, 104.5, 0.0, 50.0), Contabilita.andamento(m).map { it.second })
    }

    @Test fun debitoQuandoSiPerde() {
        val b = Contabilita.bilancio(listOf(mov(TipoMovimento.RICARICA, 100.0), mov(TipoMovimento.PUNTATA, -30.0), mov(TipoMovimento.PUNTATA, -30.0)))
        assertEquals(-60.0, b.utileMetodo, 1e-9)
    }

    @Test fun riepilogoMensileIncassatoEPerso() {
        fun t(anno: Int, mese: Int, giorno: Int) = LocalDateTime.of(anno, mese, giorno, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val schedine = listOf(
            SchedinaChiusa(Sport.CALCIO, t(2026, 9, 3), 10.0, 42.0, StatoSchedina.VINTA),
            SchedinaChiusa(Sport.CALCIO, t(2026, 9, 10), 10.0, 0.0, StatoSchedina.PERSA),
            SchedinaChiusa(Sport.BASKET, t(2026, 9, 20), 5.0, 0.0, StatoSchedina.PERSA),
            SchedinaChiusa(Sport.CALCIO, t(2026, 10, 1), 10.0, 10.0, StatoSchedina.RIMBORSATA),
            SchedinaChiusa(Sport.CALCIO, t(2026, 10, 2), 20.0, 0.0, StatoSchedina.PERSA),
        )
        val mesi = Contabilita.riepilogoMensile(schedine, ZoneOffset.UTC)
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10)), mesi.map { it.mese })
        val set = mesi[0]
        assertEquals(3, set.giocate); assertEquals(1, set.vinte); assertEquals(2, set.perse)
        assertEquals(42.0, set.incassato, 1e-9)
        assertEquals(15.0, set.perso, 1e-9)
        assertEquals(17.0, set.netto, 1e-9) // 42 - (10 + 10 + 5)
        assertEquals(-20.0, mesi[1].netto, 1e-9) // il rimborso non è né vinto né perso
    }
}
