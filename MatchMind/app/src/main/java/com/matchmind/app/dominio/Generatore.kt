package com.matchmind.app.dominio

/**
 * Analisi delle partite e composizione della schedina.
 *
 * Per ogni partita si valutano tutte le giocate possibili: vincente (1/2), pareggio (X), doppia chance
 * (1X/X2/12), Goal/NoGoal e Under/Over 2,5. Le probabilità 1X2 nascono da quote reali + modello statistico
 * (pesati con l'esperienza); i gol attesi vengono poi resi coerenti con le quote 1X2 e Under/Over del mercato,
 * e da lì si ricavano Goal/NoGoal e Under/Over. Ogni probabilità è calibrata con l'esperienza del suo tipo.
 *
 * La schedina non ha un numero fisso di partite: si aggiungono i pronostici migliori finché la probabilità
 * di vincere l'intera schedina resta sopra la soglia di sicurezza scelta (massimo [MAX_PARTITE]).
 *
 * © Roberto Di Flumeri
 */
object Generatore {

    /** Partite che iniziano entro pochi minuti non si giocano più: le quote potrebbero essere chiuse. */
    const val ANTICIPO_MINIMO_MS = 10 * 60 * 1000L
    const val MAX_PARTITE = 10
    /** Probabilità minima di un singolo pronostico "sicuro". */
    const val SOGLIA_PRONOSTICO_SICURO = 0.70
    const val PROBABILITA_MINIMA_VALORE = 0.50
    /** Quote più basse aggiungono rischio senza aggiungere vincita. */
    const val QUOTA_MINIMA = 1.15
    /** Probabilità minima predefinita di vincere l'intera schedina. */
    const val SICUREZZA_PREDEFINITA = 0.30

    fun analizza(
        evento: Evento,
        classifica: Map<String, StatSquadra>,
        mediaGol: Double,
        esperienza: Esperienza,
    ): Analisi? {
        val q = evento.quote
        val mercato = q?.let { ModelliProbabilistici.daQuote(it) }
        val casa = classifica[evento.casaId]
        val ospite = classifica[evento.ospiteId]
        val conStatistiche = casa != null && ospite != null && casa.partite + ospite.partite > 0
        val modello = if (conStatistiche) {
            if (evento.sport == Sport.CALCIO) ModelliProbabilistici.probabilitaCalcio(casa!!, ospite!!, mediaGol)
            else ModelliProbabilistici.probabilitaBasket(casa!!, ospite!!)
        } else null
        val finale = ModelliProbabilistici.combina(mercato, modello, esperienza.pesoMercato) ?: return null

        val opzioni = mutableListOf<Opzione>()
        fun aggiungi(s: Scommessa, pGrezza: Double, quotaReale: Double?, tipoReale: TipoQuota, pMercato: Double?, pModello: Double?) {
            val p = esperienza.calibra(pGrezza, s.mercato)
            opzioni += if (quotaReale != null) Opzione(s, p, quotaReale, tipoReale, pMercato, pModello)
            else Opzione(s, p, ModelliProbabilistici.quotaStimata(p), TipoQuota.STIMATA, pMercato, pModello)
        }
        aggiungi(Scommessa.CASA, finale.casa, q?.casa, TipoQuota.REALE, mercato?.casa, modello?.casa)
        aggiungi(Scommessa.OSPITE, finale.ospite, q?.ospite, TipoQuota.REALE, mercato?.ospite, modello?.ospite)

        var pGoal: Double? = null
        var pOver: Double? = null
        if (evento.sport == Sport.CALCIO) {
            aggiungi(Scommessa.PAREGGIO, finale.pareggio, q?.pareggio, TipoQuota.REALE, mercato?.pareggio, modello?.pareggio)
            val qx = q?.pareggio
            aggiungi(Scommessa.CASA_O_PAREGGIO, finale.casa + finale.pareggio, qx?.let { ModelliProbabilistici.quotaDerivata(q.casa, it) },
                TipoQuota.DERIVATA, mercato?.let { it.casa + it.pareggio }, modello?.let { it.casa + it.pareggio })
            aggiungi(Scommessa.PAREGGIO_O_OSPITE, finale.pareggio + finale.ospite, qx?.let { ModelliProbabilistici.quotaDerivata(it, q.ospite) },
                TipoQuota.DERIVATA, mercato?.let { it.pareggio + it.ospite }, modello?.let { it.pareggio + it.ospite })
            aggiungi(Scommessa.CASA_O_OSPITE, finale.casa + finale.ospite, q?.let { ModelliProbabilistici.quotaDerivata(it.casa, it.ospite) },
                TipoQuota.DERIVATA, mercato?.let { it.casa + it.ospite }, modello?.let { it.casa + it.ospite })

            // gol attesi coerenti con 1X2 e, se quotato, con l'Under/Over del mercato
            val lineaStandard = q?.lineaGol == ModelliProbabilistici.LINEA_GOL
            val overMercato = if (lineaStandard && q?.over != null && q.under != null) ModelliProbabilistici.overDaQuote(q.over, q.under) else null
            val golModello = if (conStatistiche) ModelliProbabilistici.golAttesi(casa!!, ospite!!, mediaGol) else null
            val totale = golModello?.let { it.first + it.second } ?: (2 * mediaGol)
            val (lc, lo) = ModelliProbabilistici.golAttesiCoerenti(finale, overMercato, totale)
            val goal = ModelliProbabilistici.probabilitaGoal(lc, lo)
            val over = ModelliProbabilistici.probabilitaOver(lc, lo)
            pGoal = goal
            pOver = over
            val goalModello = golModello?.let { ModelliProbabilistici.probabilitaGoal(it.first, it.second) }
            val overModello = golModello?.let { ModelliProbabilistici.probabilitaOver(it.first, it.second) }
            aggiungi(Scommessa.GOAL, goal, null, TipoQuota.STIMATA, null, goalModello)
            aggiungi(Scommessa.NO_GOAL, 1 - goal, null, TipoQuota.STIMATA, null, goalModello?.let { 1 - it })
            aggiungi(Scommessa.OVER, over, if (lineaStandard) q?.over else null, TipoQuota.REALE, overMercato, overModello)
            aggiungi(Scommessa.UNDER, 1 - over, if (lineaStandard) q?.under else null, TipoQuota.REALE,
                overMercato?.let { 1 - it }, overModello?.let { 1 - it })
        }

        return Analisi(evento, mercato, modello, finale, pGoal, pOver, opzioni, esperienza.affidabilita(evento.lega))
    }

