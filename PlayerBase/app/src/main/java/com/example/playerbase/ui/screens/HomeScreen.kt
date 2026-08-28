package com.example.playerbase.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import com.example.playerbase.R
import com.example.playerbase.data.Sport
import com.example.playerbase.ui.theme.BrandColors
import com.example.playerbase.ui.theme.accentColor
import com.example.playerbase.ui.theme.darken
import com.example.playerbase.viewmodel.PlayerViewModel

@Composable
fun HomeScreen(
    viewModel: PlayerViewModel,
    onSportClick: (Sport) -> Unit,
    onChartClick: () -> Unit,
    onMaxCareerStatsClick: () -> Unit,
    onScoutingExpiringClick: () -> Unit,
    onOpenColorSettings: () -> Unit
) {
    val players by viewModel.players.collectAsState()
    val basketCount = players.count { it.sport == Sport.BASKET }
    val calcioCount = players.count { it.sport == Sport.CALCIO }
    val expiringCount = viewModel.expiringScouting().size

    val context = LocalContext.current
    // Backup completo in .zip: dati giocatori + foto profilo + loghi squadra,
    // non solo il CSV — così l'export porta con sé anche le immagini caricate.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val ok = viewModel.exportDatabase(uri)
            Toast.makeText(
                context,
                if (ok) "Database e immagini esportati" else "Esportazione non riuscita",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val count = viewModel.importDatabase(uri)
            Toast.makeText(
                context,
                if (count != null) "Importati $count giocatori (con foto e loghi)" else "Import non riuscito: file non valido",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrandColors.navy)
                    .padding(24.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.logo_playerbase),
                            contentDescription = "PlayerBase Logo",
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "PlayerBase",
                                color = BrandColors.gold,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                "Anagrafica, avatar e scouting dei tuoi giocatori",
                                color = Color.White.copy(alpha = 0.80f),
                                fontSize = 13.sp
                            )
                        }
                        IconButton(onClick = onOpenColorSettings) {
                            Icon(Icons.Filled.Palette, contentDescription = "Personalizza colori app", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Column {
                        TextButton(onClick = { exportLauncher.launch("playerbase_backup.zip") }) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null, tint = BrandColors.gold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Esporta dati e immagini", color = Color.White)
                        }
                        TextButton(onClick = { importLauncher.launch("application/zip") }) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null, tint = BrandColors.gold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Importa dati e immagini", color = Color.White)
                        }
                    }
                }
            }

            LazyVerticalGridSections(
                onSportClick = onSportClick,
                onChartClick = onChartClick,
                onMaxCareerStatsClick = onMaxCareerStatsClick,
                onScoutingExpiringClick = onScoutingExpiringClick,
                basketCount = basketCount,
                calcioCount = calcioCount,
                expiringCount = expiringCount
            )
        }
    }
}

@Composable
private fun LazyVerticalGridSections(
    onSportClick: (Sport) -> Unit,
    onChartClick: () -> Unit,
    onMaxCareerStatsClick: () -> Unit,
    onScoutingExpiringClick: () -> Unit,
    basketCount: Int,
    calcioCount: Int,
    expiringCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PhotoSectionCard(
            title = "Basket",
            subtitle = "$basketCount giocatori",
            imageRes = R.drawable.basket_energy,
            onClick = { onSportClick(Sport.BASKET) }
        )
        PhotoSectionCard(
            title = "Calcio",
            subtitle = "$calcioCount giocatori",
            imageRes = R.drawable.calcio_energy,
            onClick = { onSportClick(Sport.CALCIO) }
        )
        GradientSectionCard(
            icon = Icons.Filled.BarChart,
            title = "Statistiche",
            subtitle = "Raggruppa per Max Team",
            gradient = Brush.horizontalGradient(listOf(BrandColors.gold.darken(0.4f), BrandColors.gold)),
            onClick = onChartClick
        )
        GradientSectionCard(
            icon = Icons.Filled.EmojiEvents,
            title = "Max Career",
            subtitle = "Raggruppa per livello raggiunto",
            gradient = Brush.horizontalGradient(listOf(Color(0xFF0D5C4A), Color(0xFF17A589))),
            onClick = onMaxCareerStatsClick
        )
        GradientSectionCard(
            icon = Icons.Filled.NotificationsActive,
            title = "Scouting in scadenza",
            subtitle = if (expiringCount == 0) "Tutto aggiornato" else "$expiringCount giocatori da ricontrollare",
            gradient = Brush.horizontalGradient(listOf(Color(0xFF7A0C0C), Color(0xFFD32F2F))),
            onClick = onScoutingExpiringClick,
            badgeCount = expiringCount
        )
    }
}

/** Card a piena immagine per Basket/Calcio: la foto riempie tutta la card, con
 * una sfumatura scura in basso per leggere titolo e contatore. */
@Composable
private fun PhotoSectionCard(
    title: String,
    subtitle: String,
    imageRes: Int,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(18.dp)
            ) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Card con sfondo a gradiente sfumato (invece del bianco piatto di default),
 * per Statistiche e Scouting che non hanno una foto dedicata. */
@Composable
private fun GradientSectionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradient: Brush,
    onClick: () -> Unit,
    badgeCount: Int = 0
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
            }
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(badgeCount.toString(), color = Color(0xFF7A0C0C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
