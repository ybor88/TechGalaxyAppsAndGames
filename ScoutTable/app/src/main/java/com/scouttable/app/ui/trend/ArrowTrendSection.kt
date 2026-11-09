// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.trend

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.lookup.BasketRole
import com.scouttable.app.data.lookup.CalcioReparto
import com.scouttable.app.data.lookup.basketRoleOf
import com.scouttable.app.data.lookup.calcioRepartoOf
import com.scouttable.app.data.ranking.PlayerScoring
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.common.calcioTrend
import com.scouttable.app.ui.common.effTrend
import com.scouttable.app.ui.theme.ScoutGreen

private val DownColor = Color(0xFFDC2626)

/** Un gruppo di ruolo con i giocatori divisi per freccia (su / giù / trattino). */
private data class RoleArrows(val label: String, val up: List<Player>, val down: List<Player>, val flat: List<Player>) {
    val total get() = up.size + down.size + flat.size
}

/** Freccia di un giocatore, con la stessa regola mostrata nella lista (vedi PlayerRow). */
private fun trendOf(player: Player, sport: Sport) = if (sport.isBasket) effTrend(player) else calcioTrend(player)

/** Etichetta del ruolo per il raggruppamento, nell'ordine del campo (dalla difesa all'attacco). */
private fun roleGroups(sport: Sport): List<Pair<String, (Player) -> Boolean>> =
    if (sport.isBasket) {
        listOf(
            "Playmaker" to { p -> basketRoleOf(p.ruolo) == BasketRole.PLAYMAKER },
            "Guardie" to { p -> basketRoleOf(p.ruolo) == BasketRole.GUARDIA },
            "Ali piccole" to { p -> basketRoleOf(p.ruolo) == BasketRole.ALA_PICCOLA },
            "Ali grandi" to { p -> basketRoleOf(p.ruolo) == BasketRole.ALA_GRANDE },
            "Centri" to { p -> basketRoleOf(p.ruolo) == BasketRole.CENTRO },
            "Ruolo non indicato" to { p -> basketRoleOf(p.ruolo) == null },
        )
    } else {
        listOf(
            "Portieri" to { p -> calcioRepartoOf(p.ruolo) == CalcioReparto.PORTIERE },
            "Difensori" to { p -> calcioRepartoOf(p.ruolo) == CalcioReparto.DIFENSORE },
            "Centrocampisti" to { p -> calcioRepartoOf(p.ruolo) == CalcioReparto.CENTROCAMPISTA },
            "Attaccanti" to { p -> calcioRepartoOf(p.ruolo) == CalcioReparto.ATTACCANTE },
            "Ruolo non indicato" to { p -> calcioRepartoOf(p.ruolo) == null },
        )
    }

/**
 * Giocatori raggruppati per freccia di rendimento (la stessa della lista: Eff × 2 contro minuti
 * medi nel basket, gol rispetto alle presenze per reparto nel calcio), prima in totale e poi
 * ruolo per ruolo. Toccando un ruolo si vedono i nomi divisi per freccia.
 */
@Composable
fun ArrowTrendSection(sport: Sport, players: List<Player>) {
    val groups = remember(players, sport) {
        roleGroups(sport).map { (label, matches) ->
            val inRole = players.filter(matches)
            RoleArrows(
                label = label,
                up = inRole.filter { trendOf(it, sport) == 1 }.sortedBy { it.nome },
                down = inRole.filter { trendOf(it, sport) == -1 }.sortedBy { it.nome },
                flat = inRole.filter { trendOf(it, sport) == 0 }.sortedBy { it.nome },
            )
        }.filter { it.total > 0 }
    }
    var expanded by rememberSaveable(sport) { mutableStateOf(setOf<String>()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Andamento per freccia", style = MaterialTheme.typography.titleMedium)
        Text(
            if (sport.isBasket) "Freccia su se l'Eff medio × 2 supera i minuti medi a partita, giù se è minore; " +
                "trattino se pari o senza Eff/minuti (solo con l'URL Proballers)."
            else "Freccia su se i gol superano le presenze diviso 2,5 (attaccanti), 5 (centrocampisti) o 10 " +
                "(difensori); portieri con al massimo 1,15 gol subiti a partita. Trattino se il ruolo non è riconosciuto " +
                "o mancano le presenze.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        if (groups.isEmpty()) {
            Text("Nessun giocatore in lista.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        // Totale di tutti i ruoli.
        ArrowCounts(
            label = "Tutti i giocatori",
            up = groups.sumOf { it.up.size },
            down = groups.sumOf { it.down.size },
            flat = groups.sumOf { it.flat.size },
            bold = true,
        )
        Spacer(modifier = Modifier.size(8.dp))
        groups.forEach { group ->
            val open = group.label in expanded
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { expanded = if (open) expanded - group.label else expanded + group.label },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ArrowCounts(
                            label = "${group.label} (${group.total})",
                            up = group.up.size,
                            down = group.down.size,
                            flat = group.flat.size,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (open) "Chiudi" else "Mostra giocatori",
                        )
                    }
                    if (open) {
                        PlayerNames(Icons.Default.ArrowUpward, ScoutGreen, group.up)
                        PlayerNames(Icons.Default.ArrowDownward, DownColor, group.down)
                        PlayerNames(Icons.Default.Remove, MaterialTheme.colorScheme.onSurfaceVariant, group.flat)
                    }
                }
            }
        }
    }
}

