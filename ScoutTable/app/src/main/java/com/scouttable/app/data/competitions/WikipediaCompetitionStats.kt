// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.competitions

import com.scouttable.app.data.Sport
import com.scouttable.app.data.lookup.countryFromCode
import com.scouttable.app.data.lookup.lookupHttpClient
import com.scouttable.app.data.lookup.stripHtmlComments
import java.net.URLEncoder
import kotlinx.coroutines.delay
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup

/** Una squadra citata in "Detentori": club (con stemma) o nazionale (con bandiera, vedi [isNational]). */
data class TeamRef(
    val name: String,
    val badgeUrl: String? = null,
    val isNational: Boolean = false,
    /** Voce Wikipedia della squadra, da cui si prende lo stemma. */
    val wikiTitle: String? = null,
)

/** Dati di una competizione letti dall'infobox di en.wikipedia.org. */
data class CompetitionData(
    /** Detentore: squadra (o squadre, a pari merito) che ha vinto più volte la competizione. */
    val recordTeams: List<TeamRef>,
    val recordTitles: Int?,
    val logoUrl: String?,
)

/** Infobox di una voce già interpretato, prima della ricerca delle immagini. */
internal data class ParsedCompetition(
    /** Titolo effettivo della voce (dopo i redirect). */
    val title: String,
    val recordTeams: List<TeamRef>,
    val recordTitles: Int?,
    val logoFile: String?,
    /** false se l'infobox indica una competizione non più disputata (es. "folded", "last season"). */
    val active: Boolean,
    /** Testo iniziale della voce, per riconoscere lo sport (vedi [WikipediaCompetitionStats.isAboutSport]). */
    val lead: String,
    /** true se l'infobox non nomina le squadre ma solo quante sono a pari merito (es. "Six teams
     *  (1 title each)", Basketball Africa League): vanno lette dalla tabella "Performance by club"
     *  della voce (vedi [WikipediaCompetitionStats.teamsFromByClubTable]). */
    val groupRecord: Boolean = false,
) {
    val hasRecord: Boolean get() = recordTeams.isNotEmpty() || groupRecord
}

/**
 * Legge da en.wikipedia.org il detentore (la squadra con più titoli) e il logo delle competizioni, e scopre
 * le competizioni nuove dai riquadri di navigazione e dagli elenchi di Wikipedia.
 *
 * Tutto a blocchi di 50 voci per richiesta (API "query"): con ~500 competizioni tra calcio e
 * basket, una richiesta per voce farebbe scattare il limite di Wikipedia (risposta 429, verificato
 * anche in sequenza con 0,4 s di pausa).
 *
 * I nomi del campo cambiano da un infobox all'altro (verificato su ~80 voci): "most successful
 * club"/"most_successful_team"/"most_titles"/"most_champs". I valori mescolano wikilink ("[[Real
 * Madrid CF|Real Madrid]] (15 titles)"), template delle nazionali ("{{fb|BRA}} (5 titles)") e
 * testo semplice, spesso dentro "{{nowrap|...}}"; il numero di titoli è tra parentesi.
 */
object WikipediaCompetitionStats {

    private const val USER_AGENT = "ScoutTableApp/1.0 (Android scouting app; contact: n/a)"
    private const val API = "https://en.wikipedia.org/w/api.php?"
    private const val BATCH = 50
    // Wikimedia genera le miniature solo in alcune larghezze standard (120, 250, 330...): con una
    // larghezza diversa (era 160) l'URL restituito dall'API risponde 400 e il logo non compare.
    private const val THUMB_WIDTH = "250"

    // ---------------------------------------------------------------- scoperta

    /**
     * Pagine di Wikipedia che elencano le competizioni dello sport: riquadri di navigazione delle
     * confederazioni (calcio) ed elenchi delle leghe (basket, l'unico che copre anche NBA, G League,
     * WNBA, NCAA e i campionati fuori dall'Europa).
     */
    private val discoverySources = mapOf(
        Sport.CALCIO to listOf(
            "Template:UEFA competitions", "Template:AFC competitions", "Template:CAF competitions",
            "Template:CONCACAF competitions", "Template:OFC competitions", "Template:UEFA leagues",
            "Template:CONMEBOL leagues", "Template:AFC leagues", "Template:CAF leagues", "Template:CONCACAF leagues",
            // Femminile: senza queste pagine la ricerca trovava quasi solo competizioni maschili.
            "Template:Women's football in Europe", "Template:Women's association football", "Template:International women's football",
        ),
        Sport.BASKET to listOf(
            "List of basketball leagues", "FIBA", "Template:Basketball in Europe",
            "Template:National Basketball Association", "Template:NCAA Division I men's basketball tournament",
            "Women's basketball", "Template:EuroLeague Women",
        ),
    )

