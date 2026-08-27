package com.example.playerbase.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.playerbase.data.TeamLogoStore

/**
 * Piccolo stemma squadra, mostrato solo se per [teamKey] è stato caricato un
 * logo (con [TeamLogoStore]); altrimenti non disegna nulla. [teamKey] è di
 * norma il "Max Team" del giocatore, con fallback sul suo id se non compilato
 * (stessa chiave usata in "Foto e maglia" per l'upload). Mostra il logo
 * scelto personalmente da [playerId], così due giocatori con lo stesso Max
 * Team ma stemmi diversi restano ciascuno con il proprio.
 */
@Composable
fun TeamLogoBadge(playerId: String, teamKey: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val logoStore = remember { TeamLogoStore(context) }
    val logoFile = remember(playerId, teamKey) { logoStore.getLogoForPlayer(playerId, teamKey) }

    if (logoFile != null) {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(Color.White)
                .border(1.5.dp, Color.White, CircleShape)
        ) {
            AsyncImage(
                model = logoFile,
                contentDescription = "Logo squadra",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
