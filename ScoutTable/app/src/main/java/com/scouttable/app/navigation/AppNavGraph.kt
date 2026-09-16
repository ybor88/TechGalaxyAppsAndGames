package com.scouttable.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scouttable.app.data.Sport
import com.scouttable.app.ui.playerdetail.PlayerDetailScreen
import com.scouttable.app.ui.sporthome.SportHomeScreen
import com.scouttable.app.ui.sportselect.SportSelectionScreen

private const val ROUTE_SPORT_SELECT = "sport_select"
private const val ROUTE_SPORT_HOME = "sport_home/{sport}"
private const val ROUTE_PLAYER_DETAIL = "player_detail/{sport}/{playerId}"
private const val ARG_SPORT = "sport"
private const val ARG_PLAYER_ID = "playerId"

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_SPORT_SELECT) {
        composable(ROUTE_SPORT_SELECT) {
            SportSelectionScreen(
                onSportSelected = { sport ->
                    navController.navigate("sport_home/${sport.name}")
                }
            )
        }
        composable(
            route = ROUTE_SPORT_HOME,
            arguments = listOf(navArgument(ARG_SPORT) { type = NavType.StringType }),
        ) { backStackEntry ->
            val sport = Sport.valueOf(backStackEntry.arguments?.getString(ARG_SPORT) ?: Sport.BASKET.name)
            SportHomeScreen(
                sport = sport,
                onSwitchSport = {
                    navController.popBackStack(ROUTE_SPORT_SELECT, inclusive = false)
                },
                onOpenPlayer = { playerId ->
                    navController.navigate("player_detail/${sport.name}/$playerId")
                },
            )
        }
        composable(
            route = ROUTE_PLAYER_DETAIL,
            arguments = listOf(
                navArgument(ARG_SPORT) { type = NavType.StringType },
                navArgument(ARG_PLAYER_ID) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val sport = Sport.valueOf(backStackEntry.arguments?.getString(ARG_SPORT) ?: Sport.BASKET.name)
            val playerId = backStackEntry.arguments?.getString(ARG_PLAYER_ID).orEmpty()
            PlayerDetailScreen(
                sport = sport,
                playerId = playerId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
