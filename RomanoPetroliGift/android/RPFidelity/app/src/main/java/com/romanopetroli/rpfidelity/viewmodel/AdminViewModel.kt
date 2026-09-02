package com.romanopetroli.rpfidelity.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.romanopetroli.rpfidelity.data.ApiClient
import com.romanopetroli.rpfidelity.data.model.RifornimentoReport
import com.romanopetroli.rpfidelity.data.model.TotaliReport
import com.romanopetroli.rpfidelity.data.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class CarrelloVoucher(
    val codiceVoucher: String,
    val nome: String,
    val importoPremio: Double
)

class AdminViewModel : ViewModel() {

    private val _clienteIdentificato = MutableStateFlow<User?>(null)
    val clienteIdentificato: StateFlow<User?> = _clienteIdentificato

    private val _carrelloVoucher = MutableStateFlow<List<CarrelloVoucher>>(emptyList())
    val carrelloVoucher: StateFlow<List<CarrelloVoucher>> = _carrelloVoucher

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _successo = MutableStateFlow<String?>(null)
    val successo: StateFlow<String?> = _successo

    private val _reportRifornimenti = MutableStateFlow<List<RifornimentoReport>>(emptyList())
    val reportRifornimenti: StateFlow<List<RifornimentoReport>> = _reportRifornimenti

    private val _reportTotali = MutableStateFlow<TotaliReport?>(null)
    val reportTotali: StateFlow<TotaliReport?> = _reportTotali

    private val _voucherVerificato = MutableStateFlow<JSONObject?>(null)
    val voucherVerificato: StateFlow<JSONObject?> = _voucherVerificato

    fun identificaCliente(codiceCard: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = ApiClient.get("/admin/clienti/cerca", mapOf("codice_card" to codiceCard))
            _loading.value = false
            if (result.success) {
                val userJson = result.body.optJSONObject("cliente")
                if (userJson != null) {
                    _clienteIdentificato.value = User.fromJson(userJson)
                    _carrelloVoucher.value = emptyList()
                }
            } else {
                _error.value = result.errorMessage
            }
        }
    }

    fun cambiaCliente() {
        _clienteIdentificato.value = null
        _carrelloVoucher.value = emptyList()
        _successo.value = null
        _error.value = null
    }

    fun aggiungiVoucher(codice: String) {
        val cliente = _clienteIdentificato.value
        if (cliente == null) {
            _error.value = "Identifica prima il cliente."
            return
        }
        if (_carrelloVoucher.value.any { it.codiceVoucher.equals(codice, ignoreCase = true) }) {
            _error.value = "Voucher già aggiunto a questo rifornimento."
            return
        }

        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = ApiClient.get(
                "/admin/voucher/verifica",
                mapOf("codice" to codice, "codice_card" to (cliente.codiceCard ?: ""))
            )
            _loading.value = false
            if (result.success) {
                val v = result.body.optJSONObject("voucher")
                if (v != null) {
                    _carrelloVoucher.value = _carrelloVoucher.value + CarrelloVoucher(
                        codiceVoucher = v.optString("codice_voucher"),
                        nome = v.optString("nome"),
                        importoPremio = v.optDouble("importo_premio", 0.0)
                    )
                }
            } else {
                _error.value = result.errorMessage
            }
        }
    }

    fun rimuoviVoucher(codice: String) {
        _carrelloVoucher.value = _carrelloVoucher.value.filterNot { it.codiceVoucher == codice }
    }

    fun confermaRifornimento(importo: Double, onSuccess: () -> Unit) {
        val cliente = _clienteIdentificato.value
        if (cliente == null) {
            _error.value = "Identifica prima il cliente."
            return
        }

        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = ApiClient.post(
                "/admin/rifornimenti",
                mapOf(
                    "codice_card" to cliente.codiceCard,
                    "importo" to importo,
                    "voucher_codici" to _carrelloVoucher.value.map { it.codiceVoucher }
                )
            )
            _loading.value = false
            if (result.success) {
                val puntiMaturati = result.body.optDouble("punti_maturati", 0.0)
                val importoPagato = result.body.optDouble("importo_pagato", 0.0)
                _successo.value = "Rifornimento registrato: %.2f€ pagati, %.2f punti accreditati a %s %s.".format(
                    importoPagato, puntiMaturati, cliente.nome, cliente.cognome
                )
                _clienteIdentificato.value = null
                _carrelloVoucher.value = emptyList()
                onSuccess()
            } else {
                _error.value = result.errorMessage
            }
        }
    }

    fun caricaReports(dal: String? = null, al: String? = null) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val query = buildMap {
                dal?.takeIf { it.isNotBlank() }?.let { put("dal", it) }
                al?.takeIf { it.isNotBlank() }?.let { put("al", it) }
            }
            val result = ApiClient.get("/admin/reports", query)
            _loading.value = false
            if (result.success) {
                val array = result.body.optJSONArray("rifornimenti")
                _reportRifornimenti.value = (0 until (array?.length() ?: 0)).map {
                    RifornimentoReport.fromJson(array!!.getJSONObject(it))
                }
                result.body.optJSONObject("totali")?.let { _reportTotali.value = TotaliReport.fromJson(it) }
            } else {
                _error.value = result.errorMessage
            }
        }
    }

    fun verificaVoucher(codice: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _voucherVerificato.value = null
            val result = ApiClient.get("/admin/verifica-voucher", mapOf("codice" to codice))
            _loading.value = false
            if (result.success) {
                _voucherVerificato.value = result.body.optJSONObject("voucher")
            } else {
                _error.value = result.errorMessage
            }
        }
    }

    fun usaVoucher(voucherId: Int, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            val result = ApiClient.post("/admin/verifica-voucher/usa", mapOf("voucher_id" to voucherId))
            _loading.value = false
            if (result.success) {
                _voucherVerificato.value = null
                onSuccess()
            } else {
                _error.value = result.errorMessage
            }
        }
    }

    fun clearMessages() {
        _error.value = null
        _successo.value = null
    }
}
