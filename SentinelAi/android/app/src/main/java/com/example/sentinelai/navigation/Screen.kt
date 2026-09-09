package com.example.sentinelai.navigation

sealed class Screen(val route: String, val label: String) {
    object Dashboard : Screen("dashboard", "Dashboard")
    object Scan : Screen("scan", "Scansione")
    object Realtime : Screen("realtime", "Tempo Reale")
    object Quarantine : Screen("quarantine", "Quarantena")
    object Privacy : Screen("privacy", "Privacy")
    object Cloud : Screen("cloud", "Cloud")
    object Settings : Screen("settings", "Impostazioni")

    companion object {
        val bottomBarScreens = listOf(Dashboard, Scan, Realtime, Quarantine, Settings)
    }
}
