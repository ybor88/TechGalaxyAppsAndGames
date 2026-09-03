package com.romanopetroli.rpfidelity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.romanopetroli.rpfidelity.ui.theme.RpGold
import com.romanopetroli.rpfidelity.ui.theme.RpNavy
import com.romanopetroli.rpfidelity.ui.theme.RpNavyDark
import com.romanopetroli.rpfidelity.ui.theme.RpOrange
import com.romanopetroli.rpfidelity.viewmodel.SessionViewModel
import kotlin.math.floor

fun formatPunti(valore: Double): String =
    if (valore == floor(valore)) valore.toInt().toString() else "%.2f".format(valore)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    sessionViewModel: SessionViewModel,
    onOpenDrawer: () -> Unit,
    onLogout: () -> Unit,
    onLaMiaCard: () -> Unit,
    onRifornimenti: () -> Unit,
    onVoucher: () -> Unit,
    onAdminRegistraRifornimento: () -> Unit,
    onAdminReports: () -> Unit,
    onAdminVerificaVoucher: () -> Unit
) {
    val user by sessionViewModel.user.collectAsState()
    val isAdmin = user?.isAdmin == true

    Scaffold(
        topBar = {
            com.romanopetroli.rpfidelity.ui.theme.RpTopBar(
                title = "RP Fidelity",
                navigationIcon = Icons.Filled.Menu,
                onNavigationClick = onOpenDrawer,
                navigationContentDescription = "Apri menu",
                actionIcon = Icons.AutoMirrored.Filled.Logout,
                onActionClick = onLogout,
                actionContentDescription = "Esci"
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Brush.verticalGradient(listOf(RpNavy, RpNavyDark)))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Ciao ${user?.nome ?: ""}!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Romano Petroli", color = Color.White.copy(alpha = 0.7f))
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(20.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(RpOrange, RpGold)))
                            .padding(18.dp)
                    ) {
                        if (isAdmin) {
                            Text("Pannello Amministratore", color = Color.White)
                            Text(
                                "Gestione rifornimenti e voucher",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        } else {
                            Text("Saldo punti attuale", color = Color.White)
                            Text(
                                "${formatPunti(user?.puntiSaldo ?: 0.0)} punti",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Column(modifier = Modifier.padding(top = 24.dp)) {
                    if (isAdmin) {
                        Button(onClick = onAdminRegistraRifornimento, modifier = Modifier.fillMaxWidth()) {
                            Text("Registra Rifornimento")
                        }
                        Button(
                            onClick = onAdminReports,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) { Text("Reports") }
                        Button(
                            onClick = onAdminVerificaVoucher,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) { Text("Verifica Voucher") }
                    } else {
                        Button(onClick = onLaMiaCard, modifier = Modifier.fillMaxWidth()) {
                            Text("La mia Card")
                        }
                        Button(
                            onClick = onRifornimenti,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) { Text("I miei rifornimenti") }
                        Button(
                            onClick = onVoucher,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) { Text("Voucher") }
                    }
                }
            }
        }
    }
}
