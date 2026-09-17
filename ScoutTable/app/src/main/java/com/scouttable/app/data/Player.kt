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
    /**
     * Quando il giocatore è stato revisionato l'ultima volta (0 = mai): serve a nascondere un
     * giocatore già aperto in "Revisione" per 30 giorni da quel momento, indipendentemente dal
     * giro di flag mensile globale per sport (vedi [PlayerDao.flagActiveForReview]) — altrimenti
     * chi lo revisiona un giorno prima del prossimo giro mensile se lo ritrova segnalato di nuovo
     * il giorno dopo.
     */
    val lastReviewedAt: Long = 0,
    /** Club del "secondo logo" (periodo/stagione migliore): vedi [com.scouttable.app.data.lookup.BestSpellPicker]
     *  per il calcio, l'Eff più alto di una singola stagione Proballers per il basket. */
    val secondLogoClub: String = "",
    val secondLogoPath: String? = null,
    /** Periodo del secondo logo: intervallo di anni allo spell (calcio, es. "2002–2012") o
     *  etichetta stagione (basket, es. "07-08"). */
    val secondLogoPeriodo: String = "",
    /** Presenze calcio nello spell del secondo logo. */
    val secondLogoPresenze: Int = 0,
    /** Gol calcio nello spell del secondo logo: gol segnati per i ruoli di movimento, gol subiti
     *  per i portieri (il segno nel wikitext di it.wikipedia.org distingue i due casi, vedi
     *  [com.scouttable.app.data.lookup.WikipediaItCareerStats]). */
    val secondLogoGol: Int = 0,
    /** Giovanili (solo calcio): lista JSON di [YouthClub], da it.wikipedia.org. Null/vuoto se non trovate. */
    val giovanili: String? = null,
    /** Eff della stagione Proballers del secondo logo (basket). */
    val secondLogoEff: Int = 0,
    /** Media dell'Eff su tutte le stagioni Proballers (basket): solo valore, nessun logo associato. */
    val effMedio: Int = 0,
    /** Minuti totali di carriera (basket): somma di (MIN medio a partita × GP) per ogni stagione Proballers. */
    val minutiCarriera: Int = 0,
    /** Minuti totali in Nazionale (basket): stessa formula di [minutiCarriera] sulla sezione internazionale Proballers. */
    val minutiNazionale: Int = 0,
    /** College NCAA (basket, solo nome): da Wikipedia {{Infobox basketball biography}}, campo "college". */
    val college: String = "",
    /** Il giocatore è stato visionato dal vivo dallo scout (calcio e basket): flag manuale, nessuna fonte automatica. */
    val visionato: Boolean = false,
)

/** Una voce delle giovanili (calcio): club e periodo, senza statistiche. */
data class YouthClub(val club: String, val anni: String)

private const val YOUTH_CLUB_KEY = "club"
private const val YOUTH_ANNI_KEY = "anni"

/** Codifica la lista di giovanili in JSON per la colonna [Player.giovanili]; null se vuota. */
fun encodeYouthClubs(clubs: List<YouthClub>): String? {
    if (clubs.isEmpty()) return null
    val array = org.json.JSONArray()
    clubs.forEach { club ->
        array.put(
            org.json.JSONObject()
                .put(YOUTH_CLUB_KEY, club.club)
                .put(YOUTH_ANNI_KEY, club.anni),
        )
    }
    return array.toString()
}

/** Decodifica [Player.giovanili]; tollerante verso JSON assente/malformato (lista vuota). */
fun decodeYouthClubs(json: String?): List<YouthClub> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = org.json.JSONArray(json)
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            YouthClub(obj.optString(YOUTH_CLUB_KEY), obj.optString(YOUTH_ANNI_KEY))
        }
    }.getOrElse { emptyList() }
}

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
