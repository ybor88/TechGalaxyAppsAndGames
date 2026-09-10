package com.scouttable.app.ui.sportselect

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scouttable.app.R
import com.scouttable.app.data.Sport
import com.scouttable.app.data.lookup.DataSource
import com.scouttable.app.data.lookup.DataSources
import com.scouttable.app.data.lookup.SourceState
import com.scouttable.app.data.lookup.SourceStatus
import com.scouttable.app.ui.theme.ScoutGradient
import com.scouttable.app.ui.theme.ScoutGreen

@Composable
fun SportSelectionScreen(onSportSelected: (Sport) -> Unit) {
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(R.drawable.app_logo),
                        contentDescription = "ScoutTable AI",
                        modifier = Modifier
                            .size(140.dp)
                            .padding(bottom = 8.dp),
                    )
                    Text(
                        text = "ScoutTable AI",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "IMPORT. ANALYZE. COMPARE.",
                        fontSize = 12.sp,
                        letterSpacing = 2.sp,
                        color = ScoutGreen,
                        modifier = Modifier.padding(bottom = 32.dp),
                    )
                    Text(
                        text = "Scegli lo sport da scoutare",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )

                    SportCard(
                        title = "Basket",
                        icon = Icons.Default.SportsBasketball,
                        onClick = { onSportSelected(Sport.BASKET) },
                    )
                    SportCard(
                        title = "Calcio",
                        icon = Icons.Default.SportsSoccer,
                        onClick = { onSportSelected(Sport.CALCIO) },
                    )

                    Text(
                        text = "Stato fonti dati",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 8.dp),
                    )
                }
            }
            items(DataSources.all) { source ->
                SourceStatusRow(source)
            }
        }
    }
}

@Composable
private fun SportCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(96.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScoutGradient),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(32.dp))
                Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}

/**
 * Riga cliccabile (apre l'homepage della fonte nel browser) con un pallino che indica se
 * risponde in questo momento: verde = online, rosso = offline/non raggiungibile, grigio = in
 * controllo. Il test avviene una volta al caricamento della schermata, non è un monitoraggio
 * continuo.
 */
@Composable
private fun SourceStatusRow(source: DataSource) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(SourceState.CHECKING) }

    LaunchedEffect(source) { state = SourceStatus.check(source) }

    Card(
        onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.homeUrl)))
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state == SourceState.CHECKING) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                val dotColor = if (state == SourceState.ONLINE) ScoutGreen else Color(0xFFE05C5C)
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
            Text(
                source.name,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            Text(
                when (state) {
                    SourceState.CHECKING -> "verifica…"
                    SourceState.ONLINE -> "online"
                    SourceState.OFFLINE -> "non raggiungibile"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
