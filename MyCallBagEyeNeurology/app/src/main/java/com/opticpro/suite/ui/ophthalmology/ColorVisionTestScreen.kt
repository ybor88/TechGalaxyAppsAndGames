package com.opticpro.suite.ui.ophthalmology

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Tavole pseudoisocromatiche generate proceduralmente (NON riproduzioni delle tavole
 * Ishihara originali, protette da copyright). Utile come screening di massima,
 * non sostituisce un test Ishihara/D-15 certificato.
 */
private data class Plate(val figureDigit: Int, val figureColor: Color, val bgColor: Color)

private val plates = listOf(
    Plate(12, Color(0xFFE07A5F), Color(0xFF8AA29E)),
    Plate(8, Color(0xFFD62828), Color(0xFF606C38)),
    Plate(29, Color(0xFFEE964B), Color(0xFF3D405B)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorVisionTestScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Test colori (pseudoisocromatico)") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            items(plates) { plate ->
                Text("Numero visibile? (annota risposta paziente)", style = MaterialTheme.typography.bodySmall)
                Canvas(modifier = Modifier.fillMaxWidth().height(220.dp).padding(bottom = 24.dp)) {
                    val rnd = Random(plate.figureDigit)
                    repeat(1200) {
                        val x = rnd.nextFloat() * size.width
                        val y = rnd.nextFloat() * size.height
                        val r = 4f + rnd.nextFloat() * 6f
                        drawCircle(plate.bgColor.copy(alpha = 0.9f), r, Offset(x, y))
                    }
                    // Punti che disegnano approssimativamente il numero (blocco centrale colorato diverso)
                    repeat(500) {
                        val x = size.width * (0.3f + rnd.nextFloat() * 0.4f)
                        val y = size.height * (0.3f + rnd.nextFloat() * 0.4f)
                        val r = 4f + rnd.nextFloat() * 6f
                        drawCircle(plate.figureColor.copy(alpha = 0.9f), r, Offset(x, y))
                    }
                }
            }
        }
    }
}
