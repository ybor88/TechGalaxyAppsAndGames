// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.ranking.PlayerScoring
import com.scouttable.app.ui.theme.ScoutGreen

/**
 * Un dato cambiato tra il giocatore salvato e quello appena trovato. [keepOld] riporta in un
 * giocatore aggiornato il valore precedente di questo dato (e di quelli collegati, es. lo stemma
 * del secondo logo insieme al suo club), per accettare le modifiche una per una.
 */
data class FieldChange(
    val label: String,
    val before: String,
    val after: String,
    val keepOld: (updated: Player, old: Player) -> Player = { updated, _ -> updated },
)

/** Aggiornamento in attesa di conferma: il giocatore com'è, come diventerebbe e le differenze. */
data class PendingUpdate(val old: Player, val updated: Player, val changes: List<FieldChange>)

private class FieldSpec(
    val label: String,
    val show: Boolean,
    val get: (Player) -> String,
    val keepOld: (Player, Player) -> Player,
)

/**
 * Dati diversi tra [before] (com'è in lista) e [after] (come diventerebbe con l'aggiornamento):
 * solo i campi mostrati in app, con le etichette dello sport ("Gol" nel calcio, "Punti" nel basket).
 */
fun playerChanges(before: Player, after: Player, sport: Sport): List<FieldChange> {
    val basket = sport.isBasket
    fun num(v: Int) = if (v == 0) "—" else v.toString()
    fun txt(v: String?) = v?.trim().orEmpty().ifBlank { "—" }
    val fields = listOf(
        FieldSpec("Nome", true, { txt(it.nome) }) { u, o -> u.copy(nome = o.nome) },
        FieldSpec("Anno", true, { num(it.anno) }) { u, o -> u.copy(anno = o.anno) },
        FieldSpec("Nazione", true, { txt(it.nazione) }) { u, o -> u.copy(nazione = o.nazione) },
        FieldSpec("Ruolo", true, { txt(it.ruolo) }) { u, o -> u.copy(ruolo = o.ruolo) },
        FieldSpec("Stato", true, { txt(it.stato) }) { u, o -> u.copy(stato = o.stato) },
        FieldSpec("Carriera migliore", true, { txt(it.carrieraMigliore) }) { u, o -> u.copy(carrieraMigliore = o.carrieraMigliore) },
        FieldSpec("Competizione", true, { txt(it.competizione) }) { u, o -> u.copy(competizione = o.competizione) },
        FieldSpec("Stemma", true, { if (it.logoPath.isNullOrBlank()) "—" else "presente" }) { u, o -> u.copy(logoPath = o.logoPath) },
        FieldSpec("Presenze", true, { num(it.presenze) }) { u, o -> u.copy(presenze = o.presenze) },
        FieldSpec(if (basket) "Punti" else "Gol", true, { num(it.punteggio) }) { u, o -> u.copy(punteggio = o.punteggio) },
        FieldSpec("Assist", true, { num(it.assist) }) { u, o -> u.copy(assist = o.assist) },
        FieldSpec("Gol subiti", !basket, { num(it.golSubiti) }) { u, o -> u.copy(golSubiti = o.golSubiti) },
        FieldSpec("Rimbalzi", basket, { num(it.rimbalzi) }) { u, o -> u.copy(rimbalzi = o.rimbalzi) },
        FieldSpec("Palle recuperate", basket, { num(it.palleRecuperate) }) { u, o -> u.copy(palleRecuperate = o.palleRecuperate) },
        FieldSpec("% tiri da 2", basket, { num(it.percentualeTiriDaDue) }) { u, o ->
            u.copy(percentualeTiriDaDue = o.percentualeTiriDaDue, tiriDaDue = o.tiriDaDue)
        },
        FieldSpec("% tiri da 3", basket, { num(it.percentualeTiriDaTre) }) { u, o ->
            u.copy(percentualeTiriDaTre = o.percentualeTiriDaTre, tiriDaTre = o.tiriDaTre)
        },
        FieldSpec("Eff medio", basket, { num(it.effMedio) }) { u, o -> u.copy(effMedio = o.effMedio) },
        FieldSpec("Minuti carriera", true, { num(it.minutiCarriera) }) { u, o -> u.copy(minutiCarriera = o.minutiCarriera) },
        FieldSpec("College", basket, { txt(it.college) }) { u, o -> u.copy(college = o.college) },
        FieldSpec("Presenze Nazionale", true, { num(it.presenzeNazionale) }) { u, o -> u.copy(presenzeNazionale = o.presenzeNazionale) },
        FieldSpec(if (basket) "Punti Nazionale" else "Gol Nazionale", true, { num(it.punteggioNazionale) }) { u, o ->
            u.copy(punteggioNazionale = o.punteggioNazionale)
        },
        FieldSpec("Gol subiti Nazionale", !basket, { num(it.golSubitiNazionale) }) { u, o -> u.copy(golSubitiNazionale = o.golSubitiNazionale) },
        FieldSpec("Secondo logo", true, { txt(it.secondLogoClub) }) { u, o ->
            u.copy(
                secondLogoClub = o.secondLogoClub,
                secondLogoPath = o.secondLogoPath,
                secondLogoPresenze = o.secondLogoPresenze,
                secondLogoGol = o.secondLogoGol,
                secondLogoEff = o.secondLogoEff,
            )
        },
        FieldSpec("Periodo secondo logo", true, { txt(it.secondLogoPeriodo) }) { u, o -> u.copy(secondLogoPeriodo = o.secondLogoPeriodo) },
        FieldSpec(StarLabel, true, { if (it.star) "sì" else "no" }) { u, o -> u.copy(star = o.star) },
    )
    return fields.filter { it.show }.mapNotNull { f ->
        val b = f.get(before)
        val a = f.get(after)
        if (a != b) FieldChange(f.label, b, a, f.keepOld) else null
    }
}

