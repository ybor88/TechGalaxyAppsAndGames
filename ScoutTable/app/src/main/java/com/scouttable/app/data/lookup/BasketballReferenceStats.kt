package com.scouttable.app.data.lookup

import java.net.URLEncoder
import kotlin.math.roundToInt
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class BasketballReferenceTotals(
    val presenze: Int,
    val punteggio: Int,
    val assist: Int,
    val nazione: String?,
    val annoNascita: Int?,
    val posizione: String?,
    /** Rimbalzi/palle recuperate: presenti nella tabella "Totals" completa (giocatori ritirati o
     * con carriera abbastanza lunga), spesso assenti nel box "SUMMARY" dei giocatori ancora in
     * attività (che mostra solo 3 statistiche "in evidenza" scelte in base al ruolo, non sempre
     * queste). Restano a 0 quando non disponibili. */
    val rimbalzi: Int = 0,
    val palleRecuperate: Int = 0,
    /** Percentuali al tiro (0-100): dalla tabella "Totals" completa ("2P%"/"3P%") quando
     * disponibile; il box "SUMMARY" ha solo "3-Point Field Goal Percentage" (niente equivalente
     * per i tiri da due), quindi percentualeTiriDaDue resta a 0 per i giocatori che passano da lì. */
    val percentualeTiriDaDue: Int = 0,
    val percentualeTiriDaTre: Int = 0,
)

/**
 * Fallback per il basket quando Wikipedia non riporta i totali di carriera: comune sia per
 * giocatori europei più datati (es. Dino Meneghin, Carlton Myers), sia — causa diversa ma stesso
 * sintomo — per i giocatori NBA ancora IN ATTIVITÀ, il cui infobox Wikipedia semplicemente non ha
 * i campi "stat1label"/"stat1value" (quel blocco viene aggiunto dagli editor tipicamente solo al
 * ritiro): senza questo fallback, un giocatore attivo (es. LeBron James) risultava con squadra e
 * ruolo corretti ma media punti sempre a zero. A differenza di fbref.com, basketball-reference.com
 * non blocca le richieste automatiche (verificato, sia sulle pagine "/international/" sia su
 * quelle NBA principali "/players/") e ha una ricerca per nome funzionante.
 *
 * ATTENZIONE (verificato manualmente): la sezione "international" copre bene le competizioni
 * continentali (EuroLeague e affini, dal 1996 in poi) ma per molti giocatori più vecchi o con
 * carriera solo nei campionati nazionali riporta SOLO le presenze in Nazionale/Olimpiadi — es. per
 * Dino Meneghin la pagina non ha affatto la sua carriera di club (Varese/Cantù) ma solo le sue 4
 * Olimpiadi (31 partite in tutto, contro le centinaia reali). Usare questo numero come "carriera
 * totale" sarebbe platealmente sbagliato. Per questo il risultato viene scartato (azzerato) a meno
 * che il numero di stagioni/partite non sembri plausibilmente una carriera vera e propria.
 */
object BasketballReferenceStats {

    private const val MIN_SEASONS = 5
    private const val MIN_GAMES = 50
    private const val SEARCH_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    suspend fun fetchCareerTotals(playerName: String): BasketballReferenceTotals? = runCatching {
        val encoded = URLEncoder.encode(playerName, "UTF-8")
        val searchHtml = getHtml("https://www.basketball-reference.com/search/search.fcgi?search=$encoded")
            ?: return@runCatching null

        val profileHtml = if (searchHtml.contains("id=\"necro-birth\"")) {
            searchHtml
        } else {
            val href = findBestProfileLink(searchHtml, playerName) ?: return@runCatching null
            getHtml("https://www.basketball-reference.com$href") ?: return@runCatching null
        }

        parseProfile(profileHtml)
    }.getOrNull()

    private fun findBestProfileLink(searchHtml: String, playerName: String): String? {
        val doc = Jsoup.parse(searchHtml)
        // "/players/" (senza "/international/") copre le pagine NBA principali, necessarie per i
        // giocatori attivi: vedi il fallback nel box "SUMMARY" in parseProfile.
        val links = doc.select("a[href^=/international/players/], a[href^=/players/]")
        if (links.isEmpty()) return null
        val wanted = playerName.trim().lowercase()
        val bestMatch = links.firstOrNull { it.text().trim().lowercase() == wanted }
            ?: links.firstOrNull { it.text().trim().lowercase().contains(wanted) }
            ?: links.first()
        return bestMatch.attr("href")
    }

    private data class CareerBoxScore(
        val games: Int,
        val points: Int,
        val assists: Int,
        val rebounds: Int = 0,
        val steals: Int = 0,
        val pctTiriDaDue: Int = 0,
        val pctTiriDaTre: Int = 0,
    )

