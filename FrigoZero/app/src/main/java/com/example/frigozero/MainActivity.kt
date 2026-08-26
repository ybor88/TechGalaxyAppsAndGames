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
import com.example.frigozero.data.RecipeRepository
import com.example.frigozero.navigation.Screen
import com.example.frigozero.ui.screens.*
import com.example.frigozero.ui.theme.FrigoZeroTheme
import com.example.frigozero.viewmodel.FrigoViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RecipeRepository.init(applicationContext)
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

    NavHost(navController = navController, startDestination = Screen.Splash.route) {
        composable(Screen.Splash.route) {
            SplashScreen(onFinished = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onScanClick = { navController.navigate(Screen.Camera.route) },
                onFindRecipesClick = { source ->
                    viewModel.searchRecipes(source)
                    navController.navigate(Screen.Recipes.route)
                },
                onMyRecipesClick = { navController.navigate(Screen.MyRecipes.route) }
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
                },
                onWebSearchClick = { navController.navigate(Screen.RecipeWebSearch.route) },
                onAddRecipeClick = { navController.navigate(Screen.AddRecipe.route) }
            )
        }
        composable(Screen.AddRecipe.route) {
            AddRecipeScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
        composable(Screen.MyRecipes.route) {
            MyRecipesScreen(
                onBack = { navController.popBackStack() },
                onRecipeClick = { recipeId ->
                    navController.navigate(Screen.RecipeDetail.createRoute(recipeId))
                },
                onAddRecipeClick = { navController.navigate(Screen.AddRecipe.route) }
            )
        }
        composable(Screen.RecipeWebSearch.route) {
            RecipeWebSearchScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
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