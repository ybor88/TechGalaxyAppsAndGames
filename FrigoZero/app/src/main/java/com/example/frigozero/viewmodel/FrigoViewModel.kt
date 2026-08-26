package com.example.frigozero.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.frigozero.data.IngredientCatalog
import com.example.frigozero.data.Recipe
import com.example.frigozero.data.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RecipeSource {
    ARCHIVIO_LOCALE,
    MEALDB_ONLINE
}

class FrigoViewModel : ViewModel() {

    private val _scannedIngredients = MutableStateFlow<List<String>>(emptyList())
    val scannedIngredients: StateFlow<List<String>> = _scannedIngredients.asStateFlow()

    private val _suggestedRecipes = MutableStateFlow<List<Pair<Recipe, Int>>>(emptyList())
    val suggestedRecipes: StateFlow<List<Pair<Recipe, Int>>> = _suggestedRecipes.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isLoadingRecipes = MutableStateFlow(false)
    val isLoadingRecipes: StateFlow<Boolean> = _isLoadingRecipes.asStateFlow()

    private val _lastRecipeSource = MutableStateFlow<RecipeSource?>(null)
    val lastRecipeSource: StateFlow<RecipeSource?> = _lastRecipeSource.asStateFlow()

    fun addIngredient(ingredient: String) {
        val incomingIngredients = ingredient
            .split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (incomingIngredients.isEmpty()) {
            return
        }

        var hasChanges = false
        incomingIngredients.forEach { raw ->
            val cleaned = IngredientCatalog.toDisplayIngredient(raw)
            val alreadyPresent = _scannedIngredients.value.any {
                it.lowercase() == cleaned.lowercase()
            }
            if (cleaned.isNotBlank() && !alreadyPresent) {
                _scannedIngredients.value = _scannedIngredients.value + cleaned
                hasChanges = true
            }
        }

        if (hasChanges) {
            refreshLocalCount()
        }
    }

    fun removeIngredient(ingredient: String) {
        _scannedIngredients.value = _scannedIngredients.value.filter { it != ingredient }
        refreshLocalCount()
    }

    fun clearIngredients() {
        _scannedIngredients.value = emptyList()
        _suggestedRecipes.value = emptyList()
        _lastRecipeSource.value = null
    }

    /** Aggiunge una nuova ricetta all'archivio personale dell'app. */
    fun addUserRecipe(
        name: String,
        description: String,
        ingredients: List<String>,
        steps: List<String>,
        cookTimeMinutes: Int,
        difficulty: String,
        emoji: String
    ) {
        RecipeRepository.addUserRecipe(
            name = name,
            description = description,
            ingredients = ingredients,
            steps = steps,
            cookTimeMinutes = cookTimeMinutes,
            difficulty = difficulty,
            emoji = emoji
        )
        refreshLocalCount()
    }

    fun onScanResult(labels: List<String>) {
        IngredientCatalog.extractSpecificIngredients(labels).forEach { addIngredient(it) }
    }

    fun setScanning(scanning: Boolean) {
        _isScanning.value = scanning
    }

    /**
     * Conteggio rapido (senza rete) usato per lo stat "Ricette possibili" in home,
     * aggiornato ad ogni aggiunta/rimozione di ingrediente.
     */
    private fun refreshLocalCount() {
        _suggestedRecipes.value = RecipeRepository.getRecipesFromLocalArchive(_scannedIngredients.value)
    }

    /** Ricerca esplicita scelta dall'utente: archivio dell'app oppure TheMealDB online. */
    fun searchRecipes(source: RecipeSource) {
        _lastRecipeSource.value = source

        if (_scannedIngredients.value.isEmpty()) {
            _suggestedRecipes.value = emptyList()
            _isLoadingRecipes.value = false
            return
        }

        when (source) {
            RecipeSource.ARCHIVIO_LOCALE -> {
                _isLoadingRecipes.value = false
                _suggestedRecipes.value = RecipeRepository.getRecipesFromLocalArchive(_scannedIngredients.value)
            }
            RecipeSource.MEALDB_ONLINE -> {
                viewModelScope.launch {
                    _isLoadingRecipes.value = true
                    _suggestedRecipes.value = try {
                        RecipeRepository.getRecipesFromWeb(_scannedIngredients.value)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    _isLoadingRecipes.value = false
                }
            }
        }
    }
}

