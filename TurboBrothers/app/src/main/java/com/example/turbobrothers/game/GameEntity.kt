package com.example.turbobrothers.game

import androidx.annotation.DrawableRes
import com.example.turbobrothers.data.EntityKind
import com.example.turbobrothers.data.PowerType

/**
 * Un oggetto attivo nella corsia di gioco. `x`/`y` sono in dp e vengono mutati
 * ogni frame dal game loop; sono letti dai composable solo quando `frameTick`
 * cambia, quindi non serve che siano Compose State individuali.
 */
class GameEntity(
    val id: Long,
    val kind: EntityKind,
    @DrawableRes val spriteRes: Int,
    val points: Int,
    val power: PowerType,
    var x: Float,
    val y: Float,
    val heightDp: Float,
    val widthDp: Float,
    val speedMultiplier: Float = 1f
)
