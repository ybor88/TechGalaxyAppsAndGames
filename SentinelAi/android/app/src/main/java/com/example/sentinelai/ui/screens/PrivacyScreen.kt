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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sentinelai.ui.components.ScreenHeader
import com.example.sentinelai.ui.components.SectionTitle
import com.example.sentinelai.ui.theme.SentinelDanger
import com.example.sentinelai.ui.theme.SentinelOk
import com.example.sentinelai.ui.theme.SentinelPanel
import com.example.sentinelai.ui.theme.SentinelText
import com.example.sentinelai.ui.theme.SentinelTextDim
import com.example.sentinelai.ui.theme.SentinelWarning
import com.example.sentinelai.viewmodel.SentinelViewModel

@Composable
fun PrivacyScreen(viewModel: SentinelViewModel) {
    val screenLock by viewModel.screenLockStatus.collectAsState()
    val playProtect by viewModel.playProtectStatus.collectAsState()
    val apps by viewModel.privacyApps.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshPrivacy() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader("Privacy e Controllo", "Stato di sicurezza del dispositivo e audit dei permessi")
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusCard("Blocco schermo", screenLock, Modifier.weight(1f))
                StatusCard("Play Protect", playProtect, Modifier.weight(1f))
            }
        }

        item { SectionTitle("App installate — permessi sensibili") }

        items(apps) { app ->
            Card(
                colors = CardDefaults.cardColors(containerColor = SentinelPanel),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(app.appName, color = SentinelText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(app.packageName, color = SentinelTextDim, fontSize = 11.sp)
                    if (app.dangerousPermissions.isNotEmpty()) {
                        Text(
                            app.dangerousPermissions.joinToString(", "),
                            color = SentinelWarning,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        Text("Nessun permesso sensibile", color = SentinelOk, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    OutlinedButton(
                        onClick = { viewModel.openAppDetails(app.packageName) },
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text("Dettagli app") }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(label: String, status: String, modifier: Modifier = Modifier) {
    val color = when (status) {
        "attivo" -> SentinelOk
        "disattivato" -> SentinelDanger
        else -> SentinelTextDim
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SentinelPanel),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, color = SentinelTextDim, fontSize = 12.sp)
            Text(status.replaceFirstChar { it.uppercase() }, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}
