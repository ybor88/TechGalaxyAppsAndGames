package com.romanopetroli.rpfidelity.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.romanopetroli.rpfidelity.ui.theme.RpNavy

/** Bolla di chat: allineata a destra e scura per il proprio messaggio, a sinistra e chiara per l'altro. */
@Composable
fun ChatBubble(autore: String, testo: String, daDestra: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (daDestra) Arrangement.End else Arrangement.Start
    ) {
        Box(modifier = Modifier.widthIn(max = 280.dp)) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (daDestra) RpNavy else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(10.dp, 8.dp)) {
                    Text(
                        autore,
                        fontWeight = FontWeight.Bold,
                        color = if (daDestra) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(testo, color = if (daDestra) Color.White else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
