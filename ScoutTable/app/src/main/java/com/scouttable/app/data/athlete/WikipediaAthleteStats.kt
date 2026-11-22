// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.athlete

import com.scouttable.app.data.PlayerStatus
import com.scouttable.app.data.Sport
import com.scouttable.app.data.importexport.PlayerImportRow
import com.scouttable.app.data.lookup.WikipediaItCareerStats
import com.scouttable.app.data.lookup.countryFromCode
import com.scouttable.app.data.lookup.lookupHttpClient
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/** Statistiche di un atleta lette dalla voce it.wikipedia.org (vedi [WikipediaAthleteStats.parse]). */
data class AthleteData(
    val title: String,
    val nome: String,
    val anno: Int,
    val nazione: String,
    val female: Boolean,
    val retired: Boolean,
    /** Scuderia principale (MotoGP/F1) o categoria di peso (Lotta). */
    val team: String,
    /** "Motomondiale", "Formula 1" o la disciplina della lotta (es. "Pugilato"). */
    val competizione: String,
    val stagioni: String,
    val imageFile: String?,
    // MotoGP / F1
    val gare: Int = 0,
    val punti: Int = 0,
    val podi: Int = 0,
    val gareVinte: Int = 0,
    val mondiali: Int = 0,
    val pole: Int = 0,
    val giriVeloci: Int = 0,
    // Lotta
    val incontri: Int = 0,
    val vinti: Int = 0,
    val vintiKo: Int = 0,
    val persi: Int = 0,
    val persiKo: Int = 0,
    val pareggi: Int = 0,
    val titoli: Int = 0,
    /** Wrestling: numero del wrestler su cagematch.net citato dalla voce (per leggerne il record). */
    val cagematchNr: Int? = null,
    /** Wrestling: pagina profightdb.com citata dalla voce (es. "hulk-hogan-19"), fonte di riserva. */
    val profightdbSlug: String? = null,
    /** MotoGP: logo della casa costruttrice preso da en.wikipedia (URL), se trovato. */
    val teamLogo: String? = null,
    /** Football / Rugby / Hockey: voce it.wikipedia della squadra principale, se collegata nella carriera. */
    val teamLink: String? = null,
    /** Football / Rugby / Hockey: stagione (o periodo, nel rugby) migliore, vedi [BestSeason]. */
    val best: BestSeason? = null,
    // Football / Rugby / Hockey (vedi TeamSportStats)
    /** Ruolo in campo (es. "Quarterback", "Terza linea centro", "Centro"). */
    val ruolo: String = "",
    /** Partite nei club (rugby, hockey) o stagioni giocate (football). */
    val partite: Int = 0,
    /** Punti (rugby) o gol (hockey) nei club. */
    val segnati: Int = 0,
    /** Assist nei club (hockey). */
    val assistTeam: Int = 0,
    val partiteNaz: Int = 0,
    val segnatiNaz: Int = 0,
    val assistNaz: Int = 0,
    val touchdown: Int = 0,
    val yard: Int = 0,
    val tackleFb: Int = 0,
    val sack: Int = 0,
    val intercetti: Int = 0,
    val proBowl: Int = 0,
    val allPro: Int = 0,
    val mvp: Int = 0,
    /** Lingua della voce letta: "it" di norma, "en" per gli sport da combattimento senza voce italiana. */
    val lang: String = "it",
)

/**
 * Legge gli atleti dal wikitext di it.wikipedia.org, template {{Sportivo}}:
 * - MotoGP e Formula 1: il blocco {{Sport motoristici}} della categoria giusta ("Motomondiale" o
 *   "Formula 1"; gli altri, es. rally o sportprototipi, sono ignorati) con mondiali, disputati,
 *   vinti, podi, pole, puntiottenuti, giriveloci e scuderie;
 * - Lotta (pugilato, lotta, MMA): il template {{Carriera pugile}} (o un altro "Carriera ...")
 *   del campo "Incontri" con disputati, vinti [KO], persi [KO], pareggi, più i titoli mondiali
 *   elencati in {{Titoli pugile}}.
 * - Football americano, Rugby, Hockey: vedi [TeamSportStats].
 * Nome, sesso e nazione da {{Sportivo}}, anno di nascita da {{Bio}}.
 */
object WikipediaAthleteStats {

    private const val USER_AGENT = "ScoutTableApp/1.0 (Android scouting app)"

    sealed class Result {
        data class Found(val data: AthleteData) : Result()
        data class Missing(val message: String) : Result()
    }

    // ---------------------------------------------------------------- rete

