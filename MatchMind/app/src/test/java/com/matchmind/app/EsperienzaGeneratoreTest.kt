package com.matchmind.app

import com.matchmind.app.dominio.Analisi
import com.matchmind.app.dominio.Esito
import com.matchmind.app.dominio.Esperienza
import com.matchmind.app.dominio.Evento
import com.matchmind.app.dominio.Generatore
import com.matchmind.app.dominio.Mercato
import com.matchmind.app.dominio.Opzione
import com.matchmind.app.dominio.Osservazione
import com.matchmind.app.dominio.Probabilita
import com.matchmind.app.dominio.Quote
import com.matchmind.app.dominio.Scommessa
import com.matchmind.app.dominio.Sport
import com.matchmind.app.dominio.StatSquadra
import com.matchmind.app.dominio.StatoEvento
import com.matchmind.app.dominio.Strategia
import com.matchmind.app.dominio.TipoQuota
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

// Apprendimento dall'esperienza e composizione della schedina. © Roberto Di Flumeri
class EsperienzaGeneratoreTest {

    private val adesso = 1_800_000_000_000L
    private val ora = 3_600_000L

    private fun evento(id: String, quote: Quote?, tra: Long = 24 * ora, lega: String = "ita.1", sport: Sport = Sport.CALCIO, stato: StatoEvento = StatoEvento.PROGRAMMATO) =
        Evento(id, sport, lega, adesso + tra, "c$id", "Casa $id", "o$id", "Ospite $id", stato, quote = quote)

    /** Osservazioni simulate: la verità è il modello, il "mercato" è rumoroso. */
    private fun osservazioniSimulate(n: Int, seme: Int = 7): List<Osservazione> {
        val r = Random(seme)
        return (0 until n).map { i ->
            val vera = 0.35 + r.nextDouble() * 0.5
            val pareggio = 0.25 * (1 - vera)
            val verita = Probabilita(vera, pareggio, 1 - vera - pareggio)
            val rumore = (r.nextDouble() - 0.5) * 0.5
            val mercato = Probabilita((vera + rumore).coerceIn(0.05, 0.9), pareggio, 0.0).let {
                Probabilita(it.casa, it.pareggio, (1 - it.casa - it.pareggio).coerceAtLeast(0.02))
            }.normalizzata()
            val u = r.nextDouble()
            val esito = when {
                u < verita.casa -> Esito.CASA
                u < verita.casa + verita.pareggio -> Esito.PAREGGIO
                else -> Esito.OSPITE
            }
            Osservazione("e$i", Sport.CALCIO, if (i % 2 == 0) "ita.1" else "eng.1", mercato, verita, esito)
        }
    }

    // ── Esperienza ───────────────────────────────────────────────────────────

    @Test fun senzaEsperienzaValoriPrudenti() {
        val e = Esperienza.impara(Sport.CALCIO, emptyList())
        assertEquals(Esperienza.PESO_MERCATO_INIZIALE, e.pesoMercato, 0.0)
        assertEquals(0.7, e.calibra(0.7), 0.0)
        assertEquals(0.7, e.calibra(0.7, Mercato.GOAL), 0.0)
        assertEquals(1.0, e.affidabilita("ita.1"), 0.0)
    }

    @Test fun imparaAFidarsiDellaFonteMigliore() {
        val e = Esperienza.impara(Sport.CALCIO, osservazioniSimulate(3000))
        assertTrue("peso mercato ${e.pesoMercato}", e.pesoMercato < 0.4)
        assertEquals(3000, e.osservazioni)
    }

    @Test fun pochiDatiPesoQuasiInvariato() {
        assertTrue(Esperienza.impara(Sport.CALCIO, osservazioniSimulate(40)).pesoMercato > 0.5)
    }

    @Test fun calibrazioneCorreggeLOttimismo() {
        // favorite date all'85% che vincono solo il 60% delle volte
        val oss = (0 until 400).map { i ->
            Osservazione("x$i", Sport.CALCIO, "ita.1", Probabilita(0.85, 0.1, 0.05), null,
                if (i % 10 < 6) Esito.CASA else Esito.PAREGGIO)
        }
        val e = Esperienza.impara(Sport.CALCIO, oss)
        val corretta = e.calibra(0.85, Mercato.VINCENTE)
        assertTrue("calibrata $corretta", corretta < 0.66 && corretta > 0.58)
        assertTrue(e.affidabilita("ita.1") < 1.0)
        assertEquals(0.6, e.precisione, 1e-9)
        // il pareggio, dato al 10%, è uscito il 40% delle volte: la calibrazione lo alza
        assertTrue(e.calibra(0.10, Mercato.PAREGGIO) > 0.2)
    }

