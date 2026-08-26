package com.example.frigozero.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    object Camera : Screen("camera")
    object Recipes : Screen("recipes")
    object AddRecipe : Screen("add_recipe")
    object MyRecipes : Screen("my_recipes")
    object RecipeWebSearch : Screen("recipe_web_search")
    object RecipeDetail : Screen("recipe_detail/{recipeId}") {
        fun createRoute(recipeId: Int) = "recipe_detail/$recipeId"
    }
}

