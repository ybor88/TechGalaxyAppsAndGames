// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.competitions

import android.content.Context
import com.scouttable.app.data.Sport
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Un premio individuale della vetrina "Premi" in "Detentori" (MVP, Pallone d'oro...): l'albo d'oro
 * si legge dalla voce [page] della Wikipedia in lingua [lang]. [tableIndex]: quale tabella dei
 * vincitori della voce (es. FIFA World Player: 0 maschile, 1 femminile, nella stessa voce).
 */
data class AwardDef(
    val sport: Sport,
    val id: String,
    val label: String,
    val lang: String,
    val page: String,
    val women: Boolean,
    val tableIndex: Int = 0,
)

/** Un'edizione del premio: [season] come la scrive Wikipedia ("2025" o "2025-2026"). */
data class AwardWinner(val season: String, val player: String, val team: String?, val nationality: String?) {
    /** Anno per l'ordinamento: per le stagioni a cavallo ("2025-2026") quello finale. */
    val sortYear: Int get() {
        val m = seasonRegex.find(season) ?: return 0
        val start = m.groupValues[1].toInt()
        val end = m.groupValues[2]
        return when {
            end.isEmpty() -> start
            end.length == 2 -> start / 100 * 100 + end.toInt()
            else -> end.toInt()
        }
    }
}

private val seasonRegex = Regex("""(\d{4})(?:\s*[-–/]\s*(\d{4}|\d{2}))?""")

/** Albo d'oro di un premio (dal più recente) e quando è stato letto. */
data class AwardData(val updatedAt: Long, val winners: List<AwardWinner>) {
    val latest: AwardWinner? get() = winners.firstOrNull()

    /** Giocatori con più premi (a pari merito tutti) e quanti. */
    val mostWins: Pair<List<String>, Int>? get() {
        val counts = winners.groupingBy { it.player }.eachCount()
        val max = counts.values.maxOrNull() ?: return null
        return counts.filterValues { it == max }.keys.toList() to max
    }
}

object AwardCatalog {
    private val all = listOf(
        AwardDef(Sport.BASKET, "mvp-nba", "MVP NBA", "it", "NBA Most Valuable Player Award", women = false),
        AwardDef(Sport.BASKET, "mvp-fiba-world-cup", "MVP Mondiali FIBA", "it", "FIBA World Cup MVP", women = false),
        AwardDef(Sport.BASKET, "mvp-wnba", "MVP WNBA", "en", "WNBA Most Valuable Player", women = true),
        AwardDef(Sport.BASKET, "mvp-fiba-world-cup-women", "MVP Mondiali FIBA femminili", "it", "FIBA World Cup MVP (femminile)", women = true),
        AwardDef(Sport.CALCIO, "pallone-oro", "Pallone d'oro", "it", "Pallone d'oro", women = false),
        AwardDef(Sport.CALCIO, "fifa-world-player", "FIFA World Player of the Year", "it", "FIFA World Player of the Year", women = false),
        AwardDef(Sport.CALCIO, "pallone-oro-women", "Pallone d'oro femminile", "it", "Pallone d'oro femminile", women = true),
        AwardDef(
            Sport.CALCIO, "fifa-world-player-women", "FIFA World Player of the Year femminile", "it",
            "FIFA World Player of the Year", women = true, tableIndex = 1,
        ),
    )

    fun forSport(sport: Sport): List<AwardDef> = all.filter { it.sport == sport }
}

/**
 * Legge l'albo d'oro dalla tabella dei vincitori di una voce Wikipedia. Le voci hanno tre forme
 * (verificate su tutte quelle di [AwardCatalog]):
 * - una riga per edizione: anno | ... | giocatore | squadra (MVP NBA, MVP FIBA, MVP WNBA);
 * - podio con l'anno su più righe (rowspan) e la colonna "Posizione": vale solo il 1º
 *   (Pallone d'oro femminile, FIFA World Player);
 * - podio con l'anno in una riga di titolo a tutta larghezza prima delle posizioni (Pallone d'oro).
 * Le tabelle dei "più premiati" (senza anno né posizione) sono scartate.
 */
internal object AwardTableParser {

    private val yearHeader = Regex("""^(anno|stagione|edizione|season|year)""", RegexOption.IGNORE_CASE)
    private val playerHeader = Regex("""giocat|calciat|nome|player|vincitore""", RegexOption.IGNORE_CASE)
    private val teamHeader = Regex("""squadra|club|team""", RegexOption.IGNORE_CASE)
    private val positionHeader = Regex("""^posizione""", RegexOption.IGNORE_CASE)
    private val nationalityHeader = Regex("""nazional""", RegexOption.IGNORE_CASE)
    private val notAwarded = Regex("""non assegnat|not awarded|cancel""", RegexOption.IGNORE_CASE)
    private val spaces = Regex("""\s+""")

    private class Row(val own: List<Element>, val cells: List<Element>)

    fun parse(html: String, tableIndex: Int): List<AwardWinner> {
        val candidates = Jsoup.parse(html).select("table.wikitable").mapNotNull { table ->
            val rows = grid(table)
            val header = rows.firstOrNull()?.cells?.map(::text) ?: return@mapNotNull null
            val playerCol = header.indexOfFirst { playerHeader.containsMatchIn(it) }
            val yearCol = header.indexOfFirst { yearHeader.containsMatchIn(it) }
            val positionCol = header.indexOfFirst { positionHeader.containsMatchIn(it) }
            if (playerCol < 0 || (yearCol < 0 && positionCol < 0)) null
            else Triple(rows, header, Triple(playerCol, yearCol, positionCol))
        }
        val (rows, header, cols) = candidates.getOrNull(tableIndex) ?: return emptyList()
        val (playerCol, yearCol, positionCol) = cols
        val teamCol = header.indexOfFirst { teamHeader.containsMatchIn(it) }
        val nationalityCol = header.indexOfFirst { nationalityHeader.containsMatchIn(it) }

        val out = mutableListOf<AwardWinner>()
        var currentYear: String? = null
        rows.drop(1).forEach { row ->
            // Riga di titolo (una sola cella piena, d'intestazione): l'anno delle righe che seguono.
            val filled = row.own.filter { text(it).isNotEmpty() }
            if (filled.size == 1 && filled[0].tagName() == "th") {
                text(filled[0]).trim().takeIf { Regex("""\d{4}""").matches(it) }?.let { currentYear = it }
                return@forEach
            }
            val season = if (yearCol >= 0) row.cells.getOrNull(yearCol)?.let { seasonRegex.find(text(it))?.value?.replace(" ", "") }
            else currentYear
            if (season == null) return@forEach
            if (positionCol >= 0 && row.cells.getOrNull(positionCol)?.let { text(it).trimStart().startsWith("1") } != true) return@forEach
            val player = row.cells.getOrNull(playerCol)?.let(::linkName)?.takeIf { it.isNotBlank() && !notAwarded.containsMatchIn(it) }
                ?: return@forEach
            out += AwardWinner(
                season = season,
                player = player,
                team = row.cells.getOrNull(teamCol)?.let(::linkName)?.ifBlank { null },
                nationality = row.cells.getOrNull(nationalityCol)?.let(::text)?.ifBlank { null },
            )
        }
        return out
    }

    /** Righe con le celle già distribuite sulle colonne (rowspan e colspan espansi). */
    private fun grid(table: Element): List<Row> {
        // Colonna -> cella che vi continua dalle righe sopra (rowspan) e per quante righe ancora.
        val pending = mutableMapOf<Int, Pair<Element, Int>>()
        return table.select("tr").map { tr ->
            val own = tr.children().toList().filter { it.tagName() == "th" || it.tagName() == "td" }
            val cells = mutableListOf<Element>()
            var col = 0
            fun takePending(): Boolean {
                val (cell, left) = pending[col] ?: return false
                cells += cell
                if (left <= 1) pending.remove(col) else pending[col] = cell to left - 1
                col++
                return true
            }
            own.forEach { cell ->
                while (takePending()) Unit
                val colspan = cell.attr("colspan").filter(Char::isDigit).toIntOrNull()?.coerceAtLeast(1) ?: 1
                val rowspan = cell.attr("rowspan").filter(Char::isDigit).toIntOrNull() ?: 1
                repeat(colspan) {
                    cells += cell
                    if (rowspan > 1) pending[col] = cell to rowspan - 1
                    col++
                }
            }
            while (takePending()) Unit
            Row(own, cells)
        }
    }

    private fun text(cell: Element): String {
        val copy = cell.clone()
        copy.select("sup").remove()
        return copy.text().replace(spaces, " ").trim()
    }

    /** Nome del primo link della cella (giocatore o squadra), senza note e numeri tra parentesi. */
    private fun linkName(cell: Element): String {
        val link = cell.select("a[href^=/wiki/]").firstOrNull { a ->
            val href = a.attr("href")
            a.text().isNotBlank() && !Regex("""^/wiki/(File|Image|Immagine):""").containsMatchIn(href)
        }
        return (link?.text() ?: text(cell)).trim()
    }
}

/**
 * Vetrina dei premi di uno sport, salvata in un file JSON ("competitions_awards_<SPORT>.json",
 * incluso nel backup come gli altri file "competitions_"). Si aggiorna con "Aggiorna" in
 * "Detentori": un premio che quella volta non risponde tiene l'albo dell'aggiornamento precedente.
 */
class AwardRepository(context: Context) {

    private val dir = context.applicationContext.filesDir

    private fun fileFor(sport: Sport) = File(dir, "competitions_awards_${sport.name}.json")

    suspend fun load(sport: Sport): Map<String, AwardData> = withContext(Dispatchers.IO) {
        runCatching { decode(fileFor(sport).readText()) }.getOrDefault(emptyMap())
    }

    /** true se manca l'albo di qualche premio (prima apertura o premio aggiunto con un aggiornamento dell'app). */
    fun missesCatalog(sport: Sport, data: Map<String, AwardData>): Boolean = AwardCatalog.forSport(sport).any { it.id !in data }

    /** Rilegge tutti i premi dello sport da Wikipedia; [onProgress] riceve (fatti, totale). */
    suspend fun refresh(sport: Sport, onProgress: suspend (Int, Int) -> Unit = { _, _ -> }): Map<String, AwardData> =
        withContext(Dispatchers.IO) {
            val previous = load(sport)
            val defs = AwardCatalog.forSport(sport)
            // Una voce con due tabelle (FIFA World Player) si scarica una volta sola.
            val pages = mutableMapOf<Pair<String, String>, String?>()
            val result = previous.toMutableMap()
            defs.forEachIndexed { i, def ->
                onProgress(i, defs.size)
                val html = pages.getOrPut(def.lang to def.page) {
                    runCatching { WikipediaCompetitionStats.pageHtml(def.lang, def.page) }.getOrNull()
                } ?: return@forEachIndexed
                val winners = runCatching { AwardTableParser.parse(html, def.tableIndex) }.getOrDefault(emptyList())
                if (winners.isNotEmpty()) {
                    result[def.id] = AwardData(System.currentTimeMillis(), winners.sortedByDescending { it.sortYear })
                }
            }
            onProgress(defs.size, defs.size)
            fileFor(sport).writeText(encode(result))
            result
        }

    private fun encode(data: Map<String, AwardData>): String {
        val root = JSONObject()
        data.forEach { (id, d) ->
            root.put(
                id,
                JSONObject()
                    .put("updatedAt", d.updatedAt)
                    .put("winners", JSONArray().apply {
                        d.winners.forEach { w ->
                            put(
                                JSONObject()
                                    .put("season", w.season)
                                    .put("player", w.player)
                                    .put("team", w.team ?: JSONObject.NULL)
                                    .put("nationality", w.nationality ?: JSONObject.NULL)
                            )
                        }
                    }),
            )
        }
        return root.toString()
    }

    private fun decode(text: String): Map<String, AwardData> {
        val root = JSONObject(text)
        return root.keys().asSequence().associateWith { id ->
            val o = root.getJSONObject(id)
            val array = o.optJSONArray("winners") ?: JSONArray()
            AwardData(
                updatedAt = o.optLong("updatedAt"),
                winners = (0 until array.length()).map { i ->
                    val w = array.getJSONObject(i)
                    AwardWinner(
                        season = w.optString("season"),
                        player = w.optString("player"),
                        team = if (w.isNull("team")) null else w.optString("team").ifBlank { null },
                        nationality = if (w.isNull("nationality")) null else w.optString("nationality").ifBlank { null },
                    )
                },
            )
        }
    }
}
