// Copyright (c) Roberto Di Flumeri
package com.soniqai.app.ui.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.soniqai.app.audio.theory.Genre
import com.soniqai.app.audio.theory.MusicKey
import com.soniqai.app.audio.theory.MusicMode
import com.soniqai.app.audio.theory.Tonalita
import com.soniqai.app.data.rememberSongRepository
import com.soniqai.app.ui.common.InlineRadioChoice
import com.soniqai.app.ui.common.LabeledDropdown
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun formatDuration(totalSec: Int): String {
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%d:%02d".format(minutes, seconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSongScreen(onSongCreated: (String) -> Unit, onBack: () -> Unit) {
    val repository = rememberSongRepository()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var titolo by remember { mutableStateOf("") }
    var genere by remember { mutableStateOf(Genre.POP) }
    var nota by remember { mutableStateOf(MusicKey.DO) }
    var modo by remember { mutableStateOf(MusicMode.MAGGIORE) }
    var durataSec by remember { mutableStateOf(60f) }
    var testoStrofe by remember { mutableStateOf("") }
    var ritornello by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    val importTxt = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
        }.getOrNull()?.let { testoStrofe = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuovo brano") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Indietro") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = titolo,
                onValueChange = { titolo = it },
                label = { Text("Titolo") },
                modifier = Modifier.fillMaxWidth(),
            )

            LabeledDropdown(
                label = "Genere",
                options = Genre.entries,
                selected = genere,
                optionLabel = { it.label },
                onSelected = { genere = it },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Tono", style = MaterialTheme.typography.titleSmall)
            LabeledDropdown(
                label = "Nota",
                options = MusicKey.entries,
                selected = nota,
                optionLabel = { it.label },
                onSelected = { nota = it },
                modifier = Modifier.fillMaxWidth(),
            )
            InlineRadioChoice(
                options = MusicMode.entries,
                selected = modo,
                optionLabel = { it.label },
                onSelected = { modo = it },
            )

            Text("Durata: ${formatDuration(durataSec.roundToInt())} min", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = durataSec,
                onValueChange = { durataSec = it },
                valueRange = 15f..360f,
                steps = 22, // scatti da 15 secondi, da 0:15 a 6:00
            )

            OutlinedTextField(
                value = testoStrofe,
                onValueChange = { testoStrofe = it },
                label = { Text("Testo (strofe)") },
                minLines = 4,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { importTxt.launch("text/plain") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Importa testo da file .txt")
            }

            OutlinedTextField(
                value = ritornello,
                onValueChange = { ritornello = it },
                label = { Text("Ritornello") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )
            Text(
                "Il ritornello scelto qui verrà ripetuto nelle sezioni \"ritornello\" dell'arrangiamento, con una base più energica.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    if (isCreating) return@Button
                    isCreating = true
                    scope.launch {
                        val song = repository.createSong(
                            titolo = titolo,
                            genere = genere,
                            tono = Tonalita(nota, modo),
                            durataSec = durataSec.roundToInt(),
                            testoStrofe = testoStrofe,
                            ritornello = ritornello,
                        )
                        repository.generateBeat(song)
                        isCreating = false
                        onSongCreated(song.id)
                    }
                },
                enabled = !isCreating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                }
                Text(if (isCreating) "Generazione in corso..." else "Crea e genera base musicale")
            }
        }
    }
}
