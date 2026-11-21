// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.talent

import com.scouttable.app.data.Sport
import com.scouttable.app.data.lookup.lookupHttpClient
import java.io.IOException
import java.time.LocalDate
import java.time.Period
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

/** Un presunto talento: giovane (al massimo 25 anni) con una voce su Wikidata per lo sport scelto. */
data class Talent(
    val nome: String,
    val nascita: LocalDate,
    val eta: Int,
    val squadra: String,
    val ruolo: String,
    /** Numero di voci Wikipedia nelle varie lingue: misura quanto è già noto (ordina la lista). */
    val notorieta: Int,
    /** Voce it.wikipedia (o en se manca), da aprire nel browser. */
    val link: String?,
)

/**
 * "Talenti": giovani atleti di una nazione per lo sport della card, cercati in rete su Wikidata
 * (query.wikidata.org). Criteri: professione dello sport (es. "calciatore"), cittadinanza della
 * nazione scelta sul globo, al massimo [MAX_ETA] anni oggi, vivente; ordinati per notorietà (voci
 * Wikipedia in più lingue), i primi [LIMITE]. Sono "presunti" talenti: chi è giovane e già noto.
 */
object TalentSearch {

    const val MAX_ETA = 25
    private const val LIMITE = 40
    private const val ENDPOINT = "https://query.wikidata.org/sparql"

    /** Professioni Wikidata (P106) per sport: interrogare la professione è molto più veloce dello sport (P641). */
    fun occupations(sport: Sport): List<String> = when (sport) {
        Sport.CALCIO -> listOf("Q937857") // calciatore
        Sport.BASKET, Sport.BASKET_REGIONALE -> listOf("Q3665646") // cestista
        Sport.MOTOGP -> listOf("Q3014296") // pilota motociclistico
        Sport.FORMULA1 -> listOf("Q10349745") // pilota automobilistico
        Sport.PUGILATO -> listOf("Q11338576") // pugile
        Sport.MMA -> listOf("Q11607585") // lottatore di arti marziali miste
        Sport.KICKBOXING -> listOf("Q11296761") // kickboxer
        Sport.SUMO -> listOf("Q2727289") // rikishi
        Sport.WRESTLING -> listOf("Q13474373") // wrestler
        Sport.FOOTBALL -> listOf("Q19204627") // giocatore di football americano
        Sport.RUGBY -> listOf("Q14089670") // rugbista a 15
        Sport.HOCKEY -> listOf("Q11774891") // hockeista su ghiaccio
    }

