package com.example.frigozero.viewmodel

import androidx.lifecycle.ViewModel
import com.example.frigozero.data.IngredientCatalog
import com.example.frigozero.data.Recipe
import com.example.frigozero.data.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FrigoViewModel : ViewModel() {

    private val _scannedIngredients = MutableStateFlow<List<String>>(emptyList())
    val scannedIngredients: StateFlow<List<String>> = _scannedIngredients.asStateFlow()

    private val _suggestedRecipes = MutableStateFlow<List<Pair<Recipe, Int>>>(emptyList())
    val suggestedRecipes: StateFlow<List<Pair<Recipe, Int>>> = _suggestedRecipes.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun addIngredient(ingredient: String) {
        val cleaned = IngredientCatalog.toDisplayIngredient(ingredient)
        if (cleaned.isNotBlank() && !_scannedIngredients.value.any {
                it.lowercase() == cleaned.lowercase()
            }) {
            _scannedIngredients.value = _scannedIngredients.value + cleaned
            refreshRecipes()
        }
    }

    fun removeIngredient(ingredient: String) {
        _scannedIngredients.value = _scannedIngredients.value.filter { it != ingredient }
        refreshRecipes()
    }

    fun clearIngredients() {
        _scannedIngredients.value = emptyList()
        _suggestedRecipes.value = emptyList()
    }

    fun onScanResult(labels: List<String>) {
        IngredientCatalog.extractSpecificIngredients(labels).forEach { addIngredient(it) }
    }

    fun setScanning(scanning: Boolean) {
        _isScanning.value = scanning
    }

    private fun refreshRecipes() {
        _suggestedRecipes.value =
            RecipeRepository.getRecipesForIngredients(_scannedIngredients.value)
    }
}

