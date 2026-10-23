// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.lookup

import com.scouttable.app.data.PlayerStatus
import com.scouttable.app.data.Sport
import com.scouttable.app.data.importexport.PlayerImportRow
import kotlin.math.roundToInt
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Dati di un giocatore delle leghe minori letti da playbasket.it (vedi [PlaybasketStats]). */
data class PlaybasketTotals(
    val nome: String,
    val annoNascita: Int?,
    /** Ruolo già in italiano, con l'iniziale maiuscola ("Centro", "Ala", "Playmaker"...). */
    val ruolo: String,
    /** Squadra con più presenze tra tutte le stagioni registrate (es. "Basket Benevento 2017"). */
    val squadraPrincipale: String?,
    /** Categoria della squadra principale, con la regione se è quella della stagione aperta. */
    val competizione: String,
    val presenze: Int,
    val punti: Int,
    val rimbalzi: Int,
    val assist: Int,
    val palleRecuperate: Int,
    val minuti: Int,
    val logoUrl: String?,
    val ultimaStagione: Int?,
)

/**
 * Fonte del "Basket regionale": la scheda giocatore di playbasket.it (es.
 * https://www.playbasket.it/campania/profile.php?season=2027&subj=1&exteid=4&extobj=14632&obj=204567&action=view&eid=5).
 * Struttura verificata sulla pagina di Lucja Grzenkowicz (Serie B Femminile Campania 2026-27):
 * - "div.info_giocatore": nell'h1 "span.season_name" (campionato della stagione) e
 *   "span.player_name"; in "span.player_info" l'età nella stagione ("Anni 26") e il ruolo ("centro");
 * - "div.profile_stats": punti, media, presenze e miglior prestazione della stagione aperta;
 * - "div#club_logo": stemma della società nello stile in linea (background-image);
 * - tabella "tabella_partita": partite della stagione con riga "Totale" (rimbalzi/assist/recuperi
 *   spesso non compilati, "-");
 * - la stessa pagina con "mod=ws" elenca tutte le stagioni del giocatore su playbasket
 *   (Squadra/Categoria/PTS/PRES): da lì si sommano i totali di carriera.
 * La nazionalità non è riportata: resta vuota (modificabile a mano).
 */
object PlaybasketStats {

    private val ageRegex = Regex("""Anni\s+(\d{1,2})""")
    private val seasonParamRegex = Regex("""[?&]season=(\d{4})""")
    private val bgUrlRegex = Regex("""url\(\s*['"]?([^'")]+)['"]?\s*\)""")

    fun isPlaybasketUrl(url: String?): Boolean = url?.contains("playbasket.it", ignoreCase = true) == true

    /** Scheda della stessa stagione con l'elenco di tutte le stagioni del giocatore ("mod=ws"). */
    internal fun allSeasonsUrl(profileUrl: String): String {
        val base = profileUrl.trim().replace(Regex("""[&?]mod=\w+"""), "")
        return base + (if (base.contains('?')) "&" else "?") + "mod=ws"
    }

    suspend fun fetch(profileUrl: String): PlaybasketTotals? = runCatching {
        val html = fetchHtmlWithFallback(profileUrl.trim()) ?: return@runCatching null
        val seasonsHtml = fetchHtmlWithFallback(allSeasonsUrl(profileUrl))
        parse(html, seasonsHtml, profileUrl.trim())
    }.getOrNull()

