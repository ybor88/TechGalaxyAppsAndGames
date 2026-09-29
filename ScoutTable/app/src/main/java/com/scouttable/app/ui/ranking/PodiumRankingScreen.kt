// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.ui.common.LoadingBar
import com.scouttable.app.ui.common.PlayerAvatar
import com.scouttable.app.ui.common.PlayerFiltersPanel
import com.scouttable.app.ui.common.rememberPlayerFilterState
import com.scouttable.app.ui.theme.ScoutGreen

private val Gold = Color(0xFFFFD54F)
private val Silver = Color(0xFFC7CDD6)
private val Bronze = Color(0xFFCD7F32)
private val PodiumTextDark = Color(0xFF3A2E00)

/**
 * Podio (oro/argento/bronzo) + lista per una classifica già ordinata: usato sia dalla classifica
 * basket per Eff medio ([RankingScreen]) sia dalle 4 classifiche calcio per ruolo
 * ([CalcioRankingScreen]), che differiscono solo per il valore di rendimento e per come viene
 * mostrato (vedi [primaryLineOf]/[bonusLineOf]). [ranked] deve arrivare già filtrato e ordinato
 * in modo decrescente dal chiamante.
 */
@Composable
fun PodiumRankingScreen(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    emptyMessage: String,
    ranked: List<Player>,
    onOpenPlayer: (String) -> Unit,
    loading: Boolean = false,
    primaryLineOf: (Player) -> String,
    bonusLineOf: (Player) -> String? = { null },
) {
    val filters = rememberPlayerFilterState()
    var showFilters by remember { mutableStateOf(false) }
    // I filtri restringono la classifica e la rinumerano (podio compreso): es. "i migliori
    // italiani" diventa una classifica a sé, dal 1° posto.
    val filtered by remember(ranked) { derivedStateOf { ranked.filter(filters::matches) } }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Intestazione fissa: titolo, totale e filtri restano visibili mentre la classifica scorre.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            FilterToggleButton(activeCount = filters.activeCount, expanded = showFilters, onClick = { showFilters = !showFilters })
            TotalCountBadge(filtered.size, total = ranked.size)
        }
        if (showFilters) {
            PlayerFiltersPanel(state = filters, players = ranked, modifier = Modifier.padding(top = 8.dp))
            if (filters.activeCount > 0) {
                TextButton(onClick = { filters.clear() }, modifier = Modifier.align(Alignment.End)) { Text("Azzera filtri") }
            }
        }

        // LazyColumn: compone solo le righe a schermo. Con una Column scrollabile venivano create
        // subito tutte le righe (e caricati tutti gli stemmi), rallentando l'apertura delle
        // classifiche lunghe.
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            item(key = "subtitle") {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
            }

            if (loading) {
                item(key = "loading") { LoadingBar(label = "Caricamento classifica…") }
                return@LazyColumn
            }

            if (filtered.isEmpty()) {
                item(key = "empty") {
                    Text(
                        if (ranked.isEmpty()) emptyMessage else "Nessun giocatore in classifica corrisponde ai filtri.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@LazyColumn
            }

            if (filtered.size >= 3) {
                item(key = "podium") {
                    Podium(top3 = filtered.take(3), onOpenPlayer = onOpenPlayer, primaryLineOf = primaryLineOf, bonusLineOf = bonusLineOf)
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            val rest = if (filtered.size > 3) filtered.drop(3) else if (filtered.size < 3) filtered else emptyList()
            val firstRank = if (filtered.size >= 3) 4 else 1
            itemsIndexed(rest, key = { _, player -> player.id }) { index, player ->
                RankingRow(
                    rank = index + firstRank,
                    player = player,
                    primaryLine = primaryLineOf(player),
                    bonusLine = bonusLineOf(player),
                    onClick = { onOpenPlayer(player.id) },
                )
            }
        }
    }
}

/** Mostra/nasconde il pannello filtri; il badge indica quanti filtri sono attivi anche a pannello chiuso. */
@Composable
private fun FilterToggleButton(activeCount: Int, expanded: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (activeCount > 0) Badge { Text("$activeCount") } }) {
            Icon(
                Icons.Default.FilterList,
                contentDescription = if (expanded) "Nascondi filtri" else "Mostra filtri",
                tint = if (activeCount > 0 || expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Podium(
    top3: List<Player>,
    onOpenPlayer: (String) -> Unit,
    primaryLineOf: (Player) -> String,
    bonusLineOf: (Player) -> String?,
) {
    val first = top3[0]
    val second = top3.getOrNull(1)
    val third = top3.getOrNull(2)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        second?.let {
            PodiumColumn(
                player = it,
                rank = 2,
                color = Silver,
                barHeight = 92.dp,
                primaryLine = primaryLineOf(it),
                bonusLine = bonusLineOf(it),
                onClick = { onOpenPlayer(it.id) },
                modifier = Modifier.weight(1f),
            )
        } ?: Spacer(modifier = Modifier.weight(1f))
        PodiumColumn(
            player = first,
            rank = 1,
            color = Gold,
            barHeight = 124.dp,
            primaryLine = primaryLineOf(first),
            bonusLine = bonusLineOf(first),
            onClick = { onOpenPlayer(first.id) },
            modifier = Modifier.weight(1f),
        )
        third?.let {
            PodiumColumn(
                player = it,
                rank = 3,
                color = Bronze,
                barHeight = 68.dp,
                primaryLine = primaryLineOf(it),
                bonusLine = bonusLineOf(it),
                onClick = { onOpenPlayer(it.id) },
                modifier = Modifier.weight(1f),
            )
        } ?: Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PodiumColumn(
    player: Player,
    rank: Int,
    color: Color,
    barHeight: Dp,
    primaryLine: String,
    bonusLine: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlayerAvatar(name = player.nome, logoPath = player.logoPath, size = 52.dp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            player.nome,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(primaryLine, style = MaterialTheme.typography.labelSmall, color = ScoutGreen)
        if (bonusLine != null) {
            Text(bonusLine, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = ScoutGreen)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(color),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                "$rank",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PodiumTextDark,
            )
        }
    }
}

@Composable
private fun RankingRow(rank: Int, player: Player, primaryLine: String, bonusLine: String?, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$rank",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(32.dp),
            )
            PlayerAvatar(name = player.nome, logoPath = player.logoPath, size = 40.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(player.nome, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    player.carrieraMigliore.ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(primaryLine, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ScoutGreen)
                if (bonusLine != null) {
                    Text(bonusLine, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = ScoutGreen)
                }
            }
        }
    }
}

/** Contatore dei record totali in classifica, a destra del titolo. Con [total] diverso da [count]
 *  (classifica filtrata) mostra "filtrati / totali". */
@Composable
internal fun TotalCountBadge(count: Int, total: Int = count) {
    Text(
        if (count == total) "Totale: $count" else "Totale: $count / $total",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
