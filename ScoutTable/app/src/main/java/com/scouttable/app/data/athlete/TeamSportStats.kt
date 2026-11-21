// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.athlete

import com.scouttable.app.data.Sport
import com.scouttable.app.data.lookup.WikipediaItCareerStats
import java.util.Calendar

/**
 * Statistiche di carriera dei giocatori di Football americano, Rugby e Hockey dalla voce
 * it.wikipedia.org (e, per il football, dall'infobox della voce inglese):
 *
 * - Rugby, {{Sportivo rugby a 15}}: il blocco "Squadre" ({{Carriera sportivo}}, righe
 *   "|2005-19|Stade Français|265 (238)" = presenze (punti)) e "Presenze e punti internazionali"
 *   ("142 (83)") della Nazionale maggiore;
 * - Hockey, {{Carriera hockeista su ghiaccio}}: righe "|1978-1988|{{Hockey su ghiaccio Edmonton|G}}|901|723|1371"
 *   = partite, gol, assist e i parametri "Nazionale_selezione_N" / "_giocate_N" / "_gol_N" / "_assist_N"
 *   della Nazionale maggiore ({{Naz|HG|CAN|M}}, non le giovanili {{NazU}});
 * - Football, {{Sportivo}}: le squadre con gli anni ("| 2000-2019 | New England Patriots |", da cui le
 *   stagioni giocate) e il {{Palmarès}} (Super Bowl, Pro Bowl, All-Pro, MVP); yard, touchdown, tackle,
 *   sack e intercetti da "stat_label/stat_value" dell'infobox inglese (it.wikipedia non li riporta).
 *
 * Per tutti e tre: ruolo da {{Sportivo}}, titoli dal {{Palmarès}}, squadra principale (più partite, o
 * più stagioni nel football) e anni di carriera.
 */
/**
 * Stagione (o periodo) migliore di un giocatore di Football, Rugby o Hockey:
 * - Football: la stagione NFL con più touchdown + intercetti + sack (tabella "Regular season" di en.wikipedia);
 * - Hockey: la stagione con più punti (gol + assist) nel campionato principale (tabella di en.wikipedia);
 * - Rugby: il periodo in un club con più punti per presenza (it.wikipedia non ha le singole stagioni).
 * [gol] = touchdown (football), punti (rugby) o gol (hockey); [extra] = intercetti + sack (football) o assist (hockey).
 */
data class BestSeason(
    val stagione: String,
    val team: String,
    val link: String? = null,
    val partite: Int = 0,
    val gol: Int = 0,
    val extra: Int = 0,
)

internal object TeamSportStats {

    /** Un periodo in una squadra. */
    data class Spell(
        val anni: String,
        val team: String,
        val partite: Int,
        val punti: Int,
        val assist: Int = 0,
        /** Voce it.wikipedia della squadra, se la riga la collega con [[Voce|Nome]]. */
        val link: String? = null,
    )

    fun parse(sport: Sport, text: String, s: Map<String, String>, common: AthleteData): WikipediaAthleteStats.Result {
        val ruolo = WikipediaItCareerStats.playingRole(s["ruolo"].orEmpty()).split(',').first().trim()
        // Hockey: tabella stagione per stagione della voce italiana (se c'è), per la stagione migliore
        // e per le partite quando l'infobox non le riporta.
        val itSeasons = if (sport == Sport.HOCKEY) italianHockeySeasons(text) else emptyList()
        val spells = when (sport) {
            Sport.HOCKEY -> hockeySpells(text).let { box ->
                if (box.sumOf { it.partite } == 0 && itSeasons.isNotEmpty()) hockeySpellsFromSeasons(itSeasons) else box
            }
            // Rugby: club, ma anche province e franchise (Dan Carter: Canterbury e Crusaders).
            else -> listOf("squadre", "squadreprovinciali", "franchise").flatMap { key ->
                s[key]?.let { WikiTemplates.find(it, "Carriera sportivo").firstOrNull() }?.let(::careerSpells).orEmpty()
            }
        }
        val years = spells.map { it.anni }
        val stagioni = careerRange(years)
        val retired = common.retired || closedBefore(years)
        val competizione = when (sport) {
            Sport.FOOTBALL -> "Football americano"
            Sport.RUGBY -> "Rugby a 15"
            else -> "Hockey su ghiaccio"
        }
        val main = mainTeam(spells, bySeasons = sport == Sport.FOOTBALL)
        val base = common.copy(
            ruolo = ruolo,
            retired = retired,
            team = main,
            teamLink = spells.firstOrNull { it.team == main && it.link != null }?.link,
            competizione = competizione,
            stagioni = stagioni,
        )
        val data = when (sport) {
            Sport.FOOTBALL -> {
                val palmares = footballPalmares(text)
                base.copy(
                    partite = years.sumOf(::seasonCount),
                    titoli = palmares.superBowl,
                    proBowl = palmares.proBowl,
                    allPro = palmares.allPro,
                    mvp = palmares.mvp,
                )
            }
            Sport.RUGBY -> {
                val (caps, puntiNaz) = rugbyNationalTeam(s)
                base.copy(
                    best = bestRugbySpell(spells),
                    partite = spells.sumOf { it.partite },
                    segnati = spells.sumOf { it.punti },
                    partiteNaz = caps,
                    segnatiNaz = puntiNaz,
                    titoli = trophies(text),
                )
            }
            else -> {
                val naz = hockeyNationalTeam(text)
                base.copy(
                    best = italianHockeyBestSeason(itSeasons),
                    partite = spells.sumOf { it.partite },
                    segnati = spells.sumOf { it.punti },
                    assistTeam = spells.sumOf { it.assist },
                    partiteNaz = naz.partite,
                    segnatiNaz = naz.punti,
                    assistNaz = naz.assist,
                    titoli = trophies(text),
                )
            }
        }
        return WikipediaAthleteStats.Result.Found(data)
    }

