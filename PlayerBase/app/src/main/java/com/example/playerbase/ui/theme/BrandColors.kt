package com.example.playerbase.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Colori del brand personalizzabili dall'utente nel pannello "Personalizza colori".
 * Sono mutableState: qualunque composable che li legge (anche indirettamente,
 * tramite funzioni non-@Composable come Sport.accentColor()) si ricompone da solo
 * quando l'utente cambia una scelta, senza dover ripassare il colore ovunque.
 */
object BrandColors {
    var navy by mutableStateOf(PlayerNavy)
    var gold by mutableStateOf(PlayerGold)
    var basket by mutableStateOf(BasketOrange)
    var calcio by mutableStateOf(FieldGreen)

    fun reset() {
        navy = PlayerNavy
        gold = PlayerGold
        basket = BasketOrange
        calcio = FieldGreen
    }
}

fun Color.lighten(amount: Float): Color = Color(
    red = (red + (1f - red) * amount).coerceIn(0f, 1f),
    green = (green + (1f - green) * amount).coerceIn(0f, 1f),
    blue = (blue + (1f - blue) * amount).coerceIn(0f, 1f),
    alpha = alpha
)

fun Color.darken(amount: Float): Color = Color(
    red = (red * (1f - amount)).coerceIn(0f, 1f),
    green = (green * (1f - amount)).coerceIn(0f, 1f),
    blue = (blue * (1f - amount)).coerceIn(0f, 1f),
    alpha = alpha
)
