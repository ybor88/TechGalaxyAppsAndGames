package com.romanopetroli.rpfidelity.data.model

import org.json.JSONObject

data class Distributore(
    val nome: String,
    val indirizzo: String?,
    val citta: String?
) {
    companion object {
        fun fromJson(json: JSONObject) = Distributore(
            nome = json.optString("nome"),
            indirizzo = json.optNullableString("indirizzo"),
            citta = json.optNullableString("citta")
        )
    }
}
