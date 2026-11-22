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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Logica applicativa: crea la bolletta "Prova a guadagnare" dalle partite reali, le registra, le verifica sui risultati reali,
 * aggiorna l'esperienza e il credito virtuale.
 *
 * © Roberto Di Flumeri
 */
class Repository(private val db: MatchMindDb, private val espn: EspnClient) {

    companion object {
        /** Oltre questa media di partite giocate la stagione in corso basta da sola. */
        const val SOGLIA_STAGIONE_MATURA = 15
        /** Il motore esperienza guarda una settimana avanti: ogni partita viene vista più volte prima di giocarsi. */
        const val GIORNI_MOTORE = 7
        /** Credito finto che il motore punta su ogni sua bolletta (sempre uguale, così le percentuali si confrontano). */
        const val PUNTATA_AUTOMATICA = 10.0
    }

    data class Proposta(
        val schedina: Schedina,
        val partiteAnalizzate: Int,
        val partiteConQuote: Int,
        /** Esperienza applicata, una per sport. */
        val esperienze: List<Esperienza>,
        val erroriRete: Int,
        val amichevoliEscluse: Int = 0,
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

    /** Verifica, cashout e backup non si accavallano: una operazione alla volta sull'archivio. */
    private val verificaInCorso = Mutex()

    val schedine = db.schedine().tutte()
    /** Bollette giocate da sole dal motore esperienza. */
    val automatiche = db.schedine().automatiche()
    val movimenti = db.movimenti().tutti()
    val osservazioniInAttesa: kotlinx.coroutines.flow.Flow<List<ConteggioSport>> = db.osservazioni().conteggioAperte()

    // ── Esperienza ───────────────────────────────────────────────────────────

    suspend fun esperienza(sport: Sport): Esperienza = withContext(Dispatchers.IO) {
        Esperienza.impara(sport, db.osservazioni().chiuse().mapNotNull { it.inDominio() })
    }

    // ── Generazione ──────────────────────────────────────────────────────────

    /** Partite analizzate in una finestra di giorni, con i conteggi per i messaggi all'utente. */
    private data class Finestra(
        val analisi: List<Analisi>,
        val esperienza: Esperienza,
        val amichevoliEscluse: Int,
        val giorni: Int,
        /** Partite che non erano ancora in memoria. */
        val nuove: Int = 0,
    )

    /**
     * Partite di [sport] nei prossimi [giorni] giorni di calendario (ora locale): 1 = solo oggi fino a
     * mezzanotte, 2 = oggi e domani, e così via. Nessuna partita oltre l'ultimo giorno scelto.
     */
    private suspend fun leggiFinestra(sport: Sport, giorni: Int, adesso: Long): Finestra = coroutineScope {
        val esperienza = esperienza(sport)
        val oggi = LocalDate.now(ZoneOffset.UTC)
        val leghe = Leghe.di(sport)
        val finestra = giorni.coerceIn(1, 7)
        val fine = fineFinestra(adesso, finestra, zonaLocale())

        val classifiche = leghe.map { l -> async { l.codice to classificaConStorico(l) } }
        // anche il giorno prima: il feed raggruppa per data americana (le notti europee sono il giorno prima)
        val partite = leghe.flatMap { l -> (-1..finestra).map { d -> async { espn.partite(l, oggi.plusDays(d.toLong())) } } }
        val tabelle = classifiche.awaitAll().toMap()
        val medie = tabelle.mapValues { (_, t) -> ModelliProbabilistici.mediaGolLega(t.values) }
        val tutte = partite.awaitAll().flatten().distinctBy { it.id }.filter { it.inizioMillis in adesso until fine }
        // le amichevoli di preseason non entrano né in schedina né nell'esperienza
        val ufficiali = tutte.filterNot { it.amichevole }
        val analisi = withContext(Dispatchers.Default) {
            ufficiali.mapNotNull { e -> Generatore.analizza(e, tabelle[e.lega].orEmpty(), medie[e.lega] ?: 1.35, esperienza) }
        }
        val nuove = registraOsservazioni(analisi)
        Finestra(analisi, esperienza, tutte.size - ufficiali.size, finestra, nuove)
    }

    /**
     * Un'unica bolletta sicura, calcio e basket insieme, sulle partite di oggi e dei prossimi giorni
     * ([Generatore.GIORNI_SICURA]): vincita e numero di partite li decide [Generatore.bollettaSicura].
     * La schedina è vuota se nessuna partita è abbastanza sicura.
     */
    suspend fun bollettaSicura(
        mercati: Set<Mercato> = Mercato.entries.toSet(),
        singola: Boolean = false,
        adesso: Long = System.currentTimeMillis(),
    ): Proposta = coroutineScope {
        espn.azzeraErrori()
        val finestre = Sport.entries.map { s -> async { leggiFinestra(s, Generatore.GIORNI_SICURA, adesso) } }.awaitAll()
        val analisi = finestre.flatMap { it.analisi }
        Proposta(
            schedina = Generatore.bollettaSicura(analisi, adesso, mercati, singola),
            partiteAnalizzate = analisi.size,
            partiteConQuote = analisi.count { it.mercato != null },
            esperienze = finestre.map { it.esperienza },
            erroriRete = espn.errori,
            amichevoliEscluse = finestre.sumOf { it.amichevoliEscluse },
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

    /**
     * Ogni partita analizzata diventa memoria: quando avrà un risultato insegnerà qualcosa al modello.
     * Una partita già in memoria e non ancora giocata viene aggiornata con la previsione più recente (quote e
     * classifiche più fresche), senza perdere quote o statistica se la nuova lettura non le ha. Le partite già
     * chiuse non si toccano. Restituisce quante partite sono nuove.
     */
    private suspend fun registraOsservazioni(analisi: List<Analisi>): Int = withContext(Dispatchers.IO) {
        val nuove = analisi.distinctBy { it.evento.id }.map { a ->
            OsservazioneEntity(
                eventoId = a.evento.id, sport = a.evento.sport.name, lega = a.evento.lega, inizio = a.evento.inizioMillis,
                mercatoCasa = a.mercato?.casa, mercatoPareggio = a.mercato?.pareggio, mercatoOspite = a.mercato?.ospite,
                modelloCasa = a.modello?.casa, modelloPareggio = a.modello?.pareggio, modelloOspite = a.modello?.ospite,
                pGoal = a.pGoal, pOver = a.pOver, casaId = a.evento.casaId, ospiteId = a.evento.ospiteId,
            )
        }
        val esistenti = nuove.map { it.eventoId }.chunked(500).flatMap { db.osservazioni().perId(it) }.associateBy { it.eventoId }
        val daAggiornare = nuove.mapNotNull { n ->
            val v = esistenti[n.eventoId] ?: return@mapNotNull null
            if (v.esito != null) return@mapNotNull null
            val conQuote = n.mercatoCasa != null
            val conModello = n.modelloCasa != null
            n.copy(
                mercatoCasa = if (conQuote) n.mercatoCasa else v.mercatoCasa,
                mercatoPareggio = if (conQuote) n.mercatoPareggio else v.mercatoPareggio,
                mercatoOspite = if (conQuote) n.mercatoOspite else v.mercatoOspite,
                modelloCasa = if (conModello) n.modelloCasa else v.modelloCasa,
                modelloPareggio = if (conModello) n.modelloPareggio else v.modelloPareggio,
                modelloOspite = if (conModello) n.modelloOspite else v.modelloOspite,
                pGoal = n.pGoal ?: v.pGoal, pOver = n.pOver ?: v.pOver,
            )
        }
        db.runInTransaction {
            db.osservazioni().inserisci(nuove.filter { it.eventoId !in esistenti })
            db.osservazioni().sostituisci(daAggiornare)
        }
        nuove.count { it.eventoId !in esistenti }
    }

    // ── Motore esperienza ────────────────────────────────────────────────────

    data class EsitoMotore(
        /** Partite analizzate in questo giro (tutte le leghe, prossimi [GIORNI_MOTORE] giorni). */
        val analizzate: Int,
        /** Di queste, quante non erano ancora in memoria. */
        val nuove: Int,
        /** Partite giocate che hanno appena insegnato qualcosa al modello. */
        val imparate: Int,
        /** Totale delle partite da cui il modello ha imparato finora. */
        val totaleImparate: Int,
        val erroriRete: Int,
        /** Bollette che il motore ha appena giocato da solo (al massimo una per tipo al giorno). */
        val autoGiocate: Int = 0,
    )

    /**
     * Il "cervello" che lavora anche se non giochi: chiude sui risultati reali tutte le partite già giocate (e le
     * schedine in corso), poi analizza TUTTE le partite di tutte le leghe dei prossimi [GIORNI_MOTORE] giorni e le
     * mette in memoria. Ogni giro ripetuto aggiorna le previsioni con le quote più recenti. Gira da solo due volte al
     * giorno ([MotoreEsperienzaWorker]) o a richiesta.
     */
    suspend fun motoreEsperienza(adesso: Long = System.currentTimeMillis()): EsitoMotore = coroutineScope {
        val verifica = verifica(adesso)
        espn.azzeraErrori()
        val finestre = Sport.entries.map { s -> async { leggiFinestra(s, GIORNI_MOTORE, adesso) } }.awaitAll()
        val autoGiocate = autoGioca(finestre.flatMap { it.analisi }, adesso)
        EsitoMotore(
            analizzate = finestre.sumOf { it.analisi.size },
            nuove = finestre.sumOf { it.nuove },
            autoGiocate = autoGiocate,
            imparate = verifica.osservazioniChiuse,
            totaleImparate = withContext(Dispatchers.IO) { db.osservazioni().numeroChiuse() },
            erroriRete = verifica.erroriRete + espn.errori,
        )
    }

    /**
     * Il motore gioca da solo, una volta al giorno per tipo, la stessa Bolletta sicura e la stessa Singola 80% che
     * proporrebbe a te (stessa esperienza, stesse regole, stesse partite dei prossimi [Generatore.GIORNI_SICURA]
     * giorni), con [PUNTATA_AUTOMATICA] di credito finto. Verranno validate sui risultati reali come le tue.
     */
    private suspend fun autoGioca(analisi: List<Analisi>, adesso: Long): Int = withContext(Dispatchers.IO) {
        val zona = zonaLocale()
        val inizioGiorno = Instant.ofEpochMilli(adesso).atZone(zona).toLocalDate().atStartOfDay(zona).toInstant().toEpochMilli()
        val giaGiocate = db.schedine().strategieAutomaticheDal(inizioGiorno).toSet()
        val fine = fineFinestra(adesso, Generatore.GIORNI_SICURA, zona)
        val vicine = analisi.filter { it.evento.inizioMillis < fine }
        val mercati = Mercato.entries.toSet()
        listOf(false, true).count { singola ->
            val tipo = if (singola) com.matchmind.app.dominio.Strategia.SINGOLA else com.matchmind.app.dominio.Strategia.SICURA
            if (tipo.name in giaGiocate) return@count false
            val s = Generatore.bollettaSicura(vicine, adesso, mercati, singola)
            if (s.pronostici.isEmpty()) return@count false
            registra(s, PUNTATA_AUTOMATICA, adesso, automatica = true)
            true
        }
    }

    suspend fun gioca(schedina: Schedina, puntata: Double, adesso: Long = System.currentTimeMillis()): Long = withContext(Dispatchers.IO) {
        registra(schedina, puntata, adesso, automatica = false)
    }

    /** Salva la schedina; solo quelle vere scalano la puntata dal credito. */
    private fun registra(schedina: Schedina, puntata: Double, adesso: Long, automatica: Boolean): Long {
        require(schedina.pronostici.isNotEmpty()) { "Schedina vuota" }
        require(puntata > 0) { "Puntata non valida" }
        return db.runInTransaction<Long> {
            val id = db.schedine().inserisci(SchedinaEntity(automatica = automatica,
                sport = schedina.sport?.name ?: com.matchmind.app.dominio.SPORT_MISTO, strategia = schedina.strategia.name, creataIl = adesso, puntata = puntata,
                quotaTotale = schedina.quotaTotale, probabilita = schedina.probabilita,
                stato = StatoSchedina.IN_CORSO.name,
            ))
            db.schedine().inserisciSelezioni(schedina.pronostici.map { p ->
                SelezioneEntity(
                    schedinaId = id, eventoId = p.evento.id, lega = p.evento.lega, inizio = p.evento.inizioMillis,
                    casa = p.evento.casa, ospite = p.evento.ospite, scelta = p.scelta.name, quota = p.quota,
                    quotaStimata = p.quotaStimata, probabilita = p.probabilita, pMercato = p.pMercato, pModello = p.pModello,
                    esito = EsitoSelezione.IN_ATTESA.name, storico = p.storico,
                )
            })
            if (!automatica) db.movimenti().inserisci(MovimentoEntity(data = adesso, tipo = TipoMovimento.PUNTATA.name, importo = -puntata, schedinaId = id))
            id
        }
    }

    // ── Verifica sui risultati reali ─────────────────────────────────────────

    /**
     * Verifica le schedine in corso sui risultati reali e chiude le osservazioni (esperienza).
     */
    suspend fun verifica(adesso: Long = System.currentTimeMillis()): EsitoVerifica =
        verificaInCorso.withLock { verificaSenzaBlocco(adesso) }

    private suspend fun verificaSenzaBlocco(adesso: Long): EsitoVerifica = coroutineScope {
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
                            val risultato = ev?.risultato ?: sel.risultato
                            sel.copy(esito = esito.name, risultato = risultato)
                        }
                    }
                    db.schedine().aggiornaSelezioni(aggiornate)
                    val esiti = aggiornate.map { EsitoSelezione.valueOf(it.esito) }
                    val stato = Verifica.statoSchedina(esiti)
                    if (stato == StatoSchedina.IN_CORSO) {
                        if (!s.schedina.automatica) inCorso++
                        db.schedine().aggiorna(s.schedina.copy(verificataIl = adesso))
                        continue
                    }
                    val quota = Verifica.quotaEffettiva(aggiornate.map { it.quota to EsitoSelezione.valueOf(it.esito) })
                    val vincita = Verifica.vincita(s.schedina.puntata, stato, quota)
                    db.schedine().aggiorna(s.schedina.copy(stato = stato.name, chiusaIl = adesso, vincita = vincita, verificataIl = adesso))
                    when (stato) {
                        StatoSchedina.VINTA -> {
                            if (!s.schedina.automatica) vinte++
                            if (!s.schedina.automatica) db.movimenti().inserisci(MovimentoEntity(data = adesso, tipo = TipoMovimento.VINCITA.name, importo = vincita, schedinaId = s.schedina.id))
                        }
                        StatoSchedina.RIMBORSATA -> {
                            if (!s.schedina.automatica) rimborsate++
                            if (!s.schedina.automatica) db.movimenti().inserisci(MovimentoEntity(data = adesso, tipo = TipoMovimento.RIMBORSO.name, importo = vincita, schedinaId = s.schedina.id))
                        }
                        else -> if (!s.schedina.automatica) perse++
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
        EsitoVerifica(aperte.count { !it.schedina.automatica }, vinte, perse, rimborsate, inCorso, chiuse, espn.errori)
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

    // ── Backup (cambio telefono) ─────────────────────────────────────────────

    /** Tutti i dati in un file di backup (testo JSON). */
    suspend fun esportaBackup(preferenze: Preferenze, adesso: Long = System.currentTimeMillis()): String = withContext(Dispatchers.IO) {
        verificaInCorso.withLock {
            val tutte = db.schedine().elenco()
            Backup.scrivi(Backup.Contenuto(
                creatoIl = adesso,
                schedine = tutte.map { it.schedina },
                selezioni = tutte.flatMap { it.selezioni },
                movimenti = db.movimenti().elenco(),
                osservazioni = db.osservazioni().elenco(),
                preferenze = preferenze,
            ))
        }
    }

    /**
     * Sostituisce tutti i dati con quelli del backup. Il file viene controllato prima e la sostituzione è
     * un'unica transazione: se qualcosa va storto i dati attuali restano come erano.
     */
    suspend fun importaBackup(testo: String): Backup.Contenuto = withContext(Dispatchers.IO) {
        val c = Backup.leggi(testo)
        verificaInCorso.withLock {
            db.runInTransaction {
                db.schedine().svuota()
                db.movimenti().svuota()
                db.osservazioni().svuota()
                db.schedine().inserisciTutte(c.schedine)
                db.schedine().inserisciSelezioni(c.selezioni)
                db.movimenti().inserisciTutti(c.movimenti)
                db.osservazioni().inserisci(c.osservazioni)
            }
        }
        c
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
    return Osservazione(eventoId, Sport.valueOf(sport), lega, mercato, modello, e, pGoal, pOver, golCasa, golOspite, casaId, ospiteId)
}

fun zonaLocale(): ZoneId = ZoneId.systemDefault()

/** Fine (esclusa) dei prossimi [giorni] giorni di calendario a partire da [adesso]: la mezzanotte dell'ultimo. */
fun fineFinestra(adesso: Long, giorni: Int, zona: ZoneId): Long =
    Instant.ofEpochMilli(adesso).atZone(zona).toLocalDate().plusDays(giorni.toLong()).atStartOfDay(zona).toInstant().toEpochMilli()
