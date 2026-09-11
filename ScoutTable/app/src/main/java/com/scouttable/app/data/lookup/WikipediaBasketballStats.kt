package com.scouttable.app.data.lookup

import java.net.URLEncoder
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import okhttp3.Request

data class BasketballCareerTotals(
    val presenze: Int,
    val punteggio: Int,
    val assist: Int,
    val squadra: String?,
    val competizione: String?,
    val posizione: String? = null,
    val annoNascita: Int? = null,
    val nazione: String? = null,
    /** Rimbalzi totali di carriera, da un campo "statNlabel" con etichetta "Rebounds": non sempre
     * presente (meno tracciato di punti/assist nell'infobox), resta 0 quando assente. */
    val rimbalzi: Int = 0,
)

/**
 * Recupera i totali di carriera di un cestista dall'infobox Wikipedia
 * ({{Infobox basketball biography}}): "stat1label"/"stat1value" ecc. (etichette libere, es.
 * "Points"/"32,292 (30.1 ppg)") e "team1" (prima squadra elencata) + "stats_league".
 * Le presenze non sono un campo diretto: si stimano dividendo il totale punti per la media a
 * partita quando disponibile (es. 32,292 / 30.1 ≈ 1073, verificato su Michael Jordan: reale 1072).
 * A differenza del calcio, funziona automaticamente senza bisogno di incollare un URL Proballers
 * (quello resta disponibile come opzione più precisa, se fornito ha la precedenza).
 */
object WikipediaBasketballStats {

    // Il valore si ferma al primo "|" fuori da wikilink/template/ref: vedi INFOBOX_VALUE in
    // WikiTextUtils, cosi' non si "mangiano" campi successivi impacchettati sulla stessa riga.
    private val statLabelRegex = Regex("""(?<![A-Za-z])stat(\d+)label\s*=\s*($INFOBOX_VALUE)""")
    private val statValueRegex = Regex("""(?<![A-Za-z])stat(\d+)value\s*=\s*($INFOBOX_VALUE)""")
    private val teamNRegex = Regex("""(?<![A-Za-z])team(\d+)\s*=\s*($INFOBOX_VALUE)""")
    private val yearsNRegex = Regex("""(?<![A-Za-z])years(\d+)\s*=\s*($INFOBOX_VALUE)""")
    private val leagueRegex = Regex("""stats_league\s*=\s*($INFOBOX_VALUE)""")
    // "(?<![A-Za-z])position" intercetta anche "career_position" (preceduto da "_", non una
    // lettera): utile perché molti articoli (Carlton Myers, Dino Meneghin, Gregor Fučka) lasciano
    // "position" VUOTO e mettono il ruolo vero solo in "career_position" più avanti — .find()
    // prendeva sempre il primo campo (vuoto) e si fermava lì, perdendo il ruolo. findAll + primo
    // risultato non vuoto risolve senza dover distinguere esplicitamente i due nomi di campo.
    private val positionRegex = Regex("""(?<![A-Za-z])position\s*=\s*($INFOBOX_VALUE)""")
    private val birthDateRegex = Regex("""(?<![A-Za-z])birth_date\s*=\s*($INFOBOX_VALUE)""")
    private val nationalityRegex = Regex("""(?<![A-Za-z])nationality\s*=\s*($INFOBOX_VALUE)""")
    private val yearTokenRegex = Regex("""(?:19|20)\d{2}""")
    private val redirectTargetRegex = Regex("""#REDIRECT\s*:?\s*\[\[([^\]|]+)""", RegexOption.IGNORE_CASE)
    // Es. "32,292 (30.1 ppg)": [^)]* dopo il numero decimale consuma "ppg"/"rpg"/"apg" prima
    // della parentesi di chiusura (senza, il gruppo opzionale non trovava mai ")" e le presenze
    // stimate restavano sempre a 0, nascondendo l'intera riga statistiche in UI).
    private val valuePattern = Regex("""([0-9,]+)\s*(?:\(([0-9.]+)[^)]*\))?""")

    suspend fun fetch(playerName: String): BasketballCareerTotals? =
        fetchOnce(playerName) ?: run { delay(600); fetchOnce(playerName) }

