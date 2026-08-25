package com.example.playerbase.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playerbase.data.Player
import com.example.playerbase.data.daysSinceScouting
import com.example.playerbase.data.isScoutingExpiring
import com.example.playerbase.ui.components.PlayerPortrait
import com.example.playerbase.ui.theme.BrandColors
import com.example.playerbase.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoutingExpiringScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onPlayerClick: (String) -> Unit
) {
    val players by viewModel.players.collectAsState()
    val expiring = remember(players) {
        players.filter { it.isScoutingExpiring() }.sortedBy { it.scoutingTimestamp }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scouting in scadenza") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.navy,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        if (expiring.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Nessun giocatore in scadenza",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tutti i giocatori attivi sono stati scoutati\nnell'ultimo mese.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "${expiring.size} giocatori non ritirati non vengono scoutati da oltre un mese.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(expiring, key = { it.id }) { player ->
                    ExpiringRow(player = player, onClick = { onPlayerClick(player.id) })
                }
            }
        }
    }
}

@Composable
private fun ExpiringRow(player: Player, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerPortrait(
                playerId = player.id,
                sport = player.sport,
                initials = player.surname.ifBlank { player.name },
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${player.sport.emoji} ${player.fullName.ifBlank { "Senza nome" }}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Nato nel ${player.birthYear}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFD32F2F).copy(alpha = 0.15f)
            ) {
                Text(
                    "${player.daysSinceScouting()}gg",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFD32F2F),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
