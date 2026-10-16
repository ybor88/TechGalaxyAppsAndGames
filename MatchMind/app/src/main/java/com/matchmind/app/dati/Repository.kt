package com.matchmind.app.dati

import com.matchmind.app.dominio.Analisi
import com.matchmind.app.dominio.Contabilita
import com.matchmind.app.dominio.Esito
import com.matchmind.app.dominio.EsitoSelezione
import com.matchmind.app.dominio.Esperienza
import com.matchmind.app.dominio.Evento
import com.matchmind.app.dominio.Generatore
import com.matchmind.app.dominio.Lega
import com.matchmind.app.dominio.Mercato
import com.matchmind.app.dominio.Scommessa
import com.matchmind.app.dominio.Leghe
import com.matchmind.app.dominio.ModelliProbabilistici
import com.matchmind.app.dominio.Movimento
import com.matchmind.app.dominio.Osservazione
import com.matchmind.app.dominio.Probabilita
import com.matchmind.app.dominio.Schedina
import com.matchmind.app.dominio.Sport
import com.matchmind.app.dominio.StatoSchedina
import com.matchmind.app.dominio.TipoMovimento
import com.matchmind.app.dominio.Verifica
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Logica applicativa: genera le schedine dalle partite reali, le registra, le verifica sui risultati reali,
 * aggiorna l'esperienza e il credito virtuale.
 *
 * © Roberto Di Flumeri
 */
class Repository(private val db: MatchMindDb, private val espn: EspnClient) {

    companion object {
        /** Oltre questa media di partite giocate la stagione in corso basta da sola. */
        const val SOGLIA_STAGIONE_MATURA = 15
    }

    data class Proposta(
        val schedina: Schedina,
        val partiteAnalizzate: Int,
        val partiteConQuote: Int,
        val esperienza: Esperienza,
        val erroriRete: Int,
        val amichevoliEscluse: Int = 0,
        val giorni: Int = 3,
        /** Pronostici abbastanza sicuri disponibili (prima del limite di sicurezza della schedina). */
        val pronosticiSicuri: Int = 0,
    )

    data class EsitoVerifica(
        val controllate: Int,
        val vinte: Int,
        val perse: Int,
        val rimborsate: Int,
        val ancoraInCorso: Int,
        val osservazioniChiuse: Int,
        val erroriRete: Int,
    )

    val schedine = db.schedine().tutte()
    val movimenti = db.movimenti().tutti()
    val osservazioniInAttesa: kotlinx.coroutines.flow.Flow<List<ConteggioSport>> = db.osservazioni().conteggioAperte()

    // ── Esperienza ───────────────────────────────────────────────────────────

    suspend fun esperienza(sport: Sport): Esperienza = withContext(Dispatchers.IO) {
        Esperienza.impara(sport, db.osservazioni().chiuse().mapNotNull { it.inDominio() })
    }

    // ── Generazione ──────────────────────────────────────────────────────────

    /**
     * Schedina sulle partite dei prossimi [giorni] giorni. Il numero di partite non è fisso: si aggiungono i
     * pronostici migliori finché la probabilità di vincere tutta la schedina resta almeno [sicurezza].
     */
    suspend fun genera(
        sport: Sport,
        giorni: Int,
        strategia: com.matchmind.app.dominio.Strategia,
        sicurezza: Double = Generatore.SICUREZZA_PREDEFINITA,
        mercati: Set<Mercato> = Mercato.entries.toSet(),
        adesso: Long = System.currentTimeMillis(),
    ): Proposta = coroutineScope {
        espn.azzeraErrori()
        val esperienza = esperienza(sport)
        val oggi = LocalDate.now(ZoneOffset.UTC)
        val leghe = Leghe.di(sport)
        val finestra = giorni.coerceIn(1, 7)
        val fine = adesso + finestra * 24 * 3600 * 1000L

        val classifiche = leghe.map { l -> async { l.codice to classificaConStorico(l) } }
        val partite = leghe.flatMap { l -> (0..finestra).map { d -> async { espn.partite(l, oggi.plusDays(d.toLong())) } } }
        val tabelle = classifiche.awaitAll().toMap()
        val medie = tabelle.mapValues { (_, t) -> ModelliProbabilistici.mediaGolLega(t.values) }
        val tutte = partite.awaitAll().flatten().distinctBy { it.id }.filter { it.inizioMillis in adesso..fine }
        // le amichevoli di preseason non entrano né in schedina né nell'esperienza
        val ufficiali = tutte.filterNot { it.amichevole }
        val analisi = withContext(Dispatchers.Default) {
            ufficiali.mapNotNull { e -> Generatore.analizza(e, tabelle[e.lega].orEmpty(), medie[e.lega] ?: 1.35, esperienza) }
        }
        registraOsservazioni(analisi)
        Proposta(
            schedina = Generatore.genera(sport, analisi, strategia, adesso, sicurezza, mercati),
            partiteAnalizzate = analisi.size,
            partiteConQuote = analisi.count { it.mercato != null },
            esperienza = esperienza,
            erroriRete = espn.errori,
            amichevoliEscluse = tutte.size - ufficiali.size,
            giorni = finestra,
            pronosticiSicuri = analisi.count { Generatore.giocabile(it.evento, adesso) && Generatore.migliore(it, strategia, mercati) != null },
        )
    }

