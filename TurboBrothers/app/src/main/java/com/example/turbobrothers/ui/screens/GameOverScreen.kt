package com.example.turbobrothers.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.turbobrothers.R
import com.example.turbobrothers.ui.components.GameButton
import com.example.turbobrothers.ui.components.OutlinedText
import com.example.turbobrothers.ui.theme.SaverioGreen
import com.example.turbobrothers.ui.theme.TurboGold
import com.example.turbobrothers.ui.theme.TurboNavy
import com.example.turbobrothers.ui.theme.TurboNavyLight
import com.example.turbobrothers.viewmodel.GameViewModel

@Composable
fun GameOverScreen(
    viewModel: GameViewModel,
    onRetry: () -> Unit,
    onMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(TurboNavy, TurboNavyLight))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Image(
                painter = painterResource(viewModel.selectedCharacter.portraitRes),
                contentDescription = viewModel.selectedCharacter.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(140.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedText(text = "Bravo ${viewModel.selectedCharacter.name}!", fontSize = 24.sp, color = Color.White)
            Spacer(modifier = Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_item_star),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedText(text = "${viewModel.score}", fontSize = 38.sp, color = TurboGold)
            }
            if (viewModel.isNewHighScore) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedText(text = "🎉 NUOVO RECORD! 🎉", fontSize = 18.sp, color = Color.White)
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedText(text = "Record: ${viewModel.highScore}", fontSize = 16.sp, color = Color.White.copy(alpha = 0.85f))
            }
            Spacer(modifier = Modifier.height(40.dp))
            GameButton(
                text = "RIGIOCA",
                baseColor = TurboGold,
                iconRes = R.drawable.ui_icon_play,
                textColor = TurboNavy,
                onClick = onRetry
            )
            Spacer(modifier = Modifier.height(16.dp))
            GameButton(
                text = "MENU",
                baseColor = SaverioGreen,
                iconRes = R.drawable.ui_icon_home,
                textColor = Color.White,
                onClick = onMenu
            )
        }
    }
}
