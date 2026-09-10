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
