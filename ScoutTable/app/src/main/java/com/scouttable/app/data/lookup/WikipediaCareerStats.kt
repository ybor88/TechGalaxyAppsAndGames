package com.scouttable.app.data.lookup

import java.net.URLEncoder
import kotlinx.coroutines.delay
import okhttp3.Request

data class CareerTotals(
    val presenze: Int,
    val punteggio: Int,
    val club: String?,
    val posizione: String? = null,
    val annoNascita: Int? = null,
    val presenzeNazionale: Int = 0,
    val punteggioNazionale: Int = 0,
    /** Nazionalità dedotta dal campo "nationalteamN" (es. "Italy"): utile quando TheSportsDB non
     * ha trovato il giocatore (nessun'altra fonte di nazionalità nel fallback solo-Wikipedia) o ha
     * un nome leggermente diverso dal titolo Wikipedia. */
    val nazione: String? = null,
)

/**
 * Somma presenze e gol di carriera (a livello di club) dall'infobox Wikipedia del giocatore
 * ("caps1"/"goals1", "caps2"/"goals2", ... per ogni squadra in cui ha giocato) e individua il
 * club con più presenze ("clubsN" abbinato al caps più alto). Niente assist: l'infobox standard
 * dei calciatori non li traccia. Verificato manualmente su Francesco Totti (caps1=619, goals1=250,
 * clubs1=[[AS Roma|Roma]]) prima di scrivere questo parser.
 *
 * Serve soprattutto per i giocatori ritirati: TheSportsDB non riporta più un club reale per loro
 * ("_Retired Soccer"), quindi il club/lega/stemma vengono recuperati da qui.
 */
object WikipediaCareerStats {

    // Esclude natcaps/natgoals (nazionale) e youthcaps ecc.: richiede che "caps"/"goals"/"clubs"
    // non sia preceduto da una lettera, cosi' non intercetta varianti diverse dalla carriera club.
    private val capsRegex = Regex("""(?<![A-Za-z])caps(\d+)\s*=\s*$INFOBOX_NUMBER""")
    private val goalsRegex = Regex("""(?<![A-Za-z])goals(\d+)\s*=\s*$INFOBOX_NUMBER""")
    // Presenze/gol in Nazionale: campi separati ("nationalcaps1"/"nationalgoals1", ...), stesso
    // formato di caps/goals ma per ogni convocazione in una selezione nazionale (club1 = giovanili,
    // es. "Italy U21", club2 = nazionale maggiore, es. "Italy": si sommano entrambe, la Nazionale
    // "vera" non si distingue in modo affidabile dalle giovanili nell'infobox).
    private val nationalCapsRegex = Regex("""(?<![A-Za-z])nationalcaps(\d+)\s*=\s*$INFOBOX_NUMBER""")
    private val nationalGoalsRegex = Regex("""(?<![A-Za-z])nationalgoals(\d+)\s*=\s*$INFOBOX_NUMBER""")
    private val nationalTeamRegex = Regex("""(?<![A-Za-z])nationalteam(\d+)\s*=\s*($INFOBOX_VALUE)""")
    // Filtra le nazionali giovanili (es. "Italy U21"): sommare tutto darebbe presenze gonfiate
    // rispetto a quelle in Nazionale maggiore, che è ciò che ci si aspetta di vedere.
    private val youthTeamIndicator = Regex("""U-?\d{2}\b|Under-?\d{2}\b|youth""", RegexOption.IGNORE_CASE)
    // Il valore si ferma al primo "|" fuori da wikilink/template/ref: vedi INFOBOX_VALUE in
    // WikiTextUtils, altrimenti su molti articoli (es. Christian Vieri, con clubs/caps/goals
    // sulla stessa riga) il nome del club include anche i campi successivi.
    private val clubsRegex = Regex("""(?<![A-Za-z])clubs(\d+)\s*=\s*($INFOBOX_VALUE)""")
    private val positionRegex = Regex("""(?<![A-Za-z])position\s*=\s*($INFOBOX_VALUE)""")
    private val birthDateRegex = Regex("""(?<![A-Za-z])birth_date\s*=\s*($INFOBOX_VALUE)""")
    private val yearTokenRegex = Regex("""(?:19|20)\d{2}""")
    private val redirectTargetRegex = Regex("""#REDIRECT\s*:?\s*\[\[([^\]|]+)""", RegexOption.IGNORE_CASE)

    // Le ricerche multiple ravvicinate possono far scattare rate-limit temporanei su Wikipedia:
    // un retry con una breve pausa assorbe questi blip senza appesantire troppo l'attesa utente.
    suspend fun fetchClubCareerTotals(playerName: String): CareerTotals? =
        fetchOnce(playerName) ?: run { delay(600); fetchOnce(playerName) }

