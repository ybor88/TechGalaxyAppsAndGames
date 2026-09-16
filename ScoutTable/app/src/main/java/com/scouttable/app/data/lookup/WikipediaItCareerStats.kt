package com.scouttable.app.data.lookup

import com.scouttable.app.data.YouthClub
import java.net.URLEncoder
import kotlin.math.abs
import kotlinx.coroutines.delay
import okhttp3.Request

/** Uno "spell" (periodo continuativo) a un club, dal blocco "Squadre" di it.wikipedia.org. */
data class ClubSpell(
    val club: String,
    val anni: String,
    val presenze: Int,
    /** Valore assoluto: gol segnati per i ruoli di movimento, gol subiti per i portieri. */
    val gol: Int,
    /** true se nel wikitext il numero tra parentesi era negativo (gol subiti, portiere). */
    val golSubiti: Boolean,
)

data class ItCareerData(
    val spells: List<ClubSpell>,
    val youthClubs: List<YouthClub>,
    val nazionalePresenze: Int?,
    val nazionaleGol: Int?,
    val nazionaleGolSubiti: Boolean,
)

/**
 * Legge spell per club, giovanili e Nazionale maggiore da it.wikipedia.org, template
 * {{Sportivo}}/{{Carriera sportivo}} (standard per gli sportivi italiani), es. per Buffon:
 * ```
 * |Squadre = {{Carriera sportivo
 *  |sport = calcio |pos = G
 *  |1995-2001|Parma|168 (-159)
 *  |2001-2018|Juventus|509 (-380)
 * }}
 * ```
 * A differenza dell'infobox inglese (dove per i portieri "goals" è sempre 0: sono i gol segnati
 * dal portiere, non subiti), qui il numero tra parentesi è scritto in chiaro nel wikitext con
 * segno: negativo = gol subiti (portiere), positivo/zero = gol segnati (tutti gli altri ruoli).
 * Non serve conoscere il ruolo in anticipo: lo dice il segno stesso. Vedi anche
 * [BestSpellPicker] per come questi spell diventano il "secondo logo" del giocatore.
 */
object WikipediaItCareerStats {

    private val redirectTargetRegex = Regex("""#REDIRECT\s*:?\s*\[\[([^\]|]+)""", RegexOption.IGNORE_CASE)

    private val squadreBlockRegex = Regex("""\|Squadre\s*=\s*\{\{Carriera sportivo(.*?)\n\s*\}\}""", RegexOption.DOT_MATCHES_ALL)
    private val giovaniliBlockRegex = Regex("""\|SquadreGiovanili\s*=\s*\{\{Carriera sportivo(.*?)\n\s*\}\}""", RegexOption.DOT_MATCHES_ALL)
    private val nazionaliBlockRegex = Regex("""\|SquadreNazionali\s*=\s*\{\{Carriera sportivo(.*?)\n\s*\}\}""", RegexOption.DOT_MATCHES_ALL)

    // Una riga dati è "|anni|club|presenze (gol)", es. "|2002-2012|Milan|224 (7)". Il gruppo club
    // riusa INFOBOX_VALUE (non un semplice "[^|]+") perché può essere un template con pipe interni,
    // es. "{{NazU|CA|ITA|M|18}}" nel blocco Nazionali: uno split ingenuo sul primo "|" spezzerebbe
    // il template a metà. Le righe di intestazione ("|sport = calcio |pos = G") non hanno un terzo
    // "|" e quindi non fanno match, niente da escludere esplicitamente.
    private val dataLineRegex = Regex("""^\s*\|([^|]+)\|($INFOBOX_VALUE)\|(.*)""")
    private val presenzeGolRegex = Regex("""(\d+)\s*\(([+-]?\d+)\)""")

    /**
     * @param expectedYear se la prima ricerca (titolo esatto) non trova nulla — tipicamente perché
     *   il titolo è una pagina di disambigua, es. "Francesco Rossi" quando esistono più calciatori
     *   con lo stesso nome — si ritenta con la convenzione di disambiguazione standard di
     *   it.wikipedia.org "Nome Cognome (calciatore ANNO)" (verificato su Francesco Rossi ->
     *   "Francesco Rossi (calciatore 1991)").
     */
    suspend fun fetch(playerName: String, expectedYear: Int? = null): ItCareerData? {
        fetchWithRetry(playerName)?.let { return it }
        if (expectedYear == null) return null
        return fetchWithRetry("$playerName (calciatore $expectedYear)")
    }

