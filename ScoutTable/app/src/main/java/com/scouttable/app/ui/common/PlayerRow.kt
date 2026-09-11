package com.scouttable.app.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.PlayerStatus
import com.scouttable.app.data.Sport
import com.scouttable.app.data.lookup.BasketRole
import com.scouttable.app.data.lookup.basketRoleOf
import com.scouttable.app.data.lookup.isCentrocampista
import com.scouttable.app.data.lookup.isDifensore
import com.scouttable.app.data.lookup.isPortiere
import com.scouttable.app.data.lookup.translateRole
import com.scouttable.app.ui.theme.ScoutGreen
import kotlin.math.round

@Composable
fun PlayerRow(player: Player, sport: Sport, onClick: () -> Unit = {}) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerAvatar(name = player.nome, logoPath = player.logoPath, size = 48.dp)

            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            ) {
                Text(player.nome, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                val flag = flagEmojiFor(player.nazione)
                // translateRole è idempotente sui ruoli già in italiano (non trovando una chiave
                // inglese corrispondente restituisce il valore invariato): serve anche a mostrare
                // subito in italiano i ruoli salvati in inglese prima di questa traduzione.
                val ruoloIt = translateRole(player.ruolo, sport)
                // Anno/nazione possono mancare per un giocatore trovato solo tramite il fallback
                // Wikipedia (nessun profilo TheSportsDB, es. Pietro Aradori): niente "0 ·" vuoto.
                val annoPart = if (player.anno > 0) "${player.anno}" else ""
                val nazionePart = if (player.nazione.isNotBlank()) {
                    "${if (flag.isNotBlank()) "$flag " else ""}${player.nazione}"
                } else ""
                val anagraficaParts = listOf(annoPart, nazionePart, ruoloIt).filter { it.isNotBlank() }
                Text(
                    anagraficaParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    player.carrieraMigliore.ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = ScoutGreen,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pallino verde per distinguere a colpo d'occhio i giocatori "Attivo" dagli
                    // altri stati (Ritirato/Infortunato/Svincolato) nella lista.
                    if (player.stato == PlayerStatus.ATTIVO) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ScoutGreen),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        player.stato,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Ruolo basket riconosciuto (Centro/Ala grande/Ala piccola/Guardia/Playmaker):
                // decide quali medie aggiuntive mostrare oltre a presenze/punti/ppg, già comuni a
                // tutti. Per il calcio, isDifensore/isCentrocampista decidono se mostrare
                // tackle+gol evitati o assist accanto a presenze/gol; il portiere ha una riga a
                // parte più sotto (niente "gol fatti", sempre ~0 per questo ruolo).
                val basketRole = if (sport == Sport.BASKET) basketRoleOf(player.ruolo) else null
                val isPortiereCalcio = sport == Sport.CALCIO && isPortiere(player.ruolo)
                if (isPortiereCalcio) {
                    if (player.presenze > 0 || player.golSubiti > 0) {
                        val presenzePart = if (player.presenze > 0) "${player.presenze} presenze" else ""
                        val golSubitiPart = if (player.golSubiti > 0) "${player.golSubiti} gol subiti" else ""
                        val competizionePart = if (player.competizione.isNotBlank()) player.competizione else ""
                        Text(
                            listOf(presenzePart, golSubitiPart, competizionePart).filter { it.isNotBlank() }
                                .joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (player.presenze > 0 || player.punteggio > 0 || player.assist > 0) {
                    val puntiLabel = if (sport == Sport.BASKET) "punti" else "gol"
                    val presenzePart = if (player.presenze > 0) "${player.presenze} presenze · " else ""
                    // Media punti a presenza: calcolata al volo da presenze/punti già presenti,
                    // non serve un nuovo campo salvato.
                    val mediaPart = if (sport == Sport.BASKET && player.presenze > 0) {
                        val media = round(player.punteggio.toDouble() / player.presenze * 10) / 10
                        " · $media ppg"
                    } else ""
                    val ruoloBasketPart = if (sport == Sport.BASKET) {
                        basketRoleStatsPart(player, basketRole)
                    } else ""
                    val difensoreCalcioPart = if (sport == Sport.CALCIO && isDifensore(player.ruolo)) {
                        val voci = listOfNotNull(
                            "${player.tackle} tackle".takeIf { player.tackle > 0 },
                            "${player.golEvitati} gol evitati".takeIf { player.golEvitati > 0 },
                        )
                        if (voci.isEmpty()) "" else " · ${voci.joinToString(" · ")}"
                    } else ""
                    val centrocampistaCalcioPart = if (sport == Sport.CALCIO && isCentrocampista(player.ruolo) && player.assist > 0) {
                        " · ${player.assist} assist"
                    } else ""
                    Text(
                        "$presenzePart${player.punteggio} $puntiLabel$mediaPart$ruoloBasketPart" +
                            "$difensoreCalcioPart$centrocampistaCalcioPart" +
                            if (player.competizione.isNotBlank()) " · ${player.competizione}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(onClick = {
                val query = Uri.encode("${player.nome} ${sport.label} highlights")
                val url = "https://www.google.com/search?q=$query&tbm=vid"
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }) {
                Icon(Icons.Default.PlayCircle, contentDescription = "Visiona", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Medie a partita (rimbalzi/palle recuperate/assist) e percentuali al tiro (due/tre) pertinenti
 * per il ruolo basket riconosciuto, oltre a punti/ppg già mostrati per tutti: rimbalzi per Centro/
 * Ala grande/Ala piccola, % da due per Ala grande, % da tre per Guardia/Playmaker, palle recuperate
 * per Ala piccola/Guardia/Playmaker, assist per Playmaker. Ruolo non riconosciuto (es. "Ala"
 * generico) -> nessuna statistica aggiuntiva. */
private fun basketRoleStatsPart(player: Player, role: BasketRole?): String {
    if (role == null) return ""
    fun perGame(total: Int): String? =
        if (player.presenze > 0 && total > 0) "${round(total.toDouble() / player.presenze * 10) / 10}" else null
    fun pct(value: Int, label: String): String? = if (value > 0) "$value% $label" else null

    val voci: List<String> = when (role) {
        BasketRole.CENTRO -> listOfNotNull(perGame(player.rimbalzi)?.plus(" rimbalzi"))
        BasketRole.ALA_GRANDE -> listOfNotNull(
            perGame(player.rimbalzi)?.plus(" rimbalzi"),
            pct(player.percentualeTiriDaDue, "da due"),
        )
        BasketRole.ALA_PICCOLA -> listOfNotNull(
            perGame(player.rimbalzi)?.plus(" rimbalzi"),
            perGame(player.palleRecuperate)?.plus(" palle recuperate"),
        )
        BasketRole.GUARDIA -> listOfNotNull(
            pct(player.percentualeTiriDaTre, "da tre"),
            perGame(player.palleRecuperate)?.plus(" palle recuperate"),
        )
        BasketRole.PLAYMAKER -> listOfNotNull(
            perGame(player.assist)?.plus(" assist"),
            perGame(player.palleRecuperate)?.plus(" palle recuperate"),
            pct(player.percentualeTiriDaTre, "da tre"),
        )
    }
    if (voci.isEmpty()) return ""
    return " · " + voci.joinToString(" · ")
}

