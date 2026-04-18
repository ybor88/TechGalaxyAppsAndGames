package com.example.frigozero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.frigozero.navigation.Screen
import com.example.frigozero.ui.screens.*
import com.example.frigozero.ui.theme.FrigoZeroTheme
import com.example.frigozero.viewmodel.FrigoViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FrigoZeroTheme {
                FrigoZeroApp()
            }
        }
    }
}

@Composable
fun FrigoZeroApp() {
    val navController = rememberNavController()
    val viewModel: FrigoViewModel = viewModel()

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onScanClick = { navController.navigate(Screen.Camera.route) },
                onFindRecipesClick = { navController.navigate(Screen.Recipes.route) }
            )
        }
        composable(Screen.Camera.route) {
            CameraPermissionScreen {
                CameraScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.Recipes.route) {
            RecipesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onRecipeClick = { recipeId ->
                    navController.navigate(Screen.RecipeDetail.createRoute(recipeId))
                }
            )
        }
        composable(
            route = Screen.RecipeDetail.route,
            arguments = listOf(navArgument("recipeId") { type = NavType.IntType })
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getInt("recipeId") ?: return@composable
            RecipeDetailScreen(
                recipeId = recipeId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}