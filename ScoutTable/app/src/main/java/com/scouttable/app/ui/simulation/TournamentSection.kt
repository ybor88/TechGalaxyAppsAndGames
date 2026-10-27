// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.simulation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsBasketball
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
import com.scouttable.app.data.simulation.PodiumPoints
import com.scouttable.app.data.simulation.SimTeam
import com.scouttable.app.data.Sport
import com.scouttable.app.data.simulation.SavedTeam
import com.scouttable.app.data.simulation.toSimTeam
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
    if (competition.logoName == "single_matches") {
        // Partite singole: pallone da basket da una parte e da calcio dall'altra, al posto della coppa.
        Row(
            modifier = Modifier.size(size).clip(CircleShape).background(Color(0xFF1B2440)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.SportsBasketball, contentDescription = null, tint = Color(0xFFF57C00), modifier = Modifier.size(size * 0.42f))
            Icon(Icons.Default.SportsSoccer, contentDescription = competition.label, tint = Color.White, modifier = Modifier.size(size * 0.42f))
        }
    } else if (id != 0) {
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
    // Una squadra salvata (inserita a mano, vedi ManualTeams) per ogni casella.
    var picked by remember(competition) { mutableStateOf<Map<Int, SavedTeam>>(emptyMap()) }
    val teams = remember(picked) { picked.mapValues { (_, t) -> t.toSimTeam(Sport.BASKET) }.filterValues { it.teamValue != null } }
    var compYear by rememberSaveable(competition) { mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()) }
    var error by remember(competition) { mutableStateOf<String?>(null) }
    var result by remember(competition) { mutableStateOf<GalaxyResult?>(null) }
    var titles by remember(competition) { mutableStateOf<List<GalaxyTitle>>(emptyList()) }
    LaunchedEffect(competition) { titles = repository.load() }

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
                    teams = entries.size, mvp = r.mvp, year = compYear.toIntOrNull(),
                    third = r.third?.team?.name, thirdLogo = r.third?.team?.logoUrl,
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
                    "${leagues.size} squadre, una per lega: scegli per ogni casella una delle tue squadre salvate (10 giocatori " +
                        "con l'Eff). Torneo a eliminazione diretta fino alla finale.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            value = compYear,
            onValueChange = { v -> compYear = v.filter(Char::isDigit).take(4) },
            label = { Text("Anno della competizione") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        leagues.forEachIndexed { i, league ->
            TeamPicker(
                sport = Sport.BASKET,
                label = "${i + 1}. $league",
                selected = picked[i],
                onSelected = { t -> picked = if (t == null) picked - i else picked + (i to t); result = null },
            )
        }
        error?.let { Text(it, color = Color(0xFFE05C5C), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        Button(
            enabled = teams.size >= 2,
            onClick = { play() },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                when {
                    teams.size < 2 -> "Scegli almeno 2 squadre"
                    result == null -> "Gioca il torneo (${teams.size} squadre)"
                    else -> "Rigioca il torneo"
                }
            )
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
            Text("Podio", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            TournamentPodium(
                listOfNotNull(r.champion, r.runnerUp, r.third).map { PodiumTeam(it.team.name, it.team.logoUrl) }
            )
        }
    }
}

/** Squadra sul podio di un torneo (nome e stemma). */
private data class PodiumTeam(val name: String, val logo: String?)

private val Silver = Color(0xFFC7CDD6)
private val Bronze = Color(0xFFCD7F32)

/**
 * Podio del torneo come nella classifica (2° a sinistra, 1° al centro più alto, 3° a destra),
 * con i punti guadagnati ([PodiumPoints]): [teams] in ordine 1°, 2°, 3° (il 3° può mancare).
 */
@Composable
private fun TournamentPodium(teams: List<PodiumTeam>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(1 to 92.dp, 0 to 124.dp, 2 to 68.dp).forEach { (index, height) ->
            val team = teams.getOrNull(index)
            if (team == null) {
                Spacer(modifier = Modifier.weight(1f))
                return@forEach
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                PlayerAvatar(name = team.name, logoPath = team.logo, size = 44.dp)
                Text(
                    team.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text("+${PodiumPoints[index]} pt", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = ScoutGreen)
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .height(height)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(listOf(Gold, Silver, Bronze)[index]),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        "${index + 1}",
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3A2E00),
                    )
                }
            }
        }
    }
}

/** Podio salvato di un torneo dell'albo, in ordine 1°, 2°, 3°. */
private fun GalaxyTitle.podium(): List<PodiumTeam> =
    listOfNotNull(PodiumTeam(champion, championLogo), PodiumTeam(runnerUp, runnerUpLogo), third?.let { PodiumTeam(it, thirdLogo) })

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

/** Riga della classifica punti di una card: punti totali dai podi e titoli vinti. */
private data class PointsRow(val name: String, val logo: String?, val points: Int, val titles: Int)

/**
 * Albo d'oro: in alto la classifica punti della card, sommando i podi di tutti i tornei giocati
 * (1° 20, 2° 10, 3° 5, vedi [PodiumPoints]); sotto lo storico, ogni torneo con finale e MVP: al
 * tocco mostra il suo podio; eliminabile col cestino (i punti si ricalcolano).
 */
@Composable
private fun HallOfFameCard(competition: SimCompetition, titles: List<GalaxyTitle>, onDelete: (GalaxyTitle) -> Unit) {
    val dateFormat = remember { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.ITALIAN) }
    val standings = remember(titles) {
        val rows = linkedMapOf<String, PointsRow>()
        titles.forEach { t ->
            t.podium().forEachIndexed { i, team ->
                val old = rows[team.name] ?: PointsRow(team.name, team.logo, 0, 0)
                rows[team.name] = old.copy(
                    logo = old.logo ?: team.logo,
                    points = old.points + PodiumPoints[i],
                    titles = old.titles + if (i == 0) 1 else 0,
                )
            }
        }
        rows.values.sortedWith(compareByDescending<PointsRow> { it.points }.thenByDescending { it.titles }.thenBy { it.name })
    }
    var openId by rememberSaveable(competition) { mutableStateOf<String?>(null) }
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
            Text(
                "Classifica punti (1° 20 · 2° 10 · 3° 5)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Gold,
                modifier = Modifier.padding(top = 10.dp),
            )
            // Stessa posizione a pari punti (1, 1, 3...).
            var position = 0
            standings.forEachIndexed { i, row ->
                if (i == 0 || row.points != standings[i - 1].points) position = i + 1
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("$position°", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(28.dp))
                    PlayerAvatar(name = row.name, logoPath = row.logo, size = 24.dp)
                    Text(row.name, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (row.titles > 0) {
                        Text("${row.titles}", style = MaterialTheme.typography.labelSmall, color = Gold)
                        TrophyCup(16.dp)
                    }
                    Text("${row.points} pt", fontWeight = FontWeight.Bold, color = ScoutGreen, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Text(
                "Storico (tocca un torneo per vedere il podio)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Gold,
                modifier = Modifier.padding(top = 12.dp),
            )
            titles.forEach { t ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { openId = if (openId == t.id) null else t.id },
                ) {
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
                        Text(
                            (t.year?.let { "Anno $it · " } ?: "") + dateFormat.format(java.util.Date(t.playedAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onDelete(t) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Elimina torneo", tint = MaterialTheme.colorScheme.error)
                    }
                }
                if (openId == t.id) {
                    Box(modifier = Modifier.padding(top = 8.dp)) { TournamentPodium(t.podium()) }
                    if (t.third == null && t.teams > 2) {
                        Text(
                            "Torneo giocato prima del podio: 3° posto non registrato.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
