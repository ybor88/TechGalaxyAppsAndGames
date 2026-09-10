package com.scouttable.app.ui.sporthome

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import com.scouttable.app.data.Sport
import com.scouttable.app.ui.bestclub.BestClubScreen
import com.scouttable.app.ui.drive.DriveBackupDialog
import com.scouttable.app.ui.generate.GenerateListScreen
import com.scouttable.app.ui.list.PlayerListScreen
import com.scouttable.app.ui.review.ReviewScreen
import com.scouttable.app.ui.update.UpdateListScreen

private enum class ScoutTab(val label: String) {
    LISTA("Lista"),
    GENERA("Genera"),
    AGGIORNA("Aggiorna"),
    CLUB("Miglior club"),
    REVISIONE("Revisione"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportHomeScreen(sport: Sport, onSwitchSport: () -> Unit) {
    var tab by remember(sport) { mutableStateOf(ScoutTab.LISTA) }
    var showDriveDialog by remember { mutableStateOf(false) }

    if (showDriveDialog) {
        DriveBackupDialog(onDismiss = { showDriveDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ScoutTable · ${sport.label}") },
                navigationIcon = {
                    IconButton(onClick = onSwitchSport) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cambia sport")
                    }
                },
                actions = {
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
            NavigationBar {
                NavigationBarItem(
                    selected = tab == ScoutTab.LISTA,
                    onClick = { tab = ScoutTab.LISTA },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(ScoutTab.LISTA.label) },
                )
                NavigationBarItem(
                    selected = tab == ScoutTab.GENERA,
                    onClick = { tab = ScoutTab.GENERA },
                    icon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                    label = { Text(ScoutTab.GENERA.label) },
                )
                NavigationBarItem(
                    selected = tab == ScoutTab.AGGIORNA,
                    onClick = { tab = ScoutTab.AGGIORNA },
                    icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                    label = { Text(ScoutTab.AGGIORNA.label) },
                )
                NavigationBarItem(
                    selected = tab == ScoutTab.CLUB,
                    onClick = { tab = ScoutTab.CLUB },
                    icon = { Icon(Icons.Default.EmojiEvents, contentDescription = null) },
                    label = { Text(ScoutTab.CLUB.label) },
                )
                NavigationBarItem(
                    selected = tab == ScoutTab.REVISIONE,
                    onClick = { tab = ScoutTab.REVISIONE },
                    icon = { Icon(Icons.Default.FactCheck, contentDescription = null) },
                    label = { Text(ScoutTab.REVISIONE.label) },
                )
            }
        },
    ) { padding ->
        when (tab) {
            ScoutTab.LISTA -> PlayerListScreen(sport = sport, padding = padding)
            ScoutTab.GENERA -> GenerateListScreen(sport = sport, padding = padding)
            ScoutTab.AGGIORNA -> UpdateListScreen(sport = sport, padding = padding)
            ScoutTab.CLUB -> BestClubScreen(sport = sport, padding = padding)
            ScoutTab.REVISIONE -> ReviewScreen(sport = sport, padding = padding)
        }
    }
}
