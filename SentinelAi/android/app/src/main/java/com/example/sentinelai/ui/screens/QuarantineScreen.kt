package com.example.sentinelai.ui.screens

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.sentinelai.ui.theme.SentinelDanger
import com.example.sentinelai.ui.theme.SentinelPanel
import com.example.sentinelai.ui.theme.SentinelText
import com.example.sentinelai.ui.theme.SentinelTextDim
import com.example.sentinelai.viewmodel.SentinelViewModel

@Composable
fun QuarantineScreen(viewModel: SentinelViewModel) {
    val items by viewModel.quarantineItems.collectAsState()
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) { viewModel.refreshQuarantine() }

    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Conferma eliminazione") },
            text = { Text("Eliminare definitivamente questo file? L'operazione non è reversibile.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteQuarantineItem(pendingDeleteId!!)
                        pendingDeleteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SentinelDanger)
                ) { Text("Elimina") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDeleteId = null }) { Text("Annulla") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader("Quarantena", "File isolati e neutralizzati: puoi ripristinarli o eliminarli")
        }

        if (items.isEmpty()) {
            item {
                Text("Nessun elemento in quarantena. Il sistema è pulito.", color = SentinelTextDim, fontSize = 13.sp)
            }
        }

        items(items) { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = SentinelPanel),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        item.originalPath.substringAfterLast('/'),
                        color = SentinelText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(item.threatName, color = SentinelTextDim, fontSize = 12.sp)
                    Text("Rischio: ${item.riskScore}/100 — ${item.quarantinedAt}", color = SentinelTextDim, fontSize = 11.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.restoreQuarantineItem(item.id) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Ripristina") }
                        Button(
                            onClick = { pendingDeleteId = item.id },
                            colors = ButtonDefaults.buttonColors(containerColor = SentinelDanger),
                            modifier = Modifier.weight(1f)
                        ) { Text("Elimina") }
                    }
                }
            }
        }
    }
}
