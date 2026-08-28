package com.example.turbobrothers.data

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.turbobrothers.R
import com.example.turbobrothers.ui.theme.ChristianOrange
import com.example.turbobrothers.ui.theme.LelioBlue
import com.example.turbobrothers.ui.theme.SaverioGreen

data class GameCharacter(
    val id: String,
    val name: String,
    val ageLabel: String,
    val description: String,
    @DrawableRes val portraitRes: Int,
    @DrawableRes val runSpriteRes: Int,
    val runAspect: Float, // larghezza/altezza dello sprite di corsa originale
    val color: Color,
    // Piccoli bonus per dare un motivo di rigiocare scegliendo un fratello diverso
    val jumpBoost: Float = 1f,
    val extraLife: Int = 0,
    val coinMultiplier: Float = 1f
)

val TurboCharacters = listOf(
    GameCharacter(
        id = "lelio",
        name = "Lelio",
        ageLabel = "5 anni",
        description = "Energico e iperattivo, salta più in alto di tutti!",
        portraitRes = R.drawable.img_char_lelio,
        runSpriteRes = R.drawable.ic_run_lelio,
        runAspect = 140f / 190f,
        color = LelioBlue,
        jumpBoost = 1.15f
    ),
    GameCharacter(
        id = "saverio",
        name = "Saverio",
        ageLabel = "3 anni",
        description = "Robusto e ribelle, parte con un cuore in più!",
        portraitRes = R.drawable.img_char_saverio,
        runSpriteRes = R.drawable.ic_run_saverio,
        runAspect = 140f / 190f,
        color = SaverioGreen,
        extraLife = 1
    ),
    GameCharacter(
        id = "christian",
        name = "Christian",
        ageLabel = "6 mesi",
        description = "Ride sempre ed è fortunato: monete raddoppiate!",
        portraitRes = R.drawable.ic_run_christian,
        runSpriteRes = R.drawable.ic_run_christian,
        runAspect = 138f / 190f,
        color = ChristianOrange,
        coinMultiplier = 2f
    )
)
