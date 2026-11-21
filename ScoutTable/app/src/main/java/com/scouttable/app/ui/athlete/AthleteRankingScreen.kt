// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.athlete

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.athlete.AthleteMetric
import com.scouttable.app.data.athlete.AthleteScoring
import com.scouttable.app.data.athlete.TeamRole
import com.scouttable.app.data.athlete.TeamRoles
import com.scouttable.app.data.athlete.isPercent
import com.scouttable.app.data.athlete.labelFor
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.ranking.PodiumRankingScreen

/**
 * Classifiche degli sport individuali con podio oro/argento/bronzo. MotoGP/F1: generale per punti
 * ottenuti, poi punti per gara, podi, mondiali, vittorie, pole, giri veloci. Lotta: generale per
 * % vittorie (vinti / incontri), poi vinti, KO, titoli, incontri; maschile e femminile separate.
 */
@Composable
fun AthleteRankingScreen(sport: Sport, padding: PaddingValues, onOpenPlayer: (String) -> Unit) {
    val repository = rememberPlayerRepository()
    val loaded by repository.observePlayers(sport).collectAsState<List<Player>, List<Player>?>(initial = null)
    val players = loaded.orEmpty()
    val metrics = AthleteMetric.forSport(sport)
    var metricName by rememberSaveable(sport) { mutableStateOf(metrics.first().name) }
    val metric = AthleteMetric.valueOf(metricName)
    var female by rememberSaveable(sport) { mutableStateOf(false) }
    // Football/Rugby/Hockey: classifica per gruppo di ruolo (null = tutti), come il calcio.
    var roleName by rememberSaveable(sport) { mutableStateOf<String?>(null) }
    val role = roleName?.let { TeamRole.valueOf(it) }
    val group = remember(players, female, role) {
        AthleteScoring.sameGroup(players, sport, female).filter { role == null || TeamRoles.roleOf(it) == role }
    }
    val ranked = remember(group, metric) { AthleteScoring.ranking(group, metric) }

    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
        if (sport.isCombat) GenderSelector(female, { female = it }, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
        if (sport.isTeamSport) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                FilterChip(selected = role == null, onClick = { roleName = null }, label = { Text("Tutti i ruoli") }, modifier = Modifier.padding(end = 8.dp))
                TeamRole.forSport(sport).forEach { r ->
                    FilterChip(selected = role == r, onClick = { roleName = r.name }, label = { Text(r.label) }, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
            metrics.forEach { m ->
                FilterChip(
                    selected = m == metric,
                    onClick = { metricName = m.name },
                    label = { Text(if (m == metrics.first()) "${m.labelFor(sport)} (generale)" else m.labelFor(sport)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        PodiumRankingScreen(
            modifier = Modifier.weight(1f),
            title = when {
                sport.isCombat -> "${metric.labelFor(sport)} · ${if (female) "femminile" else "maschile"}"
                role != null -> "${role.label} · ${metric.labelFor(sport).lowercase()}"
                else -> "Classifica ${metric.labelFor(sport).lowercase()}"
            },
            subtitle = if (metric == AthleteMetric.PUNTEGGIO_RUOLO) roleSubtitle(sport, role) else subtitleFor(sport, metric),
            emptyMessage = "Nessun ${AthleteScoring.athleteWord(sport)} con statistiche. Usa \"Genera\" con i link Wikipedia.",
            ranked = ranked,
            loading = loaded == null,
            onOpenPlayer = onOpenPlayer,
            primaryLineOf = { "${AthleteScoring.formatValue(it, metric)} ${if (metric.isPercent) "" else metric.short}".trim() },
            bonusLineOf = { p ->
                val distacco = AthleteScoring.distacco(p, players)
                val arrow = when (AthleteScoring.trend(p, players)) { 1 -> "↑"; -1 -> "↓"; else -> "–" }
                if (sport.isCombat) "${AthleteScoring.rankingDetail(p)} · $arrow"
                else "${AthleteScoring.rankingDetail(p)} · $arrow ${AthleteScoring.formatDistacco(sport, distacco)}"
            },
        )
    }
}

/** Formule del punteggio per ruolo: quella del ruolo scelto, o tutte con "Tutti i ruoli". */
private fun roleSubtitle(sport: Sport, role: TeamRole?): String =
    if (role != null) {
        "Punteggio ${role.label.lowercase()}: ${role.formula}. La freccia confronta con la media dello stesso ruolo."
    } else {
        "Ogni giocatore ha un punteggio con la formula del suo ruolo (come il calcio): " +
            TeamRole.forSport(sport).joinToString(" · ") { "${it.label}: ${it.formula}" } +
            ". Scegli un ruolo per confrontare giocatori con la stessa formula."
    }

private fun subtitleFor(sport: Sport, metric: AthleteMetric): String = when (metric) {
    AthleteMetric.PERC_VITTORIE_GARE -> "Classifica generale: gare vinte diviso gare disputate (${if (sport == Sport.FORMULA1) "Formula 1" else "Motomondiale"}). " +
        "Sotto ogni pilota i podi e il distacco dalla media (↑ sopra, ↓ sotto)."
    AthleteMetric.PUNTI -> "Punti ottenuti in carriera (${if (sport == Sport.FORMULA1) "Formula 1" else "Motomondiale"}). " +
        "Sotto ogni pilota i podi e il distacco del suo rapporto punti/gara dalla media (↑ sopra, ↓ sotto). " +
        "Attenzione: i sistemi di punteggio sono cambiati nel tempo."
    AthleteMetric.PUNTI_GARA -> "Punti ottenuti diviso gare disputate: il valore usato per la freccia ↑/↓."
    AthleteMetric.PERCENTUALE -> "Classifica generale: incontri vinti diviso incontri disputati. Sotto, vinti (V), persi (P) e incontri; " +
        "la freccia confronta la % con la media del genere."
    AthleteMetric.PUNTI_PARTITA -> if (sport == Sport.HOCKEY) {
        "Gol + assist diviso partite giocate, club e Nazionale (Wikipedia). " +
            "Sotto, punti e partite e il distacco dalla media (↑ sopra, ↓ sotto)."
    } else {
        "Punti segnati diviso presenze, club e Nazionale (Wikipedia). " +
            "Favorisce chi calcia (mediani d'apertura); sotto, punti e presenze e il distacco dalla media."
    }
    AthleteMetric.TD_INT_STAGIONE -> "Touchdown + intercetti diviso stagioni giocate " +
        "(touchdown e intercetti dall'infobox di en.wikipedia). Sotto, TD, intercetti e stagioni e il distacco dalla media (↑ sopra, ↓ sotto)."
    AthleteMetric.TOUCHDOWN, AthleteMetric.YARD, AthleteMetric.TACKLE_FB, AthleteMetric.SACK, AthleteMetric.INTERCETTI ->
        "Totali di carriera NFL dall'infobox di en.wikipedia; in classifica solo chi ha questa statistica (dipende dal ruolo)."
    else -> "Ordinati per ${metric.labelFor(sport).lowercase()}; a parità vale la classifica generale."
}
