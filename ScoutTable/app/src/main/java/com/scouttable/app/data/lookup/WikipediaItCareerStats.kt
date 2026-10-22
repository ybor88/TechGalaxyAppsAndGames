// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.lookup

import com.scouttable.app.data.YouthClub
import java.net.URLEncoder
import java.text.Normalizer
import kotlin.math.abs
import kotlinx.coroutines.delay
import okhttp3.Request
import org.json.JSONObject

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
    /** true se la voce ha il blocco "SquadreNazionali", anche con sole giovanili: in quel caso
     *  [nazionalePresenze] null significa davvero zero presenze in Nazionale maggiore. */
    val hasNazionaliBlock: Boolean = false,
    /** Ruolo già in italiano (es. "Difensore centrale"), dal campo "Ruolo" di {{Sportivo}}: non
     *  serve tradurlo con [translateRole], è l'unica fonte a darlo già nella lingua giusta. */
    val ruolo: String?,
    /** Anno di nascita dal campo "AnnoNascita" di {{Bio}}: più diretto/affidabile del parsing di
     *  "birth_date" sull'infobox inglese. */
    val annoNascita: Int?,
    /** Titolo della pagina it.wikipedia effettivamente letta (dopo redirect/disambigua): serve a
     *  ricavare la voce inglese corrispondente (vedi [WikipediaItCareerStats.linkedTitle]). */
    val title: String = "",
    /** Nome dal campo "Nome" di {{Sportivo}}, con i diacritici corretti (es. "Mario Mandžukić"
     *  dove TheSportsDB scrive "Mandžukic"). */
    val nome: String? = null,
    /** Nazione dal campo "CodiceNazione" di {{Sportivo}} (es. "{{DNK}}" -> "Denmark"), in
     *  inglese come quella di TheSportsDB: unica fonte per chi TheSportsDB non conosce. */
    val nazione: String? = null,
    /** true se {{Sportivo}} ha "TermineCarriera" compilato (es. "2010 - giocatore"): ritirato. */
    val carrieraTerminata: Boolean = false,
    /** true se la voce mostra che gioca ancora: un periodo ancora aperto nel blocco "Squadre"
     *  (es. "|2023-|Atalanta|...") o il campo "Squadra" (club attuale) compilato. */
    val inAttivita: Boolean = false,
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

    // it.wikipedia usa sia "#REDIRECT" sia l'equivalente italiano "#RINVIA" (verificato su
    // "Éderson" e "Khvicha Kvaratskhelia"): seguire solo il primo lasciava quei giocatori senza
    // statistiche, Nazionale compresa.
    private val redirectTargetRegex = Regex("""#(?:REDIRECT|RINVIA)\s*:?\s*\[\[([^\]|#]+)""", RegexOption.IGNORE_CASE)
    private val disambiguaRegex = Regex("""\{\{\s*Disambigua""", RegexOption.IGNORE_CASE)
    private val bulletLinkRegex = Regex("""^\*+[^\[\n]*\[\[([^\]|#]+)""", RegexOption.MULTILINE)
    private val codiceNazioneRegex = Regex("""CodiceNazione\s*=\s*\{\{([A-Z]{3})""")
    private val nomeRegex = Regex("""(?<![A-Za-z])Nome\s*=\s*($INFOBOX_VALUE)""")

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
    private val squadraAttualeRegex = Regex("""(?<![A-Za-z])Squadra\s*=\s*($INFOBOX_VALUE)""")
    // Riga del blocco "Squadre" con periodo aperto: "|2023-|Atalanta|12 (3)" o "|2023-| ...".
    private val openSpellRegex = Regex("""^\s*\|\s*(?:19|20)\d{2}\s*[-–]\s*\|""", RegexOption.MULTILINE)
    private val termineCarrieraRegex = Regex("""(?<![A-Za-z])TermineCarriera\s*=\s*($INFOBOX_VALUE)""")
    // "185 ({{formatnum:3315}})": i punti di carriera nel basket superano spesso le migliaia e
    // vengono scritti con formatnum, che senza questa pulizia faceva saltare proprio la riga
    // del club principale (verificato su Gianmarco Pozzecco, Varese).
    // Template "contenitore" a un solo parametro attorno ad anni/squadre, es. per chi ha cambiato
    // federazione: "|{{TA|2007-2026}}|{{TA|{{Naz|CA|BIH|M}}}}|152 (73)" (verificato su Edin Džeko e
    // Miralem Pjanić). Il template annidato spezzava il parsing della riga e la Nazionale maggiore
    // spariva: vanno "aperti" lasciando solo il contenuto.
    private val wrapperTemplateRegex = Regex("""\{\{\s*(?:TA|[Nn]owrap|[Ss]mall)\s*\|((?:[^{}]|\{\{[^{}]*\}\})*)\}\}""")
    private val formatnumRegex = Regex("""\{\{\s*formatnum\s*:\s*([+-]?[\d.,]+)\s*\}\}""", RegexOption.IGNORE_CASE)

    private const val USER_AGENT = "ScoutTableApp/1.0 (Android scouting app; contact: n/a)"
    private const val MAX_CANDIDATES = 5

    /** Esito della lettura di una singola pagina: voce di un calciatore, disambigua (con le voci
     *  elencate) o niente di utile. */
    private sealed class PageResult {
        data class Player(val data: ItCareerData) : PageResult()
        data class Disambig(val titles: List<String>) : PageResult()
        object None : PageResult()
    }

    /**
     * Trova e legge la voce del calciatore. In ordine:
     * 1. [exactTitle], se l'utente ha incollato l'URL della pagina ([trustExactTitle]: niente
     *    controllo sull'anno, la voce l'ha scelta l'utente) o se l'ha trovata una ricerca: identifica il giocatore senza
     *    ambiguità (prima l'URL veniva ridotto a "Nome Anno" e la ricerca poteva finire su un
     *    omonimo: verificato su "Thomas Kristensen (calciatore 2002)", che diventava il Kristensen
     *    del 1983);
     * 2. il titolo esatto [playerName];
     * 3. la convenzione di disambiguazione "Nome Cognome (calciatore ANNO)" (es. "Francesco Rossi
     *    (calciatore 1991)");
     * 4. le voci elencate nella disambigua e quelle della ricerca full-text "Nome calciatore"
     *    (copre anche i nomi senza diacritici di TheSportsDB, es. "Mario Mandžukic" ->
     *    "Mario Mandžukić").
     * Una voce con anno di nascita diverso da [expectedYear] è un omonimo e viene scartata; senza
     * anno, tra più candidati vince quello che ha giocato nella squadra [teamHint].
     */
    suspend fun fetch(
        playerName: String,
        expectedYear: Int? = null,
        exactTitle: String? = null,
        teamHint: String? = null,
        trustExactTitle: Boolean = true,
    ): ItCareerData? {
        val tried = mutableSetOf<String>()
        val disambigTitles = mutableListOf<String>()

        suspend fun tryTitle(title: String, trustUnconditionally: Boolean = false): ItCareerData? {
            if (!tried.add(normalizeTitle(title))) return null
            return when (val page = fetchWithRetry(title)) {
                is PageResult.Player -> page.data.takeIf { trustUnconditionally || yearMatches(it, expectedYear) }
                is PageResult.Disambig -> { disambigTitles += page.titles; null }
                PageResult.None -> null
            }
        }

        exactTitle?.let { tryTitle(it, trustUnconditionally = trustExactTitle)?.let { data -> return data } }
        tryTitle(playerName)?.let { return it }
        if (expectedYear != null) tryTitle("$playerName (calciatore $expectedYear)")?.let { return it }

        // Candidati: voci della disambigua che citano il cognome + ricerca full-text.
        val surname = surnameOf(playerName)
        val candidates = (disambigTitles.filter { surname.isNotBlank() && normalizeForMatch(it).contains(surname) } +
            searchTitles("$playerName calciatore"))
            .distinctBy(::normalizeTitle)
            .filter { normalizeTitle(it) !in tried }
            .take(MAX_CANDIDATES)

        val found = mutableListOf<ItCareerData>()
        for (title in candidates) {
            tried += normalizeTitle(title)
            val data = (fetchWithRetry(title) as? PageResult.Player)?.data ?: continue
            if (data.spells.isEmpty() && data.nazionalePresenze == null) continue
            if (!nameMatches(surname, data)) continue
            if (expectedYear != null) {
                if (data.annoNascita == expectedYear) return data
                continue
            }
            found += data
        }
        if (found.isEmpty()) return null
        if (!teamHint.isNullOrBlank()) {
            found.firstOrNull { data -> data.spells.any { clubNamesMatch(it.club, teamHint) } }?.let { return it }
        }
        return found.first()
    }

    /**
     * Solo la voce indicata, senza ricerche di ripiego per nome (che cercano "Nome calciatore" e
     * nel basket troverebbero omonimi): per l'URL it.wikipedia di un cestista incollato dall'utente.
     */
    suspend fun fetchExact(title: String): ItCareerData? = (fetchWithRetry(title) as? PageResult.Player)?.data

    /** Titolo della voce collegata (langlinks) in un'altra lingua, es. it "Éderson José dos Santos
     *  Lourenço da Silva" -> en "Éderson (footballer, born 1999)". */
    fun linkedTitle(fromLang: String, title: String, toLang: String): String? = runCatching {
        val encoded = URLEncoder.encode(title, "UTF-8")
        val request = Request.Builder()
            .url("https://$fromLang.wikipedia.org/w/api.php?action=query&prop=langlinks&lllang=$toLang&redirects=1&format=json&titles=$encoded")
            .header("User-Agent", USER_AGENT)
            .build()
        lookupHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val pages = JSONObject(response.body?.string() ?: return@use null)
                .optJSONObject("query")?.optJSONObject("pages") ?: return@use null
            val key = pages.keys().asSequence().firstOrNull() ?: return@use null
            pages.optJSONObject(key)?.optJSONArray("langlinks")?.optJSONObject(0)?.optString("*")?.ifBlank { null }
        }
    }.getOrNull()

    private fun yearMatches(data: ItCareerData, expectedYear: Int?): Boolean =
        expectedYear == null || data.annoNascita == null || data.annoNascita == expectedYear

    private fun surnameOf(playerName: String): String = normalizeForMatch(playerName).split(' ').lastOrNull().orEmpty()

    // Un risultato della ricerca full-text può essere una voce qualunque che cita il giocatore
    // (es. la stagione di un club): deve almeno contenere il cognome cercato nel titolo o nel nome.
    private fun nameMatches(surname: String, data: ItCareerData): Boolean =
        surname.isBlank() || normalizeForMatch(data.title).contains(surname) ||
            normalizeForMatch(data.nome.orEmpty()).contains(surname)

    private fun normalizeTitle(title: String): String = title.trim().replace('_', ' ').lowercase()

    private suspend fun fetchWithRetry(title: String): PageResult {
        fetchOnce(title)?.let { return it }
        delay(600)
        return fetchOnce(title) ?: PageResult.None
    }

    /** null = errore di rete (da ritentare); [PageResult.None] = pagina assente o senza dati utili. */
    private fun fetchOnce(playerName: String, redirectsLeft: Int = 2): PageResult? = runCatching {
        val title = URLEncoder.encode(playerName.trim().replace(' ', '_'), "UTF-8")
        val request = Request.Builder()
            .url("https://it.wikipedia.org/w/index.php?title=$title&action=raw")
            .header("User-Agent", USER_AGENT)
            .build()

        lookupHttpClient.newCall(request).execute().use { response ->
            if (response.code == 404) return@use PageResult.None
            if (!response.isSuccessful) return@use null
            val fullText = response.body?.string() ?: return@use null
            if (fullText.length < 500) {
                redirectTargetRegex.find(fullText)?.let { redirect ->
                    if (redirectsLeft <= 0) return@use PageResult.None
                    return@use fetchOnce(redirect.groupValues[1].trim(), redirectsLeft - 1)
                }
            }
            if (disambiguaRegex.containsMatchIn(fullText)) {
                return@use PageResult.Disambig(bulletLinkRegex.findAll(fullText).map { it.groupValues[1].trim() }.toList())
            }

            val wikitext = unwrapTemplates(
                stripHtmlComments(fullText).replace(formatnumRegex) { it.groupValues[1].replace(".", "").replace(",", "") }
            )
            val spells = squadreBlockRegex.find(wikitext)?.groupValues?.get(1)?.let(::parseSpells).orEmpty()
            val youthClubs = giovaniliBlockRegex.find(wikitext)?.groupValues?.get(1)?.let(::parseYouthClubs).orEmpty()
            val nazionaliBlock = nazionaliBlockRegex.find(wikitext)?.groupValues?.get(1)
            val nazionale = nazionaliBlock?.let(::parseSeniorNazionale)
            val ruolo = ruoloRegex.find(wikitext)?.groupValues?.get(1)?.let(::cleanWikiText)?.ifBlank { null }
            val annoNascita = annoNascitaRegex.find(wikitext)?.groupValues?.get(1)?.toIntOrNull()
            val nome = nomeRegex.find(wikitext)?.groupValues?.get(1)?.let(::cleanWikiText)?.ifBlank { null }
            val nazione = codiceNazioneRegex.find(wikitext)?.groupValues?.get(1)?.let(::countryFromCode)
            val carrieraTerminata = termineCarrieraRegex.find(wikitext)?.groupValues?.get(1)
                ?.let(::cleanWikiText)?.isNotBlank() == true
            // Il campo "Squadra" può contenere un template di club ("{{Calcio Inter|G}}"), che
            // cleanWikiText eliminerebbe: basta che il valore grezzo non sia vuoto.
            val inAttivita = !carrieraTerminata && (
                squadreBlockRegex.find(wikitext)?.groupValues?.get(1)?.let { openSpellRegex.containsMatchIn(it) } == true ||
                    squadraAttualeRegex.find(wikitext)?.groupValues?.get(1)?.isNotBlank() == true
                )

            if (spells.isEmpty() && youthClubs.isEmpty() && nazionale == null && ruolo == null) return@use PageResult.None

            PageResult.Player(
                ItCareerData(
                    spells = spells,
                    youthClubs = youthClubs,
                    nazionalePresenze = nazionale?.presenze,
                    nazionaleGol = nazionale?.gol,
                    nazionaleGolSubiti = nazionale?.golSubiti ?: false,
                    hasNazionaliBlock = nazionaliBlock != null,
                    ruolo = ruolo,
                    annoNascita = annoNascita,
                    title = playerName.trim().replace('_', ' '),
                    nome = nome,
                    nazione = nazione,
                    carrieraTerminata = carrieraTerminata,
                    inAttivita = inAttivita,
                )
            )
        }
    }.getOrNull()

    private fun unwrapTemplates(text: String): String {
        var current = text
        repeat(3) {
            val next = current.replace(wrapperTemplateRegex) { it.groupValues[1] }
            if (next == current) return next
            current = next
        }
        return current
    }

    /** Ricerca full-text su it.wikipedia: titoli delle prime voci, in ordine di rilevanza. */
    private fun searchTitles(query: String): List<String> = runCatching {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("https://it.wikipedia.org/w/api.php?action=query&list=search&srlimit=$MAX_CANDIDATES&format=json&srsearch=$encoded")
            .header("User-Agent", USER_AGENT)
            .build()
        lookupHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use emptyList()
            val results = JSONObject(response.body?.string() ?: return@use emptyList())
                .optJSONObject("query")?.optJSONArray("search") ?: return@use emptyList()
            (0 until results.length()).mapNotNull { results.optJSONObject(it)?.optString("title")?.ifBlank { null } }
        }
    }.getOrDefault(emptyList())

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

