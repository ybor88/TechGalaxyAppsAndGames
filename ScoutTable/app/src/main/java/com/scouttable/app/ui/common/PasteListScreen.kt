package com.scouttable.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Sport
import com.scouttable.app.data.importexport.PlayerImportRow
import com.scouttable.app.data.lookup.PlayerLookupService
import kotlinx.coroutines.launch

/**
 * Schermata base per "Genera nuova lista" e "Aggiorna nuova lista": l'utente incolla una lista
 * di nomi (uno per riga), l'app cerca ogni giocatore su internet (TheSportsDB) e recupera anno,
 * carriera migliore, stato, nazione e stemma del club. Nessun file da preparare.
 */
@Composable
fun PasteListScreen(
    sport: Sport,
    padding: PaddingValues,
    title: String,
    description: String,
    buttonLabel: String,
    onFound: suspend (List<PlayerImportRow>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Un giocatore per riga (nome e cognome)") },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(180.dp),
        )

        Button(
            enabled = !busy && text.isNotBlank(),
            onClick = {
                val names = text.lines().map { it.trim() }.filter { it.isNotBlank() }
                busy = true
                statusMessage = null
                scope.launch {
                    val found = mutableListOf<PlayerImportRow>()
                    val notFound = mutableListOf<String>()
                    names.forEachIndexed { index, name ->
                        progress = "Ricerca ${index + 1}/${names.size}: $name"
                        when (val result = PlayerLookupService.lookup(name, sport)) {
                            is PlayerLookupService.LookupResult.Found -> found.add(result.row)
                            is PlayerLookupService.LookupResult.NotFound -> notFound.add(name)
                            is PlayerLookupService.LookupResult.Error -> notFound.add(name)
                        }
                    }
                    onFound(found)
                    busy = false
                    progress = ""
                    statusMessage = buildString {
                        append("Trovati e salvati ${found.size} giocatori su ${names.size}.")
                        if (notFound.isNotEmpty()) {
                            append("\nNon trovati (nome non riconosciuto o sport diverso): ")
                            append(notFound.joinToString(", "))
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text(buttonLabel)
        }

        if (busy) {
            Column(modifier = Modifier.padding(top = 24.dp)) {
                CircularProgressIndicator()
                Text(progress, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        statusMessage?.let {
            Text(it, modifier = Modifier.padding(top = 24.dp), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
