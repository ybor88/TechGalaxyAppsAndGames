package com.scouttable.app.ui.ranking

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.scouttable.app.data.Sport
import com.scouttable.app.data.ranking.PlayerScoring
import com.scouttable.app.data.rememberPlayerRepository

/**
 * Classifica basket per Eff medio di carriera (coefficiente Proballers, media su tutte le
 * stagioni, non l'Eff della singola stagione migliore usata per il "secondo logo") più bonus NBA
 * (vedi [PlayerScoring.basketScore]): ordine decrescente, podio oro/argento/bronzo per i primi
 * tre, lista semplice dal quarto posto in poi. Richiesta esplicitamente solo per il basket — il
 * calcio ha le sue 4 classifiche per ruolo, vedi [CalcioRankingScreen].
 *
 * Chi ha la squadra in NBA riceve +5 sull'Eff medio come bonus (mostrato in verde separatamente
 * dall'Eff, così si distingue subito quanto viene dal bonus): a parità di Eff medio questo fa
 * salire prima in classifica chi gioca in NBA, ed è anche il valore usato per l'ordinamento in
 * generale, non solo negli spareggi.
 */
@Composable
fun RankingScreen(padding: PaddingValues, onOpenPlayer: (String) -> Unit) {
    val repository = rememberPlayerRepository()
    val players by repository.observePlayers(Sport.BASKET).collectAsState(initial = emptyList())

    // effMedio = 0 significa "mai calcolato" (nessun URL Proballers incollato), non un vero Eff
    // pari a zero: va escluso invece di comparire come ultimo in classifica.
    val ranked = remember(players) {
        players.filter { it.effMedio > 0 }.sortedByDescending { PlayerScoring.basketScore(it) }
    }

    PodiumRankingScreen(
        modifier = Modifier.padding(padding),
        title = "Classifica per Eff medio",
        subtitle = "Giocatori ordinati per coefficiente di efficienza medio di carriera (Eff) più bonus NBA, " +
            "dal più forte. Disponibile solo per chi ha l'URL Proballers incollato.",
        emptyMessage = "Nessun giocatore con Eff medio disponibile.",
        ranked = ranked,
        onOpenPlayer = onOpenPlayer,
        primaryLineOf = { "Eff ${it.effMedio}" },
        bonusLineOf = { if (PlayerScoring.isInNba(it)) "+${PlayerScoring.nbaBonus(it)} bonus NBA" else null },
    )
}