    /**
     * Basket a inizio stagione: con poche partite giocate si aggiunge la stagione regolare precedente
     * (le stagioni NBA/NCAA sono indicate con l'anno in cui finiscono; la WNBA con l'anno solare).
     */
    private suspend fun classificaConStorico(lega: Lega): Map<String, com.matchmind.app.dominio.StatSquadra> {
        val corrente = espn.classifica(lega)
        if (lega.sport != Sport.BASKET) return corrente
        val media = if (corrente.isEmpty()) 0.0 else corrente.values.sumOf { it.partite }.toDouble() / corrente.size
        if (media >= SOGLIA_STAGIONE_MATURA) return corrente
        val oggi = LocalDate.now(ZoneOffset.UTC)
        val stagioneCorrente = if (lega.codice == "wnba") oggi.year else if (oggi.monthValue >= 8) oggi.year + 1 else oggi.year
        val precedente = espn.classifica(lega, stagioneCorrente - 1)
        return ModelliProbabilistici.unisciStagioni(corrente, precedente)
    }

    /** Ogni partita analizzata diventa memoria: quando avrà un risultato insegnerà qualcosa al modello. */
    private suspend fun registraOsservazioni(analisi: List<Analisi>) = withContext(Dispatchers.IO) {
        db.osservazioni().inserisci(analisi.map { a ->
            OsservazioneEntity(
                eventoId = a.evento.id, sport = a.evento.sport.name, lega = a.evento.lega, inizio = a.evento.inizioMillis,
                mercatoCasa = a.mercato?.casa, mercatoPareggio = a.mercato?.pareggio, mercatoOspite = a.mercato?.ospite,
                modelloCasa = a.modello?.casa, modelloPareggio = a.modello?.pareggio, modelloOspite = a.modello?.ospite,
                pGoal = a.pGoal, pOver = a.pOver,
            )
        })
    }

    suspend fun gioca(schedina: Schedina, puntata: Double, adesso: Long = System.currentTimeMillis()): Long = withContext(Dispatchers.IO) {
        require(schedina.pronostici.isNotEmpty()) { "Schedina vuota" }
        require(puntata > 0) { "Puntata non valida" }
        db.runInTransaction<Long> {
            val id = db.schedine().inserisci(SchedinaEntity(
                sport = schedina.sport.name, strategia = schedina.strategia.name, creataIl = adesso, puntata = puntata,
                quotaTotale = schedina.quotaTotale, probabilita = schedina.probabilita,
                stato = StatoSchedina.IN_CORSO.name,
            ))
            db.schedine().inserisciSelezioni(schedina.pronostici.map { p ->
                SelezioneEntity(
                    schedinaId = id, eventoId = p.evento.id, lega = p.evento.lega, inizio = p.evento.inizioMillis,
                    casa = p.evento.casa, ospite = p.evento.ospite, scelta = p.scelta.name, quota = p.quota,
                    quotaStimata = p.quotaStimata, probabilita = p.probabilita, pMercato = p.pMercato, pModello = p.pModello,
                    esito = EsitoSelezione.IN_ATTESA.name,
                )
            })
            db.movimenti().inserisci(MovimentoEntity(data = adesso, tipo = TipoMovimento.PUNTATA.name, importo = -puntata, schedinaId = id))
            id
        }
    }

    // ── Verifica sui risultati reali ─────────────────────────────────────────

