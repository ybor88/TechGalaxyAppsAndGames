// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.sporthome

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Sport
import com.scouttable.app.ui.bestclub.BestClubScreen
import com.scouttable.app.ui.competitions.CompetitionsScreen
import com.scouttable.app.ui.drive.DriveBackupDialog
import com.scouttable.app.ui.formation.TopFormationScreen
import com.scouttable.app.ui.generate.GenerateListScreen
import com.scouttable.app.ui.list.PlayerListScreen
import com.scouttable.app.ui.nbaintl.NbaInternationalScreen
import com.scouttable.app.ui.simulation.SimulateGameScreen
import com.scouttable.app.ui.ranking.CalcioRankingTabsScreen
import com.scouttable.app.ui.ranking.RankingScreen
import com.scouttable.app.ui.review.ReviewScreen
import com.scouttable.app.ui.trend.BasketTrendScreen
import com.scouttable.app.ui.trend.CalcioTrendScreen

private enum class ScoutTab(val label: String) {
    LISTA("Lista"),
    GENERA("Genera"),
    CLUB("Miglior club"),
    REVISIONE("Revisione"),
    // Entrambi gli sport: raggruppamento per freccia di rendimento e ruolo (ArrowTrendSection);
    // solo nel basket anche i grafici per decennio ed Eff (BasketTrendScreen).
    ANDAMENTO("Andamento"),
    // Entrambi gli sport: per il basket è la classifica per Eff medio (RankingScreen), per il
    // calcio le 4 classifiche per ruolo con sotto-tab (CalcioRankingTabsScreen).
    CLASSIFICA("Classifica"),
    // Entrambi gli sport: migliore formazione per ruolo dai rating delle classifiche (calcio
    // 4-4-2, basket quintetto) + panchina e link ai ranking FIBA/FIFA delle nazionali.
    FORMAZIONE("Formazione"),
    // Entrambi gli sport, aperta dall'icona in alto e non dalla barra in basso (che con 7 voci nel
    // basket è già piena): per ogni competizione il detentore, cioè il club che l'ha vinta più
    // volte, vedi CompetitionsScreen.
    DETENTORI("Detentori"),
    // Tutte le sezioni, dall'icona in alto: due link squadra, partita simulata su campo 2D con
    // tabellone LED (vedi SimulateGameScreen).
    SIMULA("Simula partita"),
    // Solo basket (non regionale), dall'icona in alto: bilancio e albo d'oro delle partite tra
    // squadre NBA e squadre internazionali (vedi NbaInternationalScreen).
    NBA_MONDO("NBA vs mondo"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportHomeScreen(sport: Sport, onSwitchSport: () -> Unit, onOpenPlayer: (String) -> Unit) {
    // rememberSaveable: il tab scelto sopravvive anche se Android ricrea la schermata (la rotazione
    // è gestita dall'app senza ricrearla, vedi android:configChanges nel manifest).
    var tab by rememberSaveable(sport) { mutableStateOf(ScoutTab.LISTA) }
    var showDriveDialog by remember { mutableStateOf(false) }
    // In orizzontale la barra in basso diventa più bassa e senza etichette: alta 80dp su ~360dp
    // di schermo lasciava troppo poco spazio al contenuto.
    val compactBar = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (showDriveDialog) {
        DriveBackupDialog(onDismiss = { showDriveDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // Su due righe: con le icone in alto "ScoutTable · Basket regionale" non ci stava.
                title = {
                    Column {
                        Text("ScoutTable", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(sport.label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onSwitchSport) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cambia sport")
                    }
                },
                actions = {
                    TopTabIcon(ScoutTab.SIMULA, Icons.Default.SportsEsports, tab) { tab = it }
                    if (sport == Sport.BASKET) TopTabIcon(ScoutTab.NBA_MONDO, Icons.Default.Public, tab) { tab = it }
                    // Basket regionale: niente Detentori (non ci sono competizioni da seguire).
                    if (sport != Sport.BASKET_REGIONALE) TopTabIcon(ScoutTab.DETENTORI, Icons.Default.WorkspacePremium, tab) { tab = it }
                    IconButton(onClick = { showDriveDialog = true }) {
                        Icon(Icons.Default.CloudSync, contentDescription = "Backup dati")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            NavigationBar(modifier = if (compactBar) Modifier.height(56.dp) else Modifier) {
                NavigationBarItem(
                    selected = tab == ScoutTab.LISTA,
                    onClick = { tab = ScoutTab.LISTA },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = navLabel(compactBar, ScoutTab.LISTA),
                )
                NavigationBarItem(
                    selected = tab == ScoutTab.GENERA,
                    onClick = { tab = ScoutTab.GENERA },
                    icon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                    label = navLabel(compactBar, ScoutTab.GENERA),
                )
                NavigationBarItem(
                    selected = tab == ScoutTab.CLUB,
                    onClick = { tab = ScoutTab.CLUB },
                    icon = { Icon(Icons.Default.EmojiEvents, contentDescription = null) },
                    label = navLabel(compactBar, ScoutTab.CLUB),
                )
                NavigationBarItem(
                    selected = tab == ScoutTab.REVISIONE,
                    onClick = { tab = ScoutTab.REVISIONE },
                    icon = { Icon(Icons.Default.FactCheck, contentDescription = null) },
                    label = navLabel(compactBar, ScoutTab.REVISIONE),
                )
                NavigationBarItem(
                    selected = tab == ScoutTab.ANDAMENTO,
                    onClick = { tab = ScoutTab.ANDAMENTO },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                    label = navLabel(compactBar, ScoutTab.ANDAMENTO),
                )
                NavigationBarItem(
                    selected = tab == ScoutTab.CLASSIFICA,
                    onClick = { tab = ScoutTab.CLASSIFICA },
                    icon = { Icon(Icons.Default.MilitaryTech, contentDescription = null) },
                    label = navLabel(compactBar, ScoutTab.CLASSIFICA),
                )
                NavigationBarItem(
                    selected = tab == ScoutTab.FORMAZIONE,
                    onClick = { tab = ScoutTab.FORMAZIONE },
                    icon = { Icon(Icons.Default.Groups, contentDescription = null) },
                    label = navLabel(compactBar, ScoutTab.FORMAZIONE),
                )
            }
        },
    ) { padding ->
        when (tab) {
            ScoutTab.LISTA -> PlayerListScreen(sport = sport, padding = padding, onOpenPlayer = onOpenPlayer)
            ScoutTab.GENERA -> GenerateListScreen(sport = sport, padding = padding)
            ScoutTab.CLUB -> BestClubScreen(sport = sport, padding = padding)
            ScoutTab.REVISIONE -> ReviewScreen(sport = sport, padding = padding, onOpenPlayer = onOpenPlayer)
            ScoutTab.ANDAMENTO -> if (sport.isBasket) BasketTrendScreen(padding = padding, sport = sport) else CalcioTrendScreen(padding = padding)
            ScoutTab.CLASSIFICA -> if (sport.isBasket) {
                RankingScreen(padding = padding, onOpenPlayer = onOpenPlayer, sport = sport)
            } else {
                CalcioRankingTabsScreen(padding = padding, onOpenPlayer = onOpenPlayer)
            }
            ScoutTab.FORMAZIONE -> TopFormationScreen(sport = sport, padding = padding, onOpenPlayer = onOpenPlayer)
            ScoutTab.DETENTORI -> CompetitionsScreen(sport = sport, padding = padding, onOpenPlayer = onOpenPlayer)
            ScoutTab.SIMULA -> SimulateGameScreen(sport = sport, padding = padding)
            ScoutTab.NBA_MONDO -> NbaInternationalScreen(padding = padding)
        }
    }
}

/** Icona in alto che apre una sezione non presente nella barra in basso (evidenziata se aperta). */
@Composable
private fun TopTabIcon(target: ScoutTab, icon: ImageVector, current: ScoutTab, onSelect: (ScoutTab) -> Unit) {
    IconButton(onClick = { onSelect(target) }) {
        Icon(
            icon,
            contentDescription = target.label,
            tint = if (current == target) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}

/** Etichetta della voce nella barra in basso: nessuna in modalità compatta (telefono in orizzontale). */
private fun navLabel(compact: Boolean, tab: ScoutTab): (@Composable () -> Unit)? =
    if (compact) null else ({ Text(tab.label, maxLines = 1) })