    private fun parseProfile(profileHtml: String): BasketballReferenceTotals? {
        val doc = Jsoup.parse(profileHtml)
        val box = parseCareerTotalsFromTable(doc)
            ?: parseCareerTotalsFromSummaryBox(doc)
            ?: CareerBoxScore(0, 0, 0)

        val annoNascita = doc.selectFirst("#necro-birth")?.attr("data-birth")?.take(4)?.toIntOrNull()

        // Estrazione strutturale (via i tag <strong> "Born:"/"Position:"), non da testo appiattito:
        // Element.text() di Jsoup normalizza tutti gli spazi a uno solo, quindi un separatore basato
        // su spazi multipli tra un campo e il successivo non funzionerebbe.
        val bornParagraph = doc.select("strong").firstOrNull { it.text().trim() == "Born:" }?.parent()
        // Il paragrafo "Born:" ha anche uno span "(Date unknown)" quando la data non è nota (es.
        // Carlton Myers): richiedere il prefisso "in " (formato "in Città, Paese", o "in Paese"
        // quando la città non è nota, es. "in United Kingdom") evita di prendere quel testo per
        // una nazionalità. Il paese è dopo l'ultima virgola SE c'è una città, altrimenti tutto
        // il resto dopo "in ".
        val nazione = bornParagraph?.select("span")
            ?.firstOrNull { it.id() != "necro-birth" && it.text().trim().startsWith("in ", ignoreCase = true) }
            ?.text()?.trim()?.replaceFirst(Regex("""^in\s+""", RegexOption.IGNORE_CASE), "")
            ?.substringAfterLast(',')?.trim()?.ifBlank { null }

        // Sulle pagine NBA principali (non "/international/") il paragrafo continua con "▪ Shoots:
        // ..." e spesso elenca più ruoli separati da virgola (es. "Small Forward, Power Forward,
        // Point Guard, Center, and Shooting Guard" per un giocatore versatile): si tiene solo la
        // parte prima di "▪" e il primo ruolo elencato, così translateRole() ha la possibilità di
        // riconoscerlo invece di ricevere l'intera lista come stringa unica e non tradotta.
        val posizione = doc.select("strong").firstOrNull { it.text().trim() == "Position:" }
            ?.parent()?.text()?.removePrefix("Position:")?.substringBefore('▪')
            ?.substringBefore(',')?.trim()?.ifBlank { null }

        return BasketballReferenceTotals(
            box.games, box.points, box.assists, nazione, annoNascita, posizione,
            box.rebounds, box.steals, box.pctTiriDaDue, box.pctTiriDaTre,
        )
    }

    /**
     * Tabella "Totals" con lo storico stagione-per-stagione, che riporta anche una riga di
     * carriera già aggregata: sulle pagine "/international/" (verificato su Dino Meneghin) ha id
     * "player-stats-totals-..." e la riga di carriera si etichetta "N Seasons"; sulle pagine NBA
     * standard "/players/" (verificato su Kobe Bryant) ha id "totals_stats" e la riga si etichetta
     * "N Yrs" — due template diversi con anche nomi di colonna diversi (partite: "g" vs "games";
     * stagione: "season" vs "year_id"), qui gestiti entrambi. Colonne rimbalzi/palle recuperate/
     * tiri da due e tre ("trb"/"stl"/"fg2"/"fg3") sono identiche in entrambi i template e sempre
     * presenti in questa tabella completa, a differenza del box "SUMMARY" sotto.
     *
     * Su un giocatore passato per più squadre (es. LeBron James) questa tabella riporta solo
     * sottototali PER SQUADRA (es. "CLE (11 Yrs)"), non una riga di carriera complessiva: in quel
     * caso nessuna riga corrisponde al pattern cercato e si passa correttamente al box "SUMMARY".
     */
    private fun parseCareerTotalsFromTable(doc: Document): CareerBoxScore? {
        val totalsTable = doc.select("table[id^=player-stats-totals-], table#totals_stats").firstOrNull() ?: return null
        val careerRow = totalsTable.select("tbody tr, tfoot tr").firstOrNull { row ->
            isCareerAggregateRow(row)
        } ?: return null

        val seasonText = careerRow.cellText("season").ifBlank { careerRow.cellText("year_id") }
        val seasons = Regex("""\d+""").find(seasonText)?.value?.toIntOrNull() ?: 0
        val games = careerRow.cellNumber("g").takeIf { it > 0 } ?: careerRow.cellNumber("games")

        // Sotto soglia: la pagina copre solo un frammento della carriera (es. sole Olimpiadi per
        // Dino Meneghin), non un totale attendibile — meglio lasciare che il chiamante provi il box
        // "SUMMARY" (o rinunci del tutto) piuttosto che riportare un numero platealmente incompleto.
        if (seasons < MIN_SEASONS || games < MIN_GAMES) return null
        // "fg2_pct"/"fg3_pct" sono frazioni 0-1 (es. ".479"), a differenza del box "SUMMARY" sotto
        // che le riporta già in scala percentuale: da qui il *100 solo in questo parser.
        fun pct(dataStat: String) = (careerRow.cellText(dataStat).toDoubleOrNull()?.times(100))?.roundToInt() ?: 0
        return CareerBoxScore(
            games = games,
            points = careerRow.cellNumber("pts"),
            assists = careerRow.cellNumber("ast"),
            rebounds = careerRow.cellNumber("trb"),
            steals = careerRow.cellNumber("stl"),
            pctTiriDaDue = pct("fg2_pct"),
            pctTiriDaTre = pct("fg3_pct"),
        )
    }

