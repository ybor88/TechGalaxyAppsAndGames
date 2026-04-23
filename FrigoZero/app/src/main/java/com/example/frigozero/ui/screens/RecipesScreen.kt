package com.example.frigozero.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.frigozero.data.Recipe
import com.example.frigozero.viewmodel.FrigoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    viewModel: FrigoViewModel,
    onBack: () -> Unit,
    onRecipeClick: (Int) -> Unit
) {
    val suggestedRecipes by viewModel.suggestedRecipes.collectAsState()
    val scannedIngredients by viewModel.scannedIngredients.collectAsState()
    val hasMinimumIngredients = scannedIngredients.size >= 2

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ricette per te") },
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
        if (!hasMinimumIngredients || suggestedRecipes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("😔", fontSize = 56.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (!hasMinimumIngredients) "Servono almeno 2 ingredienti"
                        else "Nessuna ricetta trovata",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (!hasMinimumIngredients) {
                            "Scansiona almeno due ingredienti precisi per vedere le ricette."
                        } else {
                            "Prova con ingredienti come banana, pomodoro, uovo o latte."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Basato su ${scannedIngredients.size} ingredienti trovati ${suggestedRecipes.size} ricette:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(suggestedRecipes) { (recipe, matchCount) ->
                    RecipeCard(
                        recipe = recipe,
                        matchCount = matchCount,
                        totalIngredients = recipe.ingredients.size,
                        onClick = { onRecipeClick(recipe.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun RecipeCard(
    recipe: Recipe,
    matchCount: Int,
    totalIngredients: Int,
    onClick: () -> Unit
) {
    val matchPercent = (matchCount.toFloat() / totalIngredients * 100).toInt()
    val matchColor = when {
        matchPercent >= 75 -> Color(0xFF2E7D32)
        matchPercent >= 50 -> Color(0xFFE65100)
        else -> Color(0xFF1565C0)
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji icon
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(recipe.emoji, fontSize = 32.sp)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    recipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    recipe.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Match badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = matchColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "$matchCount/$totalIngredients ingredienti",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = matchColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Time badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            "⏱ ${recipe.cookTimeMinutes}min",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    // Difficulty badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            recipe.difficulty,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }
}

