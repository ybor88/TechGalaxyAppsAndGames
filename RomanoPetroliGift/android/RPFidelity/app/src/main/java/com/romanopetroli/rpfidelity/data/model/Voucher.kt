package com.romanopetroli.rpfidelity.data.model

import org.json.JSONObject

data class VoucherCatalogo(
    val id: Int,
    val nome: String,
    val costoPunti: Int,
    val importoPremio: Double
) {
    companion object {
        fun fromJson(json: JSONObject) = VoucherCatalogo(
            id = json.optInt("id"),
            nome = json.optString("nome"),
            costoPunti = json.optInt("costo_punti"),
            importoPremio = json.optDouble("importo_premio", 0.0)
        )
    }
}

data class Voucher(
    val id: Int,
    val codiceVoucher: String,
    val nome: String,
    val importoPremio: Double,
    val dataScadenza: String,
    val stato: String
) {
    companion object {
        fun fromJson(json: JSONObject) = Voucher(
            id = json.optInt("id"),
            codiceVoucher = json.optString("codice_voucher"),
            nome = json.optString("nome"),
            importoPremio = json.optDouble("importo_premio", 0.0),
            dataScadenza = json.optString("data_scadenza"),
            stato = json.optString("stato")
        )
    }
}