    // Titoli che non sono competizioni: giovanili, futsal/beach, premi, elenchi,
    // singole stagioni, voci "Football in X", division/conference NBA, ecc.
    private val excludedTitleRegex = Regex(
        """\b(U-?\d{2}|Under-\d{2}|Youth|Futsal|Beach|3x3|qualif|Awards?|Player of|Footballer|Coach|List of|in [A-Z]|""" +
            """scandal|rule|campaign|system|season|Next Generation|Triple Crown|Confederation|Federation|statistics|""" +
            """records|Ballon|Golden|Team of|Hall of Fame|Draft|All-Star|Most Valuable|Junior|Amateur|School|""" +
            """Summer League|Small Countries|Wikidata|Web archive|\d{4})\b|(Division|Conference) \(NBA\)|^(FIBA|UEFA|CONMEBOL|CAF|AFC|OFC|CONCACAF|Association football|Basketball)$""",
        RegexOption.IGNORE_CASE,
    )

    // Nel basket gli elenchi linkano anche squadre e nazioni: si tengono solo i titoli che
    // sembrano competizioni (leghe, coppe, campionati, tornei...).
    private val basketCompetitionWordRegex = Regex(
        """League|Liga|Ligue|Lega|Liiga|Ligi|Lyga|Ligaen|Division|Championship|Cup|Copa|Coppa|Pokal|NBA|NBL|BBL|ACB|LKL|""" +
            """Super ?league|Serie|Bundesliga|Tournament|Association|Ligat|Liha|EuroBasket|AfroBasket|AmeriCup|Basket|""" +
            """Trophy|Games|Series|Korisliiga|Basketligan|Pro A|Élite|Elite|Divisie""",
        RegexOption.IGNORE_CASE,
    )

    /** Titoli candidati a competizione per lo sport (da verificare poi con [fetchParsed]). */
    internal suspend fun discoverTitles(sport: Sport): List<String> {
        val links = pageLinks(discoverySources[sport].orEmpty())
        return links.filter { title ->
            !excludedTitleRegex.containsMatchIn(title) &&
                (sport != Sport.BASKET || basketCompetitionWordRegex.containsMatchIn(title))
        }
    }

    private suspend fun pageLinks(pages: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        val params = linkedMapOf(
            "action" to "query", "prop" to "links", "pllimit" to "max", "plnamespace" to "0",
            "redirects" to "1", "format" to "json", "formatversion" to "2", "titles" to pages.joinToString("|"),
        )
        repeat(30) {
            val json = apiGet(params) ?: return out.toList()
            val list = json.optJSONObject("query")?.optJSONArray("pages")
            for (i in 0 until (list?.length() ?: 0)) {
                val links = list!!.getJSONObject(i).optJSONArray("links") ?: continue
                for (j in 0 until links.length()) out += links.getJSONObject(j).getString("title")
            }
            val cont = json.optJSONObject("continue") ?: return out.toList()
            cont.keys().forEach { params[it] = cont.getString(it) }
        }
        return out.toList()
    }

    /** Vero se la voce parla dello sport giusto (un elenco di leghe di basket linka anche il Mondiale FIFA). */
    internal fun isAboutSport(parsed: ParsedCompetition, sport: Sport): Boolean {
        val lead = parsed.lead.lowercase()
        return if (sport == Sport.BASKET) "basketball" in lead else ("football" in lead || "soccer" in lead)
    }

    // ---------------------------------------------------------------- lettura a blocchi

