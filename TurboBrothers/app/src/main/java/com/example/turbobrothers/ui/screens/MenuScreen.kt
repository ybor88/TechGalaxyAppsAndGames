package com.example.turbobrothers.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.turbobrothers.R
import com.example.turbobrothers.ui.components.GameButton
import com.example.turbobrothers.ui.components.OutlinedText
import com.example.turbobrothers.ui.theme.TurboGold
import com.example.turbobrothers.ui.theme.TurboNavy
import com.example.turbobrothers.ui.theme.TurboNavyLight
import com.example.turbobrothers.ui.theme.TurboOrange

@Composable
fun MenuScreen(highScore: Int, onPlayClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "menu_pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(TurboNavy, TurboNavyLight))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.img_logo_title),
                contentDescription = "I Tre Turbo Brothers",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(36.dp))
            GameButton(
                text = "GIOCA",
                baseColor = TurboGold,
                iconRes = R.drawable.ui_icon_play,
                textColor = TurboNavy,
                modifier = Modifier.scale(pulse),
                onClick = onPlayClick
            )
            Spacer(modifier = Modifier.height(28.dp))
            if (highScore > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_item_trophy),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedText(text = "Record: $highScore", fontSize = 18.sp, color = TurboOrange)
                }
            }
        }
    }
}