    /** Titolo della voce it.wikipedia da un link (anche en.wikipedia, tradotto con i langlinks). */
    fun titleFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val m = Regex("""(\w+)(?:\.m)?\.wikipedia\.org/wiki/([^?#\s]+)""", RegexOption.IGNORE_CASE).find(url) ?: return null
        val lang = m.groupValues[1].lowercase()
        val title = URLDecoder.decode(m.groupValues[2], "UTF-8").replace('_', ' ').trim()
        return if (lang == "it") title else WikipediaItCareerStats.linkedTitle(lang, title, "it")
    }

    /** Titolo della voce en.wikipedia se [url] è un link inglese, altrimenti null. */
    private fun englishTitleFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val m = Regex("""\ben(?:\.m)?\.wikipedia\.org/wiki/([^?#\s]+)""", RegexOption.IGNORE_CASE).find(url) ?: return null
        return URLDecoder.decode(m.groupValues[1], "UTF-8").replace('_', ' ').trim()
    }

    suspend fun fetch(sport: Sport, name: String, url: String?): Result = withContext(Dispatchers.IO) {
        val itTitle = titleFromUrl(url)
        val enUrlTitle = englishTitleFromUrl(url)
        // Sport da combattimento con un link inglese senza voce italiana collegata: si legge
        // direttamente la voce inglese (prima l'atleta veniva scartato o confuso con un omonimo).
        if (sport.isCombat && enUrlTitle != null && itTitle == null) {
            return@withContext fetchEnglishFighter(sport, enUrlTitle)
        }
        val title = itTitle ?: name.trim()
        val text = rawWikitext(title) ?: run {
            delay(800)
            rawWikitext(title)
        } ?: return@withContext if (sport.isCombat) fetchEnglishFighter(sport, enUrlTitle ?: name.trim())
        else Result.Missing("voce \"$title\" non trovata su it.wikipedia.org")
        val parsed = parse(sport, text.second, text.first)
        // MotoGP: la voce italiana spesso non indica la squadra (es. Valentino Rossi); quella inglese
        // ha "MotoGP Manufacturers" con gli anni: si prende la casa con più stagioni e il suo logo.
        if (sport == Sport.MOTOGP && parsed is Result.Found && parsed.data.team.isBlank()) {
            return@withContext Result.Found(motoGpTeamFromEnglish(parsed.data))
        }
        if (sport.isTeamSport && parsed is Result.Found) return@withContext completeTeamSport(sport, parsed.data)
        if (!sport.isCombat || sport == Sport.WRESTLING || parsed !is Result.Found) return@withContext parsed
        // Sport da combattimento: record e titoli mancanti su it.wikipedia si completano dalla voce
        // inglese dello stesso atleta (infobox con il record, sezione dei titoli, yūshō del sumo).
        val enTitle = runCatching { WikipediaItCareerStats.linkedTitle("it", parsed.data.title, "en") }.getOrNull()
        val enText = enTitle?.let { rawWikitext(it, lang = "en")?.second }
        val data = if (enText != null) EnglishFightStats.enrich(sport, parsed.data, enText) else parsed.data
        if (data.incontri == 0 && data.titoli == 0) {
            Result.Missing("la voce \"${data.title}\" non riporta il record (vittorie e sconfitte) né titoli, nemmeno su en.wikipedia")
        } else Result.Found(data)
    }

    /**
     * Sport da combattimento letti dalla sola voce en.wikipedia (atleta senza voce italiana):
     * anagrafica dall'infobox (name, birth_date, nationality / birth_place, image, weight_class) e
     * record/titoli con [EnglishFightStats]. Il sesso dalle categorie ("female boxers", "women's").
     */
    private fun fetchEnglishFighter(sport: Sport, enTitle: String): Result {
        val (title, raw) = rawWikitext(enTitle, lang = "en")
            ?: return Result.Missing("voce \"$enTitle\" non trovata né su it.wikipedia.org né su en.wikipedia.org")
        val text = raw.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
        val infobox = WikiTemplates.findPrefixed(text, "Infobox").firstOrNull()?.let(WikiTemplates::params).orEmpty()
        fun field(vararg keys: String): String =
            keys.firstNotNullOfOrNull { k -> infobox[k]?.let(WikiTemplates::clean)?.ifBlank { null } }.orEmpty()
        val nome = field("name").split(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)).first().trim()
            .ifBlank { title.replace(Regex("""\s*\([^)]*\)"""), "") }
        val anno = Regex("""\b(1[89]\d{2}|20\d{2})\b""").find(infobox["birth_date"].orEmpty())?.value?.toIntOrNull() ?: 0
        val nazione = field("nationality", "country").let { raw ->
            raw.takeIf { it.isNotBlank() }?.let { com.scouttable.app.data.lookup.countryFromDemonym(it) ?: englishCountry(it.split(',', '/').first()) }
        } ?: field("birth_place").split(',').lastOrNull()?.let(::englishCountry).orEmpty()
        val female = Regex("""\[\[Category:[^\]]*\b(?:female|women's|sportswomen)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val data = AthleteData(
            title = title,
            nome = nome,
            anno = anno,
            nazione = nazione,
            female = female,
            retired = field("death_date").isNotBlank() || field("retired").isNotBlank(),
            team = field("weight_class", "weight class", "division", "weight").split(',', '/').first().trim(),
            competizione = sport.label,
            stagioni = field("years_active", "years active"),
            imageFile = infobox["image"]?.let(WikiTemplates::clean)?.removePrefix("File:")?.removePrefix("file:")?.trim()?.ifBlank { null },
            cagematchNr = CagematchStats.wrestlerNr(text),
            profightdbSlug = ProfightdbStats.slug(text),
            lang = "en",
        )
        // Wrestling: il record arriva da cagematch/profightdb (vedi PlayerLookupService.lookupWrestler).
        if (sport == Sport.WRESTLING) return Result.Found(data)
        val enriched = EnglishFightStats.enrich(sport, data, text)
        return if (enriched.incontri == 0 && enriched.titoli == 0) {
            Result.Missing("la voce inglese \"$title\" non riporta il record (vittorie e sconfitte) né titoli")
        } else Result.Found(enriched)
    }

    /** "U.S." / "USA" / "England" / "Japan" -> nome del paese in inglese come in CountryFlags. */
    private fun englishCountry(raw: String): String {
        val c = raw.replace(Regex("""\[\d+]"""), "").trim().trimEnd('.')
        return when (c.uppercase().replace(".", "")) {
            "US", "USA", "UNITED STATES OF AMERICA" -> "United States"
            "UK" -> "United Kingdom"
            else -> c
        }
    }

    /**
     * Football: yard, touchdown, tackle, sack e intercetti dall'infobox della voce inglese (quella
     * italiana non li riporta). Scartato solo chi resta senza nessuna statistica.
     */
    private fun completeTeamSport(sport: Sport, parsed: AthleteData): Result {
        // Football e hockey: la voce inglese ha la tabella stagione per stagione (stagione migliore);
        // il rugby no, il periodo migliore viene già dai club di it.wikipedia.
        // Hockey: se la voce italiana ha la tabella delle stagioni, la stagione migliore viene già da lì.
        val enText = if (sport == Sport.RUGBY || (sport == Sport.HOCKEY && parsed.best != null)) null else {
            val enTitle = runCatching { WikipediaItCareerStats.linkedTitle("it", parsed.title, "en") }.getOrNull()
            enTitle?.let { rawWikitext(it, lang = "en")?.second }
        }
        val data = when (sport) {
            Sport.FOOTBALL -> {
                val en = enText?.let(TeamSportStats::englishFootball)
                val enriched = if (en != null) TeamSportStats.enrichFootball(parsed, en) else parsed
                enriched.copy(best = enText?.let(TeamSportStats::englishFootballBestSeason))
            }
            Sport.HOCKEY -> if (parsed.best != null) parsed else parsed.copy(best = enText?.let(TeamSportStats::englishHockeyBestSeason))
            else -> parsed
        }
        return if (data.partite == 0 && data.partiteNaz == 0) {
            Result.Missing("la voce \"${data.title}\" non riporta partite né stagioni giocate")
        } else Result.Found(data)
    }

    private fun motoGpTeamFromEnglish(data: AthleteData): AthleteData {
        val (team, logo) = motoGpTeamByItTitle(data.title) ?: return data
        return data.copy(team = team, teamLogo = logo)
    }

    /** (Casa costruttrice principale, URL del logo) di un pilota MotoGP dalla voce inglese. */
    private fun motoGpTeamByItTitle(itTitle: String): Pair<String, String?>? {
        val enTitle = runCatching { WikipediaItCareerStats.linkedTitle("it", itTitle, "en") }.getOrNull() ?: return null
        val enText = rawWikitext(enTitle, lang = "en")?.second ?: return null
        val (team, link) = EnglishMotoStats.mainManufacturer(enText) ?: return null
        val logoFile = link?.let { rawWikitext(it, lang = "en")?.second }?.let(EnglishMotoStats::logoFile)
        return team to logoFile?.let(EnglishMotoStats::fileUrl)
    }

    /**
     * Scuderia di un pilota MotoGP già in lista senza squadra (importato prima che la si leggesse
     * dalla voce inglese): titolo it.wikipedia dal link salvato, altrimenti dal nome.
     */
    suspend fun motoGpTeam(sourceUrl: String?, nome: String): Pair<String, String?>? = withContext(Dispatchers.IO) {
        val title = runCatching { titleFromUrl(sourceUrl) }.getOrNull() ?: nome
        runCatching { motoGpTeamByItTitle(title) }.getOrNull()
    }

    /** Wikitext della voce (seguendo i redirect): (titolo effettivo, testo), null se non trovata. */
    private fun rawWikitext(title: String, redirectsLeft: Int = 2, lang: String = "it"): Pair<String, String>? = runCatching {
        val encoded = URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
        val request = Request.Builder()
            .url("https://$lang.wikipedia.org/w/index.php?title=$encoded&action=raw")
            .header("User-Agent", USER_AGENT)
            .build()
        lookupHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string() ?: return@use null
            val redirect = Regex("""^#(?:REDIRECT|RINVIA)\s*:?\s*\[\[([^\]|#]+)""", RegexOption.IGNORE_CASE).find(body.trim())
            if (redirect != null) {
                if (redirectsLeft <= 0) return@use null
                return@use rawWikitext(redirect.groupValues[1].trim(), redirectsLeft - 1, lang)
            }
            title to body
        }
    }.getOrNull()

    /** Foto dell'atleta: miniatura della voce (REST "page/summary"), altrimenti il file dell'infobox. */
    suspend fun photoUrl(title: String, imageFile: String?, lang: String = "it"): String? = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
            val request = Request.Builder()
                .url("https://$lang.wikipedia.org/api/rest_v1/page/summary/$encoded")
                .header("User-Agent", USER_AGENT)
                .build()
            lookupHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val json = JSONObject(response.body?.string() ?: return@use null)
                json.optJSONObject("thumbnail")?.optString("source")?.ifBlank { null }
                    ?: json.optJSONObject("originalimage")?.optString("source")?.ifBlank { null }
            }
        }.getOrNull() ?: imageFile?.let {
            "https://commons.wikimedia.org/wiki/Special:FilePath/" + URLEncoder.encode(it.replace(' ', '_'), "UTF-8") + "?width=300"
        }
    }

    /** Riga pronta per il salvataggio: statistiche nelle colonne indicate in AthleteStats.kt. */
    fun toImportRow(sport: Sport, data: AthleteData, existingId: String?, sourceUrl: String?, photo: String?): PlayerImportRow {
        val base = PlayerImportRow(
            id = existingId,
            nome = data.nome,
            anno = data.anno,
            carrieraMigliore = data.team,
            stato = if (data.retired) PlayerStatus.RITIRATO else PlayerStatus.ATTIVO,
            nazione = data.nazione,
            logoPath = photo,
            competizione = data.competizione,
            proballersUrl = sourceUrl?.trim()?.ifBlank { null }
                ?: ("https://${data.lang}.wikipedia.org/wiki/" + data.title.replace(' ', '_')),
            secondLogoPeriodo = data.stagioni,
            // Lotte: sempre dalla voce (anche nel ruolo); altri sport: solo se la voce dice "F",
            // così una scelta fatta in Genera o uno spostamento manuale non vengono annullati.
            // (Voce inglese: il sesso viene solo dalle categorie, quindi conta solo se "F".)
            femminile = if (sport.isCombat && data.lang == "it") data.female else data.female.takeIf { it },
        )
        if (sport.isTeamSport) {
            return base.copy(
                ruolo = data.ruolo,
                presenze = data.partite,
                punteggio = if (sport == Sport.FOOTBALL) data.touchdown else data.segnati,
                assist = if (sport == Sport.FOOTBALL) data.yard else data.assistTeam,
                rimbalzi = data.tackleFb,
                tackle = data.sack,
                golEvitati = data.intercetti,
                palleRecuperate = data.titoli,
                presenzeNazionale = if (sport == Sport.FOOTBALL) data.proBowl else data.partiteNaz,
                punteggioNazionale = if (sport == Sport.FOOTBALL) data.allPro else data.segnatiNaz,
                assistNazionale = if (sport == Sport.FOOTBALL) data.mvp else data.assistNaz,
                // Stagione migliore: squadra e numeri nelle colonne del "secondo logo", la stagione in college.
                secondLogoClub = data.best?.team.orEmpty(),
                secondLogoPresenze = data.best?.partite ?: 0,
                secondLogoGol = data.best?.gol ?: 0,
                secondLogoEff = data.best?.extra ?: 0,
                college = data.best?.stagione.orEmpty(),
            )
        }
        return if (sport.isMotor) {
            base.copy(
                presenze = data.gare,
                punteggio = data.punti,
                assist = data.podi,
                rimbalzi = data.gareVinte,
                palleRecuperate = data.mondiali,
                tackle = data.pole,
                golEvitati = data.giriVeloci,
            )
        } else {
            base.copy(
                ruolo = if (data.female) GENERE_FEMMINILE else GENERE_MASCHILE,
                presenze = data.incontri,
                punteggio = data.vinti,
                assist = data.persi,
                rimbalzi = data.vintiKo,
                tackle = data.persiKo,
                golEvitati = data.pareggi,
                palleRecuperate = data.titoli,
            )
        }
    }

    // ---------------------------------------------------------------- parsing

    fun parse(sport: Sport, wikitext: String, title: String): Result {
        val text = wikitext.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
        if (sport == Sport.WRESTLING) {
            WikiTemplates.find(text, "Wrestler").firstOrNull()?.let { return parseWrestler(text, it, title) }
        }
        // Il rugby usa {{Sportivo rugby a 15}} (o "a 13", "a 7") invece di {{Sportivo}}.
        val sportivo = WikiTemplates.find(text, "Sportivo").firstOrNull()
            ?: WikiTemplates.findPrefixed(text, "Sportivo rugby").firstOrNull()
            ?: return Result.Missing("la voce \"$title\" non ha la scheda sportivo")
        val s = WikiTemplates.params(sportivo)
        val bio = WikiTemplates.find(text, "Bio").firstOrNull()?.let(WikiTemplates::params).orEmpty()

        // Sumo: "白鵬翔<br />Hakuhō Shō" -> si tiene la grafia latina (l'ultima riga).
        val nome = WikiTemplates.clean(s["nome"].orEmpty().split(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)).last()).ifBlank {
            listOf(bio["nome"], bio["cognome"]).mapNotNull { it?.let(WikiTemplates::clean)?.ifBlank { null } }.joinToString(" ")
        }.ifBlank { title.replace(Regex("""\s*\([^)]*\)"""), "") }
        val anno = Regex("""\d{4}""").find(bio["annonascita"].orEmpty())?.value?.toIntOrNull() ?: 0
        val sesso = (s["sesso"] ?: bio["sesso"]).orEmpty().trim().uppercase()
        // "{{USA}}", ma anche il codice senza graffe ("USA"); con più nazioni ("{{SUN 1980-1991}}<br/>{{RUS}}") la prima valida.
        val codice = s["codicenazione"] ?: s["codicefederazione"] ?: ""
        val nazione = (Regex("""\{\{\s*([A-Z]{3})\s*\}\}""").find(codice) ?: Regex("""^\s*([A-Z]{3})\b""").find(codice))
            ?.groupValues?.get(1)?.let { countryFromCode(it) ?: it }.orEmpty()
        val termine = WikiTemplates.clean(s["terminecarriera"].orEmpty()).isNotBlank()
        val image = s["immagine"]?.let(WikiTemplates::clean)?.removePrefix("File:")?.trim()?.ifBlank { null }

        val common = AthleteData(
            title = title, nome = nome, anno = anno, nazione = nazione, female = sesso.startsWith("F"),
            retired = termine, team = "", competizione = "", stagioni = "", imageFile = image,
        )
        return when {
            sport.isMotor -> parseMotor(sport, text, common)
            sport.isTeamSport -> TeamSportStats.parse(sport, text, s, common)
            else -> parseFighter(text, s, common)
        }
    }

    /**
     * Wrestling: la voce it.wikipedia usa {{Wrestler}}, senza record di incontri (le statistiche
     * arrivano da cagematch.net). Da qui solo anagrafica: nome (ring name), foto, anno, sesso e
     * nazionalità dal campo "Nazionalità" di {{Bio}} (es. "statunitense").
     */
    private fun parseWrestler(text: String, block: String, title: String): Result {
        val w = WikiTemplates.params(block)
        val bio = WikiTemplates.find(text, "Bio").firstOrNull()?.let(WikiTemplates::params).orEmpty()
        val nome = WikiTemplates.clean(w["nome"].orEmpty()).ifBlank { WikiTemplates.clean(bio["pseudonimo"].orEmpty()) }
            .ifBlank { title.replace(Regex("""\s*\([^)]*\)"""), "") }
        val nazionalita = WikiTemplates.clean(bio["nazionalità"] ?: bio["nazionalita"] ?: "").lowercase()
        // "Bandiera = USA" (codice) prima, poi il gentilizio di {{Bio}}.
        val nazione = Regex("""\b([A-Z]{3})\b""").find(w["bandiera"].orEmpty())?.groupValues?.get(1)?.let { countryFromCode(it) }
            ?: countryFromItalianDemonym(nazionalita)
        val anni = WikiTemplates.clean(w["anniattività"] ?: w["anniattivita"] ?: "")
        val closedRange = Regex("""\d{4}\s*[-–]\s*\d{4}\s*$""").containsMatchIn(anni)
        return Result.Found(
            AthleteData(
                title = title,
                nome = nome,
                anno = Regex("""\d{4}""").find(bio["annonascita"].orEmpty())?.value?.toIntOrNull() ?: 0,
                nazione = nazione,
                female = bio["sesso"].orEmpty().trim().uppercase().startsWith("F"),
                retired = closedRange || WikiTemplates.clean(bio["annomorte"].orEmpty()).isNotBlank(),
                team = WikiTemplates.clean(w["federazione"].orEmpty()).split(',').firstOrNull()?.trim().orEmpty(),
                competizione = "Wrestling",
                stagioni = anni.ifBlank { WikiTemplates.clean(w["debutto"].orEmpty()) },
                imageFile = w["immagine"]?.let(WikiTemplates::clean)?.removePrefix("File:")?.trim()?.ifBlank { null },
                cagematchNr = CagematchStats.wrestlerNr(text),
                profightdbSlug = ProfightdbStats.slug(text),
            ),
        )
    }

    // Gentilizi italiani più comuni tra i wrestler -> nome del paese in inglese (come CountryFlags).
    private val italianDemonyms = mapOf(
        "statunitense" to "United States", "canadese" to "Canada", "messicano" to "Mexico", "messicana" to "Mexico",
        "giapponese" to "Japan", "britannico" to "United Kingdom", "britannica" to "United Kingdom", "inglese" to "England",
        "scozzese" to "Scotland", "gallese" to "Wales", "irlandese" to "Ireland", "nordirlandese" to "Northern Ireland",
        "italiano" to "Italy", "italiana" to "Italy", "tedesco" to "Germany", "tedesca" to "Germany",
        "francese" to "France", "australiano" to "Australia", "australiana" to "Australia", "neozelandese" to "New Zealand",
        "portoricano" to "Puerto Rico", "portoricana" to "Puerto Rico", "samoano" to "Samoa", "indiano" to "India",
        "sudafricano" to "South Africa", "spagnolo" to "Spain", "austriaco" to "Austria", "svizzero" to "Switzerland",
        "olandese" to "Netherlands", "belga" to "Belgium", "svedese" to "Sweden", "russo" to "Russia", "brasiliano" to "Brazil",
    )

    private fun countryFromItalianDemonym(raw: String): String {
        val first = raw.split(',', '/', ' ').firstOrNull { it.isNotBlank() }.orEmpty()
        return italianDemonyms[first] ?: italianDemonyms[raw.trim()] ?: ""
    }

    private val formulaOne = Regex("""formula\s*(1|uno)\b""", RegexOption.IGNORE_CASE)
    private val motoGp = Regex("""motomondiale|motogp|classe\s*500|motociclismo""", RegexOption.IGNORE_CASE)

    private fun parseMotor(sport: Sport, text: String, common: AthleteData): Result {
        val wanted = if (sport == Sport.FORMULA1) formulaOne else motoGp
        val label = if (sport == Sport.FORMULA1) "Formula 1" else "Motomondiale"
        val block = WikiTemplates.find(text, "Sport motoristici")
            .map(WikiTemplates::params)
            .firstOrNull { wanted.containsMatchIn(WikiTemplates.clean(it["categoria"].orEmpty())) }
            ?: return Result.Missing("nessuna statistica \"$label\" nella voce \"${common.title}\"")
        fun n(key: String) = WikiTemplates.firstNumber(block[key])
        val stagioni = WikiTemplates.clean(block["anni"].orEmpty()).replace(Regex("""\s+"""), " ")
        val lastYear = Regex("""\d{4}""").findAll(stagioni).lastOrNull()?.value?.toIntOrNull()
        val openRange = Regex("""\d{4}\s*[-–]\s*$""").containsMatchIn(stagioni)
        val thisYear = Calendar.getInstance().get(Calendar.YEAR)
        val endedHere = !openRange && lastYear != null && lastYear < thisYear - 1
        return Result.Found(
            common.copy(
                retired = common.retired || endedHere,
                team = mainTeam(block["scuderie"] ?: block["squadre"] ?: block["squadra"]).orEmpty(),
                competizione = label,
                stagioni = stagioni,
                gare = n("disputati"),
                punti = n("puntiottenuti"),
                podi = n("podi"),
                gareVinte = n("vinti"),
                mondiali = n("mondiali"),
                pole = n("pole"),
                giriVeloci = n("giriveloci"),
            ),
        )
    }

    private val mondialeRegex = Regex("""mondial|olimpi""", RegexOption.IGNORE_CASE)

    /**
     * Sport da combattimento (sezione "Lotta"): pugilato, kickboxing, muay thai, sumo, MMA, lotta.
     * Disciplina e categoria da {{Sportivo}}; il record da:
     * - {{Carriera pugile}} (pugilato, kickboxing, sumo): disputati, vinti [KO] o (KO), persi, pareggi/pareggiati;
     * - {{Carriera AMM}} (MMA): totale, vittorie, ko, sconfitte, ko-sconf, pareggi;
     * - per la lotta olimpica, che non riporta il record, le medaglie d'oro di Mondiali e Olimpiadi
     *   ({{Palmarès}}) come titoli: l'atleta entra in lista senza % di vittorie.
     */
    private fun parseFighter(text: String, s: Map<String, String>, common: AthleteData): Result {
        val career = WikiTemplates.findPrefixed(text, "Carriera ")
            .map(WikiTemplates::params)
            .firstOrNull { c -> listOf("vinti", "persi", "vittorie", "sconfitte").any { c.containsKey(it) } }
        val titoliPugile = WikiTemplates.find(text, "Titoli pugile").firstOrNull()?.let { block ->
            WikiTemplates.split(block).drop(1).count { mondialeRegex.containsMatchIn(it) }
        } ?: 0
        val ori = goldMedals(text)
        val titoliUfc = ufcTitles(text)
        // Senza record né titoli qui si restituisce comunque l'anagrafica: [fetch] prova a
        // completare dalla voce inglese e scarta l'atleta solo se manca tutto anche lì.
        val c = career.orEmpty()
        val mma = c.containsKey("vittorie") || c.containsKey("sconfitte")
        val vinti: Int
        val ko: Int
        val persi: Int
        val koSubiti: Int
        if (mma) {
            vinti = WikiTemplates.firstNumber(c["vittorie"])
            ko = WikiTemplates.firstNumber(c["ko"])
            persi = WikiTemplates.firstNumber(c["sconfitte"])
            koSubiti = WikiTemplates.firstNumber(c["ko-sconf"])
        } else {
            WikiTemplates.numberAndBracket(c["vinti"]).let { vinti = it.first; ko = it.second }
            WikiTemplates.numberAndBracket(c["persi"]).let { persi = it.first; koSubiti = it.second }
        }
        val pareggi = WikiTemplates.firstNumber(c["pareggi"] ?: c["pareggiati"] ?: c["pari"])
        val disputati = WikiTemplates.firstNumber(c["disputati"] ?: c["totale"]).takeIf { it > 0 } ?: (vinti + persi + pareggi)
        val disciplina = WikiTemplates.clean(s["disciplina"].orEmpty()).replaceFirstChar { it.uppercase() }
        val specialita = WikiTemplates.clean(s["specialità"] ?: s["specialita"] ?: "").split(',').first().trim()
        val categoria = WikiTemplates.clean(s["categoria"].orEmpty()).split(',').first().trim().replaceFirstChar { it.uppercase() }
        return Result.Found(
            common.copy(
                team = categoria,
                // "Lotta" da sola dice poco: meglio la specialità (es. "Lotta greco-romana").
                competizione = if (disciplina.equals("Lotta", ignoreCase = true) && specialita.isNotBlank()) specialita
                else disciplina.ifBlank { "Lotta" },
                incontri = disputati,
                vinti = vinti,
                vintiKo = ko,
                persi = persi,
                persiKo = koSubiti,
                pareggi = pareggi,
                titoli = titoliPugile + ori + titoliUfc,
            ),
        )
    }

    private val ufcTitleRegex = Regex("""\bvinc[ea]\b.*\btitolo\b.*\bUFC\b""", RegexOption.IGNORE_CASE)

    /**
     * MMA: titoli UFC conquistati, dalle note della tabella degli incontri ("Vince il titolo dei pesi
     * leggeri UFC"). Ogni conquista conta una volta: le difese ("Difende il titolo") no, un titolo
     * perso e poi rivinto sì (Amanda Nunes: pesi gallo 2016, pesi piuma 2018, pesi gallo 2022 = 3).
     * Solo le righe della tabella (iniziano con "|"), non il testo della biografia.
     */
    internal fun ufcTitles(text: String): Int =
        text.lineSequence().count { line -> line.trimStart().startsWith("|") && ufcTitleRegex.containsMatchIn(WikiTemplates.clean(line)) }

    /** Ori di Mondiali e Olimpiadi dai blocchi {{Palmarès}} ("competizione N" + "oro N"). */
    private fun goldMedals(text: String): Int =
        WikiTemplates.find(text, "Palmarès").sumOf { block ->
            val p = WikiTemplates.params(block)
            p.keys.filter { it.startsWith("competizione") }.sumOf { key ->
                val n = key.removePrefix("competizione")
                if (mondialeRegex.containsMatchIn(WikiTemplates.clean(p[key].orEmpty()))) WikiTemplates.firstNumber(p["oro$n"]) else 0
            }
        }

    /**
     * Scuderia principale: quella con più stagioni nel campo "scuderie" (es. "{{Automobilismo
     * Ferrari|G}} 1996-2006 <br/> {{Automobilismo Mercedes|G}} 2010-2012" -> Ferrari); a pari
     * stagioni la più recente.
     */
    /** Divide sulle virgole fuori da parentesi, wikilink e template. */
    private fun splitTopLevelCommas(text: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        for (ch in text) {
            when (ch) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth = (depth - 1).coerceAtLeast(0)
            }
            if (ch == ',' && depth == 0) {
                parts += current.toString()
                current.clear()
            } else current.append(ch)
        }
        parts += current.toString()
        return parts.filter { it.isNotBlank() }
    }

    internal fun mainTeam(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        // Una squadra per riga (<br/>) ma anche separate da virgole ("[[Moto Morini]], [[MV Agusta]],
        // [[Yamaha]]"): le virgole tra parentesi ("Yamaha (2004–2010, 2013–2021)") restano insieme.
        val entries = raw.split(Regex("""<br\s*/?>|\n|\*""", RegexOption.IGNORE_CASE)).flatMap(::splitTopLevelCommas).mapNotNull { piece ->
            val name = teamName(piece) ?: return@mapNotNull null
            val thisYear = Calendar.getInstance().get(Calendar.YEAR)
            val years = Regex("""(\d{4})(?:\s*([-–])\s*(\d{4}|\d{2})?)?""").findAll(WikiTemplates.clean(piece)).sumOf { m ->
                val from = m.groupValues[1].toInt()
                val toRaw = m.groupValues[3]
                val to = when {
                    // "2016-" (ancora in squadra): fino all'anno in corso.
                    toRaw.isEmpty() && m.groupValues[2].isNotEmpty() -> maxOf(from, thisYear)
                    toRaw.isEmpty() -> from
                    toRaw.length == 2 -> from / 100 * 100 + toRaw.toInt()
                    else -> toRaw.toInt()
                }
                (to - from + 1).coerceAtLeast(1)
            }
            Triple(name, years.coerceAtLeast(1), Regex("""\d{4}""").findAll(piece).lastOrNull()?.value?.toInt() ?: 0)
        }
        // Più stagioni con la stessa squadra in periodi diversi si sommano.
        return entries.groupBy { it.first }
            .map { (name, list) -> Triple(name, list.sumOf { it.second }, list.maxOf { it.third }) }
            .maxWithOrNull(compareBy<Triple<String, Int, Int>> { it.second }.thenBy { it.third })
            ?.first
    }

    private fun teamName(piece: String): String? {
        // {{Automobilismo Ferrari|G}} / {{Motociclismo Honda|...}}
        Regex("""\{\{\s*(?:Automobilismo|Motociclismo|Moto)\s+([^|\}]+)""", RegexOption.IGNORE_CASE).find(piece)?.let {
            return it.groupValues[1].trim()
        }
        val cleaned = WikiTemplates.clean(piece)
            .replace(Regex("""\d{4}(?:\s*[-–]\s*\d{2,4})?"""), "")
            .replace(Regex("""[,;()\s]+$"""), "")
            .trim()
        return cleaned.ifBlank { null }
    }
}

