package com.matchmind.app.dominio

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * Verifica delle schedine sui risultati reali e contabilità del credito virtuale.
 * Regole come nei bookmaker:
 *  - calcio: conta il risultato dei 90 minuti, per tutti i tipi di pronostico (1X2, doppia chance,
 *    Goal/NoGoal, Under/Over); se la partita è decisa ai supplementari il pronostico è nullo;
 *  - partita rinviata/annullata e non giocata entro 48 ore: pronostico NULLO (quota 1.00);
 *  - basta un pronostico sbagliato perché la schedina sia persa.
 *
 * © Roberto Di Flumeri
 */
enum class EsitoSelezione { IN_ATTESA, VINTA, PERSA, NULLA }

enum class StatoSchedina(val etichetta: String) {
    IN_CORSO("In attesa"), VINTA("Vinta"), PERSA("Persa"), RIMBORSATA("Rimborsata"),
    /** Chiusa in anticipo dall'utente incassando l'offerta di cashout (simulata). */
    CASHOUT("Cashout"),
}

/** Una partita della schedina vista dal cashout: quota, probabilità prevista, esito e situazione live. */
data class SelezioneCashout(
    val quota: Double,
    val probabilita: Double,
    val esito: EsitoSelezione,
    /** In corso e il punteggio attuale è contro il pronostico (ultimo controllo). */
    val inSvantaggio: Boolean = false,
    /** Pronostico ormai perso anche se la partita non è finita (Under superato, NoGoal con entrambe a segno). */
    val giaPersa: Boolean = false,
)

/**
 * Cashout simulato, come lo calcola un bookmaker: la vincita potenziale per la probabilità che le partite
 * ancora aperte vadano bene, meno il margine trattenuto.
 *  - partite indovinate: contano con la loro quota; annullate: quota 1,00;
 *  - partite da giocare: la probabilità prevista da MatchMind;
 *  - partite in corso con il punteggio contro: probabilità ridotta ([PESO_SVANTAGGIO]);
 *  - pronostico già perso o schedina decisa: cashout non disponibile (null).
 * L'offerta non supera mai la vincita potenziale.
 *
 * © Roberto Di Flumeri
 */
object Cashout {
    /** Il bookmaker trattiene circa il 5% sul cashout. */
    const val MARGINE = 0.95
    const val PESO_SVANTAGGIO = 0.5

    fun offerta(puntata: Double, selezioni: List<SelezioneCashout>): Double? {
        if (puntata <= 0 || selezioni.isEmpty()) return null
        if (selezioni.any { it.esito == EsitoSelezione.PERSA || (it.esito == EsitoSelezione.IN_ATTESA && it.giaPersa) }) return null
        val aperte = selezioni.filter { it.esito == EsitoSelezione.IN_ATTESA }
        if (aperte.isEmpty()) return null
        val potenziale = puntata * Verifica.quotaEffettiva(selezioni.map { it.quota to it.esito })
        val pResto = aperte.fold(1.0) { acc, s -> acc * s.probabilita.coerceIn(0.0, 1.0) * if (s.inSvantaggio) PESO_SVANTAGGIO else 1.0 }
        return Verifica.arrotonda((potenziale * pResto * MARGINE).coerceAtMost(potenziale))
    }
}

object Verifica {
    const val RINVIO_MASSIMO_MS = 48 * 3600 * 1000L
    const val RICERCA_MASSIMA_MS = 4 * 24 * 3600 * 1000L

