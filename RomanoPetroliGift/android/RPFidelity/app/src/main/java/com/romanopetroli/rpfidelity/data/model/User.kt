package com.romanopetroli.rpfidelity.data.model

import org.json.JSONObject

data class User(
    val id: Int,
    val nome: String,
    val cognome: String,
    val email: String,
    val telefono: String?,
    val ruolo: String,
    val puntiSaldo: Double,
    val codiceCard: String?
) {
    val isAdmin: Boolean get() = ruolo == "admin"

    companion object {
        fun fromJson(json: JSONObject): User = User(
            id = json.optInt("id"),
            nome = json.optString("nome"),
            cognome = json.optString("cognome"),
            email = json.optString("email"),
            telefono = json.optString("telefono").ifBlank { null },
            ruolo = json.optString("ruolo"),
            puntiSaldo = json.optDouble("punti_saldo", 0.0),
            codiceCard = json.optString("codice_card").ifBlank { null }
        )
    }
}
