package com.example.playerbase.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.playerbase.data.TeamLogoStore
import com.example.playerbase.ui.theme.accentColor
import com.example.playerbase.ui.theme.headerBrush
import com.example.playerbase.viewmodel.PlayerViewModel

/**
 * Logo squadra: unica personalizzazione visiva del giocatore rimasta, dopo
 * la rimozione della foto giocatore e dell'avatar disegnato.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarCreatorScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val draft by viewModel.draftPlayer.collectAsState()

    val context = LocalContext.current
    val logoStore = remember { TeamLogoStore(context) }
    var logoVersion by remember { mutableIntStateOf(0) }
    // Se "Max Team" non è compilato, il logo si aggancia comunque al giocatore
    // (chiave = id giocatore): resta sempre caricabile, senza precondizioni.
    val logoKey = draft.maxTeam.ifBlank { draft.id }
    val hasDisplayedLogo = remember(draft.id, logoKey, logoVersion) {
        logoStore.getLogoForPlayer(draft.id, logoKey) != null
    }
    val hasOwnLogo = remember(draft.id, logoVersion) { logoStore.hasPlayerAssignment(draft.id) }
    val pickLogoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            logoStore.saveLogo(logoKey, draft.id, uri)
            logoVersion++
        }
    }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.background(draft.sport.headerBrush())) {
                TopAppBar(
                    title = { Text("${draft.sport.emoji} Logo squadra") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SectionLabel("Logo squadra")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pickLogoLauncher.launch("image/*") }) {
                    Icon(Icons.Filled.Upload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (hasDisplayedLogo) "Cambia logo squadra" else "Carica logo squadra")
                }
                if (hasOwnLogo) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        logoStore.removeLogo(draft.id)
                        logoVersion++
                    }) {
                        Text("Rimuovi")
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (draft.maxTeam.isBlank()) {
                    "Compila \"Max Team\" per condividere questo logo con gli altri giocatori della stessa squadra."
                } else if (hasOwnLogo) {
                    "Questo è il logo scelto per questo giocatore. Se un compagno di squadra carica uno stemma diverso, il tuo resta invariato."
                } else {
                    "Il logo caricato viene riusato automaticamente per ogni giocatore con lo stesso Max Team che non ne ha ancora scelto uno proprio. Se nel tempo vengono caricati stemmi diversi per la stessa squadra, restano tutti visibili nelle statistiche."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )

            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = draft.sport.accentColor(), contentColor = Color.White)
            ) {
                Text("Fatto", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
}
