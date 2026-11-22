package com.matchmind.app

import com.matchmind.app.dominio.Strategia
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
import com.matchmind.app.dominio.TipoQuota
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

// Apprendimento dall'esperienza e composizione della bolletta. © Roberto Di Flumeri
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

    // ── Affidabilità delle squadre ──────────────────────────────────────────

    @Test fun squadraCheDeludeLePrevisioniVienePenalizzata() {
        // "fav" data vincente al 75% in 12 partite, ma ne vince solo 4 (e ne pareggia 4); "solida" vince sempre come previsto
        val oss = (0 until 12).map { i ->
            Osservazione("d$i", Sport.CALCIO, "ita.1", Probabilita(0.75, 0.17, 0.08), null,
                when (i % 3) { 0 -> Esito.CASA; 1 -> Esito.PAREGGIO; else -> Esito.OSPITE }, casaId = "fav", ospiteId = "x$i")
        } + (0 until 12).map { i ->
            Osservazione("s$i", Sport.CALCIO, "ita.1", Probabilita(0.75, 0.17, 0.08), null, Esito.CASA, casaId = "solida", ospiteId = "y$i")
        }
        val e = Esperienza.impara(Sport.CALCIO, oss)
        assertTrue("fav ${e.affidabilitaSquadra("fav")}", e.affidabilitaSquadra("fav") < Generatore.AFFIDABILITA_MINIMA)
        assertTrue(e.affidabilitaSquadra("solida") > 1.0)
        assertEquals(1.0, e.affidabilitaSquadra("sconosciuta"), 0.0)
        assertEquals(1.0, e.affidabilitaSquadra(null), 0.0)
        // osservazioni vecchie senza squadre: nessuna affidabilità di squadra
        assertTrue(Esperienza.impara(Sport.CALCIO, osservazioniSimulate(100)).squadre.isEmpty())
    }

    // ── Bolletta sicura ──────────────────────────────────────────────────────

    /** Squadra di casa forte (10 vinte su 12) e ospite debole: lo storico conferma il pronostico "1". */
    private val forte = StatSquadra("c", "Forte", 12, 10, 1, 1, 28.0, 8.0)
    private val debole = StatSquadra("o", "Debole", 12, 2, 3, 7, 9.0, 22.0)

    private fun analisi(
        id: String, vararg opzioni: Opzione, tra: Long = 24 * ora, stato: StatoEvento = StatoEvento.PROGRAMMATO, affid: Double = 1.0,
        casa: StatSquadra? = forte, ospite: StatSquadra? = debole, affidCasa: Double = 1.0, affidOspite: Double = 1.0,
    ) = Analisi(evento(id, Quote(2.0, 3.0, 4.0), tra, stato = stato), null, null, Probabilita(0.5, 0.3, 0.2), null, null, opzioni.toList(), affid,
        casa, ospite, affidCasa, affidOspite)

    /** Pronostico con mercato e statistica (entrambi richiesti). */
    private fun opS(p: Double, quota: Double, pMercato: Double? = p, pModello: Double? = p, s: Scommessa = Scommessa.CASA, tipo: TipoQuota = TipoQuota.REALE) =
        Opzione(s, p, quota, tipo, pMercato, pModello)

    private fun sicura(vararg a: Analisi) = Generatore.bollettaSicura(a.toList(), adesso)

    @Test fun bollettaUnicaConCalcioEBasket() {
        val calcio = analisi("c", opS(0.80, 1.20))
        val basket = analisi("b", opS(0.75, 1.25)).let { it.copy(evento = it.evento.copy(sport = Sport.BASKET, lega = "nba")) }
        val s = sicura(calcio, basket)
        assertEquals(setOf(Sport.CALCIO, Sport.BASKET), s.pronostici.map { it.evento.sport }.toSet())
        assertNull(s.sport)
        // tutte dello stesso sport: la bolletta prende quello sport
        assertEquals(Sport.CALCIO, sicura(calcio).sport)
    }

    @Test fun numeroDiPartiteDecisoPerVincerne1Su2() {
        // 0,75² = 56,25% ≥ 50%, 0,75³ = 42% no: due partite
        val s = sicura(*(1..6).map { analisi("$it", opS(0.75, 1.06), tra = (24 + it) * ora) }.toTypedArray())
        assertEquals(2, s.pronostici.size)
        assertTrue(s.probabilita >= Generatore.PROBABILITA_BOLLETTA_SICURA)
        assertEquals(1.06 * 1.06, s.quotaTotale, 1e-9)
        // il motivo dell'affidabilità arriva fino all'anteprima
        assertTrue(s.pronostici.all { it.storico == "${it.evento.casa} in stagione: 10 vinte su 12" })
        // pronostici sicurissimi: al massimo tre partite
        assertEquals(Generatore.MAX_PARTITE_SICURA, sicura(*(1..6).map { analisi("s$it", opS(0.98, 1.03)) }.toTypedArray()).pronostici.size)
    }

    @Test fun nienteBollettaSeNessunaPartitaESicura() {
        val giovane = StatSquadra("g", "Giovane", 4, 4, 0, 0, 10.0, 2.0)
        val incostante = StatSquadra("i", "Incostante", 12, 5, 5, 2, 20.0, 12.0) // vince il 42%
        val s = sicura(
            analisi("sotto50", opS(0.49, 1.08)),
            analisi("mercato49", opS(0.92, 1.06, pMercato = 0.49)),
            analisi("senza_statistica", opS(0.92, 1.06, pModello = null)),
            analisi("quota_troppo_bassa", opS(0.97, 1.02)),
            analisi("quota_stimata", opS(0.92, 1.06, tipo = TipoQuota.STIMATA)),
            analisi("poche_partite", opS(0.92, 1.06), casa = giovane),
            analisi("senza_classifica", opS(0.92, 1.06), ospite = null),
            analisi("storico_debole", opS(0.92, 1.06), casa = incostante),
            analisi("squadra_delude", opS(0.92, 1.06), affidCasa = 0.90),
            analisi("lega_imprevedibile", opS(0.92, 1.06), affid = 0.90),
            analisi("iniziata", opS(0.92, 1.06), tra = 5 * 60 * 1000L),
        )
        assertTrue(s.pronostici.isEmpty())
    }

    @Test fun doppiaChanceSuSquadraCheNonPerdeMai() {
        // l'incostante non vince abbastanza per l'1, ma non perde 10 volte su 12: va bene per l'1X
        val incostante = StatSquadra("i", "Incostante", 12, 5, 5, 2, 18.0, 12.0) // vince il 42%, non perde l'83%
        val a = analisi("a", opS(0.70, 1.5), opS(0.92, 1.07, s = Scommessa.CASA_O_PAREGGIO, tipo = TipoQuota.DERIVATA), casa = incostante)
        assertEquals(Scommessa.CASA_O_PAREGGIO, sicura(a).pronostici.single().scelta)
        // l'affidabilità conta sull'ospite quando si gioca il 2
        val ospite = analisi("o", opS(0.93, 1.06, s = Scommessa.OSPITE), casa = debole, ospite = forte, affidOspite = 0.90)
        assertFalse(Generatore.ammessaSicura(ospite, ospite.opzioni.single()))
        assertTrue(Generatore.ammessaSicura(ospite.copy(affidabilitaOspite = 1.0), ospite.opzioni.single()))
    }

    @Test fun affidabilitaAbbassaLaProbabilitaMaiLaAlza() {
        val a = analisi("a", opS(0.95, 1.06), affidCasa = 0.97, tra = 24 * ora)
        val b = analisi("b", opS(0.95, 1.06), affidCasa = 1.10, tra = 26 * ora)
        assertEquals(0.95 * 0.97, sicura(a).pronostici.single().probabilita, 1e-9)
        assertEquals(0.95, sicura(b).pronostici.single().probabilita, 1e-9)
    }

    @Test fun probabilitaMaiSopraLaFontePiuPrudente() {
        // caso reale: calibrata 94%, mercato 88%, statistica 92% → in bolletta vale 88%
        val a = analisi("a", opS(0.94, 1.09, pMercato = 0.88, pModello = 0.92))
        assertEquals(0.88, sicura(a).pronostici.single().probabilita, 1e-9)
    }

    @Test fun puntataFissaAl2PerCentoDelCredito() {
        // 500 € di credito: 10 €, qualunque sia la quota (prima a quota 1,10 consigliava 200 €)
        val c = Generatore.consiglioPuntata(1.10, 500.0)!!
        assertEquals(10.0, c.puntata, 0.0)
        assertEquals(11.0, c.vincita, 1e-9)
        assertEquals(1.0, c.guadagno, 1e-9)
        assertEquals(10.0, Generatore.consiglioPuntata(3.0, 500.0)!!.puntata, 0.0)
        // euro interi per difetto, almeno 1 €
        assertEquals(2.0, Generatore.consiglioPuntata(1.5, 149.0)!!.puntata, 0.0)
        assertEquals(1.0, Generatore.consiglioPuntata(1.5, 20.0)!!.puntata, 0.0)
        assertNull(Generatore.consiglioPuntata(1.0, 500.0))
        assertNull(Generatore.consiglioPuntata(1.5, 0.5))
    }

    @Test fun singolaUnaPartitaSoloSopraL80PerCento() {
        val s = sicura(analisi("a", opS(0.90, 1.08)), analisi("b", opS(0.85, 1.12)))
        val singola = Generatore.bollettaSicura(listOf(analisi("a", opS(0.79, 1.20)), analisi("b", opS(0.85, 1.12)), analisi("c", opS(0.90, 1.08))),
            adesso, singola = true)
        assertEquals(2, s.pronostici.size)
        assertEquals(Strategia.SINGOLA, singola.strategia)
        assertEquals("c", singola.pronostici.single().evento.id)
        // sotto l'80%: nessuna singola
        assertTrue(Generatore.bollettaSicura(listOf(analisi("a", opS(0.79, 1.20))), adesso, singola = true).pronostici.isEmpty())
    }

    @Test fun finestraAGiorniDiCalendario() {
        val zona = java.time.ZoneId.of("Europe/Rome")
        val sera = java.time.ZonedDateTime.of(2026, 11, 6, 22, 0, 0, 0, zona).toInstant().toEpochMilli()
        // 1 giorno = solo oggi: fino alla mezzanotte, non le prossime 24 ore
        assertEquals(java.time.ZonedDateTime.of(2026, 11, 7, 0, 0, 0, 0, zona).toInstant().toEpochMilli(),
            com.matchmind.app.dati.fineFinestra(sera, 1, zona))
        assertEquals(java.time.ZonedDateTime.of(2026, 11, 9, 0, 0, 0, 0, zona).toInstant().toEpochMilli(),
            com.matchmind.app.dati.fineFinestra(sera, 3, zona))
    }
}
