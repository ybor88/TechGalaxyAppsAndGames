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
        description = "Incolla i nomi dei giocatori (uno per riga): l'app cerca ogni giocatore su internet " +
            "e recupera automaticamente anno, carriera migliore, stato, nazione e stemma del club. " +
            "La lista attuale di ${sport.label} verrà sostituita interamente con i giocatori trovati.",
        buttonLabel = "Genera lista",
        onFound = { rows -> if (rows.isNotEmpty()) repository.generateList(sport, rows) },
    )
}
