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
    /** Chiusa in anticipo con il cashout (solo bollette vecchie: la funzione non esiste più). */
    CASHOUT("Cashout"),
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

data class SchedinaChiusa(
    val sport: Sport?,
    val chiusaIl: Long,
    val puntata: Double,
    val vincita: Double,
    val stato: StatoSchedina,
    /** Quota totale giocata (0 se sconosciuta). */
    val quota: Double = 0.0,
)

/**
 * Il confronto che dice se il metodo rende: quante bollette si vincono davvero e quante servirebbero, con quelle
 * quote, solo per andare in pari. Conta solo le bollette vinte o perse (non rimborsi e cashout).
 */
data class Verita(
    val decise: Int,
    val vinte: Int,
    /** Vinte / decise. */
    val percentualeVinte: Double,
    /** Percentuale di vittorie che servirebbe per chiudere a zero: puntate / vincite potenziali. */
    val percentualePareggio: Double,
    val quotaMedia: Double,
    /** Incassato meno puntato sulle bollette decise. */
    val netto: Double,
    /** Puntata per bolletta che servirebbe per guadagnare [OBIETTIVO_GIORNALIERO] con una vinta alla quota media. */
    val puntataPerObiettivo: Double,
) {
    val rende: Boolean get() = percentualeVinte > percentualePareggio

    companion object { const val OBIETTIVO_GIORNALIERO = 20.0 }
}


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

    /** Percentuale vinta contro percentuale di pareggio. Null se non c'è ancora una bolletta vinta o persa con quota nota. */
    fun verita(schedine: List<SchedinaChiusa>): Verita? {
        val decise = schedine.filter { (it.stato == StatoSchedina.VINTA || it.stato == StatoSchedina.PERSA) && it.quota > 1.0 }
        if (decise.isEmpty()) return null
        val vinte = decise.filter { it.stato == StatoSchedina.VINTA }
        val puntato = decise.sumOf { it.puntata }
        val potenziale = decise.sumOf { it.puntata * it.quota }
        val quotaMedia = potenziale / puntato
        return Verita(
            decise = decise.size,
            vinte = vinte.size,
            percentualeVinte = vinte.size.toDouble() / decise.size,
            percentualePareggio = puntato / potenziale,
            quotaMedia = quotaMedia,
            netto = Verifica.arrotonda(vinte.sumOf { it.vincita } - puntato),
            puntataPerObiettivo = kotlin.math.ceil(Verita.OBIETTIVO_GIORNALIERO / (quotaMedia - 1) - 1e-9),
        )
    }

    /** Andamento del saldo nel tempo, un punto per movimento. */
    fun andamento(movimenti: List<Movimento>): List<Pair<Long, Double>> {
        var s = 0.0
        return movimenti.sortedBy { it.data }.map { s += it.importo; it.data to Verifica.arrotonda(s) }
    }
}
