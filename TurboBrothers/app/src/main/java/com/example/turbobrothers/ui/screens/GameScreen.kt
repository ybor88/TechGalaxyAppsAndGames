package com.example.turbobrothers.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.turbobrothers.R
import com.example.turbobrothers.data.SceneKind
import com.example.turbobrothers.data.SceneThemes
import com.example.turbobrothers.ui.components.BeachBackground
import com.example.turbobrothers.ui.components.DesertBackground
import com.example.turbobrothers.ui.components.ForestBackground
import com.example.turbobrothers.ui.components.GameButton
import com.example.turbobrothers.ui.components.MountainBackground
import com.example.turbobrothers.ui.components.NaplesBackground
import com.example.turbobrothers.ui.components.NewYorkBackground
import com.example.turbobrothers.ui.components.OutlinedText
import com.example.turbobrothers.ui.components.SavianoBackground
import com.example.turbobrothers.ui.components.VollaBackground
import com.example.turbobrothers.ui.theme.SaverioGreen
import com.example.turbobrothers.ui.theme.TurboGold
import com.example.turbobrothers.viewmodel.GameViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val GROUND_MARGIN_DP = 40f
private const val PLAYER_X_DP = 56f
private const val PLAYER_HEIGHT_DP = 72f
private const val GAME_OVER_BANNER_MS = 1300L

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onGameOver: () -> Unit,
    onExitToMenu: () -> Unit
) {
    // Come per il livello superato, il game over viene prima annunciato con un
    // banner ben visibile sopra la scena, e solo dopo si passa alla schermata
    // finale: senza questa pausa il cambio schermo è troppo brusco.
    LaunchedEffect(viewModel.isGameOver) {
        if (viewModel.isGameOver) {
            delay(GAME_OVER_BANNER_MS)
            onGameOver()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportWidthDp = maxWidth.value
        val theme = SceneThemes[viewModel.sceneIndex]
        val character = viewModel.selectedCharacter
        val playerWidthDp = PLAYER_HEIGHT_DP * character.runAspect

        LaunchedEffect(Unit) {
            var lastFrameTime = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { frameTime ->
                    val dt = (frameTime - lastFrameTime) / 1_000_000_000f
                    lastFrameTime = frameTime
                    viewModel.update(dt, viewportWidthDp)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { viewModel.jump() })
                }
        ) {
            // Sfondo scenico disegnato a vettori: sempre nitido, nessuna foto sgranata
            when (theme.kind) {
                SceneKind.NEW_YORK -> NewYorkBackground(modifier = Modifier.fillMaxSize())
                SceneKind.FOREST -> ForestBackground(modifier = Modifier.fillMaxSize())
                SceneKind.BEACH -> BeachBackground(modifier = Modifier.fillMaxSize())
                SceneKind.NAPLES -> NaplesBackground(modifier = Modifier.fillMaxSize())
                SceneKind.VOLLA -> VollaBackground(modifier = Modifier.fillMaxSize())
                SceneKind.SAVIANO -> SavianoBackground(modifier = Modifier.fillMaxSize())
                SceneKind.MOUNTAIN -> MountainBackground(modifier = Modifier.fillMaxSize())
                SceneKind.DESERT -> DesertBackground(modifier = Modifier.fillMaxSize())
            }
            // Velo scuro in alto per far risaltare cuori/punteggio sopra un cielo chiaro
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .height(150.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent)))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height((GROUND_MARGIN_DP + 24f).dp)
                    .background(theme.groundColor.copy(alpha = 0.55f))
            )

            // Personaggio
            val glowColor = when {
                viewModel.isShielded -> Color(0xFF5CC8FF)
                viewModel.isFlying -> Color(0xFFFF8F1F)
                viewModel.isLightning -> Color(0xFFFFC93C)
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(
                        x = (PLAYER_X_DP - 14f).dp,
                        y = -(viewModel.playerY + GROUND_MARGIN_DP - 14f).dp
                    )
                    .size((playerWidthDp + 28f).dp)
                    .clip(CircleShape)
                    .background(glowColor.copy(alpha = if (glowColor == Color.Transparent) 0f else 0.4f))
            )
            Image(
                painter = painterResource(character.runSpriteRes),
                contentDescription = character.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = PLAYER_X_DP.dp, y = -(viewModel.playerY + GROUND_MARGIN_DP).dp)
                    .width(playerWidthDp.dp)
                    .height(PLAYER_HEIGHT_DP.dp)
            )

            // Oggetti in scena
            run {
                @Suppress("UNUSED_EXPRESSION")
                viewModel.frameTick // forza la ricomposizione ad ogni frame
                viewModel.entities.forEach { entity ->
                    Image(
                        painter = painterResource(entity.spriteRes),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = entity.x.dp, y = -(entity.y + GROUND_MARGIN_DP).dp)
                            .width(entity.widthDp.dp)
                            .height(entity.heightDp.dp)
                    )
                }
            }

            // HUD
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(viewModel.maxLives) { index ->
                            Image(
                                painter = painterResource(R.drawable.ic_ui_heart),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(30.dp)
                                    .alpha(if (index < viewModel.lives) 1f else 0.25f)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_ui_item_star),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedText(text = "${viewModel.score}", fontSize = 22.sp, color = Color.White)
                        Spacer(modifier = Modifier.width(14.dp))
                        Image(
                            painter = painterResource(
                                if (viewModel.isPaused) R.drawable.ic_ui_play else R.drawable.ic_ui_pause
                            ),
                            contentDescription = "Pausa",
                            modifier = Modifier
                                .size(38.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { viewModel.togglePause() }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    LevelBadge(level = viewModel.level)
                }
                if (viewModel.isShielded || viewModel.isFlying || viewModel.isLightning) {
                    Row(modifier = Modifier.padding(top = 6.dp)) {
                        if (viewModel.isShielded) PowerBadge(R.drawable.ic_ui_powerup_shield)
                        if (viewModel.isFlying) PowerBadge(R.drawable.ic_powerup_rocket)
                        if (viewModel.isLightning) PowerBadge(R.drawable.ic_ui_powerup_lightning)
                    }
                }
            }

            // Bottone SALTA colorato: i bimbi piccoli possono premerlo
            // direttamente invece di dover toccare un punto qualsiasi dello schermo.
            if (!viewModel.isPaused) {
                GameButton(
                    text = "SALTA",
                    baseColor = Color(0xFFFF5C7A),
                    textColor = Color.White,
                    paddingHorizontal = 20.dp,
                    paddingVertical = 10.dp,
                    fontSize = 15.sp,
                    cornerRadius = 18.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = (GROUND_MARGIN_DP + 30f).dp),
                    onClick = { viewModel.jump() }
                )
            }

            AnimatedVisibility(
                visible = viewModel.showLevelUpBanner,
                enter = scaleIn(
                    initialScale = 0.5f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                ) + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                LevelUpBanner(level = viewModel.levelUpBannerNumber)
            }

            AnimatedVisibility(
                visible = viewModel.isGameOver,
                enter = scaleIn(
                    initialScale = 0.5f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                ) + fadeIn(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                GameOverBanner()
            }

            if (viewModel.isPaused) {
                PauseOverlay(
                    onResume = { viewModel.togglePause() },
                    onMenu = onExitToMenu
                )
            }
        }
    }
}