    // Le query di Wikidata durano fino a ~45 s per le nazioni più grandi (cestisti USA).
    private val client by lazy {
        lookupHttpClient.newBuilder().readTimeout(75, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()
    }
    private val cache = mutableMapOf<String, List<Talent>>()

    /** Wikidata ha risposto "troppe richieste" anche dopo l'attesa indicata. */
    class BusyException(val retryAfterSeconds: Int) : IOException("Wikidata occupato")

    /**
     * Talenti di [sport] con cittadinanza [iso2] (es. "IT"). In memoria per la sessione; se
     * Wikidata limita le richieste, aspetta il tempo indicato (fino a 60 s, [onWait]) e riprova una volta.
     */
    suspend fun search(sport: Sport, iso2: String, today: LocalDate = LocalDate.now(), onWait: (Int) -> Unit = {}): List<Talent> {
        val key = "${occupations(sport).joinToString()}|$iso2|$today"
        synchronized(cache) { cache[key] }?.let { return it }
        val query = buildQuery(occupations(sport), iso2, today)
        var json = withContext(Dispatchers.IO) { fetch(query) }
        if (json is Fetch.Busy) {
            val wait = json.seconds.coerceIn(5, 60)
            onWait(wait)
            delay(wait * 1000L)
            json = withContext(Dispatchers.IO) { fetch(query) }
        }
        return when (json) {
            is Fetch.Busy -> throw BusyException(json.seconds)
            is Fetch.Ok -> parse(json.body, today).also { synchronized(cache) { cache[key] = it } }
        }
    }

    private sealed interface Fetch {
        class Ok(val body: String) : Fetch
        class Busy(val seconds: Int) : Fetch
    }

    private fun fetch(query: String): Fetch {
        val url = ENDPOINT.toHttpUrl().newBuilder().addQueryParameter("query", query).addQueryParameter("format", "json").build()
        val request = Request.Builder().url(url)
            .header("User-Agent", "ScoutTable/1.0 (Android app)")
            .header("Accept", "application/sparql-results+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 429) return Fetch.Busy(response.header("Retry-After")?.trim()?.toIntOrNull() ?: 60)
            if (!response.isSuccessful) throw IOException("Wikidata ha risposto ${response.code}")
            return Fetch.Ok(response.body?.string().orEmpty())
        }
    }

    /** Nato dopo questa data = al massimo [MAX_ETA] anni oggi (compie i 26 dopo oggi). */
    fun bornAfter(today: LocalDate): LocalDate = today.minusYears(MAX_ETA + 1L)

    fun buildQuery(occupations: List<String>, iso2: String, today: LocalDate): String {
        require(iso2.matches(Regex("[A-Z]{2}"))) { "codice nazione non valido: $iso2" }
        val values = occupations.joinToString(" ") { "wd:$it" }
        return """
            SELECT ?p ?pLabel ?dob ?links ?itArt ?enArt
              (GROUP_CONCAT(DISTINCT CONCAT(COALESCE(STR(?inizio), ""), "~", ?tLabel); separator="¦") AS ?teams)
              (SAMPLE(?poLabel) AS ?pos)
            WHERE {
              { SELECT DISTINCT ?p ?dob ?links WHERE {
                  ?country wdt:P297 "$iso2" .
                  VALUES ?o { $values }
                  ?p wdt:P106 ?o ; wdt:P27 ?country ; wdt:P569 ?dob ; wikibase:sitelinks ?links .
                  FILTER(?dob > "${bornAfter(today)}T00:00:00Z"^^xsd:dateTime)
                  FILTER NOT EXISTS { ?p wdt:P570 ?morte }
                } ORDER BY DESC(?links) LIMIT $LIMITE }
              OPTIONAL { ?p p:P54 ?st . ?st ps:P54 ?t . FILTER NOT EXISTS { ?st pq:P582 ?fine } OPTIONAL { ?st pq:P580 ?inizio } }
              OPTIONAL { ?p wdt:P413 ?po . }
              OPTIONAL { ?itArt schema:about ?p ; schema:isPartOf <https://it.wikipedia.org/> . }
              OPTIONAL { ?enArt schema:about ?p ; schema:isPartOf <https://en.wikipedia.org/> . }
              SERVICE wikibase:label {
                bd:serviceParam wikibase:language "it,en,mul" .
                ?p rdfs:label ?pLabel . ?t rdfs:label ?tLabel . ?po rdfs:label ?poLabel .
              }
            }
            GROUP BY ?p ?pLabel ?dob ?links ?itArt ?enArt
            ORDER BY DESC(?links)
        """.trimIndent()
    }

    private val nationalTeam = Regex("""^(nazionale|selezione)\b|national\b|\bU-?\d{2}\b|under[ -]?\d{2}""", RegexOption.IGNORE_CASE)

    /** Risposta JSON di Wikidata -> talenti (un atleta compare una volta sola, i più noti prima). */
    fun parse(body: String, today: LocalDate): List<Talent> {
        val rows = JSONObject(body).getJSONObject("results").getJSONArray("bindings")
        val seen = mutableSetOf<String>()
        val out = mutableListOf<Talent>()
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            fun v(name: String) = r.optJSONObject(name)?.optString("value").orEmpty()
            val id = v("p")
            if (!seen.add(id)) continue
            val nascita = runCatching { LocalDate.parse(v("dob").take(10)) }.getOrNull() ?: continue
            val eta = Period.between(nascita, today).years
            if (eta > MAX_ETA || eta < 0) continue
            val nome = v("pLabel").takeUnless { it.isBlank() || it.matches(Regex("Q\\d+")) } ?: continue
            out += Talent(
                nome = nome,
                nascita = nascita,
                eta = eta,
                squadra = currentTeam(v("teams")),
                ruolo = v("pos").takeUnless { it.matches(Regex("Q\\d+")) }.orEmpty(),
                notorieta = v("links").toIntOrNull() ?: 0,
                link = v("itArt").ifBlank { v("enArt") }.ifBlank { null },
            )
        }
        return out.sortedByDescending { it.notorieta }
    }

    /** Squadra attuale: tra quelle senza data di fine, la più recente; le nazionali e le giovanili no. */
    fun currentTeam(concat: String): String =
        concat.split('¦').mapNotNull { entry ->
            val start = entry.substringBefore('~')
            val label = entry.substringAfter('~', "").trim()
            if (label.isBlank() || label.matches(Regex("Q\\d+")) || nationalTeam.containsMatchIn(label)) null else start to label
        }.maxByOrNull { it.first }?.second.orEmpty()
}
