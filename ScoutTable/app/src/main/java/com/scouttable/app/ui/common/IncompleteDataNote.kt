package com.scouttable.app.ui.common

import com.scouttable.app.data.Sport
import com.scouttable.app.data.importexport.PlayerImportRow

/**
 * Tra i giocatori appena trovati, elenca quelli con dati automatici incompleti (statistiche di
 * carriera, nazionalità o ruolo mancanti): capita spesso nel basket quando l'infobox Wikipedia del
 * giocatore non riporta i totali di carriera (comune per giocatori europei più datati, es. Dino
 * Meneghin, Carlton Myers) — in quel caso l'unica fonte più precisa è incollare l'URL Proballers
 * del giocatore (in "Modifica giocatore", o rilanciando la ricerca con "Nome | URL").
 */
fun incompleteDataNote(rows: List<PlayerImportRow>, sport: Sport): String? {
    val entries = rows.mapNotNull { row ->
        val gaps = mutableListOf<String>()
        val noStats = row.presenze == 0 && row.punteggio == 0 && (sport == Sport.CALCIO || row.assist == 0)
        if (noStats) gaps += "statistiche"
        if (row.nazione.isBlank()) gaps += "nazionalità"
        if (row.ruolo.isBlank()) gaps += "ruolo"
        if (gaps.isEmpty()) null else "${row.nome} (manca: ${gaps.joinToString(", ")})"
    }
    if (entries.isEmpty()) return null

    val suggestion = if (sport == Sport.BASKET) {
        " Per le statistiche mancanti incolla l'URL Proballers del giocatore (in \"Modifica giocatore\", " +
            "o rilanciando la ricerca con \"Nome | URL Proballers\")."
    } else ""
    return "Dati incompleti (fonti automatiche senza queste informazioni):\n${entries.joinToString("\n")}.$suggestion"
}
