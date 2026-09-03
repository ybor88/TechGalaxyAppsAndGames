package com.romanopetroli.rpfidelity.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ContactMail
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.romanopetroli.rpfidelity.R
import com.romanopetroli.rpfidelity.data.model.User
import com.romanopetroli.rpfidelity.navigation.Screen
import com.romanopetroli.rpfidelity.ui.theme.RpNavy

private data class VoceMenu(val route: String, val label: String, val icon: ImageVector)

private val vociCliente = listOf(
    VoceMenu(Screen.Dashboard.route, "Home", Icons.Filled.Home),
    VoceMenu(Screen.LaMiaCard.route, "La mia Card", Icons.Filled.CreditCard),
    VoceMenu(Screen.Rifornimenti.route, "I miei rifornimenti", Icons.Filled.Receipt),
    VoceMenu(Screen.Voucher.route, "Voucher", Icons.Filled.CardGiftcard),
    VoceMenu(Screen.Contatti.route, "Contatti", Icons.Filled.ContactMail),
    VoceMenu(Screen.Impostazioni.route, "Impostazioni", Icons.Filled.Settings),
    VoceMenu(Screen.Faq.route, "FAQ", Icons.Filled.Help)
)

private val vociAdmin = listOf(
    VoceMenu(Screen.Dashboard.route, "Home", Icons.Filled.Home),
    VoceMenu(Screen.AdminStatistiche.route, "Statistiche", Icons.Filled.Assessment),
    VoceMenu(Screen.AdminClienti.route, "Gestione clienti", Icons.Filled.People),
    VoceMenu(Screen.AdminRegistraRifornimento.route, "Registra Rifornimento", Icons.Filled.LocalGasStation),
    VoceMenu(Screen.AdminReports.route, "Reports", Icons.Filled.Receipt),
    VoceMenu(Screen.AdminVerificaVoucher.route, "Verifica Voucher", Icons.Filled.QrCodeScanner)
)

@Composable
fun RpDrawerContent(
    user: User?,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
) {
    val voci = if (user?.isAdmin == true) vociAdmin else vociCliente

    ModalDrawerSheet {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Image(
                painter = painterResource(id = R.drawable.logo_rpfidelity),
                contentDescription = "RP Fidelity",
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
            )
            Text("RP Fidelity", fontWeight = FontWeight.Bold, color = RpNavy, modifier = Modifier.padding(top = 12.dp))
            if (user != null) {
                Text("Ciao ${user.nome}!", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider()

        voci.forEach { voce ->
            NavigationDrawerItem(
                icon = { Icon(voce.icon, contentDescription = null) },
                label = { Text(voce.label) },
                selected = currentRoute == voce.route,
                onClick = { onNavigate(voce.route) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        NavigationDrawerItem(
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
            label = { Text("Esci") },
            selected = false,
            onClick = onLogout,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )
    }
}
