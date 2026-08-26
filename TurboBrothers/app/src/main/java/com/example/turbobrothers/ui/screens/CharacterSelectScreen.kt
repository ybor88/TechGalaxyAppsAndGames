package com.example.turbobrothers.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.turbobrothers.data.GameCharacter
import com.example.turbobrothers.data.TurboCharacters
import com.example.turbobrothers.ui.components.OutlinedText
import com.example.turbobrothers.ui.theme.TurboNavy
import com.example.turbobrothers.ui.theme.TurboNavyLight

@Composable
fun CharacterSelectScreen(
    onBack: () -> Unit,
    onCharacterChosen: (GameCharacter) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(TurboNavy, TurboNavyLight)))
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(12.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedText(text = "Scegli il tuo Turbo Brother!", fontSize = 22.sp, color = Color.White)
            Spacer(modifier = Modifier.height(24.dp))
            TurboCharacters.forEach { character ->
                CharacterCard(character = character, onClick = { onCharacterChosen(character) })
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CharacterCard(character: GameCharacter, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(TurboNavyLight)
            .border(3.dp, character.color, RoundedCornerShape(24.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(character.color.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(character.portraitRes),
                contentDescription = character.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(80.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = character.name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = character.ageLabel,
                    fontSize = 15.sp,
                    color = character.color
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = character.description,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}
