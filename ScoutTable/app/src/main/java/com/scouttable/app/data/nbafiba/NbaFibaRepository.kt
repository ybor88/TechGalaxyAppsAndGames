// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.nbafiba

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Competizioni di "Simula partita" (solo basket) in cui le partite vengono registrate. I file
 * iniziano con "competitions_", così sono già inclusi nel backup su Drive (vedi BackupManager).
 * [logoName]: immagine in res/drawable (senza estensione); se manca si mostra un trofeo.
 */
enum class SimCompetition(
    val label: String,
    val fileName: String,
    val logoName: String,
    /** Solo per le sfide a due squadre: i due "lati" che si contendono il punto (es. NBA / FIBA). */
    val sideA: String? = null,
    val sideB: String? = null,
    /** true per le sfide di calcio (possono finire in pareggio). */
    val soccer: Boolean = false,
) {
    NBA_FIBA("NBA FIBA Champions", "competitions_nbafiba_matches.json", "nbafiba", "NBA", "FIBA"),
    NBA_EUROLEAGUE("NBA vs EuroLeague", "competitions_nba_euroleague_matches.json", "nba_euroleague", "NBA", "EuroLeague"),
    WNBA_EUROLEAGUE(
        "WNBA vs EuroLeague Femminile", "competitions_wnba_euroleague_matches.json", "wnba_euroleague", "WNBA", "EuroLeague",
    ),
    MONDIALE_INTERCONTINENTALE(
        "Mondiale Nazionali vs Intercontinentale", "competitions_mondiale_intercontinentale_matches.json",
        "mondiale_intercontinentale", "Nazionali", "Club", soccer = true,
    ),
    EUROPE_TOP_CLUB(
        "Europe Top Club", "competitions_europe_top_club_matches.json", "europe_top_club", "Maschile", "Femminile", soccer = true,
    ),
    G_LEAGUE("NBA G League Champions", "competitions_gleague_titles.json", "gleague_champions"),
    NCAA_HS_NEXTGEN("NCAA High School & Next Generation adidas", "competitions_ncaa_hs_nextgen_titles.json", "ncaa_hs_nextgen"),
    TOP_GALAXY("TOP Galaxy Champions", "competitions_topgalaxy_titles.json", "topgalaxy_logo"),
}

/** MVP: il miglior realizzatore (della squadra vincitrice in una partita, di tutto il torneo in TOP Galaxy). */
data class SimMvp(val name: String, val team: String, val points: Int)

/** Una partita registrata in una sfida a due squadre (NBA FIBA, WNBA vs EuroLeague Femminile). */
data class CompMatch(
    val id: String,
    val playedAt: Long,
    val homeName: String,
    val homeLogo: String?,
    val homeScore: Int,
    val awayName: String,
    val awayLogo: String?,
    val awayScore: Int,
    /** true se la partita è finita ai supplementari. */
    val overtime: Boolean,
    val mvp: SimMvp?,
    /** Quale squadra rappresentava il primo lato ([SimCompetition.sideA], es. NBA): 0 = casa,
     *  1 = ospite; null se non indicato. Nel file si chiama "nbaSide" (nato con NBA FIBA). */
    val nbaSide: Int? = null,
    /** Anno della competizione indicato dall'utente (es. 2025), per ordinare l'albo. */
    val year: Int? = null,
) {
    val homeWon: Boolean get() = homeScore > awayScore
    /** Solo calcio: nel basket i supplementari decidono sempre un vincitore. */
    val draw: Boolean get() = homeScore == awayScore
    /** Squadra vincitrice, null in caso di pareggio. */
    val winnerName: String? get() = when { draw -> null; homeWon -> homeName; else -> awayName }

    /** true se ha vinto il primo lato (NBA, WNBA, Mondiale), false se ha vinto il secondo, null se
     *  non noto o pareggio. */
    val nbaWon: Boolean? get() = if (draw) null else nbaSide?.let { side -> (side == 0) == homeWon }
}

/** Archivio delle partite di una sfida a due squadre, dalla più recente. */
class CompMatchRepository(context: Context, competition: SimCompetition) {

    private val file = File(context.applicationContext.filesDir, competition.fileName)

