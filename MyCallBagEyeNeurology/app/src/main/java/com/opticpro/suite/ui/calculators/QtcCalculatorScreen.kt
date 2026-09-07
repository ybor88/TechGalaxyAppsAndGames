package com.opticpro.suite.ui.calculators

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QtcCalculatorScreen(onBack: () -> Unit) {
    var qtMs by remember { mutableStateOf("") }
    var rrSec by remember { mutableStateOf("") } // intervallo RR in secondi (60/FC)

    val qt = qtMs.toDoubleOrNull()
    val rr = rrSec.toDoubleOrNull()
    val qtc = if (qt != null && rr != null && rr > 0) qt / sqrt(rr) else null

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("QTc (Bazett)") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(qtMs, { qtMs = it }, label = { Text("QT misurato (ms)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(rrSec, { rrSec = it }, label = { Text("Intervallo RR (secondi, 60/FC)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            if (qtc != null) {
                Text("QTc: %.0f ms".format(qtc), style = MaterialTheme.typography.headlineSmall)
                if (qtc > 450) Text("Prolungato: valutare rischio aritmico.", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
