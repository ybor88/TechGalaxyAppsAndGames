package com.example.turbobrothers

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.turbobrothers.data.HighScoreStore
import com.example.turbobrothers.navigation.Screen
import com.example.turbobrothers.ui.screens.CharacterSelectScreen
import com.example.turbobrothers.ui.screens.GameOverScreen
import com.example.turbobrothers.ui.screens.GameScreen
import com.example.turbobrothers.ui.screens.MenuScreen
import com.example.turbobrothers.ui.screens.SplashScreen
import com.example.turbobrothers.ui.theme.TurboBrothersTheme
import com.example.turbobrothers.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HighScoreStore.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            TurboBrothersTheme {
                TurboBrothersApp()
            }
        }
    }
}

@Composable
fun TurboBrothersApp() {
    val navController = rememberNavController()
    val viewModel: GameViewModel = viewModel()

    NavHost(navController = navController, startDestination = Screen.Splash.route) {
        composable(Screen.Splash.route) {
            SplashScreen(onFinished = {
                viewModel.loadHighScore()
                navController.navigate(Screen.Menu.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Menu.route) {
            MenuScreen(
                highScore = viewModel.highScore,
                onPlayClick = { navController.navigate(Screen.CharacterSelect.route) }
            )
        }
        composable(Screen.CharacterSelect.route) {
            CharacterSelectScreen(
                onBack = { navController.popBackStack() },
                onCharacterChosen = { character ->
                    viewModel.selectCharacter(character)
                    viewModel.startGame()
                    navController.navigate(Screen.Game.route) {
                        popUpTo(Screen.CharacterSelect.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Game.route) {
            GameScreen(
                viewModel = viewModel,
                onGameOver = {
                    navController.navigate(Screen.GameOver.route) {
                        popUpTo(Screen.Game.route) { inclusive = true }
                    }
                },
                onExitToMenu = {
                    navController.navigate(Screen.Menu.route) {
                        popUpTo(Screen.Menu.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.GameOver.route) {
            GameOverScreen(
                viewModel = viewModel,
                onRetry = {
                    viewModel.startGame()
                    navController.navigate(Screen.Game.route) {
                        popUpTo(Screen.GameOver.route) { inclusive = true }
                    }
                },
                onMenu = {
                    navController.navigate(Screen.Menu.route) {
                        popUpTo(Screen.Menu.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
