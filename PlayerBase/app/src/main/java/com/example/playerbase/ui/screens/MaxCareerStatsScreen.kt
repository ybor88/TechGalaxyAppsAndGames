package com.example.playerbase.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.playerbase.data.Sport
import com.example.playerbase.ui.components.SimpleStatsList
import com.example.playerbase.ui.theme.BrandColors
import com.example.playerbase.ui.theme.accentColor
import com.example.playerbase.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaxCareerStatsScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val players by viewModel.players.collectAsState()
    var selectedSport by remember { mutableStateOf(Sport.BASKET) }

    val careerCounts = remember(players, selectedSport) {
        viewModel.maxCareerCounts(selectedSport)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistiche Max Career") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            PrimaryTabRow(selectedTabIndex = if (selectedSport == Sport.BASKET) 0 else 1) {
                Tab(
                    selected = selectedSport == Sport.BASKET,
                    onClick = { selectedSport = Sport.BASKET },
                    text = { Text("${Sport.BASKET.emoji} Basket") }
                )
                Tab(
                    selected = selectedSport == Sport.CALCIO,
                    onClick = { selectedSport = Sport.CALCIO },
                    text = { Text("${Sport.CALCIO.emoji} Calcio") }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text("Max Career (giocatori per livello raggiunto)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            SimpleStatsList(
                items = careerCounts,
                accentColor = selectedSport.accentColor(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
