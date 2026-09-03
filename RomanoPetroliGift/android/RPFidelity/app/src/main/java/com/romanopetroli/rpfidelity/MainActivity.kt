package com.romanopetroli.rpfidelity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.romanopetroli.rpfidelity.navigation.Screen
import com.romanopetroli.rpfidelity.ui.components.RpDrawerContent
import com.romanopetroli.rpfidelity.ui.screens.ContattiScreen
import com.romanopetroli.rpfidelity.ui.screens.DashboardScreen
import com.romanopetroli.rpfidelity.ui.screens.FaqScreen
import com.romanopetroli.rpfidelity.ui.screens.ImpostazioniScreen
import com.romanopetroli.rpfidelity.ui.screens.LaMiaCardScreen
import com.romanopetroli.rpfidelity.ui.screens.LoginScreen
import com.romanopetroli.rpfidelity.ui.screens.RegisterScreen
import com.romanopetroli.rpfidelity.ui.screens.RifornimentiScreen
import com.romanopetroli.rpfidelity.ui.screens.VoucherScreen
import com.romanopetroli.rpfidelity.ui.screens.admin.ClienteDettaglioScreen
import com.romanopetroli.rpfidelity.ui.screens.admin.ClientiScreen
import com.romanopetroli.rpfidelity.ui.screens.admin.RegistraRifornimentoScreen
import com.romanopetroli.rpfidelity.ui.screens.admin.ReportsScreen
import com.romanopetroli.rpfidelity.ui.screens.admin.StatisticheScreen
import com.romanopetroli.rpfidelity.ui.screens.admin.VerificaVoucherScreen
import com.romanopetroli.rpfidelity.ui.theme.RPFidelityTheme
import com.romanopetroli.rpfidelity.viewmodel.AdminViewModel
import com.romanopetroli.rpfidelity.viewmodel.ClienteViewModel
import com.romanopetroli.rpfidelity.viewmodel.SessionViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RPFidelityTheme {
                RPFidelityApp()
            }
        }
    }
}

@Composable
fun RPFidelityApp() {
    val navController = rememberNavController()
    val sessionViewModel: SessionViewModel = viewModel()
    val clienteViewModel: ClienteViewModel = viewModel()
    val adminViewModel: AdminViewModel = viewModel()

    val initialized by sessionViewModel.initialized.collectAsState()
    val user by sessionViewModel.user.collectAsState()

    if (!initialized) {
        return
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    fun openDrawer() {
        scope.launch { drawerState.open() }
    }

    fun navigateFromDrawer(route: String) {
        scope.launch { drawerState.close() }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun logout() {
        scope.launch { drawerState.close() }
        sessionViewModel.logout()
        navController.navigate(Screen.Login.route) {
            popUpTo(0) { inclusive = true }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = user != null,
        drawerContent = {
            RpDrawerContent(
                user = user,
                currentRoute = currentRoute,
                onNavigate = ::navigateFromDrawer,
                onLogout = ::logout
            )
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = if (user != null) Screen.Dashboard.route else Screen.Login.route,
            modifier = Modifier.safeDrawingPadding()
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    sessionViewModel = sessionViewModel,
                    onLoginSuccess = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onGoToRegister = { navController.navigate(Screen.Register.route) }
                )
            }
            composable(Screen.Register.route) {
                RegisterScreen(
                    sessionViewModel = sessionViewModel,
                    onRegisterSuccess = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    sessionViewModel = sessionViewModel,
                    onOpenDrawer = ::openDrawer,
                    onLogout = ::logout,
                    onLaMiaCard = { navController.navigate(Screen.LaMiaCard.route) },
                    onRifornimenti = { navController.navigate(Screen.Rifornimenti.route) },
                    onVoucher = { navController.navigate(Screen.Voucher.route) },
                    onAdminRegistraRifornimento = { navController.navigate(Screen.AdminRegistraRifornimento.route) },
                    onAdminReports = { navController.navigate(Screen.AdminReports.route) },
                    onAdminVerificaVoucher = { navController.navigate(Screen.AdminVerificaVoucher.route) }
                )
            }
            composable(Screen.LaMiaCard.route) {
                LaMiaCardScreen(sessionViewModel = sessionViewModel, onOpenDrawer = ::openDrawer)
            }
            composable(Screen.Rifornimenti.route) {
                RifornimentiScreen(clienteViewModel = clienteViewModel, onOpenDrawer = ::openDrawer)
            }
            composable(Screen.Voucher.route) {
                VoucherScreen(
                    sessionViewModel = sessionViewModel,
                    clienteViewModel = clienteViewModel,
                    onOpenDrawer = ::openDrawer
                )
            }
            composable(Screen.Contatti.route) {
                ContattiScreen(clienteViewModel = clienteViewModel, onOpenDrawer = ::openDrawer)
            }
            composable(Screen.Impostazioni.route) {
                ImpostazioniScreen(
                    sessionViewModel = sessionViewModel,
                    clienteViewModel = clienteViewModel,
                    onOpenDrawer = ::openDrawer
                )
            }
            composable(Screen.Faq.route) {
                FaqScreen(onOpenDrawer = ::openDrawer)
            }
            composable(Screen.AdminRegistraRifornimento.route) {
                RegistraRifornimentoScreen(adminViewModel = adminViewModel, onOpenDrawer = ::openDrawer)
            }
            composable(Screen.AdminReports.route) {
                ReportsScreen(adminViewModel = adminViewModel, onOpenDrawer = ::openDrawer)
            }
            composable(Screen.AdminVerificaVoucher.route) {
                VerificaVoucherScreen(adminViewModel = adminViewModel, onOpenDrawer = ::openDrawer)
            }
            composable(Screen.AdminStatistiche.route) {
                StatisticheScreen(adminViewModel = adminViewModel, onOpenDrawer = ::openDrawer)
            }
            composable(Screen.AdminClienti.route) {
                ClientiScreen(
                    adminViewModel = adminViewModel,
                    onOpenDrawer = ::openDrawer,
                    onSelezionaCliente = { navController.navigate(Screen.AdminClienteDettaglio.route) }
                )
            }
            composable(Screen.AdminClienteDettaglio.route) {
                ClienteDettaglioScreen(adminViewModel = adminViewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}
