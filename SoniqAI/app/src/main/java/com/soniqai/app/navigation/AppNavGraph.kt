// Copyright (c) Roberto Di Flumeri
package com.soniqai.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.soniqai.app.ui.create.CreateSongScreen
import com.soniqai.app.ui.home.HomeScreen
import com.soniqai.app.ui.studio.StudioScreen

private const val ROUTE_HOME = "home"
private const val ROUTE_CREATE = "create"
private const val ROUTE_STUDIO = "studio/{songId}"
private const val ARG_SONG_ID = "songId"

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            HomeScreen(
                onCreateSong = { navController.navigate(ROUTE_CREATE) },
                onOpenSong = { songId -> navController.navigate("studio/$songId") },
            )
        }
        composable(ROUTE_CREATE) {
            CreateSongScreen(
                onSongCreated = { songId ->
                    navController.navigate("studio/$songId") {
                        popUpTo(ROUTE_HOME)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = ROUTE_STUDIO,
            arguments = listOf(navArgument(ARG_SONG_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val songId = backStackEntry.arguments?.getString(ARG_SONG_ID).orEmpty()
            StudioScreen(songId = songId, onBack = { navController.popBackStack() })
        }
    }
}