    suspend fun load(): List<CompMatch> = withContext(Dispatchers.IO) {
        val array = runCatching { JSONArray(file.readText()) }.getOrNull() ?: return@withContext emptyList()
        (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                CompMatch(
                    id = o.getString("id"),
                    playedAt = o.optLong("playedAt"),
                    homeName = o.getString("homeName"),
                    homeLogo = o.optString("homeLogo").ifBlank { null },
                    homeScore = o.getInt("homeScore"),
                    awayName = o.getString("awayName"),
                    awayLogo = o.optString("awayLogo").ifBlank { null },
                    awayScore = o.getInt("awayScore"),
                    overtime = o.optBoolean("overtime"),
                    mvp = decodeMvp(o),
                    nbaSide = if (o.has("nbaSide") && !o.isNull("nbaSide")) o.getInt("nbaSide") else null,
                    year = if (o.has("year") && !o.isNull("year")) o.getInt("year") else null,
                )
            }.getOrNull()
        }.sortedWith(byYearThenDate({ it.year }, { it.playedAt }))
    }

    /** Registra una partita e restituisce l'elenco aggiornato. */
    suspend fun add(match: CompMatch): List<CompMatch> {
        val updated = listOf(match.copy(id = UUID.randomUUID().toString(), playedAt = System.currentTimeMillis())) + load()
        save(updated)
        // Riletto: l'albo è ordinato per anno della competizione, non per ordine di inserimento.
        return load()
    }

    /** Elimina una partita e restituisce l'elenco aggiornato. */
    suspend fun delete(id: String): List<CompMatch> {
        val updated = load().filterNot { it.id == id }
        save(updated)
        // Riletto: l'albo è ordinato per anno della competizione, non per ordine di inserimento.
        return load()
    }

    private suspend fun save(matches: List<CompMatch>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        matches.forEach { m ->
            array.put(
                JSONObject()
                    .put("id", m.id)
                    .put("playedAt", m.playedAt)
                    .put("homeName", m.homeName)
                    .put("homeLogo", m.homeLogo ?: "")
                    .put("homeScore", m.homeScore)
                    .put("awayName", m.awayName)
                    .put("awayLogo", m.awayLogo ?: "")
                    .put("awayScore", m.awayScore)
                    .put("overtime", m.overtime)
                    .put("nbaSide", m.nbaSide ?: JSONObject.NULL)
                    .put("year", m.year ?: JSONObject.NULL)
                    .also { encodeMvp(it, m.mvp) }
            )
        }
        file.writeText(array.toString())
    }
}

/** Un torneo concluso (NBA G League Champions, TOP Galaxy Champions): vincitore, finalista,
 *  punteggio della finale, MVP. */
data class GalaxyTitle(
    val id: String,
    val playedAt: Long,
    val champion: String,
    val championLogo: String?,
    val championLabel: String,
    val runnerUp: String,
    val runnerUpLogo: String?,
    val finalChampionScore: Int,
    val finalRunnerUpScore: Int,
    val teams: Int,
    val mvp: SimMvp?,
    /** Anno della competizione indicato dall'utente (es. 2025), per ordinare l'albo. */
    val year: Int? = null,
)

/** Albo d'oro di un torneo (G League, TOP Galaxy), dal più recente. */
class GalaxyTitleRepository(context: Context, competition: SimCompetition) {

    private val file = File(context.applicationContext.filesDir, competition.fileName)

    suspend fun load(): List<GalaxyTitle> = withContext(Dispatchers.IO) {
        val array = runCatching { JSONArray(file.readText()) }.getOrNull() ?: return@withContext emptyList()
        (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                GalaxyTitle(
                    id = o.getString("id"),
                    playedAt = o.optLong("playedAt"),
                    champion = o.getString("champion"),
                    championLogo = o.optString("championLogo").ifBlank { null },
                    championLabel = o.optString("championLabel"),
                    runnerUp = o.getString("runnerUp"),
                    runnerUpLogo = o.optString("runnerUpLogo").ifBlank { null },
                    finalChampionScore = o.optInt("finalChampionScore"),
                    finalRunnerUpScore = o.optInt("finalRunnerUpScore"),
                    teams = o.optInt("teams"),
                    mvp = decodeMvp(o),
                    year = if (o.has("year") && !o.isNull("year")) o.getInt("year") else null,
                )
            }.getOrNull()
        }.sortedWith(byYearThenDate({ it.year }, { it.playedAt }))
    }

    suspend fun add(title: GalaxyTitle): List<GalaxyTitle> {
        val updated = listOf(title.copy(id = UUID.randomUUID().toString(), playedAt = System.currentTimeMillis())) + load()
        save(updated)
        // Riletto: l'albo è ordinato per anno della competizione, non per ordine di inserimento.
        return load()
    }

    suspend fun delete(id: String): List<GalaxyTitle> {
        val updated = load().filterNot { it.id == id }
        save(updated)
        // Riletto: l'albo è ordinato per anno della competizione, non per ordine di inserimento.
        return load()
    }

    private suspend fun save(titles: List<GalaxyTitle>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        titles.forEach { t ->
            array.put(
                JSONObject()
                    .put("id", t.id)
                    .put("playedAt", t.playedAt)
                    .put("champion", t.champion)
                    .put("championLogo", t.championLogo ?: "")
                    .put("championLabel", t.championLabel)
                    .put("runnerUp", t.runnerUp)
                    .put("runnerUpLogo", t.runnerUpLogo ?: "")
                    .put("finalChampionScore", t.finalChampionScore)
                    .put("finalRunnerUpScore", t.finalRunnerUpScore)
                    .put("teams", t.teams)
                    .put("year", t.year ?: JSONObject.NULL)
                    .also { encodeMvp(it, t.mvp) }
            )
        }
        file.writeText(array.toString())
    }
}

