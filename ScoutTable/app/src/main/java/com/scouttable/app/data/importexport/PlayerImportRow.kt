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
    val golSubiti: Int = 0,
    val presenzeNazionale: Int = 0,
    val punteggioNazionale: Int = 0,
    val assistNazionale: Int = 0,
    val rimbalzi: Int = 0,
    val palleRecuperate: Int = 0,
    val tiriDaDue: Int = 0,
    val tiriDaTre: Int = 0,
    val rimbalziNazionale: Int = 0,
    val palleRecuperateNazionale: Int = 0,
    val tackle: Int = 0,
    val golEvitati: Int = 0,
    val golSubitiNazionale: Int = 0,
    val percentualeTiriDaDue: Int = 0,
    val percentualeTiriDaTre: Int = 0,
    val proballersUrl: String? = null,
)
