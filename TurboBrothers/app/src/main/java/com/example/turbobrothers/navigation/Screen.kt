package com.example.turbobrothers.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Menu : Screen("menu")
    data object CharacterSelect : Screen("character_select")
    data object Game : Screen("game")
    data object GameOver : Screen("game_over")
}
