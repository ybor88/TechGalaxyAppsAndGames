// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.athlete

import com.scouttable.app.data.Sport

/**
 * Completa gli sport da combattimento con la voce en.wikipedia dello stesso atleta, quando quella
 * italiana non basta:
 * - sumo: "yushos = 45 (Makuuchi)<br />1 (Jūryō)" -> 45 tornei vinti nella massima divisione,
 *   "record = 1187–247–253" (vittorie–sconfitte–assenze), "highest_rank = Yokozuna (May 2007)";
 * - kickboxing: kickbox_win / kickbox_ko_win / kickbox_loss / kickbox_ko_loss / kickbox_draw;
 * - MMA: mma_win / mma_kowin / mma_loss / mma_koloss / mma_draw;
 * - pugilato: wins / KO / losses / draws;
 * - titoli: righe della sezione "Championships and accomplishments" (o "Titles") che indicano un
 *   titolo vinto ("... Champion", "... Championship"), senza secondi posti né difese del titolo.
 * Il record si prende solo se l'italiana non lo riporta; i titoli solo se l'italiana ne ha 0.
 */
object EnglishFightStats {

    fun enrich(sport: Sport, data: AthleteData, enWikitext: String): AthleteData {
        val text = enWikitext.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
        val infobox = WikiTemplates.findPrefixed(text, "Infobox").firstOrNull()?.let(WikiTemplates::params).orEmpty()
        var result = data
        if (data.incontri == 0) {
            record(sport, infobox)?.let { r ->
                result = result.copy(incontri = r.incontri, vinti = r.vinti, vintiKo = r.ko, persi = r.persi, persiKo = r.koPersi, pareggi = r.pareggi)
            }
        }
        if (sport == Sport.SUMO) {
            val yusho = makuuchiYusho(infobox["yushos"] ?: infobox["yusho"] ?: infobox["championships"])
            if (yusho != null && result.titoli == 0) result = result.copy(titoli = yusho)
            highestRank(infobox["highest_rank"] ?: infobox["highestrank"])?.let { result = result.copy(team = it) }
        } else if (result.titoli == 0) {
            val titles = countTitles(text)
            if (titles > 0) result = result.copy(titoli = titles)
        }
        return result
    }

    private data class Record(val incontri: Int, val vinti: Int, val ko: Int, val persi: Int, val koPersi: Int, val pareggi: Int)

    private fun record(sport: Sport, p: Map<String, String>): Record? {
        fun n(vararg keys: String): Int = keys.firstNotNullOfOrNull { k -> p[k]?.takeIf { it.isNotBlank() } }?.let(WikiTemplates::firstNumber) ?: 0
        return when (sport) {
            Sport.SUMO -> {
                val raw = WikiTemplates.clean(p["record"].orEmpty())
                val m = Regex("""(\d+)\s*[–\-]\s*(\d+)""").find(raw) ?: return null
                val v = m.groupValues[1].toInt()
                val s = m.groupValues[2].toInt()
                Record(v + s, v, 0, s, 0, 0)
            }
            Sport.KICKBOXING -> fromCounts(n("kickbox_win"), n("kickbox_ko_win", "kickbox_kowin"), n("kickbox_loss"), n("kickbox_ko_loss", "kickbox_koloss"), n("kickbox_draw"))
            Sport.MMA -> fromCounts(n("mma_win"), n("mma_kowin", "mma_ko_win"), n("mma_loss"), n("mma_koloss", "mma_ko_loss"), n("mma_draw"))
            Sport.PUGILATO -> fromCounts(n("wins"), n("ko"), n("losses"), 0, n("draws"))
            else -> null
        }
    }

    private fun fromCounts(v: Int, ko: Int, s: Int, koS: Int, p: Int): Record? =
        if (v + s + p == 0) null else Record(v + s + p, v, ko, s, koS, p)

    /** "45 (Makuuchi), 1 (Jūryō)" -> 45; senza divisione indicata il primo numero. */
    internal fun makuuchiYusho(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val text = WikiTemplates.clean(raw)
        Regex("""(\d+)\s*\(\s*makuuchi""", RegexOption.IGNORE_CASE).find(text)?.let { return it.groupValues[1].toInt() }
        return Regex("""\d+""").find(text)?.value?.toIntOrNull()
    }

    /** "Yokozuna (May 2007)" -> "Yokozuna". */
    private fun highestRank(raw: String?): String? =
        raw?.let { WikiTemplates.clean(it).substringBefore('(').trim() }?.ifBlank { null }?.replaceFirstChar { it.uppercase() }

    private val titlesSection = Regex(
        """\n==\s*(Championships and accomplishments|Titles and accomplishments|Championships|Titles)\s*==\s*\n(.*?)(?=\n==[^=]|$)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val wonRegex = Regex("""champion""", RegexOption.IGNORE_CASE)
    private val notWonRegex = Regex("""runner|finalist|hall of fame|defen|nominat|contender|semi|record|fight of the|performance of""", RegexOption.IGNORE_CASE)
    private val orgOnlyRegex = Regex("""^\*+\s*'''.*'''\s*$""")

    /** Titoli vinti nella sezione dei titoli: una riga elenco per titolo (le sotto-righe "***" sono difese). */
    internal fun countTitles(text: String): Int {
        val section = titlesSection.find(text)?.groupValues?.get(2) ?: return 0
        return section.lines().map { it.trim() }.count { line ->
            line.startsWith("*") && !line.startsWith("***") && !orgOnlyRegex.matches(line) &&
                wonRegex.containsMatchIn(line) && !notWonRegex.containsMatchIn(line)
        }
    }
}
