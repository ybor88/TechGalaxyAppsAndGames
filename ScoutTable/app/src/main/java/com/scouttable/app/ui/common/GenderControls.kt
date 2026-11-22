// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.common

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.athlete.isFemale
import com.scouttable.app.data.rememberPlayerRepository
import kotlinx.coroutines.launch

/** Solo i giocatori del settore scelto (maschile o femminile): classifiche, miglior club e andamento separati. */
fun List<Player>.ofGender(female: Boolean): List<Player> = filter { it.isFemale == female }

/** Schede "Maschile" / "Femminile" in cima alle schermate di classifica, miglior club, andamento e campo. */
@Composable
fun GenderTabs(female: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        FilterChip(selected = !female, onClick = { onChange(false) }, label = { Text("♂ Maschile") })
        Spacer(modifier = Modifier.width(8.dp))
        FilterChip(selected = female, onClick = { onChange(true) }, label = { Text("♀ Femminile") })
    }
}

/** "Sposta a femmina" / "Sposta a maschio" sulla card: cambia settore al giocatore già salvato. */
@Composable
fun MoveGenderButton(player: Player, modifier: Modifier = Modifier) {
    val repository = rememberPlayerRepository()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toFemale = !player.isFemale
    val label = if (toFemale) "Sposta a femmina" else "Sposta a maschio"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable {
                scope.launch {
                    repository.setFemale(player, toFemale)
                    Toast.makeText(
                        context,
                        "${player.nome} spostato nel settore ${if (toFemale) "femminile" else "maschile"}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            .padding(vertical = 4.dp),
    ) {
        Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            label + if (toFemale) " ♀" else " ♂",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
