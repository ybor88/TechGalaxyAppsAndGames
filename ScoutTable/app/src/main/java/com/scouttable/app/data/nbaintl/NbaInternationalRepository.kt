// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.nbaintl

import android.content.Context
import com.scouttable.app.data.competitions.WikipediaCompetitionStats
import com.scouttable.app.data.lookup.lookupHttpClient
import java.io.File
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** Squadra citata in una partita o nelle tabelle: [country] è il nome inglese del paese (dalla bandiera). */
data class IntlTeam(val name: String, val wikiTitle: String?, val country: String?)

/** Una partita tra una squadra NBA ([nba]) e una squadra internazionale ([intl]). */
data class IntlMatch(
    val date: String,
    val year: Int?,
    val place: String,
    val nba: IntlTeam,
    val intl: IntlTeam,
    val nbaScore: Int,
    val intlScore: Int,
    val overtime: Boolean,
) {
    val nbaWon: Boolean get() = nbaScore > intlScore
}

/** Bilancio di una squadra contro l'altra parte (NBA contro internazionali o viceversa). */
data class IntlTeamRecord(
    val team: IntlTeam,
    val isNba: Boolean,
    val games: Int,
    val wins: Int,
    val losses: Int,
    val logoUrl: String?,
    /** Voce Wikipedia effettiva (dopo i redirect), per legare le partite alla squadra. */
    val canonicalTitle: String?,
)

data class NbaIntlSnapshot(val updatedAt: Long, val records: List<IntlTeamRecord>, val matches: List<IntlMatch>, val canonical: Map<String, String>) {
    /** Partite di una squadra, dalla più recente: il suo "albo d'oro" contro l'altra parte. */
    fun matchesOf(record: IntlTeamRecord): List<IntlMatch> {
        val key = record.canonicalTitle ?: record.team.wikiTitle ?: record.team.name
        return matches.filter { m ->
            val t = if (record.isNba) m.nba else m.intl
            (canonical[t.wikiTitle] ?: t.wikiTitle ?: t.name) == key
        }.reversed()
    }
}

/**
 * Partite tra squadre NBA e squadre internazionali da en.wikipedia
 * ("List of games played between NBA and international teams"):
 * - ogni partita è un template {{basketballbox}} (date, place, teamA, scoreA, teamB, scoreB,
 *   overtime): la squadra NBA è teamA, quella internazionale teamB (bandiera USA/Canada per le NBA);
 * - le tabelle "NBA teams" e "International teams" danno il bilancio (GP/W/L) di ogni squadra.
 * Gli stemmi (immagine principale della voce di ogni squadra) vengono dalla stessa API, seguendo i
 * redirect: così "New Jersey Nets" nelle partite e "Brooklyn Nets" in tabella sono la stessa squadra.
 * Letti solo con "Aggiorna" (o alla prima apertura) e salvati in un file JSON.
 */
class NbaInternationalRepository(context: Context) {

    private val file = File(context.applicationContext.filesDir, "nba_international.json")

    suspend fun load(): NbaIntlSnapshot? = withContext(Dispatchers.IO) {
        runCatching { decode(file.readText()) }.getOrNull()
    }

    suspend fun refresh(): NbaIntlSnapshot? = withContext(Dispatchers.IO) {
        val wikitext = fetchRaw() ?: return@withContext null
        val parsed = NbaIntlParser.parse(wikitext)
        if (parsed.records.isEmpty() && parsed.matches.isEmpty()) return@withContext null
        val titles = (parsed.records.mapNotNull { it.team.wikiTitle } +
            parsed.matches.flatMap { listOfNotNull(it.nba.wikiTitle, it.intl.wikiTitle) }).distinct()
        val (canonical, images) = resolveTitles(titles)
        val records = parsed.records.map { r ->
            val canon = r.team.wikiTitle?.let { canonical[it] ?: it }
            r.copy(canonicalTitle = canon, logoUrl = r.team.wikiTitle?.let { images[it] })
        }
        val snapshot = NbaIntlSnapshot(System.currentTimeMillis(), records, parsed.matches, canonical)
        file.writeText(encode(snapshot))
        snapshot
    }

