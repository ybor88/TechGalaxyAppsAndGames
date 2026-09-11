package com.scouttable.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class Sport {
    BASKET,
    CALCIO;

    val label: String get() = if (this == BASKET) "Basket" else "Calcio"
}

class SportConverter {
    @TypeConverter
    fun fromSport(sport: Sport): String = sport.name

    @TypeConverter
    fun toSport(value: String): Sport = Sport.valueOf(value)
}

@Entity(tableName = "players")
data class Player(
    @PrimaryKey val id: String,
    val sport: Sport,
    val nome: String,
    val anno: Int,
    val carrieraMigliore: String,
    val stato: String,
    val nazione: String,
    val logoPath: String?,
    /** Ruolo in campo (es. "Forward", "Point Guard"). */
    val ruolo: String = "",
    /** Somma totale presenze in carriera (Wikipedia per il calcio, Proballers per il basket). */
    val presenze: Int = 0,
    /** Somma totale realizzazioni in carriera: gol per il calcio, punti per il basket. */
    val punteggio: Int = 0,
    /** Somma totale assist in carriera. */
    val assist: Int = 0,
    /** Competizione massima disputata (es. "Serie A", "NBA"). */
    val competizione: String = "",
    /** Gol subiti in carriera (solo portieri, calcio): da statmuse.com (colonna "GC"), con inserimento manuale come correzione/fallback. */
    val golSubiti: Int = 0,
    /** Presenze in Nazionale (calcio: da Wikipedia; basket: da Proballers se l'URL è stato fornito). */
    val presenzeNazionale: Int = 0,
    /** Realizzazioni in Nazionale: gol per il calcio, punti per il basket. */
    val punteggioNazionale: Int = 0,
    /** Assist in Nazionale (solo basket). */
    val assistNazionale: Int = 0,
    /** Rimbalzi totali in carriera (basket): Proballers/Wikipedia/Basketball-Reference. */
    val rimbalzi: Int = 0,
    /** Palle recuperate totali in carriera (basket): solo Basketball-Reference, nessun'altra fonte le traccia. */
    val palleRecuperate: Int = 0,
    /** Non più usato (era il numero di tiri da due realizzati): sostituito da [percentualeTiriDaDue]. */
    val tiriDaDue: Int = 0,
    /** Non più usato (era il numero di tiri da tre realizzati): sostituito da [percentualeTiriDaTre]. */
    val tiriDaTre: Int = 0,
    /** Rimbalzi in Nazionale (basket): da Proballers se l'URL è stato fornito. */
    val rimbalziNazionale: Int = 0,
    /** Palle recuperate in Nazionale (basket): inserimento manuale, nessuna fonte automatica. */
    val palleRecuperateNazionale: Int = 0,
    /** Tackle in carriera (calcio, difensori): da statmuse.com, dati affidabili solo dalle stagioni più recenti (~2014 in poi). */
    val tackle: Int = 0,
    /** Gol evitati in carriera (calcio, difensori): approssimato con le intercettazioni da statmuse.com, stessa limitazione di [tackle]. */
    val golEvitati: Int = 0,
    /** Gol subiti in Nazionale (solo portieri, calcio): inserimento manuale, nessuna fonte automatica trovata (statmuse copre solo il club). */
    val golSubitiNazionale: Int = 0,
    /** Percentuale al tiro da due (0-100, basket, ala grande/playmaker): dalla tabella "Totals" completa di Basketball-Reference (colonna "2P%"), quando disponibile. */
    val percentualeTiriDaDue: Int = 0,
    /** Percentuale al tiro da tre (0-100, basket, guardia/playmaker): dalla tabella "Totals" completa di Basketball-Reference (colonna "3P%"), quando disponibile. */
    val percentualeTiriDaTre: Int = 0,
    /** URL Proballers incollato dall'utente (basket): usato per il refresh in "Revisione". */
    val proballersUrl: String? = null,
    val updatedAt: Long,
    val needsReview: Boolean = false,
)

data class ClubCount(
    val club: String,
    val count: Int,
)

/** Stati suggeriti per il campo libero "stato" (usati anche per popolare il filtro). */
object PlayerStatus {
    const val ATTIVO = "Attivo"
    const val INFORTUNATO = "Infortunato"
    const val SVINCOLATO = "Svincolato"
    const val RITIRATO = "Ritirato"

    val suggested = listOf(ATTIVO, INFORTUNATO, SVINCOLATO, RITIRATO)
}