    fun giocabile(e: Evento, adesso: Long): Boolean =
        e.stato == StatoEvento.PROGRAMMATO && !e.amichevole && e.inizioMillis > adesso + ANTICIPO_MINIMO_MS

    fun ammessa(o: Opzione, strategia: Strategia, mercati: Set<Mercato>): Boolean =
        (o.scommessa.mercato == Mercato.VINCENTE || o.scommessa.mercato in mercati) &&
            o.quota >= QUOTA_MINIMA &&
            when (strategia) {
                Strategia.SICURA -> o.probabilita >= SOGLIA_PRONOSTICO_SICURO
                Strategia.VALORE -> o.tipoQuota != TipoQuota.STIMATA && o.probabilita >= PROBABILITA_MINIMA_VALORE && o.valoreAtteso > 0
            }

    fun punteggio(o: Opzione, affidabilita: Double, strategia: Strategia): Double {
        val penalita = when (o.tipoQuota) {
            TipoQuota.REALE -> 1.0
            TipoQuota.DERIVATA -> 0.995
            TipoQuota.STIMATA -> 0.97
        }
        return when (strategia) {
            Strategia.SICURA -> o.probabilita * affidabilita * penalita
            // valore atteso con la probabilità corretta per l'affidabilità della lega
            Strategia.VALORE -> (o.probabilita * affidabilita).coerceAtMost(0.99) * o.quota - 1.0 + 0.001 * o.probabilita
        }
    }

    /** La giocata migliore su una partita secondo la strategia, tra i tipi di pronostico attivi. */
    fun migliore(a: Analisi, strategia: Strategia, mercati: Set<Mercato>): Opzione? =
        a.opzioni.filter { ammessa(it, strategia, mercati) }.maxByOrNull { punteggio(it, a.affidabilita, strategia) }

    /**
     * Schedina con i pronostici migliori, uno per partita, aggiunti finché la probabilità di vincerla tutta
     * resta almeno pari a [sicurezza]. Il numero di partite lo decide il metodo (da 0 a [massimo]).
     */
    fun genera(
        sport: Sport,
        analisi: List<Analisi>,
        strategia: Strategia,
        adesso: Long,
        sicurezza: Double = SICUREZZA_PREDEFINITA,
        mercati: Set<Mercato> = Mercato.entries.toSet(),
        massimo: Int = MAX_PARTITE,
    ): Schedina {
        val candidati = analisi
            .filter { it.evento.sport == sport && giocabile(it.evento, adesso) }
            .distinctBy { it.evento.id }
            .mapNotNull { a -> migliore(a, strategia, mercati)?.let { a to it } }
            .sortedWith(compareByDescending<Pair<Analisi, Opzione>> { (a, o) -> punteggio(o, a.affidabilita, strategia) }
                .thenBy { it.first.evento.inizioMillis })
        val scelte = mutableListOf<Pair<Analisi, Opzione>>()
        var probabilita = 1.0
        for ((a, o) in candidati) {
            if (scelte.size >= massimo) break
            if (probabilita * o.probabilita < sicurezza) continue
            scelte += a to o
            probabilita *= o.probabilita
        }
        val pronostici = scelte.sortedBy { it.first.evento.inizioMillis }.map { (a, o) ->
            Pronostico(a.evento, o.scommessa, o.quota, o.tipoQuota, o.probabilita, o.pMercato, o.pModello)
        }
        return Schedina(sport, pronostici, strategia)
    }

    // ── Prova a guadagnare ───────────────────────────────────────────────────