@Composable
private fun LevelBadge(level: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        OutlinedText(text = "LIVELLO $level", fontSize = 15.sp, color = TurboGold)
    }
}

@Composable
private fun LevelUpBanner(level: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFFFFD873), TurboGold))
            )
            .padding(horizontal = 32.dp, vertical = 20.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_ui_item_star),
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedText(text = "OTTIMO! BRAVO!", fontSize = 24.sp, color = Color.White)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedText(text = "Livello $level!", fontSize = 20.sp, color = Color(0xFF0D1B2E))
    }
}

@Composable
private fun GameOverBanner() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFFFF8A8A), Color(0xFFD32F2F)))
            )
            .padding(horizontal = 32.dp, vertical = 20.dp)
    ) {
        OutlinedText(text = "GAME OVER", fontSize = 28.sp, color = Color.White)
    }
}

@Composable
private fun PowerBadge(iconRes: Int) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = Modifier
            .size(30.dp)
            .padding(end = 4.dp)
    )
}

@Composable
private fun PauseOverlay(onResume: () -> Unit, onMenu: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedText(text = "PAUSA", fontSize = 30.sp, color = Color.White)
            Spacer(modifier = Modifier.height(24.dp))
            GameButton(
                text = "RIPRENDI",
                baseColor = SaverioGreen,
                iconRes = R.drawable.ic_ui_play,
                onClick = onResume
            )
            Spacer(modifier = Modifier.height(16.dp))
            GameButton(
                text = "MENU",
                baseColor = TurboGold,
                iconRes = R.drawable.ic_ui_home,
                textColor = Color(0xFF0D1B2E),
                onClick = onMenu
            )
        }
    }
}
