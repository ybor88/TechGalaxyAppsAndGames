// PayRecap — © Roberto Di Flumeri
package com.payrecap.app.dominio

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Un cedolino riepilogato. Tutti i valori estratti dal documento sono facoltativi:
 * se il cedolino non li riporta (o il riconoscimento non li trova) restano null
 * e l'interfaccia mostra "—" invece di inventare un numero.
 */
@Entity(tableName = "cedolini")
data class Cedolino(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mese: Int,                       // 1..12
    val anno: Int,
    /** Tredicesima, quattordicesima o altra mensilità aggiuntiva. */
    val aggiuntiva: Boolean = false,
    val azienda: String? = null,
    val nomeFile: String? = null,
    val caricatoIl: Long = System.currentTimeMillis(),

    // Importi principali
    val lordo: Double? = null,
    val netto: Double? = null,
    val imponibileFiscale: Double? = null,

    // Tasse e contributi (come riportati nel documento)
    val irpef: Double? = null,
    val addizionaleRegionale: Double? = null,
    val addizionaleComunale: Double? = null,
    val contributiPrevidenziali: Double? = null,

    // Contratto
    val tipoContratto: String? = null,
    val ccnl: String? = null,
    val livello: String? = null,
    /** Scadenza del contratto a termine, formato gg/mm/aaaa. */
    val scadenza: String? = null,

    // Ferie (unità: "giorni" oppure "ore")
    val ferieMaturate: Double? = null,
    val ferieGodute: Double? = null,
    val ferieResidue: Double? = null,
    val unitaFerie: String = UNITA_GIORNI,

    // ROL / permessi
    val rolMaturati: Double? = null,
    val rolGoduti: Double? = null,
    val rolResidui: Double? = null,
    val unitaRol: String = UNITA_ORE,

    // Straordinario (ore): non è un "residuo" come ferie e ROL
    val straordinarioEffettuate: Double? = null,
    val straordinarioPagate: Double? = null,
    val straordinarioAccantonate: Double? = null,
    val bancaOreResidua: Double? = null,

    /** Testo grezzo letto dal documento, utile per controllare il riconoscimento. */
    val testoEstratto: String? = null,
) {
    /** Solo le imposte: IRPEF + addizionali. */
    val totaleTasse: Double?
        get() = sommaNonNulli(irpef, addizionaleRegionale, addizionaleComunale)

    /** Tasse più contributi previdenziali a carico del dipendente. */
    val totaleTasseEContributi: Double?
        get() = sommaNonNulli(irpef, addizionaleRegionale, addizionaleComunale, contributiPrevidenziali)

    /** Chiave di ordinamento cronologico (le mensilità aggiuntive vengono dopo il mese). */
    val chiavePeriodo: Int get() = (anno * 100 + mese) * 2 + if (aggiuntiva) 1 else 0

    val etichettaPeriodo: String
        get() = (if (aggiuntiva) "Mensilità aggiuntiva · " else "") + "${nomeMese(mese)} $anno"

    companion object {
        const val UNITA_GIORNI = "giorni"
        const val UNITA_ORE = "ore"
    }
}

fun sommaNonNulli(vararg valori: Double?): Double? {
    val presenti = valori.filterNotNull()
    return if (presenti.isEmpty()) null else presenti.sum()
}

private val MESI = listOf(
    "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
    "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre",
)

fun nomeMese(mese: Int): String = MESI.getOrElse(mese - 1) { "Mese $mese" }

fun nomeMeseBreve(mese: Int): String = nomeMese(mese).take(3)

/** Indice 1..12 del mese a partire dal nome italiano (anche abbreviato), null se non riconosciuto. */
fun meseDaNome(testo: String): Int? {
    val t = testo.lowercase().trim().trimEnd('.')
    if (t.length < 3) return null
    val idx = MESI.indexOfFirst { it.lowercase().startsWith(t) }
    return if (idx >= 0) idx + 1 else null
}
