package com.example.frigozero.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.frigozero.viewmodel.FrigoViewModel

private val difficultyOptions = listOf("Facile", "Media", "Difficile")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecipeScreen(
    viewModel: FrigoViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🍽️") }
    var description by remember { mutableStateOf("") }
    var ingredientsText by remember { mutableStateOf("") }
    var stepsText by remember { mutableStateOf("") }
    var cookTimeText by remember { mutableStateOf("30") }
    var difficulty by remember { mutableStateOf(difficultyOptions.first()) }
    var showError by remember { mutableStateOf(false) }

    val ingredients = remember(ingredientsText) {
        ingredientsText.split(",", "\n").map { it.trim() }.filter { it.isNotBlank() }
    }
    val steps = remember(stepsText) {
        stepsText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
    }
    val isValid = name.isNotBlank() && ingredients.isNotEmpty() && steps.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuova ricetta") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Questa ricetta verrà salvata nell'archivio dell'app e comparirà anche cercando senza connessione.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                )
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { if (it.length <= 2) emoji = it },
                        label = { Text("Emoji") },
                        modifier = Modifier.width(90.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nome ricetta *") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrizione") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
            }

            item {
                OutlinedTextField(
                    value = ingredientsText,
                    onValueChange = { ingredientsText = it },
                    label = { Text("Ingredienti *") },
                    placeholder = { Text("Es. pomodoro, pasta, aglio, basilico") },
                    supportingText = { Text("Separa gli ingredienti con una virgola") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }

            item {
                OutlinedTextField(
                    value = stepsText,
                    onValueChange = { stepsText = it },
                    label = { Text("Preparazione *") },
                    placeholder = { Text("Un passaggio per riga") },
                    supportingText = { Text("Scrivi ogni passaggio su una riga separata") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 10
                )
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = cookTimeText,
                        onValueChange = { value -> if (value.all(Char::isDigit)) cookTimeText = value },
                        label = { Text("Minuti") },
                        modifier = Modifier.width(120.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Column {
                        Text(
                            "Difficoltà",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            difficultyOptions.forEach { option ->
                                FilterChip(
                                    selected = difficulty == option,
                                    onClick = { difficulty = option },
                                    label = { Text(option) }
                                )
                            }
                        }
                    }
                }
            }

            if (showError && !isValid) {
                item {
                    Text(
                        "Compila almeno nome, ingredienti e preparazione.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        if (!isValid) {
                            showError = true
                            return@Button
                        }
                        viewModel.addUserRecipe(
                            name = name,
                            description = description,
                            ingredients = ingredients,
                            steps = steps,
                            cookTimeMinutes = cookTimeText.toIntOrNull() ?: 30,
                            difficulty = difficulty,
                            emoji = emoji
                        )
                        onSaved()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Salva ricetta", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
