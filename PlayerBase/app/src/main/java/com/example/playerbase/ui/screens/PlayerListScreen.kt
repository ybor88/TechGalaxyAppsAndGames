package com.example.playerbase.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playerbase.R
import com.example.playerbase.data.Player
import com.example.playerbase.data.PlayerRole
import com.example.playerbase.data.Sport
import com.example.playerbase.data.daysSinceScouting
import com.example.playerbase.data.isScoutingExpiring
import com.example.playerbase.ui.components.PlayerPortrait
import com.example.playerbase.ui.components.TeamLogoBadge
import com.example.playerbase.ui.theme.accentColor
import com.example.playerbase.ui.theme.headerBrush
import com.example.playerbase.ui.theme.leagueBadges
import com.example.playerbase.viewmodel.PlayerViewModel
import java.net.URLEncoder

private enum class PlayerSortOption(val label: String) {
    SURNAME("Cognome"),
    NAME("Nome"),
    BIRTH_YEAR("Anno di nascita"),
    HEIGHT("Altezza"),
    MAX_TEAM("Max Team")
}

private fun PlayerSortOption.comparator(): Comparator<Player> = when (this) {
    PlayerSortOption.SURNAME -> compareBy { it.surname.lowercase() }
    PlayerSortOption.NAME -> compareBy { it.name.lowercase() }
    PlayerSortOption.BIRTH_YEAR -> compareBy(nullsLast()) { it.birthYear }
    PlayerSortOption.HEIGHT -> compareBy(nullsLast()) { it.heightCm }
    PlayerSortOption.MAX_TEAM -> compareBy { it.maxTeam.lowercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerListScreen(
    viewModel: PlayerViewModel,
    sport: Sport,
    onBack: () -> Unit,
    onAddPlayer: () -> Unit,
    onPlayerClick: (String) -> Unit
) {
    val players by viewModel.players.collectAsState()
    var query by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(PlayerSortOption.SURNAME) }
    var ascending by remember { mutableStateOf(true) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val hasAnyPlayers = remember(players, sport) { players.any { it.sport == sport } }
    val sportPlayers = remember(players, sport, query, sortOption, ascending) {
        val base = players.filter { it.sport == sport }
        val filtered = if (query.isBlank()) {
            base
        } else {
            base.filter { p ->
                p.name.contains(query, ignoreCase = true) ||
                    p.surname.contains(query, ignoreCase = true) ||
                    p.maxTeam.contains(query, ignoreCase = true)
            }
        }
        val comparator = sortOption.comparator().let { if (ascending) it else it.reversed() }
        filtered.sortedWith(comparator)
    }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.background(sport.headerBrush())) {
                TopAppBar(
                    title = {
                        Column {
                            Text("${sport.emoji} ${sport.label}", fontWeight = FontWeight.ExtraBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                sport.leagueBadges().forEach { league -> LeagueBadge(league) }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPlayer, containerColor = sport.accentColor(), contentColor = Color.White) {
                Icon(Icons.Filled.Add, contentDescription = "Aggiungi giocatore")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Color.Black)
            ) {
                Image(
                    painter = painterResource(
                        if (sport == Sport.BASKET) R.drawable.basket_energy else R.drawable.calcio_energy
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Transparent, MaterialTheme.colorScheme.background)
                            )
                        )
                )
            }

            if (!hasAnyPlayers) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(sport.emoji, fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Nessun giocatore ancora",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Premi + per crearne uno",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Cerca per nome, cognome o squadra") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotBlank()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Cancella ricerca")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Ordina per",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box {
                            AssistChip(
                                onClick = { sortMenuExpanded = true },
                                label = { Text(sortOption.label) },
                                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
                            )
                            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                                PlayerSortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            sortOption = option
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = { ascending = !ascending }) {
                            Icon(
                                if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                contentDescription = if (ascending) "Crescente" else "Decrescente",
                                tint = sport.accentColor()
                            )
                        }
                    }
                }

                if (sportPlayers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Nessun risultato per \"$query\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(sportPlayers, key = { it.id }) { player ->
                            PlayerRow(
                                player = player,
                                accent = sport.accentColor(),
                                onClick = { onPlayerClick(player.id) },
                                onToggleViewed = { viewModel.toggleViewed(player.id) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(64.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeagueBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.White.copy(alpha = 0.18f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun PlayerRow(player: Player, accent: Color, onClick: () -> Unit, onToggleViewed: () -> Unit) {
    val context = LocalContext.current
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = accent.copy(alpha = 0.25f), shape = RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(56.dp)) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(width = 2.dp, color = accent, shape = CircleShape)
                ) {
                    PlayerPortrait(
                        playerId = player.id,
                        sport = player.sport,
                        initials = player.surname.ifBlank { player.name },
                        modifier = Modifier.size(56.dp)
                    )
                }
                TeamLogoBadge(
                    playerId = player.id,
                    teamKey = player.maxTeam.ifBlank { player.id },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${player.surname} ${player.name}".trim().ifBlank { "Senza nome" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (player.role != PlayerRole.NON_SPECIFICATO) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = accent.copy(alpha = 0.15f)) {
                            Text(
                                player.role.label,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                Text(
                    "Nato nel ${player.birthYear ?: "-"} · ${player.heightCm?.let { "$it cm" } ?: "- cm"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
                val extra = listOfNotNull(
                    player.maxTeam.takeIf { it.isNotBlank() },
                    player.maxCareer.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (extra.isNotBlank()) {
                    Text(
                        extra,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleViewed, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (player.viewed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = if (player.viewed) "Visionato" else "Non visionato",
                            tint = if (player.viewed) Color(0xFF388E3C) else Color(0xFF9E9E9E)
                        )
                    }
                    IconButton(
                        onClick = {
                            val query = listOfNotNull(
                                player.fullName.takeIf { it.isNotBlank() },
                                player.maxTeam.takeIf { it.isNotBlank() },
                                "highlights"
                            ).joinToString(" ")
                            val url = "https://www.google.com/search?tbm=vid&q=" +
                                URLEncoder.encode(query, "UTF-8")
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Visiona video", tint = accent)
                    }
                }
                if (player.retired) {
                    StatusChip("Ritirato", Color(0xFF757575))
                } else if (player.isScoutingExpiring()) {
                    StatusChip("Scouting scaduto (${player.daysSinceScouting()}gg)", Color(0xFFD32F2F))
                } else {
                    StatusChip("Attivo", Color(0xFF388E3C))
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
