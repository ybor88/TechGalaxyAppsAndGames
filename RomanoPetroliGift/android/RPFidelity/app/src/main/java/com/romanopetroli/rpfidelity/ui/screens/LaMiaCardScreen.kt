package com.romanopetroli.rpfidelity.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.romanopetroli.rpfidelity.ui.theme.RpGold
import com.romanopetroli.rpfidelity.ui.theme.RpNavy
import com.romanopetroli.rpfidelity.viewmodel.SessionViewModel
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaMiaCardScreen(sessionViewModel: SessionViewModel, onOpenDrawer: () -> Unit) {
    val user by sessionViewModel.user.collectAsState()

    Scaffold(
        topBar = {
            com.romanopetroli.rpfidelity.ui.theme.RpTopBar(
                title = "La mia Card",
                navigationIcon = Icons.Filled.Menu,
                onNavigationClick = onOpenDrawer,
                navigationContentDescription = "Apri menu"
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Mostra questo QR alla cassa per caricare i punti", textAlign = TextAlign.Center)

            val codice = user?.codiceCard
            if (codice != null) {
                val qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=240x240&data=" +
                    URLEncoder.encode(codice, "UTF-8")

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = RpNavy),
                    border = BorderStroke(2.dp, RpGold),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AsyncImage(
                            model = qrUrl,
                            contentDescription = "QR Card",
                            modifier = Modifier.size(220.dp)
                        )
                        Text(
                            codice,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            color = Color.White,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                        Text(
                            "Saldo punti: ${formatPunti(user?.puntiSaldo ?: 0.0)}",
                            fontWeight = FontWeight.Bold,
                            color = RpGold,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            } else {
                Text("Nessun codice card associato al tuo account.", modifier = Modifier.padding(top = 24.dp))
            }
        }
    }
}
