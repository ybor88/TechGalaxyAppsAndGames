package com.romanopetroli.rpfidelity.data.model

import org.json.JSONObject

data class Rifornimento(
    val id: Int,
    val dataOra: String,
    val importo: Double,
    val importoPagato: Double,
    val importoVoucher: Double,
    val puntiMaturati: Double
) {
    companion object {
        fun fromJson(json: JSONObject) = Rifornimento(
            id = json.optInt("id"),
            dataOra = json.optString("data_ora"),
            importo = json.optDouble("importo", 0.0),
            importoPagato = json.optDouble("importo_pagato", 0.0),
            importoVoucher = json.optDouble("importo_voucher", 0.0),
            puntiMaturati = json.optDouble("punti_maturati", 0.0)
        )
    }
}

data class RifornimentoReport(
    val id: Int,
    val dataOra: String,
    val codiceRifornimento: String,
    val clienteNome: String?,
    val clienteCognome: String?,
    val importo: Double,
    val importoPagato: Double,
    val importoVoucher: Double
) {
    companion object {
        fun fromJson(json: JSONObject) = RifornimentoReport(
            id = json.optInt("id"),
            dataOra = json.optString("data_ora"),
            codiceRifornimento = json.optString("codice_rifornimento"),
            clienteNome = json.optString("cliente_nome").ifBlank { null },
            clienteCognome = json.optString("cliente_cognome").ifBlank { null },
            importo = json.optDouble("importo", 0.0),
            importoPagato = json.optDouble("importo_pagato", 0.0),
            importoVoucher = json.optDouble("importo_voucher", 0.0)
        )
    }
}

data class TotaliReport(
    val totaleRifornimenti: Double,
    val totaleVoucher: Double,
    val saldo: Double
) {
    companion object {
        fun fromJson(json: JSONObject) = TotaliReport(
            totaleRifornimenti = json.optDouble("totale_rifornimenti", 0.0),
            totaleVoucher = json.optDouble("totale_voucher", 0.0),
            saldo = json.optDouble("saldo", 0.0)
        )
    }
}
