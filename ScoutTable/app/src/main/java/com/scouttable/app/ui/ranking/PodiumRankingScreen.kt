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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.scouttable.app.ui.common.PlayerAvatar
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
    primaryLineOf: (Player) -> String,
    bonusLineOf: (Player) -> String? = { null },
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        if (ranked.isEmpty()) {
            Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        if (ranked.size >= 3) {
            Podium(top3 = ranked.take(3), onOpenPlayer = onOpenPlayer, primaryLineOf = primaryLineOf, bonusLineOf = bonusLineOf)
            Spacer(modifier = Modifier.height(24.dp))
        }

        val rest = if (ranked.size > 3) ranked.drop(3) else if (ranked.size < 3) ranked else emptyList()
        rest.forEachIndexed { index, player ->
            val rank = if (ranked.size >= 3) index + 4 else index + 1
            RankingRow(
                rank = rank,
                player = player,
                primaryLine = primaryLineOf(player),
                bonusLine = bonusLineOf(player),
                onClick = { onOpenPlayer(player.id) },
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
