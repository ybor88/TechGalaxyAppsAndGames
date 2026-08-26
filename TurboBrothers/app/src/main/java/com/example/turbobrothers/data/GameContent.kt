package com.example.turbobrothers.data

import androidx.annotation.DrawableRes
import com.example.turbobrothers.R

enum class EntityKind { OBSTACLE, ENEMY, COLLECTIBLE, POWERUP }

enum class PowerType { NONE, HEART, SHIELD, LIGHTNING, ROCKET }

/** Modello di uno "spawnabile": ostacolo, nemico, oggetto o power-up. */
data class SpawnDef(
    val kind: EntityKind,
    @DrawableRes val spriteRes: Int,
    val points: Int = 0,
    val power: PowerType = PowerType.NONE,
    val onGround: Boolean = true,
    val aspect: Float = 1f // larghezza/altezza dello sprite originale, per non deformarlo
)

val Obstacles = listOf(
    SpawnDef(EntityKind.OBSTACLE, R.drawable.ic_obstacle_crate, aspect = 100f / 160f),
    SpawnDef(EntityKind.OBSTACLE, R.drawable.ic_obstacle_wall, aspect = 105f / 160f),
    SpawnDef(EntityKind.OBSTACLE, R.drawable.ic_obstacle_spikes, aspect = 105f / 160f),
    SpawnDef(EntityKind.OBSTACLE, R.drawable.ic_obstacle_barrel, aspect = 108f / 160f)
)

val Enemies = listOf(
    SpawnDef(EntityKind.ENEMY, R.drawable.ic_enemy_monster, aspect = 130f / 190f),
    SpawnDef(EntityKind.ENEMY, R.drawable.ic_enemy_bomb, aspect = 140f / 190f),
    SpawnDef(EntityKind.ENEMY, R.drawable.ic_enemy_robot, aspect = 148f / 190f)
)

val Collectibles = listOf(
    SpawnDef(EntityKind.COLLECTIBLE, R.drawable.ic_item_star, points = 10, onGround = false, aspect = 100f / 160f),
    SpawnDef(EntityKind.COLLECTIBLE, R.drawable.ic_item_diamond, points = 25, onGround = false, aspect = 105f / 160f),
    SpawnDef(EntityKind.COLLECTIBLE, R.drawable.ic_item_trophy, points = 50, onGround = false, aspect = 105f / 160f),
    SpawnDef(EntityKind.COLLECTIBLE, R.drawable.ic_item_puzzle, points = 15, onGround = false, aspect = 108f / 160f)
)

val PowerUps = listOf(
    SpawnDef(EntityKind.POWERUP, R.drawable.ic_powerup_lightning, power = PowerType.LIGHTNING, onGround = false, aspect = 95f / 190f),
    SpawnDef(EntityKind.POWERUP, R.drawable.ic_powerup_shield, power = PowerType.SHIELD, onGround = false, aspect = 105f / 190f),
    SpawnDef(EntityKind.POWERUP, R.drawable.ic_powerup_heart, power = PowerType.HEART, onGround = false, aspect = 100f / 190f),
    SpawnDef(EntityKind.POWERUP, R.drawable.ic_powerup_rocket, power = PowerType.ROCKET, onGround = false, aspect = 118f / 190f)
)

enum class SceneKind { IMAGE, NAPLES }

data class SceneTheme(
    val name: String,
    @DrawableRes val backgroundRes: Int?,
    val groundColor: androidx.compose.ui.graphics.Color,
    val kind: SceneKind = SceneKind.IMAGE
)

val SceneThemes = listOf(
    SceneTheme("New York", R.drawable.bg_scene_city, androidx.compose.ui.graphics.Color(0xFF2B2B44)),
    SceneTheme("Foresta", R.drawable.bg_scene_forest, androidx.compose.ui.graphics.Color(0xFF1B4B36)),
    SceneTheme("Spiaggia", R.drawable.bg_scene_beach, androidx.compose.ui.graphics.Color(0xFFE8C77E)),
    SceneTheme("Napoli", null, androidx.compose.ui.graphics.Color(0xFF2D6FA0), kind = SceneKind.NAPLES)
)