    private suspend fun fetchOnce(playerName: String, redirectsLeft: Int = 2): BasketballCareerTotals? = runCatching {
        val title = URLEncoder.encode(playerName.trim().replace(' ', '_'), "UTF-8")
        val request = Request.Builder()
            .url("https://en.wikipedia.org/w/index.php?title=$title&action=raw")
            .header("User-Agent", "ScoutTableApp/1.0 (Android scouting app; contact: n/a)")
            .build()

        lookupHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val fullText = response.body?.string() ?: return@use null
            // TheSportsDB restituisce spesso il nome senza accenti/diacritici o un soprannome
            // (es. "Anfernee Hardaway", "Peja Stojakovic") che su Wikipedia è solo una pagina di
            // redirect verso il vero titolo dell'articolo: senza seguirlo si perde anche squadra
            // e lega, non solo le statistiche, e la UI mostra la foto del giocatore al posto del
            // logo del club (unico fallback quando lo stemma non si riesce a determinare).
            if (fullText.contains("#REDIRECT", ignoreCase = true) && fullText.length < 300) {
                if (redirectsLeft <= 0) return@use null
                val target = redirectTargetRegex.find(fullText)?.groupValues?.get(1)?.trim()
                return@use if (target.isNullOrBlank()) null else fetchOnce(target, redirectsLeft - 1)
            }

            // Alcuni giocatori (es. Michael Jordan col baseball) hanno una seconda carriera con
            // un proprio blocco statNlabel/statNvalue più avanti nell'articolo, che riusa gli
            // stessi indici numerici: limitandoci a infobox + paragrafo iniziale (prima della
            // prima sezione "==...=="), evitiamo di mescolare i due blocchi.
            val wikitext = stripHtmlComments(fullText.substringBefore("\n=="))

            val labels = statLabelRegex.findAll(wikitext).associate { it.groupValues[1] to it.groupValues[2] }
            val values = statValueRegex.findAll(wikitext).associate { it.groupValues[1] to it.groupValues[2] }

            var punteggio = 0
            var assist = 0
            var rimbalzi = 0
            var estimatedGames = 0

            for ((index, rawLabel) in labels) {
                val rawValue = values[index] ?: continue
                val match = valuePattern.find(rawValue) ?: continue
                val total = match.groupValues[1].replace(",", "").toIntOrNull() ?: continue
                val perGame = match.groupValues[2].toDoubleOrNull()
                val label = cleanWikiText(rawLabel)

                when {
                    label.contains("point", ignoreCase = true) -> {
                        punteggio = total
                        if (perGame != null && perGame > 0) estimatedGames = (total / perGame).roundToInt()
                    }
                    label.contains("assist", ignoreCase = true) -> assist = total
                    label.contains("rebound", ignoreCase = true) -> rimbalzi = total
                }
            }

            val squadra = bestTeamByTenure(wikitext)
            val competizione = leagueRegex.find(wikitext)?.groupValues?.get(1)?.let(::cleanWikiText)?.ifBlank { null }
            val posizione = firstNonBlank(positionRegex, wikitext)
            val annoNascita = birthDateRegex.find(wikitext)?.groupValues?.get(1)
                ?.let { yearTokenRegex.find(it)?.value?.toIntOrNull() }
            val nazione = firstNonBlank(nationalityRegex, wikitext)?.let(::countryFromDemonym)

            // Niente punti/assist né squadra: non c'è nulla di utile da riportare (giocatori con
            // un infobox che non traccia proprio le statistiche di carriera, es. Dino Meneghin,
            // vengono comunque riportati se almeno la squadra è stata trovata).
            if (punteggio == 0 && assist == 0 && squadra == null) return@use null

            BasketballCareerTotals(estimatedGames, punteggio, assist, squadra, competizione, posizione, annoNascita, nazione, rimbalzi)
        }
    }.getOrNull()

    /**
     * "Miglior club" per il basket = quello con più anni complessivi di militanza, non il primo
     * "team1" dell'infobox: su Wikipedia team1/team2/... sono elencati in ordine cronologico (il
     * primo è la squadra d'esordio, non necessariamente la più rappresentativa), e un editor può
     * aggiungere un nuovo "teamN" finale non appena un giocatore firma per una nuova squadra anche
     * dopo un solo giorno/una sola presenza (es. LeBron James: "team1 = Cleveland Cavaliers" è la
     * sua prima squadra 2003, non la più significativa). Somma gli anni per squadra (un giocatore
     * può tornare più volte nella stessa squadra, con team/yearsN diversi) e sceglie il totale più alto.
     */
    private fun bestTeamByTenure(wikitext: String): String? {
        val teamsByIndex = teamNRegex.findAll(wikitext).associate { it.groupValues[1] to it.groupValues[2] }
        val yearsByIndex = yearsNRegex.findAll(wikitext).associate { it.groupValues[1] to it.groupValues[2] }
        if (teamsByIndex.isEmpty()) return null

        val tenureByTeam = mutableMapOf<String, Int>()
        for ((index, rawTeam) in teamsByIndex) {
            val teamName = cleanWikiText(rawTeam)
            if (teamName.isBlank()) continue
            val years = yearsByIndex[index]?.let(::yearsDuration) ?: 1
            tenureByTeam[teamName] = (tenureByTeam[teamName] ?: 0) + years
        }
        return tenureByTeam.maxByOrNull { it.value }?.key
            ?: teamsByIndex["1"]?.let(::cleanWikiText)?.ifBlank { null }
    }

    private fun yearsDuration(rawYears: String): Int {
        val tokens = yearTokenRegex.findAll(rawYears).map { it.value.toInt() }.toList()
        val hasPresent = rawYears.contains("present", ignoreCase = true)
        if (tokens.isEmpty() && !hasPresent) return 1
        val currentYear = java.time.Year.now().value
        val years = if (hasPresent) tokens + currentYear else tokens
        val start = years.minOrNull() ?: return 1
        val end = years.maxOrNull() ?: start
        return (end - start).coerceAtLeast(1)
    }
}