    private fun fetchRaw(): String? = runCatching {
        val url = "https://en.wikipedia.org/w/index.php?title=" +
            URLEncoder.encode(PAGE_TITLE, "UTF-8") + "&action=raw"
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        lookupHttpClient.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }.getOrNull()

    /** Titolo richiesto -> titolo effettivo (redirect) e -> stemma, a blocchi di 50 voci. */
    private suspend fun resolveTitles(titles: List<String>): Pair<Map<String, String>, Map<String, String>> {
        val canonical = mutableMapOf<String, String>()
        val images = mutableMapOf<String, String>()
        for (chunk in titles.chunked(50)) {
            delay(500)
            val params = linkedMapOf(
                "action" to "query", "prop" to "pageimages", "piprop" to "thumbnail", "pithumbsize" to "250",
                "pilicense" to "any", "pilimit" to "max", "redirects" to "1", "format" to "json",
                "formatversion" to "2", "titles" to chunk.joinToString("|"),
            )
            val url = "https://en.wikipedia.org/w/api.php?" + params.entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }
            val body = runCatching {
                val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                lookupHttpClient.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
            }.getOrNull() ?: continue
            val query = runCatching { JSONObject(body).getJSONObject("query") }.getOrNull() ?: continue
            val map = mutableMapOf<String, String>()
            for (key in listOf("normalized", "redirects")) {
                val arr = query.optJSONArray(key) ?: continue
                for (i in 0 until arr.length()) arr.getJSONObject(i).let { map[it.getString("from")] = it.getString("to") }
            }
            val thumbs = mutableMapOf<String, String>()
            val pages = query.optJSONArray("pages")
            for (i in 0 until (pages?.length() ?: 0)) {
                val page = pages!!.getJSONObject(i)
                page.optJSONObject("thumbnail")?.optString("source")?.ifBlank { null }?.let { thumbs[page.getString("title")] = it }
            }
            chunk.forEach { requested ->
                var t = requested
                repeat(3) { map[t]?.let { t = it } }
                canonical[requested] = t
                thumbs[t]?.let { images[requested] = it }
            }
        }
        return canonical to images
    }

    // ---------------------------------------------------------------- JSON

    private fun IntlTeam.toJson() = JSONObject().put("name", name).put("wiki", wikiTitle ?: JSONObject.NULL).put("country", country ?: JSONObject.NULL)
    private fun JSONObject.toTeam() = IntlTeam(optString("name"), optStringOrNull("wiki"), optStringOrNull("country"))
    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }

    private fun encode(s: NbaIntlSnapshot): String = JSONObject()
        .put("updatedAt", s.updatedAt)
        .put("records", JSONArray().apply {
            s.records.forEach { r ->
                put(JSONObject().put("team", r.team.toJson()).put("nba", r.isNba).put("gp", r.games).put("w", r.wins).put("l", r.losses)
                    .put("logo", r.logoUrl ?: JSONObject.NULL).put("canon", r.canonicalTitle ?: JSONObject.NULL))
            }
        })
        .put("matches", JSONArray().apply {
            s.matches.forEach { m ->
                put(JSONObject().put("date", m.date).put("year", m.year ?: JSONObject.NULL).put("place", m.place)
                    .put("nba", m.nba.toJson()).put("intl", m.intl.toJson()).put("ns", m.nbaScore).put("is", m.intlScore).put("ot", m.overtime))
            }
        })
        .put("canonical", JSONObject(s.canonical as Map<*, *>))
        .toString()

    private fun decode(text: String): NbaIntlSnapshot {
        val root = JSONObject(text)
        val recs = root.getJSONArray("records")
        val records = (0 until recs.length()).map { i ->
            val o = recs.getJSONObject(i)
            IntlTeamRecord(o.getJSONObject("team").toTeam(), o.optBoolean("nba"), o.optInt("gp"), o.optInt("w"), o.optInt("l"),
                o.optStringOrNull("logo"), o.optStringOrNull("canon"))
        }
        val ms = root.getJSONArray("matches")
        val matches = (0 until ms.length()).map { i ->
            val o = ms.getJSONObject(i)
            IntlMatch(o.optString("date"), if (o.isNull("year")) null else o.optInt("year"), o.optString("place"),
                o.getJSONObject("nba").toTeam(), o.getJSONObject("intl").toTeam(), o.optInt("ns"), o.optInt("is"), o.optBoolean("ot"))
        }
        val c = root.optJSONObject("canonical")
        val canonical = c?.keys()?.asSequence()?.associateWith { c.getString(it) }.orEmpty()
        return NbaIntlSnapshot(root.optLong("updatedAt"), records, matches, canonical)
    }

    companion object {
        const val PAGE_TITLE = "List of games played between NBA and international teams"
        private const val USER_AGENT = "ScoutTableApp/1.0 (Android scouting app; contact: n/a)"
    }
}

