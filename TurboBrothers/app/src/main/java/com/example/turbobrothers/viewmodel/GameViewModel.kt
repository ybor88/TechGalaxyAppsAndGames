package com.example.turbobrothers.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.turbobrothers.audio.Sfx
import com.example.turbobrothers.audio.SoundManager
import com.example.turbobrothers.data.Collectibles
import com.example.turbobrothers.data.EntityKind
import com.example.turbobrothers.data.Enemies
import com.example.turbobrothers.data.GameCharacter
import com.example.turbobrothers.data.HighScoreStore
import com.example.turbobrothers.data.Obstacles
import com.example.turbobrothers.data.PowerType
import com.example.turbobrothers.data.PowerUps
import com.example.turbobrothers.data.SceneThemes
import com.example.turbobrothers.data.TurboCharacters
import com.example.turbobrothers.game.GameEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.random.Random

// Costanti di gioco, tutte in dp / dp-al-secondo: i composable possono
// applicarle direttamente con Modifier.offset(x.dp, y.dp) senza conversioni.
private const val GRAVITY = 1400f
private const val JUMP_VELOCITY = 620f
private const val BASE_SPEED = 220f
private const val MAX_SPEED = 380f
private const val PLAYER_X = 56f
private const val PLAYER_SIZE = 64f
private const val ENTITY_HEIGHT = 58f
private const val AIR_ITEM_HEIGHT = 105f
private const val HIT_PADDING = 10f // hitbox più permissiva per bambini piccoli
private const val HIT_COOLDOWN_MS = 1200L
private const val SHIELD_MS = 5000L
private const val LIGHTNING_MS = 6000L
private const val ROCKET_MS = 4000L
private const val MAX_LIVES_BASE = 4

class GameViewModel : ViewModel() {

    var selectedCharacter by mutableStateOf(TurboCharacters.first())
        private set

    var lives by mutableIntStateOf(MAX_LIVES_BASE)
        private set
    var maxLives by mutableIntStateOf(MAX_LIVES_BASE)
        private set
    var score by mutableIntStateOf(0)
        private set
    var highScore by mutableIntStateOf(0)
        private set
    var isNewHighScore by mutableStateOf(false)
        private set
    var isPaused by mutableStateOf(false)
        private set
    var isGameOver by mutableStateOf(false)
        private set
    var sceneIndex by mutableIntStateOf(0)
        private set

    var playerY by mutableFloatStateOf(0f)
        private set

    /** Incrementato ad ogni frame: forza la ricomposizione degli entity renderer. */
    var frameTick by mutableLongStateOf(0L)
        private set

    val entities = mutableStateListOf<GameEntity>()

    private var velocityY = 0f
    private var speed = BASE_SPEED
    private var spawnTimer = 1f
    private var scoreAccumulator = 0f
    private var nextEntityId = 0L
    private var hitCooldownUntil = 0L
    private var shieldUntil = 0L
    private var lightningUntil = 0L
    private var rocketUntil = 0L
    private var lastMusicSceneIndex = -1

    val isShielded: Boolean get() = System.currentTimeMillis() < shieldUntil
    val isLightning: Boolean get() = System.currentTimeMillis() < lightningUntil
    val isFlying: Boolean get() = System.currentTimeMillis() < rocketUntil

    fun loadHighScore() {
        highScore = HighScoreStore.getHighScore()
    }

    fun selectCharacter(character: GameCharacter) {
        selectedCharacter = character
    }

    fun startGame() {
        maxLives = MAX_LIVES_BASE + selectedCharacter.extraLife
        lives = maxLives
        score = 0
        sceneIndex = 0
        entities.clear()
        playerY = 0f
        velocityY = 0f
        speed = BASE_SPEED
        spawnTimer = 1f
        scoreAccumulator = 0f
        hitCooldownUntil = 0L
        shieldUntil = 0L
        lightningUntil = 0L
        rocketUntil = 0L
        isPaused = false
        isGameOver = false
        isNewHighScore = false
        frameTick = 0L
        lastMusicSceneIndex = 0
        SoundManager.playMusic(SceneThemes[0].musicRes)
    }

    fun jump() {
        if (isGameOver || isPaused) return
        if (playerY <= 0.01f && !isFlying) {
            velocityY = JUMP_VELOCITY * selectedCharacter.jumpBoost
            SoundManager.playSfx(Sfx.JUMP)
        }
    }

    fun togglePause() {
        if (isGameOver) return
        isPaused = !isPaused
        SoundManager.playSfx(Sfx.BUTTON)
        if (isPaused) SoundManager.pauseMusic() else SoundManager.resumeMusic()
    }