private const val StarLabel = "Preferito ⭐"

/**
 * Il giocatore da salvare accettando solo le modifiche in [accepted] (indici di [changes]): si parte
 * da [updated] e per ogni dato non accettato si rimette il valore di [old]. La stella ⭐, salvo che
 * sia stata rifiutata, viene ricalcolata sul risultato finale (dipende dalle statistiche tenute).
 */
fun applyAcceptedChanges(old: Player, updated: Player, changes: List<FieldChange>, accepted: Set<Int>): Player {
    var result = updated
    changes.forEachIndexed { i, change -> if (i !in accepted) result = change.keepOld(result, old) }
    val starIndex = changes.indexOfFirst { it.label == StarLabel }
    return if (starIndex == -1 || starIndex in accepted) result.copy(star = PlayerScoring.computeStar(result)) else result
}

/**
 * Differenze prima/dopo della revisione di un singolo giocatore: ogni dato cambiato con il valore
 * attuale (barrato) e quello nuovo, con una casella per accettarlo o no (tutte spuntate
 * all'inizio). "Accetta" salva solo i dati spuntati, "Annulla" lascia il giocatore com'è.
 */
@Composable
fun PlayerDiffDialog(
    playerName: String,
    changes: List<FieldChange>,
    onAccept: (accepted: Set<Int>) -> Unit,
    onDismiss: () -> Unit,
    /** "Revisione" in Revisione, "Aggiorna" dall'icona della lista. */
    titlePrefix: String = "Revisione",
) {
    var accepted by remember(changes) { mutableStateOf(changes.indices.toSet()) }
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
                        "${changes.size} ${if (changes.size == 1) "dato cambiato" else "dati cambiati"}: " +
                            "togli la spunta a quelli che non vuoi aggiornare.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { accepted = changes.indices.toSet() }) { Text("Tutti") }
                        TextButton(onClick = { accepted = emptySet() }) { Text("Nessuno") }
                    }
                    ChangesTable(
                        changes,
                        accepted = accepted,
                        onToggle = { i -> accepted = if (i in accepted) accepted - i else accepted + i },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAccept(accepted) }) {
                Text(
                    when {
                        changes.isEmpty() || accepted.size == changes.size -> "Accetta aggiornamento"
                        accepted.isEmpty() -> "Nessuna modifica"
                        else -> "Accetta ${accepted.size} di ${changes.size}"
                    }
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

/** Tabella Dato / Prima (rosso, barrato) / Dopo (verde) dei dati cambiati. */
@Composable
fun ChangesTable(
    changes: List<FieldChange>,
    /** Con [onToggle] ogni riga ha una casella, spuntata se il suo indice è in [accepted]. */
    accepted: Set<Int> = emptySet(),
    onToggle: ((Int) -> Unit)? = null,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        if (onToggle != null) Spacer(modifier = Modifier.width(36.dp))
        Text("Dato", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("Prima", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("Dopo", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
    changes.forEachIndexed { i, change ->
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        val on = onToggle == null || i in accepted
        Row(
            modifier = Modifier.fillMaxWidth().then(if (onToggle != null) Modifier.clickable { onToggle(i) } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onToggle != null) {
                Checkbox(checked = i in accepted, onCheckedChange = { onToggle(i) }, modifier = Modifier.size(36.dp))
            }
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
                color = if (on) ScoutGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = if (on) null else TextDecoration.LineThrough,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val BeforeColor = Color(0xFFDC2626)
