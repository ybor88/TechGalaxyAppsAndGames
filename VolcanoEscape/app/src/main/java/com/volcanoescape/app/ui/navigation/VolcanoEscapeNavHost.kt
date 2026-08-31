package com.volcanoescape.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.volcanoescape.app.R
import com.volcanoescape.app.data.location.LocationProvider
import com.volcanoescape.app.data.model.VolcanoRepository
import com.volcanoescape.app.data.repository.RoutingRepository
import com.volcanoescape.app.ui.screens.monitoring.MonitoringScreen
import com.volcanoescape.app.ui.screens.monitoring.MonitoringViewModel
import com.volcanoescape.app.ui.screens.route.EscapeRouteScreen
import com.volcanoescape.app.ui.screens.route.EscapeRouteViewModel
import com.volcanoescape.app.ui.screens.splash.SplashScreen
import com.volcanoescape.app.ui.screens.volcanolist.VolcanoListScreen

@Composable
fun VolcanoEscapeNavHost(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = Destination.Splash.route) {
        composable(Destination.Splash.route) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Destination.VolcanoList.route) {
                        popUpTo(Destination.Splash.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Destination.VolcanoList.route) {
            VolcanoListScreen(
                onVolcanoSelected = { volcano ->
                    navController.navigate(Destination.Monitoring.createRoute(volcano.id))
                },
            )
        }

        composable(Destination.Monitoring.route) { backStackEntry ->
            val volcanoId = backStackEntry.arguments?.getString("volcanoId")
            val volcano = VolcanoRepository.italianVolcanoes.first { it.id == volcanoId }
            val viewModel: MonitoringViewModel = viewModel(
                factory = viewModelFactory { initializer { MonitoringViewModel(volcano) } },
            )
            MonitoringScreen(
                volcano = volcano,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onFindEscapeRoute = { navController.navigate(Destination.EscapeRoute.createRoute(volcano.id)) },
            )
        }

        composable(Destination.EscapeRoute.route) { backStackEntry ->
            val volcanoId = backStackEntry.arguments?.getString("volcanoId")
            val volcano = VolcanoRepository.italianVolcanoes.first { it.id == volcanoId }
            val viewModel: EscapeRouteViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        val apiKey = context.getString(R.string.tomtom_api_key)
                        EscapeRouteViewModel(
                            volcano = volcano,
                            locationProvider = LocationProvider(context.applicationContext),
                            routingRepository = RoutingRepository(apiKey),
                        )
                    }
                },
            )
            EscapeRouteScreen(
                volcano = volcano,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