/** "Mandžukić" -> "mandzukic": minuscolo, senza diacritici né punteggiatura, per confronti di nomi. */
internal fun normalizeForMatch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

// Sigle societarie e parole generiche che non distinguono un club da un altro ("FC Bayern" / "Bayern").
private val clubNoiseTokens = setOf(
    "club", "calcio", "football", "futbol", "futebol", "soccer", "sporting", "athletic", "atletico",
    "united", "city", "real", "fussball", "sport", "sports", "associazione", "societa",
)

/**
 * Vero se due nomi di club indicano (plausibilmente) la stessa squadra scritta da fonti diverse:
 * es. "Bayern Monaco" (it.wikipedia) e "Bayern Munich" (statmuse/TheSportsDB) hanno in comune
 * "bayern". Basta una parola significativa in comune (almeno 4 lettere, non una sigla societaria
 * né una parola generica come "city"/"united", che accomunerebbero club diversi).
 */
internal fun clubNamesMatch(a: String, b: String): Boolean {
    fun tokens(s: String) = normalizeForMatch(s).split(' ').filter { it.length >= 4 && it !in clubNoiseTokens }.toSet()
    val ta = tokens(a)
    val tb = tokens(b)
    if (ta.isEmpty() || tb.isEmpty()) return normalizeForMatch(a) == normalizeForMatch(b)
    return ta.any { it in tb }
}

// Codici delle nazioni britanniche usati da it.wikipedia, che non sono codici ISO.
private val britishCodes = mapOf("ENG" to "England", "SCO" to "Scotland", "WAL" to "Wales", "NIR" to "Northern Ireland")

private val countryByIso3: Map<String, String> by lazy {
    java.util.Locale.getISOCountries().mapNotNull { iso2 ->
        val locale = java.util.Locale("", iso2)
        runCatching { locale.isO3Country }.getOrNull()?.ifBlank { null }?.let { it to locale.getDisplayCountry(java.util.Locale.ENGLISH) }
    }.toMap()
}

/** "DNK" -> "Denmark": nome inglese del paese (come lo scrive TheSportsDB) da un codice ISO a 3 lettere. */
internal fun countryFromCode(code: String): String? = britishCodes[code] ?: countryByIso3[code]

