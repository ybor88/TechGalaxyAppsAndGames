// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.simulation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.nbafiba.GalaxyTitle
import com.scouttable.app.data.nbafiba.GalaxyTitleRepository
import com.scouttable.app.data.nbafiba.SimCompetition
import com.scouttable.app.data.nbafiba.SimMvp
import com.scouttable.app.data.simulation.GalaxyEntry
import com.scouttable.app.data.simulation.GalaxyGame
import com.scouttable.app.data.simulation.GalaxyResult
import com.scouttable.app.data.simulation.GalaxyTournament
import com.scouttable.app.data.simulation.SimTeam
import com.scouttable.app.data.simulation.TeamSources
import com.scouttable.app.ui.common.LoadingBar
import com.scouttable.app.ui.common.PlayerAvatar
import com.scouttable.app.ui.theme.ScoutGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Gold = Color(0xFFC9A44C)

/** Id di un'immagine in res/drawable per nome (senza estensione), 0 se non c'è. */
@Composable
private fun drawableId(name: String): Int {
    val context = LocalContext.current
    return remember(name) { context.resources.getIdentifier(name, "drawable", context.packageName) }
}

/**
 * Logo di una competizione: l'immagine res/drawable/[SimCompetition.logoName] se è stata aggiunta,
 * altrimenti un trofeo dorato (così la competizione funziona anche prima di avere il logo).
 */
@Composable
fun CompetitionLogo(competition: SimCompetition, size: androidx.compose.ui.unit.Dp) {
    val id = drawableId(competition.logoName)
    if (id != 0) {
        Image(painterResource(id), contentDescription = competition.label, modifier = Modifier.size(size).clip(CircleShape))
    } else {
        Box(modifier = Modifier.size(size).clip(CircleShape).background(Color(0xFF1B2440)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.EmojiEvents, contentDescription = competition.label, tint = Gold, modifier = Modifier.size(size * 0.6f))
        }
    }
}

/** Coppa del vincitore: res/drawable/topgalaxy_cup se aggiunta, altrimenti un trofeo dorato. */
@Composable
private fun TrophyCup(size: androidx.compose.ui.unit.Dp) {
    val id = drawableId("topgalaxy_cup")
    if (id != 0) Image(painterResource(id), contentDescription = "Coppa", modifier = Modifier.size(size))
    else Icon(Icons.Default.EmojiEvents, contentDescription = "Coppa", tint = Gold, modifier = Modifier.size(size))
}

/**
 * Torneo a eliminazione (NBA G League Champions: 4 squadre; TOP Galaxy Champions: 20): un link
 * squadra per ogni casella, con la lega scritta sopra; "Carica squadre" legge le statistiche,
 * "Gioca il torneo" simula tutto il tabellone (vedi [GalaxyTournament]) e lo mostra come un
 * tabellone playoff fino alla finale, con coppa, vincitore e MVP. Ogni torneo concluso entra
 * nell'albo d'oro della competizione, con la classifica di chi ha più titoli.
 */
