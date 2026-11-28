// PayRecap — © Roberto Di Flumeri
package com.payrecap.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.payrecap.app.dati.EsitoEstrazione
import com.payrecap.app.dati.Estrattore
import com.payrecap.app.dati.PayRecapDb
import com.payrecap.app.dati.Preferenze
import com.payrecap.app.dominio.Cedolino
import com.payrecap.app.dominio.Esempi
import com.payrecap.app.dominio.Impostazioni
import com.payrecap.app.dominio.ParserCedolino
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class Scheda { RIEPILOGO, STORICO, IMPOSTAZIONI }

sealed class StatoCaricamento {
    data object Nessuno : StatoCaricamento()
    data class Elaborazione(val messaggio: String) : StatoCaricamento()
    data class Password(val uri: Uri, val errata: Boolean) : StatoCaricamento()
    data class Revisione(
        val bozza: Cedolino,
        val riconosciuti: Int?,
        val totali: Int?,
        val mancanti: List<String>,
        val metodo: String?,
        val nuovo: Boolean,
    ) : StatoCaricamento()
    data class Errore(val messaggio: String) : StatoCaricamento()
}

class PayRecapViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = PayRecapDb.get(app).cedolini()
    private val estrattore = Estrattore(app)
    private val preferenze = Preferenze(app)

    /** null finché il database non ha risposto: evita di mostrare per un attimo lo stato "nessun cedolino". */
    val cedolini: StateFlow<List<Cedolino>?> =
        dao.tutti().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _scheda = MutableStateFlow(Scheda.RIEPILOGO)
    val scheda = _scheda.asStateFlow()

    private val _selezionato = MutableStateFlow<Long?>(null)
    val selezionato = _selezionato.asStateFlow()

    private val _caricamento = MutableStateFlow<StatoCaricamento>(StatoCaricamento.Nessuno)
    val caricamento = _caricamento.asStateFlow()

    private val _impostazioni = MutableStateFlow(preferenze.leggi())
    val impostazioni = _impostazioni.asStateFlow()

    fun vaiA(s: Scheda) { _scheda.value = s }

    fun seleziona(id: Long) {
        _selezionato.value = id
        _scheda.value = Scheda.RIEPILOGO
    }

    // ------------------------------------------------------------ caricamento documento

    fun caricaDocumento(uri: Uri, password: String? = null) {
        _caricamento.value = StatoCaricamento.Elaborazione("Lettura del cedolino in corso…")
        viewModelScope.launch {
            when (val esito = estrattore.estrai(uri, password)) {
                is EsitoEstrazione.PasswordRichiesta -> _caricamento.value = StatoCaricamento.Password(uri, false)
                is EsitoEstrazione.PasswordErrata -> _caricamento.value = StatoCaricamento.Password(uri, true)
                is EsitoEstrazione.Errore -> _caricamento.value = StatoCaricamento.Errore(esito.messaggio)
                is EsitoEstrazione.Testo -> {
                    if (esito.testo.isBlank()) {
                        _caricamento.value = StatoCaricamento.Errore(
                            "Nel documento non è stato trovato testo leggibile. Prova con un PDF originale o una foto più nitida.",
                        )
                    } else {
                        apriRevisione(esito.testo, esito.nomeFile, esito.metodo)
                    }
                }
            }
        }
    }

    private fun apriRevisione(testo: String, nomeFile: String?, metodo: String) {
        val oggi = LocalDate.now()
        val r = ParserCedolino.analizza(testo, oggi.year, oggi.monthValue, nomeFile)
        _caricamento.value = StatoCaricamento.Revisione(r.cedolino, r.campiRiconosciuti, r.campiTotali, r.campiMancanti, metodo, nuovo = true)
    }

    /** Apre la revisione sul cedolino di esempio di novembre (demo e screenshot). */
    fun apriRevisioneEsempio() = apriRevisione(Esempi.NOVEMBRE_2026, "esempio_novembre_2026.pdf", "Testo PDF")

    fun modifica(c: Cedolino) {
        _caricamento.value = StatoCaricamento.Revisione(c, null, null, emptyList(), null, nuovo = false)
    }

    fun annullaCaricamento() { _caricamento.value = StatoCaricamento.Nessuno }

    fun salva(c: Cedolino) {
        viewModelScope.launch {
            // un solo cedolino per periodo: quello nuovo sostituisce il precedente dello stesso mese
            val esistente = dao.perPeriodo(c.anno, c.mese, c.aggiuntiva)
            val daSalvare = if (esistente != null && esistente.id != c.id) {
                if (c.id != 0L) dao.elimina(c)
                c.copy(id = esistente.id)
            } else c
            val id = dao.salva(daSalvare)
            _selezionato.value = if (daSalvare.id != 0L) daSalvare.id else id
            _caricamento.value = StatoCaricamento.Nessuno
            _scheda.value = Scheda.RIEPILOGO
        }
    }

    fun elimina(c: Cedolino) {
        viewModelScope.launch {
            dao.elimina(c)
            if (_selezionato.value == c.id) _selezionato.value = null
        }
    }

    /** Carica due cedolini fittizi (ottobre e novembre 2026) passando dal parser vero. */
    fun caricaEsempi() {
        viewModelScope.launch {
            var ultimo = 0L
            for ((testo, nome) in listOf(Esempi.OTTOBRE_2026 to "esempio_ottobre_2026.pdf", Esempi.NOVEMBRE_2026 to "esempio_novembre_2026.pdf")) {
                val c = ParserCedolino.analizza(testo, 2026, 11, nome).cedolino
                val esistente = dao.perPeriodo(c.anno, c.mese, c.aggiuntiva)
                ultimo = dao.salva(if (esistente != null) c.copy(id = esistente.id) else c).let { if (esistente != null) esistente.id else it }
            }
            _selezionato.value = ultimo
            _scheda.value = Scheda.RIEPILOGO
        }
    }

    fun eliminaTutto() {
        viewModelScope.launch {
            dao.eliminaTutti()
            _selezionato.value = null
        }
    }

    fun aggiornaImpostazioni(i: Impostazioni) {
        _impostazioni.value = i
        preferenze.scrivi(i)
    }
}