    fun update(dt: Float, viewportWidthDp: Float) {
        if (isPaused || isGameOver) return
        val clampedDt = min(dt, 0.05f) // evita salti fisici se il frame è troppo lungo
        val now = System.currentTimeMillis()

        // Fisica del salto (il razzo fa librare il personaggio senza gravità)
        if (isFlying) {
            val targetY = AIR_ITEM_HEIGHT
            playerY += (targetY - playerY) * min(1f, clampedDt * 6f)
            velocityY = 0f
        } else {
            velocityY -= GRAVITY * clampedDt
            playerY += velocityY * clampedDt
            if (playerY <= 0f) {
                playerY = 0f
                velocityY = 0f
            }
        }

        // Movimento degli oggetti in scena
        val iterator = entities.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            e.x -= speed * clampedDt
            if (e.x < -80f) {
                iterator.remove()
            }
        }

        // Spawn di nuovi oggetti
        spawnTimer -= clampedDt
        if (spawnTimer <= 0f) {
            spawnEntity(viewportWidthDp)
            val minInterval = 0.65f
            val maxInterval = 1.5f
            val difficultyEase = min(score / 400f, 1f)
            spawnTimer = (maxInterval - (maxInterval - minInterval) * difficultyEase) +
                Random.nextFloat() * 0.4f
        }

        // Collisioni
        checkCollisions(now)

        // Punteggio continuo (distanza percorsa)
        scoreAccumulator += speed * clampedDt * 0.04f
        if (scoreAccumulator >= 1f) {
            val gained = scoreAccumulator.toInt()
            score += gained
            scoreAccumulator -= gained
        }

        speed = min(MAX_SPEED, BASE_SPEED + score * 0.12f)
        sceneIndex = (score / 200) % SceneThemes.size
        if (sceneIndex != lastMusicSceneIndex) {
            lastMusicSceneIndex = sceneIndex
            SoundManager.playMusic(SceneThemes[sceneIndex].musicRes)
        }

        frameTick++

        if (lives <= 0) {
            endGame()
        }
    }

    private fun spawnEntity(viewportWidthDp: Float) {
        val roll = Random.nextFloat()
        val def = when {
            roll < 0.40f -> Obstacles.random()
            roll < 0.62f -> Enemies.random()
            roll < 0.90f -> Collectibles.random()
            else -> PowerUps.random()
        }
        val y = if (def.onGround) 0f else AIR_ITEM_HEIGHT
        val width = ENTITY_HEIGHT * def.aspect
        entities.add(
            GameEntity(
                id = nextEntityId++,
                kind = def.kind,
                spriteRes = def.spriteRes,
                points = def.points,
                power = def.power,
                x = viewportWidthDp + width,
                y = y,
                heightDp = ENTITY_HEIGHT,
                widthDp = width
            )
        )
    }

    private fun checkCollisions(now: Long) {
        val playerLeft = PLAYER_X + HIT_PADDING
        val playerRight = PLAYER_X + PLAYER_SIZE - HIT_PADDING
        val playerBottom = playerY
        val playerTop = playerY + PLAYER_SIZE - HIT_PADDING * 2

        val iterator = entities.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            val eLeft = e.x + HIT_PADDING
            val eRight = e.x + e.widthDp - HIT_PADDING
            val eBottom = e.y
            val eTop = e.y + e.heightDp - HIT_PADDING * 2

            val overlapX = playerLeft < eRight && playerRight > eLeft
            val overlapY = playerBottom < eTop && playerTop > eBottom
            if (!(overlapX && overlapY)) continue

            when (e.kind) {
                EntityKind.COLLECTIBLE -> {
                    val multiplier = (if (isLightning) 2 else 1) * selectedCharacter.coinMultiplier
                    score += (e.points * multiplier).toInt()
                    SoundManager.playSfx(Sfx.COLLECT)
                    iterator.remove()
                }

                EntityKind.POWERUP -> {
                    when (e.power) {
                        PowerType.HEART -> lives = min(lives + 1, maxLives)
                        PowerType.SHIELD -> shieldUntil = now + SHIELD_MS
                        PowerType.LIGHTNING -> lightningUntil = now + LIGHTNING_MS
                        PowerType.ROCKET -> rocketUntil = now + ROCKET_MS
                        PowerType.NONE -> Unit
                    }
                    SoundManager.playSfx(Sfx.POWERUP)
                    iterator.remove()
                }

                EntityKind.OBSTACLE, EntityKind.ENEMY -> {
                    if (isShielded || isFlying) {
                        iterator.remove()
                        continue
                    }
                    if (now >= hitCooldownUntil) {
                        lives -= 1
                        hitCooldownUntil = now + HIT_COOLDOWN_MS
                        SoundManager.playSfx(Sfx.HIT)
                        iterator.remove()
                    }
                }
            }
        }
    }

    private fun endGame() {
        isGameOver = true
        isPaused = false
        SoundManager.stopMusic()
        SoundManager.playSfx(Sfx.GAME_OVER)
        isNewHighScore = HighScoreStore.saveIfHigher(score)
        highScore = HighScoreStore.getHighScore()
        if (isNewHighScore) {
            viewModelScope.launch {
                delay(700)
                SoundManager.playSfx(Sfx.NEW_RECORD)
            }
        }
    }
}