/** Utilità minime per i template del wikitext: ricerca con graffe bilanciate e parametri. */
internal object WikiTemplates {

    /** Contenuto (senza le graffe esterne) di ogni template [name] nel testo, annidati compresi. */
    fun find(text: String, name: String): List<String> =
        findMatching(text) { it.equals(name, ignoreCase = true) }

    /** Come [find], per i template il cui nome inizia con [prefix] (es. "Carriera "). */
    fun findPrefixed(text: String, prefix: String): List<String> =
        findMatching(text) { it.startsWith(prefix, ignoreCase = true) }

    private fun findMatching(text: String, accept: (String) -> Boolean): List<String> {
        val out = mutableListOf<String>()
        var i = text.indexOf("{{")
        while (i >= 0) {
            val end = closing(text, i)
            if (end < 0) break
            val inner = text.substring(i + 2, end)
            val name = inner.substringBefore('|').substringBefore('\n').trim()
            if (accept(name)) out += inner
            i = text.indexOf("{{", i + 2)
        }
        return out
    }

    /** Indice delle "}}" che chiudono le "{{" in [start], -1 se non bilanciate. */
    private fun closing(text: String, start: Int): Int {
        var depth = 0
        var i = start
        while (i < text.length - 1) {
            when {
                text.startsWith("{{", i) -> { depth++; i += 2 }
                text.startsWith("}}", i) -> {
                    depth--
                    if (depth == 0) return i
                    i += 2
                }
                else -> i++
            }
        }
        return -1
    }

