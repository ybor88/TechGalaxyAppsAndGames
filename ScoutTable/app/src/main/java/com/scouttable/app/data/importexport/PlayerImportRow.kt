package com.scouttable.app.data.importexport

/** Una riga di dati pronta per essere scritta come [com.scouttable.app.data.Player] (da ricerca internet). */
data class PlayerImportRow(
    val id: String?,
    val nome: String,
    val anno: Int,
    val carrieraMigliore: String,
    val stato: String,
    val nazione: String,
    /** URL dell'immagine (stemma del club), o null se non trovata. */
    val logoPath: String?,
    val ruolo: String = "",
    val presenze: Int = 0,
    val punteggio: Int = 0,
    val assist: Int = 0,
    val competizione: String = "",
    val proballersUrl: String? = null,
)