    /**
     * Legge e interpreta le voci richieste, a blocchi di 50. La chiave del risultato è il titolo
     * richiesto (anche se Wikipedia l'ha rediretto altrove); le voci mancanti o senza infobox utile
     * non compaiono.
     */
    internal suspend fun fetchParsed(
        titles: List<String>,
        sport: Sport,
        onBatch: suspend (done: Int, total: Int) -> Unit,
    ): Map<String, ParsedCompetition> {
        val result = mutableMapOf<String, ParsedCompetition>()
        val chunks = titles.distinct().chunked(BATCH)
        chunks.forEachIndexed { index, chunk ->
            onBatch(index, chunks.size)
            val json = apiGet(
                linkedMapOf(
                    "action" to "query", "prop" to "revisions", "rvprop" to "content", "rvslots" to "main",
                    "rvsection" to "0", "redirects" to "1", "format" to "json", "formatversion" to "2",
                    "titles" to chunk.joinToString("|"),
                )
            ) ?: return@forEachIndexed
            val query = json.optJSONObject("query") ?: return@forEachIndexed
            val resolve = titleResolver(query)
            val byTitle = mutableMapOf<String, ParsedCompetition>()
            val pages = query.optJSONArray("pages")
            for (i in 0 until (pages?.length() ?: 0)) {
                val page = pages!!.getJSONObject(i)
                val content = page.optJSONArray("revisions")?.optJSONObject(0)
                    ?.optJSONObject("slots")?.optJSONObject("main")?.optString("content") ?: continue
                parse(page.getString("title"), content, sport)?.let { byTitle[page.getString("title")] = it }
            }
            chunk.forEach { requested -> byTitle[resolve(requested)]?.let { result[requested] = it } }
        }
        onBatch(chunks.size, chunks.size)
        return result
    }

