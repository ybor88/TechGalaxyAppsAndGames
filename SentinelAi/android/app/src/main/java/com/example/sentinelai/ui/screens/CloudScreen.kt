package com.example.sentinelai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sentinelai.core.Signatures
import com.example.sentinelai.ui.components.ScreenHeader
import com.example.sentinelai.ui.components.SectionTitle
import com.example.sentinelai.ui.components.StatCard
import com.example.sentinelai.ui.theme.SentinelOk
import com.example.sentinelai.ui.theme.SentinelTeal
import com.example.sentinelai.ui.theme.SentinelText
import com.example.sentinelai.ui.theme.SentinelTextDim
import com.example.sentinelai.viewmodel.SentinelViewModel

@Composable
fun CloudScreen(viewModel: SentinelViewModel) {
    val cloudState by viewModel.cloudState.collectAsState()
    var keyInput by remember(cloudState.apiKey) { mutableStateOf(cloudState.apiKey) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader("Protezione Cloud e Offline", "Il database delle firme locali è sempre attivo. La verifica cloud è opzionale.")
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Firme offline", Signatures.count().toString(), Modifier.weight(1f))
                StatCard("Versione DB", Signatures.VERSION, Modifier.weight(1f))
            }
        }

        item {
            Text(
                "La scansione offline confronta l'hash SHA-256 di ogni file con il database locale delle firme note, senza mai richiedere una connessione.",
                color = SentinelTextDim,
                fontSize = 12.sp
            )
        }

        item { SectionTitle("Verifica cloud (VirusTotal)") }
        item {
            Text(
                "Inserisci la tua chiave API personale VirusTotal per verificare l'hash di un file sospetto. Senza chiave, SentinelAI resta esclusivamente offline.",
                color = SentinelTextDim,
                fontSize = 12.sp
            )
        }

        item {
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("Chiave API VirusTotal") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SentinelText,
                    unfocusedTextColor = SentinelText,
                    focusedBorderColor = SentinelTeal
                )
            )
        }
        item {
            Button(onClick = { viewModel.saveApiKey(keyInput) }) { Text("Salva") }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.toggleable(
                    value = cloudState.cloudEnabled,
                    onValueChange = { viewModel.setCloudEnabled(it) }
                )
            ) {
                Checkbox(
                    checked = cloudState.cloudEnabled,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = SentinelTeal)
                )
                Text("Abilita verifica cloud per i file sospetti", color = SentinelText, fontSize = 13.sp)
            }
        }

        item {
            val statusText = if (cloudState.apiKey.isNotBlank() && cloudState.cloudEnabled)
                "☁ Cloud: configurato e attivo" else "○ Cloud: non configurato — modalità offline"
            val color = if (cloudState.apiKey.isNotBlank() && cloudState.cloudEnabled) SentinelOk else SentinelTextDim
            Text(statusText, color = color, fontSize = 13.sp)
        }

        item {
            Button(onClick = {
                viewModel.testCloudLookup("275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f")
            }) { Text("Verifica hash EICAR nel cloud") }
        }
        item {
            Text(cloudState.testResult, color = SentinelTextDim, fontSize = 12.sp)
        }
    }
}
