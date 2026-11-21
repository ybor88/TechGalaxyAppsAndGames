package com.matchmind.app

import com.matchmind.app.dominio.Contabilita
import com.matchmind.app.dominio.Movimento
import com.matchmind.app.dominio.SchedinaChiusa
import com.matchmind.app.dominio.Sport
import com.matchmind.app.dominio.StatoSchedina
import com.matchmind.app.dominio.TipoMovimento
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

// Il cashout non esiste più, ma le bollette vecchie chiuse così restano nel saldo e nelle statistiche. © Roberto Di Flumeri
class StoricoCashoutTest {

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