    // ---------------------------------------------------------------- squadre

    private val yearRegex = Regex("""\d{4}""")
    private val presenzePuntiRegex = Regex("""(\d[\d.  ]*)\s*\(\s*[+-]?(\d[\d.  ]*)""")

    // Template "contenitore" a un argomento ("{{TA|139 ({{M|1489}})}}", Jonny Wilkinson): con un
    // template annidato WikiTemplates.clean li cancellerebbe interi, vanno aperti prima.
    private val wrapperTemplate = Regex("""\{\{\s*(?:TA|[Nn]owrap|[Ss]mall)\s*\|((?:[^{}]|\{\{[^{}]*\}\})*)\}\}""")

    fun unwrap(text: String): String {
        var current = text
        repeat(3) { current = current.replace(wrapperTemplate) { it.groupValues[1] } }
        return current
    }

    /** Righe "|anni|squadra|presenze (punti)" di un {{Carriera sportivo}}; le righe "chiave = valore" no. */
    fun careerSpells(inner: String): List<Spell> = inner.lines().mapNotNull { line ->
        val t = unwrap(line.trim())
        if (!t.startsWith("|")) return@mapNotNull null
        val parts = WikiTemplates.split(t).drop(1).map { it.trim() }
        if (parts.size < 2 || parts[0].contains('=')) return@mapNotNull null
        val anni = WikiTemplates.clean(parts[0])
        if (!yearRegex.containsMatchIn(anni)) return@mapNotNull null
        val team = teamName(parts[1]).removePrefix("→").trim()
        if (team.isBlank()) return@mapNotNull null
        val stats = WikiTemplates.clean(parts.getOrNull(2).orEmpty())
        val m = presenzePuntiRegex.find(stats)
        Spell(
            anni = anni,
            team = team,
            partite = m?.groupValues?.get(1)?.let(::digits) ?: WikiTemplates.firstNumber(stats),
            punti = m?.groupValues?.get(2)?.let(::digits) ?: 0,
            link = teamLink(parts[1]),
        )
    }

    /** Righe "|anni|squadra|partite|gol|assist" di {{Carriera hockeista su ghiaccio}}. */
    fun hockeySpells(text: String): List<Spell> {
        val inner = WikiTemplates.find(text, "Carriera hockeista su ghiaccio").firstOrNull() ?: return emptyList()
        return inner.lines().mapNotNull { line ->
            val t = line.trim()
            if (!t.startsWith("|")) return@mapNotNull null
            val parts = WikiTemplates.split(t).drop(1).map { it.trim() }
            if (parts.size < 3 || parts[0].contains('=')) return@mapNotNull null
            val anni = WikiTemplates.clean(parts[0])
            if (!yearRegex.containsMatchIn(anni)) return@mapNotNull null
            val team = teamName(parts[1]).removePrefix("→").trim()
            if (team.isBlank()) return@mapNotNull null
            Spell(
                anni = anni,
                team = team,
                partite = WikiTemplates.firstNumber(parts.getOrNull(2)),
                punti = WikiTemplates.firstNumber(parts.getOrNull(3)),
                assist = WikiTemplates.firstNumber(parts.getOrNull(4)),
                link = teamLink(parts[1]),
            )
        }
    }

    private val hockeyTeamTemplate = Regex("""\{\{\s*Hockey su ghiaccio\s+([^|}]+)""", RegexOption.IGNORE_CASE)
    private val rugbyTeamTemplate = Regex("""\{\{\s*Rugby\s+([^|}]+)""", RegexOption.IGNORE_CASE)
    private val footballTeamTemplate = Regex("""\{\{\s*Football\s+([^|}]+)""", RegexOption.IGNORE_CASE)
    // Stemmini e simboli davanti al nome ("{{simbolo|600px ... .svg}} [[Los Angeles Rams|St. Louis/L.A. Rams]]").
    private val symbolTemplate = Regex("""\{\{\s*(?:simbolo|bandiera|band|flagicon)\s*\|[^{}]*\}\}""", RegexOption.IGNORE_CASE)
    private val bareTemplate = Regex("""^\{\{\s*([^|{}]+?)\s*\}\}$""")

