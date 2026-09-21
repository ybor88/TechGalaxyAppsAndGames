package com.scouttable.app.ui.ranking

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.lookup.isCentrocampista
import com.scouttable.app.data.lookup.isDifensore
import com.scouttable.app.data.lookup.isPortiere
import com.scouttable.app.data.ranking.PlayerScoring
import com.scouttable.app.data.rememberPlayerRepository

/** I 4 ruoli calcio con classifica dedicata: vedi [matchesRuolo] per come un [Player.ruolo]
 *  grezzo viene assegnato a uno di questi. */
enum class CalcioRuoloFiltro(val label: String, val subtitle: String) {
    ATTACCANTI(
        "Attaccanti",
        "Ordinati per rendimento gol di carriera: gol totali rapportati alle presenze totali " +
            "(non solo alla stagione migliore), dal più forte.",
    ),
    CENTROCAMPISTI(
        "Centrocampisti",
        "Ordinati per rendimento gol di carriera: gol totali rapportati alle presenze totali " +
            "(non solo alla stagione migliore), dal più forte.",
    ),
    DIFENSORI(
        "Difensori",
        "Ordinati per presenze totali di carriera, con in più il rendimento gol (stessa formula " +
            "di attaccanti e centrocampisti) come bonus per il contributo offensivo.",
    ),
    PORTIERI(
        "Portieri",
        "Ordinati per rendimento portiere di carriera: capacità di non subire gol rapportata a " +
            "presenze e gol subiti totali, dal più forte.",
    ),
}

// Stesso raggruppamento usato da PlayerScoring.calcioScore per la stella automatica: attaccanti
// (ATTACCANTI) è il bucket "tutto il resto" (comprende anche i ruoli non riconosciuti), coerente
// col fallback su goalScore in PlayerScoring.
private fun matchesRuolo(filtro: CalcioRuoloFiltro, ruolo: String): Boolean = when (filtro) {
    CalcioRuoloFiltro.PORTIERI -> isPortiere(ruolo)
    CalcioRuoloFiltro.DIFENSORI -> isDifensore(ruolo)
    CalcioRuoloFiltro.CENTROCAMPISTI -> isCentrocampista(ruolo)
    CalcioRuoloFiltro.ATTACCANTI -> !isPortiere(ruolo) && !isDifensore(ruolo) && !isCentrocampista(ruolo)
}

private fun scoreFor(filtro: CalcioRuoloFiltro, player: Player): Int = when (filtro) {
    CalcioRuoloFiltro.PORTIERI -> PlayerScoring.goalkeeperScore(player)
    CalcioRuoloFiltro.DIFENSORI -> PlayerScoring.defenderScore(player)
    CalcioRuoloFiltro.CENTROCAMPISTI, CalcioRuoloFiltro.ATTACCANTI -> PlayerScoring.goalScore(player)
}

// Per i difensori il valore primario mostrato è le presenze (il bonus rendimento gol è mostrato a
// parte, vedi bonusLineFor): la somma delle due è scoreFor, stesso schema dell'Eff+bonus NBA nel
// basket.
private fun primaryLineFor(filtro: CalcioRuoloFiltro, player: Player): String = when (filtro) {
    CalcioRuoloFiltro.DIFENSORI -> "${player.presenze} presenze"
    else -> "Rendimento ${scoreFor(filtro, player)}"
}

private fun bonusLineFor(filtro: CalcioRuoloFiltro, player: Player): String? {
    if (filtro != CalcioRuoloFiltro.DIFENSORI) return null
    val bonus = PlayerScoring.goalScore(player)
    return if (bonus > 0) "+$bonus rendimento gol" else null
}

/** Classifica calcio per un singolo ruolo (vedi [CalcioRuoloFiltro]): stesso podio/lista della
 *  classifica basket ([RankingScreen]), formula di rendimento diversa per ruolo. */
@Composable
fun CalcioRankingScreen(filtro: CalcioRuoloFiltro, modifier: Modifier = Modifier, onOpenPlayer: (String) -> Unit) {
    val repository = rememberPlayerRepository()
    val players by repository.observePlayers(Sport.CALCIO).collectAsState(initial = emptyList())

    // presenze = 0 significa "nessuna statistica di carriera trovata": va escluso invece di
    // comparire come ultimo in classifica, stesso criterio di effMedio = 0 nel basket.
    val ranked = remember(players, filtro) {
        players.filter { it.presenze > 0 && matchesRuolo(filtro, it.ruolo) }
            .sortedByDescending { scoreFor(filtro, it) }
    }

    PodiumRankingScreen(
        modifier = modifier,
        title = "Classifica ${filtro.label}",
        subtitle = filtro.subtitle,
        emptyMessage = "Nessun giocatore in ruolo ${filtro.label.lowercase()} con statistiche disponibili.",
        ranked = ranked,
        onOpenPlayer = onOpenPlayer,
        primaryLineOf = { primaryLineFor(filtro, it) },
        bonusLineOf = { bonusLineFor(filtro, it) },
    )
}

/** Le 4 classifiche calcio per ruolo, con un selettore in alto: tab unica "Classifica" nella
 *  bottom nav di [com.scouttable.app.ui.sporthome.SportHomeScreen] per il calcio, a differenza
 *  del basket che ha un'unica classifica (Eff medio) e non ha bisogno di sotto-tab. */
@Composable
fun CalcioRankingTabsScreen(padding: PaddingValues, onOpenPlayer: (String) -> Unit) {
    var selected by remember { mutableStateOf(CalcioRuoloFiltro.ATTACCANTI) }

    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
        val entries = CalcioRuoloFiltro.entries
        ScrollableTabRow(selectedTabIndex = entries.indexOf(selected)) {
            entries.forEach { filtro ->
                Tab(
                    selected = filtro == selected,
                    onClick = { selected = filtro },
                    text = { Text(filtro.label) },
                )
            }
        }
        CalcioRankingScreen(
            filtro = selected,
            modifier = Modifier.weight(1f),
            onOpenPlayer = onOpenPlayer,
        )
    }
}
