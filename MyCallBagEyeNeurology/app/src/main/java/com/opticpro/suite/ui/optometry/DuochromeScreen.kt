package com.opticpro.suite.ui.optometry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuochromeScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Duochrome Test") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Chiedi al paziente su quale sfondo le lettere appaiono più nitide (rosso = miopizzare, verde = ipermetropizzare).",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    Modifier.weight(1f).fillMaxHeight().background(Color.Red),
                    contentAlignment = Alignment.Center
                ) { Text("O X V", color = Color.Black, style = MaterialTheme.typography.displaySmall) }
                Box(
                    Modifier.weight(1f).fillMaxHeight().background(Color(0xFF00A651)),
                    contentAlignment = Alignment.Center
                ) { Text("O X V", color = Color.Black, style = MaterialTheme.typography.displaySmall) }
            }
        }
    }
}