/** Riga con etichetta e i tre conteggi ↑ ↓ — con le rispettive percentuali sul gruppo. */
@Composable
private fun ArrowCounts(label: String, up: Int, down: Int, flat: Int, modifier: Modifier = Modifier, bold: Boolean = false) {
    val total = (up + down + flat).coerceAtLeast(1)
    Column(modifier = modifier) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            CountChip(Icons.Default.ArrowUpward, ScoutGreen, up, total)
            CountChip(Icons.Default.ArrowDownward, DownColor, down, total)
            CountChip(Icons.Default.Remove, MaterialTheme.colorScheme.onSurfaceVariant, flat, total)
        }
    }
}

@Composable
private fun CountChip(icon: ImageVector, color: Color, count: Int, total: Int) {
    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
    Text(
        "$count (${Math.round(count * 100f / total)}%)",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.padding(start = 2.dp),
    )
    Spacer(modifier = Modifier.width(14.dp))
}

@Composable
private fun PlayerNames(icon: ImageVector, color: Color, players: List<Player>) {
    if (players.isEmpty()) return
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 8.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            players.joinToString(", ") { it.nome },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * Andamento del calcio: raggruppamento per freccia e ruolo su tutta la lista, poi i grafici a
 * torta dei soli preferiti ⭐ (richiesto così: nel calcio la lista è lunga e i grafici di tutti
 * diluirebbero la rosa "ristretta"). Tre torte: decennio di nascita, reparto e rendimento rispetto
 * alla soglia della stella del proprio ruolo (le formule di rendimento sono diverse per ruolo,
 * il rapporto con la soglia invece è confrontabile tra portieri, difensori e attaccanti).
 */
@Composable
fun CalcioTrendScreen(padding: PaddingValues) {
    val repository = rememberPlayerRepository()
    val players by repository.observePlayers(Sport.CALCIO).collectAsState(initial = emptyList())
    val starPlayers = remember(players) { players.filter { it.star } }
    Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        ArrowTrendSection(Sport.CALCIO, players)
        Spacer(modifier = Modifier.size(28.dp))
        Text("Andamento preferiti ⭐", style = MaterialTheme.typography.titleMedium)
        Text(
            "Distribuzione dei soli giocatori con la stella per decennio di nascita, reparto e rendimento " +
                "rispetto alla soglia stella del loro ruolo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        if (starPlayers.size < 2) {
            Text(
                "Servono almeno due preferiti per costruire i grafici.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val withYear = starPlayers.filter { it.anno > 0 }
            if (withYear.isNotEmpty()) {
                TrendPieCard("Per decennio di nascita", decadeSlices(withYear))
                Spacer(modifier = Modifier.size(20.dp))
            }
            TrendPieCard("Per reparto", repartoSlices(starPlayers))
            Spacer(modifier = Modifier.size(20.dp))
            TrendPieCard("Per rendimento (rispetto alla soglia stella)", rendimentoSlices(starPlayers))
        }
    }
}

private fun repartoSlices(players: List<Player>): List<Slice> {
    val order = listOf(
        CalcioReparto.PORTIERE to "Portieri",
        CalcioReparto.DIFENSORE to "Difensori",
        CalcioReparto.CENTROCAMPISTA to "Centrocampisti",
        CalcioReparto.ATTACCANTE to "Attaccanti",
    )
    val byReparto = players.groupingBy { calcioRepartoOf(it.ruolo) }.eachCount()
    val entries = order.mapNotNull { (r, label) -> byReparto[r]?.let { label to it } } +
        listOfNotNull(byReparto[null]?.let { "Altro" to it })
    return colorize(entries)
}

// Fasce del rapporto rendimento/soglia stella, in percentuale.
private val rendimentoBands = listOf(
    0 to "Sotto soglia",
    100 to "100-150%",
    150 to "150-200%",
    200 to "Oltre 200%",
)

private fun rendimentoSlices(players: List<Player>): List<Slice> {
    fun bandOf(p: Player): Int {
        val threshold = PlayerScoring.calcioStarThreshold(p.ruolo).coerceAtLeast(1)
        val pct = PlayerScoring.calcioScore(p) * 100 / threshold
        return rendimentoBands.last { pct >= it.first }.first
    }
    val counts = players.groupingBy(::bandOf).eachCount()
    return colorize(rendimentoBands.mapNotNull { (b, label) -> counts[b]?.let { label to it } })
}
