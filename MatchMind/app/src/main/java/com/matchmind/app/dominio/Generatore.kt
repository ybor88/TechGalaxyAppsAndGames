package com.matchmind.app.dominio

import kotlin.math.ln

/**
 * Analisi delle partite e composizione della bolletta sicura.
 *
 * Per ogni partita si valutano tutte le giocate possibili: vincente (1/2), pareggio (X), doppia chance
 * (1X/X2/12), Goal/NoGoal e Under/Over 2,5. Le probabilità 1X2 nascono da quote reali + modello statistico
 * (pesati con l'esperienza); i gol attesi vengono poi resi coerenti con le quote 1X2 e Under/Over del mercato,
 * e da lì si ricavano Goal/NoGoal e Under/Over. Ogni probabilità è calibrata con l'esperienza del suo tipo.
 *
 * Nella bolletta entrano solo pronostici molto probabili su squadre AFFIDABILI (vedi [ammessaSicura]).
 *
 * © Roberto Di Flumeri
 */
object Generatore {

    /** Partite che iniziano entro pochi minuti non si giocano più: le quote potrebbero essere chiuse. */
    const val ANTICIPO_MINIMO_MS = 10 * 60 * 1000L

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

        return Analisi(evento, mercato, modello, finale, pGoal, pOver, opzioni, esperienza.affidabilita(evento.lega),
            statCasa = casa, statOspite = ospite,
            affidabilitaCasa = esperienza.affidabilitaSquadra(evento.casaId),
            affidabilitaOspite = esperienza.affidabilitaSquadra(evento.ospiteId))
    }

    fun giocabile(e: Evento, adesso: Long): Boolean =
        e.stato == StatoEvento.PROGRAMMATO && !e.amichevole && e.inizioMillis > adesso + ANTICIPO_MINIMO_MS

    // ── Bolletta sicura ──────────────────────────────────────────────────────

    /**
     * Bolletta sicura: puntata scelta dall'utente, vincita e numero di partite decisi dal metodo. L'obiettivo è
     * vincerne almeno 1 su 2: la probabilità stimata di vincere l'intera bolletta è almeno [PROBABILITA_BOLLETTA_SICURA].
     *
     * Regole, TUTTE obbligatorie, per ogni pronostico:
     *  1. probabilità finale (già corretta per l'affidabilità di squadra e lega) almeno [PROBABILITA_BOLLETTA_SICURA];
     *  2. mercato (quote) e statistica (modello) danno, ognuno da solo, almeno [FONTE_MINIMA_SICURA]; quota vera
     *     del bookmaker (niente quote stimate) e almeno [QUOTA_MINIMA_SICURA];
     *  3. squadre affidabili: entrambe con almeno [PARTITE_MINIME_SQUADRA] partite in stagione; la squadra su cui si
     *     punta ha ottenuto quel risultato in almeno il [STORICO_MINIMO_SICURA] delle sue partite; squadre e lega non
     *     hanno deluso le previsioni passate (affidabilità almeno [AFFIDABILITA_MINIMA]);
     *  4. al massimo [MAX_PARTITE_SICURA] partite, una per partita reale.
     */
    const val PROBABILITA_BOLLETTA_SICURA = 0.50
    const val FONTE_MINIMA_SICURA = 0.50
    const val QUOTA_MINIMA_SICURA = 1.03
    const val PARTITE_MINIME_SQUADRA = 6
    const val STORICO_MINIMO_SICURA = 0.50
    const val AFFIDABILITA_MINIMA = 0.95
    const val MAX_PARTITE_SICURA = 3
    /** Si guardano solo le partite di oggi e dei prossimi 2 giorni: quote e notizie più fresche. */
    const val GIORNI_SICURA = 3

    /**
     * Singola ad alta probabilità: una sola partita, probabilità prudente almeno [PROBABILITA_SINGOLA].
     * Si vince più spesso, ma la quota è bassa: la percentuale di vittorie sale, il guadagno no
     * (il margine del bookmaker resta). Serve a vedere sui risultati reali quanto rende davvero.
     */
    const val PROBABILITA_SINGOLA = 0.80

    /** Una giocata che ha passato tutte le regole, con la probabilità corretta per l'affidabilità. */
    private class Candidato(val analisi: Analisi, val opzione: Opzione, val probabilita: Double, val storico: String?) {
        /** Probabilità che costa per ogni unità di quota guadagnata: più basso = più conveniente. */
        val costo get() = -ln(probabilita) / ln(opzione.quota)
    }

    /**
     * Quanto spesso in stagione la squadra su cui si punta ha ottenuto il risultato giocato
     * (1/2: vittorie; 1X/X2: partite non perse; 12: partite non pareggiate, la peggiore delle due).
     * Null per i pronostici sui gol, che non dipendono da una sola squadra.
     */
    private fun storico(a: Analisi, s: Scommessa): Pair<Double, String>? {
        val c = a.statCasa ?: return null
        val o = a.statOspite ?: return null
        fun vinte(t: StatSquadra) = t.vinte.toDouble() / t.partite
        fun nonPerse(t: StatSquadra) = (t.vinte + t.pareggi).toDouble() / t.partite
        fun senzaPari(t: StatSquadra) = 1 - t.pareggi.toDouble() / t.partite
        return when (s) {
            Scommessa.CASA -> vinte(c) to "${a.evento.casa} in stagione: ${c.vinte} vinte su ${c.partite}"
            Scommessa.OSPITE -> vinte(o) to "${a.evento.ospite} in stagione: ${o.vinte} vinte su ${o.partite}"
            Scommessa.CASA_O_PAREGGIO -> nonPerse(c) to "${a.evento.casa} in stagione: ${c.vinte + c.pareggi} non perse su ${c.partite}"
            Scommessa.PAREGGIO_O_OSPITE -> nonPerse(o) to "${a.evento.ospite} in stagione: ${o.vinte + o.pareggi} non perse su ${o.partite}"
            Scommessa.CASA_O_OSPITE -> minOf(senzaPari(c), senzaPari(o)) to "pareggi in stagione: ${a.evento.casa} ${c.pareggi}/${c.partite}, ${a.evento.ospite} ${o.pareggi}/${o.partite}"
            else -> null
        }
    }

    /** Affidabilità della squadra (o delle squadre) da cui dipende la giocata. */
    private fun affidabilitaGiocata(a: Analisi, s: Scommessa): Double = when (s) {
        Scommessa.CASA, Scommessa.CASA_O_PAREGGIO -> a.affidabilitaCasa
        Scommessa.OSPITE, Scommessa.PAREGGIO_O_OSPITE -> a.affidabilitaOspite
        else -> minOf(a.affidabilitaCasa, a.affidabilitaOspite)
    }

    /** Il pronostico rispetta le regole 1, 2 e 3 (null se no). */
    private fun valuta(a: Analisi, o: Opzione, mercati: Set<Mercato>, soglia: Double = PROBABILITA_BOLLETTA_SICURA): Candidato? {
        if (o.scommessa.mercato != Mercato.VINCENTE && o.scommessa.mercato !in mercati) return null
        if (o.quota < QUOTA_MINIMA_SICURA || o.tipoQuota == TipoQuota.STIMATA || o.probabilita >= 1.0) return null
        // regola 2: mercato e statistica d'accordo, ognuno da solo
        if ((o.pMercato ?: 0.0) < FONTE_MINIMA_SICURA || (o.pModello ?: 0.0) < FONTE_MINIMA_SICURA) return null
        // regola 3: squadre con abbastanza partite, storico coerente, nessuna delusione passata
        val c = a.statCasa ?: return null
        val os = a.statOspite ?: return null
        if (c.partite < PARTITE_MINIME_SQUADRA || os.partite < PARTITE_MINIME_SQUADRA) return null
        val st = storico(a, o.scommessa)
        if (st != null && st.first < STORICO_MINIMO_SICURA) return null
        val squadra = affidabilitaGiocata(a, o.scommessa)
        if (squadra < AFFIDABILITA_MINIMA || a.affidabilita < AFFIDABILITA_MINIMA) return null
        // regola 1: mai più ottimista della fonte più prudente (la calibrazione può alzare la stima sopra
        // sia il mercato sia la statistica); poi scende se squadra o lega hanno reso meno del previsto (non sale mai)
        val prudente = minOf(o.probabilita, o.pMercato!!, o.pModello!!)
        val p = prudente * minOf(1.0, squadra) * minOf(1.0, a.affidabilita)
        if (p < soglia) return null
        return Candidato(a, o, p, st?.second)
    }

    /** Il pronostico rispetta le regole 1, 2 e 3. */
    fun ammessaSicura(a: Analisi, o: Opzione, mercati: Set<Mercato> = Mercato.entries.toSet()): Boolean = valuta(a, o, mercati) != null

    /** Prima i pronostici più probabili; a pari probabilità quello che rende di più. */
    private val piuProbabile = compareByDescending<Candidato> { it.probabilita }.thenBy { it.costo }

    /**
     * La bolletta sicura, calcio e basket insieme: per ogni partita giocabile la giocata ammessa più probabile; dalla più
     * probabile in giù (il 50% è solo il minimo) si aggiungono partite (al massimo [MAX_PARTITE_SICURA]) finché
     * la probabilità di vincere tutta la bolletta resta almeno [PROBABILITA_BOLLETTA_SICURA]. Vuota se nessuna
     * partita è abbastanza sicura.
     */
    fun bollettaSicura(
        analisi: List<Analisi>,
        adesso: Long,
        mercati: Set<Mercato> = Mercato.entries.toSet(),
        singola: Boolean = false,
    ): Schedina {
        val soglia = if (singola) PROBABILITA_SINGOLA else PROBABILITA_BOLLETTA_SICURA
        val massimo = if (singola) 1 else MAX_PARTITE_SICURA
        val candidati = analisi
            .filter { giocabile(it.evento, adesso) }
            .distinctBy { it.evento.id }
            .mapNotNull { a -> a.opzioni.mapNotNull { valuta(a, it, mercati, soglia) }.sortedWith(piuProbabile).firstOrNull() }
            .sortedWith(piuProbabile.thenByDescending { affidabilitaGiocata(it.analisi, it.opzione.scommessa) })
        val scelte = mutableListOf<Candidato>()
        var probabilita = 1.0
        for (c in candidati) {
            if (scelte.size >= massimo) break
            if (probabilita * c.probabilita < soglia) continue
            scelte += c
            probabilita *= c.probabilita
        }
        val sport = scelte.map { it.analisi.evento.sport }.distinct().singleOrNull()
        return Schedina(sport, scelte.sortedBy { it.analisi.evento.inizioMillis }.map { c ->
            val o = c.opzione
            Pronostico(c.analisi.evento, o.scommessa, o.quota, o.tipoQuota, c.probabilita, o.pMercato, o.pModello, c.storico)
        }, if (singola) Strategia.SINGOLA else Strategia.SICURA)
    }

    // ── Puntata consigliata ──────────────────────────────────────────────────

    /**
     * Puntata fissa: sempre la stessa piccola parte del credito, qualunque sia la quota.
     * Prima MatchMind consigliava la puntata per guadagnare sempre 20 € (a quota 1,15 → 134 €): una sola bolletta
     * persa cancellava 6-7 vinte. Con la puntata fissa le perdite restano piccole e lente.
     */
    const val PERCENTUALE_PUNTATA = 0.02
    const val PUNTATA_MINIMA = 1.0

    /** Consiglio di puntata per una bolletta: [puntata] a euro interi, [vincita] ipotetica e [guadagno]. */
    data class ConsiglioPuntata(val puntata: Double, val vincita: Double, val guadagno: Double)

    /**
     * Il [PERCENTUALE_PUNTATA] del [saldo], a euro interi per difetto (almeno [PUNTATA_MINIMA]).
     * Null se la quota non dà guadagno o il credito non basta.
     */
    fun consiglioPuntata(quotaTotale: Double, saldo: Double): ConsiglioPuntata? {
        if (quotaTotale <= 1.0 || saldo < PUNTATA_MINIMA) return null
        val puntata = kotlin.math.floor(saldo * PERCENTUALE_PUNTATA + 1e-9).coerceAtLeast(PUNTATA_MINIMA)
        val vincita = Verifica.arrotonda(puntata * quotaTotale)
        return ConsiglioPuntata(puntata, vincita, Verifica.arrotonda(vincita - puntata))
    }
}
