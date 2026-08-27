package com.example.playerbase.data

import java.util.Locale

/**
 * Assistente locale, senza connessione: risponde a domande semplici sui
 * giocatori usando solo i dati già in anagrafica (nessuna chiamata di rete,
 * nessuna chiave API richiesta).
 */
object PlayerAssistant {

    /** Prefisso della risposta quando non si trova nulla nei dati dei giocatori: usato dalla UI per capire quando proporre una ricerca sul web. */
    const val NOT_FOUND_PREFIX = "Non ho trovato nulla su"

    fun answer(query: String, players: List<Player>): String {
        val q = query.trim()
        if (q.isBlank()) {
            return "Fammi una domanda: il nome di un giocatore, una squadra, un ruolo, oppure \"quanti giocatori\" o \"età media\"."
        }
        if (players.isEmpty()) return "Non ci sono ancora giocatori salvati in anagrafica."
        val qLower = q.lowercase(Locale.ITALIAN)

        // 1) Nome o cognome di un giocatore specifico
        val nameMatches = players.filter { p ->
            listOf(p.name, p.surname).any { part ->
                part.isNotBlank() && part.length >= 2 && qLower.contains(part.lowercase(Locale.ITALIAN))
            }
        }
        if (nameMatches.isNotEmpty()) {
            return nameMatches.joinToString("\n\n") { describePlayer(it) }
        }

        // 2) Conteggi generali, eventualmente filtrati per sport ("quanti giocatori di basket")
        if (qLower.contains("quant")) {
            val sportFilter = Sport.entries.firstOrNull { qLower.contains(it.label.lowercase(Locale.ITALIAN)) }
            val filtered = if (sportFilter != null) players.filter { it.sport == sportFilter } else players
            val label = sportFilter?.let { " di ${it.label.lowercase(Locale.ITALIAN)}" } ?: ""
            return "Ci sono ${filtered.size} giocatori$label in anagrafica."
        }

        // 3) Età media
        if (qLower.contains("età media") || qLower.contains("eta media") ||
            (qLower.contains("media") && (qLower.contains("età") || qLower.contains("eta")))
        ) {
            val ages = players.mapNotNull { it.age() }
            if (ages.isEmpty()) return "Nessun giocatore ha un anno di nascita compilato."
            val avg = ages.average()
            return "L'età media è di circa ${"%.1f".format(avg)} anni (su ${ages.size} giocatori con anno di nascita compilato)."
        }

        // 4) Squadra (Max Team)
        val team = players.map { it.maxTeam }.firstOrNull { it.isNotBlank() && qLower.contains(it.lowercase(Locale.ITALIAN)) }
        if (team != null) {
            val sameTeam = players.filter { it.maxTeam.equals(team, ignoreCase = true) }
            return "Giocatori con Max Team \"$team\" (${sameTeam.size}):\n" +
                sameTeam.joinToString("\n") { "- ${it.fullName} (${it.role.label})" }
        }

        // 5) Ruolo
        val roleMatch = PlayerRole.entries.firstOrNull {
            it != PlayerRole.NON_SPECIFICATO && qLower.contains(it.label.lowercase(Locale.ITALIAN))
        }
        if (roleMatch != null) {
            val withRole = players.filter { it.role == roleMatch }
            if (withRole.isEmpty()) return "Nessun giocatore ha il ruolo \"${roleMatch.label}\"."
            return "Giocatori con ruolo \"${roleMatch.label}\" (${withRole.size}):\n" +
                withRole.joinToString("\n") { "- ${it.fullName}" }
        }

        // 6) Ricerca generica di fallback (Max Career o altri campi testuali)
        val fallback = players.filter { p ->
            p.maxCareer.isNotBlank() && p.maxCareer.length >= 2 && qLower.contains(p.maxCareer.lowercase(Locale.ITALIAN))
        }
        if (fallback.isNotEmpty()) {
            return fallback.joinToString("\n\n") { describePlayer(it) }
        }

        return "$NOT_FOUND_PREFIX \"$query\" in anagrafica."
    }

    private fun describePlayer(p: Player): String {
        val parts = mutableListOf("${p.fullName} — ${p.sport.label}, ${p.role.label}")
        p.age()?.let { parts.add("Età: $it anni") }
        p.heightCm?.let { parts.add("Altezza: $it cm") }
        if (p.maxTeam.isNotBlank()) parts.add("Max Team: ${p.maxTeam}")
        if (p.maxCareer.isNotBlank()) parts.add("Max Career: ${p.maxCareer}")
        p.powerDouble?.let { parts.add("Power/Double: $it") }
        if (p.retired) parts.add("Ritirato")
        return parts.joinToString("\n")
    }
}
