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
    val isDipendente: Boolean get() = ruolo == "dipendente"

    // Admin e dipendente: entrambi possono operare alla cassa (registrare rifornimenti).
    val isStaff: Boolean get() = isAdmin || isDipendente

    companion object {
        fun fromJson(json: JSONObject): User = User(
            id = json.optInt("id"),
            nome = json.optString("nome"),
            cognome = json.optString("cognome"),
            email = json.optString("email"),
            telefono = json.optNullableString("telefono"),
            ruolo = json.optString("ruolo"),
            puntiSaldo = json.optDouble("punti_saldo", 0.0),
            codiceCard = json.optNullableString("codice_card")
        )
    }
}
