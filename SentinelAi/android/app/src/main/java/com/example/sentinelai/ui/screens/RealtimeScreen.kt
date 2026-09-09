package com.example.sentinelai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sentinelai.core.RealtimeMonitorService
import com.example.sentinelai.ui.components.ScreenHeader
import com.example.sentinelai.ui.components.SectionTitle
import com.example.sentinelai.ui.theme.SentinelBlue
import com.example.sentinelai.ui.theme.SentinelTeal
import com.example.sentinelai.ui.theme.SentinelText
import com.example.sentinelai.ui.theme.SentinelTextDim
import com.example.sentinelai.viewmodel.SentinelViewModel
import java.io.File

@Composable
fun RealtimeScreen(viewModel: SentinelViewModel) {
    val dashboard by viewModel.dashboard.collectAsState()
    val feed by viewModel.realtimeFeed.collectAsState()
    val context = LocalContext.current

    val addFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val dir = RealtimeMonitorService.protectedDir(context)
            val name = "importato_${System.currentTimeMillis()}"
            val dest = File(dir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    LaunchedEffect(Unit) { viewModel.refreshDashboard() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader("Protezione in Tempo Reale", "Monitora la cartella protetta e analizza ogni nuovo file")
        }

        item {
            val onToggle: (Boolean) -> Unit = { checked ->
                if (checked) viewModel.startRealtime(70) else viewModel.stopRealtime()
            }
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.toggleable(
                    value = dashboard.realtimeActive,
                    onValueChange = onToggle
                )
            ) {
                Checkbox(
                    checked = dashboard.realtimeActive,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = SentinelTeal)
                )
                Text("Attiva protezione in tempo reale", color = SentinelText, fontSize = 14.sp)
            }
        }

        item { SectionTitle("Cartella protetta") }
        item {
            Text(viewModel.protectedDirPath(), color = SentinelTextDim, fontSize = 12.sp)
        }
        item {
            Text(
                "Android non permette a un'app normale di monitorare in tempo reale cartelle di sistema arbitrarie. " +
                    "SentinelAI protegge questa cartella privata dell'app: aggiungi qui i file da tenere sotto controllo.",
                color = SentinelTextDim,
                fontSize = 12.sp
            )
        }
        item {
            OutlinedButton(onClick = { addFileLauncher.launch(arrayOf("*/*")) }) {
                Text("Aggiungi file alla cartella protetta")
            }
        }

        item { SectionTitle("Attività recente") }

        if (feed.isEmpty()) {
            item {
                Text("Nessuna attività ancora.", color = SentinelTextDim, fontSize = 12.sp)
            }
        }
        items(feed) { line ->
            Text(line, color = SentinelText, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}