    /** "4 Seasons" (pagine /international/) o "20 Yrs" (pagine NBA standard): un numero seguito da
     * "Season(s)"/"Yr(s)". Esclude sia le singole stagioni (es. "1996-97") sia i sottototali PER
     * SQUADRA di un giocatore passato per più squadre (es. "CLE (11 Yrs)": non inizia con un
     * numero) sia righe di media come "82 Game Avg". */
    private fun isCareerAggregateRow(row: Element): Boolean {
        val text = row.selectFirst("[data-stat=season], [data-stat=year_id]")?.text()?.trim() ?: return false
        return Regex("""^\d+\s*(Seasons?|Yrs?)$""", RegexOption.IGNORE_CASE).matches(text)
    }

    /**
     * Box "SUMMARY" in cima alle pagine NBA principali (es. "/players/j/jamesle01.html"): l'unica
     * fonte di carriera disponibile per un giocatore ancora IN ATTIVITÀ, dato che lì non esiste una
     * riga "Career" pre-aggregata nella tabella stagione-per-stagione (quella si limiterebbe a
     * sommare le righe con rischio di doppio conteggio nelle stagioni con cambio squadra a metà
     * anno). Riporta solo le MEDIE a partita di carriera (colonna "Career"): i totali di punti/
     * assist sono quindi una stima (media × partite, arrotondata) — stesso livello di approssimazione
     * già accettato altrove nell'app per le "presenze" stimate dal ppg di Wikipedia.
     */
    private fun parseCareerTotalsFromSummaryBox(doc: Document): CareerBoxScore? {
        val statBoxes = doc.select("div.stats_pullout div.p1 > div")
        if (statBoxes.isEmpty()) return null

        fun careerValue(label: String): Double? = statBoxes
            .firstOrNull { it.selectFirst("span")?.attr("data-tip") == label }
            ?.select("p")?.getOrNull(1)?.text()?.trim()?.replace(",", "")?.toDoubleOrNull()

        val games = careerValue("Games")?.toInt() ?: return null
        if (games < MIN_GAMES) return null
        // Il box mostra solo 3 statistiche "in evidenza" scelte in base al ruolo (es. PTS/AST/STL
        // per un playmaker, PTS/TRB/BLK per un centro): quelle non presenti restano a 0, non è
        // possibile distinguere "zero reale" da "non mostrato qui" con questa fonte.
        fun total(label: String) = careerValue(label)?.let { (games * it).roundToInt() } ?: 0
        return CareerBoxScore(
            games = games,
            points = total("Points"),
            assists = total("Assists"),
            // L'etichetta reale in data-tip è "Total Rebounds", non "Rebounds" (verificato su LeBron
            // James: senza questa correzione careerValue non trovava mai la colonna e i rimbalzi
            // restavano sempre a 0 per ogni giocatore ancora in attività, cioè quelli che passano
            // proprio da questo box invece che dalla tabella "Totals" completa).
            rebounds = total("Total Rebounds"),
            steals = total("Steals"),
            // A differenza della tabella "Totals", qui il valore è già in scala percentuale
            // (es. "34.8", non ".348"): nessun *100. Niente equivalente per i tiri da due in
            // questo box (solo FG% complessivo, che mischia due e tre punti).
            pctTiriDaTre = careerValue("3-Point Field Goal Percentage")?.roundToInt() ?: 0,
        )
    }

    private fun Element.cellText(dataStat: String): String = selectFirst("[data-stat=$dataStat]")?.text().orEmpty()

    private fun Element.cellNumber(dataStat: String): Int = cellText(dataStat).replace(",", "").toIntOrNull() ?: 0

    private fun getHtml(url: String): String? = runCatching {
        val request = Request.Builder().url(url).header("User-Agent", SEARCH_UA).build()
        lookupHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.string()
        }
    }.getOrNull()
}
