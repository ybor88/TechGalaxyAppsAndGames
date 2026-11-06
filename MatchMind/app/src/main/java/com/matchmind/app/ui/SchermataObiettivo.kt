package com.matchmind.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.matchmind.app.dati.Repository
import com.matchmind.app.dominio.Generatore
import com.matchmind.app.dominio.Sport
import kotlin.math.roundToInt

// "Prova a guadagnare": vincita finale scelta dall'utente, puntata e combinazione della settimana. © Roberto Di Flumeri

/**
 * Chiede la vincita finale voluta (massimo 500 €), lo sport e la probabilità minima (50–60%), poi cerca sulle
 * partite della settimana con quale puntata (5–20 €) e quali partite ci si arriva.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoObiettivo(vm: MatchMindViewModel, onChiudi: () -> Unit) {
    var vincitaTesto by remember { mutableStateOf("20") }
    var sport by remember { mutableStateOf(Sport.CALCIO) }
    var sicurezza by remember { mutableFloatStateOf(Generatore.SICUREZZA_OBIETTIVO_MIN.toFloat()) }
    val minima = Generatore.PUNTATE_OBIETTIVO.min()
    val vincita = leggiImporto(vincitaTesto)?.takeIf { it <= Generatore.VINCITA_OBIETTIVO_MAX && it > minima }

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text("Prova a guadagnare") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Scegli quanto vuoi vincere: MatchMind cerca tra le partite della settimana la combinazione di " +
                    "pronostici sicuri che ci arriva e ti dice quanto puntare (da 5 a 20 €). Il numero di partite lo sceglie il metodo.",
                    style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 20, 30, 50).forEach { v -> AssistChip(onClick = { vincitaTesto = v.toString() }, label = { Text("$v €") }) }
                }
                CampoImporto(vincitaTesto, { vincitaTesto = it }, "Vincita finale (massimo 500 €)")
                if (vincitaTesto.isNotBlank() && vincita == null)
                    Text("La vincita va da più di ${minima.euro()} a 500 €.", style = MaterialTheme.typography.bodySmall, color = Colori.Rosso)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Sport.entries.forEachIndexed { i, s ->
                        val colore = if (s == Sport.CALCIO) Colori.Verde else Colori.Arancio
                        SegmentedButton(selected = sport == s, onClick = { sport = s }, shape = SegmentedButtonDefaults.itemShape(i, Sport.entries.size),
                            colors = SegmentedButtonDefaults.colors(activeContainerColor = colore.copy(alpha = 0.2f), activeContentColor = colore)) {
                            Text(s.etichetta)
                        }
                    }
                }
                Text("Probabilità di vincerla: almeno ${(sicurezza * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = sicurezza, onValueChange = { sicurezza = (it * 20).roundToInt() / 20f },
                    valueRange = Generatore.SICUREZZA_OBIETTIVO_MIN.toFloat()..Generatore.SICUREZZA_OBIETTIVO_MAX.toFloat(), steps = 1,
                    colors = SliderDefaults.colors(thumbColor = Colori.Verde, activeTrackColor = Colori.Verde),
                )
                Text("Con almeno il 50% di probabilità la quota totale resta intorno a 2 o meno: con 20 € la vincita possibile è " +
                    "circa 35–40 €. Solo credito virtuale; nessun metodo garantisce la vincita.",
                    style = MaterialTheme.typography.bodySmall, color = Colori.Arancio)
            }
        },
        confirmButton = {
            TextButton(onClick = { vincita?.let { vm.provaAGuadagnare(sport, it, sicurezza.toDouble()); onChiudi() } }, enabled = vincita != null) {
                Text("Cerca combinazioni", color = Colori.Verde)
            }
        },
        dismissButton = { TextButton(onClick = onChiudi) { Text("Annulla") } },
        containerColor = Colori.Superficie,
        tonalElevation = 0.dp,
    )
}

/** Le combinazioni trovate, una per puntata: toccandone una si apre l'anteprima da giocare. */
@Composable
fun DialogoOpzioniObiettivo(vm: MatchMindViewModel, esito: Repository.EsitoObiettivo) {
    AlertDialog(
        onDismissRequest = vm::chiudiObiettivo,
        title = { Text("Vincere ${esito.vincita.euro()}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ecco quanto puntare. Con una puntata più alta servono meno partite e la bolletta è più probabile. " +
                    "Tocca quella che vuoi giocare.",
                    style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
                esito.opzioni.forEach { proposta ->
                    val s = proposta.schedina
                    val puntata = proposta.puntataSuggerita ?: return@forEach
                    Scheda(Modifier.clip(RoundedCornerShape(20.dp)).clickable { vm.scegliObiettivo(proposta) }, bordo = Colori.Verde.copy(alpha = 0.5f)) {
                        Text("Punta ${puntata.euro()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${s.pronostici.size} ${if (s.pronostici.size == 1) "partita" else "partite"} · quota ${s.quotaTotale.quota()} · " +
                            "fino al ${s.pronostici.maxOf { it.evento.inizioMillis }.dataOra()}",
                            style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
                        RigaValori(
                            Triple("Vincita", (puntata * s.quotaTotale).euro(), Colori.Testo),
                            Triple("Guadagno", (puntata * s.quotaTotale - puntata).euroConSegno(), Colori.Verde),
                            Triple("Prob.", s.probabilita.percentualeFine(), Colori.Verde),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = vm::chiudiObiettivo) { Text("Chiudi") } },
        containerColor = Colori.Superficie,
        tonalElevation = 0.dp,
    )
}
