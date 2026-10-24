// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.review

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.ui.theme.ScoutGreen

/** Un dato cambiato tra il giocatore salvato e quello appena trovato. */
data class FieldChange(val label: String, val before: String, val after: String)

/**
 * Dati diversi tra [before] (com'è in lista) e [after] (come diventerebbe con l'aggiornamento):
 * solo i campi mostrati in app, con le etichette dello sport ("Gol" nel calcio, "Punti" nel basket).
 */
fun playerChanges(before: Player, after: Player, sport: Sport): List<FieldChange> {
    val basket = sport.isBasket
    fun num(v: Int) = if (v == 0) "—" else v.toString()
    fun txt(v: String?) = v?.trim().orEmpty().ifBlank { "—" }
    val fields: List<Triple<String, (Player) -> String, Boolean>> = listOf(
        Triple("Nome", { p: Player -> txt(p.nome) }, true),
        Triple("Anno", { p: Player -> num(p.anno) }, true),
        Triple("Nazione", { p: Player -> txt(p.nazione) }, true),
        Triple("Ruolo", { p: Player -> txt(p.ruolo) }, true),
        Triple("Stato", { p: Player -> txt(p.stato) }, true),
        Triple("Carriera migliore", { p: Player -> txt(p.carrieraMigliore) }, true),
        Triple("Competizione", { p: Player -> txt(p.competizione) }, true),
        Triple("Stemma", { p: Player -> if (p.logoPath.isNullOrBlank()) "—" else "presente" }, true),
        Triple("Presenze", { p: Player -> num(p.presenze) }, true),
        Triple(if (basket) "Punti" else "Gol", { p: Player -> num(p.punteggio) }, true),
        Triple("Assist", { p: Player -> num(p.assist) }, true),
        Triple("Gol subiti", { p: Player -> num(p.golSubiti) }, !basket),
        Triple("Rimbalzi", { p: Player -> num(p.rimbalzi) }, basket),
        Triple("Palle recuperate", { p: Player -> num(p.palleRecuperate) }, basket),
        Triple("% tiri da 2", { p: Player -> num(p.percentualeTiriDaDue) }, basket),
        Triple("% tiri da 3", { p: Player -> num(p.percentualeTiriDaTre) }, basket),
        Triple("Eff medio", { p: Player -> num(p.effMedio) }, basket),
        Triple("Minuti carriera", { p: Player -> num(p.minutiCarriera) }, true),
        Triple("College", { p: Player -> txt(p.college) }, basket),
        Triple("Presenze Nazionale", { p: Player -> num(p.presenzeNazionale) }, true),
        Triple(if (basket) "Punti Nazionale" else "Gol Nazionale", { p: Player -> num(p.punteggioNazionale) }, true),
        Triple("Gol subiti Nazionale", { p: Player -> num(p.golSubitiNazionale) }, !basket),
        Triple("Secondo logo", { p: Player -> txt(p.secondLogoClub) }, true),
        Triple("Periodo secondo logo", { p: Player -> txt(p.secondLogoPeriodo) }, true),
        Triple("Preferito ⭐", { p: Player -> if (p.star) "sì" else "no" }, true),
    )
    return fields.filter { it.third }.mapNotNull { (label, get, _) ->
        val b = get(before)
        val a = get(after)
        if (a != b) FieldChange(label, b, a) else null
    }
}

/**
 * Differenze prima/dopo della revisione di un singolo giocatore: ogni dato cambiato con il valore
 * attuale (barrato) e quello nuovo. "Accetta" salva l'aggiornamento, "Annulla" lascia il giocatore
 * com'è.
 */
@Composable
fun PlayerDiffDialog(
    playerName: String,
    changes: List<FieldChange>,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    /** "Revisione" in Revisione, "Aggiorna" dall'icona della lista. */
    titlePrefix: String = "Revisione",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$titlePrefix: $playerName") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (changes.isEmpty()) {
                    Text(
                        "Nessun dato cambiato rispetto a quelli salvati. Accettando, il giocatore viene segnato " +
                            "come revisionato.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        "${changes.size} ${if (changes.size == 1) "dato cambiato" else "dati cambiati"}:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Dato", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("Prima", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("Dopo", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }
                    changes.forEach { change ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(change.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(
                                change.before,
                                style = MaterialTheme.typography.bodySmall,
                                color = BeforeColor,
                                textDecoration = TextDecoration.LineThrough,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                change.after,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = ScoutGreen,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Accetta aggiornamento") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

private val BeforeColor = Color(0xFFDC2626)
