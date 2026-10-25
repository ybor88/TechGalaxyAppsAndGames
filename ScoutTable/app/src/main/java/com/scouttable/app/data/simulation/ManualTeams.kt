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

/** Una posizione della squadra: sigla per il motore ([MatchSimulator]) ed etichetta mostrata. */
data class SimSlot(val code: String, val label: String)

/** Basket: 5 titolari e 5 di panchina, nelle 5 posizioni. */
val BasketSlots: List<SimSlot> = listOf(
    SimSlot("PG", "Playmaker"), SimSlot("SG", "Guardia"), SimSlot("SF", "Ala piccola"),
    SimSlot("PF", "Ala grande"), SimSlot("C", "Centro"),
    SimSlot("PG", "Playmaker (panchina)"), SimSlot("SG", "Guardia (panchina)"), SimSlot("SF", "Ala piccola (panchina)"),
    SimSlot("PF", "Ala grande (panchina)"), SimSlot("C", "Centro (panchina)"),
)

/** Calcio: 4-4-2. */
val SoccerSlots: List<SimSlot> = listOf(
    SimSlot("GK", "Portiere"),
    SimSlot("DF", "Difensore 1"), SimSlot("DF", "Difensore 2"), SimSlot("DF", "Difensore 3"), SimSlot("DF", "Difensore 4"),
    SimSlot("MF", "Centrocampista 1"), SimSlot("MF", "Centrocampista 2"), SimSlot("MF", "Centrocampista 3"), SimSlot("MF", "Centrocampista 4"),
    SimSlot("FW", "Attaccante 1"), SimSlot("FW", "Attaccante 2"),
)

fun slotsFor(sport: Sport): List<SimSlot> = if (sport.isBasket) BasketSlots else SoccerSlots

/**
 * Un giocatore inserito a mano, nella posizione [slot] (indice in [slotsFor]).
 * Basket: [eff]. Calcio: [presenze] e [gol] (per il portiere i gol SUBITI).
 */
data class ManualPlayer(
    val name: String,
    val eff: Double? = null,
    val presenze: Int? = null,
    val gol: Int? = null,
)

/** Squadra inserita a mano e salvata, riutilizzabile in tutte le partite e i tornei. */
data class SavedTeam(
    val id: String,
    val name: String,
    /** URI del file dello stemma scelto dalla galleria (cartella "logos"), null se non scelto. */
    val logoPath: String?,
    /** Un giocatore per posizione, nell'ordine di [slotsFor]; null = posizione vuota. */
    val players: List<ManualPlayer?>,
) {
    /** Giocatori effettivamente inseriti. */
    val filledCount: Int get() = players.count { it != null && it.name.isNotBlank() }
}

/**
 * Squadra per il motore: un [SimPlayer] per ogni posizione compilata, nell'ordine delle
 * posizioni, con l'indice della posizione ([SimPlayer.slot]): le posizioni vuote vengono saltate e
 * il duello è sempre tra giocatori nella stessa posizione (vedi [MatchSimulator]).
 */
fun SavedTeam.toSimTeam(sport: Sport): SimTeam {
    val slots = slotsFor(sport)
    val players = slots.indices.mapNotNull { i ->
        val p = players.getOrNull(i)?.takeIf { it.name.isNotBlank() } ?: return@mapNotNull null
        val slot = slots[i]
        if (sport.isBasket) {
            SimPlayer(name = p.name.trim(), position = slot.code, games = 1, minutes = if (i < 5) 30.0 else 18.0, eff = p.eff ?: 0.0, slot = i)
        } else {
            val presenze = p.presenze ?: 0
            val gol = p.gol ?: 0
            if (slot.code == "GK") {
                SimPlayer(
                    name = p.name.trim(), position = slot.code, games = presenze, minutesTotal = presenze * 90,
                    goalsAgainstPer90 = if (presenze > 0) gol.toDouble() / presenze else null, slot = i,
                )
            } else {
                SimPlayer(name = p.name.trim(), position = slot.code, games = presenze, goals = gol, minutesTotal = presenze * 80, slot = i)
            }
        }
    }
    return SimTeam(name.trim().ifBlank { "Squadra" }, logoPath, "manual:$id", players)
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