    suspend fun verifica(adesso: Long = System.currentTimeMillis()): EsitoVerifica = coroutineScope {
        espn.azzeraErrori()
        val aperte = withContext(Dispatchers.IO) { db.schedine().inCorso() }
        val osservazioni = withContext(Dispatchers.IO) { db.osservazioni().aperte(adesso) }

        // richieste necessarie: (lega, giorno) per ogni partita già iniziata, giorno prima e dopo compresi
        // (il feed raggruppa le partite per data americana)
        val richieste = mutableSetOf<Pair<String, LocalDate>>()
        fun aggiungi(lega: String, inizio: Long) {
            val g = Instant.ofEpochMilli(inizio).atZone(ZoneOffset.UTC).toLocalDate()
            for (d in -1L..1L) richieste += lega to g.plusDays(d)
        }
        aperte.flatMap { it.selezioni }.filter { it.inizio < adesso && it.esito == EsitoSelezione.IN_ATTESA.name }.forEach { aggiungi(it.lega, it.inizio) }
        osservazioni.forEach { aggiungi(it.lega, it.inizio) }

        val tutteLeLeghe = (Leghe.CALCIO + Leghe.BASKET).associateBy { it.codice }
        val eventi: Map<String, Evento> = richieste.mapNotNull { (codice, g) -> tutteLeLeghe[codice]?.let { l: Lega -> async { espn.partite(l, g) } } }
            .awaitAll().flatten().associateBy { it.id }

        var vinte = 0; var perse = 0; var rimborsate = 0; var inCorso = 0
        withContext(Dispatchers.IO) {
            db.runInTransaction {
                for (s in aperte) {
                    val aggiornate = s.selezioni.map { sel ->
                        if (sel.esito != EsitoSelezione.IN_ATTESA.name) sel
                        else {
                            val ev = eventi[sel.eventoId]
                            val scelta = Scommessa.di(sel.scelta) ?: return@map sel.copy(esito = EsitoSelezione.NULLA.name)
                            val esito = Verifica.esitoSelezione(scelta, sel.inizio, ev, adesso)
                            sel.copy(esito = esito.name, risultato = ev?.risultato ?: sel.risultato)
                        }
                    }
                    db.schedine().aggiornaSelezioni(aggiornate)
                    val esiti = aggiornate.map { EsitoSelezione.valueOf(it.esito) }
                    val stato = Verifica.statoSchedina(esiti)
                    if (stato == StatoSchedina.IN_CORSO) {
                        inCorso++
                        db.schedine().aggiorna(s.schedina.copy(verificataIl = adesso))
                        continue
                    }
                    val quota = Verifica.quotaEffettiva(aggiornate.map { it.quota to EsitoSelezione.valueOf(it.esito) })
                    val vincita = Verifica.vincita(s.schedina.puntata, stato, quota)
                    db.schedine().aggiorna(s.schedina.copy(stato = stato.name, chiusaIl = adesso, vincita = vincita, verificataIl = adesso))
                    when (stato) {
                        StatoSchedina.VINTA -> {
                            vinte++
                            db.movimenti().inserisci(MovimentoEntity(data = adesso, tipo = TipoMovimento.VINCITA.name, importo = vincita, schedinaId = s.schedina.id))
                        }
                        StatoSchedina.RIMBORSATA -> {
                            rimborsate++
                            db.movimenti().inserisci(MovimentoEntity(data = adesso, tipo = TipoMovimento.RIMBORSO.name, importo = vincita, schedinaId = s.schedina.id))
                        }
                        else -> perse++
                    }
                }
            }
        }

        var chiuse = 0
        withContext(Dispatchers.IO) {
            db.runInTransaction {
                for (o in osservazioni) {
                    val ev = eventi[o.eventoId]
                    val esito = when {
                        ev?.esito != null -> ev.esito.name
                        ev != null && ev.stato == com.matchmind.app.dominio.StatoEvento.CONCLUSO && adesso - o.inizio > Verifica.RINVIO_MASSIMO_MS -> "NULLA"
                        adesso - o.inizio > Verifica.RICERCA_MASSIMA_MS -> "NULLA"
                        else -> null
                    }
                    if (esito != null) {
                        val conGol = esito != "NULLA"
                        db.osservazioni().chiudi(o.eventoId, esito, if (conGol) ev?.punteggioCasa else null, if (conGol) ev?.punteggioOspite else null)
                        chiuse++
                    }
                }
            }
        }
        EsitoVerifica(aperte.size, vinte, perse, rimborsate, inCorso, chiuse, espn.errori)
    }

    // ── Credito virtuale ─────────────────────────────────────────────────────

    suspend fun ricarica(importo: Double, adesso: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        require(importo > 0) { "Importo non valido" }
        db.movimenti().inserisci(MovimentoEntity(data = adesso, tipo = TipoMovimento.RICARICA.name, importo = Verifica.arrotonda(importo)))
    }

    /** Porta il saldo a zero (anche se negativo): da lì si può ricaricare un nuovo credito virtuale. */
    suspend fun azzera(adesso: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        Contabilita.azzeramento(db.movimenti().elenco().map { it.inDominio() }, adesso)?.let {
            db.movimenti().inserisci(MovimentoEntity(data = adesso, tipo = it.tipo.name, importo = it.importo))
        }
    }

    /** Cancella schedine, movimenti ed esperienza: l'app torna come appena installata. */
    suspend fun cancellaTutto() = withContext(Dispatchers.IO) {
        db.runInTransaction {
            db.schedine().svuota()
            db.movimenti().svuota()
            db.osservazioni().svuota()
        }
    }
}

// ── Conversioni ──────────────────────────────────────────────────────────────

fun MovimentoEntity.inDominio() = Movimento(data, TipoMovimento.valueOf(tipo), importo, schedinaId)

fun OsservazioneEntity.inDominio(): Osservazione? {
    val e = esito?.let { runCatching { Esito.valueOf(it) }.getOrNull() } ?: return null
    val mercato = if (mercatoCasa != null && mercatoOspite != null) Probabilita(mercatoCasa, mercatoPareggio ?: 0.0, mercatoOspite) else null
    val modello = if (modelloCasa != null && modelloOspite != null) Probabilita(modelloCasa, modelloPareggio ?: 0.0, modelloOspite) else null
    return Osservazione(eventoId, Sport.valueOf(sport), lega, mercato, modello, e, pGoal, pOver, golCasa, golOspite)
}

fun zonaLocale(): ZoneId = ZoneId.systemDefault()