/** Albo ordinato per anno della competizione, dal più recente; senza anno in fondo; a parità, il più recente. */
private fun <T> byYearThenDate(year: (T) -> Int?, playedAt: (T) -> Long): Comparator<T> =
    compareByDescending<T> { year(it) ?: Int.MIN_VALUE }.thenByDescending { playedAt(it) }

private fun decodeMvp(o: JSONObject): SimMvp? {
    val name = o.optString("mvpName").ifBlank { return null }
    return SimMvp(name, o.optString("mvpTeam"), o.optInt("mvpPoints"))
}

private fun encodeMvp(o: JSONObject, mvp: SimMvp?) {
    if (mvp == null) return
    o.put("mvpName", mvp.name).put("mvpTeam", mvp.team).put("mvpPoints", mvp.points)
}

// Le 30 franchigie NBA (nome della squadra, come compare nei nomi delle pagine squadra di
// Proballers/Basketball-Reference): servono a capire da sola quale squadra rappresenta la NBA.
private val nbaFranchises = listOf(
    "Hawks", "Celtics", "Nets", "Hornets", "Bulls", "Cavaliers", "Mavericks", "Nuggets", "Pistons", "Warriors",
    "Rockets", "Pacers", "Clippers", "Lakers", "Grizzlies", "Heat", "Bucks", "Timberwolves", "Pelicans", "Knicks",
    "Thunder", "Magic", "76ers", "Sixers", "Suns", "Trail Blazers", "Blazers", "Kings", "Spurs", "Raptors", "Jazz", "Wizards",
)

// Le franchigie WNBA, per riconoscere da sola la squadra WNBA in "WNBA vs EuroLeague Femminile".
private val wnbaFranchises = listOf(
    "Aces", "Dream", "Sky", "Sun", "Wings", "Fever", "Sparks", "Lynx", "Liberty", "Mercury", "Storm",
    "Mystics", "Valkyries", "Tempo", "Fire",
)

/** Vero se [teamName] appartiene al primo lato della sfida (franchigia NBA o WNBA). */
fun isSideATeam(competition: SimCompetition, teamName: String): Boolean = when (competition) {
    SimCompetition.WNBA_EUROLEAGUE ->
        wnbaFranchises.any { Regex("""\b${Regex.escape(it)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(teamName) }
    // Una nazionale ha come nome un paese (in inglese o in italiano, es. "Italy", "Italia").
    SimCompetition.MONDIALE_INTERCONTINENTALE -> countryNames.contains(teamName.trim().lowercase())
    // Squadra maschile = senza le parole tipiche delle squadre femminili.
    SimCompetition.EUROPE_TOP_CLUB -> !womenTeamRegex.containsMatchIn(teamName)
    else -> isNbaTeam(teamName)
}

private val womenTeamRegex = Regex("""\b(women|womens|woman|femminile|femenino|féminin|feminin|frauen|ladies|dames)\b""", RegexOption.IGNORE_CASE)

private val countryNames: Set<String> by lazy {
    java.util.Locale.getISOCountries().flatMap { iso ->
        val locale = java.util.Locale("", iso)
        listOf(locale.getDisplayCountry(java.util.Locale.ENGLISH), locale.getDisplayCountry(java.util.Locale.ITALIAN))
    }.map { it.lowercase() }.toSet() +
        setOf("england", "inghilterra", "scotland", "scozia", "wales", "galles", "northern ireland", "irlanda del nord")
}

/** Vero se [teamName] è una franchigia NBA (es. "Boston Celtics"). */
fun isNbaTeam(teamName: String): Boolean =
    nbaFranchises.any { Regex("""\b${Regex.escape(it)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(teamName) }
