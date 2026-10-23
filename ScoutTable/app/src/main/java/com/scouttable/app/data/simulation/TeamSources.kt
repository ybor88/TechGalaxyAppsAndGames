// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.simulation

import com.scouttable.app.data.lookup.fetchHtmlWithFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Pagine squadra accettate da "Simula partita":
 * - basket: Proballers (es. https://www.proballers.com/it/pallacanestro/squadra/101/boston-celtics,
 *   anche nelle altre lingue) e Basketball-Reference (es. https://www.basketball-reference.com/teams/CHI/2025.html);
 * - calcio: Transfermarkt (es. https://www.transfermarkt.it/atalanta-bergamo/startseite/verein/800, qualsiasi
 *   pagina della squadra) e FBref (es. https://fbref.com/en/squads/922493f3/Atalanta-Stats). FBref è dietro una
 *   verifica Cloudflare che spesso non si supera dall'app: Transfermarkt è la fonte consigliata.
 */
object TeamSources {

    private val proballersTeamRegex = Regex("""proballers\.com/(?:[a-z]{2}/)?[^/]+/[^/]+/(\d+)/[^/?#\s]+""", RegexOption.IGNORE_CASE)
    private val brefTeamRegex = Regex("""basketball-reference\.com/teams/[A-Z]{2,4}/\d{4}""", RegexOption.IGNORE_CASE)
    private val fbrefSquadRegex = Regex("""fbref\.com/(?:[a-z]{2}/)?squads/[0-9a-f]{8}""", RegexOption.IGNORE_CASE)
    private val transfermarktRegex = Regex("""(transfermarkt\.[a-z.]+)/([^/?#\s]+)/[a-z_]+/verein/(\d+)""", RegexOption.IGNORE_CASE)
    private val tmSeasonRegex = Regex("""(?:saison_id/|reldata/%26|reldata/&)(\d{4})""")
    // Anche i decimali senza zero iniziale di Basketball-Reference (".446").
    private val numberRegex = Regex("""-?(?:\d+(?:[.,]\d+)?|[.,]\d+)""")

    fun isBasketUrl(url: String) = proballersTeamRegex.containsMatchIn(url) || brefTeamRegex.containsMatchIn(url)
    fun isSoccerUrl(url: String) = fbrefSquadRegex.containsMatchIn(url) || transfermarktRegex.containsMatchIn(url)
    fun isFbrefUrl(url: String) = fbrefSquadRegex.containsMatchIn(url)

    /** Qualsiasi pagina squadra di Transfermarkt -> "rendimento rosa" in vista dettagliata (con gli assist). */
    internal fun transfermarktStatsUrl(url: String): String? {
        val m = transfermarktRegex.find(url) ?: return null
        val (host, slug, id) = m.destructured
        val season = tmSeasonRegex.find(url)?.groupValues?.get(1)?.let { "/reldata/%26$it" }.orEmpty()
        return "https://www.${host.removePrefix("www.")}/$slug/leistungsdaten/verein/$id$season/plus/1"
    }

    /**
     * Scarica e legge la squadra; null se il link non è riconosciuto o la pagina non ha giocatori.
     * Un link FBref viene letto da Transfermarkt: FBref è dietro una verifica Cloudflare che
     * dall'app non si completa mai (la pagina resta su "verifica in corso"). Il nome della squadra
     * si ricava dal link FBref (".../Atalanta-Stats" -> "Atalanta") e si cerca su Transfermarkt
     * (sito italiano, poi internazionale); se non si trova, null (FBref non viene mai aperto).
     */
    suspend fun fetch(url: String): SimTeam? {
        if (isFbrefUrl(url)) {
            val tm = fbrefTeamName(url)?.let { name -> transfermarktSearch(name) } ?: return null
            return fetchDirect(tm)
        }
        return fetchDirect(url)
    }

    /** ".../squads/922493f3/Atalanta-Stats" -> "Atalanta"; ".../Internazionale-Stats" -> "Internazionale". */
    internal fun fbrefTeamName(url: String): String? =
        url.trim().trimEnd('/').substringAfterLast('/').substringBefore('?')
            .replace(Regex("""-Stats$""", RegexOption.IGNORE_CASE), "").replace('-', ' ').trim()
            .takeIf { it.isNotBlank() && !Regex("""^[0-9a-f]{8}$""").matches(it) }

    /** Primo club trovato dalla ricerca di Transfermarkt per [name] (pagina squadra), o null. */
    private suspend fun transfermarktSearch(name: String): String? {
        for (host in listOf("www.transfermarkt.it", "www.transfermarkt.com")) {
            val found = runCatching {
                val searchUrl = "https://$host/schnellsuche/ergebnis/schnellsuche?query=" + java.net.URLEncoder.encode(name, "UTF-8")
                val html = fetchHtmlWithFallback(searchUrl) ?: return@runCatching null
                withContext(Dispatchers.Default) { parseTransfermarktSearch(html, "https://$host/", name) }
            }.getOrNull()
            if (found != null) return found
        }
        return null
    }

    /**
     * Primo risultato della sezione "Risultati società" (div#club-grid). Prima si prendeva il primo
     * link a un club in tutta la pagina, ma la sezione giocatori viene prima e linka il club di
     * ogni giocatore: per "Juventus" il primo era "svincolato" (verein/515, i senza squadra).
     */
    internal fun parseTransfermarktSearch(html: String, baseUrl: String = "https://www.transfermarkt.it/", query: String? = null): String? {
        val doc = Jsoup.parse(html, baseUrl)
        val clubBox = doc.selectFirst("div#club-grid")
            ?: doc.select("div.box").firstOrNull { box -> box.selectFirst("h2")?.text()?.contains(Regex("societ|club", RegexOption.IGNORE_CASE)) == true }
            ?: return null
        val clubs = clubBox.select("a[href*=/startseite/verein/][title]")
            .toList().filter { it.attr("title").isNotBlank() && !it.attr("href").contains("/verein/515") }
        // Transfermarkt non mette sempre per primo il club cercato: per "Milan" il primo è l'Inter
        // ("Inter Milan"). Tra i primi 3 risultati (oltre si trovano omonimi minori, es. "FC Stockholm
        // Internazionale") si preferisce il primo il cui nome contiene tutte le parole cercate, escluse
        // giovanili e seconde squadre; se nessuno corrisponde (es. "Internazionale" -> "Inter") resta
        // il primo risultato.
        val words = query?.let(::searchWords).orEmpty()
        val best = if (words.isEmpty()) null else clubs.take(3).firstOrNull { a ->
            val title = searchWords(a.attr("title"))
            title.containsAll(words) && (title.none { it in youthWords } || words.any { it in youthWords })
        }
        return (best ?: clubs.firstOrNull())?.attr("abs:href")?.ifBlank { null }
    }

    private val youthWords = setOf("u17", "u18", "u19", "u20", "u21", "u23", "primavera", "futuro", "next", "gen", "ii", "b", "youth", "juniores", "giovanili", "reserves", "women", "femminile")

    private fun searchWords(text: String): List<String> =
        java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "")
            .lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }

    private suspend fun fetchDirect(url: String): SimTeam? = runCatching {
        val clean = transfermarktStatsUrl(url.trim()) ?: url.trim()
        val html = fetchHtmlWithFallback(clean) ?: return@runCatching null
        withContext(Dispatchers.Default) { parse(clean, html) }
    }.getOrNull()

    /** Separata dalla rete per i test. */
    internal fun parse(url: String, html: String): SimTeam? = when {
        proballersTeamRegex.containsMatchIn(url) -> parseProballers(url, html)
        brefTeamRegex.containsMatchIn(url) -> parseBref(url, Jsoup.parse(html.replace("<!--", "").replace("-->", ""), url))
        fbrefSquadRegex.containsMatchIn(url) -> parseFbref(url, Jsoup.parse(html.replace("<!--", "").replace("-->", ""), url))
        transfermarktRegex.containsMatchIn(url) -> parseTransfermarkt(url, Jsoup.parse(html, url))
        else -> null
    }?.takeIf { it.players.isNotEmpty() }

    private fun num(text: String?): Double? =
        text?.replace("%", "")?.trim()?.let { numberRegex.find(it)?.value }?.replace(",", ".")?.toDoubleOrNull()

    // ------------------------------------------------------------------ Proballers

    /**
     * Tabella "Medie" della squadra (la prima "select-tab-average-*", campionato principale).
     * Colonne verificate sulla pagina dei Boston Celtics, uguali in tutte le lingue:
     * Giocatore, Altezza, Età, Pun, Reb, Ast, P(artite), V-P, Min, FG%, 3P%, FT%, Ro, Reb, Ast,
     * Pr (recuperi), Pp (perse), Sto (stoppate), Fa, Pun, Val. Si leggono quindi per posizione.
     */
    private fun parseProballers(url: String, html: String): SimTeam {
        val doc = Jsoup.parse(html, url)
        val id = proballersTeamRegex.find(url)!!.groupValues[1]
        val name = doc.selectFirst("h1 .lastname")?.text()?.trim()?.ifBlank { null }
            ?: doc.selectFirst("h1")?.text()?.trim().orEmpty()
        val table = doc.select("div[class*=select-tab-average] table").firstOrNull { it.select("tbody tr").isNotEmpty() }
        val players = table?.select("tbody tr")?.mapNotNull { tr ->
            val cells = tr.select("> td")
            if (cells.size < 21) return@mapNotNull null
            val playerName = cells[0].text().trim().ifBlank { return@mapNotNull null }
            fun c(i: Int) = num(cells.getOrNull(i)?.text())
            SimPlayer(
                name = playerName,
                games = c(6)?.toInt() ?: 0,
                minutes = c(8) ?: 0.0,
                points = c(3) ?: 0.0,
                rebounds = c(4) ?: 0.0,
                assists = c(5) ?: 0.0,
                threePct = (c(10) ?: 0.0) / 100.0,
                steals = c(15) ?: 0.0,
                turnovers = c(16) ?: 0.0,
                blocks = c(17) ?: 0.0,
                eff = c(20),
            )
        }.orEmpty()
        return SimTeam(name.ifBlank { "Squadra $id" }, "https://www.proballers.com/media/team/$id.svg", url, players)
    }

    // ------------------------------------------------------------------ Basketball-Reference

    /** Tabella "per_game_stats" (celle riconosciute da data-stat); nome nell'h1, stemma "img.teamlogo". */
    private fun parseBref(url: String, doc: Document): SimTeam {
        val spans = doc.select("h1 span").map { it.text().trim() }.filter { it.isNotBlank() }
        val name = spans.getOrNull(1) ?: doc.title().substringBefore(" Roster").trim()
        val logo = doc.selectFirst("img.teamlogo")?.attr("abs:src")?.ifBlank { null }
        val rows = doc.select("table#per_game_stats tbody tr").toList().filter { !it.hasClass("thead") }
        val players = rows.mapNotNull { tr ->
            val playerName = tr.statText("name_display") ?: tr.statText("player") ?: return@mapNotNull null
            if (playerName.equals("Team Totals", ignoreCase = true)) return@mapNotNull null
            SimPlayer(
                name = playerName,
                position = tr.statText("pos").orEmpty(),
                games = num(tr.statText("games"))?.toInt() ?: num(tr.statText("g"))?.toInt() ?: 0,
                minutes = num(tr.statText("mp_per_g")) ?: 0.0,
                points = num(tr.statText("pts_per_g")) ?: 0.0,
                rebounds = num(tr.statText("trb_per_g")) ?: 0.0,
                assists = num(tr.statText("ast_per_g")) ?: 0.0,
                steals = num(tr.statText("stl_per_g")) ?: 0.0,
                blocks = num(tr.statText("blk_per_g")) ?: 0.0,
                turnovers = num(tr.statText("tov_per_g")) ?: 0.0,
                threePct = num(tr.statText("fg3_pct")) ?: 0.0,
            )
        }
        return SimTeam(name, logo, url, players)
    }

    // ------------------------------------------------------------------ FBref

    /**
     * Tabella "stats_standard_*" della squadra (una riga per giocatore, celle riconosciute da
     * data-stat: player/position/games/minutes/goals/assists) e "stats_keeper_*" per i gol
     * subiti ogni 90' dei portieri. Nome nell'h1 ("2025-2026 Atalanta Stats, Serie A"), stemma
     * "img.teamlogo". Alcune tabelle arrivano dentro commenti HTML, già aperti prima del parsing.
     */
    private fun parseFbref(url: String, doc: Document): SimTeam {
        val h1 = doc.selectFirst("div#meta h1")?.text() ?: doc.selectFirst("h1")?.text().orEmpty()
        val name = h1.replace(Regex("""^\s*\d{4}(?:-\d{4})?\s+"""), "").substringBefore(" Stats").trim()
            .ifBlank { url.substringAfterLast('/').substringBefore("-Stats").replace('-', ' ') }
        val logo = doc.selectFirst("img.teamlogo")?.attr("abs:src")?.ifBlank { null }
        val standard = doc.select("table[id^=stats_standard]").firstOrNull { it.select("tbody tr").isNotEmpty() }
        val keepers = doc.select("table[id^=stats_keeper]").firstOrNull { it.select("tbody tr").isNotEmpty() }
        val gaPer90 = keepers?.select("tbody tr")?.mapNotNull { tr ->
            val n = tr.statText("player") ?: return@mapNotNull null
            num(tr.statText("gk_goals_against_per90"))?.let { n to it }
        }?.toMap().orEmpty()
        val players = standard?.select("tbody tr")?.toList()?.filter { !it.hasClass("thead") && !it.hasClass("spacer") }?.mapNotNull { tr ->
            val playerName = tr.statText("player") ?: return@mapNotNull null
            SimPlayer(
                name = playerName,
                position = tr.statText("position").orEmpty(),
                games = num(tr.statText("games")?.replace(",", ""))?.toInt() ?: 0,
                minutesTotal = num(tr.statText("minutes")?.replace(",", ""))?.toInt() ?: 0,
                goals = num(tr.statText("goals"))?.toInt() ?: 0,
                assistsTotal = num(tr.statText("assists"))?.toInt() ?: 0,
                goalsAgainstPer90 = gaPer90[playerName],
            )
        }.orEmpty()
        return SimTeam(name, logo, url, players)
    }

    // ------------------------------------------------------------------ Transfermarkt

    // Ruolo di Transfermarkt (italiano o inglese) -> sigla usata dal simulatore.
    private fun tmPosition(raw: String): String {
        val r = raw.lowercase()
        return when {
            "portiere" in r || "goalkeeper" in r -> "GK"
            "difensore" in r || "terzino" in r || "back" in r || "defender" in r -> "DF"
            "centrocampista" in r || "mediano" in r || "trequartista" in r || "midfield" in r -> "MF"
            else -> "FW"
        }
    }

    /**
     * Pagina "rendimento rosa" in vista dettagliata (".../leistungsdaten/verein/800/plus/1"): tabella
     * "items", una riga per giocatore. Colonne verificate sulla pagina dell'Atalanta: #, Giocatori
     * (nome e ruolo in una tabella interna), Età, Naz, Convocato, Presenze, Gol, Assist, Ammonizioni,
     * Doppie ammonizioni, Espulsioni, Entrate, Sostituzioni, PaP, Minuti giocati ("1.234'").
     * Le righe "Non utilizzato in questa stagione" vengono scartate. Nessun dato sui gol subiti.
     */
    private fun parseTransfermarkt(url: String, doc: Document): SimTeam {
        val id = transfermarktRegex.find(url)!!.groupValues[3]
        val name = doc.selectFirst("h1")?.text()?.trim()?.ifBlank { null }
            ?: doc.title().substringBefore(" - ").trim()
        val logo = doc.select("img[src*=wappen/head/$id]").firstOrNull()?.attr("abs:src")
            ?: "https://img.a.transfermarkt.technology/wappen/head/$id.png"
        val table = doc.selectFirst("table.items")
        val headers = table?.select("thead th")?.map { th -> th.selectFirst("[title]")?.attr("title")?.ifBlank { null } ?: th.text().trim() }.orEmpty()
        fun col(vararg names: String) = headers.indexOfFirst { h -> names.any { h.equals(it, ignoreCase = true) } }
        val gamesIdx = col("Presenze", "Appearances")
        val goalsIdx = col("Gol", "Goals")
        val assistsIdx = col("Assist", "Assists")
        val minutesIdx = col("Minuti giocati", "Minutes played")
        fun digits(text: String?) = text?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        val players = table?.select("> tbody > tr")?.mapNotNull { tr ->
            val cells = tr.select("> td")
            if (cells.size < headers.size || gamesIdx < 0) return@mapNotNull null
            val games = digits(cells.getOrNull(gamesIdx)?.text())
            if (games == 0) return@mapNotNull null
            val playerCell = cells.getOrNull(1) ?: return@mapNotNull null
            val playerName = playerCell.selectFirst("td.hauptlink a")?.text()?.trim()?.ifBlank { null } ?: return@mapNotNull null
            val role = playerCell.select("table.inline-table tr").getOrNull(1)?.text()?.trim().orEmpty()
            SimPlayer(
                name = playerName,
                position = tmPosition(role),
                games = games,
                minutesTotal = digits(cells.getOrNull(minutesIdx)?.text()),
                goals = digits(cells.getOrNull(goalsIdx)?.text()),
                assistsTotal = digits(cells.getOrNull(assistsIdx)?.text()),
            )
        }.orEmpty()
        return SimTeam(name, logo, url, players)
    }

    private fun Element.statText(stat: String): String? = selectFirst("[data-stat=$stat]")?.text()?.trim()?.ifBlank { null }
}
