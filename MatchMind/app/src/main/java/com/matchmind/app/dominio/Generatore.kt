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

    /** Puntata del bollettino speciale: da 5 a 20 €. */
    const val PUNTATA_OBIETTIVO_MIN = 5.0
    const val PUNTATA_OBIETTIVO_MAX = 20.0
    /** Vincita finale ("quanto vuoi prendere") che si può chiedere: da 50 a 500 €. */
    const val VINCITA_OBIETTIVO_MIN = 50.0
    const val VINCITA_OBIETTIVO_MAX = 500.0
    /**
     * Regole del bollettino, TUTTE obbligatorie:
     *  1. ogni pronostico ha almeno il 60% di probabilità finale ([PROBABILITA_MINIMA_OBIETTIVO]);
     *  2. su ogni pronostico sia il mercato (quote) sia la statistica (modello) danno, ognuno da solo, più del 50%
     *     ([FONTE_MINIMA_OBIETTIVO]): senza una delle due fonti il pronostico non entra;
     *  3. le partite iniziano ad almeno un'ora l'una dall'altra ([DISTANZA_MINIMA_OBIETTIVO_MS]), mai in
     *     contemporanea, così si può seguirle una alla volta e fare cashout;
     *  4. la probabilità di vincere l'intero bollettino è almeno il 20% ([PROBABILITA_BOLLETTINO_MINIMA]).
     */
    const val PROBABILITA_MINIMA_OBIETTIVO = 0.60
    const val FONTE_MINIMA_OBIETTIVO = 0.50
    const val DISTANZA_MINIMA_OBIETTIVO_MS = 60 * 60 * 1000L
    const val PROBABILITA_BOLLETTINO_MINIMA = 0.20

    /** Il bollettino speciale trovato, oppure la vincita massima raggiungibile rispettando tutte le regole. */
    sealed interface EsitoBolletta {
        data class Trovata(val schedina: Schedina) : EsitoBolletta
        /** [vincitaMassima] = puntata × quota più alta ottenibile con le regole (null: nessun pronostico le rispetta). */
        data class Impossibile(val vincitaMassima: Double?) : EsitoBolletta
    }

    /** Il pronostico rispetta le regole 1 e 2: 60% finale, mercato e statistica ognuno oltre il 50%. */
    fun ammessaObiettivo(o: Opzione, mercati: Set<Mercato>): Boolean =
        (o.scommessa.mercato == Mercato.VINCENTE || o.scommessa.mercato in mercati) &&
            o.quota >= QUOTA_MINIMA && o.probabilita >= PROBABILITA_MINIMA_OBIETTIVO && o.probabilita < 1.0 &&
            (o.pMercato ?: 0.0) > FONTE_MINIMA_OBIETTIVO && (o.pModello ?: 0.0) > FONTE_MINIMA_OBIETTIVO

    private fun costo(o: Opzione) = -kotlin.math.ln(o.probabilita) / kotlin.math.ln(o.quota)

    /**
     * Per ogni partita giocabile della settimana la giocata ammessa che rende di più per la probabilità che
     * costa (rapporto −ln p / ln quota più basso), dalla più conveniente.
     */
    private fun candidatiObiettivo(sport: Sport, analisi: List<Analisi>, adesso: Long, mercati: Set<Mercato>) = analisi
        .filter { it.evento.sport == sport && giocabile(it.evento, adesso) }
        .distinctBy { it.evento.id }
        .mapNotNull { a -> a.opzioni.filter { ammessaObiettivo(it, mercati) }.minByOrNull(::costo)?.let { a to it } }
        .sortedBy { (_, o) -> costo(o) }

    /**
     * Dalla più conveniente si aggiungono partite finché la quota arriva a [quotaObiettivo], saltando quelle
     * che inizierebbero a meno di un'ora da una già scelta o che porterebbero il bollettino sotto il 20%.
     * Poi via quelle non necessarie (le più rischiose per prime): togliere non viola mai le regole.
     */
    private fun scegli(candidati: List<Pair<Analisi, Opzione>>, quotaObiettivo: Double): List<Pair<Analisi, Opzione>> {
        val scelte = mutableListOf<Pair<Analisi, Opzione>>()
        var quota = 1.0
        var probabilita = 1.0
        for (c in candidati) {
            if (quota >= quotaObiettivo) break
            val inizio = c.first.evento.inizioMillis
            if (scelte.any { kotlin.math.abs(it.first.evento.inizioMillis - inizio) < DISTANZA_MINIMA_OBIETTIVO_MS }) continue
            if (probabilita * c.second.probabilita < PROBABILITA_BOLLETTINO_MINIMA) continue
            scelte += c
            quota *= c.second.quota
            probabilita *= c.second.probabilita
        }
        if (quota >= quotaObiettivo) for (c in scelte.sortedBy { it.second.probabilita }) {
            if (quota / c.second.quota >= quotaObiettivo) {
                scelte -= c
                quota /= c.second.quota
            }
        }
        return scelte
    }

    /**
     * "Prova a guadagnare": il bollettino speciale sulle partite della settimana che con [puntata] (5–20 €) fa
     * prendere almeno [vincita] (50–500 €), rispettando tutte le regole qui sopra. Il numero di partite lo
     * sceglie il metodo. Se non si può, dice la vincita massima raggiungibile con le stesse regole.
     */
    fun bollettaObiettivo(
        sport: Sport,
        analisi: List<Analisi>,
        adesso: Long,
        puntata: Double,
        vincita: Double,
        mercati: Set<Mercato> = Mercato.entries.toSet(),
    ): EsitoBolletta {
        if (puntata <= 0 || vincita <= puntata) return EsitoBolletta.Impossibile(null)
        val quotaObiettivo = vincita / puntata
        val candidati = candidatiObiettivo(sport, analisi, adesso, mercati)
        if (candidati.isEmpty()) return EsitoBolletta.Impossibile(null)
        val scelte = scegli(candidati, quotaObiettivo)
        val quota = scelte.fold(1.0) { q, c -> q * c.second.quota }
        if (quota < quotaObiettivo) {
            val massima = scegli(candidati, Double.POSITIVE_INFINITY).fold(1.0) { q, c -> q * c.second.quota }
            return EsitoBolletta.Impossibile(if (massima > 1.0) puntata * massima else null)
        }
        return EsitoBolletta.Trovata(Schedina(sport, scelte.sortedBy { it.first.evento.inizioMillis }.map { (a, o) ->
            Pronostico(a.evento, o.scommessa, o.quota, o.tipoQuota, o.probabilita, o.pMercato, o.pModello)
        }, Strategia.SICURA))
    }
}
