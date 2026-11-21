package com.matchmind.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.matchmind.app.dati.Report
import com.matchmind.app.dominio.StatoSchedina

// Report delle bollette perse, da condividere per migliorare il metodo. © Roberto Di Flumeri

/** Apre il menu di condivisione di Android (WhatsApp, email, …) con [testo]. */
fun condividi(context: Context, testo: String) {
    val invio = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "MatchMind · report bolletta persa")
        .putExtra(Intent.EXTRA_TEXT, testo)
    runCatching { context.startActivity(Intent.createChooser(invio, "Condividi il report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun copia(context: Context, testo: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Report MatchMind", testo))
}

@Composable
fun SchermataReport(vm: MatchMindViewModel) {
    val schedine by vm.schedine.collectAsState()
    val context = LocalContext.current
    val perse = schedine.filter { it.schedina.stato == StatoSchedina.PERSA.name }
        .sortedByDescending { it.schedina.chiusaIl ?: it.schedina.creataIl }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Report", style = MaterialTheme.typography.headlineSmall)
            Text("Per ogni bolletta persa: quale pronostico ha fatto perdere, il risultato, le probabilità di quote e statistica " +
                "e un'analisi automatica. Condividilo dopo ogni sconfitta per capire dove migliorare il metodo.",
                style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
        }
        if (perse.isEmpty()) {
            item {
                Scheda { Text("Nessuna bolletta persa finora.", color = Colori.Tenue) }
            }
        } else {
            if (perse.size > 1) item {
                OutlinedButton(onClick = { condividi(context, Report.tutte(perse)) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text("Condividi tutti i report (${perse.size})", color = Colori.Rosso)
                }
            }
            items(perse, key = { it.schedina.id }) { b ->
                val testo = Report.sconfitta(b) ?: return@items
                Scheda(bordo = Colori.Rosso.copy(alpha = 0.6f)) {
                    Text(testo, style = MaterialTheme.typography.bodySmall, color = Colori.Testo)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { condividi(context, testo) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Colori.Rosso, contentColor = Colori.Sfondo)) { Text("Condividi") }
                        OutlinedButton(onClick = { copia(context, testo); vm.mostra("Report copiato") }, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)) { Text("Copia", color = Colori.Testo) }
                    }
                }
            }
        }
    }
}
