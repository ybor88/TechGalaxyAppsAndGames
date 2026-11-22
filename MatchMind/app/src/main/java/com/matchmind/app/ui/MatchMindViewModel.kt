package com.matchmind.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.matchmind.app.MatchMindApp
import com.matchmind.app.dati.ConteggioSport
import com.matchmind.app.dati.Preferenze
import com.matchmind.app.dati.Repository
import com.matchmind.app.dati.SchedinaCompleta
import com.matchmind.app.dati.inDominio
import com.matchmind.app.dominio.Contabilita
import com.matchmind.app.dominio.Esperienza
import com.matchmind.app.dominio.Generatore
import com.matchmind.app.dominio.Movimento
import com.matchmind.app.dominio.SchedinaChiusa
import com.matchmind.app.dominio.Sport
import com.matchmind.app.dominio.StatoSchedina
import com.matchmind.app.dominio.Strategia
import com.matchmind.app.dominio.sportDi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Stato e azioni dell'app. © Roberto Di Flumeri
class MatchMindViewModel(app: Application) : AndroidViewModel(app) {
    private val matchMind = app as MatchMindApp
    private val repo = matchMind.repository
    private val impostazioni = matchMind.impostazioni

    val preferenze: StateFlow<Preferenze?> = impostazioni.preferenze.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val schedine: StateFlow<List<SchedinaCompleta>> = repo.schedine.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** Bollette giocate da sole dal motore esperienza (credito finto). */
    val automatiche: StateFlow<List<com.matchmind.app.dati.SchedinaEntity>> =
        repo.automatiche.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val movimenti: StateFlow<List<Movimento>> = repo.movimenti.map { l -> l.map { it.inDominio() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** Partite analizzate in attesa di risultato, per sport. */
    val osservazioniInAttesa: StateFlow<Map<Sport, Int>> = repo.osservazioniInAttesa
        .map { l: List<ConteggioSport> ->
            l.mapNotNull { c -> Sport.entries.firstOrNull { it.name == c.sport }?.let { sport -> sport to c.numero } }.toMap()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _attivita = MutableStateFlow<String?>(null)
    /** Messaggio dell'operazione in corso (null = nessuna). */
    val attivita = _attivita.asStateFlow()

    private val _messaggio = MutableStateFlow<String?>(null)
    val messaggio = _messaggio.asStateFlow()

    private val _proposta = MutableStateFlow<Repository.Proposta?>(null)
    val proposta = _proposta.asStateFlow()

    private val _esperienza = MutableStateFlow(mapOf(Sport.CALCIO to Esperienza.vuota(Sport.CALCIO), Sport.BASKET to Esperienza.vuota(Sport.BASKET)))
    val esperienza = _esperienza.asStateFlow()

    init {
        viewModelScope.launch {
            aggiornaEsperienza()
            // all'apertura si verificano in automatico le schedine con partite già giocate
            if (repo.schedine.first().any { s -> s.schedina.stato == StatoSchedina.IN_CORSO.name && s.selezioni.any { it.inizio < System.currentTimeMillis() } }) {
                verifica(silenziosa = true)
            }
        }
    }

    fun mostra(testo: String) { _messaggio.value = testo }
    fun messaggioLetto() { _messaggio.value = null }

    private suspend fun aggiornaEsperienza() {
        _esperienza.value = Sport.entries.associateWith { repo.esperienza(it) }
    }

    private fun esegui(descrizione: String, azione: suspend () -> Unit) {
        if (_attivita.value != null) return
        viewModelScope.launch {
            _attivita.value = descrizione
            try {
                azione()
            } catch (e: Exception) {
                _messaggio.value = "Operazione non riuscita: ${e.message ?: "errore imprevisto"}"
            } finally {
                _attivita.value = null
            }
        }
    }

    // ── Primo avvio e credito ────────────────────────────────────────────────

    fun configura(creditoIniziale: Double, puntata: Double) = esegui("Preparo il tuo conto virtuale…") {
        repo.ricarica(creditoIniziale)
        val p = preferenze.value ?: Preferenze()
        impostazioni.salva(p.copy(puntata = puntata, configurato = true))
    }

    fun ricarica(importo: Double) = esegui("Ricarico…") {
        repo.ricarica(importo)
        _messaggio.value = "Ricaricati ${importo.euro()} di credito virtuale"
    }

    fun azzera() = esegui("Azzero il credito…") {
        repo.azzera()
        _messaggio.value = "Credito azzerato: puoi ricaricare un nuovo saldo virtuale"
    }

    fun salva(p: Preferenze) = viewModelScope.launch { impostazioni.salva(p) }

    // ── Backup (cambio telefono) ─────────────────────────────────────────────

    /** Nome proposto per il file: MatchMind-backup-2026-11-09.json */
    fun nomeFileBackup() = "MatchMind-backup-${java.time.LocalDate.now()}.json"

    fun esportaBackup(destinazione: android.net.Uri) = esegui("Salvo il backup…") {
        val testo = repo.esportaBackup(preferenze.value ?: impostazioni.preferenze.first())
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val uscita = getApplication<Application>().contentResolver.openOutputStream(destinazione, "wt")
                ?: error("impossibile scrivere il file")
            uscita.bufferedWriter(Charsets.UTF_8).use { it.write(testo) }
        }
        val n = schedine.value.size
        _messaggio.value = "Backup salvato: $n ${if (n == 1) "schedina" else "schedine"}, credito ed esperienza. " +
            "Copialo sul nuovo telefono e usa \"Importa backup\"."
    }

    fun importaBackup(origine: android.net.Uri) = esegui("Ripristino il backup…") {
        val testo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val ingresso = getApplication<Application>().contentResolver.openInputStream(origine) ?: error("impossibile leggere il file")
            ingresso.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        val c = try {
            repo.importaBackup(testo)
        } catch (e: com.matchmind.app.dati.Backup.BackupNonValido) {
            _messaggio.value = "${e.message}. Nessun dato è stato modificato."
            return@esegui
        }
        impostazioni.salva(c.preferenze)
        aggiornaEsperienza()
        val data = if (c.creatoIl > 0) " del ${c.creatoIl.dataOra()}" else ""
        _messaggio.value = "Backup$data ripristinato: ${c.schedine.size} schedine, ${c.movimenti.size} movimenti, " +
            "${c.osservazioni.size} partite di esperienza. Saldo: ${Contabilita.saldo(c.movimenti.map { it.inDominio() }).euro()}"
    }

    fun cancellaTutto() = esegui("Cancello i dati…") {
        repo.cancellaTutto()
        impostazioni.salva(Preferenze())
        aggiornaEsperienza()
        _messaggio.value = "Dati cancellati"
    }

    // ── Bolletta sicura ──────────────────────────────────────────────────────

    /** Bolletta sicura unica, calcio e basket insieme: vincita e numero di partite li decide MatchMind (vedi [Generatore.bollettaSicura]). */
    fun bollettaSicura(singola: Boolean = false) {
        val p = preferenze.value ?: Preferenze()
        val nome = "calcio e basket"
        val soglia = if (singola) Generatore.PROBABILITA_SINGOLA else Generatore.PROBABILITA_BOLLETTA_SICURA
        esegui(if (singola) "Cerco la partita più sicura di $nome…" else "Cerco le partite più sicure di $nome…") {
            val proposta = repo.bollettaSicura(p.mercati, singola)
            aggiornaEsperienza()
            _messaggio.value = when {
                proposta.schedina.pronostici.isNotEmpty() -> { _proposta.value = proposta; null }
                proposta.erroriRete > 0 && proposta.partiteAnalizzate == 0 ->
                    "Nessuna connessione con il server dei risultati: controlla la rete e riprova."
                proposta.partiteAnalizzate == 0 ->
                    "Oggi e nei prossimi giorni non ci sono partite di $nome."
                else ->
                    "Su ${proposta.partiteAnalizzate} partite di $nome nessuna è abbastanza sicura (almeno il " +
                        "${soglia.percentuale()}, squadre affidabili): oggi meglio non giocare. Riprova più tardi."
            }
        }
    }

    fun annullaProposta() { _proposta.value = null }

    // ── Motore esperienza ────────────────────────────────────────────────────

    /** Lancia subito il motore esperienza: chiude le partite giocate e analizza tutte quelle dei prossimi giorni. */
    fun motoreEsperienza() = esegui("Il motore esperienza analizza tutte le partite di tutte le leghe…") {
        val r = repo.motoreEsperienza()
        aggiornaEsperienza()
        _messaggio.value = if (r.analizzate == 0 && r.erroriRete > 0)
            "Server dei risultati non raggiungibile: controlla la rete e riprova."
        else "Motore esperienza: ${r.analizzate} partite analizzate (${r.nuove} nuove), ${r.imparate} appena imparate. " +
            (if (r.autoGiocate > 0) "Ha giocato da solo ${r.autoGiocate} ${if (r.autoGiocate == 1) "bolletta" else "bollette"} di oggi (credito finto). " else "") +
            "Il modello ha imparato da ${r.totaleImparate} partite in tutto. Gira da solo ogni " +
            "${com.matchmind.app.dati.MotoreEsperienzaWorker.ORE_TRA_GIRI} ore, anche se non giochi."
    }

    fun gioca(puntata: Double) {
        val proposta = _proposta.value ?: return
        esegui("Registro la schedina…") {
            repo.gioca(proposta.schedina, puntata)
            _proposta.value = null
            _messaggio.value = "Schedina giocata: ${puntata.euro()} a quota ${proposta.schedina.quotaTotale.quota()}"
        }
    }

    fun verifica(silenziosa: Boolean = false) = esegui("Verifico i risultati reali…") {
        val r = repo.verifica()
        aggiornaEsperienza()
        if (silenziosa && r.vinte + r.perse + r.rimborsate == 0) return@esegui
        _messaggio.value = when {
            r.erroriRete > 0 && r.vinte + r.perse + r.rimborsate == 0 -> "Risultati non raggiungibili: controlla la connessione."
            r.controllate == 0 -> "Nessuna schedina da verificare." + if (r.osservazioniChiuse > 0) " L'esperienza ha imparato da ${r.osservazioniChiuse} nuove partite." else ""
            else -> buildString {
                append("Verificate ${r.controllate}: ")
                append(listOfNotNull(
                    r.vinte.takeIf { it > 0 }?.let { "$it vinte" },
                    r.perse.takeIf { it > 0 }?.let { "$it perse" },
                    r.rimborsate.takeIf { it > 0 }?.let { "$it rimborsate" },
                    r.ancoraInCorso.takeIf { it > 0 }?.let { "$it ancora in corso" },
                ).joinToString(", "))
                if (r.osservazioniChiuse > 0) append(". Esperienza: +${r.osservazioniChiuse} partite")
            }
        }
    }

    // ── Statistiche ──────────────────────────────────────────────────────────

    fun schedineChiuse(sport: Sport?): List<SchedinaChiusa> = schedine.value
        .filter { sport == null || it.schedina.sport == sport.name }
        .filter { it.schedina.chiusaIl != null }
        .map { SchedinaChiusa(sportDi(it.schedina.sport), it.schedina.chiusaIl!!, it.schedina.puntata, it.schedina.vincita, StatoSchedina.valueOf(it.schedina.stato), it.schedina.quotaTotale) }

    fun mesi(sport: Sport?) = Contabilita.riepilogoMensile(schedineChiuse(sport))

    /** Bollette chiuse del motore per un tipo (Sicura o Singola). */
    fun chiuseAutomatiche(tipo: Strategia): List<SchedinaChiusa> = automatiche.value
        .filter { it.strategia == tipo.name && it.chiusaIl != null }
        .map { SchedinaChiusa(sportDi(it.sport), it.chiusaIl!!, it.puntata, it.vincita, StatoSchedina.valueOf(it.stato), it.quotaTotale) }
}