/** Parser del wikitesto, separato dalla rete per i test. */
internal object NbaIntlParser {

    data class Parsed(val records: List<IntlTeamRecord>, val matches: List<IntlMatch>)

    // "}" e "]" sempre con escape: il motore regex di Android (ICU) li rifiuta se non bilanciati.
    private val linkRegex = Regex("""\[\[([^\]|]+)(?:\|([^\]]+))?\]\]""")
    private val flagRegex = Regex("""\{\{\s*flag(?:icon)?\s*\|\s*([^|}]+)""", RegexOption.IGNORE_CASE)
    private val commentRegex = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
    private val refRegex = Regex("""<ref[^>]*/>|<ref[^>]*>.*?</ref>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val templateRegex = Regex("""\{\{[^{}]*\}\}""")
    private val yearRegex = Regex("""(?:19|20)\d{2}""")
    private val numberRegex = Regex("""\d+""")
    private val rowRegex = Regex("""^\|\s*(?:align\s*=\s*"?left"?\s*\|)?(.*?)\|\|\s*(\d+)\s*\|\|\s*(\d+)\s*\|\|\s*(\d+)""")
    private val nbaCountries = setOf("USA", "CAN", "United States", "Canada")

    fun parse(wikitext: String): Parsed {
        val text = commentRegex.replace(wikitext, "")
        val matches = templates(text, "basketballbox").mapNotNull(::parseMatch)
        val nbaRecords = section(text, "NBA teams")?.let { parseTable(it, isNba = true) }.orEmpty()
        val intlRecords = section(text, "International teams")?.let { parseTable(it, isNba = false) }.orEmpty()
        // Senza tabelle (pagina riorganizzata) il bilancio si calcola dalle partite.
        val records = if (nbaRecords.isNotEmpty() || intlRecords.isNotEmpty()) nbaRecords + intlRecords else recordsFromMatches(matches)
        return Parsed(records, matches)
    }

    /** Contenuto dei template [name] (senza "{{name" e "}}"), con le graffe annidate bilanciate. */
    private fun templates(text: String, name: String): List<String> {
        val out = mutableListOf<String>()
        val start = Regex("""\{\{\s*$name\s*\|""", RegexOption.IGNORE_CASE)
        var from = 0
        while (true) {
            val m = start.find(text, from) ?: break
            var depth = 0
            var i = m.range.first
            var end = -1
            while (i < text.length - 1) {
                if (text[i] == '{' && text[i + 1] == '{') { depth++; i += 2; continue }
                if (text[i] == '}' && text[i + 1] == '}') { depth--; i += 2; if (depth == 0) { end = i; break }; continue }
                i++
            }
            if (end < 0) break
            out += text.substring(m.range.last + 1, end - 2)
            from = end
        }
        return out
    }

    /** Parametri "nome=valore" separati da "|" di primo livello (non dentro [[...]] o {{...}}). */
    private fun params(body: String): Map<String, String> {
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        var depth = 0
        var i = 0
        while (i < body.length) {
            val two = if (i + 1 < body.length) body.substring(i, i + 2) else ""
            when {
                two == "{{" || two == "[[" -> { depth++; sb.append(two); i += 2; continue }
                two == "}}" || two == "]]" -> { depth--; sb.append(two); i += 2; continue }
                body[i] == '|' && depth == 0 -> { parts += sb.toString(); sb.clear() }
                else -> sb.append(body[i])
            }
            i++
        }
        parts += sb.toString()
        return parts.mapNotNull { p ->
            val eq = p.indexOf('=')
            if (eq <= 0) null else p.substring(0, eq).trim().lowercase() to p.substring(eq + 1).trim()
        }.toMap()
    }

    private fun team(raw: String): IntlTeam? {
        val clean = refRegex.replace(raw, "").replace("'''", "")
        val link = linkRegex.find(clean)
        val country = flagRegex.find(clean)?.groupValues?.get(1)?.trim()?.let { WikipediaCompetitionStats.countryName(it) }
        val name = link?.let { (it.groupValues[2].ifBlank { it.groupValues[1] }).trim() }
            ?: templateRegex.replace(clean, "").trim().ifBlank { return null }
        return IntlTeam(name, link?.groupValues?.get(1)?.trim()?.replace('_', ' '), country)
    }

    private fun plain(raw: String): String {
        var s = refRegex.replace(raw, "")
        s = linkRegex.replace(s) { (it.groupValues[2].ifBlank { it.groupValues[1] }) }
        repeat(3) { s = templateRegex.replace(s, "") }
        return s.replace("'''", "").replace(Regex("""\s+"""), " ").trim().trim(',').trim()
    }

    private fun parseMatch(body: String): IntlMatch? {
        val p = params(body)
        val a = team(p["teama"] ?: return null) ?: return null
        val b = team(p["teamb"] ?: return null) ?: return null
        val sa = p["scorea"]?.let { numberRegex.find(it.replace("'''", ""))?.value?.toIntOrNull() } ?: return null
        val sb = p["scoreb"]?.let { numberRegex.find(it.replace("'''", ""))?.value?.toIntOrNull() } ?: return null
        val date = plain(p["date"].orEmpty())
        // La squadra NBA è di norma teamA; per sicurezza si guarda la bandiera (USA/Canada).
        val aIsNba = a.country in nbaCountries || b.country !in nbaCountries
        return IntlMatch(
            date = date,
            year = yearRegex.find(date)?.value?.toIntOrNull(),
            place = plain(p["place"].orEmpty()),
            nba = if (aIsNba) a else b,
            intl = if (aIsNba) b else a,
            nbaScore = if (aIsNba) sa else sb,
            intlScore = if (aIsNba) sb else sa,
            overtime = !p["overtime"].isNullOrBlank(),
        )
    }

    /** Testo della sezione "===[title]===" fino alla sezione successiva. */
    private fun section(text: String, title: String): String? {
        val m = Regex("""^=+\s*${Regex.escape(title)}\s*=+\s*$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)).find(text) ?: return null
        val rest = text.substring(m.range.last + 1)
        val next = Regex("""^=+[^=].*=+\s*$""", RegexOption.MULTILINE).find(rest)
        return if (next != null) rest.substring(0, next.range.first) else rest
    }

    private fun parseTable(section: String, isNba: Boolean): List<IntlTeamRecord> =
        section.lines().mapNotNull { line ->
            val m = rowRegex.find(line.trim()) ?: return@mapNotNull null
            val t = team(m.groupValues[1]) ?: return@mapNotNull null
            IntlTeamRecord(
                team = t,
                isNba = isNba,
                games = m.groupValues[2].toInt(),
                wins = m.groupValues[3].toInt(),
                losses = m.groupValues[4].toInt(),
                logoUrl = null,
                canonicalTitle = null,
            )
        }

    private fun recordsFromMatches(matches: List<IntlMatch>): List<IntlTeamRecord> {
        fun build(isNba: Boolean) = matches.groupBy { (if (isNba) it.nba else it.intl).let { t -> t.wikiTitle ?: t.name } }
            .map { (_, ms) ->
                val wins = ms.count { it.nbaWon == isNba }
                IntlTeamRecord(if (isNba) ms.last().nba else ms.last().intl, isNba, ms.size, wins, ms.size - wins, null, null)
            }
        return build(true) + build(false)
    }
}