    /** Puntate che "Prova a guadagnare" può proporre: da 5 a 20 €. */
    val PUNTATE_OBIETTIVO = listOf(5.0, 10.0, 15.0, 20.0)
    /** Partite massime di una bolletta settimanale: il numero lo sceglie il metodo. */
    const val MAX_PARTITE_OBIETTIVO = 30
    /** Vincita finale massima che si può chiedere. */
    const val VINCITA_OBIETTIVO_MAX = 500.0
    /** Probabilità di vincere la bolletta settimanale: almeno 50%, al massimo 60%. */
    const val SICUREZZA_OBIETTIVO_MIN = 0.50
    const val SICUREZZA_OBIETTIVO_MAX = 0.60

    /** Il pronostico sicuro migliore di ogni partita giocabile, dal più "conveniente" (−ln p / ln quota). */
    private fun candidatiObiettivo(sport: Sport, analisi: List<Analisi>, adesso: Long, mercati: Set<Mercato>) = analisi
        .filter { it.evento.sport == sport && giocabile(it.evento, adesso) }
        .distinctBy { it.evento.id }
        .mapNotNull { a -> migliore(a, Strategia.SICURA, mercati)?.takeIf { it.probabilita < 1.0 && it.quota > 1.0 }?.let { a to it } }
        .sortedBy { (_, o) -> -kotlin.math.ln(o.probabilita) / kotlin.math.ln(o.quota) }

    private fun schedinaDa(sport: Sport, scelte: List<Pair<Analisi, Opzione>>) =
        Schedina(sport, scelte.sortedBy { it.first.evento.inizioMillis }.map { (a, o) ->
            Pronostico(a.evento, o.scommessa, o.quota, o.tipoQuota, o.probabilita, o.pMercato, o.pModello)
        }, Strategia.SICURA)

    /**
     * Combinazione di pronostici sicuri con quota totale ≥ [quotaObiettivo] (= vincita finale / puntata): si
     * prendono prima quelli che alzano di più la quota perdendo meno probabilità, poi si tolgono quelli non
     * necessari. Null se la quota non si raggiunge o la probabilità scende sotto [sicurezza].
     */
    private fun combinazione(
        sport: Sport,
        candidati: List<Pair<Analisi, Opzione>>,
        quotaObiettivo: Double,
        sicurezza: Double,
        massimo: Int,
    ): Schedina? {
        val scelte = mutableListOf<Pair<Analisi, Opzione>>()
        var quota = 1.0
        for (c in candidati) {
            if (quota >= quotaObiettivo || scelte.size >= massimo) break
            scelte += c
            quota *= c.second.quota
        }
        if (quota < quotaObiettivo) return null
        // via i pronostici più rischiosi che non servono a raggiungere la quota
        for (c in scelte.sortedBy { it.second.probabilita }) {
            if (quota / c.second.quota >= quotaObiettivo) {
                scelte -= c
                quota /= c.second.quota
            }
        }
        return schedinaDa(sport, scelte).takeIf { it.probabilita >= sicurezza }
    }

    /**
     * Per ogni puntata possibile (5, 10, 15, 20 €) la combinazione di pronostici sicuri che arriva almeno a
     * [vincita] finale con probabilità ≥ [sicurezza]: il numero di partite lo decide il metodo (fino a
     * [MAX_PARTITE_OBIETTIVO]). Puntate con cui l'obiettivo non si raggiunge non compaiono.
     */
    fun perObiettivo(
        sport: Sport,
        analisi: List<Analisi>,
        adesso: Long,
        vincita: Double,
        sicurezza: Double = SICUREZZA_OBIETTIVO_MIN,
        mercati: Set<Mercato> = Mercato.entries.toSet(),
        massimo: Int = MAX_PARTITE_OBIETTIVO,
    ): List<Pair<Double, Schedina>> {
        val candidati = candidatiObiettivo(sport, analisi, adesso, mercati)
        return PUNTATE_OBIETTIVO.filter { vincita > it }
            .mapNotNull { puntata -> combinazione(sport, candidati, vincita / puntata, sicurezza, massimo) }
            .distinctBy { s -> s.pronostici.map { it.evento.id to it.scelta }.toSet() }
            // puntata esatta per la combinazione trovata: gli euro interi che bastano a raggiungere la vincita
            .map { s -> kotlin.math.ceil(vincita / s.quotaTotale - 1e-9).coerceAtLeast(PUNTATE_OBIETTIVO.min()) to s }
    }

    /**
     * Quota totale più alta raggiungibile restando con probabilità ≥ [sicurezza]: serve a dire all'utente la
     * vincita massima possibile quando chiede troppo.
     */
    fun quotaMassima(
        sport: Sport,
        analisi: List<Analisi>,
        adesso: Long,
        sicurezza: Double = SICUREZZA_OBIETTIVO_MIN,
        mercati: Set<Mercato> = Mercato.entries.toSet(),
        massimo: Int = MAX_PARTITE_OBIETTIVO,
    ): Double {
        var quota = 1.0
        var probabilita = 1.0
        var n = 0
        for ((_, o) in candidatiObiettivo(sport, analisi, adesso, mercati)) {
            if (n >= massimo) break
            if (probabilita * o.probabilita < sicurezza) continue
            probabilita *= o.probabilita
            quota *= o.quota
            n++
        }
        return quota
    }
}
