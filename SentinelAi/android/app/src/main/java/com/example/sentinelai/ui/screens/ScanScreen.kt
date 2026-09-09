package com.example.sentinelai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sentinelai.ui.components.ScreenHeader
import com.example.sentinelai.ui.components.verdictColorFor
import com.example.sentinelai.ui.theme.SentinelBlue
import com.example.sentinelai.ui.theme.SentinelDanger
import com.example.sentinelai.ui.theme.SentinelPanel
import com.example.sentinelai.ui.theme.SentinelPanelBorder
import com.example.sentinelai.ui.theme.SentinelText
import com.example.sentinelai.ui.theme.SentinelTextDim
import com.example.sentinelai.viewmodel.SentinelViewModel

@Composable
fun ScanScreen(viewModel: SentinelViewModel) {
    val scanState by viewModel.scanState.collectAsState()
    var pickedTreeUri by remember { mutableStateOf<Uri?>(null) }
    var quarantinedPaths by remember { mutableStateOf(setOf<String>()) }

    val pickFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            pickedTreeUri = uri
            viewModel.scanTree(uri)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader("Scansione", "Rilevamento AI Avanzato: firme e euristiche di rischio")
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { pickFolderLauncher.launch(null) }) {
                    Text("Scegli cartella")
                }
                if (scanState.isScanning) {
                    CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp), color = SentinelBlue)
                }
            }
        }

        item {
            Text(scanState.statusText, color = SentinelTextDim, fontSize = 12.sp)
        }

        if (scanState.results.isEmpty() && !scanState.isScanning) {
            item {
                Text(
                    "Nessuna minaccia rilevata finora. Scegli una cartella da analizzare.",
                    color = SentinelTextDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        items(scanState.results) { result ->
            Card(
                colors = CardDefaults.cardColors(containerColor = SentinelPanel),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(result.path, color = SentinelText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            viewModel.verdictLabel(result.verdict),
                            color = verdictColorFor(result.verdict),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("${result.riskScore}/100", color = SentinelTextDim, fontSize = 13.sp)
                    }
                    val alreadyQuarantined = result.path in quarantinedPaths
                    Button(
                        onClick = {
                            pickedTreeUri?.let { viewModel.quarantineScanResult(result, it) }
                            quarantinedPaths = quarantinedPaths + result.path
                        },
                        enabled = !alreadyQuarantined,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SentinelDanger,
                            disabledContainerColor = SentinelPanelBorder
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(if (alreadyQuarantined) "In quarantena" else "Metti in quarantena")
                    }
                }
            }
        }
    }
}
