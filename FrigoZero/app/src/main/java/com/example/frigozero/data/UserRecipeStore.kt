package com.example.frigozero.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistenza locale (SharedPreferences + JSON) delle ricette inserite manualmente
 * dall'utente, cosi restano disponibili anche dopo aver chiuso l'app.
 */
object UserRecipeStore {

    private const val prefsName = "frigozero_user_recipes"
    private const val recipesKey = "recipes_json"

    fun load(context: Context): List<Recipe> {
        val raw = prefs(context).getString(recipesKey, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let(::recipeFromJson)
            }
        } catch (e: Exception) {
            Log.w("FrigoZero", "Failed to load user recipes", e)
            emptyList()
        }
    }

    fun save(context: Context, recipes: List<Recipe>) {
        val array = JSONArray()
        recipes.forEach { array.put(recipeToJson(it)) }
        prefs(context).edit().putString(recipesKey, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private fun recipeToJson(recipe: Recipe): JSONObject = JSONObject().apply {
        put("id", recipe.id)
        put("name", recipe.name)
        put("description", recipe.description)
        put("ingredients", JSONArray(recipe.ingredients))
        put("steps", JSONArray(recipe.steps))
        put("emoji", recipe.emoji)
        put("cookTimeMinutes", recipe.cookTimeMinutes)
        put("difficulty", recipe.difficulty)
        put("sourceUrl", recipe.sourceUrl)
    }

    private fun recipeFromJson(json: JSONObject): Recipe? {
        val name = json.optString("name").trim()
        if (name.isBlank()) return null

        return Recipe(
            id = json.optInt("id"),
            name = name,
            description = json.optString("description"),
            ingredients = json.optJSONArray("ingredients").toStringList(),
            steps = json.optJSONArray("steps").toStringList(),
            emoji = json.optString("emoji").ifBlank { "🍽️" },
            cookTimeMinutes = json.optInt("cookTimeMinutes", 30),
            difficulty = json.optString("difficulty").ifBlank { "Facile" },
            sourceUrl = json.optString("sourceUrl").ifBlank { null }
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).ifBlank { null } }
    }
}
