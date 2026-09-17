// Copyright (c) Roberto Di Flumeri
package com.scouttable.app.ui.playerdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.YouthClub
import com.scouttable.app.data.decodeYouthClubs
import com.scouttable.app.data.lookup.isPortiere
import com.scouttable.app.data.lookup.translateRole
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.common.EditPlayerDialog
import com.scouttable.app.ui.common.PlayerAvatar
import com.scouttable.app.ui.common.flagEmojiFor
import com.scouttable.app.ui.theme.ScoutGreen
import kotlin.math.round

/**
 * Scheda completa di un giocatore: apre da [com.scouttable.app.ui.common.PlayerRow] (toccando la
 * riga in Lista/Revisione), sostituisce il vecchio comportamento di apertura diretta di
 * [EditPlayerDialog] (ora raggiungibile da qui con l'icona "modifica"). Mostra il "secondo logo"
 * (club/stagione con il rapporto/Eff migliore, vedi [com.scouttable.app.data.lookup.BestSpellPicker]
 * per il calcio), le giovanili e la Nazionale (calcio), Eff/minuti/college (basket).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDetailScreen(sport: Sport, playerId: String, onBack: () -> Unit) {
    val repository = rememberPlayerRepository()
    val player by repository.observePlayer(sport, playerId).collectAsState(initial = null)
    var showEditDialog by remember { mutableStateOf(false) }
    val current = player

    if (showEditDialog && current != null) {
        EditPlayerDialog(sport = sport, player = current, onDismiss = { showEditDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.nome ?: "Giocatore") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }, enabled = current != null) {
                        Icon(Icons.Default.Edit, contentDescription = "Modifica")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (current == null) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            PlayerDetailContent(player = current, sport = sport, padding = padding)
        }
    }
}

@Composable
private fun PlayerDetailContent(player: Player, sport: Sport, padding: PaddingValues) {
    val giovanili = remember(player.giovanili) { decodeYouthClubs(player.giovanili) }
    LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
        item { Header(player, sport) }
        item { Spacer(modifier = Modifier.padding(top = 16.dp)) }
        item { TwoLogoCard(player, sport) }
        if (sport == Sport.CALCIO) {
            if (giovanili.isNotEmpty()) {
                item { Spacer(modifier = Modifier.padding(top = 16.dp)) }
                item { YouthClubsCard(giovanili) }
            }
            item { Spacer(modifier = Modifier.padding(top = 16.dp)) }
            item { NazionaleCalcioCard(player) }
        } else {
            item { Spacer(modifier = Modifier.padding(top = 16.dp)) }
            item { BasketStatsCard(player) }
            item { Spacer(modifier = Modifier.padding(top = 16.dp)) }
            item { NazionaleBasketCard(player) }
        }
    }
}

@Composable
private fun Header(player: Player, sport: Sport) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PlayerAvatar(name = player.nome, logoPath = player.logoPath, size = 72.dp)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(player.nome, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (player.visionato) {
                    Spacer(modifier = Modifier.padding(start = 6.dp))
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = "Visionato dal vivo",
                        tint = ScoutGreen,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            val flag = flagEmojiFor(player.nazione)
            val ruoloIt = translateRole(player.ruolo, sport)
            val annoPart = if (player.anno > 0) "${player.anno}" else ""
            val nazionePart = if (player.nazione.isNotBlank()) {
                "${if (flag.isNotBlank()) "$flag " else ""}${player.nazione}"
            } else ""
            val parts = listOf(annoPart, nazionePart, ruoloIt).filter { it.isNotBlank() }
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (player.stato.isNotBlank()) {
                Text(player.stato, style = MaterialTheme.typography.labelMedium, color = ScoutGreen)
            }
        }
    }
}

@Composable
private fun TwoLogoCard(player: Player, sport: Sport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            LogoColumn(
                club = player.carrieraMigliore,
                logoPath = player.logoPath,
                title = "Carriera migliore",
                subtitle = null,
            )
            if (player.secondLogoClub.isNotBlank()) {
                LogoColumn(
                    club = player.secondLogoClub,
                    logoPath = player.secondLogoPath,
                    title = "Periodo migliore",
                    subtitle = secondLogoSubtitle(player, sport),
                )
            }
        }
    }
}

private fun secondLogoSubtitle(player: Player, sport: Sport): String {
    if (sport == Sport.BASKET) {
        return listOf(player.secondLogoPeriodo, "Eff ${player.secondLogoEff}")
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }
    val isPortiereCalcio = isPortiere(player.ruolo)
    val golLabel = if (isPortiereCalcio) "gol subiti" else "gol"
    return listOf(
        player.secondLogoPeriodo,
        "${player.secondLogoPresenze} presenze, ${player.secondLogoGol} $golLabel",
    ).filter { it.isNotBlank() }.joinToString(" · ")
}

@Composable
private fun RowScope.LogoColumn(club: String, logoPath: String?, title: String, subtitle: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
        PlayerAvatar(name = club, logoPath = logoPath, size = 64.dp)
        Text(
            club,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun YouthClubsCard(giovanili: List<YouthClub>) {
    SectionCard(title = "Giovanili") {
        giovanili.forEach { club ->
            Text(
                "${club.club} (${club.anni})",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun NazionaleCalcioCard(player: Player) {
    val isPortiereCalcio = isPortiere(player.ruolo)
    SectionCard(title = "Nazionale") {
        if (player.presenzeNazionale <= 0) {
            Text("Nessun dato", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            StatLine("Presenze", "${player.presenzeNazionale}")
            if (isPortiereCalcio) {
                StatLine("Gol subiti", "${player.golSubitiNazionale}")
            } else {
                StatLine("Gol", "${player.punteggioNazionale}")
            }
        }
    }
}

@Composable
private fun BasketStatsCard(player: Player) {
    SectionCard(title = "Statistiche di carriera") {
        if (player.effMedio > 0) StatLine("Eff medio", "${player.effMedio}")
        if (player.presenze > 0) {
            val mediaPunti = round(player.punteggio.toDouble() / player.presenze * 10) / 10
            StatLine("Media punti", "$mediaPunti")
            if (player.minutiCarriera > 0) {
                val mediaMinuti = round(player.minutiCarriera.toDouble() / player.presenze * 10) / 10
                StatLine("Media minuti", "$mediaMinuti")
            }
        }
        if (player.college.isNotBlank()) StatLine("College", player.college)
    }
}

@Composable
private fun NazionaleBasketCard(player: Player) {
    SectionCard(title = "Nazionale") {
        if (player.presenzeNazionale <= 0) {
            Text("Nessun dato", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            StatLine("Presenze", "${player.presenzeNazionale}")
            val mediaPunti = round(player.punteggioNazionale.toDouble() / player.presenzeNazionale * 10) / 10
            StatLine("Media punti", "$mediaPunti")
            if (player.minutiNazionale > 0) {
                val mediaMinuti = round(player.minutiNazionale.toDouble() / player.presenzeNazionale * 10) / 10
                StatLine("Media minuti", "$mediaMinuti")
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.padding(top = 8.dp))
            content()
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}