    /** Nome leggibile della squadra: "{{Hockey su ghiaccio Pittsburgh|G}}" -> "Pittsburgh",
     *  "{{Rimouski Oceanic}}" -> "Rimouski Oceanic", "[[Rugby Treviso|Treviso]]" -> "Treviso". */
    fun teamName(input: String): String {
        val raw = input.replace(symbolTemplate, "").trim()
        hockeyTeamTemplate.find(raw)?.let { return it.groupValues[1].trim() }
        footballTeamTemplate.find(raw)?.let { return it.groupValues[1].trim() }
        rugbyTeamTemplate.find(raw)?.let { return it.groupValues[1].trim() }
        bareTemplate.find(raw.trim())?.let { return it.groupValues[1].trim() }
        return WikiTemplates.clean(raw)
    }

    private val wikiLink = Regex("""\[\[\s*([^\]|#]+)""")

    /** Voce collegata dal nome della squadra ("[[Rugby Treviso|Treviso]]" -> "Rugby Treviso"); null senza collegamento. */
    fun teamLink(raw: String): String? = wikiLink.find(raw)?.groupValues?.get(1)?.trim()?.ifBlank { null }

    /** Prefisso dei template degli stemmini della squadra ("{{Hockey su ghiaccio Edmonton}}", "{{Rugby Newcastle}}"). */
    fun teamTemplatePrefix(sport: Sport): String? = when (sport) {
        Sport.HOCKEY -> "Hockey su ghiaccio"
        Sport.RUGBY -> "Rugby"
        Sport.FOOTBALL -> "Football"
        else -> null
    }

    /** Squadra principale: quella con più partite (a pari, più stagioni); nel football più stagioni. */
    fun mainTeam(spells: List<Spell>, bySeasons: Boolean): String =
        spells.groupBy { it.team }
            .map { (team, list) -> Triple(team, list.sumOf { it.partite }, list.sumOf { seasonCount(it.anni) }) }
            .maxWithOrNull(
                if (bySeasons) compareBy<Triple<String, Int, Int>> { it.third }
                else compareBy<Triple<String, Int, Int>> { it.second }.thenBy { it.third },
            )?.first.orEmpty()

    private fun thisYear() = Calendar.getInstance().get(Calendar.YEAR)

    /** (anno iniziale, anno finale o null se ancora in corso) di "2005-19", "1978-1988", "2005-", "1996". */
    private fun span(anni: String): Pair<Int, Int?>? {
        val m = Regex("""(\d{4})\s*(?:([-–])\s*(\d{4}|\d{2})?)?""").find(anni) ?: return null
        val from = m.groupValues[1].toInt()
        val dash = m.groupValues[2].isNotEmpty()
        val toRaw = m.groupValues[3]
        val to = when {
            toRaw.isEmpty() && dash -> null
            toRaw.isEmpty() -> from
            toRaw.length == 2 -> from / 100 * 100 + toRaw.toInt()
            else -> toRaw.toInt()
        }
        return from to to
    }

    /** Stagioni comprese nel periodo (estremi inclusi; ancora in corso = fino a quest'anno). */
    fun seasonCount(anni: String): Int {
        val (from, to) = span(anni) ?: return 0
        return ((to ?: thisYear()) - from + 1).coerceAtLeast(1)
    }

    /** "2002-2023" o "2005-" (ancora in attività): dal primo all'ultimo anno di carriera. */
    fun careerRange(anni: List<String>): String {
        val spans = anni.mapNotNull(::span)
        if (spans.isEmpty()) return ""
        val first = spans.minOf { it.first }
        return if (spans.any { it.second == null }) "$first-" else "$first-${spans.maxOf { it.second ?: it.first }}"
    }

    /** true se l'ultimo periodo è chiuso da almeno una stagione (giocatore ritirato o senza squadra). */
    private fun closedBefore(anni: List<String>): Boolean {
        val spans = anni.mapNotNull(::span)
        if (spans.isEmpty() || spans.any { it.second == null }) return false
        return spans.maxOf { it.second ?: it.first } < thisYear() - 1
    }

    private fun digits(raw: String): Int = raw.filter(Char::isDigit).toIntOrNull() ?: 0

    // ---------------------------------------------------------------- Nazionale

