// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.generate

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.scouttable.app.data.Sport
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.common.PasteListScreen

@Composable
fun GenerateListScreen(sport: Sport, padding: PaddingValues) {
    val repository = rememberPlayerRepository()

    PasteListScreen(
        sport = sport,
        padding = padding,
        title = "Genera nuova lista",
        description = "Incolla i link dei giocatori (uno per riga: Wikipedia per il calcio, Proballers per il basket): " +
            "l'app ricava il giocatore dal link " +
            "e recupera automaticamente anno, carriera migliore, stato, nazione e stemma del club. " +
            "I giocatori trovati si aggiungono alla lista di ${sport.label}; se un giocatore è già presente " +
            "(stesso nome e nazione) viene aggiornato invece di duplicarlo.",
        buttonLabel = "Genera lista",
        onFound = { rows -> if (rows.isNotEmpty()) repository.generateList(sport, rows) },
    )
}
