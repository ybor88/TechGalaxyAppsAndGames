package com.romanopetroli.rpfidelity.data.model

import org.json.JSONObject

data class MessaggioChat(
    val id: Int,
    val mittente: String,
    val messaggio: String,
    val creatoIl: String
) {
    val daAdmin: Boolean get() = mittente == "admin"

    companion object {
        fun fromJson(json: JSONObject) = MessaggioChat(
            id = json.optInt("id"),
            mittente = json.optString("mittente"),
            messaggio = json.optString("messaggio"),
            creatoIl = json.optString("creato_il")
        )
    }
}

data class ConversazioneCliente(
    val clienteId: Int,
    val nome: String,
    val cognome: String,
    val email: String,
    val ultimoMessaggio: String,
    val ultimoMittente: String,
    val ultimoIl: String
) {
    companion object {
        fun fromJson(json: JSONObject) = ConversazioneCliente(
            clienteId = json.optInt("cliente_id"),
            nome = json.optString("nome"),
            cognome = json.optString("cognome"),
            email = json.optString("email"),
            ultimoMessaggio = json.optString("ultimo_messaggio"),
            ultimoMittente = json.optString("ultimo_mittente"),
            ultimoIl = json.optString("ultimo_il")
        )
    }
}
