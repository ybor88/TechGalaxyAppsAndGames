package com.romanopetroli.rpfidelity.data.model

import org.json.JSONObject

data class ClienteAdmin(
    val id: Int,
    val nome: String,
    val cognome: String,
    val email: String,
    val telefono: String?,
    val ruolo: String,
    val puntiSaldo: Double,
    val stato: String,
    val codiceCard: String?,
    val dataRegistrazione: String
) {
    companion object {
        fun fromJson(json: JSONObject) = ClienteAdmin(
            id = json.optInt("id"),
            nome = json.optString("nome"),
            cognome = json.optString("cognome"),
            email = json.optString("email"),
            telefono = json.optNullableString("telefono"),
            ruolo = json.optString("ruolo"),
            puntiSaldo = json.optDouble("punti_saldo", 0.0),
            stato = json.optString("stato"),
            codiceCard = json.optNullableString("codice_card"),
            dataRegistrazione = json.optString("data_registrazione")
        )
    }
}
