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
    /** Ruolo già in italiano (es. "Difensore centrale"), dal campo "Ruolo" di {{Sportivo}}: non
     *  serve tradurlo con [translateRole], è l'unica fonte a darlo già nella lingua giusta. */
    val ruolo: String?,
    /** Anno di nascita dal campo "AnnoNascita" di {{Bio}}: più diretto/affidabile del parsing di
     *  "birth_date" sull'infobox inglese. */
    val annoNascita: Int?,
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
    private val ruoloRegex = Regex("""(?<![A-Za-z])Ruolo\s*=\s*($INFOBOX_VALUE)""")
    private val annoNascitaRegex = Regex("""(?<![A-Za-z])AnnoNascita\s*=\s*(\d{4})""")

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
            val ruolo = ruoloRegex.find(wikitext)?.groupValues?.get(1)?.let(::cleanWikiText)?.ifBlank { null }
            val annoNascita = annoNascitaRegex.find(wikitext)?.groupValues?.get(1)?.toIntOrNull()

            if (spells.isEmpty() && youthClubs.isEmpty() && nazionale == null && ruolo == null) return@use null

            ItCareerData(
                spells = spells,
                youthClubs = youthClubs,
                nazionalePresenze = nazionale?.presenze,
                nazionaleGol = nazionale?.gol,
                nazionaleGolSubiti = nazionale?.golSubiti ?: false,
                ruolo = ruolo,
                annoNascita = annoNascita,
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

    private val senzaNazTemplateRegex = Regex("""\{\{Naz\|([^}]*)\}\}""")

    // Il template "{{NazU|...}}" (giovanile) è già escluso a monte: il suo nome non contiene "|"
    // subito dopo "Naz", quindi senzaNazTemplateRegex ("Naz" seguito da "|") non lo intercetta.
    // Tra le voci "{{Naz|...}}" restanti, la Nazionale maggiore VERA ha il terzo parametro (sesso,
    // dopo sport e codice nazione) esattamente "M": le rappresentative minori (B, Olimpica, beach
    // soccer...) lasciano quel parametro vuoto e mettono la propria etichetta in un parametro
    // successivo — verificato su Davide Zappacosta, che ha sia "{{Naz|CA|ITA||B}}" (Italia B, 1
    // presenza, terzo parametro vuoto) sia "{{Naz|CA|ITA|M}}" (Nazionale maggiore vera, 14
    // presenze): senza questo controllo veniva presa la prima trovata (l'Italia B) invece della
    // maggiore, che compare più avanti nel blocco.
    private fun isSeniorNazionale(rawTeam: String): Boolean {
        val params = senzaNazTemplateRegex.find(rawTeam)?.groupValues?.get(1)?.split('|') ?: return false
        return params.getOrNull(2)?.trim() == "M"
    }

    private fun parseSeniorNazionale(block: String): SeniorNazionale? {
        for (line in block.lineSequence()) {
            val m = dataLineRegex.find(line) ?: continue
            if (!isSeniorNazionale(m.groupValues[2])) continue
            val nums = presenzeGolRegex.find(m.groupValues[3]) ?: continue
            val presenze = nums.groupValues[1].toIntOrNull() ?: continue
            val golSigned = nums.groupValues[2].toIntOrNull() ?: continue
            return SeniorNazionale(presenze, abs(golSigned), golSubiti = golSigned < 0)
        }
        return null
    }
}
