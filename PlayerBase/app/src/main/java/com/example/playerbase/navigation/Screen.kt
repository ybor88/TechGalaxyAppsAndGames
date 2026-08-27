package com.example.playerbase.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    object PlayerList : Screen("player_list/{sport}") {
        fun createRoute(sport: String) = "player_list/$sport"
    }
    object PlayerEdit : Screen("player_edit/{sport}/{playerId}") {
        fun createRoute(sport: String, playerId: String) = "player_edit/$sport/$playerId"
    }
    object AvatarCreator : Screen("avatar_creator")
    object Chart : Screen("chart")
    object MaxCareerStats : Screen("max_career_stats")
    object AgeStats : Screen("age_stats")
    object AiAssistant : Screen("ai_assistant")
    object ScoutingExpiring : Screen("scouting_expiring")
    object ColorSettings : Screen("color_settings")
}
