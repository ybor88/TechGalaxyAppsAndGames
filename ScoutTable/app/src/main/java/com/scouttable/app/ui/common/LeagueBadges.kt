// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scouttable.app.data.Sport

/**
 * Sigle degli organismi di riferimento di ogni sport, mostrate in piccolo sui campi della
 * formazione migliore. Solo testo: i loghi ufficiali (NBA, FIFA, NFL, UFC, WWE…) sono marchi
 * registrati e non si possono usare in un'app pubblicata senza licenza.
 */
fun leagueBadges(sport: Sport): List<String> = when (sport) {
    Sport.BASKET, Sport.BASKET_REGIONALE -> listOf("NBA", "FIBA")
    Sport.CALCIO -> listOf("FIFA")
    Sport.HOCKEY -> listOf("NHL")
    Sport.FOOTBALL -> listOf("NFL")
    Sport.RUGBY -> listOf("World Rugby")
    Sport.MMA -> listOf("UFC")
    Sport.WRESTLING -> listOf("WWE")
    // Pugilato: le quattro sigle mondiali principali; kickboxing: la federazione mondiale
    // riconosciuta dal CIO; sumo: la Japan Sumo Association.
    Sport.PUGILATO -> listOf("WBC", "WBA", "IBF", "WBO")
    Sport.KICKBOXING -> listOf("WAKO")
    Sport.SUMO -> listOf("JSA")
    Sport.FORMULA1 -> listOf("FIA")
    Sport.MOTOGP -> listOf("FIM")
}

/** Piccole etichette con le sigle di [leagueBadges], da sovrapporre a un angolo del campo. */
@Composable
fun LeagueBadges(sport: Sport, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        leagueBadges(sport).forEach { name ->
            Text(
                name,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC0B1220))
                    .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
