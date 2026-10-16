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
import com.matchmind.app.dominio.Movimento
import com.matchmind.app.dominio.SchedinaChiusa
import com.matchmind.app.dominio.Sport
import com.matchmind.app.dominio.StatoSchedina
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

    fun cancellaTutto() = esegui("Cancello i dati…") {
        repo.cancellaTutto()
        impostazioni.salva(Preferenze())
        aggiornaEsperienza()
        _messaggio.value = "Dati cancellati"
    }

    // ── Schedine ─────────────────────────────────────────────────────────────

    fun genera(sport: Sport) {
        val p = preferenze.value ?: Preferenze()
        esegui("Leggo partite e quote reali di ${sport.etichetta.lowercase()}…") {
            val proposta = repo.genera(sport, p.giorni, p.strategia, p.sicurezza, p.mercati)
            aggiornaEsperienza()
            val nome = sport.etichetta.lowercase()
            when {
                proposta.schedina.pronostici.isNotEmpty() -> _proposta.value = proposta
                proposta.erroriRete > 0 && proposta.partiteAnalizzate == 0 ->
                    _messaggio.value = "Nessuna connessione con il server dei risultati: controlla la rete e riprova."
                proposta.partiteAnalizzate == 0 && proposta.amichevoliEscluse > 0 ->
                    _messaggio.value = "Nei prossimi ${proposta.giorni} giorni ci sono solo ${proposta.amichevoliEscluse} partite di preseason di $nome: " +
                        "non sono adatte a pronostici affidabili. Riprova quando inizia la stagione regolare."
                proposta.partiteAnalizzate == 0 ->
                    _messaggio.value = "Nei prossimi ${proposta.giorni} giorni non ci sono partite di $nome."
                else ->
                    _messaggio.value = "Su ${proposta.partiteAnalizzate} partite di $nome nessun pronostico è abbastanza sicuro per una schedina " +
                        "con almeno il ${p.sicurezza.percentuale()} di probabilità. Puoi abbassare la sicurezza o allargare i giorni nelle impostazioni."
            }
        }
    }

    fun annullaProposta() { _proposta.value = null }

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
        .map { SchedinaChiusa(Sport.valueOf(it.schedina.sport), it.schedina.chiusaIl!!, it.schedina.puntata, it.schedina.vincita, StatoSchedina.valueOf(it.schedina.stato)) }

    fun mesi(sport: Sport?) = Contabilita.riepilogoMensile(schedineChiuse(sport))
}
