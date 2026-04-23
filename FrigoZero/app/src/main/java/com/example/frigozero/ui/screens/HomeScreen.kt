package com.example.frigozero.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.frigozero.R
import com.example.frigozero.viewmodel.FrigoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: FrigoViewModel,
    onScanClick: () -> Unit,
    onFindRecipesClick: () -> Unit
) {
    val scannedIngredients by viewModel.scannedIngredients.collectAsState()
    var manualInput by remember { mutableStateOf("") }
    var showManualInput by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScanClick,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Scansiona") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(24.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.drawable.logo_frigozero),
                                contentDescription = "FrigoZero Logo",
                                modifier = Modifier
                                    .size(108.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    "FrigoZero",
                                    color = Color(0xFF224F7C),
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "La tua dispensa intelligente",
                                    color = Color(0xFF224F7C).copy(alpha = 0.75f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "📷 Fotografa gli ingredienti e scopri cosa puoi cucinare!",
                            color = Color(0xFF224F7C),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Stats bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatCard(
                        number = scannedIngredients.size.toString(),
                        label = "Ingredienti",
                        color = MaterialTheme.colorScheme.primary
                    )
                    StatCard(
                        number = if (scannedIngredients.isEmpty()) "0"
                        else viewModel.suggestedRecipes.collectAsState().value.size.toString(),
                        label = "Ricette possibili",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            // Ingredients section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🧺 I tuoi ingredienti",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        TextButton(onClick = { showManualInput = !showManualInput }) {
                            Text("Aggiungi manuale")
                        }
                        if (scannedIngredients.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearIngredients() }) {
                                Text("Svuota", color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }

            // Manual input
            if (showManualInput) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualInput,
                            onValueChange = { manualInput = it },
                            label = { Text("Es. pomodoro, uovo...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (manualInput.isNotBlank()) {
                                viewModel.addIngredient(manualInput.trim())
                                manualInput = ""
                            }
                        }) {
                            Text("Aggiungi")
                        }
                    }
                }
            }

            if (scannedIngredients.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📷", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Nessun ingrediente ancora!",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Premi il pulsante + per scansionare\ngli ingredienti con la fotocamera",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            } else {
                items(scannedIngredients) { ingredient ->
                    IngredientChipRow(
                        ingredient = ingredient,
                        onRemove = { viewModel.removeIngredient(ingredient) }
                    )
                }
            }

            // Find recipes button
            if (scannedIngredients.size >= 2) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onFindRecipesClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Restaurant, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Trova Ricette",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else if (scannedIngredients.size == 1) {
                item {
                    Text(
                        "Scansiona ancora 1 ingrediente per vedere le ricette.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(number: String, label: String, color: Color) {
    Card(
        modifier = Modifier.width(140.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                number,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun IngredientChipRow(ingredient: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🥦", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    ingredient.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Rimuovi",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

