package com.example.playerbase

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.playerbase.data.Sport
import com.example.playerbase.navigation.Screen
import com.example.playerbase.notification.ScoutingCheckWorker
import com.example.playerbase.ui.screens.*
import com.example.playerbase.ui.theme.PlayerBaseTheme
import com.example.playerbase.ui.theme.ThemeColorStore
import com.example.playerbase.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeColorStore.load(this)
        ScoutingCheckWorker.schedule(this)
        setContent {
            PlayerBaseTheme {
                PlayerBaseApp()
            }
        }
    }
}

@Composable
fun PlayerBaseApp() {
    val navController = rememberNavController()
    val viewModel: PlayerViewModel = viewModel()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* se negato, semplicemente non arriveranno notifiche */ }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        viewModel.checkScoutingNotifications()
    }

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
                onSportClick = { sport ->
                    navController.navigate(Screen.PlayerList.createRoute(sport.name))
                },
                onChartClick = { navController.navigate(Screen.Chart.route) },
                onScoutingExpiringClick = { navController.navigate(Screen.ScoutingExpiring.route) },
                onOpenColorSettings = { navController.navigate(Screen.ColorSettings.route) }
            )
        }
        composable(
            route = Screen.PlayerList.route,
            arguments = listOf(navArgument("sport") { type = NavType.StringType })
        ) { backStackEntry ->
            val sport = Sport.valueOf(backStackEntry.arguments?.getString("sport") ?: Sport.BASKET.name)
            PlayerListScreen(
                viewModel = viewModel,
                sport = sport,
                onBack = { navController.popBackStack() },
                onAddPlayer = {
                    viewModel.startNewPlayer(sport)
                    navController.navigate(Screen.PlayerEdit.createRoute(sport.name, "new"))
                },
                onPlayerClick = { playerId ->
                    viewModel.startEditPlayer(playerId)
                    navController.navigate(Screen.PlayerEdit.createRoute(sport.name, playerId))
                }
            )
        }
        composable(
            route = Screen.PlayerEdit.route,
            arguments = listOf(
                navArgument("sport") { type = NavType.StringType },
                navArgument("playerId") { type = NavType.StringType }
            )
        ) {
            PlayerEditScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onCustomizeAvatar = { navController.navigate(Screen.AvatarCreator.route) }
            )
        }
        composable(Screen.AvatarCreator.route) {
            AvatarCreatorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Chart.route) {
            ChartScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ColorSettings.route) {
            ColorSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.ScoutingExpiring.route) {
            ScoutingExpiringScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onPlayerClick = { playerId ->
                    val player = viewModel.players.value.find { it.id == playerId }
                    if (player != null) {
                        viewModel.startEditPlayer(playerId)
                        navController.navigate(Screen.PlayerEdit.createRoute(player.sport.name, playerId))
                    }
                }
            )
        }
    }
}