    /** Titolo richiesto -> titolo effettivo, seguendo normalizzazioni e redirect riportati dall'API. */
    private fun titleResolver(query: JSONObject): (String) -> String {
        val map = mutableMapOf<String, String>()
        for (key in listOf("normalized", "redirects")) {
            val arr = query.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                map[o.getString("from")] = o.getString("to")
            }
        }
        return { title ->
            var t = title
            repeat(3) { map[t]?.let { t = it } }
            t
        }
    }

    /**
     * Immagine principale di ogni voce (stemma dei club, logo delle competizioni), anche se non
     * libera (pilicense=any: gli stemmi sono quasi sempre immagini non libere, escluse di default).
     */
    internal suspend fun pageImages(
        titles: Collection<String>,
        onChunk: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Map<String, String> {
        val out = mutableMapOf<String, String>()
        val chunks = titles.distinct().chunked(BATCH)
        for ((index, chunk) in chunks.withIndex()) {
            onChunk(index, chunks.size)
            val json = apiGet(
                linkedMapOf(
                    "action" to "query", "prop" to "pageimages", "piprop" to "thumbnail", "pithumbsize" to THUMB_WIDTH,
                    "pilicense" to "any", "pilimit" to "max", "redirects" to "1", "format" to "json",
                    "formatversion" to "2", "titles" to chunk.joinToString("|"),
                )
            ) ?: continue
            val query = json.optJSONObject("query") ?: continue
            val resolve = titleResolver(query)
            val byTitle = mutableMapOf<String, String>()
            val pages = query.optJSONArray("pages")
            for (i in 0 until (pages?.length() ?: 0)) {
                val page = pages!!.getJSONObject(i)
                page.optJSONObject("thumbnail")?.optString("source")?.ifBlank { null }?.let { byTitle[page.getString("title")] = it }
            }
            chunk.forEach { requested -> byTitle[resolve(requested)]?.let { out[requested] = it } }
        }
        return out
    }

    /** Miniature dei file indicati nel campo "logo"/"image" degli infobox. */
    internal suspend fun fileThumbs(fileNames: Collection<String>): Map<String, String> {
        val out = mutableMapOf<String, String>()
        for (chunk in fileNames.distinct().chunked(BATCH)) {
            val json = apiGet(
                linkedMapOf(
                    "action" to "query", "prop" to "imageinfo", "iiprop" to "url", "iiurlwidth" to THUMB_WIDTH,
                    "format" to "json", "formatversion" to "2", "titles" to chunk.joinToString("|") { "File:$it" },
                )
            ) ?: continue
            val query = json.optJSONObject("query") ?: continue
            val resolve = titleResolver(query)
            val byTitle = mutableMapOf<String, String>()
            val pages = query.optJSONArray("pages")
            for (i in 0 until (pages?.length() ?: 0)) {
                val page = pages!!.getJSONObject(i)
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                info.optString("thumburl").ifBlank { info.optString("url") }.ifBlank { null }?.let { byTitle[page.getString("title")] = it }
            }
            chunk.forEach { name -> byTitle[resolve("File:$name")]?.let { out[name] = it } }
        }
        return out
    }

    // ---------------------------------------------------------------- rete

    // Wikipedia risponde 429 (troppe richieste) anche a richieste in sequenza ravvicinate: pausa
    // fissa tra una richiesta e l'altra, e in caso di 429 nuovi tentativi con pause crescenti.
    private suspend fun apiGet(params: Map<String, String>): JSONObject? {
        val url = API + params.entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }
        for (wait in listOf(1_000L, 4_000L, 10_000L, 20_000L, 30_000L)) {
            delay(wait)
            val body = runCatching {
                val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                lookupHttpClient.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> response.body?.string()
                        response.code == 429 || response.code >= 500 -> null
                        else -> return null
                    }
                }
            }.getOrNull() ?: continue
            return runCatching { JSONObject(body) }.getOrNull()
        }
        return null
    }

    // ---------------------------------------------------------------- infobox

    private val recordKeys = listOf(
        "most_successful_club", "most_successful_team", "most_successful_clubs", "most_successful_teams",
        "most_titles", "most_champs", "most_successful", "most_wins",
    )
    private val logoKeys = listOf("logo", "image")
    private val defunctKeys = listOf(
        "folded", "abolished", "dissolved", "defunct", "ceased", "last_season", "last_held",
        "last_tournament", "last_champions", "last_champion", "replaced_by",
    )

    private fun parse(title: String, wikitext: String, sport: Sport): ParsedCompetition? {
        val fields = infoboxFields(wikitext) ?: return null
        val recordRaw = recordKeys.firstNotNullOfOrNull { fields[it]?.takeIf(String::isNotBlank) } ?: return null
        val record = firstEntryOnly(cleanValue(recordRaw))
        val groupRecord = groupRecordRegex.containsMatchIn(record)
        return ParsedCompetition(
            title = title,
            // "Six teams" non è una squadra: con un conteggio al posto dei nomi le squadre arrivano
            // dopo, dalla tabella della voce.
            recordTeams = if (groupRecord) emptyList() else parseTeams(record, sport, women = title.contains("women", ignoreCase = true)),
            groupRecord = groupRecord,
            recordTitles = recordCountRegex.find(record)?.groupValues?.get(1)?.toIntOrNull(),
            logoFile = logoKeys.firstNotNullOfOrNull { fields[it]?.let(::fileNameOf) },
            active = defunctKeys.none { !fields[it].isNullOrBlank() },
            lead = wikitext.take(4_000),
        )
    }

    // "Six teams (1 title each)", "6 clubs (2 titles each)", "{{nowrap|5 nations (1 title each)}}".
    private val groupRecordRegex = Regex(
        """\b(\d+|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\s+(clubs|teams|nations|sides|countries)\b\s*\(""",
        RegexOption.IGNORE_CASE,
    )
    private val byClubSectionRegex = Regex(
        """(performances?|winners|titles|champions|results|records?|finals?|wins)\s+by\s+(club|team|nation|country)|^by\s+(club|team|nation|country)""",
        RegexOption.IGNORE_CASE,
    )
    private val titlesHeaderRegex = Regex("""^(titles?|winners?|won|champions?|wins?|w)$""", RegexOption.IGNORE_CASE)
    private val teamHeaderRegex = Regex("""club|team|nation|country""", RegexOption.IGNORE_CASE)

    /**
     * Squadre a pari merito con il maggior numero di titoli, dalla tabella della sezione
     * "Performance by club" (o simile) della voce [title], resa in HTML dall'API (la tabella è
     * spesso inclusa da un'altra voce, quindi il wikitext non la contiene). Si tengono le squadre
     * con [titles] titoli (o, se non noto, con il massimo della tabella). Vuota se la voce non ha
     * una tabella riconoscibile.
     */
    internal suspend fun teamsFromByClubTable(title: String, titles: Int?): List<TeamRef> {
        val sections = apiGet(
            linkedMapOf("action" to "parse", "page" to title, "prop" to "sections", "redirects" to "1", "format" to "json", "formatversion" to "2")
        )?.optJSONObject("parse")?.optJSONArray("sections") ?: return emptyList()
        val index = (0 until sections.length()).map { sections.getJSONObject(it) }
            .firstOrNull { byClubSectionRegex.containsMatchIn(it.optString("line")) }?.optString("index") ?: return emptyList()
        val html = apiGet(
            linkedMapOf(
                "action" to "parse", "page" to title, "section" to index, "prop" to "text",
                "redirects" to "1", "format" to "json", "formatversion" to "2",
            )
        )?.optJSONObject("parse")?.optString("text")?.ifBlank { null } ?: return emptyList()

        val table = Jsoup.parse(html).selectFirst("table.wikitable") ?: return emptyList()
        val rows = table.select("tr")
        val header = rows.firstOrNull()?.select("> th")?.map { it.text().trim() } ?: return emptyList()
        val titlesCol = header.indexOfFirst { titlesHeaderRegex.matches(it) }.takeIf { it >= 0 } ?: return emptyList()
        val teamCol = header.indexOfFirst { teamHeaderRegex.containsMatchIn(it) }.takeIf { it >= 0 } ?: 0
        val counted = rows.drop(1).mapNotNull { row ->
            val cells = row.select("> th, > td")
            if (cells.size <= maxOf(titlesCol, teamCol)) return@mapNotNull null
            val count = cells[titlesCol].text().trim().toIntOrNull() ?: return@mapNotNull null
            val cell = cells[teamCol]
            // Prima del nome c'è spesso la bandiera, anch'essa un link (alla voce del paese).
            val link = cell.select("a[href^=/wiki/]").lastOrNull { it.text().isNotBlank() }
            val name = (link?.text() ?: cell.text()).trim().ifBlank { return@mapNotNull null }
            val wikiTitle = link?.attr("href")?.removePrefix("/wiki/")?.substringBefore('#')
                ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }?.replace('_', ' ')
            TeamRef(name, isNational = wikiTitle?.contains(" national ", ignoreCase = true) == true, wikiTitle = wikiTitle) to count
        }
        val target = titles ?: counted.maxOfOrNull { it.second } ?: return emptyList()
        if (target <= 0) return emptyList()
        return counted.filter { it.second == target }.map { it.first }.distinctBy { it.name.lowercase() }
    }

    private val fieldRegex = Regex("""^\s*\|\s*([A-Za-z_ ]+?)\s*=(.*)$""")

    /**
     * Campi del primo infobox della voce, con chiave normalizzata ("most successful club" ->
     * "most_successful_club"). Un valore continua sulle righe successive finché le graffe dei
     * template non si chiudono (es. "{{plainlist|" con le voci elencate sotto).
     */
    private fun infoboxFields(wikitext: String): Map<String, String>? {
        val start = wikitext.indexOf("{{Infobox", ignoreCase = true).takeIf { it >= 0 } ?: return null
        val box = stripHtmlComments(wikitext.substring(start, minOf(wikitext.length, start + 20_000)))
        val lines = box.lines()
        val fields = mutableMapOf<String, String>()
        var i = 0
        while (i < lines.size) {
            val m = fieldRegex.matchEntire(lines[i])
            if (m != null) {
                val key = m.groupValues[1].trim().lowercase().replace(' ', '_')
                val value = StringBuilder(m.groupValues[2])
                while (braceDepth(value) > 0 && i + 1 < lines.size && !fieldRegex.matches(lines[i + 1])) {
                    i++
                    value.append('\n').append(lines[i])
                }
                fields.putIfAbsent(key, value.toString().trim())
            }
            i++
        }
        return fields
    }

    private fun braceDepth(text: CharSequence): Int {
        val s = text.toString()
        return Regex("""\{\{""").findAll(s).count() - Regex("""\}\}""").findAll(s).count()
    }

    private val refRegex = Regex("""<ref[^>]*/>|<ref[^>]*>.*?</ref>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val brRegex = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)

    private fun cleanValue(raw: String): String =
        raw.replace(refRegex, "").replace("&nbsp;", " ").replace(brRegex, "\n").trim()

    private val recordCountRegex = Regex("""\((\d+)\s*(?:titles?|wins?|times)?\s*(?:each)?\s*\)""", RegexOption.IGNORE_CASE)
    private val ordinalRegex = Regex("""\((\d+)\s*(?:st|nd|rd|th)\b""", RegexOption.IGNORE_CASE)

    /** Più conteggi nello stesso valore indicano più serie nello stesso campo (es. B.League:
     *  "B1: ... (3 titles) B2: ... (2 titles)"): vale solo la prima, la serie maggiore. */
    private fun firstEntryOnly(value: String): String {
        val matches = (recordCountRegex.findAll(value) + ordinalRegex.findAll(value)).sortedBy { it.range.first }.toList()
        if (matches.size < 2) return value
        return value.substring(0, matches.first().range.last + 1)
    }

    // Link o template di una nazionale ("{{fb|BRA}}", "{{bkw|Soviet Union}}", "{{fbu|23|ESP}}").
    private val teamTokenRegex = Regex(
        """\[\[([^\]|]+)(?:\|([^\]]+))?]]|\{\{\s*(fb|fbw|bk|bkw|fbu|bku|fbwu|bkwu)\s*\|([^{}]*)\}\}""",
        RegexOption.IGNORE_CASE,
    )
    private val wrapperTemplateRegex = Regex(
        """\{\{\s*(?:nowrap|no break|nobreak|ubl|unbulleted list|plainlist|plain list|hlist)\s*\|""",
        RegexOption.IGNORE_CASE,
    )
    private val simpleTemplateRegex = Regex("""\{\{[^{}]*\}\}""")
    private val parenRegex = Regex("""\([^)]*\)""")
    private val divisionPrefixRegex = Regex("""^[A-Z0-9]{1,3}:\s*""")
    private val seasonRegex = Regex("""^\d{4}""")
    private val linkMarkupRegex = Regex("""\[\[(?:[^\]|]*\|)?|]]""")
    private val nonTeamLinkRegex = Regex("""^(File|Image|Category|List of)\b""", RegexOption.IGNORE_CASE)

    private fun parseTeams(value: String, sport: Sport, women: Boolean): List<TeamRef> {
        val teams = mutableListOf<TeamRef>()
        for (m in teamTokenRegex.findAll(value)) {
            if (m.groupValues[1].isNotEmpty()) {
                val target = m.groupValues[1].trim()
                // File, liste e voci di stagione non sono squadre (es. "[[List of winners of ...]]").
                if (nonTeamLinkRegex.containsMatchIn(target)) continue
                val display = m.groupValues[2].ifBlank { target }.replace(parenRegex, "").trim()
                // Un anno in testa è una stagione ("[[2025–26 ...|2025–26]]"), non una squadra; altre
                // cifre sì (es. "1º de Agosto", Angola).
                if (display.isBlank() || seasonRegex.containsMatchIn(display)) continue
                val isSeasonPage = target.firstOrNull()?.isDigit() == true
                teams += TeamRef(
                    name = display,
                    isNational = target.contains(" national ", ignoreCase = true),
                    // "[[2025–26 New York Knicks season|New York Knicks]]": la voce della stagione
                    // non ha lo stemma, quella della squadra (dal nome mostrato) sì.
                    wikiTitle = if (isSeasonPage) display else target,
                )
            } else {
                // Parametri posizionali del template: il primo non numerico è il paese (codice o nome).
                val country = m.groupValues[4].split('|').map { it.trim() }
                    .firstOrNull { it.isNotBlank() && !it.contains('=') && !it.all(Char::isDigit) } ?: continue
                val name = countryName(country)
                // La voce della nazionale (es. "Brazil national football team") ha come immagine lo
                // stemma della federazione: è il "logo" della squadra, come lo stemma per un club.
                teams += TeamRef(name, isNational = true, wikiTitle = nationalTeamTitle(name, m.groupValues[3]))
            }
        }
        if (teams.isNotEmpty()) return teams.distinctBy { it.name.lowercase() }

        // Solo testo semplice (es. "{{fbaicon|ESP}} Spain (4 titles)", "Bayern Munich (34 titles)").
        var text = value.replace(wrapperTemplateRegex, "")
        while (simpleTemplateRegex.containsMatchIn(text)) text = text.replace(simpleTemplateRegex, "")
        text = text.replace("{{", "").replace("}}", "").replace(linkMarkupRegex, "").replace(parenRegex, "")
        val name = text.split('\n', '|', '*').map { it.trim().replace(divisionPrefixRegex, "") }
            .firstOrNull { it.length >= 2 && !it.first().isDigit() } ?: return emptyList()
        // Un paese scritto come testo (es. "{{fbaicon|ESP}} Spain (4 titles)" negli Europei) è una
        // nazionale: lo stemma è quello della federazione, sulla voce della nazionale, non la
        // bandiera della voce del paese.
        if (name in countryNames) {
            val template = (if (sport == Sport.BASKET) "bk" else "fb") + if (women) "w" else ""
            return listOf(TeamRef(name, isNational = true, wikiTitle = nationalTeamTitle(name, template)))
        }
        return listOf(TeamRef(name, wikiTitle = name))
    }

    // Codici FIFA/CIO usati dai template delle nazionali che non coincidono con i codici ISO (o
    // indicano paesi che non esistono più).
    private val fifaCodes = mapOf(
        "GER" to "Germany", "NED" to "Netherlands", "POR" to "Portugal", "CRO" to "Croatia",
        "SUI" to "Switzerland", "DEN" to "Denmark", "GRE" to "Greece", "URU" to "Uruguay",
        "ENG" to "England", "SCO" to "Scotland", "WAL" to "Wales", "NIR" to "Northern Ireland",
        "URS" to "Soviet Union", "YUG" to "Yugoslavia", "TCH" to "Czechoslovakia", "FRG" to "West Germany",
        "GDR" to "East Germany", "CHI" to "Chile", "PAR" to "Paraguay", "KSA" to "Saudi Arabia",
        "RSA" to "South Africa", "ALG" to "Algeria", "CRC" to "Costa Rica", "HON" to "Honduras",
        "BUL" to "Bulgaria", "SLO" to "Slovenia", "LTU" to "Lithuania", "LAT" to "Latvia", "ANG" to "Angola",
        "NGR" to "Nigeria", "QTR" to "Qatar", "IRI" to "Iran", "UAE" to "United Arab Emirates",
        "PHI" to "Philippines", "INA" to "Indonesia", "MAS" to "Malaysia", "VIE" to "Vietnam",
        "ZAM" to "Zambia", "ZIM" to "Zimbabwe", "CIV" to "Ivory Coast", "GBR" to "Great Britain",
        "USA" to "United States", "KOR" to "South Korea", "PUR" to "Puerto Rico", "TPE" to "Taiwan",
    )

    // Paesi in cui en.wikipedia chiama la nazionale di calcio "soccer team", maschile e femminile
    // separate (es. "United States women's national soccer team").
    private val soccerCountries = setOf("United States", "Canada", "Australia")

    private fun nationalTeamTitle(country: String, template: String): String {
        val women = template.lowercase().startsWith("fbw") || template.lowercase().startsWith("bkw")
        return when {
            template.lowercase().startsWith("bk") ->
                if (women) "$country women's national basketball team" else "$country men's national basketball team"
            country in soccerCountries ->
                if (women) "$country women's national soccer team" else "$country men's national soccer team"
            else -> if (women) "$country women's national football team" else "$country national football team"
        }
    }

    private val countryNames: Set<String> by lazy {
        java.util.Locale.getISOCountries().map { java.util.Locale("", it).getDisplayCountry(java.util.Locale.ENGLISH) }.toSet() +
            fifaCodes.values
    }

    internal fun countryName(codeOrName: String): String =
        if (codeOrName.length == 3 && codeOrName.all { it.isUpperCase() }) {
            fifaCodes[codeOrName] ?: countryFromCode(codeOrName) ?: codeOrName
        } else codeOrName

    private val imageExtensionRegex = Regex("""\.(svg|png|jpe?g|gif|webp)$""", RegexOption.IGNORE_CASE)

    /** "[[File:UEFA Champions League.svg|frameless|200px]]" o "Logo.svg{{!}}class=..." -> nome del file. */
    private fun fileNameOf(raw: String): String? {
        val name = raw.trim()
            .replace(Regex("""^\[\[\s*(?:File|Image)\s*:""", RegexOption.IGNORE_CASE), "")
            .substringBefore('|').substringBefore("{{").substringBefore("]]").trim()
            // "Logo de la Conmebol Copa Am%C3%A9rica.svg" (Copa América): va decodificato,
            // altrimenti il file non viene trovato e il logo manca.
            .let { runCatching { java.net.URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.getOrDefault(it) }
        return name.takeIf { imageExtensionRegex.containsMatchIn(it) }
    }
}
