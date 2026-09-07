package com.opticpro.suite.ui.neurology

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class Dermatome(val level: String, val region: String)

private val dermatomes = listOf(
    Dermatome("C2", "Cuoio capelluto posteriore, occipite"),
    Dermatome("C3", "Collo laterale"),
    Dermatome("C4", "Spalla (acromion)"),
    Dermatome("C5", "Faccia laterale del braccio"),
    Dermatome("C6", "Avambraccio laterale, pollice"),
    Dermatome("C7", "Dito medio"),
    Dermatome("C8", "Mignolo, avambraccio mediale"),
    Dermatome("T1", "Faccia mediale del braccio"),
    Dermatome("T4", "Linea dei capezzoli"),
    Dermatome("T10", "Ombelico"),
    Dermatome("T12", "Regione inguinale"),
    Dermatome("L1", "Inguine, parte alta della coscia"),
    Dermatome("L2", "Coscia anteriore"),
    Dermatome("L3", "Ginocchio mediale"),
    Dermatome("L4", "Malleolo mediale"),
    Dermatome("L5", "Dorso del piede, alluce"),
    Dermatome("S1", "Malleolo laterale, mignolo del piede"),
    Dermatome("S2-S4", "Regione perianale"),
)

/** Riferimento rapido dei livelli dermatomerici per la localizzazione clinica di deficit sensitivi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DermatomeMapScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Mappa dei dermatomeri") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(dermatomes) { d ->
                ListItem(
                    headlineContent = { Text(d.level, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(d.region) }
                )
                HorizontalDivider()
            }
        }
    }
}
