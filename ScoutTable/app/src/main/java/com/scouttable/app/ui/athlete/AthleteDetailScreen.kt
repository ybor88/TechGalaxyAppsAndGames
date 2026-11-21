// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.athlete

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.athlete.AthleteScoring
import com.scouttable.app.data.athlete.gare
import com.scouttable.app.data.athlete.gareVinte
import com.scouttable.app.data.athlete.giriVeloci
import com.scouttable.app.data.athlete.incontri
import com.scouttable.app.data.athlete.labelFor
import com.scouttable.app.data.athlete.bestSeasonDetail
import com.scouttable.app.data.athlete.bestSeasonTitle
import com.scouttable.app.data.athlete.mondiali
import com.scouttable.app.data.athlete.pareggi
import com.scouttable.app.data.athlete.persi
import com.scouttable.app.data.athlete.persiKo
import com.scouttable.app.data.athlete.podi
import com.scouttable.app.data.athlete.pole
import com.scouttable.app.data.athlete.punti
import com.scouttable.app.data.athlete.titoli
import com.scouttable.app.data.athlete.vinti
import com.scouttable.app.data.athlete.vintiKo
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.common.PlayerAvatar
import com.scouttable.app.ui.ranking.rankPositionOf
import com.scouttable.app.ui.theme.ScoutGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AthleteDetailScreen(sport: Sport, playerId: String, onBack: () -> Unit) {
    val repository = rememberPlayerRepository()
    val context = LocalContext.current
    val player by repository.observePlayer(sport, playerId).collectAsState(initial = null)
    // Aprire la scheda segna il giocatore come visionato (solo all'apertura, una volta).
    androidx.compose.runtime.LaunchedEffect(playerId) { repository.markVisionato(playerId) }
    val players by repository.observePlayers(sport).collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf(false) }
    val current = player

    if (editing && current != null) AthleteEditDialog(sport = sport, player = current, onDismiss = { editing = false }, onDeleted = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.nome ?: AthleteScoring.athleteWord(sport).replaceFirstChar { it.uppercase() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro") }
                },
                actions = {
                    current?.proballersUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        IconButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Apri Wikipedia")
                        }
                    }
                    IconButton(onClick = { editing = true }, enabled = current != null) {
                        Icon(Icons.Default.Edit, contentDescription = "Modifica")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (current == null) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val rank = remember(current, players) { rankPositionOf(sport, current, players) }
        val distacco = remember(current, players) { AthleteScoring.distacco(current, players) }
        val trend = remember(current, players) { AthleteScoring.trend(current, players) }
        val ratioMetric = AthleteScoring.ratioMetric(sport)
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatar(name = current.nome, logoPath = current.logoPath, size = 96.dp)
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(current.nome + if (current.star) " ⭐" else "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(athleteSubtitle(current), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        listOf(current.competizione, current.secondLogoPeriodo, current.stato).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (sport.isTeamSport) BestCareerSeasonCard(current)

            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ratioMetric.label, style = MaterialTheme.typography.labelMedium)
                        Text(AthleteScoring.formatValue(current, ratioMetric), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Distacco dalla media: ${AthleteScoring.formatDistacco(sport, distacco)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TrendArrow(trend, size = 40.dp)
                }
            }
            rank?.let {
                Text(
                    "${it.position}° su ${it.total} nella classifica ${it.label}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ScoutGreen,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Text("Statistiche", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            val tiles = statTiles(current)
            tiles.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    row.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
            Text(
                "Fonte: it.wikipedia.org · © Roberto Di Flumeri",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
    }
}

/**
 * Football, Rugby, Hockey: carriera migliore (squadra con più partite, nel football più stagioni) e
 * stagione migliore (periodo nel rugby), ognuna col logo della squadra. Stessa squadra: una colonna.
 */
@Composable
private fun BestCareerSeasonCard(p: Player) {
    val hasBest = p.secondLogoClub.isNotBlank()
    val sameTeam = hasBest && (
        p.secondLogoClub.trim().equals(p.carrieraMigliore.trim(), ignoreCase = true) ||
            (!p.secondLogoPath.isNullOrBlank() && p.secondLogoPath == p.logoPath)
        )
    val careerLogo = p.logoPath?.takeIf { com.scouttable.app.data.lookup.WikipediaClubLogo.isLogoImage(it) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            BestColumn(
                team = p.carrieraMigliore.ifBlank { "—" },
                logo = careerLogo,
                title = if (sameTeam) "Carriera e ${bestSeasonTitle(p.sport).lowercase()}" else "Carriera migliore",
                detail = if (sameTeam) bestSeasonDetail(p) else p.secondLogoPeriodo,
            )
            if (hasBest && !sameTeam) {
                BestColumn(
                    team = p.secondLogoClub,
                    logo = p.secondLogoPath,
                    title = bestSeasonTitle(p.sport),
                    detail = bestSeasonDetail(p),
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BestColumn(team: String, logo: String?, title: String, detail: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
        PlayerAvatar(name = team, logoPath = logo, size = 64.dp)
        Text(team, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (detail.isNotBlank()) {
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

private fun statTiles(p: Player): List<Pair<String, String>> =
    if (p.sport.isTeamSport) teamStatTiles(p)
    else if (p.sport.isMotor) listOf(
        "Mondiali" to "${p.mondiali}",
        "Punti" to "${p.punti}",
        "Gare" to "${p.gare}",
        "Vittorie" to "${p.gareVinte}",
        "Podi" to "${p.podi}",
        "Pole" to "${p.pole}",
        "Giri veloci" to "${p.giriVeloci}",
        "Punti/gara" to AthleteScoring.format(com.scouttable.app.data.athlete.AthleteMetric.PUNTI_GARA, AthleteScoring.puntiPerGara(p)),
        "% podi" to if (p.gare > 0) "${p.podi * 100 / p.gare}%" else "—",
    ) else listOf(
        "Incontri" to "${p.incontri}",
        "Vinti" to "${p.vinti}",
        "Vinti KO" to "${p.vintiKo}",
        "Persi" to "${p.persi}",
        "Persi KO" to "${p.persiKo}",
        "Pareggi" to "${p.pareggi}",
        "% vittorie" to AthleteScoring.format(com.scouttable.app.data.athlete.AthleteMetric.PERCENTUALE, AthleteScoring.percentualeVittorie(p)),
        "% KO" to if (p.vinti > 0) "${p.vintiKo * 100 / p.vinti}%" else "—",
        AthleteScoring.titlesLabel(p.sport) to "${p.titoli}",
    )

/** Football, Rugby, Hockey: le statistiche salvate più i valori derivati delle classifiche. */
private fun teamStatTiles(p: Player): List<Pair<String, String>> {
    val general = AthleteScoring.generalMetric(p.sport)
    val derived = when (p.sport) {
        Sport.FOOTBALL -> listOf(general.label to AthleteScoring.formatValue(p, general))
        Sport.HOCKEY -> listOf(
            general.labelFor(p.sport) to AthleteScoring.formatValue(p, general),
            "Punti (gol + assist)" to "${AthleteScoring.puntiTotali(p)}",
            "Partite totali" to "${AthleteScoring.partiteTotali(p)}",
        )
        else -> listOf(
            general.label to AthleteScoring.formatValue(p, general),
            "Punti totali" to "${AthleteScoring.puntiTotali(p)}",
            "Presenze totali" to "${AthleteScoring.partiteTotali(p)}",
        )
    }
    val ruolo = listOfNotNull(p.ruolo.takeIf { it.isNotBlank() }?.let { "Ruolo" to it })
    return ruolo + derived + com.scouttable.app.data.athlete.teamStatFields(p.sport).map { it.label to "${it.get(p)}" }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ScoutGreen)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
