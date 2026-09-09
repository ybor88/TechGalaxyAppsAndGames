package com.example.sentinelai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sentinelai.ui.theme.SentinelDanger
import com.example.sentinelai.ui.theme.SentinelOk
import com.example.sentinelai.ui.theme.SentinelPanel
import com.example.sentinelai.ui.theme.SentinelPanelBorder
import com.example.sentinelai.ui.theme.SentinelTeal
import com.example.sentinelai.ui.theme.SentinelText
import com.example.sentinelai.ui.theme.SentinelTextDim
import com.example.sentinelai.ui.theme.SentinelWarning

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SentinelPanel),
        border = BorderStroke(1.dp, SentinelPanelBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(value, color = SentinelText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label.uppercase(), color = SentinelTextDim, fontSize = 10.sp)
        }
    }
}

@Composable
fun FeatureTile(icon: String, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = SentinelPanel),
        border = BorderStroke(1.dp, SentinelPanelBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(icon, fontSize = 22.sp)
            Text(title, color = SentinelText, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Text(subtitle, color = SentinelTextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
fun ShieldStatusCard(protected: Boolean, subtitle: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SentinelPanel),
        border = BorderStroke(1.dp, SentinelPanelBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(if (protected) "🛡" else "⚠", fontSize = 34.sp)
            Column {
                Text(
                    if (protected) "Sistema protetto" else "Protezione in tempo reale disattivata",
                    color = SentinelText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(subtitle, color = SentinelTextDim, fontSize = 12.sp)
            }
        }
    }
}

fun verdictColorFor(verdict: String) = when (verdict) {
    "malicious" -> SentinelDanger
    "suspicious" -> SentinelWarning
    else -> SentinelOk
}

@Composable
fun StatusPill(text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

@Composable
fun ScreenHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(title, color = SentinelText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = SentinelTextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = SentinelText, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}
