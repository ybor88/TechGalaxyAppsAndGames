package com.example.sentinelai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sentinelai.ui.components.ScreenHeader
import com.example.sentinelai.ui.components.SectionTitle
import com.example.sentinelai.ui.theme.SentinelTeal
import com.example.sentinelai.ui.theme.SentinelText
import com.example.sentinelai.ui.theme.SentinelTextDim
import com.example.sentinelai.viewmodel.SentinelViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: SentinelViewModel) {
    val threshold by viewModel.settings.autoQuarantineThreshold.collectAsState(initial = 70)
    var sliderValue by remember(threshold) { mutableFloatStateOf(threshold.toFloat()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importStatus by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val json = viewModel.exportProfileJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                importStatus = "Profilo esportato correttamente."
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (text != null) {
                viewModel.importProfileJson(text) { success ->
                    importStatus = if (success) "Profilo importato correttamente." else "Importazione non riuscita."
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader("Impostazioni", "Difesa Predittiva, protezione e sincronizzazione multipiattaforma")
        }

        item { SectionTitle("Difesa Predittiva") }
        item {
            Text(
                "I file con un punteggio di rischio pari o superiore alla soglia scelta vengono messi in quarantena automaticamente dalla protezione in tempo reale.",
                color = SentinelTextDim,
                fontSize = 12.sp
            )
        }
        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { viewModel.setAutoQuarantineThreshold(sliderValue.toInt()) },
                    valueRange = 30f..100f,
                    colors = SliderDefaults.colors(thumbColor = SentinelTeal, activeTrackColor = SentinelTeal),
                    modifier = Modifier.weight(1f)
                )
                Text(sliderValue.toInt().toString(), color = SentinelText, fontSize = 14.sp)
            }
        }

        item { SectionTitle("📱 Sicurezza Multipiattaforma") }
        item {
            Text(
                "Esporta il tuo profilo di protezione per riutilizzarlo su un'altra installazione di SentinelAI (desktop o mobile).",
                color = SentinelTextDim,
                fontSize = 12.sp
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("sentinelai_profile.json") }) {
                    Text("Esporta profilo")
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                    Text("Importa profilo")
                }
            }
        }
        item { Text(importStatus, color = SentinelTextDim, fontSize = 12.sp) }

        item { SectionTitle("Informazioni") }
        item {
            Text(
                "SentinelAI Mobile — Intelligent. Proactive. Protected.\n© Roberto Di Flumeri",
                color = SentinelTextDim,
                fontSize = 12.sp
            )
        }
    }
}
