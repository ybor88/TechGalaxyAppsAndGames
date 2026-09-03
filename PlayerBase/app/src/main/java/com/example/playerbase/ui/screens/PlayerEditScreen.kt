package com.example.playerbase.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playerbase.data.Gender
import com.example.playerbase.data.PlayerRole
import com.example.playerbase.ui.components.PlayerPortrait
import com.example.playerbase.ui.theme.accentColor
import com.example.playerbase.ui.theme.headerBrush
import com.example.playerbase.viewmodel.PlayerViewModel
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerEditScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onCustomizeAvatar: () -> Unit
) {
    val draft by viewModel.draftPlayer.collectAsState()
    val isNew by viewModel.draftIsNew.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.ITALY) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            Box(modifier = Modifier.background(draft.sport.headerBrush())) {
                TopAppBar(
                    title = { Text("${draft.sport.emoji} " + if (isNew) "Nuovo giocatore" else "Modifica giocatore") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                        }
                    },
                    actions = {
                        if (!isNew) {
                            IconButton(onClick = {
                                viewModel.deletePlayer(draft.id)
                                onBack()
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Elimina")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
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
            PlayerPortrait(
                playerId = draft.id,
                sport = draft.sport,
                initials = draft.surname.ifBlank { draft.name },
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onCustomizeAvatar,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = draft.sport.accentColor())
            ) {
                Icon(Icons.Filled.Shield, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logo squadra")
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = draft.name,
                onValueChange = { value -> viewModel.updateDraft { it.copy(name = value) } },
                label = { Text("Nome") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = draft.surname,
                onValueChange = { value -> viewModel.updateDraft { it.copy(surname = value) } },
                label = { Text("Cognome") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text("Sesso", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Gender.entries.forEach { g ->
                    FilterChip(
                        selected = draft.gender == g,
                        onClick = { viewModel.updateDraft { it.copy(gender = g) } },
                        label = { Text(g.label) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Text("Ruolo", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth()) {
                PlayerRole.optionsFor(draft.sport).forEach { r ->
                    FilterChip(
                        selected = draft.role == r,
                        onClick = { viewModel.updateDraft { it.copy(role = r) } },
                        label = { Text(r.label) },
                        modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            OptionalNumberField(
                label = "Anno di nascita",
                value = draft.birthYear,
                onValueChange = { value -> viewModel.updateDraft { it.copy(birthYear = value) } }
            )
            Spacer(modifier = Modifier.height(12.dp))

            OptionalNumberField(
                label = "Altezza (cm)",
                value = draft.heightCm,
                onValueChange = { value -> viewModel.updateDraft { it.copy(heightCm = value) } }
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = draft.maxCareer,
                onValueChange = { value -> viewModel.updateDraft { it.copy(maxCareer = value) } },
                label = { Text("Max Carriera") },
                supportingText = { Text("Testo libero: es. traguardo o record raggiunto") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = draft.maxTeam,
                onValueChange = { value -> viewModel.updateDraft { it.copy(maxTeam = value) } },
                label = { Text("Max Team") },
                supportingText = { Text("Nome della squadra: usato anche per cercare il logo squadra") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    val query = listOfNotNull(
                        draft.fullName.takeIf { it.isNotBlank() },
                        draft.maxTeam.takeIf { it.isNotBlank() },
                        "highlights"
                    ).joinToString(" ")
                    val url = "https://www.google.com/search?tbm=vid&q=" +
                        URLEncoder.encode(query, "UTF-8")
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = draft.sport.accentColor()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Visiona video su Google")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = draft.viewed,
                        onCheckedChange = { value -> viewModel.updateDraft { it.copy(viewed = value) } }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Visionato", fontWeight = FontWeight.Bold)
                        Text(
                            "Segna il giocatore come visionato dal vivo o in video",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Giocatore ritirato", fontWeight = FontWeight.Bold)
                        Text(
                            "Se ritirato non compare tra gli avvisi di scouting scaduto",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                    Switch(
                        checked = draft.retired,
                        onCheckedChange = { value -> viewModel.updateDraft { it.copy(retired = value) } }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isNew) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Ultimo scouting", fontWeight = FontWeight.Bold)
                        Text(
                            "${dateFormat.format(Date(draft.scoutingTimestamp))} · aggiornato ora alla visualizzazione",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = {
                    viewModel.saveDraft()
                    onBack()
                },
                enabled = draft.name.isNotBlank() && draft.surname.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = draft.sport.accentColor(), contentColor = Color.White)
            ) {
                Text("Salva", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Campo numerico senza valore precompilato: il campo resta vuoto finché l'utente non lo imposta. */
@Composable
private fun OptionalNumberField(label: String, value: Int?, onValueChange: (Int?) -> Unit) {
    OutlinedTextField(
        value = value?.toString() ?: "",
        onValueChange = { text ->
            if (text.isBlank()) onValueChange(null) else text.toIntOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}
