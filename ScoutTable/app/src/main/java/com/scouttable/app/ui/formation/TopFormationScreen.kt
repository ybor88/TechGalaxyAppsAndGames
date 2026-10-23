// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.formation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.lookup.BasketRole
import com.scouttable.app.data.lookup.basketRoleOf
import com.scouttable.app.data.ranking.PlayerScoring
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.common.LoadingBar
import com.scouttable.app.ui.common.PlayerAvatar
import com.scouttable.app.ui.common.drawBasketCourt
import com.scouttable.app.ui.common.drawSoccerPitch
import com.scouttable.app.ui.common.fieldAspectRatio
import com.scouttable.app.ui.ranking.CalcioRuoloFiltro
import com.scouttable.app.ui.ranking.matchesRuolo
import com.scouttable.app.ui.ranking.scoreFor
import com.scouttable.app.ui.theme.ScoutGreen

/**
 * Un posto in formazione: ruolo (sigla mostrata sul campo), posizione sul campo in frazioni
 * (0..1, x da sinistra, y dall'alto; la squadra attacca verso l'alto) e giocatore assegnato,
 * null se in lista non c'è nessuno con quel ruolo e statistiche disponibili.
 */
private data class Slot(
    val roleLabel: String,
    val roleShort: String,
    val x: Float,
    val y: Float,
    val player: Player?,
    val scoreLine: String,
)

private data class Lineup(val starters: List<Slot>, val bench: List<Slot>)

// ---- Calcio: 4-4-2 (POR, 4 DIF, 4 CEN, 2 ATT) + un cambio per ruolo ----

private data class CalcioReparto(val filtro: CalcioRuoloFiltro, val label: String, val short: String, val positions: List<Pair<Float, Float>>)

private val calcioReparti = listOf(
    CalcioReparto(CalcioRuoloFiltro.PORTIERI, "Portiere", "POR", listOf(0.5f to 0.9f)),
    CalcioReparto(
        CalcioRuoloFiltro.DIFENSORI, "Difensore", "DIF",
        listOf(0.1f to 0.66f, 0.36f to 0.71f, 0.64f to 0.71f, 0.9f to 0.66f),
    ),
    CalcioReparto(
        CalcioRuoloFiltro.CENTROCAMPISTI, "Centrocampista", "CEN",
        listOf(0.1f to 0.4f, 0.36f to 0.45f, 0.64f to 0.45f, 0.9f to 0.4f),
    ),
    CalcioReparto(CalcioRuoloFiltro.ATTACCANTI, "Attaccante", "ATT", listOf(0.32f to 0.14f, 0.68f to 0.14f)),
)

/** Stesse classifiche per ruolo della tab "Classifica" (vedi [com.scouttable.app.ui.ranking.CalcioRankingScreen]):
 *  titolari i primi N del ruolo, in panchina un secondo 4-4-2 completo con i successivi N. */
private fun calcioLineup(players: List<Player>): Lineup {
    val starters = mutableListOf<Slot>()
    val bench = mutableListOf<Slot>()
    calcioReparti.forEach { reparto ->
        val ranked = players.filter { it.presenze > 0 && matchesRuolo(reparto.filtro, it.ruolo) }
            .sortedByDescending { scoreFor(reparto.filtro, it) }
        fun line(p: Player?) = p?.let { "Rating ${scoreFor(reparto.filtro, it)}" }.orEmpty()
        val n = reparto.positions.size
        reparto.positions.forEachIndexed { i, (x, y) ->
            val p = ranked.getOrNull(i)
            starters += Slot(reparto.label, reparto.short, x, y, p, line(p))
            val sub = ranked.getOrNull(n + i)
            bench += Slot(reparto.label, reparto.short, 0f, 0f, sub, line(sub))
        }
    }
    return Lineup(starters, bench)
}

// ---- Basket: quintetto (C, AG, AP, G, PM) + un cambio per ruolo ----

private data class BasketSpot(val role: BasketRole, val label: String, val short: String, val x: Float, val y: Float)

private val basketSpots = listOf(
    BasketSpot(BasketRole.CENTRO, "Centro", "C", 0.5f, 0.17f),
    BasketSpot(BasketRole.ALA_GRANDE, "Ala grande", "AG", 0.18f, 0.3f),
    BasketSpot(BasketRole.ALA_PICCOLA, "Ala piccola", "AP", 0.82f, 0.3f),
    BasketSpot(BasketRole.GUARDIA, "Guardia", "G", 0.2f, 0.56f),
    BasketSpot(BasketRole.PLAYMAKER, "Playmaker", "PM", 0.62f, 0.62f),
)

/** Stesso rating della classifica basket (Eff medio + bonus NBA/WNBA, vedi [PlayerScoring.basketScore]). */
private fun basketLineup(players: List<Player>): Lineup {
    val starters = mutableListOf<Slot>()
    val bench = mutableListOf<Slot>()
    basketSpots.forEach { spot ->
        val ranked = players.filter { it.effMedio > 0 && basketRoleOf(it.ruolo) == spot.role }
            .sortedByDescending { PlayerScoring.basketScore(it) }
        fun line(p: Player?) = p?.let { "Rating ${PlayerScoring.basketScore(it)}" }.orEmpty()
        val first = ranked.getOrNull(0)
        val second = ranked.getOrNull(1)
        starters += Slot(spot.label, spot.short, spot.x, spot.y, first, line(first))
        bench += Slot(spot.label, spot.short, 0f, 0f, second, line(second))
    }
    return Lineup(starters, bench)
}

