package com.opticpro.suite.ui.ophthalmology

import androidx.camera.core.CameraSelector
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opticpro.suite.ui.common.CameraPreview

/**
 * Metodo di "neutralizzazione" (principio dello skiascopio manuale): la lente in esame
 * viene interposta tra fotocamera e un target; l'operatore avvicina/allontana la lente
 * fino a quando il target non mostra più parallasse muovendo il dispositivo (punto di neutralità).
 * Potere approssimato (D) = 1 / distanza lente-target (m). Il segno dipende dal verso del movimento
 * apparente osservato dall'operatore (richiesto input manuale: no reale calcolo automatico da immagine,
 * poiché servirebbe hardware dedicato — collimatore — per una vera misura automatica accurata).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LensometerAiScreen(onBack: () -> Unit) {
    var distanceCm by remember { mutableStateOf(50f) }
    var movesSameDirection by remember { mutableStateOf(true) }

    val powerD = 100f / distanceCm
    val signedPower = if (movesSameDirection) -powerD else powerD

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Lensometro (metodo di neutralizzazione)") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "⚠️ Beta: stima manuale basata sul metodo di neutralizzazione, NON una misura automatica " +
                        "da immagine. Per lensometria accurata usare uno strumento dedicato.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "1) Interponi la lente tra fotocamera e questo schermo (guardando il target attraverso la lente). " +
                        "2) Muovi lateralmente il dispositivo: se l'immagine oltre la lente si muove nella stessa " +
                        "direzione del movimento, la lente è negativa; se in direzione opposta, è positiva. " +
                        "3) Trova la distanza lente-occhio a cui l'immagine non si muove (punto neutro) e impostala sotto."
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CameraPreview(modifier = Modifier.fillMaxSize(), facing = CameraSelector.LENS_FACING_BACK)
            }
            Column(Modifier.padding(16.dp)) {
                Text("Distanza al punto neutro: ${distanceCm.toInt()} cm")
                Slider(value = distanceCm, onValueChange = { distanceCm = it }, valueRange = 5f..200f)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = movesSameDirection, onCheckedChange = { movesSameDirection = it })
                    Text("Il movimento apparente è nella STESSA direzione (lente negativa)")
                }
                Spacer(Modifier.height(8.dp))
                Text("Potere stimato: %.2f D".format(signedPower), style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
