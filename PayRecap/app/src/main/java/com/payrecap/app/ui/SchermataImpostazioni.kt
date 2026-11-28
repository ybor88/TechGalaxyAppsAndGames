// PayRecap — © Roberto Di Flumeri
package com.payrecap.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payrecap.app.dominio.Impostazioni
import kotlin.math.roundToInt

@Composable
fun SchermataImpostazioni(
    impostazioni: Impostazioni,
    numeroCedolini: Int,
    padding: PaddingValues,
    onCambia: (Impostazioni) -> Unit,
    onEsempi: () -> Unit,
    onEliminaTutto: () -> Unit,
) {
    var conferma by remember { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = SpaziaturaElenco,
    ) {
        item { Text("Impostazioni", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) }
        item {
            Scheda(titolo = "Avvisi", icona = Icons.Filled.NotificationsActive) {
                Text("Segnala variazioni di netto, lordo e tasse oltre il ${impostazioni.sogliaVariazione.roundToInt()}%", fontSize = 14.sp)
                Slider(
                    value = impostazioni.sogliaVariazione.toFloat(),
                    onValueChange = { onCambia(impostazioni.copy(sogliaVariazione = it.roundToInt().toDouble())) },
                    valueRange = 3f..30f,
                )
                Text("Avvisa ${impostazioni.giorniPreavvisoScadenza} giorni prima della scadenza del contratto", fontSize = 14.sp)
                Slider(
                    value = impostazioni.giorniPreavvisoScadenza.toFloat(),
                    onValueChange = { onCambia(impostazioni.copy(giorniPreavvisoScadenza = (it / 5).roundToInt() * 5)) },
                    valueRange = 15f..180f,
                )
            }
        }
        item {
            Scheda(titolo = "Privacy", icona = Icons.Filled.Lock) {
                Text(
                    "I cedolini vengono letti e salvati solo su questo telefono. PayRecap non chiede il permesso di accedere a " +
                        "Internet: nessun dato viene inviato a server esterni. Il backup automatico di Android è disattivato.",
                    fontSize = 13.sp, color = Colori.TestoSecondario, lineHeight = 18.sp,
                )
            }
        }
        item {
            Scheda(titolo = "Dati", icona = Icons.Filled.Science) {
                Text("$numeroCedolini cedolini salvati", fontSize = 14.sp)
                SpazioVerticale(8.dp)
                OutlinedButton(onClick = onEsempi, modifier = Modifier.fillMaxWidth()) { Text("Carica i cedolini di esempio") }
                OutlinedButton(
                    onClick = { conferma = true }, modifier = Modifier.fillMaxWidth(), enabled = numeroCedolini > 0,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Colori.Rosso),
                ) {
                    Icon(Icons.Filled.DeleteForever, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Elimina tutti i cedolini")
                }
            }
        }
        item {
            Scheda(titolo = "Come leggere i dati", icona = Icons.Filled.Info) {
                Text(
                    "• Lordo, netto, IRPEF, addizionali e contributi sono quelli scritti nel cedolino.\n" +
                        "• La stima IRPEF è solo indicativa: dipende da contratto, imponibile progressivo, detrazioni e conguagli.\n" +
                        "• Ferie e ROL: maturati, goduti e residui come riportati in busta.\n" +
                        "• Straordinario: ore effettuate, pagate, accantonate e banca ore residua, se presenti.\n" +
                        "• Il riconoscimento automatico può sbagliare: controlla sempre i dati nella schermata di revisione.",
                    fontSize = 13.sp, color = Colori.TestoSecondario, lineHeight = 19.sp,
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth()) {
                Text("PayRecap 1.0.0", fontSize = 12.sp, color = Colori.TestoSecondario, modifier = Modifier.weight(1f))
            }
            Copyright()
        }
    }
    if (conferma) {
        AlertDialog(
            onDismissRequest = { conferma = false },
            title = { Text("Eliminare tutti i cedolini?") },
            text = { Text("Tutti i riepiloghi salvati su questo telefono verranno cancellati. L'operazione non si può annullare.") },
            confirmButton = { TextButton(onClick = { conferma = false; onEliminaTutto() }) { Text("Elimina tutto", color = Colori.Rosso) } },
            dismissButton = { TextButton(onClick = { conferma = false }) { Text("Annulla") } },
        )
    }
}