    @Test fun calibrazioneGoalSuiGolReali() {
        // Goal previsto al 70% ma le partite finiscono sempre 1-0: GG mai uscito
        val oss = (0 until 200).map { i ->
            Osservazione("g$i", Sport.CALCIO, "ita.1", Probabilita(0.5, 0.3, 0.2), null, Esito.CASA,
                pGoal = 0.7, pOver = 0.6, golCasa = 1, golOspite = 0)
        }
        val e = Esperienza.impara(Sport.CALCIO, oss)
        assertTrue(e.calibra(0.7, Mercato.GOAL) < 0.4)
        assertTrue(e.calibra(0.7, Mercato.VINCENTE) > 0.69) // gli altri tipi non ne risentono
        val goal = e.riepilogo.first { it.mercato == Mercato.GOAL }
        assertEquals(200, goal.n)
        assertEquals(0.0, goal.reale, 1e-9)
    }

    @Test fun esperienzaSeparataPerSport() {
        assertEquals(0, Esperienza.impara(Sport.BASKET, osservazioniSimulate(200)).osservazioni)
    }

    // ── Analisi: tutte le giocate ───────────────────────────────────────────

    private val esp = Esperienza.vuota(Sport.CALCIO)

    @Test fun analisiCalcioValutaTuttiITipi() {
        val q = Quote(1.50, 4.20, 6.50, lineaGol = 2.5, over = 1.70, under = 2.15)
        val a = Generatore.analizza(evento("1", q), emptyMap(), 1.35, esp)!!
        assertEquals(Scommessa.entries.toSet(), a.opzioni.map { it.scommessa }.toSet())
        val o = a.opzioni.associateBy { it.scommessa }
        assertEquals(1.0, o.getValue(Scommessa.CASA).probabilita + o.getValue(Scommessa.PAREGGIO).probabilita + o.getValue(Scommessa.OSPITE).probabilita, 1e-6)
        assertEquals(o.getValue(Scommessa.CASA).probabilita + o.getValue(Scommessa.PAREGGIO).probabilita, o.getValue(Scommessa.CASA_O_PAREGGIO).probabilita, 1e-6)
        assertEquals(1.0, o.getValue(Scommessa.GOAL).probabilita + o.getValue(Scommessa.NO_GOAL).probabilita, 1e-6)
        // quote: 1X2 e Under/Over reali, doppia chance derivata, Goal stimata
        assertEquals(TipoQuota.REALE, o.getValue(Scommessa.OVER).tipoQuota)
        assertEquals(1.70, o.getValue(Scommessa.OVER).quota, 0.0)
        assertEquals(TipoQuota.DERIVATA, o.getValue(Scommessa.CASA_O_PAREGGIO).tipoQuota)
        assertTrue(o.getValue(Scommessa.CASA_O_PAREGGIO).quota < 1.50)
        assertEquals(TipoQuota.STIMATA, o.getValue(Scommessa.GOAL).tipoQuota)
        // l'Over del mercato (circa 56%) viene rispettato
        assertEquals(0.56, a.pOver!!, 0.04)
        assertNotNull(a.pGoal)
    }

    @Test fun basketSoloVincente() {
        val a = Generatore.analizza(evento("b", Quote(1.4, null, 3.0), sport = Sport.BASKET, lega = "nba"), emptyMap(), 1.35, Esperienza.vuota(Sport.BASKET))!!
        assertEquals(setOf(Scommessa.CASA, Scommessa.OSPITE), a.opzioni.map { it.scommessa }.toSet())
        assertNull(a.pGoal)
    }

    @Test fun senzaQuoteUsaModelloEQuotaStimata() {
        val classifica = mapOf(
            "c1" to StatSquadra("c1", "A", 10, 8, 1, 1, 24.0, 6.0),
            "o1" to StatSquadra("o1", "B", 10, 1, 2, 7, 7.0, 20.0),
        )
        val a = Generatore.analizza(evento("1", null), classifica, 1.35, esp)!!
        val casa = a.opzioni.first { it.scommessa == Scommessa.CASA }
        assertTrue(casa.tipoQuota == TipoQuota.STIMATA && casa.probabilita > 0.5 && a.mercato == null)
        assertNull(Generatore.analizza(evento("2", null), emptyMap(), 1.35, esp))
    }

    // ── Composizione della schedina ─────────────────────────────────────────

    private fun analisi(id: String, vararg opzioni: Opzione, tra: Long = 24 * ora, stato: StatoEvento = StatoEvento.PROGRAMMATO, affid: Double = 1.0) =
        Analisi(evento(id, Quote(2.0, 3.0, 4.0), tra, stato = stato), null, null, Probabilita(0.5, 0.3, 0.2), null, null, opzioni.toList(), affid)

    private fun op(s: Scommessa, p: Double, quota: Double, tipo: TipoQuota = TipoQuota.REALE) = Opzione(s, p, quota, tipo)

