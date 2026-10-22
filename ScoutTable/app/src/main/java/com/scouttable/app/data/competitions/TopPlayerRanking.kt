// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.competitions

import java.text.Normalizer

/** Titoli di un club in "Detentori": totale e competizioni in cui è il detentore. */
data class ClubTitles(
    val team: TeamRef,
    val total: Int,
    /** Competizione (nome mostrato) -> titoli, dalla più vinta. */
    val competitions: List<Pair<String, Int>>,
)

/**
 * "Top player of world": classifica dei club per titoli totali secondo "Detentori". Per ogni club
 * si sommano i titoli di tutte le competizioni di cui è il detentore (la squadra che le ha vinte
 * più volte). Le nazionali non contano (non sono club). Gira fuori dal thread dell'interfaccia
 * (vedi CompetitionsScreen).
 */
object TopPlayerRanking {

    /**
     * Club detentori con i titoli raggruppati, dal più titolato. Il raggruppamento è per voce
     * Wikipedia del club quando c'è: due club omonimi (es. "Barcelona" = FC Barcelona e Barcelona
     * SC dell'Ecuador) restano separati invece di sommare i titoli.
     */
    fun clubTitles(defs: List<CompetitionDef>, data: Map<String, CompetitionData>): List<ClubTitles> {
        val byClub = linkedMapOf<String, MutableList<Pair<TeamRef, Pair<String, Int>>>>()
        defs.forEach { def ->
            val d = data[def.id] ?: return@forEach
            val titles = d.recordTitles ?: return@forEach
            d.recordTeams.filter { !it.isNational }.forEach { team ->
                val groupKey = team.wikiTitle?.trim()?.lowercase()?.ifBlank { null } ?: clubKey(team.name)
                byClub.getOrPut(groupKey) { mutableListOf() } += team to (def.label to titles)
            }
        }
        return byClub.values.map { entries ->
            ClubTitles(
                team = entries.firstNotNullOfOrNull { it.first.takeIf { t -> t.badgeUrl != null } } ?: entries.first().first,
                total = entries.sumOf { it.second.second },
                competitions = entries.map { it.second }.sortedByDescending { it.second },
            )
        }.sortedByDescending { it.total }
    }

    // Sigle e parole generiche che non distinguono un club ("FC Barcelona" = "Barcelona").
    private val noise = setOf(
        "fc", "cf", "ac", "afc", "sc", "ssc", "as", "ss", "us", "cd", "ud", "sd", "rc", "rcd", "sv", "vfb", "vfl",
        "bc", "bk", "kk", "club", "calcio", "football", "futbol", "basketball", "basket", "de", "the",
    )

    // Nomi italiani dei club (it.wikipedia, usati per la carriera migliore) -> nome inglese usato
    // da en.wikipedia nei detentori.
    private val aliases = mapOf(
        "inter" to "inter milan", "internazionale" to "inter milan",
        "bayern monaco" to "bayern munich", "barcellona" to "barcelona", "siviglia" to "sevilla",
        "stella rossa" to "red star belgrade", "steaua bucarest" to "steaua bucuresti",
        "dinamo zagabria" to "dinamo zagreb", "dinamo kiev" to "dynamo kyiv", "dinamo mosca" to "dynamo moscow",
        "spartak mosca" to "spartak moscow", "cska mosca" to "cska moscow", "sporting lisbona" to "sporting cp",
        "olympiakos" to "olympiacos", "lione" to "lyon", "marsiglia" to "marseille",
        "colonia" to "koln", "amburgo" to "hamburger", "stoccarda" to "stuttgart",
    )

    private val diacriticsRegex = Regex("\\p{Mn}+")
    private val nonAlnumRegex = Regex("[^a-z0-9]+")

    private fun clubKey(name: String): String {
        val plain = Normalizer.normalize(name, Normalizer.Form.NFD).replace(diacriticsRegex, "")
            .lowercase().replace(nonAlnumRegex, " ").trim()
        val translated = aliases[plain] ?: plain
        return translated.split(' ').filter { it.isNotBlank() && it !in noise }.joinToString(" ")
    }
}
