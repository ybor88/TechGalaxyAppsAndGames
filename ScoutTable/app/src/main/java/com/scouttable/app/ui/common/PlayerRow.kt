// Copyright © Roberto Di Flumeri
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.PlayerStatus
import com.scouttable.app.data.ReviewPrefs
import com.scouttable.app.data.Sport
import com.scouttable.app.data.lookup.CalcioReparto
import com.scouttable.app.data.lookup.calcioRepartoOf
import com.scouttable.app.data.lookup.isCentrocampista
import com.scouttable.app.data.lookup.isDifensore
import com.scouttable.app.data.lookup.isPortiere
import com.scouttable.app.data.lookup.translateRole
import com.scouttable.app.data.ranking.PlayerScoring
import com.scouttable.app.ui.ranking.RankPosition
import com.scouttable.app.ui.theme.ScoutGreen

@Composable
fun PlayerRow(
    player: Player,
    sport: Sport,
    onClick: () -> Unit = {},
    /** Mostra l'indicatore "revisione tra N gg" (lista principale); false in "Revisione", dove
     *  sono tutti già segnalati. */
    showReviewHint: Boolean = false,
    /** Posizione in classifica sul totale (es. 14/230), mostrata a destra; null = non mostrata. */
    rank: RankPosition? = null,
) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // weight(fill = false): un nome lungo va a capo invece di occupare tutta la
                    // riga e spingere fuori (nascondendole) la stella e la spunta qui accanto.
                    Text(
                        player.nome,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Stella gialla per i preferiti (flag manuale, vedi EditPlayerDialog): richiesta
                    // esplicitamente come emoji, non icona Material, per distinguerla a colpo
                    // d'occhio dalla spunta "visionato" qui accanto.
                    if (player.star) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("⭐", style = MaterialTheme.typography.bodyMedium)
                    }
                    // Spunta per i giocatori visionati dal vivo dallo scout (flag manuale, vedi
                    // EditPlayerDialog): un segnale rapido, distinto dallo stato "Attivo" più sotto.
                    if (player.visionato) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = "Visionato dal vivo",
                            tint = ScoutGreen,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    TrendIcon(if (sport == Sport.BASKET) effTrend(player) else calcioTrend(player))
                }
                // Spiegazione testuale della stella (calcolata automaticamente in base al
                // rendimento del giocatore, vedi PlayerScoring): null se il giocatore non ha la
                // stella, quindi nessuna riga mostrata in quel caso.
                PlayerScoring.starReason(player)?.let { reason ->
                    Text(
                        reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                    if (showReviewHint) {
                        reviewHint(player)?.let { (label, color) ->
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(color),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = color,
                            )
                        }
                    }
                }
                // Le statistiche di carriera (presenze/punti/ruolo ecc.) per il basket si vedono
                // solo nel dettaglio giocatore (PlayerDetailScreen), non più qui nella card della
                // lista: qui restano solo per il calcio.
                if (sport == Sport.CALCIO) {
                    if (isPortiere(player.ruolo)) {
                        if (player.presenze > 0 || player.golSubiti > 0) {
                            val presenzePart = if (player.presenze > 0) "${player.presenze} presenze" else ""
                            val golSubitiPart = if (player.golSubiti > 0) "${player.golSubiti} gol subiti" else ""
                            Text(
                                listOf(presenzePart, golSubitiPart).filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (player.presenze > 0 || player.punteggio > 0 || player.assist > 0) {
                        val presenzePart = if (player.presenze > 0) "${player.presenze} presenze · " else ""
                        val difensoreCalcioPart = if (isDifensore(player.ruolo)) {
                            val voci = listOfNotNull(
                                "${player.tackle} tackle".takeIf { player.tackle > 0 },
                                "${player.golEvitati} gol evitati".takeIf { player.golEvitati > 0 },
                            )
                            if (voci.isEmpty()) "" else " · ${voci.joinToString(" · ")}"
                        } else ""
                        val centrocampistaCalcioPart = if (isCentrocampista(player.ruolo) && player.assist > 0) {
                            " · ${player.assist} assist"
                        } else ""
                        Text(
                            "$presenzePart${player.punteggio} gol$difensoreCalcioPart$centrocampistaCalcioPart",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (rank != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${rank.position}/${rank.total}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        rank.label,
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


/**
 * Basket: rendimento rispetto al minutaggio. Eff medio di carriera × 2 confrontato con i minuti
 * medi a partita (minuti di carriera / partite; i minuti totali non sono confrontabili con una
 * media). Maggiore -> 1 (freccia su verde), minore -> -1 (freccia giù rossa), uguale o dati
 * mancanti (Eff e minuti arrivano solo da Proballers/RealGM) -> 0 (trattino).
 */
internal fun effTrend(player: Player): Int {
    if (player.effMedio <= 0 || player.minutiCarriera <= 0 || player.presenze <= 0) return 0
    val minutiMedi = player.minutiCarriera.toDouble() / player.presenze
    val effDoppio = player.effMedio * 2.0
    return when {
        effDoppio > minutiMedi -> 1
        effDoppio < minutiMedi -> -1
        else -> 0
    }
}

/**
 * Gol subiti a partita fino a cui un portiere ha comunque la freccia su anche se i gol subiti
 * superano le presenze: 1,15 a partita (es. 500 gol subiti in 450 presenze = 1,11 -> su;
 * 540 in 450 = 1,2 -> giù). Una media vicina a 1 è normale anche per un buon portiere, oltre
 * 1,15 si è nella fascia dei portieri che incassano troppo.
 */
private const val PORTIERE_TOLLERANZA_GOL_A_PARTITA = 1.15

/**
 * Calcio: rendimento in gol rispetto alle presenze, con soglia diversa per reparto.
 * - Attaccante: gol > presenze / 2,5
 * - Centrocampista: gol > presenze / 5
 * - Difensore: gol > presenze / 10
 * - Portiere: gol subiti ≤ presenze × [PORTIERE_TOLLERANZA_GOL_A_PARTITA]
 * 1 = freccia su, -1 = freccia giù, 0 = trattino (ruolo non riconosciuto o nessuna presenza).
 */
internal fun calcioTrend(player: Player): Int {
    if (player.presenze <= 0) return 0
    val presenze = player.presenze.toDouble()
    val su = when (calcioRepartoOf(player.ruolo) ?: return 0) {
        CalcioReparto.ATTACCANTE -> player.punteggio > presenze / 2.5
        CalcioReparto.CENTROCAMPISTA -> player.punteggio > presenze / 5
        CalcioReparto.DIFENSORE -> player.punteggio > presenze / 10
        CalcioReparto.PORTIERE -> player.golSubiti <= presenze * PORTIERE_TOLLERANZA_GOL_A_PARTITA
    }
    return if (su) 1 else -1
}

private val TrendDownColor = Color(0xFFDC2626)

@Composable
private fun TrendIcon(trend: Int) {
    when (trend) {
        1 -> Icon(Icons.Default.ArrowUpward, contentDescription = "Rendimento in crescita", tint = ScoutGreen, modifier = Modifier.size(16.dp))
        -1 -> Icon(Icons.Default.ArrowDownward, contentDescription = "Rendimento in calo", tint = TrendDownColor, modifier = Modifier.size(16.dp))
        else -> Icon(Icons.Default.Remove, contentDescription = "Rendimento non valutabile", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
/** Entro questa soglia la revisione è "imminente": indicatore ambra invece che grigio. */
private const val REVIEW_HINT_DAYS = 7
private val ReviewSoonColor = Color(0xFFF59E0B)

/** Etichetta + colore dell'indicatore di revisione sulla card: rosso se il giocatore è già in
 *  "Revisione", ambra se ci entrerà entro [REVIEW_HINT_DAYS] giorni, grigio altrimenti (vedi
 *  [ReviewPrefs.nextReviewAt]); null solo per i non "Attivo", che non vengono mai segnalati.
 *  Sempre visibile per gli attivi: ogni "Genera"/"Aggiorna" azzera la revisione, quindi con la
 *  sola soglia dei 7 giorni dopo un import la lista restava senza alcun indicatore. */
@Composable
private fun reviewHint(player: Player): Pair<String, Color>? {
    if (player.needsReview) return "Da revisionare" to MaterialTheme.colorScheme.error
    val at = ReviewPrefs.nextReviewAt(player) ?: return null
    val days = ((at - System.currentTimeMillis() + DAY_MILLIS - 1) / DAY_MILLIS).toInt()
    return when {
        days > REVIEW_HINT_DAYS -> "Revisione tra $days gg" to MaterialTheme.colorScheme.onSurfaceVariant
        // Già scaduto ma non ancora in Revisione: aspetta che si liberi un posto nel blocco.
        days <= 0 -> "In coda per revisione" to ReviewSoonColor
        days == 1 -> "Revisione domani" to ReviewSoonColor
        else -> "Revisione tra $days gg" to ReviewSoonColor
    }
}
