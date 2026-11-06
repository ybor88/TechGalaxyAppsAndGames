package com.matchmind.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.matchmind.app.dominio.Generatore
import com.matchmind.app.dominio.Sport

// "Prova a guadagnare": bollettino speciale con puntata e vincita scelte dall'utente. © Roberto Di Flumeri

/**
 * Chiede quanto puntare (5–20 €), quanto si vuole prendere (50–500 €) e lo sport; MatchMind sceglie le partite
 * della settimana, ognuna con almeno il 60% di probabilità o, se non bastano, scendendo fino al 30%.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoObiettivo(vm: MatchMindViewModel, onChiudi: () -> Unit) {
    var puntataTesto by remember { mutableStateOf("10") }
    var vincitaTesto by remember { mutableStateOf("50") }
    var sport by remember { mutableStateOf(Sport.CALCIO) }
    val puntata = leggiImporto(puntataTesto)?.takeIf { it in Generatore.PUNTATA_OBIETTIVO_MIN..Generatore.PUNTATA_OBIETTIVO_MAX }
    val vincita = leggiImporto(vincitaTesto)?.takeIf { it in Generatore.VINCITA_OBIETTIVO_MIN..Generatore.VINCITA_OBIETTIVO_MAX }

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text("Prova a guadagnare") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Bollettino speciale della settimana: scegli quanto punti e quanto vuoi prendere. Le partite le sceglie " +
                    "MatchMind: prima solo quelle con almeno il 60% di probabilità, poi, se non bastano, scende fino al 30%.",
                    style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 20).forEach { v -> AssistChip(onClick = { puntataTesto = v.toString() }, label = { Text("$v €") }) }
                }
                CampoImporto(puntataTesto, { puntataTesto = it }, "Quanto punti (da 5 a 20 €)")
                if (puntataTesto.isNotBlank() && puntata == null)
                    Text("La puntata va da 5 a 20 €.", style = MaterialTheme.typography.bodySmall, color = Colori.Rosso)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(50, 100, 200, 500).forEach { v -> AssistChip(onClick = { vincitaTesto = v.toString() }, label = { Text("$v €") }) }
                }
                CampoImporto(vincitaTesto, { vincitaTesto = it }, "Quanto vuoi prendere (da 50 a 500 €)")
                if (vincitaTesto.isNotBlank() && vincita == null)
                    Text("La vincita va da 50 a 500 €.", style = MaterialTheme.typography.bodySmall, color = Colori.Rosso)
                if (puntata != null && vincita != null)
                    Text("Serve una quota totale di ${(vincita / puntata).quota()}.", style = MaterialTheme.typography.bodySmall, color = Colori.Verde)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Sport.entries.forEachIndexed { i, s ->
                        val colore = if (s == Sport.CALCIO) Colori.Verde else Colori.Arancio
                        SegmentedButton(selected = sport == s, onClick = { sport = s }, shape = SegmentedButtonDefaults.itemShape(i, Sport.entries.size),
                            colors = SegmentedButtonDefaults.colors(activeContainerColor = colore.copy(alpha = 0.2f), activeContentColor = colore)) {
                            Text(s.etichetta)
                        }
                    }
                }
                Text("Più alta la vincita rispetto alla puntata, più partite servono e più è difficile vincere il bollettino: " +
                    "la probabilità reale te la mostra l'anteprima. Solo credito virtuale.",
                    style = MaterialTheme.typography.bodySmall, color = Colori.Arancio)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (puntata != null && vincita != null) { vm.provaAGuadagnare(sport, puntata, vincita); onChiudi() } },
                enabled = puntata != null && vincita != null,
            ) { Text("Crea bollettino", color = Colori.Verde) }
        },
        dismissButton = { TextButton(onClick = onChiudi) { Text("Annulla") } },
        containerColor = Colori.Superficie,
        tonalElevation = 0.dp,
    )
}
