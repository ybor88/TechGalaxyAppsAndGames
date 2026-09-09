package com.example.sentinelai

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.sentinelai.navigation.Screen
import com.example.sentinelai.ui.screens.CloudScreen
import com.example.sentinelai.ui.screens.DashboardScreen
import com.example.sentinelai.ui.screens.PrivacyScreen
import com.example.sentinelai.ui.screens.QuarantineScreen
import com.example.sentinelai.ui.screens.RealtimeScreen
import com.example.sentinelai.ui.screens.ScanScreen
import com.example.sentinelai.ui.screens.SettingsScreen
import com.example.sentinelai.ui.theme.SentinelAITheme
import com.example.sentinelai.ui.theme.SentinelBg
import com.example.sentinelai.ui.theme.SentinelBgSidebar
import com.example.sentinelai.ui.theme.SentinelTeal
import com.example.sentinelai.ui.theme.SentinelTextDim
import com.example.sentinelai.viewmodel.SentinelViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SentinelAITheme {
                SentinelApp()
            }
        }
    }
}

@Composable
fun SentinelApp() {
    val navController = rememberNavController()
    val viewModel: SentinelViewModel = viewModel()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Shared by the bottom bar AND the dashboard tiles, so every entry point
    // into a top-level destination leaves the same clean back stack shape
    // (a route pushed by one path but popped by the other otherwise leaves
    // a dead-end: the bottom bar's singleTop+restoreState no-ops instead of
    // navigating, because the stack shape it expects was never established).
    val navigateToTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = SentinelBg,
        bottomBar = {
            NavigationBar(containerColor = SentinelBgSidebar) {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.hierarchy?.firstOrNull()?.route

                Screen.bottomBarScreens.forEach { screen ->
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = { navigateToTopLevel(screen.route) },
                        icon = { Icon(iconFor(screen.route), contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SentinelTeal,
                            selectedTextColor = SentinelTeal,
                            unselectedIconColor = SentinelTextDim,
                            unselectedTextColor = SentinelTextDim,
                            indicatorColor = SentinelBg
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(viewModel, navigateToTopLevel)
            }
            composable(Screen.Scan.route) { ScanScreen(viewModel) }
            composable(Screen.Realtime.route) { RealtimeScreen(viewModel) }
            composable(Screen.Quarantine.route) { QuarantineScreen(viewModel) }
            composable(Screen.Privacy.route) { PrivacyScreen(viewModel) }
            composable(Screen.Cloud.route) { CloudScreen(viewModel) }
            composable(Screen.Settings.route) { SettingsScreen(viewModel) }
        }
    }
}

private fun iconFor(route: String) = when (route) {
    Screen.Dashboard.route -> Icons.Filled.Dashboard
    Screen.Scan.route -> Icons.Filled.Search
    Screen.Realtime.route -> Icons.Filled.Shield
    Screen.Quarantine.route -> Icons.Filled.Inventory2
    else -> Icons.Filled.Settings
}
