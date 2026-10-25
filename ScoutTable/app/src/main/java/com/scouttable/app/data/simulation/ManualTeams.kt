// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.simulation

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
 * Giocatore della versione precedente (squadre inserite giocatore per giocatore): non più usato
 * per giocare, letto solo per ricavare un valore dalle squadre salvate allora (vedi legacyValue).
 */
data class ManualPlayer(
    val name: String,
    val eff: Double? = null,
    val presenze: Int? = null,
    val gol: Int? = null,
)

/** Squadra salvata (nome, stemma e valore), riutilizzabile in tutte le partite e i tornei. */
data class SavedTeam(
    val id: String,
    val name: String,
    /** URI del file dello stemma scelto dalla galleria (cartella "logos"), null se non scelto. */
    val logoPath: String?,
    /** Giocatori della versione precedente (non più usati per giocare, tenuti per compatibilità). */
    val players: List<ManualPlayer?> = emptyList(),
    /** Valore della squadra: basket efficienza totale (vince il più alto), calcio percentuale/decimale (vince il più basso). */
    val value: Double? = null,
) {
}

/** Squadra per il motore: solo nome, stemma e valore ([SimTeam.teamValue]). */
fun SavedTeam.toSimTeam(sport: Sport): SimTeam =
    SimTeam(name.trim().ifBlank { "Squadra" }, logoPath, "manual:$id", emptyList(), teamValue = value ?: legacyValue(sport))

/** Squadre salvate con i giocatori (versione precedente): somma degli Eff / media dei rapporti gol-presenze. */
private fun SavedTeam.legacyValue(sport: Sport): Double? {
    val filled = players.filterNotNull().filter { it.name.isNotBlank() }
    if (filled.isEmpty()) return null
    return if (sport.isBasket) filled.sumOf { it.eff ?: 0.0 }
    else filled.mapNotNull { p -> p.presenze?.takeIf { it > 0 }?.let { (p.gol ?: 0).toDouble() / it } }.average().takeIf { !it.isNaN() }
}


/**
 * Squadre salvate di uno sport: file JSON "competitions_simteams_<SPORT>.json" (il prefisso lo
 * include nel backup su Drive) e stemmi nella cartella "logos" (anch'essa nel backup).
 */
class ManualTeamRepository(context: Context, sport: Sport) {

    private val dir = context.applicationContext.filesDir
    private val file = File(dir, "competitions_simteams_${sport.name}.json")
    private val logosDir get() = File(dir, "logos")

    suspend fun load(): List<SavedTeam> = withContext(Dispatchers.IO) {
        val array = runCatching { JSONArray(file.readText()) }.getOrNull() ?: return@withContext emptyList()
        (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                val ps = o.optJSONArray("players") ?: JSONArray()
                SavedTeam(
                    id = o.getString("id"),
                    name = o.optString("name"),
                    logoPath = o.optString("logo").ifBlank { null }?.let(::resolveLogo),
                    value = if (o.has("value") && !o.isNull("value")) o.getDouble("value") else null,
                    players = (0 until ps.length()).map { j ->
                        if (ps.isNull(j)) null else ps.getJSONObject(j).let { p ->
                            ManualPlayer(
                                name = p.optString("name"),
                                eff = if (p.has("eff") && !p.isNull("eff")) p.getDouble("eff") else null,
                                presenze = if (p.has("presenze") && !p.isNull("presenze")) p.getInt("presenze") else null,
                                gol = if (p.has("gol") && !p.isNull("gol")) p.getInt("gol") else null,
                            )
                        }
                    },
                )
            }.getOrNull()
        }.sortedBy { it.name.lowercase() }
    }

    /** Salva (nuova o modificata) e restituisce l'elenco aggiornato. */
    suspend fun save(team: SavedTeam): List<SavedTeam> {
        val list = load().filterNot { it.id == team.id } + team
        write(list)
        return load()
    }

    suspend fun delete(id: String): List<SavedTeam> {
        write(load().filterNot { it.id == id })
        return load()
    }

    /** Copia l'immagine scelta dalla galleria nella cartella degli stemmi e ne restituisce l'URI. */
    suspend fun importLogo(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            logosDir.mkdirs()
            val target = File(logosDir, "simteam_${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(source)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                ?: return@runCatching null
            Uri.fromFile(target).toString()
        }.getOrNull()
    }

    private suspend fun write(list: List<SavedTeam>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        list.forEach { t ->
            array.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    // Solo il nome del file: il percorso completo cambia su un altro telefono (backup).
                    .put("logo", t.logoPath?.substringAfterLast('/') ?: "")
                    .put("value", t.value ?: JSONObject.NULL)
                    .put("players", JSONArray().apply {
                        t.players.forEach { p ->
                            if (p == null) put(JSONObject.NULL) else put(
                                JSONObject()
                                    .put("name", p.name)
                                    .put("eff", p.eff ?: JSONObject.NULL)
                                    .put("presenze", p.presenze ?: JSONObject.NULL)
                                    .put("gol", p.gol ?: JSONObject.NULL)
                            )
                        }
                    })
            )
        }
        file.writeText(array.toString())
    }

    private fun resolveLogo(fileName: String): String? =
        File(logosDir, fileName).takeIf { it.exists() }?.let { Uri.fromFile(it).toString() }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