    // Nazionale maggiore: "{{Naz|R15|ITA|M}}" o "{{Naz|HG|CAN|M|}}" (terzo parametro "M", nessuna
    // etichetta dopo); le giovanili usano {{NazU|...}} o un'etichetta ("U20", "A").
    private val seniorNaz = Regex("""\{\{\s*Naz\s*\|[^|}]*\|[^|}]*\|\s*M\s*(?:\|\s*)?\}\}""", RegexOption.IGNORE_CASE)
    private val brRegex = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)

    /** Rugby: (presenze, punti) della Nazionale maggiore da "Presenze e punti internazionali". */
    fun rugbyNationalTeam(s: Map<String, String>): Pair<Int, Int> {
        val teams = s["squadreinternazionali"].orEmpty().split(brRegex)
        val stats = s["presenzeepuntiinternazionali"].orEmpty().split(brRegex)
        val index = teams.indexOfFirst { seniorNaz.containsMatchIn(it) }.takeIf { it >= 0 } ?: 0
        val raw = WikiTemplates.clean(unwrap(stats.getOrNull(index).orEmpty()))
        val m = presenzePuntiRegex.find(raw) ?: return WikiTemplates.firstNumber(raw) to 0
        return digits(m.groupValues[1]) to digits(m.groupValues[2])
    }

    /** Hockey: partite, gol e assist della Nazionale maggiore (parametri "Nazionale_*_N"). */
    fun hockeyNationalTeam(text: String): Spell {
        val inner = WikiTemplates.find(text, "Carriera hockeista su ghiaccio").firstOrNull()
            ?: return Spell("", "", 0, 0)
        val p = WikiTemplates.params(inner)
        val ids = p.keys.mapNotNull { Regex("""^nazionale_selezione_(\w+)$""").find(it)?.groupValues?.get(1) }
        val senior = ids.filter { seniorNaz.containsMatchIn(p["nazionale_selezione_$it"].orEmpty()) }
        return Spell(
            anni = "",
            team = "",
            partite = senior.sumOf { WikiTemplates.firstNumber(p["nazionale_giocate_$it"]) },
            punti = senior.sumOf { WikiTemplates.firstNumber(p["nazionale_gol_$it"]) },
            assist = senior.sumOf { WikiTemplates.firstNumber(p["nazionale_assist_$it"]) },
        )
    }

    // ---------------------------------------------------------------- titoli

    private val worldOrOlympic = Regex("""mondial|olimpi""", RegexOption.IGNORE_CASE)
    private val youth = Regex("""\bU-?\d{2}\b|under|giovanil|junior""", RegexOption.IGNORE_CASE)

    /**
     * Rugby/Hockey: titoli dal {{Palmarès}}: i trofei vinti ("trofeo N" + "vittorie N", es. "Stanley
     * Cup" 3) più gli ori di Mondiali e Olimpiadi ("competizione N" + "oro N"), senza le giovanili.
     */
    fun trophies(text: String): Int = WikiTemplates.find(text, "Palmarès").sumOf { block ->
        val p = WikiTemplates.params(block)
        val wins = p.keys.filter { it.startsWith("vittorie") }.sumOf { WikiTemplates.firstNumber(p[it]) }
        val golds = p.keys.filter { it.startsWith("competizione") }.sumOf { key ->
            val n = key.removePrefix("competizione")
            val comp = WikiTemplates.clean(p[key].orEmpty())
            if (worldOrOlympic.containsMatchIn(comp) && !youth.containsMatchIn(comp)) WikiTemplates.firstNumber(p["oro$n"]) else 0
        }
        wins + golds
    }

    data class FootballPalmares(val superBowl: Int = 0, val proBowl: Int = 0, val allPro: Int = 0, val mvp: Int = 0)

    /** Football: Super Bowl vinti, selezioni al Pro Bowl e All-Pro, MVP della NFL dal {{Palmarès}}. */
    fun footballPalmares(text: String): FootballPalmares {
        var result = FootballPalmares()
        WikiTemplates.find(text, "Palmarès").forEach { block ->
            val p = WikiTemplates.params(block)
            p.keys.filter { it.startsWith("trofeo") }.forEach { key ->
                val n = key.removePrefix("trofeo")
                val label = WikiTemplates.clean(p[key].orEmpty()).lowercase()
                val wins = WikiTemplates.firstNumber(p["vittorie$n"])
                result = when {
                    label == "super bowl" || label == "super bowl vinti" -> result.copy(superBowl = result.superBowl + wins)
                    "pro bowl" in label && "mvp" !in label -> result.copy(proBowl = result.proBowl + wins)
                    "all-pro" in label -> result.copy(allPro = result.allPro + wins)
                    "mvp" in label && "nfl" in label && "super bowl" !in label -> result.copy(mvp = result.mvp + wins)
                    else -> result
                }
            }
        }
        return result
    }

    // ---------------------------------------------------------------- football: voce inglese

    data class FootballStats(
        val touchdown: Int = 0,
        val yard: Int = 0,
        val tackle: Int = 0,
        val sack: Int = 0,
        val intercetti: Int = 0,
        val palmares: FootballPalmares = FootballPalmares(),
        val seasons: Int = 0,
    )

    private val enNumber = Regex("""\d[\d,]*""")

    private fun enInt(raw: String): Int = enNumber.find(raw)?.value?.replace(",", "")?.toIntOrNull() ?: 0

    /**
     * Statistiche di carriera dall'infobox della voce en.wikipedia ({{Infobox NFL biography}} o
     * {{Infobox gridiron football biography}}): le coppie "stat_labelN"/"stat_valueN" cambiano col
     * ruolo (un quarterback ha yard di passaggio e "TD–INT", un linebacker tackle e sack), quindi si
     * riconoscono dall'etichetta. In più, dagli "highlights", Pro Bowl, All-Pro, Super Bowl e MVP.
     */
    fun englishFootball(text: String): FootballStats? {
        val box = WikiTemplates.findPrefixed(text, "Infobox")
            .map(WikiTemplates::params)
            .firstOrNull { p -> p.keys.any { it.startsWith("stat_label") || it.startsWith("statlabel") || it == "highlights" } }
            ?: return null
        val quarterback = WikiTemplates.clean(box["position"].orEmpty()).contains("quarterback", ignoreCase = true)
        var tdSum = 0
        var tdTotal: Int? = null
        var yards = 0
        var tackleTotal: Int? = null
        var tacklePlain: Int? = null
        var tackleParts = 0
        var sacks = 0
        var ints = 0
        for (i in 1..20) {
            val label = WikiTemplates.clean(box["stat_label$i"] ?: box["statlabel$i"] ?: continue).lowercase()
            val value = WikiTemplates.clean(box["stat_value$i"] ?: box["statvalue$i"] ?: "")
            val n = enInt(value)
            when {
                // "TD–INT" dei quarterback: "649–212", il primo numero sono i touchdown.
                Regex("""^td\s*[-–/]\s*int""").containsMatchIn(label) -> tdSum += n
                "touchdown" in label || label == "td" || label == "tds" ->
                    if ("total" in label) tdTotal = n else tdSum += n
                "yard" in label && listOf("average", "per ", "longest", "return", "punt", "kick").none { it in label } -> yards += n
                "tackle" in label -> when {
                    "total" in label -> tackleTotal = n
                    "solo" in label || "assist" in label -> tackleParts += n
                    else -> tacklePlain = n
                }
                "sack" in label && "sacked" !in label && "times" !in label -> sacks += n
                "interception" in label && !quarterback && "thrown" !in label -> ints += n
            }
        }
        return FootballStats(
            touchdown = tdTotal ?: tdSum,
            yard = yards,
            tackle = tackleTotal ?: tacklePlain ?: tackleParts,
            sack = sacks,
            intercetti = ints,
            palmares = englishHighlights(box["highlights"].orEmpty()),
            seasons = englishSeasons(box),
        )
    }

    private val timesPrefix = Regex("""^\s*(\d+)\s*[×x]""")

    /** "7× Super Bowl champion", "15× Pro Bowl", "3× NFL Most Valuable Player"... dagli highlights. */
    private fun englishHighlights(raw: String): FootballPalmares {
        var r = FootballPalmares()
        raw.split(Regex("""\n\s*\*+|<br\s*/?>|\{\{\s*(?:plainlist|unbulleted list)""", RegexOption.IGNORE_CASE)).forEach { piece ->
            val line = WikiTemplates.clean(piece)
            if (line.isBlank()) return@forEach
            val times = timesPrefix.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val l = line.lowercase()
            r = when {
                "super bowl champion" in l -> r.copy(superBowl = r.superBowl + times)
                "pro bowl" in l && "mvp" !in l -> r.copy(proBowl = r.proBowl + times)
                "all-pro" in l -> r.copy(allPro = r.allPro + times)
                ("most valuable player" in l || l.endsWith("mvp")) && "nfl" in l && "super bowl" !in l && "offensive" !in l &&
                    "defensive" !in l && "rookie" !in l -> r.copy(mvp = r.mvp + times)
                else -> r
            }
        }
        return r
    }

    /** Stagioni NFL dagli anni delle squadre dell'infobox inglese ("past_teams": "* [[...]] (2000–2019)"). */
    private fun englishSeasons(box: Map<String, String>): Int {
        val teams = box["past_teams"] ?: box["pastteams"] ?: return 0
        return Regex("""\((\d{4})\s*(?:[–-]\s*(\d{4}|present)?)?\)""", RegexOption.IGNORE_CASE).findAll(teams).sumOf { m ->
            val from = m.groupValues[1].toInt()
            val to = m.groupValues[2].toIntOrNull() ?: if (m.groupValues[2].isNotEmpty()) thisYear() else from
            (to - from + 1).coerceAtLeast(1)
        }
    }

    /** Completa i dati del football letti da it.wikipedia con quelli della voce inglese. */
    fun enrichFootball(data: AthleteData, en: FootballStats): AthleteData = data.copy(
        partite = data.partite.takeIf { it > 0 } ?: en.seasons,
        touchdown = en.touchdown,
        yard = en.yard,
        tackleFb = en.tackle,
        sack = en.sack,
        intercetti = en.intercetti,
        titoli = maxOf(data.titoli, en.palmares.superBowl),
        proBowl = maxOf(data.proBowl, en.palmares.proBowl),
        allPro = maxOf(data.allPro, en.palmares.allPro),
        mvp = maxOf(data.mvp, en.palmares.mvp),
    )

    // ---------------------------------------------------------------- stagione migliore

    /** Rugby: periodo con più punti per presenza, tra quelli con almeno 20 presenze (se ce ne sono). */
    fun bestRugbySpell(spells: List<Spell>): BestSeason? {
        val played = spells.filter { it.partite > 0 }
        val pool = played.filter { it.partite >= 20 }.ifEmpty { played }
        val best = pool.maxWithOrNull(
            compareBy<Spell> { it.punti.toDouble() / it.partite }.thenBy { it.partite },
        ) ?: return null
        return BestSeason(best.anni, best.team, best.link, best.partite, best.punti)
    }

    private val abbrTemplate = Regex("""\{\{\s*abbr\s*\|([^|{}]*)\|[^{}]*\}\}""", RegexOption.IGNORE_CASE)
    private val cellAttributes = Regex("""^\s*(?:[a-z-]+\s*=\s*"[^"]*"\s*)+\|(?!\|)""", RegexOption.IGNORE_CASE)
    private val seasonYear = Regex("""\d{4}""")
    private val leadingYear = Regex("""^\d{4}\s+""")

    /** Testo pulito di una cella di tabella: senza attributi di stile, grassetti e {{abbr}}. */
    private fun cell(raw: String): String {
        val noAttr = raw.replace(cellAttributes, "")
        return WikiTemplates.clean(noAttr.replace(abbrTemplate) { it.groupValues[1] }).replace(Regex("'{2,}"), "").trim()
    }

    /** Righe di una tabella wikitext: per ogni riga, le celle in ordine ("!" e "|", anche "!!" e "||"). */
    private fun tableRows(table: String): List<List<String>> =
        table.split(Regex("""\n\|-[^\n]*""")).map { row ->
            row.lines().map { it.trim() }
                .filter { (it.startsWith("|") || it.startsWith("!")) && !it.startsWith("|}") && !it.startsWith("{|") }
                .flatMap { line -> line.substring(1).split(if (line.startsWith("!")) Regex("""!!|\|\|""") else Regex("""\|\|""")) }
        }

    /** Prima tabella dopo il titolo [heading] (es. "Regular season"), fino a "|}". */
    private fun tableAfter(text: String, heading: Regex): String? {
        val h = heading.find(text) ?: return null
        val start = text.indexOf("{|", h.range.last)
        if (start < 0) return null
        val end = text.indexOf("\n|}", start)
        return if (end < 0) text.substring(start) else text.substring(start, end)
    }

    /** "2007 New England Patriots season" -> "New England Patriots". */
    private fun seasonArticleTeam(raw: String): String? {
        val target = teamLink(raw) ?: return null
        return target.replace(leadingYear, "").removeSuffix(" season").trim().ifBlank { null }
    }

    /**
     * Football: stagione NFL con più touchdown + intercetti + sack (a pari, più tackle) dalla tabella
     * "Regular season" della voce inglese. Le colonne si riconoscono dalle intestazioni: "TD" (lanciati,
     * corse, ricezioni, ritorni), "Int" fuori dal gruppo "Passing" (per un quarterback sono gli
     * intercetti subiti, non contano), "Sck" fuori da "Sacked" e "Cmb" (tackle). [BestSeason.extra] =
     * intercetti + sack.
     */
    fun englishFootballBestSeason(text: String): BestSeason? {
        val table = tableAfter(text, Regex("""=+\s*Regular season\s*=+""", RegexOption.IGNORE_CASE)) ?: return null
        val rows = tableRows(table).filter { it.isNotEmpty() }
        val groupRow = rows.firstOrNull { r -> r.any { "rowspan" in it && "Year" in it } } ?: return null
        val labelRow = rows.getOrNull(rows.indexOf(groupRow) + 1) ?: return null
        val groups = mutableListOf<String>()
        groupRow.filterNot { "rowspan" in it }.forEach { c ->
            val span = Regex("""colspan\s*=\s*"?(\d+)""").find(c)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            repeat(span) { groups += cell(c) }
        }
        val labels = labelRow.map(::cell)
        val tdCols = labels.indices.filter { labels[it].equals("TD", ignoreCase = true) }
        val intCols = labels.indices.filter {
            labels[it].equals("Int", ignoreCase = true) && !groups.getOrElse(it) { "" }.contains("Passing", ignoreCase = true)
        }
        // Sack fatti (non "Sacked" dei quarterback) e tackle combinati: per i difensori senza
        // touchdown né intercetti (es. Aaron Donald) la stagione migliore è quella con più giocate.
        val sackCols = labels.indices.filter {
            labels[it].lowercase() in setOf("sck", "sacks", "sack") && !groups.getOrElse(it) { "" }.contains("Sacked", ignoreCase = true)
        }
        val tackleCol = labels.indexOfFirst { it.lowercase() in setOf("cmb", "comb", "tot", "total", "tkl") }
        val gpCol = labels.indexOfFirst { it.equals("GP", ignoreCase = true) || it.equals("G", ignoreCase = true) }
        var best: BestSeason? = null
        var bestScore = -1
        for (r in rows.drop(rows.indexOf(labelRow) + 1)) {
            if (r.size < 3) continue
            val year = seasonYear.find(cell(r[0]))?.value ?: continue
            val team = seasonArticleTeam(r[1]) ?: cell(r[1])
            val values = r.drop(2).map { enInt(cell(it)) }
            val td = tdCols.sumOf { values.getOrElse(it) { 0 } }
            val ints = intCols.sumOf { values.getOrElse(it) { 0 } }
            val sacks = sackCols.sumOf { values.getOrElse(it) { 0 } }
            val tackles = if (tackleCol >= 0) values.getOrElse(tackleCol) { 0 } else 0
            val score = (td + ints + sacks) * 10 + tackles
            if (score > bestScore) {
                bestScore = score
                best = BestSeason(year, team, null, values.getOrElse(gpCol) { 0 }, td, ints + sacks)
            }
        }
        return best?.takeIf { bestScore > 0 }
    }

    /**
     * Hockey: stagione con più punti (gol + assist) nella tabella "Regular season and playoffs" della
     * voce inglese (colonne Season, Team, League, GP, G, A, Pts, PIM della stagione regolare), solo nel
     * campionato principale del giocatore (quello con più partite, di solito la NHL). Portieri: la
     * stagione con più vittorie ([BestSeason.gol] = vittorie, [BestSeason.extra] = shutout).
     */
    fun englishHockeyBestSeason(text: String): BestSeason? {
        val table = tableAfter(text, Regex("""=+\s*(?:Regular season and playoffs|Career statistics)\s*=+""", RegexOption.IGNORE_CASE))
            ?: return null
        class Row(val season: String, val team: String, val link: String?, val league: String, val gp: Int, val g: Int, val a: Int, val pts: Int)
        val all = tableRows(table)
        // Portieri: colonne GP, W, L, (T), (OTL), MIN, GA, SO, GAA... -> vittorie e shutout al posto
        // di gol e assist; "pts" = vittorie, a pari più shutout.
        val labels = all.firstOrNull { r -> r.map(::cell).let { "GP" in it && "W" in it } }?.map(::cell)
        val winCol = labels?.indexOf("W") ?: -1
        val soCol = labels?.indexOf("SO") ?: -1
        val goalie = winCol >= 0
        val rows = all.mapNotNull { r ->
            if (r.size < 7) return@mapNotNull null
            val season = cell(r[0])
            if (!seasonYear.containsMatchIn(season)) return@mapNotNull null
            val n = r.map { enInt(cell(it).replace("—", "")) }
            if (goalie) {
                val w = n.getOrElse(winCol) { 0 }
                val so = if (soCol >= 0) n.getOrElse(soCol) { 0 } else 0
                Row(season, cell(r[1]), teamLink(r[1]), cell(r[2]), n[3], w, so, w * 100 + so)
            } else {
                Row(season, cell(r[1]), teamLink(r[1]), cell(r[2]), n[3], n[4], n[5], n[6])
            }
        }
        val league = rows.groupBy { it.league }.maxByOrNull { (_, l) -> l.sumOf { it.gp } }?.key ?: return null
        val best = rows.filter { it.league == league && it.gp > 0 }
            .maxWithOrNull(compareBy<Row> { it.pts }.thenBy { -it.gp }) ?: return null
        return BestSeason(best.season, best.team, best.link, best.gp, best.g, best.a)
    }

    // ---------------------------------------------------------------- hockey: tabella di it.wikipedia

    /** Una stagione della tabella "Statistiche > Club" di it.wikipedia (stagione regolare). */
    data class HockeySeason(
        val stagione: String,
        val team: String,
        val link: String?,
        val lega: String,
        val partite: Int,
        /** Gol, o vittorie per un portiere. */
        val gol: Int,
        /** Assist, o shutout per un portiere. */
        val assist: Int,
        val punti: Int,
        val portiere: Boolean,
    )

    private class GridCell(val text: String, val header: Boolean)

    private val gridAttributes = Regex("""^\s*((?:[a-z-]+\s*=\s*(?:"[^"]*"|[^\s|"]+)\s*)+)\|(?!\|)""", RegexOption.IGNORE_CASE)
    private val rowspanAttr = Regex("""rowspan\s*=\s*"?(\d+)""", RegexOption.IGNORE_CASE)
    private val colspanAttr = Regex("""colspan\s*=\s*"?(\d+)""", RegexOption.IGNORE_CASE)
    private val descrizioneComando = Regex("""\{\{\s*Descrizione comando\s*\|([^|{}]+)""", RegexOption.IGNORE_CASE)

    /**
     * Tabella wikitext come griglia, con rowspan e colspan espansi: una cella unita su più righe o
     * colonne compare in ogni posizione che copre (la squadra "rowspan=9" sta in tutte e 9 le stagioni).
     */
    private fun tableGrid(table: String): List<List<GridCell?>> {
        val grid = mutableListOf<List<GridCell?>>()
        val pending = mutableMapOf<Int, Pair<GridCell, Int>>() // colonna -> (cella, righe ancora coperte)
        for (rowText in table.split(Regex("""\n\s*\|-[^\n]*"""))) {
            val cells = rowText.lines().map(String::trim)
                .filter { (it.startsWith("|") || it.startsWith("!")) && !it.startsWith("|}") && !it.startsWith("{|") && !it.startsWith("|+") }
                .flatMap { line ->
                    val header = line.startsWith("!")
                    var body = line.substring(1)
                    if (!header && body.startsWith("|")) body = body.substring(1) // "|| 1976-1977 || ..."
                    body.split(if (header) Regex("""!!|\|\|""") else Regex("""\|\|""")).map { header to it }
                }
            if (cells.isEmpty()) continue
            val row = mutableListOf<GridCell?>()
            fun takePending(col: Int): Boolean {
                val (c, left) = pending[col] ?: return false
                row.add(c)
                if (left <= 1) pending.remove(col) else pending[col] = c to left - 1
                return true
            }
            for ((header, raw) in cells) {
                while (takePending(row.size)) Unit
                val attrs = gridAttributes.find(raw)
                val content = if (attrs != null) raw.substring(attrs.range.last + 1) else raw
                val attrText = attrs?.groupValues?.get(1).orEmpty()
                val rs = rowspanAttr.find(attrText)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val cs = colspanAttr.find(attrText)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val gc = GridCell(content.trim(), header)
                repeat(cs.coerceIn(1, 50)) {
                    if (rs > 1) pending[row.size] = gc to rs - 1
                    row.add(gc)
                }
            }
            while (pending.keys.any { it >= row.size }) { if (!takePending(row.size)) row.add(null) }
            grid += row
        }
        return grid
    }

    private fun gridLabel(c: GridCell?): String =
        c?.text?.let { t -> descrizioneComando.find(t)?.groupValues?.get(1)?.trim() ?: cell(t) }.orEmpty()

    /**
     * Stagioni dalla tabella "Statistiche > Club" della voce italiana (Wayne Gretzky, Martin Brodeur):
     * colonne Stagione, Squadra, Lega e, per la stagione regolare, P G A Pt (o P W SO per i portieri).
     * Le righe di totale (intestazioni "!") sono scartate.
     */
    fun italianHockeySeasons(text: String): List<HockeySeason> {
        val table = tableAfter(text, Regex("""=+\s*Statistiche[^=\n]*=+""", RegexOption.IGNORE_CASE)) ?: return emptyList()
        val grid = tableGrid(table)
        val labelIndex = grid.indexOfFirst { r ->
            val l = r.filter { it?.header == true }.map(::gridLabel)
            ("P" in l || "GP" in l) && (("G" in l && "A" in l) || "W" in l)
        }
        if (labelIndex < 0) return emptyList()
        val labels = grid[labelIndex].map(::gridLabel)
        fun col(vararg names: String) = labels.indexOfFirst { l -> names.any { it.equals(l, ignoreCase = true) } }
        val seasonCol = col("Stagione", "Anno").takeIf { it >= 0 } ?: 0
        val teamCol = col("Squadra", "Club").takeIf { it >= 0 } ?: 1
        val leagueCol = col("Lega", "Campionato")
        val gpCol = col("P", "GP", "PG")
        val winCol = col("W", "V")
        val goalie = winCol >= 0 && col("G") < 0
        val gCol = if (goalie) winCol else col("G")
        val aCol = if (goalie) col("SO") else col("A")
        val ptsCol = col("Pt", "Pts", "Punti")
        fun number(r: List<GridCell?>, c: Int) = if (c < 0) 0 else r.getOrNull(c)?.text?.let { WikiTemplates.firstNumber(cell(it)) } ?: 0
        return grid.drop(labelIndex + 1).mapNotNull { r ->
            val seasonCell = r.getOrNull(seasonCol) ?: return@mapNotNull null
            val teamCell = r.getOrNull(teamCol) ?: return@mapNotNull null
            if (seasonCell.header || teamCell.header) return@mapNotNull null
            val season = cell(seasonCell.text).replace("*", "").trim()
            if (!seasonYear.containsMatchIn(season)) return@mapNotNull null
            val team = teamName(teamCell.text).trim()
            if (team.isBlank()) return@mapNotNull null
            val g = number(r, gCol)
            val a = number(r, aCol)
            HockeySeason(
                stagione = season,
                team = team,
                link = teamLink(teamCell.text),
                lega = if (leagueCol >= 0) r.getOrNull(leagueCol)?.text?.let(::cell).orEmpty() else "",
                partite = number(r, gpCol),
                gol = g,
                assist = a,
                punti = if (goalie) g else number(r, ptsCol).takeIf { it > 0 } ?: (g + a),
                portiere = goalie,
            )
        }
    }

    /**
     * Hockey: stagione migliore dalla tabella della voce italiana, nel campionato principale (quello
     * con più partite): più punti (gol + assist) o, per un portiere, più vittorie e poi più shutout.
     */
    fun italianHockeyBestSeason(seasons: List<HockeySeason>): BestSeason? {
        val league = seasons.groupBy { it.lega }.maxByOrNull { (_, l) -> l.sumOf { it.partite } }?.key ?: return null
        val best = seasons.filter { it.lega == league && it.partite > 0 }
            .maxWithOrNull(compareBy<HockeySeason> { it.punti }.thenBy { if (it.portiere) it.assist else 0 }.thenBy { -it.partite })
            ?: return null
        return BestSeason(best.stagione, best.team, best.link, best.partite, best.gol, best.assist)
    }

    /** Periodi per squadra dalla tabella (se l'infobox non ha le partite): stagioni consecutive nella stessa squadra. */
    fun hockeySpellsFromSeasons(seasons: List<HockeySeason>): List<Spell> {
        val out = mutableListOf<Spell>()
        for (s in seasons) {
            val last = out.lastOrNull()
            val gol = if (s.portiere) 0 else s.gol
            val assist = if (s.portiere) 0 else s.assist
            if (last != null && last.team == s.team) {
                out[out.lastIndex] = last.copy(
                    anni = last.anni.substringBefore("-") + "-" + s.stagione.substringAfterLast("-"),
                    partite = last.partite + s.partite,
                    punti = last.punti + gol,
                    assist = last.assist + assist,
                )
            } else {
                out += Spell(s.stagione, s.team, s.partite, gol, assist, s.link)
            }
        }
        return out
    }
}