    private suspend fun fetchWithRetry(title: String): ItCareerData? =
        fetchOnce(title) ?: run { delay(600); fetchOnce(title) }

    private suspend fun fetchOnce(playerName: String, redirectsLeft: Int = 2): ItCareerData? = runCatching {
        val title = URLEncoder.encode(playerName.trim().replace(' ', '_'), "UTF-8")
        val request = Request.Builder()
            .url("https://it.wikipedia.org/w/index.php?title=$title&action=raw")
            .header("User-Agent", "ScoutTableApp/1.0 (Android scouting app; contact: n/a)")
            .build()

        lookupHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val fullText = response.body?.string() ?: return@use null
            if (fullText.contains("#REDIRECT", ignoreCase = true) && fullText.length < 300) {
                if (redirectsLeft <= 0) return@use null
                val target = redirectTargetRegex.find(fullText)?.groupValues?.get(1)?.trim()
                return@use if (target.isNullOrBlank()) null else fetchOnce(target, redirectsLeft - 1)
            }

            val wikitext = stripHtmlComments(fullText)
            val spells = squadreBlockRegex.find(wikitext)?.groupValues?.get(1)?.let(::parseSpells).orEmpty()
            val youthClubs = giovaniliBlockRegex.find(wikitext)?.groupValues?.get(1)?.let(::parseYouthClubs).orEmpty()
            val nazionale = nazionaliBlockRegex.find(wikitext)?.groupValues?.get(1)?.let(::parseSeniorNazionale)

            if (spells.isEmpty() && youthClubs.isEmpty() && nazionale == null) return@use null

            ItCareerData(
                spells = spells,
                youthClubs = youthClubs,
                nazionalePresenze = nazionale?.presenze,
                nazionaleGol = nazionale?.gol,
                nazionaleGolSubiti = nazionale?.golSubiti ?: false,
            )
        }
    }.getOrNull()

    private fun parseSpells(block: String): List<ClubSpell> =
        block.lineSequence().mapNotNull { line ->
            val m = dataLineRegex.find(line) ?: return@mapNotNull null
            val club = stripLoanArrow(cleanWikiText(m.groupValues[2]))
            if (club.isBlank()) return@mapNotNull null
            val nums = presenzeGolRegex.find(m.groupValues[3]) ?: return@mapNotNull null
            val presenze = nums.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val golSigned = nums.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            ClubSpell(club, m.groupValues[1].trim(), presenze, abs(golSigned), golSubiti = golSigned < 0)
        }.toList()

    private fun parseYouthClubs(block: String): List<YouthClub> =
        block.lineSequence().mapNotNull { line ->
            val m = dataLineRegex.find(line) ?: return@mapNotNull null
            val club = stripLoanArrow(cleanWikiText(m.groupValues[2]))
            if (club.isBlank()) return@mapNotNull null
            YouthClub(club, m.groupValues[1].trim())
        }.toList()

    // Un prestito è scritto come "→ Nome Club" (es. "→ Lilla"): la freccia non fa parte né di un
    // wikilink né di un template/ref, quindi cleanWikiText non la tocca e resterebbe nel nome del
    // club (rompendo la ricerca dello stemma su TheSportsDB, vedi PlayerLookupService.fetchTeamInfo).
    private fun stripLoanArrow(club: String): String = club.removePrefix("→").trim()

    private data class SeniorNazionale(val presenze: Int, val gol: Int, val golSubiti: Boolean)

    // Il template usato per la voce distingue la categoria: "{{NazU|...}}" = giovanile (esclusa),
    // "{{Naz|...|olimpica}}" = Olimpica (esclusa), "{{Naz|...}}" singolo = Nazionale maggiore (quella
    // da tenere) — un filtro per nome-template, più affidabile dell'euristica su en.wiki (basata su
    // regex "U-\d\d"/"youth" nel nome del team).
    private fun parseSeniorNazionale(block: String): SeniorNazionale? {
        for (line in block.lineSequence()) {
            val m = dataLineRegex.find(line) ?: continue
            val rawTeam = m.groupValues[2]
            if (rawTeam.contains("NazU")) continue
            if (rawTeam.contains("olimpica", ignoreCase = true)) continue
            if (!rawTeam.contains("Naz")) continue
            val nums = presenzeGolRegex.find(m.groupValues[3]) ?: continue
            val presenze = nums.groupValues[1].toIntOrNull() ?: continue
            val golSigned = nums.groupValues[2].toIntOrNull() ?: continue
            return SeniorNazionale(presenze, abs(golSigned), golSubiti = golSigned < 0)
        }
        return null
    }
}