    private suspend fun fetchOnce(playerName: String, redirectsLeft: Int = 2): CareerTotals? = runCatching {
        val title = URLEncoder.encode(playerName.trim().replace(' ', '_'), "UTF-8")
        val request = Request.Builder()
            .url("https://en.wikipedia.org/w/index.php?title=$title&action=raw")
            // Wikipedia chiede un User-Agent descrittivo per le richieste automatiche.
            .header("User-Agent", "ScoutTableApp/1.0 (Android scouting app; contact: n/a)")
            .build()

        lookupHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val fullText = response.body?.string() ?: return@use null
            // Molti giocatori sono indicizzati da TheSportsDB con un nome (es. senza accenti, o
            // un soprannome) che su Wikipedia è solo una pagina di redirect verso il vero titolo
            // dell'articolo (es. "Peja Stojakovic" -> "Peja Stojaković"): senza seguirlo si perdono
            // tutti i dati di carriera, non solo le statistiche.
            if (fullText.contains("#REDIRECT", ignoreCase = true) && fullText.length < 300) {
                if (redirectsLeft <= 0) return@use null
                val target = redirectTargetRegex.find(fullText)?.groupValues?.get(1)?.trim()
                return@use if (target.isNullOrBlank()) null else fetchOnce(target, redirectsLeft - 1)
            }

            // Limitarsi a infobox + paragrafo iniziale (prima della prima sezione "==...==")
            // evita di raccogliere numerazioni "capsN/clubsN" di altre carriere/sezioni più
            // avanti nell'articolo che riuserebbero gli stessi indici.
            val wikitext = stripHtmlComments(fullText.substringBefore("\n=="))

            val capsByIndex = capsRegex.findAll(wikitext)
                .associate { it.groupValues[1] to (it.groupValues[2].toIntOrNull() ?: 0) }
            val clubsByIndex = clubsRegex.findAll(wikitext)
                .associate { it.groupValues[1] to it.groupValues[2] }

            val presenze = capsByIndex.values.sum()
            val punteggio = goalsRegex.findAll(wikitext).sumOf { it.groupValues[2].toIntOrNull() ?: 0 }

            val bestClubIndex = capsByIndex.maxByOrNull { it.value }?.key
            val bestClub = bestClubIndex?.let { clubsByIndex[it] }?.let(::cleanWikiText)?.ifBlank { null }
            val posizione = firstNonBlank(positionRegex, wikitext)
            val annoNascita = birthDateRegex.find(wikitext)?.groupValues?.get(1)
                ?.let { yearTokenRegex.find(it)?.value?.toIntOrNull() }

            val nationalTeamsByIndex = nationalTeamRegex.findAll(wikitext)
                .associate { it.groupValues[1] to cleanWikiText(it.groupValues[2]) }
            val nationalCapsByIndex = nationalCapsRegex.findAll(wikitext)
                .associate { it.groupValues[1] to (it.groupValues[2].toIntOrNull() ?: 0) }
            val nationalGoalsByIndex = nationalGoalsRegex.findAll(wikitext)
                .associate { it.groupValues[1] to (it.groupValues[2].toIntOrNull() ?: 0) }
            val seniorIndices = nationalTeamsByIndex.filterValues { !youthTeamIndicator.containsMatchIn(it) }.keys
            // Se nessuna nazionale è etichettata come giovanile (o il campo "nationalteamN" manca
            // del tutto), meglio sommare tutto quello che c'è piuttosto che riportare zero.
            val indicesToSum = seniorIndices.ifEmpty { nationalCapsByIndex.keys }
            val presenzeNazionale = indicesToSum.sumOf { nationalCapsByIndex[it] ?: 0 }
            val punteggioNazionale = indicesToSum.sumOf { nationalGoalsByIndex[it] ?: 0 }
            // "nationalteamN" riporta il nome del paese (es. "Italy"), non un gentilizio: nessuna
            // conversione necessaria, a differenza di countryFromDemonym usato altrove per il basket.
            val nazione = seniorIndices.firstNotNullOfOrNull { nationalTeamsByIndex[it]?.ifBlank { null } }
                ?: nationalTeamsByIndex.values.firstOrNull()?.ifBlank { null }

            // Niente presenze/gol né club: non c'è nulla di utile da riportare (a differenza del
            // caso "solo posizione/anno trovati", che comunque vale la pena restituire quando il
            // club non è stato ricavato da nessun'altra fonte).
            if (presenze == 0 && punteggio == 0 && bestClub == null) return@use null

            CareerTotals(presenze, punteggio, bestClub, posizione, annoNascita, presenzeNazionale, punteggioNazionale, nazione)
        }
    }.getOrNull()
}