    fun esitoSelezione(scelta: Scommessa, inizio: Long, evento: Evento?, adesso: Long): EsitoSelezione {
        if (evento == null) {
            return if (adesso - inizio > RICERCA_MASSIMA_MS) EsitoSelezione.NULLA else EsitoSelezione.IN_ATTESA
        }
        return when (evento.stato) {
            StatoEvento.CONCLUSO -> {
                val casa = evento.punteggioCasa
                val ospite = evento.punteggioOspite
                when {
                    // finita ai supplementari/rigori: il risultato dei 90' non è disponibile → pronostico nullo
                    evento.esito == null || casa == null || ospite == null ->
                        if (adesso - inizio > RINVIO_MASSIMO_MS) EsitoSelezione.NULLA else EsitoSelezione.IN_ATTESA
                    scelta.vinta(casa, ospite) -> EsitoSelezione.VINTA
                    else -> EsitoSelezione.PERSA
                }
            }
            StatoEvento.RINVIATO, StatoEvento.ANNULLATO ->
                if (adesso - inizio > RINVIO_MASSIMO_MS) EsitoSelezione.NULLA else EsitoSelezione.IN_ATTESA
            else -> EsitoSelezione.IN_ATTESA
        }
    }

    /** Scarto punti oltre il quale nel basket si avvisa anche nel primo tempo. */
    const val SCARTO_BASKET = 10

    /**
     * Partita in corso: con il punteggio attuale il pronostico sta perdendo? Serve per avvisare
     * "fai cashout". Per non suonare al calcio d'inizio (0-0 "perde" un 1, un Over, un GG):
     *  - Under 2,5 con 3+ gol e NoGoal con entrambe a segno: già persi → sempre;
     *  - 1, 2, X, doppia chance: il risultato attuale è contro, dopo il primo gol oppure nel secondo tempo;
     *  - Over 2,5 e Goal non ancora raggiunti: dal secondo tempo;
     *  - basket: in svantaggio nel secondo tempo, o prima con almeno [SCARTO_BASKET] punti di distacco.
     */
    fun inSvantaggio(scelta: Scommessa, evento: Evento?): Boolean {
        if (evento == null || evento.stato != StatoEvento.IN_CORSO) return false
        val casa = evento.punteggioCasa ?: return false
        val ospite = evento.punteggioOspite ?: return false
        if (scelta.vinta(casa, ospite)) return false
        // NCAA maschile: due tempi; NBA/WNBA: quattro quarti
        val inizioSecondoTempo = if (evento.sport == Sport.BASKET && evento.lega != "mens-college-basketball") 3 else 2
        val secondoTempo = evento.periodo >= inizioSecondoTempo
        if (evento.sport == Sport.BASKET) return secondoTempo || kotlin.math.abs(casa - ospite) >= SCARTO_BASKET
        return when (scelta) {
            Scommessa.UNDER, Scommessa.NO_GOAL -> true
            Scommessa.OVER, Scommessa.GOAL -> secondoTempo
            else -> casa + ospite > 0 || secondoTempo
        }
    }

    /** Pronostico ormai perso anche se la partita non è finita (non si può più recuperare). */
    fun giaPersa(scelta: Scommessa, casa: Int, ospite: Int): Boolean = when (scelta) {
        Scommessa.UNDER -> casa + ospite >= 3
        Scommessa.NO_GOAL -> casa > 0 && ospite > 0
        else -> false
    }

    fun statoSchedina(esiti: List<EsitoSelezione>): StatoSchedina = when {
        esiti.any { it == EsitoSelezione.PERSA } -> StatoSchedina.PERSA
        esiti.any { it == EsitoSelezione.IN_ATTESA } -> StatoSchedina.IN_CORSO
        esiti.all { it == EsitoSelezione.NULLA } -> StatoSchedina.RIMBORSATA
        else -> StatoSchedina.VINTA
    }

    /** Quota effettiva: i pronostici nulli valgono 1.00. */
    fun quotaEffettiva(quoteEsiti: List<Pair<Double, EsitoSelezione>>): Double =
        quoteEsiti.fold(1.0) { acc, (q, e) -> if (e == EsitoSelezione.NULLA) acc else acc * q }

    fun vincita(puntata: Double, stato: StatoSchedina, quotaEffettiva: Double): Double = when (stato) {
        StatoSchedina.VINTA -> arrotonda(puntata * quotaEffettiva)
        StatoSchedina.RIMBORSATA -> puntata
        else -> 0.0
    }

    fun arrotonda(v: Double) = Math.round(v * 100) / 100.0
}