    @Test fun numeroDiPartiteDecisoDallaSicurezza() {
        val lista = (1..20).map { analisi("$it", op(Scommessa.CASA, 0.80, 1.22)) }
        // 0.8^5 = 0.33 ≥ 30%, 0.8^6 = 0.26 < 30%: cinque partite
        assertEquals(5, Generatore.genera(Sport.CALCIO, lista, Strategia.SICURA, adesso, sicurezza = 0.30).pronostici.size)
        // soglia più bassa → più partite; più alta → meno
        assertEquals(10, Generatore.genera(Sport.CALCIO, lista, Strategia.SICURA, adesso, sicurezza = 0.10).pronostici.size) // 0.8^10 = 0.107
        assertEquals(2, Generatore.genera(Sport.CALCIO, lista, Strategia.SICURA, adesso, sicurezza = 0.60).pronostici.size)
        // pronostici più sicuri → più partite a parità di soglia
        val sicurissime = (1..20).map { analisi("s$it", op(Scommessa.CASA, 0.92, 1.16)) }
        assertTrue(Generatore.genera(Sport.CALCIO, sicurissime, Strategia.SICURA, adesso, sicurezza = 0.30).pronostici.size > 5)
        val s = Generatore.genera(Sport.CALCIO, lista, Strategia.SICURA, adesso, sicurezza = 0.30)
        assertTrue(s.probabilita >= 0.30)
    }

    @Test fun perOgniPartitaLaGiocataPiuSicura() {
        val a = analisi("1", op(Scommessa.CASA, 0.72, 1.30), op(Scommessa.CASA_O_PAREGGIO, 0.88, 1.17, TipoQuota.DERIVATA),
            op(Scommessa.GOAL, 0.60, 1.60, TipoQuota.STIMATA), op(Scommessa.UNDER, 0.81, 1.20))
        val s = Generatore.genera(Sport.CALCIO, listOf(a), Strategia.SICURA, adesso)
        assertEquals(Scommessa.CASA_O_PAREGGIO, s.pronostici.single().scelta)
        // tipo di pronostico disattivato → si passa al migliore tra quelli ammessi
        val senzaDoppia = Generatore.genera(Sport.CALCIO, listOf(a), Strategia.SICURA, adesso,
            mercati = Mercato.entries.toSet() - Mercato.DOPPIA_CHANCE)
        assertEquals(Scommessa.UNDER, senzaDoppia.pronostici.single().scelta)
        // il vincente resta sempre ammesso
        val soloVincente = Generatore.genera(Sport.CALCIO, listOf(a), Strategia.SICURA, adesso, mercati = emptySet())
        assertEquals(Scommessa.CASA, soloVincente.pronostici.single().scelta)
    }

    @Test fun esclusePartiteIniziateAmichevoliEQuoteBasse() {
        val amichevole = analisi("amichevole", op(Scommessa.CASA, 0.9, 1.25)).let { it.copy(evento = it.evento.copy(amichevole = true)) }
        val lista = listOf(
            analisi("iniziata", op(Scommessa.CASA, 0.9, 1.25), tra = 5 * 60 * 1000L),
            analisi("in_corso", op(Scommessa.CASA, 0.9, 1.25), stato = StatoEvento.IN_CORSO),
            analisi("incerta", op(Scommessa.CASA, 0.55, 1.75)),
            analisi("quota_bassa", op(Scommessa.CASA_O_PAREGGIO, 0.97, 1.05)),
            amichevole,
            analisi("buona", op(Scommessa.CASA, 0.8, 1.3)),
        )
        val s = Generatore.genera(Sport.CALCIO, lista, Strategia.SICURA, adesso, sicurezza = 0.05)
        assertEquals(listOf("buona"), s.pronostici.map { it.evento.id })
    }

    @Test fun nessunPronosticoSicuroSchedinaVuota() {
        val s = Generatore.genera(Sport.CALCIO, listOf(analisi("a", op(Scommessa.CASA, 0.6, 1.6))), Strategia.SICURA, adesso)
        assertTrue(s.pronostici.isEmpty())
    }

    @Test fun affidabilitaDellaLegaInfluenzaLaScelta() {
        val lista = listOf(analisi("a", op(Scommessa.CASA, 0.80, 1.25), affid = 0.85), analisi("b", op(Scommessa.CASA, 0.76, 1.3), affid = 1.1))
        val s = Generatore.genera(Sport.CALCIO, lista, Strategia.SICURA, adesso, massimo = 1)
        assertEquals("b", s.pronostici.single().evento.id)
    }

    @Test fun strategiaValorePreferisceQuoteConValore() {
        val lista = listOf(
            analisi("sicura_senza_valore", op(Scommessa.CASA, 0.90, 1.08)),
            analisi("valore", op(Scommessa.OVER, 0.70, 1.60)),
            analisi("stimata", op(Scommessa.GOAL, 0.80, 1.50, TipoQuota.STIMATA)),
        )
        val s = Generatore.genera(Sport.CALCIO, lista, Strategia.VALORE, adesso, sicurezza = 0.5)
        assertEquals("valore", s.pronostici.single().evento.id)
        assertTrue(s.valoreAtteso(10.0) > 0)
    }

    @Test fun catenaCompletaQuoteETotali() {
        val lista = listOf(analisi("a", op(Scommessa.CASA, 0.8, 1.3)), analisi("b", op(Scommessa.NO_GOAL, 0.75, 1.4)))
        val s = Generatore.genera(Sport.CALCIO, lista, Strategia.SICURA, adesso, sicurezza = 0.5)
        assertEquals(2, s.pronostici.size)
        assertEquals(1.3 * 1.4, s.quotaTotale, 1e-9)
        assertEquals(0.8 * 0.75, s.probabilita, 1e-9)
    }
}