/**
 * "Formazione Top": la miglior formazione possibile con i giocatori in lista, ruolo per ruolo,
 * presa dai rating delle classifiche (calcio 4-4-2 su campo con le porte, basket quintetto su
 * campo con i due canestri), più la panchina (calcio: un secondo 4-4-2 completo; basket: un
 * cambio per ruolo) e i link ai ranking ufficiali delle nazionali dello sport corrente (FIBA per
 * il basket, FIFA per il calcio; maschile e femminile).
 */
@Composable
fun TopFormationScreen(sport: Sport, padding: PaddingValues, onOpenPlayer: (String) -> Unit) {
    val repository = rememberPlayerRepository()
    val loadedPlayers by repository.observePlayers(sport).collectAsState<List<Player>, List<Player>?>(initial = null)
    val lineup = remember(loadedPlayers, sport) {
        val players = loadedPlayers.orEmpty()
        if (sport.isBasket) basketLineup(players) else calcioLineup(players)
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            if (sport.isBasket) "Quintetto top" else "Formazione top · 4-4-2",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (sport.isBasket) {
                "Il miglior giocatore per ruolo secondo il rating della classifica (Eff medio + bonus NBA/WNBA)."
            } else {
                "I migliori per ruolo secondo il rating delle classifiche per ruolo (portieri, difensori, centrocampisti, attaccanti)."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        if (loadedPlayers == null) {
            LoadingBar(label = "Caricamento formazione…")
        } else {
            Field(sport = sport, slots = lineup.starters, onOpenPlayer = onOpenPlayer)

            Text(
                "Panchina",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            lineup.bench.forEach { slot -> BenchRow(slot, onOpenPlayer) }
        }

        Text(
            "Rating nazionali",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        nationalRankingLinks.filter { it.sport.isBasket == sport.isBasket }.forEach { link -> RankingLinkRow(link) }

        Text(
            "© Roberto Di Flumeri",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
    }
}

// ---- Campo ----

// Il rapporto d'aspetto del Box segue quello del campo vero (vedi FieldDrawing.kt).
@Composable
private fun Field(sport: Sport, slots: List<Slot>, onOpenPlayer: (String) -> Unit) {
    val ratio = fieldAspectRatio(sport.isBasket)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (sport.isBasket) drawBasketCourt() else drawSoccerPitch()
        }
        slots.forEach { slot ->
            FieldPlayer(
                slot = slot,
                onOpenPlayer = onOpenPlayer,
                modifier = Modifier.align(BiasAlignment(slot.x * 2f - 1f, slot.y * 2f - 1f)),
            )
        }
    }
}

@Composable
private fun FieldPlayer(slot: Slot, onOpenPlayer: (String) -> Unit, modifier: Modifier = Modifier) {
    val player = slot.player
    Column(
        modifier = modifier
            .padding(2.dp)
            .widthIn(max = 86.dp)
            .then(if (player != null) Modifier.clickable { onOpenPlayer(player.id) } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (player != null) {
            PlayerAvatar(name = player.nome, logoPath = player.logoPath, size = 40.dp)
        } else {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("?", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Column(
            modifier = Modifier
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                player?.nome ?: "Nessuno",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (player != null) "${slot.roleShort} · ${slot.scoreLine.removePrefix("Rating ")}" else slot.roleShort,
                style = MaterialTheme.typography.labelSmall,
                color = ScoutGreen,
                maxLines = 1,
            )
        }
    }
}

// ---- Panchina ----

@Composable
private fun BenchRow(slot: Slot, onOpenPlayer: (String) -> Unit) {
    val player = slot.player
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (player != null) Modifier.clickable { onOpenPlayer(player.id) } else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                slot.roleShort,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp),
            )
            if (player != null) {
                PlayerAvatar(name = player.nome, logoPath = player.logoPath, size = 36.dp)
            }
            Column(modifier = Modifier.weight(1f).padding(start = if (player != null) 12.dp else 0.dp)) {
                Text(
                    player?.nome ?: "Nessun cambio disponibile",
                    fontWeight = if (player != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (player != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (player != null) "${slot.roleLabel} · ${player.carrieraMigliore.ifBlank { "—" }}" else slot.roleLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (player != null) {
                Text(slot.scoreLine, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ScoutGreen)
            }
        }
    }
}

// ---- Rating nazionali ----

// Solo i ranking dello sport corrente: FIBA per il basket, FIFA per il calcio.
private data class RankingLink(val sport: Sport, val title: String, val subtitle: String, val url: String)

private val nationalRankingLinks = listOf(
    RankingLink(Sport.BASKET, "FIBA · Maschile", "Ranking mondiale basket nazionali maschili", "https://www.fiba.basketball/en/ranking/men"),
    RankingLink(Sport.BASKET, "FIBA · Femminile", "Ranking mondiale basket nazionali femminili", "https://www.fiba.basketball/en/ranking/women"),
    RankingLink(Sport.CALCIO, "FIFA · Maschile", "Ranking mondiale calcio nazionali maschili", "https://inside.fifa.com/fifa-world-ranking/men"),
    RankingLink(Sport.CALCIO, "FIFA · Femminile", "Ranking mondiale calcio nazionali femminili", "https://inside.fifa.com/fifa-world-ranking/women"),
)

@Composable
private fun RankingLinkRow(link: RankingLink) {
    val context = LocalContext.current
    Card(
        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(link.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(link.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Apri", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