// ── Contabilità ───────────────────────────────────────────────────────────────

enum class TipoMovimento(val etichetta: String) {
    RICARICA("Ricarica"), AZZERAMENTO("Azzeramento"), PUNTATA("Puntata"), VINCITA("Vincita"), RIMBORSO("Rimborso"),
    CASHOUT("Cashout"),
}

data class Movimento(val data: Long, val tipo: TipoMovimento, val importo: Double, val schedinaId: Long? = null)

data class SchedinaChiusa(val sport: Sport, val chiusaIl: Long, val puntata: Double, val vincita: Double, val stato: StatoSchedina)

data class Mese(
    val mese: YearMonth,
    val giocate: Int,
    val vinte: Int,
    val perse: Int,
    /** Somme riscosse dalle schedine vinte e dai cashout. */
    val incassato: Double,
    /** Puntate delle schedine perse. */
    val perso: Double,
    /** Incassato meno tutte le puntate delle schedine chiuse nel mese. */
    val netto: Double,
)

data class Bilancio(
    val saldo: Double,
    val ricaricato: Double,
    val puntato: Double,
    val incassato: Double,
    /** Credito (positivo) o debito (negativo) generato dal metodo: vincite + rimborsi - puntate. */
    val utileMetodo: Double,
)

object Contabilita {

    fun saldo(movimenti: List<Movimento>) = Verifica.arrotonda(movimenti.sumOf { it.importo })

    /** Movimento che porta il saldo a zero (per ricominciare con un nuovo credito virtuale). */
    fun azzeramento(movimenti: List<Movimento>, data: Long): Movimento? {
        val s = saldo(movimenti)
        return if (s == 0.0) null else Movimento(data, TipoMovimento.AZZERAMENTO, -s)
    }

    fun bilancio(movimenti: List<Movimento>): Bilancio {
        fun somma(t: TipoMovimento) = movimenti.filter { it.tipo == t }.sumOf { it.importo }
        val puntato = -somma(TipoMovimento.PUNTATA)
        val incassato = somma(TipoMovimento.VINCITA) + somma(TipoMovimento.RIMBORSO) + somma(TipoMovimento.CASHOUT)
        return Bilancio(
            saldo = saldo(movimenti),
            ricaricato = Verifica.arrotonda(somma(TipoMovimento.RICARICA)),
            puntato = Verifica.arrotonda(puntato),
            incassato = Verifica.arrotonda(incassato),
            utileMetodo = Verifica.arrotonda(incassato - puntato),
        )
    }

    fun riepilogoMensile(schedine: List<SchedinaChiusa>, zona: ZoneId = ZoneId.systemDefault()): List<Mese> =
        schedine.filter { it.stato != StatoSchedina.IN_CORSO }
            .groupBy { YearMonth.from(Instant.ofEpochMilli(it.chiusaIl).atZone(zona)) }
            .map { (mese, lista) ->
                val vinte = lista.filter { it.stato == StatoSchedina.VINTA }
                val perse = lista.filter { it.stato == StatoSchedina.PERSA }
                // il cashout è un incasso (anche se minore della puntata), non una schedina vinta
                val incassato = vinte.sumOf { it.vincita } + lista.filter { it.stato == StatoSchedina.CASHOUT }.sumOf { it.vincita }
                val puntate = lista.filter { it.stato != StatoSchedina.RIMBORSATA }.sumOf { it.puntata }
                Mese(mese, lista.size, vinte.size, perse.size, Verifica.arrotonda(incassato),
                    Verifica.arrotonda(perse.sumOf { it.puntata }), Verifica.arrotonda(incassato - puntate))
            }
            .sortedBy { it.mese }

    /** Andamento del saldo nel tempo, un punto per movimento. */
    fun andamento(movimenti: List<Movimento>): List<Pair<Long, Double>> {
        var s = 0.0
        return movimenti.sortedBy { it.data }.map { s += it.importo; it.data to Verifica.arrotonda(s) }
    }
}
