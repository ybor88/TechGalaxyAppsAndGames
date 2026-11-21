// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.athlete

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.SportsFootball
import androidx.compose.material.icons.filled.SportsHockey
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.SportsRugby
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Sport
import com.scouttable.app.ui.drive.DriveBackupDialog
import com.scouttable.app.ui.generate.GenerateListScreen

private enum class AthleteTab {
    LISTA, GENERA, ANDAMENTO, CLASSIFICA, GRIGLIA, DETENTORI, TEAM, TALENTI;
}

private fun AthleteTab.label(sport: Sport): String = when (this) {
    AthleteTab.LISTA -> "Lista"
    AthleteTab.GENERA -> "Genera"
    AthleteTab.ANDAMENTO -> "Andamento"
    AthleteTab.CLASSIFICA -> "Classifica"
    AthleteTab.GRIGLIA -> when (sport) {
        Sport.SUMO -> "Dohyō"
        Sport.MMA -> "Gabbia"
        else -> when {
            sport.isCombat -> "Ring"
            sport.isTeamSport -> "Campo"
            else -> "Griglia"
        }
    }
    AthleteTab.DETENTORI -> "Detentori"
    AthleteTab.TALENTI -> "Talenti"
    AthleteTab.TEAM -> when {
        sport.isMotor -> "Miglior scuderia"
        sport.isTeamSport -> "Miglior squadra"
        else -> "Miglior nazione"
    }
}

/**
 * Home degli sport da combattimento, MotoGP, Formula 1, Football americano, Rugby e Hockey: stessa
 * struttura di basket e calcio (barra in basso con le sezioni principali, icone in alto per
 * Detentori, Miglior scuderia/squadra e backup), con le schermate degli sport individuali. Genera è
 * quella comune: la ricerca legge la voce it.wikipedia.org dell'atleta. La revisione si fa dalla
 * lista (ordine "Prossima revisione" e aggiornamento del singolo atleta).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AthleteHomeScreen(sport: Sport, onSwitchSport: () -> Unit, onOpenPlayer: (String) -> Unit) {
    var tab by rememberSaveable(sport) { mutableStateOf(AthleteTab.LISTA) }
    var showDrive by remember { mutableStateOf(false) }
    val compactBar = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (showDrive) DriveBackupDialog(onDismiss = { showDrive = false })

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ScoutTable", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(sport.label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onSwitchSport) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cambia sport") }
                },
                actions = {
                    // Talenti (tutte le card): globo 3D e giovani talenti del paese scelto, vedi TalentScreen.
                    TopIcon(sport, AthleteTab.TALENTI, Icons.Default.TravelExplore, tab) { tab = it }
                    // "Miglior scuderia/squadra" per MotoGP, F1 e sport di squadra: nelle lotte non serve.
                    if (sport.isMotor || sport.isTeamSport) TopIcon(sport, AthleteTab.TEAM, Icons.Default.EmojiEvents, tab) { tab = it }
                    TopIcon(sport, AthleteTab.DETENTORI, Icons.Default.WorkspacePremium, tab) { tab = it }
                    IconButton(onClick = { showDrive = true }) { Icon(Icons.Default.CloudSync, contentDescription = "Backup dati") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            NavigationBar(modifier = if (compactBar) Modifier.height(56.dp) else Modifier) {
                listOf(
                    AthleteTab.LISTA to Icons.AutoMirrored.Filled.List,
                    AthleteTab.GENERA to Icons.Default.UploadFile,
                    AthleteTab.ANDAMENTO to Icons.Default.BarChart,
                    AthleteTab.CLASSIFICA to Icons.Default.MilitaryTech,
                    AthleteTab.GRIGLIA to when (sport) {
                        Sport.FOOTBALL -> Icons.Default.SportsFootball
                        Sport.RUGBY -> Icons.Default.SportsRugby
                        Sport.HOCKEY -> Icons.Default.SportsHockey
                        else -> if (sport.isCombat) Icons.Default.SportsMma else Icons.Default.Flag
                    },
                ).forEach { (t, icon) ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(icon, contentDescription = null) },
                        label = if (compactBar) null else ({ Text(t.label(sport), maxLines = 1) }),
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            AthleteTab.LISTA -> AthleteListScreen(sport, padding, onOpenPlayer)
            AthleteTab.GENERA -> GenerateListScreen(sport = sport, padding = padding)
            AthleteTab.ANDAMENTO -> AthleteTrendScreen(sport, padding, onOpenPlayer)
            AthleteTab.CLASSIFICA -> AthleteRankingScreen(sport, padding, onOpenPlayer)
            AthleteTab.GRIGLIA -> AthleteGridScreen(sport, padding, onOpenPlayer)
            AthleteTab.DETENTORI -> AthleteHoldersScreen(sport, padding, onOpenPlayer)
            AthleteTab.TEAM -> AthleteTeamScreen(sport, padding)
            AthleteTab.TALENTI -> com.scouttable.app.ui.talent.TalentScreen(sport, padding)
        }
    }
}

@Composable
private fun TopIcon(sport: Sport, target: AthleteTab, icon: ImageVector, current: AthleteTab, onSelect: (AthleteTab) -> Unit) {
    IconButton(onClick = { onSelect(target) }) {
        Icon(
            icon,
            contentDescription = target.label(sport),
            tint = if (current == target) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}
