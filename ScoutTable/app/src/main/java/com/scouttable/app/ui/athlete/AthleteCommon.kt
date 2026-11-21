// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.athlete

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.athlete.AthleteScoring
import com.scouttable.app.ui.common.PlayerAvatar
import com.scouttable.app.ui.common.flagEmojiFor
import com.scouttable.app.ui.ranking.RankPosition
import com.scouttable.app.ui.theme.ScoutGreen

internal val DownRed = Color(0xFFDC2626)

/** Freccia del rendimento: su (verde), giù (rossa), trattino (grigio). */
@Composable
fun TrendArrow(trend: Int, size: Dp = 16.dp) {
    when (trend) {
        1 -> Icon(Icons.Default.ArrowUpward, contentDescription = "Sopra la media", tint = ScoutGreen, modifier = Modifier.size(size))
        -1 -> Icon(Icons.Default.ArrowDownward, contentDescription = "Sotto la media", tint = DownRed, modifier = Modifier.size(size))
        else -> Icon(Icons.Default.Remove, contentDescription = "Nella media", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size))
    }
}

/** Scelta Maschile / Femminile (solo sport da combattimento). */
@Composable
fun GenderSelector(female: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        listOf(false to "Maschile", true to "Femminile").forEach { (value, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onChange(value) }.padding(end = 16.dp),
            ) {
                RadioButton(selected = female == value, onClick = { onChange(value) })
                Text(label)
            }
        }
    }
}

/** "🇮🇹 Italy · 1979 · Ferrari": anagrafica breve dell'atleta. */
fun athleteSubtitle(p: Player): String {
    val flag = flagEmojiFor(p.nazione)
    return listOfNotNull(
        p.nazione.takeIf { it.isNotBlank() }?.let { if (flag.isNotBlank()) "$flag $it" else it },
        p.anno.takeIf { it > 0 }?.toString(),
        p.carrieraMigliore.takeIf { it.isNotBlank() },
        p.ruolo.takeIf { (p.sport.isCombat || p.sport.isTeamSport) && it.isNotBlank() },
    ).joinToString(" · ")
}

/**
 * Card di un atleta: foto, nome con stella/visionato/freccia, anagrafica, statistiche principali
 * e a destra il rapporto (punti per gara o % vittorie) con il distacco dalla media.
 */
@Composable
fun AthleteRow(
    player: Player,
    allPlayers: List<Player>?,
    onClick: () -> Unit,
    rank: RankPosition? = null,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    /** Indicatore "Revisione tra N gg" (lista principale), come nella lista di calcio e basket. */
    showReviewHint: Boolean = false,
) {
    val sport = player.sport
    val metric = AthleteScoring.ratioMetric(sport)
    val distacco = allPlayers?.let { AthleteScoring.distacco(player, it) }
    val trend = allPlayers?.let { AthleteScoring.trend(player, it) } ?: 0
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(name = player.nome, logoPath = player.logoPath, size = 48.dp)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(player.nome, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
                    if (player.star) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("⭐", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (player.visionato) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.Visibility, contentDescription = "Visionato", tint = ScoutGreen, modifier = Modifier.size(16.dp))
                    }
                    if (player.scouting) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("🔎", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (allPlayers != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        TrendArrow(trend)
                    }
                }
                AthleteScoring.starReason(player)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(athleteSubtitle(player), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (AthleteScoring.hasStats(player)) AthleteScoring.statsLine(player) else "Statistiche non disponibili",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (player.stato.isNotBlank()) {
                        Text(player.stato, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (showReviewHint) com.scouttable.app.ui.common.ReviewHintBadge(player)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // MotoGP/F1: logo della scuderia accanto al rating.
                    TeamLogo(player, 26.dp)
                    Text(
                        AthleteScoring.formatValue(player, metric),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ScoutGreen,
                    )
                }
                Text(metric.short, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (distacco != null) {
                    Text(
                        AthleteScoring.formatDistacco(sport, distacco),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (trend >= 0) ScoutGreen else DownRed,
                    )
                }
                rank?.let {
                    Text("${it.position}°/${it.total}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onRefresh != null) {
                    if (refreshing) {
                        CircularProgressIndicator(modifier = Modifier.padding(top = 4.dp).size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Aggiorna da Wikipedia", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Logo della scuderia (MotoGP/F1, da Wikipedia) o, per Football/Rugby/Hockey, della squadra della
 *  stagione migliore quando è diversa da quella della carriera (già nell'avatar); niente per gli altri sport. */
@Composable
fun TeamLogo(player: Player, size: Dp) {
    val logo = player.secondLogoPath?.takeIf {
        it.isNotBlank() && (player.sport.isMotor || (player.sport.isTeamSport && it != player.logoPath))
    } ?: return
    PlayerAvatar(name = player.secondLogoClub.ifBlank { player.carrieraMigliore }, logoPath = logo, size = size)
    Spacer(modifier = Modifier.width(6.dp))
}

// Colori delle scuderie più note (griglia di partenza); le altre ricevono un colore stabile dal nome.
private val teamColors = listOf(
    "ferrari" to Color(0xFFDC0000),
    "mercedes" to Color(0xFF00A19C),
    "red bull" to Color(0xFF1E41FF),
    "mclaren" to Color(0xFFFF8000),
    "williams" to Color(0xFF005AFF),
    "renault" to Color(0xFFFFD800),
    "alpine" to Color(0xFF0090FF),
    "benetton" to Color(0xFF00A651),
    "jordan" to Color(0xFFF7D417),
    "lotus" to Color(0xFF1A1A1A),
    "brabham" to Color(0xFF2E7D32),
    "aston martin" to Color(0xFF00594F),
    "tyrrell" to Color(0xFF1565C0),
    "sauber" to Color(0xFF9B0000),
    "toro rosso" to Color(0xFF0032A0),
    "alfa romeo" to Color(0xFF900000),
    "honda" to Color(0xFFCC0000),
    "yamaha" to Color(0xFF0D47A1),
    "ducati" to Color(0xFFD50000),
    "suzuki" to Color(0xFF1976D2),
    "aprilia" to Color(0xFF212121),
    "ktm" to Color(0xFFFF6F00),
    "kawasaki" to Color(0xFF2E7D32),
    "gresini" to Color(0xFF29B6F6),
    "vr46" to Color(0xFFFFEB3B),
)

private val fallbackColors = listOf(
    Color(0xFF7E57C2), Color(0xFF26A69A), Color(0xFFEF6C00), Color(0xFF5C6BC0),
    Color(0xFF8D6E63), Color(0xFFEC407A), Color(0xFF66BB6A), Color(0xFF42A5F5),
)

/** Colore della scuderia (o, senza scuderia, della nazione): sempre lo stesso per lo stesso nome. */
fun teamColor(name: String): Color {
    val key = name.trim().lowercase()
    teamColors.firstOrNull { key.contains(it.first) }?.let { return it.second }
    if (key.isEmpty()) return Color(0xFF9E9E9E)
    return fallbackColors[Math.floorMod(key.hashCode(), fallbackColors.size)]
}

/** Colore della livrea dell'atleta: scuderia se nota, altrimenti la nazione. */
fun athleteColor(p: Player): Color = teamColor(p.carrieraMigliore.ifBlank { p.nazione })
