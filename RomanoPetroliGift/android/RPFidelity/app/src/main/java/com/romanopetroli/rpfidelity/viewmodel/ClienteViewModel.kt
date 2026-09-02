package com.romanopetroli.rpfidelity.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.romanopetroli.rpfidelity.data.ApiClient
import com.romanopetroli.rpfidelity.data.model.Rifornimento
import com.romanopetroli.rpfidelity.data.model.Voucher
import com.romanopetroli.rpfidelity.data.model.VoucherCatalogo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ClienteViewModel : ViewModel() {

    private val _rifornimenti = MutableStateFlow<List<Rifornimento>>(emptyList())
    val rifornimenti: StateFlow<List<Rifornimento>> = _rifornimenti

    private val _catalogo = MutableStateFlow<List<VoucherCatalogo>>(emptyList())
    val catalogo: StateFlow<List<VoucherCatalogo>> = _catalogo

    private val _mieiVoucher = MutableStateFlow<List<Voucher>>(emptyList())
    val mieiVoucher: StateFlow<List<Voucher>> = _mieiVoucher

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _messaggio = MutableStateFlow<String?>(null)
    val messaggio: StateFlow<String?> = _messaggio

    fun caricaRifornimenti(dal: String? = null, al: String? = null) {
        viewModelScope.launch {
            _loading.value = true
            val query = buildMap {
                dal?.takeIf { it.isNotBlank() }?.let { put("dal", it) }
                al?.takeIf { it.isNotBlank() }?.let { put("al", it) }
            }
            val result = ApiClient.get("/rifornimenti", query)
            _loading.value = false
            if (result.success) {
                val array = result.body.optJSONArray("rifornimenti")
                _rifornimenti.value = (0 until (array?.length() ?: 0)).map { Rifornimento.fromJson(array!!.getJSONObject(it)) }
            } else {
                _error.value = result.errorMessage
            }
        }
    }

    fun caricaVoucher() {
        viewModelScope.launch {
            _loading.value = true
            val catalogoResult = ApiClient.get("/voucher/catalogo")
            val mieiResult = ApiClient.get("/voucher/miei")
            _loading.value = false

            if (catalogoResult.success) {
                val array = catalogoResult.body.optJSONArray("catalogo")
                _catalogo.value = (0 until (array?.length() ?: 0)).map { VoucherCatalogo.fromJson(array!!.getJSONObject(it)) }
            } else {
                _error.value = catalogoResult.errorMessage
            }

            if (mieiResult.success) {
                val array = mieiResult.body.optJSONArray("voucher")
                _mieiVoucher.value = (0 until (array?.length() ?: 0)).map { Voucher.fromJson(array!!.getJSONObject(it)) }
            } else if (_error.value == null) {
                _error.value = mieiResult.errorMessage
            }
        }
    }

    fun riscatta(voucherCatalogoId: Int, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = ApiClient.post("/voucher/riscatta", mapOf("voucher_catalogo_id" to voucherCatalogoId))
            _loading.value = false
            if (result.success) {
                _messaggio.value = "Voucher riscattato con successo!"
                caricaVoucher()
                onSuccess()
            } else {
                _error.value = result.errorMessage
            }
        }
    }

    fun clearMessages() {
        _error.value = null
        _messaggio.value = null
    }
}
