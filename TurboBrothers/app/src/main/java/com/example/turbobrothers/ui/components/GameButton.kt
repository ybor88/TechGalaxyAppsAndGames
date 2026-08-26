package com.example.turbobrothers.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottone "da gioco": rilievo, bordo spesso e ombra offset, in stile cartoon
 * invece del classico bottone piatto da app.
 */
@Composable
fun GameButton(
    text: String,
    baseColor: Color,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    textColor: Color = Color.White,
    paddingHorizontal: androidx.compose.ui.unit.Dp = 40.dp,
    paddingVertical: androidx.compose.ui.unit.Dp = 18.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 22.sp,
    iconSize: androidx.compose.ui.unit.Dp = 28.dp,
    cornerRadius: androidx.compose.ui.unit.Dp = 28.dp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .shadow(elevation = 10.dp, shape = shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(lighten(baseColor, 0.25f), baseColor, darken(baseColor, 0.15f))
                )
            )
            .border(3.dp, darken(baseColor, 0.35f), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(horizontal = paddingHorizontal, vertical = paddingVertical)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (iconRes != null) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            OutlinedText(text = text, fontSize = fontSize, color = textColor)
        }
    }
}

/** Testo con un leggero contorno scuro, per leggibilità in stile cartoon su sfondi vari. */
@Composable
fun OutlinedText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    outlineColor: Color = Color(0xFF12253F),
    fontWeight: FontWeight = FontWeight.ExtraBold
) {
    Box {
        androidx.compose.material3.Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = outlineColor,
            modifier = Modifier.offset(x = 1.5.dp, y = 1.5.dp)
        )
        androidx.compose.material3.Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color
        )
    }
}

private fun lighten(color: Color, amount: Float): Color = Color(
    red = (color.red + (1f - color.red) * amount).coerceIn(0f, 1f),
    green = (color.green + (1f - color.green) * amount).coerceIn(0f, 1f),
    blue = (color.blue + (1f - color.blue) * amount).coerceIn(0f, 1f),
    alpha = color.alpha
)

private fun darken(color: Color, amount: Float): Color = Color(
    red = (color.red * (1f - amount)).coerceIn(0f, 1f),
    green = (color.green * (1f - amount)).coerceIn(0f, 1f),
    blue = (color.blue * (1f - amount)).coerceIn(0f, 1f),
    alpha = color.alpha
)