    /** Parti separate dai "|" di primo livello (non dentro altri template o wikilink). */
    fun split(inner: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var braces = 0
        var brackets = 0
        var i = 0
        while (i < inner.length) {
            when {
                inner.startsWith("{{", i) -> { braces++; current.append("{{"); i += 2; continue }
                inner.startsWith("}}", i) -> { braces--; current.append("}}"); i += 2; continue }
                inner.startsWith("[[", i) -> { brackets++; current.append("[["); i += 2; continue }
                inner.startsWith("]]", i) -> { brackets--; current.append("]]"); i += 2; continue }
                inner[i] == '|' && braces == 0 && brackets == 0 -> { parts += current.toString(); current.clear() }
                else -> current.append(inner[i])
            }
            i++
        }
        parts += current.toString()
        return parts
    }

    /** Parametri con nome ("chiave = valore"), chiavi in minuscolo senza spazi. */
    fun params(inner: String): Map<String, String> =
        split(inner).drop(1).mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            val key = part.substring(0, eq).trim().lowercase().replace(" ", "")
            if (key.isEmpty() || key.contains('{') || key.contains('[')) null else key to part.substring(eq + 1).trim()
        }.toMap()

    private val refRegex = Regex("""<ref[^>]*/>|<ref[^>]*>.*?</ref>""", RegexOption.DOT_MATCHES_ALL)
    private val linkRegex = Regex("""\[\[([^\]|]+)(?:\|([^\]]*))?]]""")

    /** Testo leggibile: senza ref, tag, grassetti; i link diventano il testo mostrato; i template
     *  con un solo argomento diventano quell'argomento ({{F1|1991}} -> 1991), gli altri spariscono. */
    fun clean(raw: String): String {
        var s = raw.replace(refRegex, "")
        // Immagini incorporate ("[[File:Cintura.png|center|100px|didascalia]]"): non sono testo.
        s = s.replace(Regex("""\[\[(?:File|Image|Immagine|Categoria|Category):(?:[^\[\]]|\[\[[^\]]*]])*]]""", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), ", ")
        s = s.replace(Regex("""<[^>]+>"""), "")
        s = s.replace("'''", "").replace("''", "")
        // Anni delle stagioni ({{MGP|2013|MotoGP}}, {{F1|1991}}): l'anno, anche con un secondo argomento.
        s = s.replace(Regex("""\{\{\s*(?:MGP|F1)\s*\|\s*(\d{4})[^\{\}]*\}\}""", RegexOption.IGNORE_CASE)) { it.groupValues[1] }
        // Bandiere ({{Bandiera|DEU}}): non sono testo, vanno tolte prima della regola "un solo argomento".
        s = s.replace(Regex("""\{\{\s*(?:bandiera|band|flagicon|flag)\s*\|[^\{\}]*\}\}""", RegexOption.IGNORE_CASE), "")
        repeat(3) {
            s = s.replace(Regex("""\{\{\s*formatnum:\s*([^\}]*)\}\}""", RegexOption.IGNORE_CASE)) { it.groupValues[1] }
            s = s.replace(Regex("""\{\{[^\{\}|]*\|([^\{\}|]*)\}\}""")) { it.groupValues[1] }
            s = s.replace(Regex("""\{\{[^\{\}]*\}\}"""), "")
        }
        s = linkRegex.replace(s) { m -> m.groupValues[2].ifBlank { m.groupValues[1] } }
        s = s.replace("&nbsp;", " ")
        return s.replace(Regex("""\s+"""), " ").trim().trim(',').trim()
    }

    private val numberRegex = Regex("""\d{1,3}(?:[.  ]\d{3})+(?!\d)|\d+""")

    /** Primo numero del valore ("308 (306 partenze)" -> 308, "'''7''' (1994, ...)" -> 7, "1.566" -> 1566). */
    fun firstNumber(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        val m = numberRegex.find(clean(raw)) ?: return 0
        return m.value.filter(Char::isDigit).toIntOrNull() ?: 0
    }

    /** "50 [44]" o "104 (43)" -> (numero, KO tra parentesi); 0 se assenti. */
    fun numberAndBracket(raw: String?): Pair<Int, Int> {
        if (raw.isNullOrBlank()) return 0 to 0
        val text = clean(raw)
        // KO tra quadre (pugilato, "50 [44]") o tonde (kickboxing, "104 (43)").
        val main = firstNumber(text.substringBefore('[').substringBefore('('))
        val bracket = Regex("""[\[(]\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return main to bracket
    }
}
