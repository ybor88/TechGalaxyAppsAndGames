// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.competitions

import android.content.Context
import android.net.Uri
import com.scouttable.app.data.Sport
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Detentori salvati di uno sport: competizioni trovate in automatico ([discovered], oltre a
 * quelle dell'elenco fisso [CompetitionCatalog]) e dati per competizione (chiave [CompetitionDef.id]).
 */
data class CompetitionSnapshot(
    val updatedAt: Long,
    val discovered: List<CompetitionDef>,
    val data: Map<String, CompetitionData>,
) {
    /** Tutte le card da mostrare: elenco fisso + competizioni trovate in automatico, senza doppioni
     *  (una competizione salvata da un aggiornamento precedente può essere entrata nel frattempo
     *  nell'elenco fisso: due card con lo stesso id fanno chiudere l'app). */
    fun definitions(sport: Sport): List<CompetitionDef> =
        (CompetitionCatalog.forSport(sport) + discovered).distinctBy { it.id.trim().lowercase() }
}

/** Avanzamento di un aggiornamento, per la barra in "Detentori": [fraction] è l'avanzamento
 *  complessivo (0..1) su tutte le fasi, non solo su quella in corso. */
data class RefreshProgress(val phase: String, val fraction: Float)

/**
 * Detentori (il club con più titoli di ogni competizione): letti da Wikipedia solo
 * quando l'utente preme "Aggiorna", e salvato in un file JSON nella memoria dell'app così da
 * restare visibile anche offline. Nessuna tabella nel database: sono dati di consultazione,
 * sostituiti in blocco a ogni aggiornamento.
 */
class CompetitionRepository(context: Context) {

    private val dir = context.applicationContext.filesDir

    private fun fileFor(sport: Sport) = File(dir, "competitions_${sport.name}.json")

    // ---------------------------------------------------------------- loghi scelti a mano

    // Loghi scelti dall'utente dalla galleria, per competizione: file a parte, così "Aggiorna"
    // (che riscrive i dati da Wikipedia) non li tocca mai. Il nome inizia con "competitions_" e le
    // immagini stanno nella cartella "logos": entrambi già inclusi nel backup (vedi BackupManager).
    // Si salva solo il nome del file, non il percorso completo, che cambia su un altro telefono.
    private fun overridesFile(sport: Sport) = File(dir, "competitions_logos_${sport.name}.json")
    private val logosDir get() = File(dir, "logos")

    /** Logo scelto a mano per ogni competizione (chiave [CompetitionDef.id]) -> URI del file locale. */
    suspend fun loadLogoOverrides(sport: Sport): Map<String, String> = loadOverrides(overridesFile(sport))

    /** Copia l'immagine scelta dalla galleria e la usa come logo della competizione [id]. */
    suspend fun setLogoOverride(context: Context, sport: Sport, id: String, source: Uri): Boolean =
        setOverride(context, overridesFile(sport), "competition", id, source)

    // Stemmi delle squadre detentrici scelti a mano dalla galleria: stesso meccanismo dei loghi
    // delle competizioni, file a parte che "Aggiorna" non tocca mai (incluso nel backup).
    private fun teamOverridesFile(sport: Sport) = File(dir, "competitions_team_logos_${sport.name}.json")

    /** Stemma scelto a mano per ogni squadra (chiave [teamKey]) -> URI del file locale. */
    suspend fun loadTeamLogoOverrides(sport: Sport): Map<String, String> = loadOverrides(teamOverridesFile(sport))

    /** Copia l'immagine scelta dalla galleria e la usa come stemma della squadra [key] (vedi [teamKey]). */
    suspend fun setTeamLogoOverride(context: Context, sport: Sport, key: String, source: Uri): Boolean =
        setOverride(context, teamOverridesFile(sport), "team", key, source)

    private suspend fun loadOverrides(file: File): Map<String, String> = withContext(Dispatchers.IO) {
        val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return@withContext emptyMap()
        json.keys().asSequence().mapNotNull { id ->
            val image = File(logosDir, json.getString(id))
            if (image.exists()) id to Uri.fromFile(image).toString() else null
        }.toMap()
    }

    private suspend fun setOverride(context: Context, file: File, prefix: String, id: String, source: Uri): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                logosDir.mkdirs()
                val name = "${prefix}_${UUID.randomUUID()}.jpg"
                val target = File(logosDir, name)
                context.contentResolver.openInputStream(source)?.use { input ->
                    target.outputStream().use { input.copyTo(it) }
                } ?: return@runCatching false
                val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: JSONObject()
                // L'immagine sostituita non serve più.
                json.optString(id).takeIf { it.isNotBlank() }?.let { File(logosDir, it).delete() }
                json.put(id, name)
                file.writeText(json.toString())
                true
            }.getOrDefault(false)
        }

    suspend fun load(sport: Sport): CompetitionSnapshot? = withContext(Dispatchers.IO) {
        runCatching { decode(fileFor(sport).readText(), sport) }.getOrNull()
    }

    /**
     * Aggiorna i detentori dello sport:
     * 1. cerca su Wikipedia le competizioni non ancora in elenco (così una competizione nuova
     *    compare da sola, senza aggiornare l'app);
     * 2. rilegge tutte le competizioni (elenco fisso + trovate), a blocchi di 50 voci;
     * 3. scarica stemmi delle squadre e loghi delle competizioni, sempre a blocchi.
     * Una competizione trovata in automatico entra solo se è dello sport giusto, ancora disputata e
     * se Wikipedia indica il club con più titoli. Una competizione che questa volta non
     * risponde tiene i dati dell'aggiornamento precedente invece di sparire.
     * @return snapshot salvato e numero di competizioni rimaste senza dati.
     */
    suspend fun refreshAll(
        sport: Sport,
        onProgress: (RefreshProgress) -> Unit,
    ): Pair<CompetitionSnapshot, Int> = withContext(Dispatchers.IO) {
        // Percentuale complessiva: ogni fase occupa una fetta fissa della barra (ricerca 0-5%,
        // lettura 5-60%, tabelle dei pari merito 60-70%, stemmi 70-95%, loghi 95-100%), così la
        // barra avanza sempre e non riparte da zero a ogni fase.
        suspend fun progress(phase: String, from: Int, to: Int, done: Int, total: Int) {
            val fraction = (from + (to - from) * (if (total > 0) done.toFloat() / total else 0f)) / 100f
            withContext(Dispatchers.Main) { onProgress(RefreshProgress(phase, fraction.coerceIn(0f, 1f))) }
        }

        val previous = load(sport)
        val catalog = CompetitionCatalog.forSport(sport)
        val catalogKeys = catalog.map { titleKey(it.wikiTitle) }.toSet()

        progress("Ricerca nuove competizioni su Wikipedia", 0, 5, 0, 1)
        val candidates = runCatching { WikipediaCompetitionStats.discoverTitles(sport) }.getOrDefault(emptyList())
        // Anche le trovate in precedenza: se una sparisce dagli elenchi di Wikipedia resta comunque.
        val extraTitles = (candidates + previous?.discovered.orEmpty().map { it.wikiTitle })
            .filter { titleKey(it) !in catalogKeys }
            .distinctBy(::titleKey)

        val allTitles = catalog.map { it.wikiTitle } + extraTitles
        val parsedByRequest = WikipediaCompetitionStats.fetchParsed(allTitles, sport) { done, total ->
            progress("Lettura competizioni (blocco ${minOf(done + 1, total)} di $total)", 5, 60, done, total)
        }
        // Raggiungibili sia dal titolo richiesto sia da quello effettivo: una competizione trovata
        // in automatico è salvata con il titolo effettivo (es. "Botola Pro", richiesta come
        // "Botola"), e senza questa seconda chiave al momento di comporre i dati restava vuota.
        val parsed = parsedByRequest + parsedByRequest.values.associateBy { it.title }

        // Una voce dell'elenco fisso può essere raggiunta anche da un altro titolo (redirect): il
        // titolo effettivo evita la stessa competizione due volte.
        val catalogResolved = catalog.mapNotNull { parsed[it.wikiTitle]?.title?.let(::titleKey) }.toSet() + catalogKeys
        val previousDiscovered = previous?.discovered.orEmpty().associateBy { titleKey(it.wikiTitle) }
        val discovered = extraTitles.mapNotNull { title ->
            val p = parsed[title]
            when {
                p == null -> previousDiscovered[titleKey(title)]
                titleKey(p.title) in catalogResolved -> null
                p.active && p.hasRecord && WikipediaCompetitionStats.isAboutSport(p, sport) ->
                    CompetitionDef(sport, guessCategory(p), p.title, p.title, discovered = true)
                else -> null
            }
        }.distinctBy { titleKey(it.wikiTitle) }

        val defs = catalog + discovered
        // Detentori a pari merito indicati solo come numero ("Six teams"): squadre dalla tabella
        // della voce, così la card le mostra tutte con nome e stemma.
        val groupDefs = defs.filter { parsed[it.wikiTitle]?.let { p -> p.groupRecord && p.recordTeams.isEmpty() } == true }
        var groupDone = 0
        val parsedByDef = defs.mapNotNull { def ->
            val p = parsed[def.wikiTitle] ?: return@mapNotNull null
            val fixed = if (p.groupRecord && p.recordTeams.isEmpty()) {
                progress("Squadre a pari merito: ${def.label}", 60, 70, groupDone++, groupDefs.size)
                p.copy(recordTeams = runCatching { WikipediaCompetitionStats.teamsFromByClubTable(p.title, p.recordTitles) }.getOrDefault(emptyList()))
            } else p
            def.id to fixed
        }.toMap()
        val pageTitles = parsedByDef.values.flatMap { p ->
            listOf(p.title) + p.recordTeams.mapNotNull { it.wikiTitle }
        }
        val images = WikipediaCompetitionStats.pageImages(pageTitles) { done, total ->
            progress("Stemmi delle squadre (blocco ${minOf(done + 1, total)} di $total)", 70, 95, done, total)
        }
        progress("Loghi", 95, 100, 0, 1)
        // Il logo indicato nell'infobox ha la precedenza sull'immagine principale della voce (che
        // per alcune competizioni è una foto del trofeo o dello stadio).
        val fileThumbs = WikipediaCompetitionStats.fileThumbs(parsedByDef.values.mapNotNull { it.logoFile })

        val previousData = previous?.data.orEmpty()
        val result = mutableMapOf<String, CompetitionData>()
        defs.forEach { def ->
            val p = parsedByDef[def.id]
            if (p == null) {
                previousData[def.id]?.let { result[def.id] = it }
                return@forEach
            }
            val old = previousData[def.id]
            // Anche le nazionali citate con un link (es. Jugoslavia, senza bandiera emoji): l'immagine
            // della voce è lo stemma della federazione.
            // Uno stemma già salvato non viene mai sostituito da quello di Wikipedia: si scarica
            // solo per le squadre che non l'hanno ancora (richiesto esplicitamente, così uno
            // stemma corretto resta tale anche se Wikipedia cambia immagine).
            fun withBadge(team: TeamRef, oldTeams: List<TeamRef>?) = team.copy(
                badgeUrl = oldTeams?.firstOrNull { teamKey(it) == teamKey(team) || it.name == team.name }?.badgeUrl
                    ?: team.wikiTitle?.let { images[it] },
            )
            result[def.id] = CompetitionData(
                recordTeams = p.recordTeams.map { withBadge(it, old?.recordTeams) },
                recordTitles = p.recordTitles,
                logoUrl = p.logoFile?.let { fileThumbs[it] } ?: images[p.title] ?: old?.logoUrl,
            )
        }

        val snapshot = CompetitionSnapshot(System.currentTimeMillis(), discovered, result)
        fileFor(sport).writeText(encode(snapshot))
        progress("Completato", 100, 100, 1, 1)
        snapshot to defs.count { it.id !in result }
    }

    private fun titleKey(title: String) = title.trim().replace('_', ' ').lowercase()

    companion object {
        /** Chiave di una squadra per gli stemmi scelti a mano: la sua voce Wikipedia, o il nome. */
        fun teamKey(team: TeamRef): String = (team.wikiTitle ?: team.name).trim().replace('_', ' ').lowercase()
    }

    // Parole che indicano una coppa (non un campionato) e una competizione internazionale.
    private val cupRegex = Regex(
        """\b(Cup|Copa|Coppa|Coupe|Pokal|Taça|Shield|Trophy|Supercup|SuperCup|Super Cup|Supercopa|Supercoppa|beker|Kupa|Tournament)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val internationalRegex = Regex(
        """\b(UEFA|CAF|AFC|CONCACAF|CONMEBOL|OFC|FIBA|Euro\w*|Champions League|Libertadores|Sudamericana|Intercontinental|""" +
            """Arab|African|Asian|ASEAN|COSAFA|CECAFA|CAFA|SAFF|EAFF|WAFF|Gulf|Americas|Continental|Club World|Interclub|Leagues Cup|Finalissima|Sudamericano|Centrobasket|WABA|SABA|EABA|West Asia|East Asia|Nile Basin)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Categoria di una competizione trovata in automatico, dedotta da squadre e nome. */
    private fun guessCategory(p: ParsedCompetition): CompetitionCategory {
        val teams = p.recordTeams
        return when {
            teams.isNotEmpty() && teams.all { it.isNational } -> CompetitionCategory.NAZIONALI
            internationalRegex.containsMatchIn(p.title) -> CompetitionCategory.CLUB_INTERNAZIONALI
            cupRegex.containsMatchIn(p.title) -> CompetitionCategory.COPPE_NAZIONALI
            else -> CompetitionCategory.CAMPIONATI
        }
    }

    // ---------------------------------------------------------------- JSON

    private fun encode(snapshot: CompetitionSnapshot): String {
        val items = JSONObject()
        snapshot.data.forEach { (id, d) ->
            items.put(
                id,
                JSONObject()
                    .put("recordTeams", encodeTeams(d.recordTeams))
                    .put("recordTitles", d.recordTitles ?: JSONObject.NULL)
                    .put("logoUrl", d.logoUrl ?: JSONObject.NULL),
            )
        }
        val discovered = JSONArray().apply {
            snapshot.discovered.forEach { put(JSONObject().put("title", it.wikiTitle).put("category", it.category.name)) }
        }
        return JSONObject()
            .put("updatedAt", snapshot.updatedAt)
            .put("discovered", discovered)
            .put("items", items)
            .toString()
    }

    private fun encodeTeams(teams: List<TeamRef>) = JSONArray().apply {
        teams.forEach {
            put(
                JSONObject()
                    .put("name", it.name)
                    .put("badgeUrl", it.badgeUrl ?: JSONObject.NULL)
                    .put("isNational", it.isNational)
                    .put("wikiTitle", it.wikiTitle ?: JSONObject.NULL)
            )
        }
    }

    private fun decode(text: String, sport: Sport): CompetitionSnapshot {
        val root = JSONObject(text)
        val items = root.getJSONObject("items")
        val data = items.keys().asSequence().associateWith { id ->
            val o = items.getJSONObject(id)
            CompetitionData(
                recordTeams = decodeTeams(o.optJSONArray("recordTeams")),
                recordTitles = o.optIntOrNull("recordTitles"),
                logoUrl = fixThumbSize(o.optStringOrNull("logoUrl")),
            )
        }
        val discoveredArray = root.optJSONArray("discovered")
        val discovered = (0 until (discoveredArray?.length() ?: 0)).mapNotNull { i ->
            val o = discoveredArray!!.getJSONObject(i)
            val category = runCatching { CompetitionCategory.valueOf(o.getString("category")) }.getOrNull() ?: return@mapNotNull null
            val title = o.getString("title")
            CompetitionDef(sport, category, title, title, discovered = true)
        }
        return CompetitionSnapshot(root.optLong("updatedAt"), discovered, data)
    }

    private fun decodeTeams(array: JSONArray?): List<TeamRef> =
        if (array == null) emptyList() else (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            TeamRef(o.optString("name"), fixThumbSize(o.optStringOrNull("badgeUrl")), o.optBoolean("isNational"), o.optStringOrNull("wikiTitle"))
        }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)
    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }

    // Gli aggiornamenti fatti con la versione precedente salvavano miniature larghe 160px, una
    // misura che Wikimedia non genera più (risponde 400): convertite alla misura standard 250px,
    // così loghi e stemmi compaiono senza dover rifare l'aggiornamento.
    private fun fixThumbSize(url: String?): String? = url?.replace("/160px-", "/250px-")
}
