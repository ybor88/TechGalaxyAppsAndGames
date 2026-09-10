package com.scouttable.app.ui.update

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.scouttable.app.data.Sport
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.common.PasteListScreen

@Composable
fun UpdateListScreen(sport: Sport, padding: PaddingValues) {
    val repository = rememberPlayerRepository()

    PasteListScreen(
        sport = sport,
        padding = padding,
        title = "Aggiorna nuova lista",
        description = "Incolla i nomi dei giocatori (uno per riga): l'app cerca ogni giocatore su internet " +
            "e aggiorna tutti i campi se il giocatore esiste già (stesso nome+nazione), oppure lo aggiunge se è nuovo.",
        buttonLabel = "Aggiorna lista",
        onFound = { rows -> repository.updateList(sport, rows) },
    )
}