    /** Separata dalla rete per i test: [seasonsHtml] è la pagina "mod=ws" (null se non scaricata). */
    internal fun parse(html: String, seasonsHtml: String?, profileUrl: String): PlaybasketTotals? {
        val doc = Jsoup.parse(html, profileUrl)
        val info = doc.selectFirst("div.info_giocatore") ?: return null
        val nome = info.selectFirst("span.player_name")?.text()?.trim()?.replace(Regex("""\s+"""), " ")
            ?.ifBlank { null } ?: return null
        val seasonName = info.selectFirst("span.season_name")?.text()?.trim().orEmpty()
        val seasonEnd = seasonEndYear(seasonName) ?: seasonParamRegex.find(profileUrl)?.groupValues?.get(1)?.toIntOrNull()
        val spans = info.select("span.player_info > span").map { it.text().trim() }
        val age = spans.firstNotNullOfOrNull { ageRegex.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        // Età "nella stagione": la stagione 2026-27 inizia nel 2026.
        val annoNascita = if (age != null && seasonEnd != null) seasonEnd - 1 - age else null
        val ruolo = spans.firstOrNull { title ->
            !ageRegex.containsMatchIn(title) && !title.contains("stagion", ignoreCase = true) && title.isNotBlank()
        }?.let { translateRole(it.replaceFirstChar { c -> c.uppercase() }, Sport.BASKET_REGIONALE) }.orEmpty()

        val stats = doc.select("div.profile_stats > div").associate { div ->
            val p = div.select("p")
            p.getOrNull(1)?.text()?.trim()?.lowercase().orEmpty() to p.getOrNull(0)?.text()?.trim().orEmpty()
        }
        val seasonPoints = stats["punti"]?.toIntOrNull() ?: 0
        val seasonGames = stats["games"]?.toIntOrNull() ?: 0

        val logoUrl = doc.selectFirst("div#club_logo")?.attr("style")?.let { bgUrlRegex.find(it)?.groupValues?.get(1) }
            ?.let { raw -> runCatching { java.net.URL(java.net.URL(profileUrl), raw).toString() }.getOrDefault(raw) }
        val currentClub = doc.selectFirst("div#club_links h2")?.text()?.trim()
        val currentCategory = seasonName.replace(Regex("""\s*\d{4}\s*[-/]\s*\d{2,4}\s*$"""), "").trim()

        val match = matchTotals(doc)
        val seasons = seasonsHtml?.let { parseSeasons(Jsoup.parse(it, profileUrl)) }.orEmpty()

        // Totali di carriera dalle stagioni; senza quella pagina, solo la stagione aperta.
        val presenze = if (seasons.isNotEmpty()) seasons.sumOf { it.games } else seasonGames
        val punti = if (seasons.isNotEmpty()) seasons.sumOf { it.points } else seasonPoints
        val teamGames = seasons.groupBy { it.team }.mapValues { (_, rows) -> rows.sumOf { it.games } }
        val topTeam = teamGames.maxByOrNull { it.value }?.key
        val topCategory = seasons.filter { it.team == topTeam }.groupBy { it.category }
            .maxByOrNull { (_, rows) -> rows.sumOf { it.games } }?.key
        // La squadra della scheda aperta ha nome breve nell'elenco stagioni ("Basket Benevento
        // 2017") e completo nell'intestazione ("A.S.D. Basket Benevento 2017").
        val topIsCurrent = topTeam == null || currentClub?.let { normalizeForMatch(it).contains(normalizeForMatch(topTeam)) } == true
        val squadra = topTeam ?: currentClub?.ifBlank { null }
        val competizione = if (topIsCurrent) currentCategory.ifBlank { topCategory.orEmpty() } else topCategory.orEmpty()
        val ultimaStagione = seasons.mapNotNull { it.seasonEnd }.maxOrNull() ?: seasonEnd

        if (presenze == 0 && punti == 0 && squadra == null) return null
        return PlaybasketTotals(
            nome = nome,
            annoNascita = annoNascita,
            ruolo = ruolo,
            squadraPrincipale = squadra,
            competizione = competizione,
            presenze = presenze,
            punti = punti,
            rimbalzi = match.rebounds,
            assist = match.assists,
            palleRecuperate = match.steals,
            minuti = match.minutes,
            logoUrl = logoUrl.takeIf { topIsCurrent },
            ultimaStagione = ultimaStagione,
        )
    }

    private data class SeasonRow(val seasonEnd: Int?, val team: String, val category: String, val points: Int, val games: Int)

    /** Righe della tabella "playerCV" (AS / Squadra / Categoria / PTS / PRES / AVG / BEST). */
    private fun parseSeasons(doc: Document): List<SeasonRow> {
        val table = doc.selectFirst("table.playerCV") ?: return emptyList()
        val headers = table.select("thead tr").first()?.select("th")?.map { it.text().trim().uppercase() }.orEmpty()
        val teamIdx = headers.indexOfFirst { it.startsWith("SQUADRA") }
        val catIdx = headers.indexOfFirst { it.startsWith("CATEGORIA") }
        return table.select("tbody tr").mapNotNull { tr ->
            val cells = tr.select("td")
            val season = cells.getOrNull(0)?.let { it.attr("title").ifBlank { it.text() } }.orEmpty()
            val points = tr.selectFirst("td[alt=pts]")?.text()?.trim()?.toIntOrNull() ?: 0
            val games = tr.selectFirst("td[alt=pres]")?.text()?.trim()?.toIntOrNull() ?: 0
            SeasonRow(
                seasonEnd = seasonEndYear(season),
                team = cells.getOrNull(teamIdx)?.text()?.trim().orEmpty().takeIf { teamIdx >= 0 }.orEmpty(),
                category = cells.getOrNull(catIdx)?.text()?.trim().orEmpty().takeIf { catIdx >= 0 }.orEmpty(),
                points = points,
                games = games,
            )
        }.filter { it.games > 0 || it.points > 0 }
    }

    private data class MatchTotals(val rebounds: Int = 0, val assists: Int = 0, val steals: Int = 0, val minutes: Int = 0)

    /** Riga "Totale" della tabella partite: rimbalzi (RD+RO), assist, recuperi, minuti, se compilati. */
    private fun matchTotals(doc: Document): MatchTotals {
        val table = doc.select("table").firstOrNull { t ->
            t.select("th").any { it.text().trim() == "Pts" } && t.select("th").any { it.text().trim() == "Ass" }
        } ?: return MatchTotals()
        val headers = table.select("tr").first()?.select("th,td")?.map { it.text().trim() }.orEmpty()
        val total = table.select("tr").lastOrNull { it.text().trim().startsWith("Totale") } ?: return MatchTotals()
        val cells = total.select("th,td").map { it.text().trim() }
        fun col(name: String): Int = headers.indexOf(name).takeIf { it >= 0 }?.let { cells.getOrNull(it) }
            ?.replace(',', '.')?.toDoubleOrNull()?.roundToInt() ?: 0
        return MatchTotals(
            rebounds = col("RD") + col("RO"),
            assists = col("Ass"),
            steals = col("PR"),
            minutes = col("Min"),
        )
    }

    /** Riga pronta per la lista del "Basket regionale". */
    fun toImportRow(t: PlaybasketTotals, existingId: String?, url: String): PlayerImportRow {
        // Nessuna valutazione ufficiale nelle leghe minori: come Eff medio si usa la somma delle
        // voci disponibili a partita (punti, e rimbalzi/assist/recuperi quando compilati), la
        // stessa base della valutazione di Proballers, così classifica e andamento funzionano.
        val eff = if (t.presenze > 0) ((t.punti + t.rimbalzi + t.assist + t.palleRecuperate).toDouble() / t.presenze).roundToInt() else 0
        return PlayerImportRow(
            id = existingId,
            nome = t.nome,
            anno = t.annoNascita ?: 0,
            carrieraMigliore = t.squadraPrincipale.orEmpty(),
            stato = PlayerStatusFromSeasons.fromLastSeason(t.ultimaStagione) ?: PlayerStatus.ATTIVO,
            nazione = "",
            logoPath = t.logoUrl,
            ruolo = t.ruolo,
            presenze = t.presenze,
            punteggio = t.punti,
            assist = t.assist,
            competizione = t.competizione,
            rimbalzi = t.rimbalzi,
            palleRecuperate = t.palleRecuperate,
            proballersUrl = url,
            effMedio = eff,
            minutiCarriera = t.minuti,
        )
    }
}
