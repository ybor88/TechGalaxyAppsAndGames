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
