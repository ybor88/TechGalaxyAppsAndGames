// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.athlete

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.athlete.AthleteScoring
import com.scouttable.app.data.athlete.labelFor
import com.scouttable.app.data.athlete.mondiali
import com.scouttable.app.ui.common.flagEmojiFor
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.theme.ScoutGreen

private const val NON_INDICATA = "Non indicata"

private data class TeamGroup(val name: String, val athletes: List<Player>, val titles: Int, val avgRatio: Double?)

/**
 * Equivalente di "Miglior club": MotoGP/F1 per scuderia principale, Lotta per nazione (nella lotta
 * non ci sono squadre). Ordinati per mondiali/titoli vinti dai loro atleti, poi per numero di atleti.
 */
@Composable
fun AthleteTeamScreen(sport: Sport, padding: PaddingValues) {
    val repository = rememberPlayerRepository()
    val all by repository.observePlayers(sport).collectAsState(initial = emptyList())
    // MotoGP/F1 per scuderia, sport di squadra per squadra principale, lotte per nazione.
    val byTeam = sport.isMotor || sport.isTeamSport
    var female by rememberSaveable(sport) { mutableStateOf(false) }
    val groups = remember(all, female) {
        AthleteScoring.sameGroup(all, sport, female)
            .groupBy { (if (byTeam) it.carrieraMigliore else it.nazione).ifBlank { NON_INDICATA } }
            .map { (name, list) -> TeamGroup(name, list.sortedBy { it.nome }, list.sumOf { it.mondiali }, AthleteScoring.averageRatio(list)) }
            // "Non indicata" sempre in fondo; lotte: ordine solo per numero di atleti (i titoli non si mostrano).
            .sortedWith(
                compareBy<TeamGroup> { it.name == NON_INDICATA }
                    .thenByDescending { if (byTeam) it.titles else 0 }
                    .thenByDescending { it.athletes.size }
                    .thenBy { it.name },
            )
    }
    val metric = AthleteScoring.ratioMetric(sport)

    // MotoGP/F1: scuderie senza logo (piloti importati prima che il logo venisse salvato): lo si
    // cerca su Wikipedia una volta per scuderia e si salva sui suoi piloti, così compare qui e
    // accanto al rating. Ogni scuderia si tenta una sola volta per apertura della schermata.
    val tried = remember(sport) { mutableSetOf<String>() }
    // MotoGP: piloti senza scuderia (importati prima che la si leggesse dalla voce inglese): la si
    // cerca ora, pilota per pilota, una sola volta per apertura della schermata.
    val triedRiders = remember(sport) { mutableSetOf<String>() }
    var fillingTeams by remember(sport) { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(all) {
        if (sport != Sport.MOTOGP) return@LaunchedEffect
        val missing = all.filter { it.carrieraMigliore.isBlank() && it.id !in triedRiders }
        missing.forEachIndexed { i, p ->
            triedRiders += p.id
            fillingTeams = "Recupero scuderie da Wikipedia: ${i + 1} di ${missing.size} (${p.nome})"
            com.scouttable.app.data.athlete.WikipediaAthleteStats.motoGpTeam(p.proballersUrl, p.nome)?.let { (team, logo) ->
                repository.setRiderTeam(p.id, team, logo)
            }
        }
        fillingTeams = null
    }
    androidx.compose.runtime.LaunchedEffect(groups) {
        if (!sport.isMotor) return@LaunchedEffect
        groups.filter { g ->
            g.name != NON_INDICATA && g.name !in tried && g.athletes.none { !it.secondLogoPath.isNullOrBlank() }
        }.forEach { g ->
            tried += g.name
            val logo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { com.scouttable.app.data.lookup.WikipediaClubLogo.fetchMotorTeam(g.name, sport == Sport.MOTOGP) }.getOrNull()
            }
            if (logo != null) repository.setTeamLogo(sport, g.name, logo)
        }
    }

    LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                Text(
                    when {
                        sport.isMotor -> "Miglior scuderia"
                        sport.isTeamSport -> "Miglior squadra"
                        else -> "Miglior nazione"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (sport.isTeamSport) {
                        "I giocatori in lista raggruppati per squadra principale (quella con più partite). " +
                            "🏆 = ${AthleteScoring.titlesLabel(sport).lowercase()} in totale dai suoi giocatori (ordine della lista); " +
                            "sotto, la media ${metric.labelFor(sport).lowercase()}."
                    } else if (sport.isMotor) {
                        "I piloti in lista raggruppati per scuderia principale (quella con più stagioni). " +
                            "🏆 = mondiali vinti in totale dai piloti di quella scuderia (ordine della lista); " +
                            "sotto, la media ${metric.label.lowercase()} dei suoi piloti."
                    } else {
                        "${AthleteScoring.athleteWord(sport, true).replaceFirstChar { it.uppercase() }} in lista raggruppati per nazione, " +
                            "dalla più rappresentata; a destra la media ${metric.label.lowercase()}."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GenderSelector(female, { female = it })
                fillingTeams?.let {
                    com.scouttable.app.ui.common.LoadingBar(label = it, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        if (groups.isEmpty()) {
            item { Text("Nessun atleta in lista.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        itemsIndexed(groups, key = { _, g -> g.name }) { i, g ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}°", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(40.dp))
                    // MotoGP/F1: logo della scuderia (da uno dei suoi piloti); senza logo su Wikipedia
                    // (es. Brabham) le iniziali. Lotte: pallino colorato della nazione.
                    if (byTeam) {
                        val logo = if (sport.isTeamSport) {
                            g.athletes.firstNotNullOfOrNull { a ->
                                a.logoPath?.takeIf { it.isNotBlank() && com.scouttable.app.data.lookup.WikipediaClubLogo.isLogoImage(it) }
                            }
                        } else g.athletes.firstNotNullOfOrNull { it.secondLogoPath?.takeIf(String::isNotBlank) }
                        com.scouttable.app.ui.common.PlayerAvatar(name = g.name, logoPath = logo, size = 36.dp)
                    } else {
                        Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(teamColor(g.name)))
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        val flag = if (byTeam) "" else flagEmojiFor(g.name)
                        Text("$flag ${g.name}".trim(), fontWeight = FontWeight.Bold)
                        Text(
                            "${g.athletes.size} ${AthleteScoring.athleteWord(sport, g.athletes.size != 1)}: " + g.athletes.joinToString(", ") { it.nome },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        // Lotte: solo il raggruppamento per nazione, niente coppa né numero di titoli.
                        if (byTeam) Text("${g.titles} 🏆", fontWeight = FontWeight.Bold, color = ScoutGreen)
                        Text(AthleteScoring.format(metric, g.avgRatio), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