@Composable
fun TournamentSection(competition: SimCompetition, leagues: List<String>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(competition) { GalaxyTitleRepository(context, competition) }
    var urls by rememberSaveable(competition) { mutableStateOf(List(leagues.size) { "" }) }
    var teams by remember(competition) { mutableStateOf<Map<Int, SimTeam>>(emptyMap()) }
    var loading by remember(competition) { mutableStateOf(false) }
    var loadProgress by remember(competition) { mutableFloatStateOf(0f) }
    var loadLabel by remember(competition) { mutableStateOf("") }
    var error by remember(competition) { mutableStateOf<String?>(null) }
    var result by remember(competition) { mutableStateOf<GalaxyResult?>(null) }
    var titles by remember(competition) { mutableStateOf<List<GalaxyTitle>>(emptyList()) }
    LaunchedEffect(competition) { titles = repository.load() }

    fun load() {
        val toLoad = urls.withIndex().filter { it.value.isNotBlank() }
        val invalid = toLoad.filter { !TeamSources.isBasketUrl(it.value.trim()) }
        if (invalid.isNotEmpty()) {
            error = "Link non validi (servono link squadra di Proballers o Basketball-Reference): " +
                invalid.joinToString(", ") { leagues[it.index] }
            return
        }
        if (toLoad.size < 2) {
            error = "Servono almeno due squadre."
            return
        }
        loading = true
        error = null
        result = null
        scope.launch {
            val loaded = mutableMapOf<Int, SimTeam>()
            val failed = mutableListOf<String>()
            toLoad.forEachIndexed { i, (index, url) ->
                loadLabel = "Squadra ${i + 1} di ${toLoad.size}: ${leagues[index]}"
                loadProgress = i.toFloat() / toLoad.size
                val team = runCatching { TeamSources.fetch(url.trim()) }.getOrNull()
                if (team != null && team.players.isNotEmpty()) loaded[index] = team else failed += leagues[index]
            }
            loadProgress = 1f
            teams = loaded
            loading = false
            if (failed.isNotEmpty()) error = "Non lette: ${failed.joinToString(", ")}. Il torneo si gioca con le squadre lette."
        }
    }

    fun play() {
        val entries = teams.entries.sortedBy { it.key }.map { (index, team) -> GalaxyEntry(index + 1, leagues[index], team) }
        if (entries.size < 2) return
        scope.launch {
            val r = withContext(Dispatchers.Default) { runCatching { GalaxyTournament.play(entries, System.nanoTime()) }.getOrNull() }
            if (r == null) {
                error = "Simulazione non riuscita."
                return@launch
            }
            result = r
            val final = r.final
            val championScore = if (final.winner === final.home) final.result.homeScore else final.result.awayScore
            val runnerScore = if (final.winner === final.home) final.result.awayScore else final.result.homeScore
            titles = repository.add(
                GalaxyTitle(
                    id = "", playedAt = 0,
                    champion = r.champion.team.name, championLogo = r.champion.team.logoUrl, championLabel = r.champion.league,
                    runnerUp = r.runnerUp.team.name, runnerUpLogo = r.runnerUp.team.logoUrl,
                    finalChampionScore = championScore, finalRunnerUpScore = runnerScore,
                    teams = entries.size, mvp = r.mvp,
                )
            )
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            CompetitionLogo(competition, 56.dp)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(competition.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${leagues.size} squadre, una per lega: incolla il link squadra (Proballers o Basketball-Reference) " +
                        "sotto ogni etichetta. Torneo a eliminazione diretta fino alla finale.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        leagues.forEachIndexed { i, league ->
            Text(
                "${i + 1}. $league",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp),
            )
            OutlinedTextField(
                value = urls[i],
                onValueChange = { v -> urls = urls.toMutableList().also { it[i] = v } },
                placeholder = { Text("Link squadra $league", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                trailingIcon = teams[i]?.let { team -> { PlayerAvatar(name = team.name, logoPath = team.logoUrl, size = 28.dp) } },
                supportingText = teams[i]?.let { team ->
                    {
                        Column {
                            Text("${team.name} · ${team.players.size} giocatori")
                            team.seasonNote?.let { Text(it, color = Color(0xFFFFB300)) }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            enabled = !loading && urls.count { it.isNotBlank() } >= 2,
            onClick = { load() },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text("Carica squadre (${urls.count { it.isNotBlank() }})") }
        if (loading) LoadingBar(label = loadLabel, progress = loadProgress, modifier = Modifier.padding(top = 12.dp))
        error?.let { Text(it, color = Color(0xFFE05C5C), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        if (!loading && teams.size >= 2) {
            Button(onClick = { play() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (result == null) "Gioca il torneo (${teams.size} squadre)" else "Rigioca il torneo")
            }
        }

        result?.let { r ->
            ChampionCard(competition, r)
            Text("Tabellone", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            Bracket(r)
        }

        if (titles.isNotEmpty()) {
            HallOfFameCard(competition, titles, onDelete = { t -> scope.launch { titles = repository.delete(t.id) } })
        }
    }
}

/** Vincitore del torneo: coppa, logo della competizione, squadra campione, finale e MVP. */
@Composable
private fun ChampionCard(competition: SimCompetition, r: GalaxyResult) {
    val final = r.final
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, Gold),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompetitionLogo(competition, 48.dp)
                Spacer(modifier = Modifier.width(12.dp))
                TrophyCup(96.dp)
            }
            Text("Campione", style = MaterialTheme.typography.labelMedium, color = Gold, modifier = Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatar(name = r.champion.team.name, logoPath = r.champion.team.logoUrl, size = 36.dp)
                Text(
                    r.champion.team.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(r.champion.league, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Finale: ${final.home.team.name} ${final.result.homeScore} - ${final.result.awayScore} ${final.away.team.name}" +
                    if (final.result.overtimes > 0) " (supplementari)" else "",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            r.mvp?.let { MvpLine(it, label = "MVP del torneo") }
        }
    }
}

@Composable
fun MvpLine(mvp: SimMvp, label: String = "MVP", unit: String = "pt") {
    Text(
        "$label: ${mvp.name} (${mvp.team}) · ${mvp.points} $unit",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = Gold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** Tabellone playoff: una colonna per turno, da sinistra (Play-in) a destra (Finale e coppa). */
@Composable
private fun Bracket(r: GalaxyResult) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        r.rounds.forEach { round ->
            Column(
                modifier = Modifier.width(190.dp).padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(round.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                round.games.forEach { BracketGame(it) }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(140.dp)) {
            TrophyCup(72.dp)
            Text(r.champion.team.name, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun BracketGame(game: GalaxyGame) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFF3A3A3A)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            BracketTeam(game.home, game.result.homeScore, game.winner === game.home)
            BracketTeam(game.away, game.result.awayScore, game.winner === game.away)
        }
    }
}

@Composable
private fun BracketTeam(entry: GalaxyEntry, score: Int, winner: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text("${entry.seed}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(18.dp))
        PlayerAvatar(name = entry.team.name, logoPath = entry.team.logoUrl, size = 20.dp)
        Text(
            entry.team.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (winner) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
        Text("$score", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (winner) ScoutGreen else MaterialTheme.colorScheme.onSurface)
    }
}

/** Albo d'oro: chi ha più titoli in alto, poi ogni torneo con finale e MVP, eliminabile col cestino. */
@Composable
private fun HallOfFameCard(competition: SimCompetition, titles: List<GalaxyTitle>, onDelete: (GalaxyTitle) -> Unit) {
    val dateFormat = remember { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.ITALIAN) }
    val leaders = remember(titles) {
        titles.groupBy { it.champion }.map { (name, list) -> Triple(name, list.first().championLogo, list.size) }
            .sortedWith(compareByDescending<Triple<String, String?, Int>> { it.third }.thenBy { it.first })
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Gold),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompetitionLogo(competition, 48.dp)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text("Albo d'oro · ${competition.label}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("${titles.size} ${if (titles.size == 1) "torneo giocato" else "tornei giocati"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Più titoli", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(top = 10.dp))
            leaders.forEachIndexed { i, (name, logo, count) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("${i + 1}°", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(28.dp))
                    PlayerAvatar(name = name, logoPath = logo, size = 24.dp)
                    Text(name, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$count", fontWeight = FontWeight.Bold, color = ScoutGreen)
                    TrophyCup(18.dp)
                }
            }
            titles.forEach { t ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PlayerAvatar(name = t.champion, logoPath = t.championLogo, size = 24.dp)
                            Text(t.champion, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            "Finale ${t.finalChampionScore}-${t.finalRunnerUpScore} contro ${t.runnerUp} · ${t.teams} squadre",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        t.mvp?.let { MvpLine(it) }
                        Text(dateFormat.format(java.util.Date(t.playedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onDelete(t) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Elimina torneo", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
