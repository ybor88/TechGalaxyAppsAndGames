package com.romanopetroli.rpfidelity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.romanopetroli.rpfidelity.navigation.Screen
import com.romanopetroli.rpfidelity.ui.screens.DashboardScreen
import com.romanopetroli.rpfidelity.ui.screens.LaMiaCardScreen
import com.romanopetroli.rpfidelity.ui.screens.LoginScreen
import com.romanopetroli.rpfidelity.ui.screens.RegisterScreen
import com.romanopetroli.rpfidelity.ui.screens.RifornimentiScreen
import com.romanopetroli.rpfidelity.ui.screens.VoucherScreen
import com.romanopetroli.rpfidelity.ui.screens.admin.RegistraRifornimentoScreen
import com.romanopetroli.rpfidelity.ui.screens.admin.ReportsScreen
import com.romanopetroli.rpfidelity.ui.screens.admin.VerificaVoucherScreen
import com.romanopetroli.rpfidelity.ui.theme.RPFidelityTheme
import com.romanopetroli.rpfidelity.viewmodel.AdminViewModel
import com.romanopetroli.rpfidelity.viewmodel.ClienteViewModel
import com.romanopetroli.rpfidelity.viewmodel.SessionViewModel

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

    NavHost(
        navController = navController,
        startDestination = if (user != null) Screen.Dashboard.route else Screen.Login.route
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
                onLogout = {
                    sessionViewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onLaMiaCard = { navController.navigate(Screen.LaMiaCard.route) },
                onRifornimenti = { navController.navigate(Screen.Rifornimenti.route) },
                onVoucher = { navController.navigate(Screen.Voucher.route) },
                onAdminRegistraRifornimento = { navController.navigate(Screen.AdminRegistraRifornimento.route) },
                onAdminReports = { navController.navigate(Screen.AdminReports.route) },
                onAdminVerificaVoucher = { navController.navigate(Screen.AdminVerificaVoucher.route) }
            )
        }
        composable(Screen.LaMiaCard.route) {
            LaMiaCardScreen(sessionViewModel = sessionViewModel, onBack = { navController.popBackStack() })
        }
        composable(Screen.Rifornimenti.route) {
            RifornimentiScreen(clienteViewModel = clienteViewModel, onBack = { navController.popBackStack() })
        }
        composable(Screen.Voucher.route) {
            VoucherScreen(
                sessionViewModel = sessionViewModel,
                clienteViewModel = clienteViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.AdminRegistraRifornimento.route) {
            RegistraRifornimentoScreen(adminViewModel = adminViewModel, onBack = { navController.popBackStack() })
        }
        composable(Screen.AdminReports.route) {
            ReportsScreen(adminViewModel = adminViewModel, onBack = { navController.popBackStack() })
        }
        composable(Screen.AdminVerificaVoucher.route) {
            VerificaVoucherScreen(adminViewModel = adminViewModel, onBack = { navController.popBackStack() })
        }
    }
}
