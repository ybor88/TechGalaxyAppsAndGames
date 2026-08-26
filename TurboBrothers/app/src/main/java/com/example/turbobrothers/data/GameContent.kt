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
    val aspect: Float = 1f, // larghezza/altezza dello sprite originale, per non deformarlo
    val speedMultiplier: Float = 1f // <1 = più lento della velocità base di scorrimento
)

// Ostacoli e nemici uccidono al contatto: leggermente più lenti della corsia
// per dare più tempo di reazione ai bambini piccoli.
private const val DEADLY_SPEED_MULTIPLIER = 0.88f

val Obstacles = listOf(
    SpawnDef(EntityKind.OBSTACLE, R.drawable.ic_obstacle_crate, aspect = 100f / 160f, speedMultiplier = DEADLY_SPEED_MULTIPLIER),
    SpawnDef(EntityKind.OBSTACLE, R.drawable.ic_obstacle_wall, aspect = 105f / 160f, speedMultiplier = DEADLY_SPEED_MULTIPLIER),
    SpawnDef(EntityKind.OBSTACLE, R.drawable.ic_obstacle_spikes, aspect = 105f / 160f, speedMultiplier = DEADLY_SPEED_MULTIPLIER),
    SpawnDef(EntityKind.OBSTACLE, R.drawable.ic_obstacle_barrel, aspect = 108f / 160f, speedMultiplier = DEADLY_SPEED_MULTIPLIER)
)

val Enemies = listOf(
    SpawnDef(EntityKind.ENEMY, R.drawable.ic_enemy_monster, aspect = 130f / 190f, speedMultiplier = DEADLY_SPEED_MULTIPLIER),
    SpawnDef(EntityKind.ENEMY, R.drawable.ic_enemy_bomb, aspect = 140f / 190f, speedMultiplier = DEADLY_SPEED_MULTIPLIER),
    SpawnDef(EntityKind.ENEMY, R.drawable.ic_enemy_robot, aspect = 148f / 190f, speedMultiplier = DEADLY_SPEED_MULTIPLIER)
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

// Tutti gli sfondi sono disegnati a vettori (vedi SceneBackgrounds.kt): restano
// nitidi a qualsiasi risoluzione, a differenza di un ritaglio fotografico ingrandito.
enum class SceneKind { NEW_YORK, FOREST, BEACH, NAPLES, VOLLA, SAVIANO, MOUNTAIN, DESERT }

data class SceneTheme(
    val name: String,
    val kind: SceneKind,
    val groundColor: androidx.compose.ui.graphics.Color,
    @androidx.annotation.RawRes val musicRes: Int
)

val SceneThemes = listOf(
    SceneTheme("New York", SceneKind.NEW_YORK, androidx.compose.ui.graphics.Color(0xFF15121F), R.raw.music_newyork),
    SceneTheme("Foresta", SceneKind.FOREST, androidx.compose.ui.graphics.Color(0xFF3F8F52), R.raw.music_forest),
    SceneTheme("Spiaggia", SceneKind.BEACH, androidx.compose.ui.graphics.Color(0xFFEFD9A0), R.raw.music_beach),
    SceneTheme("Napoli", SceneKind.NAPLES, androidx.compose.ui.graphics.Color(0xFF2D6FA0), R.raw.music_naples),
    SceneTheme("Volla", SceneKind.VOLLA, androidx.compose.ui.graphics.Color(0xFFB08F5C), R.raw.music_volla),
    SceneTheme("Saviano", SceneKind.SAVIANO, androidx.compose.ui.graphics.Color(0xFF5C8F4A), R.raw.music_saviano),
    // Riusano musiche già esistenti: nessun nuovo file audio necessario.
    SceneTheme("Montagna", SceneKind.MOUNTAIN, androidx.compose.ui.graphics.Color(0xFFD8E4EE), R.raw.music_forest),
    SceneTheme("Deserto", SceneKind.DESERT, androidx.compose.ui.graphics.Color(0xFFD9A066), R.raw.music_beach)
)
